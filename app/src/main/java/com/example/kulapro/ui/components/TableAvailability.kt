package com.example.kulapro.ui.components

/**
 * A table on the plan, and why the diner can or cannot have it.
 *
 * Taken and too small are kept apart on purpose. A full room and a room full of two seaters
 * are different problems, and only the second one is solved by shrinking the party.
 */
enum class TableAvailability {
    FREE,

    /** Someone else has it for this sitting. */
    TAKEN,

    /** Free, but not enough seats for the party. */
    TOO_SMALL,
}
