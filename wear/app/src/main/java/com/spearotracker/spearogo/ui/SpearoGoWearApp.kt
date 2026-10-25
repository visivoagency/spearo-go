package com.spearotracker.spearogo.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.HorizontalPagerScaffold
import com.spearotracker.spearogo.ui.pages.*
import kotlinx.coroutines.launch

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
                viewModel = viewModel,
                onRequestPermission = onRequestPermission,
                onSkip = {
                    hasCompletedOnboarding = true
                    viewModel.refresh()
                }
            )
        } else {
            // Settings (spots, sign-in, About) is the last page, after Fish Activity
            // (Kevin, 25 Oct). Long-pressing the verdict jumps there, the same gesture
            // watchOS uses; Back from Settings returns to the verdict.
            var showInfo by remember { mutableStateOf(false) }
            val pagerState = rememberPagerState(pageCount = { 7 })
            val scope = rememberCoroutineScope()

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
                            onInfoTap = { scope.launch { pagerState.animateScrollToPage(6) } },
                            viewModel = viewModel
                        )
                        1 -> TodayPage(uiState = uiState)
                        2 -> ConditionsPage(uiState = uiState)
                        3 -> WaterPage(uiState = uiState)
                        4 -> TidesPage(uiState = uiState)
                        5 -> FishActivityPage(uiState = uiState)
                        6 -> SpotsPage(
                            viewModel = viewModel,
                            onAbout = { showInfo = true },
                            onDismiss = { scope.launch { pagerState.animateScrollToPage(0) } }
                        )
                    }
                }
            }
        }
    }
}
