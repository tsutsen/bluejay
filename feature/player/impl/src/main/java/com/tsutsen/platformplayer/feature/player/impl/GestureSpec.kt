package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.ui.geometry.Offset
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
 * Build GestureBindings from a parsed YAML spec and runtime callbacks.
 *
 * Replaces [defaultPlayerBindings] — the YAML is the source of truth,
 * this function just wires callbacks to zones based on the spec actions.
 */
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
 * Build GestureBindings from a parsed YAML spec and runtime callbacks.
 */
fun buildGestureBindings(
    mode: PlayerMode,
    specs: Map<PlayerMode, ModeGestureSpec>,
    callbacks: GestureCallbacks,
): GestureBindings {
    val modeSpec = specs[mode] ?: return GestureBindings(emptyMap())
    val bindings = mutableMapOf<GestureZone, ZoneBindings>()

    // Pre-build action wrappers so we don't recreate lambdas per zone
    val morphDrag = object : ContinuousAction {
        override fun onStart(zone: GestureZone, position: Offset) { callbacks.onMorphDragStart() }
        override fun onDelta(deltaPx: Float) { callbacks.onMorphDrag(deltaPx) }
        override fun onEnd() {}
    }
    val brightnessDrag = continuousAction { deltaPx -> callbacks.onBrightnessDrag(deltaPx) }
    val volumeDrag = continuousAction { deltaPx -> callbacks.onVolumeDrag(deltaPx) }

    val doubleTapLeft = discreteAction { callbacks.onDoubleTapSeekLeft() }
    val doubleTapRight = discreteAction { callbacks.onDoubleTapSeekRight() }
    val tap = discreteAction { callbacks.onTap() }
    val longPressStart = discreteAction { callbacks.onLongPressStart() }
    val longPressEnd = discreteAction { callbacks.onLongPressEnd() }
    val morphDragEnd = discreteAction { callbacks.onMorphDragEnd() }

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
                // Morph zones also need drag-end on release
                if (zoneSpec.swipeVertical == SpecAction.Morph) {
                    discrete[DiscreteGesture.LONG_PRESS_END] = discreteAction {
                        callbacks.onLongPressEnd()
                        callbacks.onMorphDragEnd()
                    }
                } else {
                    discrete[DiscreteGesture.LONG_PRESS_END] = longPressEnd
                }
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
