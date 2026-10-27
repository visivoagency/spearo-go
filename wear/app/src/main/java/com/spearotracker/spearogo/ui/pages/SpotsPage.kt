package com.spearotracker.spearogo.ui.pages

import android.app.Activity
import android.app.RemoteInput
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import com.spearotracker.spearogo.sync.SyncState
import com.spearotracker.spearogo.ui.SignInUiState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import androidx.wear.compose.material3.Text
import androidx.wear.input.RemoteInputIntentHelper
import com.spearotracker.spearogo.models.GeocodedPlace
import com.spearotracker.spearogo.models.SavedLocation
import com.spearotracker.spearogo.ui.AppViewModel
import com.spearotracker.spearogo.ui.components.ScrollingList
import com.spearotracker.spearogo.ui.theme.Brand

private const val QUERY_KEY = "spot_query"

/**
 * Dive spots: search for a place, save it, switch between saved ones.
 *
 * Until this existed a Wear user could only get conditions for where they were
 * standing — the Room entity, the DAO and every ViewModel function were already
 * here, with nothing calling them. It also made the verdict page's advice,
 * "Save a dive spot on the coast", actionable.
 *
 * Text entry goes through the system input activity via RemoteInput, which
 * provides voice dictation, keyboard and handwriting. Voice is how people
 * actually enter text on a watch.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpotsPage(
    viewModel: AppViewModel,
    onAbout: () -> Unit,
    onDismiss: () -> Unit
) {
    val accountPrompt by viewModel.accountPrompt.collectAsState()
    if (accountPrompt) {
        AccountPrompt(onSignIn = { viewModel.acceptAccountPrompt() }, onNotNow = { viewModel.dismissAccountPrompt() })
        return
    }

    val saved by viewModel.savedLocations.collectAsState()
    val syncState by viewModel.syncState.collectAsState()

    // Long-press a saved spot: its detail page (Kevin, 25 Oct). Looked up by id, so a spot that a
    // sync removes meanwhile closes the page instead of showing a stale copy.
    var detailId by remember { mutableStateOf<String?>(null) }
    val detail = detailId?.let { id -> saved.firstOrNull { it.id == id } }
    if (detail != null) {
        SpotDetail(
            location = detail,
            canDelete = !(detail.origin == "VISION" && syncState !is SyncState.SignedOut),
            signedOut = syncState is SyncState.SignedOut,
            onUse = { viewModel.setActiveLocation(detail); detailId = null },
            onDelete = { viewModel.deleteLocation(detail); detailId = null },
            onClose = { detailId = null }
        )
        return
    }

    BackHandler { onDismiss() }

    val search by viewModel.search.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val signIn by viewModel.signIn.collectAsState()
    val accountName by viewModel.accountName.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val text = RemoteInput.getResultsFromIntent(result.data)
            ?.getCharSequence(QUERY_KEY)?.toString()
        if (!text.isNullOrBlank()) viewModel.searchPlaces(text)
    }

    fun promptForPlace() {
        val intent: Intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putRemoteInputsExtra(
            intent,
            listOf(RemoteInput.Builder(QUERY_KEY).setLabel("Search a place").build())
        )
        launcher.launch(intent)
    }

    ScrollingList {
        item {
            Text(
                text = "SETTINGS",
                style = Brand.Typography.sectionHeader,
                color = Brand.Colors.textSecondary,
                modifier = Modifier.padding(bottom = Brand.Spacing.item)
            )
        }

        item { ActionRow(icon = "🔍", label = "Search a place") { promptForPlace() } }

        // Search outcome. "No places found" and "Search failed" are different
        // things and are never collapsed into one another.
        when {
            search.isSearching -> item { Hint("Searching…") }
            search.failed -> item { Hint("Couldn't search. Tap to try again.") { promptForPlace() } }
            search.hasSearched && search.results.isEmpty() ->
                item { Hint("No places found for \"${search.query}\"") }
            else -> Unit
        }

        items(search.results) { place ->
            PlaceRow(place) { viewModel.saveAndActivate(place) }
        }

        item {
            ActionRow(
                icon = "📍",
                label = "Use my location",
                selected = uiState.locationLabel != null && saved.none { it.isActive }
            ) { viewModel.setActiveLocation(null) }
        }

        if (saved.isNotEmpty()) {
            item {
                Text(
                    text = "SAVED",
                    style = Brand.Typography.sectionHeader,
                    color = Brand.Colors.textSecondary,
                    modifier = Modifier.padding(top = Brand.Spacing.section, bottom = Brand.Spacing.micro)
                )
            }
            items(saved) { location ->
                SavedRow(
                    location = location,
                    onSelect = { viewModel.setActiveLocation(location) },
                    onDetails = { detailId = location.id }
                )
            }
            item {
                Text(
                    text = "Long-press a spot for details",
                    style = Brand.Typography.caption,
                    color = Brand.Colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = Brand.Spacing.item)
                )
            }
        }

        syncRows(
            syncState = syncState,
            signIn = signIn,
            accountName = accountName,
            onSignIn = { viewModel.signInWithGoogle() },
            onCancelSignIn = { viewModel.cancelSignIn() },
            onSync = { viewModel.syncSpots() },
            onSignOut = { viewModel.signOut() }
        )

        item { ActionRow(icon = "ℹ️", label = "About") { onAbout() } }
    }
}

@Composable
private fun ActionRow(
    icon: String,
    label: String,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Brand.Spacing.item),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Brand.Spacing.micro)
            .background(
                Brand.Colors.textPrimary.copy(
                    alpha = if (selected) 0.14f else Brand.Opacity.cardFill
                ),
                RoundedCornerShape(Brand.Radius.card)
            )
            .clickable { onClick() }
            .padding(horizontal = Brand.Spacing.item, vertical = 10.dp)
    ) {
        Text(text = icon, style = Brand.Typography.caption)
        Text(
            text = label,
            style = Brand.Typography.personalityCopy,
            color = Brand.Colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PlaceRow(place: GeocodedPlace, onSelect: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Brand.Spacing.micro)
            .background(
                Brand.Colors.secondary.copy(alpha = 0.12f),
                RoundedCornerShape(Brand.Radius.card)
            )
            .clickable { onSelect() }
            .padding(horizontal = Brand.Spacing.item, vertical = 8.dp)
    ) {
        Text(
            text = place.name,
            style = Brand.Typography.personalityCopy,
            color = Brand.Colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        // Region and country are the whole point: "Lagos" matches four places,
        // and the Portuguese one is fourth.
        Text(
            text = place.label,
            style = Brand.Typography.caption,
            color = Brand.Colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SavedRow(location: SavedLocation, onSelect: () -> Unit, onDetails: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Brand.Spacing.item),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Brand.Spacing.micro)
            .background(
                Brand.Colors.textPrimary.copy(
                    alpha = if (location.isActive) 0.14f else Brand.Opacity.cardFill
                ),
                RoundedCornerShape(Brand.Radius.card)
            )
            .combinedClickable(onClick = onSelect, onLongClick = onDetails)
            .padding(horizontal = Brand.Spacing.item, vertical = 10.dp)
    ) {
        Text(
            text = if (location.isActive) "●" else "○",
            style = Brand.Typography.caption,
            color = if (location.isActive) Brand.Colors.secondary else Brand.Colors.textSecondary
        )
        Column {
            Text(
                text = location.name,
                style = Brand.Typography.personalityCopy,
                color = Brand.Colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (location.origin == "VISION") {
                Text(
                    text = "Saved in Vision",
                    style = Brand.Typography.caption,
                    color = Brand.Colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * A saved spot's details, from what the watch keeps: name, position, date saved and where it came
 * from. Use it, or delete it behind a confirmation. Signed in, a Vision-owned spot is read-only
 * (Vision owns it); signed out it can be removed from the watch. Twin of SpotDetailView in Swift.
 */
