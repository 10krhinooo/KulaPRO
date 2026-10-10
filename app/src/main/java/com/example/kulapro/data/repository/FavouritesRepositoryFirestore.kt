package com.example.kulapro.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

class FavouritesRepositoryFirestore(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : FavouritesRepository {

    override fun favourites(): Flow<Set<String>> {
        // A guest has none rather than an error. Browsing works signed out, so the heart is
        // simply not offered until there is somebody to keep the list for.
        val uid = auth.currentUser?.uid ?: return flowOf(emptySet())
        return callbackFlow {
            val registration = collection(uid).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents.orEmpty().map { it.id }.toSet())
            }
            awaitClose { registration.remove() }
        }
    }

    override suspend fun setFavourite(
        restaurantId: String,
        isFavourite: Boolean,
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.Failure("Sign in to keep a restaurant.")

        return runCatchingFirestore {
            val document = collection(uid).document(restaurantId)
            if (isFavourite) {
                // The id is the restaurant, so the document only has to record when. That
                // makes adding the same favourite twice a no-op rather than a duplicate.
                document.set(mapOf("createdAt" to Timestamp.now())).await()
            } else {
                document.delete().await()
            }
        }
    }

    private fun collection(uid: String) = firestore.collection(FirestorePaths.USERS)
        .document(uid)
        .collection(FirestorePaths.FAVOURITES)
}
