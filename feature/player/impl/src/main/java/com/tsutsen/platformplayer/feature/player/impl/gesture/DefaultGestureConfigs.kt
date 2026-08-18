package com.tsutsen.platformplayer.feature.player.impl.gesture

/**
 * Factory that builds the shipped default gesture configs for all four overlay modes.
 *
 * Config spec:
 *   FULLSCREEN — top rows swipe-V → MORPH_TO_FLOATING;
 *                middle/bottom left swipe-V → BRIGHTNESS;
 *                middle/bottom right swipe-V → VOLUME.
 *   NORMAL     — swipe-V → MORPH_VERTICAL everywhere (up = FULLSCREEN,
 *                down = FLOATING; no brightness / volume).
 *   COMPACT    — identical to NORMAL.
 *   FLOATING   — all NONE.
 */
fun buildDefaultGestureConfigs(): GestureConfigs {
    val a = GestureAction.NONE
    val speedup = GestureAction.SPEEDUP
    val rewindBack = GestureAction.REWIND_BACK
    val rewindForward = GestureAction.REWIND_FORWARD
    val brightness = GestureAction.BRIGHTNESS
    val volume = GestureAction.VOLUME
    val morphFloating = GestureAction.MORPH_TO_FLOATING
    val morphVertical = GestureAction.MORPH_VERTICAL

    // --- reusable slot configs ---
    val none = GestureSlotConfig()

    // Left-column sector: hold→SPEEDUP, double-tap→REWIND_BACK
    val leftHoldRewindBack = GestureSlotConfig(doubleTap = rewindBack, hold = speedup)

    // Right-column sector: hold→SPEEDUP, double-tap→REWIND_FORWARD
    val rightHoldRewindFwd = GestureSlotConfig(doubleTap = rewindForward, hold = speedup)

    // --- FULLSCREEN ---
    val fullscreen = GestureConfig().withSectors(mapOf(
        GestureSector.TOP_LEFT to leftHoldRewindBack.copy(swipeVertical = morphFloating),
        GestureSector.TOP_CENTER to none.copy(swipeVertical = morphFloating),
        GestureSector.TOP_RIGHT to rightHoldRewindFwd.copy(swipeVertical = morphFloating),

        GestureSector.MIDDLE_LEFT to leftHoldRewindBack.copy(swipeVertical = brightness),
        GestureSector.MIDDLE_CENTER to none,
        GestureSector.MIDDLE_RIGHT to rightHoldRewindFwd.copy(swipeVertical = volume),

        GestureSector.BOTTOM_LEFT to leftHoldRewindBack.copy(swipeVertical = brightness),
        GestureSector.BOTTOM_CENTER to none,
        GestureSector.BOTTOM_RIGHT to rightHoldRewindFwd.copy(swipeVertical = volume),
    ))

    // --- NORMAL (swipe-V → MORPH_VERTICAL: up = fullscreen, down = floating) ---
    val normal = GestureConfig().withSectors(mapOf(
        GestureSector.TOP_LEFT to leftHoldRewindBack.copy(swipeVertical = morphVertical),
        GestureSector.TOP_CENTER to none.copy(swipeVertical = morphVertical),
        GestureSector.TOP_RIGHT to rightHoldRewindFwd.copy(swipeVertical = morphVertical),

        GestureSector.MIDDLE_LEFT to leftHoldRewindBack.copy(swipeVertical = morphVertical),
        GestureSector.MIDDLE_CENTER to none.copy(swipeVertical = morphVertical),
        GestureSector.MIDDLE_RIGHT to rightHoldRewindFwd.copy(swipeVertical = morphVertical),

        GestureSector.BOTTOM_LEFT to leftHoldRewindBack.copy(swipeVertical = morphVertical),
        GestureSector.BOTTOM_CENTER to none.copy(swipeVertical = morphVertical),
        GestureSector.BOTTOM_RIGHT to rightHoldRewindFwd.copy(swipeVertical = morphVertical),
    ))

    // --- COMPACT — same as NORMAL ---
    val compact = normal

    // --- FLOATING — all NONE ---
    val floating = GestureConfig()

    return GestureConfigs(
        fullscreen = fullscreen,
        normal = normal,
        compact = compact,
        floating = floating
    )
}
