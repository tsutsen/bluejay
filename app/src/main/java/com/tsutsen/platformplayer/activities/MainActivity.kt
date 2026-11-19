package com.tsutsen.platformplayer.activities
import com.tsutsen.platformplayer.di.SettingsRepositoryImpl

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.util.DisplayMetrics
import android.util.Log
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.Display
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.pip.PictureInPictureDelegate
import androidx.core.pip.VideoPlaybackPictureInPicture
import androidx.lifecycle.lifecycleScope
import com.tsutsen.platformplayer.Settings
import com.tsutsen.platformplayer.compose.BluejayAppThemeRoot
import com.tsutsen.platformplayer.compose.BluejayNavGraph
import com.tsutsen.platformplayer.gettingstarted.GettingStartedFlow
import com.tsutsen.platformplayer.core.data.repository.ChannelRepository
import com.tsutsen.platformplayer.core.data.repository.HomeRepository
import com.tsutsen.platformplayer.core.data.repository.LibraryRepository
import com.tsutsen.platformplayer.core.data.repository.PlayerRepository
import com.tsutsen.platformplayer.core.data.repository.SettingsRepository
import com.tsutsen.platformplayer.core.ui.GamepadKeyBus
import com.tsutsen.platformplayer.core.datastore.model.ThemeMode
import com.tsutsen.platformplayer.core.designsystem.layout.AppLayout
import com.tsutsen.platformplayer.core.designsystem.layout.AppNavigationChrome
import com.tsutsen.platformplayer.core.designsystem.layout.NavItemDef
import com.tsutsen.platformplayer.core.designsystem.layout.bluejayNavItems
import com.tsutsen.platformplayer.core.designsystem.layout.rememberAppLayoutConfig
import com.tsutsen.platformplayer.core.navigation.NavDestination
import com.tsutsen.platformplayer.core.navigation.Navigator
import com.tsutsen.platformplayer.feature.player.impl.PipSurface
import com.tsutsen.platformplayer.feature.player.impl.PlayerView
import com.tsutsen.platformplayer.feature.player.impl.SystemControls
import com.tsutsen.platformplayer.states.StateApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Compose-based MainActivity for Bluejay.
 * Hosts AppLayout with BluejayNavGraph, and drives the second-screen
 * CompanionPresentation on the rear display from the "dual screen" setting.
 */
