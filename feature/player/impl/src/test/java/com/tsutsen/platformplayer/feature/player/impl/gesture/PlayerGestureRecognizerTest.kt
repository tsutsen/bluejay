package com.tsutsen.platformplayer.feature.player.impl.gesture

import com.tsutsen.platformplayer.feature.player.impl.PlayerOverlayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Replay-tests for the pure gesture decision state machine. Times are
 * explicit; no Compose, no coroutines, no wall clock.
 */
class PlayerGestureRecognizerTest {

    // A sector config with every slot bound.
    private val cfg = GestureConfig().withSector(
        GestureSector.MIDDLE_CENTER,
        GestureSlotConfig(
            swipeVertical = GestureAction.VOLUME,
            swipeHorizontal = GestureAction.BRIGHTNESS,
            doubleTap = GestureAction.REWIND_FORWARD,
            hold = GestureAction.SPEEDUP
        )
    )
    private val noneCfg = GestureConfig() // all slots NONE

    private val surfaceW = 800f
    private val surfaceH = 450f
    private val slop = 50f

    private fun down(
        r: PlayerGestureRecognizer,
        x: Float = 400f, y: Float = 225f, time: Long = 1000L,
        mode: PlayerOverlayMode = PlayerOverlayMode.FULLSCREEN,
        config: GestureConfig = cfg,
    ) = r.onDown(x, y, time, surfaceW, surfaceH, mode, config, slop)

    /** Middle-center sector: MIDDLE_CENTER in all four modes except where noted. */
    // ---- tap / double tap ----

    @Test
    fun singleTapIsDeferred() {
        val r = PlayerGestureRecognizer()
        down(r)
        val up = r.onUp(1050L)
        assertTrue(up is PlayerGestureRecognizer.UpResult.TapDeferred)
    }

    @Test
    fun secondTapWithinWindowAndSlopFiresDoubleTap() {
        val r = PlayerGestureRecognizer()
        down(r, x = 400f, y = 225f, time = 1000L)
        r.onUp(1050L)
        // 250 ms later, 11 px away — inside both limits.
        val d = down(r, x = 411f, y = 230f, time = 1250L)
        assertTrue(d.doubleTap)
        assertEquals(GestureAction.REWIND_FORWARD, d.instantAction!!.action)
    }

    @Test
    fun secondTapBeyondTimeoutIsNotDoubleTap() {
        val r = PlayerGestureRecognizer()
        down(r, x = 400f, y = 225f, time = 1000L)
        r.onUp(1050L)
        val d = down(r, x = 411f, y = 230f, time = 1350L) // 350 ms > 300
        assertFalse(d.doubleTap)
    }

    @Test
    fun secondTapBeyondSlopIsNotDoubleTap() {
        val r = PlayerGestureRecognizer()
        down(r, x = 400f, y = 225f, time = 1000L)
        r.onUp(1050L)
        val d = down(r, x = 460f, y = 230f, time = 1250L) // 60 px > 50 slop
        assertFalse(d.doubleTap)
    }

    @Test
    fun doubleTapInNoneSlotStillConsumesTap() {
        val r = PlayerGestureRecognizer()
        down(r, x = 400f, y = 225f, time = 1000L, config = noneCfg)
        r.onUp(1050L)
        val d = down(r, x = 411f, y = 230f, time = 1250L, config = noneCfg)
        assertTrue(d.doubleTap)
        assertNull(d.instantAction)
    }

    // ---- slide ----

    @Test
    fun verticalSlideEmitsStartActiveAndEndFrames() {
        val r = PlayerGestureRecognizer()
        down(r, x = 400f, y = 250f) // FULLSCREEN middle row
        val move = r.onMove(400f, 290f, 1050L) // 40 px down > 30 threshold
        val slide = move as PlayerGestureRecognizer.MoveResult.SlideStart
        assertEquals(GesturePhase.START, slide.startFrame!!.phase)
        assertEquals(GestureAction.VOLUME, slide.startFrame!!.action)

        val active = r.onSlideMove(400f, 310f, 1080L)!!
        assertEquals(GesturePhase.ACTIVE, active.phase)
        // First ACTIVE frame: instant delta is measured from the down position.
        assertEquals(60f, active.instantDelta.y)
        assertEquals(60f, active.totalDelta.y)

        val end = r.onSlideEnd(1100L)!!
        assertEquals(GesturePhase.END, end.phase)
        // END is dispatched exactly once.
        assertNull(r.onSlideEnd(1101L))
        assertNull(r.cancel(1102L))
    }

    @Test
    fun jitterBelowThresholdDoesNotStartSlide() {
        val r = PlayerGestureRecognizer()
        down(r)
        val move = r.onMove(410f, 232f, 1050L) // ~11 px
        assertTrue(move is PlayerGestureRecognizer.MoveResult.Idle)
        val up = r.onUp(1060L)
        assertTrue(up is PlayerGestureRecognizer.UpResult.TapDeferred)
    }

