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
        var dialogRef: Dialog? = null
        val dialog = showInDialogWindow(context) {
            ImportOptionsContent(
                export,
                unknownPluginUrls,
                onImport = { dialogRef?.dismiss(); onImport(it) },
                onCancel = { dialogRef?.dismiss(); onCancel() },
            )
        }
        dialogRef = dialog
        return dialog
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

    @Composable
    private fun ImportOptionsContent(
        export: StateBackup.ExportStructure,
        unknownPluginUrls: List<String>,
        onImport: (ImportOptions) -> Unit,
        onCancel: () -> Unit
    ) {
        // Every section the archive contains starts selected.
        var importSettings by remember { mutableStateOf(export.settings != null) }
        var installPlugins by remember { mutableStateOf(unknownPluginUrls.isNotEmpty()) }
        var enablePlugins by remember { mutableStateOf(true) }
        var importPluginSettings by remember { mutableStateOf(export.pluginSettings.isNotEmpty()) }
        var importStores by remember { mutableStateOf(export.stores.isNotEmpty()) }

        AlertDialog(
            onDismissRequest = onCancel,
            title = { Text("Import backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (export.settings != null)
                        SectionRow("Settings", "App preferences", importSettings) { importSettings = it }
                    if (unknownPluginUrls.isNotEmpty())
                        SectionRow("Plugins (${unknownPluginUrls.size})", unknownPluginUrls.joinToString("\n"), installPlugins) { installPlugins = it }
                    if (export.pluginSettings.isNotEmpty())
                        SectionRow("Plugin settings (${export.pluginSettings.size})", null, importPluginSettings) { importPluginSettings = it }
                    SectionRow("Enable all plugins", "Enables all available sources during import", enablePlugins) { enablePlugins = it }
                    if (export.stores.isNotEmpty())
                        SectionRow(
                            "Stores",
                            export.stores.entries.joinToString("\n") { "${it.key} (${it.value.size})" },
                            importStores
                        ) { importStores = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onImport(ImportOptions(importSettings, installPlugins, enablePlugins, importPluginSettings, importStores))
                }) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = onCancel) { Text("Cancel") }
            },
        )
    }

    @Composable
    private fun SectionRow(label: String, detail: String?, checked: Boolean, onChecked: (Boolean) -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onChecked(!checked) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = checked, onCheckedChange = onChecked)
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                if (detail != null)
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
            }
        }
    }

    private fun showInDialogWindow(context: Context, content: @Composable () -> Unit): Dialog {
        val dialog = Dialog(context)
        // A Dialog window carries no ViewTreeLifecycleOwner (unlike an
        // activity window), but Compose requires one in the hierarchy — bind
        // the owning activity's lifecycle (same as CompanionPresentation),
        // falling back to a permanently-resumed owner.
        // The passed context may be a ContextWrapper, not the Activity itself —
        // unwrap to find the real one; Compose needs all three view-tree owners.
        fun Context.findActivity(): android.app.Activity? =
            when (this) {
                is android.app.Activity -> this
                is android.content.ContextWrapper -> baseContext.findActivity()
                else -> null
            }
        val activity = context.findActivity()
        val lifecycleOwner: LifecycleOwner = (activity as? LifecycleOwner) ?: object : LifecycleOwner {
            private val registry = LifecycleRegistry(this)

            init {
                registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            }

            override val lifecycle: Lifecycle
                get() = registry
        }
        val savedStateOwner: SavedStateRegistryOwner =
            (activity as? SavedStateRegistryOwner) ?: object : SavedStateRegistryOwner {
                private val registry = LifecycleRegistry(this)
                private val controller = androidx.savedstate.SavedStateRegistryController.create(this)
                override val savedStateRegistry get() = controller.savedStateRegistry
                override val lifecycle: Lifecycle get() = registry
                init {
                    registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                    registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
                    controller.performRestore(null)
                }
            }
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(savedStateOwner)
            (activity as? androidx.lifecycle.ViewModelStoreOwner)?.let { setViewTreeViewModelStoreOwner(it) }
            // Belt and braces: also set on the dialog's decor so the whole
            // window hierarchy propagates the owners.
            dialog.window?.decorView?.let { decor ->
                decor.setViewTreeLifecycleOwner(lifecycleOwner)
                decor.setViewTreeSavedStateRegistryOwner(savedStateOwner)
                (activity as? androidx.lifecycle.ViewModelStoreOwner)?.let { decor.setViewTreeViewModelStoreOwner(it) }
            }
            setContent {
                BluejayTheme {
                    content()
                }
            }
        }
        // Not cancelable: only the Import/Cancel buttons (or back on the
        // AlertDialog, which routes to onCancel) may close it.
        dialog.setCancelable(false)
        dialog.setContentView(
            composeView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        dialog.show()
        return dialog
    }
}
