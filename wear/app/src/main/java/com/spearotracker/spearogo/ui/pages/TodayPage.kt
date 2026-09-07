package com.spearotracker.spearogo.ui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Text
import com.spearotracker.spearogo.ui.AppUiState
import com.spearotracker.spearogo.ui.components.ConditionItem
import com.spearotracker.spearogo.ui.components.ConditionRow
import com.spearotracker.spearogo.ui.components.ConditionItemSkeleton
import com.spearotracker.spearogo.ui.components.ScrollingPage
import com.spearotracker.spearogo.ui.theme.Brand
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Today's weather, over two vertically paged screens.
 *
 * Everything fitted on one screen only by being clipped: the header ran under
 * the system clock and the daylight row fell off the bottom of the round
 * display. A free scroll did not fix it either, because it comes to rest
 * anywhere. A snapping pager always lands square on a whole screen.
 *
 * Every value is nullable and rendered as "—" when the API does not report it.
 * Nothing on this page is substituted or estimated.
 */
@Composable
fun TodayPage(uiState: AppUiState) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val weather = uiState.weatherData

    VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        when (page) {
            0 -> Screenful {
                Text(
                    text = "TODAY",
                    style = Brand.Typography.sectionHeader,
                    color = Brand.Colors.textSecondary,
                    modifier = Modifier.padding(bottom = Brand.Spacing.item)
                )

                when {
                    weather != null -> {
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

                        ConditionRow {
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
                    }

                    uiState.isLoading -> {
                        ConditionRow {
                            ConditionItemSkeleton()
                            ConditionItemSkeleton()
                        }
                    }

                    else -> {
                        Text(
                            text = "No weather data for this spot",
                            style = Brand.Typography.caption,
                            color = Brand.Colors.textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            1 -> Screenful {
                Text(
                    text = "SKY",
                    style = Brand.Typography.sectionHeader,
                    color = Brand.Colors.textSecondary,
                    modifier = Modifier.padding(bottom = Brand.Spacing.item)
                )

                ConditionRow(modifier = Modifier.padding(bottom = Brand.Spacing.section)) {
                    ConditionItem(
                        icon = "rain",
                        label = "Rain",
                        value = weather?.precipitationChance?.toString() ?: "—",
                        unit = if (weather?.precipitationChance == null) "" else "%"
                    )
                    ConditionItem(
                        icon = "cloud",
                        label = "Cloud",
                        value = weather?.cloudCover?.toString() ?: "—",
                        unit = if (weather?.cloudCover == null) "" else "%"
                    )
                }

                // Daylight uses the same ConditionItem treatment as every other
                // reading, rather than a smaller caption line. The Sun genuinely
                // does not rise or set on some days at high latitude, and "—"
                // says that in the same way a missing swell reading does.
                val sunrise = uiState.solunarData?.sunrise
                val sunset = uiState.solunarData?.sunset
                ConditionRow {
                    ConditionItem(
                        icon = "sun",
                        label = "Rise",
                        value = sunrise?.let { timeFormat.format(Date(it)) } ?: "—",
                        unit = ""
                    )
                    ConditionItem(
                        icon = "moon",
                        label = "Set",
                        value = sunset?.let { timeFormat.format(Date(it)) } ?: "—",
                        unit = ""
                    )
                }
            }
        }
    }
}

/**
 * One full page of the vertical pager.
 *
 * A fixed 30.dp top inset used to clear the system clock. It did not clear the
 * round edge once the text size grew, and the page could not scroll, so a
 * large text size simply lost the daylight row off the bottom. ScrollingPage
 * carries the percentage insets and the scroll; a nested vertical scroll hands
 * the gesture to the pager once it reaches its end.
 */
@Composable
private fun Screenful(content: @Composable ColumnScope.() -> Unit) {
    ScrollingPage(content = content)
}
