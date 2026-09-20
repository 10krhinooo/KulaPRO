package com.example.kulapro.feature.owner

import com.example.kulapro.data.model.MenuItem
import com.example.kulapro.data.model.Reservation
import com.example.kulapro.data.model.ReservationStatus
import com.example.kulapro.data.model.Restaurant
import com.example.kulapro.data.model.RestaurantTable
import com.example.kulapro.data.repository.OwnerRepository
import com.example.kulapro.data.repository.Result
import com.google.firebase.Timestamp

/** Holds bookings in memory so a test can say "this day looked like this" and act on it. */
class FakeOwnerRepository(
    var bookings: MutableList<Reservation> = mutableListOf(),
    var bookingsFailure: String? = null,
    var updateFailure: String? = null,
    var walkInFailure: String? = null,
    var saveFailure: String? = null,
    var deleteFailure: String? = null,
) : OwnerRepository {

    val walkIns = mutableListOf<Reservation>()
    val menuItems = mutableListOf<MenuItem>()
    val tables = mutableListOf<RestaurantTable>()
    var seating: Seating? = null
    var savedRestaurant: Restaurant? = null

    override suspend fun updateRestaurant(restaurant: Restaurant): Result<Unit> {
        saveFailure?.let { return Result.Failure(it) }
        savedRestaurant = restaurant
        return Result.Success(Unit)
    }

    override suspend fun bookingsFor(
        restaurantId: String,
        from: Timestamp,
        to: Timestamp,
    ): Result<List<Reservation>> {
        bookingsFailure?.let { return Result.Failure(it) }
        return Result.Success(
            bookings.filter {
                it.startsAt.seconds >= from.seconds && it.startsAt.seconds < to.seconds
            },
        )
    }

    override suspend fun updateReservationStatus(
        reservationId: String,
        status: String,
    ): Result<Unit> {
        updateFailure?.let { return Result.Failure(it) }
        val index = bookings.indexOfFirst { it.id == reservationId }
        if (index >= 0) bookings[index] = bookings[index].copy(status = status)
        return Result.Success(Unit)
    }

    override suspend fun addWalkIn(
        restaurantId: String,
        restaurantName: String,
        partySize: Int,
        startsAt: Timestamp,
        tableId: String,
        tableLabel: String,
    ): Result<Unit> {
        walkInFailure?.let { return Result.Failure(it) }
        val walkIn = Reservation(
            id = "walk-${walkIns.size + 1}",
            restaurantId = restaurantId,
            restaurantName = restaurantName,
            startsAt = startsAt,
            partySize = partySize,
            status = ReservationStatus.SEATED.name,
            isWalkIn = true,
        )
        walkIns += walkIn
        bookings += walkIn
        return Result.Success(Unit)
    }

    override suspend fun saveMenuItem(item: MenuItem): Result<Unit> {
        saveFailure?.let { return Result.Failure(it) }
        // Firestore assigns an id to a new document, so the fake does too. A test that
        // saved a dish and got back a blank id would not be testing what happens in the app.
        val saved = if (item.id.isBlank()) item.copy(id = "menu-${menuItems.size + 1}") else item
        val index = menuItems.indexOfFirst { it.id == saved.id }
        if (index >= 0) menuItems[index] = saved else menuItems += saved
        return Result.Success(Unit)
    }

    override suspend fun deleteMenuItem(restaurantId: String, menuItemId: String): Result<Unit> {
        deleteFailure?.let { return Result.Failure(it) }
        menuItems.removeAll { it.id == menuItemId }
        return Result.Success(Unit)
    }

    override suspend fun saveTable(table: RestaurantTable): Result<Unit> {
        saveFailure?.let { return Result.Failure(it) }
        val saved = if (table.id.isBlank()) table.copy(id = "table-${tables.size + 1}") else table
        val index = tables.indexOfFirst { it.id == saved.id }
        if (index >= 0) tables[index] = saved else tables += saved
        return Result.Success(Unit)
    }

    override suspend fun deleteTable(restaurantId: String, tableId: String): Result<Unit> {
        deleteFailure?.let { return Result.Failure(it) }
        tables.removeAll { it.id == tableId }
        return Result.Success(Unit)
    }

    override suspend fun updateSeating(
        restaurantId: String,
        capacityPerSlot: Int,
        slotDurationMinutes: Int,
        openingHours: Map<String, String>,
    ): Result<Unit> {
        saveFailure?.let { return Result.Failure(it) }
        seating = Seating(capacityPerSlot, slotDurationMinutes, openingHours)
        return Result.Success(Unit)
    }

    /** What the last seating save wrote, so a test can assert on it as one thing. */
    data class Seating(
        val capacityPerSlot: Int,
        val slotDurationMinutes: Int,
        val openingHours: Map<String, String>,
    )
}
