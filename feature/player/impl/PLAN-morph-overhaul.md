# Player Morph Overhaul — Implementation Plan

> **Goal**: Smooth, lag-free morph between player states with correct gesture semantics per mode.
> **Current state**: `93a92ec8` — morph architecture exists but has friction points.

---

## Problem Statement

1. **Gesture conflict**: Brightness/volume swipe gestures fire in NORMAL/COMPACT mode, but swipe-down on the video in NORMAL should trigger the morph-to-mini transition instead.
2. **Nested scroll vs morph**: `nestedScrollConnection` shrinks `playerHeightPx` during scroll, while the morph also shrinks the rendered height. They fight each other during morph drags.
3. **`pointerInput` key tears down on flag flip**: `pointerInput(isMorphDragging)` is recreated every time drag starts/stops — causes a frame skip right at the moment smoothness matters most.
4. **Tree swap on mode transitions**: `when(playerMode)` swaps between `FullscreenPlayerContent`, `FloatingPlayerContent`, and `WindowedPlayerContent` — completely different composable trees. Fullscreen↔NORMAL tears down the LazyColumn and video surface.
5. **Mini-player snap is instant**: `FloatingPlayerContent` snaps to corner instantly, then `animateFloatAsState` glides — "jump then glide" artifact.
6. **Dead code**: `PlayerMode.kt` enum barely used, `PlayerControlsScaffold.kt` mixes gestures with overlays, `SeekIndicators` always hidden, `CommentsSection` function never called.
7. **Fat files**: `PlayerScreen.kt` (410 lines), `PlayerDetailsSection.kt` (310 lines), `PlayerOverlays.kt` (370 lines).

---

## Design Principles

| Principle | Why |
|-----------|-----|
| **One shared morph box** | All modes (except fullscreen) share a single video box. Geometry, drag gesture, and crossfade are all driven by `morphProgress`. No tree swap. |
| **Gestures are mode-aware** | Each mode has its own gesture contract. NORMAL = morph drag. FULLSCREEN = brightness/volume. FLOATING = drag-to-snap (its own world). |
| **Morph owns the video box** | `PlayerMorphBox` is the single source of truth for video box geometry, size, position, corner radius, and drag. Everything else reads from it. |
| **Chrome fades, not swaps** | Windowed chrome and floating chrome coexist in the tree. Visibility is driven by crossfade alpha from `morphProgress`. No `if/else` tree teardown. |
| **ViewModel notified only on settle** | During a drag, `morphProgress` is driven purely by finger. ViewModel (minimize/exitMiniPlayer) is only called when the drag settles. |

---

## Gesture Contract Per Mode

| Mode | Swipe-down on video | Swipe-left/right | Tap | Double-tap |
|------|---------------------|------------------|-----|------------|
| **NORMAL** | Morph drag (0→1 progress) | — | Toggle overlays | Enter fullscreen |
| **COMPACT** | Morph drag (0→1 progress) | — | Toggle overlays | Enter fullscreen |
| **FLOATING** | Drag-to-move mini window | — | Tap scrim = expand | — |
| **FULLSCREEN** | Brightness (left) / Volume (right) | — | Toggle overlays | Exit fullscreen |

**Key change**: In NORMAL/COMPACT, the **entire video area** is a morph drag target. Brightness/volume gestures are **disabled** on the video area. They remain available on the **scrub area** (timeline) in fullscreen only, or can be added later as a separate feature.

---

## File Restructuring

### Delete
| File | Reason |
|------|--------|
| `PlayerMode.kt` | Enum is dead code. Mode is implicit from `morphProgress` value. |
| `PlayerControlsScaffold.kt` | Replaced by `PlayerGestures.kt` + `PlayerOverlays.kt` split. |

### Merge / Rewrite
| File | Becomes | Reason |
|------|---------|--------|
| `FloatingPlayerContent.kt` | `FloatingChrome.kt` | Drops drag/snap logic (that's the morph box's job). Becomes a thin chrome overlay that fades in during morph. |
| `FullscreenPlayerContent.kt` | Keep as-is | Fullscreen is its own world. Can be crossfaded later if desired. |

### Split
| File | Into | Reason |
|------|------|--------|
| `PlayerScreen.kt` (410 lines) | Keep as orchestrator (~150 lines), extract scroll math to `PlayerScrollState.kt` | Too much responsibility |
| `PlayerDetailsSection.kt` (310 lines) | `ChannelRow.kt`, `VideoStatsRow.kt`, `DescriptionSection.kt`, `CommentsSection.kt`, `RecommendedSection.kt` | Each component is independent |
| `PlayerOverlays.kt` (370 lines) | `OverlayBars.kt` (TopOverlay, BottomOverlay, CompactControlsRow), `OverlayIndicators.kt` (brightness, volume, seek), `OverlayModals.kt` (OptionsModal, ChaptersPanel) | Bars, indicators, and modals are independent concerns |

