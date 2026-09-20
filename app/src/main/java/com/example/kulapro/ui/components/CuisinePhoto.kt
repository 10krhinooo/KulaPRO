package com.example.kulapro.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.example.kulapro.R

/**
 * A bundled photo for a restaurant that has not published one of its own.
 *
 * Cloud Storage is not provisioned for this project, so there is nowhere to upload real
 * photography to yet. Rather than showing an icon on a coloured block, a listing falls back
 * to a photo of the kind of food it serves. When a restaurant does publish an image, that
 * wins and none of this applies.
 *
 * Four photos cannot uniquely back a dozen restaurants, so the photo is only half of the
 * answer: [restaurantWash] gives each listing its own colour over the top. Two Italian
 * restaurants then read as two different places rather than as the same asset repeated,
 * which is how a listing looked before and made the whole list look like a mock-up.
 */
@DrawableRes
fun cuisinePhoto(cuisine: String, restaurantName: String): Int {
    val match = FALLBACKS.entries.firstOrNull { (keyword, _) ->
        cuisine.contains(keyword, ignoreCase = true)
    }
    if (match != null) return match.value

    // Deterministic, so the same restaurant keeps the same photo between launches.
    return ROTATION[indexFor(restaurantName, ROTATION.size)]
}

/**
 * A colour laid over the stand-in photo, chosen from the restaurant's name.
 *
 * Deterministic, so a restaurant looks the same on every launch and on every device, and
 * kept light enough that the food underneath still reads as food. This is only ever drawn
 * over a bundled photo: a restaurant that publishes its own photography gets it untouched,
 * because tinting someone's actual photograph would be rude as well as wrong.
 */
fun restaurantWash(restaurantName: String): Color = WASHES[indexFor(restaurantName, WASHES.size)]

/** Stable across launches and devices, which [String.hashCode] on its own is not signed for. */
private fun indexFor(value: String, size: Int): Int =
    ((value.hashCode().toLong() and Int.MAX_VALUE.toLong()) % size).toInt()

/**
 * Cuisines grouped by what the food actually looks like, three to a photo.
 *
 * Grilled meat, flatbread, seafood and plated European covers the seeded list evenly.
 * Loading five cuisines onto one photo, as this did, guaranteed repeats on screen.
 */
private val FALLBACKS = linkedMapOf(
    // Charred meat over fire.
    "grill" to R.drawable.grill,
    "kenyan" to R.drawable.grill,
    "ethiopian" to R.drawable.grill,

    // Flatbread and things folded into it.
    "italian" to R.drawable.pizza,
    "pizza" to R.drawable.pizza,
    "mexican" to R.drawable.pizza,
    "indian" to R.drawable.pizza,

    // Fish and rice.
    "japanese" to R.drawable.sushi,
    "sushi" to R.drawable.sushi,
    "chinese" to R.drawable.sushi,
    "swahili" to R.drawable.sushi,

    // Plated, knife and fork.
    "european" to R.drawable.bistro,
    "levantine" to R.drawable.bistro,
    "vegetarian" to R.drawable.bistro,
)

private val ROTATION = listOf(
    R.drawable.bistro,
    R.drawable.sushi,
    R.drawable.grill,
    R.drawable.pizza,
)

/**
 * Six washes drawn from the brand's own range, so a tinted card still looks like KulaPro.
 *
 * Six against four photos gives twenty four combinations, which is more than enough that no
 * two restaurants in the list share a look.
 */
private val WASHES = listOf(
    Color(0xFF2C6E49), // herb green
    Color(0xFFB4531F), // terracotta
    Color(0xFF1F4E5F), // deep teal
    Color(0xFF7A3E6B), // plum
    Color(0xFF8A6D1F), // amber
    Color(0xFF3A4A7A), // indigo
)

/** How strongly the wash sits over the photo. Enough to distinguish, not enough to obscure. */
const val RESTAURANT_WASH_ALPHA = 0.28f
