# Refactoring Opportunities

This document identifies concrete refactoring opportunities for the Bluejay codebase, comparing the current implementation with best practices from Android's architecture-samples (Google's official Compose + ViewModel + Repository pattern reference app).

---

## 1. State Management: Singleton → ViewModel

### Current (Problematic)
```kotlin
// StateLibrary.kt - Large singleton with many responsibilities
class StateLibrary {
    private val _files = FragmentedStorage.get<StringArrayStorage>("libraryFiles")
    
    fun getFileDirectories(): List<FileEntry> { ... }
    fun deleteFileDirectory(path: String) { ... }
    fun addFileDirectory(onAdded: ((entry: FileEntry) -> Unit)? = null, skipDialog: Boolean = false): Boolean { ... }
    fun searchTracks(str: String): List<IPlatformVideo> { ... }
    // ... 100+ more methods
}

// HomeScene.kt - Direct access to singleton
@Composable
private fun HomeScene(navigator: BluejayNavigator) {
    val p = com.tsutsen.platformplayer.states.StatePlatform.instance.getHomeRefresh(this)
    // ...
}
```

### Target (Best Practice)
```kotlin
// HomeViewModel.kt - ViewModel with StateFlow
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val platformRepository: PlatformRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState
    
    fun loadFeed() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                val content = platformRepository.getHomeFeed()
                _uiState.value = HomeUiState.Success(content)
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.message)
            }
        }
    }
}

// Sealed class for async states
sealed interface HomeUiState {
    object Loading : HomeUiState
    data class Success(val items: List<FeedItem>) : HomeUiState
    data class Error(val message: String?) : HomeUiState
}
```

### Benefits
- **Testability**: ViewModels can be tested independently of Android framework
- **Lifecycle-aware**: Automatic cleanup when screen is destroyed
- **State restoration**: `SavedStateHandle` survives configuration changes
- **Single responsibility**: Each ViewModel manages one screen's state

### Priority: **HIGH**

---

## 2. Navigation: Keep navigation3, Enhance NavigationActions

**IMPORTANT: Do NOT migrate to standard Compose Navigation.**

navigation3 is the RIGHT choice for Bluejay because:
- **Per-tab back stacks** - natively supported via `NavigationState` with per-tab `backStacks`
- **Adaptive layouts** - built-in support for portrait/landscape with `currentWindowAdaptiveInfo`
- **Type-safe navigation** - `NavKey` sealed class with `@Serializable` annotations
- **Complex navigation scenarios** - 40+ routes with parameters, deep linking, back stack management

Standard Compose Navigation would be WORSE for this use case because:
- No built-in per-tab back stack support
- Would need custom workarounds for adaptive layouts
- Less type-safe (route strings vs sealed classes)

### Current (Good)
```kotlin
// NavKey sealed class - type-safe, serializable
sealed class NavKey {
    @Serializable data object Home : NavKey()
    @Serializable data class VideoDetail(val url: String) : NavKey()
    // ... 40+ types
}

// NavigationState - per-tab back stacks
class BluejayNavigationState {
    val topLevelRoute = MutableStateFlow<NavKey>(Home)
    val backStacks = mutableMapOf<NavKey, MutableList<NavKey>>()
}

// Navigation actions for specific routes
class BluejayNavigator(val state: BluejayNavigationState) {
    fun navigateToVideo(url: String) { ... }
    fun navigateToPlaylist(url: String) { ... }
    // ... 40+ convenience methods
}
```

