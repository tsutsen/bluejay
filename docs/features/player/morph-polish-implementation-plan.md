# Morph Polish — Implementation Plan

> **Goal**: Fix morph transition bugs (pop-in, alpha snap, gesture conflicts) and refactor player UI for maintainability.
> **Current state**: `a7e6267b` — original morph overhaul complete (Column-based architecture). Polish work NOT yet started.
> **Total effort**: 16–26 hours across 5 phases + gesture system redesign.

---

## Getting Started (Read These First)

A fresh agent should read these files **in order** before implementing anything:

### 1. Context (2 min)
- This file — `morph-polish-implementation-plan.md`
- `morph-transition-polish.md` — bug catalog + 8 architectural rules + what's already fixed

### 2. Current Source (10 min scan)
These are the files that will be modified. Read them to understand current state:

| File | Lines | What it does |
|------|-------|-------------|
| `feature/player/impl/PlayerView.kt` | 576 | Orchestrator — state, animations, scroll, gestures, rendering |
| `feature/player/impl/ui/PlayerContent.kt` | 305 | Alpha math, video layout, composition |
| `feature/player/impl/ui/PlayerControls.kt` | 428 | Controls + hand-rolled gesture state machine |
| `feature/player/impl/ui/PlayerGestures.kt` | 274 | Mode-gated gesture boxes (3 regions) |
| `feature/player/impl/ui/PlayerUIScaffold.kt` | 175 | Scaffold with disable flags |
| `feature/player/impl/PlayerGeometry.kt` | — | `computeVideoLayout()` — existing pure function pattern to mirror |
| `feature/player/impl/PlayerViewModel.kt` | — | `PlayerUiState`, `PlayerGestureCallbacks` |

### 3. Design Reference (read before Phase 5)
- `gesture-system-handoff.md` — full 3×3 zone grid + binding table design

### 4. Verify Current State
Before starting Phase 1, confirm:
- [ ] App compiles: `./gradlew :feature:player:impl:compileDebugKotlin`
- [ ] Current morph transition works (NORMAL→FLOATING via drag)
- [ ] No existing tests are broken

### 5. Git Workflow
- Branch: `feature/morph-polish` off `main`
- Commits: one per phase, prefixed `refactor(player): Phase N - <description>`
- Example: `refactor(player): Phase 1 - centralize constants and alpha helpers`
- Each phase must compile and pass verification before moving to next

### 6. Using Subagents (MANDATORY)

**Context is expensive. Delegate everything you can.** The parent session should stay lean — only orchestrate, verify, and apply final decisions. Never implement a phase yourself.

#### Agent Roles

| Agent | When to use | Example task |
|-------|-------------|-------------|
| `scout` | Recon — understand current code, find files, trace imports | "Find all callers of `MORPH_TRANSITION_START` across the player module" |
| `worker` | Implementation — write code, create files, make edits | "Implement Phase 1: create `PlayerMorphConfig.kt` and replace inline alpha formulas" |
| `reviewer` | Verification — check diffs compile, look correct, follow patterns | "Review the Phase 1 diff. Does it compile? Are all constants centralized?" |
| `context-builder` | Deep analysis — build handoff context for complex decisions | "Build context on how `PlayerView.kt` orchestrates state, animations, and gestures" |
| `oracle` / `advisor` | Decision-making — when you need to choose between approaches | "Should `collapseAlpha` be computed in `PlayerView` or passed down from `PlayerContent`?" |

#### Rules

1. **ONE agent at a time.** Never run parallel agents. Wait for one to finish before launching the next.
2. **Never implement a phase yourself.** Read the phase spec, then delegate to `worker` with a clear task prompt including:
   - Goal (what to build)
   - Context (which files to read/modify)
   - Success criteria (what must be true when done)
   - Hard constraints (no behavior changes in cleanup phases, mirror `computeVideoLayout` pattern, etc.)
3. **Always verify with `reviewer` after `worker`.** Don't trust the implementation blindly. Have a reviewer check the diff compiles and follows the spec.
4. **Use `scout` before `worker` for unfamiliar code.** If you haven't read a file yet, send `scout` to summarize it before asking `worker` to modify it.
5. **Compile after every phase.** Use the build commands in §4 to verify. If compilation fails, fix it yourself or send `worker` back with the error.

