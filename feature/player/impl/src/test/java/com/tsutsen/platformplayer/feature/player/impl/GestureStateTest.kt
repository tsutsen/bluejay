package com.tsutsen.platformplayer.feature.player.impl

import com.tsutsen.platformplayer.core.model.PlayerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureStateTest {

    @Test
    fun `onDrag accumulates across multiple calls`() {
        val state = GestureState()
        state.onDragStart(
            onModeComputed = {},
            onStartProgress = { 0f }
        )
        state.onDrag(deltaY = 50f, dragTravelPx = 500f)
        state.onDrag(deltaY = 50f, dragTravelPx = 500f)
        assertEquals(0.2f, state.dragMorphProgress) // 100/500, not stuck at 50/500
    }

    @Test
    fun `onDrag clamps progress to range`() {
        val state = GestureState()
        state.onDragStart(
            onModeComputed = {},
            onStartProgress = { 0f }
        )
        state.onDrag(deltaY = 600f, dragTravelPx = 500f)
        assertEquals(1f, state.dragMorphProgress)
    }

    @Test
    fun `onDragStart clears stale dragMorphProgress`() {
        val state = GestureState()
        state.dragMorphProgress = 0.5f
        state.onDragStart(
            onModeComputed = {},
            onStartProgress = { 0f }
        )
        assertNull(state.dragMorphProgress)
    }

    @Test
    fun `onDragEnd clears drag state and unlocks mode`() {
        val state = GestureState()
        var minimized = false
        state.onDragStart(
            onModeComputed = {},
            onStartProgress = { 0f }
        )
        state.onDrag(deltaY = 250f, dragTravelPx = 500f) // 0.5 progress
        state.onDragEnd(
            currentProgress = 0.5f,
            onSnapTo = {},
            onMinimize = { minimized = true }
        )
        assertFalse(state.isDraggingMorph)
        assertNull(state.dragMorphProgress)
        assertEquals(PlayerMode.NORMAL, state.lockedGestureMode)
        assertTrue(minimized)
    }

    @Test
    fun `onDragEnd does not minimize below threshold`() {
        val state = GestureState()
        var minimized = false
        state.onDragStart(
            onModeComputed = {},
            onStartProgress = { 0f }
        )
        state.onDrag(deltaY = 100f, dragTravelPx = 500f) // 0.2 progress
        state.onDragEnd(
            currentProgress = 0.2f,
            onSnapTo = {},
            onMinimize = { minimized = true }
        )
        assertFalse(minimized)
    }

    @Test
    fun `onDragEnd returns false when not dragging`() {
        val state = GestureState()
        var minimized = false
        val result = state.onDragEnd(
            currentProgress = 0.5f,
            onSnapTo = {},
            onMinimize = { minimized = true }
        )
        assertFalse(result)
        assertFalse(minimized)
    }

    @Test
    fun `onDragStart computes locked mode from start progress`() {
        val state = GestureState()
        var lockedMode: PlayerMode? = null
        state.onDragStart(
            onModeComputed = { mode -> lockedMode = mode },
            onStartProgress = { 0.5f } // Start mid-morph
        )
        assertEquals(PlayerMode.FLOATING, lockedMode)
        assertTrue(state.isDraggingMorph)
    }

    @Test
    fun `onDrag preserves start progress offset`() {
        val state = GestureState()
        state.onDragStart(
            onModeComputed = {},
            onStartProgress = { 0.3f } // Start at 30%
        )
        state.onDrag(deltaY = 100f, dragTravelPx = 500f) // +20%
        assertEquals(0.5f, state.dragMorphProgress) // 0.3 + 0.2
    }
}
