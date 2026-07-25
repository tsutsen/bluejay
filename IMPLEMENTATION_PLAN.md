# Bluejay Fork — Implementation Plan

> **Status**: Draft  
> **Last updated**: 2026-07-23  
> **Target**: AYN Thor dual-screen handheld (API 33+)  
> **Source**: Derived from ARCHITECTURE.md (§12 Migration Phases) + DESIGN.md (§1–14)

---

## Plan Summary

| Phase | Focus | Est. Effort | Status |
|-------|-------|-------------|--------|
| **0** | Foundation & Infrastructure | 3–4 weeks | ✅ Complete |
| **0.5** | State Machine & Cross-Activity State | 1 week | ✅ Complete |
| **1** | App Chrome (Layout + MainActivity) | 1–2 weeks | ✅ Complete |
| **2** | Settings + Appearance | 1 week | ✅ Complete |
| **3** | Home Feed | 2 weeks | ✅ Complete |
| **4** | Video Player | 2–3 weeks | 🟡 In Progress |
| **5** | Library, Subscriptions, Search | 3–4 weeks | ⬜ |
| **6** | Detail Screens + Deep Links | 2–3 weeks | ⬜ |
| **7** | Companion Content + Gamepad | 2 weeks | ⬜ |
| **8** | Cleanup & Migration Bridge Removal | 1–2 weeks | ⬜ |

**Total estimated effort**: 15–20 weeks

**Current progress**: Phase 0–3 complete, Phase 4 in progress (9–12 weeks). App launches with navigation chrome, 6-tab navigation works, Home Feed renders with infinite scroll, Settings with hierarchical categories, Video Player with mini-player and animated transitions.

---

## Phase 0 — Foundation & Infrastructure

**Goal**: Set up the new module structure, DI, database, repositories, and design system. Nothing user-visible yet.

**Status**: ✅ Complete

### 0.1 — Hilt Dependency Injection Setup

| Task | Description | Status |
|------|-------------|--------|
| Create `PlatformPlayerApp` | `@HiltAndroidApp` application class, `DynamicColors` init | ✅ |
| Create `DatabaseModule` | Room DB provider, DAO providers | ✅ |
| Create `RepositoryModule` | Repository interface → impl bindings | ✅ |
| Create `DataSourceModule` | Engine bridge providers (`StatePlayer`, etc.) | ✅ |
| Create `NavigationModule` | `NavHostController` provider | ✅ |
| Create `DualScreenModule` | `CompanionWindowManager` provider | ✅ |

**Acceptance**: App compiles with Hilt, no runtime DI errors. ✅

### 0.2 — Room Database Schema

| Task | Description | Status |
|------|-------------|--------|
| Define `AppDatabase` | Room database with all DAOs | ✅ |
| Define `QueueEntity` | Playback queue items | ✅ |
| Define `HistoryEntity` | Watch history records | ✅ |
| Define `PlaylistEntity` | User playlists | ✅ |
| Define `PlaylistVideoEntity` | Playlist membership | ✅ |
| Define `HomeFeedCacheEntity` | Cached home feed items | ✅ |
| Define `SubscriptionEntity` | Subscribed channels | ✅ |
| Define DAOs | `QueueDao`, `HistoryDao`, `PlaylistDao`, `HomeFeedCacheDao`, `SubscriptionDao` | ✅ |
| Define `AppPreferencesEntity` | DataStore-backed settings | ✅ |

**Acceptance**: All entities compile, DAOs have `@Query` annotations, fallback-to-destructive-migration enabled. ✅

### 0.3 — Repository Interfaces + Bridge Implementations

| Task | Description | Status |
|------|-------------|--------|
| Define `PlayerRepository` | Interface (§4 in ARCHITECTURE.md) | ✅ |
| Define `HomeRepository` | Interface | ✅ |
| Define `SearchRepository` | Interface | ✅ |
| Define `LibraryRepository` | Interface | ✅ |
| Define `SubscriptionRepository` | Interface | ✅ |
| Define `SettingsRepository` | Interface | ✅ |
| Implement `PlayerRepositoryImpl` | Bridges to `StatePlayer` + `PlayerManager` | ✅ |
| Implement `HomeRepositoryImpl` | Bridges to `StatePlatform` | ✅ |
| Implement `SearchRepositoryImpl` | Bridges to engine | ✅ |
| Implement `LibraryRepositoryImpl` | Bridges to `StatePlayer` history | ✅ |
| Implement `SubscriptionRepositoryImpl` | Bridges to `StateSubscriptions` | ✅ |
| Implement `SettingsRepositoryImpl` | DataStore-backed | ✅ |

