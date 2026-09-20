package com.example.kulapro.pages

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.kulapro.data.model.Reservation
import com.example.kulapro.data.repository.ReservationRepository
import com.example.kulapro.data.repository.ReservationRepositoryFirestore
import com.example.kulapro.data.repository.RestaurantRepository
import com.example.kulapro.data.repository.RestaurantRepositoryFirestore
import com.example.kulapro.data.repository.Result
import com.example.kulapro.domain.AvailabilityCalculator
import com.example.kulapro.ui.components.MessageHost
import com.example.kulapro.ui.components.rememberMessageHostState
import com.example.kulapro.util.Validators
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Booking form.
 *
 * Differs from the first version in three ways that matter: it books the restaurant the user
 * actually tapped rather than a hardcoded "The Bistro", it offers only slots the restaurant
 * can still seat rather than a fixed list of nineteen times, and it navigates away only after
 * the write succeeds.
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages = rememberMessageHostState()
    val dateFormat = remember { SimpleDateFormat("EEE d MMM yyyy", Locale.getDefault()) }

    var selectedDate by remember { mutableStateOf<Date?>(null) }
    var partySize by remember { mutableStateOf("2") }
    var selectedSlot by remember { mutableStateOf<AvailabilityCalculator.Slot?>(null) }
    var slots by remember { mutableStateOf<List<AvailabilityCalculator.Slot>>(emptyList()) }
    var partySizeError by remember { mutableStateOf<String?>(null) }
    var isLoadingSlots by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    // Recompute availability whenever the day or party size changes. Selecting a slot then
    // raising the party size must be able to invalidate that slot.
    LaunchedEffect(selectedDate, partySize, restaurantId) {
        val day = selectedDate ?: return@LaunchedEffect
        val size = partySize.toIntOrNull() ?: return@LaunchedEffect
        isLoadingSlots = true
        selectedSlot = null

        when (val restaurant = restaurantRepository.restaurant(restaurantId)) {
            is Result.Failure -> {
                slots = emptyList()
                messages.showError(restaurant.message)
            }

            is Result.Success -> {
                val dayStart = startOfDay(day)
                val dayEnd = startOfDay(Date(day.time + DAY_MILLIS))
                when (
                    val taken = reservationRepository.seatsTakenFor(
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
                            day = day,
                            seatsTaken = taken.data,
                            partySize = size,
                        )
                    }
                }
            }
        }
        isLoadingSlots = false
    }

    Scaffold(
        modifier = modifier,
        // Insets are owned by the navigation Scaffold; applying them again here would
        // double count the navigation bar height.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text(restaurantName.ifBlank { "Book a table" }) }) },
        snackbarHost = { MessageHost(messages) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = selectedDate?.let(dateFormat::format).orEmpty(),
                onValueChange = {},
                readOnly = true,
                label = { Text("Date") },
                placeholder = { Text("Choose a date") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = { showDatePicker(context) { selectedDate = it } }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Pick a date")
                    }
                },
            )

            OutlinedTextField(
                value = partySize,
                onValueChange = {
                    partySize = it
                    partySizeError = Validators.partySizeError(it)
                },
                label = { Text("Number of guests") },
                isError = partySizeError != null,
                supportingText = partySizeError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Available times", style = MaterialTheme.typography.titleMedium)

            when {
                selectedDate == null ->
                    Text(
                        "Pick a date to see what is free.",
                        style = MaterialTheme.typography.bodyMedium,
                    )

                isLoadingSlots ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }

                slots.isEmpty() ->
                    Text(
                        "This restaurant is not open on that day.",
                        style = MaterialTheme.typography.bodyMedium,
                    )

                slots.none { it.isAvailable } ->
                    Text(
                        "Fully booked for a party of $partySize. Try another date.",
                        style = MaterialTheme.typography.bodyMedium,
                    )

                else -> SlotGrid(
                    slots = slots,
                    selected = selectedSlot,
                    onSelect = { selectedSlot = it },
                )
            }

            Button(
                onClick = {
                    val slot = selectedSlot ?: return@Button
                    val size = partySize.toIntOrNull() ?: return@Button
                    isSubmitting = true
                    scope.launch {
                        val result = reservationRepository.create(
                            Reservation(
                                restaurantId = restaurantId,
                                restaurantName = restaurantName,
                                startsAt = Timestamp(slot.startsAt),
                                partySize = size,
                            ),
                        )
                        isSubmitting = false
                        when (result) {
                            // Navigate only on success. The first version navigated home
                            // unconditionally, so a failed write still looked like a booking.
                            is Result.Success -> navController.navigate("reservation") {
                                popUpTo("home")
                            }

                            is Result.Failure -> messages.showError(result.message)
                        }
                    }
                },
                enabled = !isSubmitting &&
                    selectedSlot != null &&
                    partySizeError == null &&
                    partySize.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isSubmitting) "Booking..." else "Confirm booking")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlotGrid(
    slots: List<AvailabilityCalculator.Slot>,
    selected: AvailabilityCalculator.Slot?,
    onSelect: (AvailabilityCalculator.Slot) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        slots.chunked(3).forEach { row ->
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                row.forEach { slot ->
                    FilterChip(
                        selected = selected?.startsAt == slot.startsAt,
                        onClick = { onSelect(slot) },
                        enabled = slot.isAvailable,
                        label = { Text(slot.label) },
                        colors = FilterChipDefaults.filterChipColors(),
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

private fun startOfDay(date: Date): Date = Calendar.getInstance().apply {
    time = date
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.time

private fun showDatePicker(context: android.content.Context, onPicked: (Date) -> Unit) {
    val now = Calendar.getInstance()
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            onPicked(
                Calendar.getInstance().apply {
                    set(year, month, dayOfMonth, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }.time,
            )
        },
        now.get(Calendar.YEAR),
        now.get(Calendar.MONTH),
        now.get(Calendar.DAY_OF_MONTH),
    ).apply {
        // A booking in the past is never valid, so the picker should not offer one.
        datePicker.minDate = now.timeInMillis
    }.show()
}
