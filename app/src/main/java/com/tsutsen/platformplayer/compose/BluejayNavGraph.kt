package com.tsutsen.platformplayer.compose

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import com.tsutsen.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.tsutsen.platformplayer.auth.LoginScreen
import com.tsutsen.platformplayer.compose.plugins.PluginBrowserScene
import com.tsutsen.platformplayer.core.designsystem.component.PlaceholderScreen
import com.tsutsen.platformplayer.core.navigation.NavDestination
import com.tsutsen.platformplayer.core.navigation.Navigator
import com.tsutsen.platformplayer.feature.channel.impl.ChannelScreen
import com.tsutsen.platformplayer.feature.home.impl.HomeScreen
import com.tsutsen.platformplayer.feature.library.impl.LibraryScreen
import com.tsutsen.platformplayer.feature.library.impl.LibrarySectionDetailScreen
import com.tsutsen.platformplayer.feature.search.impl.SearchScreen
import com.tsutsen.platformplayer.feature.settings.impl.SettingsScreen
import com.tsutsen.platformplayer.feature.settings.impl.SettingsSectionScreen
import com.tsutsen.platformplayer.feature.subscriptions.impl.SubscriptionsScreen
import com.tsutsen.platformplayer.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Top-level tabs that stay composed after their first visit, so scroll
 * position and in-screen state survive tab switches and detail round-trips.
 */
private val keepAliveTabs =
    setOf(
        NavDestination.Home,
        NavDestination.Search,
        NavDestination.Subscriptions,
        NavDestination.Library,
        NavDestination.Notifications,
        NavDestination.Settings,
    )

/**
 * App-level NavHost configuration for Bluejay.
 * Wires all feature screens into the navigation graph.
 * Lives in the app module to avoid core module depending on feature modules.
 */
