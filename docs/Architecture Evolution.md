# Architecture Evolution: XML → Hybrid → Compose

This document delineates the three architectural layers that have coexisted in the Grayjay codebase during its migration from XML Views to Jetpack Compose.

---

## 1. ORIGINAL (XML) Architecture

**Status**: Legacy reference only. Do not maintain or extend.

### Core Pattern
- **UI = XML Layouts + Fragment Controllers**
- Each screen is an XML layout file (`res/layout/...`) inflated in a Fragment's `onCreateView()`
- State lives in singleton `State*` objects (`StateLibrary`, `StatePlayer`, `StateSubscriptions`, `StateDownloads`)
- Navigation is FragmentManager-based via `NavDestination` and `NavHostFragment`

### Key Components (Community 152, 171, 189, 334, 418)

| Component | Location | Role |
|-----------|----------|------|
| `MainFragment` | `fragment/mainactivity/main/MainFragment.kt` | Root container, hosts child fragments |
| `VideoDetailFragment` | `fragment/mainactivity/main/VideoDetailFragment.kt` | Video detail screen |
| `ChannelFragment` | `fragment/mainactivity/main/ChannelFragment.kt` | Channel detail screen |
| `RecyclerFragment` | `fragment/mainactivity/main/RecyclerFragment.kt` | Base list fragment |
| `SearchTopBarFragment` | `fragment/mainactivity/topbar/SearchTopBarFragment.kt` | Search bar overlay |
| `NavigationTopBarFragment` | `fragment/mainactivity/topbar/NavigationTopBarFragment.kt` | Top navigation bar |
| `ImportTopBarFragment` | `fragment/mainactivity/topbar/ImportTopBarFragment.kt` | Import flow top bar |
| `AddTopBarFragment` | `fragment/mainactivity/topbar/AddTopBarFragment.kt` | Add content top bar |
| `GeneralTopBarFragment` | `fragment/mainactivity/topbar/GeneralTopBarFragment.kt` | Generic top bar |
| `FilesTopBarFragment` | `fragment/mainactivity/topbar/FilesTopBarFragment.kt` | File browser top bar |

### Navigation Flow
```
MainActivity
  └── MainFragment (NavHostFragment)
        ├── VideoDetailFragment
        ├── ChannelFragment
        ├── DownloadsFragment
        ├── LibraryFragment
        ├── ShortsFragment
        ├── SuggestionsFragment
        └── ... (10+ more XML fragments)
```

### State Management
- **Singleton StateObjects**: `StateLibrary`, `StatePlayer`, `StateSubscriptions`, `StateDownloads`, `StatePlaylists`, `StatePlatform`
- **LiveData** for reactive updates
- **BatchedTaskHandler** for batching API calls (5-min LRU cache)
- **Plugin-based detail fetching**: JS scripts fetch data, wrapped in typed models (`IPlatformVideoDetails`, `IPlatformChannel`)

### Data Layer (Shared across all architectures)
- **Repository pattern**: Clean separation of data sources
- **Room DAOs**: Local database access
- **Plugin API**: `IPlatformClient`, `IPlatformContent`, `IPlatformVideo`, `IPlatformChannel` interfaces

---

## 2. HYBRID Architecture

**Status**: Transitional. Exists to bridge XML → Compose migration. Should be removed.

### Core Pattern
- **Compose composables hosted inside XML Fragments**
- Uses `ComposeFragment` (interop wrapper) to embed Compose content in Fragment lifecycle
- Navigation uses Compose `NavHost` but falls back to XML fragments for screens without Compose implementations
- Bottom navigation implemented as a Fragment (`BottomBarFragment`) hosting a Compose `BottomBar()` composable

### Key Components (Community 236)

| Component | Location | Role |
|-----------|----------|------|
| `ComposeFragment` | `compose/interop/ComposeFragment.kt` | Generic Fragment that hosts a `ComposeView` with a `createContent()` callback |
| `BottomBarFragment` | `compose/bottombar/BottomBarFragment.kt` | Fragment hosting `BottomBar()` composable; manages tab navigation |
| `TestComposeFragment` | `compose/test/TestComposeFragment.kt` | Test fragment extending `MainFragment` (legacy), hosts `TestComposeScreen()` |
| `BottomBar()` | `compose/bottombar/BottomBar.kt` | Compose composable for bottom navigation bar |

