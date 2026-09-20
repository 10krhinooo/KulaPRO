package com.example.kulapro.feature.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kulapro.data.repository.Result
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
) : ViewModel() {

    private val _state = MutableStateFlow(
        ScannerUiState(isAvailable = scannerRepository.isAvailable),
    )
    val state: StateFlow<ScannerUiState> = _state.asStateFlow()

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
        ScannerUiState(isAvailable = scannerRepository.isAvailable)
    }
}
