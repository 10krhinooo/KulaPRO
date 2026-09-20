package com.example.kulapro.pages

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.kulapro.R
import com.example.kulapro.Routes
import com.example.kulapro.feature.home.ActiveFilterSummary
import com.example.kulapro.feature.home.HomeCuisineRow
import com.example.kulapro.feature.home.HomeFilterActions
import com.example.kulapro.feature.home.HomeFilterRow
import com.example.kulapro.feature.home.HomeSearchField
import com.example.kulapro.feature.home.HomeUiState
import com.example.kulapro.feature.home.HomeViewModel
import com.example.kulapro.feature.owner.Portal
import com.example.kulapro.feature.owner.PortalSwitcher
import com.example.kulapro.ui.components.AnimatedListItem
import com.example.kulapro.ui.components.EmptyState
import com.example.kulapro.ui.components.MessageBanner
import com.example.kulapro.ui.components.RestaurantCard
import com.example.kulapro.ui.components.RestaurantCardSkeleton
import com.example.kulapro.ui.components.UiMessage
import com.example.kulapro.ui.components.rememberListEntryAnimator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePage(
    navController: NavController,
    onOpenRestaurant: (restaurantId: String) -> Unit,
    onSwitchToHosting: (String) -> Unit,
    modifier: Modifier = Modifier,
    managedRestaurantId: String? = null,
    isReviewer: Boolean = false,
    /** Null when no scanner is configured, or when nobody is signed in to charge it to. */
    onScanDish: (() -> Unit)? = null,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Hoisted above the list, so a card that scrolls away and back does not replay its
    // entrance and appear to load late.
    val entryAnimator = rememberListEntryAnimator()

    Scaffold(
        modifier = modifier,
        // Insets are owned by the navigation Scaffold; applying them again here would
        // double count the navigation bar height.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            MessageBanner(
                message = state.message?.let(UiMessage::error),
                onDismiss = viewModel::dismissMessage,
            )
        },
        floatingActionButton = {
            // The scanner's front door. On Home rather than buried in a menu, because it is
            // the one thing in the app a diner might open it for without wanting a table.
            onScanDish?.let {
                ExtendedFloatingActionButton(
                    onClick = it,
                    icon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null) },
                    text = { Text("Scan a dish") },
                )
            }
        },
        topBar = {
            // Left aligned rather than centred: with a portal switcher and two icons in the
            // actions slot, a centred title is squeezed into a column narrow enough to wrap
            // the wordmark onto two lines.
            TopAppBar(
                title = { Wordmark() },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                actions = {
                    // Only rendered when the user's claims name a restaurant to manage or a
                    // platform role to exercise, so the control never promises access the
                    // rules would refuse. A reviewer who hosts nothing still has an admin
                    // side: the requests waiting on them.
                    if (managedRestaurantId != null || isReviewer) {
                        PortalSwitcher(
                            current = Portal.CUSTOMER,
                            onSwitch = { onSwitchToHosting(managedRestaurantId.orEmpty()) },
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = { navController.navigate(Routes.ABOUT) }) {
                        Icon(Icons.Rounded.Info, contentDescription = "About")
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
            Greeting(name = state.greetingName)

            val actions = HomeFilterActions(
                onQueryChange = viewModel::setQuery,
                onToggleCuisine = viewModel::toggleCuisine,
                onTogglePriceBand = viewModel::togglePriceBand,
                onToggleOpenNow = viewModel::toggleOpenNow,
                onToggleFreeTonight = viewModel::toggleFreeTonight,
                onToggleFavourites = viewModel::toggleFavouritesOnly,
                onClearFilters = viewModel::clearFilters,
            )

            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HomeSearchField(query = state.query, onQueryChange = actions.onQueryChange)
                HomeFilterRow(state = state, actions = actions)
                HomeCuisineRow(state = state, onToggleCuisine = actions.onToggleCuisine)
                ActiveFilterSummary(state = state, onClearFilters = actions.onClearFilters)
            }

            RestaurantList(
                state = state,
                entryAnimator = entryAnimator,
                onOpenRestaurant = onOpenRestaurant,
                onToggleFavourite = { viewModel.toggleFavourite(it) },
                onClearFilters = actions.onClearFilters,
            )
        }
    }
}

@Composable
private fun Wordmark(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(R.drawable.ic_kula_mark),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(text = "KulaPro", style = MaterialTheme.typography.titleLarge, maxLines = 1)
    }
}

/**
 * Greets the user by name once we know it.
 *
 * A guest is never asked who they are, so the heading falls back to the question rather
 * than to an empty "Hello ,".
 */
@Composable
private fun Greeting(name: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)) {
        if (name.isNotBlank()) {
            Text("Hello $name", style = MaterialTheme.typography.headlineMedium)
            Text("Where are you eating?", style = MaterialTheme.typography.titleMedium)
        } else {
            Text("Where are you eating?", style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
private fun RestaurantList(
    state: HomeUiState,
    entryAnimator: com.example.kulapro.ui.components.ListEntryAnimator,
    onOpenRestaurant: (String) -> Unit,
    onToggleFavourite: (com.example.kulapro.data.model.Restaurant) -> Unit,
    onClearFilters: () -> Unit,
) {
    when {
        // Skeletons matching the real card, so the layout does not jump on load.
        state.isLoading -> LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(PLACEHOLDER_COUNT) { RestaurantCardSkeleton() }
        }

        state.isEmpty -> EmptyState(
            title = "No restaurants yet",
            description = "Once restaurants join KulaPro they will show up here.",
            icon = Icons.Outlined.Restaurant,
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
        )

        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
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
