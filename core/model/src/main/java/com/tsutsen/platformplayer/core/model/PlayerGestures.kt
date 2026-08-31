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

    /** The four gesture types each slot can bind, in display order. */
    val GESTURE_TYPES = listOf("hold", "double_tap", "swipe_h", "swipe_v")

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

    /** Display labels for the gesture types. */
    val TYPE_LABELS: Map<String, String> =
        mapOf(
            "hold" to "Hold",
            "double_tap" to "Double tap",
            "swipe_h" to "H-swipe",
            "swipe_v" to "V-swipe",
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
            // MORPH_TO_FULLSCREEN is intentionally absent from the swipe
            // lists: the swipe-up variant is an unimplemented stub (it only
            // works as an instant double-tap action).
            "swipe_v" to
                listOf(
                    NONE,
                    BRIGHTNESS,
                    VOLUME,
                    MORPH_TO_FLOATING,
                    MORPH_VERTICAL,
                ),
            // No morph options here: morph handlers only read the vertical
            // delta, so they can never fire on a horizontal swipe.
            "swipe_h" to
                listOf(
                    NONE,
                    SPEEDUP,
                    SPEEDDOWN,
                    REWIND_BACK,
                    REWIND_FORWARD,
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

    /**
     * Canonical per-slot defaults — what each button shows before the user
     * customizes it, and what the player uses when a cell is unset.
     * Mirrors the shipped gesture behaviour.
     */
    val DEFAULT_SLOTS: Map<String, Map<String, String>> =
        mapOf(
            "top" to
                mapOf(
                    "hold" to SPEEDUP,
                    "double_tap" to NONE,
                    "swipe_h" to SPEEDUP,
                    "swipe_v" to MORPH_VERTICAL,
                ),
            "bottomLeft" to
                mapOf(
                    "hold" to SPEEDUP,
                    "double_tap" to REWIND_BACK,
                    "swipe_h" to SPEEDUP,
                    "swipe_v" to BRIGHTNESS,
                ),
            "bottomCenter" to
                mapOf(
                    "hold" to NONE,
                    "double_tap" to NONE,
                    "swipe_h" to NONE,
                    "swipe_v" to NONE,
                ),
            "bottomRight" to
                mapOf(
                    "hold" to SPEEDUP,
                    "double_tap" to REWIND_FORWARD,
                    "swipe_h" to SPEEDUP,
                    "swipe_v" to VOLUME,
                ),
        )

    /** Effective action for one cell: the user's override, else the default. */
    fun resolve(
        slot: String,
        type: String,
        userSlot: Map<String, String>,
    ): String = userSlot[type] ?: DEFAULT_SLOTS[slot]?.get(type) ?: NONE
}