#### Example Workflow for Phase 1

```
1. (Parent) Read phase-1-helpers-config.md spec
2. (Parent → scout) "Summarize PlayerContent.kt and PlayerControls.kt. Find all inline 3-branch alpha formulas and const val thresholds."
3. (Parent → worker) "Implement Phase 1 per the spec. Create PlayerMorphConfig.kt with progressAlpha helper. Replace inline formulas in PlayerContent.kt and PlayerControls.kt. Remove duplicate imports in PlayerGestures.kt. Do not change behavior."
4. (Parent → reviewer) "Review the Phase 1 diff. Compile check. Verify no const val thresholds remain in individual files. Verify no inline 3-branch formulas remain."
5. (Parent) Compile: `./gradlew :app:assembleUnstableDebug`
6. (Parent) If clean → commit. If issues → send worker back with specific fixes.
```

#### Anti-Patterns to Avoid

- ❌ **Implementing everything yourself** — context clogs fast, you'll miss bugs
- ❌ **Running multiple agents in parallel** — only one at a time
- ❌ **Skipping reviewer after worker** — trust but verify
- ❌ **Sending worker without reading the spec first** — worker needs context
- ❌ **Ignoring compile errors** — always verify with build command

---

## Important: Phase Numbering

The **original** Player Morph Overhaul (Plan doc, Phases 0–7) is **complete** at commit `a7e6267b`. It adopted the Column-based architecture from compose-migration.

These **polish phases** (1–5) are a *separate* body of work on top of that foundation. They fix bugs and improve architecture but don't change the overall Column-based approach.

```
Original Overhaul (COMPLETE):  Phases 0–7 → Column-based architecture
Morph Polish (THIS PLAN):      Phases 1–5 → Bug fixes + refactoring on top
```

---

## Background

The original **Player Morph Overhaul** (Phases 0–7, committed through `a7e6267b`) is complete. It replaced the tree-swap architecture with a Column-based approach from the compose-migration branch.

This **Morph Polish** plan addresses remaining bugs and architectural debt:
- Floating controls "pop-in" at morph transition start (§1A — **FIXED**)
- `isCollapsedControls` boolean alpha snap (§1B — **NOT FIXED**)
- Details panel offset desync during morph (§1C — **NEEDS VERIFICATION**)
- Scattered constants and repeated alpha formulas
- Three fragmented gesture handlers causing conflicts
- `PlayerView.kt` at 576 lines — too much responsibility

---

## Diagnosed Issues

### Issue A: Floating Controls Pop-in ✅ FIXED
**Symptom**: Around `miniProgress ≈ 0.3`, floating controls abruptly appear.
**Root cause**: `if (miniProgress > MORPH_TRANSITION_START)` gated the subtree.
**Fix**: Keep container always composed; drive visibility via alpha only. Use `graphicsLayer { alpha = ... }` instead of `Modifier.alpha()`.

### Issue B: isCollapsedControls Alpha Snap ❌ NOT FIXED
**Symptom**: Normal/compact controls don't crossfade smoothly when `isCollapsedControls` flips mid-drag.
**Root cause**: Boolean gate (`if (isCollapsedControls) 0f else 1f`) on alpha values.
**Fix**: Replace with continuous `collapseAlpha` derived from `playerHeightRatio`.

### Issue C: Details Panel Offset Desync 🔍 NEEDS VERIFICATION
**Symptom**: Details panel can ride up into control territory during morph.
**Root cause**: `detailsOffsetY` shrinks with video while `detailsTranslateY` doesn't compensate proportionally.

---

## Architectural Rules (from Bug Analysis)

| Rule | Statement | Bugs Prevented |
|------|-----------|----------------|
| **R1** | Alpha/visibility gets same treatment as geometry — one pure function, one owner | §1B, `showTopOverlay` divergence, future threshold drift |
| **R2** | Never let a boolean gate composition of something mid-animation | §1A, gestures-blocked-during-loading, missing fade-out |
| **R3** | Reach for `AnimatedVisibility` instead of hand-rolled alpha + `if` | Any future pop-in/fade-out issues |
| **R4** | One gesture recognizer per region — not two stacked with disable-flags | Play/pause blink, future gesture conflicts |
| **R5** | Single owner for shared mutable state — no dual writers | Race conditions on `hideControlsJob` |
| **R6** | Extract `PlayerView.kt` into dedicated state holders | Cross-cutting bugs, maintainability |
| **R7** | Repeated three-branch alpha formula → one helper | Copy-paste drift in alpha interpolation |

