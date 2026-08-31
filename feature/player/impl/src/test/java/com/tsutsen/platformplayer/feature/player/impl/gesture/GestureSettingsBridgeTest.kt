package com.tsutsen.platformplayer.feature.player.impl.gesture

import com.tsutsen.platformplayer.core.datastore.model.PlayerGesturePreferences
import com.tsutsen.platformplayer.core.datastore.model.PlayerGestureSlotSet
import com.tsutsen.platformplayer.core.model.PlayerGestures
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Chain test: user slot sets → buildGestureConfigs → per-sector engine
 * actions. Guards the "changing gesture settings does nothing" regression:
 * any link that silently drops a user override fails here.
 */
class GestureSettingsBridgeTest {
    @Test
    fun userOverrideWinsInFullscreen() {
        val prefs =
            PlayerGesturePreferences(
                fullscreen =
                    PlayerGestureSlotSet(
                        top = mapOf("swipe_v" to PlayerGestures.VOLUME),
                    ),
            )
        val configs = buildGestureConfigs(prefs)
        // top slot → whole top row; user set v-swipe = volume
        assertEquals(
            GestureAction.VOLUME,
            configs.fullscreen.resolve(GestureSector.TOP_CENTER, GestureType.SWIPE_VERTICAL),
        )
        // user override covers the whole top row
        assertEquals(
            GestureAction.VOLUME,
            configs.fullscreen.resolve(GestureSector.TOP_LEFT, GestureType.SWIPE_VERTICAL),
        )
        // unset slots keep the fullscreen defaults
        assertEquals(
            GestureAction.VOLUME,
            configs.fullscreen.resolve(GestureSector.BOTTOM_RIGHT, GestureType.SWIPE_VERTICAL),
        )
        assertEquals(
            GestureAction.NONE,
            configs.fullscreen.resolve(GestureSector.BOTTOM_CENTER, GestureType.SWIPE_VERTICAL),
        )
    }

    @Test
    fun modesDoNotLeakIntoEachOther() {
        val prefs =
            PlayerGesturePreferences(
                fullscreen =
                    PlayerGestureSlotSet(
                        bottomRight = mapOf("double_tap" to PlayerGestures.MORPH_TO_FLOATING),
                    ),
            )
        val configs = buildGestureConfigs(prefs)
        assertEquals(
            GestureAction.MORPH_TO_FLOATING,
            configs.fullscreen.resolve(GestureSector.BOTTOM_RIGHT, GestureType.DOUBLE_TAP),
        )
        // normal mode keeps its own defaults (bottom-right double tap = jump forward)
        assertEquals(
            GestureAction.REWIND_FORWARD,
            configs.normal.resolve(GestureSector.BOTTOM_RIGHT, GestureType.DOUBLE_TAP),
        )
        // compact mirrors normal
        assertEquals(configs.normal, configs.compact)
    }

    @Test
    fun emptySlotSetYieldsCanonicalDefaults() {
        val configs = buildGestureConfigs(PlayerGesturePreferences())
        // normal mode: v-swipe is the direction-aware morph everywhere
        // except the bottom-center column.
        assertEquals(
            GestureAction.MORPH_VERTICAL,
            configs.normal.resolve(GestureSector.MIDDLE_LEFT, GestureType.SWIPE_VERTICAL),
        )
        assertEquals(
            GestureAction.NONE,
            configs.normal.resolve(GestureSector.BOTTOM_CENTER, GestureType.SWIPE_VERTICAL),
        )
        // fullscreen: sides are brightness/volume
        assertEquals(
            GestureAction.BRIGHTNESS,
            configs.fullscreen.resolve(GestureSector.BOTTOM_LEFT, GestureType.SWIPE_VERTICAL),
        )
        assertEquals(
            GestureAction.VOLUME,
            configs.fullscreen.resolve(GestureSector.MIDDLE_RIGHT, GestureType.SWIPE_VERTICAL),
        )
    }

    @Test
    fun unknownActionMapsToNone() {
        assertEquals(GestureAction.NONE, "garbage".toEngineAction())
        assertEquals(GestureAction.NONE, null.toEngineAction())
        assertEquals(GestureAction.MORPH_TO_NORMAL, PlayerGestures.MORPH_TO_NORMAL.toEngineAction())
    }
}
