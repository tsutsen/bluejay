# Drag-to-Mini-Player Polish — Implementation Plan

**Status:** Implemented  
**Created:** 2026-07-27  
**Scope:** `feature/player/impl` — `PlayerScreen.kt`, `WindowedPlayerContent.kt`, `MiniControlsRow.kt`

---

## Problem Statement

The current drag-to-mini implementation has four polish issues that need fixing before
the feature feels production-ready:

1. **Wrong final position** — The dragged player lands past the mini player's resting corner
2. **No dimension morphing** — Width and height stay fixed during drag (only scale/translate)
3. **Unidirectional drag** — Upward drag during morph doesn't reverse progress
4. **Controls shrink with player** — MiniControlsRow elements get smaller as the player shrinks

---

## Current Architecture (relevant pieces)

```
PlayerScreen (holds morphProgress Animatable)
├── Computes scale, cornerRadius, translationX, translationY from morphProgress
├── Applies graphicsLayer transform to WindowedPlayerContent
└── Dispatches to WindowedPlayerContent for NORMAL/COMPACT

WindowedPlayerContent
├── Video Box with detectDragGestures + detectTapGestures
├── LazyColumn detail panel (fades during morph)
└── PlayerControlsScaffold OR MiniControlsRow (at morphProgress >= 0.5f)

FloatingPlayerContent (reference for final position)
├── .align(Alignment.BottomEnd)
├── .padding(16.dp)
├── .offset { IntOffset(x = offsetX, y = offsetY) }
└── Fixed size: 280dp × 157.5dp (9:16)

MiniControlsRow (new shared composable)
├── Play/pause + Close row (top)
├── Title + author + More + Fullscreen row (bottom)
└── Progress bar (bottom edge)
```

---

## Implementation Plan

### Fix 1: Morph width and height (not just scale)

**Current behavior:**
- Uses `scale` for proportional scaling
- Video box height driven by `playerHeightPx` (scroll collapse)
- Width always `fillMaxWidth`

**Target behavior:**
- Width animates from `containerSize.width` → `miniWidth` (280dp)
- Height animates from `playerHeightPx` → `miniHeight` (280 × 9/16 = 157.5dp)

**Changes:**

#### `PlayerScreen.kt`

Replace scale-based transform with explicit width/height morph:

```kotlin
// Remove:
val scale = (1f + (miniScaleTarget - 1f) * p).coerceIn(0f, 1f)

// Add:
val morphWidth = containerSize.width - (containerSize.width - miniWidthPx) * p
val morphHeight = playerHeightPx - (playerHeightPx - miniHeightPx) * p
```

#### `WindowedPlayerContent.kt`

Apply width/height to video Box:

```kotlin
Box(
    modifier = Modifier
        .width(with(LocalDensity.current) { morphWidth.toDp() })
        .height(with(LocalDensity.current) { morphHeight.toDp() })
        .background(Color.Black)
        .clipToBounds()
        .pointerInput(Unit) { detectTapGestures(...) }
        .pointerInput(Unit) { detectDragGestures(...) }
) {
    PlayerVideoSurface(player = player)
}
```

Pass `morphWidth` and `morphHeight` as parameters from `PlayerScreen`.

---

### Fix 2: Bidirectional drag with small deadzone

**Current behavior:**
```kotlin
if (dragAmount.y > 0f || morphProgress > 0f) {
    change.consume()
    onMorphDrag(dragAmount.y)
}
```
Upward drags ignored unless already mid-morph.

**Target behavior:**
- Small deadzone at start (0–5% progress) to prevent accidental reversal
- Upward drags reverse progress when `morphProgress > 0.05f`
- Downward drags always accepted

**Changes:**

#### `WindowedPlayerContent.kt`

```kotlin
onDrag = { change, dragAmount ->
    val shouldAccept = dragAmount.y > 0f || 
                       (dragAmount.y < 0f && morphProgress > 0.05f)
    if (shouldAccept) {
        change.consume()
        onMorphDrag(dragAmount.y)
    }
}
```

#### `PlayerScreen.kt`

No changes needed — the drag callback already handles progress update.

---

### Fix 3: Controls constant size during morph

**Current behavior:**
- `MiniControlsRow` Box uses `playerHeightPx.toDp()` as height
- Elements shrink as player shrinks during morph

**Target behavior:**
- Controls pinned to `miniHeight` (157.5dp) during morph
- `MiniControlsRow` uses fixed height, not `fillMaxSize`

**Changes:**

#### `MiniControlsRow.kt`

Replace `fillMaxSize()` with fixed dimensions:

```kotlin
@Composable
fun MiniControlsRow(
    state: PlayerUiState.Loaded,
    onPlayPause: () -> Unit,
    onClose: () -> Unit,
    onMoreOptions: () -> Unit,
    onFullscreen: () -> Unit,
    miniHeight: Dp = 157.5.dp  // 280 * 9/16
) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .height(miniHeight)
    ) {
        // ... existing content
    }
}
```

#### `WindowedPlayerContent.kt`

Pin controls height during morph:

```kotlin
val controlsHeight = if (morphProgress >= 0.5f) {
    with(LocalDensity.current) { miniHeight.toDp() }
} else {
    with(LocalDensity.current) { playerHeightPx.toDp() }
}

// Use controlsHeight for both MiniControlsRow Box and PlayerControlsScaffold
```

Pass `miniHeight` as parameter from `PlayerScreen`.

---

### Fix 4: Correct final position

**Current behavior:**
```kotlin
val miniRestingTranslationX = containerSize.width - miniWidthPx - paddingPx
val miniRestingTranslationY = containerSize.height - miniHeightPx - paddingPx
val morphTranslationX = miniRestingTranslationX * p
val morphTranslationY = miniRestingTranslationY * p
```
Translation calculated from container edge, but doesn't match FloatingPlayerContent's layout.

