package com.example.kulapro.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Test

class DishNutritionTest {

    private val dish = DishNutrition(
        isFood = true,
        dishName = "Ugali na nyama",
        assumedPortion = "one restaurant main",
        caloriesKcal = 800.0,
        proteinG = 40.0,
        carbsG = 90.0,
        fatG = 30.0,
        fibreG = 6.0,
    )

    @Test
    fun `halving the portion halves every number`() {
        val half = dish.scaledBy(0.5)

        assertEquals(400.0, half.caloriesKcal, 0.001)
        assertEquals(20.0, half.proteinG, 0.001)
        assertEquals(45.0, half.carbsG, 0.001)
        assertEquals(15.0, half.fatG, 0.001)
        assertEquals(3.0, half.fibreG, 0.001)
    }

    @Test
    fun `scaling leaves the estimate's own basis alone`() {
        val half = dish.scaledBy(0.5)

        // The serving the model assumed is what the numbers were calculated against.
        // Rewriting it here would erase the one thing that makes them readable.
        assertEquals("one restaurant main", half.assumedPortion)
        assertEquals(dish.confidence, half.confidence)
        assertEquals(dish.dishName, half.dishName)
    }

    @Test
    fun `scaling by one changes nothing`() {
        assertEquals(dish, dish.scaledBy(1.0))
    }

    @Test
    fun `every confidence has wording a person can read`() {
        DishNutrition.Confidence.entries.forEach {
            assert(it.label.isNotBlank())
            assert(it.explanation.isNotBlank())
        }
    }

    @Test
    fun `confidence is read case insensitively, as the wire sends it lowercase`() {
        assertEquals(
            DishNutrition.Confidence.HIGH,
            DishNutrition.Confidence.fromWire("high"),
        )
        assertEquals(
            DishNutrition.Confidence.HIGH,
            DishNutrition.Confidence.fromWire("HIGH"),
        )
    }
}
