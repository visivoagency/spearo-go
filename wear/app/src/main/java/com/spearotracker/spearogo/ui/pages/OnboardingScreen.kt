package com.spearotracker.spearogo.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
import com.spearotracker.spearogo.sync.SyncState
import com.spearotracker.spearogo.ui.AppViewModel
import com.spearotracker.spearogo.ui.SignInUiState
import com.spearotracker.spearogo.ui.components.ScrollingPage
import com.spearotracker.spearogo.ui.theme.Brand
import com.spearotracker.spearogo.utils.Constants
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    viewModel: AppViewModel,
    onRequestPermission: () -> Unit,
    onSkip: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 5 })
    val scope = rememberCoroutineScope()

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
                2 -> SafetyPage()
                3 -> SignInPage(
                    viewModel = viewModel,
                    onNotNow = { scope.launch { pagerState.animateScrollToPage(4) } }
                )
                4 -> LocationPage(onRequestPermission = onRequestPermission)
            }
        }
    }
}

@Composable
private fun WelcomePage() {
    ScrollingPage {
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
    ScrollingPage {
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

// Terms §7.2: "A GO verdict does not mean it is safe to dive." The verdict is a
// forecast; this page says so before the first one is shown.
@Composable
private fun SafetyPage() {
    ScrollingPage {
        Text(text = "\u26A0\uFE0F", fontSize = 24.sp)

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Before You Dive",
            style = Brand.Typography.dataValue,
            color = Brand.Colors.textPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = Constants.Legal.DISCLAIMER,
            style = Brand.Typography.caption,
            color = Brand.Colors.textSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = Constants.Legal.AGE_NOTICE,
            style = Brand.Typography.caption,
            color = Brand.Colors.textPrimary,
            textAlign = TextAlign.Center
        )
    }
}

// Sign-in is optional, so this page is skippable. Without it the only way in was a
// long-press on the verdict, which nobody found (Kevin, 25 Oct).
@Composable
private fun SignInPage(viewModel: AppViewModel, onNotNow: () -> Unit) {
    val syncState by viewModel.syncState.collectAsState()
    val signIn by viewModel.signIn.collectAsState()

    ScrollingPage {
        Text(text = "\u2601\uFE0F", fontSize = 24.sp)

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Sync with spearo.pro",
            style = Brand.Typography.dataValue,
            color = Brand.Colors.textPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        val caption = when {
            syncState !is SyncState.SignedOut -> "You're signed in. Swipe \u2192"
            signIn is SignInUiState.InProgress -> "Check your phone to sign in"
            signIn is SignInUiState.AccountExists ->
                "You already have a Spearo account with this email. Sign in the way you did before, or link methods at spearo.pro."
            signIn is SignInUiState.Failed -> (signIn as SignInUiState.Failed).message
            else -> "Optional. Keep your spots in your Spearo account."
        }
        Text(
            text = caption,
            style = Brand.Typography.caption,
            color = Brand.Colors.textSecondary,
            textAlign = TextAlign.Center
        )

        if (syncState is SyncState.SignedOut) {
            Spacer(modifier = Modifier.height(12.dp))

            if (signIn is SignInUiState.InProgress) {
                OnboardingButton("Cancel", Brand.Colors.textPrimary.copy(alpha = Brand.Opacity.cardFill)) { viewModel.cancelSignIn() }
            } else {
                OnboardingButton("Sign in with Google", Brand.Colors.primary) { viewModel.signInWithGoogle() }
                Spacer(modifier = Modifier.height(6.dp))
                OnboardingButton("Not now", Brand.Colors.textPrimary.copy(alpha = Brand.Opacity.cardFill), onClick = onNotNow)
            }
        }
    }
}

@Composable
private fun OnboardingButton(label: String, containerColor: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = Brand.Colors.textPrimary
        ),
        shape = RoundedCornerShape(Brand.Radius.pill),
        modifier = Modifier.fillMaxWidth(0.85f)
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = Brand.Typography.itemLabel,
                color = Brand.Colors.textPrimary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LocationPage(onRequestPermission: () -> Unit) {
    ScrollingPage {
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
