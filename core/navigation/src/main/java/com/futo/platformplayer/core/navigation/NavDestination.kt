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
    data object WatchLater : NavDestination()
    data object Shorts : NavDestination()
    data class SubscriptionGroupDetail(val id: String) : NavDestination()
    data object SubscriptionGroupList : NavDestination()
    data object LibraryAlbums : NavDestination()
    data class LibraryAlbumDetail(val id: String) : NavDestination()
    data object LibraryArtists : NavDestination()
    data class LibraryArtistDetail(val id: String) : NavDestination()
    data object LibraryVideos : NavDestination()
    data object LibraryFiles : NavDestination()
    data object LibrarySearch : NavDestination()

    // Auth & other
    data object Login : NavDestination()
    data object Developer : NavDestination()
    data object Tutorial : NavDestination()
    data object Buy : NavDestination()
    data object ImportSubscriptions : NavDestination()
    data object ImportPlaylists : NavDestination()
    data class Browser(val url: String) : NavDestination()
    data class Comments(val url: String) : NavDestination()
    data object Suggestions : NavDestination()
    data class SettingsFragment(val category: String) : NavDestination()
    data object TestCompose : NavDestination()
}
