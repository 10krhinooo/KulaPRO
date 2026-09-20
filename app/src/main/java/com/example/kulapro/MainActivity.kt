package com.example.kulapro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kulapro.data.settings.AppSettings
import com.example.kulapro.data.settings.SettingsRepository
import com.example.kulapro.data.settings.ThemeMode
import com.example.kulapro.ui.theme.KulaProTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * Injected here rather than read in a view model, because the theme wraps everything
     * and has to be decided before any screen composes.
     */
    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by rememberSettings(settingsRepository.settings)

            KulaProTheme(
                darkTheme = settings.themeMode.isDark(isSystemInDarkTheme()),
                dynamicColor = settings.useDynamicColour,
            ) {
                KulaProNavigation(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun rememberSettings(
    flow: Flow<AppSettings>,
): androidx.compose.runtime.State<AppSettings> =
    flow.collectAsStateWithLifecycle(initialValue = AppSettings())

/** Whether this choice means dark right now, which only SYSTEM needs to ask about. */
private fun ThemeMode.isDark(systemIsDark: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemIsDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
