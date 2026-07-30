package com.tsutsen.platformplayer.feature.player.impl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerVisibilityTest {

    @Test
    fun `normal bar visible at zero progress`() {
        val v = computeControlsVisibility(0f, 0f, 1f, true)
        assertEquals(1f, v.barAlpha, 0.001f)
        assertEquals(0f, v.floatingAlpha, 0.001f)
        assertTrue(v.showBars)
        assertTrue(!v.showFloatingOverlay)
    }

    @Test
    fun `floating fades in during morph transition range`() {
        assertEquals(0f, computeControlsVisibility(0.3f, 0f, 1f, true).floatingAlpha, 0.001f)
        assertEquals(0.5f, computeControlsVisibility(0.5f, 0f, 1f, true).floatingAlpha, 0.001f)
        assertEquals(1f, computeControlsVisibility(0.7f, 0f, 1f, true).floatingAlpha, 0.001f)
    }

    @Test
    fun `bar alpha reflects player height ratio`() {
        val tall = computeControlsVisibility(0f, 0f, 0.5f, true)
        assertTrue("barAlpha should be dominant when tall", tall.barAlpha >= 0.5f)

        val short = computeControlsVisibility(0f, 0f, 0.2f, true)
        assertEquals(0f, short.barAlpha, 0.001f)

        val mid = computeControlsVisibility(0f, 0f, 0.3f, true)
        assertTrue("barAlpha should be partial", mid.barAlpha > 0f && mid.barAlpha < 1f)
    }

    @Test
    fun `bar alpha unaffected by controlsVisible parameter`() {
        // controlsVisible no longer affects barAlpha — visibility animation
        // is handled by controlsVisibleAlpha in PlayerControls.kt
        val v = computeControlsVisibility(0f, 0f, 1f, false)
        assertEquals(1f, v.barAlpha, 0.001f)
    }

    @Test
    fun `config overrides change transition ranges`() {
        val config = PlayerMorphConfig.Default.copy(
            morphTransitionStart = 0.5f,
            morphTransitionEnd = 0.8f,
        )
        assertEquals(0f, computeControlsVisibility(0.5f, 0f, 1f, true, config).floatingAlpha, 0.001f)
        assertEquals(0.5f, computeControlsVisibility(0.65f, 0f, 1f, true, config).floatingAlpha, 0.001f)
        assertEquals(1f, computeControlsVisibility(0.8f, 0f, 1f, true, config).floatingAlpha, 0.001f)
    }

    @Test
    fun `details fade independent of controls visibility`() {
        // Details alpha should use its own transition range, not be gated by controlsVisible
        val v = computeControlsVisibility(0.25f, 0f, 1f, true)
        assertTrue("detailsAlpha should be positive at 0.25 progress", v.detailsAlpha > 0f)
    }

    @Test
    fun `fullscreen bar alpha independent of mini progress`() {
        val v = computeControlsVisibility(0f, 1f, 1f, true)
        assertEquals(1f, v.barAlpha, 0.001f)
    }
}
