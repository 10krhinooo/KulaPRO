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
    val status: String = ReservationStatus.PENDING.name,
    val notes: String = "",
    val createdAt: Timestamp = Timestamp.now(),
) {
    val statusEnum: ReservationStatus
        get() = ReservationStatus.entries.firstOrNull { it.name == status }
            ?: ReservationStatus.PENDING
}

enum class ReservationStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    COMPLETED,
    NO_SHOW,
    ;

    /** Statuses that occupy a seat, and so count against a slot's capacity. */
    val occupiesCapacity: Boolean
        get() = this == PENDING || this == CONFIRMED
}
