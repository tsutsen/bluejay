package com.tsutsen.platformplayer.feature.settings.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.tsutsen.platformplayer.core.designsystem.theme.ThemeEngine
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import java.util.UUID

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
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        appearance.customThemes.forEach { theme ->
            ThemeRow(
                theme = theme,
                isActive = theme.id == appearance.activeThemeId,
                onClick = {
                    editing = theme
                    editorOpen = true
                },
                onUse = { viewModel.setActiveTheme(theme.id) },
                onDelete = { viewModel.deleteTheme(theme.id) },
            )
        }

        if (appearance.activeThemeId != null) {
            TextButton(
                onClick = { viewModel.setActiveTheme(null) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("Use default theme")
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
            Text("New theme", modifier = Modifier.padding(start = 8.dp))
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

@Composable
private fun ThemeRow(
    theme: CustomTheme,
    isActive: Boolean,
    onClick: () -> Unit,
    onUse: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isActive)
                        MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceContainer,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.clip(CircleShape),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(24.dp)
                            .background(theme.primary.toColor(), CircleShape),
                )
                theme.secondary?.let {
                    Box(
                        modifier =
                            Modifier
                                .size(24.dp)
                                .background(it.toColor(), CircleShape),
                    )
                }
                theme.tertiary?.let {
                    Box(
                        modifier =
                            Modifier
                                .size(24.dp)
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
                    contentDescription = "Active theme",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(Tokens.IconMd),
                )
            } else {
                TextButton(onClick = onUse) { Text("Use") }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete theme",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Theme editor form: name, three key colors (primary required, secondary and
 * tertiary optional), palette style, and a live preview of the generated
 * scheme (light and dark).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeEditor(
    initial: CustomTheme?,
    onSave: (CustomTheme) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var primary by remember(initial) {
        mutableStateOf(initial?.primary ?: 0xFF6750A4.toInt())
    }
    var secondary by remember(initial) { mutableStateOf(initial?.secondary) }
    var tertiary by remember(initial) { mutableStateOf(initial?.tertiary) }
    var style by remember(initial) {
        mutableStateOf(initial?.paletteStyle ?: PaletteStyle.TONAL_SPOT)
    }
    var pickSlot by remember { mutableStateOf<ThemeSlot?>(null) }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.SpaceLg),
        verticalArrangement = Arrangement.spacedBy(Tokens.SpaceMd),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Theme name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        ThemeColorRow(
            label = "Primary",
            color = primary.toColor(),
            optional = false,
            onPick = { pickSlot = ThemeSlot.PRIMARY },
            onClear = {},
        )
        ThemeColorRow(
            label = "Secondary",
            color = secondary?.toColor(),
            optional = true,
            onPick = { pickSlot = ThemeSlot.SECONDARY },
            onClear = { secondary = null },
        )
        ThemeColorRow(
            label = "Tertiary",
            color = tertiary?.toColor(),
            optional = true,
            onPick = { pickSlot = ThemeSlot.TERTIARY },
            onClear = { tertiary = null },
        )

        Text(
            text = "Palette style",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )
        PaletteStyle.entries.forEach { option ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RadioButton(
                    selected = style == option,
                    onClick = { style = option },
                )
                Text(option.label)
            }
        }

        ThemePreview(
            primary = primary,
            secondary = secondary,
            tertiary = tertiary,
            style = style,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            ElevatedButton(
                onClick = {
                    onSave(
                        CustomTheme(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            name = name.ifBlank { "My theme" },
                            primary = primary,
                            secondary = secondary,
                            tertiary = tertiary,
                            paletteStyle = style,
                        )
                    )
                },
            ) {
                Text("Save")
            }
        }
    }

    pickSlot?.let { slot ->
        val initialColor =
            when (slot) {
                ThemeSlot.PRIMARY -> primary.toColor()
                ThemeSlot.SECONDARY -> (secondary ?: 0xFF625B71.toInt()).toColor()
                ThemeSlot.TERTIARY -> (tertiary ?: 0xFF7D5260.toInt()).toColor()
            }
        ColorPickerDialog(
            initialColor = initialColor,
            onPick = { newColor ->
                val argb = newColor.toArgb()
                when (slot) {
                    ThemeSlot.PRIMARY -> primary = argb
                    ThemeSlot.SECONDARY -> secondary = argb
                    ThemeSlot.TERTIARY -> tertiary = argb
                }
                pickSlot = null
            },
            onDismiss = { pickSlot = null },
        )
    }
}

