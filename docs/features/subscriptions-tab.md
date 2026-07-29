# Subscriptions Tab Implementation

## Overview

Implemented the Subscriptions tab as specified in DESIGN.md §4, replacing the placeholder with a fully functional Compose screen.

## Files Created

### 1. `feature/subscriptions/impl/src/main/java/com/tsutsen/platformplayer/feature/subscriptions/impl/SubscriptionsViewModel.kt`

**Purpose**: Manages the Subscriptions tab state, including:
- Creator list from `StateSubscriptions`
- Filtered video feed
- Filter state (creator, watched/continue, video/streams, source)

**Key Features**:
- Sealed `SubscriptionsUiState` with Loading/Success/Error states
- `SubscriptionCreator` data class for avatar strip
- Methods: `loadCreators()`, `loadFeed()`, `refresh()`, `selectCreator()`, `toggleWatched()`, `toggleContinue()`, `toggleVideo()`, `toggleStreams()`, `toggleSourceFilter()`
- Uses `StateSubscriptions.instance` for data access (consistent with `HomeViewModel` pattern)

### 2. `feature/subscriptions/impl/src/main/java/com/tsutsen/platformplayer/feature/subscriptions/impl/SubscriptionsScreen.kt`

**Purpose**: Full Compose UI for the Subscriptions tab with responsive layout.

**Key Components**:
- **CreatorAvatarStripHorizontal**: Horizontal scroll for portrait (48dp circles, 8dp gap)
- **CreatorAvatarStripVertical**: Vertical scroll for landscape right side (72dp wide)
- **CreatorAvatar**: Reusable avatar with selection ring, new content indicator
- **SubscriptionFilterBadges**: Filter chips for Watched/Continue/Video/Streams/sources
- **SubscriptionsVideoFeed**: Single-column LazyColumn for portrait
- **SubscriptionsVideoGrid**: 3-column LazyVerticalGrid for landscape

**Responsive Behavior**:
- **Portrait**: Creators on top → Filters → Single-column video feed
- **Landscape**: Filters + Video grid (center) → Creator strip (right side)

## Files Modified

### `app/src/main/java/com/tsutsen/platformplayer/compose/BluejayNavGraph.kt`

> **Note:** Previously referenced `PlatformPlayerActivity.kt` which no longer exists. Navigation registration now lives in `BluejayNavGraph.kt` / `MainActivity.kt`.

**Changes**:
1. Added import: `import com.tsutsen.platformplayer.compose.subscriptions.SubscriptionsScreen`
2. Replaced placeholder: `SubscriptionsScene(n) = placeholder(n, "Subscriptions")` → `SubscriptionsScene(n) = SubscriptionsScreen(navigator = n)`

## Architecture

### Data Flow

```
StateSubscriptions (singleton)
    ↓ getSubscriptions()
SubscriptionCreator list → Avatar strip
    ↓
StateSubscriptions.getGlobalSubscriptionFeed()
    ↓ IPager<IPlatformContent>
allContent → Filter by active filters
    ↓
FeedItem list → Video feed/grid
```

### Filter Logic

- **Creator filter**: `activeCreatorId` - filters content by channel URL
- **Watched/Continue**: Mutually exclusive (toggle pattern)
- **Video/Streams**: Mutually exclusive (toggle pattern)
- **Source filters**: Independent, combinable (YouTube, SoundCloud, etc.)

### Current Filter Defaults

- Watched: ON
- Continue: OFF
- Video: ON
- Streams: OFF
- Sources: All active (empty map = no filtering)

## TODO / Future Enhancements

### 1. Watch State Integration
- Connect to `HistoryDao` for actual watched/continue filtering
- `HistoryDao.observeContinueWatching()` returns items where `lastPositionMs > 0`
- Need to track watch percentage (≥95% = watched, 1s-95% = continue)

### 2. New Content Indicator
- Compute `hasNewContent` from `Subscription.lastVideo` timestamps
- Compare with last-seen timestamps per subscription

### 3. Source Filter Population
- Dynamically populate source filters from active plugins
- Get source IDs from `StatePlatform.instance.getAvailableClients()`

### 4. Pagination
- Implement load-more for subscription feed
- Use `feedPager.hasMorePages()` to detect end of feed

### 5. Repository Pattern
- Wire `SubscriptionRepositoryImpl` to bridge `StateSubscriptions` → Repository
- Currently using `StateSubscriptions.instance` directly (consistent with `HomeViewModel`)

## Testing

### Manual Testing Checklist

- [ ] Open Subscriptions tab (should load creators and feed)
- [ ] Select/deselect creators (avatar ring should update)
- [ ] Toggle Watched/Continue filters (mutually exclusive)
- [ ] Toggle Video/Streams filters (mutually exclusive)
- [ ] Select "All" creator (should show all content)
- [ ] Select specific creator (should filter to that creator)
- [ ] Portrait vs Landscape layout (responsive behavior)
- [ ] Empty state (no subscriptions)
- [ ] Loading state (initial load)
- [ ] Error state (network failure)

### Expected Behavior

1. **First Launch**: Shows empty state "No subscriptions yet"
2. **With Subscriptions**: Shows creator avatars + filtered video feed
3. **Selecting Creator**: Filters feed to that creator's content
4. **Toggling Filters**: Updates feed in real-time
5. **Portrait**: Single column, creators on top
6. **Landscape**: 3-column grid, creators on right

## Design Compliance

### DESIGN.md §4 Requirements

| Requirement | Status |
|-------------|--------|
| Creator Avatar Strip (horizontal portrait) | ✅ Implemented |
| Creator Avatar Strip (vertical landscape) | ✅ Implemented |
| Filter Badges (Watched, Continue, Video, Streams) | ✅ Implemented |
| Source Filters | ✅ Placeholder (dynamic population TODO) |
| Video Feed (single column portrait) | ✅ Implemented |
| Video Feed (3-col grid landscape) | ✅ Implemented |
| Responsive layout | ✅ Implemented |
| Loading/Empty/Error states | ✅ Implemented |

### Material Design

- Avatar size: 48dp (unselected), 52dp (selected) ✅
- Gap: 8dp between avatars ✅
- Selection ring: Accent color ✅
- New content indicator: Small dot ✅
- Filter chips: Standard Material 3 ✅

## Next Steps

1. **Connect to HistoryDao** for actual watch state filtering
2. **Implement pagination** for load-more
3. **Populate source filters** dynamically from plugins
4. **Add pull-to-refresh** for feed refresh
5. **Wire to SubscriptionRepository** for proper DI

## References

- DESIGN.md §4: Subscriptions Screen
- `feature/home/impl/.../HomeViewModel.kt`: Pattern reference
- `feature/home/impl/.../HomeScreen.kt`: Pattern reference
- StateSubscriptions.kt: Data source
- SubscriptionEntity.kt: Database schema
- SubscriptionDao.kt: Database queries
