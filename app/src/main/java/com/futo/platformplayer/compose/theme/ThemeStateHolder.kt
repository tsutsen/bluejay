package com.futo.platformplayer.compose.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.appcompat.app.AppCompatDelegate
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

// DataStore key for theme mode (mirrors AppearancePreferencesManager)
private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
private val COLOR_SCHEME_KEY = stringPreferencesKey("color_scheme_mode")
private val CONTRAST_LEVEL_KEY = stringPreferencesKey("contrast_level")
private val FONT_CHOICE_KEY = stringPreferencesKey("font_choice")
private val ICON_STYLE_KEY = stringPreferencesKey("icon_style")

// ThemeMode enum values match AppearancePreferences
enum class ComposeThemeMode {
    AUTO, LIGHT, DARK
}

enum class ComposeColorSchemeMode {
    DYNAMIC, CUSTOM_SEED, PRESET
}

enum class ComposeContrastLevel {
    STANDARD, MEDIUM, HIGH
}

enum class ComposeFontChoice {
    INTER, SYSTEM
}

enum class ComposeIconStyle {
    ROUNDED, SHARP, OUTLINED
}

// Extension functions to convert Compose enums to legacy enums
fun ComposeThemeMode.toLegacyThemeMode(): com.futo.platformplayer.theming.ThemeMode {
    return when (this) {
        ComposeThemeMode.AUTO -> com.futo.platformplayer.theming.ThemeMode.AUTO
        ComposeThemeMode.LIGHT -> com.futo.platformplayer.theming.ThemeMode.LIGHT
        ComposeThemeMode.DARK -> com.futo.platformplayer.theming.ThemeMode.DARK
    }
}

fun ComposeColorSchemeMode.toLegacyColorSchemeMode(): com.futo.platformplayer.theming.ColorSchemeMode {
    return when (this) {
        ComposeColorSchemeMode.DYNAMIC -> com.futo.platformplayer.theming.ColorSchemeMode.DYNAMIC
        ComposeColorSchemeMode.CUSTOM_SEED -> com.futo.platformplayer.theming.ColorSchemeMode.CUSTOM_SEED
        ComposeColorSchemeMode.PRESET -> com.futo.platformplayer.theming.ColorSchemeMode.PRESET
    }
}

fun ComposeContrastLevel.toLegacyContrastLevel(): com.futo.platformplayer.theming.ContrastLevel {
    return when (this) {
        ComposeContrastLevel.STANDARD -> com.futo.platformplayer.theming.ContrastLevel.STANDARD
        ComposeContrastLevel.MEDIUM -> com.futo.platformplayer.theming.ContrastLevel.MEDIUM
        ComposeContrastLevel.HIGH -> com.futo.platformplayer.theming.ContrastLevel.HIGH
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
    fun toLegacyThemeMode(): com.futo.platformplayer.theming.ThemeMode {
        return themeMode.toLegacyThemeMode()
    }

    /**
     * Convert to the equivalent AppearancePreferences.ColorSchemeMode.
     */
    fun toLegacyColorSchemeMode(): com.futo.platformplayer.theming.ColorSchemeMode {
        return colorSchemeMode.toLegacyColorSchemeMode()
    }

    /**
     * Convert to the equivalent AppearancePreferences.ContrastLevel.
     */
    fun toLegacyContrastLevel(): com.futo.platformplayer.theming.ContrastLevel {
        return contrastLevel.toLegacyContrastLevel()
    }
}

/**
 * Extension property to get a DataStore for appearance settings.
 */
private val Context.dataStore: DataStore<Preferences>
    by preferencesDataStore(name = "compose_theme")

/**
 * Read the current theme state from DataStore as a Flow.
 * This is the bridge from the existing persistence layer to Compose.
 */
fun Context.themeStateFlow(): Flow<ComposeThemeState> {
    return dataStore.data.map { prefs ->
        ComposeThemeState(
            themeMode = parseThemeMode(prefs[THEME_MODE_KEY]),
            colorSchemeMode = parseColorSchemeMode(prefs[COLOR_SCHEME_KEY]),
            contrastLevel = parseContrastLevel(prefs[CONTRAST_LEVEL_KEY]),
            fontChoice = parseFontChoice(prefs[FONT_CHOICE_KEY]),
            iconStyle = parseIconStyle(prefs[ICON_STYLE_KEY])
        )
    }
}

private fun parseThemeMode(value: String?): ComposeThemeMode {
    return try { ComposeThemeMode.valueOf(value ?: ComposeThemeMode.AUTO.name) }
    catch (e: IllegalArgumentException) { ComposeThemeMode.AUTO }
}

private fun parseColorSchemeMode(value: String?): ComposeColorSchemeMode {
    return try { ComposeColorSchemeMode.valueOf(value ?: ComposeColorSchemeMode.DYNAMIC.name) }
    catch (e: IllegalArgumentException) { ComposeColorSchemeMode.DYNAMIC }
}

private fun parseContrastLevel(value: String?): ComposeContrastLevel {
    return try { ComposeContrastLevel.valueOf(value ?: ComposeContrastLevel.STANDARD.name) }
    catch (e: IllegalArgumentException) { ComposeContrastLevel.STANDARD }
}

private fun parseFontChoice(value: String?): ComposeFontChoice {
    return try { ComposeFontChoice.valueOf(value ?: ComposeFontChoice.INTER.name) }
    catch (e: IllegalArgumentException) { ComposeFontChoice.INTER }
}

private fun parseIconStyle(value: String?): ComposeIconStyle {
    return try { ComposeIconStyle.valueOf(value ?: ComposeIconStyle.ROUNDED.name) }
    catch (e: IllegalArgumentException) { ComposeIconStyle.ROUNDED }
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
 */
fun applyThemeModeToLegacy(context: Context, mode: ComposeThemeMode) {
    when (mode) {
        ComposeThemeMode.AUTO -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        ComposeThemeMode.LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        ComposeThemeMode.DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
    }
}
