package com.spearotracker.spearogo.ui

import android.content.Context
import android.location.Geocoder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spearotracker.spearogo.models.*
import com.spearotracker.spearogo.services.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import com.spearotracker.spearogo.utils.PersonalityCopy
import com.spearotracker.spearogo.services.NoMarineCoverageException
import com.spearotracker.spearogo.services.TideLookup
import com.spearotracker.spearogo.models.GeocodedPlace
import com.spearotracker.spearogo.services.GeocodingService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

data class AppUiState(
    val weatherData: WeatherData? = null,
    val marineData: MarineData? = null,
    val tideData: TideData? = null,
    val solunarData: SolunarData? = null,
    val diveScore: DiveScore? = null,
    // Chosen once per refresh, not per render. These are drawn at random from a
    // pool, and calling that from a composable re-rolled the line on every
    // recomposition - the verdict copy visibly reshuffled while standing still.
    /** This coordinate has no sea: neither marine nor tide data covers it. */
    val hasNoSea: Boolean = false,
    val personalityMessage: String = "",
    val loadingMessage: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val lastRefreshed: Long? = null,
    val isUsingFallbackLocation: Boolean = false,
    val hasLocationPermission: Boolean = false,
    val locationLabel: String? = null
) {
    val lastRefreshedLabel: String?
        get() {
            val last = lastRefreshed ?: return null
            val elapsed = System.currentTimeMillis() - last
            return when {
                elapsed < 60_000 -> "Just now"
                elapsed < 3600_000 -> "${elapsed / 60_000} min ago"
                else -> "Stale"
            }
        }

    val isStale: Boolean
        get() {
            val last = lastRefreshed ?: return false
            return System.currentTimeMillis() - last > 1800_000
        }
}

