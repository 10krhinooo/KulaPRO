package com.example.kulapro.feature.reservations

import com.example.kulapro.data.model.Reservation
import com.example.kulapro.data.model.ReservationStatus
import com.google.firebase.Timestamp
import java.util.Calendar
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReservationsUiStateTest {

    /** Midday on a Wednesday, so "tomorrow" and "in three days" are unambiguous. */
    private val now = Calendar.getInstance().apply {
        set(2026, Calendar.SEPTEMBER, 16, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.time

    private fun at(offsetMinutes: Int): Timestamp =
        Timestamp(Date(now.time + offsetMinutes * 60L * 1000L))

    private fun booking(
        id: String,
        offsetMinutes: Int,
        status: ReservationStatus = ReservationStatus.CONFIRMED,
    ) = Reservation(id = id, startsAt = at(offsetMinutes), status = status.name)

    private fun state(vararg bookings: Reservation) = ReservationsUiState(
        reservations = bookings.toList(),
        isLoading = false,
        isSignedIn = true,
        now = now,
    )

    @Test
    fun `upcoming runs soonest first, which is the question the screen answers`() {
        val loaded = state(
            booking("later", offsetMinutes = 4320),
            booking("soon", offsetMinutes = 120),
            booking("middle", offsetMinutes = 1440),
        )

        assertEquals(listOf("soon", "middle", "later"), loaded.upcoming.map { it.id })
    }

    @Test
    fun `history runs most recent first, which is how a past is read`() {
        val loaded = state(
            booking("old", offsetMinutes = -10_000, status = ReservationStatus.COMPLETED),
            booking("recent", offsetMinutes = -120, status = ReservationStatus.COMPLETED),
        )

        assertEquals(listOf("recent", "old"), loaded.past.map { it.id })
    }

    @Test
    fun `a cancelled booking is history however far off it was`() {
        val loaded = state(booking("off", 4320, status = ReservationStatus.CANCELLED))

        assertTrue(loaded.upcoming.isEmpty())
        assertEquals(listOf("off"), loaded.past.map { it.id })
    }

    @Test
    fun `a sitting that has passed is history even if nobody marked it completed`() {
        // Restaurants forget to tap the button. Leaving last week's dinner under "upcoming"
        // because of that would be wrong in the way a diner notices first.
        val loaded = state(booking("yesterday", -1440, status = ReservationStatus.CONFIRMED))

        assertTrue(loaded.upcoming.isEmpty())
        assertEquals(listOf("yesterday"), loaded.past.map { it.id })
    }

    @Test
    fun `the next booking is the soonest one still to happen`() {
        val loaded = state(booking("later", 4320), booking("soon", 120))

        assertEquals("soon", loaded.next?.id)
        assertEquals(listOf("later"), loaded.laterUpcoming.map { it.id })
    }

    @Test
    fun `with nothing coming there is no next booking`() {
        val loaded = state(booking("gone", -120, status = ReservationStatus.COMPLETED))

        assertNull(loaded.next)
    }

    @Test
    fun `the visible list follows the selected tab`() {
        val loaded = state(
            booking("soon", 120),
            booking("gone", -120, status = ReservationStatus.COMPLETED),
        )

        assertEquals(listOf("soon"), loaded.visible.map { it.id })
        assertEquals(
            listOf("gone"),
            loaded.copy(tab = ReservationTab.PAST).visible.map { it.id },
        )
    }

    @Test
    fun `an empty tab is empty, and a loading one is not`() {
        assertTrue(state().isEmpty)
        assertFalse(ReservationsUiState(isLoading = true, now = now).isEmpty)
    }

    @Test
    fun `a booking still to come can be changed`() {
        val loaded = state()

        assertTrue(loaded.canChange(booking("soon", 120)))
    }

    @Test
    fun `a booking whose sitting has started cannot be changed`() {
        val loaded = state()

        assertFalse(loaded.canChange(booking("started", -5)))
    }

    @Test
    fun `a cancelled booking cannot be changed`() {
        val loaded = state()

        assertFalse(loaded.canChange(booking("off", 120, ReservationStatus.CANCELLED)))
    }

    @Test
    fun `only a completed visit can be reviewed, which is what the rules accept`() {
        val loaded = state()

        assertTrue(loaded.canReview(booking("done", -120, ReservationStatus.COMPLETED)))
        assertFalse(loaded.canReview(booking("soon", 120)))
        assertFalse(loaded.canReview(booking("missed", -120, ReservationStatus.NO_SHOW)))
    }

    @Test
    fun `a sitting within the hour counts down in minutes`() {
        val loaded = state()

        assertEquals("In 30 min", loaded.countdownFor(booking("soon", 30)))
    }

    @Test
    fun `a sitting later the same day says today`() {
        val loaded = state()

        assertEquals("Today", loaded.countdownFor(booking("tonight", 420)))
    }

    @Test
    fun `a sitting the next day says tomorrow`() {
        val loaded = state()

        assertEquals("Tomorrow", loaded.countdownFor(booking("tomorrow", 1440)))
    }

    @Test
    fun `a sitting later in the week counts down in days`() {
        val loaded = state()

        assertEquals("In 3 days", loaded.countdownFor(booking("thursday", 4320)))
    }

    @Test
    fun `a sitting weeks away counts down in weeks`() {
        val loaded = state()

        assertEquals("In 2 weeks", loaded.countdownFor(booking("far", 60 * 24 * 10)))
    }

    @Test
    fun `a sitting that has started reads as now rather than as a negative countdown`() {
        val loaded = state()

        assertEquals("Now", loaded.countdownFor(booking("started", -5)))
    }

    @Test
    fun `both tabs are labelled`() {
        assertEquals("Upcoming", ReservationTab.UPCOMING.label)
        assertEquals("History", ReservationTab.PAST.label)
    }
}
