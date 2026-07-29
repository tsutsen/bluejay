package com.tsutsen.platformplayer.feature.player.impl

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

/**
 * Parse raw YAML map into structured ModeGestureSpec.
 * @param rawMap output of GestureSpecParser.parse()
 */
fun buildGestureSpecs(rawMap: Map<String, Map<String, Map<String, String>>>): Map<PlayerMode, ModeGestureSpec> {
    val result = mutableMapOf<PlayerMode, ModeGestureSpec>()

    for ((modeName, zoneMap) in rawMap) {
        // COMPACT in YAML maps to NORMAL — it's a visual variant, not a separate mode
        val modeNameResolved = if (modeName == "COMPACT") "NORMAL" else modeName
        val mode = try { PlayerMode.valueOf(modeNameResolved) } catch (_: IllegalArgumentException) { continue }
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
