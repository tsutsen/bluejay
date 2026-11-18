package com.tsutsen.platformplayer.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTokens
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import com.tsutsen.platformplayer.core.ui.R

/**
 * Hand-picked featured presets shown as a prominent circle row at the top
 * of [ColorPicker] — the app's curated accent palette (one tap picks).
 */
val PresetColorsFeatured: List<Int> =
    listOf(
        0xFFD96C47.toInt(),
        0xFF7A9E7E.toInt(),
        0xFF5B7C99.toInt(),
        0xFF8E5572.toInt(),
        0xFFD9A441.toInt(),
        0xFF3F8E8B.toInt(),
        0xFF4A4A55.toInt(),
    )

/**
 * Free-form color picker: featured preset row, saturation/value square,
 * hue slider and a hex entry field, top to bottom. Works in opaque ARGB
 * ints ([initialColor] in, [onColorChange] out — alpha is preserved as
 * given; the picker itself always emits fully opaque colors).
 *
 * Internal HSV state is derived once from [initialColor]; every gesture
 * mutation recomputes the ARGB and calls [onColorChange]. The callback's
 * result is never fed back into the HSV state, so there are no echo loops.
 */
@Composable
fun ColorPicker(
    initialColor: Int,
    onColorChange: (Int) -> Unit,
) {
    // Single source of truth: [hue, saturation, value].
    var hsv by remember {
        mutableStateOf(FloatArray(3).also { android.graphics.Color.colorToHSV(initialColor, it) })
    }
    // Re-derive ONLY when the caller changes initialColor EXTERNALLY (preset
    // tap in the parent, dialog reopen). Colors we emitted ourselves come
    // back as the new initialColor (the parent keeps a draft) — re-deriving
    // from those is a bug: gray/near-black ARGBs degenerate to hue 0 in
    // colorToHSV, so the hue would jitter and drift to zero when dragging
    // into the low-saturation/low-value corner of the SV square.
    var lastEmitted by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(initialColor) {
        if (initialColor != lastEmitted) {
            hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(initialColor, it) }
        }
    }

    fun commit(s: Float, v: Float, h: Float = hsv[0]) {
        val next = floatArrayOf(h, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))
        hsv = next
        val argb = android.graphics.Color.HSVToColor(next)
        lastEmitted = argb
        onColorChange(argb)
    }

    Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceMd)) {
        FeaturedPresetRow(
            current = android.graphics.Color.HSVToColor(hsv),
            onPick = { argb ->
                hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(argb, it) }
                lastEmitted = argb
                onColorChange(argb)
            },
        )
        SvSquare(
            hsv = hsv,
            onChange = { s, v -> commit(s, v) },
        )
        HueSlider(
            hue = hsv[0],
            onHueChange = { h -> commit(hsv[1], hsv[2], h) },
        )
        // Live swatch beside the hex field: shows the exact committed color.
        Row(
            horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(Tokens.SwatchLg)
                        .clip(CircleShape)
                        .background(Color(android.graphics.Color.HSVToColor(hsv)))
                        .border(Tokens.SpaceXxs, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
            Box(modifier = Modifier.weight(1f)) {
                ColorPickerHexField(
                    color = android.graphics.Color.HSVToColor(hsv),
                    onCommit = { argb ->
                        hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(argb, it) }
                        lastEmitted = argb
                        onColorChange(argb)
                    },
                )
            }
        }
    }
}

/** The 7 featured presets as prominent circles (tap selects). */
@Composable
private fun FeaturedPresetRow(
    current: Int,
    onPick: (Int) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PresetColorsFeatured.forEach { argb ->
            val color = Color(argb)
            Box(
                modifier =
                    Modifier
                        .size(Tokens.SwatchLg)
                        .expressiveClickable(onClick = { onPick(argb) })
                        .clip(CircleShape)
                        .background(color)
                        // Subtle ring so light presets read on light surfaces.
                        .border(Tokens.SpaceXxs, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (argb == current) {
                    Box(
                        modifier =
                            Modifier
                                .size(Tokens.SwatchSm)
                                .clip(CircleShape)
                                .border(Tokens.StrokeEmphasized, MaterialTheme.colorScheme.onSurface, CircleShape),
                    )
                }
            }
        }
    }
}

