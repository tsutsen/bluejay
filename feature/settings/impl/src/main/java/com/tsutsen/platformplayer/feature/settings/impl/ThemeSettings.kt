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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tsutsen.platformplayer.core.datastore.model.AppearancePreferences
import com.tsutsen.platformplayer.core.datastore.model.CustomTheme
import com.tsutsen.platformplayer.core.datastore.model.PaletteStyle
import com.tsutsen.platformplayer.core.designsystem.component.BluejayModalBottomSheet
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
 * Swatch-grid color picker in a dialog. M3 1.4.0 has no ColorPicker
 * composable, so a preset palette is the whole picker — tap picks.
 */
@Composable
internal fun ColorPickerDialog(
    initialColor: Color,
    onPick: (Color) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(Tokens.SpaceLg)
                        .width(Tokens.DialogSm),
                verticalArrangement = Arrangement.spacedBy(Tokens.SpaceMd),
            ) {
                Text(
                    text = "#" + initialColor.toArgb().toString(16),
                    style = MaterialTheme.typography.labelLarge,
                )
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceSm)) {
                    (listOf(initialColor.toArgb()) + PresetColors)
                        .distinct()
                        .chunked(6)
                        .forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSm)) {
                                row.forEach { argb ->
                                    ColorSwatch(
                                        color = Color(argb),
                                        isSelected = argb == initialColor.toArgb(),
                                        onClick = { onPick(Color(argb)) },
                                    )
                                }
                            }
                        }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
            }
        }
    }
}

@Composable
internal fun ColorSwatch(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(Tokens.SwatchMd)
                .expressiveClickable(onClick = onClick)
                .clip(CircleShape)
                .background(color),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(Tokens.IconXs),
            )
        }
    }
}

internal val PresetColors: List<Int> =
    listOf(
        0xFF6750A4.toInt(),
        0xFF7C4DFF.toInt(),
        0xFF6200EE.toInt(),
        0xFF5E35B1.toInt(),
        0xFF3F51B5.toInt(),
        0xFF1E88E5.toInt(),
        0xFF2962FF.toInt(),
        0xFF00B0FF.toInt(),
        0xFF009688.toInt(),
        0xFF018786.toInt(),
        0xFF00BFA5.toInt(),
        0xFF43A047.toInt(),
        0xFF4CAF50.toInt(),
        0xFF00C853.toInt(),
        0xFF76FF03.toInt(),
        0xFFFBC02D.toInt(),
        0xFFFFAB40.toInt(),
        0xFFF57C00.toInt(),
        0xFFFF6D00.toInt(),
        0xFFC62828.toInt(),
        0xFFD50000.toInt(),
        0xFFFF5252.toInt(),
        0xFFC2185B.toInt(),
        0xFFFF4081.toInt(),
        0xFF8D6E63.toInt(),
        0xFF546E7A.toInt(),
        0xFF37474F.toInt(),
        0xFF757575.toInt(),
        0xFFB0BEC5.toInt(),
        0xFF212121.toInt(),
        0xFF625B71.toInt(),
        0xFF7D5260.toInt(),
    )
private val PaletteStyle.label: String
    get() =
        when (this) {
            PaletteStyle.TONAL_SPOT -> "Tonal spot"
            PaletteStyle.VIBRANT -> "Vibrant"
            PaletteStyle.EXPRESSIVE -> "Expressive"
            PaletteStyle.FRUIT_SALAD -> "Fruit salad"
        }
