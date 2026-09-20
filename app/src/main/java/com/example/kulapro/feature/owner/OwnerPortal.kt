package com.example.kulapro.feature.owner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kulapro.data.model.Restaurant
import com.example.kulapro.feature.ownership.OwnershipReviewScreen
import com.example.kulapro.ui.components.ErrorState
import com.example.kulapro.ui.components.MessageBanner

/**
 * The restaurant side of the app.
 *
 * A bar across the bottom rather than buttons on a dashboard, because these are the places
 * someone running a service moves between all evening, not a menu they visit once. Sections
 * bring their own chrome, so this owns only the bar and what sits behind it.
 */
@Composable
fun OwnerPortal(
    onSwitchToCustomer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OwnerPortalViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            MessageBanner(message = state.message, onDismiss = viewModel::dismissMessage)
        },
        bottomBar = {
            NavigationBar {
                state.sections.forEach { section ->
                    NavigationBarItem(
                        selected = section == state.section && !state.isEditingListing,
                        onClick = { viewModel.selectSection(section) },
                        icon = { Icon(section.icon, contentDescription = null) },
                        label = { Text(section.label) },
                    )
                }
                // The way out, always present. A reviewer who hosts no restaurant sees only
                // one section, and without this the system back button was the only exit.
                NavigationBarItem(
                    selected = false,
                    onClick = onSwitchToCustomer,
                    icon = {
                        Icon(Icons.Outlined.TravelExplore, contentDescription = null)
                    },
                    label = { Text("Booking") },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            PortalSection(state = state, viewModel = viewModel)
        }
    }
}

@Composable
private fun PortalSection(state: OwnerPortalUiState, viewModel: OwnerPortalViewModel) {
    // Menu, setup and requests each read their own data and do not need the restaurant
    // document this screen is still fetching, so they are not held up behind it.
    when (state.section) {
        AdminSection.MENU -> {
            MenuManagementScreen()
            return
        }

        AdminSection.SETUP -> {
            CapacitySetupScreen()
            return
        }

        AdminSection.REQUESTS -> {
            OwnershipReviewScreen()
            return
        }

        AdminSection.BOOKINGS -> {
            OwnerBookingsScreen()
            return
        }

        AdminSection.TODAY -> Unit
    }

    val restaurant = state.restaurant
    if (!state.hostsARestaurant) {
        // A reviewer who manages no restaurant has nothing to show here, and the bar will
        // not have offered this section in the first place.
        return
    }
    when {
        state.loadError != null && restaurant == null ->
            ErrorState(message = state.loadError, onRetry = viewModel::refresh)

        restaurant == null -> CircularProgressIndicator()

        state.isEditingListing -> ListingEditor(
            state = state,
            restaurant = restaurant,
            onSave = viewModel::saveListing,
            onBack = viewModel::stopEditingListing,
        )

        else -> TodayScaffold(
            state = state,
            restaurant = restaurant,
            onEditRestaurant = viewModel::editListing,
            onViewBookings = { viewModel.selectSection(AdminSection.BOOKINGS) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodayScaffold(
    state: OwnerPortalUiState,
    restaurant: Restaurant,
    onEditRestaurant: () -> Unit,
    onViewBookings: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Hosting") },
            )
        },
    ) { padding ->
        OwnerDashboard(
            restaurant = restaurant,
            todaysBookings = state.todaysBookings,
            onEditRestaurant = onEditRestaurant,
            onViewBookings = onViewBookings,
            modifier = Modifier.padding(padding),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListingEditor(
    state: OwnerPortalUiState,
    restaurant: Restaurant,
    onSave: (Restaurant) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Edit listing") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        EditRestaurantScreen(
            restaurant = restaurant,
            saving = state.isSaving,
            onSave = onSave,
            modifier = Modifier.padding(padding),
        )
    }
}
