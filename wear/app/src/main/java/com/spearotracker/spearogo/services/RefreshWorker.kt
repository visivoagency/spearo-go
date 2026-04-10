package com.spearotracker.spearogo.services

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class RefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val locationService: LocationService,
    private val weatherService: WeatherService,
    private val marineService: MarineService,
    private val tideService: TideService,
    private val solunarService: SolunarService,
    private val scoreService: ScoreService,
    private val cacheService: CacheService,
    private val locationDao: LocationDao
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // Determine coordinate
            val active = locationDao.getActiveLocation()
            val lat: Double
            val lon: Double

            if (active != null) {
                lat = active.latitude
                lon = active.longitude
            } else {
                val location = locationService.getLocation()
                if (location != null) {
                    lat = location.latitude
                    lon = location.longitude
                } else {
                    lat = 32.7  // San Diego fallback
                    lon = -117.2
                }
            }

            // Fetch and cache
            val weather = try {
                weatherService.fetch(lat, lon).also { cacheService.storeWeather(it, lat, lon) }
            } catch (e: Exception) { return Result.retry() }

            val marine = try {
                marineService.fetch(lat, lon).also { cacheService.storeMarine(it, lat, lon) }
            } catch (e: Exception) {
                com.spearotracker.spearogo.models.MarineData(0.0, 10.0, 0.0, 22.0)
            }

            tideService.calculate(lat, lon)
            solunarService.calculate(lat, lon)

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "spearo_go_refresh"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(
                30, TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
