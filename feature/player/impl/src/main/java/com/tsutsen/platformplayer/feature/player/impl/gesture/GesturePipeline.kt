package com.tsutsen.platformplayer.feature.player.impl.gesture

import com.tsutsen.platformplayer.core.datastore.model.PlayerGesturePreferences
import com.tsutsen.platformplayer.core.datastore.model.PlayerGestureSlotSet
import com.tsutsen.platformplayer.core.model.PlayerGestures
import com.tsutsen.platformplayer.feature.player.impl.PlayerOverlayMode

/**
 * Player-side effects a gesture can trigger. The pipeline owns recognition
 * and action dispatch; this port is everything it reaches out to (playback,
 * brightness/volume, morph animations).
 */
interface GestureEffects {
    fun seekBy(deltaMs: Long)

    fun seekTo(positionMs: Long)

    fun positionMs(): Long

    fun durationMs(): Long

    fun isPlaying(): Boolean

    fun pause()

    fun resume()

    fun playbackSpeed(): Float

    fun setPlaybackSpeed(speed: Float)

    fun defaultSpeedup(): Float

    fun speedupSensitivity(): Float

    fun jumpStepMs(): Long

    /** Screen height in px, for normalising brightness/volume drag deltas. */
    fun screenHeightPx(): Float

    fun brightness(): Float

    fun applyBrightness(level: Float)

    fun volume(): Float

    fun setVolume(level: Float)

    fun minimize()

    fun toggleFullscreen()

    fun exitFullscreen()

    fun enterPip()

    // Morph drag callbacks — the surface owns the animations.
    fun morphDragStart()

    fun morphDrag(dragY: Float)

    fun morphDragEnd(dragY: Float, velocityPxPerMs: Float)

    fun fullscreenDragStart()

    fun fullscreenDrag(dragY: Float)

    fun fullscreenDragEnd(dragY: Float, velocityPxPerMs: Float)

    fun shrinkDragStart()

    fun shrinkDrag(dragY: Float)

    fun shrinkDragEnd(dragY: Float, velocityPxPerMs: Float)
}

/**
 * Deep gesture seam: builds the sector configs from the user's preferences,
 * runs the [PlayerGestureRecognizer] and dispatches the resulting frames to
 * the player through [GestureEffects].
 *
 * The composable adapter ([PlayerGestureSystem]) only feeds pointer events
 * and reacts to the two outcomes that need Compose state: a double-tap
 * (drain the pointer) and a deferred single tap (schedule the tap).
 */
interface GesturePipeline {
    fun configs(prefs: PlayerGesturePreferences): GestureConfigs

    /** @return true when this down completed a double tap (already dispatched). */
    fun onDown(
        x: Float,
        y: Float,
        timeMs: Long,
        widthPx: Float,
        heightPx: Float,
        overlayMode: PlayerOverlayMode,
        cfg: GestureConfig,
        doubleTapSlopPx: Float,
    ): Boolean

    /** Fires the hold once the watchdog deadline elapsed; dispatched internally. */
    fun onHoldTimeout(timeMs: Long)

    /** @return true when the lift was a single tap the caller must defer. */
    fun onUp(timeMs: Long): Boolean

    fun onMove(x: Float, y: Float, timeMs: Long): MoveResult

    fun onSlideMove(x: Float, y: Float, timeMs: Long)

    fun onSlideEnd(timeMs: Long)

    fun cancel(timeMs: Long)

    sealed interface MoveResult {
        data object Idle : MoveResult

        /** @property bound false when the slot has no action (plain drain). */
        data class SlideStarted(val bound: Boolean) : MoveResult
    }
}

/** Default pipeline: recognizer + action handler wired to [effects]. */
class DefaultGesturePipeline(
    effects: GestureEffects,
) : GesturePipeline {
    private val recognizer = PlayerGestureRecognizer()
    private val actions = PlayerGestureActionHandler(effects)

    override fun configs(prefs: PlayerGesturePreferences): GestureConfigs = buildGestureConfigs(prefs)

    override fun onDown(
        x: Float,
        y: Float,
        timeMs: Long,
        widthPx: Float,
        heightPx: Float,
        overlayMode: PlayerOverlayMode,
        cfg: GestureConfig,
        doubleTapSlopPx: Float,
    ): Boolean {
        val result =
            recognizer.onDown(x, y, timeMs, widthPx, heightPx, overlayMode, cfg, doubleTapSlopPx)
        result.instantAction?.let(actions::handleInstantAction)
        return result.doubleTap
    }

    override fun onHoldTimeout(timeMs: Long) {
        recognizer.onHoldTimeout(timeMs)?.let(actions::handleGestureFrame)
    }

    override fun onUp(timeMs: Long): Boolean =
        when (val result = recognizer.onUp(timeMs)) {
            PlayerGestureRecognizer.UpResult.TapDeferred -> true
            is PlayerGestureRecognizer.UpResult.End -> {
                result.frame?.let(actions::handleGestureFrame)
                false
            }
        }

    override fun onMove(
        x: Float,
        y: Float,
        timeMs: Long,
    ): GesturePipeline.MoveResult =
        when (val result = recognizer.onMove(x, y, timeMs)) {
            is PlayerGestureRecognizer.MoveResult.Idle -> {
                result.frames.forEach(actions::handleGestureFrame)
                GesturePipeline.MoveResult.Idle
            }
            is PlayerGestureRecognizer.MoveResult.SlideStart -> {
                result.startFrame?.let(actions::handleGestureFrame)
                GesturePipeline.MoveResult.SlideStarted(bound = result.startFrame != null)
            }
        }

    override fun onSlideMove(
        x: Float,
        y: Float,
        timeMs: Long,
    ) {
        recognizer.onSlideMove(x, y, timeMs)?.let(actions::handleGestureFrame)
    }

    override fun onSlideEnd(timeMs: Long) {
        recognizer.onSlideEnd(timeMs)?.let(actions::handleGestureFrame)
    }

    override fun cancel(timeMs: Long) {
        recognizer.cancel(timeMs)?.let(actions::handleGestureFrame)
    }
}

