package com.futo.platformplayer.core.navigation

/**
 * Sealed class defining all navigation destinations in the app.
 * Corresponds to routes defined in ARCHITECTURE.md §6.
 */
sealed class NavDestination {

    // Top-level tabs
    data object Home : NavDestination()
    data object Search : NavDestination()
    data object Subscriptions : NavDestination()
    data object Library : NavDestination()
    data object Notifications : NavDestination()
    data object Settings : NavDestination()

    // Detail screens
    data class VideoDetail(val url: String) : NavDestination()
    data class ChannelDetail(val url: String) : NavDestination()
    data class PlaylistDetail(val url: String) : NavDestination()
    data class SourceDetail(val url: String) : NavDestination()
    data class PostDetail(val url: String) : NavDestination()
    data class ArticleDetail(val url: String) : NavDestination()
    data class WebDetail(val url: String) : NavDestination()

    // Search results
    data class ContentSearchResults(val query: String) : NavDestination()
    data class CreatorSearchResults(val query: String) : NavDestination()
    data class PlaylistSearchResults(val query: String) : NavDestination()

    // Library sub-screens
    data class WatchLater : NavDestination()
    data class Shorts : NavDestination()
    data class SubscriptionGroupDetail(val id: String) : NavDestination()
    data class SubscriptionGroupList : NavDestination()
    data class LibraryAlbums : NavDestination()
    data class LibraryAlbumDetail(val id: String) : NavDestination()
    data class LibraryArtists : NavDestination()
    data class LibraryArtistDetail(val id: String) : NavDestination()
    data class LibraryVideos : NavDestination()
    data class LibraryFiles : NavDestination()
    data class LibrarySearch : NavDestination()

    // Auth & other
    data class Login : NavDestination()
    data class Developer : NavDestination()
    data class Tutorial : NavDestination()
    data class Buy : NavDestination()
    data class ImportSubscriptions : NavDestination()
    data class ImportPlaylists : NavDestination()
    data class Browser(val url: String) : NavDestination()
    data class Comments(val url: String) : NavDestination()
    data class Suggestions : NavDestination()
    data class SettingsFragment(val category: String) : NavDestination()
    data class TestCompose : NavDestination()
}
