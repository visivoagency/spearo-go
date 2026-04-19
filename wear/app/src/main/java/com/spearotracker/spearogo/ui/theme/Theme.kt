package com.spearotracker.spearogo.ui.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

@Composable
fun SpearoGoTheme(content: @Composable () -> Unit) {
    val colorScheme = ColorScheme(
        primary = Brand.Colors.primary,
        secondary = Brand.Colors.secondary,
        background = Brand.Colors.background,
        onBackground = Brand.Colors.textPrimary,
        onPrimary = Brand.Colors.textPrimary,
        onSecondary = Brand.Colors.textPrimary,
        surfaceContainer = Brand.Colors.background,
        onSurface = Brand.Colors.textPrimary
    )

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
