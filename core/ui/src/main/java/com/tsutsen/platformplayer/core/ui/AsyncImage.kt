package com.tsutsen.platformplayer.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.tsutsen.platformplayer.core.datastore.model.TintBlendMode
import coil.compose.AsyncImage as CoilAsyncImage

/**
 * User thumbnail tint (Settings > Appearance > Thumbnails > Tint), provided
 * at the theme roots. Null = no overlay. The color is used opaque: how much
 * of it mixes in is [LocalThumbnailTintStrength], and how it combines is
 * [LocalThumbnailTintBlendMode].
 */
val LocalThumbnailTint = compositionLocalOf<Color?> { null }

/** Tint strength 0..1 (0 = no tint effect), provided at the theme roots. */
val LocalThumbnailTintStrength = compositionLocalOf { 0.35f }

/** Tint blend mode ([TintBlendMode] name), provided at the theme roots. */
val LocalThumbnailTintBlendMode = compositionLocalOf { TintBlendMode.WASH.name }

/** Thumbnail desaturation (1 = full color, 0 = grayscale), provided at the theme roots. */
val LocalThumbnailSaturation = compositionLocalOf { 1f }

/** Thumbnail contrast (1 = neutral, 0 = flat gray, 2 = high contrast), provided at the theme roots. */
val LocalThumbnailContrast = compositionLocalOf { 1f }

/** Maps a [TintBlendMode] name to its draw blend mode; unknown → wash. */
fun thumbnailTintBlend(name: String): BlendMode =
    when (name) {
        TintBlendMode.MULTIPLY.name -> BlendMode.Multiply
        TintBlendMode.SCREEN.name -> BlendMode.Screen
        TintBlendMode.OVERLAY.name -> BlendMode.Overlay
        TintBlendMode.SOFT_LIGHT.name -> BlendMode.Softlight
        TintBlendMode.COLOR_BURN.name -> BlendMode.ColorBurn
        else -> BlendMode.SrcOver
    }

/**
 * Draws the tint over the (already drawn) image with the requested blend
 * mode and strength. Placed on the image's own modifier so the blend
 * resolves against the image pixels; thumbnails decode opaque and use
 * ContentScale.Crop, so the rect always sits over image content.
 */
fun Modifier.thumbnailTintOverlay(
    tint: Color?,
    strength: Float,
    blend: BlendMode,
): Modifier {
    if (tint == null || strength <= 0f) return this
    val color = tint.copy(alpha = tint.alpha.coerceIn(0f, 1f) * strength.coerceIn(0f, 1f))
    return drawWithContent {
        drawContent()
        drawRect(color, blendMode = blend)
    }
}

/**
 * Composition-local-driven thumbnail color filter (desaturate + contrast),
 * for previews that render a local drawable instead of a Coil URL.
 */
@Composable
fun rememberThumbnailColorFilter(): ColorFilter? =
    combinedThumbnailFilter(
        saturation = LocalThumbnailSaturation.current,
        contrast = LocalThumbnailContrast.current,
    )

/**
 * Wrapper around Coil's AsyncImage with built-in placeholder and error handling.
 */
@Composable
fun AsyncImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: Painter? = null,
    error: Painter? = null,
    onIntrinsicSize: ((Int, Int) -> Unit)? = null,
) {
    if (url.isNullOrEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (placeholder != null) {
                androidx.compose.foundation.Image(
                    painter = placeholder,
                    contentDescription = contentDescription,
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
        return
    }

    // Remembered per URL so scrolling/recomposing doesn't rebuild the request
    // (and its listener) on every recomposition. Coil sizes the decode to the
    // canvas automatically, so no explicit request size is needed.
    // crossfade is intentionally NOT set: it keeps an extra bitmap per image,
    // which is pure overhead for scrolling thumbnails (enable it per-caller for
    // hero images if ever needed).
    val context = androidx.compose.ui.platform.LocalContext.current
    val request =
        remember(url, context, onIntrinsicSize) {
            ImageRequest
                .Builder(context)
                .data(url)
                .apply {
                    if (onIntrinsicSize != null) {
                        listener(
                            object : ImageRequest.Listener {
                                override fun onSuccess(
                                    request: ImageRequest,
                                    result: SuccessResult,
                                ) {
                                    onIntrinsicSize(
                                        result.drawable.intrinsicWidth,
                                        result.drawable.intrinsicHeight,
                                    )
                                }
                            },
                        )
                    }
                }.build()
        }

    CoilAsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier =
            modifier.thumbnailTintOverlay(
                tint = LocalThumbnailTint.current,
                strength = LocalThumbnailTintStrength.current,
                blend = thumbnailTintBlend(LocalThumbnailTintBlendMode.current),
            ),
        contentScale = contentScale,
        placeholder = placeholder,
        error = error,
        colorFilter = combinedThumbnailFilter(
            saturation = LocalThumbnailSaturation.current,
            contrast = LocalThumbnailContrast.current,
        ),
    )
}

/**
 * Per-pixel color work a ColorFilter can express, carried in one matrix:
 * desaturation first, then contrast (each channel scaled around the 128
 * mid-gray); the tint rides on top as a blend-mode overlay instead, because
 * Multiply/Screen/Overlay & co. have no matrix form. Null when a no-op.
 */
private fun combinedThumbnailFilter(
    saturation: Float,
    contrast: Float,
): ColorFilter? {
    val sat = saturation.coerceIn(0f, 1f)
    val con = contrast.coerceIn(0.5f, 2f)
    if (sat >= 0.999f && con in 0.999f..1.001f) return null
    val saturation = if (sat < 0.999f) ColorMatrix().also { it.setToSaturation(sat) } else null
    val contrast = if (con !in 0.999f..1.001f) contrastMatrix(con) else null
    // Compose's ColorMatrix has no concat helpers (android.graphics does) —
    // multiply the 4x5 matrices here: contrast runs AFTER saturation, so the
    // contrast matrix is "on top" of the saturation matrix.
    val combined = when {
        saturation == null -> contrast!!.values
        contrast == null -> saturation.values
        else -> concatColorMatrix(contrast.values, saturation.values)
    }
    return ColorFilter.colorMatrix(ColorMatrix(combined))
}

/** Row-major 4x5 multiply: [top] applied after [bottom]; returns a new 4x5 array. */
private fun concatColorMatrix(top: FloatArray, bottom: FloatArray): FloatArray {
    val out = FloatArray(20)
    for (r in 0..3) {
        for (c in 0..4) {
            var acc = 0f
            for (k in 0..3) acc += top[r * 5 + k] * bottom[k * 5 + c]
            out[r * 5 + c] = acc + top[r * 5 + 4] * (if (c == 4) 1f else 0f)
        }
    }
    return out
}

/** Contrast matrix: each RGB channel scaled by [contrast] around 128 mid-gray; alpha untouched. */
private fun contrastMatrix(contrast: Float): ColorMatrix =
    ColorMatrix(
        floatArrayOf(
            contrast, 0f, 0f, 0f, 128f * (1f - contrast),
            0f, contrast, 0f, 0f, 128f * (1f - contrast),
            0f, 0f, contrast, 0f, 128f * (1f - contrast),
            0f, 0f, 0f, 1f, 0f,
        ),
    )
