# Phase 1 — Helpers & Config Object

**Status:** Draft  
**Phase:** 1 of 5  
**Rules addressed:** R7 (repeated formula → helper), R8 (everything in config)  
**Risk:** Low — zero behavior change  
**Effort:** 1–2 hours  

---

## Goal

Centralize all scattered constants and repeated alpha formulas. No behavior change — purely structural. Every transition looks identical after this phase.

## Steps

### 1.1 Create `PlayerMorphConfig`

**File:** `PlayerMorphConfig.kt` (new, alongside `PlayerGeometry.kt`)

```kotlin
data class PlayerMorphConfig(
    // — Morph transition ranges —
    val morphTransitionStart: Float = 0.3f,
    val morphTransitionEnd: Float = 0.7f,
    val detailsFadeStart: Float = 0.1f,
    val detailsFadeEnd: Float = 0.4f,
    val detailsTranslateFraction: Float = 0.3f,

    // — Settled state thresholds —
    val miniDragThreshold: Float = 0.98f,
    val miniSettledThreshold: Float = 0.01f,
    val fullscreenSettledThreshold: Float = 0.01f,

    // — Gesture parameters —
    val dragTravelFraction: Float = 0.9f,
    val deadzoneProgress: Float = 0.05f,
    val doubleTapIntervalMs: Long = 300,

    // — Seek & media control —
    val seekAmountSeconds: Int = 10,
    val brightnessStepSize: Float = 0.02f,
    val volumeStepSize: Float = 0.02f,

    // — Animation timing —
    val transitionDurationMs: Int = 250,
    val animationSpeedMultiplier: Float = 1.0f,
    val autoHideMs: Long = 3000,

    // — Layout —
    val miniPlayerWidthDp: Dp = 280.dp,
    val miniPlayerAspectRatio: Float = 9f / 16f,
    val miniPlayerPaddingDp: Dp = 16.dp,
) {
    companion object {
        val Default = PlayerMorphConfig()
    }

    fun effectiveDuration(baseMs: Int): Int =
        (baseMs / animationSpeedMultiplier).coerceIn(50, baseMs * 4).toInt()
}
```

### 1.2 Create `progressAlpha` helper

**File:** Same file or `PlayerAlphaHelpers.kt`

```kotlin
fun progressAlpha(
    p: Float,
    start: Float,
    end: Float,
    reversed: Boolean = false
): Float = when {
    !reversed -> when {
        p <= start -> 0f
        p >= end -> 1f
        else -> (p - start) / (end - start)
    }
    else -> when {
        p <= start -> 1f
        p >= end -> 0f
        else -> 1f - (p - start) / (end - start)
    }
}.coerceIn(0f, 1f)
```

### 1.3 Replace inline formulas

**Files:** `PlayerContent.kt`, `PlayerControls.kt`

Search for the 3-branch pattern and replace:

```kotlin
// BEFORE (appears ~4× across 2 files)
if (miniProgress <= MORPH_TRANSITION_START) 0f
else if (miniProgress >= MORPH_TRANSITION_END) 1f
else (miniProgress - MORPH_TRANSITION_START) / (MORPH_TRANSITION_END - MORPH_TRANSITION_START)

// AFTER
progressAlpha(miniProgress, config.morphTransitionStart, config.morphTransitionEnd)
// or for reverse fades:
progressAlpha(miniProgress, config.morphTransitionStart, config.morphTransitionEnd, reversed = true)
```

### 1.4 Remove scattered `const val` thresholds

Delete top-level constants from individual files. Single source: `PlayerMorphConfig.Default`.

### 1.5 Cosmetic cleanup

- Remove duplicate imports in `PlayerGestures.kt` (lines 8-9: `detectDragGestures`, `detectTapGestures`)
- Remove or flag dead `SeekIndicators(false, false)` call in `PlayerUIScaffold.kt:136`

## Verification

- [ ] Compiles cleanly
- [ ] Every transition looks identical to pre-phase-1
- [ ] No `const val` thresholds remain in individual files (all in `PlayerMorphConfig`)
- [ ] No inline 3-branch alpha formulas remain

## Files touched

| File | Action |
|------|--------|
| `PlayerMorphConfig.kt` | **New** — config + `progressAlpha` helper |
| `PlayerContent.kt` | Replace inline formulas, use config |
| `PlayerControls.kt` | Replace inline formulas, use config |
| `PlayerGestures.kt` | Remove duplicate imports |
| `PlayerUIScaffold.kt` | Remove dead SeekIndicators call |
