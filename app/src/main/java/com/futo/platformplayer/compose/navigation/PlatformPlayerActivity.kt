/*
 * PlatformPlayerActivity (Compose Navigation)
 *
 * The new Compose-based main activity for Grayjay, using navigation3
 * for adaptive landscape/portrait navigation.
 *
 * Features:
 * - Navigation3 runtime for type-safe navigation
 * - Responsive layout: bottom nav bar (portrait) / nav rail (landscape)
 * - Per-tab back stacks via NavigationState
 * - Video detail as a full-screen overlay scene
 *
 * Migration path:
 * - Legacy MainActivity handles XML-based fragments
 * - This activity handles Compose-based fragments
 * - Both can coexist during migration
 */

package com.futo.platformplayer.compose.navigation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowWidthSizeClass
import com.futo.platformplayer.compose.feed.FeedScreen

class PlatformPlayerActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PlatformPlayerNavHost()
        }
    }
}

// ==================== Navigation Bar / Rail Items ====================

private data class NavItemDef(
    val key: NavKey,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val label: String
)

private val grayjayNavItems = listOf(
    NavItemDef(Home, Icons.Outlined.Home, Icons.Filled.Home, "Home"),
    NavItemDef(Subscriptions, Icons.Outlined.Subscriptions, Icons.Filled.Subscriptions, "Subscriptions"),
    NavItemDef(Creators, Icons.Outlined.People, Icons.Filled.People, "Creators"),
    NavItemDef(Sources, Icons.Outlined.Source, Icons.Filled.Source, "Sources"),
    NavItemDef(Playlists, Icons.Outlined.PlaylistPlay, Icons.Filled.PlaylistPlay, "Playlists"),
    NavItemDef(History, Icons.Outlined.History, Icons.Filled.History, "History"),
    NavItemDef(Downloads, Icons.Outlined.Download, Icons.Filled.Download, "Downloads"),
    NavItemDef(Settings, Icons.Outlined.Settings, Icons.Filled.Settings, "Settings"),
)

// ==================== Main Nav Host ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformPlayerNavHost() {
    val navigationState = rememberGrayjayNavigationState()
    val navigator = remember(navigationState) { GrayjayNavigator(navigationState) }

    val windowAdaptiveInfo = currentWindowAdaptiveInfo()
    val isWide = windowAdaptiveInfo.windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.MEDIUM ||
               windowAdaptiveInfo.windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED

    // Get the current tab's back stack
    val currentBackStack = navigationState.currentBackStack

    if (isWide) {
        // Landscape: Navigation Rail on left + content
        Row(modifier = Modifier.fillMaxSize()) {
            GrayjayNavRail(
                items = grayjayNavItems,
                topLevelRoute = navigationState.topLevelRoute.value,
                onNavItemClick = { navigator.navigateToTab(it) }
            )
            if (currentBackStack != null) {
                NavDisplay(
                    backStack = currentBackStack,
                    onBack = {
                        if (!navigator.goBack()) {
                            navigationState.topLevelRoute.value = navigationState.startRoute
                        }
                    },
                    modifier = Modifier.weight(1f),
                    entryProvider = { key ->
                        createGrayjayNavEntry(key, navigator)
                    }
                )
            }
        }
    } else {
        // Portrait: Content + Bottom Navigation Bar
        Column(modifier = Modifier.fillMaxSize()) {
            if (currentBackStack != null) {
                NavDisplay(
                    backStack = currentBackStack,
                    onBack = {
                        if (!navigator.goBack()) {
                            navigationState.topLevelRoute.value = navigationState.startRoute
                        }
                    },
                    modifier = Modifier.weight(1f),
                    entryProvider = { key ->
                        createGrayjayNavEntry(key, navigator)
                    }
                )
            }
            GrayjayBottomNavBar(
                items = grayjayNavItems,
                topLevelRoute = navigationState.topLevelRoute.value,
                onNavItemClick = { navigator.navigateToTab(it) }
            )
        }
    }
}

// ==================== Navigation Bar / Rail ====================

