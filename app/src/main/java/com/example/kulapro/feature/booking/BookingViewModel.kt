package com.example.kulapro.feature.booking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kulapro.data.model.Reservation
import com.example.kulapro.data.model.RestaurantTable
import com.example.kulapro.data.repository.ReservationRepository
import com.example.kulapro.data.repository.RestaurantRepository
import com.example.kulapro.data.repository.Result
import com.example.kulapro.data.settings.SettingsRepository
import com.example.kulapro.domain.AvailabilityCalculator
import com.example.kulapro.domain.startOfDay
import com.example.kulapro.feature.reminders.BookingReminderScheduler
import com.example.kulapro.ui.components.UiMessage
import com.google.firebase.Timestamp
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The booking screen's logic, lifted out of the composable.
 *
 * Availability depends on the day and the party size together, so the two cannot be held
 * independently and reconciled later: raising the party size has to be able to invalidate
 * the slot already chosen, and changing the slot has to release the table. Keeping that in
 * one place is the reason this exists, and it is what the tests pin down.
 */
@HiltViewModel
class BookingViewModel @Inject constructor(
    private val reservationRepository: ReservationRepository,
    private val restaurantRepository: RestaurantRepository,
    private val reminderScheduler: BookingReminderScheduler,
    private val settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val restaurantId: String = savedStateHandle.get<String>(ARG_RESTAURANT_ID).orEmpty()
    private val restaurantName: String =
        savedStateHandle.get<String>(ARG_RESTAURANT_NAME).orEmpty()

    private val summaryFormat = SimpleDateFormat("EEE d MMM", Locale.getDefault())

    private val _state = MutableStateFlow(
        BookingUiState(
            restaurantName = restaurantName,
            days = upcomingDays(BOOKABLE_DAYS, Date()),
        ),
    )
    val state: StateFlow<BookingUiState> = _state.asStateFlow()

    /** Cancelled whenever the day or party size changes, so a stale reply cannot land. */
    private var availabilityJob: Job? = null

    init {
        _state.update { it.copy(selectedDate = it.days.firstOrNull()) }
        loadTables()
        refreshAvailability()
    }

    fun selectDate(day: Date) {
        if (_state.value.selectedDate == day) return
        // Both selections belong to the old day, so neither survives the change.
        _state.update { it.copy(selectedDate = day, selectedSlot = null, selectedTable = null) }
        refreshAvailability()
    }

    fun setPartySize(size: Int) {
        val clamped = size.coerceIn(MIN_PARTY_SIZE, MAX_PARTY_SIZE)
        if (_state.value.partySize == clamped) return
        _state.update { it.copy(partySize = clamped, selectedSlot = null, selectedTable = null) }
        refreshAvailability()
    }

    fun selectSlot(slot: AvailabilityCalculator.Slot) {
        if (!slot.isAvailable) return
        // A table is free within a sitting, so changing the sitting releases it.
        _state.update {
            it.copy(
                selectedSlot = slot,
                selectedTable = null,
                takenTableIds = takenFor(slot),
            )
        }
    }

    /**
     * Tapping the chosen table again clears it, so picking a table is never a decision the
     * diner cannot take back.
     */
    fun toggleTable(table: RestaurantTable) {
        if (table.id in _state.value.takenTableIds) return
        if (table.seats < _state.value.partySize) return
        _state.update {
            it.copy(selectedTable = if (it.selectedTable?.id == table.id) null else table)
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun submit() {
        val current = _state.value
        val slot = current.selectedSlot ?: return
        val day = current.selectedDate ?: return
        if (current.isSubmitting || current.confirmedLabel != null) return

        _state.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            val result = reservationRepository.create(
                Reservation(
                    restaurantId = restaurantId,
                    restaurantName = restaurantName,
                    startsAt = Timestamp(slot.startsAt),
                    partySize = current.partySize,
                    tableId = current.selectedTable?.id.orEmpty(),
                    tableLabel = current.selectedTable?.label.orEmpty(),
                ),
            )
            val whenLabel = "${summaryFormat.format(day)} at ${slot.label}"
            if (result is Result.Success) {
                // Scheduled here rather than in the repository: a reminder is a thing this
                // device does for this user, not part of writing the booking down.
                val settings = settingsRepository.settings.first()
                reminderScheduler.schedule(
                    reservationId = result.data,
                    restaurantName = restaurantName,
                    whenLabel = slot.label,
                    startsAtMillis = slot.startsAt.time,
                    leadHours = settings.reminderLeadHours,
                    enabled = settings.remindersEnabled,
                )
            }
            _state.update {
                when (result) {
                    // Confirmed only once the write lands. The first version navigated away
                    // unconditionally, so a failed write still looked like a booking.
                    is Result.Success -> it.copy(
                        isSubmitting = false,
                        confirmedLabel = whenLabel,
                    )

                    is Result.Failure -> it.copy(
                        isSubmitting = false,
                        message = UiMessage.error(result.message),
                    )
                }
            }
        }
    }

    /** The floor plan changes far less often than availability, so it is read once. */
    private fun loadTables() {
        viewModelScope.launch {
            val tables = when (val result = restaurantRepository.tables(restaurantId)) {
                is Result.Success -> result.data
                // A restaurant with no mapped tables is supported, not an error: booking
                // goes through on seat count and the diner is seated on arrival.
                is Result.Failure -> emptyList()
            }
            _state.update { it.copy(tables = tables) }
        }
    }

    private fun refreshAvailability() {
        availabilityJob?.cancel()
        val day = _state.value.selectedDate ?: return
        val partySize = _state.value.partySize

        _state.update { it.copy(isLoadingSlots = true) }
        availabilityJob = viewModelScope.launch {
            when (val restaurant = restaurantRepository.restaurant(restaurantId)) {
                is Result.Failure -> _state.update {
                    it.copy(
                        isLoadingSlots = false,
                        slots = emptyList(),
                        message = UiMessage.error(restaurant.message),
                    )
                }

                is Result.Success -> {
                    val dayStart = startOfDay(day)
                    val dayEnd = startOfDay(Date(day.time + DAY_MILLIS))
                    when (
                        val taken = reservationRepository.slotCountsFor(
                            restaurantId = restaurantId,
                            from = Timestamp(dayStart),
                            to = Timestamp(dayEnd),
                        )
                    ) {
                        is Result.Failure -> _state.update {
                            it.copy(
                                isLoadingSlots = false,
                                slots = emptyList(),
                                message = UiMessage.error(taken.message),
                            )
                        }

                        is Result.Success -> _state.update {
                            it.copy(
                                isLoadingSlots = false,
                                slots = AvailabilityCalculator.slotsFor(
                                    restaurant = restaurant.data,
                                    day = day,
                                    seatsTaken = taken.data.mapValues { e -> e.value.seatsTaken },
                                    partySize = partySize,
                                ),
                                takenBySlot = taken.data
                                    .mapValues { e -> e.value.takenTableIds.toSet() },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun takenFor(slot: AvailabilityCalculator.Slot): Set<String> =
        _state.value.takenBySlot[slot.startsAt.time / MILLIS_PER_SECOND].orEmpty()

    companion object {
        const val ARG_RESTAURANT_ID = "restaurantId"
        const val ARG_RESTAURANT_NAME = "restaurantName"
    }
}

/** Today plus the next days, each normalised to midnight. */
internal fun upcomingDays(count: Int, from: Date): List<Date> {
    val calendar = Calendar.getInstance().apply {
        time = from
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return List(count) {
        calendar.time.also { calendar.add(Calendar.DAY_OF_YEAR, 1) }
    }
}

private const val DAY_MILLIS = 24L * 60 * 60 * 1000
private const val MILLIS_PER_SECOND = 1000L
