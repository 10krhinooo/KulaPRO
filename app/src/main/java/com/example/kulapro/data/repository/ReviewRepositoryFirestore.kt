package com.example.kulapro.data.repository

import com.example.kulapro.data.model.Review
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ReviewRepositoryFirestore(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : ReviewRepository {

    private fun collection(restaurantId: String) =
        firestore.collection(FirestorePaths.RESTAURANTS)
            .document(restaurantId)
            .collection(FirestorePaths.REVIEWS)

    override fun reviews(restaurantId: String): Flow<List<Review>> = callbackFlow {
        val registration = collection(restaurantId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObjects(Review::class.java).orEmpty())
            }
        awaitClose { registration.remove() }
    }

    override suspend fun submit(review: Review): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.Failure(
                "Sign in to leave a review, so the restaurant knows who it is from.",
            )
        if (review.reservationId.isBlank()) {
            return Result.Failure(
                "You can review a restaurant once you have eaten there on a booking made " +
                    "through KulaPro.",
            )
        }
        return runCatchingFirestore {
            // One review per reservation: using the reservation id as the document id makes
            // that a property of the data rather than a check that can be forgotten.
            collection(review.restaurantId)
                .document(review.reservationId)
                .set(
                    review.copy(
                        id = review.reservationId,
                        userId = uid,
                        createdAt = Timestamp.now(),
                    ),
                )
                .await()
        }
    }

    override suspend fun reviewForReservation(
        restaurantId: String,
        reservationId: String,
    ): Result<Review?> = runCatchingFirestore {
        collection(restaurantId).document(reservationId).get().await()
            .toObject(Review::class.java)
    }
}