@Composable
private fun GrayjayBottomNavBar(
    items: List<NavItemDef>,
    topLevelRoute: NavKey,
    onNavItemClick: (NavKey) -> Unit
) {
    NavigationBar {
        items.forEach { item ->
            NavigationBarItem(
                selected = item.key == topLevelRoute,
                onClick = { onNavItemClick(item.key) },
                icon = {
                    Icon(
                        imageVector = if (item.key == topLevelRoute) item.selectedIcon else item.icon,
                        contentDescription = item.label
                    )
                },
                label = { Text(item.label) }
            )
        }
    }
}

@Composable
private fun GrayjayNavRail(
    items: List<NavItemDef>,
    topLevelRoute: NavKey,
    onNavItemClick: (NavKey) -> Unit
) {
    NavigationRail(
        modifier = Modifier
            .width(80.dp)
            .verticalScroll(rememberScrollState())
    ) {
        items.forEach { item ->
            NavigationRailItem(
                selected = item.key == topLevelRoute,
                onClick = { onNavItemClick(item.key) },
                icon = {
                    Icon(
                        imageVector = if (item.key == topLevelRoute) item.selectedIcon else item.icon,
                        contentDescription = item.label
                    )
                },
                label = { Text(item.label) }
            )
        }
    }
}

// ==================== Nav Entry Creation ====================

private fun createGrayjayNavEntry(key: NavKey, navigator: GrayjayNavigator): NavEntry<NavKey> {
    return when (key) {
        is Home -> NavEntry(key) { HomeScene(navigator) }
        is Subscriptions -> NavEntry(key) { SubscriptionsScene(navigator) }
        is Creators -> NavEntry(key) { CreatorsScene(navigator) }
        is Sources -> NavEntry(key) { SourcesScene(navigator) }
        is Playlists -> NavEntry(key) { PlaylistsScene(navigator) }
        is History -> NavEntry(key) { HistoryScene(navigator) }
        is Downloads -> NavEntry(key) { DownloadsScene(navigator) }
        is Settings -> NavEntry(key) { SettingsScene(navigator) }
        is Library -> NavEntry(key) { LibraryScene(navigator) }
        is Search -> NavEntry(key) { SearchScene(navigator) }
        is VideoDetail -> NavEntry(key) { VideoDetailScene(key, navigator) }
        is ChannelDetail -> NavEntry(key) { ChannelDetailScene(key, navigator) }
        is PlaylistDetail -> NavEntry(key) { PlaylistDetailScene(key, navigator) }
        is SourceDetail -> NavEntry(key) { SourceDetailScene(key, navigator) }
        is ContentSearchResults -> NavEntry(key) { ContentSearchScene(key, navigator) }
        is CreatorSearchResults -> NavEntry(key) { CreatorSearchScene(key, navigator) }
        is PlaylistSearchResults -> NavEntry(key) { PlaylistSearchScene(key, navigator) }
        is PostDetail -> NavEntry(key) { PostDetailScene(key, navigator) }
        is ArticleDetail -> NavEntry(key) { ArticleDetailScene(key, navigator) }
        is WebDetail -> NavEntry(key) { WebDetailScene(key, navigator) }
        is Tutorial -> NavEntry(key) { TutorialScene(navigator) }
        is Buy -> NavEntry(key) { BuyScene(navigator) }
        is ImportSubscriptions -> NavEntry(key) { ImportSubscriptionsScene(navigator) }
        is ImportPlaylists -> NavEntry(key) { ImportPlaylistsScene(navigator) }
        is WatchLater -> NavEntry(key) { WatchLaterScene(navigator) }
        is Shorts -> NavEntry(key) { ShortsScene(navigator) }
        is Notifications -> NavEntry(key) { NotificationsScene(navigator) }
        is SubscriptionGroupDetail -> NavEntry(key) { SubscriptionGroupDetailScene(key, navigator) }
        is SubscriptionGroupList -> NavEntry(key) { SubscriptionGroupListScene(navigator) }
        is LibraryAlbums -> NavEntry(key) { LibraryAlbumsScene(navigator) }
        is LibraryAlbumDetail -> NavEntry(key) { LibraryAlbumDetailScene(key, navigator) }
        is LibraryArtists -> NavEntry(key) { LibraryArtistsScene(navigator) }
        is LibraryArtistDetail -> NavEntry(key) { LibraryArtistDetailScene(key, navigator) }
        is LibraryVideos -> NavEntry(key) { LibraryVideosScene(navigator) }
        is LibraryFiles -> NavEntry(key) { LibraryFilesScene(navigator) }
        is LibrarySearch -> NavEntry(key) { LibrarySearchScene(navigator) }
        is Login -> NavEntry(key) { LoginScene(navigator) }
        is Developer -> NavEntry(key) { DeveloperScene(navigator) }
        is Browser -> NavEntry(key) { BrowserScene(key, navigator) }
        is Comments -> NavEntry(key) { CommentsScene(key, navigator) }
        is Suggestions -> NavEntry(key) { SuggestionsScene(navigator) }
        is TestCompose -> NavEntry(key) { TestComposeScene(navigator) }
        else -> NavEntry(key) { UnknownScene(key) }
    }
}

