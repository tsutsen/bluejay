package com.futo.platformplayer.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.futo.platformplayer.core.designsystem.component.PlaceholderScreen

/**
 * NavHost configuration for Grayjay.
 * Uses a state-based navigation approach with the Navigator's StateFlow.
 */
@Composable
fun GrayjayNavGraph(
    navigator: Navigator,
    startDestination: NavDestination = NavDestination.Home,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
) {
    // Set up navigator callbacks to update internal state
    LaunchedEffect(Unit) {
        navigator.setOnNavigate { destination ->
            // Update current route (navigation handled by collecting StateFlow)
        }
        navigator.setOnBack {
            false
        }
    }

    // Collect current route from navigator state
    val currentRoute by navigator.currentRoute.collectAsState(initial = startDestination)

    // Render the current destination
    when (currentRoute) {
        null -> PlaceholderScreen("Grayjay", "Welcome")
        is NavDestination.Home -> PlaceholderScreen("Home", "Coming soon")
        is NavDestination.Search -> PlaceholderScreen("Search", "Coming soon")
        is NavDestination.Subscriptions -> PlaceholderScreen("Subscriptions", "Coming soon")
        is NavDestination.Library -> PlaceholderScreen("Library", "Coming soon")
        is NavDestination.Notifications -> PlaceholderScreen("Notifications", "Coming soon")
        is NavDestination.Settings -> PlaceholderScreen("Settings", "Coming soon")
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
        is NavDestination.PluginBrowser -> PlaceholderScreen("Plugin Browser", "Coming soon")
        is NavDestination.TestCompose -> PlaceholderScreen("Test Compose", "Coming soon")
    }
}
