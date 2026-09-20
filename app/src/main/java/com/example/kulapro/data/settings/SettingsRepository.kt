package com.example.kulapro.data.settings

import kotlinx.coroutines.flow.Flow

/**
 * Where preferences live between launches.
 *
 * A flow rather than a read, because the theme has to change the moment the switch does,
 * not the next time the app starts.
 */
interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setDynamicColour(enabled: Boolean)

    suspend fun setRemindersEnabled(enabled: Boolean)

    suspend fun setReminderLeadHours(hours: Int)
}
