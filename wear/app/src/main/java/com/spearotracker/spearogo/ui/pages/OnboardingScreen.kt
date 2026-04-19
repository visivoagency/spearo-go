package com.spearotracker.spearogo.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.foundation.Image
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Text
import com.spearotracker.spearogo.R
import com.spearotracker.spearogo.ui.theme.Brand

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
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.splash_icon),
            contentDescription = "Spearo Go",
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Spearo Go",
            style = Brand.Typography.verdictLabel,
            color = Brand.Colors.textPrimary
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Your dive-day verdict\nat a glance.",
            style = Brand.Typography.personalityCopy,
            color = Brand.Colors.textSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Swipe \u2192",
            style = Brand.Typography.caption,
            color = Brand.Colors.accent
        )
    }
}

@Composable
private fun HowItWorksPage() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp)
    ) {
        Text(
            text = "HOW IT WORKS",
            style = Brand.Typography.sectionHeader,
            color = Brand.Colors.textSecondary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OnboardingBullet("\uD83D\uDCA8", "Wind & weather")
            OnboardingBullet("\uD83C\uDF0A", "Swell & marine")
            OnboardingBullet("\u2195\uFE0F", "Tides")
            OnboardingBullet("\uD83C\uDF19", "Solunar activity")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            VerdictBadge("GO", Brand.Colors.go)
            VerdictBadge("MAYBE", Brand.Colors.maybe)
            VerdictBadge("SKETCHY", Brand.Colors.sketchy)
            VerdictBadge("NO GO", Brand.Colors.noGo)
        }
    }
}

@Composable
private fun LocationPage(onRequestPermission: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 32.dp)
    ) {
        Text(text = "\uD83D\uDCCD", fontSize = 24.sp)

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Location",
            style = Brand.Typography.dataValue,
            color = Brand.Colors.textPrimary
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Needed to fetch conditions\nfor your dive spot.",
            style = Brand.Typography.caption,
            color = Brand.Colors.textSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(
                containerColor = Brand.Colors.primary,
                contentColor = Brand.Colors.textPrimary
            ),
            shape = RoundedCornerShape(Brand.Radius.pill),
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(36.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Let's Go",
                    style = Brand.Typography.scoreNumber,
                    color = Brand.Colors.textPrimary
                )
            }
        }
    }
}

@Composable
private fun OnboardingBullet(icon: String, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, fontSize = 11.sp)
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
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = Brand.Typography.itemLabel.copy(fontSize = 6.sp),
            color = color
        )
    }
}