---

## Implementation Phases

### Phase 1: Helpers & Config
**Status**: ❌ Not started
**Rules**: R7, R8 (everything in config)
**Risk**: Low — zero behavior change
**Effort**: 1–2 hours

**Goal**: Centralize scattered constants and repeated alpha formulas. No behavior change.

**Steps**:
1. Create `PlayerMorphConfig.kt` — config data class + `progressAlpha` helper
2. Replace inline 3-branch alpha formulas in `PlayerContent.kt` and `PlayerControls.kt`
3. Remove scattered `const val` thresholds (all in `PlayerMorphConfig.Default`)
4. Cosmetic: remove duplicate imports in `PlayerGestures.kt`, remove dead `SeekIndicators` call

**Files**:
| File | Action |
|------|--------|
| `PlayerMorphConfig.kt` | **New** — config + `progressAlpha` helper |
| `PlayerContent.kt` | Replace inline formulas, use config |
| `PlayerControls.kt` | Replace inline formulas, use config |
| `PlayerGestures.kt` | Remove duplicate imports |
| `PlayerUIScaffold.kt` | Remove dead SeekIndicators call |

**Verification**:
- [ ] Compiles cleanly
- [ ] Every transition looks identical to pre-phase-1
- [ ] No `const val` thresholds remain in individual files
- [ ] No inline 3-branch alpha formulas remain

---

### Phase 2: Single Source of Truth for Alpha
**Status**: ❌ Not started
**Rules**: R1
**Also fixes**: §1B — `isCollapsedControls` alpha snap
**Risk**: Medium — touches every alpha value
**Effort**: 4–6 hours

**Goal**: One pure function computes all visibility values. One caller at the top of the hierarchy.

**Steps**:
1. Create `PlayerVisibility.kt` — `ControlsVisibility` data class + `computeControlsVisibility()` pure function
2. Wire into `PlayerView.kt` — compute visibility once, pass down
3. Delete shadow computations in `PlayerControls.kt`
4. Delete `isCollapsedControls` boolean gates in `PlayerContent.kt`
5. Unit tests in `PlayerVisibilityTest.kt`

**Key design decision**: `collapseAlpha` replaces `isCollapsedControls` boolean. `playerHeightRatio` drives smooth crossfade between normal/compact bars. No discrete toggle.

**Files**:
| File | Action |
|------|--------|
| `PlayerVisibility.kt` | **New** — data class + pure function |
| `PlayerVisibilityTest.kt` | **New** — unit tests |
| `PlayerView.kt` | Compute visibility once, pass down |
| `PlayerContent.kt` | Use `visibility.*` instead of local alpha math |
| `PlayerControls.kt` | Use `visibility.*` instead of shadow computations |

**Verification**:
- [ ] All unit tests pass
- [ ] Normal→compact transition is smooth crossfade at every scroll position
- [ ] Morph transition looks identical to pre-phase-2
- [ ] No independent alpha computation remains in `PlayerControls.kt`

---

### Phase 3: AnimatedVisibility + Composition Audit
**Status**: ❌ Not started
**Rules**: R2, R3
**Risk**: Low — Phase 2's clean separation makes this straightforward
**Effort**: 2–3 hours

**Goal**: Replace hand-rolled `graphicsLayer { alpha }` patterns with `AnimatedVisibility`. Audit and eliminate remaining boolean-gated compositions of animated content.

**Steps**:
1. Replace alpha-gated Boxes with `AnimatedVisibility` in `PlayerContent.kt` and `PlayerControls.kt`
2. Audit remaining `if`-gated compositions — convert animated gates to `AnimatedVisibility`, keep structural gates
3. Document composition rules in player README

**Elements to convert**:
- Floating controls overlay (in `PlayerControls.kt`)
- Details panel (in `PlayerContent.kt`)
- Normal/compact bar containers (if not already handled by bar alpha)

**Files**:
| File | Action |
|------|--------|
| `PlayerContent.kt` | Replace alpha-gated Boxes with `AnimatedVisibility` |
| `PlayerControls.kt` | Replace alpha-gated Boxes with `AnimatedVisibility` |
| `PlayerGestures.kt` | Derive mode gates from `visibility` booleans |
| `README.md` (player) | **New** — document composition rules |