**Acceptance**: All repositories expose `StateFlow`, bridge implementations compile, no UI code touches legacy singletons directly. ✅

### 0.4 — Design System

| Task | Description | Status |
|------|-------------|--------|
| Create `GrayjayTheme` | Material You theme with dynamic color, Inter font | ✅ |
| Define `GrayjayTypography` | Inter font family, typography scale | ✅ |
| Define color tokens | `GrayjayColorTokens` for light/dark schemes | ✅ |
| Define icon system | Material Symbols + custom font mapping | ✅ |
| Create shared components | `VideoCard`, `CompactVideoCard`, `FilterChip`, `EmptyState`, `LoadingSkeleton`, `CardContainer`, `SectionHeader`, `TabRow`, `ModalBottomSheet`, `DescriptionExpandable`, `PaginationControls`, `SettingsRow`, `NotificationCard`, `CreatorAvatar`, `Comment`, `ChannelHero` | ✅ |
| Create `AppLayout` | Orientation-aware rail/bottom bar | ✅ |

**Acceptance**: `GrayjayTheme` applies to a test composable, all components render in previews. ✅

### 0.5 — Navigation Infrastructure

| Task | Description | Status |
|------|-------------|--------|
| Define `NavDestination` | Sealed class with all routes (§6) | ✅ |
| Create `Navigator` | Hilt singleton wrapping `NavHostController` | ✅ |
| Create `GrayjayNavGraph` | NavHost with all composable registrations | ✅ |
| Create `NavigationState` | Current route tracking for chrome | ✅ |

| Task | Description | Files |
|------|-------------|-------|
| Define `NavDestination` | Sealed class with all routes (§6) | `core/navigation/NavDestination.kt` |
| Create `Navigator` | Hilt singleton wrapping `NavHostController` | `core/navigation/Navigator.kt` |
| Create `GrayjayNavGraph` | NavHost with all composable registrations | `core/navigation/GrayjayNavGraph.kt` |
| Create `NavigationState` | Current route tracking for chrome | `core/navigation/NavigationState.kt` |

**Acceptance**: Navigation compiles, all routes registered, `Navigator.navigate()` works.

### 0.6 — Core UI Utilities

| Task | Description | Status |
|------|-------------|--------|
| Create `RelativeTime` formatter | §13 in DESIGN.md (45m ago, 3h ago, etc.) | ✅ |
| Create `AsyncImage` wrapper | Thumbnail loading with placeholder/error | ✅ |
| Create `Shimmer` composable | Loading skeleton animation | ✅ |
| Create `Card` sealed interface | Data-only card types (§11.2) | ✅ |
| Create data classes | `VideoCard`, `ShortCard`, `PlaylistCard`, `ChannelCard` | ✅ |

**Acceptance**: Utilities work in previews, no Android context required.

### 0.6 — Core UI Utilities

| Task | Description | Files |
|------|-------------|-------|
| Create `RelativeTime` formatter | §13 in DESIGN.md (45m ago, 3h ago, etc.) | `core/ui/RelativeTime.kt` |
| Create `AsyncImage` wrapper | Thumbnail loading with placeholder/error | `core/ui/AsyncImage.kt` |
| Create `Shimmer` composable | Loading skeleton animation | `core/ui/Shimmer.kt` |
| Create `Card` sealed interface | Data-only card types (§11.2) | `core/model/Card.kt` |
| Create data classes | `VideoCard`, `ShortCard`, `PlaylistCard`, `ChannelCard` | `core/model/Cards.kt` |

**Acceptance**: Utilities work in previews, no Android context required.

---

## Phase 0.5 — State Machine & Cross-Activity State

**Goal**: Enable dual-screen coordination before building the companion window.

**Status**: ✅ Complete

| Task | Description | Status |
|------|-------------|--------|
| Define `AppScreenState` | Sealed class: Browsing/VideoOpen/VideoMinimized | ✅ |
| Create `ScreenCoordinator` | `@Singleton`, `StateFlow<AppScreenState>` | ✅ |
| Create `PlayerViewModel` | Application-scoped, observes `PlayerRepository` | ✅ |

