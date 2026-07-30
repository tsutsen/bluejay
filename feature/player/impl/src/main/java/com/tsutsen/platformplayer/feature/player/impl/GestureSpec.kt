package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.rememberUpdatedState
import com.tsutsen.platformplayer.core.model.PlayerMode

// ==================== YAML Parser ====================

/**
 * Minimal YAML parser for our constrained gesture spec format.
 *
 * Supported structure:
 *   MODE:
 *     ZONE:
 *       GESTURE: action
 *
 * No external dependencies — just indentation-based line parsing.
 */
object GestureSpecParser {

    /** Parse multi-document YAML (sections separated by --- or comments) into a map of MODE → ZONE → GESTURE → action. */
    fun parse(yaml: String): Map<String, Map<String, Map<String, String>>> {
        val result = mutableMapOf<String, MutableMap<String, MutableMap<String, String>>>()
        var currentMode: MutableMap<String, MutableMap<String, String>>? = null

        for (line in yaml.lineSequence()) {
            val trimmed = line.trim()

            // Skip empty lines, --- separators, and full-line comments
            if (trimmed.isEmpty() || trimmed == "---" || trimmed.startsWith("#")) continue

            if (trimmed.endsWith(":") && !line.startsWith(" ") && !line.startsWith("\t")) {
                // Top-level key (MODE)
                currentMode = mutableMapOf()
                result[trimmed.dropLast(1).trim()] = currentMode
            } else if (currentMode != null) {
                val indent = line.takeWhile { it == ' ' }.length
                if (indent >= 2 && indent < 6 && trimmed.endsWith(":")) {
                    // Zone key (indented 2+ spaces, ends with colon, not deep enough for gesture)
                    currentMode[trimmed.dropLast(1).trim()] = mutableMapOf()
                } else if (indent >= 4 && trimmed.contains(":")) {
                    // Gesture: action (indented 4+ spaces)
                    val colonIdx = trimmed.indexOf(":")
                    val key = trimmed.substring(0, colonIdx).trim()
                    val value = trimmed.substring(colonIdx + 1).trim()
                    // Find the last zone added (gestures belong to most recent zone)
                    val zone = currentMode.keys.lastOrNull()
                    if (zone != null) {
                        currentMode[zone]!![key] = value
                    }
                }
            }
        }

        return result
    }
}

// ==================== Spec Data ====================

/** Action assigned to a gesture in the spec. */
enum class SpecAction(
    val yamlName: String
) {
    Morph("morph"),
    Brightness("brightness"),
    Volume("volume"),
    SeekLeft("seek-left"),
    SeekRight("seek-right"),
    SpeedHold("speed-hold"),
    ToggleControls("toggle-controls"),
    Expand("expand"),
    PlayPause("play-pause"),
    None("none");

    companion object {
        private val BY_NAME = values().associateBy(SpecAction::yamlName)
        fun fromYaml(name: String): SpecAction = BY_NAME.getOrElse(name, { None })
    }
}

/** Parsed gesture spec for a single zone. */
data class ZoneGestureSpec(
    val swipeVertical: SpecAction = SpecAction.None,
    val swipeHorizontal: SpecAction = SpecAction.None,
    val doubleTap: SpecAction = SpecAction.None,
    val hold: SpecAction = SpecAction.None,
    val tap: SpecAction = SpecAction.None,
)

/** Parsed gesture spec for a single mode (all zones). */
data class ModeGestureSpec(
    val zones: Map<GestureZone, ZoneGestureSpec> = emptyMap()
)

/** Parse zone name like "TOP_LEFT" into GestureZone(GestureRow.TOP, GestureColumn.LEFT). */
private fun parseZoneName(name: String): GestureZone? {
    val parts = name.split("_")
    if (parts.size != 2) return null
    val row = when (parts[0]) {
        "TOP" -> GestureRow.TOP
        "MIDDLE" -> GestureRow.MIDDLE
        "BOTTOM" -> GestureRow.BOTTOM
        else -> return null
    }
    val col = when (parts[1]) {
        "LEFT" -> GestureColumn.LEFT
        "CENTER" -> GestureColumn.CENTER
        "RIGHT" -> GestureColumn.RIGHT
        else -> return null
    }
    return GestureZone(row, col)
}

// ==================== Spec Builder ====================

/**
 * Callbacks supplied by the caller (PlayerView) for each action type.
 * The builder wires these into the right zones based on the YAML spec.
 */
data class GestureCallbacks(
    val onMorphDragStart: () -> Unit = {},
    val onMorphDrag: (deltaPx: Float) -> Unit = {},
    val onMorphDragEnd: () -> Unit = {},
    val onBrightnessDrag: (deltaPx: Float) -> Unit = {},
    val onVolumeDrag: (deltaPx: Float) -> Unit = {},
    val onDoubleTapSeekLeft: () -> Unit = {},
    val onDoubleTapSeekRight: () -> Unit = {},
    val onTap: () -> Unit = {},
    val onLongPressStart: () -> Unit = {},
    val onLongPressEnd: () -> Unit = {},
)

/**
 * Cached action wrappers to avoid recreating objects on every composition.
 * Wraps callbacks in a reference holder so they always reference the latest values.
 */
data class CachedGestureActions(
    val morphDrag: ContinuousAction,
    val brightnessDrag: ContinuousAction,
    val volumeDrag: ContinuousAction,
    val doubleTapLeft: DiscreteAction,
    val doubleTapRight: DiscreteAction,
    val tap: DiscreteAction,
    val longPressStart: DiscreteAction,
    val longPressEnd: DiscreteAction,
    val morphDragEnd: DiscreteAction,
)

