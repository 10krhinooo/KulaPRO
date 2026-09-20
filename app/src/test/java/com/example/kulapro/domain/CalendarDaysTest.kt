package com.example.kulapro.domain

import java.util.Calendar
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarDaysTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Date =
        Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

    @Test
    fun `the start of a day is midnight on that day`() {
        val start = startOfDay(at(2026, Calendar.SEPTEMBER, 16, 23, 59))
        val calendar = Calendar.getInstance().apply { time = start }

        assertEquals(16, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, calendar.get(Calendar.MINUTE))
        assertEquals(0, calendar.get(Calendar.MILLISECOND))
    }

    @Test
    fun `an instant already at midnight is left where it is`() {
        val midnight = at(2026, Calendar.SEPTEMBER, 16, 0)

        assertEquals(midnight, startOfDay(midnight))
    }

    @Test
    fun `days are counted by the date, not by elapsed hours`() {
        // An hour apart, and still a day apart. Counting elapsed time is what made a
        // booking seventy one hours away read as two days and one seventy three hours away
        // read as three, when both are the same Saturday.
        val lateTuesday = at(2026, Calendar.SEPTEMBER, 15, 23, 30)
        val earlyWednesday = at(2026, Calendar.SEPTEMBER, 16, 0, 30)

        assertEquals(1, calendarDaysBetween(lateTuesday, earlyWednesday))
    }

    @Test
    fun `the same day is zero days apart whatever the hour`() {
        assertEquals(
            0,
            calendarDaysBetween(
                at(2026, Calendar.SEPTEMBER, 16, 1),
                at(2026, Calendar.SEPTEMBER, 16, 22),
            ),
        )
    }

    @Test
    fun `counting backwards is negative rather than wrong`() {
        assertEquals(
            -2,
            calendarDaysBetween(
                at(2026, Calendar.SEPTEMBER, 16, 12),
                at(2026, Calendar.SEPTEMBER, 14, 12),
            ),
        )
    }

    @Test
    fun `the hour of the day is the local one`() {
        assertEquals(19, hourOfDay(at(2026, Calendar.SEPTEMBER, 16, 19, 45)))
        assertEquals(0, hourOfDay(at(2026, Calendar.SEPTEMBER, 16, 0, 1)))
    }
}
