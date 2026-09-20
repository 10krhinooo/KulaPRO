package com.example.kulapro.data.scanner

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The wire contract, tested against strings rather than against a server.
 *
 * These are the cases that decide whether a number reaches the screen with its meaning
 * intact, so they are checked here rather than trusted.
 */
@RunWith(RobolectricTestRunner::class)
class ScannerResponseTest {

    private fun json(body: String) = parse(JSONObject(body))

    @Test
    fun `a full result is read into the app's own type`() {
        val result = json(
            """
            {
              "is_food": true,
              "dish_name": "Ugali na nyama",
              "cuisine": "Kenyan",
              "confidence": "medium",
              "assumed_portion": "one restaurant main, about 400g",
              "calories_kcal": 780.0,
              "protein_g": 42.0,
              "carbs_g": 88.0,
              "fat_g": 28.0,
              "fibre_g": 6.0,
              "likely_ingredients": ["maize flour", "beef"],
              "health_notes": ["Ask the restaurant about allergens"],
              "cached": false
            }
            """.trimIndent(),
        )

        assertTrue(result.isFood)
        assertEquals("Ugali na nyama", result.dishName)
        assertEquals("Kenyan", result.cuisine)
        assertEquals(DishNutrition.Confidence.MEDIUM, result.confidence)
        assertEquals("one restaurant main, about 400g", result.assumedPortion)
        assertEquals(780.0, result.caloriesKcal, 0.001)
        assertEquals(listOf("maize flour", "beef"), result.likelyIngredients)
        assertFalse(result.wasCached)
    }

    @Test
    fun `a cached result says so, because it cost nothing and was instant`() {
        val result = json("""{"is_food": true, "dish_name": "Ugali", "cached": true}""")

        assertTrue(result.wasCached)
    }

    @Test
    fun `a non food photo never arrives carrying macros`() {
        val result = json(
            """{"is_food": false, "dish_name": "a stapler", "calories_kcal": 450.0}""",
        )

        assertFalse(result.isFood)
        assertEquals(0.0, result.caloriesKcal, 0.001)
        assertEquals("", result.dishName)
    }

    @Test
    fun `an unrecognised confidence is read as the least confident, never the most`() {
        val result = json("""{"is_food": true, "confidence": "certain"}""")

        assertEquals(DishNutrition.Confidence.LOW, result.confidence)
    }

    @Test
    fun `a missing confidence is read as the least confident`() {
        val result = json("""{"is_food": true}""")

        assertEquals(DishNutrition.Confidence.LOW, result.confidence)
    }

    @Test
    fun `a cuisine the model could not place comes through as nothing, not as "null"`() {
        val result = json("""{"is_food": true, "cuisine": null}""")

        assertNull(result.cuisine)
    }

    @Test
    fun `missing numbers read as zero rather than throwing`() {
        val result = json("""{"is_food": true, "dish_name": "Ugali"}""")

        assertEquals(0.0, result.proteinG, 0.001)
        assertEquals(0.0, result.fibreG, 0.001)
    }

    @Test
    fun `an empty list of notes is a list, not a null`() {
        val result = json("""{"is_food": true}""")

        assertTrue(result.healthNotes.isEmpty())
        assertTrue(result.likelyIngredients.isEmpty())
    }
}
