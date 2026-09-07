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

    // The type scale is in sp and follows Settings -> Display -> Text size
    // without a ceiling. A 1.3x cap lived here from 2026-08-31 to 2026-09-07
    // and Play rejected the build under "Wear font size": the guideline is
    // that the app conforms to the size the user chose, and that nothing is
    // cut off when they choose a large one. Layouts have to reflow and scroll
    // instead (see ScrollingPage); capping the scale is not a fix.
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
