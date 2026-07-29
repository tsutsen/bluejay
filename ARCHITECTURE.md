# Bluejay Architecture Guide

> **Purpose:** This document documents the complete project structure, module relationships, and architectural patterns. Use this to understand where code lives and how modules interact.

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Module Map](#2-module-map)
3. [Dependency Graph](#3-dependency-graph)
4. [App Module](#4-app-module)
5. [Core Modules](#5-core-modules)
6. [Feature Modules](#6-feature-modules)
7. [Data Flow](#7-data-flow)
8. [Navigation Architecture](#8-navigation-architecture)
9. [Key Entry Points](#9-key-entry-points)
10. [Submodules & Dependencies](#10-submodules--dependencies)
11. [Build Configuration](#11-build-configuration)
12. [Quick Reference](#12-quick-reference)

---

## 1. Project Overview

**Bluejay** is a modern Android video player app built with Jetpack Compose. It's a fork/continuation of Grayjay, focusing on decentralized content consumption.

### Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose (Material 3)
- **Architecture:** MVVM (Model-View-ViewModel)
- **DI:** Hilt (Dagger 2)
- **Database:** Room
- **Preferences:** DataStore
- **Navigation:** Navigation Compose (navigation3)
- **Coroutines:** Kotlin Coroutines + Flow
- **Build:** Gradle (Kotlin DSL)

### Module Count

- **10 Core modules** — Shared libraries
- **9 Feature modules** — User-facing features
- **1 App module** — Application entry point
- **Total:** 20 modules

### File Distribution

| Module Type | Files | Percentage |
|-------------|-------|------------|
| app/ | 512 | 73% |
| core/ | 70 | 10% |
| feature/ | 36 | 5% |
| dep/ (submodules) | ~100 | 12% |
| **Total** | **~718** | **100%** |

---

## 2. Module Map

### Top-Level Structure

```
bluejay/
├── app/                          # Application module (entry point)
├── core/                         # Shared core libraries
│   ├── data/                     # Repository interfaces & implementations
│   ├── database/                 # Room database
│   ├── datastore/                # DataStore preferences
│   ├── designsystem/             # Shared Compose components & theme
│   ├── model/                    # Domain models
│   ├── navigation/               # Navigation primitives
│   ├── notifications/            # Notification handling
│   ├── sync/                     # Sync service
│   ├── testing/                  # Test utilities
│   └── ui/                       # UI utilities
├── feature/                      # Feature modules
│   ├── companion/                # Companion mode (desktop integration)
│   ├── dualscreen/               # Dual-screen/tablet support
│   ├── feed/                     # Feed display (placeholder)
│   ├── home/                     # Home screen
│   ├── library/                  # Library/playlist management
│   ├── player/                   # Video player UI
│   ├── search/                   # Search functionality
│   ├── settings/                 # Settings screen
│   └── subscriptions/            # Subscriptions management
├── dep/                          # Submodules (external dependencies)
│   ├── futopay/                  # Payment processing
│   └── polycentricandroid/       # Decentralized identity (Polycentric)
├── graphify-out/                 # Graph analysis output (tooling)
├── scripts/                      # Build/deployment scripts
├── docs/                         # Documentation
├── tasks/                        # Task/issue tracking
└── artifacts/                    # SDLC artifacts
```

---

## 3. Dependency Graph

### Module Dependencies

```
app/ (512 files)
├── depends on: core/*, feature/*, dep/*
├── provides: Activities, DI modules, navigation graph, services
└── entry point: MainActivity

feature/* (36 files total)
├── depends on: core/*, other features (if necessary)
├── provides: UI screens, ViewModels
└── consumed by: app/

core/* (70 files total)
├── depends on: other core modules (if necessary)
├── provides: Repositories, models, database, designsystem
└── no module depends on app/

dep/* (submodules)
├── futopay/ — Payment processing
├── polycentricandroid/ — Decentralized identity
└── consumed by: app/, feature/* (as needed)
```

### Dependency Rules

1. **App** can depend on everything
2. **Feature** can depend on `core/*` and other features
3. **Core** modules depend only on other core modules
4. **No module** can depend on `app/`
5. **Circular dependencies** are forbidden

### Visual Dependency Graph

```
┌─────────────────────────────────────────────────────────────┐
│                        app/                                 │
│  ┌───────────┐ ┌──────────┐ ┌───────────┐ ┌─────────────┐ │
│  │ Activities│ │   DI     │ │  NavGraph │ │   Services  │ │
│  └─────┬─────┘ └────┬────┘ └─────┬─────┘ └──────┬──────┘ │
└────────┼─────────────┼────────────┼──────────────┼────────┘
         │             │            │              │
         ▼             ▼            ▼              ▼
┌─────────────────────────────────────────────────────────────┐
│                   feature/*                                 │
│  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐ ┌──────────┐ │
│  │  home  │ │ player │ │ search │ │settings│ │subscript.│ │
│  └───┬────┘ └───┬────┘ └───┬────┘ └───┬────┘ └────┬─────┘ │
└──────┼──────────┼──────────┼──────────┼────────────┼───────┘
       │          │          │          │            │
       ▼          ▼          ▼          ▼            ▼
┌─────────────────────────────────────────────────────────────┐
│                     core/*                                  │
│  ┌──────┐ ┌───────┐ ┌───────┐ ┌──────────┐ ┌───────────┐ │
│  │ model│ │  data │ │database│ │designsystem│ navigation │ │
│  └──────┘ └───────┘ └───────┘ └──────────┘ └───────────┘ │
└─────────────────────────────────────────────────────────────┘
```

---

## 4. App Module

**Location:** `app/src/main/java/com/tsutsen/platformplayer/`

**Purpose:** Application entry point, DI configuration, activities, services, and high-level orchestration.

### File Count: 512 files

### Key Directories

```
app/src/main/java/com/tsutsen/platformplayer/
├── activities/              # Android Activities
├── api/                     # API clients & HTTP
├── auth/                    # Authentication
├── background/              # Background tasks
├── casting/                 # Cast/Chromecast support
├── compose/                 # Compose navigation & state
├── constructs/              # Reusable task patterns
├── debug/                   # Debug utilities
├── developer/               # Developer tools
├── di/                      # Hilt DI modules
├── dialogs/                 # Dialog components
├── downloads/               # Download management
├── encryption/              # Encryption utilities
├── engine/                  # JS engine for plugins
├── exceptions/              # Custom exceptions
├── functional/              # Functional utilities
├── helpers/                 # Helper functions
├── images/                  # Image loading
├── logging/                 # Logging utilities
├── models/                  # App-specific models
├── navigation/              # Navigation utilities
├── noise/                   # Noise protocol (encryption)
├── others/                  # Miscellaneous
├── parsers/                 # HTML/JSON parsers
├── polycentric/             # Polycentric integration
├── receivers/               # Broadcast receivers
├── sabr/                    # SABR streaming protocol
├── serializers/             # Serialization utilities
├── services/                # Background services
├── states/                  # State management
├── stores/                  # Data stores
├── subscription/            # Subscription handling
├── subsexchange/            # Subscription exchange
├── sync/                    # Sync utilities
├── testing/                 # Test utilities
└── theming/                 # Theme management
```

### Key Files

#### Activities

| File | Purpose |
|------|---------|
| `MainActivity.kt` | Main activity, hosts Compose UI |
| `BaseActivity.kt` | Base class for all activities |
| `CaptchaActivity.kt` | Captcha solving UI |
| `CompanionActivity.kt` | Companion mode activity |
| `InstallUpdateActivity.kt` | Update installation UI |
| `QRCaptureActivity.kt` | QR code scanning |

#### DI Modules

| File | Purpose |
|------|---------|
| `di/DatabaseModule.kt` | Room database components |
| `di/DataSourceModule.kt` | Data source bindings |
| `di/DualScreenModule.kt` | Dual-screen dependencies |
| `di/HomeEngineModule.kt` | JS engine for home |
| `di/NavigationModule.kt` | Navigation dependencies |
| `di/RepositoryModule.kt` | Repository bindings |

#### Navigation

| File | Purpose |
|------|---------|
| `compose/BluejayNavGraph.kt` | Main navigation graph |
| `compose/navigation/BluejayNavigator.kt` | Navigation actions |
| `compose/navigation/BluejayNavKey.kt` | Navigation keys (route params) |
| `compose/navigation/BluejayNavigationState.kt` | Navigation state |

#### Services

| File | Purpose |
|------|---------|
| `services/DownloadService.kt` | Download management |
| `services/MediaPlaybackService.kt` | Media playback (foreground) |
| `sync/internal/SyncService.kt` | Sync service |
| `UpdateDownloadService.kt` | App update downloads |

---

## 5. Core Modules

### 5.1 core/model

**Location:** `core/model/src/main/java/.../core/model/`

**Purpose:** Domain models shared across all modules.

**File Count:** 9 files

| File | Purpose |
|------|---------|
| `Cards.kt` | Card interfaces (`Card`, `VideoCard`, `ChannelCard`, etc.) |
| `CommentItem.kt` | Comment models |
| `ContentItem.kt` | Content item models |
| `Creator.kt` | Creator/author models |
| `FeedPage.kt` | Feed pagination models |
| `LibrarySection.kt` | Library section models |
| `PlayerState.kt` | Player state models |
| `SearchResult.kt` | Search result models |
| `SubscriptionFeed.kt` | Subscription feed models |

**Key Interfaces:**
```kotlin
sealed interface Card : Parcelable
// Implementations: VideoCard, ChannelCard, PlaylistCard, etc.
```

### 5.2 core/data

**Location:** `core/data/src/main/java/.../core/data/`

**Purpose:** Repository interfaces and implementations.

**File Count:** 14 files

```
core/data/src/main/java/.../core/data/
├── repository/
│   ├── HomeRepository.kt           # Interface
│   ├── LibraryRepository.kt        # Interface
│   ├── PlayerRepository.kt         # Interface
│   ├── SearchRepository.kt         # Interface
│   ├── SettingsRepository.kt       # Interface
│   ├── SubscriptionRepository.kt   # Interface
│   ├── CommentRepository.kt        # Interface
│   ├── VideoDetails.kt             # Video details model
│   └── impl/
│       ├── HomeRepositoryImpl.kt
│       ├── LibraryRepositoryImpl.kt
│       ├── PlayerRepositoryImpl.kt
│       ├── SearchRepositoryImpl.kt
│       ├── SettingsRepositoryImpl.kt
│       ├── SubscriptionRepositoryImpl.kt
│       └── (others...)
```

**Pattern:** Interface in `repository/`, implementation in `repository/impl/`

### 5.3 core/database

**Location:** `core/database/src/main/java/.../core/database/`

**Purpose:** Room database schema, DAOs, and entities.

**File Count:** 12 files

```
core/database/src/main/java/.../core/database/
├── AppDatabase.kt                  # Room database
├── dao/
│   ├── HistoryDao.kt               # Watch history
│   ├── HomeFeedCacheDao.kt         # Home feed cache
│   ├── PlaylistDao.kt              # Playlists
│   ├── QueueDao.kt                 # Play queue
│   ├── SubscriptionDao.kt          # Subscriptions
│   └── (others...)
└── entity/
    ├── HistoryEntity.kt
    ├── HomeFeedCacheEntity.kt
    ├── PlaylistEntity.kt
    ├── PlaylistVideoEntity.kt
    ├── QueueEntity.kt
    └── SubscriptionEntity.kt
```

### 5.4 core/datastore

**Location:** `core/datastore/src/main/java/.../core/datastore/`

**Purpose:** DataStore preferences for settings.

**File Count:** 3 files

| File | Purpose |
|------|---------|
| `model/AppPreferences.kt` | App preferences data class |
| `PreferencesKeys.kt` | DataStore key definitions |
| `AppPreferencesSerializer.kt` | Serialization/deserialization |

### 5.5 core/designsystem

**Location:** `core/designsystem/src/main/java/.../core/designsystem/`

**Purpose:** Shared Compose components, theme, and layout.

**File Count:** 22 files

```
core/designsystem/src/main/java/.../core/designsystem/
├── component/
│   ├── CardContainer.kt            # Video container (List/Horizontal/Grid)
│   ├── ChannelHero.kt              # Channel banner/profile
│   ├── Comment.kt                  # Comment display
│   ├── CreatorAvatar.kt            # Creator avatar
│   ├── DescriptionExpandable.kt    # Expandable description
│   ├── EmptyState.kt               # Empty state placeholder
│   ├── FilterChip.kt               # Filter chips
│   ├── LoadingContent.kt           # Loading indicator
│   ├── LoadingSkeleton.kt          # Skeleton loading
│   ├── ModalBottomSheet.kt         # Modal bottom sheet
│   ├── NotificationCard.kt         # Notification card
│   ├── PaginationControls.kt       # Pagination UI
│   ├── PlaceholderScreen.kt        # Placeholder screen
│   ├── SectionHeader.kt            # Section headers
│   ├── SettingsRow.kt              # Settings row item
│   ├── TabRow.kt                   # Tab row
│   └── VideoCard.kt                # Video card component
├── icons/
│   └── Icons.kt                    # Custom icons
├── layout/
│   └── AppLayout.kt                # App layout wrapper
└── theme/
    ├── Color.kt                    # Color palette
    ├── GrayjayTheme.kt             # Theme composition
    └── Typography.kt               # Typography styles
```

### 5.6 core/navigation

**Location:** `core/navigation/src/main/java/.../core/navigation/`

**Purpose:** Navigation primitives, destinations, and navigator interface.

**File Count:** 4 files

| File | Purpose |
|------|---------|
| `GrayjayNavGraph.kt` | Base navigation graph |
| `NavDestination.kt` | Navigation destination sealed class |
| `NavigationState.kt` | Navigation state management |
| `Navigator.kt` | Navigator interface |

### 5.7 core/notifications

**Location:** `core/notifications/src/main/java/.../core/notifications/`

**Purpose:** Notification handling utilities.

**File Count:** 1 file

### 5.8 core/sync

**Location:** `core/sync/src/main/java/.../core/sync/`

**Purpose:** Sync service utilities.

**File Count:** 1 file

### 5.9 core/testing

**Location:** `core/testing/src/main/java/.../core/testing/`

**Purpose:** Test utilities and fixtures.

**File Count:** 1 file

### 5.10 core/ui

**Location:** `core/ui/src/main/java/.../core/ui/`

**Purpose:** UI utilities.

**File Count:** 3 files

---

## 6. Feature Modules

### 6.1 feature/home

**Location:** `feature/home/impl/src/main/java/.../feature/home/impl/`

**Purpose:** Home screen with feed display.

**File Count:** 2 files

| File | Purpose |
|------|---------|
| `HomeScreen.kt` | Home screen composable |
| `HomeViewModel.kt` | Home screen ViewModel |

**Navigation Route:** `HomeScreen`

### 6.2 feature/player

**Location:** `feature/player/impl/src/main/java/.../feature/player/impl/`

**Purpose:** Video player UI with controls, comments, and recommendations.

**File Count:** 23 files (largest feature)

```
feature/player/impl/src/main/java/.../feature/player/impl/
├── PlayerGeometry.kt               # Player geometry calculations
├── PlayerOverlayMode.kt            # Overlay mode enum
├── PlayerView.kt                   # Main player view
├── PlayerViewModel.kt              # Player ViewModel
├── ui/
│   ├── PlayerContent.kt            # Player content layout
│   ├── PlayerControls.kt           # Playback controls
│   ├── PlayerDetails.kt            # Video details
│   ├── PlayerGestures.kt           # Touch gestures
│   ├── PlayerIndicators.kt         # Progress indicators
│   ├── PlayerModals.kt             # Modal dialogs
│   ├── PlayerUIScaffold.kt         # Player scaffold layout
│   ├── components/
│   │   ├── ChannelRow.kt           # Channel info row
│   │   ├── CommentCard.kt          # Comment card
│   │   ├── DescriptionSection.kt   # Description section
│   │   ├── PlayerVideoSurface.kt   # Video surface
│   │   ├── RecommendedSection.kt   # Recommendations
│   │   ├── TabsSection.kt          # Tabs (comments, etc.)
│   │   └── VideoStatsRow.kt        # View count, likes
│   ├── overlays/
│   │   ├── PlayerCompactOverlay.kt # Compact overlay
│   │   ├── PlayerFloatingOverlay.kt# Floating overlay
│   │   ├── PlayerNormalBottomOverlay.kt# Normal bottom
│   │   └── PlayerNormalTopOverlay.kt   # Normal top
│   └── utils/
│       └── PlayerFormatters.kt     # Time/number formatting
```

**Navigation Route:** `PlayerScreen`

### 6.3 feature/search

**Location:** `feature/search/impl/src/main/java/.../feature/search/impl/`

**Purpose:** Search functionality.

**File Count:** 1 file

| File | Purpose |
|------|---------|
| `SearchScreen.kt` | Search screen composable |

**Navigation Route:** `SearchScreen`

### 6.4 feature/settings

**Location:** `feature/settings/impl/src/main/java/.../feature/settings/impl/`

**Purpose:** Settings screen with categories.

**File Count:** 2 files

| File | Purpose |
|------|---------|
| `SettingsScreen.kt` | Settings screen with hub and categories |
| `SettingsViewModel.kt` | Settings ViewModel |

**Navigation Route:** `SettingsScreen`

**Categories:**
1. Appearance
2. Feed & Content
3. Player
4. Privacy & Data
5. Sync & Identity
6. General
7. Plugin Browser
8. About

### 6.5 feature/subscriptions

**Location:** `feature/subscriptions/impl/src/main/java/.../feature/subscriptions/impl/`

**Purpose:** Subscriptions management.

**File Count:** 2 files

| File | Purpose |
|------|---------|
| `SubscriptionsScreen.kt` | Subscriptions screen |
| `SubscriptionsViewModel.kt` | Subscriptions ViewModel |

**Navigation Route:** `SubscriptionsScreen`

### 6.6 feature/library

**Location:** `feature/library/impl/src/main/java/.../feature/library/impl/`

**Purpose:** Library/playlist management.

**File Count:** 1 file

| File | Purpose |
|------|---------|
| `LibraryScreen.kt` | Library screen |

**Navigation Route:** `LibraryScreen`

### 6.7 feature/companion

**Location:** `feature/companion/impl/src/main/java/.../feature/companion/impl/`

**Purpose:** Companion mode (desktop integration).

**File Count:** 1 file

| File | Purpose |
|------|---------|
| `CompanionScreen.kt` | Companion mode screen |

### 6.8 feature/dualscreen

**Location:** `feature/dualscreen/src/main/java/.../feature/dualscreen/`

**Purpose:** Dual-screen/tablet support.

**File Count:** 3 files

**Note:** This module does NOT use `/impl/` subdirectory (inconsistent with other features).

| File | Purpose |
|------|---------|
| `AppScreenState.kt` | App state for dual-screen |
| `CompanionWindowManager.kt` | Companion window management |
| `ScreenCoordinator.kt` | Screen coordination |

### 6.9 feature/feed

**Location:** `feature/feed/impl/src/main/java/.../feature/feed/impl/`

**Purpose:** Feed display (placeholder).

**File Count:** 1 file

| File | Purpose |
|------|---------|
| `PlaceholderScreen.kt` | Placeholder for feed feature |

---

## 7. Data Flow

### Repository Pattern

```
Feature ViewModel
    ↓ depends on interface
Repository Interface (core/data/)
    ↓ implementation
RepositoryImpl (core/data/impl/)
    ↓ uses
Database/DataStore (core/database/, core/datastore/)
    ↓ persists
SQLite/SharedPreferences
```

### Example: Home Feed

```
HomeViewModel
    ↓ calls
HomeRepository.getHomeFeed()
    ↓ implemented by
HomeRepositoryImpl
    ↓ queries
HomeFeedCacheDao
    ↓ returns
Flow<List<Card>>
    ↓ collected by
HomeViewModel.uiState
    ↓ observed by
HomeScreen
    ↓ displays
LazyColumn of VideoCards
```

### State Flow

```
Repository
    ↓ emits
StateFlow<UiState>
    ↓ collected in ViewModel
StateFlow<UiState>
    ↓ observed in Compose
collectAsState()
    ↓ triggers recomposition
UI updates
```

---

## 8. Navigation Architecture

### Navigation Stack

```
BluejayNavGraph (app/)
    ↓ hosts
GrayjayNavGraph (core/navigation/)
    ↓ routes to
Feature Screens (feature/*/)
```

### Navigation Keys

Defined in `app/compose/navigation/BluejayNavKey.kt`:

| Key | Purpose |
|-----|---------|
| `ChannelDetail` | Channel page |
| `PlaylistDetail` | Playlist page |
| `SourceDetail` | Source details |
| `ContentSearchResults` | Search results |
| `CreatorSearchResults` | Creator search |

### Navigator

Interface in `core/navigation/Navigator.kt`:

```kotlin
interface Navigator {
    fun navigateToHome()
    fun navigateToPlayer(videoId: String)
    fun navigateToSearch(query: String)
    fun navigateToSettings()
    fun navigateToPluginBrowser()
    fun popBackStack()
}
```

Implementation in `app/compose/navigation/BluejayNavigator.kt`.

---

## 9. Key Entry Points

### Application Entry

```kotlin
// app/src/main/AndroidManifest.xml
<activity android:name=".activities.MainActivity" ...>
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```

### MainActivity Flow

```
MainActivity.onCreate()
    ↓ sets content
GrayjayMainActivity()
    ↓ composes
BluejayNavGraph()
    ↓ hosts
GrayjayNavGraph()
    ↓ routes based on state
HomeScreen / PlayerScreen / etc.
```

### Service Entry Points

| Service | Trigger |
|---------|---------|
| `MediaPlaybackService` | Media playback |
| `DownloadService` | Download requests |
| `SyncService` | Sync scheduler |
| `UpdateDownloadService` | App updates |

---

## 10. Submodules & Dependencies

### dep/futopay

**Purpose:** Payment processing for creator support.

**Location:** `dep/futopay/android/`

**Key Components:**
- Payment UI
- Payment methods
- Transaction handling

### dep/polycentricandroid

**Purpose:** Decentralized identity and sync via Polycentric protocol.

**Location:** `dep/polycentricandroid/`

**Key Components:**
- Identity management
- Sync protocol
- Message handling

---

## 11. Build Configuration

### Gradle Structure

```
build.gradle                      # Root build file
settings.gradle                   # Module includes
gradle.properties                 # Gradle properties
```

### Module Includes (settings.gradle)

```groovy
include ':app'
include ':core:model'
include ':core:data'
include ':core:database'
include ':core:datastore'
include ':core:designsystem'
include ':core:navigation'
include ':core:notifications'
include ':core:sync'
include ':core:testing'
include ':core:ui'
include ':feature:home:impl'
include ':feature:player:impl'
include ':feature:search:impl'
include ':feature:settings:impl'
include ':feature:subscriptions:impl'
include ':feature:library:impl'
include ':feature:companion:impl'
include ':feature:dualscreen'
include ':feature:feed:impl'
```

### Build Variants

- `stable` — F-Droid release
- `unstable` — Play Store release
- `debug` — Debug build

---

## 12. Quick Reference

### "Where do I find...?"

| I want to... | Look in... |
|--------------|-----------|
| Add a new screen | `feature/<name>/impl/src/...` |
| Modify a ViewModel | `feature/<name>/impl/src/.../<Name>ViewModel.kt` |
| Add a repository method | `core/data/src/.../repository/<Name>Repository.kt` |
| Add a database table | `core/database/src/.../entity/` + `dao/` |
| Add a shared component | `core/designsystem/src/.../component/` |
| Modify the theme | `core/designsystem/src/.../theme/` |
| Add a navigation route | `app/compose/BluejayNavGraph.kt` |
| Add a DI binding | `app/di/RepositoryModule.kt` |
| Modify settings | `feature/settings/impl/src/.../SettingsScreen.kt` |
| Modify player UI | `feature/player/impl/src/.../ui/` |
| Add a service | `app/services/` |
| Add an activity | `app/activities/` |
| Add a model | `core/model/src/.../` |

### Module File Counts

| Module | Files |
|--------|-------|
| app/ | 512 |
| core/data | 14 |
| core/database | 12 |
| core/designsystem | 22 |
| core/model | 9 |
| core/navigation | 4 |
| core/datastore | 3 |
| core/ui | 3 |
| core/notifications | 1 |
| core/sync | 1 |
| core/testing | 1 |
| feature/player | 23 |
| feature/home | 2 |
| feature/settings | 2 |
| feature/subscriptions | 2 |
| feature/library | 1 |
| feature/search | 1 |
| feature/companion | 1 |
| feature/dualscreen | 3 |
| feature/feed | 1 |

### Key Packages

| Package | Purpose |
|---------|---------|
| `...activities` | Android Activities |
| `...compose` | Compose navigation |
| `...di` | Hilt DI modules |
| `...services` | Background services |
| `...core.data.repository` | Repository interfaces |
| `...core.data.repository.impl` | Repository implementations |
| `...core.database` | Room database |
| `...core.model` | Domain models |
| `...core.designsystem` | Shared components |
| `...feature.*.impl` | Feature screens |

---

*Last updated: 2026-07-29*
*Owner: Bluejay Core Team*
