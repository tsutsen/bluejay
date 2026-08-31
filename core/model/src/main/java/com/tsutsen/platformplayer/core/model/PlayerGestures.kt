package com.tsutsen.platformplayer.core.model

/**
 * Flat catalog of player gesture actions — shared by the settings UI (which
 * edits per-slot assignments) and the player (which maps them back onto the
 * engine's gesture configs). Empty string = unassigned (no-op).
 *
 * The id space is the union of what the four gesture types can do:
 * swipes carry VOLUME/BRIGHTNESS/MORPH_*, holds SPEEDUP/SPEEDDOWN,
 * double-taps the rewind/context actions.
 */
object PlayerGestures {
    const val NONE = ""
    const val VOLUME = "volume"
    const val BRIGHTNESS = "brightness"
    const val SPEEDUP = "speedup"
    const val SPEEDDOWN = "speeddown"
    const val REWIND_BACK = "rewind_back"
    const val REWIND_FORWARD = "rewind_forward"
    const val CONTEXT_MENU = "context_menu"
    const val MORPH_TO_FLOATING = "morph_floating"
    const val MORPH_TO_FULLSCREEN = "morph_fullscreen"
    const val MORPH_VERTICAL = "morph_vertical"

    /** The four gesture types each slot can bind. */
    val GESTURE_TYPES = listOf("swipe_v", "swipe_h", "double_tap", "hold")

    val DISPLAY_NAMES: Map<String, String> =
        mapOf(
            NONE to "None",
            VOLUME to "Volume",
            BRIGHTNESS to "Brightness",
            SPEEDUP to "Speed up (hold)",
            SPEEDDOWN to "Speed down (hold)",
            REWIND_BACK to "Rewind",
            REWIND_FORWARD to "Fast forward",
            CONTEXT_MENU to "Context menu",
            MORPH_TO_FLOATING to "Morph to floating",
            MORPH_TO_FULLSCREEN to "Morph to fullscreen",
            MORPH_VERTICAL to "Morph (up/down)",
        )

    /** Display labels for the gesture types (column headers in the editor). */
    val TYPE_LABELS: Map<String, String> =
        mapOf(
            "swipe_v" to "Swipe",
            "swipe_h" to "S. swipe",
            "double_tap" to "Double-tap",
            "hold" to "Hold",
        )

    /** Slot keys, in display order. */
    val SLOTS = listOf("top", "bottomLeft", "bottomCenter", "bottomRight")

    val SLOT_LABELS: Map<String, String> =
        mapOf(
            "top" to "Top",
            "bottomLeft" to "Bottom left",
            "bottomCenter" to "Bottom center",
            "bottomRight" to "Bottom right",
        )

    /** Action options per gesture type (which actions make sense for it). */
    val OPTIONS_BY_TYPE: Map<String, List<String>> =
        mapOf(
            "swipe_v" to
                listOf(
                    NONE,
                    BRIGHTNESS,
                    VOLUME,
                    MORPH_TO_FLOATING,
                    MORPH_TO_FULLSCREEN,
                    MORPH_VERTICAL,
                ),
            "swipe_h" to
                listOf(
                    NONE,
                    SPEEDUP,
                    SPEEDDOWN,
                    REWIND_BACK,
                    REWIND_FORWARD,
                    MORPH_TO_FLOATING,
                    MORPH_TO_FULLSCREEN,
                    MORPH_VERTICAL,
                ),
            "double_tap" to
                listOf(
                    NONE,
                    REWIND_BACK,
                    REWIND_FORWARD,
                    CONTEXT_MENU,
                    MORPH_TO_FLOATING,
                    MORPH_TO_FULLSCREEN,
                ),
            "hold" to
                listOf(
                    NONE,
                    SPEEDUP,
                    SPEEDDOWN,
                    REWIND_BACK,
                    REWIND_FORWARD,
                    VOLUME,
                    BRIGHTNESS,
                ),
        )
}
