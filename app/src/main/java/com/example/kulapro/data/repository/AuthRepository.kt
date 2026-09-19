package com.example.kulapro.data.repository

import android.content.Context
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    /** The signed-in user's uid, or null. Read synchronously to pick a start destination. */
    val currentUserId: String?

    val currentUserEmail: String?

    /** Emits the uid on sign-in and null on sign-out, for the duration of collection. */
    fun authState(): Flow<String?>

    suspend fun signIn(email: String, password: String): Result<Unit>

    suspend fun register(email: String, password: String): Result<Unit>

    /**
     * Google Sign-In through Credential Manager.
     *
     * [activityContext] must be an Activity: Credential Manager renders a system bottom
     * sheet and cannot do so from an application context.
     */
    suspend fun signInWithGoogle(activityContext: Context): Result<Unit>

    suspend fun sendPasswordReset(email: String): Result<Unit>

    /**
     * Firebase requires a recent sign-in before changing credentials. Callers must pass the
     * current password; without this the first version's email and password changes failed
     * with FirebaseAuthRecentLoginRequiredException every time after the first few minutes.
     */
    suspend fun reauthenticate(currentPassword: String): Result<Unit>

    suspend fun updateEmail(newEmail: String, currentPassword: String): Result<Unit>

    suspend fun updatePassword(newPassword: String, currentPassword: String): Result<Unit>

    /**
     * Restaurants this user may manage, read from their Firebase Auth custom claims.
     *
     * Claims are signed by Firebase and cannot be edited by the client, unlike the role
     * field on the profile document, which its owner can write. Anything that grants
     * privilege must read from here.
     */
    suspend fun managedRestaurantIds(forceRefresh: Boolean = false): Result<List<String>>

    fun signOut()
}
