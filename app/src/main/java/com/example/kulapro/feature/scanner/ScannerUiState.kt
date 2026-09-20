package com.example.kulapro.feature.scanner

import com.example.kulapro.data.scanner.DishNutrition

/**
 * What the scanner screen shows.
 *
 * [portionFactor] is separate from the result on purpose. The model estimated against a
 * serving it assumed; the diner is looking at theirs, and adjusting must not lose what was
 * originally returned or a second adjustment would compound the first.
 */
data class ScannerUiState(
    val isScanning: Boolean = false,
    val result: DishNutrition? = null,
    val portionFactor: Double = 1.0,
    val errorMessage: String? = null,
    val isAvailable: Boolean = true,
    /** Set when the scanner was opened from a particular dish on a menu. */
    val dishFromMenu: String = "",
) {
    /** The numbers actually drawn, after the diner's own portion adjustment. */
    val shown: DishNutrition? get() = result?.scaledBy(portionFactor)

    val hasResult: Boolean get() = result != null

    /** True when the photograph was not of food, which is a friendly answer, not an error. */
    val isNotFood: Boolean get() = result?.isFood == false

    /** True when the restaurant's own figures answered, so nothing was sent anywhere. */
    val answeredFromMenu: Boolean get() = result?.isFromMenu == true

    /** A named dish with nothing on file still needs a photograph, and says whose it is. */
    val needsPhotoForNamedDish: Boolean
        get() = dishFromMenu.isNotBlank() && result == null && !isScanning

    /** Said out loud when the portion is not the one the estimate was calculated against. */
    val portionLabel: String
        get() = when (portionFactor) {
            HALF -> "Half of it"
            1.0 -> "The serving below"
            else -> "One and a half times"
        }
}

/** The adjustments people actually make. A free slider implies a precision nobody has. */
val PORTION_CHOICES = listOf(HALF, 1.0, ONE_AND_A_HALF)

const val HALF = 0.5
const val ONE_AND_A_HALF = 1.5
