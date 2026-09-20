package com.example.kulapro.data.repository

import android.net.Uri
import com.example.kulapro.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * The signed-in user's profile document and avatar.
 *
 * Separate from [AuthRepository]: authenticating someone and maintaining their profile are
 * different jobs with different failure modes, and bundling them produced one interface that
 * did too much.
 */
interface ProfileRepository {

    suspend fun profile(): Result<UserProfile>

    /** Observes the profile so photo and name edits appear without a manual refresh. */
    fun profileFlow(): Flow<UserProfile?>

    suspend fun updateProfileDetails(displayName: String, phone: String): Result<Unit>

    /**
     * Stores a new avatar on the profile document.
     *
     * The image is downscaled and inlined rather than uploaded to Cloud Storage: an avatar
     * is small enough to sit inside the document, which keeps it visible to other users and
     * synced across devices without requiring a Storage bucket.
     */
    suspend fun updateProfilePhoto(imageUri: Uri): Result<String>

    suspend fun removeProfilePhoto(): Result<Unit>
}
