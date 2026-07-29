# Phase 3 — AnimatedVisibility + Composition Audit

**Status:** Draft  
**Phase:** 3 of 5  
**Rules addressed:** R2 (no boolean-gated animated composition), R3 (AnimatedVisibility)  
**Risk:** Low — Phase 2's clean separation makes this straightforward  
**Effort:** 2–3 hours  

---

## Goal

Replace hand-rolled `graphicsLayer { alpha }` patterns with `AnimatedVisibility`. Audit and eliminate remaining boolean-gated compositions of animated content.

## Background

`AnimatedVisibility` guarantees the exit transition finishes **before** removing content from composition — exactly the property we manually re-derived with `graphicsLayer` in the floating-controls pop-in fix (§1A). Once Phase 2 separates "structural applicability" from "is currently visible," the visible-ness boolean becomes a clean input to `AnimatedVisibility`.

## Steps

### 3.1 Replace alpha-gated Boxes with `AnimatedVisibility`

**Files:** `PlayerContent.kt`, `PlayerControls.kt`

For each element that currently uses `graphicsLayer { alpha = someAlpha }`:

```kotlin
// BEFORE
Box(modifier = Modifier.graphicsLayer { alpha = visibility.floatingAlpha }) {
    if (visibility.floatingAlpha > 0.01f) {
        PlayerFloatingOverlay(...)
    }
}

// AFTER
AnimatedVisibility(
    visible = visibility.showFloatingOverlay,
    enter = fadeIn(animationSpec = tween(config.effectiveDuration(250))),
    exit = fadeOut(animationSpec = tween(config.effectiveDuration(250))),
) {
    PlayerFloatingOverlay(...)
}
```

Elements to convert:
- Floating controls overlay (in `PlayerControls.kt`)
- Details panel (in `PlayerContent.kt`)
- Normal/compact bar containers (in `PlayerContent.kt`) — if not already handled by bar alpha

### 3.2 Audit remaining `if`-gated compositions

Scan all player files for `if` conditions that gate composables whose visibility is also driven by animated values. For each:

1. **Structural gate** (mode enum, feature flag)? → Keep as `if`.
2. **Animated visibility gate**? → Replace with `AnimatedVisibility`.
3. **Both**? → Separate: structural `if` outside, `AnimatedVisibility` inside.

**Known candidates:**

| File | Pattern | Verdict |
|------|---------|---------|
| `PlayerGestures.kt` | Mode-gated gesture boxes (fullscreen/normal/floating) | Structural — but derive mode from `visibility` booleans, not raw progress |
| `PlayerContent.kt` | Details panel `if (detailsAlphaFinal > 0.01f)` | Animated — convert to `AnimatedVisibility` |
| `PlayerControls.kt` | Floating overlay `if (floatingAlpha > 0.01f)` | Animated — convert to `AnimatedVisibility` |

### 3.3 Document composition rules

**File:** Add to `feature/player/impl/README.md` (new) or a `CONVENTIONS.md`

```markdown
## Player UI Composition Rules

1. **Never `if`-gate animated content.** Use `AnimatedVisibility` for anything whose
   visibility is driven by a progress value. `if` is for structural/enum state only.

2. **One source of truth for alpha.** All visibility values come from
   `computeControlsVisibility()`. Never compute alpha independently in a composable.

3. **One gesture owner per region.** No stacked recognizers with disable-flags.
```

## Verification

- [ ] All fade transitions look identical or smoother (AnimatedVisibility guarantees exit completes)
- [ ] No pop-in at any threshold boundary
- [ ] No `graphicsLayer { alpha = ... }` remains for visibility gating (only for transform effects)
- [ ] All `if` gates on animated content are either converted to `AnimatedVisibility` or justified as structural

## Files touched

| File | Action |
|------|--------|
| `PlayerContent.kt` | Replace alpha-gated Boxes with `AnimatedVisibility` |
| `PlayerControls.kt` | Replace alpha-gated Boxes with `AnimatedVisibility` |
| `PlayerGestures.kt` | Derive mode gates from `visibility` booleans |
| `README.md` or `CONVENTIONS.md` | **New** — document composition rules |
