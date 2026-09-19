package com.example.kulapro.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

/**
 * A user's profile document.
 *
 * [role] is a display mirror only and is never the basis for privilege. Anyone who owns this
 * document can write it, so gating admin access on this field would let a client promote
 * itself. Authorisation lives in Firebase Auth custom claims, which the client cannot forge.
 */
data class UserProfile(
    @DocumentId val id: String = "",
    val email: String = "",
    val displayName: String = "",
    val phone: String = "",
    val favouriteRestaurantIds: List<String> = emptyList(),
    val role: String = UserRole.DINER.name,
    val createdAt: Timestamp = Timestamp.now(),
)

enum class UserRole { DINER, STAFF, OWNER, PLATFORM_ADMIN }
