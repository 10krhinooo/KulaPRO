package com.example.kulapro.feature.owner

import com.example.kulapro.data.model.MenuItem
import com.example.kulapro.data.model.NutritionInfo
import com.example.kulapro.ui.components.UiMessage
import com.example.kulapro.util.Money

/** The menu as the restaurant sees it, grouped the way it is printed. */
data class MenuUiState(
    val items: List<MenuItem> = emptyList(),
    val isLoading: Boolean = true,
    val draft: MenuDraft? = null,
    val isSaving: Boolean = false,
    val deletingId: String? = null,
    val loadError: String? = null,
    val message: UiMessage? = null,
) {
    /**
     * Courses in the order they were entered, with anything uncategorised last.
     *
     * Alphabetical would put Desserts before Starters, which is not how a menu reads. The
     * order the restaurant typed them in is the order the restaurant meant.
     */
    val sections: List<MenuSection>
        get() {
            val named = items.filter { it.category.isNotBlank() }
                .groupBy { it.category }
                .map { (category, dishes) -> MenuSection(category, dishes) }
            val loose = items.filter { it.category.isBlank() }
            return if (loose.isEmpty()) named else named + MenuSection(OTHER_SECTION, loose)
        }

    /** Dishes that would let the scanner answer from Firestore instead of paying for a call. */
    val withNutrition: Int get() = items.count { it.nutrition != null }

    val isEmpty: Boolean get() = !isLoading && loadError == null && items.isEmpty()
}

data class MenuSection(val title: String, val items: List<MenuItem>)

/**
 * A dish being written, held as the text that was typed rather than as parsed values.
 *
 * Parsing on every keystroke would fight the person typing: "12." is not a number, and a
 * field that erases it mid-entry is unusable. The text is kept, validated when it matters,
 * and only turned into a [MenuItem] on save.
 */
data class MenuDraft(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val price: String = "",
    val category: String = "",
    val calories: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
) {
    val isNew: Boolean get() = id.isBlank()

    val nameError: String?
        get() = if (name.isBlank()) "Give the dish a name diners will recognise." else null

    val priceError: String?
        get() = when {
            price.isBlank() -> "Enter a price, even if it is 0."
            Money.parseToCents(price) == null -> "That is not a price. Try 1250 or 1250.50."
            else -> null
        }

    val canSave: Boolean get() = nameError == null && priceError == null

    /**
     * The nutrition typed so far, or null if none of it was.
     *
     * All four fields blank means the restaurant has not filled this in, which is different
     * from a dish with no calories. Storing zeroes for "not known" would let the scanner
     * answer confidently with a number nobody entered.
     */
    private fun nutritionOrNull(): NutritionInfo? {
        val values = listOf(calories, protein, carbs, fat)
        if (values.all { it.isBlank() }) return null
        return NutritionInfo(
            caloriesKcal = calories.toDoubleOrNull() ?: 0.0,
            proteinG = protein.toDoubleOrNull() ?: 0.0,
            carbsG = carbs.toDoubleOrNull() ?: 0.0,
            fatG = fat.toDoubleOrNull() ?: 0.0,
            // Typed by the restaurant from its own recipe, so it is curated rather than
            // estimated, and the scanner may answer from it without a model call.
            confidence = NutritionInfo.Confidence.HIGH.name,
            source = NutritionInfo.NutritionSource.CURATED.name,
        )
    }

    fun toMenuItem(restaurantId: String, currency: String): MenuItem = MenuItem(
        id = id,
        restaurantId = restaurantId,
        name = name.trim(),
        description = description.trim(),
        priceCents = Money.parseToCents(price) ?: 0,
        currency = currency,
        category = category.trim(),
        nutrition = nutritionOrNull(),
    )

    companion object {
        /** Opens an existing dish for editing, with the stored price back as plain text. */
        fun of(item: MenuItem): MenuDraft = MenuDraft(
            id = item.id,
            name = item.name,
            description = item.description,
            price = Money.toEditableText(item.priceCents),
            category = item.category,
            calories = item.nutrition?.caloriesKcal.asEditableText(),
            protein = item.nutrition?.proteinG.asEditableText(),
            carbs = item.nutrition?.carbsG.asEditableText(),
            fat = item.nutrition?.fatG.asEditableText(),
        )
    }
}

private const val OTHER_SECTION = "Everything else"

/**
 * A stored macro as text for an edit field, with zero shown as blank.
 *
 * Zero here means "never entered", so putting a literal 0 in the field would invite the
 * restaurant to save it as a real measurement.
 */
private fun Double?.asEditableText(): String =
    this?.takeIf { it > 0 }?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }
        .orEmpty()
