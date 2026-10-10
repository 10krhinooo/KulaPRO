package com.example.kulapro.data.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Test

class AppSettingsMappingTest {

    @Test
    fun `falls back to sensible defaults when nothing is stored`() {
        val settings = mutablePreferencesOf().toAppSettings()

        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
        // Off, so the brand survives on Android 12 and later.
        assertEquals(false, settings.useDynamicColour)
        assertEquals(true, settings.remindersEnabled)
        assertEquals(DEFAULT_REMINDER_LEAD_HOURS, settings.reminderLeadHours)
    }

    @Test
    fun `reads back everything that was stored`() {
        val stored = mutablePreferencesOf(
            KEY_THEME_MODE to ThemeMode.DARK.name,
            KEY_DYNAMIC_COLOUR to true,
            KEY_REMINDERS to false,
            KEY_REMINDER_LEAD to 24,
        )

        val settings = stored.toAppSettings()

        assertEquals(ThemeMode.DARK, settings.themeMode)
        assertEquals(true, settings.useDynamicColour)
        assertEquals(false, settings.remindersEnabled)
        assertEquals(24, settings.reminderLeadHours)
    }

    @Test
    fun `ignores a theme mode it does not recognise`() {
        // A value written by an older or newer build must not leave the app themeless.
        val stored = mutablePreferencesOf(KEY_THEME_MODE to "SEPIA")

        assertEquals(ThemeMode.SYSTEM, stored.toAppSettings().themeMode)
    }

    @Test
    fun `keeps stored values independent of each other`() {
        val stored = mutablePreferencesOf(KEY_REMINDERS to false)

        val settings = stored.toAppSettings()

        assertEquals(false, settings.remindersEnabled)
        // Turning reminders off must not quietly reset the theme.
        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
    }
}
