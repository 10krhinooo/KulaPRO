package com.example.kulapro.feature.reservations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TableRestaurant
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.kulapro.BottomNavigationBar
import com.example.kulapro.Routes
import com.example.kulapro.data.model.Reservation
import com.example.kulapro.ui.components.AnimatedListItem
import com.example.kulapro.ui.components.EmptyState
import com.example.kulapro.ui.components.MessageBanner
import com.example.kulapro.ui.components.RestaurantCardSkeleton
import com.example.kulapro.ui.components.ReviewDialog
import com.example.kulapro.ui.components.SignInPrompt
import com.example.kulapro.ui.components.StatusBadge
import com.example.kulapro.ui.components.UiMessage
import com.example.kulapro.ui.components.rememberListEntryAnimator
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Where the diner is eating next, and where they have eaten.
 *
 * The screen leads with the next booking rather than listing everything at one weight,
 * because "where am I eating next" is the question people open this for, and the old flat
 * list sorted newest first answered it by putting last March above tonight.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReservationsScreen(
    onSignIn: () -> Unit,
    onOpenReservation: (String) -> Unit,
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: ReservationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val entryAnimator = rememberListEntryAnimator()

    state.reviewTarget?.let { target ->
        ReviewDialog(
            restaurantName = target.restaurantName.ifBlank { "this restaurant" },
            submitting = state.isPostingReview,
            onDismiss = viewModel::cancelReview,
            onSubmit = viewModel::submitReview,
        )
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Your reservations") }) },
        bottomBar = {
            BottomNavigationBar(
                navController = navController,
                currentRoute = Routes.RESERVATIONS,
            )
        },
        snackbarHost = {
            MessageBanner(
                message = state.message?.let {
                    if (state.isError) UiMessage.error(it) else UiMessage.success(it)
                },
                onDismiss = viewModel::dismissMessage,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (!state.isSignedIn) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    SignInPrompt(
                        title = "Sign in to see your bookings",
                        description = "Your reservations are tied to your account. " +
                            "Browsing restaurants does not need one.",
                        onSignIn = onSignIn,
                    )
                }
                return@Column
            }

            TabRow(selectedTabIndex = state.tab.ordinal) {
                ReservationTab.entries.forEach { tab ->
                    Tab(
                        selected = tab == state.tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = {
                            Text(
                                text = if (tab == ReservationTab.UPCOMING &&
                                    state.upcoming.isNotEmpty()
                                ) {
                                    "${tab.label} (${state.upcoming.size})"
                                } else {
                                    tab.label
                                },
                            )
                        },
                    )
                }
            }

            when {
                state.isLoading -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(PLACEHOLDER_COUNT) { RestaurantCardSkeleton() }
                }

                state.isEmpty -> EmptyState(
                    title = if (state.tab == ReservationTab.UPCOMING) {
                        "Nothing booked"
                    } else {
                        "Nothing here yet"
                    },
                    description = if (state.tab == ReservationTab.UPCOMING) {
                        "When you book a table it will show up here."
                    } else {
                        "Tables you have been to will appear here once the evening is over."
                    },
                    icon = if (state.tab == ReservationTab.UPCOMING) {
                        Icons.AutoMirrored.Outlined.EventNote
                    } else {
                        Icons.Outlined.History
                    },
                )

                else -> ReservationList(
                    state = state,
                    entryAnimator = entryAnimator,
                    onOpen = onOpenReservation,
                    onCancel = viewModel::cancel,
                    onReview = viewModel::startReview,
                )
            }
        }
    }
}

