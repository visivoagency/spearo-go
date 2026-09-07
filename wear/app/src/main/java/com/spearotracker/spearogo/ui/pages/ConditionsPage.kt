package com.spearotracker.spearogo.ui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.Text
import com.spearotracker.spearogo.ui.AppUiState
import com.spearotracker.spearogo.ui.components.ConditionItem
import com.spearotracker.spearogo.ui.components.ConditionRow
import com.spearotracker.spearogo.ui.components.ScrollingPage
import com.spearotracker.spearogo.ui.components.ConditionItemSkeleton
import com.spearotracker.spearogo.ui.theme.Brand
import androidx.compose.ui.text.style.TextAlign

@Composable
fun ConditionsPage(uiState: AppUiState) {
    ScrollingPage {
        Text(
            text = "CONDITIONS",
            style = Brand.Typography.sectionHeader,
            color = Brand.Colors.textSecondary,
            modifier = Modifier.padding(bottom = Brand.Spacing.item)
        )

        val weather = uiState.weatherData
        val marine = uiState.marineData

        // Wind is shown whenever weather is known, even where there is no
        // sea. Requiring marine data here hid the wind at any location the
        // marine API does not cover.
        if (weather != null) {
            ConditionRow(modifier = Modifier.padding(bottom = Brand.Spacing.section)) {
                ConditionItem(icon = "wind", label = "Wind", value = "%.0f".format(weather.windSpeed), unit = "kn")
                ConditionItem(
                    icon = "waves",
                    label = "Swell",
                    value = marine?.let { "%.1f".format(it.waveHeight) } ?: "—",
                    unit = if (marine == null) "" else "m"
                )
            }
            ConditionRow {
                ConditionItem(icon = "direction", label = "Dir", value = compassDirection(weather.windDirection), unit = "")
                ConditionItem(
                    icon = "timer",
                    label = "Period",
                    value = marine?.wavePeriod?.let { "%.0f".format(it) } ?: "—",
                    unit = if (marine?.wavePeriod == null) "" else "s"
                )
            }
            if (marine == null) {
                Text(
                    text = "No swell data for this spot",
                    style = Brand.Typography.caption,
                    color = Brand.Colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Brand.Spacing.item)
                )
            }
        } else {
            ConditionRow(modifier = Modifier.padding(bottom = Brand.Spacing.section)) {
                ConditionItemSkeleton()
                ConditionItemSkeleton()
            }
            ConditionRow {
                ConditionItemSkeleton()
                ConditionItemSkeleton()
            }
        }
    }
}

fun compassDirection(degrees: Double): String {
    val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return dirs[((degrees + 22.5) / 45.0).toInt() % 8]
}
