package com.futo.platformplayer.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.futo.platformplayer.core.designsystem.component.PlaceholderScreen
import com.futo.platformplayer.core.navigation.NavDestination
import com.futo.platformplayer.core.navigation.Navigator
import com.futo.platformplayer.feature.home.impl.HomeScreen
import com.futo.platformplayer.feature.settings.impl.SettingsScreen

/**
 * App-level NavHost configuration for Grayjay.
 * Wires all feature screens into the navigation graph.
 * Lives in the app module to avoid core module depending on feature modules.
 */
@Composable
fun GrayjayNavGraph(
    navigator: Navigator,
    startDestination: NavDestination = NavDestination.Home
) {
    // Set up navigator callbacks
    LaunchedEffect(Unit) {
        navigator.setOnNavigate { destination ->
            // Navigation handled by collecting StateFlow below
        }
        navigator.setOnBack {
            false
        }
    }

    // Collect current route from navigator state
    val currentRoute by navigator.currentRoute.collectAsState(initial = startDestination)

    // Render the current destination
    when (val destination = currentRoute) {
        null -> PlaceholderScreen("Grayjay", "Welcome")
        is NavDestination.Home -> HomeScreen(navigator = navigator)
        is NavDestination.Search -> PlaceholderScreen("Search", "Coming soon")
        is NavDestination.Subscriptions -> PlaceholderScreen("Subscriptions", "Coming soon")
        is NavDestination.Library -> PlaceholderScreen("Library", "Coming soon")
        is NavDestination.Notifications -> PlaceholderScreen("Notifications", "Coming soon")
        is NavDestination.Settings -> SettingsScreen()
        is NavDestination.VideoDetail -> PlaceholderScreen("Video Detail", "Coming soon")
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
        is NavDestination.Login -> PlaceholderScreen("Login", "Coming soon")
        is NavDestination.Developer -> PlaceholderScreen("Developer", "Coming soon")
        is NavDestination.Tutorial -> PlaceholderScreen("Tutorial", "Coming soon")
        is NavDestination.Buy -> PlaceholderScreen("Buy License", "Coming soon")
        is NavDestination.ImportSubscriptions -> PlaceholderScreen("Import Subscriptions", "Coming soon")
        is NavDestination.ImportPlaylists -> PlaceholderScreen("Import Playlists", "Coming soon")
        is NavDestination.Browser -> PlaceholderScreen("Browser", "Coming soon")
        is NavDestination.Comments -> PlaceholderScreen("Comments", "Coming soon")
        is NavDestination.Suggestions -> PlaceholderScreen("Suggestions", "Coming soon")
        is NavDestination.SettingsFragment -> PlaceholderScreen("Settings", "Coming soon")
        is NavDestination.TestCompose -> PlaceholderScreen("Test Compose", "Coming soon")
    }
}
