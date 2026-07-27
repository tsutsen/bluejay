# Drag-to-Mini Player Gesture

**Status:** Draft  
**Created:** 2026-07-27  
**Scope:** `feature/player/impl` — `PlayerScreen.kt`, `WindowedPlayerContent.kt`, `FloatingPlayerContent.kt`, `PlayerControlsScaffold.kt`

---

## Overview

In **NORMAL** and **COMPACT** player modes, a vertical drag-down on the video surface
transforms the player into the mini (FLOATING) player. The transformation is continuous:
the player scales down, translates toward the mini player's default corner position, the
controls morph between full overlay and compact mini row, and the details panel fades out.

When the drag reaches ~10% from the screen bottom, the player snaps into the fully
minimized state.

---

## Current Architecture (relevant pieces)

```
PlayerScreen (dispatches via PlayerMode)
├── FLOATING    → FloatingPlayerContent (mini, draggable, 280dp wide)
├── NORMAL      → WindowedPlayerContent + TopOverlay + BottomOverlay + details panel
├── COMPACT     → WindowedPlayerContent + CompactControlsRow (same video box, different controls)
└── FULLSCREEN  → FullscreenPlayerContent

PlayerMode.kt:
  FLOATING ← isMinimized
  NORMAL   ← else
  COMPACT  ← isCollapsedControls
  FULLSCREEN ← isFullscreen

PlayerScreen.kt already computes (but doesn't consume) scale/translationX/translationY
off isMinimizedAnim. This is the scaffolding we extend.
```

### Key files

| File | Role |
|------|------|
| `PlayerScreen.kt` | Dispatches to mode-specific composables, holds animation state |
| `WindowedPlayerContent.kt` | NORMAL/COMPACT layout: video box + details + controls scaffold (rename `WindowedContent` → `WindowedPlayerContent` throughout doc) |
| `FloatingPlayerContent.kt` | FLOATING layout: mini player with its own controls |
| `PlayerControlsScaffold.kt` | Gesture layer + top/bottom bar slots (NORMAL/COMPACT only) |
| `PlayerMode.kt` | Enum + `computePlayerMode()` helper |
| `PlayerViewModel.kt` | `minimize()` / `exitMiniPlayer()` / `close()` |
| `PlayerRepository.kt` | StateFlow + ExoPlayer instance |

---

## Design Decisions

### 1. Replace boolean-driven animations with continuous `Animatable<Float>`

**Why:** Current `scale`/`cornerRadius`/`translationX`/`translationY` are four separate
`animateFloatAsState` calls keyed on `isMinimizedAnim.value` — binary, not interruptible.
A drag gesture needs smooth, frame-by-frame control.

**How:** Introduce `morphProgress: Animatable<Float>` (0 = NORMAL, 1 = FLOATING).
Derive all four visual values via `lerp`:

```kotlin
val morphProgress = remember { Animatable(0f) }
var isDraggingMorph by remember { mutableStateOf(false) }

// Sync morphProgress to the discrete state only when the user isn't dragging.
// Gated to skip if morphProgress is already within epsilon of target — otherwise it
// redundantly re-animates to the same value every time isMinimizedAnim flips.
val morphTarget = if (isMinimizedAnim.value) 1f else 0f
LaunchedEffect(isMinimizedAnim.value, morphProgress.value) {
    if (!isDraggingMorph && kotlin.math.abs(morphProgress.value - morphTarget) > 0.01f) {
        morphProgress.animateTo(
            targetValue = morphTarget,
            animationSpec = transitionSpringSpec
        )
    }
}

val scale = lerp(1f, miniScaleTarget, morphProgress.value)
val cornerRadius = lerp(0f, 12f, morphProgress.value).dp
// See §4 for why these are computed from FloatingPlayerContent's actual layout math,
// not flat container fractions.
val morphTranslationX = lerp(0f, miniRestingTranslationX, morphProgress.value)
val morphTranslationY = lerp(0f, miniRestingTranslationY, morphProgress.value)
val detailAlpha = (1f - (morphProgress.value - 0.6f) / 0.4f).coerceIn(0f, 1f)
```

`detailAlpha` fades over the **last 40%** of the gesture, per spec.

**Critical: the `onMorphDragEnd` sequence must animate to 1.0 BEFORE flipping the mode.**
`playerMode` is computed from `isMinimizedAnim.value`, and `WindowedPlayerContent` only
renders while `playerMode` is NORMAL/COMPACT. If `viewModel.minimize()` fires first,
`isMinimizedAnim` flips, `playerMode` switches to FLOATING, and `WindowedPlayerContent`
unmounts on the very next recomposition — taking `morphProgress`'s animation with it.
The player will visibly *jump* from its in-flight shrink state to the fully-minimized
state, not glide there. Fix: animate to 1.0 first, *then* flip the mode:

