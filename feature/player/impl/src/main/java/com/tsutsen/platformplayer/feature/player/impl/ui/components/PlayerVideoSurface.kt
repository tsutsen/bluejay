package com.tsutsen.platformplayer.feature.player.impl

import android.content.Context
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * PlayerView subclass that unconditionally refuses to claim touch input.
 *
 * `isClickable = false` / `isFocusable = false` on the outer PlayerView are not enough:
 * once a video track loads, PlayerView attaches an internal SurfaceView (inside
 * ContentFrameLayout/AspectRatioFrameLayout) for rendering. That child is hit-tested by
 * the platform's normal ViewGroup#dispatchTouchEvent *before* Compose's pointerInput
 * pipeline on sibling composables (e.g. the playerGesture-modified control layer drawn on
 * top in PlayerContent's Box) gets a chance to see the event - regardless of Compose z-order.
 * Before a frame is available there's nothing to intercept, which is why this only
 * manifested "once the video loads."
 *
 * Overriding dispatchTouchEvent to always return false makes this guarantee explicit and
 * immune to whatever PlayerView does internally (SurfaceView, subtitle overlay, controller
 * auto-show, etc.) - it never consumes ACTION_DOWN, so the event is free to propagate to
 * the Compose gesture layer above it.
 */
private class TouchTransparentPlayerView(context: Context) : PlayerView(context) {
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean = false
}

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
            TouchTransparentPlayerView(ctx).apply {
                useController = false
                setControllerAutoShow(false)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                // Belt-and-suspenders: these no longer do the real work (dispatchTouchEvent
                // override above does), but kept so nothing inside PlayerView tries to grab
                // focus/click state either.
                isClickable = false
                isFocusable = false
                isFocusableInTouchMode = false
            }
        },
        update = { view -> view.player = player },
        modifier = modifier
    )
}
