package com.futo.platformplayer.theming

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Typeface
import android.util.TypedValue
import androidx.annotation.FontRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.futo.platformplayer.R

/**
 * Manages theme application across the app.
 * Handles font switching, icon style, and contrast level.
 * Applied in Activity.onCreate() before setContentView().
 */
object ThemeManager {

    /**
     * Apply appearance preferences to an activity's context.
     * Must be called before setContentView().
     */
    fun applyToContext(context: Context, preferences: AppearancePreferences) {
        val res = context.resources
        val config = res.configuration

        // Apply font
        val fontRes = getFontResource(preferences.fontChoice)
        if (fontRes != 0) {
            val typeface = ResourcesCompat.getFont(context, fontRes)
            if (typeface != null) {
                config.fontFamily = typeface
            }
        }

        // Apply contrast level via ui mode flags
        // High contrast can be applied via system settings or custom theme overlay
        when (preferences.contrastLevel) {
            ContrastLevel.HIGH -> {
                // High contrast: use a higher contrast theme overlay
                // This can be implemented by applying a themed context
            }
            ContrastLevel.MEDIUM -> {
                // Medium contrast: subtle adjustments
            }
            ContrastLevel.STANDARD -> {
                // Standard: no changes needed
            }
        }
    }

    /**
     * Apply appearance preferences to an Activity.
     * This is the main entry point called from Activity.onCreate().
     */
    fun apply(activity: Activity, preferences: AppearancePreferences) {
        // Apply font to the activity's context before setting content view
        applyToContext(activity, preferences)
    }

    /**
     * Get the font resource ID for the given font choice.
     */
    @FontRes
    fun getFontResource(fontChoice: FontChoice): Int {
        return when (fontChoice) {
            FontChoice.INTER -> R.font.inter_regular
            FontChoice.SYSTEM -> 0 // Use system default
        }
    }

    /**
     * Get the Material Symbols icon style name for the given icon style.
     * Used for setting the font weight/variation settings on Material Icons.
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
     * Material Symbols use weight to control visual appearance.
     */
    fun getIconFontWeight(iconStyle: IconStyle): Int {
        return when (iconStyle) {
            IconStyle.ROUNDED -> 400 // Regular weight for rounded
            IconStyle.SHARP -> 400
            IconStyle.OUTLINED -> 400
        }
    }
}