### Enhancement (Better Structure)
```kotlin
// Extract navigation actions into separate class (like architecture-samples)
class BluejayNavigationActions(
    private val navController: NavHostController,
    private val navigationState: BluejayNavigationState
) {
    fun navigateToHome() {
        navigationState.topLevelRoute.value = Home
    }
    
    fun navigateToVideoDetail(videoUrl: String) {
        val stack = navigationState.backStacks[navigationState.topLevelRoute.value]
        stack?.removeIf { it is VideoDetail }
        stack?.add(VideoDetail(videoUrl))
    }
    
    fun navigateUp() {
        // Handle back navigation
    }
}

// Use in composables
@Composable
fun HomeScreen(
    navigationActions: BluejayNavigationActions = rememberBluejayNavigationActions()
) {
    // Use navigationActions instead of BluejayNavigator
}
```

### Benefits
- **Keep navigation3 benefits** - per-tab back stacks, adaptive layouts, type-safety
- **Better testability** - NavigationActions class is easier to mock
- **Cleaner separation** - Navigation logic separated from state management
- **Future-proof** - Can migrate to standard NavHost later if needed

### Priority: **LOW** (Already well-structured, minor refactoring)

---

## 3. UI State: Multiple remember variables → Single UiState

### Current (Problematic)
```kotlin
// HomeScene.kt - Multiple independent state variables
@Composable
private fun HomeScene(navigator: BluejayNavigator) {
    var uiState by remember { mutableStateOf(FeedUiState(isLoading = true)) }
    var pager by remember { mutableStateOf<ReusableRefreshPager<IPlatformContent>?>(null) }
    var items by remember { mutableStateOf<List<FeedItem>>(emptyList()) }
    var contentList by remember { mutableStateOf<List<IPlatformContent>>(emptyList()) }
    
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        val job = scope.launch { ... }
        onDispose { job.cancel() }
    }
}
```

### Target (Best Practice)
```kotlin
// Single UiState data class
data class HomeUiState(
    val isLoading: Boolean = false,
    val items: List<FeedItem> = emptyList(),
    val error: String? = null,
    val isEmpty: Boolean = false
)

// TasksScreen.kt pattern - collectAsStateWithLifecycle
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    navigator: BluejayNavigator
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { HomeTopAppBar() },
        floatingActionButton = { RefreshButton(onClick = viewModel::refresh) }
    ) { padding ->
        LoadingContent(
            loading = uiState.isLoading,
            empty = uiState.isEmpty && !uiState.isLoading,
            emptyContent = { EmptyState() },
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(padding)
        ) {
            FeedContent(
                items = uiState.items,
                onItemClicked = { content -> navigator.navigateToContent(content) }
            )
        }
    }
    
    // Handle snackbar messages
    uiState.error?.let { error ->
        LaunchedEffect(snackbarHostState, error) {
            snackbarHostState.showSnackbar(error)
            viewModel.onSnackbarShown()
        }
    }
}
```

### Benefits
- **Single source of truth**: One state object, no inconsistency
- **Predictable updates**: State changes are atomic
- **Easier debugging**: Can dump entire UI state
- **Better performance**: `collectAsStateWithLifecycle` only updates when visible

### Priority: **HIGH**

---

## 4. Loading/Empty States: Manual → Reusable LoadingContent

### Current (Problematic)
```kotlin
// Scattered loading logic in each scene
@Composable
private fun HomeScene(navigator: BluejayNavigator) {
    // ... loading logic
    FeedScreen(
        state = uiState,
        // No consistent loading/empty handling
    )
}

@Composable
private fun placeholder(n: BluejayNavigator, title: String, ...) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = title, style = MaterialTheme.typography.headlineMedium)
            Text(text = "(Migrate XML fragment to Compose)", ...)
        }
    }
}
```

### Target (Best Practice)
```kotlin
// LoadingContent.kt - Reusable loading/empty state composable
@Composable
fun LoadingContent(
    loading: Boolean,
    empty: Boolean,
    emptyContent: @Composable () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (empty) {
        emptyContent()
    } else {
        SwipeRefresh(
            state = rememberSwipeRefreshState(loading),
            onRefresh = onRefresh,
            modifier = modifier,
            content = content
        )
    }
}

// Usage in every screen
@Composable
fun TasksScreen(viewModel: TasksViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    LoadingContent(
        loading = uiState.isLoading,
        empty = uiState.items.isEmpty() && !uiState.isLoading,
        emptyContent = { TasksEmptyContent() },
        onRefresh = viewModel::refresh
    ) {
        LazyColumn {
            items(uiState.items) { task -> TaskItem(task) }
        }
    }
}
```