### New Files
| File | Purpose |
|------|---------|
| `PlayerMorphBox.kt` | **The shared morphing video box.** Geometry lerped from `morphProgress`, drag gesture handler, crossfade between windowed/floating chrome. This is the heart of the new architecture. |
| `PlayerGestures.kt` | Extracted from `PlayerControlsScaffold`. `pointerInput` handlers for tap, double-tap, vertical drag. Can be disabled during morph drag. Uses stable key (`Unit`) so it never tears down. |
| `PlayerScrollState.kt` | Extracted scroll math from `PlayerScreen.kt`. `playerHeightPx`, `nestedScrollConnection`, `isCollapsedControls`. Lives independently so `PlayerScreen` stays clean. |

### Final File Map

```
PlayerScreen.kt              (~150 lines)  ← orchestrator: state hoisting, morph orchestration, mode dispatch, system UI
PlayerMorphState.kt          (~100 lines)  ← core morph state (keep as-is, minor cleanup)
PlayerMorphBox.kt            (~100 lines)  ← NEW: shared morphing video box, drag, crossfade
PlayerGestures.kt            (~80 lines)   ← NEW: pointerInput handlers (tap, double-tap, vertical drag)
PlayerOverlays.kt            (~120 lines)  ← TopOverlay, BottomOverlay, CompactControlsRow (bars only)
OverlayIndicators.kt         (~80 lines)   ← NEW: BrightnessIndicator, VolumeIndicator, SeekIndicators, LoadingSpinner
OverlayModals.kt             (~120 lines)  ← NEW: OptionsModal, ChaptersPanel
WindowedPlayerContent.kt     (~80 lines)   ← video box (height=morphH) + LazyColumn details + controls scaffold
FloatingChrome.kt            (~60 lines)   ← NEW: mini-player chrome overlay (no drag, no snap)
FullscreenPlayerContent.kt   (~75 lines)   ← keep as-is
PlayerVideoSurface.kt        (~35 lines)   ← keep as-is
PlayerDetailsSection/        (5 files)     ← split from current PlayerDetailsSection.kt
PlayerFormatters.kt          (~60 lines)   ← keep as-is
PlayerViewModel.kt           (~170 lines)  ← keep as-is (minor: clean up getPlayer() cast)
```

---

## Implementation Phases

### Phase 0: Foundation (no behavior change) ✅ COMPLETE
**Goal**: Clean up dead code and restructure files without changing behavior.

1. Delete `PlayerMode.kt` — enum is unused. ✅
2. Delete dead code: `SeekIndicators` (always hidden), `CommentsSection` function (never called), `onVerticalDragStart` callback (empty), `floatingOffsetX/Y` animated values (only activate at >0.99f). ✅
3. Split `PlayerDetailsSection.kt` into individual component files. ✅
4. Split `PlayerOverlays.kt` into `OverlayBars.kt` + `OverlayIndicators.kt` + `OverlayModals.kt`. ✅
5. Extract scroll math from `PlayerScreen.kt` into `PlayerScrollState.kt`. ✅
6. Extract gesture handlers from `PlayerControlsScaffold.kt` into `PlayerGestures.kt`. ✅
7. Rename `PlayerControlsScaffold.kt` → `PlayerOverlays.kt` (bars only). ✅

**Verification**: App works identically after Phase 0. No behavior changes. ✅

**Commit**: `e51af265` - refactor(player): Phase 0 - extract monolithic files into focused modules

### Phase 1: Gesture Fix ✅ COMPLETE
**Goal**: Correct gesture semantics per mode.

1. In NORMAL/COMPACT: disable brightness/volume vertical drag on the video area. The video area becomes a morph drag target. ✅
2. In FULLSCREEN: brightness/volume vertical drag remains active on the video area. ✅
3. Fix `pointerInput` key: use stable key (`Unit` or `morphState`) instead of `isMorphDragging`. Gate drag logic internally with `if (morphState.isDragging)`. ✅
4. Remove `isDraggingMiniPlayer` state from `PlayerScreen.kt` — `FloatingPlayerContent` owns its own drag state. ✅

**Verification**: Swipe-down on video in NORMAL triggers morph drag. Brightness/volume only works in FULLSCREEN. ✅

**Commit**: `15adc3ef` - refactor(player): Phase 1 - mode-aware gestures and morph drag fix

### Phase 2: Eliminate Nested Scroll vs Morph Conflict ✅ COMPLETE
**Goal**: No more jitter during morph drag.

1. Disable nested scroll height consumption during morph drag: ✅
   ```kotlin
   nestedScrollConnection = if (!morphState.isDragging) scrollState.connection else null
   ```
2. During morph drag, video box height is driven purely by `morphProgress` (not scroll). ✅
3. When morph settles, re-enable nested scroll. ✅

**Verification**: Morph drag is smooth, no height jumps from scroll fighting. ✅

**Commit**: `093f877c` - refactor(player): Phase 2 - disable nested scroll during morph drag

### Phase 3: Shared Morph Box ✅ COMPLETE
**Goal**: Single video box for all non-fullscreen modes. No tree swap.

