package com.example.kulapro.feature.booking

import com.example.kulapro.data.model.RestaurantTable
import com.example.kulapro.domain.AvailabilityCalculator
import com.example.kulapro.ui.components.UiMessage
import java.util.Date

/**
 * Everything the booking screen draws, in one value.
 *
 * A single state object rather than a dozen separate flags, so an impossible combination
 * (submitting while nothing is selected, a chosen table on a slot that has changed) is a
 * bug in one place that a test can pin down, rather than something the screen is left to
 * reconcile at draw time.
 */
data class BookingUiState(
    val restaurantName: String = "",
    val days: List<Date> = emptyList(),
    val selectedDate: Date? = null,
    val partySize: Int = DEFAULT_PARTY_SIZE,
    val slots: List<AvailabilityCalculator.Slot> = emptyList(),
    val selectedSlot: AvailabilityCalculator.Slot? = null,
    val tables: List<RestaurantTable> = emptyList(),
    /** Tables already gone, per sitting, keyed by slot start in epoch seconds. */
    val takenBySlot: Map<Long, Set<String>> = emptyMap(),
    val takenTableIds: Set<String> = emptySet(),
    val selectedTable: RestaurantTable? = null,
    val isLoadingSlots: Boolean = true,
    val isSubmitting: Boolean = false,
    /** Set once the booking is written, and what the confirmation screen reads. */
    val confirmedLabel: String? = null,
    val message: UiMessage? = null,
) {
    /** The restaurant is closed that day: no sittings at all, rather than none left. */
    val isClosedToday: Boolean get() = !isLoadingSlots && slots.isEmpty()

    /** Every sitting exists but none can take this party. */
    val isFullyBooked: Boolean
        get() = !isLoadingSlots && slots.isNotEmpty() && slots.none { it.isAvailable }

    /** A table is only ever offered within a chosen sitting, and only if the floor is mapped. */
    val showsTablePlan: Boolean get() = tables.isNotEmpty() && selectedSlot != null

    val canSubmit: Boolean get() = selectedSlot != null && !isSubmitting && confirmedLabel == null
}

/**
 * Everything the booking screen can do.
 *
 * Grouped rather than passed one by one: the screen has one job, and a caller that has to
 * supply eight separate lambdas in the right order is easy to wire up wrongly.
 */
data class BookingActions(
    val onBack: () -> Unit = {},
    val onSelectDate: (Date) -> Unit = {},
    val onPartySizeChange: (Int) -> Unit = {},
    val onSelectSlot: (AvailabilityCalculator.Slot) -> Unit = {},
    val onToggleTable: (RestaurantTable) -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onDismissMessage: () -> Unit = {},
)

const val DEFAULT_PARTY_SIZE = 2
const val MIN_PARTY_SIZE = 1

/** Larger parties are a phone call to the restaurant, not a self service booking. */
const val MAX_PARTY_SIZE = 12

/** Two weeks ahead. Far enough to plan, near enough that availability still means something. */
const val BOOKABLE_DAYS = 14
