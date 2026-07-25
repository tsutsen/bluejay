package com.tsutsen.platformplayer.compose.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.appcompat.app.AppCompatDelegate
import com.tsutsen.platformplayer.theming.AppearancePreferences
import com.tsutsen.platformplayer.theming.AppearancePreferencesManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Theme state holder for Jetpack Compose screens.
 *
 * Bridges from the existing AppearancePreferencesManager/DataStore system to Compose's
 * observable state, enabling live theme recomposition without Activity.recreate().
 *
 * This is the mechanism that solves the "live theming is broken" problem:
 * - Theme mode (light/dark/auto) changes propagate via recomposition
 * - Color scheme, contrast level, and other tokens are exposed as State<T>
 * - No Activity recreation needed — Compose handles the rest
 */

// ThemeMode enum values match AppearancePreferences
enum class ComposeThemeMode(val label: String) {
    AUTO("Auto"), LIGHT("Light"), DARK("Dark")
}

enum class ComposeColorSchemeMode(val label: String) {
    DYNAMIC("Dynamic"), CUSTOM_SEED("Custom"), PRESET("Preset")
}

enum class ComposeContrastLevel(val label: String) {
    STANDARD("Standard"), MEDIUM("Medium"), HIGH("High")
}

enum class ComposeFontChoice(val label: String) {
    INTER("Inter"), SYSTEM("System")
}

enum class ComposeIconStyle(val label: String) {
    ROUNDED("Rounded"), SHARP("Sharp"), OUTLINED("Outlined")
}

// Extension functions to convert Compose enums to legacy enums
fun ComposeThemeMode.toLegacyThemeMode(): com.tsutsen.platformplayer.theming.ThemeMode {
    return when (this) {
        ComposeThemeMode.AUTO -> com.tsutsen.platformplayer.theming.ThemeMode.AUTO
        ComposeThemeMode.LIGHT -> com.tsutsen.platformplayer.theming.ThemeMode.LIGHT
        ComposeThemeMode.DARK -> com.tsutsen.platformplayer.theming.ThemeMode.DARK
    }
}

fun ComposeColorSchemeMode.toLegacyColorSchemeMode(): com.tsutsen.platformplayer.theming.ColorSchemeMode {
    return when (this) {
        ComposeColorSchemeMode.DYNAMIC -> com.tsutsen.platformplayer.theming.ColorSchemeMode.DYNAMIC
        ComposeColorSchemeMode.CUSTOM_SEED -> com.tsutsen.platformplayer.theming.ColorSchemeMode.CUSTOM_SEED
        ComposeColorSchemeMode.PRESET -> com.tsutsen.platformplayer.theming.ColorSchemeMode.PRESET
    }
}

fun ComposeContrastLevel.toLegacyContrastLevel(): com.tsutsen.platformplayer.theming.ContrastLevel {
    return when (this) {
        ComposeContrastLevel.STANDARD -> com.tsutsen.platformplayer.theming.ContrastLevel.STANDARD
        ComposeContrastLevel.MEDIUM -> com.tsutsen.platformplayer.theming.ContrastLevel.MEDIUM
        ComposeContrastLevel.HIGH -> com.tsutsen.platformplayer.theming.ContrastLevel.HIGH
    }
}

/**
 * Theme state data class — the single source of truth for Compose theme state.
 */
data class ComposeThemeState(
    val themeMode: ComposeThemeMode = ComposeThemeMode.AUTO,
    val colorSchemeMode: ComposeColorSchemeMode = ComposeColorSchemeMode.DYNAMIC,
    val contrastLevel: ComposeContrastLevel = ComposeContrastLevel.STANDARD,
    val fontChoice: ComposeFontChoice = ComposeFontChoice.INTER,
    val iconStyle: ComposeIconStyle = ComposeIconStyle.ROUNDED
) {
    /**
     * Convert to the equivalent AppearancePreferences.ThemeMode for the legacy ThemeManager.
     */
    fun toLegacyThemeMode(): com.tsutsen.platformplayer.theming.ThemeMode {
        return themeMode.toLegacyThemeMode()
    }

    /**
     * Convert to the equivalent AppearancePreferences.ColorSchemeMode.
     */
    fun toLegacyColorSchemeMode(): com.tsutsen.platformplayer.theming.ColorSchemeMode {
        return colorSchemeMode.toLegacyColorSchemeMode()
    }

    /**
     * Convert to the equivalent AppearancePreferences.ContrastLevel.
     */
    fun toLegacyContrastLevel(): com.tsutsen.platformplayer.theming.ContrastLevel {
        return contrastLevel.toLegacyContrastLevel()
    }
}

