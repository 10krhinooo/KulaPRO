package com.example.kulapro.data.repository

import com.example.kulapro.data.model.Review
import kotlinx.coroutines.flow.Flow

interface ReviewRepository {
    /** Reviews for a restaurant, newest first. */
    fun reviews(restaurantId: String): Flow<List<Review>>

    /**
     * Posts a review.
     *
     * [reservationId] must reference a COMPLETED reservation belonging to the author.
     * Security rules enforce this server side, so a client that skips the check is rejected
     * rather than trusted.
     */
    suspend fun submit(review: Review): Result<Unit>

    /** The user's review for a reservation, if they have already left one. */
    suspend fun reviewForReservation(
        restaurantId: String,
        reservationId: String,
    ): Result<Review?>
}
