package com.example.kulapro.data.repository

import com.example.kulapro.data.model.Reservation
import com.example.kulapro.data.model.Restaurant
import com.google.firebase.Timestamp

/** Restaurant-side operations, available only to users whose claims name the restaurant. */
interface OwnerRepository {

    suspend fun updateRestaurant(restaurant: Restaurant): Result<Unit>

    suspend fun bookingsFor(
        restaurantId: String,
        from: Timestamp,
        to: Timestamp,
    ): Result<List<Reservation>>

    suspend fun updateReservationStatus(reservationId: String, status: String): Result<Unit>

    /**
     * Records a party the restaurant seated without a booking.
     *
     * Walk-ins are most of a busy night in most restaurants. Without them the availability
     * the app offers diners would describe a room that does not exist, so they take seats
     * from the same slot counter a booking does.
     */
    suspend fun addWalkIn(
        restaurantId: String,
        restaurantName: String,
        partySize: Int,
        startsAt: Timestamp,
        tableId: String = "",
        tableLabel: String = "",
    ): Result<Unit>
}
