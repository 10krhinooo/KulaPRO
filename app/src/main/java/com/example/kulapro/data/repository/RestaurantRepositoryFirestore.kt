package com.example.kulapro.data.repository

import com.example.kulapro.data.model.MenuItem
import com.example.kulapro.data.model.Restaurant
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class RestaurantRepositoryFirestore(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : RestaurantRepository {

    override fun restaurants(): Flow<List<Restaurant>> = callbackFlow {
        val registration = firestore.collection(FirestorePaths.RESTAURANTS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObjects(Restaurant::class.java).orEmpty())
            }
        awaitClose { registration.remove() }
    }

    override suspend fun restaurant(id: String): Result<Restaurant> = runCatchingFirestore {
        firestore.collection(FirestorePaths.RESTAURANTS).document(id).get().await()
            .toObject(Restaurant::class.java)
            ?: error("Restaurant not found")
    }

    override suspend fun menu(restaurantId: String): Result<List<MenuItem>> =
        runCatchingFirestore {
            firestore.collection(FirestorePaths.RESTAURANTS)
                .document(restaurantId)
                .collection(FirestorePaths.MENU_ITEMS)
                .get()
                .await()
                .toObjects(MenuItem::class.java)
        }
}
