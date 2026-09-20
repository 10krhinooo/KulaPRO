package com.example.kulapro.data.repository

import com.example.kulapro.data.model.Reservation
import com.example.kulapro.data.model.ReservationStatus
import com.example.kulapro.data.model.SlotCount
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

    override suspend fun slotCountsFor(
        restaurantId: String,
        from: Timestamp,
        to: Timestamp,
    ): Result<Map<Long, SlotCount>> = runCatchingFirestore {
        firestore.collection(FirestorePaths.RESTAURANTS)
            .document(restaurantId)
            .collection(FirestorePaths.SLOTS)
            .whereGreaterThanOrEqualTo("startsAtSeconds", from.seconds)
            .whereLessThan("startsAtSeconds", to.seconds)
            .get()
            .await()
            .toObjects(SlotCount::class.java)
            .associateBy { it.startsAtSeconds }
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
            val slot = slotDocument(reservation.restaurantId, reservation.startsAt.seconds)

            // The booking and the seat count move together. A transaction rather than a
            // batch, because the new count depends on the value read a moment earlier.
            firestore.runTransaction { transaction ->
                val current = transaction.get(slot).toObject(SlotCount::class.java)
                val alreadyTaken = current?.takenTableIds.orEmpty()
                // Read inside the transaction, so two diners racing for the same table
                // cannot both win it.
                if (toSave.tableId.isNotBlank() && toSave.tableId in alreadyTaken) {
                    error("That table has just been taken. Pick another.")
                }
                transaction.set(document, toSave)
                transaction.set(
                    slot,
                    SlotCount(
                        restaurantId = reservation.restaurantId,
                        startsAtSeconds = reservation.startsAt.seconds,
                        seatsTaken = (current?.seatsTaken ?: 0) + reservation.partySize,
                        takenTableIds = if (toSave.tableId.isBlank()) {
                            alreadyTaken
                        } else {
                            alreadyTaken + toSave.tableId
                        },
                    ),
                )
            }.await()
            document.id
        }
    }

    override suspend fun cancel(reservationId: String): Result<Unit> = runCatchingFirestore {
        val reservationRef = firestore.collection(FirestorePaths.RESERVATIONS)
            .document(reservationId)

        firestore.runTransaction { transaction ->
            val reservation = transaction.get(reservationRef).toObject(Reservation::class.java)
                ?: error("Reservation not found")
            transaction.update(reservationRef, "status", ReservationStatus.CANCELLED.name)

            // Give the seats back, so a cancelled booking stops blocking the slot.
            if (reservation.statusEnum.occupiesCapacity) {
                val slot = slotDocument(reservation.restaurantId, reservation.startsAt.seconds)
                val current = transaction.get(slot).toObject(SlotCount::class.java)
                transaction.set(
                    slot,
                    SlotCount(
                        restaurantId = reservation.restaurantId,
                        startsAtSeconds = reservation.startsAt.seconds,
                        seatsTaken = ((current?.seatsTaken ?: 0) - reservation.partySize)
                            .coerceAtLeast(0),
                        takenTableIds = current?.takenTableIds.orEmpty()
                            .filterNot { it == reservation.tableId },
                    ),
                )
            }
        }.await()
    }

    /** Slot documents are keyed by start time, so a booking maps to exactly one counter. */
    private fun slotDocument(restaurantId: String, startsAtSeconds: Long) =
        firestore.collection(FirestorePaths.RESTAURANTS)
            .document(restaurantId)
            .collection(FirestorePaths.SLOTS)
            .document(startsAtSeconds.toString())
}

internal inline fun <T> runCatchingFirestore(block: () -> T): Result<T> = try {
    Result.Success(block())
} catch (e: Exception) {
    Result.Failure(e.message ?: "Could not reach the server", e)
}
