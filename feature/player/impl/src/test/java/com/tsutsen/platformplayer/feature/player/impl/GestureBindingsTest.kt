package com.tsutsen.platformplayer.feature.player.impl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureBindingsTest {

    @Test
    fun `morph zone always has LONG_PRESS_END binding`() {
        // Property: any zone with swipeVertical = Morph should have LONG_PRESS_END
        val specs = buildGestureSpecs(
            mapOf(
                "NORMAL" to mapOf(
                    "MIDDLE_CENTER" to mapOf(
                        "SWIPE_VERTICAL" to "morph"
                    )
                )
            )
        )

        val actions = createGestureActions(GestureCallbacks())
        val bindings = buildGestureBindings(
            mode = com.tsutsen.platformplayer.core.model.PlayerMode.NORMAL,
            specs = specs,
            actions = actions
        )

        val morphZone = GestureZone(GestureRow.MIDDLE, GestureColumn.CENTER)
        val zoneBindings = bindings.byZone[morphZone]
        assertNotNull(zoneBindings)
        assertTrue(zoneBindings!!.discrete.containsKey(DiscreteGesture.LONG_PRESS_END))
    }

    @Test
    fun `buildGestureBindings returns empty for unknown mode`() {
        val actions = createGestureActions(GestureCallbacks())
        val bindings = buildGestureBindings(
            mode = com.tsutsen.platformplayer.core.model.PlayerMode.FULLSCREEN,
            specs = mapOf(),
            actions = actions
        )
        assertTrue(bindings.byZone.isEmpty())
    }

    @Test
    fun `brightness zone does not get morph drag bindings`() {
        val specs = buildGestureSpecs(
            mapOf(
                "NORMAL" to mapOf(
                    "MIDDLE_CENTER" to mapOf(
                        "SWIPE_VERTICAL" to "brightness"
                    )
                )
            )
        )

        val actions = createGestureActions(GestureCallbacks())
        val bindings = buildGestureBindings(
            mode = com.tsutsen.platformplayer.core.model.PlayerMode.NORMAL,
            specs = specs,
            actions = actions
        )

        val brightnessZone = GestureZone(GestureRow.MIDDLE, GestureColumn.CENTER)
        val zoneBindings = bindings.byZone[brightnessZone]
        assertNotNull(zoneBindings)
        // Should have VERTICAL_DRAG (brightness) but not morph drag
        assertTrue(zoneBindings!!.continuous.containsKey(ContinuousGesture.VERTICAL_DRAG))
        // No LONG_PRESS_END for brightness zone
        assertTrue(!zoneBindings.discrete.containsKey(DiscreteGesture.LONG_PRESS_END))
    }
}