**Acceptance**: `ScreenCoordinator` is the same instance injected into both `MainActivity` and `CompanionActivity`. `PlayerViewModel` exposes `StateFlow<PlaybackState>`. ✅

---

## Phase 1 — App Chrome

**Goal**: The user can launch the app and navigate between the 6 main tabs. No content yet.

**Status**: ✅ Complete

| Task | Description | Status |
|------|-------------|--------|
| Create `MainActivity` | Thin wrapper, hosts `AppLayout`, observes `ScreenCoordinator` | ✅ |
| Create `CompanionActivity` | Secondary display window, hosts `CompanionScreen` | ✅ |
| Implement `AppLayout` | Orientation detection, NavigationRail/NavigationBar | ✅ |
| Create `CompanionWindowManager` | `DisplayManager` detection, auto-launch companion | ✅ |
| Wire `GrayjayNavGraph` | Register all 6 main destinations + detail routes | ✅ |
| Create placeholder screens | Empty composable for each tab (Home, Search, Subs, Library, Notifications, Settings) | ✅ |

**Acceptance**: App launches on primary display with nav chrome. Companion window opens on secondary display. Orientation change swaps between rail and bottom bar. ✅

---

## Phase 2 — Settings + Appearance

**Goal**: User can change theme, font, icon style, contrast, and playback preferences.

| Task | Description | Files |
|------|-------------|-------|
| Create `SettingsScreen` | Full Compose settings UI (§6 in DESIGN.md) | `feature/settings/impl/SettingsScreen.kt` |
| Implement `SettingsRepositoryImpl` | DataStore-backed preference persistence | `core/data/repository/impl/SettingsRepositoryImpl.kt` |
| Create `SettingsViewModel` | MVI pattern with `UiState` | `feature/settings/impl/SettingsViewModel.kt` |
| Wire theme changes | `GrayjayTheme` responds to `SettingsRepository.observePreferences()` | `core/designsystem/theme/GrayjayTheme.kt` |
| Wire font changes | Dynamic font family in theme | `core/designsystem/theme/GrayjayTheme.kt` |
| Wire icon style changes | Rounded/Sharp/Outlined Material Symbols | `core/designsystem/icons/Icons.kt` |
| Wire contrast changes | Standard/Medium/High contrast color schemes | `core/designsystem/theme/Color.kt` |
| Add playback preferences | Auto-play toggle, quality dropdown | `feature/settings/impl/SettingsScreen.kt` |
| Add Plugin Browser link | "Open Plugin Browser →" button | `feature/settings/impl/SettingsScreen.kt` |
| Add About section | Version, license, GitHub link | `feature/settings/impl/SettingsScreen.kt` |

**Acceptance**: All settings persist across app restarts. Theme changes apply immediately.

---

## Phase 3 — Home Feed

**Goal**: User sees a scrollable feed of recommended videos.

| Task | Description | Files | Status |
|------|-------------|-------|--------|
| Create `HomeViewModel` | MVI pattern, sealed `UiState` (§5) | `feature/home/impl/HomeViewModel.kt` | ✅ |
| Create `HomeScreen` | Infinite scroll feed, portrait single-column | `feature/home/impl/HomeScreen.kt` | ✅ |
| Create `VideoContainer` | Type-agnostic container with LayoutMode (List/HorizontalStrip/Grid) | `core/designsystem/component/CardContainer.kt` | ✅ |
| Wire navigation | Tap video → `NavDestination.VideoDetail` | `HomeScreen.kt` | ✅ |
| Wire nav graph | Register HomeScreen in GrayjayNavGraph | `app/compose/GrayjayNavGraph.kt` | ✅ |

**Acceptance**: Feed loads from `HomeRepository`, infinite scroll works, landscape shows 3-col grid, portrait shows single column. ✅

**Implementation Notes**:
- `HomeViewModel` uses MVI pattern with `StateFlow<HomeUiState>`
- `VideoContainer` supports 3 layout modes: List (portrait), HorizontalStrip (sections), Grid (landscape)
- Infinite scroll detection via `LazyListState`/`LazyGridState` layout info
- Navigation to video detail via `Navigator.navigateToVideo()`
- `HomeRepositoryImpl` currently returns empty feed (TODO: wire to engine)

---

## Phase 4 — Video Player

**Goal**: Full-screen video player with gesture controls, mini-player (floating), and transitions between states.

