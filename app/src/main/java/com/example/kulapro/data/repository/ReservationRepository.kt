package com.example.kulapro.data.repository

import com.example.kulapro.data.model.Reservation
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow

interface ReservationRepository {
    /** The signed-in user's reservations, newest first. Empty when signed out. */
    fun myReservations(): Flow<List<Reservation>>

    /**
     * Seats already taken per sitting for one restaurant on one day.
     *
     * Reads public aggregates rather than other people's reservations, so availability works
     * without exposing who else is booked.
     */
    suspend fun seatsTakenFor(
        restaurantId: String,
        from: Timestamp,
        to: Timestamp,
    ): Result<Map<Long, Int>>

    suspend fun create(reservation: Reservation): Result<String>

    suspend fun cancel(reservationId: String): Result<Unit>
}
