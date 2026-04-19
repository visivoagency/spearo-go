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

data class AppUiState(
    val weatherData: WeatherData? = null,
    val marineData: MarineData? = null,
    val tideData: TideData? = null,
    val solunarData: SolunarData? = null,
    val diveScore: DiveScore? = null,
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
    private val locationDao: LocationDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    // Default fallback (San Diego, CA) if GPS unavailable and no saved location
    private val defaultLat = 32.7
    private val defaultLon = -117.2

    private var activeOverrideLat: Double? = null
    private var activeOverrideLon: Double? = null
    private var activeOverrideName: String? = null

    init {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                hasLocationPermission = locationService.hasPermission()
            )
            // Load active saved location
            val active = locationDao.getActiveLocation()
            if (active != null) {
                activeOverrideLat = active.latitude
                activeOverrideLon = active.longitude
                activeOverrideName = active.name
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                // Determine coordinate
                val overrideLat = activeOverrideLat
                val overrideLon = activeOverrideLon
                var lat: Double
                var lon: Double
                var usingFallback = false

                var label: String? = null

                if (overrideLat != null && overrideLon != null) {
                    lat = overrideLat
                    lon = overrideLon
                    label = activeOverrideName
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

                // Fetch marine (with cache, fallback for landlocked)
                val marineData = cacheService.cachedMarine(lat, lon)
                    ?: try {
                        marineService.fetch(lat, lon).also {
                            cacheService.storeMarine(it, lat, lon)
                        }
                    } catch (e: Exception) {
                        // Marine API can fail for landlocked coordinates
                        MarineData(
                            waveHeight = 0.0, wavePeriod = 10.0,
                            waveDirection = 0.0, seaSurfaceTemp = 22.0
                        )
                    }

                val tideData = tideService.calculate(lat, lon)
                val solunarData = solunarService.calculate(lat, lon)
                val score = scoreService.score(weatherData, marineData, tideData, solunarData)

                _uiState.value = AppUiState(
                    weatherData = weatherData,
                    marineData = marineData,
                    tideData = tideData,
                    solunarData = solunarData,
                    diveScore = score,
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
                activeOverrideLat = location.latitude
                activeOverrideLon = location.longitude
                activeOverrideName = location.name
            } else {
                activeOverrideLat = null
                activeOverrideLon = null
                activeOverrideName = null
            }
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