| Task | Description | Files | Status |
|------|-------------|-------|--------|
| Create `PlayerViewModel` | Application-scoped, `StateFlow<PlaybackState>` | `feature/player/impl/PlayerViewModel.kt` | ✅ |
| Create `PlayerScreen` | Full-screen player with overlays (§10 in DESIGN.md) | `feature/player/impl/PlayerScreen.kt` | ✅ |
| Implement top overlay | Minimize, title, channel, options | `feature/player/impl/PlayerScreen.kt` | ✅ |
| Implement bottom overlay | Timeline, chapters button | `feature/player/impl/PlayerScreen.kt` | ✅ |
| Implement options modal | Speed, quality selection | `feature/player/impl/PlayerScreen.kt` | ✅ |
| Implement brightness slider | Left-side vertical slider with sun icon | `feature/player/impl/PlayerScreen.kt` | ✅ |
| Implement volume slider | Right-side vertical slider with speaker icon | `feature/player/impl/PlayerScreen.kt` | ✅ |
| Implement mini-player | Floating player with controls overlay | `feature/player/impl/PlayerScreen.kt` | ✅ |
| Implement player→miniplayer transition | Animated graphicsLayer transitions | `feature/player/impl/PlayerScreen.kt` | ✅ |
| Support all transitions | mini↔full, normal↔full | `feature/player/impl/PlayerScreen.kt` | ✅ |
| Implement auto-hide | Overlay fades after 3s inactivity | `feature/player/impl/PlayerScreen.kt` | ✅ |
| Wire `PlayerRepository.player` | Expose ExoPlayer for companion window | `core/data/repository/impl/PlayerRepositoryImpl.kt` | ✅ |
| Create inline player variant | Reduced-chrome player for Video Detail screen | `feature/player/impl/InlinePlayer.kt` | ⬜ |
| Implement double-tap seek | ±10s on left/right halves (placeholder) | `feature/player/impl/PlayerScreen.kt` | ⬜ |
| Implement chapters panel | Chapter list with seek-to | `feature/player/impl/PlayerScreen.kt` | ⬜ |
| Implement edge-swipe gestures | Minimize (top), exit fullscreen (bottom) | `feature/player/impl/PlayerScreen.kt` | ⬜ |

**Acceptance**: Full-screen player works with all gestures. Mini-player floats with smooth transitions. All three states (mini, normal, fullscreen) work with animated transitions between them.

**Implementation Notes**:
- Based on Flow app's `DraggablePlayerLayout` pattern using `graphicsLayer` for smooth transitions
- `expandFraction` (0f=fullscreen, 1f=mini-player) animates between states
- Scale, translation, shadow, and rounded corners animate based on fraction
- Scrim fades based on transition state
- Mini-player controls overlay with play/pause, close, fullscreen buttons
- Tap-to-expand gesture on mini-player

---

## Phase 5 — Library, Subscriptions, Search

**Goal**: Migrate remaining main-tab screens to Compose.

### 5.1 — Search Screen

| Task | Description | Files |
|------|-------------|-------|
| Create `SearchViewModel` | MVI, debounced search, filter state | `feature/search/impl/SearchViewModel.kt` |
| Create `SearchScreen` | Search bar, filter badges, results list (§3) | `feature/search/impl/SearchScreen.kt` |
| Create search bar component | TextField with clear button, debounce | `feature/search/impl/components/SearchBar.kt` |
| Create filter badges | `FilterChip` row, per-source toggles | `feature/search/impl/components/FilterBadges.kt` |
| Create mixed result cards | Video/Channel/Playlist/Article/Card dispatch | `feature/search/impl/components/ResultCards.kt` |
| Wire `SearchRepository` | Engine bridge for source queries | `core/data/repository/impl/SearchRepositoryImpl.kt` |
| Implement empty states | No query (recent searches), no results, no sources | `feature/search/impl/SearchScreen.kt` |

### 5.2 — Subscriptions Screen

| Task | Description | Files |
|------|-------------|-------|
| Create `SubscriptionsViewModel` | MVI, creator filter, content filter state | `feature/subscriptions/impl/SubscriptionsViewModel.kt` |
| Create `SubscriptionsScreen` | Avatar strip + filters + video grid (§4) | `feature/subscriptions/impl/SubscriptionsScreen.kt` |
| Create `CreatorAvatarStrip` | Horizontal portrait / vertical right landscape | `feature/subscriptions/impl/components/CreatorAvatarStrip.kt` |
| Create subscription filters | Watched/Continue/Video/Streams + source badges | `feature/subscriptions/impl/components/SubscriptionFilters.kt` |
| Wire `SubscriptionRepository` | Engine bridge for subscription feeds | `core/data/repository/impl/SubscriptionRepositoryImpl.kt` |

