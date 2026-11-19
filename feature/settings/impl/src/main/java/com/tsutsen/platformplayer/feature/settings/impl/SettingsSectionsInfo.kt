package com.tsutsen.platformplayer.feature.settings.impl

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import com.tsutsen.platformplayer.core.ui.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Copyright
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tsutsen.platformplayer.core.data.repository.UpdateCheckState
import com.tsutsen.platformplayer.core.designsystem.component.GroupPosition
import com.tsutsen.platformplayer.core.designsystem.component.SettingsOptionCard
import com.tsutsen.platformplayer.core.designsystem.icon.Download
import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTokens
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens

/**
 * "Backup & restore" section: tiles with the size of the current app
 * state (loaded on demand, no background work), then export / restore.
 * Both actions run the app's existing backup engine (StateBackup), which
 * handles the file pickers, the zip format and the import UI.
 */
@Composable
internal fun BackupItems(
    viewModel: SettingsViewModel,
    onNavigateTab: (String) -> Unit = {},
) {
    val summary by viewModel.backupSummary.collectAsState()
    var confirmRestore by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadBackupSummary() }

    Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceLg)) {
        // State tiles: two per row, the third spans the full width.
        Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceSm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSm)) {
                BackupTile(
                    count = summary?.subscriptions ?: 0,
                    label = "subscriptions",
                    detail = "across ${summary?.sources ?: 0} sources",
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateTab("subscriptions") },
                )
                BackupTile(
                    count = summary?.savedVideos ?: 0,
                    label = "videos",
                    detail = "across ${summary?.savedPlaylists ?: 0} playlists",
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateTab("library") },
                )
            }
            BackupTile(
                count = summary?.customSettings ?: 0,
                label = "custom settings",
                detail = "across ${summary?.sections ?: 0} sections",
                modifier = Modifier.fillMaxWidth(),
                onClick = { onNavigateTab("settings") },
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
            SettingsOptionCard(
                icon = Icons.Filled.Upload,
                title = stringResource(R.string.settings_export_backup),
                subtitle = stringResource(R.string.settings_save_settings_subscriptions_playlists_watch_later_history_an),
                onClick = { viewModel.exportBackup() },
                groupPosition = GroupPosition.First,
            )
            SettingsOptionCard(
                icon = Icons.Filled.Restore,
                title = stringResource(R.string.settings_restore_from_backup),
                subtitle = stringResource(R.string.settings_load_a_backup_file_and_restore_the_app_state),
                onClick = { confirmRestore = true },
                groupPosition = GroupPosition.Last,
            )
        }

        Text(
            text = stringResource(R.string.settings_backup_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text(stringResource(R.string.settings_restore_backup)) },
            text = {
                Text(stringResource(R.string.settings_restore_disclaimer))
            },
            confirmButton = {
                TextButton(onClick = { confirmRestore = false; viewModel.importBackup() }) {
                    Text(stringResource(R.string.settings_restore))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text(stringResource(R.string.settings_cancel)) }
            },
        )
    }
}

@Composable
internal fun BackupTile(
    count: Int,
    label: String,
    detail: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick ?: {},
        enabled = onClick != null,
        shape = RoundedCornerShape(BluejayTokens().radius.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(Tokens.SpaceMd),
            verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXxs),
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = scheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * About page: app identity + version, the support-the-original notice
 * (Bluejay is a fork of Grayjay, which builds on FUTO), and the standard
 * about links (upstream site, source repo, license).
 */
@Composable
internal fun AboutItems(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    // The feature module has no BuildConfig of its own (versionName is set
    // on the app module) — read it from the package manager instead.
    val version =
        runCatching {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
        }.getOrNull() ?: "unknown"

    Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceLg)) {
        Card(
            shape = RoundedCornerShape(BluejayTokens().radius.card),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(Tokens.SpaceLg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The real launcher icon, rendered from the package
                // manager (the feature module can't reference app R
                // resources).
                val launcherIcon =
                    remember {
                        val drawable: android.graphics.drawable.Drawable =
                            context.packageManager.getApplicationIcon(context.packageName)
                        val sizePx = 96
                        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        drawable.setBounds(0, 0, sizePx, sizePx)
                        drawable.draw(canvas)
                        bitmap.asImageBitmap()
                    }
                Image(
                    bitmap = launcherIcon,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(Tokens.AvatarLg)
                            .clip(RoundedCornerShape(BluejayTokens().radius.sm)),
                )
                Spacer(Modifier.width(Tokens.SpaceLg))
                Column {
                    Text(
                        text = stringResource(R.string.settings_bluejay),
                        style = MaterialTheme.typography.titleLarge,
                        color = scheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.settings_version_1_s, version),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }

        Text(
            text =
                "Bluejay is a rewrite of Grayjay media app by FUTO and it still " +
                    "relies on the plugins developped and updated by them! So if you " +
                    "love this app, consider supporting the original project and their team.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )

        Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
            val updateState by viewModel.updateCheckState.collectAsState()
            SettingsOptionCard(
                icon = Download,
                title = stringResource(R.string.settings_check_for_updates),
                subtitle =
                    when (val s = updateState) {
                        UpdateCheckState.Idle -> "Check GitHub releases — current $version"
                        UpdateCheckState.Checking -> "Checking…"
                        is UpdateCheckState.UpToDate -> "Up to date (${s.versionName})"
                        is UpdateCheckState.Available -> "${s.tag} available — tap to download"
                        is UpdateCheckState.Downloading -> "${s.tag} downloading…"
                        is UpdateCheckState.Ready -> "${s.tag} downloaded — installing… (tap to retry)"
                        is UpdateCheckState.Failed -> "Check failed: ${s.message}"
                    },
                onClick = { viewModel.onUpdateCardClick() },
                groupPosition = GroupPosition.First,
            )
            SettingsOptionCard(
                icon = Icons.Filled.PlayArrow,
                title = stringResource(R.string.settings_launch_initial_setup),
                subtitle = stringResource(R.string.settings_show_the_getting_started_flow_again),
                onClick = { viewModel.restartGettingStarted() },
                groupPosition = GroupPosition.Middle,
            )
            SettingsOptionCard(
                icon = Icons.Filled.Launch,
                title = stringResource(R.string.settings_support_grayjay),
                subtitle = "grayjay.app — the original project",
                onClick = { openUrl(context, "https://grayjay.app") },
                groupPosition = GroupPosition.Middle,
            )
            SettingsOptionCard(
                icon = Icons.Filled.Code,
                title = stringResource(R.string.settings_github),
                subtitle = stringResource(R.string.settings_source_code_issues_feature_requests),
                onClick = {
                    openUrl(context, "https://github.com/tsutsen/bluejay")
                },
                groupPosition = GroupPosition.Middle,
            )
            SettingsOptionCard(
                icon = Icons.Filled.Copyright,
                title = stringResource(R.string.settings_license),
                subtitle = stringResource(R.string.settings_source_first_license_1_1),
                onClick = {
                    openUrl(
                        context,
                        "https://github.com/tsutsen/bluejay/blob/master/LICENSE.md",
                    )
                },
                groupPosition = GroupPosition.Last,
            )
        }
    }
}

internal fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
