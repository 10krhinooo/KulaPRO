package com.example.kulapro.data.repository

import com.example.kulapro.data.model.MenuItem
import com.example.kulapro.data.model.Restaurant
import com.example.kulapro.data.model.RestaurantTable
import kotlinx.coroutines.flow.Flow

interface RestaurantRepository {
    fun restaurants(): Flow<List<Restaurant>>

    suspend fun restaurant(id: String): Result<Restaurant>

    suspend fun menu(restaurantId: String): Result<List<MenuItem>>

    /**
     * The restaurant's floor plan.
     *
     * Empty for a restaurant that has not mapped its tables, in which case booking falls back
     * to the seat count alone and the diner is simply seated on arrival.
     */
    suspend fun tables(restaurantId: String): Result<List<RestaurantTable>>
}
