package com.spearotracker.spearogo.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.spearotracker.spearogo.models.TidePhase
import com.spearotracker.spearogo.ui.AppUiState
import com.spearotracker.spearogo.ui.theme.Brand
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TidesPage(uiState: AppUiState) {
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
            text = "TIDES",
            style = Brand.Typography.sectionHeader,
            color = Brand.Colors.textSecondary,
            modifier = Modifier.padding(bottom = Brand.Spacing.item)
        )

        val tide = uiState.tideData

        if (tide != null) {
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(bottom = Brand.Spacing.section)
            ) {
                // HIGH
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "HIGH", style = Brand.Typography.itemLabel, color = Brand.Colors.textSecondary)
                    Text(
                        text = timeFormat.format(Date(tide.nextHighTime)),
                        style = Brand.Typography.timeDisplay,
                        color = Brand.Colors.textPrimary
                    )
                    Text(
                        text = "%.1fm".format(tide.nextHighHeight),
                        style = Brand.Typography.caption,
                        color = Brand.Colors.secondary
                    )
                }

                // Divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(44.dp)
                        .background(Brand.Colors.textSecondary.copy(alpha = Brand.Opacity.borderLine))
                )

                // LOW
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "LOW", style = Brand.Typography.itemLabel, color = Brand.Colors.textSecondary)
                    Text(
                        text = timeFormat.format(Date(tide.nextLowTime)),
                        style = Brand.Typography.timeDisplay,
                        color = Brand.Colors.textPrimary
                    )
                    Text(
                        text = "%.1fm".format(tide.nextLowHeight),
                        style = Brand.Typography.caption,
                        color = Brand.Colors.secondary
                    )
                }
            }

            // Tide direction card
            Row(
                modifier = Modifier
                    .background(
                        Brand.Colors.textPrimary.copy(alpha = Brand.Opacity.cardFill),
                        RoundedCornerShape(Brand.Radius.card)
                    )
                    .border(
                        1.dp,
                        Brand.Colors.textPrimary.copy(alpha = Brand.Opacity.borderLine),
                        RoundedCornerShape(Brand.Radius.card)
                    )
                    .padding(Brand.Spacing.item),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Brand.Spacing.item)
            ) {
                Text(
                    text = if (tide.isRising) "\u2191" else "\u2193",
                    style = Brand.Typography.dataValue,
                    color = if (tide.isRising) Brand.Colors.maybe else Brand.Colors.primary
                )
                Column {
                    Text(
                        text = if (tide.isRising) "Incoming" else "Outgoing",
                        style = Brand.Typography.personalityCopy,
                        color = Brand.Colors.textPrimary
                    )
                    Text(
                        text = tide.phase.label,
                        style = Brand.Typography.caption,
                        color = Brand.Colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(Brand.Spacing.item))

            // Current height
            Row(horizontalArrangement = Arrangement.spacedBy(Brand.Spacing.micro)) {
                Text(text = "Now", style = Brand.Typography.itemLabel, color = Brand.Colors.textSecondary)
                Text(text = "%.1fm".format(tide.currentHeight), style = Brand.Typography.caption, color = Brand.Colors.secondary)
            }
        } else {
            Text(
                text = "Loading tides...",
                style = Brand.Typography.caption,
                color = Brand.Colors.textSecondary
            )
        }
    }
    }
}
