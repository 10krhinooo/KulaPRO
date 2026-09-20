package com.example.kulapro.feature.reservations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kulapro.data.model.Reservation
import com.example.kulapro.data.model.Review
import com.example.kulapro.data.repository.AuthRepository
import com.example.kulapro.data.repository.ReservationRepository
import com.example.kulapro.data.repository.Result
import com.example.kulapro.data.repository.ReviewRepository
import com.example.kulapro.feature.reminders.BookingReminderScheduler
import com.example.kulapro.util.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The diner's own bookings.
 *
 * The screen used to read Firestore inline and hold six pieces of local state, which is why
 * none of the interesting behaviour here, what counts as upcoming, when a booking can still
 * be changed, could be tested. All of that now lives in the state object.
 */
@HiltViewModel
class ReservationsViewModel @Inject constructor(
    private val reservationRepository: ReservationRepository,
    private val reviewRepository: ReviewRepository,
    private val reminderScheduler: BookingReminderScheduler,
    private val authRepository: AuthRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(ReservationsUiState(now = clock.now()))
    val state: StateFlow<ReservationsUiState> = _state.asStateFlow()

    private var reservationsJob: Job? = null

    init {
        observeSession()
    }

    fun selectTab(tab: ReservationTab) = _state.update { it.copy(tab = tab) }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun startReview(reservation: Reservation) =
        _state.update { it.copy(reviewTarget = reservation) }

    fun cancelReview() = _state.update { it.copy(reviewTarget = null) }

    /**
     * Follows the signed in user, so signing out empties the list rather than leaving one
     * person's bookings on screen for the next.
     */
    private fun observeSession() = viewModelScope.launch {
        authRepository.authState().collect { uid ->
            _state.update {
                it.copy(
                    isSignedIn = uid != null,
                    reservations = if (uid == null) emptyList() else it.reservations,
                    isLoading = uid != null,
                )
            }
            reservationsJob?.cancel()
            if (uid != null) observeReservations()
        }
    }

    private fun observeReservations() {
        reservationsJob = viewModelScope.launch {
            reservationRepository.myReservations().collect { reservations ->
                // The clock is re-read on every emission, so a screen left open past a
                // sitting moves that booking from upcoming to history on its own.
                _state.update {
                    it.copy(
                        reservations = reservations,
                        isLoading = false,
                        now = clock.now(),
                    )
                }
            }
        }
    }

    fun cancel(reservation: Reservation) {
        _state.update { it.copy(busyReservationId = reservation.id) }
        viewModelScope.launch {
            val result = reservationRepository.cancel(reservation.id)
            _state.update {
                when (result) {
                    is Result.Success -> {
                        // The reminder goes with the booking. A notification for a table
                        // nobody is keeping is worse than no notification.
                        reminderScheduler.cancel(reservation.id)
                        it.copy(
                            busyReservationId = null,
                            message = "Booking cancelled. The seats are back.",
                            isError = false,
                        )
                    }

                    is Result.Failure -> it.copy(
                        busyReservationId = null,
                        message = result.message,
                        isError = true,
                    )
                }
            }
        }
    }

    fun submitReview(rating: Int, comment: String) {
        val target = _state.value.reviewTarget ?: return
        _state.update { it.copy(isPostingReview = true) }

        viewModelScope.launch {
            val result = reviewRepository.submit(
                Review(
                    restaurantId = target.restaurantId,
                    // The reservation being reviewed. Security rules read it to check the
                    // reviewer actually ate there, which is what makes a fake review hard
                    // rather than merely discouraged.
                    reservationId = target.id,
                    rating = rating,
                    comment = comment,
                ),
            )
            _state.update {
                it.copy(
                    isPostingReview = false,
                    reviewTarget = null,
                    message = when (result) {
                        is Result.Success -> "Thanks, your review is live"
                        is Result.Failure -> result.message
                    },
                    isError = result is Result.Failure,
                )
            }
        }
    }
}
