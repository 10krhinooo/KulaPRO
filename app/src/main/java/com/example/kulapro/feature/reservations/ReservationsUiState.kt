package com.example.kulapro.feature.reservations

import com.example.kulapro.data.model.Reservation
import com.example.kulapro.data.model.ReservationStatus
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * The diner's bookings, split the way they are actually thought about.
 *
 * One flat list sorted newest first put last March's dinner above tonight's, which is the
 * wrong way round for the only question this screen exists to answer: where am I eating
 * next. Upcoming runs soonest first, history runs most recent first, and they are different
 * tabs because they are different questions.
 */
data class ReservationsUiState(
    val reservations: List<Reservation> = emptyList(),
    val tab: ReservationTab = ReservationTab.UPCOMING,
    val isLoading: Boolean = true,
    val isSignedIn: Boolean = false,
    val busyReservationId: String? = null,
    val reviewTarget: Reservation? = null,
    val isPostingReview: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    private val now: Date = Date(),
) {
    /** Still to happen, and not called off. Soonest first, because that is the next question. */
    val upcoming: List<Reservation>
        get() = reservations
            .filter { it.statusEnum.isOpen && it.startsAt.toDate().after(now) }
            .sortedBy { it.startsAt.seconds }

    /**
     * Everything settled, most recent first.
     *
     * A booking whose sitting has passed belongs here even if nobody marked it completed.
     * Leaving last week's dinner under "upcoming" because the restaurant forgot to tap a
     * button would make the screen wrong in the way a diner would notice first.
     */
    val past: List<Reservation>
        get() = reservations
            .filterNot { it.statusEnum.isOpen && it.startsAt.toDate().after(now) }
            .sortedByDescending { it.startsAt.seconds }

    val visible: List<Reservation>
        get() = if (tab == ReservationTab.UPCOMING) upcoming else past

    /** The one the screen leads with. Null when there is nothing coming. */
    val next: Reservation? get() = upcoming.firstOrNull()

    /** The upcoming bookings below the one being led with. */
    val laterUpcoming: List<Reservation> get() = upcoming.drop(1)

    val isEmpty: Boolean get() = !isLoading && visible.isEmpty()

    /** A visit that happened and has not been reviewed is worth asking about. */
    fun canReview(reservation: Reservation): Boolean =
        reservation.statusEnum == ReservationStatus.COMPLETED

    /** A booking can be changed or called off right up until its sitting starts. */
    fun canChange(reservation: Reservation): Boolean =
        reservation.statusEnum.occupiesCapacity && reservation.startsAt.toDate().after(now)

    /**
     * How far off the sitting is, in the words someone would use.
     *
     * "In 3 days" reads instantly; a date does not, and a countdown in hours is only useful
     * on the day. The point is that the screen answers "when" before it is asked.
     */
    fun countdownFor(reservation: Reservation): String {
        val startsAt = reservation.startsAt.toDate()
        val millis = startsAt.time - now.time
        if (millis <= 0) return "Now"

        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        // Calendar days rather than elapsed hours. Someone booked for Saturday says "in
        // three days" on Wednesday whatever the time, and counting elapsed time makes a
        // booking 71 hours away "in 2 days" and one 73 hours away "in 3".
        val days = calendarDaysBetween(now, startsAt)
        return when {
            minutes < MINUTES_PER_HOUR -> "In $minutes min"
            days == 0 -> "Today"
            days == 1 -> "Tomorrow"
            days < DAYS_PER_WEEK -> "In $days days"
            else -> "In ${days / DAYS_PER_WEEK + 1} weeks"
        }
    }
}

enum class ReservationTab(val label: String) {
    UPCOMING("Upcoming"),
    PAST("History"),
}

/** Whole days between two instants, counted by the date rather than by elapsed hours. */
private fun calendarDaysBetween(from: Date, to: Date): Int {
    val start = startOfDay(from)
    val end = startOfDay(to)
    return ((end.time - start.time) / MILLIS_PER_DAY).toInt()
}

private fun startOfDay(date: Date): Date = Calendar.getInstance().apply {
    time = date
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.time

private const val MINUTES_PER_HOUR = 60
private const val DAYS_PER_WEEK = 7
private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
