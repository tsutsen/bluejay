package com.tsutsen.platformplayer.feature.player.impl

import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * The single ExoPlayer video surface, shared by NORMAL, COMPACT, FULLSCREEN, and FLOATING.
 *
 * The previous PlayerScreen had 2+ byte-for-byte copies of this exact AndroidView factory
 * (one for fullscreen, one for the windowed/detail-page player) and FLOATING was missing
 * one entirely - the mini player rendered its drag/controls box with no video underneath.
 * Extracting this fixes that gap for free: FloatingPlayerContent now just calls this too.
 */
@Composable
fun PlayerVideoSurface(
    player: ExoPlayer?,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                setControllerAutoShow(false)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { view -> view.player = player },
        modifier = modifier
    )
}
