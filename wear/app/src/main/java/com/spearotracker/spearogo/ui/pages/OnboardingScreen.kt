package com.spearotracker.spearogo.ui.pages

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Text
import com.spearotracker.spearogo.ui.theme.Brand

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onRequestPermission: () -> Unit,
    onSkip: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 3 })

    HorizontalPager(state = pagerState) { page ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brand.Colors.background),
            contentAlignment = Alignment.Center
        ) {
            when (page) {
                0 -> WelcomePage()
                1 -> HowItWorksPage()
                2 -> LocationPage(onRequestPermission = onRequestPermission)
            }
        }
    }
}

@Composable
private fun WelcomePage() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(Brand.Spacing.page)
    ) {
        Text(text = "\uD83D\uDC1F", fontSize = 32.sp)

        Spacer(modifier = Modifier.height(Brand.Spacing.section))

        Text(
            text = "Spearo Go",
            style = Brand.Typography.verdictLabel,
            color = Brand.Colors.textPrimary
        )

        Text(
            text = "Your dive-day verdict\nin one glance.",
            style = Brand.Typography.personalityCopy,
            color = Brand.Colors.textSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Brand.Spacing.section))

        Text(
            text = "Swipe to continue \u2192",
            style = Brand.Typography.caption,
            color = Brand.Colors.textSecondary
        )
    }
}

@Composable
private fun HowItWorksPage() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(Brand.Spacing.page)
    ) {
        Text(
            text = "HOW IT WORKS",
            style = Brand.Typography.sectionHeader,
            color = Brand.Colors.textSecondary,
            modifier = Modifier.padding(bottom = Brand.Spacing.item)
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(Brand.Spacing.item),
            modifier = Modifier.padding(bottom = Brand.Spacing.item)
        ) {
            OnboardingBullet("\uD83D\uDCA8", "Weather & wind")
            OnboardingBullet("\uD83C\uDF0A", "Swell & marine data")
            OnboardingBullet("\u2195\uFE0F", "Tide phases")
            OnboardingBullet("\uD83C\uDF19", "Solunar fish activity")
        }

        Text(
            text = "Combined into one score\nfrom 0 to 10.",
            style = Brand.Typography.personalityCopy,
            color = Brand.Colors.textSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Brand.Spacing.item))

        Row(horizontalArrangement = Arrangement.spacedBy(Brand.Spacing.item)) {
            VerdictBadge("GO", Brand.Colors.go)
            VerdictBadge("MAYBE", Brand.Colors.maybe)
            VerdictBadge("NO GO", Brand.Colors.noGo)
        }
    }
}

@Composable
private fun LocationPage(onRequestPermission: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(Brand.Spacing.page)
    ) {
        Text(text = "\uD83D\uDCCD", fontSize = 28.sp)

        Spacer(modifier = Modifier.height(Brand.Spacing.section))

        Text(
            text = "Location Access",
            style = Brand.Typography.dataValue,
            color = Brand.Colors.textPrimary
        )

        Text(
            text = "Spearo Go needs your location to fetch conditions for your dive spot.",
            style = Brand.Typography.caption,
            color = Brand.Colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Brand.Spacing.item)
        )

        Spacer(modifier = Modifier.height(Brand.Spacing.section))

        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(
                containerColor = Brand.Colors.primary,
                contentColor = Brand.Colors.background
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Let's Go",
                style = Brand.Typography.scoreNumber
            )
        }
    }
}

@Composable
private fun OnboardingBullet(icon: String, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Brand.Spacing.item),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, fontSize = 12.sp)
        Text(
            text = text,
            style = Brand.Typography.personalityCopy,
            color = Brand.Colors.textPrimary
        )
    }
}

@Composable
private fun VerdictBadge(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(Brand.Radius.badge))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = Brand.Typography.itemLabel.copy(fontSize = 7.sp),
            color = color
        )
    }
}
