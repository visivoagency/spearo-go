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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Text
import androidx.wear.input.RemoteInputIntentHelper
import com.spearotracker.spearogo.models.GeocodedPlace
import com.spearotracker.spearogo.models.SavedLocation
import com.spearotracker.spearogo.ui.AppViewModel
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
    BackHandler { onDismiss() }

    val saved by viewModel.savedLocations.collectAsState()
    val search by viewModel.search.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Brand.Spacing.page),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = "SPOTS",
                style = Brand.Typography.sectionHeader,
                color = Brand.Colors.textSecondary,
                modifier = Modifier.padding(top = 28.dp, bottom = Brand.Spacing.item)
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
                    onDelete = { viewModel.deleteLocation(location) }
                )
            }
            item {
                Text(
                    text = "Long-press a spot to remove it",
                    style = Brand.Typography.caption,
                    color = Brand.Colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = Brand.Spacing.item)
                )
            }
        }

        item { ActionRow(icon = "ℹ️", label = "About") { onAbout() } }
        item { Spacer(modifier = Modifier.height(24.dp)) }
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
private fun SavedRow(location: SavedLocation, onSelect: () -> Unit, onDelete: () -> Unit) {
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
            .combinedClickable(onClick = onSelect, onLongClick = onDelete)
            .padding(horizontal = Brand.Spacing.item, vertical = 10.dp)
    ) {
        Text(
            text = if (location.isActive) "●" else "○",
            style = Brand.Typography.caption,
            color = if (location.isActive) Brand.Colors.secondary else Brand.Colors.textSecondary
        )
        Text(
            text = location.name,
            style = Brand.Typography.personalityCopy,
            color = Brand.Colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
