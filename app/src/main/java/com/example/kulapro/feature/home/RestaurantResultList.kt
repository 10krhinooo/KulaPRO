package com.example.kulapro.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kulapro.data.model.Restaurant
import com.example.kulapro.ui.components.AnimatedListItem
import com.example.kulapro.ui.components.EmptyState
import com.example.kulapro.ui.components.ListEntryAnimator
import com.example.kulapro.ui.components.RestaurantCard
import com.example.kulapro.ui.components.RestaurantCardSkeleton

/**
 * The list of restaurants, wherever it is being shown.
 *
 * Shared by Home and by search rather than written twice. Two copies would drift, and the
 * one that drifted would be whichever was edited second.
 */
@Composable
fun RestaurantResultList(
    state: HomeUiState,
    entryAnimator: ListEntryAnimator,
    onOpenRestaurant: (String) -> Unit,
    onToggleFavourite: (Restaurant) -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Int = DEFAULT_BOTTOM_PADDING,
) {
    when {
        // Skeletons matching the real card, so the layout does not jump on load.
        state.isLoading -> LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(PLACEHOLDER_COUNT) { RestaurantCardSkeleton() }
        }

        state.isEmpty -> EmptyState(
            title = "No restaurants yet",
            description = "Once restaurants join KulaPro they will show up here.",
            icon = Icons.Outlined.Restaurant,
            modifier = modifier,
        )

        // A filtered-to-nothing list needs different words from an empty one. Telling
        // someone there are no restaurants when they have just asked for cheap Ethiopian
        // food at midnight is answering a question they did not ask.
        state.isFilteredToNothing -> EmptyState(
            title = "Nothing matches that",
            description = if (state.favouritesOnly && state.favouriteIds.isEmpty()) {
                "You have not kept any restaurants yet. Tap the heart on one to start."
            } else {
                "Try a wider search, or clear the filters to see everywhere again."
            },
            icon = Icons.Outlined.SearchOff,
            actionLabel = "Clear filters",
            onAction = onClearFilters,
            modifier = modifier,
        )

        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = bottomPadding.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Keyed so a row scrolled out and back is the same row, not a new one rebuilt
            // from scratch.
            itemsIndexed(
                items = state.visible,
                key = { _, restaurant -> restaurant.id },
            ) { index, restaurant ->
                AnimatedListItem(index = index, key = restaurant.id, animator = entryAnimator) {
                    RestaurantCard(
                        restaurant = restaurant,
                        onClick = { onOpenRestaurant(restaurant.id) },
                        // Withheld from a guest, who has nowhere to keep a restaurant yet.
                        isFavourite = state.isFavourite(restaurant.id)
                            .takeIf { state.isSignedIn },
                        onToggleFavourite = { onToggleFavourite(restaurant) }
                            .takeIf { state.isSignedIn },
                    )
                }
            }
        }
    }
}

private const val PLACEHOLDER_COUNT = 3

/** Clear of the scanner's floating button, so the last card is never hidden behind it. */
private const val DEFAULT_BOTTOM_PADDING = 96