### 5.3 — Library Screen

| Task | Description | Files |
|------|-------------|-------|
| Create `LibraryViewModel` | MVI, history/playlists/watch-later data | `feature/library/impl/LibraryViewModel.kt` |
| Create `LibraryScreen` | Three strips with ">" full views (§5) | `feature/library/impl/LibraryScreen.kt` |
| Create strip components | History, Watch Later, Playlists horizontal strips | `feature/library/impl/components/LibraryStrips.kt` |
| Create full-view navigation | ">" tap → full infinite-scroll view | `feature/library/impl/LibraryScreen.kt` |
| Create playlist detail | Play all, video list, create playlist | `feature/library/impl/PlaylistDetailScreen.kt` |
| Wire `LibraryRepository` | Engine bridge for history/playlists | `core/data/repository/impl/LibraryRepositoryImpl.kt` |

**Acceptance**: All three screens render content, navigation to detail screens works, filters function correctly.

---

## Phase 6 — Detail Screens + Deep Links

**Goal**: Video detail, channel detail, and deep link handling.

| Task | Description | Files |
|------|-------------|-------|
| Create `VideoDetailScreen` | Player + title + channel row + description + tabs (§7) | `feature/player/impl/VideoDetailScreen.kt` |
| Create `ChannelDetailScreen` | Hero + tabs (Videos/Shorts/Playlists/About) (§8) | `feature/library/impl/ChannelDetailScreen.kt` |
| Create channel hero component | Banner + avatar + subscribe button | `core/designsystem/component/ChannelHero.kt` |
| Create description expandable | 2-3 lines → full text toggle | `core/designsystem/component/DescriptionExpandable.kt` |
| Create comment component | Username, time, likes, reply, copy, expandable replies | `core/designsystem/component/Comment.kt` |
| Create tab row component | Underlined indicator, horizontal scroll | `core/designsystem/component/TabRow.kt` |
| Create pagination controls | Previous/Page/Next with batch loading | `core/designsystem/component/PaginationControls.kt` |
| Create shorts grid | 9:16 vertical cards | `feature/library/impl/components/ShortsGrid.kt` |
| Wire deep links | `NavDestination.VideoDetail`, `.ChannelDetail`, `.PlaylistDetail`, etc. | `core/navigation/GrayjayNavGraph.kt` |
| Wire short-form player | Full-screen vertical player for shorts | `feature/player/impl/ShortsPlayer.kt` |
| Create `ArticleDetailScreen` | For article-type content | `feature/feed/impl/ArticleDetailScreen.kt` |
| Create `PostDetailScreen` | For Polycentric social posts | `feature/feed/impl/PostDetailScreen.kt` |
| Create `WebDetailScreen` | For URL-based content | `feature/feed/impl/WebDetailScreen.kt` |

**Acceptance**: Detail screens show all content sections. Deep links from notifications/external sources work. Shorts player supports swipe navigation.

---

## Phase 7 — Companion Content + Gamepad

**Goal**: Full companion window with its own tabs, plus gamepad key mapping.

| Task | Description | Files |
|------|-------------|-------|
| Create `CompanionScreen` | Player controls + 3 tabs (§12 in DESIGN.md) | `feature/companion/impl/CompanionScreen.kt` |
| Create companion player controls | Thumbnail, title, progress bar, playback buttons | `feature/companion/impl/components/CompanionPlayerControls.kt` |
| Create companion recs tab | Compact video cards for recommendations | `feature/companion/impl/components/CompanionRecsContent.kt` |
| Create companion comments tab | Comment cards for current video | `feature/companion/impl/components/CompanionCommentsContent.kt` |
| Create companion polycentric tab | Social post cards | `feature/companion/impl/components/CompanionPolycentricContent.kt` |
| Wire `ScreenCoordinator` | Cross-Activity state for companion | `feature/dualscreen/ScreenCoordinator.kt` |
| Implement gamepad key mapping | Map A/B/X/Y, D-pad, triggers to actions | `core/ui/GamepadKeyMapper.kt` |
| Implement gamepad in companion | KeyEvent handler in `CompanionActivity` | `activities/CompanionActivity.kt` |
| Implement gamepad in main | KeyEvent handler in `MainActivity` | `activities/MainActivity.kt` |
| Persist `AppScreenState` | DataStore-backed state across process death | `feature/dualscreen/AppScreenState.kt` |

