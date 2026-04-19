package com.spearotracker.spearogo.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.HorizontalPagerScaffold
import com.spearotracker.spearogo.ui.pages.*

@Composable
fun SpearoGoWearApp(
    viewModel: AppViewModel,
    onRequestPermission: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var hasCompletedOnboarding by remember { mutableStateOf(false) }

    // Skip onboarding if permission is already granted
    LaunchedEffect(uiState.hasLocationPermission) {
        if (uiState.hasLocationPermission) {
            if (!hasCompletedOnboarding) {
                hasCompletedOnboarding = true
            }
            viewModel.refresh()
        }
    }

    AppScaffold {
        if (!hasCompletedOnboarding) {
            OnboardingScreen(
                onRequestPermission = onRequestPermission,
                onSkip = {
                    hasCompletedOnboarding = true
                    viewModel.refresh()
                }
            )
        } else {
            var showInfo by remember { mutableStateOf(false) }
            val pagerState = rememberPagerState(pageCount = { 5 })

            if (showInfo) {
                InfoPage(onDismiss = { showInfo = false })
            } else {
                HorizontalPagerScaffold(
                    pagerState = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> VerdictPage(
                            uiState = uiState,
                            onRefresh = { viewModel.refresh() },
                            onInfoTap = { showInfo = true },
                            viewModel = viewModel
                        )
                        1 -> ConditionsPage(uiState = uiState)
                        2 -> WaterPage(uiState = uiState)
                        3 -> TidesPage(uiState = uiState)
                        4 -> FishActivityPage(uiState = uiState)
                    }
                }
            }
        }
    }
}
