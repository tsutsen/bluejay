# Feed Video Click Issue - Analysis & Plan

## Problem
When clicking a video card in the Compose feed:
1. App goes fullscreen (status bar hides)
2. Video starts playing somewhere in the background
3. **No video detail page opens** (no title, description, comments, etc.)

## Root Cause Analysis

### What's happening in FeedFragment
```kotlin
// Current code in FeedFragment.kt onItemClicked:
ma._fragVideoDetail.closeVideoDetails()
ma._fragVideoDetail.onShown(content, false)
ma._fragVideoDetail.maximizeVideoDetail(true)
```

### What happens when `onShown(content, false)` is called:
1. `onShown` → calls `onShownWithView(parameter, isBack)` (VideoDetailFragment.kt:251)
2. `onShownWithView` checks: `if (parameter is IPlatformVideo)` → calls `_viewDetail?.setVideoOverview(parameter)`
3. **If `_viewDetail` is null, nothing happens** — no error, no video loaded

### Why `_viewDetail` might be null
- `_viewDetail` is set in `onCreateMainView` (VideoDetailFragment.kt:327)
- `onCreateMainView` is called when the fragment's main view is created
- The fragment is added to the fragment manager at startup (MainActivity.kt:508) via `replace(R.id.fragment_overlay, _fragVideoDetail)`
- But `replace` may not trigger `onCreateMainView` until the fragment is actually shown

### What the original HomeFragment does differently
```kotlin
// ContentFeedView.kt onContentClicked:
if (StatePlayer.instance.hasQueue) {
    StatePlayer.instance.insertToQueue(content, true);
} else {
    fragment.navigate<VideoDetailFragment>(content).maximizeVideoDetail();
}
```

The original uses `fragment.navigate<VideoDetailFragment>(content)` which:
1. Uses reified generic `navigate<T>()` → calls `requireFragment<T>()` → calls `getFragment<T>()`
2. `getFragment<VideoDetailFragment>()` returns the pre-existing `_fragVideoDetail` instance
3. Then calls `MainActivity.navigate(fragment, parameter)` which:
   - Makes `_fragContainerVideoDetail` visible
   - Checks state and calls `maximizeVideoDetail()` if MINIMIZED/CLOSED
   - Calls `segment.onShown(parameter, isBack)`
   - **Returns early** (doesn't go through normal fragment navigation)

### Key insight
The original code path goes through `MainActivity.navigate()` which handles the VideoDetailFragment specially. But it still calls `onShown` on the fragment. So the issue is likely that `_viewDetail` is null.

But wait — the original HomeFragment works fine. So `_viewDetail` must be non-null when the original code runs. Why?

**Hypothesis**: When the HomeFragment is shown (as the main tab), its `onCreateMainView` has been called, and the VideoDetailFragment's `onCreateMainView` has also been called because it was added to the fragment manager at startup. But in the Compose FeedFragment, something is different.

**Alternative hypothesis**: The `closeVideoDetails()` call is clearing `_viewDetail` or causing issues.

Let me check `closeVideoDetails()`:
```kotlin
fun closeVideoDetails() {
    _viewDetail?.setFullscreen(false);
    state = State.CLOSED;
    _viewDetail?.onStop();
    close();  // This might be the issue!
    StatePlayer.instance.clearQueue();
    StatePlayer.instance.setPlayerClosed();
}
```

The `close()` call might be destroying the fragment's view, which would set `_viewDetail` to null.

### The `close()` function
Need to check what `close()` does — it might be calling `onDestroyMainView()` which sets `_view = null` and `_viewDetail = null`.

## Files Involved

### 1. FeedFragment.kt (compose/feed/)
- **Problem**: Calls `closeVideoDetails()` which may destroy the fragment's view
- **Fix**: Don't call `closeVideoDetails()` — just call `onShown` directly

### 2. VideoDetailFragment.kt (fragment/mainactivity/main/)
- **Problem**: `close()` may destroy `_viewDetail`
- **Problem**: `onShownWithView` doesn't handle null `_viewDetail` for `IPlatformVideo` type
- **Fix**: Store parameter in a field if `_viewDetail` is null (like it does for String/UrlVideoWithTime)

### 3. MainActivity.kt (activities/)
- **Problem**: `navigate()` returns early after `onShown`, not calling `maximizeVideoDetail()`
- **Current FeedFragment**: Calls `maximizeVideoDetail(true)` after `onShown` — this should work

## Proposed Fix (in order of likelihood)

### Fix 1: Remove `closeVideoDetails()` call (simplest)
In FeedFragment.kt, remove the `closeVideoDetails()` call:
```kotlin
is IPlatformVideo -> {
    ma._fragVideoDetail.onShown(content, false)
    ma._fragVideoDetail.maximizeVideoDetail(true)
}
```

### Fix 2: Use `MainActivity.navigate()` instead of direct `onShown`
This ensures the fragment is properly initialized:
```kotlin
is IPlatformVideo -> {
    ma.navigate(ma._fragVideoDetail, content, true, false)
}
```

### Fix 3: Handle null `_viewDetail` in VideoDetailFragment
In `onShownWithView`, store the parameter if `_viewDetail` is null:
```kotlin
else if (parameter is IPlatformVideo) {
    if (_viewDetail == null) {
        _loadVideoOnCreate = parameter
    } else {
        _viewDetail.setVideoOverview(parameter)
    }
}
```
And in `onCreateMainView`, after setting `_viewDetail`:
```kotlin
_viewDetail = _view!!.findViewById<VideoDetailView>(R.id.fragview_videodetail).also {
    // ... existing setup ...
    if (this@VideoDetailFragment._loadVideoOnCreate != null) {
        it.setVideoOverview(_loadVideoOnCreate!!)
        _loadVideoOnCreate = null
    }
}
```

### Fix 4: Check if the issue is in `setVideoOverview`
The video might be loading but the detail UI (title, description) isn't showing.
Check `VideoDetailView.setVideoOverview` to ensure it populates the UI.

## Debug Steps
1. Add logging to `onShownWithView` to check if `_viewDetail` is null
2. Add logging to `onCreateMainView` to verify it's being called
3. Add logging to `setVideoOverview` to verify it's being called
4. Check if `_view` is null in `onShownWithView`

## Definition of Done ✅
- ✅ Tapping a video card in the Compose feed opens the VideoDetailFragment
- ✅ Video detail page shows title, description, comments, etc.
- ✅ Video loads and plays within the detail page
- ✅ Non-video content (playlists, posts, articles) still opens correctly

## Resolution
The issue was that `FeedFragment` called `ma._fragVideoDetail.onShown(content, false)` directly, bypassing `MainActivity.navigate()` which is responsible for making the `fragment_overlay` container visible. The fix uses `ma.navigate(ma._fragVideoDetail, content, true, false)` which properly handles container visibility and maximization.
