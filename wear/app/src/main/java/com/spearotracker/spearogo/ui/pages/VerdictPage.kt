package com.spearotracker.spearogo.ui.pages

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.spearotracker.spearogo.models.Verdict
import com.spearotracker.spearogo.ui.AppUiState
import com.spearotracker.spearogo.ui.AppViewModel
import com.spearotracker.spearogo.ui.theme.Brand
import com.spearotracker.spearogo.utils.PersonalityCopy

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VerdictPage(
    uiState: AppUiState,
    onRefresh: () -> Unit,
    onInfoTap: () -> Unit = {},
    viewModel: AppViewModel
) {
    val scrollState = rememberScrollState()
    ScreenScaffold(scrollState = scrollState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 32.dp)
            .combinedClickable(
                onClick = { onRefresh() },
                onLongClick = { onInfoTap() }
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            uiState.isLoading -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        colors = androidx.wear.compose.material3.ProgressIndicatorDefaults.colors(
                            indicatorColor = Brand.Colors.primary
                        )
                    )
                    Spacer(modifier = Modifier.height(Brand.Spacing.item))
                    Text(
                        text = PersonalityCopy.loading(),
                        style = Brand.Typography.caption,
                        color = Brand.Colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            uiState.diveScore != null -> {
                val score = uiState.diveScore
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(Brand.Spacing.page)
                ) {
                    Text(
                        text = score.verdict.label,
                        style = Brand.Typography.verdictLabel,
                        color = Brand.Colors.forVerdict(score.verdict)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = PersonalityCopy.message(score.verdict),
                        style = Brand.Typography.personalityCopy,
                        color = Brand.Colors.textPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = Brand.Spacing.item)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    ScoreRing(score = score.composite, verdict = score.verdict)

                    Spacer(modifier = Modifier.height(8.dp))

                    // Stale cache indicator
                    uiState.lastRefreshedLabel?.let { label ->
                        Text(
                            text = label,
                            style = Brand.Typography.caption,
                            color = if (uiState.isStale) Brand.Colors.sketchy else Brand.Colors.textSecondary
                        )
                    }

                    // Location label
                    uiState.locationLabel?.let { label ->
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = label,
                            style = Brand.Typography.caption,
                            color = if (uiState.isUsingFallbackLocation) Brand.Colors.sketchy else Brand.Colors.textSecondary
                        )
                    }
                }
            }

            uiState.error != null -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Couldn't load conditions",
                        style = Brand.Typography.caption,
                        color = Brand.Colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(Brand.Spacing.item))
                    Text(
                        text = "Tap to retry",
                        style = Brand.Typography.caption,
                        color = Brand.Colors.textSecondary
                    )
                }
            }

            else -> {
                Text(
                    text = "Tap to load conditions",
                    style = Brand.Typography.caption,
                    color = Brand.Colors.textSecondary
                )
            }
        }
    }
    }
}

@Composable
fun ScoreRing(score: Double, verdict: Verdict) {
    val animatedProgress by animateFloatAsState(
        targetValue = (score / 10.0).toFloat(),
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "scoreRing"
    )

    val verdictColor = Brand.Colors.forVerdict(verdict)
    val trackColor = Brand.Colors.textSecondary.copy(alpha = Brand.Opacity.ringTrack)
    val ringSize = 58.dp
    val strokeWidthPx = 5.dp

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(ringSize)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidthPx.toPx()
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)

            // Track
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )

            // Progress
            drawArc(
                color = verdictColor,
                startAngle = -90f,
                sweepAngle = animatedProgress * 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }

        Text(
            text = "%.1f".format(score),
            style = Brand.Typography.scoreNumber,
            color = Brand.Colors.textPrimary
        )
    }
}
