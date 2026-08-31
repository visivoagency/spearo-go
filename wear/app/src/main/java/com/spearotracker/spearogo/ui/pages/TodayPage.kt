package com.spearotracker.spearogo.ui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
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
 * Today's weather, over two screenfuls scrolled vertically.
 *
 * Everything fitted on one screen only by being clipped: the header ran under
 * the status area and the daylight row fell off the bottom of the round
 * display. Each half is now sized to the viewport, so a scroll lands squarely
 * on a complete screen rather than halfway through a row.
 *
 * Every value is nullable and rendered as "—" when the API does not report it.
 * Nothing on this page is substituted or estimated.
 */
@Composable
fun TodayPage(uiState: AppUiState) {
    val scrollState = rememberScrollState()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val weather = uiState.weatherData

    ScreenScaffold(scrollState = scrollState) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // ── Screen 1: what it is doing right now ──────────────────────
            Screenful(screenHeight) {
                Text(
                    text = "TODAY",
                    style = Brand.Typography.sectionHeader,
                    color = Brand.Colors.textSecondary,
                    modifier = Modifier.padding(bottom = Brand.Spacing.item)
                )

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
                        style = Brand.Typography.verdictLabel,
                        color = Brand.Colors.textPrimary,
                        modifier = Modifier.padding(vertical = Brand.Spacing.item)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
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

            // ── Screen 2: rain, cloud, and daylight ───────────────────────
            if (weather != null) {
                Screenful(screenHeight) {
                    Text(
                        text = "SKY",
                        style = Brand.Typography.sectionHeader,
                        color = Brand.Colors.textSecondary,
                        modifier = Modifier.padding(bottom = Brand.Spacing.section)
                    )

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
                    Text(
                        text = if (sunrise != null && sunset != null) {
                            "☀️ ${timeFormat.format(Date(sunrise))}   " +
                                "🌙 ${timeFormat.format(Date(sunset))}"
                        } else {
                            // The Sun genuinely does not rise or set on some
                            // days at high latitude. Say so rather than blank.
                            "No sunrise or sunset today"
                        },
                        style = Brand.Typography.caption,
                        color = Brand.Colors.textSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.padding(top = Brand.Spacing.section)
                    )
                }
            }
        }
    }
}

/**
 * One viewport-height panel, so a scroll comes to rest on a whole screen
 * instead of partway through a row.
 */
@Composable
private fun Screenful(height: androidx.compose.ui.unit.Dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = Brand.Spacing.page),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content
    )
}