**Verification**:
- [ ] All fade transitions look identical or smoother
- [ ] No pop-in at any threshold boundary
- [ ] No `graphicsLayer { alpha = ... }` remains for visibility gating
- [ ] All `if` gates on animated content are converted or justified

---

### Phase 4: Code Organization
**Status**: ❌ Not started
**Rules**: R5, R6
**Risk**: Low-medium — organizational refactor, no pixel changes
**Effort**: 3–5 hours

**Goal**: Extract `PlayerView.kt` (~576 lines) into manageable, independently reviewable state holders. Eliminate dual-writer races on shared mutable state.

**Steps**:
1. Extract `rememberAutoHideState` — sole owner of controls auto-hide timer
2. Extract `rememberMorphState` — encapsulates morph progress, drag callbacks, settle animations
3. Extract `rememberFullscreenState` — fullscreen progress and enter/exit
4. Slim down `PlayerView.kt` to ~200 lines (orchestrator only)

**Target structure**:
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

**Files**:
| File | Action |
|------|--------|
| `PlayerAutoHide.kt` | **New** — auto-hide timer state holder |
| `PlayerMorphState.kt` | **New** — morph progress state holder |
| `PlayerFullscreenState.kt` | **New** — fullscreen progress state holder |
| `PlayerView.kt` | Slimmed to ~200 lines (orchestrator only) |

**Verification**:
- [ ] `PlayerView.kt` is under 250 lines
- [ ] Each state holder file is under 100 lines
- [ ] No shared mutable state has more than one writer
- [ ] All behavior identical to pre-extraction
- [ ] One extraction per commit, tested independently

---

### Phase 5: Gesture Unification
**Status**: ❌ Not started
**Rules**: R4
**Risk**: High — rewrites gesture detection across the player
**Effort**: 6–10 hours (separate epic)

**Goal**: Replace three fragmented gesture handlers with a single recognizer, single zone model, and single binding table.

**Current problem**: Gesture handling split across:
| File | What it handles | Problem |
|------|----------------|---------|
| `PlayerGestures.kt` | Mode-gated Boxes (fullscreen/normal/floating) | Multiple `detect*Gestures` on sibling Boxes; dead zones during morph |
| `PlayerControls.kt` | Hand-rolled `awaitEachGesture` | Duplicates tap/double-tap logic; inconsistent `change.isConsumed` → play/pause blink |
| `PlayerUIScaffold.kt` | `detectTapGestures` + `detectVerticalDragGestures` (disabled via flags) | Duct tape |

**Solution**: See full design in [`gesture-system-handoff.md`](gesture-system-handoff.md)

**Architecture**:
- 3×3 zone grid (TOP/MIDDLE/BOTTOM × LEFT/CENTER/RIGHT)
- Zone resolved once at touch-down (not re-evaluated mid-drag)
- Binding table: `(zone, gesture)` → action with precedence (zone > row > column > global)
- Single `pointerInput` with `awaitEachGesture` per player region

**Default binding table**:
| Scope | Gesture | Action |
|-------|---------|--------|
| Row: TOP | continuous `VERTICAL_DRAG` | drive `fullscreenProgress` (pull down to exit fullscreen) |
| Row: TOP | discrete `LONG_PRESS` | 2× speed |
| Row: BOTTOM | continuous `VERTICAL_DRAG` | drive `miniProgress` (minimize-to-floating morph) |
| Row: BOTTOM | discrete `LONG_PRESS` | 2× speed |
| Column: LEFT | continuous `VERTICAL_DRAG` | brightness *(only MIDDLE_LEFT — row wins by precedence)* |
| Column: RIGHT | continuous `VERTICAL_DRAG` | volume *(only MIDDLE_RIGHT — row wins by precedence)* |
| Column: LEFT | discrete `DOUBLE_TAP` | seek −5s |
| Column: RIGHT | discrete `DOUBLE_TAP` | seek +5s |
| Column: CENTER | discrete `DOUBLE_TAP` | toggle fullscreen |
| Global | discrete `TAP` | toggle `controlsVisible` |