### Benefits
- **Consistency**: Same loading/empty behavior across all screens
- **Reusability**: Write once, use everywhere
- **Maintainability**: Change loading behavior in one place
- **User experience**: Consistent pull-to-refresh, empty states

### Priority: **HIGH**

---

## 5. Activity: FragmentActivity → ComponentActivity

### Current (Problematic)
```kotlin
// PlatformPlayerActivity.kt
class PlatformPlayerActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ... Fragment-related code
    }
}
```

### Target (Best Practice)
```kotlin
// TodoActivity.kt pattern
@AndroidEntryPoint
class PlatformPlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BluejayTheme {
                BluejayNavGraph()
            }
        }
    }
}
```

### Benefits
- **Simplicity**: No Fragment dependency in activity
- **Compose-first**: Pure Compose navigation
- **Hilt integration**: `@AndroidEntryPoint` for DI
- **Edge-to-edge**: Modern Android UI

### Priority: **MEDIUM**

---

## 6. Repository Pattern: Already Clean → Leverage More

### Current (Good)
```kotlin
// core/data/src/main/java/com/tsutsen/platformplayer/core/data/repository/SearchRepository.kt
interface SearchRepository {
    suspend fun search(query: String): List<SearchResult>
}

class DefaultSearchRepository @Inject constructor(
    private val api: SearchApi,
    private val dao: SearchDao
) : SearchRepository {
    override suspend fun search(query: String): List<SearchResult> {
        // Network first, then cache
        return try {
            val results = api.search(query)
            dao.insertAll(results)
            results
        } catch (e: Exception) {
            dao.getRecentSearches()
        }
    }
}
```

### Target (Enhanced)
```kotlin
// Add repository to ViewModel via constructor injection
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val _query = savedStateHandle.getStateFlow("query", "")
    val uiState: StateFlow<SearchUiState> = combine(_query) { query ->
        if (query.isBlank()) {
            SearchUiState.Empty
        } else {
            SearchUiState.Loading
        }
    }.stateIn(viewModelScope, WhileUiSubscribed, SearchUiState.Empty)
    
    fun search(query: String) {
        viewModelScope.launch {
            _uiState.value = SearchUiState.Loading
            try {
                val results = searchRepository.search(query)
                _uiState.value = SearchUiState.Success(results)
            } catch (e: Exception) {
                _uiState.value = SearchUiState.Error(e.message)
            }
        }
    }
}
```

### Benefits
- **Testability**: Mock repository in ViewModel tests
- **Single source of truth**: Repository handles data fetching logic
- **Caching strategy**: Consistent cache/network strategy across app
- **Error handling**: Centralized error handling

### Priority: **LOW** (Already well-structured)

---

## 7. Logging: Log.d → Logger/Slf4j

### Current (Problematic)
```kotlin
// Scattered Log.d/w/e calls
Log.d("HomeScene", "Loading feed...")
Log.w("HomeScene", "No refreshable pager: ${p?.javaClass}")
Log.e("HomeScene", "Error loading feed", e)
```

### Target (Best Practice)
```kotlin
// Use Logger (already available in codebase)
Logger.d("HomeScene", "Loading feed...")
Logger.w("HomeScene", "No refreshable pager: ${p?.javaClass}")
Logger.e("HomeScene", "Error loading feed", e)

// Or better: structured logging with context
Logger.d(
    tag = "HomeScene",
    message = "Feed loaded",
    data = mapOf("count" to items.size, "duration" to duration)
)
```