private const val THUMB_RADIUS_DP = 7f
private const val THUMB_STROKE_DP = 3f

/** White ring thumb shared by the SV square and the hue slider. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRingThumb(center: Offset) {
    drawCircle(
        color = Color.White,
        radius = THUMB_RADIUS_DP.dp.toPx(),
        center = center,
        style = Stroke(width = THUMB_STROKE_DP.dp.toPx(), cap = StrokeCap.Round),
    )
}

/**
 * Outer reach of the ring (radius + half stroke). DrawScope is a Density,
 * so this resolves px directly inside a canvas lambda.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.thumbInsetPx(): Float =
    (THUMB_RADIUS_DP + THUMB_STROKE_DP / 2f).dp.toPx()

/** Saturation (x) / value (y) square, ~96dp, with a draggable ring thumb. */
@Composable
private fun SvSquare(
    hsv: FloatArray,
    onChange: (s: Float, v: Float) -> Unit,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val hueColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f)))
    Canvas(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(96.dp)
                .onSizeChanged { size = it }
                .clip(RoundedCornerShape(BluejayTokens().radius.sm))
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        if (size.width > 0 && size.height > 0) {
                            onChange(pos.x / size.width, 1f - pos.y / size.height)
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        if (size.width > 0 && size.height > 0) {
                            onChange(change.position.x / size.width, 1f - change.position.y / size.height)
                        }
                    }
                },
    ) {
        // White -> pure hue across x (saturation), transparent -> black down y (value).
        drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val pad = thumbInsetPx()
        val x = pad + hsv[1].coerceIn(0f, 1f) * (this.size.width - 2 * pad)
        val y = pad + (1f - hsv[2].coerceIn(0f, 1f)) * (this.size.height - 2 * pad)
        drawRingThumb(Offset(x, y))
    }
}

/** Rainbow hue slider, ~24dp tall, rounded track, ring thumb. */
@Composable
private fun HueSlider(
    hue: Float,
    onHueChange: (Float) -> Unit,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val rainbow =
        listOf(
            Color.Red, Color(0xFFFFFF00), Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red,
        )
    Canvas(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .onSizeChanged { size = it }
                .clip(RoundedCornerShape(BluejayTokens().radius.sm))
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        if (size.width > 0) onHueChange(((pos.x / size.width) * 360f).coerceIn(0f, 360f))
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        if (size.width > 0) onHueChange(((change.position.x / size.width) * 360f).coerceIn(0f, 360f))
                    }
                },
    ) {
        drawRoundRect(
            brush = Brush.horizontalGradient(rainbow),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
        )
        val pad = thumbInsetPx()
        val x = pad + (hue / 360f).coerceIn(0f, 1f) * (this.size.width - 2 * pad)
        drawRingThumb(Offset(x, this.size.height / 2f))
    }
}

/**
 * Compact `#RRGGBB` entry. Text is refreshed from the current color only
 * while the field is NOT focused, so typing isn't clobbered by live
 * updates from the square/slider. A valid 6-digit hex commits immediately.
 */
@Composable
private fun ColorPickerHexField(
    color: Int,
    onCommit: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(formatHex(color)) }
    var focused by remember { mutableStateOf(false) }

    LaunchedEffect(color) {
        if (!focused) text = formatHex(color)
    }

    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            text = raw.uppercase()
            val parsed = parseHex(raw)
            if (parsed != null) onCommit(parsed)
        },
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .onFocusChanged { focused = it.isFocused },
        singleLine = true,
        label = { Text(stringResource(R.string.picker_hex_label)) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { /* default IME action */ }),
        textStyle = MaterialTheme.typography.bodyMedium,
    )
}

private fun formatHex(color: Int): String =
    "#%06X".format(java.util.Locale.US, color and 0xFFFFFF)

/** Accepts an optional leading `#` plus exactly 6 hex digits. */
private fun parseHex(raw: String): Int? {
    val body = raw.trim().removePrefix("#")
    if (body.length != 6 || body.any { it.lowercaseChar() !in "0123456789abcdef" }) return null
    return 0xFF000000.toInt() or body.toInt(16)
}
