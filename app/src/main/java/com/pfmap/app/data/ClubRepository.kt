package com.pfmap.app.data

import android.content.Context
import com.pfmap.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit

sealed interface UpdateResult {
    data class UpToDate(val file: ClubsFile) : UpdateResult
    data class Updated(val file: ClubsFile, val added: Int, val removed: Int) : UpdateResult
    data class Failed(val reason: String) : UpdateResult
}

/**
 * Club list storage + update check.
 *
 * Order of truth: newest of (bundled asset, downloaded cache). On every startup the app polls a
 * tiny manifest (clubs-meta.json) published by the repo's "Update club list" GitHub Action and
 * downloads the full list only when its content hash differs.
 */
class ClubRepository(private val context: Context) {
    private val cacheFile = File(context.filesDir, "clubs.json")
    private val repo = BuildConfig.DATA_REPO

    private val sources = listOf(
        // GitHub release asset: works regardless of branch names.
        "https://github.com/$repo/releases/download/clubs-data/clubs-meta.json" to
            "https://github.com/$repo/releases/download/clubs-data/clubs.json",
        // Same files committed to the default branch.
        "https://raw.githubusercontent.com/$repo/HEAD/data/clubs-meta.json" to
            "https://raw.githubusercontent.com/$repo/HEAD/data/clubs.json",
    )

    suspend fun loadLocal(): ClubsFile? = withContext(Dispatchers.IO) {
        val bundled = runCatching {
            context.assets.open("clubs.json").use { AppJson.decodeFromString<ClubsFile>(it.reader().readText()) }
        }.getOrNull()
        val cached = runCatching {
            AppJson.decodeFromString<ClubsFile>(cacheFile.readText())
        }.getOrNull()
        // A freshly installed APK may carry a newer snapshot than an old download, so pick the newest.
        listOfNotNull(bundled, cached).filter { it.clubs.isNotEmpty() }.maxByOrNull { it.generated }
            ?: bundled ?: cached
    }

    suspend fun checkForUpdate(current: ClubsFile?): UpdateResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        for ((metaUrl, dataUrl) in sources) {
            try {
                val meta = AppJson.decodeFromString<ClubsMeta>(Http.get(metaUrl))
                if (current != null && meta.version == current.version && current.clubs.isNotEmpty()) {
                    return@withContext UpdateResult.UpToDate(current)
                }
                val file = AppJson.decodeFromString<ClubsFile>(Http.get(dataUrl, timeoutMs = 30_000))
                if (file.clubs.size < MIN_CLUBS) throw IllegalStateException("only ${file.clubs.size} clubs")
                return@withContext saveAndDiff(current, file)
            } catch (e: Exception) {
                errors += "${hostOf(metaUrl)}: ${e.message}"
            }
        }
        // Last resort: read the open All the Places dataset directly, but only if our data is
        // missing or stale - the GitHub sources are the normal path.
        if (current == null || current.clubs.isEmpty() || isStale(current)) {
            try {
                val atp = fetchFromAllThePlaces()
                // That crawl is often partial; never let it replace a substantially bigger list.
                val floor = (current?.clubs?.size ?: 0) * 85 / 100
                if (atp.clubs.size < floor) error("partial crawl (${atp.clubs.size} clubs)")
                return@withContext saveAndDiff(current, atp)
            } catch (e: Exception) {
                errors += "alltheplaces: ${e.message}"
            }
        }
        UpdateResult.Failed(errors.joinToString("; "))
    }

    private fun saveAndDiff(current: ClubsFile?, file: ClubsFile): UpdateResult {
        val tmp = File(context.filesDir, "clubs.json.tmp")
        tmp.writeText(AppJson.encodeToString(ClubsFile.serializer(), file))
        tmp.renameTo(cacheFile)
        val oldIds = current?.clubs?.mapTo(HashSet()) { it.id }.orEmpty()
        val newIds = file.clubs.mapTo(HashSet()) { it.id }
        return UpdateResult.Updated(file, added = (newIds - oldIds).size, removed = (oldIds - newIds).size)
    }

    private fun fetchFromAllThePlaces(): ClubsFile {
        val latest = AppJson.parseToJsonElement(Http.get("https://data.alltheplaces.xyz/runs/latest.json")).jsonObject
        val runId = latest["run_id"]?.jsonPrimitive?.contentOrNull ?: error("no run id")
        val outputUrl = latest["output_url"]?.jsonPrimitive?.contentOrNull
        val base = outputUrl?.substringBeforeLast('/') ?: "https://alltheplaces-data.openaddresses.io/runs/$runId"
        val clubs = AtpParser.parse(Http.get("$base/output/planet_fitness_us.geojson", timeoutMs = 30_000))
            .sortedWith(compareBy({ it.state }, { it.city }, { it.name }, { it.id }))
        if (clubs.size < MIN_CLUBS) error("only ${clubs.size} clubs")
        return ClubsFile(
            version = "atp-$runId",
            generated = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
            source = "All the Places run $runId (CC0), fetched on device",
            count = clubs.size,
            clubs = clubs,
        )
    }

    private fun isStale(file: ClubsFile): Boolean = runCatching {
        Instant.parse(file.generated).isBefore(Instant.now().minus(45, ChronoUnit.DAYS))
    }.getOrDefault(true)

    private fun hostOf(url: String) = url.substringAfter("://").substringBefore('/')

    companion object {
        const val MIN_CLUBS = 100
    }
}
