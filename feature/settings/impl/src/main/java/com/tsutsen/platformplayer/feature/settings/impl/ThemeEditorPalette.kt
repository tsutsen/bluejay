package com.tsutsen.platformplayer.feature.settings.impl

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.tsutsen.platformplayer.core.ui.R
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tsutsen.platformplayer.core.datastore.model.CustomTheme
import com.tsutsen.platformplayer.core.datastore.model.PaletteStyle
import com.tsutsen.platformplayer.core.datastore.model.SurfaceStyle
import com.tsutsen.platformplayer.core.designsystem.component.ColorPicker
import com.tsutsen.platformplayer.core.designsystem.component.PresetColorsFeatured
import com.tsutsen.platformplayer.core.designsystem.component.PillTabs
import com.tsutsen.platformplayer.core.designsystem.component.expressiveClickable
import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTokens
import com.tsutsen.platformplayer.core.designsystem.theme.LocalSurfaceSkins
import com.tsutsen.platformplayer.core.designsystem.theme.SurfaceKeys
import com.tsutsen.platformplayer.core.designsystem.theme.SurfaceSkin
import com.tsutsen.platformplayer.core.designsystem.theme.ThemeEngine
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import com.tsutsen.platformplayer.core.designsystem.theme.spatialSpec
import com.tsutsen.platformplayer.core.designsystem.theme.surfaceBorder
import com.tsutsen.platformplayer.core.designsystem.theme.surfaceColor
import com.tsutsen.platformplayer.core.designsystem.theme.surfaceContentColor
import com.tsutsen.platformplayer.core.designsystem.theme.surfaceOutline
import com.tsutsen.platformplayer.core.designsystem.theme.surfaceTexture
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Theme editor form: name, three key colors in one row (primary required,
 * secondary and tertiary optional), a swatch-style palette style selector,
 * and a compact light+dark preview of the generated scheme.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThemeEditor(
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
    var tab by remember(initial) { mutableStateOf(0) }
    var surfaceStyles by remember(initial) { mutableStateOf(initial?.surfaceStyles ?: emptyMap()) }
    // The id is generated up front so uploaded textures can be filed under it
    // before the theme is ever saved.
    val themeId = remember(initial) { initial?.id ?: UUID.randomUUID().toString() }
    var editSlot by remember { mutableStateOf<String?>(null) }

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
        // Same toggle-group chrome as the search tab's filters, at the very
        // top of the sheet.
        PillTabs(
            labels = listOf("Palette", "Custom"),
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.settings_theme_editor_theme_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (tab == 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceMd),
            ) {
                ThemeColorSlot(
                    label = stringResource(R.string.settings_theme_editor_primary),
                    color = primary,
                    optional = false,
                    modifier = Modifier.weight(1f),
                    onPick = { pickSlot = ThemeSlot.PRIMARY },
                    onClear = {},
                )
                ThemeColorSlot(
                    label = stringResource(R.string.settings_theme_editor_secondary),
                    color = secondary,
                    optional = true,
                    modifier = Modifier.weight(1f),
                    onPick = { pickSlot = ThemeSlot.SECONDARY },
                    onClear = { secondary = null },
                )
                ThemeColorSlot(
                    label = stringResource(R.string.settings_theme_editor_tertiary),
                    color = tertiary,
                    optional = true,
                    modifier = Modifier.weight(1f),
                    onPick = { pickSlot = ThemeSlot.TERTIARY },
                    onClear = { tertiary = null },
                )
                // Background (surfaces) is the fourth optional key color.
                ThemeColorSlot(
                    label = stringResource(R.string.settings_theme_editor_background),
                    color = background,
                    optional = true,
                    modifier = Modifier.weight(1f),
                    onPick = { pickSlot = ThemeSlot.BACKGROUND },
                    onClear = { background = null },
                )
            }

            Text(
                text = stringResource(R.string.settings_theme_editor_palette_style),
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

            Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.settings_theme_editor_contrast),
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

        } else {
            Text(
                text = stringResource(R.string.settings_theme_editor_per_surface_overrides_slots_left_empty_use_the_palette_schem),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSm),
            ) {
                SurfaceKeys.ALL.forEach { key ->
                    SurfaceStyleSlot(
                        label = SurfaceKeys.label(key),
                        style = surfaceStyles[key],
                        modifier = Modifier.weight(1f),
                        onPick = { editSlot = key },
                    )
                }
            }
        }

        ThemeComponentPreview(
            primary = primary,
            secondary = secondary,
            tertiary = tertiary,
            background = background,
            style = style,
            contrast = contrast,
            surfaceStyles = surfaceStyles,
        )

        Button(
            onClick = {
                onSave(
                    CustomTheme(
                        id = themeId,
                        name = name.ifBlank { "My theme" },
                        primary = primary,
                        secondary = secondary,
                        tertiary = tertiary,
                        background = background,
                        paletteStyle = style,
                        contrast = contrast,
                        surfaceStyles = surfaceStyles,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_theme_editor_save))
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
            title =
                when (slot) {
                    ThemeSlot.PRIMARY -> stringResource(R.string.settings_slot_primary)
                    ThemeSlot.SECONDARY -> stringResource(R.string.settings_slot_secondary)
                    ThemeSlot.TERTIARY -> stringResource(R.string.settings_slot_tertiary)
                    ThemeSlot.BACKGROUND -> stringResource(R.string.settings_slot_background)
                },
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

    editSlot?.let { key ->
        SurfaceSlotDialog(
            key = key,
            style = surfaceStyles[key],
            themeId = themeId,
            onUpdate = { style ->
                surfaceStyles =
                    if (style == null) surfaceStyles - key else surfaceStyles + (key to style)
            },
            onDismiss = { editSlot = null },
        )
    }
}

/**
 * One key color as a tappable swatch: circle + label under it. The plus icon
 * reads as an add (pick a color) when the slot is empty and turns 45° into
 * an X when filled, so tapping clears it; the required (primary) slot has no
 * clear state and always opens the picker.
 */
@Composable
internal fun ThemeColorSlot(
    label: String,
    color: Int?,
    optional: Boolean,
    modifier: Modifier = Modifier,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // Keeps the last filled color so the fade-out has something to fade.
    var shownColor by remember { mutableStateOf(color) }
    LaunchedEffect(color) { color?.let { shownColor = it } }
    val filled = color != null
    // The X state only exists on optional slots; primary stays a plain plus.
    val clearable = filled && optional
    val fillAlpha by
        animateFloatAsState(
            targetValue = if (filled) 1f else 0f,
            animationSpec = spatialSpec<Float>(),
            label = "slot-fill",
        )
    val plusScale by
        animateFloatAsState(
            targetValue = if (clearable || !filled) 1f else 0f,
            animationSpec = spatialSpec<Float>(),
            label = "slot-plus-scale",
        )
    val plusRotation by
        animateFloatAsState(
            targetValue = if (clearable) 45f else 0f,
            animationSpec = spatialSpec<Float>(),
            label = "slot-plus-rotation",
        )
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs),
    ) {
        Box(
            modifier =
                Modifier
                    .size(Tokens.SwatchLg)
                    .expressiveClickable(
                        onClick = { if (clearable) onClear() else onPick() },
                    )
                    .clip(CircleShape)
                    .background(scheme.surfaceContainerHighest)
                    .background((shownColor?.toColor() ?: Color.Transparent).copy(alpha = fillAlpha)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = if (clearable) "Remove $label color" else "Pick $label color",
                tint = scheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .size(Tokens.IconMd)
                        .scale(plusScale)
                        .rotate(plusRotation),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

/**
 * Swatch square for the palette style selector: a 2x2 grid of primary |
 * secondary over tertiary | surface. Surface is the divergent cell — styles
 * treat neutrals differently (e.g. expressive shifts neutral hue, vibrant
 * raises neutral chroma) while the key colors anchor the comparison.
 * The swatches fill the square edge to edge; selection grows a 2dp primary
 * ring INTO the square (the ring is the padding, so no background peeks
 * behind it) while the corner rounding morphs.
 */
@Composable
internal fun PaletteSwatchSquare(
    scheme: ColorScheme,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val r = BluejayTokens().radius
    val ring by
        animateFloatAsState(
            targetValue = if (selected) Tokens.StrokeEmphasized.value else 0f,
            animationSpec = spatialSpec<Float>(),
            label = "swatch-ring",
        )
    val outerCorner by
        animateFloatAsState(
            targetValue = if (selected) r.md.value else r.lg.value,
            animationSpec = spatialSpec<Float>(),
            label = "swatch-corner",
        )
    val ringColor by
        animateColorAsState(
            targetValue =
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
            animationSpec = tween(200, easing = FastOutSlowInEasing),
            label = "swatch-ring-color",
        )
    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .expressiveClickable(onClick = onClick)
                // spring overshoot: corner/padding must stay non-negative
                .clip(RoundedCornerShape(outerCorner.coerceAtLeast(0f).dp))
                .background(ringColor)
                .padding(ring.coerceAtLeast(0f).dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape((outerCorner - ring).coerceAtLeast(0f).dp)),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(scheme.primary),
                    )
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(scheme.secondary),
                    )
                }
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(scheme.tertiary),
                    )
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(scheme.surface),
                    )
                }
            }
        }
    }
}