@Composable
private fun SpotDetail(
    location: SavedLocation,
    canDelete: Boolean,
    signedOut: Boolean,
    onUse: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit
) {
    var confirming by remember(location.id) { mutableStateOf(false) }
    BackHandler { if (confirming) confirming = false else onClose() }

    ScrollingList {
        item {
            Text(
                text = location.name,
                style = Brand.Typography.personalityCopy,
                color = Brand.Colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = Brand.Spacing.micro)
            )
        }
        item { Hint(spotCoordinates(location.latitude, location.longitude)) }
        item { Hint("Saved ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(location.createdAt))}") }
        item { Hint(spotSource(location.origin)) }

        if (confirming) {
            item {
                Text(
                    text = "Delete ${location.name}?",
                    style = Brand.Typography.personalityCopy,
                    color = Brand.Colors.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = Brand.Spacing.item)
                )
            }
            item {
                TwoLineRow(
                    title = "Yes, delete",
                    subtitle = if (signedOut) "Removes it from this watch" else "Removes it here and from your Spearo account",
                    onClick = onDelete
                )
            }
            item { TwoLineRow(title = "Cancel", subtitle = "Keep this spot", onClick = { confirming = false }) }
        } else {
            if (location.isActive) {
                item { Hint("Showing now") }
            } else {
                item { TwoLineRow(title = "Use this spot", subtitle = "Show its conditions", onClick = onUse) }
            }
            if (canDelete) {
                item {
                    TwoLineRow(
                        title = "Delete spot",
                        subtitle = "You'll be asked to confirm",
                        onClick = { confirming = true }
                    )
                }
            } else {
                item { Hint("Saved in Vision. Delete it in Vision.") }
            }
        }
    }
}

/** "38.7223° N, 9.1393° W". */
internal fun spotCoordinates(lat: Double, lon: Double): String =
    String.format(Locale.US, "%.4f° %s, %.4f° %s",
        kotlin.math.abs(lat), if (lat >= 0) "N" else "S",
        kotlin.math.abs(lon), if (lon >= 0) "E" else "W")

