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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ==================== Morph State ====================

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
    val snapTo: (target: Float) -> Unit,
    val cancelAnimation: () -> Unit,
)

@Composable
fun rememberMorphState(
    onMinimize: () -> Unit,
    config: PlayerMorphConfig = PlayerMorphConfig.Default,
): MorphState {
    val morphProgress = remember { Animatable(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var animJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val transitionSpringSpec = tween<Float>(
        durationMillis = 300,
        easing = FastOutSlowInEasing
    )

    fun launchAnimation(target: Float) {
        animJob?.cancel()
        animJob = scope.launch { morphProgress.animateTo(target, transitionSpringSpec) }
    }

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
                launchAnimation(0f)
            }
        },
        restore = { launchAnimation(0f) },
        animateTo = { target -> launchAnimation(target) },
        snapTo = { target ->
            animJob?.cancel()
            animJob = scope.launch { morphProgress.snapTo(target) }
        },
        cancelAnimation = { animJob?.cancel() },
    )
}

// ==================== Fullscreen State ====================

/**
 * State holder for fullscreen progress Animatable.
 * Encapsulates enter/exit fullscreen animation logic.
 */
data class FullscreenState(
    val progress: Float,
    val enterFullscreen: () -> Unit,
    val exitFullscreen: () -> Unit,
    val cancelAnimation: () -> Unit,
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
    var animJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun launchAnimation(target: Float) {
        animJob?.cancel()
        animJob = scope.launch { fullscreenProgress.animateTo(target, transitionSpringSpec) }
    }

    return FullscreenState(
        progress = fullscreenProgress.value,
        enterFullscreen = { launchAnimation(1f) },
        exitFullscreen = { launchAnimation(0f) },
        cancelAnimation = { animJob?.cancel() },
    )
}

// ==================== Auto-Hide State ====================

/**
 * State holder for the controls auto-hide timer.
 * Sole owner of hideControlsJob — eliminates the dual-writer race in PlayerView.kt.
 */
data class AutoHideState(
    val isVisible: Boolean,
    val hide: () -> Unit,
    val show: () -> Unit,
    val notifyInteraction: () -> Unit,
)

@Composable
fun rememberAutoHideState(
    autoHideMs: Long = 3000,
    initialState: Boolean = true,
): AutoHideState {
    var isVisible by remember { mutableStateOf(initialState) }
    var hideJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    fun scheduleHide() {
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(autoHideMs)
            isVisible = false
        }
    }

    fun cancelSchedule() {
        hideJob?.cancel()
        hideJob = null
    }

    return AutoHideState(
        isVisible = isVisible,
        hide = {
            cancelSchedule()
            isVisible = false
        },
        show = {
            cancelSchedule()
            isVisible = true
        },
        notifyInteraction = {
            cancelSchedule()
            isVisible = true
            scheduleHide()
        },
    )
}
