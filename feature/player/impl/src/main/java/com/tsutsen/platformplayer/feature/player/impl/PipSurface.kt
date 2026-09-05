package com.tsutsen.platformplayer.feature.player.impl

import android.view.View
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The SurfaceView of the currently composed player surface. The PiP branch
 * swap (MainActivity renders the video-only PlayerView instead of the app
 * tree while in PiP) means exactly one video surface exists at a time.
 *
 * Feeds the Jetpack PiP delegate's `setPlayerView`, which tracks the view's
 * bounds as the sourceRectHint for the enter/exit animations.
 */
object PipSurface {
    val surfaceView = MutableStateFlow<View?>(null)
}