### Hybrid Navigation Pattern (PlatformPlayerActivity.kt)

```kotlin
// HYBRID: Falls back to XML fragments for screens without Compose implementations
private fun createGrayjayNavEntry(key: NavKey, navigator: GrayjayNavigator, activity: FragmentActivity): NavEntry<NavKey> {
    return when (key) {
        is Home -> NavEntry(key) { HomeScene(navigator) }  // Pure Compose
        
        // HYBRID: Tries Compose first, falls back to XML fragment
        is Subscriptions -> {
            val fragment = getXmlFragmentForNavKey(key)
            if (fragment != null) {
                NavEntry(key) { FragmentFallback(fragment, activity) }  // XML via AndroidView
            } else {
                NavEntry(key) { SubscriptionsScene(navigator) }  // Compose
            }
        }
        
        // ... 20+ more routes with same pattern
        is TestCompose -> NavEntry(key) { TestComposeScene(navigator) }  // Pure Compose
        else -> NavEntry(key) { UnknownScene(key) }
    }
}

// HYBRID: Maps NavKey → XML Fragment
private fun getXmlFragmentForNavKey(key: NavKey): Fragment? {
    val fragment = when (key) {
        is Subscriptions -> SubscriptionsFeedFragment.newInstance()
        is Playlists -> PlaylistsFragment.newInstance()
        is Library -> LibraryFragment.newInstance()
        is Search -> ContentSearchResultsFragment.newInstance()
        // ... 25+ more mappings
        else -> null
    }
    return fragment
}

// HYBRID: Hosts XML fragment inside Compose via AndroidView
@Composable
private fun FragmentFallback(fragment: Fragment, activity: FragmentActivity) {
    AndroidView(
        factory = { context ->
            val containerView = FragmentContainerView(context).apply {
                id = View.generateViewId()
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            activity.supportFragmentManager.beginTransaction()
                .replace(containerView.id, fragment)
                .commit()
            containerView
        }
    )
}
```

### Transitional Code Locations
- `PlatformPlayerActivity.kt`: `getXmlFragmentForNavKey()` (30+ fragment mappings), `FragmentFallback` composable, `when` blocks with fallback logic
- `compose/interop/ComposeFragment.kt`: Generic Fragment wrapper for ComposeView
- `compose/bottombar/BottomBarFragment.kt`: Fragment wrapper around Compose BottomBar
- `compose/test/TestComposeFragment.kt`: Test fragment (extends legacy MainFragment)

### Why Hybrid Exists
- Allows incremental migration: screens can be ported one at a time
- Provides a safety net: if a Compose scene fails, fall back to XML
- Enables testing of Compose scenes alongside existing XML screens

---

## 3. NEW (Full Compose) Architecture

**Status**: Target architecture. All screens should eventually use this pattern.

### Core Pattern
- **UI = Pure Compose composables**
- **Navigation = Compose Navigation (`NavHost` + `NavKey` sealed class)**
- **State = `ViewModel` + `StateFlow`/`SharedFlow`**
- **No XML layouts for UI, no Fragment controllers for navigation**

### Key Components

| Component | Location | Role |
|-----------|----------|------|
| `PlatformPlayerActivity` | `compose/navigation/PlatformPlayerActivity.kt` | Hosts Compose `NavHost`, manages back stack |
| `GrayjayNavigator` | `compose/navigation/GrayjayNavigator.kt` | Navigation controller (push, pop, navigate to tab) |
| `NavKey` | `compose/navigation/NavKey.kt` | Sealed class hierarchy representing all navigation destinations |
| `NavEntry` | `compose/navigation/NavEntry.kt` | Wrapper for Compose scenes with lazy composition |
| `HomeScene` | `compose/home/HomeScene.kt` | Home screen (pure Compose) |
| `SubscriptionsScene` | `compose/subscriptions/SubscriptionsScene.kt` | Subscriptions screen (pure Compose) |
| `LibraryScene` | `compose/library/LibraryScene.kt` | Library screen (pure Compose) |
| `SearchScene` | `compose/search/SearchScene.kt` | Search screen (pure Compose) |
| `VideoDetailScene` | `compose/player/VideoDetailScene.kt` | Video detail screen (pure Compose) |
| `SettingsScene` | `compose/settings/SettingsScene.kt` | Settings screen (pure Compose) |
| `GrayjayBottomNavBar` | `compose/navigation/PlatformPlayerActivity.kt` | Bottom navigation bar (pure Compose) |

