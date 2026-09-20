package com.example.kulapro.domain

import com.example.kulapro.data.model.Restaurant
import java.util.Calendar
import java.util.Date

/**
 * Works out which time slots a restaurant can still seat.
 *
 * This is the piece the first version was missing entirely: it accepted any date, any time,
 * and any party size unconditionally, which is why the app could not deliver the
 * overcrowding-reduction it promised. Pure and free of Firebase types so it is directly
 * unit-testable.
 */
object AvailabilityCalculator {

    private const val MILLIS_PER_SECOND = 1000

    data class Slot(
        val label: String,
        val startsAt: Date,
        val seatsRemaining: Int,
    ) {
        val isAvailable: Boolean get() = seatsRemaining > 0
    }

    /**
     * @param seatsTaken seats already booked, keyed by slot start in epoch seconds. Read from
     *   public per slot counters rather than from other people's reservations, so a diner
     *   never needs permission to see who else is booked.
     */
    fun slotsFor(
        restaurant: Restaurant,
        day: Date,
        seatsTaken: Map<Long, Int>,
        partySize: Int,
    ): List<Slot> {
        val hours = openingHoursFor(restaurant, day) ?: return emptyList()

        return generateSlots(day, hours, restaurant.slotDurationMinutes).map { start ->
            val used = seatsTaken[start.time / MILLIS_PER_SECOND] ?: 0
            val remaining = (restaurant.capacityPerSlot - used).coerceAtLeast(0)
            Slot(
                label = formatTime(start),
                startsAt = start,
                // A slot with fewer seats left than the party needs is not bookable by them,
                // even though it is not strictly full.
                seatsRemaining = if (remaining >= partySize) remaining else 0,
            )
        }
    }

    private fun openingHoursFor(restaurant: Restaurant, day: Date): OpeningHours? =
        OpeningHours.parseRange(restaurant.openingHours[OpeningHours.dayKeyFor(day)])

    private fun generateSlots(
        day: Date,
        hours: OpeningHours,
        durationMinutes: Int,
    ): List<Date> {
        if (durationMinutes <= 0) return emptyList()
        val slots = mutableListOf<Date>()
        var minute = hours.openMinutes
        // The last seating must finish before closing, so stop a full slot short of close.
        while (minute + durationMinutes <= hours.closeMinutes) {
            slots += atMinuteOfDay(day, minute)
            minute += durationMinutes
        }
        return slots
    }

    private fun atMinuteOfDay(day: Date, minuteOfDay: Int): Date =
        Calendar.getInstance().apply {
            time = day
            set(Calendar.HOUR_OF_DAY, minuteOfDay / MINUTES_PER_HOUR)
            set(Calendar.MINUTE, minuteOfDay % MINUTES_PER_HOUR)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

    private fun formatTime(date: Date): String {
        val calendar = Calendar.getInstance().apply { time = date }
        val hour24 = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val suffix = if (hour24 < 12) "AM" else "PM"
        val hour12 = when {
            hour24 % 12 == 0 -> 12
            else -> hour24 % 12
        }
        return "%d:%02d %s".format(hour12, minute, suffix)
    }
}
