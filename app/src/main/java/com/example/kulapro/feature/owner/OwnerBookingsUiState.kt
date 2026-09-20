package com.example.kulapro.feature.owner

import com.example.kulapro.data.model.Reservation
import com.example.kulapro.data.model.ReservationStatus
import com.example.kulapro.ui.components.UiMessage
import java.util.Date

/** What the bookings screen shows, filtered to one day and optionally one status. */
data class OwnerBookingsUiState(
    val restaurantName: String = "",
    val capacityPerSlot: Int = 0,
    val days: List<Date> = emptyList(),
    val selectedDate: Date? = null,
    val bookings: List<Reservation> = emptyList(),
    val filter: BookingFilter = BookingFilter.ALL,
    val isLoading: Boolean = true,
    val busyReservationId: String? = null,
    val isAddingWalkIn: Boolean = false,
    val message: UiMessage? = null,
) {
    /** The rows actually drawn, after the filter. */
    val visible: List<Reservation>
        get() = bookings.filter(filter::matches)

    /** Seats taken across the day by everything still occupying a table. */
    val covers: Int
        get() = bookings.filter { it.statusEnum.occupiesCapacity }.sumOf { it.partySize }

    /** Parties still to arrive, which is what an owner scans the screen for. */
    val expected: Int
        get() = bookings.count {
            it.statusEnum == ReservationStatus.PENDING ||
                it.statusEnum == ReservationStatus.CONFIRMED
        }

    val seated: Int get() = bookings.count { it.statusEnum == ReservationStatus.SEATED }

    val walkIns: Int get() = bookings.count { it.isWalkIn }

    /**
     * Bookings that arrived but were never marked, as a share of those that should have.
     *
     * Counted over settled bookings only, so a quiet evening still in progress does not
     * read as a perfect record.
     */
    val noShowPercent: Int
        get() {
            val settled = bookings.count {
                it.statusEnum == ReservationStatus.COMPLETED ||
                    it.statusEnum == ReservationStatus.NO_SHOW ||
                    it.statusEnum == ReservationStatus.SEATED
            }
            if (settled == 0) return 0
            val missed = bookings.count { it.statusEnum == ReservationStatus.NO_SHOW }
            return missed * PERCENT / settled
        }
}

/** The cuts of a service an owner actually asks for. */
enum class BookingFilter {
    ALL,

    /** Still to arrive, or at the table now. */
    OPEN,
    PENDING,
    SEATED,
    FINISHED,
    ;

    fun matches(reservation: Reservation): Boolean = when (this) {
        ALL -> true
        OPEN -> reservation.statusEnum.isOpen
        PENDING -> reservation.statusEnum == ReservationStatus.PENDING
        SEATED -> reservation.statusEnum == ReservationStatus.SEATED
        FINISHED -> !reservation.statusEnum.isOpen
    }

    val label: String
        get() = when (this) {
            ALL -> "All"
            OPEN -> "Open"
            PENDING -> "To confirm"
            SEATED -> "At table"
            FINISHED -> "Finished"
        }
}

private const val PERCENT = 100
