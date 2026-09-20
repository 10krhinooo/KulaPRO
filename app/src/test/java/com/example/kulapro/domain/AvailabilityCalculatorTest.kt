package com.example.kulapro.domain

import com.example.kulapro.data.model.Restaurant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

class AvailabilityCalculatorTest {

    /** A Wednesday, so the weekday opening-hours entry applies. */
    private fun wednesday(): Date = Calendar.getInstance().apply {
        set(2026, Calendar.SEPTEMBER, 23, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.time

    private fun restaurant(
        capacity: Int = 10,
        slotMinutes: Int = 60,
        hours: Map<String, String> = mapOf("wednesday" to "12:00-15:00"),
    ) = Restaurant(
        id = "r1",
        name = "Test",
        capacityPerSlot = capacity,
        slotDurationMinutes = slotMinutes,
        openingHours = hours,
    )

    /** Seats taken at a given time, keyed the way the slot counters are. */
    private fun takenAt(hour: Int, minute: Int, seats: Int): Pair<Long, Int> {
        val time = Calendar.getInstance().apply {
            time = wednesday()
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return (time / 1000) to seats
    }

    @Test
    fun `generates one slot per interval within opening hours`() {
        val slots = AvailabilityCalculator.slotsFor(
            restaurant = restaurant(),
            day = wednesday(),
            seatsTaken = emptyMap(),
            partySize = 2,
        )
        // 12:00-15:00 at 60 minutes yields 12:00, 13:00, 14:00. The 15:00 seating would run
        // past closing, so it must not be offered.
        assertEquals(listOf("12:00 PM", "1:00 PM", "2:00 PM"), slots.map { it.label })
    }

    @Test
    fun `closed days produce no slots`() {
        val slots = AvailabilityCalculator.slotsFor(
            restaurant = restaurant(hours = mapOf("monday" to "12:00-15:00")),
            day = wednesday(),
            seatsTaken = emptyMap(),
            partySize = 2,
        )
        assertTrue(slots.isEmpty())
    }

    @Test
    fun `existing bookings reduce the seats left in their own slot only`() {
        val slots = AvailabilityCalculator.slotsFor(
            restaurant = restaurant(capacity = 10),
            day = wednesday(),
            seatsTaken = mapOf(takenAt(13, 0, 4)),
            partySize = 2,
        )
        assertEquals(10, slots.first { it.label == "12:00 PM" }.seatsRemaining)
        assertEquals(6, slots.first { it.label == "1:00 PM" }.seatsRemaining)
        assertEquals(10, slots.first { it.label == "2:00 PM" }.seatsRemaining)
    }

    @Test
    fun `a full slot is not available`() {
        val slots = AvailabilityCalculator.slotsFor(
            restaurant = restaurant(capacity = 4),
            day = wednesday(),
            seatsTaken = mapOf(takenAt(12, 0, 4)),
            partySize = 1,
        )
        assertFalse(slots.first { it.label == "12:00 PM" }.isAvailable)
    }

    @Test
    fun `an empty counter leaves every slot open`() {
        val slots = AvailabilityCalculator.slotsFor(
            restaurant = restaurant(capacity = 4),
            day = wednesday(),
            // Cancelled bookings return their seats to the counter, so nothing is held.
            seatsTaken = emptyMap(),
            partySize = 2,
        )
        assertTrue(slots.first { it.label == "12:00 PM" }.isAvailable)
        assertTrue(slots.first { it.label == "1:00 PM" }.isAvailable)
    }

    @Test
    fun `held seats make a slot unavailable`() {
        // A table held but not yet confirmed is not a free table.
        val slots = AvailabilityCalculator.slotsFor(
            restaurant = restaurant(capacity = 4),
            day = wednesday(),
            seatsTaken = mapOf(takenAt(12, 0, 4)),
            partySize = 1,
        )
        assertFalse(slots.first { it.label == "12:00 PM" }.isAvailable)
    }

    @Test
    fun `a slot with room but not enough for this party is unavailable`() {
        val slots = AvailabilityCalculator.slotsFor(
            restaurant = restaurant(capacity = 10),
            day = wednesday(),
            seatsTaken = mapOf(takenAt(12, 0, 8)),
            partySize = 4,
        )
        // Two seats remain, which is real capacity but cannot seat a party of four.
        assertFalse(slots.first { it.label == "12:00 PM" }.isAvailable)
        assertTrue(slots.first { it.label == "1:00 PM" }.isAvailable)
    }

    @Test
    fun `counters are matched to their slot start`() {
        val slots = AvailabilityCalculator.slotsFor(
            restaurant = restaurant(capacity = 6),
            day = wednesday(),
            seatsTaken = mapOf(takenAt(12, 0, 6)),
            partySize = 1,
        )
        assertFalse(slots.first { it.label == "12:00 PM" }.isAvailable)
    }

    @Test
    fun `malformed opening hours are treated as closed rather than crashing`() {
        listOf("", "12:00", "not-a-range", "25:00-26:00", "15:00-12:00").forEach { raw ->
            val slots = AvailabilityCalculator.slotsFor(
                restaurant = restaurant(hours = mapOf("wednesday" to raw)),
                day = wednesday(),
                seatsTaken = emptyMap(),
                partySize = 2,
            )
            assertTrue("expected no slots for '$raw'", slots.isEmpty())
        }
    }

    @Test
    fun `zero slot duration does not loop forever`() {
        val slots = AvailabilityCalculator.slotsFor(
            restaurant = restaurant(slotMinutes = 0),
            day = wednesday(),
            seatsTaken = emptyMap(),
            partySize = 2,
        )
        assertTrue(slots.isEmpty())
    }

    @Test
    fun `every day of the week maps to its own opening hours`() {
        // Only the Wednesday arm was exercised before, so a typo in any other day's
        // mapping would have shipped unnoticed.
        val days = listOf(
            Calendar.MONDAY to "monday",
            Calendar.TUESDAY to "tuesday",
            Calendar.WEDNESDAY to "wednesday",
            Calendar.THURSDAY to "thursday",
            Calendar.FRIDAY to "friday",
            Calendar.SATURDAY to "saturday",
            Calendar.SUNDAY to "sunday",
        )

        days.forEach { (calendarDay, name) ->
            val date = Calendar.getInstance().apply {
                time = wednesday()
                set(Calendar.DAY_OF_WEEK, calendarDay)
            }.time

            val open = AvailabilityCalculator.slotsFor(
                restaurant = restaurant(hours = mapOf(name to "12:00-14:00")),
                day = date,
                seatsTaken = emptyMap(),
                partySize = 2,
            )
            assertEquals(
                "$name should be open",
                listOf("12:00 PM", "1:00 PM"),
                open.map { it.label },
            )

            // The same date against a different day's hours must read as closed.
            val other = if (name == "monday") "tuesday" else "monday"
            val closed = AvailabilityCalculator.slotsFor(
                restaurant = restaurant(hours = mapOf(other to "12:00-14:00")),
                day = date,
                seatsTaken = emptyMap(),
                partySize = 2,
            )
            assertTrue("$name should be closed under $other hours", closed.isEmpty())
        }
    }

    @Test
    fun `zero slot duration with existing bookings still returns no slots`() {
        // Exercises the guard in slot alignment: a zero duration must not divide by zero
        // while grouping existing reservations.
        val slots = AvailabilityCalculator.slotsFor(
            restaurant = restaurant(slotMinutes = 0),
            day = wednesday(),
            seatsTaken = mapOf(takenAt(12, 0, 2)),
            partySize = 2,
        )
        assertTrue(slots.isEmpty())
    }

    @Test
    fun `closing time that does not divide evenly drops the partial slot`() {
        val slots = AvailabilityCalculator.slotsFor(
            restaurant = restaurant(hours = mapOf("wednesday" to "12:00-14:30")),
            day = wednesday(),
            seatsTaken = emptyMap(),
            partySize = 2,
        )
        // 14:00 would run to 15:00, past a 14:30 close, so it is not offered.
        assertEquals(listOf("12:00 PM", "1:00 PM"), slots.map { it.label })
    }

    @Test
    fun `minutes out of range are rejected as closed`() {
        listOf("12:60-14:00", "12:00-14:99", "-1:00-14:00", "ab:cd-14:00").forEach { raw ->
            val slots = AvailabilityCalculator.slotsFor(
                restaurant = restaurant(hours = mapOf("wednesday" to raw)),
                day = wednesday(),
                seatsTaken = emptyMap(),
                partySize = 2,
            )
            assertTrue("expected no slots for '$raw'", slots.isEmpty())
        }
    }

    @Test
    fun `a malformed closing time alone is enough to close the day`() {
        val slots = AvailabilityCalculator.slotsFor(
            restaurant = restaurant(hours = mapOf("wednesday" to "12:00-notatime")),
            day = wednesday(),
            seatsTaken = emptyMap(),
            partySize = 2,
        )
        assertTrue(slots.isEmpty())
    }

    @Test
    fun `midnight formats as twelve AM not zero`() {
        val slots = AvailabilityCalculator.slotsFor(
            restaurant = restaurant(hours = mapOf("wednesday" to "00:00-02:00")),
            day = wednesday(),
            seatsTaken = emptyMap(),
            partySize = 1,
        )
        assertEquals(listOf("12:00 AM", "1:00 AM"), slots.map { it.label })
    }
}