**Files**:
| File | Action |
|------|--------|
| `GestureZone.kt` | **New** — `GestureRow`, `GestureColumn`, `GestureZone`, `resolveGestureZone` |
| `PlayerGestureBinding.kt` | **New** — `DiscreteGesture`, `ContinuousGesture`, `GestureBindings` |
| `DefaultPlayerBindings.kt` | **New** — concrete binding table |
| `PlayerGestureRecognizer.kt` | **New** — single recognizer composable |
| `PlayerGestures.kt` | Replaced (folded into new recognizer) |
| `PlayerControls.kt` | Remove hand-rolled `awaitEachGesture` |
| `PlayerUIScaffold.kt` | Remove disable-flags, remove gesture layer |

**Verification**:
- [ ] Play/pause button click doesn't also fire background tap
- [ ] Brightness/volume swipes work in correct zones during all modes
- [ ] Double-tap seek works in all modes
- [ ] Morph drag works during transition (no dead zone)
- [ ] Mini player drag works in FLOATING mode
- [ ] No gesture handler is composed twice for the same region

---

## Dependencies

```
Phase 1 (helpers)
    ↓
Phase 2 (alpha source) ──→ Phase 4 (code org) can parallel with 3
    ↓
Phase 3 (AnimatedVisibility)
    ↓
Phase 5 (gestures) — requires 2, recommended 3
```

---

## Order of Operations

```
Phase 1 (helpers) → Phase 2 (alpha source) → Phase 3 (AnimatedVisibility) → Phase 4 (code org) → Phase 5 (gestures)
```

Each phase should be committed separately for easy revert.

---

## Common Pitfalls to Avoid

1. **Don't skip Phase 1** — Phase 2 depends on having the config object and `progressAlpha` helper in place. Refactoring alpha math without centralizing constants first creates a mess.

2. **Don't use `Modifier.alpha()` for visibility gating** — always use `graphicsLayer { alpha = ... }` or `AnimatedVisibility`. `Modifier.alpha()` allocates a layer on every recomposition.

3. **Don't `if`-gate animated content** — if it fades in/out, use `AnimatedVisibility`. `if` removes the subtree and causes pop-in on first composition.

4. **Don't create new `pointerInput` keys on drag state changes** — `pointerInput(isMorphDragging)` tears down and recreates the handler every drag, causing frame skips. Use a stable key (`Unit`) and gate logic internally.

5. **Don't let two `LaunchedEffect`s write the same state** — the `hideControlsJob` dual-writer race is a known bug. Extract to a state holder (Phase 4).

6. **Don't change behavior in cleanup phases** — Phases 1–4 should produce identical visual output. Only Phase 5 changes gesture semantics. If something looks different after Phase 1, revert and check.

7. **Mirror `computeVideoLayout()`'s pattern** — it's the existing pure function in `PlayerGeometry.kt`. New pure functions (`computeControlsVisibility`, `resolveGestureZone`) should follow the same contract: no Compose state, no side effects, unit-testable.

---

## Reference Docs

| Doc | Purpose |
|-----|---------|
| [`player-morph-overhaul-plan.md`](player-morph-overhaul-plan.md) | Original morph overhaul (complete) |
| [`morph-transition-polish.md`](morph-transition-polish.md) | Bug catalog + 8 architectural rules + phase breakdown |
| [`phase-1-helpers-config.md`](phase-1-helpers-config.md) | Phase 1 implementation spec |
| [`phase-2-alpha-source.md`](phase-2-alpha-source.md) | Phase 2 implementation spec |
| [`phase-3-animated-visibility.md`](phase-3-animated-visibility.md) | Phase 3 implementation spec |
| [`phase-4-code-org.md`](phase-4-code-org.md) | Phase 4 implementation spec |
| [`phase-5-gesture-unification.md`](phase-5-gesture-unification.md) | Phase 5 overview |
| [`gesture-system-handoff.md`](gesture-system-handoff.md) | Full gesture system design (3×3 zone grid) |

---

## Out of Scope (Future)

- Chapter data source wiring (currently `emptyList()`)
- `skipNext`/`skipPrevious`/`toggleReplay` TODO stubs
- Watch-later / subscribe / share button implementations
- Tablet/foldable adaptive sizing (hardcoded 280dp mini width)
- Double-tap seek (SeekIndicators always hidden)
- Settings-driven gesture binding table (Phase 5 architecture supports it)
