package com.example.kulapro.pages

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.rounded.Search
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
import com.example.kulapro.BottomNavigationBar
import com.example.kulapro.Routes
import com.example.kulapro.feature.home.HomeViewModel
import com.example.kulapro.feature.home.RestaurantResultList
import com.example.kulapro.ui.components.MessageBanner
import com.example.kulapro.ui.components.UiMessage
import com.example.kulapro.ui.components.rememberListEntryAnimator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePage(
    navController: NavController,
    onOpenRestaurant: (restaurantId: String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
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
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        // The tab bar belongs to this screen rather than to the navigation scaffold, so it
        // slides away with Home instead of staying put while another destination covers it.
        bottomBar = {
            BottomNavigationBar(navController = navController, currentRoute = Routes.HOME)
        },
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
            // Left aligned rather than centred, which is what gives the wordmark room to
            // sit at full width beside the search button.
            TopAppBar(
                title = { Wordmark() },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                // Search alone. Settings, About and the way into the restaurant side all
                // live on Profile now: they are about the account and the app rather than
                // about finding somewhere to eat, and four controls crowded a bar whose job
                // is to get out of the way of the list.
                actions = {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Rounded.Search, contentDescription = "Search restaurants")
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

            RestaurantResultList(
                state = state,
                entryAnimator = entryAnimator,
                onOpenRestaurant = onOpenRestaurant,
                onToggleFavourite = viewModel::toggleFavourite,
                onClearFilters = viewModel::clearFilters,
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