/** Where a spot came from, by its SpotOrigin. Twin of spotSource in Swift. */
internal fun spotSource(origin: String): String = when (origin) {
    "VISION" -> "Saved in Vision"
    "ACCOUNT" -> "From your Spearo account"
    else -> "Saved on this watch"
}

/**
 * Shown once, right after the first spot is saved while signed out (see AccountPromptGate).
 * Back counts as "Not now". Either way the Sign in row stays on Settings.
 */
@Composable
private fun AccountPrompt(onSignIn: () -> Unit, onNotNow: () -> Unit) {
    BackHandler { onNotNow() }
    ScrollingList {
        item {
            Text(
                text = "Save your spots to your Spearo account?",
                style = Brand.Typography.personalityCopy,
                color = Brand.Colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = Brand.Spacing.item)
            )
        }
        item {
            Text(
                text = "Sign in to keep them in your account and see them on spearo.pro.",
                style = Brand.Typography.caption,
                color = Brand.Colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = Brand.Spacing.item)
            )
        }
        item {
            TwoLineRow(
                title = "Sign in with Google",
                subtitle = "On your phone",
                onClick = onSignIn
            )
        }
        item {
            TwoLineRow(
                title = "Not now",
                subtitle = "Sign in any time from Settings",
                onClick = onNotNow
            )
        }
    }
}

/** The account rows: sign in when signed out; status and sign out when signed in. */
private fun androidx.compose.foundation.lazy.LazyListScope.syncRows(
    syncState: SyncState,
    signIn: SignInUiState,
    accountName: String?,
    onSignIn: () -> Unit,
    onCancelSignIn: () -> Unit,
    onSync: () -> Unit,
    onSignOut: () -> Unit
) {
    if (syncState is SyncState.SignedOut) {
        item {
            if (signIn is SignInUiState.InProgress) {
                TwoLineRow(
                    title = "Sync with spearo.pro",
                    subtitle = "Check your phone to sign in",
                    action = "Tap to cancel",
                    actionColor = Brand.Colors.textSecondary,
                    onClick = onCancelSignIn
                )
            } else {
                TwoLineRow(
                    title = "Sync with spearo.pro",
                    subtitle = "Sign in to keep your spots in your Spearo account",
                    action = "Sign in with Google",
                    onClick = onSignIn
                )
            }
        }
        when (signIn) {
            SignInUiState.AccountExists -> item {
                Hint("You already have a Spearo account with this email. Sign in the way you did before, or link methods at spearo.pro.")
            }
            is SignInUiState.Failed -> item { Hint(signIn.message) }
            else -> Unit
        }
    } else {
        if (accountName != null) item { Hint("Signed in as $accountName") }
        item {
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(Unit) {
                while (true) { delay(30_000); now = System.currentTimeMillis() }
            }
            TwoLineRow(
                title = "Sync with spearo.pro",
                subtitle = syncStatusText(syncState, now),
                onClick = onSync
            )
        }
        item {
            TwoLineRow(
                title = "Sign out",
                subtitle = "Your spots stay on this watch",
                onClick = onSignOut
            )
        }
    }
}

/** "Synced · just now" / "Synced · N min ago" / "Syncing…" / the failure message. */
internal fun syncStatusText(state: SyncState, now: Long): String = when (state) {
    SyncState.Syncing -> "Syncing…"
    is SyncState.Failed -> state.message
    is SyncState.Synced -> {
        val minutes = ((now - state.at) / 60_000).coerceAtLeast(0)
        when {
            minutes < 1 -> "Synced · just now"
            minutes < 60 -> "Synced · $minutes min ago"
            else -> "Synced · ${minutes / 60} h ago"
        }
    }
    else -> "Tap to sync"
}

@Composable
private fun TwoLineRow(
    title: String,
    subtitle: String,
    action: String? = null,
    actionColor: androidx.compose.ui.graphics.Color = Brand.Colors.secondary,
    onClick: (() -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Brand.Spacing.micro)
            .background(
                Brand.Colors.textPrimary.copy(alpha = Brand.Opacity.cardFill),
                RoundedCornerShape(Brand.Radius.card)
            )
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = Brand.Spacing.item, vertical = 10.dp)
    ) {
        Text(
            text = title,
            style = Brand.Typography.personalityCopy,
            color = Brand.Colors.textPrimary
        )
        Text(
            text = subtitle,
            style = Brand.Typography.caption,
            color = Brand.Colors.textSecondary
        )
        if (action != null) {
            Text(
                text = action,
                style = Brand.Typography.personalityCopy,
                color = actionColor,
                modifier = Modifier.padding(top = Brand.Spacing.micro)
            )
        }
    }
}

@Composable
private fun Hint(text: String, onClick: (() -> Unit)? = null) {
    Text(
        text = text,
        style = Brand.Typography.caption,
        color = Brand.Colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = Brand.Spacing.item)
    )
}