### Navigation Flow (Target)
```
PlatformPlayerActivity (Compose Activity)
  └── NavHost (Compose Navigation)
        ├── HomeScene
        ├── SubscriptionsScene
        ├── PlaylistsScene
        ├── LibraryScene
        ├── SearchScene
        ├── SettingsScene
        ├── VideoDetailScene(key: NavKey)
        ├── ChannelDetailScene(key: NavKey)
        ├── PlaylistDetailScene(key: NavKey)
        ├── ShortsScene
        ├── NotificationsScene
        └── ... (all routes use Compose scenes)
```

### Navigation Implementation (Target)
```kotlin
// NEW: Pure Compose scenes, no XML fallback
private fun createGrayjayNavEntry(key: NavKey, navigator: GrayjayNavigator): NavEntry<NavKey> {
    return when (key) {
        is Home -> NavEntry(key) { HomeScene(navigator) }
        is Subscriptions -> NavEntry(key) { SubscriptionsScene(navigator) }
        is Playlists -> NavEntry(key) { PlaylistsScene(navigator) }
        is Library -> NavEntry(key) { LibraryScene(navigator) }
        is Search -> NavEntry(key) { SearchScene(navigator) }
        is Settings -> NavEntry(key) { SettingsScene(navigator) }
        is VideoDetail -> NavEntry(key) { VideoDetailScene(key, navigator) }
        is ChannelDetail -> NavEntry(key) { ChannelDetailScene(key, navigator) }
        is Shorts -> NavEntry(key) { ShortsScene(navigator) }
        // ... all routes map directly to Compose scenes
        else -> NavEntry(key) { UnknownScene(key) }
    }
}
```

### State Management (Target)
- **`ViewModel` + `StateFlow`**: Replace `State*` singletons
- **`rememberCoroutineScope()`**: For launching coroutines in composables
- **`LaunchedEffect`**: For side effects tied to composable lifecycle
- **`ViewModel` sharing**: Parent-child ViewModel hierarchy for navigation

### Threading (Target)
- **Main dispatcher**: UI updates in composables
- **IO dispatcher**: Network/database operations in ViewModels
- **Default dispatcher**: CPU-intensive work (parsing, encoding)

---

## Architecture Comparison

| Aspect | ORIGINAL (XML) | HYBRID (Transitional) | NEW (Compose) |
|--------|----------------|----------------------|---------------|
| **UI Definition** | XML layouts | Mix: XML + Compose | Pure Compose |
| **Screen Controller** | Fragment | Fragment wrapper | Composable function |
| **Navigation** | FragmentManager | Compose NavHost + XML fallback | Compose NavHost only |
| **State** | `State*` singletons | `State*` singletons + `ViewModel` | `ViewModel` + `StateFlow` |
| **Bottom Nav** | `BottomBarFragment` (Fragment) | `BottomBarFragment` → `BottomBar()` | `GrayjayBottomNavBar` (Composable) |
| **Interop** | N/A | `ComposeFragment`, `FragmentFallback` | None needed |
| **Community ID** | 152, 171, 189, 334, 418 | 236 | 106, 398 |
| **Maintenance** | Read-only reference | **Remove** | **Develop here** |

---

## Migration Strategy

### Phase 1: Remove Hybrid (Current)
1. Delete `ComposeFragment.kt` (interop wrapper)
2. Delete `BottomBarFragment.kt` (transitional bottom bar)
3. Delete `TestComposeFragment.kt` (test transitional)
4. Remove `getXmlFragmentForNavKey()` from `PlatformPlayerActivity.kt`
5. Remove `FragmentFallback` composable from `PlatformPlayerActivity.kt`
6. Update `createGrayjayNavEntry()` to use Compose scenes directly

### Phase 2: Replace State Singletons
1. Replace `StateLibrary` → `HomeViewModel` + `StateFlow`
2. Replace `StatePlayer` → `PlayerViewModel` + `StateFlow`
3. Replace `StateSubscriptions` → `SubscriptionsViewModel` + `StateFlow`
4. Replace `StateDownloads` → `DownloadsViewModel` + `StateFlow`

