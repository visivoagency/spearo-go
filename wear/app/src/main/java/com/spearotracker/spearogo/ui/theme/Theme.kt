package com.spearotracker.spearogo.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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

    // Type scales with Settings -> Display -> Font size, bounded here because a
    // watch face is 1.5 inches wide: past roughly 1.3x the verdict ring and the
    // two-column condition rows truncate rather than reflow, which reads as
    // broken rather than as large. Everything below this ceiling is honoured.
    // Mirrors the .dynamicTypeSize ceiling in SpearoGoApp.swift.
    val density = LocalDensity.current
    val bounded = Density(
        density = density.density,
        fontScale = density.fontScale.coerceAtMost(MAX_FONT_SCALE)
    )

    CompositionLocalProvider(LocalDensity provides bounded) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}

private const val MAX_FONT_SCALE = 1.3f
