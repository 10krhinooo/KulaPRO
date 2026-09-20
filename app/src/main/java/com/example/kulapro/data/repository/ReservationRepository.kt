package com.example.kulapro.data.repository

import com.example.kulapro.data.model.Reservation
import com.example.kulapro.data.model.SlotCount
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow

interface ReservationRepository {
    /** The signed-in user's reservations, newest first. Empty when signed out. */
    fun myReservations(): Flow<List<Reservation>>

    /**
     * What is already taken per sitting for one restaurant on one day, keyed by slot start
     * in epoch seconds.
     *
     * Reads public aggregates rather than other people's reservations, so availability and
     * the table plan both work without exposing who else is booked.
     */
    suspend fun slotCountsFor(
        restaurantId: String,
        from: Timestamp,
        to: Timestamp,
    ): Result<Map<Long, SlotCount>>

    /**
     * The same counters for every restaurant at once, over one window.
     *
     * One collection group query rather than a query per listing. Home asks "who has a
     * table tonight" of a whole list, and asking each restaurant separately would be a read
     * per card on every load, which is the kind of thing that is fine with eight
     * restaurants and ruinous with eight hundred.
     */
    suspend fun slotCountsForAll(from: Timestamp, to: Timestamp): Result<List<SlotCount>>

    suspend fun create(reservation: Reservation): Result<String>

    suspend fun cancel(reservationId: String): Result<Unit>
}
