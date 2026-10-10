package com.example.kulapro.data.repository

import com.example.kulapro.data.model.OwnershipRequest
import com.example.kulapro.data.model.ProposedRestaurant
import com.example.kulapro.data.model.RequestStatus
import com.example.kulapro.data.model.RequestType
import com.example.kulapro.data.model.Restaurant
import com.example.kulapro.util.UserFacingException
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

class OwnershipRepositoryFirestore(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : OwnershipRepository {

    private val requests get() = firestore.collection(FirestorePaths.OWNERSHIP_REQUESTS)

    override suspend fun requestClaim(
        restaurantId: String,
        restaurantName: String,
        role: String,
        contactPhone: String,
        evidence: String,
    ): Result<Unit> = submit { user ->
        OwnershipRequest(
            userId = user.uid,
            userEmail = user.email.orEmpty(),
            userName = user.displayName.orEmpty(),
            type = RequestType.CLAIM.name,
            restaurantId = restaurantId,
            restaurantName = restaurantName,
            role = role,
            contactPhone = contactPhone,
            evidence = evidence,
        )
    }

    override suspend fun requestNewListing(
        proposed: ProposedRestaurant,
        role: String,
        contactPhone: String,
        evidence: String,
    ): Result<Unit> = submit { user ->
        OwnershipRequest(
            userId = user.uid,
            userEmail = user.email.orEmpty(),
            userName = user.displayName.orEmpty(),
            type = RequestType.NEW_LISTING.name,
            restaurantName = proposed.name,
            proposed = proposed,
            role = role,
            contactPhone = contactPhone,
            evidence = evidence,
        )
    }

    override fun myRequests(): Flow<List<OwnershipRequest>> {
        val uid = auth.currentUser?.uid ?: return flowOf(emptyList())
        return requests
            .whereEqualTo("userId", uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .asRequestFlow()
    }

    override fun pendingRequests(): Flow<List<OwnershipRequest>> = requests
        .whereEqualTo("status", RequestStatus.PENDING.name)
        .orderBy("createdAt", Query.Direction.ASCENDING)
        .asRequestFlow()

    override suspend fun approve(request: OwnershipRequest): Result<Unit> {
        val reviewer = auth.currentUser?.uid
            ?: return Result.Failure("Sign in as a reviewer to decide on this request.")

        return runCatchingFirestore {
            val restaurantRef = when (request.typeEnum) {
                RequestType.CLAIM -> {
                    if (request.restaurantId.isBlank()) {
                        throw UserFacingException(
                            "This request does not name a restaurant, so it cannot be " +
                                "approved. Ask the sender to raise it again.",
                        )
                    }
                    firestore.collection(FirestorePaths.RESTAURANTS)
                        .document(request.restaurantId)
                }

                // A new listing has no restaurant yet. It is created here rather than when
                // the request was raised, so an unapproved listing never exists at all.
                RequestType.NEW_LISTING ->
                    firestore.collection(FirestorePaths.RESTAURANTS).document()
            }

            val proposed = request.proposed
            val batch = firestore.batch()

            if (request.typeEnum == RequestType.NEW_LISTING && proposed != null) {
                batch.set(
                    restaurantRef,
                    Restaurant(
                        name = proposed.name,
                        description = proposed.description,
                        cuisine = proposed.cuisine,
                        address = proposed.address,
                        phone = proposed.phone,
                        priceBand = proposed.priceBand,
                        capacityPerSlot = proposed.capacityPerSlot,
                        openingHours = DEFAULT_OPENING_HOURS,
                        ownerUserId = request.userId,
                    ),
                )
            } else {
                // Merged rather than replaced: approving a claim must not wipe the hours,
                // rating and everything else the listing already carries.
                batch.update(restaurantRef, FIELD_OWNER_USER_ID, request.userId)
            }

            batch.update(
                requests.document(request.id),
                mapOf(
                    "status" to RequestStatus.APPROVED.name,
                    "reviewedAt" to Timestamp.now(),
                    "reviewedBy" to reviewer,
                    "restaurantId" to restaurantRef.id,
                ),
            )

            batch.commit().await()
        }
    }

    override suspend fun reject(request: OwnershipRequest, note: String): Result<Unit> {
        val reviewer = auth.currentUser?.uid
            ?: return Result.Failure("Sign in as a reviewer to decide on this request.")

        return runCatchingFirestore {
            requests.document(request.id).update(
                mapOf(
                    "status" to RequestStatus.REJECTED.name,
                    "reviewedAt" to Timestamp.now(),
                    "reviewedBy" to reviewer,
                    // Carried back to the requester, so a rejection is never silent.
                    "reviewNote" to note,
                ),
            ).await()
        }
    }

    /** Refuses a second request while one is still open, so a queue cannot be flooded. */
    private suspend fun submit(
        build: (com.google.firebase.auth.FirebaseUser) -> OwnershipRequest,
    ): Result<Unit> {
        val user = auth.currentUser
            ?: return Result.Failure(
                "Sign in to ask about a restaurant, so we know who the request is from.",
            )

        return runCatchingFirestore {
            val open = requests
                .whereEqualTo("userId", user.uid)
                .whereEqualTo("status", RequestStatus.PENDING.name)
                .get()
                .await()
            if (!open.isEmpty) {
                throw UserFacingException(
                    "You already have a request waiting on a decision. We will come back " +
                        "to you on that one first.",
                )
            }
            requests.add(build(user)).await()
            Unit
        }
    }

    private fun Query.asRequestFlow(): Flow<List<OwnershipRequest>> = callbackFlow {
        val registration = addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.toObjects(OwnershipRequest::class.java).orEmpty())
        }
        awaitClose { registration.remove() }
    }
}

private const val FIELD_OWNER_USER_ID = "ownerUserId"

/** A new listing opens every day until its owner says otherwise. */
private val DEFAULT_OPENING_HOURS = mapOf(
    "monday" to "12:00-22:00",
    "tuesday" to "12:00-22:00",
    "wednesday" to "12:00-22:00",
    "thursday" to "12:00-22:00",
    "friday" to "12:00-23:00",
    "saturday" to "11:00-23:00",
    "sunday" to "11:00-21:00",
)
