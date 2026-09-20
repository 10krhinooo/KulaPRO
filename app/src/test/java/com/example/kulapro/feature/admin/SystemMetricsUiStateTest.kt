package com.example.kulapro.feature.admin

import com.example.kulapro.data.model.Reservation
import com.example.kulapro.data.model.ReservationStatus
import com.example.kulapro.data.model.Restaurant
import com.example.kulapro.data.repository.PlatformSnapshot
import com.google.firebase.Timestamp
import java.util.Calendar
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemMetricsUiStateTest {

    private val now = Calendar.getInstance().apply {
        set(2026, Calendar.SEPTEMBER, 16, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.time

    private fun booking(
        partySize: Int = 2,
        status: ReservationStatus = ReservationStatus.COMPLETED,
        isWalkIn: Boolean = false,
        hour: Int = 19,
    ) = Reservation(
        startsAt = Timestamp(
            Calendar.getInstance().apply {
                time = now
                set(Calendar.HOUR_OF_DAY, hour)
            }.time,
        ),
        partySize = partySize,
        status = status.name,
        isWalkIn = isWalkIn,
    )

    private fun state(
        restaurants: List<Restaurant> = emptyList(),
        reservations: List<Reservation> = emptyList(),
        dinerCount: Int = 0,
        pendingRequestCount: Int = 0,
    ) = SystemMetricsUiState(
        isLoading = false,
        snapshot = PlatformSnapshot(
            restaurants = restaurants,
            reservations = reservations,
            dinerCount = dinerCount,
            pendingRequestCount = pendingRequestCount,
        ),
        now = now,
    )

    @Test
    fun `a listing nobody runs is counted, because nothing booked there can be confirmed`() {
        val loaded = state(
            restaurants = listOf(
                Restaurant(id = "r1", ownerUserId = "u1"),
                Restaurant(id = "r2"),
                Restaurant(id = "r3"),
            ),
        )

        assertEquals(3, loaded.restaurantCount)
        assertEquals(2, loaded.unclaimedRestaurantCount)
    }

    @Test
    fun `covers and bookings are different numbers and both are reported`() {
        val loaded = state(
            reservations = listOf(booking(partySize = 4), booking(partySize = 2)),
        )

        assertEquals(2, loaded.bookingCount)
        assertEquals(6, loaded.coverCount)
    }

    @Test
    fun `a cancelled booking is a booking but not a cover`() {
        val loaded = state(
            reservations = listOf(
                booking(partySize = 4),
                booking(partySize = 4, status = ReservationStatus.CANCELLED),
            ),
        )

        assertEquals(2, loaded.bookingCount)
        assertEquals(4, loaded.coverCount)
        assertEquals(0.5f, loaded.cancellationRate, 0.001f)
    }

    @Test
    fun `no-shows and walk-ins are reported as shares of every booking made`() {
        val loaded = state(
            reservations = listOf(
                booking(status = ReservationStatus.NO_SHOW),
                booking(isWalkIn = true),
                booking(),
                booking(),
            ),
        )

        assertEquals(0.25f, loaded.noShowRate, 0.001f)
        assertEquals(0.25f, loaded.walkInShare, 0.001f)
    }

    @Test
    fun `the day series is as long as the window asked for`() {
        val loaded = state(reservations = listOf(booking()))

        assertEquals(MetricsWindow.MONTH.days, loaded.coversByDay.size)
        assertEquals(
            MetricsWindow.WEEK.days,
            loaded.copy(window = MetricsWindow.WEEK).coversByDay.size,
        )
    }

    @Test
    fun `the busiest hour is surfaced for the chart's caption`() {
        val loaded = state(
            reservations = listOf(
                booking(hour = 12, partySize = 2),
                booking(hour = 20, partySize = 8),
            ),
        )

        assertEquals(20, loaded.busiestHour?.hour)
        assertEquals(12, loaded.coversByHour.first().hour)
    }

    @Test
    fun `an empty window says so rather than drawing flat charts`() {
        assertTrue(state().hasNoBookings)
        assertFalse(state(reservations = listOf(booking())).hasNoBookings)
    }

    @Test
    fun `nothing is called empty while it is still loading`() {
        assertFalse(SystemMetricsUiState(isLoading = true, now = now).hasNoBookings)
    }

    @Test
    fun `the counts the platform cannot derive come straight from the snapshot`() {
        val loaded = state(dinerCount = 42, pendingRequestCount = 3)

        assertEquals(42, loaded.dinerCount)
        assertEquals(3, loaded.pendingRequestCount)
    }

    @Test
    fun `every window is labelled and none of them is empty`() {
        assertTrue(MetricsWindow.entries.all { it.label.isNotBlank() && it.days > 0 })
    }
}
