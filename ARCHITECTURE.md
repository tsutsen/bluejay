# Grayjay Fork — Architecture Plan

> **Status**: Draft — pending Phase 0 audit (see §17 Open Questions)  
> **Last updated**: 2026-07-23  
> **Target**: AYN Thor dual-screen handheld (API 33+)  
> **Scope**: Complete architecture rewrite from XML → Compose, with modularization, MVI, and dual-screen support

---

## 1. Overview

This document defines the target architecture for the Grayjay fork. It replaces the monolithic XML-based codebase with a modular, Compose-first architecture following Android's official guidance, adapted for:

- **Dual-screen** (AYN Thor primary + secondary display)
- **Responsive layouts** (NavigationRail on landscape, bottom bar on portrait)
- **Material You** theming (dynamic color, Inter font, Material Symbols)
- **Extensibility** (each feature is independently compilable)
- **Debuggability** (observable state, sealed routes, explicit state machines)

### Non-Goals

- Do not modify the source plugin engine (`engine/`, `stable/assets/sources/`, `api/media/IPlatformClient.kt`)
- Do not add Android TV / Leanback support
- Do not support API levels below 33
- Do not implement cross-app media control (MediaBrowser, etc.)

---

## 2. Immutable Boundary: Source Plugin Engine

The following packages are **immutable**. They fetch data from content sources and return typed interfaces. Everything above them is rebuilt.

```
DO NOT TOUCH:
├── api/media/                          ← IPlatformClient, IPlatformVideo, IPlatformContent, IPlatformChannel
├── api/media/platforms/js/             ← JSClient, DevJSClient, JS model wrappers
├── api/media/platforms/local/          ← LocalClient
├── engine/                             ← V8 runtime, PackageHttp, PackageDOMParser, PackageBridge
├── stable/assets/sources/              ← JS plugin scripts (YouTube, SoundCloud, etc.)
└── dep/polycentricandroid/             ← Decentralized social (leave as-is)
```

### The Contract

```
Source Plugin (JS)
    ↓ returns
IPlatformVideo / IPlatformContent / IPlatformChannel
    ↓ consumed by
Repository layer (NEW)
    ↓ exposes as
StateFlow<UiState>
    ↓ observed by
ViewModel → Compose Screen
```

The plugin engine is a black box. It takes a source URL, returns typed interfaces. Repositories normalize, persist, and expose data. The UI layer never touches the plugin engine directly.

**Reference**: `IPlatformClient` interface at `app/src/main/java/com/futo/platformplayer/api/media/IPlatformClient.kt`

---

## 3. Module Structure

```
app/src/main/java/com/futo/platformplayer/
│
│  ┌─── IMMUTABLE: Source Engine ──────────────────────────┐
│  │  api/media/                    ← interfaces + models
│  │  engine/                       ← V8, packages, JSDOM
│  │  stable/assets/sources/        ← JS plugin scripts
│  └──────────────────────────────────────────────────────┘
│
│  ┌─── NEW: Foundation Modules ───────────────────────────┐
│  │  app/                              ← Application, Hilt setup, entry points
│  │  core/model/                       ← Shared domain models (QueueItem, etc.)
│  │  core/data/                        ← Repositories (wraps IPlatformClient)
│  │  core/database/                    ← Room DAOs + entities
│  │  core/datastore/                   ← DataStore preferences
│  │  core/navigation/                  ← Navigator, NavHost, sealed routes
│  │  core/designsystem/                ← Theme, components, tokens, icons
│  │  core/ui/                          ← Shared UI utilities, modifiers
│  │  core/testing/                     ← Test helpers, fake repositories
│  │  core/sync/                        ← Polycentric sync coordination
│  │  core/notifications/               ← Notification management
│  └──────────────────────────────────────────────────────┘
│
│  ┌─── NEW: Feature Modules ──────────────────────────────┐
│  │  feature/home/impl/              ← Home feed
│  │  feature/search/impl/            ← Search
│  │  feature/player/impl/            ← Video player
│  │  feature/library/impl/           ← Library (artists, albums, files)
│  │  feature/subscriptions/impl/     ← Subscriptions
│  │  feature/feed/impl/              ← Feed management
│  │  feature/settings/impl/          ← Settings + Appearance
│  │  feature/plugins/impl/           ← Plugin browser
│  │  feature/casting/impl/           ← Casting
│  │  feature/downloads/impl/         ← Downloads
│  │  feature/companion/impl/         ← Companion window screens
│  │  feature/dualscreen/             ← State machine, screen coordination
│  └──────────────────────────────────────────────────────┘
│
│  ┌─── MIGRATION LAYER (temporary, deprecate over time) ─┐
│  │  ui/interop/                     ← ComposeFragment bridge
│  │  ui/scene/                       ← Scene→Fragment adapters
│  │  fragment/                       ← Old XML Fragments
│  │  views/                          ← Old XML Views
│  │  dialogs/                        ← Old dialogs
│  │  states/                         ← Old State* singletons
│  │  activities/                     ← Old Activities
│  │  models/                         ← Old data models
│  │  casting/                        ← Old casting
│  │  downloads/                      ← Old downloads
│  │  sabr/                           ← Old Sabr
│  │  encryption/                     ← Old encryption
│  │  polycentric/                    ← Old polycentric
│  │  Utility.kt                      ← Old utilities
│  │  UISlideOverlays.kt              ← Old overlays
│  │  RootInsetsController.kt         ← Old insets
│  │  SettingsDev.kt                  ← Old settings dev
│  └──────────────────────────────────────────────────────┘
│
│  ┌─── NEW: Activities (thin wrappers) ──────────────────┐
│  │  activities/
│  │    MainActivity.kt             ← Hosts AppLayout, observes state
│  │    CompanionActivity.kt        ← Secondary display window
│  │    CaptchaActivity.kt          ← Keep, minimal
│  └──────────────────────────────────────────────────────┘
```

