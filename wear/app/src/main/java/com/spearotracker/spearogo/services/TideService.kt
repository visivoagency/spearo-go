package com.spearotracker.spearogo.services

import com.spearotracker.spearogo.models.TideData
import com.spearotracker.spearogo.models.TidePhase
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Singleton
class TideService @Inject constructor() {

    companion object {
        private const val M2_PERIOD = 44712.0    // seconds (~12.42h)
        private const val S2_PERIOD = 43200.0    // seconds (12.00h)
        private const val LUNAR_CYCLE = 2551443.0 // seconds (29.53 days)
        private const val LUNAR_EPOCH = 947182440.0 // Unix: Jan 6 2000 18:14 (new moon)
    }

    fun calculate(latitude: Double, longitude: Double, timeSeconds: Double = System.currentTimeMillis() / 1000.0): TideData {
        val t = timeSeconds

        // Longitude-based phase offset: tidal bulge lags ~0.8h per 15 degrees longitude
        val lonOffset = (longitude / 360.0) * M2_PERIOD

        // Lunar phase [0, 1] - 0 = new moon, 0.5 = full moon
        val lunarPhase = ((t - LUNAR_EPOCH) % LUNAR_CYCLE) / LUNAR_CYCLE

        // Spring/neap scale: peaks at new (0) and full (0.5) moon
        val springScale = 0.6 + 0.4 * cos(2 * PI * 2 * abs(lunarPhase - 0.5) - PI)

        fun height(time: Double): Double {
            val m2 = cos(2 * PI * (time + lonOffset) / M2_PERIOD)
            val s2 = 0.35 * cos(2 * PI * (time + lonOffset) / S2_PERIOD)
            return (m2 + s2) * springScale
        }

        val current = height(t)

        // Find next high and low within 13-hour search window
        fun findNext(start: Double, wantHigh: Boolean, window: Double = 46800.0): Pair<Double, Double> {
            val step = 300.0 // 5-minute resolution
            var best = start + step
            var bestH = height(best)
            var time = start + step * 2
            while (time <= start + window) {
                val h = height(time)
                val prevH = height(time - step)
                val nextH = height(time + step)
                if (if (wantHigh) (h >= prevH && h >= nextH) else (h <= prevH && h <= nextH)) {
                    return Pair(time, h)
                }
                if (if (wantHigh) h > bestH else h < bestH) {
                    best = time
                    bestH = h
                }
                time += step
            }
            return Pair(best, bestH)
        }

        val nextH = findNext(t, wantHigh = true)
        val nextL = findNext(t, wantHigh = false)

        // Rising if heading toward high
        val isRising = height(t + 1800.0) > current

        // Tide phase
        val phase = when {
            abs(current) < 0.2 * springScale -> TidePhase.SLACK
            isRising -> TidePhase.FLOOD
            else -> TidePhase.EBB
        }

        // Scale to a realistic tidal range (0-3m normalised)
        fun scale(h: Double): Double = (h + 1) * 1.5

        return TideData(
            currentHeight = scale(current),
            isRising = isRising,
            phase = phase,
            nextHighTime = (nextH.first * 1000).toLong(),
            nextHighHeight = scale(nextH.second),
            nextLowTime = (nextL.first * 1000).toLong(),
            nextLowHeight = scale(nextL.second)
        )
    }
}
