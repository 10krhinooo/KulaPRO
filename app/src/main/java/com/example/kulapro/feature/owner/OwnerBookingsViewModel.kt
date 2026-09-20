package com.example.kulapro.feature.owner

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kulapro.data.model.Reservation
import com.example.kulapro.data.model.ReservationStatus
import com.example.kulapro.data.repository.OwnerRepository
import com.example.kulapro.data.repository.RestaurantRepository
import com.example.kulapro.data.repository.Result
import com.example.kulapro.feature.booking.startOfDay
import com.example.kulapro.feature.booking.upcomingDays
import com.example.kulapro.ui.components.UiMessage
import com.google.firebase.Timestamp
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The restaurant's working screen for one day of service.
 *
 * Every action here changes what the diner side can book, because confirming, seating and
 * releasing all move the same slot counters availability is read from. Keeping that in one
 * tested place is why it is not left in the composable.
 */
@HiltViewModel
class OwnerBookingsViewModel @Inject constructor(
    private val ownerRepository: OwnerRepository,
    private val restaurantRepository: RestaurantRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val restaurantId: String = savedStateHandle.get<String>(ARG_RESTAURANT_ID).orEmpty()

    private val _state = MutableStateFlow(
        // A service is looked back on as well as forward, so the strip starts a week ago.
        OwnerBookingsUiState(days = serviceDays(Date())),
    )
    val state: StateFlow<OwnerBookingsUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        _state.update { it.copy(selectedDate = it.days.getOrNull(DAYS_BEHIND)) }
        loadRestaurant()
        refresh()
    }

    fun selectDate(day: Date) {
        if (_state.value.selectedDate == day) return
        _state.update { it.copy(selectedDate = day) }
        refresh()
    }

    fun setFilter(filter: BookingFilter) = _state.update { it.copy(filter = filter) }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun refresh() {
        loadJob?.cancel()
        val day = _state.value.selectedDate ?: return

        _state.update { it.copy(isLoading = true) }
        loadJob = viewModelScope.launch {
            val dayStart = startOfDay(day)
            val dayEnd = Date(dayStart.time + DAY_MILLIS)
            when (
                val result = ownerRepository.bookingsFor(
                    restaurantId = restaurantId,
                    from = Timestamp(dayStart),
                    to = Timestamp(dayEnd),
                )
            ) {
                is Result.Success -> _state.update {
                    it.copy(isLoading = false, bookings = result.data)
                }

                is Result.Failure -> _state.update {
                    it.copy(
                        isLoading = false,
                        bookings = emptyList(),
                        message = UiMessage.error(result.message),
                    )
                }
            }
        }
    }

    /**
     * Moves a booking on.
     *
     * Only along a transition the status allows, so a completed booking cannot be marked a
     * no-show and a cancelled one cannot be seated.
     */
    fun updateStatus(reservation: Reservation, to: ReservationStatus) {
        if (to !in reservation.statusEnum.nextActions) return
        if (_state.value.busyReservationId != null) return

        _state.update { it.copy(busyReservationId = reservation.id) }
        viewModelScope.launch {
            val result = ownerRepository.updateReservationStatus(reservation.id, to.name)
            _state.update { current ->
                when (result) {
                    is Result.Success -> current.copy(
                        busyReservationId = null,
                        // Updated locally as well as refetched, so the row changes under
                        // the finger rather than a moment later.
                        bookings = current.bookings.map {
                            if (it.id == reservation.id) it.copy(status = to.name) else it
                        },
                        message = UiMessage.success(statusMessage(reservation, to)),
                    )

                    is Result.Failure -> current.copy(
                        busyReservationId = null,
                        message = UiMessage.error(result.message),
                    )
                }
            }
            if (result is Result.Success) refresh()
        }
    }

    fun addWalkIn(partySize: Int, at: Date) {
        if (partySize <= 0 || _state.value.isAddingWalkIn) return

        _state.update { it.copy(isAddingWalkIn = true) }
        viewModelScope.launch {
            val result = ownerRepository.addWalkIn(
                restaurantId = restaurantId,
                restaurantName = _state.value.restaurantName,
                partySize = partySize,
                startsAt = Timestamp(at),
            )
            _state.update {
                when (result) {
                    is Result.Success -> it.copy(
                        isAddingWalkIn = false,
                        message = UiMessage.success(
                            "Walk-in of $partySize added. The app will offer fewer seats " +
                                "for that sitting.",
                        ),
                    )

                    is Result.Failure -> it.copy(
                        isAddingWalkIn = false,
                        message = UiMessage.error(result.message),
                    )
                }
            }
            if (result is Result.Success) refresh()
        }
    }

    private fun loadRestaurant() {
        viewModelScope.launch {
            when (val result = restaurantRepository.restaurant(restaurantId)) {
                is Result.Success -> _state.update {
                    it.copy(
                        restaurantName = result.data.name,
                        capacityPerSlot = result.data.capacityPerSlot,
                    )
                }

                // The bookings are still worth showing without the restaurant's own
                // details, so this is not surfaced as a failure of the screen.
                is Result.Failure -> Unit
            }
        }
    }

    private fun statusMessage(reservation: Reservation, to: ReservationStatus): String {
        val who = reservation.partySize
        return when (to) {
            ReservationStatus.CONFIRMED -> "Confirmed for $who."
            ReservationStatus.SEATED -> "Seated."
            ReservationStatus.COMPLETED -> "Done. The table is free again."
            ReservationStatus.NO_SHOW -> "Marked as a no-show. The seats are back."
            ReservationStatus.CANCELLED -> "Cancelled. The seats are back."
            ReservationStatus.PENDING -> "Moved back to pending."
        }
    }

    companion object {
        const val ARG_RESTAURANT_ID = "restaurantId"
    }
}

/**
 * A week behind and a fortnight ahead.
 *
 * Looking back matters as much as looking forward here: the day after a service is when
 * anyone actually asks what the no-show rate was.
 */
internal fun serviceDays(today: Date): List<Date> {
    val start = Calendar.getInstance().apply {
        time = today
        add(Calendar.DAY_OF_YEAR, -DAYS_BEHIND)
    }.time
    return upcomingDays(DAYS_BEHIND + DAYS_AHEAD, start)
}

internal const val DAYS_BEHIND = 7
private const val DAYS_AHEAD = 14
private const val DAY_MILLIS = 24L * 60 * 60 * 1000
