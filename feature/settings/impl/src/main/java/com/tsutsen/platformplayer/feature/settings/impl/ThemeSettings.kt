package com.tsutsen.platformplayer.feature.settings.impl

import com.tsutsen.platformplayer.core.ui.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tsutsen.platformplayer.core.datastore.model.AppearancePreferences
import com.tsutsen.platformplayer.core.datastore.model.CustomTheme
import com.tsutsen.platformplayer.core.datastore.model.PaletteStyle
import com.tsutsen.platformplayer.core.designsystem.component.BluejayModalBottomSheet
import com.tsutsen.platformplayer.core.designsystem.component.ColorPicker
import com.tsutsen.platformplayer.core.designsystem.component.GroupPosition
import com.tsutsen.platformplayer.core.designsystem.component.expressiveClickable
import com.tsutsen.platformplayer.core.designsystem.component.groupShape
import com.tsutsen.platformplayer.core.designsystem.theme.ThemeEngine
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens


/**
 * Custom themes management: list of saved themes + the theme editor sheet.
 * Shown inline in Settings > Appearance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemesSection(
    appearance: AppearancePreferences,
    viewModel: SettingsViewModel,
) {
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CustomTheme?>(null) } // null = new theme
    val context = LocalContext.current

    // Import: pick a theme JSON file, validate it and add it to the list.
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val imported =
                runCatching {
                    readImportedTheme(context, uri, appearance.customThemes.map { it.id }.toSet())
                }.getOrNull()
            if (imported != null) {
                viewModel.saveTheme(imported)
                Toast.makeText(context, R.string.theme_imported, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, R.string.theme_import_failed, Toast.LENGTH_SHORT).show()
            }
        }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.SpaceSm),
    ) {
        // "Default" is the always-present first entry (Material You / brand);
        // selecting it deactivates any custom theme.
        DefaultThemeRow(
            isActive = appearance.activeThemeId == null,
            groupPosition = GroupPosition.fromIndex(0, appearance.customThemes.size + 1),
            onUse = { viewModel.setActiveTheme(null) },
        )
        if (appearance.customThemes.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                appearance.customThemes.forEachIndexed { index, theme ->
                    ThemeRow(
                        theme = theme,
                        isActive = theme.id == appearance.activeThemeId,
                        groupPosition =
                            GroupPosition.fromIndex(index + 1, appearance.customThemes.size + 1),
                        onClick = {
                            editing = theme
                            editorOpen = true
                        },
                        onUse = { viewModel.setActiveTheme(theme.id) },
                        onDelete = { viewModel.deleteTheme(theme.id) },
                        onExport = {
                            runCatching { exportTheme(context, theme) }
                                .onFailure {
                                    Toast.makeText(
                                        context,
                                        R.string.theme_export_failed,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                        },
                    )
                }
            }
        }

        ElevatedButton(
            onClick = {
                editing = null
                editorOpen = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text(stringResource(R.string.settings_new_theme), modifier = Modifier.padding(start = 8.dp))
        }

        ElevatedButton(
            onClick = {
                importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Download, contentDescription = null)
            Text(stringResource(R.string.theme_import), modifier = Modifier.padding(start = 8.dp))
        }
    }

    if (editorOpen) {
        BluejayModalBottomSheet(
            onDismiss = { editorOpen = false },
            title = if (editing == null) "New theme" else "Edit theme",
        ) {
            ThemeEditor(
                initial = editing,
                onSave = { theme ->
                    viewModel.saveTheme(theme)
                    // Creating or editing an active theme keeps it active.
                    if (appearance.activeThemeId == null ||
                        appearance.activeThemeId == theme.id
                    ) {
                        viewModel.setActiveTheme(theme.id)
                    }
                    editorOpen = false
                },
            )
        }
    }
}

/** The always-present "Default" theme entry (Material You / brand colors). */
@Composable
private fun DefaultThemeRow(
    isActive: Boolean,
    groupPosition: GroupPosition = GroupPosition.Single,
    onUse: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = groupShape(groupPosition),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isActive) scheme.secondaryContainer else scheme.surfaceContainer,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(Tokens.SpaceMd),
            horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceXs),
                modifier = Modifier.clip(CircleShape),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(Tokens.SwatchSm)
                            .background(scheme.primary, CircleShape),
                )
                Box(
                    modifier =
                        Modifier
                            .size(Tokens.SwatchSm)
                            .background(scheme.secondary, CircleShape),
                )
                Box(
                    modifier =
                        Modifier
                            .size(Tokens.SwatchSm)
                            .background(scheme.tertiary, CircleShape),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_default),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(R.string.settings_theme_material_you_brand),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isActive) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = stringResource(R.string.settings_theme_active_theme),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(Tokens.IconMd),
                )
            } else {
                TextButton(onClick = onUse) { Text(stringResource(R.string.settings_theme_use)) }
            }
        }
    }
}

