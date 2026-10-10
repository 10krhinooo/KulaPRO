package com.example.kulapro.domain

import com.example.kulapro.data.model.Reservation
import com.example.kulapro.data.model.ReservationStatus
import java.util.Date

/** Bookings and covers on one day. */
data class DayLoad(val day: Date, val bookings: Int, val covers: Int)

/** Covers starting in one hour of the day, across every day in the window. */
data class HourLoad(val hour: Int, val covers: Int)

/**
 * What the platform is doing, computed from bookings rather than stored.
 *
 * Kept out of the view model and away from Firestore so it can be tested against a list of
 * bookings with no backend and no clock of its own. Every function takes the window it is
 * asked about, because "the last fortnight" is a question the caller asks, not a fact about
 * the data.
 *
 * A cover is a seat taken. A cancelled booking took none and a no-show took none either, so
 * neither counts towards covers; both are counted separately, as rates, because a platform
 * losing a fifth of its bookings to no-shows is the thing worth seeing.
 */
object PlatformMetrics {

    /** Bookings that put someone in a seat, or still might. */
    fun kept(reservations: List<Reservation>): List<Reservation> =
        reservations.filterNot { it.statusEnum in LOST_STATUSES }

    fun covers(reservations: List<Reservation>): Int =
        kept(reservations).sumOf { it.partySize }

    /**
     * One entry per day in the window, oldest first, including the days nothing happened.
     *
     * Empty days are present on purpose: a chart that silently drops them draws a quiet
     * Tuesday as though it never existed and makes a falling week look flat.
     */
    fun coversByDay(reservations: List<Reservation>, days: Int, now: Date): List<DayLoad> {
        if (days <= 0) return emptyList()
        val today = startOfDay(now)
        val byDay = kept(reservations).groupBy { startOfDay(it.startsAt.toDate()).time }

        return (days - 1 downTo 0).map { back ->
            val day = Date(today.time - back * MILLIS_PER_DAY)
            val onThatDay = byDay[day.time].orEmpty()
            DayLoad(
                day = day,
                bookings = onThatDay.size,
                covers = onThatDay.sumOf { it.partySize },
            )
        }
    }

    /**
     * Covers by hour of the day, over the hours the platform is actually used.
     *
     * Trimmed to the first and last hour with anything in them rather than always running
     * midnight to midnight, so the shape of a dinner service fills the chart instead of
     * being squeezed into a third of it. Quiet hours inside that range are kept: a dip
     * between lunch and dinner is a real thing to see.
     */
    fun coversByHour(reservations: List<Reservation>): List<HourLoad> {
        val byHour = kept(reservations)
            .groupBy { hourOfDay(it.startsAt.toDate()) }
            .mapValues { (_, bookings) -> bookings.sumOf { it.partySize } }
            .filterValues { it > 0 }
        val first = byHour.keys.minOrNull() ?: return emptyList()
        val last = byHour.keys.max()

        return (first..last).map { hour -> HourLoad(hour, byHour[hour] ?: 0) }
    }

    /** The hour taking the most covers, or null when nothing has been booked. */
    fun busiestHour(reservations: List<Reservation>): HourLoad? =
        coversByHour(reservations).maxByOrNull { it.covers }

    /**
     * The share of bookings that ended in this status, 0 to 1.
     *
     * Measured against every booking in the window, cancellations included, because the
     * question is what happens to a booking once it is made.
     */
    fun shareEnding(reservations: List<Reservation>, status: ReservationStatus): Float {
        if (reservations.isEmpty()) return 0f
        return reservations.count { it.statusEnum == status }.toFloat() / reservations.size
    }

    /** Parties the restaurant recorded itself, rather than ones that came through the app. */
    fun walkInShare(reservations: List<Reservation>): Float {
        if (reservations.isEmpty()) return 0f
        return reservations.count { it.isWalkIn }.toFloat() / reservations.size
    }
}

/** Bookings that never put anyone in a seat. */
private val LOST_STATUSES = setOf(ReservationStatus.CANCELLED, ReservationStatus.NO_SHOW)
