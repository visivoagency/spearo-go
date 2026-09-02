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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import java.util.TimeZone

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
            // UTC, because the value handed to it is already shifted into the
            // station's local time. Using the device zone here is what showed a
            // Lagos 23:50 low as 00:50 on a German wrist.
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(bottom = Brand.Spacing.section)
            ) {
                // HIGH
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "HIGH", style = Brand.Typography.itemLabel, color = Brand.Colors.textSecondary)
                    Text(
                        text = tide.nextHigh()?.let { timeFormat.format(Date(tide.stationLocalMillis(it))) } ?: "—",
                        style = Brand.Typography.timeDisplay,
                        color = Brand.Colors.textPrimary
                    )
                    Text(
                        text = tide.nextHigh()?.let { "%.1fm".format(it.height) } ?: "",
                        style = Brand.Typography.caption,
                        color = Brand.Colors.secondary
                    )
                }

                // Divider
                // Matched to the high/low columns, which are taller now the
                // type is bigger.
                val dividerHeight = (52 * LocalDensity.current.fontScale).dp
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(dividerHeight)
                        .background(Brand.Colors.textSecondary.copy(alpha = Brand.Opacity.borderLine))
                )

                // LOW
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "LOW", style = Brand.Typography.itemLabel, color = Brand.Colors.textSecondary)
                    Text(
                        text = tide.nextLow()?.let { timeFormat.format(Date(tide.stationLocalMillis(it))) } ?: "—",
                        style = Brand.Typography.timeDisplay,
                        color = Brand.Colors.textPrimary
                    )
                    Text(
                        text = tide.nextLow()?.let { "%.1fm".format(it.height) } ?: "",
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
                    text = if (tide.isRising()) "\u2191" else "\u2193",
                    style = Brand.Typography.dataValue,
                    color = if (tide.isRising()) Brand.Colors.maybe else Brand.Colors.primary
                )
                Column {
                    Text(
                        text = if (tide.isRising()) "Incoming" else "Outgoing",
                        style = Brand.Typography.personalityCopy,
                        color = Brand.Colors.textPrimary
                    )
                    Text(
                        text = tide.phase().label,
                        style = Brand.Typography.caption,
                        color = Brand.Colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(Brand.Spacing.item))

            // Current height. Null when the day's hourly readings do not
            // bracket this moment — shown as "—" rather than guessed.
            Row(horizontalArrangement = Arrangement.spacedBy(Brand.Spacing.micro)) {
                Text(text = "Now", style = Brand.Typography.itemLabel, color = Brand.Colors.textSecondary)
                Text(
                    text = tide.currentHeight()?.let { "%.1fm".format(it) } ?: "—",
                    style = Brand.Typography.caption,
                    color = Brand.Colors.secondary
                )
            }

            // Where the numbers came from. A named gauge and an ocean-model
            // estimate must never read as equally trustworthy: the model is
            // materially weaker in the estuaries and inlets these users dive.
            val source = when {
                tide.isStale -> "Saved forecast"
                tide.isModelEstimate -> "Estimated \u00b7 no nearby gauge"
                tide.stationName != null -> tide.stationName
                else -> null
            }
            source?.let {
                Spacer(modifier = Modifier.height(Brand.Spacing.micro))
                Text(
                    text = it,
                    style = Brand.Typography.caption,
                    color = Brand.Colors.textSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        } else if (uiState.isLoading) {
            Text(
                text = "Loading tides...",
                style = Brand.Typography.caption,
                color = Brand.Colors.textSecondary
            )
        } else {
            // Not a loading state. The synthetic tide model this page used to
            // draw was wrong everywhere on earth, so it was removed rather than
            // improved. Real predictions arrive with the tidesGo backend.
            Text(
                text = "No tide data for this spot yet",
                style = Brand.Typography.caption,
                color = Brand.Colors.textSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
    }
}