1. Create `PlayerMorphBox.kt`: ✅
   - Takes `morphState.progress` as input.
   - Computes geometry: height (lerp between `playerHeightPx` and `miniHeightPx`), corner radius (0→12dp), position.
   - Contains `PlayerVideoSurface` (always visible, never torn down).
   - Contains drag gesture handler (consumes all pointer events during drag).
   - Renders windowed chrome (details + controls) at alpha `1 - morphProgress/0.4` (0→0.4).
   - Renders floating chrome (`FloatingChrome`) at alpha `(morphProgress - 0.6)/0.4` (0.6→1).
2. Remove `when(playerMode)` tree swap from `PlayerScreen.kt`. ✅
3. `WindowedPlayerContent.kt` becomes just the details LazyColumn (no video box, no controls). ✅
4. `FloatingChrome.kt` becomes a thin overlay (no drag, no snap, no video surface). ✅

**Verification**: Transition from NORMAL→FLOATING is a smooth crossfade. No tree teardown. LazyColumn state is preserved. ✅

**Commit**: `6d2f5d96` - refactor(player): Phase 3 - shared morph box, no tree swap

### Phase 4: Smooth the Mini-Player Snap ✅ COMPLETE
**Goal**: No "jump then glide" artifact.

1. In `PlayerMorphBox.kt`, the floating offset is driven by `morphProgress` directly (not by a separate `animateFloatAsState`). ✅
2. As `morphProgress` approaches 1f, the floating chrome naturally slides to its corner position (geometry is lerped). ✅
3. Remove `miniPlayerOffsetX/Y` state from `PlayerScreen.kt` — no longer needed. ✅

**Verification**: Mini-player glides smoothly to corner during morph, no instant snap. ✅

**Commit**: `b13a47b3` - refactor(player): Phase 4 - smooth mini-player snap, no jump artifact

### Phase 5: Fullscreen Crossfade ✅ COMPLETE
**Goal**: Fullscreen enters/exits with a smooth transition instead of instant switch.

1. Keep fullscreen surface in the tree (hidden via `Visibility:hidden` or alpha=0). ✅
2. On fullscreen enter: crossfade from morph box → fullscreen surface. ✅
3. On fullscreen exit: crossfade back. ✅

**Verification**: Fullscreen transitions are smooth. ✅

**Commit**: `fec66313` - refactor(player): Phase 5 - fullscreen crossfade, Phase 6 - polish

### Phase 6: Polish ✅ COMPLETE
**Goal**: Clean up remaining issues.

1. Fix auto-hide overlay polling loop → simplified with delay. ✅
2. Improve fling velocity calculation (use actual drag duration). ✅
3. Remove `isFullscreenAnim` local state — use `uiState.isFullscreen` directly. ✅
4. Clean up `getPlayer()` cast in `PlayerScreen.kt`. ✅

**Commit**: `fec66313` - refactor(player): Phase 5 - fullscreen crossfade, Phase 6 - polish

### Phase 7: Layout Restructure (Critical Fixes) ✅ COMPLETE
**Goal**: Fix fundamental layout issues that broke the player.

1. PlayerMorphBox uses morphed dimensions (not fillMaxSize) for outer container. ✅
2. Video box positioned correctly with offset and morphed size. ✅
3. Gesture layer only covers video area (not entire screen). ✅
4. PlayerScrollState initializes playerHeightPx to maxPlayerHeightPx (not 0). ✅
5. Video box, details LazyColumn, and controls are separate siblings. ✅

**Commit**: `3a37b870` - fix(player): restructure layout to fix mini player, scroll, and controls

---

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| `AndroidView` (PlayerView) tears down during crossfade | Use `Visibility:hidden` instead of alpha=0 to keep the surface alive |
| Nested scroll re-enabling after morph settle causes scroll jump | Re-enable only when `morphProgress == 0f` (fully windowed), not on every settle |
| PointerInput stable key means drag logic runs even when not dragging | Minimal overhead — just an `if (isDragging)` check per event |
| Crossfade between windowed/floating chrome may show both simultaneously | Use complementary alpha: windowedAlpha = `1 - progress/0.4`, floatingAlpha = `(progress-0.6)/0.4`. They never both show at 100%. |
| Fullscreen tree swap is still jarring | Defer to Phase 5. Fullscreen is a less frequent transition. |

---

## Out of Scope (Future)

- Chapter data source wiring (currently `emptyList()`)
- `skipNext`/`skipPrevious`/`toggleReplay` TODO stubs
- Watch-later / subscribe / share button implementations
- Tablet/foldable adaptive sizing (hardcoded 280dp mini width)
- Double-tap seek (SeekIndicators always hidden)

---

## Order of Operations

```
Phase 0 (cleanup) → Phase 1 (gesture fix) → Phase 2 (scroll conflict) → Phase 3 (shared morph box) → Phase 4 (snap smooth) → Phase 5 (fullscreen crossfade) → Phase 6 (polish)
```

Each phase should be committed separately so we can revert if needed.
