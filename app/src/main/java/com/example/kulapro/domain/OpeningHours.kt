package com.example.kulapro.domain

import java.util.Calendar
import java.util.Date

/**
 * A restaurant's trading hours for one day, and the string they are stored as.
 *
 * Stored as "HH:mm-HH:mm" keyed by lowercase day name, with a missing key meaning closed.
 * Parsing lives here rather than inside [AvailabilityCalculator] because the capacity screen
 * has to write exactly what the calculator reads: two implementations of the same format
 * eventually disagree, and the way that shows up is a restaurant taking bookings for hours
 * it is shut.
 */
data class OpeningHours(val openMinutes: Int, val closeMinutes: Int) {

    /** The stored form, which is what the availability calculation reads back. */
    fun asStoredRange(): String = "${formatMinutes(openMinutes)}-${formatMinutes(closeMinutes)}"

    companion object {

        /** Monday first, because a trading week is read that way, not Sunday first. */
        val DAY_KEYS = listOf(
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
        )

        /** Parses a stored "HH:mm-HH:mm" range, or null if it is not one. */
        fun parseRange(stored: String?): OpeningHours? {
            val parts = stored?.split("-") ?: return null
            if (parts.size != 2) return null
            val open = parseMinutes(parts[0]) ?: return null
            val close = parseMinutes(parts[1]) ?: return null
            // A close that is not after the open describes no trading hours at all, which is
            // the same as being shut and is better reported as invalid than as a day of zero
            // length.
            if (close <= open) return null
            return OpeningHours(open, close)
        }

        /** Parses a single "HH:mm", or null. Minutes from midnight, so ranges compare simply. */
        fun parseMinutes(value: String): Int? {
            val segments = value.trim().split(":")
            if (segments.size != 2) return null
            val hour = segments[0].toIntOrNull() ?: return null
            val minute = segments[1].toIntOrNull() ?: return null
            if (hour !in 0..<HOURS_PER_DAY || minute !in 0..<MINUTES_PER_HOUR) return null
            return hour * MINUTES_PER_HOUR + minute
        }

        fun formatMinutes(minuteOfDay: Int): String =
            "%02d:%02d".format(minuteOfDay / MINUTES_PER_HOUR, minuteOfDay % MINUTES_PER_HOUR)

        /** The stored key for a date, so the calculator and the editor agree on the day. */
        fun dayKeyFor(day: Date): String {
            val calendar = Calendar.getInstance().apply { time = day }
            return when (calendar.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> "monday"
                Calendar.TUESDAY -> "tuesday"
                Calendar.WEDNESDAY -> "wednesday"
                Calendar.THURSDAY -> "thursday"
                Calendar.FRIDAY -> "friday"
                Calendar.SATURDAY -> "saturday"
                else -> "sunday"
            }
        }

        /** "monday" as "Monday", for a label rather than a key. */
        fun displayName(dayKey: String): String =
            dayKey.replaceFirstChar { it.uppercase() }
    }
}

internal const val HOURS_PER_DAY = 24
internal const val MINUTES_PER_HOUR = 60
