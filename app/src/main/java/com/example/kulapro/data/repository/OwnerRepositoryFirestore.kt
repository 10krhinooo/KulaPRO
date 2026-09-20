package com.example.kulapro.data.repository

import com.example.kulapro.data.model.Reservation
import com.example.kulapro.data.model.ReservationStatus
import com.example.kulapro.data.model.Restaurant
import com.example.kulapro.data.model.SlotCount
import com.example.kulapro.util.UserFacingException
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class OwnerRepositoryFirestore(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : OwnerRepository {

    override suspend fun updateRestaurant(restaurant: Restaurant): Result<Unit> =
        runCatchingFirestore {
            // merge so fields the owner form does not edit, such as the denormalised rating
            // counters maintained by triggers, are not wiped on every save.
            firestore.collection(FirestorePaths.RESTAURANTS)
                .document(restaurant.id)
                .set(
                    mapOf(
                        "name" to restaurant.name,
                        "description" to restaurant.description,
                        "cuisine" to restaurant.cuisine,
                        "priceBand" to restaurant.priceBand,
                        "address" to restaurant.address,
                        "phone" to restaurant.phone,
                        "capacityPerSlot" to restaurant.capacityPerSlot,
                        "slotDurationMinutes" to restaurant.slotDurationMinutes,
                        "openingHours" to restaurant.openingHours,
                    ),
                    SetOptions.merge(),
                )
                .await()
        }

    override suspend fun bookingsFor(
        restaurantId: String,
        from: Timestamp,
        to: Timestamp,
    ): Result<List<Reservation>> = runCatchingFirestore {
        firestore.collection(FirestorePaths.RESERVATIONS)
            .whereEqualTo("restaurantId", restaurantId)
            .whereGreaterThanOrEqualTo("startsAt", from)
            .whereLessThan("startsAt", to)
            .orderBy("startsAt", Query.Direction.ASCENDING)
            .get()
            .await()
            .toObjects(Reservation::class.java)
    }

    override suspend fun updateReservationStatus(
        reservationId: String,
        status: String,
    ): Result<Unit> = runCatchingFirestore {
        val reservationRef = firestore.collection(FirestorePaths.RESERVATIONS)
            .document(reservationId)

        firestore.runTransaction { transaction ->
            // Every read before the first write: Firestore rejects a transaction that
            // interleaves them.
            val reservation = transaction.get(reservationRef).toObject(Reservation::class.java)
                ?: throw UserFacingException(
                    "That booking is no longer there. Pull to refresh and try again.",
                )
            val was = reservation.statusEnum
            val now = ReservationStatus.entries.firstOrNull { it.name == status }
                ?: throw UserFacingException("That is not a status a booking can be in.")

            val slot = slotDocument(reservation.restaurantId, reservation.startsAt.seconds)
            val releasesSeats = was.occupiesCapacity && !now.occupiesCapacity
            val current = if (releasesSeats) {
                transaction.get(slot).toObject(SlotCount::class.java)
            } else {
                null
            }

            transaction.update(reservationRef, "status", status)

            // A party that has left or never arrived stops blocking the sitting, so the
            // seats go back and the table with them.
            if (releasesSeats) {
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

    override suspend fun addWalkIn(
        restaurantId: String,
        restaurantName: String,
        partySize: Int,
        startsAt: Timestamp,
        tableId: String,
        tableLabel: String,
    ): Result<Unit> = runCatchingFirestore {
        val document = firestore.collection(FirestorePaths.RESERVATIONS).document()
        val slot = slotDocument(restaurantId, startsAt.seconds)

        firestore.runTransaction { transaction ->
            val current = transaction.get(slot).toObject(SlotCount::class.java)
            val alreadyTaken = current?.takenTableIds.orEmpty()

            transaction.set(
                document,
                Reservation(
                    id = document.id,
                    // No userId: a walk-in belongs to nobody, which is what keeps it out of
                    // every diner's reservation list.
                    userId = "",
                    restaurantId = restaurantId,
                    restaurantName = restaurantName,
                    startsAt = startsAt,
                    partySize = partySize,
                    tableId = tableId,
                    tableLabel = tableLabel,
                    status = ReservationStatus.SEATED.name,
                    isWalkIn = true,
                    createdAt = Timestamp.now(),
                ),
            )
            transaction.set(
                slot,
                SlotCount(
                    restaurantId = restaurantId,
                    startsAtSeconds = startsAt.seconds,
                    seatsTaken = (current?.seatsTaken ?: 0) + partySize,
                    takenTableIds = if (tableId.isBlank()) {
                        alreadyTaken
                    } else {
                        alreadyTaken + tableId
                    },
                ),
            )
        }.await()
    }

    /** Slot documents are keyed by start time, so a booking maps to exactly one counter. */
    private fun slotDocument(restaurantId: String, startsAtSeconds: Long) =
        firestore.collection(FirestorePaths.RESTAURANTS)
            .document(restaurantId)
            .collection(FirestorePaths.SLOTS)
            .document(startsAtSeconds.toString())
}
