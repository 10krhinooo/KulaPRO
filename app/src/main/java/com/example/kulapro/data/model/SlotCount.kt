package com.example.kulapro.data.model

import com.google.firebase.firestore.DocumentId

/**
 * How many seats are already taken in one sitting.
 *
 * Availability is read from these aggregates rather than by querying reservations directly.
 * A diner needs to know whether a slot is full, but has no business reading who else is
 * booked, their party size or their phone number. Counting from the reservations collection
 * would require granting exactly that, so the count is kept separately and holds no
 * personal data.
 */
data class SlotCount(
    @DocumentId val id: String = "",
    val restaurantId: String = "",
    /** Slot start as epoch seconds, so the key is unambiguous across time zones. */
    val startsAtSeconds: Long = 0,
    val seatsTaken: Int = 0,
    /**
     * Tables already claimed in this sitting.
     *
     * Kept next to the seat total rather than derived from reservations for the same reason
     * the total is: a diner has to see which tables are gone, and must not be able to read
     * the bookings that took them.
     */
    val takenTableIds: List<String> = emptyList(),
)