### Benefits
- **Consistency**: Same logging format everywhere
- **Filtering**: Easy to filter by tag/level
- **Performance**: Can disable logging in release builds
- **Debugging**: Structured data for better debugging

### Priority: **LOW**

---

## Refactoring Priority Matrix

| Refactor | Effort | Impact | Priority | Status |
|----------|--------|--------|----------|--------|  
| **State Management** (Singleton → ViewModel) | High | High | **HIGH** | 🟡 Partial (Home done) |
| **UI State** (Multiple variables → Single UiState) | Medium | High | **HIGH** | 🟡 Partial (Home done) |
| **Loading/Empty States** (Reusable LoadingContent) | Low | High | **HIGH** | ✅ Done |
| **Navigation** (Keep navigation3, enhance structure) | Low | Low | **LOW** | ✅ Done |
| **Activity** (FragmentActivity → ComponentActivity) | Low | Medium | **MEDIUM** | ✅ Done |
| **Repository Pattern** (Leverage more) | Low | Low | **LOW** | ✅ Done |
| **Logging** (Log.d → Logger) | Low | Low | **LOW** | ✅ Done |

---

## Recommended Refactoring Order

### ✅ Completed (Phase 1-2)
1. **Create LoadingContent utility** ✅
2. **Refactor HomeScene** to use ViewModel + StateFlow + LoadingContent ✅
3. **Create HomeViewModel** with proper state management ✅
4. **Migrate PlatformPlayerActivity** to ComponentActivity + @AndroidEntryPoint ✅
5. **Replace Log.d/w/e with Logger** in compose modules ✅
6. **Keep navigation3** (confirmed as optimal choice) ✅

### 🟡 In Progress (Phase 3)
1. **Apply HomeViewModel pattern** to remaining screens:
   - SettingsScreen
   - SearchScreen
   - LibraryScreen
   - SubscriptionsScreen

### 📋 Pending (Phase 4)
1. **Migrate remaining State* singletons** to ViewModels:
   - StatePlayer → PlayerViewModel
   - StateSubscriptions → SubscriptionsViewModel
   - StateDownloads → DownloadsViewModel
   - StatePlaylists → PlaylistsViewModel
2. **Create UI state data classes** for all screens
3. **Add unit tests** for ViewModels
4. **Add navigation tests** using NavTestRule

---

## Key Patterns to Adopt from architecture-samples

### 1. Sealed Class for Async States
```kotlin
sealed class Async<out T> {
    object Loading : Async<Nothing>()
    data class Error(val errorMessage: Int) : Async<Nothing>()
    data class Success<out T>(val data: T) : Async<T>()
}
```

### 2. ViewModel with StateFlow
```kotlin
@HiltViewModel
class ScreenViewModel @Inject constructor(
    private val repository: Repository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    val uiState: StateFlow<ScreenUiState> = combine(...)
        .stateIn(viewModelScope, WhileUiSubscribed, initial)
}
```

### 3. Screen Composable Pattern
```kotlin
@Composable
fun ScreenScreen(
    viewModel: ScreenViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    Scaffold(...) { padding ->
        LoadingContent(
            loading = uiState.isLoading,
            empty = uiState.isEmpty,
            onRefresh = viewModel::refresh
        ) {
            ScreenContent(uiState, modifier = Modifier.padding(padding))
        }
    }
}
```

### 4. Navigation Actions Class
```kotlin
class ScreenNavigationActions(private val navController: NavHostController) {
    fun navigateToDetail(id: String) {
        navController.navigate(ScreenDetailRoute(id))
    }
}
```

---

## Files to Create/Modify

