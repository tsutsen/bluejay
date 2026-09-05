package com.tsutsen.platformplayer.activities

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
import com.tsutsen.platformplayer.core.designsystem.layout.rememberAppLayoutConfig
import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTheme
import com.tsutsen.platformplayer.core.designsystem.theme.ThemeEngine
import com.tsutsen.platformplayer.core.navigation.NavDestination
import com.tsutsen.platformplayer.core.navigation.Navigator
import com.tsutsen.platformplayer.feature.player.impl.PipSurface
import com.tsutsen.platformplayer.feature.player.impl.PlayerView
import com.tsutsen.platformplayer.feature.player.impl.SystemControls
import com.tsutsen.platformplayer.states.StateApp
import com.tsutsen.platformplayer.states.StateCasting
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
        ensureCompanion()
        lifecycleScope.launch {
            for (delay in longArrayOf(2_500L, 6_000L)) {
                delay(delay)
                if (isFinishing || isDestroyed) return@launch
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
        val display = rearDisplay()
        if (!enabled || display == null) {
            companionPresentation?.dismiss()
            companionPresentation = null
            return
        }
        val current = companionPresentation
        if (current != null && current.isShowing) return
        current?.dismiss()
        companionPresentation =
            CompanionPresentation(
                context = this,
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
            ).also { it.show() }
    }

    private fun rearDisplay(): Display? {
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return displayManager.displays.firstOrNull {
            it.displayId != Display.DEFAULT_DISPLAY && it.isValid &&
                it.state == Display.STATE_ON
        }
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
        super.onStop()
        pipLog("onStop")
        // The companion only makes sense while the app is in the
        // foreground — dismiss on minimize. onStart() re-asserts it.
        companionPresentation?.dismiss()
        companionPresentation = null
        // Stopped while OUT of PiP: pause so the video doesn't keep
        // playing invisibly. Auto-enter PiP also stops the activity on
        // entry, but modeChanged(true) has set pipActive by then, so the
        // gate holds; closing the PiP pauses via modeChanged(false).
        if (!pipActive.value && playerRepository.playerState.value.isPlaying) {
            lifecycleScope.launch { playerRepository.pause() }
        }
        // Paired with the (deferred) casting start below — restore both
        // together when re-enabling casting.
        // StateCasting.instance.onStop()
    }

    override fun onDestroy() {
        companionPresentation?.dismiss()
        companionPresentation = null
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

        // Jetpack PiP (androidx.core:core-pip), used canonically: the
        // delegate owns the platform callbacks and the official helper
        // methods push validated params (aspect clamped to
        // 100:239..239:100, sourceRectHint center-cropped to the aspect).
        pip = VideoPlaybackPictureInPicture(this)
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
                .map { it.isPlaying && !it.isCasting && it.currentVideo != null }
                .distinctUntilChanged()
                .collect { pip.setEnabled(it) }
        }

        // setAspectRatio tracks the video size (enter/exit crop).
        lifecycleScope.launch {
            playerRepository.playerState
                .map {
                    val size = playerRepository.exoPlayer?.videoSize
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

        // Casting deferred: fcast only reaches fcast-receiver apps (not
        // Chromecast TVs), which is niche — users can use system screen cast.
        // This also stops the background mDNS discovery + cast proxy server.
        // Re-enable together with the onStop() call above and the cast
        // button in PlayerView.
        // StateCasting.instance.start(this)

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Set navigator for non-Compose code access
        StateApp.instance.navigator = navigator

        enableEdgeToEdge()

        setContent {
            BluejayMainActivity(
                this,
                navigator,
                playerRepository,
                settingsRepository,
            )
        }
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

    // Settings are live: changing theme/grid columns re-composes this tree.
    // Gate on the real prefs instead of a default initial: with defaults the
    // first frame would render 100%-rounding tokens and the saved values
    // (e.g. rounding 0) would arrive a frame later — every radius spring
    // then animates default→saved and overshoots into negative corner
    // radii, which is fatal ("Corner size in Px can't be negative").
    val prefs by settingsRepository.preferences.collectAsState(initial = null)
    val p = prefs ?: return
    val darkTheme =
        when (p.appearance.themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.AUTO -> isSystemInDarkTheme()
        }

    // Second display: follow the "dual screen" toggle. ensureCompanion is a
    // no-op when nothing changes and dismisses the presentation when the
    // toggle turns off.
    LaunchedEffect(p.dualScreen) {
        activity.ensureCompanion()
    }

    // PiP mode: the window is the video itself — render video only, no app
    // chrome (expanding the PiP flips this back to the full app UI).
    val pip by activity.pipActive.collectAsState(initial = false)

    val appearance = p.appearance
    val appScope = rememberCoroutineScope()
    // Active custom theme (if any): key colors → generated light/dark schemes.
    val activeTheme = appearance.customThemes.firstOrNull { it.id == appearance.activeThemeId }
    val customSchemes =
        remember(activeTheme) {
            activeTheme?.let {
                ThemeEngine.generate(
                    it.primary,
                    it.secondary,
                    it.tertiary,
                    it.paletteStyle,
                    background = it.background,
                    contrast = it.contrast,
                )
            }
        }

    BluejayTheme(
        darkTheme = darkTheme,
        dynamicColor = appearance.dynamicColor,
        uiRounding = appearance.uiRounding,
        colorScheme = customSchemes?.let { if (darkTheme) it.dark else it.light },
    ) {
        if (pip) {
            PlayerView(isPip = true)
        } else {
            Box(Modifier.fillMaxSize()) {
                bluejayMainActivityContent(
                    activity,
                    navigator,
                    playerRepository,
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
) {
    val config = rememberAppLayoutConfig()
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
                startDestination = NavDestination.Home,
            )
            // Player overlay — only rendered when there's a video to play
            if (hasVideo) {
                PlayerView(onChannelClick = { navigator.navigateToChannel(it) })
            }
        },
    )
}