**Target behavior:**
Match FloatingPlayerContent's exact layout:
- `.align(Alignment.BottomEnd)` → anchored to bottom-right
- `.padding(16.dp)` → 16dp padding from edges
- `.offset { IntOffset(x, y) }` → drag offset (0 when at rest)

**Verification:**
FloatingPlayerContent renders at:
```
X = containerWidth - miniWidth - 16dp (padding) + offsetX
Y = containerHeight - miniHeight - 16dp (padding) + offsetY
```

When `offsetX = 0, offsetY = 0` (at rest), the top-left corner is at:
```
X = containerWidth - miniWidth - 16dp
Y = containerHeight - miniHeight - 16dp
```

**Current math:**
```kotlin
val miniRestingTranslationX = containerSize.width - miniWidthPx - paddingPx
```
This matches! The issue is that `paddingPx = 16.dp.toPx()` is correct.

**Debug:**
Verify `paddingPx` value. If `16.dp.toPx()` returns unexpected value, use hardcoded:
```kotlin
val paddingPx = 16f * density.density  // 16dp in pixels
```

**Changes:**

#### `PlayerScreen.kt`

No changes needed if math is correct. Add logging to verify:
```kotlin
Log.d(TAG, "Mini resting: X=$miniRestingTranslationX, Y=$miniRestingTranslationY")
Log.d(TAG, "Container: ${containerSize.width}x${containerSize.height}")
Log.d(TAG, "Mini: ${miniWidthPx}x${miniHeightPx}, padding=$paddingPx")
```

---

### Fix 5: Final size matches exactly

**Current behavior:**
```kotlin
val miniScaleTarget = if (isTablet) 0.35f else 0.45f
val scale = (1f + (miniScaleTarget - 1f) * p).coerceIn(0f, 1f)
```
Scale target is percentage of screen width, not fixed 280dp.

**Target behavior:**
Use fixed `miniWidth = 280.dp`, `miniHeight = 280 * 9/16 = 157.5dp`

**Changes:**

#### `PlayerScreen.kt`

Remove `miniScaleTarget`:
```kotlin
// Remove:
val miniScaleTarget = if (isTablet) 0.35f else 0.45f

// Already have:
val miniWidth = 280.dp
val miniHeight = miniWidth * 9f / 16f
```

The width/height morph (Fix 1) already uses these fixed dimensions.

---

## File Changes Summary

| File | Changes |
|------|---------|
| `PlayerScreen.kt` | Replace scale with width/height morph; remove `miniScaleTarget`; pass `morphWidth`, `morphHeight`, `miniHeight` to WindowedPlayerContent |
| `WindowedPlayerContent.kt` | Apply width/height to video Box; pin controls height during morph; fix drag deadzone; pass new params from PlayerScreen |
| `MiniControlsRow.kt` | Use fixed `miniHeight` instead of `fillMaxSize()` |
| `FloatingPlayerContent.kt` | No changes (reference only) |
| `PlayerControlsScaffold.kt` | No changes |
| `PlayerMode.kt` | No changes |
| `PlayerViewModel.kt` | No changes |

---

## Implementation Order

1. **Update `MiniControlsRow.kt`** — Add `miniHeight` parameter, use fixed height
2. **Update `PlayerScreen.kt`** — Replace scale with width/height morph; remove `miniScaleTarget`; pass new params
3. **Update `WindowedPlayerContent.kt`** — Apply width/height to video Box; pin controls height; fix drag deadzone
4. **Verify translation math** — Log final position; compare to FloatingPlayerContent
5. **Test and tune** — Bidirectional drag, constant controls size, exact final position

---

## Testing Checklist

1. ✅ Drag down: width/height animate smoothly to 280dp/157.5dp
2. ✅ Drag up during morph (past 5%): reverses progress smoothly
3. ✅ Drag up before 5%: ignored (deadzone)
4. ✅ Controls stay at 157.5dp height, don't shrink during morph
5. ✅ Final position matches FloatingPlayerContent exactly (top-left corner)
6. ✅ Tap on video still toggles controls during morph
7. ✅ Nested-scroll collapse (list → COMPACT) still works
8. ✅ Scaffold's gesture layer doesn't intercept pointer events in windowed mode
9. ✅ No negative corner radius or scale values (clamped)
10. ✅ Expand from mini → Windowed appears at full size instantly (snap)

---

## Risks & Considerations

### Dimension morph vs. scale
Using explicit width/height instead of scale means the video surface and controls morph independently. This is more accurate but requires careful alignment of the morph progress across all elements.

### Controls height pinning
Pinning `MiniControlsRow` to `miniHeight` means it won't shrink during the scroll-collapse phase (before morph starts). This is intentional — the controls should only morph during the drag-to-mini gesture.

### Deadzone tuning
5% deadzone is a starting point. May need adjustment based on feel:
- Too small → accidental reversal
- Too large → unresponsive upward drag

### Translation math verification
If the final position is still off after implementing Fix 4, add logging to compare:
- `WindowedPlayerContent` final position (at `morphProgress == 1f`)
- `FloatingPlayerContent` resting position

The math should match exactly. If not, there's a subtle difference in how the layouts are anchored.

---

## References

- **FloatingPlayerContent.kt** — Reference for final position and size
- **MiniControlsRow.kt** — Shared controls composable
- **PlayerControlsScaffold.kt** — Gesture layer (disabled during morph)
- **PlayerScreen.kt** — Morph state and animation logic
- **WindowedPlayerContent.kt** — Video box and controls during morph
