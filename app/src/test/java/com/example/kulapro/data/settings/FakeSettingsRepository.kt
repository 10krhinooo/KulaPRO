package com.example.kulapro.data.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Settings a test can set directly, with no DataStore and no file on disk.
 *
 * Backed by a [MutableStateFlow] rather than a plain value, because anything collecting
 * [settings] has to see a write land. A fake that only returned the current value would let
 * a view model pass its tests while never updating on screen.
 */
class FakeSettingsRepository(
    initial: AppSettings = AppSettings(),
) : SettingsRepository {

    private val state = MutableStateFlow(initial)

    var current: AppSettings
        get() = state.value
        set(value) {
            state.value = value
        }

    override val settings: Flow<AppSettings> = state

    override suspend fun setThemeMode(mode: ThemeMode) {
        state.value = state.value.copy(themeMode = mode)
    }

    override suspend fun setDynamicColour(enabled: Boolean) {
        state.value = state.value.copy(useDynamicColour = enabled)
    }

    override suspend fun setRemindersEnabled(enabled: Boolean) {
        state.value = state.value.copy(remindersEnabled = enabled)
    }

    override suspend fun setReminderLeadHours(hours: Int) {
        state.value = state.value.copy(reminderLeadHours = hours)
    }
}
