package com.tsutsen.platformplayer.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.compose.AsyncImage as CoilAsyncImage

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
    )
}