internal enum class ThemeSlot { PRIMARY, SECONDARY, TERTIARY, BACKGROUND }


internal fun Int.toColor(): Color = Color(this)

/** Texture preview for a slot circle, decoded small. */
@Composable
internal fun rememberTextureThumbnail(path: String): ImageBitmap? =
    remember(path) {
        runCatching {
            BitmapFactory
                .decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 4 })
                ?.asImageBitmap()
        }.getOrNull()
    }

/**
 * One surface slot in the theme editor's Custom tab: circle + label, filled
 * with the slot's custom color (or a texture thumbnail); the plus icon reads
 * as "pick a color / texture".
 */
@Composable
internal fun SurfaceStyleSlot(
    label: String,
    style: SurfaceStyle?,
    modifier: Modifier = Modifier,
    onPick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val texture = style?.texturePath?.let { rememberTextureThumbnail(it) }
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
                    .background(scheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            val slotColor = style?.colorArgb
            when {
                texture != null ->
                    Image(
                        bitmap = texture,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                slotColor != null ->
                    Box(Modifier.fillMaxSize().background(Color(slotColor), CircleShape))
                else ->
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(R.string.settings_pick_1_s_style, label),
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(Tokens.IconMd),
                    )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One surface slot's editor, as a 4-page carousel: Background / Outline /
 * Text / Texture. Saving an all-default style removes the slot override.
 * Textures are copied into app storage under [themeId] so they survive
 * restarts.
 */
@Composable
internal fun SurfaceSlotDialog(
    key: String,
    style: SurfaceStyle?,
    themeId: String,
    onUpdate: (SurfaceStyle?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var colorArgb by remember { mutableStateOf(style?.colorArgb) }
    var outlineColorArgb by remember { mutableStateOf(style?.outlineColorArgb) }
    var outlineWidth by remember { mutableStateOf(style?.outlineWidthDp ?: 0f) }
    var textColorArgb by remember { mutableStateOf(style?.textColorArgb) }
    var texturePath by remember { mutableStateOf(style?.texturePath) }
    val texture = texturePath?.let { rememberTextureThumbnail(it) }

    val texPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        val dir = File(File(context.filesDir, "theme_textures"), themeId)
                        dir.mkdirs()
                        val file = File(dir, "$key.jpg")
                        val bmp =
                            context.contentResolver.openInputStream(uri)?.use {
                                BitmapFactory.decodeStream(it)
                            }
                        if (bmp != null) {
                            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                            texturePath = file.absolutePath
                        }
                    }
                }
            }
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${SurfaceKeys.label(key)} style") },
        text = {
            Column {
                val pagerState = rememberPagerState { 4 }
                // Page indicator + switcher (the dots alone were too subtle).
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf("Background", "Outline", "Text", "Texture").forEachIndexed { i, label ->
                        val selected = pagerState.currentPage == i
                        TextButton(onClick = { scope.launch { pagerState.animateScrollToPage(i) } }) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color =
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                HorizontalPager(state = pagerState, modifier = Modifier.height(310.dp)) { page ->
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Tokens.SpaceXs),
                        verticalArrangement = Arrangement.spacedBy(Tokens.SpaceSm),
                    ) {
                        when (page) {
                            0 -> {
                                Text(stringResource(R.string.settings_container_color), style = MaterialTheme.typography.labelLarge)
                                SwatchRow(selected = colorArgb, onSelect = { colorArgb = it })
                            }
                            1 -> {
                                Text(stringResource(R.string.settings_outline_color), style = MaterialTheme.typography.labelLarge)
                                SwatchRow(selected = outlineColorArgb, onSelect = { outlineColorArgb = it })
                                Spacer(Modifier.height(Tokens.SpaceSm))
                                Text(stringResource(R.string.settings_outline_width), style = MaterialTheme.typography.labelLarge)
                                Row(horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSm)) {
                                    listOf("None" to 0f, "1 dp" to 1f, "2 dp" to 2f, "3 dp" to 3f).forEach { (label, width) ->
                                        TextButton(onClick = { outlineWidth = width }) {
                                            Text(
                                                label,
                                                color =
                                                    if (outlineWidth == width) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                            2 -> {
                                Text(stringResource(R.string.settings_text_color), style = MaterialTheme.typography.labelLarge)
                                Text(
                                    "Titles and icons on this surface.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                SwatchRow(selected = textColorArgb, onSelect = { textColorArgb = it })
                            }
                            else -> {
                                Text(stringResource(R.string.settings_texture), style = MaterialTheme.typography.labelLarge)
                                Text(
                                    "Tiles edge to edge over the container color.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(Tokens.SpaceSm))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSm),
                                ) {
                                    if (texture != null) {
                                        Image(
                                            bitmap = texture,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(Tokens.AvatarLg).clip(MaterialTheme.shapes.small),
                                        )
                                    }
                                    TextButton(onClick = { texPicker.launch("image/*") }) { Text(stringResource(R.string.settings_theme_editor_upload)) }
                                    if (texturePath != null) {
                                        TextButton(onClick = { texturePath = null }) { Text(stringResource(R.string.settings_theme_editor_remove)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val s =
                    if (colorArgb == null && outlineColorArgb == null && outlineWidth <= 0f && texturePath == null && textColorArgb == null) {
                        null
                    } else {
                        SurfaceStyle(
                            colorArgb = colorArgb,
                            outlineColorArgb = outlineColorArgb,
                            outlineWidthDp = outlineWidth.takeIf { it > 0f },
                            texturePath = texturePath,
                            textColorArgb = textColorArgb,
                        )
                    }
                onUpdate(s)
                onDismiss()
            }) { Text(stringResource(R.string.settings_theme_editor_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}

/** Free-form picker (featured presets, SV square, hue, hex + live swatch). */
@Composable
internal fun SwatchRow(
    selected: Long?,
    onSelect: (Long?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceSm)) {
        ColorPicker(
            initialColor = (selected ?: PresetColorsFeatured.first()).toInt(),
            onColorChange = { onSelect(it.toLong() and 0xFFFFFFFFL) },
        )
    }
}

/**
 * Small live preview of how the theme looks ON COMPONENTS: a mini video
 * card, settings row, option tile and sheet strip, rendered with the
 * generated scheme + the theme's surface slots. Light and dark side by side.
 */
@Composable
internal fun ThemeComponentPreview(
    primary: Int,
    secondary: Int?,
    tertiary: Int?,
    background: Int?,
    style: PaletteStyle,
    contrast: Float,
    surfaceStyles: Map<String, SurfaceStyle>,
) {
    val schemes =
        remember(primary, secondary, tertiary, background, style, contrast) {
            ThemeEngine.generate(primary, secondary, tertiary, style, background = background, contrast = contrast)
        }
    val skins =
        remember(surfaceStyles) {
            surfaceStyles.mapValues { (_, st) ->
                SurfaceSkin(
                    color = st.colorArgb?.let { Color(it) },
                    outlineColor = st.outlineColorArgb?.let { Color(it) },
                    outlineWidth = st.outlineWidthDp?.let { it.dp },
                    texture = st.texturePath?.let { decodeTextureBitmap(it) },
                    contentColor = st.textColorArgb?.let { Color(it) },
                )
            }
        }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSm)) {
        ComponentPreviewCard("Light", schemes.light, skins, Modifier.weight(1f))
        ComponentPreviewCard("Dark", schemes.dark, skins, Modifier.weight(1f))
    }
}

internal fun decodeTextureBitmap(path: String): ImageBitmap? =
    runCatching {
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap()
    }.getOrNull()

@Composable
internal fun ComponentPreviewCard(
    title: String,
    scheme: ColorScheme,
    skins: Map<String, SurfaceSkin>,
    modifier: Modifier = Modifier,
) {
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalSurfaceSkins provides skins) {
            Surface(color = scheme.surface, shape = MaterialTheme.shapes.medium, modifier = modifier) {
                Column(
                    modifier = Modifier.padding(Tokens.SpaceMd),
                    verticalArrangement = Arrangement.spacedBy(Tokens.SpaceSm),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    // Mini video card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = surfaceColor(SurfaceKeys.VIDEO_CARD, scheme.surface)),
                        shape = RoundedCornerShape(6.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = surfaceBorder(SurfaceKeys.VIDEO_CARD),
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .surfaceTexture(SurfaceKeys.VIDEO_CARD)
                                    .padding(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(30.dp)
                                        .background(scheme.surfaceContainerHighest, RoundedCornerShape(4.dp)),
                            )
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth(0.75f)
                                        .height(5.dp)
                                        .background(surfaceContentColor(SurfaceKeys.VIDEO_CARD, scheme.onSurface).copy(alpha = 0.7f), RoundedCornerShape(3.dp)),
                            )
                        }
                    }
                    // Mini settings row
                    Card(
                        colors = CardDefaults.cardColors(containerColor = surfaceColor(SurfaceKeys.SETTINGS_ROW, scheme.surfaceContainer)),
                        shape = RoundedCornerShape(6.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = surfaceBorder(SurfaceKeys.SETTINGS_ROW),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(Modifier.size(14.dp).background(scheme.primary, CircleShape))
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(5.dp)
                                    .background(surfaceContentColor(SurfaceKeys.SETTINGS_ROW, scheme.onSurface).copy(alpha = 0.7f), RoundedCornerShape(3.dp)),
                            )
                            Box(Modifier.size(12.dp).background(scheme.primaryContainer, CircleShape))
                        }
                    }
                    // Mini tile + sheet strip
                    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSm)) {
                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(surfaceColor(SurfaceKeys.TILE, scheme.surfaceContainer))
                                    .surfaceTexture(SurfaceKeys.TILE)
                                    .surfaceOutline(SurfaceKeys.TILE, RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.size(12.dp).background(surfaceContentColor(SurfaceKeys.TILE, scheme.primary), CircleShape))
                                Box(
                                    Modifier
                                        .width(24.dp)
                                        .height(4.dp)
                                        .background(surfaceContentColor(SurfaceKeys.TILE, scheme.onSurface).copy(alpha = 0.7f), RoundedCornerShape(2.dp)),
                                )
                            }
                        }
                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(surfaceColor(SurfaceKeys.SHEET, scheme.surfaceContainerLow))
                                    .surfaceTexture(SurfaceKeys.SHEET)
                                    .padding(8.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(0.6f)
                                        .height(5.dp)
                                        .background(surfaceContentColor(SurfaceKeys.SHEET, scheme.onSurface).copy(alpha = 0.7f), RoundedCornerShape(3.dp)),
                                )
                                Box(
                                    Modifier
                                        .fillMaxWidth(0.4f)
                                        .height(5.dp)
                                        .background(surfaceContentColor(SurfaceKeys.SHEET, scheme.onSurface).copy(alpha = 0.45f), RoundedCornerShape(3.dp)),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
