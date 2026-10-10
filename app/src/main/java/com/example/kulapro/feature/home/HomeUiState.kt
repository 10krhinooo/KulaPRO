package com.example.kulapro.feature.home

import com.example.kulapro.data.model.Restaurant

/**
 * What Home shows, and everything the diner has narrowed it down by.
 *
 * Filtering happens here rather than in the query, because all of it is answerable from
 * data already loaded. Re-querying Firestore on every keystroke would cost a read per
 * letter and still be slower than filtering a list of a dozen restaurants in memory. At a
 * size where that stops being true, this is where a server-side search would replace the
 * derived list, and nothing above it would change.
 */
data class HomeUiState(
    val query: String = "",
    val cuisines: Set<String> = emptySet(),
    val priceBands: Set<Int> = emptySet(),
    val openNowOnly: Boolean = false,
    val freeTonightOnly: Boolean = false,
    val favouritesOnly: Boolean = false,
    val restaurants: List<Restaurant> = emptyList(),
    val favouriteIds: Set<String> = emptySet(),
    /** Restaurants with at least one seat left in a sitting later today. */
    val freeTonightIds: Set<String> = emptySet(),
    val openNowIds: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val isSignedIn: Boolean = false,
    val greetingName: String = "",
    val message: String? = null,
) {
    /** Every cuisine on offer, so the chips describe the data rather than a fixed list. */
    val availableCuisines: List<String>
        get() = restaurants.map { it.cuisine }.filter { it.isNotBlank() }.distinct().sorted()

    val visible: List<Restaurant>
        get() = restaurants.filter(::matches)

    val hasFilters: Boolean
        get() = query.isNotBlank() ||
            cuisines.isNotEmpty() ||
            priceBands.isNotEmpty() ||
            openNowOnly ||
            freeTonightOnly ||
            favouritesOnly

    /** True when filters are on and have excluded everything, which needs different words. */
    val isFilteredToNothing: Boolean
        get() = !isLoading && restaurants.isNotEmpty() && visible.isEmpty()

    val isEmpty: Boolean get() = !isLoading && restaurants.isEmpty()

    fun isFavourite(restaurantId: String): Boolean = restaurantId in favouriteIds

    private fun matches(restaurant: Restaurant): Boolean =
        matchesQuery(restaurant) &&
            (cuisines.isEmpty() || restaurant.cuisine in cuisines) &&
            (priceBands.isEmpty() || restaurant.priceBand in priceBands) &&
            (!openNowOnly || restaurant.id in openNowIds) &&
            (!freeTonightOnly || restaurant.id in freeTonightIds) &&
            (!favouritesOnly || restaurant.id in favouriteIds)

    /**
     * Matches the name, the cuisine and the address.
     *
     * A diner searching "westlands" is naming a part of town, and one searching "sushi" is
     * naming a kind of food. Matching only the name would answer neither, and both are what
     * people actually type.
     */
    private fun matchesQuery(restaurant: Restaurant): Boolean {
        val needle = query.trim()
        if (needle.isEmpty()) return true
        return listOf(restaurant.name, restaurant.cuisine, restaurant.address)
            .any { it.contains(needle, ignoreCase = true) }
    }
}

/** The price bands a restaurant can carry, rendered as currency symbols. */
val PRICE_BANDS = listOf(1, 2, 3, 4)

fun priceBandLabel(band: Int): String = "$".repeat(band.coerceIn(1, PRICE_BANDS.size))
