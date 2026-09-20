package com.example.kulapro.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kulapro.ui.components.MessageBanner
import com.example.kulapro.ui.components.UiMessage
import com.example.kulapro.ui.components.rememberListEntryAnimator

/**
 * Search and filtering, on a page of their own.
 *
 * They started out above the list on Home and took most of the screen before a single
 * restaurant appeared, which is the wrong trade: Home's job is to show places to eat, and a
 * control someone uses occasionally should not permanently cost them the thing they came
 * for. Here there is room for the whole filter set, and the field takes focus on arrival so
 * the keyboard is already up.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenRestaurant: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val entryAnimator = rememberListEntryAnimator()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val actions = HomeFilterActions(
        onQueryChange = viewModel::setQuery,
        onToggleCuisine = viewModel::toggleCuisine,
        onTogglePriceBand = viewModel::togglePriceBand,
        onToggleOpenNow = viewModel::toggleOpenNow,
        onToggleFreeTonight = viewModel::toggleFreeTonight,
        onToggleFavourites = viewModel::toggleFavouritesOnly,
        onClearFilters = viewModel::clearFilters,
    )

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            MessageBanner(
                message = state.message?.let(UiMessage::error),
                onDismiss = viewModel::dismissMessage,
            )
        },
        topBar = {
            TopAppBar(
                title = { Text("Find a table") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HomeSearchField(
                    query = state.query,
                    onQueryChange = actions.onQueryChange,
                    modifier = Modifier.focusRequester(focusRequester),
                )
                HomeFilterRow(state = state, actions = actions)
                HomeCuisineRow(state = state, onToggleCuisine = actions.onToggleCuisine)
                ActiveFilterSummary(state = state, onClearFilters = actions.onClearFilters)
            }

            RestaurantResultList(
                state = state,
                entryAnimator = entryAnimator,
                onOpenRestaurant = onOpenRestaurant,
                onToggleFavourite = viewModel::toggleFavourite,
                onClearFilters = actions.onClearFilters,
                // No floating button on this screen, so the list runs to the bottom.
                bottomPadding = 16,
            )
        }
    }
}
