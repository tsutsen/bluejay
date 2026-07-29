package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.ui.geometry.Offset
import com.tsutsen.platformplayer.core.model.PlayerMode

/**
 * Callbacks supplied by the caller (PlayerView) for each action type.
 * The builder wires these into the right zones based on the YAML spec.
 */
data class GestureCallbacks(
    val onMorphDrag: (deltaPx: Float) -> Unit = {},
    val onBrightnessDrag: (deltaPx: Float) -> Unit = {},
    val onVolumeDrag: (deltaPx: Float) -> Unit = {},
    val onDoubleTapSeekLeft: () -> Unit = {},
    val onDoubleTapSeekRight: () -> Unit = {},
    val onTap: () -> Unit = {},
    val onLongPressStart: () -> Unit = {},
    val onLongPressEnd: () -> Unit = {},
)

/**
 * Build GestureBindings from a parsed YAML spec and runtime callbacks.
 *
 * Replaces [defaultPlayerBindings] — the YAML is the source of truth,
 * this function just wires callbacks to zones based on the spec actions.
 */
fun buildGestureBindings(
    mode: PlayerMode,
    specs: Map<PlayerMode, ModeGestureSpec>,
    callbacks: GestureCallbacks,
): GestureBindings {
    val modeSpec = specs[mode] ?: return GestureBindings(emptyMap())
    val bindings = mutableMapOf<GestureZone, ZoneBindings>()

    // Pre-build action wrappers so we don't recreate lambdas per zone
    val morphDrag = continuousAction { deltaPx -> callbacks.onMorphDrag(deltaPx) }
    val brightnessDrag = continuousAction { deltaPx -> callbacks.onBrightnessDrag(deltaPx) }
    val volumeDrag = continuousAction { deltaPx -> callbacks.onVolumeDrag(deltaPx) }

    val doubleTapLeft = discreteAction { callbacks.onDoubleTapSeekLeft() }
    val doubleTapRight = discreteAction { callbacks.onDoubleTapSeekRight() }
    val tap = discreteAction { callbacks.onTap() }
    val longPressStart = discreteAction { callbacks.onLongPressStart() }
    val longPressEnd = discreteAction { callbacks.onLongPressEnd() }

    for ((zone, zoneSpec) in modeSpec.zones) {
        val continuous = mutableMapOf<ContinuousGesture, ContinuousAction>()
        val discrete = mutableMapOf<DiscreteGesture, DiscreteAction>()

        // Continuous gestures
        when (zoneSpec.swipeVertical) {
            SpecAction.Morph -> continuous[ContinuousGesture.VERTICAL_DRAG] = morphDrag
            SpecAction.Brightness -> continuous[ContinuousGesture.VERTICAL_DRAG] = brightnessDrag
            SpecAction.Volume -> continuous[ContinuousGesture.VERTICAL_DRAG] = volumeDrag
            else -> {}
        }

        when (zoneSpec.swipeHorizontal) {
            // Future: could add horizontal actions here
            else -> {}
        }

        // Discrete gestures
        when (zoneSpec.doubleTap) {
            SpecAction.SeekLeft -> discrete[DiscreteGesture.DOUBLE_TAP] = doubleTapLeft
            SpecAction.SeekRight -> discrete[DiscreteGesture.DOUBLE_TAP] = doubleTapRight
            else -> {}
        }

        when (zoneSpec.hold) {
            SpecAction.SpeedHold -> {
                discrete[DiscreteGesture.LONG_PRESS_START] = longPressStart
                discrete[DiscreteGesture.LONG_PRESS_END] = longPressEnd
            }
            else -> {}
        }

        when (zoneSpec.tap) {
            SpecAction.ToggleControls, SpecAction.Expand -> discrete[DiscreteGesture.TAP] = tap
            else -> {}
        }

        if (continuous.isNotEmpty() || discrete.isNotEmpty()) {
            bindings[zone] = ZoneBindings(continuous = continuous, discrete = discrete)
        }
    }

    return GestureBindings(byZone = bindings)
}

/** Helper to create a ContinuousAction from a simple delta callback. */
private fun continuousAction(onDelta: (deltaPx: Float) -> Unit): ContinuousAction = object : ContinuousAction {
    override fun onStart(zone: GestureZone, position: Offset) {}
    override fun onDelta(deltaPx: Float) { onDelta(deltaPx) }
    override fun onEnd() {}
}

/** Helper to create a DiscreteAction from a simple no-arg callback. */
private fun discreteAction(onFire: () -> Unit): DiscreteAction = DiscreteAction { _, _ -> onFire() }
