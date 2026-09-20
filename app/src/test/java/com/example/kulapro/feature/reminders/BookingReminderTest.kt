package com.example.kulapro.feature.reminders

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookingReminderTest {

    private val now = 1_700_000_000_000L

    private fun hours(count: Long) = TimeUnit.HOURS.toMillis(count)

    @Test
    fun `reminds the chosen number of hours before the sitting`() {
        val delay = BookingReminder.delayMillis(
            startsAtMillis = now + hours(10),
            nowMillis = now,
            leadHours = 3,
        )

        assertEquals(hours(7), delay)
    }

    @Test
    fun `nudges shortly when the booking is inside the lead time`() {
        // Booked for tonight, two hours out, with a three hour lead. Still worth a nudge,
        // just not in the same breath as the confirmation screen.
        val delay = BookingReminder.delayMillis(
            startsAtMillis = now + hours(2),
            nowMillis = now,
            leadHours = 3,
        )

        assertEquals(BookingReminder.MINIMUM_DELAY_MILLIS, delay)
    }

    @Test
    fun `nudges shortly when the sitting is almost immediate`() {
        val delay = BookingReminder.delayMillis(
            startsAtMillis = now + TimeUnit.MINUTES.toMillis(5),
            nowMillis = now,
            leadHours = 1,
        )

        assertEquals(BookingReminder.MINIMUM_DELAY_MILLIS, delay)
    }

    @Test
    fun `says nothing about a sitting that has already passed`() {
        // A notification about dinner last Tuesday is noise, not a reminder.
        assertNull(
            BookingReminder.delayMillis(
                startsAtMillis = now - hours(1),
                nowMillis = now,
                leadHours = 3,
            ),
        )
    }

    @Test
    fun `says nothing about a sitting starting exactly now`() {
        assertNull(BookingReminder.delayMillis(now, now, leadHours = 3))
    }

    @Test
    fun `honours a longer lead for a booking far enough out`() {
        val delay = BookingReminder.delayMillis(
            startsAtMillis = now + hours(48),
            nowMillis = now,
            leadHours = 24,
        )

        assertEquals(hours(24), delay)
    }

    @Test
    fun `names one reminder per booking, so rescheduling replaces it`() {
        assertEquals("booking-reminder-abc", BookingReminder.workName("abc"))
        assert(BookingReminder.workName("abc") != BookingReminder.workName("abd"))
    }
}
