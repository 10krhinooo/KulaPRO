package com.example.kulapro.data.repository

import com.example.kulapro.data.model.MenuItem
import com.example.kulapro.data.model.Restaurant
import kotlinx.coroutines.flow.Flow

interface RestaurantRepository {
    fun restaurants(): Flow<List<Restaurant>>

    suspend fun restaurant(id: String): Result<Restaurant>

    suspend fun menu(restaurantId: String): Result<List<MenuItem>>
}
