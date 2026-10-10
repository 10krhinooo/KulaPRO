package com.example.kulapro.data.scanner

import com.example.kulapro.data.repository.Result

/**
 * Reads a dish from a photograph.
 *
 * The call goes to a proxy rather than to the model directly, because the API key cannot
 * live in the APK: a key shipped in an Android app is extractable in minutes, and whoever
 * extracts it spends the bill. The proxy also holds the shared cache and the per user
 * quota, neither of which a client can be trusted to enforce on itself.
 */
interface ScannerRepository {

    /** Whether a scanner is configured at all. False in a checkout with no proxy URL. */
    val isAvailable: Boolean

    suspend fun scan(imageBase64: String): Result<DishNutrition>
}
