package com.tsutsen.platformplayer.theming

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Preferences store for appearance-related settings.
 * Uses DataStore for type-safe, async preference management.
 */
private val Context.dataStore: DataStore<androidx.datastore.preferences.core.Preferences> by preferencesDataStore(name = "appearance")

/**
 * Color scheme mode for Material You theming.
 */
enum class ColorSchemeMode {
    /** Dynamic colors from wallpaper (Material You) */
    DYNAMIC,
    /** Custom seed color */
    CUSTOM_SEED,
    /** Predefined preset colors */
    PRESET
}

/**
 * Font choice for the app.
 */
enum class FontChoice {
    /** Inter (custom bundled font) */
    INTER,
    /** System default font (Roboto) */
    SYSTEM
}

/**
 * Icon style for Material Symbols.
 */
enum class IconStyle {
    /** Rounded (default) */
    ROUNDED,
    /** Sharp edges */
    SHARP,
    /** Outlined style */
    OUTLINED
}

/**
 * Contrast level for accessibility.
 */
enum class ContrastLevel {
    /** Standard contrast */
    STANDARD,
    /** Medium contrast (enhanced) */
    MEDIUM,
    /** High contrast (accessibility) */
    HIGH
}

/**
 * Theme mode for light/dark switching.
 */
enum class ThemeMode {
    /** Follow system setting */
    AUTO,
    /** Force light theme */
    LIGHT,
    /** Force dark theme */
    DARK
}

/**
 * Data class holding all appearance preferences.
 */
data class AppearancePreferences(
    val colorSchemeMode: ColorSchemeMode = ColorSchemeMode.DYNAMIC,
    val customSeedColor: Long = 0xFF6750A4L, // Default purple
    val presetColorName: String? = null,
    val fontChoice: FontChoice = FontChoice.INTER,
    val iconStyle: IconStyle = IconStyle.ROUNDED,
    val contrastLevel: ContrastLevel = ContrastLevel.STANDARD,
    val themeMode: ThemeMode = ThemeMode.AUTO
)

/**
 * DataStore-backed appearance preferences manager.
 */
class AppearancePreferencesManager(private val context: Context) {

    companion object {
        private val COLOR_SCHEME_MODE = stringPreferencesKey("color_scheme_mode")
        private val CUSTOM_SEED_COLOR = longPreferencesKey("custom_seed_color")
        private val PRESET_COLOR_NAME = stringPreferencesKey("preset_color_name")
        private val FONT_CHOICE = stringPreferencesKey("font_choice")
        private val ICON_STYLE = stringPreferencesKey("icon_style")
        private val CONTRAST_LEVEL = stringPreferencesKey("contrast_level")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    /** Flow of current appearance preferences. */
    val preferences: Flow<AppearancePreferences> = context.dataStore.data
        .map { prefs ->
            AppearancePreferences(
                colorSchemeMode = parseColorSchemeMode(prefs[COLOR_SCHEME_MODE]),
                customSeedColor = prefs[CUSTOM_SEED_COLOR] ?: 0xFF6750A4L,
                presetColorName = prefs[PRESET_COLOR_NAME],
                fontChoice = parseFontChoice(prefs[FONT_CHOICE]),
                iconStyle = parseIconStyle(prefs[ICON_STYLE]),
                contrastLevel = parseContrastLevel(prefs[CONTRAST_LEVEL]),
                themeMode = parseThemeMode(prefs[THEME_MODE])
            )
        }

    /** Update a specific preference. */
    suspend fun setColorSchemeMode(mode: ColorSchemeMode) {
        withContext(Dispatchers.IO) {
            context.dataStore.edit { prefs ->
                prefs[COLOR_SCHEME_MODE] = mode.name
            }
        }
    }

    suspend fun setCustomSeedColor(color: Long) {
        withContext(Dispatchers.IO) {
            context.dataStore.edit { prefs ->
                prefs[CUSTOM_SEED_COLOR] = color
            }
        }
    }

    suspend fun setPresetColorName(name: String?) {
        withContext(Dispatchers.IO) {
            context.dataStore.edit { prefs ->
                if (name == null) {
                    prefs.remove(PRESET_COLOR_NAME)
                } else {
                    prefs[PRESET_COLOR_NAME] = name
                }
            }
        }
    }

    suspend fun setFontChoice(font: FontChoice) {
        withContext(Dispatchers.IO) {
            context.dataStore.edit { prefs ->
                prefs[FONT_CHOICE] = font.name
            }
        }
    }

    suspend fun setIconStyle(style: IconStyle) {
        withContext(Dispatchers.IO) {
            context.dataStore.edit { prefs ->
                prefs[ICON_STYLE] = style.name
            }
        }
    }

    suspend fun setContrastLevel(level: ContrastLevel) {
        withContext(Dispatchers.IO) {
            context.dataStore.edit { prefs ->
                prefs[CONTRAST_LEVEL] = level.name
            }
        }
    }

    private fun parseColorSchemeMode(value: String?): ColorSchemeMode {
        return try { ColorSchemeMode.valueOf(value ?: ColorSchemeMode.DYNAMIC.name) }
        catch (e: IllegalArgumentException) { ColorSchemeMode.DYNAMIC }
    }

    private fun parseFontChoice(value: String?): FontChoice {
        return try { FontChoice.valueOf(value ?: FontChoice.INTER.name) }
        catch (e: IllegalArgumentException) { FontChoice.INTER }
    }

    private fun parseIconStyle(value: String?): IconStyle {
        return try { IconStyle.valueOf(value ?: IconStyle.ROUNDED.name) }
        catch (e: IllegalArgumentException) { IconStyle.ROUNDED }
    }

    private fun parseContrastLevel(value: String?): ContrastLevel {
        return try { ContrastLevel.valueOf(value ?: ContrastLevel.STANDARD.name) }
        catch (e: IllegalArgumentException) { ContrastLevel.STANDARD }
    }

    private fun parseThemeMode(value: String?): ThemeMode {
        return try { ThemeMode.valueOf(value ?: ThemeMode.AUTO.name) }
        catch (e: IllegalArgumentException) { ThemeMode.AUTO }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        android.util.Log.d("GrayjayTheme", "setThemeMode called: $mode")
        withContext(Dispatchers.IO) {
            context.dataStore.edit { prefs ->
                prefs[THEME_MODE] = mode.name
                android.util.Log.d("GrayjayTheme", "Saved theme_mode=${mode.name} to DataStore")
            }
        }
    }
}
