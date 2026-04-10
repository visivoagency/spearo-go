package com.spearotracker.spearogo.services

import com.spearotracker.spearogo.models.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Singleton
class ScoreService @Inject constructor() {

    fun score(weather: WeatherData, marine: MarineData, tide: TideData, solunar: SolunarData): DiveScore {
        val w = weatherScore(weather)
        val m = marineScore(marine)
        val t = tideScore(tide)
        val s = solunarScore(solunar)
        return DiveScore.calculate(weather = w, marine = m, tides = t, solunar = s)
    }

    private fun weatherScore(d: WeatherData): Double {
        var score = 10.0
        // Wind penalty
        score -= when {
            d.windSpeed < 10 -> 0.0
            d.windSpeed < 15 -> 1.0
            d.windSpeed < 20 -> 3.0
            d.windSpeed < 25 -> 5.0
            else -> 8.0
        }
        // Gust penalty
        if (d.windGusts > d.windSpeed + 10) score -= 1
        // Visibility bonus/penalty
        if (d.visibility < 5) score -= 2
        if (d.visibility > 15) score += 0.5
        return max(0.0, min(10.0, score))
    }

    private fun marineScore(d: MarineData): Double {
        var score = 10.0
        // Wave height penalty
        score -= when {
            d.waveHeight < 0.5 -> 0.0
            d.waveHeight < 1.0 -> 1.0
            d.waveHeight < 1.5 -> 2.5
            d.waveHeight < 2.0 -> 4.0
            d.waveHeight < 2.5 -> 6.0
            else -> 9.0
        }
        // Long-period swell is more manageable
        if (d.wavePeriod > 14) score += 0.5
        if (d.wavePeriod < 6) score -= 1
        // Water temp: optimal 18-28
        if (d.seaSurfaceTemp < 12) score -= 1
        if (d.seaSurfaceTemp > 30) score -= 0.5
        return max(0.0, min(10.0, score))
    }

    private fun tideScore(d: TideData): Double = when (d.phase) {
        TidePhase.SLACK -> 9.0
        TidePhase.FLOOD -> 7.5
        TidePhase.EBB -> 6.0
    }

    private fun solunarScore(d: SolunarData): Double {
        var score = 5.0
        // Moon phase bonus
        val phaseFromNewOrFull = abs(d.moonPhase - 0.5) * 2
        score += (1 - phaseFromNewOrFull) * 3

        // Proximity to major period
        d.nextMajorPeriod?.let { major ->
            val mins = abs(major - System.currentTimeMillis()) / 60000.0
            when {
                mins < 30 -> score += 2.5
                mins < 60 -> score += 1.5
                mins < 120 -> score += 0.5
            }
        }

        // Proximity to minor period
        d.nextMinorPeriod?.let { minor ->
            val mins = abs(minor - System.currentTimeMillis()) / 60000.0
            if (mins < 30) score += 0.5
        }

        return max(0.0, min(10.0, score))
    }
}
