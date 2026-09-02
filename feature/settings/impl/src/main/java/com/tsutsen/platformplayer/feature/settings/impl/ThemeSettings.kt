package com.tsutsen.platformplayer.feature.settings.impl

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import com.tsutsen.platformplayer.core.designsystem.component.GroupPosition
import com.tsutsen.platformplayer.core.designsystem.component.expressiveClickable
import com.tsutsen.platformplayer.core.designsystem.component.groupShape
import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTokens
import com.tsutsen.platformplayer.core.designsystem.theme.ThemeEngine
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import java.util.UUID
import kotlin.math.roundToInt

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
        if (appearance.customThemes.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                appearance.customThemes.forEachIndexed { index, theme ->
                    ThemeRow(
                        theme = theme,
                        isActive = theme.id == appearance.activeThemeId,
                        groupPosition =
                            GroupPosition.fromIndex(index, appearance.customThemes.size),
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
 * Theme editor form: name, three key colors in one row (primary required,
 * secondary and tertiary optional), a swatch-style palette style selector,
 * and a compact light+dark preview of the generated scheme.
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
    var background by remember(initial) { mutableStateOf(initial?.background) }
    var style by remember(initial) {
        mutableStateOf(initial?.paletteStyle ?: PaletteStyle.TONAL_SPOT)
    }
    var contrast by remember(initial) { mutableStateOf(initial?.contrast ?: 0f) }
    var pickSlot by remember { mutableStateOf<ThemeSlot?>(null) }

    // One generated scheme per style, so each swatch square shows what that
    // style actually does with the user's current colors.
    val styleSchemes =
        remember(primary, secondary, tertiary, background, contrast) {
            PaletteStyle.entries.associateWith {
                ThemeEngine.generate(
                    primary,
                    secondary,
                    tertiary,
                    it,
                    background = background,
                    contrast = contrast,
                ).light
            }
        }

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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceMd),
        ) {
            ThemeColorSlot(
                label = "Primary",
                color = primary,
                optional = false,
                modifier = Modifier.weight(1f),
                onPick = { pickSlot = ThemeSlot.PRIMARY },
                onClear = {},
            )
            ThemeColorSlot(
                label = "Secondary",
                color = secondary,
                optional = true,
                modifier = Modifier.weight(1f),
                onPick = { pickSlot = ThemeSlot.SECONDARY },
                onClear = { secondary = null },
            )
            ThemeColorSlot(
                label = "Tertiary",
                color = tertiary,
                optional = true,
                modifier = Modifier.weight(1f),
                onPick = { pickSlot = ThemeSlot.TERTIARY },
                onClear = { tertiary = null },
            )
            // Background (surfaces) is the fourth optional key color.
            ThemeColorSlot(
                label = "Background",
                color = background,
                optional = true,
                modifier = Modifier.weight(1f),
                onPick = { pickSlot = ThemeSlot.BACKGROUND },
                onClear = { background = null },
            )
        }

        Text(
            text = "Palette style",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSm),
        ) {
            PaletteStyle.entries.forEach { option ->
                PaletteSwatchSquare(
                    scheme = styleSchemes.getValue(option),
                    selected = style == option,
                    onClick = { style = option },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            text = style.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Contrast",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = if (contrast == 0f) "standard" else (contrast * 100).roundToInt().toString() + "%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // -1 = minimum, 0 = standard (as specced), 1 = maximum — the
            // spec's contrast level drives the per-role contrast curves.
            Slider(
                value = contrast,
                onValueChange = { contrast = it },
                valueRange = -1f..1f,
                steps = 3,
            )
        }

        ThemePreview(
            primary = primary,
            secondary = secondary,
            tertiary = tertiary,
            background = background,
            style = style,
            contrast = contrast,
        )

        Button(
            onClick = {
                onSave(
                    CustomTheme(
                        id = initial?.id ?: UUID.randomUUID().toString(),
                        name = name.ifBlank { "My theme" },
                        primary = primary,
                        secondary = secondary,
                        tertiary = tertiary,
                        background = background,
                        paletteStyle = style,
                        contrast = contrast,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save")
        }
    }

    pickSlot?.let { slot ->
        val initialColor =
            when (slot) {
                ThemeSlot.PRIMARY -> primary.toColor()
                ThemeSlot.SECONDARY -> (secondary ?: 0xFF625B71.toInt()).toColor()
                ThemeSlot.TERTIARY -> (tertiary ?: 0xFF7D5260.toInt()).toColor()
                ThemeSlot.BACKGROUND -> (background ?: 0xFFF7F2FA.toInt()).toColor()
            }
        ColorPickerDialog(
            initialColor = initialColor,
            onPick = { newColor ->
                val argb = newColor.toArgb()
                when (slot) {
                    ThemeSlot.PRIMARY -> primary = argb
                    ThemeSlot.SECONDARY -> secondary = argb
                    ThemeSlot.TERTIARY -> tertiary = argb
                    ThemeSlot.BACKGROUND -> background = argb
                }
                pickSlot = null
            },
            onDismiss = { pickSlot = null },
        )
    }
}

/**
 * One key color as a tappable swatch: 48dp circle, label under it, and a
 * small clear badge for the optional slots.
 */
@Composable
private fun ThemeColorSlot(
    label: String,
    color: Int?,
    optional: Boolean,
    modifier: Modifier = Modifier,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs),
    ) {
        Box(
            modifier =
                Modifier
                    .size(Tokens.SwatchLg)
                    .expressiveClickable(onClick = onPick)
                    .clip(CircleShape)
                    .background(color?.toColor() ?: MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (color == null) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Pick $label color",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (optional && color != null) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(Tokens.SwatchSm)
                        .expressiveClickable(onClick = onClear)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Clear $label",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Tokens.IconXs),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Swatch square for the palette style selector (PixelPlayer style): a 2x2
 * grid of the scheme's key colors. Selected = squarer corners + primary
 * border, unselected = circular.
 */
@Composable
private fun PaletteSwatchSquare(
    scheme: ColorScheme,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val r = BluejayTokens().radius
    val outerCorner =
        if (selected) r.md else r.lg
    // The selection border is concentric: same rectangle as the shape, but
    // with its corner radius shrunk by the stroke width so the outline
    // follows the outer rounding instead of sitting slightly outside it.
    val borderCorner =
        if (selected) r.md - Tokens.StrokeEmphasized else r.lg
    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .expressiveClickable(onClick = onClick)
                .clip(RoundedCornerShape(outerCorner))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(
                    width = if (selected) Tokens.StrokeEmphasized else 0.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(borderCorner),
                ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(if (selected) Tokens.SpaceXs else Tokens.SpaceXxs),
        ) {
            Row(modifier = Modifier.weight(1f)) {
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(scheme.primary),
                )
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(scheme.secondary),
                )
            }
            Row(modifier = Modifier.weight(1f)) {
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(scheme.tertiary),
                )
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(scheme.surfaceContainerHighest),
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
        ThemePreviewCard(title = "Light", scheme = schemes.light, modifier = Modifier.weight(1f))
        ThemePreviewCard(title = "Dark", scheme = schemes.dark, modifier = Modifier.weight(1f))
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
                    text = "Container",
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
                        .chunked(8)
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

private val PresetColors: List<Int> =
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

private enum class ThemeSlot { PRIMARY, SECONDARY, TERTIARY, BACKGROUND }

private val PaletteStyle.label: String
    get() =
        when (this) {
            PaletteStyle.TONAL_SPOT -> "Tonal spot"
            PaletteStyle.VIBRANT -> "Vibrant"
            PaletteStyle.EXPRESSIVE -> "Expressive"
            PaletteStyle.FRUIT_SALAD -> "Fruit salad"
        }

private fun Int.toColor(): Color = Color(this)
