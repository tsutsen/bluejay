package com.tsutsen.platformplayer.player

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.tsutsen.platformplayer.receivers.MediaControlReceiver
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented regression test for the "Source error" slab after a device
 * lock/unlock: playing a REAL YouTube video, locking the screen, waking the
 * device, resuming playback, and asserting that playback recovers to
 * STATE_PLAYING without any PlaybackException being surfaced to the UI.
 *
 * Companion JVM-level coverage lives in core/data SourceRecoveryManagerTest;
 * this test exercises the full on-device path (real network, real plugin
 * stream resolution, real lock/unlock lifecycle).
 *
 * Limitations / assumptions:
 *  - The app has no deep-link entry to open a video by URL (MainActivity does
 *    not consume VIEW/SEND intents), so playback is started by clicking a
 *    video on the home feed, exactly like BluejayBenchmarks does. The home
 *    feed requires the YouTube plugin to be set up and working network; if no
 *    video starts within the timeout, the test ASSUMES (skips cleanly)
 *    instead of failing, so it never false-fails on a device without feed data.
 *  - The fixture is the first item on the home feed (served by the YouTube
 *    plugin). A region-blocked item would fail to start playback before the
 *    lock, which the pre-lock assume check turns into a clean skip.
 *  - Lock duration is configurable via the instrumentation argument
 *    `lockDurationSeconds` (default 30). The regression triggers with a short
 *    lock; use a larger value to chase the long-lock/URL-expire scenario
 *    manually, e.g. `-e lockDurationSeconds 3600` (do not run this in CI).
 *  - Playback state is read via `dumpsys media_session` (UiAutomator runs it
 *    as the shell user), so no production test hook was added.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class SourceErrorLockResumeTest {

    private val device: UiDevice by lazy {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }
    private val targetContext: Context by lazy {
        InstrumentationRegistry.getInstrumentation().targetContext
    }

    /** Configurable lock duration; 30s is enough to trigger the regression. */
    private var lockDurationSeconds: Long = DEFAULT_LOCK_DURATION_SECONDS

    @Before
    fun readArgs() {
        val args = InstrumentationRegistry.getArguments()
        args.getString("lockDurationSeconds")?.toLongOrNull()?.let {
            if (it > 0) lockDurationSeconds = it
        }
    }

    @Test
    fun playbackSurvivesLockUnlock() {
        startApp()
        startPlaybackFromHomeFeed()
        waitForPlayingOrSkip(START_PLAYBACK_TIMEOUT_MS)

        // Lock the screen. UiDevice.sleep() presses the power button.
        device.sleep()
        Thread.sleep(lockDurationSeconds * 1000)
        device.wakeUp()
        device.dismissKeyguard()
        device.waitForIdle()

        // Resume playback via the app's own media control receiver (same
        // mechanism the lock-screen notification play button uses), retrying
        // up to twice.
        var resumed = false
        repeat(RESUME_RETRIES) {
            sendPlay()
            resumed = waitForCondition(RESUME_TIMEOUT_MS / RESUME_RETRIES) { isSessionPlaying() }
            if (resumed) return@repeat
        }
        assertTrue(
            "Playback did not reach STATE_PLAYING within " +
                "${RESUME_TIMEOUT_MS / 1000}s after unlock",
            resumed,
        )

        // The red signal: any surfaced PlaybackException ("Source error",
        // "Unknown error", ...) fails the test.
        assertFalse(
            "Player surfaced an error slab after lock/unlock (Source error)",
            isAnyErrorTextShown(),
        )
    }

    // ---------- playback start ----------

    private fun startApp() {
        device.pressHome()
        val launchIntent = targetContext.packageManager
            .getLaunchIntentForPackage(PACKAGE)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK) }
        Assume.assumeTrue("App not installed on device", launchIntent != null)
        targetContext.startActivity(launchIntent)
        device.wait(Until.hasObject(By.pkg(PACKAGE).depth(0)), APP_LAUNCH_TIMEOUT_MS)
        device.waitForIdle()
    }

    /**
     * Clicks a video on the home feed. Same minimal approach as
     * BluejayBenchmarks: pick the first clickable element in the content
     * area (below the top bar, above the bottom nav) so the nav bar is not
     * hit by accident. There is no deep-link/intent alternative.
     */
    private fun startPlaybackFromHomeFeed() {
        val height = device.displayHeight
        val candidates = device.findObjects(By.clickable(true))
            .filter { clickable ->
                val r = clickable.visibleBounds
                r.centerY() > height / 6 && r.centerY() < height * 5 / 6
            }
        Assume.assumeTrue(
            "No tappable home-feed content found — the feed needs the YouTube " +
                "plugin and network; skipping instead of false-failing",
            candidates.isNotEmpty(),
        )
        candidates.first().click()
        device.waitForIdle()
    }

    // ---------- playback state via dumpsys ----------

    /**
     * Reads `dumpsys media_session`. The media3 PlayerService publishes a
     * MediaSession whose PlaybackState shows `state=3` (STATE_PLAYING) and a
     * live position. No production test hook needed.
     */
    private fun dumpMediaSessions(): String = try {
        device.executeShellCommand("dumpsys media_session")
    } catch (e: Throwable) {
        ""
    }

    private fun isSessionPlaying(): Boolean {
        val dump = dumpMediaSessions()
        return dump.contains(PACKAGE) &&
            (SESSION_PLAYING_REGEX.containsMatchIn(dump) ||
                PLAIN_STATE_PLAYING_REGEX.containsMatchIn(dump))
    }

    /** Searches the visible UI for the surfaced PlaybackException text. */
    private fun isAnyErrorTextShown(): Boolean = ERROR_TEXTS.any { text ->
        device.hasObject(By.textContains(text))
    }

    private fun waitForCondition(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(POLL_INTERVAL_MS)
        }
        return condition()
    }

    private fun waitForPlayingOrSkip(timeoutMs: Long) {
        val started = waitForCondition(timeoutMs) { isSessionPlaying() }
        Assume.assumeTrue(
            "Playback did not start on the home feed within " +
                "${timeoutMs / 1000}s — skipping (no video / no network / no plugin)",
            started,
        )
    }

    private fun sendPlay() {
        // Same broadcast the lock-screen notification play button sends.
        targetContext.sendBroadcast(
            Intent(targetContext, MediaControlReceiver::class.java).apply {
                putExtra(MediaControlReceiver.EXTRA_MEDIA_ACTION, MediaControlReceiver.EVENT_PLAY)
            },
        )
    }

    companion object {
        private const val PACKAGE = "com.tsutsen.platformplayer.d"
        private const val DEFAULT_LOCK_DURATION_SECONDS = 30L
        private const val APP_LAUNCH_TIMEOUT_MS = 15_000L
        private const val START_PLAYBACK_TIMEOUT_MS = 60_000L
        private const val RESUME_TIMEOUT_MS = 120_000L
        private const val RESUME_RETRIES = 2
        private const val POLL_INTERVAL_MS = 1_000L

        /**
         * PlaybackException.message as surfaced through PlayerState.error
         * (see PlayerListeners.onPlayerError) — the "Source error" slab.
         */
        private val ERROR_TEXTS = listOf("Source error", "Unknown error")

        /**
         * Matches the PlaybackState line in dumpsys media_session output,
         * e.g. `PlaybackState {state=3, position=...}` — tolerant to both the
         * short (`state=3`) and verbose form.
         */
        private val SESSION_PLAYING_REGEX =
            Regex("""state\s*=\s*PlaybackState\s*\{\s*state\s*=\s*3""")

        /** Also accept the plain `state=3` form some devices print. */
        private val PLAIN_STATE_PLAYING_REGEX = Regex("""state=3""")
    }
}
