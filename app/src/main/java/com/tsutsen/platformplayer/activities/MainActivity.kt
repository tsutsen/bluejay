package com.tsutsen.platformplayer.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tsutsen.platformplayer.core.designsystem.layout.AppLayout
import com.tsutsen.platformplayer.core.designsystem.layout.AppNavigationChrome
import com.tsutsen.platformplayer.core.designsystem.layout.rememberAppLayoutConfig
import com.tsutsen.platformplayer.core.designsystem.theme.GrayjayTheme
import com.tsutsen.platformplayer.compose.GrayjayNavGraph
import com.tsutsen.platformplayer.core.navigation.NavDestination
import com.tsutsen.platformplayer.core.data.repository.PlayerRepository
import com.tsutsen.platformplayer.core.navigation.Navigator
import com.tsutsen.platformplayer.feature.dualscreen.CompanionWindowManager
import com.tsutsen.platformplayer.feature.player.impl.PlayerView
import com.tsutsen.platformplayer.feature.dualscreen.ScreenCoordinator
import com.tsutsen.platformplayer.states.StateApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val TAG = "MainActivity"

/**
 * Compose-based MainActivity for Bluejay.
 * Hosts AppLayout with GrayjayNavGraph, observes ScreenCoordinator for
 * cross-activity state (e.g., mini player), and launches CompanionActivity
 * when a secondary display is available.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity(), IWithResultLauncher {

    @Inject
    lateinit var screenCoordinator: ScreenCoordinator

    @Inject
    lateinit var companionWindowManager: CompanionWindowManager

    @Inject
    lateinit var navigator: Navigator

    @Inject
    lateinit var playerRepository: PlayerRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Remove the top window title bar
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        
        // Initialize StateApp and FragmentedStorage before setting content
        StateApp.instance.setGlobalContext(this, lifecycleScope, "compose")
        StateApp.instance.mainAppStarting(this)
        
        // Set navigator for non-Compose code access
        StateApp.instance.navigator = navigator
        
        enableEdgeToEdge()

        setContent {
            GrayjayTheme {
                GrayjayMainActivity(this, screenCoordinator, companionWindowManager, navigator, playerRepository)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Check for secondary display availability
        screenCoordinator.setCompanionVisible(companionWindowManager.isCompanionAvailable.value)
    }

    // Legacy navigation methods for backward compatibility

    fun navigate(fragment: Fragment, parameter: Any? = null, withHistory: Boolean = true, isTopLevel: Boolean = false) {
        Log.d(TAG, "navigate: ${fragment.javaClass.simpleName}")
        // Stub: In a full implementation, this would navigate using the Compose navigation
    }

    inline fun <reified T : Fragment> navigate(parameter: Any? = null, withHistory: Boolean = true) {
        _navigateImpl(T::class.java.simpleName)
    }

    inline fun <reified T : Fragment> navigateTab(parameter: Any? = null) {
        _navigateTabImpl(T::class.java.simpleName)
    }

    fun navigateTab(fragmentClass: Class<out Fragment>, parameter: Any? = null) {
        Log.d(TAG, "navigateTab: ${fragmentClass.simpleName}")
    }

    inline fun <reified T : Fragment> getFragment(): T? {
        return _getFragmentImpl(T::class.java.simpleName) as? T
    }

    fun closeSegment(fragment: Fragment? = null) {
        Log.d(TAG, "closeSegment")
    }

    fun handleUrl(url: String, position: Long = 0): Boolean {
        Log.d(TAG, "handleUrl: $url")
        return false
    }

    fun handleUrlAll(urls: List<String>) {
        Log.d(TAG, "handleUrlAll: ${urls.size} URLs")
    }

    fun requestPermissionAudio(callback: (Boolean) -> Unit) {
        Log.d(TAG, "requestPermissionAudio")
        callback(true) // Stub: always grant permission
    }

    fun requestPermissionVideo(callback: (Boolean) -> Unit) {
        Log.d(TAG, "requestPermissionVideo")
        callback(true) // Stub: always grant permission
    }

    fun requestPermissionMusic() {
        Log.d(TAG, "requestPermissionMusic")
    }

    fun requestNotificationPermissions(message: String? = null) {
        Log.d(TAG, "requestNotificationPermissions: $message")
    }

    fun showAppToast(toast: Any) {
        Log.d(TAG, "showAppToast (stub)")
    }

    fun showUrlQrCodeScanner() {
        Log.d(TAG, "showUrlQrCodeScanner (stub)")
    }

    // Fragment references (stubs for legacy code)
    var _fragMainHome: Fragment? = null
    var _fragMainSubscriptionsFeed: Fragment? = null
    var _fragMainPlaylists: Fragment? = null
    var _fragMainSettingsHub: Fragment? = null
    var _fragVideoDetail: Fragment? = null

    // Legacy property for fragment access
    var fragCurrent: Fragment? = null
        get() = _fragMainHome

    // Navigation event bus (simplified)
    val onNavigated = NavigationEventBus()

    // Helper for inline functions
    fun _navigateImpl(fragmentClass: String) {
        Log.d(TAG, "_navigateImpl: $fragmentClass")
    }

    fun _navigateTabImpl(fragmentClass: String) {
        Log.d(TAG, "_navigateTabImpl: $fragmentClass")
    }

    fun _getFragmentImpl(fragmentClass: String): Fragment? {
        Log.d(TAG, "_getFragmentImpl: $fragmentClass")
        return null
    }

    fun _isFragmentActiveImpl(fragmentClass: String): Boolean {
        Log.d(TAG, "_isFragmentActiveImpl: $fragmentClass")
        return false
    }

    // Static helper methods
    companion object {
        fun getVideoIntent(context: Context, url: String): Intent {
            return Intent(context, MainActivity::class.java).apply {
                putExtra("video_url", url)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }

        fun getImportOptionsIntent(context: Context): Intent {
            return Intent()
        }

        fun getActionIntent(context: Context, action: String): Intent {
            return Intent()
        }

        fun getTabIntent(context: Context, tabId: Any): Intent {
            return Intent()
        }

        fun showAppToast(context: Context, message: String) {
            // Stub
        }

        fun showUrlQrCodeScanner(activity: MainActivity, callback: (String) -> Unit) {
            callback("")
        }

        fun showUrlQrCodeScanner() {
            // Stub
        }
    }

    inline fun <reified T : Fragment> isFragmentActive(): Boolean {
        return _isFragmentActiveImpl(T::class.java.simpleName)
    }

    override fun launchForResult(intent: Intent, code: Int, handler: (ActivityResult) -> Unit) {
        Log.d(TAG, "launchForResult: code=$code")
        // Stub: In a full implementation, this would use registerForActivityResult
    }
}

/**
 * Simple navigation event bus for backward compatibility.
 */
