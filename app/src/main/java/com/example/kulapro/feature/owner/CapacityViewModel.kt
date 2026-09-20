package com.example.kulapro.feature.owner

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kulapro.data.model.RestaurantTable
import com.example.kulapro.data.repository.OwnerRepository
import com.example.kulapro.data.repository.RestaurantRepository
import com.example.kulapro.data.repository.Result
import com.example.kulapro.domain.OpeningHours
import com.example.kulapro.ui.components.UiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Sets what the restaurant can seat, and when.
 *
 * This is the other half of the availability promise. The diner side already refuses to
 * overbook a sitting; until now the numbers it enforced could only be put there by a seed
 * script, so a real restaurant had no way to tell the app how big it is.
 */
@HiltViewModel
class CapacityViewModel @Inject constructor(
    private val ownerRepository: OwnerRepository,
    private val restaurantRepository: RestaurantRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val restaurantId: String =
        savedStateHandle.get<String>(OwnerBookingsViewModel.ARG_RESTAURANT_ID).orEmpty()

    private val _state = MutableStateFlow(CapacityUiState())
    val state: StateFlow<CapacityUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            when (val result = restaurantRepository.restaurant(restaurantId)) {
                is Result.Success -> _state.update { current ->
                    val restaurant = result.data
                    current.copy(
                        restaurantName = restaurant.name,
                        seatsPerSitting = restaurant.capacityPerSlot.toString(),
                        sittingMinutes = restaurant.slotDurationMinutes
                            .takeIf { it > 0 } ?: DEFAULT_SITTING_MINUTES,
                        days = OpeningHours.DAY_KEYS.map { key ->
                            DayHours.of(key, restaurant.openingHours[key])
                        },
                        isLoading = false,
                    )
                }

                is Result.Failure -> _state.update {
                    it.copy(isLoading = false, loadError = result.message)
                }
            }
            loadTables()
        }
    }

    private suspend fun loadTables() {
        // A restaurant with no floor plan is a supported case, not an error: booking falls
        // back to the seat count alone. So a failure here is reported and the rest stands.
        when (val result = restaurantRepository.tables(restaurantId)) {
            is Result.Success -> _state.update { it.copy(tables = result.data) }
            is Result.Failure -> _state.update {
                it.copy(message = UiMessage.error(result.message))
            }
        }
    }

    fun setSeatsPerSitting(seats: String) =
        _state.update { it.copy(seatsPerSitting = seats.filter(Char::isDigit)) }

    fun setSittingMinutes(minutes: Int) = _state.update { it.copy(sittingMinutes = minutes) }

    fun setDay(day: DayHours) = _state.update { current ->
        current.copy(days = current.days.map { if (it.key == day.key) day else it })
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun save() {
        val current = _state.value
        // Guarded here as well as on the button: a disabled button is a courtesy, not a
        // guarantee, and saving a half-typed closing time would shut the restaurant.
        if (!current.canSave) return

        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = ownerRepository.updateSeating(
                restaurantId = restaurantId,
                capacityPerSlot = current.seatsPerSitting.toIntOrNull() ?: 0,
                slotDurationMinutes = current.sittingMinutes,
                // Days the restaurant is shut are absent rather than stored empty, which is
                // what the availability calculation already treats as closed.
                openingHours = current.days
                    .mapNotNull { day -> day.asStoredRange()?.let { day.key to it } }
                    .toMap(),
            )
            _state.update {
                it.copy(
                    isSaving = false,
                    message = when (result) {
                        is Result.Success -> UiMessage.success("Saved. Diners see this now.")
                        is Result.Failure -> UiMessage.error(result.message)
                    },
                )
            }
        }
    }

    fun addTable() = _state.update { it.copy(tableDraft = TableDraft()) }

    fun editTable(table: RestaurantTable) =
        _state.update { it.copy(tableDraft = TableDraft.of(table)) }

    fun updateTableDraft(draft: TableDraft) = _state.update { it.copy(tableDraft = draft) }

    fun cancelTableEditing() = _state.update { it.copy(tableDraft = null) }

    fun saveTable() {
        val current = _state.value
        val draft = current.tableDraft ?: return
        if (!draft.canSave) return

        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = ownerRepository.saveTable(draft.toTable(restaurantId, current.tables))
            _state.update { it.copy(isSaving = false) }
            when (result) {
                is Result.Success -> {
                    _state.update {
                        it.copy(
                            tableDraft = null,
                            message = UiMessage.success(
                                if (draft.isNew) "Table added" else "Table updated",
                            ),
                        )
                    }
                    loadTables()
                }

                is Result.Failure ->
                    _state.update { it.copy(message = UiMessage.error(result.message)) }
            }
        }
    }

    fun deleteTable(table: RestaurantTable) {
        _state.update { it.copy(deletingTableId = table.id) }
        viewModelScope.launch {
            val result = ownerRepository.deleteTable(restaurantId, table.id)
            _state.update { it.copy(deletingTableId = null) }
            when (result) {
                is Result.Success -> _state.update {
                    it.copy(
                        tables = it.tables.filterNot { existing -> existing.id == table.id },
                        message = UiMessage.success("${table.label} removed"),
                    )
                }

                is Result.Failure ->
                    _state.update { it.copy(message = UiMessage.error(result.message)) }
            }
        }
    }
}
