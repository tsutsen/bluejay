package com.tsutsen.platformplayer.feature.player.impl

import com.tsutsen.platformplayer.core.model.PlayerMode
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerModesTest {

    @Test
    fun `computePlayerMode returns FLOATING above threshold`() {
        assertEquals(
            PlayerMode.FLOATING,
            computePlayerMode(
                miniProgress = 0.02f, // Above threshold (0.01)
                fullscreenProgress = 0f
            )
        )
    }

    @Test
    fun `computePlayerMode returns NORMAL below threshold`() {
        assertEquals(
            PlayerMode.NORMAL,
            computePlayerMode(
                miniProgress = 0.005f, // Below threshold
                fullscreenProgress = 0f
            )
        )
    }

    @Test
    fun `computePlayerMode returns FULLSCREEN above half`() {
        assertEquals(
            PlayerMode.FULLSCREEN,
            computePlayerMode(
                miniProgress = 0f,
                fullscreenProgress = 0.6f
            )
        )
    }

    @Test
    fun `computePlayerMode FLOATING wins over FULLSCREEN`() {
        assertEquals(
            PlayerMode.FLOATING,
            computePlayerMode(
                miniProgress = 0.5f, // FLOATING wins
                fullscreenProgress = 0.6f
            )
        )
    }

    @Test
    fun `computePlayerMode returns COMPACT below height threshold`() {
        assertEquals(
            PlayerMode.COMPACT,
            computePlayerMode(
                miniProgress = 0f,
                fullscreenProgress = 0f,
                playerHeightRatio = 0.25f // Below 0.3
            )
        )
    }

    @Test
    fun `computePlayerMode priority order`() {
        // FLOATING wins
        assertEquals(
            PlayerMode.FLOATING,
            computePlayerMode(
                miniProgress = 0.5f,
                fullscreenProgress = 0f,
                playerHeightRatio = 0.25f
            )
        )

        // FULLSCREEN wins
        assertEquals(
            PlayerMode.FULLSCREEN,
            computePlayerMode(
                miniProgress = 0f,
                fullscreenProgress = 0.6f,
                playerHeightRatio = 0.25f
            )
        )

        // COMPACT wins
        assertEquals(
            PlayerMode.COMPACT,
            computePlayerMode(
                miniProgress = 0f,
                fullscreenProgress = 0f,
                playerHeightRatio = 0.25f
            )
        )

        // NORMAL
        assertEquals(
            PlayerMode.NORMAL,
            computePlayerMode(
                miniProgress = 0f,
                fullscreenProgress = 0f,
                playerHeightRatio = 0.5f
            )
        )
    }
}
