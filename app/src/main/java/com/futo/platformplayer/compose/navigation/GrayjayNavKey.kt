/*
 * Grayjay Navigation Keys
 *
 * Defines all navigation routes for the Compose-based navigation system.
 * Top-level routes correspond to bottom bar / navigation rail tabs.
 * Sub-routes are pushed onto the active tab's back stack.
 *
 * Modeled after nav3-recipes patterns using androidx.navigation3.
 */

package com.futo.platformplayer.compose.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// ==================== Top-Level Routes (Tabs) ====================

/** Home / Feed tab — the main content feed */
@Serializable
data object Home : NavKey

/** Subscriptions tab — subscribed channels' latest content */
@Serializable
data object Subscriptions : NavKey

/** Playlists tab — user-created and remote playlists, history, and downloads */
@Serializable
data object Playlists : NavKey

/** Settings tab — app settings */
@Serializable
data object Settings : NavKey

/** Library tab — local media library */
@Serializable
data object Library : NavKey

/** Search results (shown as overlay from any tab) */
@Serializable
data object Search : NavKey

// ==================== Sub-Routes (pushed onto tab back stacks) ====================

/** Video player detail — can be maximized (fullscreen) or minimized (PiP-style) */
@Serializable
data class VideoDetail(val url: String, val position: Long? = null, val startMinimized: Boolean = false) : NavKey

/** Channel / creator detail page */
@Serializable
data class ChannelDetail(val url: String) : NavKey

/** Playlist detail page (local or remote) */
@Serializable
data class PlaylistDetail(val url: String, val isRemote: Boolean = false) : NavKey

/** Source detail page */
@Serializable
data class SourceDetail(val url: String) : NavKey

/** Content search results (video search) */
@Serializable
data class ContentSearchResults(val query: String) : NavKey

/** Creator search results */
@Serializable
data class CreatorSearchResults(val query: String) : NavKey

/** Playlist search results */
@Serializable
data class PlaylistSearchResults(val query: String) : NavKey

/** Post detail page */
@Serializable
data class PostDetail(val url: String) : NavKey

/** Article detail page */
@Serializable
data class ArticleDetail(val url: String) : NavKey

/** Web page detail (embedded browser) */
@Serializable
data class WebDetail(val url: String) : NavKey

/** Tutorial / onboarding */
@Serializable
data object Tutorial : NavKey

/** Buy / licensing page */
@Serializable
data object Buy : NavKey

/** Import subscriptions */
@Serializable
data object ImportSubscriptions : NavKey

/** Import playlists */
@Serializable
data object ImportPlaylists : NavKey

/** Watch later / saved videos */
@Serializable
data object WatchLater : NavKey

/** Shorts / vertical video feed */
@Serializable
data object Shorts : NavKey

/** Notification overlay */
@Serializable
data object Notifications : NavKey

/** Subscription group detail */
@Serializable
data class SubscriptionGroupDetail(val groupId: String) : NavKey

/** Subscription group list */
@Serializable
data object SubscriptionGroupList : NavKey

/** Library sub-pages */
@Serializable
data object LibraryAlbums : NavKey

@Serializable
data class LibraryAlbumDetail(val id: String) : NavKey

@Serializable
data object LibraryArtists : NavKey

@Serializable
data class LibraryArtistDetail(val id: String) : NavKey

@Serializable
data object LibraryVideos : NavKey

@Serializable
data object LibraryFiles : NavKey

@Serializable
data object LibrarySearch : NavKey

/** Login page */
@Serializable
data object Login : NavKey

/** Developer / debug page */
@Serializable
data object Developer : NavKey

/** Browser (plugin store) */
@Serializable
data class Browser(val url: String) : NavKey

/** Comments page */
@Serializable
data class Comments(val url: String) : NavKey

/** Suggestions page */
@Serializable
data object Suggestions : NavKey

/** Test compose page (development) */
@Serializable
data object TestCompose : NavKey

/** Settings sub-screen — category parameter specifies which settings page */
@Serializable
data class SettingsFragment(val category: String) : NavKey

// ==================== Helpers ====================

/**
 * All top-level (tab) routes. Used by NavigationState to manage per-tab back stacks.
 */
val topLevelRoutes: Set<NavKey> = setOf(
    Home, Subscriptions, Playlists,
    Settings, Library, Search
)

/**
 * Check if a route is a top-level tab route.
 */
fun NavKey.isTopLevel(): Boolean = this in topLevelRoutes

/**
 * Check if a route represents the video player.
 */
fun NavKey.isVideoDetail(): Boolean = this is VideoDetail

/**
 * Check if a route is search-related.
 */
fun NavKey.isSearch(): Boolean = this is ContentSearchResults ||
    this is CreatorSearchResults ||
    this is PlaylistSearchResults ||
    this is Search
