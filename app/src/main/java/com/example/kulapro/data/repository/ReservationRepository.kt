package com.example.kulapro.data.repository

import com.example.kulapro.data.model.Reservation
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow

interface ReservationRepository {
    /** The signed-in user's reservations, newest first. Empty when signed out. */
    fun myReservations(): Flow<List<Reservation>>

    /** Reservations for one restaurant within a window. Drives availability and admin. */
    suspend fun reservationsFor(
        restaurantId: String,
        from: Timestamp,
        to: Timestamp,
    ): Result<List<Reservation>>

    suspend fun create(reservation: Reservation): Result<String>

    suspend fun cancel(reservationId: String): Result<Unit>
}
