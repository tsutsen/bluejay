package com.tsutsen.platformplayer.activities

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.Display
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import com.tsutsen.platformplayer.compose.BluejayNavGraph
import com.tsutsen.platformplayer.core.data.repository.ChannelRepository
import com.tsutsen.platformplayer.core.data.repository.HomeRepository
import com.tsutsen.platformplayer.core.data.repository.LibraryRepository
import com.tsutsen.platformplayer.core.data.repository.PlayerRepository
import com.tsutsen.platformplayer.core.data.repository.SettingsRepository
import com.tsutsen.platformplayer.core.ui.GamepadKeyBus
import com.tsutsen.platformplayer.core.datastore.model.AppPreferences
import com.tsutsen.platformplayer.core.datastore.model.ThemeMode
import com.tsutsen.platformplayer.core.designsystem.layout.AppLayout
import com.tsutsen.platformplayer.core.designsystem.layout.AppNavigationChrome
import com.tsutsen.platformplayer.core.designsystem.layout.rememberAppLayoutConfig
import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTheme
import com.tsutsen.platformplayer.core.navigation.NavDestination
import com.tsutsen.platformplayer.core.navigation.Navigator
import com.tsutsen.platformplayer.feature.player.impl.PlayerView
import com.tsutsen.platformplayer.feature.player.impl.SystemControls
import com.tsutsen.platformplayer.states.StateApp
import com.tsutsen.platformplayer.states.StateCasting
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
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

    override fun onStart() {
        super.onStart()
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
                // Tapping the channel badge on the second screen navigates
                // the main screen to the channel page.
                onChannelClick = { url -> navigator.navigateToChannel(url) },
            ).also { it.show() }
    }

    private fun rearDisplay(): Display? {
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return displayManager.displays.firstOrNull {
            it.displayId != Display.DEFAULT_DISPLAY && it.isValid &&
                it.state == Display.STATE_ON
        }
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
     * playing moves playback into the floating PiP window instead of just
     * minimizing the app.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (pipActive.value) return
        val st = playerRepository.playerState.value
        if (st.currentVideo == null || !st.isPlaying || st.isCasting) return
        val builder = PictureInPictureParams.Builder()
        playerRepository.exoPlayer?.videoSize?.let { size ->
            if (size.width > 0 && size.height > 0) {
                // Deprecated API 33+ (Rational form) — the only overload
                // visible to this SDK stub; behaves identically on 36.
                @Suppress("DEPRECATION")
                builder.setAspectRatio(Rational(size.width, size.height))
            }
        }
        enterPictureInPictureMode(builder.build())
    }

    override fun onPictureInPictureModeChanged(
        isInPipMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPipMode, newConfig)
        pipActive.value = isInPipMode
    }

    override fun onStop() {
        super.onStop()
        // The companion only makes sense while the app is in the
        // foreground — dismiss on minimize. onStart() re-asserts it.
        companionPresentation?.dismiss()
        companionPresentation = null
        // Paired with the (deferred) casting start below — restore both
        // together when re-enabling casting.
        // StateCasting.instance.onStop()
    }

    override fun onDestroy() {
        companionPresentation?.dismiss()
        companionPresentation = null
        super.onDestroy()
    }

    /** Route gamepad/remote key events through [GamepadKeyBus] (controller
     *  button mapping, Settings > Controller) before normal handling. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        GamepadKeyBus.dispatchKey(event) || super.dispatchKeyEvent(event)

    /** Gamepad "motion" events (buttons/triggers/sticks, see
     *  [GamepadKeyBus.motionEdges]) also arrive as touch-sourced events and
     *  must be consumed, or they produce phantom taps on the UI. */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean =
        GamepadKeyBus.dispatchMotion(event) || super.dispatchTouchEvent(event)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize StateApp and FragmentedStorage before setting content
        StateApp.instance.setGlobalContext(this, lifecycleScope, "compose")
        StateApp.instance.mainAppStarting(this)

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
    val prefs by settingsRepository.preferences.collectAsState(initial = AppPreferences())
    val darkTheme =
        when (prefs.appearance.themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.AUTO -> isSystemInDarkTheme()
        }

    // Second display: follow the "dual screen" toggle. ensureCompanion is a
    // no-op when nothing changes and dismisses the presentation when the
    // toggle turns off.
    LaunchedEffect(prefs.dualScreen) {
        activity.ensureCompanion()
    }

    // PiP mode: the window is the video itself — render video only, no app
    // chrome (expanding the PiP flips this back to the full app UI).
    val pip by activity.pipActive.collectAsState(initial = false)

    BluejayTheme(darkTheme = darkTheme, dynamicColor = prefs.appearance.dynamicColor) {
        if (pip) {
            PlayerView(isPip = true)
        } else {
            bluejayMainActivityContent(
                activity,
                navigator,
                playerRepository,
            )
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
