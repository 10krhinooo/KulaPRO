package com.example.kulapro.feature.owner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kulapro.data.model.Reservation
import com.example.kulapro.data.model.Restaurant
import com.example.kulapro.data.repository.OwnerRepository
import com.example.kulapro.data.repository.OwnerRepositoryFirestore
import com.example.kulapro.data.repository.RestaurantRepository
import com.example.kulapro.data.repository.RestaurantRepositoryFirestore
import com.example.kulapro.data.repository.Result
import com.example.kulapro.ui.components.ErrorState
import com.example.kulapro.ui.components.MessageHost
import com.example.kulapro.ui.components.rememberMessageHostState
import com.google.firebase.Timestamp
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.launch

private enum class OwnerScreen { DASHBOARD, BOOKINGS, EDIT }

/**
 * The restaurant side of the app.
 *
 * Reached through the portal switcher, and only offered to users whose Auth claims name a
 * restaurant, so a diner never sees a door they cannot open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerPortal(
    restaurantId: String,
    onSwitchToCustomer: () -> Unit,
    modifier: Modifier = Modifier,
    restaurantRepository: RestaurantRepository = remember { RestaurantRepositoryFirestore() },
    ownerRepository: OwnerRepository = remember { OwnerRepositoryFirestore() },
) {
    val scope = rememberCoroutineScope()
    val messages = rememberMessageHostState()

    var screen by remember { mutableStateOf(OwnerScreen.DASHBOARD) }
    var restaurant by remember { mutableStateOf<Restaurant?>(null) }
    var bookings by remember { mutableStateOf<List<Reservation>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var reloadToken by remember { mutableStateOf(0) }

    LaunchedEffect(restaurantId, reloadToken) {
        loadError = null
        when (val result = restaurantRepository.restaurant(restaurantId)) {
            is Result.Success -> restaurant = result.data
            is Result.Failure -> loadError = result.message
        }
        val dayStart = startOfToday()
        val dayEnd = Date(dayStart.time + DAY_MILLIS)
        when (
            val result = ownerRepository.bookingsFor(
                restaurantId = restaurantId,
                from = Timestamp(dayStart),
                to = Timestamp(dayEnd),
            )
        ) {
            is Result.Success -> bookings = result.data
            is Result.Failure -> loadError = loadError ?: result.message
        }
    }

    // Bookings brings its own chrome, so it replaces this screen rather than nesting inside
    // it and producing two stacked app bars.
    if (screen == OwnerScreen.BOOKINGS) {
        OwnerBookingsScreen(
            onBack = { screen = OwnerScreen.DASHBOARD },
            modifier = modifier,
        )
        return
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(if (screen == OwnerScreen.EDIT) "Edit listing" else "Hosting") },
                navigationIcon = {
                    if (screen == OwnerScreen.EDIT) {
                        IconButton(onClick = { screen = OwnerScreen.DASHBOARD }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    }
                },
                actions = {
                    PortalSwitcher(
                        current = Portal.OWNER,
                        onSwitch = { onSwitchToCustomer() },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                },
            )
        },
        snackbarHost = { MessageHost(messages) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            val current = restaurant
            when {
                loadError != null && current == null -> ErrorState(
                    message = loadError.orEmpty(),
                    onRetry = { reloadToken++ },
                )

                current == null -> CircularProgressIndicator()

                screen == OwnerScreen.DASHBOARD -> OwnerDashboard(
                    restaurant = current,
                    todaysBookings = bookings,
                    onEditRestaurant = { screen = OwnerScreen.EDIT },
                    onViewBookings = { screen = OwnerScreen.BOOKINGS },
                )

                else -> EditRestaurantScreen(
                    restaurant = current,
                    saving = isSaving,
                    onSave = { updated ->
                        isSaving = true
                        scope.launch {
                            val result = ownerRepository.updateRestaurant(updated)
                            isSaving = false
                            when (result) {
                                is Result.Success -> {
                                    restaurant = updated
                                    screen = OwnerScreen.DASHBOARD
                                    messages.showSuccess("Listing updated")
                                }

                                is Result.Failure ->
                                    messages.showError(result.message)
                            }
                        }
                    },
                )
            }
        }
    }
}

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

private fun startOfToday(): Date = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.time
