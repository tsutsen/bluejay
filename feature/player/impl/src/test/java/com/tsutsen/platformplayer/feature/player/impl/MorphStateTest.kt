package com.tsutsen.platformplayer.feature.player.impl

import com.tsutsen.platformplayer.core.model.PlayerMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
        assertEquals(0.2f, state.progress) // 100/500, not stuck at 50/500
    }

    @Test
    fun `onDrag clamps progress to range`() {
        val state = MorphState(testScope, onMinimize = {})
        state.onDragStart(
            onModeComputed = {},
            onStartProgress = { 0f }
        )
        state.onDrag(deltaY = 600f, dragTravelPx = 500f)
        assertEquals(1f, state.progress)
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
        var lockedMode: PlayerMode? = null
        state.onDragStart(
            onModeComputed = { mode -> lockedMode = mode },
            onStartProgress = { 0.5f } // Start mid-morph
        )
        assertEquals(PlayerMode.FLOATING, lockedMode)
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
        assertEquals(0.5f, state.progress) // 0.3 + 0.2
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
    fun `cancelAnimation returns to Idle phase`() {
        val state = MorphState(testScope, onMinimize = {})
        state.onDragStart(onModeComputed = {}, onStartProgress = { 0f })
        assertTrue(state.isDragging)
        
        state.cancelAnimation()
        assertFalse(state.isDragging)
        assertEquals(0f, state.progress, 0.01f)
    }
}