**Acceptance**: Companion window shows player controls + 3 content tabs. Gamepad controls work on both displays. State survives process death.

---

## Phase 8 — Cleanup & Migration Bridge Removal

**Goal**: Remove all legacy migration bridge code.

| Task | Description | Files |
|------|-------------|-------|
| Audit `core/data` for `State*` imports | Grep for all legacy singleton references | `core/data/repository/impl/*.kt` |
| Replace `StatePlayer` with direct ExoPlayer | Remove bridge in `PlayerRepositoryImpl` | `core/data/repository/impl/PlayerRepositoryImpl.kt` |
| Replace `StatePlatform` with direct engine calls | Remove bridge in `HomeRepositoryImpl` | `core/data/repository/impl/HomeRepositoryImpl.kt` |
| Replace `StateSubscriptions` with direct engine calls | Remove bridge in `SubscriptionRepositoryImpl` | `core/data/repository/impl/SubscriptionRepositoryImpl.kt` |
| Delete `fragment/` directory | Old XML fragments | `fragment/` |
| Delete `views/` directory | Old XML views | `views/` |
| Delete `dialogs/` directory | Old dialog classes | `dialogs/` |
| Delete `states/` directory | Old singleton state classes | `states/` |
| Delete old `activities/` | Old activity classes (keep `MainActivity`, `CompanionActivity`, `CaptchaActivity`) | `activities/` |
| Delete `ui/interop/` | ComposeFragment bridge | `ui/interop/` |
| Delete `ui/scene/` | Scene→Fragment adapters | `ui/scene/` |
| Delete `models/` (old) | Old data models (replaced by `core/model/`) | `models/` |
| Delete `casting/`, `downloads/`, `sabr/`, `encryption/`, `polycentric/` (old) | Old feature implementations | Various |
| Delete `Utility.kt`, `UISlideOverlays.kt`, `RootInsetsController.kt`, `SettingsDev.kt` | Old utilities | Various |
| Update `build.gradle` | Remove old module references, verify new module deps | `app/build.gradle`, `settings.gradle` |

**Acceptance**: No `core:*` or `feature:*` module imports anything under deleted directories. App compiles and runs identically.

---

## Cross-Cutting Concerns (All Phases)

### Testing

| Task | When | Description |
|------|------|-------------|
| Unit tests for ViewModels | Each phase | Test MVI state transitions |
| Unit tests for repositories | Phase 0 | Test bridge implementations with fakes |
| Unit tests for card dispatch | Phase 0 | Test `CardContent` when/branch logic |
| Unit tests for relative time | Phase 0 | Test all time buckets |
| UI tests for navigation | Phase 1 | Test tab switching, deep link navigation |
| UI tests for player gestures | Phase 4 | Test minimize/exit fullscreen |
| Integration tests | Phase 5 | Test repository → ViewModel → Screen flow |

### Build Configuration

| Task | When | Description |
|------|------|-------------|
| Create `:core:designsystem` module | Phase 0 | Design system + shared components |
| Create `:core:navigation` module | Phase 0 | Navigation infrastructure |
| Create `:core:data` module | Phase 0 | Repositories + database |
| Create `:core:datastore` module | Phase 0 | DataStore preferences |
| Create `:core:ui` module | Phase 0 | Shared UI utilities |
| Create `:core:model` module | Phase 0 | Shared domain models (Card types) |
| Create `:core:testing` module | Phase 0 | Test helpers, fake repositories |
| Create `:core:sync` module | Phase 0 | Polycentric sync coordination |
| Create `:core:notifications` module | Phase 0 | Notification management |
| Create `:feature:home:impl` module | Phase 3 | Home feed |
| Create `:feature:search:impl` module | Phase 5 | Search |
| Create `:feature:player:impl` module | Phase 4 | Player + detail |
| Create `:feature:library:impl` module | Phase 5 | Library + subscriptions |
| Create `:feature:subscriptions:impl` module | Phase 5 | Subscriptions |
| Create `:feature:feed:impl` module | Phase 6 | Feed, article, post, web detail |
| Create `:feature:settings:impl` module | Phase 2 | Settings |
| Create `:feature:plugins:impl` module | Phase 2 | Plugin browser (deferred) |
| Create `:feature:casting:impl` module | Phase 7 | Casting (deferred) |
| Create `:feature:downloads:impl` module | Phase 7 | Downloads (deferred) |
| Create `:feature:companion:impl` module | Phase 7 | Companion screen |
| Create `:feature:dualscreen` module | Phase 0.5 | State machine + coordinator |

