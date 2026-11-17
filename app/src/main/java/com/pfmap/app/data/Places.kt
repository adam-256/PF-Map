package com.pfmap.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

@Serializable data class LocalizedText(val text: String = "", val languageCode: String = "")
@Serializable data class LatLngLiteral(val latitude: Double = 0.0, val longitude: Double = 0.0)
@Serializable data class AuthorAttribution(val displayName: String = "", val uri: String = "", val photoUri: String = "")

@Serializable
data class PlaceReview(
    val rating: Double = 0.0,
    val relativePublishTimeDescription: String = "",
    val text: LocalizedText? = null,
    val originalText: LocalizedText? = null,
    val authorAttribution: AuthorAttribution? = null,
    val publishTime: String = "",
    val googleMapsUri: String = "",
) {
    val body: String get() = text?.text?.ifBlank { null } ?: originalText?.text.orEmpty()
}

@Serializable
data class Place(
    val id: String = "",
    val displayName: LocalizedText? = null,
    val formattedAddress: String = "",
    val rating: Double? = null,
    val userRatingCount: Int? = null,
    val reviews: List<PlaceReview> = emptyList(),
    val googleMapsUri: String = "",
    val location: LatLngLiteral? = null,
)

@Serializable private data class SearchTextResponse(val places: List<Place> = emptyList())

/** Google Places API (New) - Text Search, biased to the club's coordinates. */
object PlacesClient {
    private const val URL = "https://places.googleapis.com/v1/places:searchText"
    private const val FIELDS = "places.id,places.displayName,places.formattedAddress,places.rating," +
        "places.userRatingCount,places.reviews,places.googleMapsUri,places.location"

    suspend fun lookup(club: Club, apiKey: String): Place? = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("textQuery", "Planet Fitness, ${club.fullAddress}")
            put("pageSize", 3)
            putJsonObject("locationBias") {
                putJsonObject("circle") {
                    putJsonObject("center") { put("latitude", club.lat); put("longitude", club.lng) }
                    put("radius", 800.0)
                }
            }
        }.toString()
        val raw = try {
            Http.post(URL, body, mapOf("X-Goog-Api-Key" to apiKey, "X-Goog-FieldMask" to FIELDS))
        } catch (e: HttpException) {
            val msg = runCatching {
                AppJson.parseToJsonElement(e.body).jsonObject["error"]!!.jsonObject["message"]!!.jsonPrimitive.contentOrNull
            }.getOrNull()
            throw IllegalStateException(msg ?: "Google Places error ${e.code}")
        }
        val places = AppJson.decodeFromString<SearchTextResponse>(raw).places
        // Pick the nearest result that's actually a Planet Fitness within ~1.5 km.
        places
            .filter { it.location != null }
            .map { it to distanceMeters(club.lat, club.lng, it.location!!.latitude, it.location.longitude) }
            .filter { (p, d) -> d < 1500 && p.displayName?.text.orEmpty().contains("planet fitness", ignoreCase = true) }
            .minByOrNull { it.second }?.first
            ?: places.firstOrNull { it.location != null && distanceMeters(club.lat, club.lng, it.location.latitude, it.location.longitude) < 400 }
    }

    private fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val k = 111_320.0
        val dx = (lng2 - lng1) * k * cos(Math.toRadians((lat1 + lat2) / 2))
        val dy = (lat2 - lat1) * k
        return sqrt(dx.pow(2) + dy.pow(2))
    }
}
