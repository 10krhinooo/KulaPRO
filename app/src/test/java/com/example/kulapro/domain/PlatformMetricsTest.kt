package com.example.kulapro.domain

import com.example.kulapro.data.model.Reservation
import com.example.kulapro.data.model.ReservationStatus
import com.google.firebase.Timestamp
import java.util.Calendar
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformMetricsTest {

    /** A Wednesday lunchtime, so "yesterday" and "three days ago" are unambiguous. */
    private val now = Calendar.getInstance().apply {
        set(2026, Calendar.SEPTEMBER, 16, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.time

    private fun at(daysAgo: Int, hour: Int): Timestamp {
        val calendar = Calendar.getInstance().apply {
            time = now
            add(Calendar.DAY_OF_YEAR, -daysAgo)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return Timestamp(calendar.time)
    }

    private fun booking(
        daysAgo: Int = 0,
        hour: Int = 19,
        partySize: Int = 2,
        status: ReservationStatus = ReservationStatus.COMPLETED,
        isWalkIn: Boolean = false,
    ) = Reservation(
        startsAt = at(daysAgo, hour),
        partySize = partySize,
        status = status.name,
        isWalkIn = isWalkIn,
    )

    @Test
    fun `a cancelled booking took no seats, so it is not a cover`() {
        val bookings = listOf(
            booking(partySize = 4),
            booking(partySize = 6, status = ReservationStatus.CANCELLED),
        )

        assertEquals(4, PlatformMetrics.covers(bookings))
    }

    @Test
    fun `a no-show took no seats either`() {
        val bookings = listOf(
            booking(partySize = 2),
            booking(partySize = 8, status = ReservationStatus.NO_SHOW),
        )

        assertEquals(2, PlatformMetrics.covers(bookings))
    }

    @Test
    fun `a booking still to happen counts, because the seats are held`() {
        val bookings = listOf(booking(partySize = 3, status = ReservationStatus.CONFIRMED))

        assertEquals(3, PlatformMetrics.covers(bookings))
    }

    @Test
    fun `covers by day runs oldest first and ends today`() {
        val days = PlatformMetrics.coversByDay(
            reservations = listOf(booking(daysAgo = 0), booking(daysAgo = 2)),
            days = 3,
            now = now,
        )

        assertEquals(3, days.size)
        assertEquals(startOfDay(now), days.last().day)
        assertEquals(2, days.first().covers)
        assertEquals(2, days.last().covers)
    }

    @Test
    fun `a day with nothing booked is still in the series`() {
        // A chart that drops empty days draws a quiet Tuesday as though it never happened,
        // and makes a falling week look flat.
        val days = PlatformMetrics.coversByDay(
            reservations = listOf(booking(daysAgo = 0)),
            days = 3,
            now = now,
        )

        assertEquals(listOf(0, 0, 2), days.map { it.covers })
        assertEquals(listOf(0, 0, 1), days.map { it.bookings })
    }

    @Test
    fun `a booking older than the window is not charted`() {
        val days = PlatformMetrics.coversByDay(
            reservations = listOf(booking(daysAgo = 30)),
            days = 7,
            now = now,
        )

        assertEquals(0, days.sumOf { it.covers })
    }

    @Test
    fun `a window of no days charts nothing rather than throwing`() {
        assertTrue(PlatformMetrics.coversByDay(listOf(booking()), days = 0, now = now).isEmpty())
    }

    @Test
    fun `covers by hour is trimmed to the hours actually used`() {
        val byHour = PlatformMetrics.coversByHour(
            listOf(
                booking(hour = 12, partySize = 2),
                booking(hour = 19, partySize = 4),
            ),
        )

        // Noon to seven, not midnight to midnight: the shape of a service should fill the
        // chart rather than sit in a third of it.
        assertEquals(12, byHour.first().hour)
        assertEquals(19, byHour.last().hour)
    }

    @Test
    fun `a quiet hour inside the range is kept, because the dip is the point`() {
        val byHour = PlatformMetrics.coversByHour(
            listOf(booking(hour = 12), booking(hour = 14)),
        )

        assertEquals(listOf(12, 13, 14), byHour.map { it.hour })
        assertEquals(0, byHour[1].covers)
    }

    @Test
    fun `covers in the same hour on different days add up`() {
        val byHour = PlatformMetrics.coversByHour(
            listOf(
                booking(daysAgo = 0, hour = 19, partySize = 2),
                booking(daysAgo = 3, hour = 19, partySize = 5),
            ),
        )

        assertEquals(listOf(HourLoad(19, 7)), byHour)
    }

    @Test
    fun `with nothing booked there are no hours and no busiest one`() {
        assertTrue(PlatformMetrics.coversByHour(emptyList()).isEmpty())
        assertNull(PlatformMetrics.busiestHour(emptyList()))
    }

    @Test
    fun `the busiest hour is the one taking the most covers`() {
        val busiest = PlatformMetrics.busiestHour(
            listOf(
                booking(hour = 12, partySize = 2),
                booking(hour = 20, partySize = 9),
                booking(hour = 21, partySize = 4),
            ),
        )

        assertEquals(20, busiest?.hour)
        assertEquals(9, busiest?.covers)
    }

    @Test
    fun `the cancellation rate is measured against every booking made`() {
        val bookings = listOf(
            booking(),
            booking(status = ReservationStatus.CANCELLED),
            booking(status = ReservationStatus.CANCELLED),
            booking(),
        )

        assertEquals(
            0.5f,
            PlatformMetrics.shareEnding(bookings, ReservationStatus.CANCELLED),
            0.001f,
        )
    }

    @Test
    fun `rates over an empty window are zero rather than a division by zero`() {
        assertEquals(
            0f,
            PlatformMetrics.shareEnding(emptyList(), ReservationStatus.NO_SHOW),
            0.001f,
        )
        assertEquals(0f, PlatformMetrics.walkInShare(emptyList()), 0.001f)
    }

    @Test
    fun `the walk-in share says how much of the room never used the app`() {
        val bookings = listOf(
            booking(isWalkIn = true),
            booking(isWalkIn = true),
            booking(),
            booking(),
        )

        assertEquals(0.5f, PlatformMetrics.walkInShare(bookings), 0.001f)
    }
}
