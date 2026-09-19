package com.example.kulapro.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = HerbGreen,
    onPrimary = OnHerbGreen,
    primaryContainer = HerbGreenContainer,
    onPrimaryContainer = OnHerbGreenContainer,
    secondary = Amber,
    onSecondary = OnAmber,
    secondaryContainer = AmberContainer,
    onSecondaryContainer = OnAmberContainer,
    tertiary = Terracotta,
    onTertiary = OnTerracotta,
    tertiaryContainer = TerracottaContainer,
    onTertiaryContainer = OnTerracottaContainer,
    error = ErrorRed,
    onError = OnErrorRed,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    background = WarmWhite,
    onBackground = OnWarmWhite,
    surface = WarmWhite,
    onSurface = OnWarmWhite,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    inverseSurface = InverseSurfaceLight,
    inverseOnSurface = InverseOnSurfaceLight,
    inversePrimary = InversePrimaryLight,
)

private val DarkColors = darkColorScheme(
    primary = HerbGreenDark,
    onPrimary = OnHerbGreenDark,
    primaryContainer = HerbGreenContainerDark,
    onPrimaryContainer = OnHerbGreenContainerDark,
    secondary = AmberDark,
    onSecondary = OnAmberDark,
    secondaryContainer = AmberContainerDark,
    onSecondaryContainer = OnAmberContainerDark,
    tertiary = TerracottaDark,
    onTertiary = OnTerracottaDark,
    tertiaryContainer = TerracottaContainerDark,
    onTertiaryContainer = OnTerracottaContainerDark,
    error = ErrorRedDark,
    onError = OnErrorRedDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    background = CharcoalGreen,
    onBackground = OnCharcoalGreen,
    surface = CharcoalGreen,
    onSurface = OnCharcoalGreen,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    inverseSurface = InverseSurfaceDark,
    inverseOnSurface = InverseOnSurfaceDark,
    inversePrimary = InversePrimaryDark,
)

@Composable
fun KulaProTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Off by default so the brand survives on Android 12 and later. The template had this on,
    // which meant the palette below was discarded in favour of the user's wallpaper colours.
    // Settings exposes it as an opt-in.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    CompositionLocalProvider(LocalReduceMotion provides rememberSystemReduceMotion()) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = KulaProTypography,
            shapes = KulaProShapes,
            content = content,
        )
    }
}
