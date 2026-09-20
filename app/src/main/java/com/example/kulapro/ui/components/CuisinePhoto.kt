package com.example.kulapro.ui.components

import androidx.annotation.DrawableRes
import com.example.kulapro.R

/**
 * A bundled photo for a restaurant that has not published one of its own.
 *
 * Cloud Storage is not provisioned for this project, so there is nowhere to upload real
 * photography to yet. Rather than showing an icon on a coloured block, a listing falls back
 * to a photo of the kind of food it serves. When a restaurant does publish an image, that
 * wins and none of this applies.
 *
 * The cuisine is matched loosely, and anything unrecognised is spread across the available
 * photos by name so a list does not come out looking like the same restaurant twelve times.
 */
@DrawableRes
fun cuisinePhoto(cuisine: String, restaurantName: String): Int {
    val match = FALLBACKS.entries.firstOrNull { (keyword, _) ->
        cuisine.contains(keyword, ignoreCase = true)
    }
    if (match != null) return match.value

    // Deterministic, so the same restaurant keeps the same photo between launches.
    val index = (restaurantName.hashCode().toLong() and Int.MAX_VALUE.toLong()) % ROTATION.size
    return ROTATION[index.toInt()]
}

private val FALLBACKS = linkedMapOf(
    "japanese" to R.drawable.sushi,
    "sushi" to R.drawable.sushi,
    "chinese" to R.drawable.sushi,
    "italian" to R.drawable.pizza,
    "pizza" to R.drawable.pizza,
    "mexican" to R.drawable.pizza,
    "grill" to R.drawable.grill,
    "kenyan" to R.drawable.grill,
    "swahili" to R.drawable.grill,
    "ethiopian" to R.drawable.grill,
    "levantine" to R.drawable.grill,
    "european" to R.drawable.bistro,
    "indian" to R.drawable.bistro,
    "vegetarian" to R.drawable.bistro,
)

private val ROTATION = listOf(
    R.drawable.bistro,
    R.drawable.sushi,
    R.drawable.grill,
    R.drawable.pizza,
)
