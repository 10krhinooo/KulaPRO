package com.example.kulapro.feature.owner

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kulapro.data.model.MenuItem
import com.example.kulapro.data.repository.OwnerRepository
import com.example.kulapro.data.repository.RestaurantRepository
import com.example.kulapro.data.repository.Result
import com.example.kulapro.ui.components.UiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The restaurant's own menu, editable.
 *
 * Until now menu data could only be created by running a seed script by hand, which made the
 * diner-side menu a fixture rather than a feature. Every dish entered here also spares the
 * camera scanner a model call, because an item that carries its own nutrition is answered
 * from Firestore.
 */
@HiltViewModel
class MenuViewModel @Inject constructor(
    private val ownerRepository: OwnerRepository,
    private val restaurantRepository: RestaurantRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val restaurantId: String =
        savedStateHandle.get<String>(OwnerBookingsViewModel.ARG_RESTAURANT_ID).orEmpty()

    private val _state = MutableStateFlow(MenuUiState())
    val state: StateFlow<MenuUiState> = _state.asStateFlow()

    /**
     * The currency the restaurant already prices in.
     *
     * Taken from the existing menu rather than asked for on every dish, so a menu cannot end
     * up half in one currency and half in another.
     */
    private var currency: String = DEFAULT_CURRENCY

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            when (val result = restaurantRepository.menu(restaurantId)) {
                is Result.Success -> {
                    currency = result.data.firstOrNull()?.currency?.takeIf { it.isNotBlank() }
                        ?: DEFAULT_CURRENCY
                    _state.update { it.copy(items = result.data, isLoading = false) }
                }

                is Result.Failure ->
                    _state.update { it.copy(isLoading = false, loadError = result.message) }
            }
        }
    }

    fun addDish() = _state.update { it.copy(draft = MenuDraft()) }

    fun editDish(item: MenuItem) = _state.update { it.copy(draft = MenuDraft.of(item)) }

    fun updateDraft(draft: MenuDraft) = _state.update { it.copy(draft = draft) }

    fun cancelEditing() = _state.update { it.copy(draft = null) }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun saveDraft() {
        val draft = _state.value.draft ?: return
        // Checked here as well as on the button, because a disabled button is a courtesy and
        // not a guarantee: the state can change between the two.
        if (!draft.canSave) return

        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = ownerRepository.saveMenuItem(draft.toMenuItem(restaurantId, currency))
            _state.update { it.copy(isSaving = false) }
            when (result) {
                is Result.Success -> {
                    _state.update {
                        it.copy(
                            draft = null,
                            message = UiMessage.success(
                                if (draft.isNew) "Added to the menu" else "Dish updated",
                            ),
                        )
                    }
                    refresh()
                }

                is Result.Failure ->
                    _state.update { it.copy(message = UiMessage.error(result.message)) }
            }
        }
    }

    fun deleteDish(item: MenuItem) {
        _state.update { it.copy(deletingId = item.id) }
        viewModelScope.launch {
            val result = ownerRepository.deleteMenuItem(restaurantId, item.id)
            _state.update { it.copy(deletingId = null) }
            when (result) {
                is Result.Success -> {
                    // Removed here rather than waiting on a reload, so the row disappears
                    // when it is tapped instead of a moment later.
                    _state.update {
                        it.copy(
                            items = it.items.filterNot { existing -> existing.id == item.id },
                            message = UiMessage.success("${item.name} removed"),
                        )
                    }
                }

                is Result.Failure ->
                    _state.update { it.copy(message = UiMessage.error(result.message)) }
            }
        }
    }
}

private const val DEFAULT_CURRENCY = "KES"
