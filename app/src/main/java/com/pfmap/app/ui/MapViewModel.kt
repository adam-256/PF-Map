package com.pfmap.app.ui

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pfmap.app.data.Club
import com.pfmap.app.data.ClubRepository
import com.pfmap.app.data.ClubsFile
import com.pfmap.app.data.Place
import com.pfmap.app.data.PlacesClient
import com.pfmap.app.data.Settings
import com.pfmap.app.data.UpdateResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.NumberFormat

sealed interface GoogleState {
    data object NoKey : GoogleState
    data object Loading : GoogleState
    data class Loaded(val place: Place?) : GoogleState
    data class Error(val message: String) : GoogleState
}

data class Status(val text: String, val busy: Boolean = false)

data class UiState(
    val data: ClubsFile? = null,
    val loading: Boolean = true,
    val status: Status? = null,
    val selected: Club? = null,
    val google: GoogleState = GoogleState.NoKey,
    val query: String = "",
    val results: List<Club> = emptyList(),
    val userLocation: Location? = null,
    val apiKey: String = "",
) {
    val clubs: List<Club> get() = data?.clubs.orEmpty()
}

class MapViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ClubRepository(app)
    private val settings = Settings(app)
    private val googleCache = mutableMapOf<String, Place?>()
    private var googleJob: Job? = null
    private var statusJob: Job? = null

    private val _state = MutableStateFlow(UiState(apiKey = settings.placesApiKey))
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val local = repo.loadLocal()
            _state.update { it.copy(data = local, loading = false) }
            checkForUpdates(manual = false)
        }
    }

    /** Runs on every launch (and from Settings): polls the published manifest, downloads only on change. */
    fun checkForUpdates(manual: Boolean) {
        viewModelScope.launch {
            showStatus(Status("Checking for club list updates…", busy = true), sticky = true)
            when (val r = repo.checkForUpdate(_state.value.data)) {
                is UpdateResult.UpToDate ->
                    showStatus(Status("Club list up to date · ${fmt(r.file.clubs.size)} clubs"))
                is UpdateResult.Updated -> {
                    _state.update { s -> s.copy(data = r.file, results = search(s.query, r.file.clubs, s.userLocation)) }
                    val diff = buildList {
                        if (r.added > 0) add("+${fmt(r.added)} new")
                        if (r.removed > 0) add("−${fmt(r.removed)} closed")
                    }.joinToString(", ")
                    showStatus(Status("Club list updated · ${fmt(r.file.clubs.size)} clubs" + if (diff.isNotEmpty()) " ($diff)" else ""), millis = 6000)
                }
                is UpdateResult.Failed -> {
                    val n = _state.value.clubs.size
                    showStatus(
                        Status(if (n > 0) "Offline – showing saved list (${fmt(n)} clubs)" else "Couldn't download the club list. Check your connection."),
                        millis = if (n > 0 || !manual) 5000 else 8000,
                    )
                }
            }
        }
    }

    fun select(club: Club?) {
        _state.update { it.copy(selected = club) }
        googleJob?.cancel()
        if (club == null) return
        val key = settings.placesApiKey
        if (key.isBlank()) {
            _state.update { it.copy(google = GoogleState.NoKey) }
            return
        }
        if (googleCache.containsKey(club.id)) {
            _state.update { it.copy(google = GoogleState.Loaded(googleCache[club.id])) }
            return
        }
        _state.update { it.copy(google = GoogleState.Loading) }
        googleJob = viewModelScope.launch {
            val result = runCatching { PlacesClient.lookup(club, key) }
            if (_state.value.selected?.id != club.id) return@launch
            result.onSuccess { place ->
                googleCache[club.id] = place
                _state.update { it.copy(google = GoogleState.Loaded(place)) }
            }.onFailure { e ->
                _state.update { it.copy(google = GoogleState.Error(e.message ?: "Couldn't load Google reviews")) }
            }
        }
    }

    fun selectById(id: String) = _state.value.clubs.firstOrNull { it.id == id }?.let { select(it) }

    fun retryGoogle() {
        _state.value.selected?.let { googleCache.remove(it.id); select(it) }
    }

    fun setQuery(q: String) = _state.update { it.copy(query = q, results = search(q, it.clubs, it.userLocation)) }

    fun setUserLocation(loc: Location) =
        _state.update { it.copy(userLocation = loc, results = search(it.query, it.clubs, loc)) }

    fun setApiKey(key: String) {
        settings.placesApiKey = key
        googleCache.clear()
        _state.update { it.copy(apiKey = settings.placesApiKey) }
        _state.value.selected?.let { select(it) }
    }

    fun nearest(n: Int = 1): List<Club> {
        val loc = _state.value.userLocation ?: return emptyList()
        return _state.value.clubs.sortedBy { distanceTo(loc, it) }.take(n)
    }

    private fun showStatus(status: Status, millis: Long = 4000, sticky: Boolean = false) {
        statusJob?.cancel()
        _state.update { it.copy(status = status) }
        if (!sticky) statusJob = viewModelScope.launch {
            delay(millis)
            _state.update { it.copy(status = null) }
        }
    }

    companion object {
        fun fmt(n: Int): String = NumberFormat.getIntegerInstance().format(n)

        fun distanceTo(loc: Location, club: Club): Float {
            val out = FloatArray(1)
            Location.distanceBetween(loc.latitude, loc.longitude, club.lat, club.lng, out)
            return out[0]
        }

        private val STATE_NAMES = mapOf(
            "alabama" to "AL", "alaska" to "AK", "arizona" to "AZ", "arkansas" to "AR", "california" to "CA",
            "colorado" to "CO", "connecticut" to "CT", "delaware" to "DE", "florida" to "FL", "georgia" to "GA",
            "hawaii" to "HI", "idaho" to "ID", "illinois" to "IL", "indiana" to "IN", "iowa" to "IA", "kansas" to "KS",
            "kentucky" to "KY", "louisiana" to "LA", "maine" to "ME", "maryland" to "MD", "massachusetts" to "MA",
            "michigan" to "MI", "minnesota" to "MN", "mississippi" to "MS", "missouri" to "MO", "montana" to "MT",
            "nebraska" to "NE", "nevada" to "NV", "new hampshire" to "NH", "new jersey" to "NJ", "new mexico" to "NM",
            "new york" to "NY", "north carolina" to "NC", "north dakota" to "ND", "ohio" to "OH", "oklahoma" to "OK",
            "oregon" to "OR", "pennsylvania" to "PA", "rhode island" to "RI", "south carolina" to "SC",
            "south dakota" to "SD", "tennessee" to "TN", "texas" to "TX", "utah" to "UT", "vermont" to "VT",
            "virginia" to "VA", "washington" to "WA", "west virginia" to "WV", "wisconsin" to "WI", "wyoming" to "WY",
            "puerto rico" to "PR", "district of columbia" to "DC",
        )

        /** Every word must match name/street/city/state/zip; full state names map to their code. */
        fun search(q: String, clubs: List<Club>, loc: Location?): List<Club> {
            var query = q.trim().lowercase()
            if (query.length < 2) return emptyList()
            var stateFilter: String? = null
            STATE_NAMES.entries.sortedByDescending { it.key.length }.firstOrNull { query.contains(it.key) }?.let {
                stateFilter = it.value
                query = query.replace(it.key, " ")
            }
            val tokens = query.split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
            val matches = clubs.filter { c ->
                (stateFilter == null || c.state == stateFilter) &&
                    tokens.all { t -> if (t.length == 2 && t.uppercase() == c.state) true else c.searchText.contains(t) }
            }
            val sorted = if (loc != null) matches.sortedBy { distanceTo(loc, it) } else matches
            return sorted.take(100)
        }
    }
}
