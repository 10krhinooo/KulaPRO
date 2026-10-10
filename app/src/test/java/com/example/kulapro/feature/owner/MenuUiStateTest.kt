package com.example.kulapro.feature.owner

import com.example.kulapro.data.model.MenuItem
import com.example.kulapro.data.model.NutritionInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuUiStateTest {

    private fun dish(
        id: String,
        name: String = "Dish $id",
        category: String = "",
        nutrition: NutritionInfo? = null,
    ) = MenuItem(id = id, name = name, category = category, nutrition = nutrition)

    @Test
    fun `courses keep the order they were entered, not alphabetical order`() {
        val state = MenuUiState(
            isLoading = false,
            items = listOf(
                dish("1", category = "Starters"),
                dish("2", category = "Mains"),
                dish("3", category = "Starters"),
            ),
        )

        assertEquals(listOf("Starters", "Mains"), state.sections.map { it.title })
        assertEquals(2, state.sections.first().items.size)
    }

    @Test
    fun `uncategorised dishes are gathered at the end rather than dropped`() {
        val state = MenuUiState(
            isLoading = false,
            items = listOf(dish("1", category = "Mains"), dish("2")),
        )

        assertEquals(2, state.sections.size)
        assertEquals("Everything else", state.sections.last().title)
        assertEquals(listOf("2"), state.sections.last().items.map { it.id })
    }

    @Test
    fun `a menu with no courses at all is one section`() {
        val state = MenuUiState(isLoading = false, items = listOf(dish("1"), dish("2")))

        assertEquals(1, state.sections.size)
        assertEquals(2, state.sections.single().items.size)
    }

    @Test
    fun `dishes carrying nutrition are counted, because that is what saves a model call`() {
        val state = MenuUiState(
            isLoading = false,
            items = listOf(
                dish("1", nutrition = NutritionInfo(caloriesKcal = 500.0)),
                dish("2"),
            ),
        )

        assertEquals(1, state.withNutrition)
    }

    @Test
    fun `empty means loaded with nothing in it, not still loading`() {
        assertFalse(MenuUiState(isLoading = true).isEmpty)
        assertFalse(MenuUiState(isLoading = false, loadError = "no").isEmpty)
        assertTrue(MenuUiState(isLoading = false).isEmpty)
    }
}

class MenuDraftTest {

    @Test
    fun `a dish with no name cannot be saved`() {
        val draft = MenuDraft(name = "  ", price = "500")

        assertNotNull(draft.nameError)
        assertFalse(draft.canSave)
    }

    @Test
    fun `a price that is not a price cannot be saved`() {
        val draft = MenuDraft(name = "Ugali", price = "a lot")

        assertNotNull(draft.priceError)
        assertFalse(draft.canSave)
    }

    @Test
    fun `a blank price is refused rather than stored as free`() {
        assertNotNull(MenuDraft(name = "Ugali", price = "").priceError)
    }

    @Test
    fun `a named dish with a readable price can be saved`() {
        val draft = MenuDraft(name = "Ugali", price = "250")

        assertNull(draft.nameError)
        assertNull(draft.priceError)
        assertTrue(draft.canSave)
    }

    @Test
    fun `the price is stored as cents, so it cannot drift`() {
        val item = MenuDraft(name = "Ugali", price = "250.50")
            .toMenuItem(restaurantId = "r1", currency = "KES")

        assertEquals(25_050L, item.priceCents)
        assertEquals("KES", item.currency)
        assertEquals("r1", item.restaurantId)
    }

    @Test
    fun `surrounding spaces are trimmed off what is stored`() {
        val item = MenuDraft(name = "  Ugali  ", price = "250", category = " Mains ")
            .toMenuItem("r1", "KES")

        assertEquals("Ugali", item.name)
        assertEquals("Mains", item.category)
    }

    @Test
    fun `nutrition left blank is stored as absent, not as zero`() {
        val item = MenuDraft(name = "Ugali", price = "250").toMenuItem("r1", "KES")

        assertNull(item.nutrition)
    }

    @Test
    fun `a single macro is enough for the dish to carry nutrition`() {
        val item = MenuDraft(name = "Ugali", price = "250", calories = "320")
            .toMenuItem("r1", "KES")

        assertNotNull(item.nutrition)
        assertEquals(320.0, item.nutrition?.caloriesKcal ?: 0.0, 0.001)
    }

    @Test
    fun `nutrition typed by the restaurant is marked curated, not estimated`() {
        val nutrition = MenuDraft(name = "Ugali", price = "250", calories = "320")
            .toMenuItem("r1", "KES")
            .nutrition

        assertEquals(NutritionInfo.NutritionSource.CURATED.name, nutrition?.source)
        assertEquals(NutritionInfo.Confidence.HIGH.name, nutrition?.confidence)
    }

    @Test
    fun `an existing dish opens with its stored values back in the form`() {
        val item = MenuItem(
            id = "m1",
            name = "Ugali",
            description = "With sukuma",
            priceCents = 25_050,
            category = "Mains",
            nutrition = NutritionInfo(caloriesKcal = 320.0, proteinG = 12.5),
        )

        val draft = MenuDraft.of(item)

        assertEquals("m1", draft.id)
        assertEquals("Ugali", draft.name)
        assertEquals("250.50", draft.price)
        assertEquals("Mains", draft.category)
        assertEquals("320", draft.calories)
        assertEquals("12.5", draft.protein)
        assertFalse(draft.isNew)
    }

    @Test
    fun `a macro stored as zero comes back blank, because zero means never entered`() {
        val draft = MenuDraft.of(
            MenuItem(id = "m1", nutrition = NutritionInfo(caloriesKcal = 320.0)),
        )

        assertEquals("320", draft.calories)
        assertEquals("", draft.protein)
    }

    @Test
    fun `a draft with no id is a new dish`() {
        assertTrue(MenuDraft().isNew)
    }
}