// ==================== Scene Composables ====================

@Composable
private fun HomeScene(navigator: GrayjayNavigator) {
    FeedScreen(
        state = com.futo.platformplayer.compose.feed.FeedUiState(isLoading = true),
        onRefresh = {},
        onLoadMore = {},
        onItemClicked = { /* TODO: Navigate to video */ },
        onSortChanged = {},
        onTagClicked = {},
        modifier = Modifier.fillMaxSize()
    )
}

@Composable private fun SubscriptionsScene(n: GrayjayNavigator) = placeholder(n, "Subscriptions")
@Composable private fun CreatorsScene(n: GrayjayNavigator) = placeholder(n, "Creators")
@Composable private fun SourcesScene(n: GrayjayNavigator) = placeholder(n, "Sources")
@Composable private fun PlaylistsScene(n: GrayjayNavigator) = placeholder(n, "Playlists")
@Composable private fun HistoryScene(n: GrayjayNavigator) = placeholder(n, "History")
@Composable private fun DownloadsScene(n: GrayjayNavigator) = placeholder(n, "Downloads")
@Composable private fun SettingsScene(n: GrayjayNavigator) = placeholder(n, "Settings")
@Composable private fun LibraryScene(n: GrayjayNavigator) = placeholder(n, "Library")
@Composable private fun SearchScene(n: GrayjayNavigator) = placeholder(n, "Search")

