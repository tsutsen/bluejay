package com.tsutsen.platformplayer.core.model

/**
 * The single gesture-action vocabulary. Persisted by [name], displayed via
 * [displayName], and driven by the player engine (the engine's GestureAction
 * IS this enum — no id translation layer). [NONE] = unassigned (no-op).
 *
 * The id space is the union of what the four gesture types can do:
 * swipes carry VOLUME/BRIGHTNESS/MORPH_*, holds SPEEDUP/SPEEDDOWN,
 * double-taps the jump/context actions.
 */
enum class GestureAction(val displayName: String) {
    // No-op
    NONE("None"),

    // Swipe (continuous)
    VOLUME("Volume"),
    BRIGHTNESS("Brightness"),

    // Hold (base + optional swipe modulation)
    SPEEDUP("Speed up"),
    SPEEDDOWN("Speed down"),

    // Hold: pause + horizontal scrub, commit seek on release.
    SEEK_HOLD("Hold to seek"),

    // Instant (double-tap)
    REWIND_FORWARD("Jump forward"),
    REWIND_BACK("Jump back"),
    CONTEXT_MENU("Context menu"),
    PLAY_PAUSE("Play / pause"),
    PIP("Picture in picture"),

    // Morph transitions (swipe or instant depending on config)
    MORPH_TO_FLOATING("Morph to floating"),
    MORPH_TO_FULLSCREEN("Morph to fullscreen"),

    // Fullscreen only: shrink the surface back toward normal size
    // (downward v-swipe, commit crosses the normal layout).
    MORPH_TO_NORMAL("Morph to normal"),

    // Direction-aware vertical morph: swipe up → FULLSCREEN, swipe down → FLOATING.
    MORPH_VERTICAL("Morph to floating/fullscreen");

    companion object {
        /**
         * Read migration for persisted gesture assignments: accepts the
         * current format ([name], e.g. "VOLUME") and the legacy flat ids
         * (e.g. "volume", "morph_floating") written before the vocabulary
         * was consolidated; unknown/empty → [NONE] (same rule the old id
         * layer used).
         */
        fun fromPersisted(value: String?): GestureAction {
            if (value.isNullOrEmpty()) return NONE
            entries.firstOrNull { it.name == value }?.let { return it }
            return when (value) {
                "volume" -> VOLUME
                "brightness" -> BRIGHTNESS
                "speedup" -> SPEEDUP
                "speeddown" -> SPEEDDOWN
                "rewind_back" -> REWIND_BACK
                "rewind_forward" -> REWIND_FORWARD
                "context_menu" -> CONTEXT_MENU
                "morph_floating" -> MORPH_TO_FLOATING
                "morph_fullscreen" -> MORPH_TO_FULLSCREEN
                "morph_normal" -> MORPH_TO_NORMAL
                "morph_vertical" -> MORPH_VERTICAL
                "seek_hold" -> SEEK_HOLD
                "play_pause" -> PLAY_PAUSE
                "pip" -> PIP
                else -> NONE
            }
        }
    }
}

/**
 * Structural metadata for the gesture editor: player modes, the four gesture
 * types, the surface slots, and the per-mode defaults/options — all keyed by
 * the historical string ids (persisted map keys), while ACTIONS are the
 * [GestureAction] enum.
 */
object PlayerGestures {
    /** Player states with editable gesture sections. */
    const val MODE_FULLSCREEN = "fullscreen"
    const val MODE_NORMAL = "normal"

    /** The four gesture types each slot can bind, in display order. */
    val GESTURE_TYPES = listOf("hold", "double_tap", "swipe_h", "swipe_v")

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
    ): List<GestureAction> =
        when (type) {
            "swipe_v" ->
                if (mode == MODE_FULLSCREEN) {
                    listOf(
                        GestureAction.NONE,
                        GestureAction.BRIGHTNESS,
                        GestureAction.VOLUME,
                        GestureAction.MORPH_TO_FLOATING,
                        GestureAction.MORPH_TO_NORMAL,
                    )
                } else {
                    listOf(
                        GestureAction.NONE,
                        GestureAction.BRIGHTNESS,
                        GestureAction.VOLUME,
                        GestureAction.MORPH_VERTICAL,
                    )
                }

            "swipe_h" ->
                listOf(
                    GestureAction.NONE,
                    GestureAction.SPEEDUP,
                    GestureAction.SPEEDDOWN,
                    GestureAction.REWIND_BACK,
                    GestureAction.REWIND_FORWARD,
                )

            "double_tap" ->
                listOf(
                    GestureAction.NONE,
                    GestureAction.PLAY_PAUSE,
                    GestureAction.MORPH_TO_FULLSCREEN,
                    GestureAction.MORPH_TO_FLOATING,
                    GestureAction.MORPH_TO_NORMAL,
                    GestureAction.PIP,
                    GestureAction.REWIND_BACK,
                    GestureAction.REWIND_FORWARD,
                    GestureAction.CONTEXT_MENU,
                )

            "hold" ->
                listOf(
                    GestureAction.NONE,
                    GestureAction.SPEEDUP,
                    GestureAction.SPEEDDOWN,
                    GestureAction.SEEK_HOLD,
                    GestureAction.REWIND_BACK,
                    GestureAction.REWIND_FORWARD,
                    GestureAction.VOLUME,
                    GestureAction.BRIGHTNESS,
                )

            else -> emptyList()
        }

    /** Shared per-slot skeleton (v-swipe filled per mode below). */
    private fun slotSet(swipeV: (slot: String) -> GestureAction): Map<String, Map<String, GestureAction>> =
        mapOf(
            "top" to
                mapOf(
                    "hold" to GestureAction.SPEEDUP,
                    "double_tap" to GestureAction.NONE,
                    "swipe_h" to GestureAction.SPEEDUP,
                    "swipe_v" to swipeV("top"),
                ),
            "bottomLeft" to
                mapOf(
                    "hold" to GestureAction.SPEEDUP,
                    "double_tap" to GestureAction.REWIND_BACK,
                    "swipe_h" to GestureAction.SPEEDUP,
                    "swipe_v" to swipeV("bottomLeft"),
                ),
            "bottomCenter" to
                mapOf(
                    "hold" to GestureAction.SEEK_HOLD,
                    "double_tap" to GestureAction.NONE,
                    "swipe_h" to GestureAction.NONE,
                    "swipe_v" to swipeV("bottomCenter"),
                ),
            "bottomRight" to
                mapOf(
                    "hold" to GestureAction.SPEEDUP,
                    "double_tap" to GestureAction.REWIND_FORWARD,
                    "swipe_h" to GestureAction.SPEEDUP,
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
    val DEFAULT_SLOTS: Map<String, Map<String, Map<String, GestureAction>>> =
        mapOf(
            MODE_FULLSCREEN to
                slotSet(
                    {
                        when (it) {
                            "top" -> GestureAction.MORPH_TO_FLOATING
                            "bottomLeft" -> GestureAction.BRIGHTNESS
                            "bottomRight" -> GestureAction.VOLUME
                            else -> GestureAction.NONE
                        }
                    },
                ),
            MODE_NORMAL to slotSet({ GestureAction.MORPH_VERTICAL }),
        )
    /** Effective action for one cell: the user's override, else the default. */
    fun resolve(
        mode: String,
        slot: String,
        type: String,
        userSlot: Map<String, GestureAction>,
    ): GestureAction = userSlot[type] ?: DEFAULT_SLOTS[mode]?.get(slot)?.get(type) ?: GestureAction.NONE
}
