package com.example.kulapro.data.settings

/**
 * The preferences a user can actually change, and what they mean.
 *
 * Every one of these does something. The settings screen previously held three switches in
 * local state that were discarded the moment it was closed, which is worse than having no
 * settings screen at all: a control that lies teaches people not to trust the rest.
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /**
     * Take colours from the wallpaper instead of the KulaPro palette.
     *
     * Off by default, so the brand survives on Android 12 and later rather than being
     * discarded in favour of whatever the user's home screen happens to be.
     */
    val useDynamicColour: Boolean = false,
    val remindersEnabled: Boolean = true,
    /** How long before a sitting the reminder fires. */
    val reminderLeadHours: Int = DEFAULT_REMINDER_LEAD_HOURS,
)

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    val label: String
        get() = when (this) {
            SYSTEM -> "Match the system"
            LIGHT -> "Always light"
            DARK -> "Always dark"
        }
}

/** Long enough to change plans, short enough that the booking is still on the user's mind. */
const val DEFAULT_REMINDER_LEAD_HOURS = 3
