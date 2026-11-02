package com.tsutsen.platformplayer.feature.player.impl.gesture

import androidx.compose.ui.geometry.Offset
import com.tsutsen.platformplayer.core.model.GestureAction
import com.tsutsen.platformplayer.feature.player.impl.PlayerEvent
import com.tsutsen.platformplayer.feature.player.impl.PlayerEventBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PlayerGestureActionHandler] against an in-memory
 * [PlaybackEffects] fake (the seam the interface split provides). Layout
 * tests use a recording [LayoutEffects]; everything else passes the
 * default no-op implementation.
 */
class GestureActionHandlerTest {

    private class FakePlayback : PlaybackEffects {
        var playing = false
        var speed = 1f
        var positionMs = 0L
        var durMs = 0L
        var defaultSpeedup = 2f
        var speedupSensitivity = 1f
        var jumpStepMs = 5000L
        val seekByCalls = mutableListOf<Long>()
        val seekToCalls = mutableListOf<Long>()
        val speedCalls = mutableListOf<Float>()
        var pauses = 0
        var resumes = 0

        override fun seekBy(deltaMs: Long) { seekByCalls += deltaMs }
        override fun seekTo(positionMs: Long) { seekToCalls += positionMs }
        override fun positionMs(): Long = positionMs
        override fun durationMs(): Long = durMs
        override fun isPlaying(): Boolean = playing
        override fun pause() { pauses++ }
        override fun resume() { resumes++ }
        override fun playbackSpeed(): Float = speed
        override fun setPlaybackSpeed(speed: Float) { speedCalls += speed }
        override fun defaultSpeedup(): Float = defaultSpeedup
        override fun speedupSensitivity(): Float = speedupSensitivity
        override fun jumpStepMs(): Long = jumpStepMs
        override fun screenHeightPx(): Float = 1000f
        override fun brightness(): Float = 0.5f
        override fun applyBrightness(level: Float) {}
        override fun volume(): Float = 0.5f
        override fun setVolume(level: Float) {}
    }

    private class RecordingLayout : LayoutEffects {
        var morphStarts = 0
        val morphDrags = mutableListOf<Float>()
        var morphEndDelta = Float.NaN
        var morphEndVelocity = Float.NaN

        override fun morphDragStart() { morphStarts++ }
        override fun morphDrag(dragY: Float) { morphDrags += dragY }
        override fun morphDragEnd(dragY: Float, velocityPxPerMs: Float) {
            morphEndDelta = dragY
            morphEndVelocity = velocityPxPerMs
        }
    }

    private fun frame(
        action: GestureAction,
        phase: GesturePhase,
        total: Offset = Offset.Zero,
        elapsedMs: Long = 0,
        gestureType: GestureType = GestureType.HOLD,
    ) = GestureFrame(
        sector = GestureSector.MIDDLE_CENTER,
        gestureType = gestureType,
        action = action,
        phase = phase,
        totalDelta = total,
        elapsedMs = elapsedMs,
        fingerPosition = Offset.Zero,
    )

    /**
     * Collects the events emitted by [block] from the global bus. The
     * collector runs on an unconfined dispatcher inside runBlocking, so
     * emissions on the test thread are delivered synchronously.
     */
    private fun collectDuring(maxEvents: Int = 16, block: () -> Unit): List<PlayerEvent> {
        val events = mutableListOf<PlayerEvent>()
        runBlocking {
            val job = launch(Dispatchers.Unconfined) {
                PlayerEventBus.events.take(maxEvents).toCollection(events)
            }
            block()
            job.cancel()
        }
        return events
    }

    // ---- hold-seek: accumulate drift, pause on start, commit + resume on release ----