    @Test
    fun unboundSlideSlotDrainsWithoutFrames() {
        val r = PlayerGestureRecognizer()
        down(r, config = noneCfg)
        val move = r.onMove(400f, 290f, 1050L)
        val slide = move as PlayerGestureRecognizer.MoveResult.SlideStart
        assertNull(slide.startFrame)
        assertNull(r.onSlideMove(400f, 310f, 1080L))
        assertNull(r.onSlideEnd(1100L))
    }

    @Test
    fun horizontalSlideResolvesHorizontalAction() {
        val r = PlayerGestureRecognizer()
        down(r)
        val move = r.onMove(440f, 250f, 1050L) // 40 px right
        val slide = move as PlayerGestureRecognizer.MoveResult.SlideStart
        assertEquals(GestureType.SWIPE_HORIZONTAL, slide.startFrame!!.gestureType)
        assertEquals(GestureAction.BRIGHTNESS, slide.startFrame!!.action)
    }

    // ---- morph drag resolution ----

    @Test
    fun upwardVerticalSlideInNormalModeEmitsAssignedAction() {
        val r = PlayerGestureRecognizer()
        down(r, mode = PlayerOverlayMode.NORMAL, config = cfg)
        val move = r.onMove(400f, 190f, 1050L) // 60 px up
        val slide = move as PlayerGestureRecognizer.MoveResult.SlideStart
        assertEquals(GesturePhase.START, slide.startFrame!!.phase)
        assertEquals(GestureAction.VOLUME, slide.startFrame!!.action)
    }

    // ---- hold ----

    @Test
    fun holdFiresAfterTimeoutAndModulatesOnDrift() {
        val r = PlayerGestureRecognizer()
        down(r)
        val start = r.onHoldTimeout(1500L)!!
        assertEquals(GesturePhase.START, start.phase)
        assertEquals(GestureAction.SPEEDUP, start.action)

        val active = r.onMove(405f, 228f, 1550L).let {
            (it as PlayerGestureRecognizer.MoveResult.Idle).frames.single()
        }
        assertEquals(GesturePhase.ACTIVE, active.phase)
        assertEquals(5f, active.totalDelta.x)

        val end = (r.onUp(1600L) as PlayerGestureRecognizer.UpResult.End).frame!!
        assertEquals(GesturePhase.END, end.phase)
        assertNull(r.cancel(1601L))
    }

    @Test
    fun holdInNoneSlotSwallowsTap() {
        val r = PlayerGestureRecognizer()
        down(r, config = noneCfg)
        assertNull(r.onHoldTimeout(1500L)) // no frame, but the hold still fires
        val up = r.onUp(1600L)
        // Not a tap — holding 500 ms in a NONE slot consumes the gesture.
        assertTrue(up is PlayerGestureRecognizer.UpResult.End)
        assertNull((up as PlayerGestureRecognizer.UpResult.End).frame)
    }

    @Test
    fun holdWatchdogFiresOnlyOnce() {
        val r = PlayerGestureRecognizer()
        down(r)
        r.onHoldTimeout(1500L)
        assertNull(r.onHoldTimeout(1501L))
    }

    @Test
    fun holdWatchdogStaysSilentAfterSwipeThreshold() {
        val r = PlayerGestureRecognizer()
        down(r)
        r.onMove(400f, 290f, 1050L) // resolved to slide before the watchdog
        assertNull(r.onHoldTimeout(1500L))
    }

    @Test
    fun holdWithHorizontalDriftStaysHold() {
        val r = PlayerGestureRecognizer()
        down(r)
        r.onHoldTimeout(1500L)
        // 40 px horizontal — locked to the hold, no hand-off.
        val move = r.onMove(440f, 250f, 1550L)
        assertTrue(move is PlayerGestureRecognizer.MoveResult.Idle)
        assertTrue((move as PlayerGestureRecognizer.MoveResult.Idle).frames.isNotEmpty())
    }

    @Test
    fun holdWithVerticalDriftKeepsHold() {
        val r = PlayerGestureRecognizer()
        down(r)
        r.onHoldTimeout(1500L)!!
        // 45 px vertical past the threshold — the hold owns the finger,
        // no slide may start from under it; the hold keeps modulating.
        val move = r.onMove(400f, 295f, 1550L)
        val active = (move as PlayerGestureRecognizer.MoveResult.Idle).frames.single()
        assertEquals(GesturePhase.ACTIVE, active.phase)
        assertEquals(GestureAction.SPEEDUP, active.action)
        // Release ends the hold exactly once.
        val end = (r.onUp(1600L) as PlayerGestureRecognizer.UpResult.End).frame!!
        assertEquals(GesturePhase.END, end.phase)
        assertNull(r.cancel(1601L))
    }

    // ---- END-frame exactly-once under cancellation ----

    @Test
    fun cancelMidSlideSendsEndExactlyOnce() {
        val r = PlayerGestureRecognizer()
        down(r)
        r.onMove(400f, 290f, 1050L) // slide started
        r.onSlideMove(400f, 310f, 1080L)
        val end = r.cancel(1100L)
        assertEquals(GesturePhase.END, end!!.phase)
        assertNull(r.cancel(1101L)) // idempotent
    }

