package com.example.kulapro.data.scanner

/**
 * What the scanner came back with.
 *
 * [assumedPortion] and [confidence] are not decoration and must never be dropped on the way
 * to the screen. Portion size cannot be measured from one photograph, so a number without
 * the serving it was calculated against, and without how sure the model was, would be a
 * confident lie. The UI is required to show both.
 */
data class DishNutrition(
    val isFood: Boolean = false,
    val dishName: String = "",
    val cuisine: String? = null,
    val confidence: Confidence = Confidence.MEDIUM,
    val assumedPortion: String = "",
    val caloriesKcal: Double = 0.0,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
    val fibreG: Double = 0.0,
    val likelyIngredients: List<String> = emptyList(),
    val healthNotes: List<String> = emptyList(),
    /** True when the answer came from the shared cache, so it cost nothing. */
    val wasCached: Boolean = false,
) {
    /**
     * Scales every macro for a different serving.
     *
     * The model assumed a plate; the diner is looking at theirs. Without this the only
     * honest thing the screen could say is "this is probably wrong", which helps nobody.
     */
    fun scaledBy(factor: Double): DishNutrition = copy(
        caloriesKcal = caloriesKcal * factor,
        proteinG = proteinG * factor,
        carbsG = carbsG * factor,
        fatG = fatG * factor,
        fibreG = fibreG * factor,
    )

    enum class Confidence(val label: String, val explanation: String) {
        HIGH(
            "Fairly confident",
            "A common dish, clearly shown. Still an estimate against the serving below.",
        ),
        MEDIUM(
            "Roughly",
            "Kitchens differ, and a photo does not show oil, butter or sugar in a sauce.",
        ),
        LOW(
            "Very rough",
            "This dish is hard to read from a photo. Treat the numbers as a starting point.",
        ),
        ;

        companion object {
            /** Anything unrecognised is the least confident reading, never the most. */
            fun fromWire(value: String?): Confidence =
                entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: LOW
        }
    }
}
