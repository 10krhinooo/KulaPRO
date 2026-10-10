package com.example.kulapro.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

/**
 * A restaurant review.
 *
 * [reservationId] is what makes reviews trustworthy: security rules only accept a review
 * whose referenced reservation belongs to the author and has status COMPLETED, one review
 * per reservation. That makes fake reviews structurally hard rather than merely discouraged.
 */
data class Review(
    @DocumentId val id: String = "",
    val restaurantId: String = "",
    val userId: String = "",
    val authorName: String = "",
    val reservationId: String = "",
    val rating: Int = 0,
    val comment: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val ownerReply: String = "",
)