@Composable
private fun ReservationList(
    state: ReservationsUiState,
    entryAnimator: com.example.kulapro.ui.components.ListEntryAnimator,
    onOpen: (String) -> Unit,
    onCancel: (Reservation) -> Unit,
    onReview: (Reservation) -> Unit,
) {
    val dateFormat = remember {
        SimpleDateFormat("EEE d MMM 'at' HH:mm", Locale.getDefault())
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The next booking is drawn once, large, at the top. Everything after it is a row.
        // A list that gives tonight the same weight as a booking in three weeks makes the
        // reader do the sorting.
        val leading = state.next.takeIf { state.tab == ReservationTab.UPCOMING }
        if (leading != null) {
            item(key = "next-${leading.id}") {
                NextBookingCard(
                    reservation = leading,
                    countdown = state.countdownFor(leading),
                    timeLabel = dateFormat.format(leading.startsAt.toDate()),
                    isBusy = state.busyReservationId == leading.id,
                    canCancel = state.canChange(leading),
                    onOpen = { onOpen(leading.id) },
                    onCancel = { onCancel(leading) },
                )
            }
            if (state.laterUpcoming.isNotEmpty()) {
                item(key = "later-header") {
                    Text(
                        text = "After that",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        val rows = if (leading != null) state.laterUpcoming else state.visible
        itemsIndexed(rows, key = { _, row -> row.id }) { index, reservation ->
            AnimatedListItem(index = index, key = reservation.id, animator = entryAnimator) {
                ReservationRow(
                    reservation = reservation,
                    state = state,
                    timeLabel = dateFormat.format(reservation.startsAt.toDate()),
                    onOpen = { onOpen(reservation.id) },
                    onCancel = { onCancel(reservation) },
                    onReview = { onReview(reservation) },
                )
            }
        }
    }
}

/**
 * The booking the diner is about to keep.
 *
 * Bigger, on the brand colour, and leading with how long there is to go rather than with a
 * date, because "tomorrow" is read instantly and "Thu 21 May" is not.
 */
@Composable
private fun NextBookingCard(
    reservation: Reservation,
    countdown: String,
    timeLabel: String,
    isBusy: Boolean,
    canCancel: Boolean,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        // The whole card opens the booking. The cancel button inside it is its own target,
        // so tapping the card never cancels anything by accident.
        onClick = onOpen,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = countdown,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                StatusBadge(status = reservation.statusEnum)
            }

            Text(
                text = reservation.restaurantName.ifBlank { "Your table" },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            BookingFacts(
                reservation = reservation,
                timeLabel = timeLabel,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            if (canCancel) {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    // Coloured against this card rather than inheriting the accent, which
                    // on the brand container is nearly the same green as the background.
                    TextButton(
                        onClick = onCancel,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        Text("Cancel booking")
                    }
                }
            }
        }
    }
}

@Composable
private fun ReservationRow(
    reservation: Reservation,
    // The state rather than three booleans lifted out of it. Whether a booking can be
    // cancelled or reviewed is one question with one answer, and asking it here keeps the
    // row and the screen from ever disagreeing about it.
    state: ReservationsUiState,
    timeLabel: String,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
    onReview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        onClick = onOpen,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = reservation.restaurantName.ifBlank { "Restaurant" },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(status = reservation.statusEnum)
            }

            BookingFacts(
                reservation = reservation,
                timeLabel = timeLabel,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when {
                state.busyReservationId == reservation.id ->
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))

                state.canChange(reservation) ->
                    TextButton(onClick = onCancel) { Text("Cancel booking") }

                // Only a completed visit can be reviewed, which mirrors what the security
                // rules accept, so the button never leads to a rejected write.
                state.canReview(reservation) ->
                    TextButton(onClick = onReview) { Text("Leave a review") }
            }
        }
    }
}

/** When, how many, and where. The three things a booking actually is. */
@Composable
private fun BookingFacts(
    reservation: Reservation,
    timeLabel: String,
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Fact(icon = Icons.Outlined.Schedule, text = timeLabel, tint = tint)
        Fact(
            icon = Icons.Outlined.People,
            text = "${reservation.partySize} " +
                if (reservation.partySize == 1) "guest" else "guests",
            tint = tint,
        )
        // Only when a table was actually chosen. A booking without one is seated on
        // arrival, and saying "table " would be a lie.
        if (reservation.tableLabel.isNotBlank()) {
            Fact(
                icon = Icons.Outlined.TableRestaurant,
                text = "Table ${reservation.tableLabel}",
                tint = tint,
            )
        }
    }
}

@Composable
private fun Fact(
    icon: ImageVector,
    text: String,
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = tint)
    }
}

private const val PLACEHOLDER_COUNT = 3