```kotlin
onMorphDragEnd = {
    isDraggingMorph = false
    coroutineScope.launch {
        if (morphProgress.value > 0.5f) {
            // Stay mounted in WindowedPlayerContent through the full tween
            morphProgress.animateTo(1f, transitionSpringSpec)
            // Only now does the mode switch unmount us — but geometry already matches
            viewModel.minimize()
        } else {
            morphProgress.animateTo(0f, transitionSpringSpec)
        }
    }
}
```

### 2. Gesture location — video box, not the scroll list

**Why:** The existing nested-scroll drag on `LazyColumn` already owns "drag down to shrink
into COMPACT." A new recognizer on the same area would fight.

**How:** Put `detectDragGestures` directly on the video `Box` in `WindowedPlayerContent`:

```kotlin
Box(
    modifier = Modifier
        .fillMaxWidth()
        .height(with(LocalDensity.current) { playerHeightPx.toDp() })
        .background(Color.Black)
        .clipToBounds()
        .pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { onMorphDragStart() },
                onDrag = { change, dragAmount ->
                    if (dragAmount.y > 0f || morphProgress > 0f) {
                        change.consume()
                        onMorphDrag(dragAmount.y)
                    }
                },
                onDragEnd = { onMorphDragEnd() },
                onDragCancel = { onMorphDragEnd() }
            )
        }
) {
    PlayerVideoSurface(player = player)
}
```

### 3. Drag progress math

```kotlin
val dragTravelPx = containerSize.height * 0.9f  // 90% drag = full mini

onMorphDragStart = { isDraggingMorph = true },
onMorphDrag = { deltaY ->
    coroutineScope.launch {
        morphProgress.snapTo(
            (morphProgress.value + deltaY / dragTravelPx).coerceIn(0f, 1f)
        )
    }
},
onMorphDragEnd = {
    isDraggingMorph = false
    coroutineScope.launch {
        if (morphProgress.value > 0.5f) {
            morphProgress.animateTo(1f, transitionSpringSpec)
            viewModel.minimize()
        } else {
            morphProgress.animateTo(0f, transitionSpringSpec)
        }
    }
}
```

- **50% threshold** to commit → minimize
- **90% of containerHeight** as full travel (reaches 10% from screen bottom)
- These are tunable constants — test and tune against feel

**Caveat: relative drag distance vs. absolute screen position.** The spec says "reached
10% from the bottom of the screen" — an absolute-position check. The implementation
accumulates relative drag delta (`morphProgress + deltaY / dragTravelPx`). These aren't
the same thing: if the user starts the drag already partway down the video box, relative
distance reaches 1.0 sooner than absolute position would. This is a conscious
simplification (relative drag feels more natural and is easier to tune), but worth
calling out as a deviation from the written spec.

### 4. Transform application — with correct translation targets

**Bug alert: `graphicsLayer` has a self-shadowing bug.** Inside a `GraphicsLayerScope`
lambda, `translationX`/`translationY` are members of the receiver. The outer `val
translationX`/`val translationY` computed in `PlayerScreen.kt` share those exact names,
so `translationX = translationX` resolves to `this.translationX = this.translationX` — a
silent self-assignment no-op. `scaleX`/`scaleY` are fine because the outer val is named
`scale`, but the translate-toward-corner part of the whole gesture would quietly never
fire. **Rename the outer locals** to `morphTranslationX` / `morphTranslationY` to dodge
the shadow.

**Bug alert: the 0.85f/0.8f translation targets are eyeballed and won't line up with
where `FloatingPlayerContent` actually rests.** These constants were inherited from the
pre-existing dead code (which was never rendered). `FloatingPlayerContent`'s real resting
position is computed from its actual layout:

```kotlin
// From FloatingPlayerContent.kt's onDragEnd snap logic:
val miniWidthPx = miniWidth.toPx()        // 280.dp
val miniHeightPx = miniHeight.toPx()       // 280 * 9/16
val paddingPx = 16.dp.toPx()
val miniRestingX = containerWidth - miniWidthPx - paddingPx
val miniRestingY = containerHeight - miniHeightPx - paddingPx
```

Now that the morph is meant to *land exactly where the real mini player sits* (that's
the whole point of a seamless handoff), we must compute the same math. Fix #1 (the
sequence fix) is what makes Fix #4's precision actually matter — without it, the
translation targets only need to be "close enough." With it, they need to be *exact*.