/**
 * Create gesture action wrappers that reference the latest callback values via a mutable reference.
 * The wrappers are created once and the reference is updated when callbacks change.
 */
fun createGestureActions(callbacks: GestureCallbacks): CachedGestureActions {
    val callbacksRef = androidx.compose.runtime.mutableStateOf(callbacks)
    
    return CachedGestureActions(
        morphDrag = object : ContinuousAction {
            override fun onStart(zone: GestureZone, position: Offset) { callbacksRef.value.onMorphDragStart() }
            override fun onDelta(deltaPx: Float) { callbacksRef.value.onMorphDrag(deltaPx) }
            override fun onEnd() {}
        },
        brightnessDrag = continuousAction { deltaPx -> callbacksRef.value.onBrightnessDrag(deltaPx) },
        volumeDrag = continuousAction { deltaPx -> callbacksRef.value.onVolumeDrag(deltaPx) },
        doubleTapLeft = discreteAction { callbacksRef.value.onDoubleTapSeekLeft() },
        doubleTapRight = discreteAction { callbacksRef.value.onDoubleTapSeekRight() },
        tap = discreteAction { callbacksRef.value.onTap() },
        longPressStart = discreteAction { callbacksRef.value.onLongPressStart() },
        longPressEnd = discreteAction { callbacksRef.value.onLongPressEnd() },
        morphDragEnd = discreteAction { callbacksRef.value.onMorphDragEnd() },
    )
}

fun buildGestureSpecs(rawMap: Map<String, Map<String, Map<String, String>>>): Map<PlayerMode, ModeGestureSpec> {
    val result = mutableMapOf<PlayerMode, ModeGestureSpec>()

    for ((modeName, zoneMap) in rawMap) {
        val mode = try { PlayerMode.valueOf(modeName) } catch (_: IllegalArgumentException) { continue }
        val zoneSpecs = mutableMapOf<GestureZone, ZoneGestureSpec>()

        for ((zoneName, gestureMap) in zoneMap) {
            val zone = parseZoneName(zoneName) ?: continue
            zoneSpecs[zone] = ZoneGestureSpec(
                swipeVertical = SpecAction.fromYaml(gestureMap["SWIPE_VERTICAL"] ?: "none"),
                swipeHorizontal = SpecAction.fromYaml(gestureMap["SWIPE_HORIZONTAL"] ?: "none"),
                doubleTap = SpecAction.fromYaml(gestureMap["DOUBLE_TAP"] ?: "none"),
                hold = SpecAction.fromYaml(gestureMap["HOLD"] ?: "none"),
                tap = SpecAction.fromYaml(gestureMap["TAP"] ?: "none"),
            )
        }

        result[mode] = ModeGestureSpec(zoneSpecs)
    }

    return result
}

/**
 * Build GestureBindings from a parsed YAML spec and pre-cached action wrappers.
 * Use [rememberGestureActions] to create cached wrappers that avoid recreating
 * action objects on every composition.
 */
fun buildGestureBindings(
    mode: PlayerMode,
    specs: Map<PlayerMode, ModeGestureSpec>,
    actions: CachedGestureActions,
): GestureBindings {
    val modeSpec = specs[mode] ?: return GestureBindings(emptyMap())
    val bindings = mutableMapOf<GestureZone, ZoneBindings>()

    for ((zone, zoneSpec) in modeSpec.zones) {
        val continuous = mutableMapOf<ContinuousGesture, ContinuousAction>()
        val discrete = mutableMapOf<DiscreteGesture, DiscreteAction>()

        // Continuous gestures
        when (zoneSpec.swipeVertical) {
            SpecAction.Morph -> continuous[ContinuousGesture.VERTICAL_DRAG] = actions.morphDrag
            SpecAction.Brightness -> continuous[ContinuousGesture.VERTICAL_DRAG] = actions.brightnessDrag
            SpecAction.Volume -> continuous[ContinuousGesture.VERTICAL_DRAG] = actions.volumeDrag
            else -> {}
        }

        when (zoneSpec.swipeHorizontal) {
            // Future: could add horizontal actions here
            else -> {}
        }

        // Discrete gestures
        when (zoneSpec.doubleTap) {
            SpecAction.SeekLeft -> discrete[DiscreteGesture.DOUBLE_TAP] = actions.doubleTapLeft
            SpecAction.SeekRight -> discrete[DiscreteGesture.DOUBLE_TAP] = actions.doubleTapRight
            else -> {}
        }

        val isSpeedHoldZone = zoneSpec.hold == SpecAction.SpeedHold
        val isMorphZone = zoneSpec.swipeVertical == SpecAction.Morph

        if (isSpeedHoldZone) {
            discrete[DiscreteGesture.LONG_PRESS_START] = actions.longPressStart
        }

        // LONG_PRESS_END fires for any zone with a continuous drag that needs a release
        // signal — not just speed-hold zones. A morph zone with no hold action configured
        // still needs onMorphDragEnd() to fire on release, or the drag never settles/animates
        // and gestureState is left stuck mid-gesture.
        if (isSpeedHoldZone || isMorphZone) {
            discrete[DiscreteGesture.LONG_PRESS_END] = discreteAction {
                if (isSpeedHoldZone) actions.longPressEnd.invoke(zone, androidx.compose.ui.geometry.Offset.Zero)
                if (isMorphZone) actions.morphDragEnd.invoke(zone, androidx.compose.ui.geometry.Offset.Zero)
            }
        }

        when (zoneSpec.tap) {
            SpecAction.ToggleControls, SpecAction.Expand -> discrete[DiscreteGesture.TAP] = actions.tap
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
