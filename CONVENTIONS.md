# Bluejay Conventions

> **Purpose:** This document defines the structural, naming, and architectural conventions for the Bluejay codebase. All new code must follow these conventions. Existing code should be migrated gradually.

---

## Table of Contents

1. [Module Structure](#1-module-structure)
2. [Package Naming](#2-package-naming)
3. [File & Class Naming](#3-file--class-naming)
4. [Architecture Layers](#4-architecture-layers)
5. [Kotlin Language Conventions](#5-kotlin-language-conventions)
6. [Compose UI Conventions](#6-compose-ui-conventions)
7. [Dependency Injection](#7-dependency-injection)
8. [Data Layer Conventions](#8-data-layer-conventions)
9. [Feature Module Conventions](#9-feature-module-conventions)
10. [Testing Conventions](#10-testing-conventions)
11. [Git & Code Review](#11-git--code-review)

---

## 1. Module Structure

### Directory Layout

```
bluejay/
├── app/                          # Application module (DI, activities, application class)
│   └── src/main/java/.../app/
├── core/                         # Shared core modules
│   ├── data/                     # Repository interfaces & implementations
│   ├── database/                 # Room database
│   ├── datastore/                # DataStore preferences
│   ├── designsystem/             # Shared Compose components
│   ├── model/                    # Domain models
│   ├── navigation/               # Navigation graph & destinations
│   ├── notifications/            # Notification handling
│   ├── sync/                     # Sync service
│   ├── testing/                  # Test utilities
│   └── ui/                       # UI utilities
└── feature/                      # Feature modules
    └── <name>/                   # Each feature has its own module
        └── impl/                 # Implementation (always /impl/)
            └── src/main/java/...
```

### Rules

- **All feature modules MUST use `/impl/` subdirectory:**
  ```
  feature/home/impl/src/          ✓
  feature/dualscreen/src/         ✗ (must be feature/dualscreen/impl/src/)
  ```

- **Core modules do NOT use `/impl/`:**
  ```
  core/data/src/                  ✓
  core/data/impl/src/             ✗
  ```

- **App module is the only module that depends on all others.** No other module should depend on `app/`.

### Module Dependencies

```
app/ → feature/*, core/*
feature/* → core/*, other features (only if necessary)
core/* → each other (only if necessary)
```

**Rule:** Circular dependencies are forbidden.

---

## 2. Package Naming

### Base Package

All packages use: `com.tsutsen.platformplayer`

### Feature Modules

```
com.tsutsen.platformplayer.feature.<module>
```

Examples:
```kotlin
// feature/home/impl/
package com.tsutsen.platformplayer.feature.home

// feature/settings/impl/
package com.tsutsen.platformplayer.feature.settings
```

**Note:** Do NOT include `.impl` in the package name. The `/impl/` directory is for build structure only.

### Core Modules

```
com.tsutsen.platformplayer.core.<module>
```

Examples:
```kotlin
// core/data/
package com.tsutsen.platformplayer.core.data

// core/model/
package com.tsutsen.platformplayer.core.model
```

### Subpackages

Organize by responsibility, not by file type:

```
com.tsutsen.platformplayer.feature.home/
├── HomeScreen.kt                 # UI
├── HomeViewModel.kt              # UI state
├── HomeRepository.kt             # (if feature-specific)
└── internal/                     # Internal helpers (not exported)
    └── HomeExtensions.kt
```

**Do NOT organize by file type:**
```
com.tsutsen.platformplayer.feature.home/
├── screens/                      # ✗
├── viewmodels/                   # ✗
└── repositories/                 # ✗
```

---

## 3. File & Class Naming

### General Rules

- Use **PascalCase** for classes, interfaces, and objects
- Use **camelCase** for functions, properties, and variables
- Use **UPPER_SNAKE_CASE** for constants

### Interface Naming

**Drop the "I" prefix.** Kotlin interfaces don't need a prefix.

```kotlin
// Before (old):
interface IPlatformChannel
interface IPlatformContent
interface IWithResultLauncher

// After (new):
interface PlatformChannel
interface PlatformContent
interface ResultLauncher
```

**Exception:** When interfacing with Java libraries that use "I" prefix, keep it for compatibility.

### Repository Naming

```
Interface:   <Name>Repository.kt
Implementation: <Name>RepositoryImpl.kt
```

Location:
```
core/data/src/.../repository/
├── HomeRepository.kt             # Interface
├── LibraryRepository.kt          # Interface
└── impl/
    ├── HomeRepositoryImpl.kt     # Implementation
    └── LibraryRepositoryImpl.kt  # Implementation
```

### ViewModel Naming

```
<Name>ViewModel.kt
```

Examples:
```kotlin
HomeViewModel.kt
SettingsViewModel.kt
PlayerViewModel.kt
```

### Screen/Compose Naming

```
<Name>Screen.kt                  # Main screen
<Name>Components.kt              # Screen-specific composables
```

Examples:
```kotlin
HomeScreen.kt
SettingsScreen.kt
SettingsComponents.kt            # SettingsOptionCard, SettingsSwitchCard, etc.
```

### State Naming

```
sealed interface <Name>UiState   # UI state (sealed interface)
data class <Name>State           # Domain state (if needed)
```

Examples:
```kotlin
// In HomeViewModel.kt:
sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Loaded(val items: List<Card>) : HomeUiState
    data class Error(val message: String) : HomeUiState
}
```

### Data Class Naming

- Use standard English spelling (`Metadata` not `MetaData`)
- Add domain prefix when ambiguous
- Use singular nouns

```kotlin
// Good:
data class VideoCard(...)
data class ChannelDetails(...)
data class StreamMetadata(...)

// Bad:
data class VideoCards(...)          # Plural
data class VideoCardData(...)       # Redundant "Data"
data class StreamMetaData(...)      # Wrong spelling
```

### Manager vs Repository vs Service

| Suffix | Responsibility | Location |
|--------|---------------|----------|
| `Repository` | Data access | `core/data/` |
| `ViewModel` | UI state | `feature/<name>/impl/` |
| `Manager` | UI-state coordination | `feature/<name>/impl/` or `app/` |
| `Service` | Background work | `core/` or `app/services/` |
| `Handler` | Request processing | `app/` (HTTP handlers) |

**Rule:** `Manager` should be short-lived and UI-scoped. Long-lived state belongs in `ViewModel` or `Repository`.

---

## 4. Architecture Layers

### Layer Responsibilities

```
┌─────────────────────────────────────────┐
│              App Layer                   │
│  - Application class                     │
│  - Activities/fragments                  │
│  - DI modules (Hilt)                     │
│  - Navigation graph                      │
└────────────┬────────────────────────────┘
             │ depends on
┌────────────▼────────────────────────────┐
│           Feature Layers                 │
│  - UI screens (Compose)                  │
│  - ViewModels                            │
│  - Feature-specific use cases            │
│  - Local data (if any)                   │
└────────────┬────────────────────────────┘
             │ depends on
┌────────────▼────────────────────────────┐
│            Core Layers                   │
│  - Domain models                         │
│  - Repository interfaces                 │
│  - Repository implementations            │
│  - Database/DataStore                    │
│  - Navigation primitives                 │
│  - Design system                         │
└─────────────────────────────────────────┘
```

### Dependency Rules

1. **App** can depend on everything
2. **Feature** can depend on `core/*` and other features (if necessary)
3. **Core** modules depend only on other core modules (if necessary)
4. **No module** can depend on `app/`

### Clean Architecture Principles

```
Feature → Repository Interface → RepositoryImpl → Database/DataStore
```

**Rule:** Features depend on repository **interfaces**, not implementations. Implementations live in `core/data/`.

---

## 5. Kotlin Language Conventions

### Sealed Classes vs Interfaces

**Use `sealed interface` for UI states and result types.**

```kotlin
// Good:
sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Loaded(val items: List<Card>) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

// Avoid (unless you need shared implementation):
sealed class HomeUiState {
    data object Loading : HomeUiState
    data class Loaded(val items: List<Card>) : HomeUiState
    data class Error(val message: String) : HomeUiState
}
```

### Data Classes

Use data classes for:
- Domain models
- UI state payloads
- DTOs

```kotlin
data class VideoCard(
    val id: String,
    val title: String,
    val thumbnailUrl: String?
)
```

### Interfaces

Use interfaces for:
- Repository contracts
- Platform abstractions
- Callbacks

```kotlin
interface HomeRepository {
    suspend fun getHomeFeed(): Result<List<Card>>
}
```

### Null Safety

- Use nullable types (`String?`) only when null is a valid state
- Use `requireNotNull()` for preconditions
- Use `?:` elvis operator for defaults
- Prefer `sealed classes` over nullable enums

```kotlin
// Good:
sealed interface VideoState {
    data object Idle : VideoState
    data class Playing(val duration: Long) : VideoState
    data class Error(val message: String) : VideoState
}

// Avoid:
enum class VideoState { IDLE, PLAYING, ERROR }
```

### Coroutines

- Use `suspend` functions for async operations
- Use `Flow`/`StateFlow` for reactive streams
- Use `viewModelScope` in ViewModels
- Use `lifecycleScope` in Composables

```kotlin
// ViewModel:
class HomeViewModel(
    private val repository: HomeRepository
) : ViewModel() {
    val uiState: StateFlow<HomeUiState> = repository.getHomeFeed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState.Loading)
}
```

---

## 6. Compose UI Conventions

### Composable Function Naming

- **Public composables:** `PascalCase`
- **Internal composables:** `camelCase` with `internal` visibility

```kotlin
// Public
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    // ...
}

// Internal
@Composable
internal fun HomeFeedCard(card: Card) {
    // ...
}
```

### State Hoisting

Hoist state to the nearest common ancestor.

```kotlin
// Good:
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    navigator: Navigator? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    // ...
}

// Avoid (state inside child composable):
@Composable
fun SettingsScreen() {
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    // ...
}
```

### Scaffold Usage

Use `Scaffold` for screen-level layout.

```kotlin
@Composable
fun HomeScreen() {
    Scaffold(
        topBar = { HomeTopBar() },
        floatingActionButton = { HomeFAB() }
    ) { padding ->
        HomeContent(padding = padding)
    }
}
```

### Preview Annotations

Add `@Preview` to all public composables.

```kotlin
@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    MaterialTheme {
        HomeScreen()
    }
}
```

---

## 7. Dependency Injection

### Hilt Conventions

**Module naming:** `<Name>Module.kt`

**Location:** `app/src/.../di/`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton
    fun provideHomeRepository(
        @ApplicationContext context: Context
    ): HomeRepository = HomeRepositoryImpl(context)
}
```

**ViewModel injection:**

```kotlin
@HiltViewModel
class HomeViewModel(
    private val repository: HomeRepository
) : ViewModel() {
    // ...
}
```

**Rule:** All repository implementations are provided as their interface type.

### DI Module Organization

| Module | Responsibility |
|--------|---------------|
| `DataSourceModule` | Data sources (Room, DataStore) |
| `DatabaseModule` | Room database components |
| `RepositoryModule` | Repository bindings |
| `NavigationModule` | Navigation dependencies |
| `DualScreenModule` | Dual-screen specific dependencies |

---

## 8. Data Layer Conventions

### Repository Pattern

```kotlin
// Interface (core/data/.../repository/)
interface HomeRepository {
    suspend fun getHomeFeed(): Result<List<Card>>
    suspend fun refreshHomeFeed()
}

// Implementation (core/data/.../repository/impl/)
class HomeRepositoryImpl(
    private val database: PlatformDatabase,
    private val api: PlatformApi
) : HomeRepository {
    override suspend fun getHomeFeed(): Result<List<Card>> = runCatching {
        // ...
    }
}
```

### Database

- Use Room for persistent storage
- Entities in `core/database/.../entity/`
- DAOs in `core/database/.../dao/`
- Database class in `core/database/`

```kotlin
@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey val id: String,
    val title: String,
    val url: String
)

@Dao
interface VideoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(video: VideoEntity)
    
    @Query("SELECT * FROM videos")
    fun getVideos(): Flow<List<VideoEntity>>
}
```

### DataStore

- Use DataStore for preferences
- Models in `core/datastore/.../model/`
- Extensions for type-safe access

```kotlin
// Model
data class AppPreferences(
    val theme: Theme = Theme.System,
    val language: String = "en"
)

// Preferences keys object
object PreferencesKeys {
    val THEME = stringPreferencesKey("theme")
    val LANGUAGE = stringPreferencesKey("language")
}
```

---

## 9. Feature Module Conventions

### Standard Feature Structure

```
feature/<name>/
├── impl/
│   └── src/main/java/
│       └── com/tsutsen/platformplayer/feature/<name>/impl/
│           ├── <Name>Screen.kt           # Main screen
│           ├── <Name>ViewModel.kt        # UI state
│           ├── <Name>Components.kt       # Screen-specific composables
│           └── internal/                 # Internal helpers
│               └── <Name>Extensions.kt
├── build.gradle.kts
└── proguard-rules.pro (if needed)
```

### ViewModel Pattern

```kotlin
@HiltViewModel
class <Name>ViewModel(
    private val repository: <Name>Repository
) : ViewModel() {
    
    // UI state
    private val _uiState: MutableStateFlow<UiState> = MutableStateFlow(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    // Actions
    fun onEvent(event: Event) {
        viewModelScope.launch {
            when (event) {
                is Event.Refresh -> refresh()
                is Event.Select -> select(event.item)
            }
        }
    }
    
    private suspend fun refresh() {
        _uiState.value = UiState.Loading
        _uiState.value = try {
            val data = repository.getData()
            UiState.Loaded(data)
        } catch (e: Exception) {
            UiState.Error(e.message ?: "Unknown error")
        }
    }
    
    // Sealed interface for UI state
    sealed interface UiState {
        data object Loading : UiState
        data class Loaded(val data: List<Item>) : UiState
        data class Error(val message: String) : UiState
    }
}
```

---

## 10. Testing Conventions

### Test Structure

```
feature/<name>/impl/
└── src/
    ├── main/
    └── test/
        └── java/.../feature/<name>/impl/
            ├── <Name>ViewModelTest.kt
            └── <Name>ScreenTest.kt
```

### Unit Tests

- Name: `<ClassName>Test.kt`
- Use `@RunWith(MockitoJUnitRunner::class)` or `mockk`
- Test naming: `should<behavior>When<condition>()`

```kotlin
class HomeViewModelTest {
    @Test
    fun `should show loaded state when data is fetched`() = runTest {
        // Given
        val repository = mock<HomeRepository> {
            on { getHomeFeed() } doReturn Result.success(emptyList())
        }
        val viewModel = HomeViewModel(repository)
        
        // When
        viewModel.refresh()
        
        // Then
        assertThat(viewModel.uiState.value).isInstanceOf<HomeViewModel.UiState.Loaded>::class
    }
}
```

### Compose UI Tests

- Use `@AndroidTest` for instrumented tests
- Use `createComposeRule()` for Compose testing
- Test user flows, not implementation details

```kotlin
@AndroidTest
class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()
    
    @Test
    fun testHomeScreenDisplaysItems() {
        composeRule.setContent {
            HomeScreen(viewModel = mockViewModel)
        }
        composeRule.onNodeWithText("Welcome").assertExists()
    }
}
```

---

## 11. Git & Code Review

### Branch Naming

```
feature/<name>              # New feature
fix/<bug-id>-<description>  # Bug fix
refactor/<description>      # Refactoring
docs/<description>          # Documentation
```

### Commit Messages

```
<type>(<scope>): <description>

feat(home): add pull-to-refresh
fix(settings): fix theme not persisting
refactor(player): extract playback logic to UseCase
docs(conventions): add conventions document
```

**Types:** `feat`, `fix`, `refactor`, `docs`, `style`, `chore`, `test`

### Code Review Checklist

- [ ] Follows module structure conventions
- [ ] Package names match directory structure
- [ ] No "I" prefix on interfaces
- [ ] UI state uses `sealed interface`
- [ ] Repository implementations in correct location
- [ ] No circular dependencies
- [ ] Composables are previewable
- [ ] Tests included for new logic
- [ ] No TODO comments without issue link

---

## Quick Reference

| Concept | Convention | Example |
|---------|-----------|---------|
| Feature module | `feature/<name>/impl/` | `feature/home/impl/` |
| Core module | `core/<name>/` | `core/data/` |
| Package (feature) | `...feature.<name>` | `...feature.home` |
| Package (core) | `...core.<name>` | `...core.data` |
| Interface | No "I" prefix | `PlatformChannel` |
| Repository | `<Name>Repository` | `HomeRepository` |
| Repository impl | `<Name>RepositoryImpl` | `HomeRepositoryImpl` |
| ViewModel | `<Name>ViewModel` | `HomeViewModel` |
| UI state | `sealed interface <Name>UiState` | `sealed interface HomeUiState` |
| Screen | `<Name>Screen` | `HomeScreen` |
| Composable (public) | `PascalCase` | `HomeScreen()` |
| Composable (internal) | `camelCase` + `internal` | `internal fun HomeCard()` |
| Data class | Singular, standard spelling | `VideoCard`, `Metadata` |
| DI module | `<Name>Module` | `RepositoryModule` |
| Test | `<ClassName>Test` | `HomeViewModelTest` |

---

## Migration Guide

### Phase 1: Structural (High Priority)

1. Move `feature/dualscreen/src/` to `feature/dualscreen/impl/src/`
2. Update package declarations to remove `.impl` suffix
3. Move repository impls from `app/di/` to `core/data/repository/impl/`

### Phase 2: Naming (Medium Priority)

1. Rename `I*` interfaces to remove prefix
2. Rename `MetaData` → `Metadata`
3. Standardize UI state to `sealed interface`

### Phase 3: Architecture (Low Priority)

1. Introduce UseCase layer (optional)
2. Remove duplicate managers/services between core and app
3. Add comprehensive test coverage

---

*Last updated: 2025-07-29*
*Owner: Bluejay Core Team*