Apply at the `WindowedPlayerContent` call site via `graphicsLayer`:

```kotlin
// Compute resting translation targets from the same math FloatingPlayerContent uses
val miniWidthPx = miniWidth.toPx()
val miniHeightPx = miniHeight.toPx()
val paddingPx = 16.dp.toPx()
val miniRestingTranslationX = containerSize.width - miniWidthPx - paddingPx
val miniRestingTranslationY = containerSize.height - miniHeightPx - paddingPx

WindowedPlayerContent(
    modifier = Modifier.graphicsLayer {
        scaleX = scale; scaleY = scale
        translationX = morphTranslationX   // NOT translationX — avoids self-shadow
        translationY = morphTranslationY   // NOT translationY — avoids self-shadow
        shape = RoundedCornerShape(cornerRadius)
        clip = true
        // Default pivot is center; scale visually aims toward bottom-right as it
        // translates there. No transformOrigin tweak needed since the translation
        // already lands at the correct corner.
    },
    morphProgress = morphProgress.value,
    onMorphDragStart = { isDraggingMorph = true },
    onMorphDrag = { /* as above */ },
    onMorphDragEnd = { /* as above */ },
    onClose = { viewModel.close() },
    ...
)
```

### 5. Controls swap at midpoint

The bottom bar's `AnimatedContent` currently targets `Boolean` (collapsed or not).
Extend to a 3-state enum:

```kotlin
private enum class ControlsVariant { NORMAL, COMPACT, MINI }
val controlsVariant = when {
    morphProgress >= 0.5f -> ControlsVariant.MINI
    isCollapsedControls -> ControlsVariant.COMPACT
    else -> ControlsVariant.NORMAL
}
```

For the MINI branch, **bypass `PlayerControlsScaffold` entirely** — its top/bottom bar
slots don't map to the floating player's full-area control overlay. Instead, render
`MiniControlsRow` directly:

```kotlin
if (controlsVariant == ControlsVariant.MINI) {
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .fillMaxWidth()
            .height(with(LocalDensity.current) { playerHeightPx.toDp() })
            .clipToBounds()
            .background(Color.Black.copy(alpha = 0.6f))
    ) {
        MiniControlsRow(
            state = state,
            onPlayPause = onPlayPause,
            onClose = onClose,
            onMoreOptions = onOptions,
            onFullscreen = onFullscreenToggle
        )
    }
} else {
    PlayerControlsScaffold(...)  // unchanged NORMAL/COMPACT path
}
```

While `controlsVariant == MINI`, the scaffold's gesture layer (tap-to-toggle, double-tap
fullscreen, vertical-drag brightness/volume) is skipped — which is correct: a mid-morph
mini player shouldn't eat brightness/volume swipes.

**Resolved: gesture layering with `PlayerControlsScaffold`.** The scaffold's `pointerInput`
with `detectTapGestures` is composed as a sibling *after* the video `Box` in the same
outer `Box`, so it overlays on top and intercepts **all** pointer events before the video
Box's `detectDragGestures` ever sees them. `disableVerticalDragGestures = true` only
skips the vertical drag detector — the tap detector still fires. This is a real blocker:
the drag gesture would silently never fire.

**Fix:** Add a `disableTapGestures` parameter to `PlayerControlsScaffold` (mirrors
`disableVerticalDragGestures`), and pass `true` from the `WindowedPlayerContent` call.
The scaffold's gesture layer then becomes a no-op for the Windowed content, letting the
video Box's drag gesture receive events. This is a small change to `PlayerControlsScaffold.kt`
— the only file in the "no changes" row that actually needs modification.

### 6. Shared `MiniControlsRow` composable

Extract from `FloatingPlayerContent.kt`'s inner `Column`:

```kotlin
@Composable
fun MiniControlsRow(
    state: PlayerUiState.Loaded,
    onPlayPause: () -> Unit,
    onClose: () -> Unit,
    onMoreOptions: () -> Unit,
    onFullscreen: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Play/pause + Close row (top)
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), ...) {
            IconButton(onClick = onPlayPause) { ... }
            IconButton(onClick = onClose) { ... }
        }
        Spacer(modifier = Modifier.weight(1f))
        // Title + author + More + Fullscreen row (bottom)
        Row(modifier = Modifier.fillMaxWidth().padding(...), ...) { ... }
        // Progress bar
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            LinearProgressIndicator(...)
        }
    }
}
```

