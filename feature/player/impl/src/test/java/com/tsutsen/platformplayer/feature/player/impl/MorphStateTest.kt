package com.tsutsen.platformplayer.feature.player.impl

import com.tsutsen.platformplayer.core.model.PlayerMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MorphStateTest {

    private val testDispatcher: TestDispatcher = StandardTestDispatcher()
    private val testScope = CoroutineScope(testDispatcher)

    @Test
    fun `onDrag accumulates across multiple calls`() {
        val state = MorphState(testScope, onMinimize = {})
        state.onDragStart(
            onModeComputed = {},
            onStartProgress = { 0f }
        )
        state.onDrag(deltaY = 50f, dragTravelPx = 500f)
        state.onDrag(deltaY = 50f, dragTravelPx = 500f)
        assertEquals(0.2f, state.progress, 0.001f) // 100/500, not stuck at 50/500
    }

    @Test
    fun `onDrag clamps progress to range`() {
        val state = MorphState(testScope, onMinimize = {})
        state.onDragStart(
            onModeComputed = {},
            onStartProgress = { 0f }
        )
        state.onDrag(deltaY = 600f, dragTravelPx = 500f)
        assertEquals(1f, state.progress, 0.001f)
    }

    @Test
    fun `onDragEnd clears drag state and unlocks mode`() {
        val state = MorphState(testScope, onMinimize = {})
        var minimized = false
        state.onDragStart(
            onModeComputed = {},
            onStartProgress = { 0f }
        )
        state.onDrag(deltaY = 250f, dragTravelPx = 500f) // 0.5 progress
        state.onDragEnd(
            onSnapTo = {},
            onMinimize = { minimized = true }
        )
        assertFalse(state.isDragging)
        assertEquals(PlayerMode.NORMAL, state.lockedGestureMode)
        assertTrue(minimized)
    }

    @Test
    fun `onDragEnd does not minimize below threshold`() {
        val state = MorphState(testScope, onMinimize = {})
        var minimized = false
        state.onDragStart(
            onModeComputed = {},
            onStartProgress = { 0f }
        )
        state.onDrag(deltaY = 100f, dragTravelPx = 500f) // 0.2 progress
        state.onDragEnd(
            onSnapTo = {},
            onMinimize = { minimized = true }
        )
        assertFalse(minimized)
    }

    @Test
    fun `onDragEnd returns false when not dragging`() {
        val state = MorphState(testScope, onMinimize = {})
        var minimized = false
        val result = state.onDragEnd(
            onSnapTo = {},
            onMinimize = { minimized = true }
        )
        assertFalse(result)
        assertFalse(minimized)
    }

    @Test
    fun `onDragStart computes locked mode from start progress`() {
        val state = MorphState(testScope, onMinimize = {})
        var receivedMode: PlayerMode? = null
        state.onDragStart(
            onModeComputed = { mode -> receivedMode = mode },
            onStartProgress = { 0.5f } // Start mid-morph
        )
        assertEquals(PlayerMode.FLOATING, receivedMode)
        assertEquals(PlayerMode.FLOATING, state.lockedGestureMode)
        assertTrue(state.isDragging)
    }

    @Test
    fun `onDrag preserves start progress offset`() {
        val state = MorphState(testScope, onMinimize = {})
        state.onDragStart(
            onModeComputed = {},
            onStartProgress = { 0.3f } // Start at 30%
        )
        state.onDrag(deltaY = 100f, dragTravelPx = 500f) // +20%
        assertEquals(0.5f, state.progress, 0.001f) // 0.3 + 0.2
    }

    @Test
    fun `isDragging reflects phase`() {
        val state = MorphState(testScope, onMinimize = {})
        assertFalse(state.isDragging)

        state.onDragStart(onModeComputed = {}, onStartProgress = { 0f })
        assertTrue(state.isDragging)

        state.onDragEnd(onSnapTo = {}, onMinimize = {})
        assertFalse(state.isDragging)
    }

    @Test
    fun `cancelAnimation returns to Idle phase when not dragging`() {
        val state = MorphState(testScope, onMinimize = {})
        // Start in Idle
        assertFalse(state.isDragging)

        state.cancelAnimation()
        assertFalse(state.isDragging)
        assertEquals(0f, state.progress, 0.01f)
    }

    @Test
    fun `cancelAnimation does not clobber active drag`() {
        val state = MorphState(testScope, onMinimize = {})
        state.onDragStart(
            onModeComputed = {},
            onStartProgress = { 0.5f }
        )
        state.onDrag(deltaY = 50f, dragTravelPx = 500f)
        assertTrue(state.isDragging)

        state.cancelAnimation()
        // Drag should still be active — cancelAnimation only cancels animations, not drags
        assertTrue(state.isDragging)
        assertEquals(PlayerMode.FLOATING, state.lockedGestureMode)
    }

    @Test
    fun `dragTravelPx affects progress calculation`() {
        val state = MorphState(testScope, onMinimize = {})
        state.onDragStart(onModeComputed = {}, onStartProgress = { 0f })
        state.onDrag(deltaY = 50f, dragTravelPx = 100f) // 50% with 100px travel
        assertEquals(0.5f, state.progress, 0.001f)
        
        // With same accumulated drag but different travel, progress changes
        state.onDrag(deltaY = 50f, dragTravelPx = 200f) // 100/200 = 50%
        assertEquals(0.5f, state.progress, 0.001f)
        
        // More drag with same travel
        state.onDrag(deltaY = 50f, dragTravelPx = 200f) // 150/200 = 75%
        assertEquals(0.75f, state.progress, 0.001f)
    }

    @Test
    fun `lockedGestureMode is passthrough from Dragging phase`() {
        val state = MorphState(testScope, onMinimize = {})
        // Before drag: default
        assertEquals(PlayerMode.NORMAL, state.lockedGestureMode)

        state.onDragStart(
            onModeComputed = {},
            onStartProgress = { 0.5f }
        )
        assertEquals(PlayerMode.FLOATING, state.lockedGestureMode)

        state.onDragEnd(onSnapTo = {}, onMinimize = {})
        assertEquals(PlayerMode.NORMAL, state.lockedGestureMode)
    }

    @Test
    fun `onDrag calls without onDragStart throws`() {
        val state = MorphState(testScope, onMinimize = {})
        var threw = false
        try {
            state.onDrag(deltaY = 10f, dragTravelPx = 500f)
        } catch (e: IllegalStateException) {
            threw = true
        }
        assertTrue("onDrag without onDragStart should throw", threw)
    }

    @Test
    fun `onDragStart cancels prior animation job`() {
        val state = MorphState(testScope, onMinimize = {})
        state.animateTo(1f)
        state.onDragStart(onModeComputed = {}, onStartProgress = { 0f })
        // Should be in Dragging phase, not Animating
        assertTrue(state.isDragging)
    }
}
