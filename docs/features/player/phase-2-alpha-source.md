# Phase 2 — Single Source of Truth for Alpha

**Status:** Draft  
**Phase:** 2 of 5  
**Rules addressed:** R1 (one pure function, one owner)  
**Also fixes:** §1B — `isCollapsedControls` alpha snap  
**Risk:** Medium — touches every alpha value  
**Effort:** 4–6 hours  

---

## Goal

One pure function computes all visibility values. One caller at the top of the hierarchy. No shadow computations in child composables.

This mirrors the existing `computeVideoLayout()` pattern — pure, unit-testable, no Compose dependencies.

## Steps

### 2.1 Create `ControlsVisibility` data class + pure function

**File:** `PlayerVisibility.kt` (new)

```kotlin
data class ControlsVisibility(
    val normalBarAlpha: Float,
    val compactBarAlpha: Float,
    val fullscreenBarAlpha: Float,
    val miniControlsAlpha: Float,
    val floatingAlpha: Float,
    val detailsAlpha: Float,
    val detailsTranslateY: Float,
    val showNormalTopBar: Boolean,
    val showNormalBottomBar: Boolean,
    val showCompactBar: Boolean,
    val showFullscreenBar: Boolean,
    val showMiniControls: Boolean,
    val showFloatingOverlay: Boolean,
    val showDetails: Boolean,
)

fun computeControlsVisibility(
    miniProgress: Float,
    fullscreenProgress: Float,
    playerHeightRatio: Float,
    controlsVisible: Boolean,
    config: PlayerMorphConfig = PlayerMorphConfig.Default,
): ControlsVisibility {
    val controlsVisibleFactor = if (controlsVisible) 1f else 0f

    // Continuous crossfade replaces isCollapsedControls boolean.
    // playerHeightRatio 0.4+ → fully normal. 0.2- → fully compact. Between → crossfade.
    val collapseAlpha = progressAlpha(
        p = playerHeightRatio,
        start = 0.4f,
        end = 0.2f,
        reversed = true
    )

    val normalMorphFade = progressAlpha(
        miniProgress, config.morphTransitionStart, config.morphTransitionEnd, reversed = true
    )
    val fullscreenMorphFade = progressAlpha(fullscreenProgress, 0f, 1f, reversed = true)

    return ControlsVisibility(
        normalBarAlpha = normalMorphFade * fullscreenMorphFade * (1f - collapseAlpha) * controlsVisibleFactor,
        compactBarAlpha = normalMorphFade * fullscreenMorphFade * collapseAlpha * controlsVisibleFactor,
        fullscreenBarAlpha = progressAlpha(fullscreenProgress, 0f, 1f) * controlsVisibleFactor,
        miniControlsAlpha = progressAlpha(miniProgress, config.morphTransitionStart, config.morphTransitionEnd),
        floatingAlpha = progressAlpha(miniProgress, config.morphTransitionStart, config.morphTransitionEnd),
        detailsAlpha = progressAlpha(miniProgress, config.detailsFadeStart, config.detailsFadeEnd, reversed = true),
        detailsTranslateY = miniProgress * config.detailsTranslateFraction,
        showNormalTopBar = normalBarAlpha > 0.01f && !controlsVisible,
        showNormalBottomBar = normalBarAlpha > 0.01f && !controlsVisible,
        showCompactBar = compactBarAlpha > 0.01f && !controlsVisible,
        showFullscreenBar = fullscreenBarAlpha > 0.01f && !controlsVisible,
        showMiniControls = miniControlsAlpha > 0.01f,
        showFloatingOverlay = floatingAlpha > 0.01f,
        showDetails = detailsAlpha > 0.01f,
    )
}
```

### 2.2 Wire into `PlayerView.kt`

```kotlin
val visibility = computeControlsVisibility(
    miniProgress = miniProgressAnim.value,
    fullscreenProgress = fullscreenProgressAnim.value,
    playerHeightRatio = if (containerSize.height > 0f) playerHeightPx / containerSize.height else 1f,
    controlsVisible = controlsVisible,
    config = PlayerMorphConfig.Default,
)
```