- `FloatingPlayerContent.kt` collapses to just calling `MiniControlsRow(...)`
- `WindowedPlayerContent.kt` calls the same `MiniControlsRow(...)` for the MINI variant (note: doc consistently uses `WindowedPlayerContent`, not `WindowedContent`)

### 7. Detail panel fade-out

On the `LazyColumn`:

```kotlin
LazyColumn(
    state = scrollState,
    userScrollEnabled = morphProgress.value < 0.9f,   // ← real param, not a pointerInput hack
    modifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight()
        .nestedScroll(nestedScrollConnection)
        .background(MaterialTheme.colorScheme.surface)
        .graphicsLayer { alpha = detailAlpha }
)
```

- `alpha = detailAlpha` fades it out over the last 40% of the gesture
- `userScrollEnabled = false` at 0.9f stops the list from intercepting touches near the end
- **Do NOT use `pointerInput(morphProgress.value)`** — the key changes every frame during
  the drag (driven by `snapTo` on every `onMorphDrag` call), which tears down and restarts
  the gesture-detection coroutine dozens of times per second. This would eat/drop touch
events. Use the real `userScrollEnabled` parameter instead.

---

## File Changes Summary

| File | Changes |
|------|---------|
| `PlayerScreen.kt` | Add `morphProgress`, `isDraggingMorph`; replace `scale`/`translationX`/`translationY` with lerp-derived values; compute `miniRestingTranslationX/Y` from `FloatingPlayerContent`'s layout math; pass new params to `WindowedPlayerContent`; handle drag callbacks |
| `WindowedPlayerContent.kt` | Add `morphProgress`, `onMorphDragStart/Drag/DragEnd`, `onClose` params; add `detectDragGestures` on video Box; add `ControlsVariant` enum; branch to `MiniControlsRow` at MINI; apply `graphicsLayer` transform; fade detail panel alpha |
| `FloatingPlayerContent.kt` | Replace inline controls `Column` with `MiniControlsRow(...)` |
| `MiniControlsRow.kt` | **New file** — shared mini player controls |
| `PlayerControlsScaffold.kt` | Add `disableTapGestures` param (mirrors `disableVerticalDragGestures`) — needed so the video Box's drag gesture isn't swallowed by the scaffold's tap detector |
| `PlayerMode.kt` | No changes (morph state is separate from discrete modes) |
| `PlayerViewModel.kt` | No changes (uses existing `minimize()` / `close()`) |

---

## Risks & Considerations

### Seamless handoff (resolved by fixes #1 + #4)
With fix #1 (animate-to-1.0 before mode flip) and fix #4 (translation targets computed
from the same math as `FloatingPlayerContent`'s resting position), the geometry at
`morphProgress == 1f` in `WindowedPlayerContent` should match `FloatingPlayerContent`'s
layout exactly. `WindowedPlayerContent` stays mounted through the full tween, then
unmounts cleanly when `viewModel.minimize()` flips the mode. **If a flash still occurs**,
the fix is to hoist `PlayerVideoSurface` to `PlayerScreen` (shared above both composables),
not touching ExoPlayer state again.

### Gesture conflict with nested scroll
The drag-on-video-Box must not interfere with the existing nested-scroll collapse.
**Mitigation:** Drag recognizer is on the video `Box` only (not the `LazyColumn`), and
only fires when `dragAmount.y > 0f` (downward) or `morphProgress > 0f` (already dragging).
In COMPACT mode (already collapsed), the video box is small enough that downward drags
naturally feel like "pull to expand" rather than "drag to mini."

### Gesture layering with `PlayerControlsScaffold` (resolved)
Added `disableTapGestures` param to `PlayerControlsScaffold.kt` (mirrors `disableVerticalDragGestures`),
passed `true` from the `WindowedPlayerContent` call. The scaffold's gesture layer becomes
a no-op for Windowed content, letting the video Box's drag gesture receive events.

### Tuning constants
- `dragTravelPx = containerSize.height * 0.9f` — how far to drag for full mini
- Commit threshold: `0.5f` of progress
- Detail fade start: `0.6f` of progress
- Scroll disable: `0.9f` of progress

These should be tested and tuned against actual feel.

---

## Implementation Order

1. **Extract `MiniControlsRow`** — shared composable, no behavior change
2. **Refactor `FloatingPlayerContent`** to use `MiniControlsRow`
3. **Add `morphProgress` state** in `PlayerScreen.kt` with lerp-derived values
4. **Pass drag callbacks + new params** through to `WindowedPlayerContent`
5. **Add drag gesture + controls variant branching** in `WindowedPlayerContent`
6. **Add detail panel fade** in `WindowedPlayerContent`
7. **Test and tune** constants against feel