### Phase 3: Retire XML Fragments
1. Remove `MainFragment` (replaced by `PlatformPlayerActivity` NavHost)
2. Remove `VideoDetailFragment` (replaced by `VideoDetailScene`)
3. Remove `ChannelFragment` (replaced by `ChannelDetailScene`)
4. Remove all other XML fragments
5. Remove `NavDestination` XML navigation graph

---

## Key Files by Architecture

### ORIGINAL (XML) — Read Only
```
app/src/main/java/com/futo/platformplayer/fragment/mainactivity/main/
  ├── MainFragment.kt
  ├── VideoDetailFragment.kt
  ├── VideoDetailView.kt
  ├── ChannelFragment.kt
  ├── DownloadsFragment.kt
  ├── LibraryFragment.kt
  ├── ShortsFragment.kt
  ├── SuggestionsFragment.kt
  └── ImportSubscriptionsFragment.kt

app/src/main/java/com/futo/platformplayer/fragment/mainactivity/topbar/
  ├── SearchTopBarFragment.kt
  ├── NavigationTopBarFragment.kt
  ├── AddTopBarFragment.kt
  ├── GeneralTopBarFragment.kt
  ├── FilesTopBarFragment.kt
  └── ImportTopBarFragment.kt
```

### HYBRID (Transitional) — DELETE
```
app/src/main/java/com/futo/platformplayer/compose/
  ├── interop/ComposeFragment.kt          ← DELETE
  ├── bottombar/BottomBarFragment.kt      ← DELETE
  └── test/TestComposeFragment.kt         ← DELETE

app/src/main/java/com/futo/platformplayer/compose/navigation/PlatformPlayerActivity.kt
  ├── getXmlFragmentForNavKey()           ← DELETE
  ├── FragmentFallback                    ← DELETE
  └── when blocks with fallback logic     ← SIMPLIFY
```

### NEW (Compose) — DEVELOP HERE
```
app/src/main/java/com/futo/platformplayer/compose/
  ├── navigation/
  │   ├── PlatformPlayerActivity.kt       ← NavHost host
  │   ├── GrayjayNavigator.kt             ← Navigation controller
  │   ├── NavKey.kt                       ← Sealed class destinations
  │   └── NavEntry.kt                     ← Scene wrapper
  ├── home/
  │   └── HomeScene.kt                    ← Home screen
  ├── subscriptions/
  │   └── SubscriptionsScene.kt           ← Subscriptions screen
  ├── library/
  │   └── LibraryScene.kt                 ← Library screen
  ├── search/
  │   └── SearchScene.kt                  ← Search screen
  ├── player/
  │   ├── VideoDetailScene.kt             ← Video detail
  │   └── PlayerScreen.kt                 ← Full player
  ├── settings/
  │   └── SettingsScene.kt                ← Settings screen
  └── theme/
      └── ThemeStateHolder.kt             ← Theme state (shared)
```

### SHARED (All Architectures)
```
app/src/main/java/com/futo/platformplayer/
  ├── states/                              ← State singletons (migrate to ViewModel)
  │   ├── StateLibrary.kt
  │   ├── StatePlayer.kt
  │   ├── StateSubscriptions.kt
  │   ├── StateDownloads.kt
  │   ├── StatePlaylists.kt
  │   └── StatePlatform.kt
  ├── api/media/                           ← Plugin API (unchanged)
  │   ├── IPlatformClient.kt
  │   ├── LiveChatManager.kt
  │   ├── models/                          ← Data models
  │   └── platforms/js/                    ← JS plugin implementation
  ├── core/data/                           ← Repository pattern (unchanged)
  │   └── repository/
  ├── downloads/                           ← Download management (unchanged)
  ├── encryption/                          ← Encryption (unchanged)
  ├── subscription/                        ← Subscription algorithms (unchanged)
  └── views/                               ← Custom views (some may migrate to Compose)
      ├── SearchView.kt
      ├── video/FutoShortPlayer.kt
      └── overlays/LiveChatOverlay.kt
```

---

## Guidelines

1. **Never add new XML UI** — XML is a reference/specification only
2. **Never create new Fragment-based screens** — Use Compose composables
3. **Never add fallback logic** — All navigation should use Compose scenes directly
4. **Always use ViewModel + StateFlow** — Replace `State*` singletons
5. **Always use Compose Navigation** — No FragmentManager-based navigation
6. **Share data layer** — Repository pattern, Room DAOs, and Plugin API remain unchanged
