# Compose Architecture Guide

This guide documents the Compose-based architecture for Grayjay, including how to use and extend it.

---

## Table of Contents
1. [Architecture Overview](#architecture-overview)
2. [Migration History](#migration-history)
3. [Project Structure](#project-structure)
4. [Creating a New Screen](#creating-a-new-screen)
5. [Creating a ViewModel](#creating-a-viewmodel)
6. [Navigation](#navigation)
7. [State Management](#state-management)
8. [Loading & Empty States](#loading--empty-states)
9. [Repository Pattern](#repository-pattern)
10. [Theming](#theming)
11. [Best Practices](#best-practices)
12. [Common Patterns](#common-patterns)
13. [Migration Checklist](#migration-checklist)

---

## Architecture Overview

Grayjay uses a **Compose-first architecture** with the following layers:

```
┌─────────────────────────────────────────────────────────────┐
│                    Compose UI Layer                         │
│  Screens, Components, Themes, Navigation                    │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                    ViewModel Layer                          │
│  StateFlow, Business Logic, Repository Coordination         │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                    Repository Layer                         │
│  Data Abstraction, Caching, Network/Local Sources           │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                    Data Layer                               │
│  Room DAOs, API Clients, Plugin Engine                      │
└─────────────────────────────────────────────────────────────┘
```

### Key Principles
- **Compose-first**: All new UI uses Compose, not XML
- **ViewModel + StateFlow**: Reactive state management
- **Repository pattern**: Clean data abstraction
- **navigation3**: Type-safe navigation with per-tab back stacks
- **Hilt DI**: Dependency injection throughout

---

## Migration History

Grayjay is migrating from XML Views to Jetpack Compose through three architectural layers:

### Layer 1: ORIGINAL (XML) — Legacy Reference Only
- **UI**: XML layouts + Fragment controllers
- **State**: Singleton `State*` objects (`StateLibrary`, `StatePlayer`, etc.)
- **Navigation**: FragmentManager-based via `NavDestination`
- **Status**: Read-only reference. Do not maintain or extend.

### Layer 2: HYBRID (Transitional) — Removed
- **UI**: Compose composables hosted inside XML Fragments
- **Navigation**: Compose NavHost with XML fallback
- **Status**: Deleted. No longer exists in the codebase.

### Layer 3: NEW (Full Compose) — Current Target
- **UI**: Pure Compose composables
- **Navigation**: Compose Navigation (`NavHost` + `NavKey` sealed class)
- **State**: `ViewModel` + `StateFlow`/`SharedFlow`
- **Status**: Actively developed. All new screens should use this pattern.

### Architecture Comparison

| Aspect | ORIGINAL (XML) | NEW (Compose) |
|--------|----------------|---------------|
| **UI Definition** | XML layouts | Pure Compose |
| **Screen Controller** | Fragment | Composable function |
| **Navigation** | FragmentManager | Compose NavHost |
| **State** | `State*` singletons | `ViewModel` + `StateFlow` |
| **Bottom Nav** | `MenuBottomBarFragment` (XML) | **Not yet migrated** |
| **Maintenance** | Read-only reference | **Develop here** |

### Shared Components (Unchanged)
These components are shared across all architectures and remain unchanged:
- `core/data/repository/` — Repository pattern
- `api/media/` — Plugin API interfaces
- `states/` — State singletons (being migrated to ViewModel)
- `downloads/` — Download management
- `encryption/` — Encryption utilities
- `subscription/` — Subscription algorithms

---

## Project Structure

```
app/src/main/java/com/futo/platformplayer/compose/
├── home/                          # Home/Feed screen
│   ├── HomeScreen.kt              # Compose UI
│   └── HomeViewModel.kt           # State management
├── navigation/                    # Navigation system
│   ├── PlatformPlayerActivity.kt  # Root activity
│   ├── GrayjayNavKey.kt           # Navigation routes (sealed class)
│   ├── GrayjayNavigationState.kt  # Per-tab back stacks
│   └── GrayjayNavigator.kt        # Navigation controller
├── player/                        # Video player
│   ├── VideoPlayerScene.kt        # Full-screen player
│   ├── MiniPlayerBar.kt           # Mini player
│   ├── VideoPlayerState.kt        # Player state
│   └── MiniPlayerState.kt         # Mini player state
├── feed/                          # Feed components
│   ├── FeedScreen.kt              # Feed UI (+ FeedItem model)
│   └── FeedItemCard.kt            # Feed item component
├── settings/                      # Settings screens
│   └── SettingsComponents.kt      # Settings UI components
├── plugins/                       # Plugin management
│   └── PluginBrowserScene.kt      # Plugin browser
├── theme/                         # Theming
│   └── ThemeStateHolder.kt        # Theme state
├── util/                          # Utilities
│   └── LoadingContent.kt          # Loading/empty states
├── view/                          # Reusable views
│   └── TagBadge.kt                # Tag badge component
├── widget/                        # Reusable widgets
│   ├── VideoList.kt               # Configurable video list/grid
│   ├── VideoCardItem.kt           # Video card variants
│   └── VideoListSource.kt         # Data source abstraction

```

---

## Creating a New Screen

### Step 1: Create ViewModel

```kotlin
// MyScreenViewModel.kt
package com.futo.platformplayer.compose.myscreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.futo.platformplayer.logging.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "MyScreenViewModel"

/**
 * UI state for MyScreen.
 * Use sealed interface for different loading states.
 */
sealed interface MyScreenUiState {
    object Loading : MyScreenUiState
    data class Success(
        val items: List<MyItem> = emptyList(),
        val error: String? = null
    ) : MyScreenUiState
    data class Error(val message: String) : MyScreenUiState
}

/**
 * ViewModel for MyScreen.
 * Use @HiltViewModel for DI.
 */
@HiltViewModel
class MyScreenViewModel @Inject constructor(
    private val myRepository: MyRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<MyScreenUiState>(MyScreenUiState.Loading)
    val uiState: StateFlow<MyScreenUiState> = _uiState.asStateFlow()
    
    init {
        loadData()
    }
    
    /**
     * Load initial data.
     */
    fun loadData() {
        viewModelScope.launch {
            Logger.i(TAG, "Loading data")
            _uiState.value = MyScreenUiState.Loading
            try {
                val items = myRepository.getItems()
                Logger.i(TAG, "Loaded ${items.size} items")
                _uiState.value = MyScreenUiState.Success(items = items)
            } catch (e: Exception) {
                Logger.e(TAG, e) { "Error loading data" }
                _uiState.value = MyScreenUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * Refresh data.
     */
    fun refresh() {
        viewModelScope.launch {
            Logger.i(TAG, "Refreshing data")
            try {
                val items = myRepository.getItems()
                _uiState.value = MyScreenUiState.Success(items = items)
            } catch (e: Exception) {
                Logger.e(TAG, e) { "Error refreshing data" }
            }
        }
    }
    
    /**
     * Load more data (pagination).
     */
    fun loadMore() {
        viewModelScope.launch {
            Logger.i(TAG, "Loading more data")
            try {
                val items = myRepository.getItems(page = currentPage + 1)
                // Append to existing items
                val currentItems = when (val state = _uiState.value) {
                    is MyScreenUiState.Success -> state.items
                    else -> emptyList()
                }
                _uiState.value = MyScreenUiState.Success(
                    items = currentItems + items,
                    error = null
                )
            } catch (e: Exception) {
                Logger.e(TAG, e) { "Error loading more data" }
            }
        }
    }
}
```

### Step 2: Create Screen Composable

```kotlin
// MyScreen.kt
package com.futo.platformplayer.compose.myscreen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.futo.platformplayer.compose.util.LoadingContent
import com.futo.platformplayer.compose.util.EmptyState

/**
 * MyScreen composable using ViewModel + StateFlow pattern.
 * Demonstrates the recommended architecture for Compose screens.
 */
@Composable
fun MyScreen(
    modifier: Modifier = Modifier,
    viewModel: MyScreenViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    Scaffold(
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        when (val state = uiState) {
            is MyScreenUiState.Loading -> {
                LoadingContent(
                    loading = true,
                    empty = false,
                    emptyContent = {},
                    modifier = Modifier.padding(paddingValues)
                ) {}
            }
            is MyScreenUiState.Success -> {
                if (state.items.isEmpty()) {
                    EmptyState(
                        message = "No items yet",
                        modifier = Modifier.padding(paddingValues)
                    )
                } else {
                    MyContent(
                        items = state.items,
                        onRefresh = viewModel::refresh,
                        onLoadMore = viewModel::loadMore,
                        modifier = Modifier.padding(paddingValues)
                    )
                }
            }
            is MyScreenUiState.Error -> {
                ErrorState(
                    message = state.message,
                    onRetry = viewModel::loadData,
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

/**
 * Error state composable with retry button.
 */
@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Error: $message",
                color = MaterialTheme.colorScheme.error
            )
            TextButton(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

/**
 * Main content with lazy list.
 */
@Composable
private fun MyContent(
    items: List<MyItem>,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    
    // Detect scroll to bottom for load-more
    LaunchedEffect(listState.isScrollInProgress, listState.layoutInfo) {
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()
        if (lastVisible?.index == listState.layoutInfo.totalItemsCount - 1 &&
            listState.layoutInfo.totalItemsCount > 0
        ) {
            onLoadMore()
        }
    }
    
    LazyColumn(
        state = listState,
        modifier = modifier
    ) {
        items(items) { item ->
            MyItemCard(item = item)
        }
    }
}
```

### Step 3: Register in Navigation

```kotlin
// GrayjayNavKey.kt - Add new route
@Serializable
data object MyScreen : NavKey

// GrayjayNavigationState.kt - Add to topLevelRoutes
val topLevelRoutes: Set<NavKey> = setOf(
    Home, Subscriptions, Playlists, Notifications, Search,
    Settings, Library, MyScreen  // <-- Add here
)
```

### Step 4: Add to NavEntry Creation

```kotlin
// PlatformPlayerActivity.kt - In createGrayjayNavEntry()
is MyScreen -> NavEntry(key) { MyScreen() }
```

---

## Navigation

Grayjay uses **navigation3** for type-safe navigation with per-tab back stacks.

### Navigation Keys

All routes are defined as `@Serializable` data classes in `GrayjayNavKey.kt`:

```kotlin
// Top-level routes (tabs)
@Serializable
data object Home : NavKey

@Serializable
data class VideoDetail(val url: String) : NavKey

// Sub-routes (pushed onto tab back stack)
@Serializable
data class SearchResults(val query: String) : NavKey
```

### Navigation State

Per-tab back stacks are managed by `GrayjayNavigationState`:

```kotlin
class GrayjayNavigationState {
    val topLevelRoute = MutableStateFlow<NavKey>(Home)
    val backStacks = mutableMapOf<NavKey, MutableList<NavKey>>()
    
    init {
        // Initialize back stacks for each tab
        topLevelRoutes.forEach { route ->
            backStacks[route] = mutableListOf(route)
        }
    }
}
```

### Navigating Between Screens

```kotlin
// In ViewModel or Navigator
fun navigateToVideoDetail(url: String) {
    val stack = navigationState.backStacks[navigationState.topLevelRoute.value]
    stack?.removeIf { it is VideoDetail }
    stack?.add(VideoDetail(url))
}

fun navigateUp() {
    val currentStack = navigationState.backStacks[navigationState.topLevelRoute.value]
    currentStack?.removeLastOrNull()
}
```

### Deep Linking

```kotlin
// Handle deep links in PlatformPlayerActivity
val uri = intent.data
if (uri != null) {
    when {
        uri.host == "video" -> {
            val videoUrl = uri.getQueryParameter("url")
            navigator.navigateToVideo(videoUrl ?: "")
        }
        uri.host == "channel" -> {
            val channelUrl = uri.getQueryParameter("url")
            navigator.navigateToChannel(channelUrl ?: "")
        }
    }
}
```

---

## State Management

### ViewModel + StateFlow Pattern

```kotlin
// 1. Define UI state
sealed interface MyScreenUiState {
    object Loading : MyScreenUiState
    data class Success(val items: List<MyItem>) : MyScreenUiState
    data class Error(val message: String) : MyScreenUiState
}

// 2. Create ViewModel
@HiltViewModel
class MyScreenViewModel @Inject constructor(
    private val repository: MyRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<MyScreenUiState>(MyScreenUiState.Loading)
    val uiState: StateFlow<MyScreenUiState> = _uiState.asStateFlow()
    
    // 3. Expose methods for actions
    fun loadData() {
        viewModelScope.launch {
            _uiState.value = MyScreenUiState.Loading
            try {
                val items = repository.getItems()
                _uiState.value = MyScreenUiState.Success(items)
            } catch (e: Exception) {
                _uiState.value = MyScreenUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}

// 4. Collect in Composable
@Composable
fun MyScreen(viewModel: MyScreenViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    when (uiState) {
        is MyScreenUiState.Loading -> LoadingContent(...)
        is MyScreenUiState.Success -> Content(uiState.items)
        is MyScreenUiState.Error -> ErrorState(uiState.message)
    }
}
```

### State Restoration

Use `SavedStateHandle` for state that should survive configuration changes:

```kotlin
@HiltViewModel
class MyScreenViewModel @Inject constructor(
    private val repository: MyRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    // SavedStateHandle survives configuration changes
    private val _query = savedStateHandle.getStateFlow("query", "")
    
    fun setQuery(query: String) {
        savedStateHandle["query"] = query
    }
}
```

---

## Loading & Empty States

### LoadingContent Utility

```kotlin
// Use LoadingContent for consistent loading/empty states
LoadingContent(
    loading = uiState.isLoading,
    empty = uiState.items.isEmpty() && !uiState.isLoading,
    emptyContent = { EmptyState(message = "No items yet") },
    modifier = Modifier.padding(paddingValues)
) {
    // Main content
    LazyColumn {
        items(uiState.items) { item ->
            ItemCard(item)
        }
    }
}
```

### EmptyState Utility

```kotlin
// Use EmptyState for consistent empty states
EmptyState(
    message = "No items yet",
    modifier = Modifier.fillMaxSize()
)
```

### ErrorState Pattern

```kotlin
// Use ErrorState for error states
ErrorState(
    message = uiState.error,
    onRetry = viewModel::loadData,
    modifier = Modifier.fillMaxSize()
)
```

---

## Repository Pattern

### Repository Interface

```kotlin
// MyRepository.kt
interface MyRepository {
    suspend fun getItems(): List<MyItem>
    suspend fun getItemById(id: String): MyItem?
    suspend fun addItem(item: MyItem): Long
    suspend fun updateItem(item: MyItem): Boolean
    suspend fun deleteItem(id: String): Boolean
}
```

### Repository Implementation

```kotlin
// MyRepositoryImpl.kt
@Singleton
class MyRepositoryImpl @Inject constructor(
    private val api: MyApi,
    private val dao: MyDao
) : MyRepository {
    
    override suspend fun getItems(): List<MyItem> {
        return try {
            // Network first
            val items = api.getItems()
            dao.insertAll(items)
            items
        } catch (e: Exception) {
            // Fallback to local
            dao.getItems()
        }
    }
    
    override suspend fun getItemById(id: String): MyItem? {
        return dao.getItemById(id)
    }
    
    // ... other methods
}
```

### Hilt Binding

```kotlin
// MyRepositoryModule.kt
@Module
@InstallIn(SingletonComponent::class)
abstract class MyRepositoryModule {
    
    @Binds
    abstract fun bindMyRepository(
        impl: MyRepositoryImpl
    ): MyRepository
}
```

---

## Theming

### Theme State

```kotlin
// ThemeStateHolder.kt
@HiltViewModel
class ThemeStateHolder @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    
    val themeMode = settingsRepository.themeMode
    
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }
}
```

### Using Theme

```kotlin
@Composable
fun MyApp(themeStateHolder: ThemeStateHolder = hiltViewModel()) {
    val themeMode by themeStateHolder.themeMode.collectAsState()
    
    GrayjayTheme(themeMode = themeMode) {
        // App content
    }
}
```

---

## Best Practices

### 1. Use ViewModel for All Screens

```kotlin
// ✅ Good
@Composable
fun MyScreen(viewModel: MyScreenViewModel = hiltViewModel()) { ... }

// ❌ Bad
@Composable
fun MyScreen() {
    val state = remember { mutableStateOf(...) }
    // ...
}
```

### 2. Use StateFlow for Reactive State

```kotlin
// ✅ Good
val uiState: StateFlow<MyScreenUiState> = _uiState.asStateFlow()

// ❌ Bad
var state by mutableStateOf<MyScreenUiState>(...)
```

### 3. Use collectAsStateWithLifecycle

```kotlin
// ✅ Good
val uiState by viewModel.uiState.collectAsStateWithLifecycle()

// ❌ Bad
val uiState by viewModel.uiState.collectAsState()
```

### 4. Use LoadingContent for Consistent States

```kotlin
// ✅ Good
LoadingContent(
    loading = uiState.isLoading,
    empty = uiState.items.isEmpty(),
    onRefresh = viewModel::refresh
) { ... }

// ❌ Bad
if (isLoading) { CircularProgressIndicator() } 
else if (items.isEmpty()) { Text("Empty") }
else { ... }
```

### 5. Use Logger for Debugging

```kotlin
// ✅ Good
Logger.i(TAG, "Loading data")
Logger.e(TAG, e) { "Error loading data" }

// ❌ Bad
Log.d("MyScreen", "Loading data")
```

### 6. Use Hilt for DI

```kotlin
// ✅ Good
@HiltViewModel
class MyScreenViewModel @Inject constructor(
    private val repository: MyRepository
) : ViewModel() { ... }

// ❌ Bad
class MyScreenViewModel(
    private val repository: MyRepository = MyRepositoryImpl()
) : ViewModel() { ... }
```

### 7. Keep Composables Pure

```kotlin
// ✅ Good
@Composable
fun ItemCard(item: MyItem, onClick: () -> Unit) { ... }

// ❌ Bad
@Composable
fun ItemCard() {
    // Directly access ViewModel or repository
}
```

### 8. Use Modifier Parameters

```kotlin
// ✅ Good
@Composable
fun MyComponent(modifier: Modifier = Modifier) { ... }

// ❌ Bad
@Composable
fun MyComponent() { ... }
```

---

## Common Patterns

### Pull-to-Refresh

```kotlin
@Composable
fun MyScreen(viewModel: MyScreenViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    SwipeRefresh(
        state = rememberSwipeRefreshState(uiState.isLoading),
        onRefresh = viewModel::refresh
    ) {
        LazyColumn {
            items(uiState.items) { item ->
                ItemCard(item)
            }
        }
    }
}
```

### Infinite Scroll

```kotlin
@Composable
fun MyScreen(viewModel: MyScreenViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    
    LaunchedEffect(listState.isScrollInProgress, listState.layoutInfo) {
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()
        if (lastVisible?.index == listState.layoutInfo.totalItemsCount - 1) {
            viewModel.loadMore()
        }
    }
    
    LazyColumn(state = listState) {
        items(uiState.items) { item ->
            ItemCard(item)
        }
    }
}
```

### Dialog/Modal

```kotlin
@Composable
fun MyScreen(viewModel: MyScreenViewModel = hiltViewModel()) {
    var showDialog by remember { mutableStateOf(false) }
    
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Title") },
            text = { Text("Content") },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
    
    Button(onClick = { showDialog = true }) {
        Text("Show Dialog")
    }
}
```

### Navigation with Parameters

```kotlin
// Navigate with parameters
navigator.navigateToVideoDetail(videoUrl = "https://...")

// Receive parameters in destination
@Composable
fun VideoDetailScreen(videoUrl: String, onBack: () -> Unit) { ... }
```

---

## Migration Guide

### From XML to Compose

1. **Identify XML screen** (e.g., `MyFragment.kt`)
2. **Create ViewModel** (`MyViewModel.kt`)
3. **Create Compose screen** (`MyScreen.kt`)
4. **Add NavKey** (`GrayjayNavKey.kt`)
5. **Register in navigation** (`PlatformPlayerActivity.kt`)
6. **Test and remove XML**

### From State* Singleton to ViewModel

1. **Identify State* singleton** (e.g., `StateLibrary`)
2. **Create Repository** (`MyRepository.kt`)
3. **Create ViewModel** (`MyViewModel.kt`)
4. **Migrate screen to ViewModel**
5. **Remove singleton access**

---

## Troubleshooting

### Issue: Screen not showing

**Solution:** Check NavKey is registered in `topLevelRoutes` and `createGrayjayNavEntry()`.

### Issue: State not updating

**Solution:** Use `collectAsStateWithLifecycle()` and ensure ViewModel uses `StateFlow`.

### Issue: Navigation not working

**Solution:** Check NavKey is `@Serializable` and route is registered in navigation.

### Issue: Memory leaks

**Solution:** Use `viewModelScope` for coroutines and `DisposableEffect` for cleanup.

---

## Resources

- [Android Compose Documentation](https://developer.android.com/jetpack/compose)
- [ViewModel Documentation](https://developer.android.com/topic/libraries/architecture/viewmodel)
- [StateFlow Documentation](https://developer.android.com/kotlin/flow/stateflow-and-sharedflow)
- [navigation3 Documentation](https://developer.android.com/jetpack/androidx/releases/navigation)
- [Hilt Documentation](https://developer.android.com/training/dependency-injection/hilt-android)

---

## Guidelines

These are the **hard rules** for the Grayjay architecture:

1. **Never add new XML UI** — XML is a reference/specification only
2. **Never create new Fragment-based screens** — Use Compose composables
3. **Never add fallback logic** — All navigation should use Compose scenes directly
4. **Always use ViewModel + StateFlow** — Replace `State*` singletons
5. **Always use Compose Navigation** — No FragmentManager-based navigation
6. **Share data layer** — Repository pattern, Room DAOs, and Plugin API remain unchanged

---

## Migration Checklist

Use this checklist to track migration progress for each screen:

### Screen Migration Checklist

- [ ] Identify XML screen (e.g., `MyFragment.kt`)
- [ ] Create ViewModel (`MyViewModel.kt`)
- [ ] Create Compose screen (`MyScreen.kt`)
- [ ] Add NavKey (`GrayjayNavKey.kt`)
- [ ] Register in navigation (`PlatformPlayerActivity.kt`)
- [ ] Test screen functionality
- [ ] Remove XML fragment (if no longer needed)
- [ ] Update tests

### State* Singleton Migration Checklist

- [ ] Identify State* singleton (e.g., `StateLibrary`)
- [ ] Create Repository (`MyRepository.kt`)
- [ ] Create ViewModel (`MyViewModel.kt`)
- [ ] Migrate screen to ViewModel
- [ ] Remove singleton access
- [ ] Test data flow
- [ ] Remove State* singleton (if no longer needed)

---

## Version History

| Version | Date | Changes |
|---------|------|---------|  
| 1.0 | 2024-07-25 | Initial architecture documentation |
| 1.1 | 2024-07-25 | Added migration guide and troubleshooting |
| 1.2 | 2024-07-25 | Added migration history, guidelines, and checklist |