@HiltViewModel
class AppViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationService: LocationService,
    private val weatherService: WeatherService,
    private val marineService: MarineService,
    private val tideService: TideService,
    private val solunarService: SolunarService,
    private val scoreService: ScoreService,
    private val cacheService: CacheService,
    private val locationDao: LocationDao,
    private val geocodingService: GeocodingService
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    // Default fallback (San Diego, CA) if GPS unavailable and no saved location
    private val defaultLat = 32.7
    private val defaultLon = -117.2

    init {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                hasLocationPermission = locationService.hasPermission()
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                loadingMessage = PersonalityCopy.loading()
            )

            try {
                // Determine coordinate.
                //
                // Read from the database rather than an in-memory copy loaded
                // in init. That copy was populated by a coroutine racing this
                // one, so on a cold start refresh could read it before the load
                // finished and silently fall back to GPS - the saved spot
                // appeared to forget itself every time the app restarted.
                val active = locationDao.getActiveLocation()
                var lat: Double
                var lon: Double
                var usingFallback = false

                var label: String? = null

                if (active != null) {
                    lat = active.latitude
                    lon = active.longitude
                    label = active.name
                } else {
                    val location = locationService.getLocation()
                    if (location != null) {
                        lat = location.latitude
                        lon = location.longitude
                    } else {
                        lat = defaultLat
                        lon = defaultLon
                        usingFallback = true
                    }
                }

                // Resolve location name if we don't have one yet
                if (label == null) {
                    label = resolveLocationName(lat, lon)
                }

                // Fetch weather (with cache)
                val weatherData = cacheService.cachedWeather(lat, lon)
                    ?: weatherService.fetch(lat, lon).also {
                        cacheService.storeWeather(it, lat, lon)
                    }

                // No marine data is reported as no marine data. The previous
                // neutral defaults (0m swell, 22C) were not neutral - they are
                // near-ideal inputs, so a failed lookup INFLATED the verdict.
                // "No sea at this coordinate" and "the lookup failed" are
                // tracked apart, because only the first means this is not a
                // dive spot.
                var marineHasNoSea = false
                val marineData: MarineData? = cacheService.cachedMarine(lat, lon)
                    ?: try {
                        marineService.fetch(lat, lon).also {
                            cacheService.storeMarine(it, lat, lon)
                        }
                    } catch (e: NoMarineCoverageException) {
                        marineHasNoSea = true
                        null
                    } catch (e: Exception) {
                        null
                    }

                // Real predictions, or a reason there are none.
                val tideLookup = try {
                    tideService.fetch(lat, lon)
                } catch (e: Exception) {
                    TideLookup.Unavailable
                }
                val tideData = (tideLookup as? TideLookup.Found)?.data

                // Neither the marine model nor any tide station covers this
                // coordinate: it is not water. Saying GO here, from wind and
                // moon alone, reads as a recommendation to dive 400km inland.
                val noSea = marineHasNoSea && tideLookup is TideLookup.NoCoverage
                val solunarData = solunarService.calculate(lat, lon)
                val score = scoreService.score(weatherData, marineData, tideData, solunarData)
                val personality = PersonalityCopy.message(score.verdict)

                _uiState.value = AppUiState(
                    weatherData = weatherData,
                    marineData = marineData,
                    tideData = tideData,
                    solunarData = solunarData,
                    diveScore = score,
                    personalityMessage = personality,
                    hasNoSea = noSea,
                    isLoading = false,
                    lastRefreshed = System.currentTimeMillis(),
                    isUsingFallbackLocation = usingFallback,
                    hasLocationPermission = locationService.hasPermission(),
                    locationLabel = label
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load conditions"
                )
            }
        }
    }

    fun setActiveLocation(location: SavedLocation?) {
        viewModelScope.launch {
            locationDao.deactivateAll()
            if (location != null) {
                locationDao.activateLocation(location.id)
            }
            // refresh() re-reads the active row, so there is nothing to keep in
            // sync here and no second copy to drift.
            refresh()
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveLocationName(lat: Double, lon: Double): String {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                val city = addr.locality ?: addr.subAdminArea
                val region = addr.adminArea
                when {
                    city != null && region != null -> "$city, $region"
                    city != null -> city
                    region != null -> region
                    else -> "%.1f\u00b0, %.1f\u00b0".format(lat, lon)
                }
            } else {
                "%.1f\u00b0, %.1f\u00b0".format(lat, lon)
            }
        } catch (e: Exception) {
            "%.1f\u00b0, %.1f\u00b0".format(lat, lon)
        }
    }

    /** Saved spots, for the locations screen. */
    val savedLocations = locationDao.getAllLocations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _search = MutableStateFlow(SpotSearchState())
    val search: StateFlow<SpotSearchState> = _search.asStateFlow()

    fun searchPlaces(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _search.value = SpotSearchState(query = query, isSearching = true)
            try {
                val results = geocodingService.search(query)
                _search.value = SpotSearchState(query = query, results = results)
            } catch (e: Exception) {
                // No invented results. The screen says the search failed.
                _search.value = SpotSearchState(query = query, failed = true)
            }
        }
    }

    fun clearSearch() {
        _search.value = SpotSearchState()
    }

    /** Save a searched place and switch to it, which is always what was meant. */
    fun saveAndActivate(place: GeocodedPlace) {
        viewModelScope.launch {
            val location = SavedLocation(
                name = place.savedName,
                latitude = place.latitude,
                longitude = place.longitude
            )
            locationDao.insert(location)
            clearSearch()
            setActiveLocation(location)
        }
    }

    fun addLocation(name: String, latitude: Double, longitude: Double) {
        viewModelScope.launch {
            locationDao.insert(
                SavedLocation(
                    name = name,
                    latitude = latitude,
                    longitude = longitude
                )
            )
        }
    }

    fun deleteLocation(location: SavedLocation) {
        viewModelScope.launch {
            locationDao.delete(location)
        }
    }

    fun updatePermissionState() {
        _uiState.value = _uiState.value.copy(
            hasLocationPermission = locationService.hasPermission()
        )
    }
}

/** What the locations screen shows while and after a place-name search. */
data class SpotSearchState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<GeocodedPlace> = emptyList(),
    /** The lookup failed. Distinct from "no places found", which is `results` empty. */
    val failed: Boolean = false
) {
    val hasSearched: Boolean get() = query.isNotEmpty() && !isSearching
}
