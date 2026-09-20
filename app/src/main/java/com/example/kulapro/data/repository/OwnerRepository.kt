package com.example.kulapro.data.repository

import com.example.kulapro.data.model.MenuItem
import com.example.kulapro.data.model.Reservation
import com.example.kulapro.data.model.Restaurant
import com.example.kulapro.data.model.RestaurantTable
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

    /**
     * Adds a dish or rewrites one.
     *
     * An item carrying nutrition is an item the camera scanner never has to pay a model call
     * for, so what is typed here directly reduces what the app costs to run.
     */
    suspend fun saveMenuItem(item: MenuItem): Result<Unit>

    suspend fun deleteMenuItem(restaurantId: String, menuItemId: String): Result<Unit>

    suspend fun saveTable(table: RestaurantTable): Result<Unit>

    suspend fun deleteTable(restaurantId: String, tableId: String): Result<Unit>

    /**
     * Sets what the room can take, which is what the diner side reads to decide availability.
     *
     * Capacity, sitting length and opening hours travel together because changing one without
     * the others produces a schedule nobody can honour: a ninety minute sitting in a room
     * that closes in an hour is a slot the restaurant cannot serve.
     */
    suspend fun updateSeating(
        restaurantId: String,
        capacityPerSlot: Int,
        slotDurationMinutes: Int,
        openingHours: Map<String, String>,
    ): Result<Unit>
}
