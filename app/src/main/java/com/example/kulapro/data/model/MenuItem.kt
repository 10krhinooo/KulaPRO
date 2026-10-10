package com.example.kulapro.data.model

import com.google.firebase.firestore.DocumentId

/**
 * A dish on a restaurant's menu.
 *
 * [nutrition] is nullable on purpose. An item that already carries nutrition lets the camera
 * scanner skip the model call entirely and read from Firestore instead, so filling this in
 * from the admin view directly reduces running cost.
 */
data class MenuItem(
    @DocumentId val id: String = "",
    val restaurantId: String = "",
    val name: String = "",
    val description: String = "",
    val priceCents: Long = 0,
    val currency: String = "KES",
    val category: String = "",
    val imageUrl: String = "",
    val nutrition: NutritionInfo? = null,
)