    @Test
    fun cancelMidHoldSendsEndExactlyOnce() {
        val r = PlayerGestureRecognizer()
        down(r)
        r.onHoldTimeout(1500L)
        val end = r.cancel(1550L)
        assertEquals(GesturePhase.END, end!!.phase)
        assertNull(r.cancel(1551L))
    }

    @Test
    fun cancelAfterTapSendsNothing() {
        val r = PlayerGestureRecognizer()
        down(r)
        r.onUp(1050L)
        assertNull(r.cancel(1051L))
    }

    // ---- config decides what a v-swipe slide does (regression: morph used
    // to be hard-coded per mode/direction, swallowing user assignments) ----

    @Test
    fun normalModeDownSwipeWithVolumeAssignmentEmitsVolumeFrames() {
        val r = PlayerGestureRecognizer()
        val volumeCfg = GestureConfig().withSector(
            GestureSector.MIDDLE_CENTER,
            GestureSlotConfig(swipeVertical = GestureAction.VOLUME),
        )
        down(r, mode = PlayerOverlayMode.NORMAL, config = volumeCfg)
        // Downward v-swipe in normal mode — previously always a morph drag.
        val start = r.onMove(400f, 290f, 1050L)
        assertTrue(start is PlayerGestureRecognizer.MoveResult.SlideStart)
        assertEquals(GestureAction.VOLUME, (start as PlayerGestureRecognizer.MoveResult.SlideStart).startFrame!!.action)
        val active = r.onSlideMove(400f, 330f, 1080L)
        assertEquals(GestureAction.VOLUME, active!!.action)
        assertEquals(GesturePhase.ACTIVE, active.phase)
    }

    @Test
    fun fullscreenTopDownSwipeWithMorphToNormalAssignmentEmitsMorphFrames() {
        val r = PlayerGestureRecognizer()
        val cfg2 = GestureConfig().withSector(
            GestureSector.TOP_CENTER,
            GestureSlotConfig(swipeVertical = GestureAction.MORPH_TO_NORMAL),
        )
        // TOP_CENTER sector: top row, center column.
        down(r, x = 400f, y = 50f, mode = PlayerOverlayMode.FULLSCREEN, config = cfg2)
        val start = r.onMove(400f, 120f, 1050L)
        assertTrue(start is PlayerGestureRecognizer.MoveResult.SlideStart)
        assertEquals(GestureAction.MORPH_TO_NORMAL, (start as PlayerGestureRecognizer.MoveResult.SlideStart).startFrame!!.action)
    }

    @Test
    fun morphVerticalAssignmentStillEmitsMorphFramesBothDirections() {
        val r = PlayerGestureRecognizer()
        val cfg2 = GestureConfig().withSector(
            GestureSector.MIDDLE_CENTER,
            GestureSlotConfig(swipeVertical = GestureAction.MORPH_VERTICAL),
        )
        down(r, mode = PlayerOverlayMode.NORMAL, config = cfg2)
        val up = r.onMove(400f, 160f, 1050L) // swipe up
        assertTrue(up is PlayerGestureRecognizer.MoveResult.SlideStart)
        assertEquals(GestureAction.MORPH_VERTICAL, (up as PlayerGestureRecognizer.MoveResult.SlideStart).startFrame!!.action)

        val r2 = PlayerGestureRecognizer()
        down(r2, mode = PlayerOverlayMode.NORMAL, config = cfg2)
        val downResult = r2.onMove(400f, 290f, 1050L) // swipe down
        assertTrue(downResult is PlayerGestureRecognizer.MoveResult.SlideStart)
        assertEquals(GestureAction.MORPH_VERTICAL, (downResult as PlayerGestureRecognizer.MoveResult.SlideStart).startFrame!!.action)
    }

    @Test
    fun unboundVSwipeStillDrainsWithoutFrames() {
        val r = PlayerGestureRecognizer()
        down(r, mode = PlayerOverlayMode.NORMAL, config = noneCfg)
        val start = r.onMove(400f, 290f, 1050L)
        assertTrue(start is PlayerGestureRecognizer.MoveResult.SlideStart)
        assertNull((start as PlayerGestureRecognizer.MoveResult.SlideStart).startFrame)
    }

    @Test
    fun newGestureAfterFinishedGestureResetsState() {
        val r = PlayerGestureRecognizer()
        down(r)
        r.onMove(400f, 290f, 1050L)
        r.onSlideEnd(1100L)
        // A fresh gesture must not inherit the previous slide state.
        down(r, time = 2000L)
        val move = r.onMove(405f, 228f, 2050L) // small drift, no slide
        assertTrue(move is PlayerGestureRecognizer.MoveResult.Idle)
        val up = r.onUp(2060L)
        assertTrue(up is PlayerGestureRecognizer.UpResult.TapDeferred)
    }
}
