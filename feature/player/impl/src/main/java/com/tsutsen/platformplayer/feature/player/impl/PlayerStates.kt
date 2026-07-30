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
    val toggle: () -> Unit,
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
        toggle = {
            cancelSchedule()
            isVisible = !isVisible
            if (isVisible) scheduleHide()
        },
        notifyInteraction = {
            cancelSchedule()
            isVisible = true
            scheduleHide()
        },
    )
}
