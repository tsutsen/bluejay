package com.tsutsen.platformplayer.dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ComposeViewContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTheme
import com.tsutsen.platformplayer.states.StateBackup
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Backup/restore dialogs. The app has no global Compose dialog host, so the
 * choice dialog is hosted in a plain [Dialog] window with a [ComposeView] —
 * the same hosting approach as CompanionPresentation, with the window's
 * missing ViewTree owners bound to the activity's.
 */
object BackupRestoreDialogs {

    /** Restore choices, one per archive section (replaces the old dialog chain). */
    data class ImportOptions(
        val importSettings: Boolean,
        val installPlugins: Boolean,
        val enablePlugins: Boolean,
        val importPluginSettings: Boolean,
        val importStores: Boolean,
    )

    /**
     * One dialog collecting all restore choices at once — replaces the legacy
     * chain of sequential per-section system AlertDialogs.
     */
    fun showImportOptionsDialog(
        context: Context,
        export: StateBackup.ExportStructure,
        unknownPluginUrls: List<String>,
        onImport: (ImportOptions) -> Unit,
        onCancel: () -> Unit
    ): Dialog {
        // One system dialog with every restore section as a checkbox — no
        // ComposeView in a bare Dialog window (that crashed with
        // ViewTreeSavedStateRegistryOwner).
        val labels = mutableListOf<String>()
        val keys = mutableListOf<String>()
        fun add(label: String, key: String, present: Boolean, default: Boolean) {
            if (present) { labels.add(label); keys.add(key) }
        }
        add("Settings", "settings", export.settings != null, true)
        add("Plugins (${unknownPluginUrls.size})", "plugins", unknownPluginUrls.isNotEmpty(), true)
        add("Enable all plugins", "enableAll", true, true)
        add("Plugin settings (${export.pluginSettings.size})", "pluginSettings", export.pluginSettings.isNotEmpty(), true)
        add("Stores (${export.stores.size})", "stores", export.stores.isNotEmpty(), true)

        val checked = BooleanArray(labels.size) { true }
        return AlertDialog.Builder(context)
            .setTitle("Import backup")
            .setMultiChoiceItems(labels.toTypedArray(), checked) { _, _, _ -> }
            .setPositiveButton("Import") { _, _ ->
                val opts = ImportOptions(
                    importSettings = "settings" in keys.filterIndexed { i, _ -> checked[i] },
                    installPlugins = "plugins" in keys.filterIndexed { i, _ -> checked[i] },
                    enablePlugins = "enableAll" in keys.filterIndexed { i, _ -> checked[i] },
                    importPluginSettings = "pluginSettings" in keys.filterIndexed { i, _ -> checked[i] },
                    importStores = "stores" in keys.filterIndexed { i, _ -> checked[i] },
                )
                onImport(opts)
            }
            .setNegativeButton("Cancel") { _, _ -> onCancel() }
            .setCancelable(false)
            .show()
    }

    /**
     * Yes/No store question (history, subscription groups) as a suspending
     * call. Not cancelable — dismissing it must not leave the importer stuck.
     */
    suspend fun askYesNo(context: Context, title: String, message: String): Boolean =
        suspendCancellableCoroutine { cont ->
            val dialog = AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("Yes") { _, _ -> cont.resume(true) }
                .setNegativeButton("No") { _, _ -> cont.resume(false) }
                .show()
            cont.invokeOnCancellation { dialog.dismiss() }
        }


}