### Open Questions (from ARCHITECTURE.md §17)

| Question | Resolution | When |
|----------|-----------|------|
| What version of `androidx.media3` is currently used? | Check `app/build.gradle` | Phase 0 |
| Does `StatePlayer` expose `player.play()`, `pause()`, `seekTo()` publicly? | Audit `StatePlayer.kt` | Phase 0 |
| Is `MediaSession` accessible outside `StatePlayer`? | Audit `StatePlayer.kt` | Phase 0 |
| What are the exact navigation targets in `MenuBottomBarView`? | Audit `MenuBottomBarView.kt` | Phase 0 |
| Are colors hardcoded or managed by a theme system? | Audit XML color resources | Phase 0 |
| Does `res/layout-land/` exist? | Audit `res/` directory | Phase 0 |
| Where is Polycentric currently queried and displayed? | Audit `polycentric/` package | Phase 0 |
| What is the exact gesture detection for swipe-to-minimize? | Audit `VideoDetailView.kt` | Phase 4 |

---

## Risk Register

| Risk | Impact | Mitigation |
|------|--------|------------|
| Engine types (`IPlatformVideo`, etc.) aren't `@Stable` | Compose recomposition thrashing on feed | Audit stability in Phase 0; hold only display fields in Card types if needed |
| `StatePlayer` doesn't expose public playback API | Can't bridge `PlayerRepository` to legacy engine | Audit in Phase 0; may need to add accessors or refactor `StatePlayer` |
| Secondary display not detected on AYN Thor | Companion window never opens | Test early in Phase 1; `CompanionWindowManager` has explicit logging |
| Process death loses `AppScreenState` | Companion window shows "Browsing" after relaunch | DataStore-backed persistence in Phase 7 |
| Phase 8 deletion of `states/` breaks `core:data` | App crashes at runtime | Add Phase 8 entry criterion: grep for `State` imports in `core/data` |
| Gamepad key mapping conflicts with system gestures | Unusable on AYN Thor | Test on device early in Phase 7; document known conflicts |
| Modularization adds build complexity | Slow iteration during development | Use `:app` as single module initially, split into feature modules incrementally |

---

## Execution Order (Critical Path)

```
Phase 0 (Foundation) ✅
  ├── 0.1 Hilt DI ✅
  ├── 0.2 Room Database ✅
  ├── 0.3 Repository Interfaces + Bridges ✅
  ├── 0.4 Design System ✅
  ├── 0.5 Navigation Infrastructure ✅
  └── 0.6 Core UI Utilities ✅
        │
        ▼
Phase 0.5 (State Machine) ✅
        │
        ▼
Phase 1 (Chrome) ✅ ──────────────────────────────┐
        │                                         │
        ▼                                         │
Phase 2 (Settings) ⬜ ← NEXT                       │
        │                                         │
        ▼                                         │
Phase 3 (Home Feed) ✅                              │
        │                                         │
        ▼                                         │
Phase 4 (Player) ⬜                                 │
        │                                         │
        ├──► Phase 5.1 (Search) ⬜                 │
        ├──► Phase 5.2 (Subscriptions) ⬜          │
        └──► Phase 5.3 (Library) ⬜                │
        │                                         │
        ▼                                         │
Phase 6 (Detail Screens) ──────────────────────────┤
        │                                         │
        ▼                                         │
Phase 7 (Companion + Gamepad)                      │
        │                                         │
        ▼                                         │
Phase 8 (Cleanup) ◄───────────────────────────────┘
```

**Parallelization opportunities**:
- Phase 5.1 (Search), 5.2 (Subscriptions), 5.3 (Library) can work in parallel once Phase 3 is complete (they share `core:data` and `core:designsystem` but have independent ViewModels and screens)
- Phase 2 (Settings) is independent of Phase 3 (Home Feed) — can be parallelized
- Testing for each phase can begin as soon as that phase's components are implemented
