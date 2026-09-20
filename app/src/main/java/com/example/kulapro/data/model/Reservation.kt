package com.example.kulapro.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

/**
 * A table booking.
 *
 * [startsAt] is a real [Timestamp] rather than the display strings the first version stored
 * ("01/12/2024" plus "7:00 PM"). Without an ordered, comparable instant there is no way to
 * query "my upcoming reservations", to sort history, or to count how many bookings fall in a
 * slot, which is what availability depends on.
 */
data class Reservation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val restaurantId: String = "",
    /** Denormalised so reservation lists render without a second read per row. */
    val restaurantName: String = "",
    val startsAt: Timestamp = Timestamp.now(),
    val partySize: Int = 0,
    /** Empty when the restaurant has not mapped its floor, or the diner let it be assigned. */
    val tableId: String = "",
    val tableLabel: String = "",
    val status: String = ReservationStatus.PENDING.name,
    val notes: String = "",
    /**
     * A party the restaurant seated without a booking.
     *
     * Recorded so the capacity model reflects the room rather than only what came through
     * the app. Without it an owner's dashboard would show a half empty restaurant on the
     * busiest night of the week. These carry no userId, because there is no user.
     */
    val isWalkIn: Boolean = false,
    val createdAt: Timestamp = Timestamp.now(),
) {
    val statusEnum: ReservationStatus
        get() = ReservationStatus.entries.firstOrNull { it.name == status }
            ?: ReservationStatus.PENDING
}

enum class ReservationStatus {
    PENDING,
    CONFIRMED,

    /** They arrived and are at the table now. */
    SEATED,
    CANCELLED,
    COMPLETED,
    NO_SHOW,
    ;

    /**
     * Statuses that occupy a seat, and so count against a slot's capacity.
     *
     * A seated party is very much taking up a table, so it counts. A completed one has
     * left, and a no-show never came, so neither should keep blocking the sitting.
     */
    val occupiesCapacity: Boolean
        get() = this == PENDING || this == CONFIRMED || this == SEATED

    /** Whether the restaurant still has something to do about this booking. */
    val isOpen: Boolean
        get() = this == PENDING || this == CONFIRMED || this == SEATED

    /** What the restaurant would sensibly do next, in the order a service runs. */
    val nextActions: List<ReservationStatus>
        get() = when (this) {
            PENDING -> listOf(CONFIRMED, CANCELLED)
            CONFIRMED -> listOf(SEATED, NO_SHOW, CANCELLED)
            SEATED -> listOf(COMPLETED)
            CANCELLED, COMPLETED, NO_SHOW -> emptyList()
        }
}
