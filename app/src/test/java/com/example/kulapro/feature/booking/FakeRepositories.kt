package com.example.kulapro.feature.booking

import com.example.kulapro.data.model.MenuItem
import com.example.kulapro.data.model.Reservation
import com.example.kulapro.data.model.Restaurant
import com.example.kulapro.data.model.RestaurantTable
import com.example.kulapro.data.model.SlotCount
import com.example.kulapro.data.repository.ReservationRepository
import com.example.kulapro.data.repository.RestaurantRepository
import com.example.kulapro.data.repository.Result
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Hand written fakes rather than mocks.
 *
 * The booking logic cares about what the data says, not about which calls were made, and a
 * fake that actually holds state lets a test say "this slot is full" instead of stubbing a
 * return value per call and hoping the order matches.
 */
class FakeRestaurantRepository(
    var restaurant: Restaurant = Restaurant(id = "r1", name = "The Bistro"),
    var tables: List<RestaurantTable> = emptyList(),
    var menuItems: List<MenuItem> = emptyList(),
    var restaurantFailure: String? = null,
    var tablesFailure: String? = null,
    var menuFailure: String? = null,
) : RestaurantRepository {

    override fun restaurants(): Flow<List<Restaurant>> = flowOf(listOf(restaurant))

    override suspend fun restaurant(id: String): Result<Restaurant> =
        restaurantFailure?.let { Result.Failure(it) } ?: Result.Success(restaurant)

    override suspend fun menu(restaurantId: String): Result<List<MenuItem>> =
        menuFailure?.let { Result.Failure(it) } ?: Result.Success(menuItems)

    override suspend fun tables(restaurantId: String): Result<List<RestaurantTable>> =
        tablesFailure?.let { Result.Failure(it) } ?: Result.Success(tables)
}

class FakeReservationRepository(
    var slotCounts: Map<Long, SlotCount> = emptyMap(),
    var createFailure: String? = null,
    var slotCountsFailure: String? = null,
) : ReservationRepository {

    /** Every booking written through this fake, so a test can assert what was saved. */
    val created = mutableListOf<Reservation>()

    override fun myReservations(): Flow<List<Reservation>> = flowOf(emptyList())

    override suspend fun slotCountsFor(
        restaurantId: String,
        from: Timestamp,
        to: Timestamp,
    ): Result<Map<Long, SlotCount>> =
        slotCountsFailure?.let { Result.Failure(it) } ?: Result.Success(slotCounts)

    override suspend fun slotCountsForAll(
        from: Timestamp,
        to: Timestamp,
    ): Result<List<SlotCount>> {
        slotCountsFailure?.let { return Result.Failure(it) }
        return Result.Success(
            slotCounts.values.filter {
                it.startsAtSeconds >= from.seconds && it.startsAtSeconds < to.seconds
            },
        )
    }

    override suspend fun create(reservation: Reservation): Result<String> {
        createFailure?.let { return Result.Failure(it) }
        created += reservation
        return Result.Success("res-${created.size}")
    }

    override suspend fun cancel(reservationId: String): Result<Unit> = Result.Success(Unit)
}
