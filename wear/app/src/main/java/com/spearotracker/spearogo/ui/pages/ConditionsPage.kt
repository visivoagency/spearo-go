package com.spearotracker.spearogo.ui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.spearotracker.spearogo.ui.AppUiState
import com.spearotracker.spearogo.ui.components.ConditionItem
import com.spearotracker.spearogo.ui.components.ConditionItemSkeleton
import com.spearotracker.spearogo.ui.theme.Brand
import androidx.compose.ui.text.style.TextAlign

@Composable
fun ConditionsPage(uiState: AppUiState) {
    val scrollState = rememberScrollState()

    ScreenScaffold(scrollState = scrollState) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(Brand.Spacing.page),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.padding(bottom = Brand.Spacing.section)
                ) {
                    ConditionItem(icon = "wind", label = "Wind", value = "%.0f".format(weather.windSpeed), unit = "kn")
                    ConditionItem(
                        icon = "waves",
                        label = "Swell",
                        value = marine?.let { "%.1f".format(it.waveHeight) } ?: "—",
                        unit = if (marine == null) "" else "m"
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(bottom = Brand.Spacing.section)) {
                    ConditionItemSkeleton()
                    ConditionItemSkeleton()
                }
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    ConditionItemSkeleton()
                    ConditionItemSkeleton()
                }
            }
        }
    }
}

fun compassDirection(degrees: Double): String {
    val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return dirs[((degrees + 22.5) / 45.0).toInt() % 8]
}
