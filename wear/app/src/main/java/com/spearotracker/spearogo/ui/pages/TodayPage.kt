package com.spearotracker.spearogo.ui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Text
import com.spearotracker.spearogo.ui.AppUiState
import com.spearotracker.spearogo.ui.components.ConditionItem
import com.spearotracker.spearogo.ui.components.ConditionItemSkeleton
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
                    }

                    uiState.isLoading -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
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
                    modifier = Modifier.padding(bottom = Brand.Spacing.section)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
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

                val sunrise = uiState.solunarData?.sunrise
                val sunset = uiState.solunarData?.sunset
                Text(
                    text = if (sunrise != null && sunset != null) {
                        "☀️ ${timeFormat.format(Date(sunrise))}   " +
                            "🌙 ${timeFormat.format(Date(sunset))}"
                    } else {
                        // The Sun genuinely does not rise or set on some days at
                        // high latitude. Say so rather than leave a blank.
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

/**
 * One full page of the vertical pager.
 *
 * The top inset clears the system clock, which sits centred at the very top of
 * every Wear screen and swallowed the section header when content was simply
 * centred in the viewport.
 */
@Composable
private fun Screenful(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Brand.Spacing.page)
            .padding(top = 30.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content
    )
}
