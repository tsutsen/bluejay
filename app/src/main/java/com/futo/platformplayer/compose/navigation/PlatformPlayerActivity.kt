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
import android.util.Log
import android.view.View
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import com.futo.platformplayer.logging.Logger
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowWidthSizeClass
import com.futo.platformplayer.api.media.models.article.IPlatformArticle
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.playlists.IPlatformPlaylist
import com.futo.platformplayer.api.media.models.post.IPlatformPost
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.platforms.js.models.JSWeb
import com.futo.platformplayer.api.media.structures.IRefreshPager
import com.futo.platformplayer.api.media.structures.ReusableRefreshPager
import com.futo.platformplayer.compose.feed.FeedItem
import com.futo.platformplayer.compose.feed.FeedScreen
import com.futo.platformplayer.compose.feed.FeedUiState
import com.futo.platformplayer.compose.settings.SettingsOption
import com.futo.platformplayer.compose.settings.RadioButtonDialog
import com.futo.platformplayer.compose.settings.SettingsOptionCard
import com.futo.platformplayer.compose.settings.SettingsScreen
import com.futo.platformplayer.compose.settings.SettingsSection
import com.futo.platformplayer.compose.plugins.PluginBrowserScene
import com.futo.platformplayer.fragment.mainactivity.main.*
import com.futo.platformplayer.fragment.settings.getItemsForCategory
import com.futo.platformplayer.fragment.settings.SettingsItem
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePlatform

private const val TAG = "PlatformPlayer"

class PlatformPlayerActivity : FragmentActivity() {

    private var pendingTab: String? = null

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StateApp.instance.setGlobalContext(this, lifecycleScope, "compose")
        StateApp.instance.mainAppStarting(this)

        // Load plugins
        runBlocking {
            try {
                StatePlatform.instance.updateAvailableClients(this@PlatformPlayerActivity)
            } catch (e: Throwable) {
                Logger.e("PlatformPlayer", "Failed to update available clients", e)
            }
        }

        // Handle intent to navigate to specific tab
        val tab = intent.getStringExtra("TAB")
        if (tab != null) {
            Log.d("PlatformPlayer", "Received TAB intent: $tab")
            pendingTab = tab
        }

        setContent {
            PlatformPlayerNavHost(pendingTab)
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
    NavItemDef(Playlists, Icons.Outlined.PlaylistPlay, Icons.Filled.PlaylistPlay, "Playlists"),
    NavItemDef(Settings, Icons.Outlined.Settings, Icons.Filled.Settings, "Settings"),
)

// ==================== Main Nav Host ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformPlayerNavHost(pendingTab: String?) {
    val navigationState = rememberGrayjayNavigationState()
    val navigator = remember(navigationState) { GrayjayNavigator(navigationState) }

