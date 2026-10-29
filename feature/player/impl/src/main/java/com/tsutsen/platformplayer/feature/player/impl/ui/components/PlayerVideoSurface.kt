package com.tsutsen.platformplayer.feature.player.impl

import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.tsutsen.platformplayer.core.data.repository.PlaybackEngine
import com.tsutsen.platformplayer.core.data.repository.PlaybackViewHost

/**
 * The single ExoPlayer video surface, shared by NORMAL, COMPACT, FULLSCREEN,
 * and FLOATING.
 *
 * The previous PlayerScreen had 2+ byte-for-byte copies of this exact
 * AndroidView factory (one for fullscreen, one for the windowed/detail-page
 * player) and FLOATING was missing one entirely - the mini player rendered
 * its drag/controls box with no video underneath. Extracting this fixes that
 * gap for free: FloatingPlayerContent now just calls this too.
 *
 * The underlying SurfaceView is also reported to [PipSurface.surfaceView] so
 * the Jetpack PiP delegate can track it (sourceRectHint).
 *
 * Binding goes through the engine-neutral [PlaybackEngine]/[PlaybackViewHost]
 * port; the Media3 cast stays in this feature-side host.
 */
@Composable
fun PlayerVideoSurface(
    playbackEngine: PlaybackEngine,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                setControllerAutoShow(false)
                // Captions are rendered by the Compose overlay (constant
                // font size, styleable) instead of the built-in view, whose
                // font size scales with the surface.
                subtitleView?.visibility = View.GONE
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
            }
        },
        update = { view ->
            playbackEngine.attach(Media3PlaybackViewHost(view))
            // Media3 1.9 has no public surface accessor: the SurfaceView is
            // an internal child. update() runs on every player-state tick
            // (10/s), so the reference is picked up as soon as the view
            // creates its surface, and a stale reference just stops
            // reporting once its view is detached — harmless.
            view.findSurfaceView()?.let { PipSurface.surfaceView.value = it }
        },
        modifier = modifier,
    )
}

/** Feature-side [PlaybackViewHost]: the only place the Media3 cast happens. */
private class Media3PlaybackViewHost(
    private val view: PlayerView,
) : PlaybackViewHost {
    override fun attachToHost(renderer: Any?) {
        view.player = renderer as? ExoPlayer
    }
}

/** Depth-first search for the (internal) SurfaceView inside a view tree. */
private fun View.findSurfaceView(): SurfaceView? {
    if (this is SurfaceView) return this
    if (this is ViewGroup) {
        for (i in 0 until childCount) {
            val found = getChildAt(i).findSurfaceView()
            if (found != null) return found
        }
    }
    return null
}