### New Files
```
app/src/main/java/com/tsutsen/platformplayer/compose/
├── home/
│   ├── HomeViewModel.kt              ← NEW
│   ├── HomeScreen.kt                 ← REFACTOR from PlatformPlayerActivity.kt
│   └── HomeNavigation.kt             ← NEW
├── search/
│   ├── SearchViewModel.kt            ← NEW
│   └── SearchScreen.kt               ← REFACTOR from PlatformPlayerActivity.kt
├── library/
│   ├── LibraryViewModel.kt           ← NEW
│   └── LibraryScreen.kt              ← REFACTOR from PlatformPlayerActivity.kt
├── settings/
│   ├── SettingsViewModel.kt          ← NEW
│   └── SettingsScreen.kt             ← REFACTOR from PlatformPlayerActivity.kt
└── util/
    ├── LoadingContent.kt             ← NEW (from architecture-samples)
    └── Async.kt                      ← NEW (from architecture-samples)
```

### Modified Files
```
app/src/main/java/com/tsutsen/platformplayer/compose/navigation/
├── PlatformPlayerActivity.kt         ← Simplify, remove scenes
├── BluejayNavigator.kt               ← Update for standard navigation
└── NavKey.kt                         ← Keep for now, migrate later

app/src/main/java/com/tsutsen/platformplayer/states/
├── StateLibrary.kt                   ← Reduce responsibility
├── StatePlayer.kt                    ← Reduce responsibility
├── StateSubscriptions.kt             ← Reduce responsibility
└── StateDownloads.kt                 ← Reduce responsibility
```

---

## Testing Strategy

### Unit Tests (ViewModels)
```kotlin
@HiltTest
class HomeViewModelTest {
    @Inject lateinit var fixture: ViewModelFactory
    
    @Test
    fun `when loadFeed succeeds, uiState is Success`() = runTest {
        val viewModel = fixture.create<HomeViewModel>()
        viewModel.loadFeed()
        
        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf<HomeUiState.Success>::class
    }
    
    @Test
    fun `when loadFeed fails, uiState is Error`() = runTest {
        // Mock repository to throw exception
        val viewModel = fixture.create<HomeViewModel>()
        viewModel.loadFeed()
        
        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf<HomeUiState.Error>::class
    }
}
```

### Navigation Tests
```kotlin
@AndroidTest
class NavigationTest {
    @Rule @JvmField
    val rule = NavTestRule(activityTestRule)
    
    @Test
    fun `navigate to video detail`() {
        rule.onAction(
            NavTestRule.actionOnDestination("home") {
                // Click video item
            }
        )
        rule.assertCurrentDestination("video_detail")
    }
}
```

### UI Tests
```kotlin
@AndroidTest
class HomeScreenTest {
    @Rule @JvmField
    val rule = ActivityTestRule(PlatformPlayerActivity::class.java)
    
    @Test
    fun `shows loading state initially`() {
        onView(withText("Loading...")).check(matches(isDisplayed()))
    }
    
    @Test
    fun `shows feed after loading`() {
        // Wait for loading to complete
        IdlingRegistry.getInstance().register(idlingResource)
        onView(withText("Feed")).check(matches(isDisplayed()))
    }
}
```

---

## Migration Checklist

- [x] Create `LoadingContent.kt` utility
- [x] Create `HomeViewModel` with StateFlow
- [x] Refactor `HomeScreen` to use ViewModel
- [x] Migrate `PlatformPlayerActivity` to ComponentActivity + @AndroidEntryPoint
- [x] Replace Log.d/w/e with Logger in compose modules
- [x] Keep navigation3 (confirmed as optimal choice)
- [ ] Apply pattern to `SearchScreen`
- [ ] Apply pattern to `LibraryScreen`
- [ ] Apply pattern to `SettingsScreen`
- [ ] Apply pattern to `SubscriptionsScreen`
- [ ] Migrate StatePlayer → PlayerViewModel
- [ ] Migrate StateSubscriptions → SubscriptionsViewModel
- [ ] Migrate StateDownloads → DownloadsViewModel
- [ ] Migrate StatePlaylists → PlaylistsViewModel
- [ ] Add unit tests for ViewModels
- [ ] Add navigation tests
