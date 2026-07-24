package com.futo.platformplayer.core.navigation

import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hilt singleton navigator.
 * Provides type-safe navigation methods for all destinations.
 */
@Singleton
@Stable
class Navigator @Inject constructor() {

    private val _currentRoute = MutableStateFlow<NavDestination?>(NavDestination.Home)
    val currentRoute: StateFlow<NavDestination?> = _currentRoute.asStateFlow()

    private var onNavigate: ((NavDestination) -> Unit)? = null
    private var onBack: (() -> Boolean)? = null

    fun setOnNavigate(action: (NavDestination) -> Unit) {
        onNavigate = action
    }

    fun setOnBack(action: () -> Boolean) {
        onBack = action
    }

    /**
     * Update the current route from external sources.
     */
    fun updateCurrentRoute(route: String) {
        _currentRoute.value = when {
            route == "home" -> NavDestination.Home
            route == "search" -> NavDestination.Search
            route == "subscriptions" -> NavDestination.Subscriptions
            route == "library" -> NavDestination.Library
            route == "notifications" -> NavDestination.Notifications
            route == "settings" -> NavDestination.Settings
            else -> null
        }
    }

    // Top-level navigation
    fun navigateHome() = navigate(NavDestination.Home)
    fun navigateSearch() = navigate(NavDestination.Search)
    fun navigateSubscriptions() = navigate(NavDestination.Subscriptions)
    fun navigateLibrary() = navigate(NavDestination.Library)
    fun navigateNotifications() = navigate(NavDestination.Notifications)
    fun navigateSettings() = navigate(NavDestination.Settings)

    // Detail navigation
    fun navigateToChannel(url: String) = navigate(NavDestination.ChannelDetail(url))
    fun navigateToPlaylist(url: String) = navigate(NavDestination.PlaylistDetail(url))
    fun navigateToSource(url: String) = navigate(NavDestination.SourceDetail(url))
    fun navigateToPost(url: String) = navigate(NavDestination.PostDetail(url))
    fun navigateToArticle(url: String) = navigate(NavDestination.ArticleDetail(url))
    fun navigateToWeb(url: String) = navigate(NavDestination.WebDetail(url))

    // Search results
    fun navigateToContentSearch(query: String) = navigate(NavDestination.ContentSearchResults(query))
    fun navigateToCreatorSearch(query: String) = navigate(NavDestination.CreatorSearchResults(query))
    fun navigateToPlaylistSearch(query: String) = navigate(NavDestination.PlaylistSearchResults(query))

    // Library sub-screens
    fun navigateToWatchLater() = navigate(NavDestination.WatchLater)
    fun navigateToShorts() = navigate(NavDestination.Shorts)
    fun navigateToSubscriptionGroup(id: String) = navigate(NavDestination.SubscriptionGroupDetail(id))
    fun navigateToSubscriptionGroups() = navigate(NavDestination.SubscriptionGroupList)
    fun navigateToLibraryAlbums() = navigate(NavDestination.LibraryAlbums)
    fun navigateToLibraryAlbum(id: String) = navigate(NavDestination.LibraryAlbumDetail(id))
    fun navigateToLibraryArtists() = navigate(NavDestination.LibraryArtists)
    fun navigateToLibraryArtist(id: String) = navigate(NavDestination.LibraryArtistDetail(id))
    fun navigateToLibraryVideos() = navigate(NavDestination.LibraryVideos)
    fun navigateToLibraryFiles() = navigate(NavDestination.LibraryFiles)
    fun navigateToLibrarySearch() = navigate(NavDestination.LibrarySearch)

    // Auth & other
    fun navigateToLogin() = navigate(NavDestination.Login)
    fun navigateToDeveloper() = navigate(NavDestination.Developer)
    fun navigateToTutorial() = navigate(NavDestination.Tutorial)
    fun navigateToBuy() = navigate(NavDestination.Buy)
    fun navigateToImportSubscriptions() = navigate(NavDestination.ImportSubscriptions)
    fun navigateToImportPlaylists() = navigate(NavDestination.ImportPlaylists)
    fun navigateToBrowser(url: String) = navigate(NavDestination.Browser(url))
    fun navigateToComments(url: String) = navigate(NavDestination.Comments(url))
    fun navigateToSuggestions() = navigate(NavDestination.Suggestions)
    fun navigateToSettingsFragment(category: String) = navigate(NavDestination.SettingsFragment(category))
    fun navigateToPluginBrowser() = navigate(NavDestination.PluginBrowser)
    fun navigateToTestCompose() = navigate(NavDestination.TestCompose)

    // Generic navigation
    fun navigate(destination: NavDestination) {
        _currentRoute.value = destination
        onNavigate?.invoke(destination)
    }

    fun goBack(): Boolean {
        return onBack?.invoke() ?: false
    }

    fun popToRoot() {
        // Pop to root by navigating to home
        navigateHome()
    }
}