/**
 * Build the gesture configs from the user's per-mode, per-slot assignments.
 *
 * Each mode has 4 slots × 4 gesture types, where each slot owns a column of
 * the engine's 9-sector grid:
 *   top          → the whole top row
 *   bottomLeft   → left column, middle + bottom rows
 *   bottomCenter → center column, middle + bottom rows
 *   bottomRight  → right column, middle + bottom rows
 *
 * Each cell resolves to the user's assignment when set, else the canonical
 * [PlayerGestures.DEFAULT_SLOTS] default for that mode. The "normal" config
 * drives both the normal and compact layouts; the floating mini surface
 * stays gesture-free.
 */
internal fun buildGestureConfigs(prefs: PlayerGesturePreferences): GestureConfigs {
    val slotFor: Map<GestureSector, String> =
        mapOf(
            GestureSector.TOP_LEFT to "top",
            GestureSector.TOP_CENTER to "top",
            GestureSector.TOP_RIGHT to "top",
            GestureSector.MIDDLE_LEFT to "bottomLeft",
            GestureSector.MIDDLE_CENTER to "bottomCenter",
            GestureSector.MIDDLE_RIGHT to "bottomRight",
            GestureSector.BOTTOM_LEFT to "bottomLeft",
            GestureSector.BOTTOM_CENTER to "bottomCenter",
            GestureSector.BOTTOM_RIGHT to "bottomRight",
        )

    return GestureConfigs(
        fullscreen = buildModeConfig(prefs.fullscreen, PlayerGestures.MODE_FULLSCREEN, slotFor),
        normal = buildModeConfig(prefs.normal, PlayerGestures.MODE_NORMAL, slotFor),
        compact = buildModeConfig(prefs.normal, PlayerGestures.MODE_NORMAL, slotFor),
        // No gestures on the mini player surface.
        floating = GestureConfig(),
    )
}

/** One [GestureConfig] from one mode's slot set. */
private fun buildModeConfig(
    slotSet: PlayerGestureSlotSet,
    mode: String,
    slotFor: Map<GestureSector, String>,
): GestureConfig {
    val slotMaps: Map<String, Map<String, String>> =
        mapOf(
            "top" to slotSet.top,
            "bottomLeft" to slotSet.bottomLeft,
            "bottomCenter" to slotSet.bottomCenter,
            "bottomRight" to slotSet.bottomRight,
        )
    return GestureConfig().withSectors(
        GestureSector.entries.associate { sector ->
            val slot = slotFor[sector] ?: "top"
            val map = slotMaps[slot].orEmpty()
            sector to
                GestureSlotConfig(
                    swipeVertical =
                        PlayerGestures.resolve(mode, slot, "swipe_v", map).toEngineAction(),
                    swipeHorizontal =
                        PlayerGestures.resolve(mode, slot, "swipe_h", map).toEngineAction(),
                    doubleTap =
                        PlayerGestures.resolve(mode, slot, "double_tap", map).toEngineAction(),
                    hold =
                        PlayerGestures.resolve(mode, slot, "hold", map).toEngineAction(),
                )
        },
    )
}

/** Flat action id → engine [GestureAction] (unknown/empty → NONE). */
internal fun String?.toEngineAction(): GestureAction =
    when (this) {
        PlayerGestures.NONE, null -> GestureAction.NONE
        PlayerGestures.VOLUME -> GestureAction.VOLUME
        PlayerGestures.BRIGHTNESS -> GestureAction.BRIGHTNESS
        PlayerGestures.SPEEDUP -> GestureAction.SPEEDUP
        PlayerGestures.SPEEDDOWN -> GestureAction.SPEEDDOWN
        PlayerGestures.REWIND_BACK -> GestureAction.REWIND_BACK
        PlayerGestures.REWIND_FORWARD -> GestureAction.REWIND_FORWARD
        PlayerGestures.CONTEXT_MENU -> GestureAction.CONTEXT_MENU
        PlayerGestures.MORPH_TO_FLOATING -> GestureAction.MORPH_TO_FLOATING
        PlayerGestures.MORPH_TO_FULLSCREEN -> GestureAction.MORPH_TO_FULLSCREEN
        PlayerGestures.MORPH_TO_NORMAL -> GestureAction.MORPH_TO_NORMAL
        PlayerGestures.MORPH_VERTICAL -> GestureAction.MORPH_VERTICAL
        PlayerGestures.SEEK_HOLD -> GestureAction.SEEK_HOLD
        PlayerGestures.PLAY_PAUSE -> GestureAction.PLAY_PAUSE
        PlayerGestures.PIP -> GestureAction.PIP
        else -> GestureAction.NONE
    }
