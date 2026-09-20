package com.example.kulapro.data.repository

import kotlinx.coroutines.flow.Flow

/**
 * The restaurants a diner has kept.
 *
 * A subcollection under the user rather than an array on their profile document. An array
 * would be one document two writes could race on, and it would grow inside the same 1 MiB
 * limit the rest of the profile lives in. A document per favourite has neither problem, and
 * the security rule is the one already protecting everything else the user owns.
 */
interface FavouritesRepository {

    /** The ids kept, or an empty set for a guest. Never null, so the UI has no third case. */
    fun favourites(): Flow<Set<String>>

    suspend fun setFavourite(restaurantId: String, isFavourite: Boolean): Result<Unit>
}
