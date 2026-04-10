package com.spearotracker.spearogo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Text
import com.spearotracker.spearogo.ui.theme.Brand

@Composable
fun ConditionItem(icon: String, label: String, value: String, unit: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Brand.Spacing.micro),
        modifier = Modifier.widthIn(min = 60.dp)
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
            color = Brand.Colors.textSecondary
        )

        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = value,
                style = Brand.Typography.dataValue,
                color = Brand.Colors.textPrimary
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
fun ConditionItemSkeleton() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Brand.Spacing.micro),
        modifier = Modifier.widthIn(min = 60.dp)
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
    else -> "\u2022"                // bullet
}
