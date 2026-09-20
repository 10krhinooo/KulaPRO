package com.example.kulapro.feature.owner

import com.example.kulapro.data.repository.ClaimsRepository
import com.example.kulapro.data.repository.Result

/** Claims a test can set directly, standing in for a signed Firebase token. */
class FakeClaimsRepository(
    var managedRestaurants: List<String> = emptyList(),
    var isPlatformAdmin: Boolean = false,
    var failure: String? = null,
) : ClaimsRepository {

    override suspend fun managedRestaurantIds(forceRefresh: Boolean): Result<List<String>> =
        failure?.let { Result.Failure(it) } ?: Result.Success(managedRestaurants)

    override suspend fun isPlatformAdmin(forceRefresh: Boolean): Result<Boolean> =
        failure?.let { Result.Failure(it) } ?: Result.Success(isPlatformAdmin)
}
