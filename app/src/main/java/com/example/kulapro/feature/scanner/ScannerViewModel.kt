package com.example.kulapro.feature.scanner

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kulapro.data.repository.Result
import com.example.kulapro.data.repository.RestaurantRepository
import com.example.kulapro.data.scanner.DishNutrition
import com.example.kulapro.data.scanner.ScannerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Turns a photograph into an estimate, and keeps the honesty attached to it.
 *
 * The result is held exactly as it came back, and the portion adjustment is applied on the
 * way to the screen rather than folded in. Two adjustments then do not compound, and the
 * serving the model actually assumed is never lost.
 */
@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val scannerRepository: ScannerRepository,
    private val restaurantRepository: RestaurantRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val restaurantId: String = savedStateHandle.get<String>(ARG_RESTAURANT_ID).orEmpty()
    private val menuItemId: String = savedStateHandle.get<String>(ARG_MENU_ITEM_ID).orEmpty()

    private val _state = MutableStateFlow(
        ScannerUiState(isAvailable = scannerRepository.isAvailable),
    )
    val state: StateFlow<ScannerUiState> = _state.asStateFlow()

    init {
        if (menuItemId.isNotBlank()) loadMenuItem()
    }

    /**
     * Answers from the restaurant's own figures when it can.
     *
     * A dish whose menu entry already carries nutrition never reaches the model: no photo,
     * no network, no cost, and a better answer than a photograph could give. This is what
     * makes an owner filling in the admin menu worth their time.
     */
    private fun loadMenuItem() = viewModelScope.launch {
        val menu = restaurantRepository.menu(restaurantId)
        if (menu !is Result.Success) return@launch

        val item = menu.data.firstOrNull { it.id == menuItemId } ?: return@launch
        _state.update {
            it.copy(dishFromMenu = item.name, result = DishNutrition.fromMenuItem(item))
        }
    }

    fun scan(imageBase64: String) {
        _state.update {
            // The previous result goes as the new scan starts. Leaving it on screen under a
            // spinner invites reading the old dish's numbers as the new dish's.
            it.copy(isScanning = true, errorMessage = null, result = null, portionFactor = 1.0)
        }
        viewModelScope.launch {
            when (val result = scannerRepository.scan(imageBase64)) {
                is Result.Success ->
                    _state.update { it.copy(isScanning = false, result = result.data) }

                is Result.Failure ->
                    _state.update { it.copy(isScanning = false, errorMessage = result.message) }
            }
        }
    }

    /** Reports that the photograph could not be read, without going near the network. */
    fun reportUnreadableImage() = _state.update {
        it.copy(
            isScanning = false,
            errorMessage = "We could not open that photo. Try taking it again.",
        )
    }

    fun setPortion(factor: Double) = _state.update { it.copy(portionFactor = factor) }

    fun dismissError() = _state.update { it.copy(errorMessage = null) }

    fun clear() = _state.update {
        // The dish this was opened for survives, so "scan another" from a menu item still
        // knows which dish the diner was asking about.
        ScannerUiState(
            isAvailable = scannerRepository.isAvailable,
            dishFromMenu = it.dishFromMenu,
        )
    }

    companion object {
        const val ARG_RESTAURANT_ID = "restaurantId"
        const val ARG_MENU_ITEM_ID = "menuItemId"
    }
}
