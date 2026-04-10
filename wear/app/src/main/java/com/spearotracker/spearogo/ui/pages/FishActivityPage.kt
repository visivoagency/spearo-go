package com.spearotracker.spearogo.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Text
import com.spearotracker.spearogo.ui.AppUiState
import com.spearotracker.spearogo.ui.components.ConditionItem
import com.spearotracker.spearogo.ui.components.ConditionItemSkeleton
import com.spearotracker.spearogo.ui.theme.Brand
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun FishActivityPage(uiState: AppUiState) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(Brand.Spacing.page),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "FISH ACTIVITY",
            style = Brand.Typography.sectionHeader,
            color = Brand.Colors.textSecondary,
            modifier = Modifier.padding(bottom = Brand.Spacing.item)
        )

        val sol = uiState.solunarData

        if (sol != null) {
            val moonIcon = moonPhaseName(sol.moonPhase)

            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.padding(bottom = Brand.Spacing.section)
            ) {
                ConditionItem(icon = "moon", label = "Moon", value = "%.0f%%".format(sol.moonIllumination * 100), unit = "")
                ConditionItem(icon = "fish", label = "Rating", value = sol.activityRating, unit = "")
            }

            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

            // Major period
            sol.nextMajorPeriod?.let { major ->
                SolunarPeriodRow(label = "Major", time = timeFormat.format(Date(major)), color = Brand.Colors.go)
            }
            // Minor period
            sol.nextMinorPeriod?.let { minor ->
                SolunarPeriodRow(label = "Minor", time = timeFormat.format(Date(minor)), color = Brand.Colors.maybe)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(bottom = Brand.Spacing.section)) {
                ConditionItemSkeleton()
                ConditionItemSkeleton()
            }
        }
    }
}

@Composable
private fun SolunarPeriodRow(label: String, time: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Brand.Spacing.page, vertical = 4.dp)
            .background(
                Brand.Colors.textPrimary.copy(alpha = Brand.Opacity.cardFill),
                RoundedCornerShape(Brand.Radius.chip)
            )
            .padding(horizontal = Brand.Spacing.page, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label.uppercase(),
            style = Brand.Typography.itemLabel,
            color = color,
            modifier = Modifier.width(40.dp)
        )
        Text(
            text = time,
            style = Brand.Typography.periodTime,
            color = Brand.Colors.textPrimary
        )
        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

private fun moonPhaseName(phase: Double): String = when {
    phase < 0.1 || phase >= 0.9 -> "New Moon"
    phase < 0.25 -> "Waxing Crescent"
    phase < 0.35 -> "First Quarter"
    phase < 0.5 -> "Waxing Gibbous"
    phase < 0.6 -> "Full Moon"
    phase < 0.75 -> "Waning Gibbous"
    phase < 0.9 -> "Last Quarter"
    else -> "Waning Crescent"
}