@Composable
private fun ThemeColorRow(
    label: String,
    color: Color?,
    optional: Boolean,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color ?: MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = if (color == null) "Not set" else "#" + color.toArgb().toString(16),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (optional && color != null) {
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Delete, contentDescription = "Clear $label")
            }
        }
        TextButton(onClick = onPick) { Text("Pick") }
    }
}

/** Live preview of the scheme ThemeEngine would generate for these inputs. */
@Composable
private fun ThemePreview(
    primary: Int,
    secondary: Int?,
    tertiary: Int?,
    style: PaletteStyle,
) {
    val schemes =
        remember(primary, secondary, tertiary, style) {
            ThemeEngine.generate(primary, secondary, tertiary, style)
        }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemePreviewCard(title = "Light", light = true, schemes = schemes)
        ThemePreviewCard(title = "Dark", light = false, schemes = schemes)
    }
}

@Composable
private fun ThemePreviewCard(
    title: String,
    light: Boolean,
    schemes: ThemeEngine.Schemes,
) {
    val scheme = if (light) schemes.light else schemes.dark
    Surface(
        color =
            if (light)
                MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.inverseSurface,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PreviewSwatch(scheme.primary)
                PreviewSwatch(scheme.secondary)
                PreviewSwatch(scheme.tertiary)
            }
            Surface(
                color = scheme.primaryContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    text = "Primary container",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onPrimaryContainer,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun PreviewSwatch(color: Color) {
    Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(color))
}

/**
 * Swatch-grid color picker in a dialog. M3 1.4.0 has no ColorPicker
 * composable, so a preset palette is the whole picker — tap picks.
 */
@Composable
private fun ColorPickerDialog(
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
                        .width(300.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "#" + initialColor.toArgb().toString(16),
                    style = MaterialTheme.typography.labelLarge,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (listOf(initialColor.toArgb()) + PresetColors).distinct().chunked(8)
                        .forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(color)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private val PresetColors: List<Int> =
    listOf(
        0xFF6750A4.toInt(), 0xFF7C4DFF.toInt(), 0xFF6200EE.toInt(), 0xFF5E35B1.toInt(),
        0xFF3F51B5.toInt(), 0xFF1E88E5.toInt(), 0xFF2962FF.toInt(), 0xFF00B0FF.toInt(),
        0xFF009688.toInt(), 0xFF018786.toInt(), 0xFF00BFA5.toInt(), 0xFF43A047.toInt(),
        0xFF4CAF50.toInt(), 0xFF00C853.toInt(), 0xFF76FF03.toInt(), 0xFFFBC02D.toInt(),
        0xFFFFAB40.toInt(), 0xFFF57C00.toInt(), 0xFFFF6D00.toInt(), 0xFFC62828.toInt(),
        0xFFD50000.toInt(), 0xFFFF5252.toInt(), 0xFFC2185B.toInt(), 0xFFFF4081.toInt(),
        0xFF8D6E63.toInt(), 0xFF546E7A.toInt(), 0xFF37474F.toInt(), 0xFF757575.toInt(),
        0xFFB0BEC5.toInt(), 0xFF212121.toInt(), 0xFF625B71.toInt(), 0xFF7D5260.toInt(),
    )

private enum class ThemeSlot { PRIMARY, SECONDARY, TERTIARY }

private val PaletteStyle.label: String
    get() =
        when (this) {
            PaletteStyle.TONAL_SPOT -> "Tonal spot"
            PaletteStyle.VIBRANT -> "Vibrant"
            PaletteStyle.EXPRESSIVE -> "Expressive"
            PaletteStyle.FRUIT_SALAD -> "Fruit salad"
        }

private fun Int.toColor(): Color = Color(this)