    // Handle pending tab navigation
    LaunchedEffect(pendingTab) {
        pendingTab?.let { tab ->
            Log.d("PlatformPlayer", "LaunchedEffect: navigating to tab: $tab")
            when (tab) {
                "BROWSE_PLUGINS" -> {
                    Log.d("PlatformPlayer", "Navigating to Settings tab (Sources removed)")
                    navigator.navigateToTab(Settings)
                }
                else -> {
                    Log.w("PlatformPlayer", "Unknown tab: $tab")
                }
            }
        }
    }

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
                val activity = LocalContext.current as FragmentActivity
                NavDisplay(
                    backStack = currentBackStack,
                    onBack = {
                        if (!navigator.goBack()) {
                            navigationState.topLevelRoute.value = navigationState.startRoute
                        }
                    },
                    modifier = Modifier.weight(1f),
                    entryProvider = { key ->
                        createGrayjayNavEntry(key, navigator, activity)
                    }
                )
            }
        }
    } else {
        // Portrait: Content + Bottom Navigation Bar
        Column(modifier = Modifier.fillMaxSize()) {
            if (currentBackStack != null) {
                val activity = LocalContext.current as FragmentActivity
                NavDisplay(
                    backStack = currentBackStack,
                    onBack = {
                        if (!navigator.goBack()) {
                            navigationState.topLevelRoute.value = navigationState.startRoute
                        }
                    },
                    modifier = Modifier.weight(1f),
                    entryProvider = { key ->
                        createGrayjayNavEntry(key, navigator, activity)
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

private fun createGrayjayNavEntry(key: NavKey, navigator: GrayjayNavigator, activity: FragmentActivity): NavEntry<NavKey> {
    return when (key) {
        is Home -> NavEntry(key) { HomeScene(navigator) }
        // Fall back to XML fragments for top-level tabs that don't have Compose implementations
        is Subscriptions -> {
            val fragment = getXmlFragmentForNavKey(key)
            if (fragment != null) {
                NavEntry(key) { FragmentFallback(fragment, activity) }
            } else {
                NavEntry(key) { SubscriptionsScene(navigator) }
            }
        }
        is Playlists -> {
            val fragment = getXmlFragmentForNavKey(key)
            if (fragment != null) {
                NavEntry(key) { FragmentFallback(fragment, activity) }
            } else {
                NavEntry(key) { PlaylistsScene(navigator) }
            }
        }
        is Library -> {
            val fragment = getXmlFragmentForNavKey(key)
            if (fragment != null) {
                NavEntry(key) { FragmentFallback(fragment, activity) }
            } else {
                NavEntry(key) { LibraryScene(navigator) }
            }
        }
        is Search -> {
            val fragment = getXmlFragmentForNavKey(key)
            if (fragment != null) {
                NavEntry(key) { FragmentFallback(fragment, activity) }
            } else {
                NavEntry(key) { SearchScene(navigator) }
            }
        }
        is Settings -> NavEntry(key) { SettingsScene(navigator) }
        is VideoDetail -> NavEntry(key) { VideoDetailScene(key, navigator) }
        is ChannelDetail -> NavEntry(key) { ChannelDetailScene(key, navigator) }
        is PlaylistDetail -> NavEntry(key) { PlaylistDetailScene(key, navigator) }
        is SourceDetail -> {
            val fragment = getXmlFragmentForNavKey(key)
            if (fragment != null) {
                NavEntry(key) { FragmentFallback(fragment, activity) }
            } else {
                NavEntry(key) { SourceDetailScene(key, navigator) }
            }
        }
        is ContentSearchResults -> {
            val fragment = getXmlFragmentForNavKey(key)
            if (fragment != null) {
                NavEntry(key) { FragmentFallback(fragment, activity) }
            } else {
                NavEntry(key) { ContentSearchScene(key, navigator) }
            }
        }
        is CreatorSearchResults -> {
            val fragment = getXmlFragmentForNavKey(key)
            if (fragment != null) {
                NavEntry(key) { FragmentFallback(fragment, activity) }
            } else {
                NavEntry(key) { CreatorSearchScene(key, navigator) }
            }
        }
        is PlaylistSearchResults -> {
            val fragment = getXmlFragmentForNavKey(key)
            if (fragment != null) {
                NavEntry(key) { FragmentFallback(fragment, activity) }
            } else {
                NavEntry(key) { PlaylistSearchScene(key, navigator) }
            }
        }
        is PostDetail -> {
            val fragment = getXmlFragmentForNavKey(key)
            if (fragment != null) {
                NavEntry(key) { FragmentFallback(fragment, activity) }
            } else {
                NavEntry(key) { PostDetailScene(key, navigator) }
            }
        }
        is ArticleDetail -> {
            val fragment = getXmlFragmentForNavKey(key)
            if (fragment != null) {
                NavEntry(key) { FragmentFallback(fragment, activity) }
            } else {
                NavEntry(key) { ArticleDetailScene(key, navigator) }
            }
        }
        is WebDetail -> {
            val fragment = getXmlFragmentForNavKey(key)
            if (fragment != null) {
                NavEntry(key) { FragmentFallback(fragment, activity) }
            } else {
                NavEntry(key) { WebDetailScene(key, navigator) }
            }
        }
        is Tutorial -> {
            val fragment = getXmlFragmentForNavKey(key)
            if (fragment != null) {
                NavEntry(key) { FragmentFallback(fragment, activity) }
            } else {
                NavEntry(key) { TutorialScene(navigator) }
            }
        }
        is Buy -> {
            val fragment = getXmlFragmentForNavKey(key)
            if (fragment != null) {
                NavEntry(key) { FragmentFallback(fragment, activity) }
            } else {
                NavEntry(key) { BuyScene(navigator) }
            }
        }
        is ImportSubscriptions -> {
            val fragment = getXmlFragmentForNavKey(key)
            if (fragment != null) {
                NavEntry(key) { FragmentFallback(fragment, activity) }
            } else {
                NavEntry(key) { ImportSubscriptionsScene(navigator) }
            }
        }
        is ImportPlaylists -> {
            val fragment = getXmlFragmentForNavKey(key)
            if (fragment != null) {
                NavEntry(key) { FragmentFallback(fragment, activity) }
            } else {
                NavEntry(key) { ImportPlaylistsScene(navigator) }
            }
        }
        is WatchLater -> {
            val fragment = getXmlFragmentForNavKey(key)
            if (fragment != null) {
                NavEntry(key) { FragmentFallback(fragment, activity) }
            } else {
                NavEntry(key) { WatchLaterScene(navigator) }
            }
        }
        is Shorts -> {
            val fragment = getXmlFragmentForNavKey(key)
            if (fragment != null) {
                NavEntry(key) { FragmentFallback(fragment, activity) }
            } else {
                NavEntry(key) { ShortsScene(navigator) }
            }
        }
        is Notifications -> {
            val fragment = getXmlFragmentForNavKey(key)
            if (fragment != null) {
                NavEntry(key) { FragmentFallback(fragment, activity) }
            } else {
                NavEntry(key) { NotificationsScene(navigator) }
            }
        }
        is SubscriptionGroupDetail -> {
            val fragment = getXmlFragmentForNavKey(key)
            if (fragment != null) {
                NavEntry(key) { FragmentFallback(fragment, activity) }
            } else {
                NavEntry(key) { SubscriptionGroupDetailScene(key, navigator) }
            }
        }
        is SubscriptionGroupList -> {
            val fragment = getXmlFragmentForNavKey(key)
            if (fragment != null) {
                NavEntry(key) { FragmentFallback(fragment, activity) }
            } else {
                NavEntry(key) { SubscriptionGroupListScene(navigator) }
            }
        }
        is LibraryAlbums -> {
            val fragment = getXmlFragmentForNavKey(key)
            if (fragment != null) {
                NavEntry(key) { FragmentFallback(fragment, activity) }
            } else {
                NavEntry(key) { LibraryAlbumsScene(navigator) }
            }
        }
        is LibraryAlbumDetail -> {
            val fragment = getXmlFragmentForNavKey(key)
            if (fragment != null) {
                NavEntry(key) { FragmentFallback(fragment, activity) }
            } else {
                NavEntry(key) { LibraryAlbumDetailScene(key, navigator) }
            }
        }
        is LibraryArtists -> {
            val fragment = getXmlFragmentForNavKey(key)
            if (fragment != null) {
                NavEntry(key) { FragmentFallback(fragment, activity) }
            } else {
                NavEntry(key) { LibraryArtistsScene(navigator) }
            }
        }
        is LibraryArtistDetail -> {
            val fragment = getXmlFragmentForNavKey(key)
            if (fragment != null) {
                NavEntry(key) { FragmentFallback(fragment, activity) }
            } else {
                NavEntry(key) { LibraryArtistDetailScene(key, navigator) }
            }
        }
        is LibraryVideos -> {
            val fragment = getXmlFragmentForNavKey(key)
            if (fragment != null) {
                NavEntry(key) { FragmentFallback(fragment, activity) }
            } else {
                NavEntry(key) { LibraryVideosScene(navigator) }
            }
        }
        is LibraryFiles -> {
            val fragment = getXmlFragmentForNavKey(key)
            if (fragment != null) {
                NavEntry(key) { FragmentFallback(fragment, activity) }
            } else {
                NavEntry(key) { LibraryFilesScene(navigator) }
            }
        }
        is LibrarySearch -> {
            val fragment = getXmlFragmentForNavKey(key)
            if (fragment != null) {
                NavEntry(key) { FragmentFallback(fragment, activity) }
            } else {
                NavEntry(key) { LibrarySearchScene(navigator) }
            }
        }
        is Login -> {
            val fragment = getXmlFragmentForNavKey(key)
            if (fragment != null) {
                NavEntry(key) { FragmentFallback(fragment, activity) }
            } else {
                NavEntry(key) { LoginScene(navigator) }
            }
        }
        is Developer -> {
            val fragment = getXmlFragmentForNavKey(key)
            if (fragment != null) {
                NavEntry(key) { FragmentFallback(fragment, activity) }
            } else {
                NavEntry(key) { DeveloperScene(navigator) }
            }
        }
        is Browser -> {
            val fragment = getXmlFragmentForNavKey(key)
            if (fragment != null) {
                NavEntry(key) { FragmentFallback(fragment, activity) }
            } else {
                NavEntry(key) { BrowserScene(key, navigator) }
            }
        }
        is Comments -> {
            val fragment = getXmlFragmentForNavKey(key)
            if (fragment != null) {
                NavEntry(key) { FragmentFallback(fragment, activity) }
            } else {
                NavEntry(key) { CommentsScene(key, navigator) }
            }
        }
        is Suggestions -> {
            val fragment = getXmlFragmentForNavKey(key)
            if (fragment != null) {
                NavEntry(key) { FragmentFallback(fragment, activity) }
            } else {
                NavEntry(key) { SuggestionsScene(navigator) }
            }
        }
        is TestCompose -> NavEntry(key) { TestComposeScene(navigator) }
        is SettingsFragment -> NavEntry(key) { SettingsFragmentScene(key, navigator) }
        // Fallback to XML fragments for backwards compatibility
        else -> {
            val xmlFragment = getXmlFragmentForNavKey(key)
            if (xmlFragment != null) {
                NavEntry(key) { FragmentFallback(xmlFragment, activity) }
            } else {
                NavEntry(key) { UnknownScene(key) }
            }
        }
    }
}

/**
 * Map a NavKey to its corresponding XML fragment for backwards compatibility.
 * Returns null if no XML fragment exists for this route.
 */
private fun getXmlFragmentForNavKey(key: NavKey): Fragment? {
    val fragment = when (key) {
        is Home -> HomeFragment()
        is Subscriptions -> SubscriptionsFeedFragment.newInstance()
        is Playlists -> PlaylistsFragment.newInstance()
        is Library -> LibraryFragment.newInstance()
        is Search -> ContentSearchResultsFragment.newInstance()
        is VideoDetail -> VideoDetailFragment.newInstance()
        is ChannelDetail -> ChannelFragment.newInstance()
        is PlaylistDetail -> PlaylistFragment.newInstance()
        is SourceDetail -> SourceDetailFragment.newInstance()
        is ContentSearchResults -> ContentSearchResultsFragment.newInstance()
        is CreatorSearchResults -> CreatorSearchResultsFragment.newInstance()
        is PlaylistSearchResults -> PlaylistSearchResultsFragment.newInstance()
        is PostDetail -> PostDetailFragment.newInstance()
        is ArticleDetail -> ArticleDetailFragment.newInstance()
        is WebDetail -> WebDetailFragment.newInstance()
        is Tutorial -> TutorialFragment.newInstance()
        is Buy -> BuyFragment.newInstance()
        is ImportSubscriptions -> ImportSubscriptionsFragment.newInstance()
        is ImportPlaylists -> ImportPlaylistsFragment.newInstance()
        is WatchLater -> WatchLaterFragment.newInstance()
        is Shorts -> ShortsFragment.newInstance()
        is Notifications -> SuggestionsFragment.newInstance() // Fallback to suggestions if no notifications fragment
        is SubscriptionGroupDetail -> SubscriptionGroupFragment.newInstance()
        is SubscriptionGroupList -> SubscriptionGroupListFragment.newInstance()
        is LibraryAlbums -> LibraryAlbumsFragment.newInstance()
        is LibraryAlbumDetail -> LibraryAlbumFragment.newInstance()
        is LibraryArtists -> LibraryArtistsFragment.newInstance()
        is LibraryArtistDetail -> LibraryArtistFragment.newInstance()
        is LibraryVideos -> LibraryVideosFragment.newInstance()
        is LibraryFiles -> LibraryFilesFragment.newInstance()
        is LibrarySearch -> LibrarySearchFragment.newInstance()
        is Login -> LoginFragment.newInstance()
        is Developer -> DeveloperFragment.newInstance()
        is Browser -> BrowserFragment.newInstance()
        is Comments -> CommentsFragment.newInstance()
        is Suggestions -> SuggestionsFragment.newInstance()
        else -> null
    }
    
    if (fragment != null) {
        Log.d("PlatformPlayer", "getXmlFragmentForNavKey: ${key.javaClass.simpleName} -> ${fragment.javaClass.simpleName}")
    } else {
        Log.w("PlatformPlayer", "getXmlFragmentForNavKey: No fragment for ${key.javaClass.simpleName}")
    }
    
    return fragment
}

// ==================== Scene Composables ====================

@Composable
private fun HomeScene(navigator: GrayjayNavigator) {
    var uiState by remember { mutableStateOf(FeedUiState(isLoading = true)) }
    var pager by remember { mutableStateOf<com.futo.platformplayer.api.media.structures.ReusableRefreshPager<com.futo.platformplayer.api.media.models.contents.IPlatformContent>?>(null) }
    var items by remember { mutableStateOf<List<com.futo.platformplayer.compose.feed.FeedItem>>(emptyList()) }
    var contentList by remember { mutableStateOf<List<com.futo.platformplayer.api.media.models.contents.IPlatformContent>>(emptyList()) }

    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        val job = scope.launch {
            try {
                Log.d("HomeScene", "Loading feed...")
                val p = com.futo.platformplayer.states.StatePlatform.instance.getHomeRefresh(this)
                Log.d("HomeScene", "Got pager: $p")
                if (p is com.futo.platformplayer.api.media.structures.IRefreshPager) {
                    val rp = com.futo.platformplayer.api.media.structures.ReusableRefreshPager(p)
                    pager = rp
                    rp.nextPage()
                    val loaded = rp.getResults()
                    Log.d("HomeScene", "Loaded ${loaded.size} items")
                    val feedItems = loaded.map { toFeedItem(it) }
                    contentList = loaded
                    items = feedItems
                    uiState = FeedUiState(isLoading = false, items = feedItems)
                } else {
                    Log.w("HomeScene", "No refreshable pager: ${p?.javaClass}")
                    uiState = FeedUiState(isLoading = false, items = emptyList())
                }
            } catch (e: Exception) {
                Log.e("HomeScene", "Error loading feed", e)
                uiState = uiState.copy(isLoading = false, error = e.message)
            }
        }
        onDispose { job.cancel() }
    }

    FeedScreen(
        state = uiState,
        onRefresh = {
            pager?.let { p ->
                p.nextPage()
                val loaded = p.getResults()
                val feedItems = loaded.map { toFeedItem(it) }
                contentList = loaded
                items = feedItems
                uiState = uiState.copy(items = feedItems)
            }
        },
        onLoadMore = {
            pager?.let { p ->
                if (p.hasMorePages()) {
                    p.nextPage()
                    val loaded = p.getResults()
                    val feedItems = loaded.map { toFeedItem(it) }
                    contentList = loaded
                    items = feedItems
                    uiState = uiState.copy(items = feedItems)
                }
            }
        },
        onItemClicked = { id ->
            val content = contentList.find { it.id?.value == id }
            when (content) {
                is IPlatformVideo -> {
                    navigator.navigateToVideo(content.url)
                }
                is IPlatformPlaylist -> {
                    navigator.navigateToPlaylist(content.url)
                }
                is IPlatformPost -> {
                    navigator.navigateToPost(content.url)
                }
                is IPlatformArticle -> {
                    navigator.navigateToArticle(content.url)
                }
                is JSWeb -> {
                    navigator.navigateToWeb(content.url)
                }
            }
        },
        onSortChanged = {},
        onTagClicked = {},
        modifier = Modifier.fillMaxSize()
    )
}

@Composable private fun SubscriptionsScene(n: GrayjayNavigator) = placeholder(n, "Subscriptions")
@Composable private fun SourcesScene(n: GrayjayNavigator) = placeholder(n, "Sources (removed)")
@Composable private fun PlaylistsScene(n: GrayjayNavigator) = placeholder(n, "Playlists")
@Composable
private fun SettingsScene(n: GrayjayNavigator) {
    SettingsScreen(
        title = "Settings",
        onBack = { n.goBack() }
    ) {
        SettingsSection("General")
        SettingsOptionCard(Icons.Default.Palette, "Appearance", "Theme, colors, typography, icons, contrast") {
            n.navigate(SettingsFragment("appearance"))
        }
        SettingsSection("Feed & Content")
        SettingsOptionCard(Icons.Default.Add, "Plugins", "Browse and manage plugins") {
            n.navigate(SettingsFragment("plugins"))
        }
        SettingsOptionCard(Icons.Default.Feed, "Feed & Content", "Home feed, search, channels, subscriptions") {
            n.navigate(SettingsFragment("feed"))
        }
        SettingsOptionCard(Icons.Default.PlayArrow, "Player", "Playback, downloads, gestures, casting") {
            n.navigate(SettingsFragment("player"))
        }
        SettingsOptionCard(Icons.Default.Lock, "Privacy & Data", "Privacy, data management, backup & restore") {
            n.navigate(SettingsFragment("privacy"))
        }
        SettingsOptionCard(Icons.Default.Sync, "Sync & Identity", "Synchronization, Polycentric") {
            n.navigate(SettingsFragment("sync"))
        }
        SettingsOptionCard(Icons.Default.Settings, "General", "Language, tabs, link handling, FAQ") {
            n.navigate(SettingsFragment("general"))
        }
        SettingsOptionCard(Icons.Default.Info, "About", "Version, license, payment") {
            n.navigate(SettingsFragment("about"))
        }
    }
}

@Composable
private fun SettingsSubScene(category: String, n: GrayjayNavigator) {
    // Special case for Plugins
    if (category == "plugins") {
        Log.d(TAG, "Rendering PluginBrowserScene")
        PluginBrowserScene()
        return
    }
    
    val items = getItemsForCategory(category)
    var dialogItem by remember { mutableStateOf<SettingsItem?>(null) }
    
    SettingsScreen(
        title = category.replace("_", " ").replaceFirstChar { it.uppercase() },
        onBack = { n.goBack() }
    ) {
        items.forEach { item ->
            SettingsOptionCard(
                icon = item.icon,
                title = item.title,
                subtitle = item.subtitle
            ) {
                when {
                    item.subCategory != null -> {
                        n.navigate(SettingsFragment(item.subCategory))
                    }
                    item.dialogOptions != null -> {
                        dialogItem = item
                    }
                }
            }
        }
        
        // Show dialog for items with options
        dialogItem?.let { item ->
            val options = item.dialogOptions!!
            val selected = SettingsOption(item.subtitle)
            RadioButtonDialog(
                title = item.title,
                options = options,
                selected = selected,
                onSelected = { selectedOption ->
                    dialogItem = null
                },
                onDismiss = { dialogItem = null }
            )
        }
    }
}
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
@Composable private fun SettingsFragmentScene(d: SettingsFragment, n: GrayjayNavigator) {
    SettingsSubScene(d.category, n)
}

@Composable
private fun UnknownScene(key: NavKey) {
    placeholder(GrayjayNavigator(rememberGrayjayNavigationState()), "Unknown Route", key.toString())
}

@Composable
private fun FragmentFallback(fragment: Fragment, activity: FragmentActivity) {
    Log.d("PlatformPlayer", "FragmentFallback: ${fragment.javaClass.simpleName}")
    
    // Host the XML fragment in a FragmentContainerView
    AndroidView(
        factory = { context ->
            Log.d("PlatformPlayer", "Creating FragmentContainerView for ${fragment.javaClass.simpleName}")
            // Use a unique ID for the container, not android.R.id.content
            val containerView = androidx.fragment.app.FragmentContainerView(context).apply {
                id = View.generateViewId()
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            
            // Perform fragment transaction on this container
            val fragmentManager = activity.supportFragmentManager
            if (fragmentManager != null) {
                val transaction = fragmentManager.beginTransaction()
                Log.d("PlatformPlayer", "Transaction created, replacing container ${containerView.id} with ${fragment.javaClass.simpleName}")
                transaction.replace(containerView.id, fragment)
                transaction.commit()
                Log.d("PlatformPlayer", "Transaction committed")
            } else {
                Log.e("PlatformPlayer", "Failed to get FragmentManager from activity")
            }
            
            containerView
        }
    )
}

// ==================== Helper Functions ====================

private fun toFeedItem(content: com.futo.platformplayer.api.media.models.contents.IPlatformContent): com.futo.platformplayer.compose.feed.FeedItem {
    val thumbnailUrl = when (content) {
        is com.futo.platformplayer.api.media.models.video.IPlatformVideo -> content.thumbnails.getHQThumbnail()
        else -> null
    }
    return com.futo.platformplayer.compose.feed.FeedItem(
        id = content.id?.value ?: "",
        title = content.name ?: "",
        subtitle = content.author?.name,
        thumbnailUrl = thumbnailUrl,
        timestamp = null
    )
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
