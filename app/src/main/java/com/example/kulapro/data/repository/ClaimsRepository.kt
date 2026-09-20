package com.example.kulapro.data.repository

/**
 * What the signed-in user is allowed to do, read from their Firebase Auth custom claims.
 *
 * Separate from [AuthRepository]: proving who someone is and deciding what they may do are
 * different questions, and the second one is the one that has to be got right.
 *
 * Claims are signed by Firebase and cannot be edited by the client, unlike the role field on
 * the profile document, which its owner can write. Anything granting privilege reads here.
 */
interface ClaimsRepository {

    /** Restaurants this user may manage. */
    suspend fun managedRestaurantIds(forceRefresh: Boolean = false): Result<List<String>>

    /**
     * Whether this user reviews ownership requests.
     *
     * The one role that cannot be granted from inside the app, precisely because it is the
     * role that grants every other one.
     */
    suspend fun isPlatformAdmin(forceRefresh: Boolean = false): Result<Boolean>
}
