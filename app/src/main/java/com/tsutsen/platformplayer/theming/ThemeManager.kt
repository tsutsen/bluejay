package com.tsutsen.platformplayer.theming

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import androidx.annotation.FontRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.res.ResourcesCompat
import com.tsutsen.platformplayer.R

/**
 * Manages theme application across the app.
 * Handles font switching, icon style, contrast level, and theme mode (light/dark/auto).
 * Applied in Activity.onCreate() before setContentView().
 */
object ThemeManager {

    /**
     * Apply theme mode (light/dark/auto) based on user preference.
     * This should be called early, before any UI is drawn.
     */
    fun applyThemeMode(themeMode: ThemeMode) {
        when (themeMode) {
            ThemeMode.AUTO -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
            ThemeMode.LIGHT -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
            ThemeMode.DARK -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
        }
    }

    /**
     * Apply appearance preferences to a Context.
     * Must be called before setContentView().
     * Returns a context with modified configuration.
     */
    fun applyToContext(context: Context, preferences: AppearancePreferences): Context {
        val res = context.resources
        val config = Configuration(res.configuration)

        // Apply font override via configuration
        if (preferences.fontChoice == FontChoice.INTER) {
            val typeface = ResourcesCompat.getFont(context, R.font.inter_medium)
            if (typeface != null) {
                // Inter medium (weight 500) is applied via theme fontFamily attribute
            }
        }

        // Apply contrast level
        when (preferences.contrastLevel) {
            ContrastLevel.HIGH -> {
                // High contrast: could apply a themed context overlay
            }
            ContrastLevel.MEDIUM -> {
                // Medium contrast adjustments
            }
            ContrastLevel.STANDARD -> {
                // Standard: no changes needed
            }
        }

        return context.createConfigurationContext(config)
    }

    /**
     * Apply appearance preferences to an Activity.
     * This is the main entry point called from Activity.onCreate().
     */
    fun apply(activity: Activity, preferences: AppearancePreferences) {
        // Apply theme mode first (light/dark/auto)
        applyThemeMode(preferences.themeMode)
        // Then apply context-level preferences
        applyToContext(activity, preferences)
    }

    /**
     * Get the font resource ID for the given font choice.
     */
    @FontRes
    fun getFontResource(fontChoice: FontChoice): Int {
        return when (fontChoice) {
            FontChoice.INTER -> R.font.inter_medium
            FontChoice.SYSTEM -> 0 // Use system default
        }
    }

    /**
     * Get the Material Symbols icon style name for the given icon style.
     */
    fun getIconStyleName(iconStyle: IconStyle): String {
        return when (iconStyle) {
            IconStyle.ROUNDED -> "Rounded"
            IconStyle.SHARP -> "Sharp"
            IconStyle.OUTLINED -> "Outlined"
        }
    }

    /**
     * Get the font weight for the given icon style.
     */
    fun getIconFontWeight(iconStyle: IconStyle): Int {
        return when (iconStyle) {
            IconStyle.ROUNDED -> 400
            IconStyle.SHARP -> 400
            IconStyle.OUTLINED -> 400
        }
    }
}
