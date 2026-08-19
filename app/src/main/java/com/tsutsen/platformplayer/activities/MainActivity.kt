package com.tsutsen.platformplayer.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import com.tsutsen.platformplayer.compose.GrayjayNavGraph
import com.tsutsen.platformplayer.core.data.repository.PlayerRepository
import com.tsutsen.platformplayer.core.data.repository.SettingsRepository
import com.tsutsen.platformplayer.core.datastore.model.AppPreferences
import com.tsutsen.platformplayer.core.datastore.model.ThemeMode
import com.tsutsen.platformplayer.core.designsystem.layout.AppLayout
import com.tsutsen.platformplayer.core.designsystem.layout.AppNavigationChrome
import com.tsutsen.platformplayer.core.designsystem.layout.rememberAppLayoutConfig
import com.tsutsen.platformplayer.core.designsystem.theme.GrayjayTheme
import com.tsutsen.platformplayer.core.navigation.NavDestination
import com.tsutsen.platformplayer.core.navigation.Navigator
import com.tsutsen.platformplayer.feature.player.impl.PlayerView
import com.tsutsen.platformplayer.states.StateApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Compose-based MainActivity for Bluejay.
 * Hosts AppLayout with GrayjayNavGraph, and drives the CompanionActivity on
 * the second display from the "dual screen" setting.
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize StateApp and FragmentedStorage before setting content
        StateApp.instance.setGlobalContext(this, lifecycleScope, "compose")
        StateApp.instance.mainAppStarting(this)

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
            GrayjayMainActivity(
                this,
                navigator,
                playerRepository,
                settingsRepository,
            )
        }
    }
}

@Composable
private fun GrayjayMainActivity(
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

    // Second display: follow the "dual screen" toggle. CompanionActivity.start
    // is a no-op on single-screen devices and finishes the window when the
    // toggle turns off.
    LaunchedEffect(prefs.dualScreen) {
        CompanionActivity.start(activity, prefs.dualScreen)
    }

    GrayjayTheme(darkTheme = darkTheme, dynamicColor = prefs.appearance.dynamicColor) {
        grayjayMainActivityContent(
            activity,
            navigator,
            playerRepository,
        )
    }
}

@Composable
private fun grayjayMainActivityContent(
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

    AppLayout(
        config = config.copy(showNavigation = showNavChrome),
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
                isWide = config.isWide,
            )
        },
        content = {
            GrayjayNavGraph(
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