    @Test
    fun holdSeekAccumulatesDriftAndCommitsOnRelease() {
        val fake = FakePlayback().apply {
            playing = true
            positionMs = 60_000
            durMs = 600_000
        }
        val handler = PlayerGestureActionHandler(fake, object : LayoutEffects {})

        val events = collectDuring {
            handler.handleGestureFrame(frame(GestureAction.SEEK_HOLD, GesturePhase.START))
            assertEquals(1, fake.pauses)
            // 100 px drift: 60s + 100px * 120ms/px = 72s.
            handler.handleGestureFrame(
                frame(GestureAction.SEEK_HOLD, GesturePhase.ACTIVE, total = Offset(100f, 0f), elapsedMs = 100),
            )
            // Cumulative drift 200 px: 60s + 24s = 84s (accumulates from totalDelta).
            handler.handleGestureFrame(
                frame(GestureAction.SEEK_HOLD, GesturePhase.ACTIVE, total = Offset(200f, 0f), elapsedMs = 200),
            )
            handler.handleGestureFrame(frame(GestureAction.SEEK_HOLD, GesturePhase.END))
        }

        assertEquals(listOf<Long>(84_000), fake.seekToCalls)
        assertEquals(1, fake.resumes)
        assertEquals(
            listOf(
                PlayerEvent.SeekPreview(72_000),
                PlayerEvent.SeekPreview(84_000),
                PlayerEvent.SeekCommitted(84_000),
            ),
            events,
        )
    }

    @Test
    fun holdSeekClampsScrubToDurationBounds() {
        val fake = FakePlayback().apply {
            playing = false
            durMs = 100_000
        }
        val handler = PlayerGestureActionHandler(fake, object : LayoutEffects {})

        // Left past the video start → clamp to 0.
        fake.positionMs = 90_000
        handler.handleGestureFrame(frame(GestureAction.SEEK_HOLD, GesturePhase.START))
        handler.handleGestureFrame(
            frame(GestureAction.SEEK_HOLD, GesturePhase.ACTIVE, total = Offset(-1000f, 0f), elapsedMs = 100),
        )
        handler.handleGestureFrame(frame(GestureAction.SEEK_HOLD, GesturePhase.END))
        assertEquals(listOf<Long>(0), fake.seekToCalls)

        // Right past the end → clamp to the duration.
        fake.positionMs = 90_000
        handler.handleGestureFrame(frame(GestureAction.SEEK_HOLD, GesturePhase.START))
        handler.handleGestureFrame(
            frame(GestureAction.SEEK_HOLD, GesturePhase.ACTIVE, total = Offset(1000f, 0f), elapsedMs = 100),
        )
        handler.handleGestureFrame(frame(GestureAction.SEEK_HOLD, GesturePhase.END))
        assertEquals(listOf<Long>(0, 100_000), fake.seekToCalls)
        // Not playing → no pause/resume churn.
        assertEquals(0, fake.pauses)
        assertEquals(0, fake.resumes)
    }

    // ---- speed hold: base = speed × multiplier, ±0.1 per step, restore on release ----

    @Test
    fun speedHoldStepsInTenthsAndRestoresOnRelease() {
        val fake = FakePlayback().apply { speed = 1f } // defaultSpeedup 2 → base 2.0
        val handler = PlayerGestureActionHandler(fake, object : LayoutEffects {})

        handler.handleGestureFrame(frame(GestureAction.SPEEDUP, GesturePhase.START))
        handler.handleGestureFrame(
            frame(GestureAction.SPEEDUP, GesturePhase.ACTIVE, total = Offset(200f, 0f), elapsedMs = 100),
        )
        handler.handleGestureFrame(
            frame(GestureAction.SPEEDUP, GesturePhase.ACTIVE, total = Offset(400f, 0f), elapsedMs = 200),
        )
        handler.handleGestureFrame(frame(GestureAction.SPEEDUP, GesturePhase.END))

        // START applies the base; each 200 px (sensitivity 1) adds 0.1; END restores.
        assertEquals(listOf(2.0f, 2.1f, 2.2f, 1.0f), fake.speedCalls)
    }

