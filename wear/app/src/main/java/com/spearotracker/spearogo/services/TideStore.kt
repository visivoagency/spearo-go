package com.spearotracker.spearogo.services

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.spearotracker.spearogo.models.TideData
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent store for tide predictions.
 *
 * The app's CacheService is deliberately memory-only — "watch storage is
 * precious" — but a week of tides has to survive a relaunch, or every cold
 * start costs a metered lookup. The backend returns seven days for the price of
 * one, so keeping them is most of the point.
 *
 * Small on disk: at most 7 days across a handful of saved spots, and anything
 * whose day has passed is dropped on the next write.
 */
@Singleton
class TideStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("tides", Context.MODE_PRIVATE)
    private val gson = Gson()

    private data class Entry(
        val savedAt: Long,
        val days: List<TideData> = emptyList(),
        /** The backend said this coordinate has no sea. A settled fact. */
        val noCoverage: Boolean = false
    )

    private fun key(latitude: Double, longitude: Double) =
        "%.2f,%.2f".format(latitude, longitude)

    private fun read(latitude: Double, longitude: Double): Entry? {
        val raw = prefs.getString(key(latitude, longitude), null) ?: return null
        return try {
            gson.fromJson(raw, object : TypeToken<Entry>() {}.type)
        } catch (e: Exception) {
            null
        }
    }

    private fun isFresh(entry: Entry) =
        System.currentTimeMillis() - entry.savedAt < CACHE_MILLIS

    /** A cached day, still within its life. */
    fun fresh(latitude: Double, longitude: Double, date: String): TideData? {
        val entry = read(latitude, longitude) ?: return null
        if (entry.noCoverage || !isFresh(entry)) return null
        return entry.days.firstOrNull { it.date == date }
    }

    /**
     * A cached day past its life, for when the network is gone. Better than
     * nothing, and flagged in the UI — but never invented.
     */
    fun stale(latitude: Double, longitude: Double, date: String): TideData? {
        val entry = read(latitude, longitude) ?: return null
        if (entry.noCoverage) return null
        return entry.days.firstOrNull { it.date == date }
    }

    /** Whether this coordinate is already known to have no sea. */
    fun knownWithoutCoverage(latitude: Double, longitude: Double): Boolean {
        val entry = read(latitude, longitude) ?: return false
        return entry.noCoverage && isFresh(entry)
    }

    fun save(latitude: Double, longitude: Double, days: List<TideData>) {
        val today = java.time.LocalDate.now().toString()
        val kept = days.filter { it.date >= today }
        prefs.edit()
            .putString(key(latitude, longitude), gson.toJson(Entry(System.currentTimeMillis(), kept)))
            .apply()
    }

    /**
     * Remembered so a landlocked spot stops asking. Held for the same day as
     * real data — long enough to stop the traffic, short enough that a backend
     * gaining coverage is picked up.
     */
    fun rememberNoCoverage(latitude: Double, longitude: Double) {
        prefs.edit()
            .putString(
                key(latitude, longitude),
                gson.toJson(Entry(System.currentTimeMillis(), emptyList(), noCoverage = true))
            )
            .apply()
    }

    private companion object {
        /** Predictions for a given day do not change. */
        const val CACHE_MILLIS = 24 * 60 * 60 * 1000L
    }
}
