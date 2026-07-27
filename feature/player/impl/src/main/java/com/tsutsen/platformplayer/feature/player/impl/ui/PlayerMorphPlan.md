# Player Morph — Implementation Plan (Tracking)

**Branch:** `player-morph`
**Goal:** Replace instant composable swaps between NORMAL/COMPACT, FLOATING, and FULLSCREEN with continuous morph transitions.

---

## Status: Phase 1 Complete ✅

The following has been implemented and committed (commit `7371a07c`):

- [x] `computeVideoLayout()` — pure geometry function
- [x] `UnifiedPlayerContent` — single persistent composable
- [x] Weighted-alpha cross-fade for controls
- [x] Details panel fade/translate + pointer-input gating
- [x] Nested scroll connection gated during transitions
- [x] Scaffold bars forced true during active transitions
- [x] Two separate gesture subtrees (scaffold + bespoke mini Box)
- [x] `PlayerScreen.kt` refactored: dead animated values removed, `miniProgress`/`fullscreenProgress` floats added
- [x] NORMAL/COMPACT/FLOATING flow through unified composable
- [x] FULLSCREEN still on old discrete `FullscreenPlayerContent` path

---

## Remaining Work (Phase 2)

### Step 6: Wire fullscreenProgress into unified path

Currently `fullscreenProgress` is held at `0f` in the `UnifiedPlayerContent` call. Extend the geometry and controls to include it:

**Files to change:** `PlayerScreen.kt` (remove the `if (isFullscreenAnim.value)` branch), `UnifiedPlayerContent.kt`

**Changes:**
1. Remove the `if (isFullscreenAnim.value) { FullscreenPlayerContent(...) } else { UnifiedPlayerContent(...) }` branch in `PlayerScreen.kt`.
2. Always call `UnifiedPlayerContent` and pass the real `fullscreenProgress` value.
3. In `UnifiedPlayerContent`:
   - The geometry already handles `fullscreenProgress` (nested lerp through NORMAL→FULLSCREEN→FLOATING).
   - The `fullscreenBarAlpha` weight already accounts for it: `fullscreenProgress * (1f - miniProgress)`.
   - The `resolvedShowTopBar`/`resolvedShowBottomBar` logic already handles fullscreen territory.
   - The `disableVerticalDrag` already returns `false` when fullscreen is settled.
   - The nested scroll connection already disables when either progress is non-zero.

**Key concern:** The `playerHeightPx` collapsing logic in `PlayerScreen.kt` is NORMAL-only. When `fullscreenProgress > 0`, the video should fill `containerHeight`, so the collapsing height math is irrelevant. The geometry function already handles this (NORMAL height → FULLSCREEN containerHeight lerp).

### Step 7: Verify MINI↔FULLSCREEN geometry

This is the transition most exposed to the nested-lerp approximation (§3 of the original plan).

**Action:** Create a synthetic debug overlay or unit test that sweeps `(miniProgress, fullscreenProgress)` over `[0,1]×[0,1]` and inspects the resulting rect path.

If the sag (path through NORMAL as implicit third anchor) is visible/undesirable, replace with an explicit 3-anchor barycentric blend:

```kotlin
// Option A: Barycentric blend (if sag is unacceptable)
fun computeVideoLayoutBarycentric(
    miniProgress: Float, fullscreenProgress: Float, ...
): VideoLayout {
    val w = miniProgress * fullscreenProgress * floatingWidth +
            (1f - miniProgress) * fullscreenProgress * fullscreenWidth +
            (1f - miniProgress) * (1f - fullscreenProgress) * normalWidth
    // ... same for height, x, y
}

// Option B: Keep nested lerp (acceptable if visually smooth)
// Current implementation
```

### Step 8: Clean up old composables

Once Steps 6–7 are verified across all four transition cases:

- [ ] Remove `FullscreenPlayerContent.kt` (no longer called)
- [ ] Remove `FloatingPlayerContent.kt` (replaced by mini overlay in UnifiedPlayerContent)
- [ ] Remove `WindowedPlayerContent.kt` (replaced by UnifiedPlayerContent)
- [ ] Remove `computePlayerMode()` from `PlayerMode.kt` (no longer dispatches on it)
- [ ] Remove the `PlayerMode` enum if no other consumers
- [ ] Remove the `fullscreenScrimAlpha` Box from `PlayerScreen.kt` (fullscreen overlay handled by full-screen geometry now)

### Verification checklist

- [ ] NORMAL ↔ FLOATING: continuous scale, move to/from corner, corner radius change, details fade/slide, weighted control cross-fade — no snap
- [ ] NORMAL ↔ FULLSCREEN: continuous scale to fill, details fade out, controls cross-fade
- [ ] MINI ↔ FULLSCREEN: one continuous motion (both progresses animate together)
- [ ] Drag + corner snap on mini still works and shares the same offset the morph uses
- [ ] COMPACT row still appears when collapsed in windowed mode and participates correctly in weighted cross-fade
- [ ] One `PlayerVideoSurface` instance for the lifetime of a loaded session — no remount on any mode change
- [ ] No regression in play/pause, seek, brightness/volume gestures, options/chapters modals, system UI hide/show, or orientation auto-rotate
- [ ] Scroll position of details preserved across a minimize/restore round trip

---

## Open Items

| Item | Status | Notes |
|------|--------|-------|
| Exact `miniProgress` threshold for drag activation | **Done** | Set to `0.98f` (`MINI_DRAG_THRESHOLD`) |
| Whether nested-lerp geometry is acceptable | **Pending** | Needs synthetic sweep (§3) |
| Whether `PlayerControlsScaffold`/`PlayerVideoSurface`/`PlayerMode.kt` impose discrete-mode assumptions | **Resolved** | Confirmed safe — scaffold gestures are conditionally attached, surface accepts modifier param |
| `pointerInput(isDragging)` key re-arming after MINI→FULLSCREEN→MINI round trip | **Pending** | The key is `miniProgress, isDraggingMiniPlayer` — should re-arm correctly since `miniProgress` goes back to 1f. Verify during manual testing. |

---

## File Inventory

| File | Status | Purpose |
|------|--------|---------|
| `state/PlayerScreen.kt` | Modified | Hoists state, computes geometry, dispatches to unified or fullscreen |
| `ui/UnifiedPlayerContent.kt` | New | Single persistent composable for NORMAL/COMPACT/FLOATING (and soon FULLSCREEN) |
| `ui/VideoLayout.kt` | New | Pure `computeVideoLayout()` + `VideoLayout` data class |
| `ui/modes/FullscreenPlayerContent.kt` | **To delete** | Phase 1 fallback, removed in Step 8 |
| `ui/modes/FloatingPlayerContent.kt` | **To delete** | Replaced by mini overlay in UnifiedPlayerContent |
| `ui/modes/WindowedPlayerContent.kt` | **To delete** | Replaced by UnifiedPlayerContent |
| `state/PlayerMode.kt` | **To delete** (if unused) | Enum + `computePlayerMode()` no longer dispatches |
