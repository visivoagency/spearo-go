package com.spearotracker.spearogo.services

import com.spearotracker.spearogo.models.MarineData
import com.spearotracker.spearogo.models.WeatherData
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CacheService @Inject constructor() {

    private data class Entry<T>(val value: T, val expiresAt: Long)

    private val mutex = Mutex()
    private val weatherCache = mutableMapOf<String, Entry<WeatherData>>()
    private val marineCache = mutableMapOf<String, Entry<MarineData>>()

    private val ttl = 1800_000L // 30 minutes in millis

    suspend fun cachedWeather(latitude: Double, longitude: Double): WeatherData? = mutex.withLock {
        val entry = weatherCache[key(latitude, longitude)]
        if (entry != null && entry.expiresAt > System.currentTimeMillis()) entry.value else null
    }

    suspend fun cachedMarine(latitude: Double, longitude: Double): MarineData? = mutex.withLock {
        val entry = marineCache[key(latitude, longitude)]
        if (entry != null && entry.expiresAt > System.currentTimeMillis()) entry.value else null
    }

    suspend fun storeWeather(data: WeatherData, latitude: Double, longitude: Double) = mutex.withLock {
        weatherCache[key(latitude, longitude)] = Entry(data, System.currentTimeMillis() + ttl)
    }

    suspend fun storeMarine(data: MarineData, latitude: Double, longitude: Double) = mutex.withLock {
        marineCache[key(latitude, longitude)] = Entry(data, System.currentTimeMillis() + ttl)
    }

    private fun key(lat: Double, lon: Double): String = "%.2f,%.2f".format(lat, lon)
}
