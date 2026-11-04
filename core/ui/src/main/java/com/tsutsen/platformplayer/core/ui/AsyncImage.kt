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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.compose.AsyncImage as CoilAsyncImage

/**
 * User thumbnail tint (Settings > Appearance > Thumbnail tint), provided at
 * the theme roots. Null = no overlay. The tint carries its own alpha, so
 * SrcOver reads as a translucent color wash over the image.
 */
val LocalThumbnailTint = compositionLocalOf<Color?> { null }

/** Thumbnail desaturation (1 = full color, 0 = grayscale), provided at the theme roots. */
val LocalThumbnailSaturation = compositionLocalOf { 1f }

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
        modifier = modifier,
        contentScale = contentScale,
        placeholder = placeholder,
        error = error,
        colorFilter = combinedThumbnailFilter(
            saturation = LocalThumbnailSaturation.current,
            tint = LocalThumbnailTint.current,
        ),
    )
}

/**
 * One filter combining desaturation + the tint wash (a single colorFilter
 * slot): result = wash(saturate(src)), both expressed as 4x5 color matrices.
 * Null when both are no-ops.
 */
private fun combinedThumbnailFilter(
    saturation: Float,
    tint: Color?,
): ColorFilter? {
    val sat = saturation.coerceIn(0f, 1f)
    if (sat >= 0.999f && tint == null) return null
    val s = ColorMatrix().apply { setToSaturation(sat) }
    if (tint == null) return ColorFilter.colorMatrix(s)
    val a = tint.alpha.coerceIn(0f, 1f)
    val wash =
        ColorMatrix(
            floatArrayOf(
                1f - a, 0f, 0f, 0f, a * tint.red * 255f,
                0f, 1f - a, 0f, 0f, a * tint.green * 255f,
                0f, 0f, 1f - a, 0f, a * tint.blue * 255f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
    // Combined = wash x sat (sat applied first): M[i][j] = Σk wash[i][k]*sat[k][j],
    // M[i][4] += wash[i][4].
    val out = FloatArray(20)
    for (i in 0 until 4) {
        for (j in 0 until 5) {
            var acc = wash[i, 4]
            for (k in 0 until 4) acc += wash[i, k] * s[k, j]
            out[i * 5 + j] = acc
        }
    }
    return ColorFilter.colorMatrix(ColorMatrix(out))
}
