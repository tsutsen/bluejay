/*
 * Grayjay Navigator
 *
 * Handles navigation logic for the Grayjay app.
 * Distinguishes between top-level tab navigation and sub-route navigation.
 * Modeled after nav3-recipes Navigator pattern.
 */

package com.futo.platformplayer.compose.navigation

import androidx.navigation3.runtime.NavKey

class GrayjayNavigator(val state: GrayjayNavigationState) {

    /**
     * Navigate to a route.
     *
     * If the route is a top-level tab, switch to that tab.
     * Otherwise, push the route onto the current tab's back stack.
     */
    fun navigate(route: NavKey) {
        if (route in state.backStacks.keys) {
            // This is a top level route — just switch to it
            state.topLevelRoute.value = route
        } else {
            // Sub-route — add to the current tab's back stack
            state.backStacks[state.topLevelRoute.value]?.add(route)
        }
    }

    /**
     * Navigate to a top-level tab without pushing to back stack.
     */
    fun navigateToTab(route: NavKey) {
        if (route in state.backStacks.keys) {
            state.topLevelRoute.value = route
        }
    }

    /**
     * Go back. If at the base of the current route, go back to the start route tab.
     * Otherwise, pop the last entry from the current tab's back stack.
     */
    fun goBack(): Boolean {
        val currentStack = state.backStacks[state.topLevelRoute.value]
            ?: return false
        val currentRoute = currentStack.last()

        // If we're at the base of the current route, go back to the start route stack.
        if (currentRoute == state.topLevelRoute.value) {
            state.topLevelRoute.value = state.startRoute
            return true
        } else {
            currentStack.removeLastOrNull()
            return true
        }
    }

    // ==================== Convenience methods for specific navigation actions ====================

    /** Navigate to the video player */
    fun navigateToVideo(url: String, position: Long? = null, startMinimized: Boolean = false) {
        val videoDetail = VideoDetail(url, position, startMinimized)
        // Remove any existing video detail before adding new one
        val stack = state.backStacks[state.topLevelRoute.value]
        stack?.removeIf { it is VideoDetail }
        stack?.add(videoDetail)
    }

    /** Navigate to a channel detail */
    fun navigateToChannel(url: String) {
        val stack = state.backStacks[state.topLevelRoute.value]
        stack?.removeIf { it is ChannelDetail }
        stack?.add(ChannelDetail(url))
    }

    /** Navigate to a playlist detail */
    fun navigateToPlaylist(url: String, isRemote: Boolean = false) {
        val stack = state.backStacks[state.topLevelRoute.value]
        stack?.removeIf { it is PlaylistDetail }
        stack?.add(PlaylistDetail(url, isRemote))
    }

    /** Navigate to a source detail */
    fun navigateToSource(url: String) {
        val stack = state.backStacks[state.topLevelRoute.value]
        stack?.removeIf { it is SourceDetail }
        stack?.add(SourceDetail(url))
    }

    /** Navigate to content search results */
    fun navigateToContentSearch(query: String) {
        val stack = state.backStacks[state.topLevelRoute.value]
        stack?.removeIf { it is ContentSearchResults }
        stack?.add(ContentSearchResults(query))
    }

    /** Navigate to creator search results */
    fun navigateToCreatorSearch(query: String) {
        val stack = state.backStacks[state.topLevelRoute.value]
        stack?.removeIf { it is CreatorSearchResults }
        stack?.add(CreatorSearchResults(query))
    }

    /** Navigate to playlist search results */
    fun navigateToPlaylistSearch(query: String) {
        val stack = state.backStacks[state.topLevelRoute.value]
        stack?.removeIf { it is PlaylistSearchResults }
        stack?.add(PlaylistSearchResults(query))
    }

    /** Navigate to post detail */
    fun navigateToPost(url: String) {
        val stack = state.backStacks[state.topLevelRoute.value]
        stack?.removeIf { it is PostDetail }
        stack?.add(PostDetail(url))
    }

    /** Navigate to article detail */
    fun navigateToArticle(url: String) {
        val stack = state.backStacks[state.topLevelRoute.value]
        stack?.removeIf { it is ArticleDetail }
        stack?.add(ArticleDetail(url))
    }

    /** Navigate to web detail */
    fun navigateToWeb(url: String) {
        val stack = state.backStacks[state.topLevelRoute.value]
        stack?.removeIf { it is WebDetail }
        stack?.add(WebDetail(url))
    }

    /** Navigate to login */
    fun navigateToLogin() {
        navigate(Login)
    }

    /** Navigate to settings */
    fun navigateToSettings() {
        navigateToTab(Settings)
    }

