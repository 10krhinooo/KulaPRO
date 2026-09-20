package com.example.kulapro.data.model

import com.google.firebase.firestore.DocumentId

/**
 * One physical table in a restaurant.
 *
 * Laid out on a coarse grid rather than at real coordinates: [row] and [column] place the
 * table on a plan the diner can read, without anyone having to draw the room to scale. A
 * restaurant that has not mapped its tables simply has none of these, and booking falls back
 * to the seat count alone.
 */
data class RestaurantTable(
    @DocumentId val id: String = "",
    val restaurantId: String = "",
    /** What the staff call it, e.g. "T4". Shown on the plan. */
    val label: String = "",
    val seats: Int = 2,
    /** Window, Terrace, Bar, Main floor. Groups the plan into sections the diner recognises. */
    val zone: String = "Main floor",
    val row: Int = 0,
    val column: Int = 0,
)
