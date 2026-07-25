package com.futo.platformplayer

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.futo.platformplayer.logging.Logger

/**
 * Simple dialog utilities for showing alerts and toasts.
 * Replacement for the deleted UIDialogs.kt which relied on XML dialogs.
 */
object UIDialogs {
    private const val TAG = "UIDialogs"

    data class Action(
        val title: String,
        val onClick: () -> Unit,
        val style: ActionStyle = ActionStyle.NONE
    )

    enum class ActionStyle {
        NONE, PRIMARY, NEGATIVE
    }

    data class Descriptor(
        val icon: Int,
        val title: String,
        val subtitle: String?,
        val details: String?,
        val actionCount: Int,
        val actions: List<Action> = emptyList()
    ) {
        fun withCondition(condition: Boolean): Descriptor? = if (condition) this else null
    }

    /**
     * Show a simple toast message.
     */
    fun toast(context: Context?, message: String?, duration: Int = Toast.LENGTH_SHORT) {
        if (context == null || message.isNullOrEmpty()) return
        try {
            Toast.makeText(context, message, duration).show()
        } catch (e: Throwable) {
            Logger.e(TAG, "Failed to show toast", e)
        }
    }

    /**
     * Show a toast message using the app context from StateApp.
     */
    fun toast(message: String?, duration: Int = Toast.LENGTH_SHORT) {
        // Stub: requires context to be passed explicitly now
        // Callers should use toast(context, message, duration) instead
    }

    /**
     * Show an app-level toast (stays visible longer).
     */
    fun appToast(toast: Any, isLong: Boolean = false) {
        // Simplified: just show a regular toast with the message
        val message = when (toast) {
            is String -> toast
            else -> toast.toString()
        }
        // Stub: requires context to be passed explicitly now
    }

    /**
     * Show a simple OK dialog.
     */
    fun showDialogOk(context: Context, icon: Int, text: String, handler: (() -> Unit)? = null) {
        showDialog(context, icon, text, null, null, 0,
            Action("Ok", { handler?.invoke() }, ActionStyle.PRIMARY))
    }

    /**
     * Show a general error dialog.
     */
    fun showGeneralErrorDialog(context: Context, title: String, error: Throwable) {
        showDialog(context,
            android.R.drawable.ic_dialog_alert,
            title,
            error.message,
            null, 0,
            Action("Ok", {}, ActionStyle.PRIMARY))
    }

    /**
     * Show a confirmation dialog.
     */
    fun showConfirmationDialog(
        context: Context,
        title: String,
        onConfirm: () -> Unit,
        onCancel: () -> Unit
    ) {
        showDialog(context,
            android.R.drawable.ic_dialog_alert,
            title,
            null, null, 0,
            Action("Cancel", { onCancel() }),
            Action("Confirm", { onConfirm() }, ActionStyle.PRIMARY))
    }

    /**
     * Show a dialog with actions.
     */
    fun showDialog(
        context: Context,
        icon: Int,
        title: String,
        text: String?,
        details: String?,
        actionCount: Int,
        vararg actions: Action
    ) {
        try {
            val builder = AlertDialog.Builder(context)
            builder.setTitle(title)
            text?.let { builder.setMessage(it) }
            details?.let { builder.setNeutralButton("Details") { _, _ ->
                AlertDialog.Builder(context).setTitle("Details").setMessage(it).show()
            }}

            actions.forEachIndexed { index, action ->
                when (action.style) {
                    ActionStyle.PRIMARY -> {
                        builder.setNegativeButton(action.title) { _, _ -> action.onClick() }
                    }
                    ActionStyle.NEGATIVE -> {
                        builder.setNeutralButton(action.title) { _, _ -> action.onClick() }
                    }
                    ActionStyle.NONE -> {
                        builder.setPositiveButton(action.title) { _, _ -> action.onClick() }
                    }
                }
            }

            builder.create().show()
        } catch (e: Throwable) {
            Logger.e(TAG, "Failed to show dialog", e)
        }
    }

    /**
     * Show multiple dialogs sequentially.
     */
    fun multiShowDialog(
        context: Context,
        dialogDescriptor: List<Descriptor?>,
        finally: (() -> Unit)? = null
    ) {
        if (dialogDescriptor.isEmpty()) {
            finally?.invoke()
            return
        }

        val next = dialogDescriptor.filterNotNull().firstOrNull() ?: return
        showDialog(context, next.icon, next.title, next.subtitle, next.details, next.actionCount,
            *next.actions.toTypedArray())

        // After showing, continue with remaining dialogs
        if (dialogDescriptor.size > 1) {
            multiShowDialog(context, dialogDescriptor.drop(1), finally)
        } else {
            finally?.invoke()
        }
    }

    // Placeholder methods for dialogs that were deleted
    fun showAutomaticBackupDialog(context: Context) {
        toast(context, "Automatic backup configuration not yet migrated to Compose")
    }

    fun showAutomaticRestoreDialog(context: Context, scope: Any?) {
        toast(context, "Automatic restore not yet migrated to Compose")
    }

    fun showCastingDialog(context: Context) {
        toast(context, "Casting dialog not yet migrated to Compose")
    }

    fun showChangelogDialog(context: Context) {
        toast(context, "Changelog dialog not yet migrated to Compose")
    }

    fun showImportDialog(context: Context, store: Any, key: String, values: List<String>, cache: Any?, callback: () -> Unit) {
        toast(context, "Import dialog not yet migrated to Compose")
    }

    fun showMigrateDialog(context: Context, store: Any, callback: () -> Unit) {
        toast(context, "Migration dialog not yet migrated to Compose")
    }

    fun showPluginUpdateDialog(context: Context) {
        toast(context, "Plugin update dialog not yet migrated to Compose")
    }

    fun showUpdateAvailableDialog(context: Context, latestVersion: String, hideExceptionButtons: Boolean) {
        toast(context, "Update available: v$latestVersion")
    }

    fun showUrlHandlingPrompt(context: Context, url: String = "", callback: (String) -> Unit = {}) {
        toast(context, "URL handling prompt not yet migrated to Compose")
    }

    fun showProgressDialog(context: Context, handler: (Any) -> Unit) {
        toast(context, "Progress dialog not yet migrated to Compose")
    }
}
