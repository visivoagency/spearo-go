package com.spearotracker.spearogo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Text
import com.spearotracker.spearogo.ui.theme.Brand

/**
 * A row of readings, centred, that shares its width between its items.
 *
 * Items were laid out at their natural width with a fixed gap, which is fine
 * until the text size grows enough for two of them to be wider than the
 * screen; then the row overflowed and the outer digits were cut by the round
 * edge. Each item is now capped at its share of the row and wraps inside it.
 */
@Composable
fun ConditionRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
        content = content
    )
}

@Composable
fun RowScope.ConditionItem(icon: String, label: String, value: String, unit: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Brand.Spacing.micro),
        modifier = Modifier
            .weight(1f, fill = false)
            .widthIn(min = 60.dp)
    ) {
        // Icon represented as text emoji for simplicity on Wear OS
        Text(
            text = iconForType(icon),
            style = Brand.Typography.scoreNumber,
            color = Brand.Colors.primary
        )

        Text(
            text = label.uppercase(),
            style = Brand.Typography.itemLabel,
            color = Brand.Colors.textSecondary,
            textAlign = TextAlign.Center
        )

        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = value,
                style = Brand.Typography.dataValue,
                color = Brand.Colors.textPrimary,
                textAlign = TextAlign.Center
            )
            if (unit.isNotEmpty()) {
                Text(
                    text = unit,
                    style = Brand.Typography.unit,
                    color = Brand.Colors.textSecondary
                )
            }
        }
    }
}

@Composable
fun RowScope.ConditionItemSkeleton() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Brand.Spacing.micro),
        modifier = Modifier
            .weight(1f, fill = false)
            .widthIn(min = 60.dp)
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .background(
                    Brand.Colors.textSecondary.copy(alpha = 0.12f),
                    RoundedCornerShape(Brand.Radius.badge)
                )
        )
        Box(
            modifier = Modifier
                .size(width = 30.dp, height = 8.dp)
                .background(
                    Brand.Colors.textSecondary.copy(alpha = 0.12f),
                    RoundedCornerShape(Brand.Radius.badge)
                )
        )
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 18.dp)
                .background(
                    Brand.Colors.textSecondary.copy(alpha = 0.12f),
                    RoundedCornerShape(Brand.Radius.badge)
                )
        )
    }
}

private fun iconForType(type: String): String = when (type) {
    "wind" -> "\uD83D\uDCA8"       // wind emoji
    "waves" -> "\uD83C\uDF0A"      // wave emoji
    "direction" -> "\u2197\uFE0F"   // arrow
    "timer" -> "\u23F1"             // stopwatch
    "temp" -> "\uD83C\uDF21"        // thermometer
    "eye" -> "\uD83D\uDC41"         // eye
    "moon" -> "\uD83C\uDF19"        // moon
    "fish" -> "\uD83D\uDC1F"        // fish
    "rain" -> "\uD83C\uDF27"        // cloud with rain
    "cloud" -> "\u2601\uFE0F"       // cloud
    "sun" -> "\u2600\uFE0F"         // sun
    else -> "\u2022"                // bullet
}
