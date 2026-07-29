package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
