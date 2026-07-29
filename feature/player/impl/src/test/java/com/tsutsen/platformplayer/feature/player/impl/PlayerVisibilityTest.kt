package com.tsutsen.platformplayer.feature.player.impl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerVisibilityTest {

    @Test
    fun `normal bar visible at zero progress`() {
        val v = computeControlsVisibility(0f, 0f, 1f, true)
        assertEquals(1f, v.normalBarAlpha, 0.001f)
        assertEquals(0f, v.floatingAlpha, 0.001f)
        assertEquals(0f, v.miniControlsAlpha, 0.001f)
        assertTrue(v.showNormalTopBar)
        assertTrue(!v.showFloatingOverlay)
    }

    @Test
    fun `floating fades in during morph transition range`() {
        assertEquals(0f, computeControlsVisibility(0.3f, 0f, 1f, true).floatingAlpha, 0.001f)
        assertEquals(0.5f, computeControlsVisibility(0.5f, 0f, 1f, true).floatingAlpha, 0.001f)
        assertEquals(1f, computeControlsVisibility(0.7f, 0f, 1f, true).floatingAlpha, 0.001f)
    }

    @Test
    fun `normal to compact crossfade is continuous`() {
        // Test at miniProgress=0.5 (mid-morph) to verify crossfade logic
        val tall = computeControlsVisibility(0.5f, 0f, 0.5f, true)
        assertTrue("normalBarAlpha should be dominant when tall", tall.normalBarAlpha >= 0.5f)
        assertEquals(0f, tall.compactBarAlpha, 0.001f)

        val short = computeControlsVisibility(0.5f, 0f, 0.2f, true)
        assertEquals(0f, short.normalBarAlpha, 0.001f)
        assertTrue("compactBarAlpha should be dominant when short", short.compactBarAlpha >= 0.5f)

        val mid = computeControlsVisibility(0.5f, 0f, 0.3f, true)
        assertTrue("normalBarAlpha should be partial", mid.normalBarAlpha > 0f && mid.normalBarAlpha < 1f)
        assertTrue("compactBarAlpha should be partial", mid.compactBarAlpha > 0f && mid.compactBarAlpha < 1f)
    }

    @Test
    fun `controls hidden when controlsVisible is false`() {
        val v = computeControlsVisibility(0f, 0f, 1f, false)
        assertEquals(0f, v.normalBarAlpha, 0.001f)
        assertEquals(0f, v.compactBarAlpha, 0.001f)
        assertEquals(0f, v.fullscreenBarAlpha, 0.001f)
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
        val v = computeControlsVisibility(0.5f, 1f, 1f, true)
        assertEquals(1f, v.fullscreenBarAlpha, 0.001f)
    }
}
