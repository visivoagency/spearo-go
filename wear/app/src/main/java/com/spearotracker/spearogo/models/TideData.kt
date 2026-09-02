package com.spearotracker.spearogo.models

enum class TidePhase(val label: String) {
    SLACK("Slack"),
    FLOOD("Flood"),
    EBB("Ebb")
}

enum class TideType { HIGH, LOW }

data class TideEvent(
    val timeSeconds: Long,   // epoch seconds, UTC
    val type: TideType,
    val height: Double       // metres above chart datum
)

data class TideHeight(
    val timeSeconds: Long,
    val height: Double
)

/**
 * A day of real tide predictions from the `tidesGo` backend.
 *
 * Holds only what the server sent. Everything time-dependent — which tide is
 * next, whether the water is rising, the height right now — is derived here at
 * read time, because a week of this is cached and "next" changes by the minute.
 *
 * There is no synthetic fallback. The previous implementation invented a curve
 * anchored to the Unix epoch and got Lagos, Portugal exactly inverted: it showed
 * a low at 04:49 where the gauge reads a high at 04:50. Wrong tide times are
 * worse than none, because a diver cannot tell them apart.
 */
data class TideData(
    val date: String,                 // yyyy-MM-dd, station-local
    val events: List<TideEvent> = emptyList(),
    val heights: List<TideHeight> = emptyList(),
    val stationName: String? = null,
    /** "gauge" for a real tide station, "model" for the ocean grid estimate. */
    val provenance: String? = null,
    val utcOffsetSeconds: Int = 0,
    val tidalRange: String = "Normal",
    /** Served from an expired cache because the lookup failed. */
    val isStale: Boolean = false,
    val fetchedAt: Long = System.currentTimeMillis()
) {
    /** A grid estimate is materially weaker in the inlets these users dive. */
    val isModelEstimate: Boolean get() = provenance == "model"

    /**
     * The moment to render, shifted into the tide station's own local time.
     *
     * Format the result in UTC. A diver in Germany reading Portuguese tides
     * must see the time the Portuguese tide table prints, not that instant
     * translated into German wall clock — the watch was showing 00:50 for a low
     * that happens at 23:50 in Lagos. Same defect class as the one that started
     * all this.
     */
    fun stationLocalMillis(event: TideEvent): Long =
        (event.timeSeconds + utcOffsetSeconds) * 1000L

    // Derived state takes the moment as a parameter so it can be tested. A week
    // of predictions is cached and "next" changes by the minute, so none of this
    // can be precomputed server-side.

    fun nextEvent(now: Long = nowSeconds()): TideEvent? =
        events.firstOrNull { it.timeSeconds > now }

    fun nextHigh(now: Long = nowSeconds()): TideEvent? =
        events.firstOrNull { it.timeSeconds > now && it.type == TideType.HIGH }

    fun nextLow(now: Long = nowSeconds()): TideEvent? =
        events.firstOrNull { it.timeSeconds > now && it.type == TideType.LOW }

    /** Rising when the next turn is a high. */
    fun isRising(now: Long = nowSeconds()): Boolean = nextEvent(now)?.type == TideType.HIGH

    /** Linear interpolation between the two hourly readings either side of now. */
    fun currentHeight(now: Long = nowSeconds()): Double? {
        for (i in 0 until heights.size - 1) {
            val a = heights[i]
            val b = heights[i + 1]
            if (a.timeSeconds <= now && b.timeSeconds > now) {
                val span = (b.timeSeconds - a.timeSeconds).toDouble()
                if (span <= 0) return a.height
                return a.height + (b.height - a.height) * ((now - a.timeSeconds) / span)
            }
        }
        return null
    }

    /**
     * Slack water is the half hour either side of a turn, when the flow stills —
     * the window divers actually care about. Previously derived from a
     * fabricated curve's amplitude; now measured against real turn times.
     */
    fun phase(now: Long = nowSeconds()): TidePhase {
        val nearestTurn = events.minByOrNull { kotlin.math.abs(it.timeSeconds - now) }
        val minutesToTurn = nearestTurn?.let { kotlin.math.abs(it.timeSeconds - now) / 60 }
        return when {
            minutesToTurn != null && minutesToTurn <= 30 -> TidePhase.SLACK
            isRising(now) -> TidePhase.FLOOD
            else -> TidePhase.EBB
        }
    }

    private fun nowSeconds() = System.currentTimeMillis() / 1000
}