    // Downloads and History tabs removed - consolidated into Playlists tab

    /** Navigate to library */
    fun navigateToLibrary() {
        navigateToTab(Library)
    }

    /** Navigate to notifications */
    fun navigateToNotifications() {
        val stack = state.backStacks[state.topLevelRoute.value]
        stack?.removeIf { it is Notifications }
        stack?.add(Notifications)
    }

    /** Navigate to watch later */
    fun navigateToWatchLater() {
        val stack = state.backStacks[state.topLevelRoute.value]
        stack?.removeIf { it is WatchLater }
        stack?.add(WatchLater)
    }

    /** Navigate to shorts */
    fun navigateToShorts() {
        val stack = state.backStacks[state.topLevelRoute.value]
        stack?.removeIf { it is Shorts }
        stack?.add(Shorts)
    }

    /** Navigate to tutorial */
    fun navigateToTutorial() {
        val stack = state.backStacks[state.topLevelRoute.value]
        stack?.removeIf { it is Tutorial }
        stack?.add(Tutorial)
    }

    /** Navigate to developer */
    fun navigateToDeveloper() {
        navigate(Developer)
    }

    /** Navigate to buy/license */
    fun navigateToBuy() {
        navigate(Buy)
    }

    /** Navigate to import subscriptions */
    fun navigateToImportSubscriptions() {
        navigate(ImportSubscriptions)
    }

    /** Navigate to import playlists */
    fun navigateToImportPlaylists() {
        navigate(ImportPlaylists)
    }

    /** Navigate to browser (plugin store) */
    fun navigateToBrowser(url: String) {
        val stack = state.backStacks[state.topLevelRoute.value]
        stack?.removeIf { it is Browser }
        stack?.add(Browser(url))
    }

    /** Navigate to comments */
    fun navigateToComments(url: String) {
        val stack = state.backStacks[state.topLevelRoute.value]
        stack?.removeIf { it is Comments }
        stack?.add(Comments(url))
    }

    /** Navigate to suggestions */
    fun navigateToSuggestions() {
        val stack = state.backStacks[state.topLevelRoute.value]
        stack?.removeIf { it is Suggestions }
        stack?.add(Suggestions)
    }

    /** Navigate to subscription group list */
    fun navigateToSubscriptionGroupList() {
        val stack = state.backStacks[state.topLevelRoute.value]
        stack?.removeIf { it is SubscriptionGroupList }
        stack?.add(SubscriptionGroupList)
    }

    /** Navigate to subscription group detail */
    fun navigateToSubscriptionGroup(groupId: String) {
        val stack = state.backStacks[state.topLevelRoute.value]
        stack?.removeIf { it is SubscriptionGroupDetail }
        stack?.add(SubscriptionGroupDetail(groupId))
    }

    /** Navigate to library albums */
    fun navigateToLibraryAlbums() {
        val stack = state.backStacks[state.topLevelRoute.value]
        stack?.removeIf { it is LibraryAlbums }
        stack?.add(LibraryAlbums)
    }

    /** Navigate to library album detail */
    fun navigateToLibraryAlbum(id: String) {
        val stack = state.backStacks[state.topLevelRoute.value]
        stack?.removeIf { it is LibraryAlbumDetail }
        stack?.add(LibraryAlbumDetail(id))
    }

    /** Navigate to library artists */
    fun navigateToLibraryArtists() {
        val stack = state.backStacks[state.topLevelRoute.value]
        stack?.removeIf { it is LibraryArtists }
        stack?.add(LibraryArtists)
    }

    /** Navigate to library artist detail */
    fun navigateToLibraryArtist(id: String) {
        val stack = state.backStacks[state.topLevelRoute.value]
        stack?.removeIf { it is LibraryArtistDetail }
        stack?.add(LibraryArtistDetail(id))
    }

    /** Navigate to library videos */
    fun navigateToLibraryVideos() {
        val stack = state.backStacks[state.topLevelRoute.value]
        stack?.removeIf { it is LibraryVideos }
        stack?.add(LibraryVideos)
    }

    /** Navigate to library files */
    fun navigateToLibraryFiles() {
        val stack = state.backStacks[state.topLevelRoute.value]
        stack?.removeIf { it is LibraryFiles }
        stack?.add(LibraryFiles)
    }

    /** Navigate to library search */
    fun navigateToLibrarySearch() {
        val stack = state.backStacks[state.topLevelRoute.value]
        stack?.removeIf { it is LibrarySearch }
        stack?.add(LibrarySearch)
    }

    /** Navigate to test compose (dev) */
    fun navigateToTestCompose() {
        navigate(TestCompose)
    }
}
