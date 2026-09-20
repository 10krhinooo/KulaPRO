package com.example.kulapro.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Preferences on disk.
 *
 * Takes the store rather than a [android.content.Context] so the file it writes to is the
 * caller's decision. The Context extension that DataStore ships caches a single store for
 * the whole process, which is right for the app and wrong for a test, where each case needs
 * its own file to start from nothing.
 */
class SettingsRepositoryDataStore(
    private val store: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = store.data
        .catch { error ->
            // A corrupt preferences file is not worth crashing over. Falling back to the
            // defaults loses a preference; rethrowing loses the app.
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { it.toAppSettings() }

    override suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[KEY_THEME_MODE] = mode.name }
    }

    override suspend fun setDynamicColour(enabled: Boolean) {
        store.edit { it[KEY_DYNAMIC_COLOUR] = enabled }
    }

    override suspend fun setRemindersEnabled(enabled: Boolean) {
        store.edit { it[KEY_REMINDERS] = enabled }
    }

    override suspend fun setReminderLeadHours(hours: Int) {
        store.edit { it[KEY_REMINDER_LEAD] = hours }
    }
}

/**
 * Reads stored preferences, falling back to the default for anything missing or unreadable.
 *
 * Kept separate and internal so the mapping can be tested without a DataStore, an Android
 * context or a file on disk.
 */
internal fun Preferences.toAppSettings(): AppSettings = AppSettings(
    themeMode = this[KEY_THEME_MODE]
        ?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
        ?: ThemeMode.SYSTEM,
    useDynamicColour = this[KEY_DYNAMIC_COLOUR] ?: false,
    remindersEnabled = this[KEY_REMINDERS] ?: true,
    reminderLeadHours = this[KEY_REMINDER_LEAD] ?: DEFAULT_REMINDER_LEAD_HOURS,
)

internal val KEY_THEME_MODE = stringPreferencesKey("themeMode")
internal val KEY_DYNAMIC_COLOUR = booleanPreferencesKey("useDynamicColour")
internal val KEY_REMINDERS = booleanPreferencesKey("remindersEnabled")
internal val KEY_REMINDER_LEAD = intPreferencesKey("reminderLeadHours")

/** One file, named once, so the app and anything inspecting it agree on where it is. */
const val SETTINGS_STORE_NAME = "settings"