### Module Dependency Rules

```
app → feature:* → core:* → (immutable engine)
feature:* ↔ feature:* (only via core: interfaces)
core:* → core:* (allowed, but keep dependencies minimal)
feature:* → feature:* (NOT allowed — use core: as mediator)
```

**Temporary exception**: `core/data` repository implementations (§4) wrap legacy
`State*` singletons (`StatePlayer`, `StatePlatform`, ...) that live in the
"MIGRATION LAYER" bucket above, which is slated for deletion in Phase 8. This means
`core:data`, a Foundation module, has a real dependency on code marked temporary —
that's accepted as a necessary migration bridge, not an oversight, but it must not be
forgotten: **Phase 8 cannot delete `states/` until every `core:data` repository impl
has been re-pointed at genuinely new data sources (Room, DataStore, or the immutable
engine directly)**. Add a grep-for-`State`-imports-in-`core/data` check as a Phase 8
entry criterion.

**Reference**: [Now in Android modularization guide](https://github.com/nowinandroid/nowinandroid/blob/main/docs/ModularizationLearningJourney.md)

---

## 4. Data Layer: Repositories

Repositories sit between the immutable plugin engine and the UI layer. They normalize, persist, and expose data as `StateFlow`.

### Repository Interfaces

```kotlin
// core/data/repository/PlayerRepository.kt
interface PlayerRepository {
    fun observeQueue(): Flow<List<QueueItem>>
    fun observeCurrentItem(): Flow<QueueItem?>
    fun observePlaybackState(): Flow<PlaybackState>
    suspend fun play(video: IPlatformVideo)
    suspend fun pause()
    suspend fun next()
    suspend fun previous()
    suspend fun addToQueue(video: IPlatformVideo)
    suspend fun removeFromQueue(videoId: String)
    suspend fun setQueue(items: List<QueueItem>)
    val player: Player  // Direct ExoPlayer access for companion window
}

// core/data/repository/HomeRepository.kt
interface HomeRepository {
    fun observeHomeFeed(page: Int = 0): Flow<HomeFeedResult>
    suspend fun refreshHomeFeed()
}

// core/data/repository/SearchRepository.kt
interface SearchRepository {
    fun search(query: String, page: Int = 0): Flow<SearchResult>
}

// core/data/repository/LibraryRepository.kt
interface LibraryRepository {
    fun observeArtists(): Flow<List<Artist>>
    fun observeAlbums(): Flow<List<Album>>
    fun observePlaylists(): Flow<List<Playlist>>
    suspend fun createPlaylist(name: String): Playlist
    suspend fun deletePlaylist(id: String)
    suspend fun addVideosToPlaylist(playlistId: String, videos: List<IPlatformVideo>)
}

// core/data/repository/SubscriptionRepository.kt
interface SubscriptionRepository {
    fun observeSubscriptions(): Flow<List<Subscription>>
    suspend fun subscribe(channel: IPlatformChannel)
    suspend fun unsubscribe(channelId: String)
}

// core/data/repository/SettingsRepository.kt
interface SettingsRepository {
    fun observePreferences(): Flow<AppPreferences>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setFont(font: FontChoice)
    suspend fun setIconStyle(style: IconStyle)
}
```

### Migration Bridge Pattern

During migration, repository implementations wrap the legacy `State*` classes:

```kotlin
// core/data/repository/impl/PlayerRepositoryImpl.kt
@HiltSingleton
class PlayerRepositoryImpl @Inject constructor(
    private val statePlayer: StatePlayer,        // Legacy bridge
    private val playerManager: PlayerManager,    // Legacy bridge
    private val queueDao: QueueDao,              // New persistence
    private val historyDao: HistoryDao           // New persistence
) : PlayerRepository {

    private val _playbackState = MutableStateFlow(PlaybackState.Idle)

    override val player: Player
        get() = statePlayer.instance.player

    override fun observeQueue(): Flow<List<QueueItem>> =
        queueDao.observeAll().map { it.map { q -> QueueItem.from(q) } }

    override suspend fun play(video: IPlatformVideo) {
        statePlayer.instance.setQueueWithExisting(listOf(video))
        statePlayer.instance.player.play()
    }

    override suspend fun pause() {
        statePlayer.instance.player.pause()
    }

    // ... other methods bridge to StatePlayer during migration
}
```

**Reference**: [Now in Android OfflineFirstNewsRepository](https://github.com/nowinandroid/nowinandroid/blob/main/core/data/src/main/kotlin/com/google/samples/apps/nowinandroid/core/data/repository/OfflineFirstNewsRepository.kt)

---

## 5. UI Layer: MVI with Sealed UiState

Every feature screen follows the same pattern: sealed `UiState`, `StateFlow`, `ViewModel`.

### ViewModel Pattern

```kotlin
// feature/home/impl/HomeViewModel.kt
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeRepository: HomeRepository,
    private val playerRepository: PlayerRepository
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Success(
            val sections: List<FeedSection>,
            val isLoadingMore: Boolean = false,
            val error: String? = null,
            val selectedVideo: IPlatformVideo? = null
        ) : UiState
        data class Error(val message: String) : UiState
    }

    data class FeedSection(
        val title: String,
        val videos: List<IPlatformVideo>,
        val loadMore: suspend () -> Unit
    )

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            homeRepository.observeHomeFeed()
                .collect { result ->
                    _uiState.value = UiState.Success(
                        sections = result.sections.map { section ->
                            FeedSection(
                                title = section.title,
                                videos = section.videos,
                                loadMore = { /* paginate */ }
                            )
                        }
                    )
                }
        }
    }

    fun onVideoClicked(video: IPlatformVideo) {
        val current = _uiState.value as? UiState.Success ?: return
        _uiState.value = current.copy(selectedVideo = video)
    }
}
```

### Screen Pattern

```kotlin
// feature/home/impl/HomeScreen.kt
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onVideoSelected: (IPlatformVideo) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val s = state) {
        is UiState.Loading -> LoadingScreen()
        is UiState.Error -> ErrorScreen(s.message) { viewModel.retry() }
        is UiState.Success -> HomeContent(
            sections = s.sections,
            onVideoClick = onVideoSelected
        )
    }
}
```

**Reference**: [Now in Android SearchViewModel](https://github.com/nowinandroid/nowinandroid/blob/main/feature/search/impl/src/main/kotlin/com/google/samples/apps/nowinandroid/feature/search/impl/SearchViewModel.kt)  
**Reference**: [Now in Android SearchScreen](https://github.com/nowinandroid/nowinandroid/blob/main/feature/search/impl/src/main/kotlin/com/google/samples/apps/nowinandroid/feature/search/impl/SearchScreen.kt)  
**Reference**: [Flow MusicViewModel](https://github.com/aedev/flow/blob/main/app/src/main/java/io/github/aedev/flow/ui/screens/music/MusicViewModel.kt)

---

## 6. Navigation: Sealed Routes + Navigator

Centralized navigation with sealed destinations prevents string-based route typos.

### NavDestination

```kotlin
// core/navigation/NavDestination.kt
sealed class NavDestination(val route: String) {
    object Home : NavDestination("home")
    object Search : NavDestination("search")
    object Library : NavDestination("library")
    object Subscriptions : NavDestination("subscriptions")
    object Notifications : NavDestination("notifications")
    object Settings : NavDestination("settings")
    // PluginBrowser is reachable from the Settings screen ("Open Plugin Browser →"),
    // not one of the 6 main tabs — see DESIGN.md §6. It is NOT in AppLayout.destinations.
    object PluginBrowser : NavDestination("plugins")

    // NOTE: `route` on template patterns below (e.g. VideoDetail.ROUTE) is a nav-graph
    // template with placeholders, used for `composable(...)` registration. `createRoute(...)`
    // is a companion function producing a concrete, navigable path — it does NOT require an
    // instance, so `NavDestination.VideoDetail.createRoute(id)` works directly.

    data class VideoDetail(val videoId: String, val channelId: String? = null) :
        NavDestination(ROUTE) {
        companion object {
            const val ROUTE = "video_detail/{videoId}"
            fun createRoute(videoId: String, channelId: String? = null) =
                "video_detail/$videoId${channelId?.let { "/$it" } ?: ""}"
        }
    }

    data class ArtistDetail(val artistId: String) :
        NavDestination(ROUTE) {
        companion object {
            const val ROUTE = "artist_detail/{artistId}"
            fun createRoute(artistId: String) = "artist_detail/$artistId"
        }
    }

    data class ChannelDetail(val channelId: String) :
        NavDestination(ROUTE) {
        companion object {
            const val ROUTE = "channel_detail/{channelId}"
            fun createRoute(channelId: String) = "channel_detail/$channelId"
        }
    }

    data class PlaylistDetail(val playlistId: String) :
        NavDestination(ROUTE) {
        companion object {
            const val ROUTE = "playlist_detail/{playlistId}"
            fun createRoute(playlistId: String) = "playlist_detail/$playlistId"
        }
    }

    data class Comments(val videoId: String) :
        NavDestination(ROUTE) {
        companion object {
            const val ROUTE = "comments/{videoId}"
            fun createRoute(videoId: String) = "comments/$videoId"
        }
    }

    data class ArticleDetail(val articleId: String) :
        NavDestination(ROUTE) {
        companion object {
            const val ROUTE = "article_detail/{articleId}"
            fun createRoute(articleId: String) = "article_detail/$articleId"
        }
    }

    data class WebDetail(val url: String) :
        NavDestination(ROUTE) {
        companion object {
            const val ROUTE = "web_detail/{url}"
            fun createRoute(url: String) = "web_detail/$url"
        }
    }

    data class PostDetail(val postId: String) :
        NavDestination(ROUTE) {
        companion object {
            const val ROUTE = "post_detail/{postId}"
            fun createRoute(postId: String) = "post_detail/$postId"
        }
    }
}
```

### Navigator

```kotlin
// core/navigation/Navigator.kt
@HiltSingleton
class Navigator @Inject constructor(
    private val navController: NavHostController
) {
    fun navigate(
        destination: NavDestination,
        route: String = destination.route,
        options: NavOptionsBuilder.() -> Unit = {
            popUpTo(NavDestination.Home.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    ) {
        navController.navigate(route, options)
    }

    fun navigateUp() = navController.navigateUp()
    fun popBackStack() = navController.popBackStack()
    val currentRoute: String? get() = navController.currentBackStackEntry?.destination?.route
}
```

### NavGraph

```kotlin
// core/navigation/GrayjayNavGraph.kt
@Composable
fun GrayjayNavGraph(
    navigator: Navigator,
    startDestination: NavDestination = NavDestination.Home
) {
    NavHost(
        navController = navigator.navController,
        startDestination = startDestination.route
    ) {
        composable(NavDestination.Home.route) {
            val vm: HomeViewModel = hiltViewModel()
            HomeScreen(
                viewModel = vm,
                onVideoSelected = {
                    navigator.navigate(
                        destination = NavDestination.VideoDetail(it.videoId),
                        route = NavDestination.VideoDetail.createRoute(it.videoId)
                    )
                }
            )
        }

        composable(NavDestination.Search.route) {
            SearchScreen(/* ... */)
        }

        composable(NavDestination.Library.route) {
            LibraryScreen(/* ... */)
        }

        composable(NavDestination.Subscriptions.route) {
            SubscriptionsScreen(/* ... */)
        }

        composable(NavDestination.Settings.route) {
            SettingsScreen(/* ... */)
        }

        composable(NavDestination.VideoDetail.ROUTE) { backStackEntry ->
            val videoId = backStackEntry.arguments?.getString("videoId") ?: return@composable
            val channelId = backStackEntry.arguments?.getString("channelId")
            VideoDetailScreen(videoId = videoId, channelId = channelId)
        }

        composable(NavDestination.ArtistDetail.ROUTE) { backStackEntry ->
            val artistId = backStackEntry.arguments?.getString("artistId") ?: return@composable
            ArtistDetailScreen(artistId = artistId)
        }

        // ... all other routes
    }
}
```

**Reference**: [Now in Android Navigator](https://github.com/nowinandroid/nowinandroid/blob/main/core/navigation/src/main/kotlin/com/google/samples/apps/nowinandroid/core/navigation/Navigator.kt)  
**Reference**: [Now in Android NavigationState](https://github.com/nowinandroid/nowinandroid/blob/main/core/navigation/src/main/kotlin/com/google/samples/apps/nowinandroid/core/navigation/NavigationState.kt)  
**Reference**: [Nav3 Recipes EntryProviderInstaller](https://github.com/nav3-recipes/nav3-recipes/blob/main/app/src/main/java/com/example/nav3recipes/modular/hilt/README.md)

---

## 7. App Layout: Orientation-Aware Chrome

Single Compose layout that detects orientation and renders NavigationRail (landscape) or bottom bar (portrait).

### AppLayout

```kotlin
// core/designsystem/layout/AppLayout.kt
@Composable
fun AppLayout(
    navigator: Navigator,
    screenState: StateFlow<AppScreenState>,
    modifier: Modifier = Modifier
) {
    val orientation by remember {
        derivedStateOf { LocalConfiguration.current.orientation }
    }
    val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE

    // Hoisted above the Row — referenced by both the landscape rail and the portrait bar below.
    val destinations = remember {
        listOf(
            NavDestination.Home to Icons.Default.Home,
            NavDestination.Search to Icons.Default.Search,
            NavDestination.Subscriptions to Icons.Default.Subscriptions,
            NavDestination.Library to Icons.Default.LibraryBooks,
            NavDestination.Notifications to Icons.Default.Notifications,
            NavDestination.Settings to Icons.Default.Settings
        )
    }

    Row(modifier = modifier.fillMaxSize()) {
        if (isLandscape) {
            NavigationRail(
                modifier = Modifier.width(56.dp),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                destinations.forEach { (dest, icon) ->
                    NavigationRailItem(
                        selected = navigator.currentRoute == dest.route,
                        onClick = { navigator.navigate(dest) },
                        icon = { Icon(icon, contentDescription = dest.route) }
                    )
                }
            }
        }

        Box(modifier = Modifier
            .weight(1f)
            .fillMaxHeight()) {
            GrayjayNavGraph(navigator = navigator)
        }

        if (!isLandscape) {
            NavigationBar(
                modifier = Modifier.height(56.dp),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                destinations.forEach { (dest, icon) ->
                    NavigationBarItem(
                        selected = navigator.currentRoute == dest.route,
                        onClick = { navigator.navigate(dest) },
                        icon = { Icon(icon, contentDescription = dest.route) }
                    )
                }
            }
        }
    }
}
```

**Reference**: [Now in Android NiaAppState](https://github.com/nowinandroid/nowinandroid/blob/main/app/src/main/kotlin/com/google/samples/apps/nowinandroid/ui/NiaAppState.kt) (orientation-aware navigation state)

---

## 8. Dual-Screen: State Machine + Companion Window

### AppScreenState

```kotlin
// feature/dualscreen/AppScreenState.kt
sealed class AppScreenState {
    object Browsing : AppScreenState()
    data class VideoOpen(val videoId: String) : AppScreenState()
    data class VideoMinimized(val videoId: String) : AppScreenState()
}
```

### ScreenCoordinator

```kotlin
// feature/dualscreen/ScreenCoordinator.kt
//
// NOTE: This is intentionally a plain @Singleton, NOT a @HiltViewModel.
// @HiltViewModel instances are scoped to a ViewModelStoreOwner (an Activity or
// Fragment) — they cannot be shared across two separate Activities, and Hilt does
// not support field-injecting a ViewModel via @Inject the way this class is used
// in CompanionActivity below. A plain @Singleton, scoped to SingletonComponent,
// is what actually gives MainActivity and CompanionActivity the same instance.
// It still exposes StateFlow, so Compose call sites don't need to change.
@Singleton
class ScreenCoordinator @Inject constructor(
    private val playerRepository: PlayerRepository,
    private val navigator: Navigator
) {

    private val _state = MutableStateFlow<AppScreenState>(AppScreenState.Browsing)
    val state: StateFlow<AppScreenState> = _state.asStateFlow()

    fun onVideoOpened(videoId: String) {
        _state.value = AppScreenState.VideoOpen(videoId)
    }

    fun onVideoMinimized(videoId: String) {
        _state.value = AppScreenState.VideoMinimized(videoId)
    }

    fun onVideoClosed() {
        _state.value = AppScreenState.Browsing
    }

    fun goToVideo(videoId: String) {
        navigator.navigate(NavDestination.VideoDetail(videoId))
    }

    fun backToMain() {
        navigator.navigate(NavDestination.Home) {
            popUpTo(0) { inclusive = true }
        }
    }
}
```

### CompanionActivity

```kotlin
// activities/CompanionActivity.kt
//
// NOTE: PlayerViewModel has the same cross-Activity scoping problem ScreenCoordinator
// had (see above) — a @HiltViewModel obtained via `by viewModels()` in MainActivity
// is a *different instance* than one obtained the same way in CompanionActivity, since
// each Activity is its own ViewModelStoreOwner. Field-injecting it with @Inject
// (as in an earlier draft of this doc) also isn't a supported Hilt pattern for
// ViewModel classes. Since companion playback state genuinely needs to be identical
// across both windows, CompanionScreen reads player state via `playerRepository`
// directly (already a @Singleton, see §4) instead of through a second ViewModel.
@AndroidEntryPoint
class CompanionActivity : AppCompatActivity() {

    @Inject lateinit var screenCoordinator: ScreenCoordinator
    @Inject lateinit var playerRepository: PlayerRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GrayjayTheme {
                val playerState by playerRepository.observePlaybackState()
                    .collectAsStateWithLifecycle(initialValue = PlaybackState.Idle)

                CompanionScreen(
                    screenState = screenCoordinator.state,
                    playerState = playerState,
                    onBackToMain = { screenCoordinator.backToMain() },
                    onGoToVideo = { id -> screenCoordinator.goToVideo(id) }
                )
            }
        }
    }
}
```

### CompanionWindowManager

```kotlin
// feature/dualscreen/CompanionWindowManager.kt
class CompanionWindowManager @Inject constructor(
    private val context: Context
) {
    private val displayManager: DisplayManager
        get() = context.getSystemService(DISPLAY_SERVICE) as DisplayManager

    fun launchCompanionIfNeeded() {
        // Don't assume the secondary display is always at index 1 — filter out
        // DEFAULT_DISPLAY explicitly instead, since display ordering/index isn't
        // guaranteed by the platform.
        val secondary = displayManager.displays
            .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
            ?: return

        val options = ActivityOptions.makeBasic()
            .setLaunchDisplayId(secondary.displayId)
        context.startActivity(
            Intent(context, CompanionActivity::class.java),
            options.toBundle()
        )
    }

    fun registerDisplayListener(listener: DisplayManager.DisplayListener) {
        displayManager.registerDisplayListener(listener, null)
    }
}
```

### Companion Screen

```kotlin
// feature/companion/impl/CompanionScreen.kt
@Composable
fun CompanionScreen(
    screenState: StateFlow<AppScreenState>,
    playerState: PlaybackState,
    onBackToMain: () -> Unit,
    onGoToVideo: (String) -> Unit
) {
    val state by screenState.collectAsState()
    val player = playerState

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        TextButton(onClick = onBackToMain) {
            Text("← Back to main", style = MaterialTheme.typography.bodySmall)
        }

        if (state is AppScreenState.VideoOpen || state is AppScreenState.VideoMinimized) {
            CompanionPlayerControls(
                isPlaying = player.isPlaying,
                position = player.position,
                duration = player.duration,
                onPlayPause = { /* toggle */ },
                onSeek = { /* seek */ },
                onGoToVideo = { if (state is AppScreenState.VideoOpen) onGoToVideo(state.videoId) }
            )
        }

        var selectedTab by remember { mutableIntStateOf(0) }
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) { Text("Recs") }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) { Text("Comments") }
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) { Text("Polycentric") }
        }

        when (selectedTab) {
            0 -> CompanionRecsContent()
            1 -> CompanionCommentsContent()
            2 -> CompanionPolycentricContent()
        }
    }
}
```

**Reference**: [Now in Android AppScreenState](https://github.com/nowinandroid/nowinandroid/blob/main/app/src/main/kotlin/com/google/samples/apps/nowinandroid/ui/NiaAppState.kt) (app-wide state management)

---

## 9. Theming: Material You

### GrayjayTheme

```kotlin
// core/designsystem/theme/GrayjayTheme.kt
@Composable
fun GrayjayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            DynamicColorExtension().getSystemColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    val typography = GrayjayTypography  // Inter font

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}
```

### Appearance Preferences

```kotlin
// core/datastore/model/AppearancePreferences.kt
data class AppearancePreferences(
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val font: FontChoice = FontChoice.INTER,
    val iconStyle: IconStyle = IconStyle.ROUNDED,
    val contrast: Contrast = Contrast.STANDARD
)

enum class ThemeMode { AUTO, LIGHT, DARK }
enum class FontChoice { INTER, SYSTEM }
enum class IconStyle { ROUNDED, SHARP, OUTLINED }
enum class Contrast { STANDARD, MEDIUM, HIGH }
```

**Reference**: [Now in Android Preferences](https://github.com/nowinandroid/nowinandroid/blob/main/core/datastore/src/main/kotlin/com/google/samples/apps/nowinandroid/core/datastore/UserPreferencesDataStore.kt)

---

## 10. Dependency Injection: Hilt

### Application

```kotlin
// app/PlatformPlayerApp.kt
@HiltAndroidApp
class PlatformPlayerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
```

### Database Module

```kotlin
// app/di/DatabaseModule.kt
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "grayjay.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideQueueDao(db: AppDatabase): QueueDao = db.queueDao()
    @Provides fun provideHistoryDao(db: AppDatabase): HistoryDao = db.historyDao()
    @Provides fun providePlaylistDao(db: AppDatabase): PlaylistDao = db.playlistDao()
}
```

### Repository Module

```kotlin
// app/di/RepositoryModule.kt
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides @Singleton
    fun providePlayerRepository(
        statePlayer: StatePlayer,
        playerManager: PlayerManager,
        queueDao: QueueDao,
        historyDao: HistoryDao
    ): PlayerRepository = PlayerRepositoryImpl(statePlayer, playerManager, queueDao, historyDao)

    @Provides @Singleton
    fun provideHomeRepository(
        statePlatform: StatePlatform,
        homeFeedDao: HomeFeedCacheDao
    ): HomeRepository = HomeRepositoryImpl(statePlatform, homeFeedDao)
}
```

**Reference**: [Now in Android DataModule](https://github.com/nowinandroid/nowinandroid/blob/main/core/data/src/main/kotlin/com/google/samples/apps/nowinandroid/core/data/di/DataModule.kt)  
**Reference**: [Now in Android build-logic Graph.kt](https://github.com/nowinandroid/nowinandroid/blob/main/build-logic/convention/src/main/kotlin/com/google/samples/apps/nowinandroid/Graph.kt)

---

## 11. Responsive Video Cards

### VideoCard (orientation-aware)

```kotlin
// feature/home/impl/components/VideoCard.kt
@Composable
fun VideoCard(
    video: IPlatformVideo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        CompactVideoCard(video = video, onClick = onClick, modifier = modifier)
    } else {
        StandardVideoCard(video = video, onClick = onClick, modifier = modifier)
    }
}
```

### CompactVideoCard (landscape)

```kotlin
// feature/home/impl/components/CompactVideoCard.kt
@Composable
fun CompactVideoCard(
    video: IPlatformVideo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier.size(96.dp, 54.dp).clip(RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(video.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(
                "${video.channelName} • ${video.formattedDuration}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = { /* options */ }) {
            Icon(Icons.Default.MoreVert, contentDescription = "More")
        }
    }
}
```

**Reference**: [Flow VideoCard](https://github.com/aedev/flow/blob/main/app/src/main/java/io/github/aedev/flow/ui/components/VideoCard.kt)

---

## 12. Migration Phases

| Phase | Focus | Key Deliverable | Reference Project |
|-------|-------|-----------------|-------------------|
| **0** | Foundation | Hilt setup, `core:designsystem`, `core:navigation`, `core:data`, Room database, repositories | [Now in Android](https://github.com/nowinandroid/nowinandroid) |
| **0.5** | State machine | `AppScreenState`, `ScreenCoordinator`, `PlayerViewModel` (Application-scoped) | [Now in Android NiaAppState](https://github.com/nowinandroid/nowinandroid/blob/main/app/src/main/kotlin/com/google/samples/apps/nowinandroid/ui/NiaAppState.kt) |
| **1** | Chrome | `AppLayout` (rail/bottom bar), `MainActivity` (thin wrapper), `CompanionActivity`, DisplayManager detection | [Nav3 Recipes](https://github.com/nav3-recipes/nav3-recipes) |
| **2** | Settings | Compose Settings, Appearance preferences (Material You, font, icons), `SettingsRepository` | [Now in Android feature:settings](https://github.com/nowinandroid/nowinandroid/tree/main/feature/settings/impl) |
| **3** | Home feed | `HomeScreen`, `HomeViewModel`, responsive video cards (portrait/landscape) | [Flow HomeScreen](https://github.com/aedev/flow/blob/main/app/src/main/java/io/github/aedev/flow/ui/screens/home/HomeScreen.kt) |
| **4** | Player | `PlayerViewModel` (Application-scoped), `PlayerScreen`, companion player controls | [Flow MusicViewModel](https://github.com/aedev/flow/blob/main/app/src/main/java/io/github/aedev/flow/ui/screens/music/MusicViewModel.kt) |
| **5** | Library/Subscriptions/Search | Migrate remaining fragments → Compose screens | [Now in Android feature modules](https://github.com/nowinandroid/nowinandroid/tree/main/feature) |
| **6** | Detail screens | Video/Artist/Channel detail, deep links, State B/C wiring | [Nav3 Recipes multi-stack](https://github.com/nav3-recipes/nav3-recipes/tree/main/app/src/main/java/com/example/nav3recipes/multiplestacks) |
| **7** | Companion content | Recs/Comments/Polycentric tabs, gamepad key mapping, full state machine | [Handoff doc](/home/leon/Documents/grayjay-fork-handoff.md) |
| **8** | Cleanup | Verify no `core:*` or `feature:*` module imports anything under `states/`/`ui/interop`/`ui/scene` (see §3 exception note), then delete `fragment/`, `views/`, `dialogs/`, `states/`, old `activities/` | — |

---

## 13. Debuggability Improvements

| Problem | Solution |
|---------|----------|
| Two Activities, hard to trace state | `ScreenCoordinator` is the single source of truth — log all state transitions |
| Companion window state drift | Both Activities inject the same `ScreenCoordinator` + `PlayerViewModel` |
| Orientation changes break layout | `AppLayout` detects orientation via `LocalConfiguration` — test with Android Studio's orientation toggle |
| Player controls unresponsive on companion | `PlayerViewModel.player` is the same ExoPlayer instance — check `playerRepository.player` |
| State machine deadlocks | Sealed `AppScreenState` + `StateFlow` — use `state.value` in debug builds to dump current state |
| Secondary display not detected | Log `DisplayManager.displays.size` in `CompanionWindowManager.launchCompanionIfNeeded()` |
| Gamepad keys not working on companion | Stub `KeyEvent` handler in `CompanionActivity` — log all key events before mapping |
| `AppScreenState` lost on process death | Currently in-memory only (`MutableStateFlow` in a plain singleton). If the OS kills `MainActivity` while the companion window is open, `ScreenCoordinator.state` resets to `Browsing` on relaunch. Not fixed in this plan — needs a `SavedStateHandle` or DataStore-backed persistence pass before Phase 7 ships. |

---

## 14. Reference Projects

### Now in Android
- **URL**: https://github.com/nowinandroid/nowinandroid
- **Architecture docs**: `docs/ArchitectureLearningJourney.md`, `docs/ModularizationLearningJourney.md`
- **Key patterns**: MVI with UiState, repository pattern, Hilt DI, modular feature modules, design system
- **Most relevant files**:
  - `core/navigation/Navigator.kt` — Navigator pattern
  - `core/navigation/NavigationState.kt` — Navigation state management
  - `core/data/repository/OfflineFirstNewsRepository.kt` — Repository with offline-first
  - `core/data/di/DataModule.kt` — Hilt DI setup
  - `feature/search/impl/SearchViewModel.kt` — MVI ViewModel
  - `feature/search/impl/SearchScreen.kt` — MVI Screen
  - `app/src/main/kotlin/.../ui/NiaAppState.kt` — App-wide state management
  - `build-logic/convention/src/main/kotlin/.../Graph.kt` — Module dependency graph

### Flow
- **URL**: https://github.com/aedev/flow
- **Key patterns**: Single-activity Compose, ViewModel per screen group, Room database, recommendation engine
- **Most relevant files**:
  - `app/src/main/java/io/github/aedev/flow/ui/screens/home/HomeScreen.kt` — Home screen pattern
  - `app/src/main/java/io/github/aedev/flow/ui/screens/music/MusicViewModel.kt` — ViewModel pattern
  - `app/src/main/java/io/github/aedev/flow/data/local/AppDatabase.kt` — Room database setup
  - `app/src/main/java/io/github/aedev/flow/di/DatabaseModule.kt` — Hilt database module
  - `app/src/main/java/io/github/aedev/flow/ui/FlowNavigation.kt` — Navigation setup

### Nav3 Recipes
- **URL**: https://github.com/nav3-recipes/nav3-recipes
- **Key patterns**: Modular navigation, EntryProviderInstaller, multi-stack navigation
- **Most relevant files**:
  - `app/src/main/java/com/example/nav3recipes/modular/hilt/README.md` — Modular navigation architecture
  - `app/src/main/java/com/example/nav3recipes/multiplestacks/NavigationState.kt` — Multi-stack navigation
  - `app/src/main/java/com/example/nav3recipes/multiplestacks/MultipleStacksActivity.kt` — Activity with multiple stacks

### Architecture Samples (Google)
- **URL**: https://github.com/android/architecture-samples
- **Key patterns**: MVVM, LiveData/StateFlow, repository pattern, testing
- **Most relevant files**:
  - `app/src/main/java/com/example/android/architecture/blueprints/todoapp/di/DataModules.kt` — DI modules

---

## 15. Gotchas from Previous Migration (Icon Font)

From the handoff document, these lessons apply to the new architecture:

1. **Material Symbols need explicit Typeface** — `TextView` uses the theme's default typeface (Inter), not Material Symbols. Set `android:fontFamily` in XML or load from assets in Kotlin.

2. **Custom view attributes with `format="reference"` break with string icon names** — Change to `format="string"`.

3. **Same icon name in multiple places** — Always grep for all usages of an icon name across the codebase.

4. **Hardcoded colors break in dark theme** — Replace `@color/white` with `?attr/colorOnSurface`.

5. **Kotlin constructor overloading** — When migrating, replace ALL references to old drawable IDs with string icon names. Don't rely on backward-compat constructors.

6. **Font has multiple glyphs for the same name** — Use `fontTools` to inspect the actual `.ttf` file's `cmap` table and verify codepoints.

7. **ClassCastException when casting ImageView to TextView** — Always update BOTH XML and Kotlin variable declarations.

---

## 16. Build & Install

### Prerequisites

- Android Studio (with SDK 36, build tools 35)
- `git-lfs` installed
- AYN Thor connected via USB Wi-Fi debugging

### Build

```bash
# Clone and init submodules
git clone https://gitlab.futo.org/videostreaming/grayjay.git
cd grayjay
git lfs install
git submodule update --init --recursive

# Build unstable debug APK
./gradlew :app:assembleUnstableDebug
```

### Install

```bash
adb connect 192.168.1.214:5555
adb -s 192.168.1.214:5555 install -r app/build/outputs/apk/unstable/debug/app-unstable-arm64-v8a-debug.apk
```

---

## 17. Open Questions

| Question | Status |
|----------|--------|
| What version of `androidx.media3` is currently used? | Check `app/build.gradle` |
| Does `StatePlayer` expose `player.play()`, `player.pause()`, `player.seekTo()` publicly? | Audit needed |
| Is `MediaSession` accessible outside `StatePlayer`? | Audit needed |
| What are the exact navigation targets in `MenuBottomBarView`? | Audit needed (Home, Search, Subscriptions, Library, Settings, etc.) |
| Are colors hardcoded or managed by a theme system? | Audit needed |
| Does `res/layout-land/` exist? | Audit needed |
| Where is Polycentric currently queried and displayed? | Audit needed |
| What is the exact gesture detection for swipe-to-minimize? | Audit needed |
