package com.example.kulapro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.kulapro.data.settings.AppSettings
import com.example.kulapro.data.settings.SettingsRepository
import com.example.kulapro.data.settings.ThemeMode
import com.example.kulapro.ui.theme.KulaProTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * Injected here rather than read in a view model, because the theme wraps everything
     * and has to be decided before any screen composes.
     */
    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super.onCreate, which is where the platform expects the splash to be
        // claimed. The app used to open on a blank white window for as long as the process
        // took to start, which reads as a hang rather than as a launch.
        val splash = installSplashScreen()
        var themeIsKnown = false
        splash.setKeepOnScreenCondition { !themeIsKnown }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Held for one read of the preferences, no longer. Drawing before it lands would
        // show the light theme for a frame and then swap, which is a worse first impression
        // than the extra few milliseconds.
        lifecycleScope.launch {
            settingsRepository.settings.first()
            themeIsKnown = true
        }

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

@Composable
private fun rememberSettings(flow: Flow<AppSettings>): State<AppSettings> =
    flow.collectAsStateWithLifecycle(initialValue = AppSettings())

/** Whether this choice means dark right now, which only SYSTEM needs to ask about. */
private fun ThemeMode.isDark(systemIsDark: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemIsDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
