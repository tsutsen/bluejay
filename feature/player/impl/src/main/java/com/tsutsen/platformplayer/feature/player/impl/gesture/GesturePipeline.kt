package com.tsutsen.platformplayer.feature.player.impl.gesture

import com.tsutsen.platformplayer.core.model.GestureAction
import com.tsutsen.platformplayer.core.datastore.model.PlayerGesturePreferences
import com.tsutsen.platformplayer.core.datastore.model.PlayerGestureSlotSet
import com.tsutsen.platformplayer.core.model.PlayerGestures
import com.tsutsen.platformplayer.feature.player.impl.PlayerOverlayMode

/**
 * Pure playback vocabulary a gesture drives: seek/position/duration/play
 * state/speed, the drag-normalising screen height, and the brightness/volume
 * levels the handler applies. This is the narrow port
 * [PlayerGestureActionHandler] unit tests fake; mode transitions and morph
 * drags live in [LayoutEffects].
 */
interface PlaybackEffects {
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
}

/**
 * Layout side of the port: player-mode transitions and the morph /
 * fullscreen / shrink drag callbacks the surface animates. Every member
 * defaults to a no-op so tests can ignore layout without a recording
 * fake; the production adapter overrides everything.
 */
interface LayoutEffects {
    fun minimize() {}

    fun toggleFullscreen() {}

    fun exitFullscreen() {}

    fun enterPip() {}

    // Morph drag callbacks — the surface owns the animations.
    fun morphDragStart() {}

    fun morphDrag(dragY: Float) {}

    fun morphDragEnd(dragY: Float, velocityPxPerMs: Float) {}

    fun fullscreenDragStart() {}

    fun fullscreenDrag(dragY: Float) {}

    fun fullscreenDragEnd(dragY: Float, velocityPxPerMs: Float) {}

    fun shrinkDragStart() {}

    fun shrinkDrag(dragY: Float) {}

    fun shrinkDragEnd(dragY: Float, velocityPxPerMs: Float) {}
}

/** The full port the pipeline dispatches to: playback + layout. */
interface GestureEffects : PlaybackEffects, LayoutEffects

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
    private val actions = PlayerGestureActionHandler(effects, effects)

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
    val slotMaps: Map<String, Map<String, GestureAction>> =
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
                        PlayerGestures.resolve(mode, slot, "swipe_v", map),
                    swipeHorizontal =
                        PlayerGestures.resolve(mode, slot, "swipe_h", map),
                    doubleTap =
                        PlayerGestures.resolve(mode, slot, "double_tap", map),
                    hold =
                        PlayerGestures.resolve(mode, slot, "hold", map),
                )
        },
    )
}
