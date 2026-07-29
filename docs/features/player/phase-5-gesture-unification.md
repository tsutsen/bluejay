# Phase 5 — Gesture Unification

**Status:** Draft  
**Phase:** 5 of 5  
**Rules addressed:** R4 (one gesture recognizer per region)  
**Risk:** High — rewrites gesture detection across the player  
**Effort:** Separate epic (6–10 hours estimated)  

---

## Goal

Replace the three fragmented gesture handlers with a single recognizer, single zone model, and single binding table. Eliminates the play/pause blink, gesture conflicts, and dead zones during morph transition.

## Current state (problem)

Gesture handling is split across three files with overlapping responsibility:

| File | What it handles | Problem |
|------|----------------|---------|
| `PlayerGestures.kt` | Mode-gated Boxes: fullscreen (brightness/volume + 2x + seek), normal (seek + 2x), floating (tap + drag) | Multiple `detect*Gestures` on sibling Boxes; mode gated by hard thresholds creates dead zones |
| `PlayerControls.kt` | Hand-rolled `awaitEachGesture` inside normal controls Box (tap, double-tap-seek, morph-drag-start) | Duplicates tap/double-tap logic; inconsistent `change.isConsumed` handling → play/pause blink |
| `PlayerUIScaffold.kt` | `detectTapGestures` + `detectVerticalDragGestures` (disabled via flags) | Exists as fallback; `disableTapGestures`/`disableVerticalDragGestures` flags = duct tape |

During morph transition (`miniProgress ∈ [0.01, 0.98]`), **neither** the normal nor floating gesture handlers from `PlayerGestures.kt` are composed — there's a dead zone. The morph drag itself is handled by `PlayerControls.kt`'s hand-rolled state machine, not by `PlayerGestures.kt`.

## Solution

**See full design:** [`gesture-system-handoff.md`](/home/leon/Downloads/gesture-system-handoff.md)

The handoff doc specifies:
- Single `pointerInput` with `awaitEachGesture` per screen region
- 3×3 zone grid for gesture disambiguation (brightness/volume/seek zones)
- Binding table mapping (zone + gesture) → action
- `DiscreteAction` enum for tap/double-tap/drag/vertical-drag
- Configurable via `PlayerMorphConfig` (deadzone, double-tap interval, step sizes)

## Dependencies

- **Phase 2 must be landed** — clean alpha source means gesture changes won't interact with visibility bugs
- **Phase 3 recommended** — `AnimatedVisibility` prevents composition races when gesture handlers change

## Implementation outline

1. Create `PlayerGestureRecognizer.kt` — single recognizer with zone grid
2. Create `PlayerGestureBinding.kt` — (zone, gesture) → action mapping
3. Replace `PlayerGestures.kt` mode-gated boxes with zone-based recognizer
4. Remove hand-rolled `awaitEachGesture` from `PlayerControls.kt`
5. Remove `disableTapGestures`/`disableVerticalDragGestures` from `PlayerUIScaffold.kt`
6. Wire config parameters from `PlayerMorphConfig`

## Verification

- [ ] Play/pause button click doesn't also fire background tap
- [ ] Brightness/volume swipes work in correct zones during all modes
- [ ] Double-tap seek works in all modes
- [ ] Morph drag works during transition (no dead zone)
- [ ] Mini player drag works in FLOATING mode
- [ ] No gesture handler is composed twice for the same region

## Files touched

| File | Action |
|------|--------|
| `PlayerGestureRecognizer.kt` | **New** — unified gesture recognizer |
| `PlayerGestureBinding.kt` | **New** — zone + gesture → action mapping |
| `PlayerGestures.kt` | Replaced (or significantly rewritten) |
| `PlayerControls.kt` | Remove hand-rolled gesture state machine |
| `PlayerUIScaffold.kt` | Remove disable-flags, remove gesture layer |

## Reference

- Full gesture system design: `gesture-system-handoff.md` (see `/home/leon/Downloads/gesture-system-handoff.md`)
