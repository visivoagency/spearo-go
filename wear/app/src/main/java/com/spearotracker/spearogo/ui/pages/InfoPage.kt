package com.spearotracker.spearogo.ui.pages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.Text
import com.spearotracker.spearogo.ui.components.ScrollingPage
import com.spearotracker.spearogo.ui.theme.Brand
import com.spearotracker.spearogo.utils.Constants

@Composable
fun InfoPage(onDismiss: () -> Unit) {
    BackHandler { onDismiss() }

    ScrollingPage(modifier = Modifier.clickable { onDismiss() }) {
        Text(
            text = "PRIVACY",
            style = Brand.Typography.sectionHeader,
            color = Brand.Colors.textSecondary
        )

        Spacer(modifier = Modifier.height(Brand.Spacing.section))

        PolicySection(
            title = "Location Data",
            body = "Your spot's coordinates are sent over HTTPS to Open-Meteo for weather and sea state, and to Spearo's tide service, which passes them to NOAA or WorldTides. The tide service rounds the position to about 1km and keeps that point 24 hours so the same spot is not looked up twice. It also keeps a one-way hash of your IP address for a short time to limit abuse. Neither is linked to you."
        )

        PolicySection(
            title = "Saved Locations",
            body = "Dive spots are stored on your watch. If you sign in, they are also kept in your Spearo account so you can see them at spearo.pro. They are private and never shared."
        )

        PolicySection(
            title = "No Tracking",
            body = "No ads, no analytics. An account is optional; if you sign in, your email and saved spots are linked to it."
        )

        PolicySection(
            title = "APIs",
            body = "Weather and marine data from Open-Meteo. Solunar calculated on-device. Tides from NOAA or WorldTides via Spearo's tide service."
        )

        Spacer(modifier = Modifier.height(Brand.Spacing.section))

        Text(
            text = "v${Constants.App.VERSION} \u00b7 \u00a9 2026 Visivo Agency",
            style = Brand.Typography.caption,
            color = Brand.Colors.textSecondary
        )

        Spacer(modifier = Modifier.height(Brand.Spacing.item))

        Text(
            text = "Tap to close",
            style = Brand.Typography.caption,
            color = Brand.Colors.accent
        )
    }
}

@Composable
private fun PolicySection(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Brand.Spacing.section),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = title,
            style = Brand.Typography.itemLabel,
            color = Brand.Colors.primary
        )

        Spacer(modifier = Modifier.height(Brand.Spacing.micro))

        Text(
            text = body,
            style = Brand.Typography.caption,
            color = Brand.Colors.textPrimary
        )
    }
}
