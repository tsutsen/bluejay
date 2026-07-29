# Phase 4 — Code Organization

**Status:** Draft  
**Phase:** 4 of 5  
**Rules addressed:** R5 (single owner for shared mutable state), R6 (extract PlayerView)  
**Risk:** Low-medium — organizational refactor, no pixel changes  
**Effort:** 3–5 hours  

---

## Goal

Extract `PlayerView.kt` (~570 lines) into manageable, independently reviewable state holders. Eliminate dual-writer races on shared mutable state.

## Steps

### 4.1 Extract `rememberAutoHideState`

**File:** `PlayerAutoHide.kt` (new)

Sole owner of controls auto-hide timer. Single writer for `hideControlsJob`.

```kotlin
@Composable
fun rememberAutoHideState(
    autoHideMs: Long = 3000,
    onVisibilityChange: (Boolean) -> Unit,
): AutoHideState {
    // Single LaunchedEffect owns the job
    // Exposes:
    //   notifyInteraction() — restart auto-hide timer
    //   hide() — immediately hide
    //   show() — immediately show
    // Read-only: isVisible: State<Boolean>
}
```

Replace the two current writers of `hideControlsJob` in `PlayerView.kt`:
- `LaunchedEffect` auto-hide → use `rememberAutoHideState`
- `onTap` handler → call `autoHideState.notifyInteraction()`

### 4.2 Extract `rememberMorphState`

**File:** `PlayerMorphState.kt` (new)

Encapsulates `morphProgress` Animatable, `isDraggingMorph`, drag callbacks, and the "animate-to-1.0-before-flip" sequence.

```kotlin
@Composable
fun rememberMorphState(
    onMinimize: () -> Unit,
    config: PlayerMorphConfig = PlayerMorphConfig.Default,
): MorphState {
    // Exposes:
    //   progress: State<Float>
    //   isDragging: State<Boolean>
    //   startDrag()
    //   drag(deltaY: Float)
    //   endDrag()
    //   minimize() — animate to 1.0, then call onMinimize
    //   restore() — animate to 0
}
```

### 4.3 Extract `rememberFullscreenState`

**File:** `PlayerFullscreenState.kt` (new)

```kotlin
@Composable
fun rememberFullscreenState(
    onFullscreenChange: (Boolean) -> Unit,
    config: PlayerMorphConfig = PlayerMorphConfig.Default,
): FullscreenState {
    // Exposes:
    //   progress: State<Float>
    //   enterFullscreen()
    //   exitFullscreen()
}
```

### 4.4 Slim down `PlayerView.kt`

After extraction, `PlayerView.kt` should be ~200 lines:

```kotlin
@Composable
fun PlayerView(...) {
    // 1. Window sizing + insets + orientation
    val containerSize = ...
    val playerHeightPx = ...

    // 2. State holders
    val autoHide = rememberAutoHideState(...)
    val morph = rememberMorphState(onMinimize = { viewModel.minimize() })
    val fullscreen = rememberFullscreenState(...)
    val visibility = computeControlsVisibility(...)
    val videoLayout = computeVideoLayout(...)

    // 3. Render
    PlayerContent(
        visibility = visibility,
        videoLayout = videoLayout,
        morphState = morph,
        ...
    )
}
```

No inline animation logic, gesture handling, or timer management.

## Verification

- [ ] `PlayerView.kt` is under 250 lines
- [ ] Each state holder file is under 100 lines
- [ ] No shared mutable state has more than one writer
- [ ] All behavior identical to pre-extraction
- [ ] One extraction per commit, tested independently

## Files touched

| File | Action |
|------|--------|
| `PlayerAutoHide.kt` | **New** — auto-hide timer state holder |
| `PlayerMorphState.kt` | **New** — morph progress state holder |
| `PlayerFullscreenState.kt` | **New** — fullscreen progress state holder |
| `PlayerView.kt` | Slimmed to ~200 lines (orchestrator only) |
