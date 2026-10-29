package com.tsutsen.platformplayer.feature.player.impl.gesture

import android.app.Activity
import android.content.Context
import com.tsutsen.platformplayer.feature.player.impl.PipSurface
import com.tsutsen.platformplayer.feature.player.impl.PlayerUiState
import com.tsutsen.platformplayer.feature.player.impl.PlayerViewModel
import com.tsutsen.platformplayer.feature.player.impl.SystemControls

/**
 * [GestureEffects] backed by the player view model, system brightness/volume,
 * and the surface's morph-drag callbacks.
 */
class PlayerGestureEffects(
    private val viewModel: PlayerViewModel,
    private val context: Context,
    private val activity: Activity? = null,
    private val screenHeight: () -> Float,
    private val onMorphDragStart: () -> Unit = {},
    private val onMorphDrag: (dragY: Float) -> Unit = {},
    private val onMorphDragEnd: (dragY: Float, velocityPxPerMs: Float) -> Unit = { _, _ -> },
    private val onFullscreenDragStart: () -> Unit = {},
    private val onFullscreenDrag: (dragY: Float) -> Unit = {},
    private val onFullscreenDragEnd: (dragY: Float, velocityPxPerMs: Float) -> Unit = { _, _ -> },
    private val onShrinkDragStart: () -> Unit = {},
    private val onShrinkDrag: (dragY: Float) -> Unit = {},
    private val onShrinkDragEnd: (dragY: Float, velocityPxPerMs: Float) -> Unit = { _, _ -> },
) : GestureEffects {
    private fun loadedState(): PlayerUiState.Loaded? = viewModel.uiState.value as? PlayerUiState.Loaded

    override fun seekBy(deltaMs: Long) = viewModel.seekBy(deltaMs)

    override fun seekTo(positionMs: Long) = viewModel.seekTo(positionMs)

    override fun positionMs(): Long = viewModel.currentPositionMs()

    override fun durationMs(): Long = loadedState()?.durationMs ?: 0L

    override fun isPlaying(): Boolean = loadedState()?.isPlaying ?: false

    override fun pause() = viewModel.pause()

    override fun resume() = viewModel.resume()

    override fun playbackSpeed(): Float = loadedState()?.playbackSpeed ?: 1f

    override fun setPlaybackSpeed(speed: Float) = viewModel.setPlaybackSpeed(speed)

    override fun defaultSpeedup(): Float = viewModel.defaultSpeedup

    override fun speedupSensitivity(): Float = viewModel.speedupSensitivity

    override fun jumpStepMs(): Long = viewModel.jumpStepSeconds * 1000L

    override fun screenHeightPx(): Float = screenHeight()

    override fun brightness(): Float = SystemControls.readBrightness(context)

    override fun applyBrightness(level: Float) =
        SystemControls.applyBrightness(context, level, activity?.window)

    override fun volume(): Float = SystemControls.getVolume(context)

    override fun setVolume(level: Float) = SystemControls.setVolume(context, level)

    override fun minimize() = viewModel.minimize()

    override fun toggleFullscreen() = viewModel.toggleFullscreen()

    override fun exitFullscreen() = viewModel.exitFullscreen()

    override fun enterPip() {
        PipSurface.enterPip?.invoke()
    }

    override fun morphDragStart() = onMorphDragStart()

    override fun morphDrag(dragY: Float) = onMorphDrag(dragY)

    override fun morphDragEnd(dragY: Float, velocityPxPerMs: Float) = onMorphDragEnd(dragY, velocityPxPerMs)

    override fun fullscreenDragStart() = onFullscreenDragStart()

    override fun fullscreenDrag(dragY: Float) = onFullscreenDrag(dragY)

    override fun fullscreenDragEnd(dragY: Float, velocityPxPerMs: Float) =
        onFullscreenDragEnd(dragY, velocityPxPerMs)

    override fun shrinkDragStart() = onShrinkDragStart()

    override fun shrinkDrag(dragY: Float) = onShrinkDrag(dragY)

    override fun shrinkDragEnd(dragY: Float, velocityPxPerMs: Float) =
        onShrinkDragEnd(dragY, velocityPxPerMs)
}
