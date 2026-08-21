package com.tsutsen.platformplayer.activities

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.tsutsen.platformplayer.compose.BluejayNavGraph
import com.tsutsen.platformplayer.core.data.repository.HomeRepository
import com.tsutsen.platformplayer.core.data.repository.LibraryRepository
import com.tsutsen.platformplayer.core.data.repository.PlayerRepository
import com.tsutsen.platformplayer.core.data.repository.SettingsRepository
import com.tsutsen.platformplayer.core.datastore.model.AppPreferences
import com.tsutsen.platformplayer.core.datastore.model.ThemeMode
import com.tsutsen.platformplayer.core.designsystem.layout.AppLayout
import com.tsutsen.platformplayer.core.designsystem.layout.AppNavigationChrome
import com.tsutsen.platformplayer.core.designsystem.layout.rememberAppLayoutConfig
import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTheme
import com.tsutsen.platformplayer.core.navigation.NavDestination
import com.tsutsen.platformplayer.core.navigation.Navigator
import com.tsutsen.platformplayer.feature.player.impl.PlayerView
import com.tsutsen.platformplayer.states.StateApp
import com.tsutsen.platformplayer.states.StateCasting
import dagger.hilt.android.AndroidEntryPoint
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

    override fun onStop() {
        super.onStop()
        // Paired with the (deferred) casting start below — restore both
        // together when re-enabling casting.
        // StateCasting.instance.onStop()
    }

    override fun onDestroy() {
        companionPresentation?.dismiss()
        companionPresentation = null
        super.onDestroy()
    }

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

    BluejayTheme(darkTheme = darkTheme, dynamicColor = prefs.appearance.dynamicColor) {
        bluejayMainActivityContent(
            activity,
            navigator,
            playerRepository,
        )
    }
}

@Composable
private fun bluejayMainActivityContent(
    activity: MainActivity,
    navigator: Navigator,
    playerRepository: PlayerRepository,
) {
    val config = rememberAppLayoutConfig()
    val playerState by playerRepository.playerState.collectAsState()

    // Navigating away while a video is active (tab switch, go-to-channel, ...)
    // collapses the player to the mini player instead of leaving it covering
    // the new screen.
    val currentRoute by navigator.currentRoute.collectAsState(initial = null)
    LaunchedEffect(currentRoute) {
        if (playerState.currentVideo != null && !playerState.isMinimized) {
            playerRepository.minimize()
        }
    }

    val showNavChrome = !playerState.isFullscreen

    // The video page in normal (inline) mode merges the nav chrome into the
    // player area: the chrome surface morphs from a rounded card into a flat
    // edge-to-edge rectangle (see NavigationSurface in AppLayout).
    val navMorphed =
        playerState.currentVideo != null &&
            !playerState.isFullscreen &&
            !playerState.isMinimized

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
                labelsVisible = !playerState.isFullscreen,
                isWide = config.isWide,
            )
        },
        content = {
            BluejayNavGraph(
                navigator = navigator,
                startDestination = NavDestination.Home,
            )
            // Player overlay — only rendered when there's a video to play
            if (playerState.currentVideo != null) {
                PlayerView(onChannelClick = { navigator.navigateToChannel(it) })
            }
        },
    )
}
