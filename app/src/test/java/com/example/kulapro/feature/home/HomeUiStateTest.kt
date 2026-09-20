package com.example.kulapro.feature.home

import com.example.kulapro.data.model.Restaurant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeUiStateTest {

    private val bistro = Restaurant(
        id = "bistro",
        name = "The Bistro",
        cuisine = "European",
        priceBand = 3,
        address = "Woodvale Grove, Westlands, Nairobi",
    )
    private val sushi = Restaurant(
        id = "sushi",
        name = "Sushi Den",
        cuisine = "Japanese",
        priceBand = 4,
        address = "Riverside Drive, Nairobi",
    )
    private val tacos = Restaurant(
        id = "tacos",
        name = "Casa Tacos",
        cuisine = "Mexican",
        priceBand = 2,
        address = "Westlands, Nairobi",
    )

    private val loaded = HomeUiState(
        restaurants = listOf(bistro, sushi, tacos),
        isLoading = false,
    )

    @Test
    fun `with nothing set, everything is shown`() {
        assertEquals(3, loaded.visible.size)
        assertFalse(loaded.hasFilters)
    }

    @Test
    fun `a search matches the restaurant's name`() {
        val filtered = loaded.copy(query = "sushi")

        assertEquals(listOf("sushi"), filtered.visible.map { it.id })
    }

    @Test
    fun `a search matches the cuisine, because that is what people type`() {
        val filtered = loaded.copy(query = "mexican")

        assertEquals(listOf("tacos"), filtered.visible.map { it.id })
    }

    @Test
    fun `a search matches the area, because that is also what people type`() {
        val filtered = loaded.copy(query = "westlands")

        assertEquals(setOf("bistro", "tacos"), filtered.visible.map { it.id }.toSet())
    }

    @Test
    fun `a search ignores case and surrounding spaces`() {
        assertEquals(1, loaded.copy(query = "  SUSHI  ").visible.size)
    }

    @Test
    fun `a search with no match shows nothing rather than everything`() {
        assertTrue(loaded.copy(query = "zzzz").visible.isEmpty())
    }

    @Test
    fun `a cuisine filter narrows to that cuisine`() {
        val filtered = loaded.copy(cuisines = setOf("Japanese"))

        assertEquals(listOf("sushi"), filtered.visible.map { it.id })
    }

    @Test
    fun `two cuisines are read as either, not both, because a place has only one`() {
        val filtered = loaded.copy(cuisines = setOf("Japanese", "Mexican"))

        assertEquals(setOf("sushi", "tacos"), filtered.visible.map { it.id }.toSet())
    }

    @Test
    fun `a price filter narrows to that band`() {
        val filtered = loaded.copy(priceBands = setOf(2))

        assertEquals(listOf("tacos"), filtered.visible.map { it.id })
    }

    @Test
    fun `filters of different kinds are read as all of them at once`() {
        val filtered = loaded.copy(query = "nairobi", cuisines = setOf("Mexican"))

        assertEquals(listOf("tacos"), filtered.visible.map { it.id })
    }

    @Test
    fun `open now narrows to the restaurants serving`() {
        val filtered = loaded.copy(openNowOnly = true, openNowIds = setOf("sushi"))

        assertEquals(listOf("sushi"), filtered.visible.map { it.id })
    }

    @Test
    fun `a table tonight narrows to the restaurants with seats left`() {
        val filtered = loaded.copy(freeTonightOnly = true, freeTonightIds = setOf("bistro"))

        assertEquals(listOf("bistro"), filtered.visible.map { it.id })
    }

    @Test
    fun `kept narrows to what the diner has kept`() {
        val filtered = loaded.copy(favouritesOnly = true, favouriteIds = setOf("tacos"))

        assertEquals(listOf("tacos"), filtered.visible.map { it.id })
    }

    @Test
    fun `kept with nothing kept shows nothing, which the screen explains`() {
        val filtered = loaded.copy(favouritesOnly = true, favouriteIds = emptySet())

        assertTrue(filtered.visible.isEmpty())
        assertTrue(filtered.isFilteredToNothing)
    }

    @Test
    fun `filtered to nothing is different from having nothing`() {
        val nothingLoaded = HomeUiState(isLoading = false)

        assertTrue(nothingLoaded.isEmpty)
        assertFalse(nothingLoaded.isFilteredToNothing)
    }

    @Test
    fun `neither empty state fires while the list is still loading`() {
        val loading = HomeUiState(isLoading = true)

        assertFalse(loading.isEmpty)
        assertFalse(loading.isFilteredToNothing)
    }

    @Test
    fun `the cuisine chips describe the data rather than a fixed list`() {
        assertEquals(listOf("European", "Japanese", "Mexican"), loaded.availableCuisines)
    }

    @Test
    fun `a restaurant with no cuisine does not produce a blank chip`() {
        val withBlank = loaded.copy(restaurants = loaded.restaurants + Restaurant(id = "x"))

        assertEquals(3, withBlank.availableCuisines.size)
    }

    @Test
    fun `any one filter counts as filtered, so the clear control appears`() {
        assertTrue(loaded.copy(query = "a").hasFilters)
        assertTrue(loaded.copy(cuisines = setOf("Japanese")).hasFilters)
        assertTrue(loaded.copy(priceBands = setOf(1)).hasFilters)
        assertTrue(loaded.copy(openNowOnly = true).hasFilters)
        assertTrue(loaded.copy(freeTonightOnly = true).hasFilters)
        assertTrue(loaded.copy(favouritesOnly = true).hasFilters)
    }

    @Test
    fun `a search of only spaces is not a filter`() {
        assertFalse(loaded.copy(query = "   ").hasFilters)
        assertEquals(3, loaded.copy(query = "   ").visible.size)
    }

    @Test
    fun `a kept restaurant is reported as kept`() {
        val withFavourites = loaded.copy(favouriteIds = setOf("sushi"))

        assertTrue(withFavourites.isFavourite("sushi"))
        assertFalse(withFavourites.isFavourite("tacos"))
    }

    @Test
    fun `price bands render as the symbols people recognise`() {
        assertEquals("$", priceBandLabel(1))
        assertEquals("$$$$", priceBandLabel(4))
    }

    @Test
    fun `a price band outside the scale is clamped rather than drawn as a long row`() {
        assertEquals("$$$$", priceBandLabel(9))
        assertEquals("$", priceBandLabel(0))
    }
}
