package com.example.kulapro.feature.owner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.example.kulapro.data.model.RestaurantTable
import com.example.kulapro.ui.components.ErrorState
import com.example.kulapro.ui.components.KulaTextField
import com.example.kulapro.ui.components.MessageBanner
import com.example.kulapro.ui.components.PrimaryButton
import com.example.kulapro.ui.components.SecondaryButton

/**
 * How big the room is, and when it is open.
 *
 * Everything on this screen is read by the diner side to decide which sittings to offer, so
 * it is worth saying so on the screen: an owner changing a number here is changing what
 * strangers can book tonight, and that should not be a surprise.
 */
@Composable
fun CapacitySetupScreen(
    modifier: Modifier = Modifier,
    viewModel: CapacityViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CapacitySetupScreen(
        state = state,
        actions = CapacityActions(
            onSeatsChange = viewModel::setSeatsPerSitting,
            onSittingMinutesChange = viewModel::setSittingMinutes,
            onDayChange = viewModel::setDay,
            onSave = viewModel::save,
            onAddTable = viewModel::addTable,
            onEditTable = viewModel::editTable,
            onDeleteTable = viewModel::deleteTable,
            onTableDraftChange = viewModel::updateTableDraft,
            onSaveTable = viewModel::saveTable,
            onCancelTable = viewModel::cancelTableEditing,
            onRetry = viewModel::refresh,
            onDismissMessage = viewModel::dismissMessage,
        ),
        modifier = modifier,
    )
}

/** Grouped rather than passed one by one: twelve callbacks is not a parameter list. */
data class CapacityActions(
    val onSeatsChange: (String) -> Unit,
    val onSittingMinutesChange: (Int) -> Unit,
    val onDayChange: (DayHours) -> Unit,
    val onSave: () -> Unit,
    val onAddTable: () -> Unit,
    val onEditTable: (RestaurantTable) -> Unit,
    val onDeleteTable: (RestaurantTable) -> Unit,
    val onTableDraftChange: (TableDraft) -> Unit,
    val onSaveTable: () -> Unit,
    val onCancelTable: () -> Unit,
    val onRetry: () -> Unit,
    val onDismissMessage: () -> Unit,
)

@Composable
fun CapacitySetupScreen(
    state: CapacityUiState,
    actions: CapacityActions,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = {
            MessageBanner(message = state.message, onDismiss = actions.onDismissMessage)
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

                else -> CapacityForm(state = state, actions = actions)
            }
        }
    }

    state.tableDraft?.let { draft ->
        TableDialog(
            draft = draft,
            isSaving = state.isSaving,
            onChange = actions.onTableDraftChange,
            onSave = actions.onSaveTable,
            onCancel = actions.onCancelTable,
        )
    }
}

@Composable
private fun CapacityForm(state: CapacityUiState, actions: CapacityActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Seating", style = MaterialTheme.typography.titleMedium)
        KulaTextField(
            value = state.seatsPerSitting,
            onValueChange = actions.onSeatsChange,
            label = "Seats per sitting",
            error = state.seatsError.takeIf { state.seatsPerSitting.isNotEmpty() },
            helperText = "The most guests you will seat at once. Diners cannot book past it.",
            keyboardType = KeyboardType.Number,
        )

        if (state.seatsExceedTables) {
            Note(
                "Your floor plan holds ${state.seatsOnTheFloor} seats, fewer than you are " +
                    "selling per sitting. That is fine if you seat people the plan does not " +
                    "show, and a typo otherwise.",
            )
        }

        Text("How long a sitting lasts", style = MaterialTheme.typography.titleSmall)
        SittingLengthChooser(
            selected = state.sittingMinutes,
            onSelect = actions.onSittingMinutesChange,
        )

        HorizontalDivider()

        Text("Opening hours", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "A day you are closed is a day with no sittings at all.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.days.forEach { day ->
            DayRow(day = day, onChange = actions.onDayChange)
        }

        PrimaryButton(
            text = "Save seating and hours",
            onClick = actions.onSave,
            loading = state.isSaving,
            loadingText = "Saving",
            enabled = state.canSave,
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider()

        TablesSection(state = state, actions = actions)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SittingLengthChooser(selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SITTING_CHOICES.forEach { minutes ->
            FilterChip(
                selected = minutes == selected,
                onClick = { onSelect(minutes) },
                label = { Text("$minutes min") },
            )
        }
    }
}

@Composable
private fun DayRow(day: DayHours, onChange: (DayHours) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = day.label,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (day.isOpen) "Open" else "Closed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Switch(
                    checked = day.isOpen,
                    onCheckedChange = { onChange(day.copy(isOpen = it)) },
                )
            }

            if (day.isOpen) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KulaTextField(
                        value = day.opens,
                        onValueChange = { onChange(day.copy(opens = it)) },
                        label = "Opens",
                        error = day.error,
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                    KulaTextField(
                        value = day.closes,
                        onValueChange = { onChange(day.copy(closes = it)) },
                        label = "Closes",
                        // The message sits under the opening field only, so it is said once
                        // rather than twice about the same problem.
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TablesSection(state: CapacityUiState, actions: CapacityActions) {
    Text("Tables", style = MaterialTheme.typography.titleMedium)
    Text(
        text = if (state.tables.isEmpty()) {
            "Map your tables and diners can choose where they sit. Without them, booking " +
                "goes on the seat count alone, which still works."
        } else {
            "${state.tables.size} tables, ${state.seatsOnTheFloor} seats on the floor."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    state.tables.forEach { table ->
        TableRow(
            table = table,
            isDeleting = state.deletingTableId == table.id,
            onEdit = { actions.onEditTable(table) },
            onDelete = { actions.onDeleteTable(table) },
        )
    }

    SecondaryButton(
        text = "Add a table",
        onClick = actions.onAddTable,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TableRow(
    table: RestaurantTable,
    isDeleting: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(table.label, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "${table.seats} seats, ${table.zone}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, contentDescription = "Edit ${table.label}")
            }
            if (isDeleting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Remove ${table.label}",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun TableDialog(
    draft: TableDraft,
    isSaving: Boolean,
    onChange: (TableDraft) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (draft.isNew) "Add a table" else "Edit table") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                KulaTextField(
                    value = draft.label,
                    onValueChange = { onChange(draft.copy(label = it)) },
                    label = "Table name",
                    helperText = "What your staff call it, such as T4",
                    error = draft.labelError.takeIf { draft.label.isNotEmpty() },
                )
                KulaTextField(
                    value = draft.seats,
                    onValueChange = { onChange(draft.copy(seats = it.filter(Char::isDigit))) },
                    label = "Seats",
                    error = draft.seatsError.takeIf { draft.seats.isNotEmpty() },
                    keyboardType = KeyboardType.Number,
                )
                KulaTextField(
                    value = draft.zone,
                    onValueChange = { onChange(draft.copy(zone = it)) },
                    label = "Area",
                    helperText = "Window, Terrace, Bar, Main floor",
                )
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
private fun Note(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(12.dp),
        )
    }
}
