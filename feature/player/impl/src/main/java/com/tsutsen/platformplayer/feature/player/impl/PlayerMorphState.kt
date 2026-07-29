package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

/**
 * State holder for morph progress Animatable and drag callbacks.
 * Encapsulates the minimize/restore animation logic.
 */
data class MorphState(
    val progress: Float,
    val isDragging: Boolean,
    val startDrag: () -> Unit,
    val drag: (dragY: Float, dragTravelPx: Float) -> Unit,
    val endDrag: (dragY: Float, dragTravelPx: Float) -> Unit,
    val restore: () -> Unit,
    val animateTo: (target: Float) -> Unit,
)

@Composable
fun rememberMorphState(
    onMinimize: () -> Unit,
    config: PlayerMorphConfig = PlayerMorphConfig.Default,
): MorphState {
    val morphProgress = remember { Animatable(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val transitionSpringSpec = tween<Float>(
        durationMillis = 300,
        easing = FastOutSlowInEasing
    )

    return MorphState(
        progress = morphProgress.value,
        isDragging = isDragging,
        startDrag = { isDragging = true },
        drag = { dragY, dragTravelPx ->
            val progress = (dragY / dragTravelPx).coerceIn(0f, 1f)
            scope.launch { morphProgress.snapTo(progress) }
        },
        endDrag = { dragY, dragTravelPx ->
            isDragging = false
            val progress = (dragY / dragTravelPx).coerceIn(0f, 1f)
            if (progress > config.morphSettleThreshold) {
                onMinimize()
            } else {
                scope.launch { morphProgress.animateTo(0f, transitionSpringSpec) }
            }
        },
        restore = {
            scope.launch { morphProgress.animateTo(0f, transitionSpringSpec) }
        },
        animateTo = { target ->
            scope.launch { morphProgress.animateTo(target, transitionSpringSpec) }
        },
    )
}
