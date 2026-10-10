package com.example.kulapro.data.model

/**
 * Nutrition for a dish, from either a curated menu entry or the camera scanner.
 *
 * [assumedPortion] and [confidence] are not decoration. Portion size cannot be measured from
 * a single photo, so a scanned result is an estimate against an assumed serving, and the UI
 * is required to say so. A value that hides its own uncertainty would be worse than no value.
 */
data class NutritionInfo(
    val caloriesKcal: Double = 0.0,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
    val fibreG: Double = 0.0,
    val assumedPortion: String = "",
    val confidence: String = Confidence.MEDIUM.name,
    val likelyIngredients: List<String> = emptyList(),
    /** Curated menu data, or a model estimate. Shown to the user; never inferred silently. */
    val source: String = NutritionSource.ESTIMATED.name,
) {
    enum class Confidence { HIGH, MEDIUM, LOW }
    enum class NutritionSource { CURATED, ESTIMATED }
}
