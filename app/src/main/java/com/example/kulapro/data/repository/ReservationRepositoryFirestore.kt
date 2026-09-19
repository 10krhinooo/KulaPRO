package com.example.kulapro.data.repository

import com.example.kulapro.data.model.Reservation
import com.example.kulapro.data.model.ReservationStatus
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

class ReservationRepositoryFirestore(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : ReservationRepository {

    override fun myReservations(): Flow<List<Reservation>> {
        val uid = auth.currentUser?.uid ?: return flowOf(emptyList())
        return callbackFlow {
            val registration = firestore.collection(FirestorePaths.RESERVATIONS)
                .whereEqualTo("userId", uid)
                .orderBy("startsAt", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    trySend(snapshot?.toObjects(Reservation::class.java).orEmpty())
                }
            awaitClose { registration.remove() }
        }
    }

    override suspend fun reservationsFor(
        restaurantId: String,
        from: Timestamp,
        to: Timestamp,
    ): Result<List<Reservation>> = runCatchingFirestore {
        firestore.collection(FirestorePaths.RESERVATIONS)
            .whereEqualTo("restaurantId", restaurantId)
            .whereGreaterThanOrEqualTo("startsAt", from)
            .whereLessThan("startsAt", to)
            .get()
            .await()
            .toObjects(Reservation::class.java)
    }

    override suspend fun create(reservation: Reservation): Result<String> {
        val uid = auth.currentUser?.uid
            ?: return Result.Failure("You need to be signed in to book a table")
        return runCatchingFirestore {
            // userId is stamped here, never taken from the caller. Security rules match on
            // it, so a reservation that carried someone else's uid would be both a data leak
            // and unwritable.
            val document = firestore.collection(FirestorePaths.RESERVATIONS).document()
            val toSave = reservation.copy(
                id = document.id,
                userId = uid,
                status = ReservationStatus.PENDING.name,
                createdAt = Timestamp.now(),
            )
            document.set(toSave).await()
            document.id
        }
    }

    override suspend fun cancel(reservationId: String): Result<Unit> = runCatchingFirestore {
        firestore.collection(FirestorePaths.RESERVATIONS)
            .document(reservationId)
            .update("status", ReservationStatus.CANCELLED.name)
            .await()
    }
}

internal inline fun <T> runCatchingFirestore(block: () -> T): Result<T> = try {
    Result.Success(block())
} catch (e: Exception) {
    Result.Failure(e.message ?: "Could not reach the server", e)
}
