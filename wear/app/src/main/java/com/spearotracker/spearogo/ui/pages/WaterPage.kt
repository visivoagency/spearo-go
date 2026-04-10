package com.spearotracker.spearogo.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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

@Composable
fun WaterPage(uiState: AppUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Brand.Spacing.page),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "WATER",
            style = Brand.Typography.sectionHeader,
            color = Brand.Colors.textSecondary,
            modifier = Modifier.padding(bottom = Brand.Spacing.item)
        )

        val marine = uiState.marineData

        if (marine != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.padding(bottom = Brand.Spacing.section)
            ) {
                ConditionItem(icon = "temp", label = "Temp", value = "%.0f".format(marine.seaSurfaceTemp), unit = "\u00B0C")
                ConditionItem(icon = "eye", label = "Viz", value = vizLabel(marine.waveHeight), unit = "")
            }

            // Wetsuit tip
            Box(
                modifier = Modifier
                    .background(
                        Brand.Colors.secondary.copy(alpha = 0.08f),
                        RoundedCornerShape(Brand.Radius.chip)
                    )
                    .border(
                        1.dp,
                        Brand.Colors.secondary.copy(alpha = 0.15f),
                        RoundedCornerShape(Brand.Radius.chip)
                    )
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(
                    text = wetsuitTip(marine.seaSurfaceTemp),
                    style = Brand.Typography.caption,
                    color = Brand.Colors.secondary,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(bottom = Brand.Spacing.section)) {
                ConditionItemSkeleton()
                ConditionItemSkeleton()
            }
        }
    }
}

private fun vizLabel(waveHeight: Double): String = when {
    waveHeight < 0.5 -> "Great"
    waveHeight < 1.0 -> "Good"
    waveHeight < 1.5 -> "Fair"
    else -> "Poor"
}

private fun wetsuitTip(temp: Double): String = when {
    temp < 15 -> "Cold. 7mm + gloves."
    temp < 20 -> "Chilly. 5mm wetsuit."
    temp < 25 -> "Comfortable. 3mm."
    else -> "Warm. 1-2mm or skin."
}
