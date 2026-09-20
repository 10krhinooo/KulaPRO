package com.example.kulapro.feature.owner

import com.example.kulapro.data.model.RestaurantTable
import com.example.kulapro.domain.OpeningHours
import com.example.kulapro.ui.components.UiMessage

/**
 * The room, as the restaurant describes it.
 *
 * Everything here is what the diner side reads to decide what it may offer, which is why it
 * belongs to the restaurant rather than to a seed script. Seats per sitting, how long a
 * sitting lasts, which hours the kitchen serves, and which tables exist: change any of them
 * and the times a diner is shown change with it.
 */
data class CapacityUiState(
    val restaurantName: String = "",
    val seatsPerSitting: String = "",
    val sittingMinutes: Int = DEFAULT_SITTING_MINUTES,
    val days: List<DayHours> = OpeningHours.DAY_KEYS.map { DayHours(it) },
    val tables: List<RestaurantTable> = emptyList(),
    val tableDraft: TableDraft? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val deletingTableId: String? = null,
    val loadError: String? = null,
    val message: UiMessage? = null,
) {
    val seatsError: String?
        get() = when (seatsPerSitting.toIntOrNull()) {
            null -> "Enter how many seats one sitting can take."
            in Int.MIN_VALUE..0 -> "A sitting has to seat at least one person."
            in MAX_SEATS_PER_SITTING + 1..Int.MAX_VALUE ->
                "That is more than $MAX_SEATS_PER_SITTING seats. Check the number."

            else -> null
        }

    /** Seats the mapped tables add up to, which is the number to sanity check against. */
    val seatsOnTheFloor: Int get() = tables.sumOf { it.seats }

    val openDays: Int get() = days.count { it.isOpen }

    val canSave: Boolean
        get() = seatsError == null && days.none { it.error != null } && !isSaving

    /**
     * Whether the mapped tables hold fewer seats than the sitting is sold for.
     *
     * Not an error: plenty of restaurants seat people the plan does not show. Worth saying
     * out loud, because the two numbers disagreeing is usually a typo rather than a choice.
     */
    val seatsExceedTables: Boolean
        get() = tables.isNotEmpty() &&
            (seatsPerSitting.toIntOrNull() ?: 0) > seatsOnTheFloor
}

/**
 * One day's trading hours while they are being edited.
 *
 * Held as the typed text rather than as parsed minutes, so a half-typed "1" does not get
 * rewritten under the person entering it.
 */
data class DayHours(
    val key: String,
    val isOpen: Boolean = false,
    val opens: String = DEFAULT_OPENS,
    val closes: String = DEFAULT_CLOSES,
) {
    val label: String get() = OpeningHours.displayName(key)

    val error: String?
        get() {
            if (!isOpen) return null
            val open = OpeningHours.parseMinutes(opens)
            val close = OpeningHours.parseMinutes(closes)
            return when {
                open == null || close == null -> "Use a 24 hour time, like 12:00."
                close <= open -> "Closing has to come after opening."
                else -> null
            }
        }

    /** The stored form, or null when the restaurant is shut or the times do not read. */
    fun asStoredRange(): String? {
        if (!isOpen || error != null) return null
        return "$opens-$closes"
    }

    companion object {
        /** Reads a stored day back into the form, defaulting a day with no entry to shut. */
        fun of(key: String, stored: String?): DayHours {
            val parsed = OpeningHours.parseRange(stored) ?: return DayHours(key, isOpen = false)
            return DayHours(
                key = key,
                isOpen = true,
                opens = OpeningHours.formatMinutes(parsed.openMinutes),
                closes = OpeningHours.formatMinutes(parsed.closeMinutes),
            )
        }
    }
}

/** A table being added or renamed. */
data class TableDraft(
    val id: String = "",
    val label: String = "",
    val seats: String = "2",
    val zone: String = DEFAULT_ZONE,
) {
    val isNew: Boolean get() = id.isBlank()

    val labelError: String?
        get() = if (label.isBlank()) "Give the table the name your staff use for it." else null

    val seatsError: String?
        get() = when (seats.toIntOrNull()) {
            null -> "How many people does it seat?"
            in Int.MIN_VALUE..0 -> "A table seats at least one person."
            in MAX_SEATS_PER_TABLE + 1..Int.MAX_VALUE -> "That is a very large table. Check it."
            else -> null
        }

    val canSave: Boolean get() = labelError == null && seatsError == null

    fun toTable(restaurantId: String, existing: List<RestaurantTable>): RestaurantTable {
        val previous = existing.firstOrNull { it.id == id }
        return RestaurantTable(
            id = id,
            restaurantId = restaurantId,
            label = label.trim(),
            seats = seats.toIntOrNull() ?: 1,
            zone = zone.trim().ifBlank { DEFAULT_ZONE },
            // A new table goes on the next free spot on the grid; an edited one stays where
            // the diner already knows to find it.
            row = previous?.row ?: (existing.size / GRID_COLUMNS),
            column = previous?.column ?: (existing.size % GRID_COLUMNS),
        )
    }

    companion object {
        fun of(table: RestaurantTable): TableDraft = TableDraft(
            id = table.id,
            label = table.label,
            seats = table.seats.toString(),
            zone = table.zone,
        )
    }
}

/** The sitting lengths restaurants actually run, rather than a free number field. */
val SITTING_CHOICES = listOf(60, 90, 120, 150)

const val DEFAULT_SITTING_MINUTES = 90
private const val DEFAULT_OPENS = "12:00"
private const val DEFAULT_CLOSES = "22:00"
private const val DEFAULT_ZONE = "Main floor"
private const val MAX_SEATS_PER_SITTING = 500
private const val MAX_SEATS_PER_TABLE = 30
private const val GRID_COLUMNS = 4
