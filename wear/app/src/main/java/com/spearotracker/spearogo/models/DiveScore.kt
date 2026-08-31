package com.spearotracker.spearogo.models

import androidx.compose.ui.graphics.Color
import com.spearotracker.spearogo.ui.theme.Brand
import com.spearotracker.spearogo.utils.Constants
import kotlin.math.roundToInt

enum class Verdict(val label: String) {
    GO("GO"),
    MAYBE("MAYBE"),
    SKETCHY("SKETCHY"),
    NO_GO("NO GO");

    val color: Color
        get() = Brand.Colors.forVerdict(this)
}

data class DiveScore(
    val composite: Double,     // 0-10
    val weatherScore: Double,  // 0-10
    val marineScore: Double?,  // 0-10, null when the location has no marine data
    val tideScore: Double?,    // 0-10, null when tides are unavailable
    val solunarScore: Double   // 0-10
) {
    /** Signals that could not be measured, for the UI to name. */
    val missingSignals: List<String>
        get() = buildList {
            if (marineScore == null) add("swell")
            if (tideScore == null) add("tides")
        }

    val isPartial: Boolean get() = missingSignals.isNotEmpty()

    val verdict: Verdict
        get() = when {
            composite >= 8.0 -> Verdict.GO
            composite >= 6.0 -> Verdict.MAYBE
            composite >= 4.0 -> Verdict.SKETCHY
            else -> Verdict.NO_GO
        }

    companion object {
        // Weighted composite: Weather 30%, Marine 30%, Tides 15%, Solunar 25%.
        //
        // A missing signal is dropped and the remaining weights renormalised,
        // rather than being scored as an average one. Substituting a
        // placeholder would let absent data move the verdict - which is exactly
        // what a 0m, 22C "neutral" marine default used to do, inflating the
        // score for any location the marine API does not cover.
        fun calculate(weather: Double, marine: Double?, tides: Double?, solunar: Double): DiveScore {
            var weighted = 0.0
            var totalWeight = 0.0

            fun include(value: Double?, weight: Double) {
                if (value == null) return
                weighted += value * weight
                totalWeight += weight
            }

            include(weather, Constants.Weights.WEATHER)
            include(marine, Constants.Weights.MARINE)
            include(tides, Constants.Weights.TIDES)
            include(solunar, Constants.Weights.SOLUNAR)

            val raw = if (totalWeight > 0) weighted / totalWeight else 0.0
            return DiveScore(
                composite = (raw * 10).roundToInt() / 10.0,
                weatherScore = weather,
                marineScore = marine,
                tideScore = tides,
                solunarScore = solunar
            )
        }
    }
}
