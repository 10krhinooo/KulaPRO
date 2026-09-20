package com.example.kulapro.feature.reservations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TableRestaurant
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.kulapro.data.model.Reservation
import com.example.kulapro.data.model.ReservationStatus
import com.example.kulapro.ui.components.ErrorState
import com.example.kulapro.ui.components.MessageBanner
import com.example.kulapro.ui.components.PrimaryButton
import com.example.kulapro.ui.components.ReviewDialog
import com.example.kulapro.ui.components.SecondaryButton
import com.example.kulapro.ui.components.StatusBadge
import com.example.kulapro.ui.components.UiMessage
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * One booking, in full.
 *
 * The list gives each booking three lines, which is right for scanning and not enough when
 * somebody actually wants to check something: the reference to read out on the phone, the
 * note they left, which table, when it was made. This is where those live, along with the
 * actions that only make sense once you are looking at one booking rather than all of them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReservationDetailScreen(
    reservationId: String,
    onBack: () -> Unit,
    onOpenRestaurant: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReservationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reservation = state.reservations.firstOrNull { it.id == reservationId }

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
        topBar = {
            TopAppBar(
                title = { Text("Booking") },
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
        snackbarHost = {
            MessageBanner(
                message = state.message?.let {
                    if (state.isError) UiMessage.error(it) else UiMessage.success(it)
                },
                onDismiss = viewModel::dismissMessage,
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.isLoading -> CircularProgressIndicator()

                // Cancelling from here removes nothing, so a booking that has genuinely
                // gone is the only way to reach this. Saying so beats an empty screen.
                reservation == null -> ErrorState(
                    message = "That booking is no longer on your list.",
                    onRetry = onBack,
                )

                else -> Detail(
                    reservation = reservation,
                    state = state,
                    onCancel = { viewModel.cancel(reservation) },
                    onReview = { viewModel.startReview(reservation) },
                    onOpenRestaurant = { onOpenRestaurant(reservation.restaurantId) },
                )
            }
        }
    }
}

@Composable
private fun Detail(
    reservation: Reservation,
    state: ReservationsUiState,
    onCancel: () -> Unit,
    onReview: () -> Unit,
    onOpenRestaurant: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dayFormat = remember { SimpleDateFormat("EEEE d MMMM yyyy", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val madeFormat = remember { SimpleDateFormat("d MMM yyyy 'at' HH:mm", Locale.getDefault()) }
    val startsAt = reservation.startsAt.toDate()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = state.countdownFor(reservation),
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
                Text(
                    text = timeFormat.format(startsAt),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = dayFormat.format(startsAt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        DetailRow(
            icon = Icons.Outlined.People,
            label = "Party",
            value = "${reservation.partySize} " +
                if (reservation.partySize == 1) "guest" else "guests",
        )
        DetailRow(
            icon = Icons.Outlined.TableRestaurant,
            label = "Table",
            // A booking without a table is seated on arrival, which is a real answer and
            // better than a blank row or a fabricated table number.
            value = reservation.tableLabel.ifBlank { "Seated on arrival" },
        )
        if (reservation.notes.isNotBlank()) {
            DetailRow(
                icon = Icons.Outlined.Notes,
                label = "Your note to the restaurant",
                value = reservation.notes,
            )
        }

        HorizontalDivider()

        DetailRow(
            icon = Icons.Outlined.ConfirmationNumber,
            label = "Reference",
            // The id, shortened and in capitals. This is what somebody reads down the phone
            // when a restaurant asks which booking they mean, so it has to be short enough
            // to say out loud.
            value = reservation.id.take(REFERENCE_LENGTH).uppercase(),
        )
        DetailRow(
            icon = Icons.Outlined.CalendarMonth,
            label = "Booked",
            value = madeFormat.format(reservation.createdAt.toDate()),
        )
        DetailRow(
            icon = Icons.Outlined.Schedule,
            label = "Status",
            value = statusExplanation(reservation),
        )

        SecondaryButton(
            text = "View restaurant",
            onClick = onOpenRestaurant,
            modifier = Modifier.fillMaxWidth(),
        )

        when {
            state.canChange(reservation) -> PrimaryButton(
                text = "Cancel this booking",
                onClick = onCancel,
                loading = state.busyReservationId == reservation.id,
                loadingText = "Cancelling",
                modifier = Modifier.fillMaxWidth(),
            )

            state.canReview(reservation) -> PrimaryButton(
                text = "Leave a review",
                onClick = onReview,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** What the status means for the diner, rather than the word the database stores. */
private fun statusExplanation(reservation: Reservation): String =
    when (reservation.statusEnum) {
        ReservationStatus.PENDING ->
            "Waiting for the restaurant to confirm"

        ReservationStatus.CONFIRMED ->
            "Confirmed by the restaurant"

        ReservationStatus.SEATED -> "You are at the table"
        ReservationStatus.COMPLETED -> "Finished"
        ReservationStatus.CANCELLED -> "Cancelled"
        ReservationStatus.NO_SHOW -> "Marked as a no-show"
    }

@Composable
private fun DetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** Long enough to be unique among one person's bookings, short enough to say out loud. */
private const val REFERENCE_LENGTH = 6
