package com.example.kulapro.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.GeoPoint

/**
 * A bookable restaurant.
 *
 * [location] and [geohash] are present from the first version even though Phase 1 does not
 * read them: the map view filters client-side at this scale, but storing the geohash now
 * means moving to range queries later is a query change rather than a data migration.
 *
 * [averageRating] and [reviewCount] are denormalised from the reviews subcollection by a
 * Firestore trigger so list and map screens read one document per restaurant instead of
 * aggregating a subcollection per marker.
 */
data class Restaurant(
    @DocumentId val id: String = "",
    val name: String = "",
    val description: String = "",
    val cuisine: String = "",
    /** 1..4, rendered as currency symbols. */
    val priceBand: Int = 2,
    val imageUrl: String = "",
    val address: String = "",
    val phone: String = "",
    val location: GeoPoint? = null,
    val geohash: String = "",
    val averageRating: Double = 0.0,
    val reviewCount: Int = 0,
    /** Total seats bookable in any one slot. Drives the availability calculation. */
    val capacityPerSlot: Int = 0,
    /** Opening hours keyed by lowercase day name, e.g. "monday" to "12:00-22:00". */
    val openingHours: Map<String, String> = emptyMap(),
    val slotDurationMinutes: Int = 90,
)
