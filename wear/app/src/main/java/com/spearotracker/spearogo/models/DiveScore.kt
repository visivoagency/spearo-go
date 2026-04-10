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
    val marineScore: Double,   // 0-10
    val tideScore: Double,     // 0-10
    val solunarScore: Double   // 0-10
) {
    val verdict: Verdict
        get() = when {
            composite >= 8.0 -> Verdict.GO
            composite >= 6.0 -> Verdict.MAYBE
            composite >= 4.0 -> Verdict.SKETCHY
            else -> Verdict.NO_GO
        }

    companion object {
        fun calculate(weather: Double, marine: Double, tides: Double, solunar: Double): DiveScore {
            val raw = (weather * Constants.Weights.WEATHER) +
                      (marine * Constants.Weights.MARINE) +
                      (tides * Constants.Weights.TIDES) +
                      (solunar * Constants.Weights.SOLUNAR)
            val composite = (raw * 10).roundToInt() / 10.0
            return DiveScore(
                composite = composite,
                weatherScore = weather,
                marineScore = marine,
                tideScore = tides,
                solunarScore = solunar
            )
        }
    }
}
