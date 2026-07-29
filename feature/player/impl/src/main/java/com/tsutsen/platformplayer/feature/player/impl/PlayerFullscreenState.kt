package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/**
 * State holder for fullscreen progress Animatable.
 * Encapsulates enter/exit fullscreen animation logic.
 */
data class FullscreenState(
    val progress: Float,
    val enterFullscreen: () -> Unit,
    val exitFullscreen: () -> Unit,
)

@Composable
fun rememberFullscreenState(
    config: PlayerMorphConfig = PlayerMorphConfig.Default,
): FullscreenState {
    val fullscreenProgress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val transitionSpringSpec = tween<Float>(
        durationMillis = 300,
        easing = FastOutSlowInEasing
    )

    return FullscreenState(
        progress = fullscreenProgress.value,
        enterFullscreen = {
            scope.launch { fullscreenProgress.animateTo(1f, transitionSpringSpec) }
        },
        exitFullscreen = {
            scope.launch { fullscreenProgress.animateTo(0f, transitionSpringSpec) }
        },
    )
}
