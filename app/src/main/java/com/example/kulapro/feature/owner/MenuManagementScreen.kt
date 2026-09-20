package com.example.kulapro.feature.owner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kulapro.data.model.MenuItem
import com.example.kulapro.ui.components.AnimatedListItem
import com.example.kulapro.ui.components.EmptyState
import com.example.kulapro.ui.components.ErrorState
import com.example.kulapro.ui.components.KulaTextField
import com.example.kulapro.ui.components.MessageBanner
import com.example.kulapro.ui.components.PrimaryButton
import com.example.kulapro.ui.components.rememberListEntryAnimator
import com.example.kulapro.util.Money

/**
 * The menu, as the restaurant maintains it.
 *
 * Nutrition sits on the same form as the price rather than behind a separate screen, because
 * the moment someone is typing a dish is the only moment they know its recipe. A dish that
 * carries nutrition is one the camera scanner answers from Firestore instead of paying a
 * model for.
 */
@Composable
fun MenuManagementScreen(
    modifier: Modifier = Modifier,
    viewModel: MenuViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    MenuManagementScreen(
        state = state,
        actions = MenuActions(
            onAdd = viewModel::addDish,
            onEdit = viewModel::editDish,
            onDelete = viewModel::deleteDish,
            onDraftChange = viewModel::updateDraft,
            onSave = viewModel::saveDraft,
            onCancel = viewModel::cancelEditing,
            onRetry = viewModel::refresh,
            onDismissMessage = viewModel::dismissMessage,
        ),
        modifier = modifier,
    )
}

/** Grouped so the screen is not a nine parameter function, which detekt rightly refuses. */
data class MenuActions(
    val onAdd: () -> Unit,
    val onEdit: (MenuItem) -> Unit,
    val onDelete: (MenuItem) -> Unit,
    val onDraftChange: (MenuDraft) -> Unit,
    val onSave: () -> Unit,
    val onCancel: () -> Unit,
    val onRetry: () -> Unit,
    val onDismissMessage: () -> Unit,
)

@Composable
fun MenuManagementScreen(
    state: MenuUiState,
    actions: MenuActions,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = {
            MessageBanner(message = state.message, onDismiss = actions.onDismissMessage)
        },
        floatingActionButton = {
            if (!state.isLoading && state.loadError == null) {
                ExtendedFloatingActionButton(
                    onClick = actions.onAdd,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Add a dish") },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.isLoading -> CircularProgressIndicator()

                state.loadError != null ->
                    ErrorState(message = state.loadError, onRetry = actions.onRetry)

                state.isEmpty -> EmptyState(
                    title = "No dishes yet",
                    description = "Add what you serve, and diners see it on your listing.",
                    icon = Icons.Outlined.RestaurantMenu,
                    actionLabel = "Add a dish",
                    onAction = actions.onAdd,
                )

                else -> MenuList(state = state, actions = actions)
            }
        }
    }

    state.draft?.let { draft ->
        DishDialog(
            draft = draft,
            isSaving = state.isSaving,
            onChange = actions.onDraftChange,
            onSave = actions.onSave,
            onCancel = actions.onCancel,
        )
    }
}

@Composable
private fun MenuList(state: MenuUiState, actions: MenuActions) {
    val animator = rememberListEntryAnimator()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            // Clear of the floating button, so the last dish is never hidden behind it.
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "summary") {
            NutritionCoverage(
                withNutrition = state.withNutrition,
                total = state.items.size,
            )
        }

        state.sections.forEach { section ->
            item(key = "section-${section.title}") {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            itemsIndexed(section.items, key = { _, item -> item.id }) { index, item ->
                AnimatedListItem(index = index, key = item.id, animator = animator) {
                    DishRow(
                        item = item,
                        isDeleting = state.deletingId == item.id,
                        onEdit = { actions.onEdit(item) },
                        onDelete = { actions.onDelete(item) },
                    )
                }
            }
        }
    }
}

/**
 * How much of the menu the scanner can answer for free.
 *
 * Shown rather than kept internal, because it is the one number that tells the restaurant
 * what filling in nutrition actually buys them.
 */
@Composable
private fun NutritionCoverage(withNutrition: Int, total: Int, modifier: Modifier = Modifier) {
    if (total == 0) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "$withNutrition of $total dishes have nutrition",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = "Dishes with nutrition answer instantly, and cost nothing to look up.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun DishRow(
    item: MenuItem,
    isDeleting: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(item.name, style = MaterialTheme.typography.titleSmall)
                if (item.description.isNotBlank()) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = Money.format(item.priceCents, item.currency),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    if (item.nutrition != null) {
                        AssistChip(
                            onClick = onEdit,
                            label = { Text("${item.nutrition.caloriesKcal.toInt()} kcal") },
                        )
                    }
                }
            }

            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, contentDescription = "Edit ${item.name}")
            }
            if (isDeleting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Remove ${item.name}",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun DishDialog(
    draft: MenuDraft,
    isSaving: Boolean,
    onChange: (MenuDraft) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (draft.isNew) "Add a dish" else "Edit dish") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                KulaTextField(
                    value = draft.name,
                    onValueChange = { onChange(draft.copy(name = it)) },
                    label = "Dish name",
                    // Only after something has been typed: telling someone a field is
                    // empty before they have reached it is scolding, not helping.
                    error = draft.nameError.takeIf { draft.name.isNotEmpty() },
                )
                KulaTextField(
                    value = draft.description,
                    onValueChange = { onChange(draft.copy(description = it)) },
                    label = "Description",
                )
                KulaTextField(
                    value = draft.price,
                    onValueChange = { onChange(draft.copy(price = it)) },
                    label = "Price",
                    error = draft.priceError.takeIf { draft.price.isNotEmpty() },
                    keyboardType = KeyboardType.Decimal,
                )
                KulaTextField(
                    value = draft.category,
                    onValueChange = { onChange(draft.copy(category = it)) },
                    label = "Course",
                    helperText = "Starters, Mains, Desserts",
                )

                HorizontalDivider()
                Text("Nutrition", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Optional, and only if you know it. Leave it blank rather than " +
                        "guessing: a number here is shown to diners as fact.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MacroFields(draft = draft, onChange = onChange)
            }
        },
        confirmButton = {
            PrimaryButton(
                text = "Save",
                onClick = onSave,
                loading = isSaving,
                loadingText = "Saving",
                enabled = draft.canSave,
            )
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun MacroFields(draft: MenuDraft, onChange: (MenuDraft) -> Unit) {
    KulaTextField(
        value = draft.calories,
        onValueChange = { onChange(draft.copy(calories = it.filter(Char::isDigit))) },
        label = "Calories (kcal)",
        keyboardType = KeyboardType.Number,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KulaTextField(
            value = draft.protein,
            onValueChange = { onChange(draft.copy(protein = it)) },
            label = "Protein g",
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.weight(1f),
        )
        KulaTextField(
            value = draft.carbs,
            onValueChange = { onChange(draft.copy(carbs = it)) },
            label = "Carbs g",
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.weight(1f),
        )
        KulaTextField(
            value = draft.fat,
            onValueChange = { onChange(draft.copy(fat = it)) },
            label = "Fat g",
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.weight(1f),
        )
    }
}
