package com.example.kulapro.feature.owner

import com.example.kulapro.data.model.Reservation
import com.example.kulapro.data.model.ReservationStatus
import com.example.kulapro.data.model.Restaurant
import com.example.kulapro.data.repository.OwnerRepository
import com.example.kulapro.data.repository.Result
import com.google.firebase.Timestamp

/** Holds bookings in memory so a test can say "this day looked like this" and act on it. */
class FakeOwnerRepository(
    var bookings: MutableList<Reservation> = mutableListOf(),
    var bookingsFailure: String? = null,
    var updateFailure: String? = null,
    var walkInFailure: String? = null,
) : OwnerRepository {

    val walkIns = mutableListOf<Reservation>()

    override suspend fun updateRestaurant(restaurant: Restaurant): Result<Unit> =
        Result.Success(Unit)

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
}
