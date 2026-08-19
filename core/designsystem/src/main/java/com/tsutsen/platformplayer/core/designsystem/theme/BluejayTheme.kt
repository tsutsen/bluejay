package com.tsutsen.platformplayer.core.designsystem.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DefaultColorScheme = lightColorScheme()
private val DarkColorSchemeCustom = darkColorScheme()

/**
 * Semantic colors (warning etc.) for the current effective theme.
 * Danger and highlight use the M3 colorScheme roles directly; this
 * Local only carries what M3 has no role for.
 */
val LocalSemanticColors = compositionLocalOf<SemanticColors> { SemanticColorsLight }

@Composable
fun BluejayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    colorScheme: ColorScheme =
        if (dynamicColor) {
            if (darkTheme) {
                dynamicDarkColorScheme(LocalView.current.context)
            } else {
                dynamicLightColorScheme(LocalView.current.context)
            }
        } else {
            if (darkTheme) DarkColorSchemeCustom else DefaultColorScheme
        },
    typography: Typography = BluejayTypography,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // The context is not an Activity when the UI is hosted in a
            // Presentation (second screen) — skip window chrome there.
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = {
            CompositionLocalProvider(
                LocalSemanticColors provides
                    if (darkTheme) SemanticColorsDark else SemanticColorsLight,
            ) {
                // Surface (not Box) so LocalContentColor resolves to onSurface —
                // bare Text/Icons would otherwise default to hardcoded Color.Black.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = colorScheme.surface,
                    contentColor = colorScheme.onSurface,
                ) {
                    content()
                }
            }
        },
    )
}