@Composable
private fun ThemeRow(
    theme: CustomTheme,
    isActive: Boolean,
    groupPosition: GroupPosition = GroupPosition.Single,
    onClick: () -> Unit,
    onUse: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit = {},
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = groupShape(groupPosition),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isActive) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(Tokens.SpaceMd),
            horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceXs),
                modifier = Modifier.clip(CircleShape),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(Tokens.SwatchSm)
                            .background(theme.primary.toColor(), CircleShape),
                )
                theme.secondary?.let {
                    Box(
                        modifier =
                            Modifier
                                .size(Tokens.SwatchSm)
                                .background(it.toColor(), CircleShape),
                    )
                }
                theme.tertiary?.let {
                    Box(
                        modifier =
                            Modifier
                                .size(Tokens.SwatchSm)
                                .background(it.toColor(), CircleShape),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = theme.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = theme.paletteStyle.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isActive) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = stringResource(R.string.settings_theme_active_theme),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(Tokens.IconMd),
                )
            } else {
                TextButton(onClick = onUse) { Text(stringResource(R.string.settings_theme_use)) }
            }
            IconButton(onClick = onExport) {
                Icon(
                    Icons.Default.SaveAlt,
                    contentDescription = stringResource(R.string.theme_export),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.settings_theme_delete_theme),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Compact side-by-side light+dark preview of the generated scheme. */
@Composable
private fun ThemePreview(
    primary: Int,
    secondary: Int?,
    tertiary: Int?,
    style: PaletteStyle,
    background: Int? = null,
    contrast: Float = 0f,
) {
    val schemes =
        remember(primary, secondary, tertiary, style, background, contrast) {
            ThemeEngine.generate(
                primary,
                secondary,
                tertiary,
                style,
                background = background,
                contrast = contrast,
            )
        }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSm),
    ) {
        ThemePreviewCard(title = stringResource(R.string.settings_light), scheme = schemes.light, modifier = Modifier.weight(1f))
        ThemePreviewCard(title = stringResource(R.string.settings_dark), scheme = schemes.dark, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ThemePreviewCard(
    title: String,
    scheme: ColorScheme,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = scheme.surface,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(Tokens.SpaceMd),
            verticalArrangement = Arrangement.spacedBy(Tokens.SpaceSm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSm)) {
                PreviewSwatch(scheme.primary)
                PreviewSwatch(scheme.secondary)
                PreviewSwatch(scheme.tertiary)
            }
            Surface(
                color = scheme.primaryContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = stringResource(R.string.settings_theme_container),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = Tokens.SpaceSm, vertical = Tokens.SpaceXs),
                )
            }
        }
    }
}

@Composable
private fun PreviewSwatch(color: Color) {
    Box(modifier = Modifier.size(Tokens.SwatchXs).clip(CircleShape).background(color))
}

/**
 * Free-form color picker in a dialog: featured presets + the free-form
 * ColorPicker (saturation/value square, hue slider, hex field) on top,
 * with the full 32-color preset grid below for quick picks.
 */
@Composable
internal fun ColorPickerDialog(
    title: String,
    initialColor: Color,
    onPick: (Color) -> Unit,
    onDismiss: () -> Unit,
) {
    // Draft color: nothing commits until Apply, so exploring colors in the
    // picker doesn't close the dialog or write the slot immediately.
    var draft by remember { mutableStateOf(initialColor.toArgb()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier =
                    Modifier
                        // Scroll when the picker content exceeds the screen —
                        // without this the bottom rows compress/clip.
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Tokens.SpaceMd),
            ) {
                ColorPicker(
                    initialColor = draft,
                    onColorChange = { draft = it },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(Color(draft)) }) {
                Text(stringResource(R.string.settings_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}

private val PaletteStyle.label: String
    get() =
        when (this) {
            PaletteStyle.TONAL_SPOT -> "Tonal spot"
            PaletteStyle.VIBRANT -> "Vibrant"
            PaletteStyle.EXPRESSIVE -> "Expressive"
            PaletteStyle.FRUIT_SALAD -> "Fruit salad"
        }
