package com.tsutsen.platformplayer.core.model

/**
 * Flat catalog of player gesture actions — shared by the settings UI (which
 * edits per-mode, per-slot assignments) and the player (which maps them back
 * onto the engine's gesture configs). Empty string = unassigned (no-op).
 *
 * The id space is the union of what the four gesture types can do:
 * swipes carry VOLUME/BRIGHTNESS/MORPH_*, holds SPEEDUP/SPEEDDOWN,
 * double-taps the jump/context actions.
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
    const val MORPH_TO_NORMAL = "morph_normal"
    const val MORPH_VERTICAL = "morph_vertical"

    /** Player states with editable gesture sections. */
    const val MODE_FULLSCREEN = "fullscreen"
    const val MODE_NORMAL = "normal"

    /** The four gesture types each slot can bind, in display order. */
    val GESTURE_TYPES = listOf("hold", "double_tap", "swipe_h", "swipe_v")

    val DISPLAY_NAMES: Map<String, String> =
        mapOf(
            NONE to "None",
            VOLUME to "Volume",
            BRIGHTNESS to "Brightness",
            SPEEDUP to "Speed up",
            SPEEDDOWN to "Speed down",
            REWIND_BACK to "Jump back",
            REWIND_FORWARD to "Jump forward",
            CONTEXT_MENU to "Context menu",
            MORPH_TO_FLOATING to "Morph to floating",
            MORPH_TO_FULLSCREEN to "Morph to fullscreen",
            MORPH_TO_NORMAL to "Morph to normal",
            // Direction-aware: up = fullscreen, down = floating.
            MORPH_VERTICAL to "Morph to floating/fullscreen",
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

    /**
     * Action options per gesture type, per player mode.
     *
     * v-swipe differs between the two modes: fullscreen offers the two
     * explicit targets (floating / normal), normal offers the direction-aware
     * morph (up = fullscreen, down = floating).
     *
     * MORPH_TO_FULLSCREEN is absent from the swipe lists: the swipe-up
     * variant is an unimplemented stub (it only works as a double-tap).
     * No morph options for h-swipe: morph handlers only read the vertical
     * delta, so they can never fire on a horizontal swipe.
     */
    fun optionsFor(
        mode: String,
        type: String,
    ): List<String> =
        when (type) {
            "swipe_v" ->
                if (mode == MODE_FULLSCREEN) {
                    listOf(
                        NONE,
                        BRIGHTNESS,
                        VOLUME,
                        MORPH_TO_FLOATING,
                        MORPH_TO_NORMAL,
                    )
                } else {
                    listOf(
                        NONE,
                        BRIGHTNESS,
                        VOLUME,
                        MORPH_VERTICAL,
                    )
                }

            "swipe_h" ->
                listOf(
                    NONE,
                    SPEEDUP,
                    SPEEDDOWN,
                    REWIND_BACK,
                    REWIND_FORWARD,
                )

            "double_tap" ->
                listOf(
                    NONE,
                    REWIND_BACK,
                    REWIND_FORWARD,
                    CONTEXT_MENU,
                    MORPH_TO_FLOATING,
                    MORPH_TO_FULLSCREEN,
                )

            "hold" ->
                listOf(
                    NONE,
                    SPEEDUP,
                    SPEEDDOWN,
                    REWIND_BACK,
                    REWIND_FORWARD,
                    VOLUME,
                    BRIGHTNESS,
                )

            else -> emptyList()
        }

    /** Shared per-slot skeleton (v-swipe filled per mode below). */
    private fun slotSet(swipeV: (slot: String) -> String): Map<String, Map<String, String>> =
        mapOf(
            "top" to
                mapOf(
                    "hold" to SPEEDUP,
                    "double_tap" to NONE,
                    "swipe_h" to SPEEDUP,
                    "swipe_v" to swipeV("top"),
                ),
            "bottomLeft" to
                mapOf(
                    "hold" to SPEEDUP,
                    "double_tap" to REWIND_BACK,
                    "swipe_h" to SPEEDUP,
                    "swipe_v" to swipeV("bottomLeft"),
                ),
            "bottomCenter" to
                mapOf(
                    "hold" to NONE,
                    "double_tap" to NONE,
                    "swipe_h" to NONE,
                    "swipe_v" to swipeV("bottomCenter"),
                ),
            "bottomRight" to
                mapOf(
                    "hold" to SPEEDUP,
                    "double_tap" to REWIND_FORWARD,
                    "swipe_h" to SPEEDUP,
                    "swipe_v" to swipeV("bottomRight"),
                ),
        )

    /**
     * Canonical per-mode, per-slot defaults — what each button shows before
     * the user customizes it, and what the player uses when a cell is unset.
     * Mirrors the shipped gesture behaviour:
     *   fullscreen — top v-swipe → floating, sides → brightness/volume
     *   normal     — v-swipe → direction-aware morph everywhere
     */
    val DEFAULT_SLOTS: Map<String, Map<String, Map<String, String>>> =
        mapOf(
            MODE_FULLSCREEN to
                slotSet(
                    {
                        when (it) {
                            "top" -> MORPH_TO_FLOATING
                            "bottomLeft" -> BRIGHTNESS
                            "bottomRight" -> VOLUME
                            else -> NONE
                        }
                    },
                ),
            MODE_NORMAL to
                slotSet(
                    {
                        when (it) {
                            "bottomCenter" -> NONE
                            else -> MORPH_VERTICAL
                        }
                    },
                ),
        )

    /** Effective action for one cell: the user's override, else the default. */
    fun resolve(
        mode: String,
        slot: String,
        type: String,
        userSlot: Map<String, String>,
    ): String = userSlot[type] ?: DEFAULT_SLOTS[mode]?.get(slot)?.get(type) ?: NONE
}
