package com.tsutsen.platformplayer.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation3.runtime.NavKey
import com.tsutsen.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.tsutsen.platformplayer.auth.LoginScreen
import com.tsutsen.platformplayer.compose.navigation.Login
import com.tsutsen.platformplayer.compose.player.VideoPlayerScene
import com.tsutsen.platformplayer.core.designsystem.component.PlaceholderScreen
import com.tsutsen.platformplayer.core.navigation.NavDestination
import com.tsutsen.platformplayer.core.navigation.Navigator
import com.tsutsen.platformplayer.feature.home.impl.HomeScreen
import com.tsutsen.platformplayer.compose.plugins.PluginBrowserScene
import com.tsutsen.platformplayer.compose.subscriptions.SubscriptionsScreen
import com.tsutsen.platformplayer.feature.settings.impl.SettingsScreen
import com.tsutsen.platformplayer.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * App-level NavHost configuration for Bluejay.
 * Wires all feature screens into the navigation graph.
 * Lives in the app module to avoid core module depending on feature modules.
 */
@Composable
fun GrayjayNavGraph(
    navigator: Navigator,
    startDestination: NavDestination = NavDestination.Home
) {
    // Collect current route from navigator state
    val currentRoute by navigator.currentRoute.collectAsState(initial = startDestination)

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

    // Render the current destination
    when (val destination = currentRoute) {
        null -> PlaceholderScreen("Bluejay", "Welcome")
        is NavDestination.Home -> HomeScreen(navigator = navigator)
        is NavDestination.Search -> PlaceholderScreen("Search", "Coming soon")
        is NavDestination.Subscriptions -> SubscriptionsScreen(navigator = navigator)
        is NavDestination.VideoDetail -> VideoPlayerScene(d = destination, n = navigator)
        is NavDestination.Library -> PlaceholderScreen("Library", "Coming soon")
        is NavDestination.Notifications -> PlaceholderScreen("Notifications", "Coming soon")
        is NavDestination.Settings -> SettingsScreen(navigator = navigator)
        is NavDestination.ChannelDetail -> PlaceholderScreen("Channel Detail", "Coming soon")
        is NavDestination.PlaylistDetail -> PlaceholderScreen("Playlist Detail", "Coming soon")
        is NavDestination.SourceDetail -> PlaceholderScreen("Source Detail", "Coming soon")
        is NavDestination.PostDetail -> PlaceholderScreen("Post Detail", "Coming soon")
        is NavDestination.ArticleDetail -> PlaceholderScreen("Article Detail", "Coming soon")
        is NavDestination.WebDetail -> PlaceholderScreen("Web Detail", "Coming soon")
        is NavDestination.ContentSearchResults -> PlaceholderScreen("Search Results", "Coming soon")
        is NavDestination.CreatorSearchResults -> PlaceholderScreen("Creator Search", "Coming soon")
        is NavDestination.PlaylistSearchResults -> PlaceholderScreen("Playlist Search", "Coming soon")
        is NavDestination.WatchLater -> PlaceholderScreen("Watch Later", "Coming soon")
        is NavDestination.Shorts -> PlaceholderScreen("Shorts", "Coming soon")
        is NavDestination.SubscriptionGroupDetail -> PlaceholderScreen("Subscription Group", "Coming soon")
        is NavDestination.SubscriptionGroupList -> PlaceholderScreen("Subscription Groups", "Coming soon")
        is NavDestination.LibraryAlbums -> PlaceholderScreen("Albums", "Coming soon")
        is NavDestination.LibraryAlbumDetail -> PlaceholderScreen("Album Detail", "Coming soon")
        is NavDestination.LibraryArtists -> PlaceholderScreen("Artists", "Coming soon")
        is NavDestination.LibraryArtistDetail -> PlaceholderScreen("Artist Detail", "Coming soon")
        is NavDestination.LibraryVideos -> PlaceholderScreen("Library Videos", "Coming soon")
        is NavDestination.LibraryFiles -> PlaceholderScreen("Library Files", "Coming soon")
        is NavDestination.LibrarySearch -> PlaceholderScreen("Library Search", "Coming soon")
        is NavDestination.Login -> {
            val config = Json.decodeFromString<SourcePluginConfig>(destination.configJson)
            LoginScreen(
                config = config,
                onLogin = { auth ->
                    if (auth != null) {
                        try {
                            // Save auth to plugin
                            com.tsutsen.platformplayer.states.StatePlugins.instance.setPluginAuth(config.id, auth)
                            Logger.i("BluejayNavGraph", "Auth saved for ${config.name}")
                            // Reload the client to apply auth
                            val scope = com.tsutsen.platformplayer.states.StateApp.instance.scope
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    val context = com.tsutsen.platformplayer.states.StateApp.instance.context
                                    val client = com.tsutsen.platformplayer.states.StatePlatform.instance.reloadClient(context, config.id) {
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
                onBack = { navigator.goBack() }
            )
        }
        is NavDestination.Developer -> PlaceholderScreen("Developer", "Coming soon")
        is NavDestination.Tutorial -> PlaceholderScreen("Tutorial", "Coming soon")
        is NavDestination.Buy -> PlaceholderScreen("Buy License", "Coming soon")
        is NavDestination.ImportSubscriptions -> PlaceholderScreen("Import Subscriptions", "Coming soon")
        is NavDestination.ImportPlaylists -> PlaceholderScreen("Import Playlists", "Coming soon")
        is NavDestination.Browser -> PlaceholderScreen("Browser", "Coming soon")
        is NavDestination.Comments -> PlaceholderScreen("Comments", "Coming soon")
        is NavDestination.Suggestions -> PlaceholderScreen("Suggestions", "Coming soon")
        is NavDestination.SettingsFragment -> PlaceholderScreen("Settings", "Coming soon")
        is NavDestination.PluginBrowser -> PluginBrowserScene(onBack = { navigator.goBack() })
        is NavDestination.TestCompose -> PlaceholderScreen("Test Compose", "Coming soon")
    }
}
