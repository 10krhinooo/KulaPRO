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
}
