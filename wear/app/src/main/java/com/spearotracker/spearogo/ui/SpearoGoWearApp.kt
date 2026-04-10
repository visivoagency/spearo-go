package com.spearotracker.spearogo.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.HorizontalPageIndicator
import androidx.wear.compose.material3.PageIndicatorDefaults
import com.spearotracker.spearogo.ui.pages.*
import com.spearotracker.spearogo.ui.theme.Brand

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpearoGoWearApp(
    viewModel: AppViewModel,
    onRequestPermission: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var hasCompletedOnboarding by remember { mutableStateOf(false) }

    // Auto-refresh on first load
    LaunchedEffect(Unit) {
        if (uiState.hasLocationPermission) {
            hasCompletedOnboarding = true
            viewModel.refresh()
        }
    }

    if (!hasCompletedOnboarding && !uiState.hasLocationPermission) {
        OnboardingScreen(
            onRequestPermission = {
                onRequestPermission()
                hasCompletedOnboarding = true
            },
            onSkip = {
                hasCompletedOnboarding = true
                viewModel.refresh()
            }
        )
    } else {
        val pageCount = 5
        val pagerState = rememberPagerState(pageCount = { pageCount })

        androidx.compose.foundation.layout.Box {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
            ) { page ->
                when (page) {
                    0 -> VerdictPage(
                        uiState = uiState,
                        onRefresh = { viewModel.refresh() },
                        viewModel = viewModel
                    )
                    1 -> ConditionsPage(uiState = uiState)
                    2 -> WaterPage(uiState = uiState)
                    3 -> TidesPage(uiState = uiState)
                    4 -> FishActivityPage(uiState = uiState)
                }
            }

            HorizontalPageIndicator(
                pageIndicatorState = PageIndicatorDefaults.pageIndicatorState(
                    pageCount = pageCount,
                    selectedPage = { pagerState.currentPage }
                )
            )
        }
    }
}
