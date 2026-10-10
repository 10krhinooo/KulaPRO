package com.example.kulapro.feature.reminders

import java.util.concurrent.TimeUnit

/**
 * When a reminder for a sitting should fire, relative to now.
 *
 * Pure, so the awkward cases can be tested without a scheduler: a booking later today, one
 * already inside the lead time, and one whose sitting has already been and gone.
 */
object BookingReminder {

    /**
     * Milliseconds to wait before reminding, or null when there is no point reminding at all.
     *
     * A sitting already in the past gets nothing: a notification about dinner last Tuesday
     * is noise. A booking inside the lead time fires shortly rather than immediately, so a
     * notification does not land in the same breath as the confirmation screen.
     */
    fun delayMillis(
        startsAtMillis: Long,
        nowMillis: Long,
        leadHours: Int,
    ): Long? {
        if (startsAtMillis <= nowMillis) return null

        val leadMillis = TimeUnit.HOURS.toMillis(leadHours.toLong())
        val idealMillis = startsAtMillis - leadMillis
        return if (idealMillis <= nowMillis) {
            // Booked at short notice. Still worth a nudge, just not this second.
            MINIMUM_DELAY_MILLIS
        } else {
            idealMillis - nowMillis
        }
    }

    /** One reminder per booking, so rescheduling replaces rather than stacks. */
    fun workName(reservationId: String): String = "booking-reminder-$reservationId"

    /** A minute's grace, so the reminder never arrives alongside the confirmation. */
    internal val MINIMUM_DELAY_MILLIS = TimeUnit.MINUTES.toMillis(1)
}
