package com.example.kulapro.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Everything the filter row can do, grouped so the row is not a nine parameter function. */
data class HomeFilterActions(
    val onQueryChange: (String) -> Unit,
    val onToggleCuisine: (String) -> Unit,
    val onTogglePriceBand: (Int) -> Unit,
    val onToggleOpenNow: () -> Unit,
    val onToggleFreeTonight: () -> Unit,
    val onToggleFavourites: () -> Unit,
    val onClearFilters: () -> Unit,
)

/**
 * Search, and the cuts of the list people actually ask for.
 *
 * Chips rather than a filter sheet behind a button. With a dozen restaurants the entire
 * useful filter set fits on screen, and a sheet would hide the one control most likely to
 * explain why the list looks empty.
 */
@Composable
fun HomeSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Search a name, a cuisine or an area") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            // Only once there is something to clear, so the field is not permanently
            // wearing a button that does nothing.
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Clear the search",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeFilterRow(
    state: HomeUiState,
    actions: HomeFilterActions,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The two that change most often lead, because they answer "can I eat there now".
        FilterChip(
            selected = state.openNowOnly,
            onClick = actions.onToggleOpenNow,
            label = { Text("Open now") },
        )
        FilterChip(
            selected = state.freeTonightOnly,
            onClick = actions.onToggleFreeTonight,
            label = { Text("Table tonight") },
        )
        // Offered only to someone with a list to filter to. A guest tapping it would get an
        // empty screen and no explanation.
        if (state.isSignedIn) {
            FilterChip(
                selected = state.favouritesOnly,
                onClick = actions.onToggleFavourites,
                label = { Text("Kept") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
            )
        }
        PRICE_BANDS.forEach { band ->
            FilterChip(
                selected = band in state.priceBands,
                onClick = { actions.onTogglePriceBand(band) },
                label = { Text(priceBandLabel(band)) },
            )
        }
    }
}

/**
 * Cuisines, scrolled sideways.
 *
 * A separate row from the rest because the list is as long as the data is wide, and mixing
 * it into the wrapping row above would push the price bands off the bottom of the screen on
 * a day when somebody adds four new cuisines.
 */
@Composable
fun HomeCuisineRow(
    state: HomeUiState,
    onToggleCuisine: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.availableCuisines.isEmpty()) return

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.availableCuisines, key = { it }) { cuisine ->
            FilterChip(
                selected = cuisine in state.cuisines,
                onClick = { onToggleCuisine(cuisine) },
                label = { Text(cuisine) },
            )
        }
    }
}

/** Shown only while something is filtered, so it never sits there as dead text. */
@Composable
fun ActiveFilterSummary(
    state: HomeUiState,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.hasFilters) return

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "${state.visible.size} of ${state.restaurants.size}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
        TextButton(onClick = onClearFilters) { Text("Clear filters") }
    }
}