    @Test
    fun speedHoldDeadzoneNeverModulates() {
        val fake = FakePlayback().apply { speed = 1f }
        val handler = PlayerGestureActionHandler(fake, object : LayoutEffects {})

        handler.handleGestureFrame(frame(GestureAction.SPEEDUP, GesturePhase.START))
        // 40 px drift is inside the 48 px deadzone: a still hold must not
        // step the speed no matter how the finger jitters.
        handler.handleGestureFrame(
            frame(GestureAction.SPEEDUP, GesturePhase.ACTIVE, total = Offset(40f, 0f), elapsedMs = 100),
        )
        handler.handleGestureFrame(frame(GestureAction.SPEEDUP, GesturePhase.END))

        assertEquals(listOf(2.0f, 1.0f), fake.speedCalls)
    }

    // ---- instant / start-phase jumps ----

    @Test
    fun jumpActionsSeekByTheConfiguredStep() {
        val fake = FakePlayback()
        val handler = PlayerGestureActionHandler(fake, object : LayoutEffects {})

        handler.handleInstantAction(
            InstantActionEvent(GestureSector.MIDDLE_CENTER, GestureAction.REWIND_BACK, Offset.Zero),
        )
        handler.handleInstantAction(
            InstantActionEvent(GestureSector.MIDDLE_CENTER, GestureAction.REWIND_FORWARD, Offset.Zero),
        )
        assertEquals(listOf(-5000L, 5000L), fake.seekByCalls)
    }

    @Test
    fun jumpSlotFiresOnceAtStartAndIgnoresActiveFrames() {
        val fake = FakePlayback()
        val handler = PlayerGestureActionHandler(fake, object : LayoutEffects {})

        handler.handleGestureFrame(frame(GestureAction.REWIND_BACK, GesturePhase.START))
        handler.handleGestureFrame(frame(GestureAction.REWIND_BACK, GesturePhase.ACTIVE, elapsedMs = 100))
        assertEquals(listOf(-5000L), fake.seekByCalls)
    }

    @Test
    fun playPauseInstantTogglesPlayback() {
        val fake = FakePlayback().apply { playing = true }
        val handler = PlayerGestureActionHandler(fake, object : LayoutEffects {})
        handler.handleInstantAction(
            InstantActionEvent(GestureSector.MIDDLE_CENTER, GestureAction.PLAY_PAUSE, Offset.Zero),
        )
        assertEquals(1, fake.pauses)
        assertEquals(0, fake.resumes)

        fake.playing = false
        handler.handleInstantAction(
            InstantActionEvent(GestureSector.MIDDLE_CENTER, GestureAction.PLAY_PAUSE, Offset.Zero),
        )
        assertEquals(1, fake.pauses)
        assertEquals(1, fake.resumes)
    }

    // ---- morph drag: release velocity is the last frame-pair delta ----

    @Test
    fun morphDragSeedsReleaseVelocityFromLastSamplePair() {
        val fake = FakePlayback()
        val layout = RecordingLayout()
        val handler = PlayerGestureActionHandler(fake, layout)

        handler.handleGestureFrame(
            frame(GestureAction.MORPH_TO_FLOATING, GesturePhase.START, gestureType = GestureType.SWIPE_VERTICAL),
        )
        handler.handleGestureFrame(
            frame(
                GestureAction.MORPH_TO_FLOATING, GesturePhase.ACTIVE,
                total = Offset(0f, 50f), elapsedMs = 100, gestureType = GestureType.SWIPE_VERTICAL,
            ),
        )
        handler.handleGestureFrame(
            frame(
                GestureAction.MORPH_TO_FLOATING, GesturePhase.ACTIVE,
                total = Offset(0f, 150f), elapsedMs = 200, gestureType = GestureType.SWIPE_VERTICAL,
            ),
        )
        handler.handleGestureFrame(
            frame(GestureAction.MORPH_TO_FLOATING, GesturePhase.END, gestureType = GestureType.SWIPE_VERTICAL),
        )

        assertEquals(1, layout.morphStarts)
        assertEquals(listOf(50f, 150f), layout.morphDrags)
        // END carries the last drag position and the frame-pair velocity
        // ((150-50) px over (200-100) ms) that seeds the settle spring.
        assertEquals(150f, layout.morphEndDelta, 0.0001f)
        assertEquals(1f, layout.morphEndVelocity, 0.0001f)
        assertTrue(fake.seekToCalls.isEmpty())
    }
}
