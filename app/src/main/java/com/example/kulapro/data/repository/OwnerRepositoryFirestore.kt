package com.example.kulapro.data.repository

import com.example.kulapro.data.model.Reservation
import com.example.kulapro.data.model.Restaurant
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
        firestore.collection(FirestorePaths.RESERVATIONS)
            .document(reservationId)
            .update("status", status)
            .await()
    }
}
