package com.example.kulapro.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.kulapro.Routes
import com.example.kulapro.data.model.Reservation
import com.example.kulapro.data.model.RestaurantTable
import com.example.kulapro.data.repository.ReservationRepository
import com.example.kulapro.data.repository.ReservationRepositoryFirestore
import com.example.kulapro.data.repository.RestaurantRepository
import com.example.kulapro.data.repository.RestaurantRepositoryFirestore
import com.example.kulapro.data.repository.Result
import com.example.kulapro.domain.AvailabilityCalculator
import com.example.kulapro.ui.components.BookingConfirmation
import com.example.kulapro.ui.components.MessageHost
import com.example.kulapro.ui.components.PrimaryButton
import com.example.kulapro.ui.components.ShimmerBox
import com.example.kulapro.ui.components.TablePlan
import com.example.kulapro.ui.components.rememberMessageHostState
import com.example.kulapro.ui.theme.LocalReduceMotion
import com.example.kulapro.ui.theme.Motion
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Booking form.
 *
 * Built around the three decisions a diner actually makes, in the order they make them: which
 * day, how many people, what time. Each one is a direct control rather than a text field: a
 * strip of the next two weeks, a stepper, and the real slots the kitchen can still seat.
 *
 * It books the restaurant the user tapped rather than a hardcoded "The Bistro", offers only
 * slots the restaurant can still seat rather than a fixed list of nineteen times, and
 * navigates away only after the write succeeds.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReservationFormScreen(
    navController: NavController,
    restaurantId: String,
    restaurantName: String,
    modifier: Modifier = Modifier,
    reservationRepository: ReservationRepository = remember { ReservationRepositoryFirestore() },
    restaurantRepository: RestaurantRepository = remember { RestaurantRepositoryFirestore() },
) {
    val scope = rememberCoroutineScope()
    val messages = rememberMessageHostState()
    val dayFormat = remember { SimpleDateFormat("EEE", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("d", Locale.getDefault()) }
    val monthFormat = remember { SimpleDateFormat("MMM", Locale.getDefault()) }
    val summaryFormat = remember { SimpleDateFormat("EEE d MMM", Locale.getDefault()) }

    val days = remember { upcomingDays(BOOKABLE_DAYS) }
    var selectedDate by remember { mutableStateOf(days.first()) }
    var partySize by remember { mutableStateOf(DEFAULT_PARTY_SIZE) }
    var selectedSlot by remember { mutableStateOf<AvailabilityCalculator.Slot?>(null) }
    var slots by remember { mutableStateOf<List<AvailabilityCalculator.Slot>>(emptyList()) }
    var tables by remember { mutableStateOf<List<RestaurantTable>>(emptyList()) }
    var takenBySlot by remember { mutableStateOf<Map<Long, Set<String>>>(emptyMap()) }
    var selectedTable by remember { mutableStateOf<RestaurantTable?>(null) }
    var isLoadingSlots by remember { mutableStateOf(true) }
    var isSubmitting by remember { mutableStateOf(false) }
    var confirmed by remember { mutableStateOf<String?>(null) }

    // Recompute availability whenever the day or party size changes. Selecting a slot then
    // raising the party size must be able to invalidate that slot.
    LaunchedEffect(selectedDate, partySize, restaurantId) {
        isLoadingSlots = true
        selectedSlot = null
        selectedTable = null

        when (val restaurant = restaurantRepository.restaurant(restaurantId)) {
            is Result.Failure -> {
                slots = emptyList()
                messages.showError(restaurant.message)
            }

            is Result.Success -> {
                val dayStart = startOfDay(selectedDate)
                val dayEnd = startOfDay(Date(selectedDate.time + DAY_MILLIS))
                when (
                    val taken = reservationRepository.slotCountsFor(
                        restaurantId = restaurantId,
                        from = Timestamp(dayStart),
                        to = Timestamp(dayEnd),
                    )
                ) {
                    is Result.Failure -> {
                        slots = emptyList()
                        messages.showError(taken.message)
                    }

                    is Result.Success -> {
                        slots = AvailabilityCalculator.slotsFor(
                            restaurant = restaurant.data,
                            day = selectedDate,
                            seatsTaken = taken.data.mapValues { it.value.seatsTaken },
                            partySize = partySize,
                        )
                        takenBySlot = taken.data.mapValues { it.value.takenTableIds.toSet() }
                    }
                }
            }
        }
        isLoadingSlots = false
    }

    // The floor plan changes far less often than availability does, so it is read once for
    // the restaurant rather than on every change of day or party size.
    LaunchedEffect(restaurantId) {
        when (val result = restaurantRepository.tables(restaurantId)) {
            is Result.Success -> tables = result.data
            // A restaurant with no mapped tables is a supported case, not an error: the
            // booking simply goes through on seat count and the diner is seated on arrival.
            is Result.Failure -> tables = emptyList()
        }
    }

    // The confirmation is the app's key success moment, so it gets a beat of its own before
    // the user is moved on. The old version fired a toast and navigated in the same frame.
    confirmed?.let { whenLabel ->
        LaunchedEffect(whenLabel) {
            delay(CONFIRMATION_MILLIS)
            navController.navigate(Routes.RESERVATIONS) {
                // Land on the Reservations tab with Home still beneath it, so the bottom bar
                // behaves exactly as it would had the user tapped the tab themselves.
                popUpTo(Routes.HOME)
                launchSingleTop = true
            }
        }
        BookingConfirmation(restaurantName = restaurantName, whenLabel = whenLabel)
        return
    }

    Scaffold(
        modifier = modifier,
        // Insets are owned by the navigation Scaffold; applying them again here would
        // double count the navigation bar height.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { MessageHost(messages) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = restaurantName.ifBlank { "Book a table" },
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = "Choose a day, a party and a time",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            BookingBar(
                summary = selectedSlot?.let {
                    buildString {
                        append(summaryFormat.format(selectedDate))
                        append(", ")
                        append(it.label)
                        append(", ")
                        append(partySize)
                        append(if (partySize == 1) " guest" else " guests")
                        selectedTable?.let { table -> append(", table ${table.label}") }
                    }
                },
                isSubmitting = isSubmitting,
                onConfirm = {
                    val slot = selectedSlot ?: return@BookingBar
                    isSubmitting = true
                    scope.launch {
                        val result = reservationRepository.create(
                            Reservation(
                                restaurantId = restaurantId,
                                restaurantName = restaurantName,
                                startsAt = Timestamp(slot.startsAt),
                                partySize = partySize,
                                tableId = selectedTable?.id.orEmpty(),
                                tableLabel = selectedTable?.label.orEmpty(),
                            ),
                        )
                        isSubmitting = false
                        when (result) {
                            // Navigate only on success. The first version navigated home
                            // unconditionally, so a failed write still looked like a booking.
                            is Result.Success ->
                                confirmed = "${summaryFormat.format(selectedDate)} at ${slot.label}"

                            is Result.Failure -> messages.showError(result.message)
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StepHeading(number = 1, title = "When are you coming?")
            DayStrip(
                days = days,
                selected = selectedDate,
                onSelect = { selectedDate = it },
                dayFormat = dayFormat,
                dateFormat = dateFormat,
                monthFormat = monthFormat,
            )

            StepHeading(number = 2, title = "How many of you?")
            PartySizeStepper(
                value = partySize,
                onChange = { partySize = it },
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            StepHeading(number = 3, title = "What time suits?")
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                when {
                    isLoadingSlots -> SlotSkeleton()

                    slots.isEmpty() -> SlotNotice(
                        "This restaurant is closed on that day. Try another.",
                    )

                    slots.none { it.isAvailable } -> SlotNotice(
                        "Fully booked for a party of $partySize. " +
                            "Try another day or a smaller table.",
                    )

                    else -> SlotGrid(
                        slots = slots,
                        selected = selectedSlot,
                        onSelect = {
                            selectedSlot = it
                            // A table is only free within a sitting, so changing the time
                            // invalidates whatever was picked.
                            selectedTable = null
                        },
                    )
                }
            }

            // Only offered once there is a sitting to be free within, and only by
            // restaurants that have actually mapped their floor.
            val slot = selectedSlot
            if (tables.isNotEmpty() && slot != null) {
                StepHeading(number = 4, title = "Where would you like to sit?")
                Text(
                    text = "Optional. Skip it and the restaurant will seat you on arrival.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                TablePlan(
                    tables = tables,
                    takenTableIds = takenBySlot[slot.startsAt.time / MILLIS_PER_SECOND]
                        .orEmpty(),
                    partySize = partySize,
                    selectedTableId = selectedTable?.id,
                    onSelect = { table ->
                        // Tapping the chosen table again clears it, so picking a table is
                        // never a decision the diner cannot take back.
                        selectedTable = if (selectedTable?.id == table.id) null else table
                    },
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun StepHeading(number: Int, title: String) {
    Row(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(FULLY_ROUNDED),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(24.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "$number",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Text(text = title, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * The next fortnight as a scrollable strip.
 *
 * Replaces the platform date dialog: picking "this Friday" took three taps through a calendar
 * that happily offered dates the restaurant was closed on.
 */
@Composable
private fun DayStrip(
    days: List<Date>,
    selected: Date,
    onSelect: (Date) -> Unit,
    dayFormat: SimpleDateFormat,
    dateFormat: SimpleDateFormat,
    monthFormat: SimpleDateFormat,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(days) { index, day ->
            val isSelected = day == selected
            val scale by animateFloatAsState(
                targetValue = if (isSelected && !LocalReduceMotion.current) 1.05f else 1f,
                animationSpec = Motion.bouncy(),
                label = "dayScale",
            )
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ),
                modifier = Modifier
                    .width(DAY_CHIP_WIDTH)
                    .scale(scale)
                    .clickable { onSelect(day) },
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val content = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                    Text(
                        text = when (index) {
                            0 -> "Today"
                            1 -> "Tmrw"
                            else -> dayFormat.format(day)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = content.copy(alpha = 0.8f),
                    )
                    Text(
                        text = dateFormat.format(day),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = content,
                    )
                    Text(
                        text = monthFormat.format(day),
                        style = MaterialTheme.typography.labelSmall,
                        color = content.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

/** A stepper rather than a keyboard: party size is a small number, never a typed one. */
@Composable
private fun PartySizeStepper(
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { onChange(value - 1) },
                enabled = value > MIN_PARTY_SIZE,
            ) {
                Icon(Icons.Rounded.Remove, contentDescription = "One fewer guest")
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = "$value", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = if (value == 1) "guest" else "guests",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = { onChange(value + 1) },
                enabled = value < MAX_PARTY_SIZE,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "One more guest")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SlotGrid(
    slots: List<AvailabilityCalculator.Slot>,
    selected: AvailabilityCalculator.Slot?,
    onSelect: (AvailabilityCalculator.Slot) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        slots.forEach { slot ->
            val isSelected = selected?.startsAt == slot.startsAt
            Surface(
                shape = MaterialTheme.shapes.small,
                color = when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    !slot.isAvailable -> MaterialTheme.colorScheme.surfaceVariant
                    else -> MaterialTheme.colorScheme.surface
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ),
                modifier = Modifier.clickable(enabled = slot.isAvailable) { onSelect(slot) },
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = slot.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.onPrimary
                            !slot.isAvailable -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                    // Scarcity is the honest reason to hurry, so it is shown rather than
                    // implied. A full slot says so instead of silently refusing the tap.
                    Text(
                        text = when {
                            !slot.isAvailable -> "full"
                            slot.seatsRemaining <= SCARCE_SEATS -> "${slot.seatsRemaining} left"
                            else -> "free"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            !slot.isAvailable -> MaterialTheme.colorScheme.onSurfaceVariant
                            slot.seatsRemaining <= SCARCE_SEATS -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SlotNotice(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.EventBusy,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SlotSkeleton() {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(SKELETON_SLOTS) {
            ShimmerBox(modifier = Modifier.width(SLOT_SKELETON_WIDTH).height(SLOT_SKELETON_HEIGHT))
        }
    }
}

/**
 * Sticky summary and confirm.
 *
 * The button stays put while the slots scroll, and it states exactly what is about to be
 * booked, so nobody confirms a time they cannot see.
 */
@Composable
private fun BookingBar(
    summary: String?,
    isSubmitting: Boolean,
    onConfirm: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            AnimatedVisibility(
                visible = summary != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Text(
                    text = summary.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                )
            }
            PrimaryButton(
                text = if (summary == null) "Pick a time" else "Confirm booking",
                loadingText = "Booking",
                loading = isSubmitting,
                enabled = summary != null,
                onClick = onConfirm,
            )
        }
    }
}

/** Today plus the next [count] minus one days, each normalised to midnight. */
private fun upcomingDays(count: Int): List<Date> {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return List(count) {
        calendar.time.also { calendar.add(Calendar.DAY_OF_YEAR, 1) }
    }
}

private fun startOfDay(date: Date): Date = Calendar.getInstance().apply {
    time = date
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.time

private const val DAY_MILLIS = 24L * 60 * 60 * 1000
private const val MILLIS_PER_SECOND = 1000L

/** Two weeks ahead. Far enough to plan, near enough that availability still means something. */
private const val BOOKABLE_DAYS = 14
private const val DEFAULT_PARTY_SIZE = 2
private const val MIN_PARTY_SIZE = 1

/** Larger parties are a phone call to the restaurant, not a self service booking. */
private const val MAX_PARTY_SIZE = 12

/** Below this many seats the slot is called out as nearly gone. */
private const val SCARCE_SEATS = 4
private const val SKELETON_SLOTS = 8
private const val CONFIRMATION_MILLIS = 1_600L
private const val FULLY_ROUNDED = 50
private val DAY_CHIP_WIDTH = 64.dp
private val SLOT_SKELETON_WIDTH = 84.dp
private val SLOT_SKELETON_HEIGHT = 56.dp
