package com.example.kulapro.data.repository

import com.example.kulapro.data.model.OwnershipRequest
import com.example.kulapro.data.model.ProposedRestaurant
import kotlinx.coroutines.flow.Flow

/**
 * Asking to manage a restaurant, and deciding who may.
 *
 * Nothing here grants privilege on its own. A request is a statement by the person making
 * it, and only [approve] changes what anyone is allowed to do, which security rules restrict
 * to a platform admin.
 */
interface OwnershipRepository {

    /** Asks to take over a restaurant that is already listed. */
    suspend fun requestClaim(
        restaurantId: String,
        restaurantName: String,
        role: String,
        contactPhone: String,
        evidence: String,
    ): Result<Unit>

    /** Asks for a restaurant that is not listed yet to be added, managed by the requester. */
    suspend fun requestNewListing(
        proposed: ProposedRestaurant,
        role: String,
        contactPhone: String,
        evidence: String,
    ): Result<Unit>

    /** The signed-in user's own requests, so they can see where each one got to. */
    fun myRequests(): Flow<List<OwnershipRequest>>

    /** Everything waiting on a decision. Readable only by a platform admin. */
    fun pendingRequests(): Flow<List<OwnershipRequest>>

    /**
     * Grants the request.
     *
     * For a claim this writes the owner onto the existing restaurant. For a new listing it
     * creates the restaurant first. Either way the requester can manage it from the next
     * read onwards, with no privileged script in between.
     */
    suspend fun approve(request: OwnershipRequest): Result<Unit>

    suspend fun reject(request: OwnershipRequest, note: String): Result<Unit>
}
