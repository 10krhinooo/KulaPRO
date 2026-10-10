package com.example.kulapro.feature.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kulapro.data.repository.AdminRepository
import com.example.kulapro.data.repository.Result
import com.example.kulapro.util.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The platform's own numbers.
 *
 * Fetched once per window rather than listened to. These are figures somebody reads and
 * thinks about, not a scoreboard, and a live listener over every booking on the platform
 * would cost real money to tell the reader nothing they could not get by pulling to refresh.
 */
@HiltViewModel
class SystemMetricsViewModel @Inject constructor(
    private val adminRepository: AdminRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(SystemMetricsUiState(now = clock.now()))
    val state: StateFlow<SystemMetricsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /**
     * Looks over a different stretch of time.
     *
     * A shorter window is a subset of a longer one, but it is still re-fetched: reusing the
     * documents already in hand would silently answer "the last 7 days" with whatever was
     * loaded an hour ago, and being wrong quietly is worse than being slow.
     */
    fun selectWindow(window: MetricsWindow) {
        if (window == _state.value.window) return
        _state.update { it.copy(window = window) }
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = adminRepository.snapshot(_state.value.window.days)) {
                is Result.Success -> _state.update {
                    it.copy(isLoading = false, snapshot = result.data, now = clock.now())
                }

                is Result.Failure -> _state.update {
                    it.copy(isLoading = false, error = result.message)
                }
            }
        }
    }
}
