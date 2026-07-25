package com.tsutsen.platformplayer.auth

import android.content.Context
import com.tsutsen.platformplayer.UIDialogs
import com.tsutsen.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.tsutsen.platformplayer.api.media.platforms.js.SourcePluginAuthConfig
import com.tsutsen.platformplayer.logging.Logger
import com.tsutsen.platformplayer.states.StateApp

/**
 * Replacement for the deleted LoginFragment.showLogin().
 * Shows a simple UIDialogs prompt for login credentials.
 * TODO: Replace with a proper Compose login screen.
 */
object LoginDialog {
    private const val TAG = "LoginDialog"

    /**
     * Shows a login dialog for the given plugin config.
     * This is a simplified replacement for LoginFragment.showLogin().
     */
    fun showLogin(config: SourcePluginConfig, callback: ((com.tsutsen.platformplayer.api.media.platforms.js.SourceAuth?) -> Unit)? = null) {
        val activity = StateApp.instance.activity
        if (activity == null) {
            Logger.w(TAG, "No activity available for login dialog")
            callback?.invoke(null)
            return
        }

        // Show a simple dialog asking the user to log in via the plugin's auth URL
        val authUrl = config.authentication?.loginUrl ?: config.sourceUrl ?: "No auth URL configured"
        UIDialogs.showDialog(activity,
            com.tsutsen.platformplayer.R.drawable.ic_login,
            "Login Required",
            "Please log in to ${config.name}\nURL: $authUrl",
            null, 0,
            UIDialogs.Action("Cancel", { callback?.invoke(null) }),
            UIDialogs.Action("OK", {
                // TODO: Implement actual login flow
                Logger.w(TAG, "Login not yet implemented - placeholder OK clicked")
                callback?.invoke(null)
            })
        )
    }

    /**
     * Stub for showing captcha dialog.
     * The original captcha dialog was an XML-based dialog.
     * It has been removed during the Compose migration.
     */
    fun showCaptcha(context: Context, config: SourcePluginConfig, url: String, html: String, callback: (com.tsutsen.platformplayer.api.media.platforms.js.SourceCaptchaData?) -> Unit) {
        // No-op stub - captcha not yet migrated to Compose
        callback(null)
    }
}