/**
 * Read the current theme state from the existing AppearancePreferences DataStore.
 * This is the bridge from the existing persistence layer to Compose.
 * Uses AppearancePreferencesManager to avoid creating a second DataStore instance.
 */
fun Context.themeStateFlow(): Flow<ComposeThemeState> {
    return AppearancePreferencesManager(this).preferences.map { prefs ->
        ComposeThemeState(
            themeMode = when (prefs.themeMode) {
                com.tsutsen.platformplayer.theming.ThemeMode.AUTO -> ComposeThemeMode.AUTO
                com.tsutsen.platformplayer.theming.ThemeMode.LIGHT -> ComposeThemeMode.LIGHT
                com.tsutsen.platformplayer.theming.ThemeMode.DARK -> ComposeThemeMode.DARK
            },
            colorSchemeMode = when (prefs.colorSchemeMode) {
                com.tsutsen.platformplayer.theming.ColorSchemeMode.DYNAMIC -> ComposeColorSchemeMode.DYNAMIC
                com.tsutsen.platformplayer.theming.ColorSchemeMode.CUSTOM_SEED -> ComposeColorSchemeMode.CUSTOM_SEED
                com.tsutsen.platformplayer.theming.ColorSchemeMode.PRESET -> ComposeColorSchemeMode.PRESET
            },
            contrastLevel = when (prefs.contrastLevel) {
                com.tsutsen.platformplayer.theming.ContrastLevel.STANDARD -> ComposeContrastLevel.STANDARD
                com.tsutsen.platformplayer.theming.ContrastLevel.MEDIUM -> ComposeContrastLevel.MEDIUM
                com.tsutsen.platformplayer.theming.ContrastLevel.HIGH -> ComposeContrastLevel.HIGH
            },
            fontChoice = when (prefs.fontChoice) {
                com.tsutsen.platformplayer.theming.FontChoice.INTER -> ComposeFontChoice.INTER
                com.tsutsen.platformplayer.theming.FontChoice.SYSTEM -> ComposeFontChoice.SYSTEM
            },
            iconStyle = when (prefs.iconStyle) {
                com.tsutsen.platformplayer.theming.IconStyle.ROUNDED -> ComposeIconStyle.ROUNDED
                com.tsutsen.platformplayer.theming.IconStyle.SHARP -> ComposeIconStyle.SHARP
                com.tsutsen.platformplayer.theming.IconStyle.OUTLINED -> ComposeIconStyle.OUTLINED
            }
        )
    }
}

/**
 * Composable that reads theme state from DataStore and exposes it as a State<T>.
 * This is the primary way Compose screens access theme state.
 *
 * Usage:
 *   val themeState by rememberComposeThemeState()
 *   val colors = getGrayjayColors(themeState.themeMode)
 */
@Composable
fun rememberComposeThemeState(): State<ComposeThemeState> {
    val context = LocalContext.current
    return produceState(
        initialValue = ComposeThemeState(),
        key1 = context
    ) {
        snapshotFlow { context }
            .collect { ctx ->
                ctx.themeStateFlow().collect { state ->
                    value = state
                }
            }
    }
}

/**
 * Helper to apply theme mode changes to the legacy AppCompat delegate.
 * This bridges Compose state changes back to the Activity-level theme system
 * so that XML-based screens also react to theme changes.
 * Also persists the setting to the existing AppearancePreferences DataStore
 * so it survives app restarts.
 */
fun applyThemeModeToLegacy(context: Context, mode: ComposeThemeMode) {
    val nightMode = when (mode) {
        ComposeThemeMode.AUTO -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        ComposeThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        ComposeThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
    }
    AppCompatDelegate.setDefaultNightMode(nightMode)
    // Persist to the existing AppearancePreferences DataStore
    val prefMode = when (mode) {
        ComposeThemeMode.AUTO -> com.tsutsen.platformplayer.theming.ThemeMode.AUTO
        ComposeThemeMode.LIGHT -> com.tsutsen.platformplayer.theming.ThemeMode.LIGHT
        ComposeThemeMode.DARK -> com.tsutsen.platformplayer.theming.ThemeMode.DARK
    }
    // Use runBlocking since this is called from non-suspend contexts (clickable lambdas)
    kotlinx.coroutines.runBlocking {
        com.tsutsen.platformplayer.theming.AppearancePreferencesManager(context).setThemeMode(prefMode)
    }
    // Recreate the Activity to apply theme to XML screens
    if (context is android.app.Activity) {
        context.recreate()
    }
}