@AndroidEntryPoint
class MainActivity :
    ComponentActivity(),
    IWithResultLauncher {
    @Inject
    lateinit var navigator: Navigator

    @Inject
    lateinit var playerRepository: PlayerRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var libraryRepository: LibraryRepository

    @Inject
    lateinit var homeRepository: HomeRepository

    @Inject
    lateinit var downloadsRepository: com.tsutsen.platformplayer.core.data.repository.DownloadsRepository

    @Inject
    lateinit var playbackQueueRepository: com.tsutsen.platformplayer.core.data.repository.PlaybackQueueRepository

    @Inject
    lateinit var liveChatRepository: com.tsutsen.platformplayer.core.data.repository.LiveChatRepository

    @Inject
    lateinit var channelRepository: ChannelRepository

    @Inject
    lateinit var historyTracker: com.tsutsen.platformplayer.feature.player.impl.HistoryTracker

    @Inject
    lateinit var subscriptionDao: com.tsutsen.platformplayer.core.database.dao.SubscriptionDao

    /** Jetpack PiP delegate (androidx.core:core-pip), used canonically below. */
    private lateinit var pip: VideoPlaybackPictureInPicture

    /** System picture-in-picture active (video-only window). */
    internal val pipActive = MutableStateFlow(false)

    private var companionPresentation: CompanionPresentation? = null
    private val resultLauncher =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts
                .StartActivityForResult(),
        ) { result: ActivityResult ->
            _pendingResultHandler?.invoke(result)
            _pendingResultHandler = null
        }

    /** One-shot: system notifications (downloads, media) are invisible without it on API 33+. */
    private val notificationPermissionLauncher =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts
                .RequestPermission(),
        ) { /* Denied: user can enable it later in system settings. */ }

    override fun launchForResult(
        intent: Intent,
        code: Int,
        handler: (ActivityResult) -> Unit,
    ) {
        // Store handler for the result callback
        _pendingResultHandler = handler
        resultLauncher.launch(intent)
    }

    private var _pendingResultHandler: ((ActivityResult) -> Unit)? = null

    /** TEMPORARY PiP debug probe - remove once the lifecycle map is settled. */
    private fun pipLog(tag: String) {
        Log.d(
            "BJPIP",
            "$tag: inPip=$isInPictureInPictureMode pipActive=${pipActive.value} " +
                "state=${lifecycle.currentState} playing=${playerRepository.playerState.value.isPlaying}",
        )
    }

    override fun onRestart() {
        super.onRestart()
        pipLog("onRestart")
    }

    override fun onPause() {
        super.onPause()
        pipLog("onPause")
    }

    private val displayListener = object : android.hardware.display.DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            // A display appearing (dock/wake/re-enumeration) may be the
            // companion panel — retry now instead of waiting for the timer.
            mainHandler.post { maybeApplyDisplayAssignment(); ensureCompanion() }
        }

        override fun onDisplayChanged(displayId: Int) {
            // Power-state transitions (STATE_OFF -> STATE_ON) are exactly the
            // wake race that made companionDisplay() return null.
            mainHandler.post { maybeApplyDisplayAssignment(); ensureCompanion() }
        }

        override fun onDisplayRemoved(displayId: Int) {
            mainHandler.post { ensureCompanion() }
        }
    }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onStart() {
        super.onStart()
        pipLog("onStart")
        // PiP UI state is deliberately NOT synced here: onStart fires
        // mid-expansion while the window size is still settling. The flip
        // happens in onResume, when the window is final, so the surface
        // swap never happens mid-transition.
        // Re-assert the dual-screen setting on every return to the
        // foreground. The rear display can still be in its wake/return
        // transition when onStart fires (STATE_OFF), so retry a couple of
        // times — ensureCompanion() is a no-op once the presentation shows.
        (getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
            .registerDisplayListener(displayListener, mainHandler)
        maybeApplyDisplayAssignment()
        ensureCompanion()
        lifecycleScope.launch {
            for (delay in longArrayOf(2_500L, 6_000L)) {
                delay(delay)
                if (isFinishing || isDestroyed) return@launch
                maybeApplyDisplayAssignment()
                ensureCompanion()
            }
        }
    }

    internal fun ensureCompanion() {
        if (isFinishing || isDestroyed) return
        // Never churn the companion window during a PiP transition:
        // dismiss/re-show races the system's PiP window reconfiguration
        // (WM input-channel disposal errors) and leaves the task in a
        // state the launcher can no longer bring forward.
        // Gate on our own state, not the system flag (which can stick
        // true after a jammed close — see onResume's self-heal).
        if (pipActive.value) return
        val enabled = settingsRepository.preferences.value.dualScreen
        val display = companionDisplay()
        if (!enabled || display == null) {
            companionPresentation?.dismiss()
            companionPresentation = null
            // If the swapped path launched the activity companion, it has
            // to go too — the companion only exists while dual-screen is
            // live (onStart re-asserts it).
            CompanionNavBus.finishCompanionActivity()
            return
        }
        // Swapped: the companion must take the DEFAULT (front) display while
        // the main UI sits on the non-default one. A Presentation cannot
        // show on the default display (show() throws InvalidDisplayException
        // by design) — host the companion UI in a real activity there
        // instead (Cocoon's ExternalDisplayActivity pattern). The unswapped
        // path below keeps the Presentation.
        // Decide from PREFS, not the activity's current display: on a toggle
        // the task takes a moment to actually move, and keying off the
        // current location left the companion stranded in front of the
        // returning main UI (blank screen until a back gesture).
        val swappedWanted =
            settingsRepository.preferences.value.let { it.dualScreen && it.dualScreenSwapDisplays }
        if (swappedWanted) {
            companionPresentation?.dismiss()
            companionPresentation = null
            launchCompanionActivityOnDefaultDisplay()
            return
        }
        // Normal (unswapped) path: the companion lives on a Presentation on
        // the other display — make sure no leftover activity companion is
        // squatting on the default display from a previous swap.
        CompanionNavBus.finishCompanionActivity()
        val current = companionPresentation
        if (current != null && current.isShowing) {
            // Recreate if the environment moved us to another display (the
            // swap relaunch): a presentation pinned to the old display would
            // stack main + companion UI on the same screen and leave the
            // other one blank.
            val wantId = display?.displayId
            if (wantId != null && current.display?.displayId == wantId) return
        }
        current?.dismiss()
        // Re-validate at show() time: the display can drop STATE_ON / detach
        // between companionDisplay()'s check and the window add (wake/return
        // transitions, the swap relaunch) — Presentation.show() then throws
        // InvalidDisplayException and crashes the app.
        if (!display.isValid || display.state != Display.STATE_ON) {
            android.util.Log.w("MainActivity", "[swap-dbg] display ${display.displayId} not ready (valid=${display.isValid} state=${display.state}); skip")
            return
        }
        android.util.Log.w("MainActivity", "[swap-dbg] show companion on display ${display.displayId} (own=${display.displayId})")
        // Some ROMs reject TYPE_PRESENTATION windows added to the DEFAULT
        // display ("specified display can not be found") unless the window
        // context is that display's own context.
        val presentationContext =
            if (display.displayId == Display.DEFAULT_DISPLAY) createDisplayContext(display) else this
        companionPresentation =
            CompanionPresentation(
                context = presentationContext,
                display = display,
                playerRepository = playerRepository,
                libraryRepository = libraryRepository,
                homeRepository = homeRepository,
                settingsRepository = settingsRepository,
                downloadsRepository = downloadsRepository,
                playbackQueueRepository = playbackQueueRepository,
                liveChatRepository = liveChatRepository,
                channelRepository = channelRepository,
                historyTracker = historyTracker,
                subscriptionDao = subscriptionDao,
                // Tapping the channel badge on the second screen navigates
                // the main screen to the channel page.
                onChannelClick = { url -> navigator.navigateToChannel(url) },
                // Tapping a library playlist title opens the playlist on the
                // main screen (same "playlist:<id>" URL the library cards use).
                onPlaylistClick = { url -> navigator.navigateToPlaylist(url) },
                // Tapping the stats card opens the watch-stats detail on the
                // main screen.
                onWatchStats = { navigator.navigateWatchStatsDetail() },
            )
        try {
            companionPresentation?.show()
        } catch (e: android.view.WindowManager.InvalidDisplayException) {
            // NOTE: InvalidDisplayException extends RuntimeException, not
            // IllegalArgumentException. The race is handled by the timed
            // retries in onStart — the next ensureCompanion() re-validates
            // and picks a fresh display.
            android.util.Log.w("MainActivity", "Companion display vanished before show(); will retry (own=${display.displayId}, target=${display.displayId}, msg=${e.message})")
            companionPresentation?.dismiss()
            companionPresentation = null
        }
    }

    /**
     * Swapped mode: launch (or re-assert) the companion UI as a real
     * activity on the DEFAULT display. Dedupe via [CompanionNavBus]'s
     * weak reference — ensureCompanion() runs on every display event, and
     * startActivity on an already-running singleTask instance would still
     * churn it to the front.
     */
    private fun launchCompanionActivityOnDefaultDisplay() {
        // Reinstall the nav callbacks: they point at this instance's
        // navigator, which changes across the swap relaunch.
        installCompanionNavCallbacks()
        val existing = CompanionNavBus.companionActivityRef?.get()
        if (existing != null && !existing.isFinishing && !existing.isDestroyed) {
            return
        }
        val intent = Intent(this, CompanionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options =
            android.app.ActivityOptions.makeBasic()
                .setLaunchDisplayId(Display.DEFAULT_DISPLAY)
                .toBundle()
        try {
            startActivity(intent, options)
        } catch (e: Exception) {
            // Some ROMs decline cross-display activity launches (display
            // mid-transition, OEM task controls). The timed retries in
            // onStart re-run ensureCompanion(), which lands here again.
            android.util.Log.w("MainActivity", "CompanionActivity launch failed; will retry (msg=${e.message})")
        }
    }

    /** Route second-screen taps back to this activity's navigator. */
    private fun installCompanionNavCallbacks() {
        CompanionNavBus.onChannelClick = { url -> navigator.navigateToChannel(url) }
        CompanionNavBus.onPlaylistClick = { url -> navigator.navigateToPlaylist(url) }
        CompanionNavBus.onWatchStats = { navigator.navigateWatchStatsDetail() }
    }

    /**
     * Display the companion UI should live on: the first valid, powered-on
     * display that is NOT the one this activity is currently on. Normally
     * that's the first non-default display; with "Swap screens" enabled and
     * a successful move, the activity itself sits on the physical rear panel
     * and the companion takes the default (front) display.
     */
    private fun companionDisplay(): Display? {
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val ownDisplayId =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display.displayId
            else Display.DEFAULT_DISPLAY
        return displayManager.displays.firstOrNull {
            it.displayId != ownDisplayId && it.isValid && it.state == Display.STATE_ON
        }
    }

    /**
     * "Swap screens": move this activity (the main video UI) onto the
     * non-default physical display so users on devices whose primary panel
     * is the "wrong" one (e.g. Anbernic RG DS bottoms vs AYN Thor tops) can
     * choose which screen hosts which interface. Moves via a relaunch with
     * ActivityOptions.setLaunchDisplayId — public API has no in-place task
     * move. The relaunchedForDisplay extra guards against relaunch loops;
     * if the ROM declines the move we simply stay put (companion picks the
     * other screen via companionDisplay(), i.e. today's behavior).
     */
    /**
     * Bring this activity onto the display the current prefs say the main UI
     * belongs on: the non-default physical panel while "Swap screens" is on,
     * the default display otherwise. Relaunches via
     * ActivityOptions.setLaunchDisplayId — the system relocates the running
     * singleTask task in place (no finish(); finishing first kills the task
     * before the move lands). Only fires when we are NOT already on the
     * target, so pref changes can call this directly without loop guards.
     */
    private fun maybeApplyDisplayAssignment() {
        if (isFinishing || isDestroyed || pipActive.value) return
        val prefs = settingsRepository.preferences.value
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val targetId =
            if (prefs.dualScreen && prefs.dualScreenSwapDisplays) {
                displayManager.displays.firstOrNull {
                    it.displayId != Display.DEFAULT_DISPLAY && it.isValid &&
                        it.state == Display.STATE_ON
                }?.displayId ?: Display.DEFAULT_DISPLAY
            } else {
                Display.DEFAULT_DISPLAY
            }
        val ownId =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display.displayId
            else Display.DEFAULT_DISPLAY
        if (ownId == targetId) return
        val intent = intent
            .setClass(this, MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        val options =
            android.app.ActivityOptions.makeBasic().setLaunchDisplayId(targetId).toBundle()
        // Freeze-frame mask: snapshot the current content, hand it to the
        // relaunched instance via SwapFreezeFrame, and cover this window so
        // the display being left shows a static frame instead of a hole or
        // splash while the system tears the window down. Best-effort — a
        // failed capture/overlay just means the plain relaunch flash.
        val freezeFrame = SwapFreezeFrame.capture(this)
        SwapFreezeFrame.prepareRelaunch(this, freezeFrame, targetId)
        startActivity(intent, options)
    }

    override fun onResume() {
        super.onResume()
        pipLog("onResume")
        // Self-heal: if modeChanged(false) was dropped and pipActive stuck
        // true, a fullscreen-sized window proves we're out of PiP — trust
        // the window, clear the state, no relaunch.
        if (pipActive.value && !windowIsPipSized()) {
            pipLog("clearing stuck pip state (window is fullscreen-sized)")
            pipActive.value = false
            ensureCompanion()
        }
    }

    /** True while our window is the small PiP window (vs a full app window). */
    private fun windowIsPipSized(): Boolean {
        if (Build.VERSION.SDK_INT < 30) return pipActive.value
        val wm = windowManager.currentWindowMetrics.bounds
        val real = DisplayMetrics()
        (getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
            .getDisplay(Display.DEFAULT_DISPLAY)
            .getRealMetrics(real)
        return wm.width() < real.widthPixels * 0.8f || wm.height() < real.heightPixels * 0.8f
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // When returning from the lock screen the displays are still in their
        // wake transition when onStart fires. By the time our window has
        // focus the displays are up, so re-assert (no-op if already shown).
        if (hasFocus) ensureCompanion()
    }

    /**
     * System picture-in-picture: leaving the app (home) while a video is
     * playing moves playback into the floating PiP window. Entry is owned
     * by the Jetpack PiP delegate: auto-enter (setEnabled, API 31+) on
     * modern systems, its manual enterPictureInPictureMode on older ones.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        pipLog("onUserLeaveHint")
    }

    override fun onPictureInPictureModeChanged(
        isInPipMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPipMode, newConfig)
        pipLog("modeChanged($isInPipMode)")
        pipActive.value = isInPipMode
        if (!isInPipMode) {
            // PiP closed: pause so the video doesn't keep playing after
            // its window is gone.
            if (playerRepository.playerState.value.isPlaying) {
                lifecycleScope.launch { playerRepository.pause() }
            }
        }
    }

    override fun onStop() {
        (getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
            .unregisterDisplayListener(displayListener)
        super.onStop()
        pipLog("onStop")
        // The companion only makes sense while the app is in the
        // foreground — dismiss on minimize. onStart() re-asserts it.
        companionPresentation?.dismiss()
        companionPresentation = null
        // Swapped mode hosts the companion in CompanionActivity — finish it
        // too, mirroring the presentation dismiss above.
        CompanionNavBus.finishCompanionActivity()
        // Stopped while OUT of PiP: pause so the video doesn't keep
        // playing invisibly. Auto-enter PiP also stops the activity on
        // entry, but modeChanged(true) has set pipActive by then, so the
        // gate holds; closing the PiP pauses via modeChanged(false).
        if (!pipActive.value && playerRepository.playerState.value.isPlaying) {
            lifecycleScope.launch { playerRepository.pause() }
        }
    }

    override fun onDestroy() {
        SwapFreezeFrame.detach(this)
        companionPresentation?.dismiss()
        companionPresentation = null
        // Belt-and-braces: onStop already finished the companion activity,
        // but the swap relaunch can destroy us without a normal stop cycle.
        CompanionNavBus.finishCompanionActivity()
        PipSurface.surfaceView.value = null
        pip.close()
        super.onDestroy()
    }

    /**
     * Controller key events (Cemu GamepadInputSource pattern): the bus
     * turns every key-edge into a [GamepadKeyBus.events] event and decides
     * consumption — while the settings binding popup is capturing everything
     * is consumed (the popup is non-focusable, the activity keeps input
     * focus); otherwise only player-mapped keys are consumed, so unbound
     * keys keep normal behavior (navigation, system keys, ...).
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        GamepadKeyBus.onKeyEvent(event) || super.dispatchKeyEvent(event)

    /**
     * Controller *analog* input (sticks, HAT d-pads) arrives as generic
     * motion events with a gamepad/joystick source — never as touch events
     * and (on most drivers) never as keys. Same capture/consume rules as
     * [dispatchKeyEvent]; real touch input has no gamepad source and passes
     * through untouched.
     */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        GamepadKeyBus.onMotionEvent(event) || super.dispatchGenericMotionEvent(event)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize StateApp and FragmentedStorage before setting content
        StateApp.instance.setGlobalContext(this, lifecycleScope, "compose")
        StateApp.instance.mainAppStarting(this)
        pipLog("onCreate")
        // React to dual-screen / swap toggles immediately: move the task and
        // re-assert the companion as soon as the switch flips, not just on
        // the next lifecycle pass.
        lifecycleScope.launch {
            settingsRepository.preferences
                .map { Pair(it.dualScreen, it.dualScreenSwapDisplays) }
                .distinctUntilChanged()
                .collect {
                    maybeApplyDisplayAssignment()
                    ensureCompanion()
                }
        }

        // Jetpack PiP (androidx.core:core-pip), used canonically: the
        // delegate owns the platform callbacks and the official helper
        // methods push validated params (aspect clamped to
        // 100:239..239:100, sourceRectHint center-cropped to the aspect).
        pip = VideoPlaybackPictureInPicture(this)
        // Double-tap "Picture in picture" gesture routes here. This alpha delegate
        // has no enter(), so use the platform API (mode-change still hits onPictureInPictureModeChanged).
        PipSurface.enterPip = {
            enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().build())
        }
        pip.addOnPictureInPictureEventListener(
            ContextCompat.getMainExecutor(this),
            object : PictureInPictureDelegate.OnPictureInPictureEventListener {
                override fun onPictureInPictureEvent(
                    event: PictureInPictureDelegate.Event,
                    config: Configuration?,
                ) {
                    pipLog("event $event")
                }
            },
        )

        // setEnabled drives auto-enter (API 31+): only while a video is
        // actually playing locally. distinctUntilChanged matters — the
        // library pushes params on every call, and playerState ticks 10/s.
        lifecycleScope.launch {
            playerRepository.playerState
                .map { it.isPlaying && it.currentVideo != null }
                .distinctUntilChanged()
                .collect { pip.setEnabled(it) }
        }

        // setAspectRatio tracks the video size (enter/exit crop).
        lifecycleScope.launch {
            playerRepository.playerState
                .map {
                    val size = playerRepository.videoSize
                    if (size != null && size.width > 0 && size.height > 0) {
                        Rational(size.width, size.height)
                    } else null
                }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { pip.setAspectRatio(it) }
        }

        // setPlayerView: the delegate tracks this view's bounds as the
        // sourceRectHint (the branch swap means it follows whichever
        // surface mode is composed — video card in the app tree, full
        // window in the PiP tree).
        lifecycleScope.launch {
            PipSurface.surfaceView
                .filterNotNull()
                .distinctUntilChanged()
                .collect { pip.setPlayerView(it) }
        }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Set navigator for non-Compose code access
        StateApp.instance.navigator = navigator

        // Route second-screen (CompanionActivity) taps to this navigator —
        // installed at startup and refreshed in
        // launchCompanionActivityOnDefaultDisplay() (the swap relaunch
        // replaces this instance).
        installCompanionNavCallbacks()

        // Start on the configured default tab (Settings > Content > Dock).
        navigator.setStartDestination(dockKeyToDestination(SettingsRepositoryImpl.instance.preferences.value.defaultDockTab))

        enableEdgeToEdge()

        setContent {
            BluejayMainActivity(
                this,
                navigator,
                playerRepository,
                settingsRepository,
            )
        }

        // Continuation of the swap freeze-frame: if this instance was
        // relaunched onto a new display, cover the fresh content with the
        // snapshot the old instance left behind and fade it out once the
        // first real frame is drawn. No-op when there is no pending frame
        // for this display (normal cold starts, in-place task moves).
        SwapFreezeFrame.consumeOnCreate(this)
    }
}

@Composable
private fun BluejayMainActivity(
    activity: MainActivity,
    navigator: Navigator,
    playerRepository: PlayerRepository,
    settingsRepository: SettingsRepository,
) {
    // Brightness changes made from the companion screen (Controls tab
    // slider) follow through the shared flow — apply to this display.
    LaunchedEffect(activity) {
        SystemControls.brightness.collect { v ->
            v?.let { SystemControls.setWindowBrightness(activity.window, it) }
        }
    }

    BluejayAppThemeRoot(settingsRepository) { p ->
        val appScope = rememberCoroutineScope()
        // PiP mode: the window is the video itself — render video only, no
        // app chrome (expanding the PiP flips this back to the full app UI).
        val pip by activity.pipActive.collectAsState()
        if (pip) {
            PlayerView(isPip = true)
        } else {
            Box(Modifier.fillMaxSize()) {
                bluejayMainActivityContent(
                    activity,
                    navigator,
                    playerRepository,
                    settingsRepository,
                )
                // One-time first-launch tour: shown until completed or skipped.
                if (!p.gettingStartedCompleted) {
                    GettingStartedFlow(
                        preferences = p,
                        settingsRepository = settingsRepository,
                        onFinished = {
                            appScope.launch {
                                settingsRepository.updateGeneral("gettingStartedCompleted", true)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun bluejayMainActivityContent(
    activity: MainActivity,
    navigator: Navigator,
    playerRepository: PlayerRepository,
    settingsRepository: SettingsRepository,
) {
    val config = rememberAppLayoutConfig()
    // Dock chrome follows the persisted tab order / default tab (Settings > Content > Dock).
    val prefs by settingsRepository.preferences.collectAsState()
    val dockItems = remember(prefs.dockTabOrder) { orderedDockItems(prefs.dockTabOrder) }
    val startDestination = remember(prefs.defaultDockTab) { dockKeyToDestination(prefs.defaultDockTab) }
    // The shell only reads a few flags — project to them so the 10 Hz
    // position ticks in playerState don't recompose the whole app shell.
    val playerFlags =
        remember {
            val s = playerRepository.playerState.value
            mutableStateOf(Triple(s.currentVideo != null, s.isMinimized, s.isFullscreen))
        }
    LaunchedEffect(Unit) {
        playerRepository.playerState
            .map { Triple(it.currentVideo != null, it.isMinimized, it.isFullscreen) }
            .distinctUntilChanged()
            .collect { playerFlags.value = it }
    }
    val hasVideo = playerFlags.value.first
    val isMinimized = playerFlags.value.second
    val isFullscreen = playerFlags.value.third

    // Navigating away while a video is active (tab switch, go-to-channel, ...)
    // collapses the player to the mini player instead of leaving it covering
    // the new screen.
    val currentRoute by navigator.currentRoute.collectAsState(initial = null)
    LaunchedEffect(currentRoute) {
        if (hasVideo && !isMinimized) {
            playerRepository.minimize()
        }
    }

    val showNavChrome = !isFullscreen

    // The video page in normal (inline) mode merges the nav chrome into the
    // player area: the chrome surface morphs from a rounded card into a flat
    // edge-to-edge rectangle (see NavigationSurface in AppLayout).
    val navMorphed =
        hasVideo &&
            !isFullscreen &&
            !isMinimized

    AppLayout(
        config = config.copy(showNavigation = showNavChrome),
        navMorphed = navMorphed,
        navigationContent = {
            AppNavigationChrome(
                items = dockItems,
                currentDestination =
                    navigator.currentRoute.collectAsState().value?.let { dest ->
                        when (dest) {
                            is NavDestination.Home -> "home"
                            is NavDestination.Search -> "search"
                            is NavDestination.Subscriptions -> "subscriptions"
                            is NavDestination.Library -> "library"
                            is NavDestination.Notifications -> "notifications"
                            is NavDestination.Settings -> "settings"
                            is NavDestination.ChannelDetail -> "channel:${dest.url}"
                            is NavDestination.PlaylistDetail -> "playlist:${dest.url}"
                            is NavDestination.SourceDetail -> "source:${dest.url}"
                            is NavDestination.PostDetail -> "post:${dest.url}"
                            is NavDestination.ArticleDetail -> "article:${dest.url}"
                            is NavDestination.WebDetail -> "web:${dest.url}"
                            is NavDestination.ContentSearchResults -> "search:${dest.query}"
                            else -> null
                        }
                    },
                onTabSelected = { tabId ->
                    when (tabId) {
                        "home" -> navigator.navigateHome()
                        "search" -> navigator.navigateSearch()
                        "subscriptions" -> navigator.navigateSubscriptions()
                        "library" -> navigator.navigateLibrary()
                        "notifications" -> navigator.navigateNotifications()
                        "settings" -> navigator.navigateSettings()
                    }
                },
                // Labels hide the moment fullscreen engages (before the
                // rail/bar finishes fading out) and return on exit.
                labelsVisible = !isFullscreen,
                isWide = config.isWide,
            )
        },
        content = {
            BluejayNavGraph(
                navigator = navigator,
                startDestination = startDestination,
            )
            // Player overlay — only rendered when there's a video to play
            if (hasVideo) {
                PlayerView(onChannelClick = { navigator.navigateToChannel(it) }, onPluginSettingsClick = { navigator.navigateToPluginBrowser() })
            }
        },
    )
}

/** Dock items in the persisted order; unknown keys dropped, missing keys appended. */
private fun orderedDockItems(order: List<String>): List<NavItemDef> {
    val known = bluejayNavItems
    return order.mapNotNull { key -> known.find { it.key == key } } +
        known.filter { item -> item.key !in order }
}

/** Maps a dock tab key to its start destination (default: Home). */
private fun dockKeyToDestination(key: String): NavDestination =
    when (key) {
        "search" -> NavDestination.Search
        "subscriptions" -> NavDestination.Subscriptions
        "library" -> NavDestination.Library
        "notifications" -> NavDestination.Notifications
        "settings" -> NavDestination.Settings
        else -> NavDestination.Home
    }
