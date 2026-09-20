package com.example.kulapro.feature.owner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kulapro.data.model.Reservation
import com.example.kulapro.data.model.ReservationStatus
import com.example.kulapro.ui.components.AnimatedListItem
import com.example.kulapro.ui.components.EmptyState
import com.example.kulapro.ui.components.MessageBanner
import com.example.kulapro.ui.components.ShimmerBox
import com.example.kulapro.ui.components.StatusBadge
import com.example.kulapro.ui.components.rememberListEntryAnimator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The restaurant's working screen for one service.
 *
 * This is the half of the README nothing in the app used to address. A diner side that
 * books tables is only half of "managing customer flow"; the other half is the person on
 * the floor deciding who is confirmed, who has arrived and who never turned up, and the
 * capacity model being wrong until walk-ins are in it.
 */
@Composable
fun OwnerBookingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OwnerBookingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    OwnerBookingsContent(
        state = state,
        onBack = onBack,
        actions = OwnerBookingActions(
            onSelectDate = viewModel::selectDate,
            onSetFilter = viewModel::setFilter,
            onUpdateStatus = viewModel::updateStatus,
            onAddWalkIn = viewModel::addWalkIn,
            onDismissMessage = viewModel::dismissMessage,
        ),
        modifier = modifier,
    )
}

/** Grouped rather than passed one by one, so a caller cannot wire them up in the wrong order. */
data class OwnerBookingActions(
    val onSelectDate: (Date) -> Unit = {},
    val onSetFilter: (BookingFilter) -> Unit = {},
    val onUpdateStatus: (Reservation, ReservationStatus) -> Unit = { _, _ -> },
    val onAddWalkIn: (Int, Date) -> Unit = { _, _ -> },
    val onDismissMessage: () -> Unit = {},
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun OwnerBookingsContent(
    state: OwnerBookingsUiState,
    onBack: () -> Unit,
    actions: OwnerBookingActions,
    modifier: Modifier = Modifier,
) {
    val dayFormat = remember { SimpleDateFormat("EEE d", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val entryAnimator = rememberListEntryAnimator()
    var addingWalkIn by remember { mutableStateOf(false) }

    if (addingWalkIn) {
        WalkInDialog(
            onDismiss = { addingWalkIn = false },
            onAdd = { size ->
                addingWalkIn = false
                actions.onAddWalkIn(size, Date())
            },
        )
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            MessageBanner(message = state.message, onDismiss = actions.onDismissMessage)
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Bookings")
                        Text(
                            text = state.restaurantName.ifBlank { "Your restaurant" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            // Walk-ins are most of a busy night, so adding one is the primary action here
            // rather than something buried in a menu.
            ExtendedFloatingActionButton(
                onClick = { addingWalkIn = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Walk-in") },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ServiceDayStrip(
                days = state.days,
                selected = state.selectedDate,
                onSelect = actions.onSelectDate,
                dayFormat = dayFormat,
            )

            ServiceSummary(state = state)

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(BookingFilter.entries.toList()) { filter ->
                    FilterChip(
                        selected = state.filter == filter,
                        onClick = { actions.onSetFilter(filter) },
                        label = { Text(filter.label) },
                    )
                }
            }

            when {
                state.isLoading -> Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    repeat(SKELETON_ROWS) {
                        ShimmerBox(modifier = Modifier.fillMaxWidth().height(96.dp))
                    }
                }

                state.visible.isEmpty() -> EmptyState(
                    title = if (state.bookings.isEmpty()) {
                        "No bookings that day"
                    } else {
                        "Nothing under ${state.filter.label.lowercase()}"
                    },
                    description = if (state.bookings.isEmpty()) {
                        "Bookings made through KulaPro appear here, and you can add " +
                            "walk-ins yourself."
                    } else {
                        "Try another filter to see the rest of the service."
                    },
                    icon = Icons.Outlined.EventAvailable,
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(state.visible, key = { _, row -> row.id }) { index, booking ->
                        AnimatedListItem(
                            index = index,
                            key = booking.id,
                            animator = entryAnimator,
                        ) {
                            BookingRow(
                                booking = booking,
                                timeLabel = timeFormat.format(booking.startsAt.toDate()),
                                isBusy = state.busyReservationId == booking.id,
                                onUpdateStatus = { actions.onUpdateStatus(booking, it) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceDayStrip(
    days: List<Date>,
    selected: Date?,
    onSelect: (Date) -> Unit,
    dayFormat: SimpleDateFormat,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(days) { day ->
            FilterChip(
                selected = day == selected,
                onClick = { onSelect(day) },
                label = { Text(dayFormat.format(day)) },
            )
        }
    }
}

/** The four numbers someone on the floor actually wants, before any list. */
@Composable
private fun ServiceSummary(state: OwnerBookingsUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SummaryFigure("Covers", "${state.covers}")
            SummaryFigure("To come", "${state.expected}")
            SummaryFigure("At table", "${state.seated}")
            SummaryFigure("No-shows", "${state.noShowPercent}%")
        }
    }
}

@Composable
private fun SummaryFigure(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun BookingRow(
    booking: Reservation,
    timeLabel: String,
    isBusy: Boolean,
    onUpdateStatus: (ReservationStatus) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = buildString {
                            append(booking.partySize)
                            append(if (booking.partySize == 1) " guest" else " guests")
                            if (booking.tableLabel.isNotBlank()) {
                                append(" at table ")
                                append(booking.tableLabel)
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    StatusBadge(status = booking.statusEnum)
                    if (booking.isWalkIn) {
                        Text(
                            text = "Walk-in",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            // Only the transitions this booking can actually make, so a completed one is
            // never offered a no-show and a cancelled one is never offered a table.
            val next = booking.statusEnum.nextActions
            if (next.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    next.forEach { status ->
                        AssistChip(
                            enabled = !isBusy,
                            onClick = { onUpdateStatus(status) },
                            label = { Text(status.actionLabel) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WalkInDialog(onDismiss: () -> Unit, onAdd: (Int) -> Unit) {
    var size by remember { mutableStateOf(2) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a walk-in") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Seated now. The app will offer fewer seats for this sitting, so what " +
                        "diners can book matches the room.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(onClick = { size = (size - 1).coerceAtLeast(1) }) {
                        Text("-", style = MaterialTheme.typography.titleLarge)
                    }
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$size", style = MaterialTheme.typography.titleLarge)
                    }
                    IconButton(onClick = { size = (size + 1).coerceAtMost(MAX_WALK_IN) }) {
                        Text("+", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(size) }) { Text("Seat them") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** What the button says, rather than what the status is called. */
private val ReservationStatus.actionLabel: String
    get() = when (this) {
        ReservationStatus.CONFIRMED -> "Confirm"
        ReservationStatus.SEATED -> "Seat them"
        ReservationStatus.COMPLETED -> "Done"
        ReservationStatus.NO_SHOW -> "No-show"
        ReservationStatus.CANCELLED -> "Cancel"
        ReservationStatus.PENDING -> "Pending"
    }

private const val SKELETON_ROWS = 4
private const val MAX_WALK_IN = 20
