# Morph Transition Polish — Bug Fixes & Architecture Suggestions

**Status:** Partially implemented (see §3 for what's done vs. remaining)
**Created:** 2025-01-XX
**Scope:** `feature/player/impl` — `PlayerContent.kt`, `PlayerControls.kt`, `PlayerGeometry.kt`, `PlayerView.kt`

---

## Problem Statement

The morph transition (NORMAL → FLOATING via drag-down gesture) exhibited several
visual artifacts during the `miniProgress ∈ [0, 1]` animation range. This doc catalogs
the diagnosed root causes, the fixes applied, and remaining suggestions.

---

## §1 — Diagnosed Issues

### Issue A: Floating controls "pop-in" at MORPH_TRANSITION_START (0.3)

**Symptom:** Around `miniProgress ≈ 0.3`, a new element abruptly becomes visible and
clips the still-fading normal controls. Not a smooth fade — a hard appearance.

**Root cause (PlayerControls.kt, formerly line ~296):**

The floating controls subtree was conditionally composed:

```kotlin
// BEFORE — BUG
if (miniProgress > MORPH_TRANSITION_START) {
    val floatingAlpha = /* smooth interpolation 0→1 */
    Box(modifier = Modifier
        .offset { IntOffset(videoLayout.offsetX.toInt(), videoLayout.offsetY.toInt()) }
        .size(width = ..., height = ...)
        .alpha(floatingAlpha)
    ) { /* shadow, PlayerFloatingOverlay, etc. */ }
}
```

Even though `floatingAlpha` interpolates smoothly from 0 to 1 across
`[MORPH_TRANSITION_START, MORPH_TRANSITION_END]`, the outer `if` gate means the entire
subtree doesn't exist in composition until `miniProgress` crosses 0.3. Compose has to
measure, layout, and place the Box (with its clip, shadow, and child) for the first time
on the exact frame 0.3 is crossed — which reads as a visual "pop."

**Fix (applied):** Keep the container always composed; drive visibility purely through
alpha. Gate only expensive inner content:

```kotlin
// AFTER — FIX
val floatingAlpha = if (miniProgress <= MORPH_TRANSITION_START) {
    0f
} else if (miniProgress >= MORPH_TRANSITION_END) {
    1f
} else {
    (miniProgress - MORPH_TRANSITION_START) / (MORPH_TRANSITION_END - MORPH_TRANSITION_START)
}.coerceIn(0f, 1f)

Box(
    modifier = Modifier
        .offset { IntOffset(videoLayout.offsetX.toInt(), videoLayout.offsetY.toInt()) }
        .size(width = ..., height = ...)
        .graphicsLayer { alpha = floatingAlpha }  // graphicsLayer, not Modifier.alpha
) {
    if (floatingAlpha > 0.01f) {
        /* shadow, PlayerFloatingOverlay — expensive content gated */
    }
}
```

**Why this works:** The outer Box's measure/layout pass stays stable across the full
`[0, 1]` range of `miniProgress`. No first-frame layout jump. Only the actual draw calls
for `PlayerFloatingOverlay` are skipped when invisible.

**Additional note:** `graphicsLayer { alpha = ... }` preferred over `Modifier.alpha(...)`
to avoid layer allocation churn as the value toggles from 0.

---

### Issue B: isCollapsedControls hard boolean gating alpha

**Symptom:** Normal controls and compact controls don't crossfade smoothly when
`isCollapsedControls` flips mid-drag. One bar disappears instantly while the other
appears at full alpha.

**Root cause (PlayerContent.kt):**

```kotlin
val normalBarAlpha = (1f - miniProgress) * (1f - fullscreenProgress) *
    (if (isCollapsedControls) 0f else 1f)
val compactBarAlpha = (1f - miniProgress) * (1f - fullscreenProgress) *
    (if (isCollapsedControls) 1f else 0f)
```

These are complementary — `normalBarAlpha` and `compactBarAlpha` are gated by a hard
`if/else` on `isCollapsedControls`, not by `miniProgress` itself. If `isCollapsedControls`
flips `false → true` mid-drag, `compactBarAlpha` snaps from 0 straight to
`(1 - miniProgress) * (1 - fullscreenProgress)`, which at `miniProgress ≈ 0.3` is ~0.7.
Compact control bar appears instantly at 70% opacity.

**Where isCollapsedControls flips (PlayerView.kt):**

```kotlin
val isCollapsedControls = !isFullscreenAnim.value &&
    containerSize.height > 0f &&
    (playerHeightPx / containerSize.height) <= 0.45f
```

This is computed from scroll-collapsed player height — a continuous `playerHeightPx`
value that crosses the 0.45 threshold at some discrete frame. When it does, the boolean
flips and both alphas snap simultaneously.

**Suggested fix (not yet applied):** Drive the crossfade purely off a continuous value
instead of gating on the boolean. Two options:

**Option 1 — Derive compact-ness from playerHeightPx ratio directly:**

```kotlin
val collapseRatio = 1f - ((playerHeightPx / containerSize.height) - 0.2f) / (0.45f - 0.2f)
    .coerceIn(0f, 1f)  // 0 at 45% height, 1 at 20% height

val normalBarAlpha = (1f - miniProgress) * (1f - fullscreenProgress) * (1f - collapseRatio)
val compactBarAlpha = (1f - miniProgress) * (1f - fullscreenProgress) * collapseRatio
```

**Option 2 — Animate the boolean transition with animateFloatAsState:**

```kotlin
val collapseAlpha by animateFloatAsState(
    targetValue = if (isCollapsedControls) 1f else 0f,
    animationSpec = tween(durationMillis = 150),
    label = "collapseTransition"
)

val normalBarAlpha = (1f - miniProgress) * (1f - fullscreenProgress) * (1f - collapseAlpha)
val compactBarAlpha = (1f - miniProgress) * (1f - fullscreenProgress) * collapseAlpha
```

Option 1 is preferred — it makes the transition a pure function of scroll position,
eliminating the discrete toggle entirely.

---

### Issue C: Details panel offset desync during morph

**Symptom (potential):** The details panel can ride up into view during morph and sit
visually where controls are, before it's fully faded out. Not a hard "pop" but can look
like clipping/overlap during the `miniProgress ∈ [0.1, 0.4]` window.

**Root cause (PlayerContent.kt):**

```kotlin
val detailsOffsetY = with(density) { videoLayout.heightPx.toDp() }
// ...
.offset(y = detailsOffsetY)
.graphicsLayer { translationY = detailsTranslateY }
```

`detailsOffsetY` shrinks as `videoLayout.heightPx` shrinks (video getting smaller during
morph), while `detailsTranslateY` only grows to `containerHeight * 0.3f` at full mini
progress. These two aren't guaranteed to stay in sync — if `videoLayout.heightPx` shrinks
faster than `detailsTranslateY` compensates, the (still semi-transparent) details panel
can ride up and overlap with controls.

**Current fade range:** Details fade from `DETAILS_FADE_START` (0.1) to `DETAILS_FADE_END`
(0.4), which is earlier than the controls fade (`MORPH_TRANSITION_START` 0.3 to
`MORPH_TRANSITION_END` 0.7). The cascading effect is intentional, but the offset math
should be verified to ensure no visual overlap.

**Suggested fix:** Ensure `detailsOffsetY + detailsTranslateY` always pushes the panel
below any visible controls. Or more simply: gate the details panel composition on
`detailsAlphaFinal > 0.01f` (already done) AND verify the `.offset(y = detailsOffsetY)`
doesn't cause the panel to reposition into control territory as video shrinks.

---

## §2 — Architectural Rules (derived from bug pattern analysis)

The five bugs from this session share a common root cause. `PlayerGeometry.kt`'s
`computeVideoLayout()` is the model: a pure function, single source of truth, explicitly
documented as "no Compose state, no side effects, unit-testable." Alpha/visibility logic
never got that treatment — and that's where almost every bug lived.

The rules below are ranked by bug-reduction-per-effort.

---

### Rule 1: Alpha/visibility gets the same treatment as geometry — one pure function, one owner

**Statement:** Compute all alpha/visibility values in a single pure function. Compute it
once at the top of the composable hierarchy, pass the result down. Delete shadow
computations.

**Evidence:** Alpha was computed independently in `PlayerContent.kt` (bar alphas) and
`PlayerControls.kt` (morph alphas) — two sources of truth that drifted out of sync,
producing the `showTopOverlay` divergence and the `isCollapsedControls` alpha snap.

**Target:**
```kotlin
data class ControlsVisibility(
    val normalBarAlpha: Float,
    val compactBarAlpha: Float,
    val fullscreenBarAlpha: Float,
    val miniControlsAlpha: Float,
    val floatingAlpha: Float,
    val detailsAlpha: Float,
    val detailsTranslateY: Float,
    val resolvedShowTopBar: Boolean,
    val resolvedShowBottomBar: Boolean,
)

fun computeControlsVisibility(
    miniProgress: Float,
    fullscreenProgress: Float,
    isCollapsedControls: Boolean,
    controlsVisible: Boolean,
    containerHeight: Float,
    playerHeightRatio: Float,  // playerHeightPx / containerHeight
): ControlsVisibility { /* ... */ }
```

**Bugs prevented:** §1B (alpha snap), `showTopOverlay` divergence, any future threshold drift.

---

### Rule 2: Never let a boolean gate composition of something that's also mid-animation

**Statement:** `if`-gated composition is for discrete/structural state only (mode enums,
feature flags). Animated visibility always stays composed and uses
`graphicsLayer { alpha = ... }`, full stop.

**Evidence:** Three separate bugs shared this shape:
- **Floating-box pop-in (§1A):** `if (miniProgress > 0.3)` removed the subtree until 0.3
- **Gestures-blocked-during-loading:** `if (state is Loaded)` removed gesture Boxes during splash
- **Missing fade-out:** Boolean gate removed content before animated alpha reached 0

If you want the perf win of skipping expensive children while invisible, gate **only the
children** off the **same** continuous value — never a second, independently-computed
boolean (that's how `resolvedShowTopBar` diverged from `gradientAlpha`).

**Target:**
```kotlin
// Container always composed — alpha drives visibility
Box(modifier = Modifier.graphicsLayer { alpha = floatingAlpha }) {
    // Only expensive inner content gated, off the SAME value
    if (floatingAlpha > 0.01f) {
        PlayerFloatingOverlay(...)
    }
}
```

**Bugs prevented:** §1A (pop-in), gestures-blocked-during-loading, missing fade-out.

---

### Rule 3: Reach for `AnimatedVisibility` instead of hand-rolled alpha + `if`

**Statement:** When you've separated "structural applicability" from "is currently visible"
(Rule 1), use `AnimatedVisibility(visible = ..., enter = fadeIn() + slideInVertically(),
exit = fadeOut() + slideOutVertically())` for the visible-ness transition.

**Evidence:** `AnimatedVisibility` guarantees the exit transition finishes **before**
removing content from composition — exactly the property we manually re-derived with
`graphicsLayer` in Fix §1A. Once alpha values come from a single source (Rule 1), the
visible-ness boolean becomes a clean input to `AnimatedVisibility`, and you get
correct-by-construction fade+slide in both directions for free.

**Bugs prevented:** Any future pop-in/fade-out issues. Highest-leverage fix available.

**Combined impact:** Rule 1 + Rule 3 together would have prevented **four of five** bugs
from this session.

---

### Rule 4: One gesture recognizer per region — not two stacked with disable-flags

**Statement:** Each screen region has exactly one gesture owner. No stacked recognizers
with boolean toggles to disable one in favor of the other.

**Evidence:** `PlayerUIScaffold.kt` has its own `detectTapGestures`/`detectVerticalDragGestures`,
immediately disabled via `disableTapGestures`/`disableVerticalDragGestures` in favor of
`PlayerControls.kt`'s hand-rolled `awaitEachGesture` — which didn't check `change.isConsumed`,
causing the play/pause blink. Two systems patched together with boolean toggles is
inherently fragile.

**Target:** Pick one owner per mode. If you need double-tap-seek beyond what
`detectTapGestures` offers, wrap Compose's primitives rather than hand-rolling consumption
tracking — the built-in ones get this right by default.

**Bugs prevented:** Play/pause blink, future gesture conflicts.

---

### Rule 5: Single owner for shared mutable state — no dual writers

**Statement:** One `LaunchedEffect` (or small state-holder class) is the sole owner of
shared mutable state. Other code paths call a named function (`notifyInteraction()`)
rather than reaching in and mutating directly.

**Evidence:** `hideControlsJob` is written from two different coroutine scopes
(`LaunchedEffect`'s auto-hide, and composable-level `rememberCoroutineScope` in `onTap`).
It happens to work because both sites `cancel()` before reassigning, but that convention
is the only thing holding it together — easy to violate in a future edit.

**Bugs prevented:** Race conditions on `hideControlsJob`, auto-hide timer confusion.

---

### Rule 6: Extract `PlayerView.kt` into dedicated state holders

**Statement:** `PlayerView.kt` (~570 lines) is doing too much: window sizing, insets,
morph sync, fullscreen sync, mini-player drag, nested scroll, auto-hide, gesture wiring,
geometry computation, and rendering — all as composable-local `remember`/`LaunchedEffect`
state. No single function is small enough to hold in your head while reviewing it.

**Target:** Pull slices into small dedicated state holders — plain classes exposing
read-only values and named intent functions, not necessarily full ViewModels:

```kotlin
// Each independently reviewable and unit-testable
val visibilityState = rememberControlsVisibilityState(
    miniProgress, fullscreenProgress, isCollapsedControls, controlsVisible
)
val morphState = rememberMorphState(onMinimize, onClose)
val miniDragState = rememberMiniPlayerDragState(containerSize, miniRestingPos)
```

**Bugs prevented:** Cross-cutting bugs like the dual-writer job slipping through review.

---

### Rule 7: Repeated three-branch alpha formula → one helper

**Statement:** The `if (p <= start) 0f else if (p >= end) 1f else (p - start)/(end - start)`
shape appears near-verbatim at least four times across two files. Centralize it.

**Evidence:** `normalAlpha`, `floatingAlpha`, `detailsFadeAlpha`, `topAlpha`'s components
— all the same shape, copy-pasted, with slight variations that drift.

**Target:**
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

Centralize threshold constants (`MORPH_TRANSITION_START/END`, `MINI_SETTLED_THRESHOLD`,
etc.) into one `object PlayerMorphThresholds` rather than scattering them as top-level
consts referenced inconsistently.

**Bugs prevented:** Copy-paste drift in alpha interpolation math.

---

## §3 — Implementation Status

| Item | Status | File(s) |
|------|--------|---------|
| §1A: Floating controls pop-in | ✅ Fixed | `PlayerControls.kt` |
| §1B: isCollapsedControls alpha snap | ⚠️ Not fixed | `PlayerContent.kt`, `PlayerView.kt` |
| §1C: Details panel offset desync | 🔍 Needs verification | `PlayerContent.kt` |
| R1: computeControlsVisibility pure function | ❌ Not started | New file or `PlayerGeometry.kt` |
| R2: No boolean-gated animated composition | ⚠️ Partial (fix applied for §1A, pattern not codified) | Multiple |
| R3: AnimatedVisibility for transitions | ❌ Not started | Multiple |
| R4: One gesture recognizer per region | ❌ Not started | `PlayerGestures.kt`, `PlayerControls.kt`, `PlayerUIScaffold.kt` |
| R5: Single owner for hideControlsJob | ❌ Not started | `PlayerView.kt` |
| R6: Extract PlayerView into state holders | ❌ Not started | `PlayerView.kt` |
| R7: progressAlpha helper + centralized thresholds | ❌ Not started | Multiple |

---

## §4 — Constants Reference

| Constant | Value | Purpose |
|----------|-------|---------|
| `MORPH_TRANSITION_START` | 0.3 | Floating controls start fading in |
| `MORPH_TRANSITION_END` | 0.7 | Floating controls fully visible |
| `DETAILS_FADE_START` | 0.1 | Details panel starts fading out |
| `DETAILS_FADE_END` | 0.4 | Details panel fully faded |
| `MINI_DRAG_THRESHOLD` | 0.98 | Considered "fully minimized" |
| `MINI_SETTLED_THRESHOLD` | 0.01 | Considered "not minimized" |
| `FULLSCREEN_SETTLED_THRESHOLD` | 0.01 | Considered "not fullscreen" |

**Transition timeline (miniProgress):**

```
0.0 ──────────────────────────────────────────────── 1.0
│                                                    │
│ DETAILS fade out                                   │
│ 0.1 ───────────── 0.4                              │
│                         NORMAL fade out    FLOATING fade in
│                        0.3 ───────────────────── 0.7
│
└── NORMAL controls visible ──┬── overlap ──┬── FLOATING controls visible
                              0.3          0.7
```

---

## §4.1 — Additional Code Issues Found During Review

### Duplicate imports in PlayerGestures.kt

```kotlin
import androidx.compose.foundation.gestures.detectDragGestures     // line 6
import androidx.compose.foundation.gestures.detectTapGestures       // line 7
import androidx.compose.foundation.gestures.detectDragGestures     // line 8 — duplicate
import androidx.compose.foundation.gestures.detectTapGestures       // line 9 — duplicate
```

Cosmetic but worth cleaning up.

### Scattered gesture handling across three files

The current gesture handling is split across:

| File | What it handles | Key issue |
|------|----------------|-----------|
| `PlayerGestures.kt` | Mode-gated Boxes: fullscreen (brightness/volume + 2x + seek), normal (seek + 2x), floating (tap + drag) | Multiple `detect*Gestures` calls on sibling Boxes that can conflict; mode gated by hard thresholds |
| `PlayerControls.kt` | Hand-rolled `awaitEachGesture` inside normal controls Box (tap, double-tap-seek, morph-drag-start) | Duplicates tap/double-tap logic from PlayerGestures.kt; `requireUnconsumed = true` present here but not in PlayerGestures.kt |
| `PlayerUIScaffold.kt` | `detectTapGestures` + `detectVerticalDragGestures` (disabled via flags when Controls.kt handles it) | Exists as fallback; `disableTapGestures`/`disableVerticalDragGestures` flags passed from Controls.kt |

This is the root cause of the controls-blink-on-play-pause bug mentioned in the gesture-system-handoff.md:
the button's own click and the background tap detector both fired for the same touch because
`PlayerControls.kt`'s `awaitEachGesture` didn't consistently check `change.isConsumed`.

**The fix** (see `gesture-system-handoff.md`) is to replace all three with a single recognizer,
single zone model, and single binding table.

### PlayerGestures.kt mode-gating composition issue

```kotlin
// FULLSCREEN mode gestures — only composed when fullscreenProgress > 0.01
if (fullscreenProgress > FULLSCREEN_SETTLED_THRESHOLD) {
    Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectVerticalDragGestures(...) })
    Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectDragGestures(...) })
    // ...
}

// NORMAL/COMPACT mode gestures — only composed when not fullscreen and not minimized
if (fullscreenProgress < FULLSCREEN_SETTLED_THRESHOLD && miniProgress < MINI_DRAG_THRESHOLD) {
    // ...
}

// FLOATING mode gestures — only composed when miniProgress > 0.98
if (miniProgress > MINI_DRAG_THRESHOLD) {
    // ...
}
```

Each mode's gesture subtree is conditionally composed, which means:
- Gesture handlers are torn down and recreated at mode boundaries
- During morph transition (`miniProgress ∈ [0.01, 0.98]`), NEITHER the normal nor floating
gesture handlers are composed — there's a dead zone where PlayerGestures.kt handles nothing
- The morph drag itself is handled by PlayerControls.kt's hand-rolled `awaitEachGesture`,
  not by PlayerGestures.kt

This fragmentation is exactly what the gesture-system-handoff.md proposes to unify.

---

## §5 — Phased Implementation Plan

Eight rules, five phases. Each phase is self-contained and can be landed independently.

| Phase | Doc | Rules | Risk | Effort |
|-------|-----|-------|------|--------|
| 1: Helpers & Config | [phase-1-helpers-config.md](phase-1-helpers-config.md) | R7, R8 | Low | 1–2h |
| 2: Single Alpha Source | [phase-2-alpha-source.md](phase-2-alpha-source.md) | R1 | Medium | 4–6h |
| 3: AnimatedVisibility | [phase-3-animated-visibility.md](phase-3-animated-visibility.md) | R2, R3 | Low | 2–3h |
| 4: Code Organization | [phase-4-code-org.md](phase-4-code-org.md) | R5, R6 | Low-med | 3–5h |
| 5: Gesture Unification | [phase-5-gesture-unification.md](phase-5-gesture-unification.md) | R4 | High | 6–10h |

**Total:** 16–26 hours. Landing order: 1 → 2 → 3 → 4 → 5.

**Dependencies:**
```
Phase 1 (helpers)
    ↓
Phase 2 (alpha source) ──→ Phase 4 (code org) can parallel with 3
    ↓
Phase 3 (AnimatedVisibility)
    ↓
Phase 5 (gestures) — requires 2, recommended 3
```

Phase 5 (gesture unification) is its own epic — the full design lives in
[gesture-system-handoff.md](/home/leon/Downloads/gesture-system-handoff.md).

## §6 — Cross-references

- **Gesture system redesign:** `gesture-system-handoff.md` (separate doc, 3×3 zone grid)
- **Morph overhaul history:** `feature/player/impl/PLAN-morph-overhaul.md`
