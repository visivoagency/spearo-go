package com.spearotracker.spearogo.ui.pages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.spearotracker.spearogo.ui.theme.Brand
import com.spearotracker.spearogo.utils.Constants

@Composable
fun InfoPage(onDismiss: () -> Unit) {
    BackHandler { onDismiss() }

    val scrollState = rememberScrollState()
    ScreenScaffold(scrollState = scrollState) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onDismiss() }
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "PRIVACY",
                style = Brand.Typography.sectionHeader,
                color = Brand.Colors.textSecondary
            )

            Spacer(modifier = Modifier.height(Brand.Spacing.section))

            PolicySection(
                title = "Location Data",
                body = "Your coordinates are sent to Open-Meteo APIs over HTTPS to fetch conditions. Never stored on any server."
            )

            PolicySection(
                title = "Saved Locations",
                body = "Dive spots are stored locally on your watch. Never uploaded or shared."
            )

            PolicySection(
                title = "No Tracking",
                body = "No accounts, no analytics, no ads. Zero personal data collected."
            )

            PolicySection(
                title = "APIs",
                body = "Weather and marine data from Open-Meteo. Tides and solunar calculated on-device."
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
