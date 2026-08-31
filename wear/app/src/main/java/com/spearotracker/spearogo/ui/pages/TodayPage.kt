package com.spearotracker.spearogo.ui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.spearotracker.spearogo.ui.AppUiState
import com.spearotracker.spearogo.ui.components.ConditionItem
import com.spearotracker.spearogo.ui.components.ConditionItemSkeleton
import com.spearotracker.spearogo.ui.theme.Brand
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Today's weather: air temperature, conditions, and daylight.
 *
 * Every value here is nullable and rendered as "—" when the API does not report
 * it. Nothing on this page is substituted or estimated.
 */
@Composable
fun TodayPage(uiState: AppUiState) {
    val scrollState = rememberScrollState()
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    ScreenScaffold(scrollState = scrollState) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = Brand.Spacing.page, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "TODAY",
                style = Brand.Typography.sectionHeader,
                color = Brand.Colors.textSecondary,
                modifier = Modifier.padding(bottom = Brand.Spacing.item)
            )

            val weather = uiState.weatherData

            if (weather != null) {
                weather.conditionLabel?.let { condition ->
                    Text(
                        text = condition,
                        style = Brand.Typography.caption,
                        color = Brand.Colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }

                Text(
                    text = weather.airTemp?.let { "%.0f°C".format(it) } ?: "—",
                    style = Brand.Typography.dataValue,
                    color = Brand.Colors.textPrimary,
                    modifier = Modifier.padding(vertical = 2.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.padding(bottom = Brand.Spacing.micro)
                ) {
                    ConditionItem(
                        icon = "temp",
                        label = "High",
                        value = weather.tempMax?.let { "%.0f".format(it) } ?: "—",
                        unit = if (weather.tempMax == null) "" else "°"
                    )
                    ConditionItem(
                        icon = "temp",
                        label = "Low",
                        value = weather.tempMin?.let { "%.0f".format(it) } ?: "—",
                        unit = if (weather.tempMin == null) "" else "°"
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    ConditionItem(
                        icon = "rain",
                        label = "Rain",
                        value = weather.precipitationChance?.toString() ?: "—",
                        unit = if (weather.precipitationChance == null) "" else "%"
                    )
                    ConditionItem(
                        icon = "cloud",
                        label = "Cloud",
                        value = weather.cloudCover?.toString() ?: "—",
                        unit = if (weather.cloudCover == null) "" else "%"
                    )
                }

                val solunar = uiState.solunarData
                val sunrise = solunar?.sunrise
                val sunset = solunar?.sunset
                if (sunrise != null && sunset != null) {
                    Text(
                        text = "\u2600\uFE0F ${timeFormat.format(Date(sunrise))}   \uD83C\uDF19 ${timeFormat.format(Date(sunset))}",
                        style = Brand.Typography.caption,
                        color = Brand.Colors.textSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.padding(top = Brand.Spacing.item)
                    )
                }
            } else if (uiState.isLoading) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    ConditionItemSkeleton()
                    ConditionItemSkeleton()
                }
            } else {
                Text(
                    text = "No weather data for this spot",
                    style = Brand.Typography.caption,
                    color = Brand.Colors.textSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
