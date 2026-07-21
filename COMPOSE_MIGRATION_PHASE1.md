# Phase 1 — Chrome + Settings: Plan

**Goal**: Establish live theme reactivity across the app's chrome (bottom nav, top bar) and Settings, proving that theme changes propagate without `Activity.recreate()`.

---

## 1. Settings — Hierarchical Navigation

### Current State
- Flat `SettingsFragment` with 20+ groups on one screen
- Schema-driven via `@FormField` annotations on `Settings.kt`
- Uses `FieldForm` custom view for rendering

### Target Architecture
```
SettingsHub (main screen)
├── Appearance
│   ├── Theme (Auto/Light/Dark)
│   ├── Color Scheme (Dynamic/Custom/Preset)
│   ├── Typography (Font choice)
│   ├── Icon Style (Rounded/Sharp/Outlined)
│   └── Contrast (Standard/Medium/High)
├── Feed & Content
│   ├── Home Feed
│   ├── Search
│   ├── Channels
│   ├── Subscriptions
│   └── Feed
├── Player
│   ├── Playback
│   ├── Downloads
│   ├── Gestures
│   └── Casting
├── Privacy & Data
│   ├── Privacy
│   ├── Data Management
│   └── Backup & Restore
├── Sync & Identity
│   ├── Synchronization
│   └── Polycentric
├── General
│   ├── Language
│   ├── Tabs
│   ├── Link Handling
│   └── FAQ / Issues
└── About
    ├── Version
    └── License / Payment
```

### Implementation Approach
- **SettingsHubFragment**: Compose `LazyColumn` of category cards → navigates to sub-screens
- **Sub-screens**: Each category gets its own `MainFragment` + Compose content
- **State bridge**: `rememberComposeThemeState()` reads from DataStore, `applyThemeModeToLegacy()` writes to AppCompat delegate
- **Backward compatibility**: Old `SettingsFragment` remains as fallback; new nav takes precedence

### Sub-screens to Create (7 total)
| # | Screen | Status | Priority |
|---|--------|--------|----------|
| 1 | Appearance | ✅ Draft created | Done |
| 2 | Feed & Content | ⬜ TODO | High |
| 3 | Player | ⬜ TODO | High |
| 4 | Privacy & Data | ⬜ TODO | Medium |
| 5 | Sync & Identity | ⬜ TODO | Medium |
| 6 | General | ⬜ TODO | Low |
| 7 | About | ⬜ TODO | Low |

### Open Questions
- Should sub-screens use `ComposeFragment` base or extend `MainFragment` directly? (Currently using `MainFragment` with inline ComposeView)
- How to handle `@FormField`-driven screens? Should we keep the annotation-driven rendering or hand-write each sub-screen?
- Should SettingsHub replace `SettingsFragment` entirely, or coexist?

---

## 2. Top Bar System

### Current State
- 6 top bar fragments: `GeneralTopBarFragment`, `SearchTopBarFragment`, `NavigationTopBarFragment`, `ImportTopBarFragment`, `AddTopBarFragment`, `FilesTopBarFragment`
- Each inflates an XML layout and manages its own views
- `GeneralTopBarFragment`: app icon, search button, cast button, notifications
- `SearchTopBarFragment`: EditText with IME handling, clear/filter buttons
- Others: simpler variants

### Target Architecture
- Shared `TopAppBar` composable with configurable slots
- Each top bar variant becomes a composable function, not a Fragment
- Top bar state (search text, notification count) managed via `ViewModel` + `StateFlow`
- Top bar hides/shows based on current screen

### Implementation Order
1. **GeneralTopBarFragment** → Compose (most complex, has cast + notifications)
2. **SearchTopBarFragment** → Compose (IME handling is the tricky part)
3. **Other top bars** → Compose (simpler variants)

### Open Questions
- How to handle IME (keyboard) showing/hiding in Compose?
- CastButton is a custom Android View — use `AndroidView` interop?
- Notification count comes from `StateAnnouncement` — how to bridge to Compose state?

---

## 3. Bottom Navigation

### Current State
- `MenuBottomBarFragment` (659 LOC)
- `MenuBottomBarView` (inner class, `LinearLayout`)
- Animated "more" overlay with `ObjectAnimator`/`AnimatorSet`
- RecyclerView for overflow buttons
- Deeply coupled to `StateApp` (airplane mode, privacy mode, navigation events)

### Target Architecture
- `BottomNavigation` composable with `NavigationRail` for expanded width
- "More" overlay → `ModalBottomSheet` or `DropdownMenu`
- Animation: Compose `tween()` / `snap()` transitions instead of `ObjectAnimator`
- State bridged from `StateApp` via a thin adapter `ViewModel`

### Complexity: HIGH
- Animation system needs re-implementation (not 1:1 translation)
- Deep coupling to `StateApp` and `MainActivity`
- RecyclerView for overflow → `LazyColumn`

### Decision
- **Defer to end of Phase 1** — after Settings and Top Bar are working
- The animation work is the biggest risk; better to have a simple bottom nav first

---

## 4. Live Theme Propagation

### Requirement
Theme changes must propagate to all Compose screens without `Activity.recreate()`.

### Mechanism
1. User changes theme in Settings → `applyThemeModeToLegacy()` calls `AppCompatDelegate.setDefaultNightMode()`
2. `rememberComposeThemeState()` in each Compose screen reads from DataStore
3. Compose recomposes affected screens automatically
4. XML screens also update via `AppCompatDelegate` (no Compose needed)

### Verification
- Change theme in Settings → verify chrome (top bar, bottom nav) updates
- Verify other Compose screens (if any) update
- Verify XML screens also update (they already do via AppCompat)
- No `Activity.recreate()` in the theme change path

---

## 5. Exit Criteria

1. ✅ Settings Hub navigable from bottom bar
2. ✅ Appearance sub-screen functional with theme picker
3. ⬜ All 7 Settings sub-screens created
4. ⬜ Top bar system ported (at least General + Search)
5. ⬜ Bottom nav ported (simple version, animations deferred)
6. ⬜ Theme changes propagate live across chrome + settings
7. ⬜ No `Activity.recreate()` in theme change path
8. ⬜ Back navigation works correctly from all sub-screens

---

## 6. Sequencing

```
Week 1: Settings Hub + Appearance (DONE)
Week 2: Remaining Settings sub-screens (Feed, Player, Privacy, Sync, General, About)
Week 3: Top Bar system (General → Search → others)
Week 4: Bottom nav (simple version) + theme propagation verification
```

---

## 7. Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Animation re-implementation (bottom nav) | High | Defer to end; use simple version first |
| IME handling in Compose | Medium | Use `WindowInsets` API; test thoroughly |
| CastButton interop | Low | Use `AndroidView` wrapper |
| StateApp coupling | Medium | Thin adapter ViewModel per screen |
| Regression in existing XML screens | High | No changes to XML code; Compose is additive |