class NavigationEventBus {
    fun subscribe(target: Any, callback: () -> Unit) {
        // Stub: In a full implementation, this would register the callback
    }
    
    fun remove(target: Any) {
        // Stub: In a full implementation, this would unregister the callback
    }
}

@Composable
private fun GrayjayMainActivity(
    activity: MainActivity,
    screenCoordinator: ScreenCoordinator,
    companionWindowManager: CompanionWindowManager,
    navigator: Navigator,
    playerRepository: PlayerRepository
) {
    val companionVisible by screenCoordinator.companionVisible.collectAsState()
    val config = rememberAppLayoutConfig()
    val playerState by playerRepository.playerState.collectAsState()

    // Launch companion window when secondary display becomes available
    LaunchedEffect(companionVisible) {
        if (companionVisible) {
            Log.d(TAG, "Secondary display available, launching companion window")
            companionWindowManager.launchCompanionWindow(activity)
        }
    }

    val showNavChrome = !playerState.isFullscreen

    AppLayout(
        config = config.copy(showNavigation = showNavChrome),
        navigationContent = {
            AppNavigationChrome(
                currentDestination = navigator.currentRoute.collectAsState().value?.let { dest ->
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
                        "plugins" -> navigator.navigateToPluginBrowser()
                    }
                },
                isWide = config.isWide
            )
        },
        content = {
            GrayjayNavGraph(
                navigator = navigator,
                startDestination = NavDestination.Home
            )
            // Player overlay — only rendered when there's a video to play
            if (playerState.currentVideo != null) {
                PlayerView()
            }
        }
    )
}