@Composable private fun VideoDetailScene(d: VideoDetail, n: GrayjayNavigator) = placeholder(n, "Video Player", d.url, true, { n.goBack() })
@Composable private fun ChannelDetailScene(d: ChannelDetail, n: GrayjayNavigator) = placeholder(n, "Channel", d.url, true, { n.goBack() })
@Composable private fun PlaylistDetailScene(d: PlaylistDetail, n: GrayjayNavigator) = placeholder(n, "Playlist", d.url, true, { n.goBack() })
@Composable private fun SourceDetailScene(d: SourceDetail, n: GrayjayNavigator) = placeholder(n, "Source", d.url, true, { n.goBack() })
@Composable private fun ContentSearchScene(d: ContentSearchResults, n: GrayjayNavigator) = placeholder(n, "Search Results", "Query: ${d.query}", true, { n.goBack() })
@Composable private fun CreatorSearchScene(d: CreatorSearchResults, n: GrayjayNavigator) = placeholder(n, "Creator Search", "Query: ${d.query}", true, { n.goBack() })
@Composable private fun PlaylistSearchScene(d: PlaylistSearchResults, n: GrayjayNavigator) = placeholder(n, "Playlist Search", "Query: ${d.query}", true, { n.goBack() })
@Composable private fun PostDetailScene(d: PostDetail, n: GrayjayNavigator) = placeholder(n, "Post", d.url, true, { n.goBack() })
@Composable private fun ArticleDetailScene(d: ArticleDetail, n: GrayjayNavigator) = placeholder(n, "Article", d.url, true, { n.goBack() })
@Composable private fun WebDetailScene(d: WebDetail, n: GrayjayNavigator) = placeholder(n, "Web Page", d.url, true, { n.goBack() })
@Composable private fun TutorialScene(n: GrayjayNavigator) = placeholder(n, "Tutorial", showBack = true, onBack = { n.goBack() })
@Composable private fun BuyScene(n: GrayjayNavigator) = placeholder(n, "Buy License", showBack = true, onBack = { n.goBack() })
@Composable private fun ImportSubscriptionsScene(n: GrayjayNavigator) = placeholder(n, "Import Subscriptions", showBack = true, onBack = { n.goBack() })
@Composable private fun ImportPlaylistsScene(n: GrayjayNavigator) = placeholder(n, "Import Playlists", showBack = true, onBack = { n.goBack() })
@Composable private fun WatchLaterScene(n: GrayjayNavigator) = placeholder(n, "Watch Later", showBack = true, onBack = { n.goBack() })
@Composable private fun ShortsScene(n: GrayjayNavigator) = placeholder(n, "Shorts", showBack = true, onBack = { n.goBack() })
@Composable private fun NotificationsScene(n: GrayjayNavigator) = placeholder(n, "Notifications", showBack = true, onBack = { n.goBack() })
@Composable private fun SubscriptionGroupDetailScene(d: SubscriptionGroupDetail, n: GrayjayNavigator) = placeholder(n, "Subscription Group", showBack = true, onBack = { n.goBack() })
@Composable private fun SubscriptionGroupListScene(n: GrayjayNavigator) = placeholder(n, "Subscription Groups", showBack = true, onBack = { n.goBack() })
@Composable private fun LibraryAlbumsScene(n: GrayjayNavigator) = placeholder(n, "Albums", showBack = true, onBack = { n.goBack() })
@Composable private fun LibraryAlbumDetailScene(d: LibraryAlbumDetail, n: GrayjayNavigator) = placeholder(n, "Album", showBack = true, onBack = { n.goBack() })
@Composable private fun LibraryArtistsScene(n: GrayjayNavigator) = placeholder(n, "Artists", showBack = true, onBack = { n.goBack() })
@Composable private fun LibraryArtistDetailScene(d: LibraryArtistDetail, n: GrayjayNavigator) = placeholder(n, "Artist", showBack = true, onBack = { n.goBack() })
@Composable private fun LibraryVideosScene(n: GrayjayNavigator) = placeholder(n, "Library Videos", showBack = true, onBack = { n.goBack() })
@Composable private fun LibraryFilesScene(n: GrayjayNavigator) = placeholder(n, "Library Files", showBack = true, onBack = { n.goBack() })
@Composable private fun LibrarySearchScene(n: GrayjayNavigator) = placeholder(n, "Library Search", showBack = true, onBack = { n.goBack() })
@Composable private fun LoginScene(n: GrayjayNavigator) = placeholder(n, "Login", showBack = true, onBack = { n.goBack() })
@Composable private fun DeveloperScene(n: GrayjayNavigator) = placeholder(n, "Developer", showBack = true, onBack = { n.goBack() })
@Composable private fun BrowserScene(d: Browser, n: GrayjayNavigator) = placeholder(n, "Browser", d.url, true, { n.goBack() })
@Composable private fun CommentsScene(d: Comments, n: GrayjayNavigator) = placeholder(n, "Comments", d.url, true, { n.goBack() })
@Composable private fun SuggestionsScene(n: GrayjayNavigator) = placeholder(n, "Suggestions", showBack = true, onBack = { n.goBack() })
@Composable private fun TestComposeScene(n: GrayjayNavigator) = placeholder(n, "Test Compose", showBack = true, onBack = { n.goBack() })

@Composable
private fun UnknownScene(key: NavKey) {
    placeholder(GrayjayNavigator(rememberGrayjayNavigationState()), "Unknown Route", key.toString())
}

// ==================== Reusable Placeholder ====================

@Composable
private fun placeholder(
    navigator: GrayjayNavigator,
    title: String,
    subtitle: String? = null,
    showBack: Boolean = false,
    onBack: (() -> Unit)? = null
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (showBack && onBack != null) {
                TextButton(onClick = onBack) { Text("← Back") }
            }
            Text(text = title, style = MaterialTheme.typography.headlineMedium)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "(Migrate XML fragment to Compose)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