@Composable
fun BluejayNavGraph(
    navigator: Navigator,
    startDestination: NavDestination = NavDestination.Home,
) {
    // Collect current route from navigator state
    val currentRoute by navigator.currentRoute.collectAsState(initial = startDestination)

    // System back: pop the navigator's back stack while it has entries.
    val backStack by navigator.backStack.collectAsState()
    BackHandler(enabled = backStack.isNotEmpty()) {
        navigator.goBack()
    }

    // At a tab root (empty back stack) the first back press goes to Home;
    // only a second one (while already on Home) closes the app.
    val context = LocalContext.current
    BackHandler(enabled = backStack.isEmpty()) {
        if (currentRoute is NavDestination.Home) {
            (context as? Activity)?.finish()
        } else {
            navigator.navigateHome()
        }
    }

    // Set up navigator callbacks
    LaunchedEffect(Unit) {
        navigator.setOnNavigate { destination ->
            // Navigation handled by collecting StateFlow below
        }
        navigator.setOnBack {
            // Navigate back to PluginBrowser if we're on Login
            if (currentRoute is NavDestination.Login) {
                navigator.navigateToPluginBrowser()
                true
            } else {
                false
            }
        }
    }

    // Keep-alive: compose each tab once visited, keep it alive (hidden, non-
    // interactive) when another destination is active so its scroll position
    // and state are never lost.
    val visitedTabs =
        remember {
            mutableStateOf(
                setOf<NavDestination>(
                    if (startDestination in keepAliveTabs) startDestination else NavDestination.Home,
                ),
            )
        }
    LaunchedEffect(currentRoute) {
        val route = currentRoute ?: return@LaunchedEffect
        if (route in keepAliveTabs && route !in visitedTabs.value) {
            visitedTabs.value = visitedTabs.value + route
        }
    }

    Box(Modifier.fillMaxSize()) {
        visitedTabs.value.forEach { tab ->
            val isActive = tab == currentRoute
            Box(
                Modifier
                    .fillMaxSize()
                    // Active tab must sit above the hidden keep-alive tabs in
                    // draw order, otherwise their pointer-swallowing layer
                    // (topmost by visit order) steals its drag events.
                    .zIndex(if (isActive) 1f else 0f)
                    .alpha(if (isActive) 1f else 0f)
                    .pointerInput(isActive) {
                        if (!isActive) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent()
                                }
                            }
                        }
                    },
            ) {
                when (tab) {
                    NavDestination.Home -> HomeScreen(navigator = navigator)
                    NavDestination.Search -> SearchScreen(navigator = navigator)
                    NavDestination.Subscriptions -> SubscriptionsScreen(navigator = navigator)
                    NavDestination.Library -> LibraryScreen(navigator = navigator)
                    NavDestination.Settings -> SettingsScreen(navigator = navigator)
                    NavDestination.Notifications -> NotificationsScreen()
                    else -> Unit
                }
            }
        }

        // Render the current destination (detail screens layer over the tabs)
        when (val destination = currentRoute) {
            is NavDestination.Home -> {
                Unit
            }

            is NavDestination.Search -> {
                Unit
            }

            is NavDestination.Subscriptions -> {
                Unit
            }

            is NavDestination.Library -> {
                Unit
            }

            is NavDestination.Settings -> {
                Unit
            }

            is NavDestination.ChannelDetail -> {
                ChannelScreen(
                    channelUrl = destination.url,
                    onBack = { navigator.goBack() },
                    navigator = navigator,
                )
            }

            is NavDestination.PlaylistDetail -> {
                PlaylistDetailScreen(
                    playlistUrl = destination.url,
                    onBack = { navigator.goBack() },
                    navigator = navigator,
                )
            }

            is NavDestination.SourceDetail -> {
                PlaceholderScreen("Source Detail", "Coming soon")
            }

            is NavDestination.PostDetail -> {
                PlaceholderScreen("Post Detail", "Coming soon")
            }

            is NavDestination.ArticleDetail -> {
                PlaceholderScreen("Article Detail", "Coming soon")
            }

            is NavDestination.WebDetail -> {
                PlaceholderScreen("Web Detail", "Coming soon")
            }

            is NavDestination.ContentSearchResults -> {
                PlaceholderScreen("Search Results", "Coming soon")
            }

            is NavDestination.CreatorSearchResults -> {
                PlaceholderScreen("Creator Search", "Coming soon")
            }

            is NavDestination.PlaylistSearchResults -> {
                PlaceholderScreen("Playlist Search", "Coming soon")
            }

            is NavDestination.Notifications -> {
                Unit
            }

            is NavDestination.WatchLater -> {
                PlaceholderScreen("Watch Later", "Coming soon")
            }

            is NavDestination.Shorts -> {
                PlaceholderScreen("Shorts", "Coming soon")
            }

            is NavDestination.SubscriptionGroupDetail -> {
                PlaceholderScreen("Subscription Group", "Coming soon")
            }

            is NavDestination.SubscriptionGroupList -> {
                PlaceholderScreen("Subscription Groups", "Coming soon")
            }

            is NavDestination.LibraryAlbums -> {
                PlaceholderScreen("Albums", "Coming soon")
            }

            is NavDestination.LibraryAlbumDetail -> {
                PlaceholderScreen("Album Detail", "Coming soon")
            }

            is NavDestination.LibraryArtists -> {
                PlaceholderScreen("Artists", "Coming soon")
            }

            is NavDestination.LibraryArtistDetail -> {
                PlaceholderScreen("Artist Detail", "Coming soon")
            }

            is NavDestination.LibraryVideos -> {
                PlaceholderScreen("Library Videos", "Coming soon")
            }

            is NavDestination.LibraryFiles -> {
                PlaceholderScreen("Library Files", "Coming soon")
            }

            is NavDestination.LibrarySearch -> {
                PlaceholderScreen("Library Search", "Coming soon")
            }

            is NavDestination.LibrarySectionDetail -> {
                LibrarySectionDetailScreen(
                    sectionId = destination.sectionId,
                    onBack = { navigator.goBack() },
                    navigator = navigator,
                )
            }

            is NavDestination.Login -> {
                val config = Json.decodeFromString<SourcePluginConfig>(destination.configJson)
                LoginScreen(
                    config = config,
                    onLogin = { auth ->
                        if (auth != null) {
                            try {
                                // Save auth to plugin
                                com.tsutsen.platformplayer.states.StatePlugins.instance
                                    .setPluginAuth(config.id, auth)
                                Logger.i("BluejayNavGraph", "Auth saved for ${config.name}")
                                // Reload the client to apply auth
                                val scope = com.tsutsen.platformplayer.states.StateApp.instance.scope
                                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    try {
                                        val context = com.tsutsen.platformplayer.states.StateApp.instance.context
                                        val client =
                                            com.tsutsen.platformplayer.states.StatePlatform.instance.reloadClient(context, config.id) {
                                                Logger.i("BluejayNavGraph", "Client reloaded after login")
                                            }
                                        Logger.i("BluejayNavGraph", "Client reloaded: ${client != null}")
                                    } catch (e: Exception) {
                                        Logger.e("BluejayNavGraph", "Failed to reload client", e)
                                    }
                                }
                            } catch (e: Exception) {
                                Logger.e("BluejayNavGraph", "Failed to save auth", e)
                            }
                        }
                    },
                    onBack = { navigator.goBack() },
                )
            }

            is NavDestination.Developer -> {
                PlaceholderScreen("Developer", "Coming soon")
            }

            is NavDestination.Tutorial -> {
                PlaceholderScreen("Tutorial", "Coming soon")
            }

            is NavDestination.Buy -> {
                PlaceholderScreen("Buy License", "Coming soon")
            }

            is NavDestination.ImportSubscriptions -> {
                PlaceholderScreen("Import Subscriptions", "Coming soon")
            }

            is NavDestination.ImportPlaylists -> {
                PlaceholderScreen("Import Playlists", "Coming soon")
            }

            is NavDestination.Browser -> {
                PlaceholderScreen("Browser", "Coming soon")
            }

            is NavDestination.Comments -> {
                PlaceholderScreen("Comments", "Coming soon")
            }

            is NavDestination.Suggestions -> {
                PlaceholderScreen("Suggestions", "Coming soon")
            }

            is NavDestination.SettingsFragment -> {
                SettingsSectionScreen(
                    category = destination.category,
                    onBack = { navigator.goBack() },
                    onPluginsClick = { navigator.navigateToPluginBrowser() },
                )
            }

            is NavDestination.PluginBrowser -> {
                PluginBrowserScene(onBack = { navigator.goBack() })
            }

            else -> {
                Unit
            }
        }
    }
}
