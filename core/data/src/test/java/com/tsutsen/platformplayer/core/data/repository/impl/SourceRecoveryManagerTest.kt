package com.tsutsen.platformplayer.core.data.repository.impl

import androidx.media3.common.PlaybackException
import com.tsutsen.platformplayer.core.data.repository.ResolutionResult
import com.tsutsen.platformplayer.core.model.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the "Source error" slab after a long device lock:
 * IO-class playback failures (expired stream URL, repeated connection
 * resets) must trigger a fresh-resolve recovery instead of surfacing a
 * dead player. See [SourceRecoveryManager.isRecoverableIoError].
 */
class SourceRecoveryManagerTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private fun manager(
        telemetry: SourceRecoveryTelemetry = SourceRecoveryTelemetry(),
        resolve: suspend (url: String, positionMs: Long) -> com.tsutsen.platformplayer.core.data.repository.ResolutionResult =
            { _, _ -> ResolutionResult(mediaSource = null, videoDetails = null) },
    ): SourceRecoveryManager =
        SourceRecoveryManager(
            logTag = "SourceRecoveryManagerTest",
            scope = scope,
            state = MutableStateFlow(PlayerState()),
            playerProvider = { null },
            currentPlayUrl = { "https://rr3---sn.example.googlevideo.com/videoplayback?expire=1" },
            currentSourceExpiresAtMs = { null },
            lastPlayingAtMs = { 0L },
            resolve = resolve,
            invalidateSourceCache = { },
            onSourceRebuilt = { },
            telemetry = telemetry,
        )

    private fun ioError(code: Int) = PlaybackException("Source error", null, code)

    @After
    fun tearDown() {
        scope.coroutineContext.cancelChildren()
    }

    @Test
    fun `network connection failure triggers recovery`() {
        runBlocking {
            val mgr = manager()
            mgr.arm()  // play() arms the budget; recovery is off until then
            assertTrue(mgr.attempt(ioError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)))
        }
    }

    @Test
    fun `expired-url http error triggers recovery`() {
        runBlocking {
            // 403 from googlevideo after the ~6h expire window surfaces as BAD_HTTP_STATUS
            val mgr = manager()
            mgr.arm()
            assertTrue(mgr.attempt(ioError(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)))
        }
    }

    @Test
    fun `network connection timeout triggers recovery`() {
        runBlocking {
            val mgr = manager()
            mgr.arm()
            assertTrue(mgr.attempt(ioError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)))
        }
    }

    @Test
    fun `non-io errors do not trigger recovery`() {
        runBlocking {
            val mgr = manager()
            assertFalse(mgr.attempt(ioError(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED)))
            assertFalse(mgr.attempt(ioError(PlaybackException.ERROR_CODE_UNSPECIFIED)))
        }
    }

    @Test
    fun `immediate second attempt is spacing-suppressed`() {
        runBlocking {
            val mgr = manager()
            mgr.arm()
            assertTrue(mgr.attempt(ioError(PlaybackException.ERROR_CODE_IO_UNSPECIFIED)))
            // Same instant: within SOURCE_RECOVERY_MIN_INTERVAL_MS, so no
            // second recovery — surfaces the error instead of looping.
            assertFalse(mgr.attempt(ioError(PlaybackException.ERROR_CODE_IO_UNSPECIFIED)))
        }
    }

    @Test
    fun `io recovery increments telemetry counters per cause`() {
        runBlocking {
            val telemetry = SourceRecoveryTelemetry()
            val mgr = manager(telemetry)
            mgr.arm()
            assertTrue(mgr.attempt(ioError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)))
            val snapshot = telemetry.snapshot()
            assertEquals(1L, snapshot["errorRecoveries"])
            assertEquals(1L, snapshot["errorRecovery:io:ERROR_CODE_IO_NETWORK_CONNECTION_FAILED"])
        }
    }

    @Test
    fun `non-io rejection does not count as recovery`() {
        runBlocking {
            val telemetry = SourceRecoveryTelemetry()
            val mgr = manager(telemetry)
            mgr.arm()
            assertFalse(mgr.attempt(ioError(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED)))
            assertEquals(0L, telemetry.snapshot()["errorRecoveries"] ?: 0L)
        }
    }

    @Test
    fun `recovery failure counted when resolve returns null mediaSource`() {
        runBlocking {
            val telemetry = SourceRecoveryTelemetry()
            val mgr = manager(telemetry)
            mgr.arm()
            assertTrue(mgr.attempt(ioError(PlaybackException.ERROR_CODE_IO_UNSPECIFIED)))
            // The recovery body runs on the test scope; wait for it to
            // observe the null MediaSource and record the failure.
            val deadline = System.currentTimeMillis() + 5_000
            while ((telemetry.snapshot()["recoveryFailures"] ?: 0L) == 0L &&
                System.currentTimeMillis() < deadline
            ) {
                Thread.sleep(50)
            }
            assertEquals(1L, telemetry.snapshot()["recoveryFailures"])
        }
    }
}
