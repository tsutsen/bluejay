/*
 * Login Dialog
 *
 * Triggers navigation to the Compose LoginScreen for plugin authentication.
 */

package com.tsutsen.platformplayer.auth

import com.tsutsen.platformplayer.api.media.platforms.js.SourceAuth
import com.tsutsen.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.tsutsen.platformplayer.logging.Logger
import com.tsutsen.platformplayer.states.StateApp
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.tsutsen.platformplayer.core.navigation.Navigator

/**
 * Replacement for the deleted LoginFragment.showLogin().
 * Navigates to the Compose LoginScreen for plugin authentication.
 */
object LoginDialog {
    private const val TAG = "LoginDialog"

    /**
     * Shows the login screen for the given plugin config.
     * Navigates to LoginScreen which opens a WebView for authentication.
     */
    fun showLogin(config: SourcePluginConfig, callback: ((SourceAuth?) -> Unit)? = null) {
        val activity = StateApp.instance.activity
        if (activity == null) {
            Logger.w(TAG, "No activity available for login dialog")
            callback?.invoke(null)
            return
        }

        try {
            // Serialize config to JSON for navigation
            val configJson = Json.encodeToString(config)
            
            // Navigate to login screen
            val navigator = StateApp.instance.navigator
            navigator?.navigateToLogin(configJson)
            
            Logger.i(TAG, "Navigated to login screen for ${config.name}")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to prepare login for ${config.name}", e)
            callback?.invoke(null)
        }
    }

    /**
     * Stub for showing captcha dialog.
     * The original captcha dialog was an XML-based dialog.
     * It has been removed during the Compose migration.
     */
    fun showCaptcha(context: android.content.Context, config: SourcePluginConfig, url: String, html: String, callback: (com.tsutsen.platformplayer.api.media.platforms.js.SourceCaptchaData?) -> Unit) {
        // No-op stub - captcha not yet migrated to Compose
        callback(null)
    }
}
