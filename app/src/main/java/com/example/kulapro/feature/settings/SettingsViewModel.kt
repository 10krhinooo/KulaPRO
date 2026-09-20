package com.example.kulapro.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kulapro.data.settings.AppSettings
import com.example.kulapro.data.settings.SettingsRepository
import com.example.kulapro.data.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The settings screen, over preferences that are actually stored.
 *
 * Each setter writes straight through rather than holding a local copy, so the switch and
 * what the app does can never disagree: there is one value, and the screen reads it back.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = AppSettings(),
    )

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch {
        settingsRepository.setThemeMode(mode)
    }

    fun setDynamicColour(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setDynamicColour(enabled)
    }

    fun setRemindersEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setRemindersEnabled(enabled)
    }

    fun setReminderLeadHours(hours: Int) = viewModelScope.launch {
        settingsRepository.setReminderLeadHours(hours)
    }
}

/** Long enough to survive a rotation without restarting the flow. */
private const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