Pass `visibility` to `PlayerContent` — replaces all individual alpha params.

### 2.3 Delete shadow computations in `PlayerControls.kt`

Remove hand-computed `normalAlpha`, `topAlpha`, `bottomAlpha`, `floatingAlpha`. Use values from `visibility` data class.

### 2.4 Delete `isCollapsedControls` boolean gates in `PlayerContent.kt`

Remove:
```kotlin
val normalBarAlpha = ... * (if (isCollapsedControls) 0f else 1f)
val compactBarAlpha = ... * (if (isCollapsedControls) 1f else 0f)
```

Use `visibility.normalBarAlpha` / `visibility.compactBarAlpha`.

### 2.5 Unit tests

**File:** `PlayerVisibilityTest.kt` (new)

```kotlin
@Test
fun `normal bar visible at zero progress`() {
    val v = computeControlsVisibility(0f, 0f, 1f, true)
    assertEquals(1f, v.normalBarAlpha, 0.001f)
    assertEquals(0f, v.floatingAlpha, 0.001f)
}

@Test
fun `floating fades in during morph transition range`() {
    assertEquals(0f, computeControlsVisibility(0.3f, 0f, 1f, true).floatingAlpha, 0.001f)
    assertEquals(0.5f, computeControlsVisibility(0.5f, 0f, 1f, true).floatingAlpha, 0.001f)
    assertEquals(1f, computeControlsVisibility(0.7f, 0f, 1f, true).floatingAlpha, 0.001f)
}

@Test
fun `normal-to-compact crossfade is continuous`() {
    val tall = computeControlsVisibility(0f, 0f, 0.5f, true)
    assertTrue(tall.normalBarAlpha > 0.5f)
    assertEquals(0f, tall.compactBarAlpha, 0.001f)

    val short = computeControlsVisibility(0f, 0f, 0.2f, true)
    assertEquals(0f, short.normalBarAlpha, 0.001f)
    assertTrue(short.compactBarAlpha > 0.5f)

    val mid = computeControlsVisibility(0f, 0f, 0.3f, true)
    assertTrue(mid.normalBarAlpha > 0f && mid.normalBarAlpha < 1f)
    assertTrue(mid.compactBarAlpha > 0f && mid.compactBarAlpha < 1f)
}

@Test
fun `config overrides change transition ranges`() {
    val config = PlayerMorphConfig.Default.copy(
        morphTransitionStart = 0.5f,
        morphTransitionEnd = 0.8f,
    )
    assertEquals(0f, computeControlsVisibility(0.5f, 0f, 1f, true, config).floatingAlpha, 0.001f)
    assertEquals(0.5f, computeControlsVisibility(0.65f, 0f, 1f, true, config).floatingAlpha, 0.001f)
}
```

## Key design decision — `collapseAlpha` replaces `isCollapsedControls`

The hard boolean that caused §1B (alpha snap) is eliminated. `playerHeightRatio` drives a
smooth crossfade between normal and compact bars. No discrete toggle, no snap.

## Verification

- [ ] All unit tests pass
- [ ] Normal→compact transition is a smooth crossfade at every scroll position
- [ ] Morph transition looks identical to pre-phase-2
- [ ] No independent alpha computation remains in `PlayerControls.kt`
- [ ] `isCollapsedControls` no longer gates alpha (may still exist for mode dispatch)

## Files touched

| File | Action |
|------|--------|
| `PlayerVisibility.kt` | **New** — data class + pure function |
| `PlayerVisibilityTest.kt` | **New** — unit tests |
| `PlayerView.kt` | Compute visibility once, pass down |
| `PlayerContent.kt` | Use `visibility.*` instead of local alpha math |
| `PlayerControls.kt` | Use `visibility.*` instead of shadow computations |
