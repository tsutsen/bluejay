# Player Gesture System — Architectural Handoff

## Why

This session traced five bugs in the player morph/controls implementation back to a
recurring root cause: **gesture handling is currently split across three places**
(`PlayerUIScaffold.kt`'s built-in `detectTapGestures`/`detectVerticalDragGestures`,
disabled via `disableTapGestures`/`disableVerticalDragGestures` flags; `PlayerControls.kt`'s
hand-rolled `awaitEachGesture` state machine; and `PlayerGestures.kt`, referenced
throughout but not yet reviewed in this session). The hand-rolled recognizer in
`PlayerControls.kt` didn't check `change.isConsumed`, which is what caused controls to
blink on every Play/Pause press — the button's own click and the background tap
detector both fired for the same touch.

This doc proposes replacing all of that with **one recognizer, one zone model, one
binding table** — designed from the start to support 9 zones and per-zone/row/column/
global gesture customization, so it's ready for a future settings UI without a rewrite.

---

## 1. Zone model — 3×3 grid

```kotlin
enum class GestureRow { TOP, MIDDLE, BOTTOM }
enum class GestureColumn { LEFT, CENTER, RIGHT }

data class GestureZone(val row: GestureRow, val column: GestureColumn) {
    companion object {
        val ALL: List<GestureZone> =
            GestureRow.entries.flatMap { r -> GestureColumn.entries.map { c -> GestureZone(r, c) } }
    }
}
```

Zone is resolved **once, at touch-down**, from the initial position — not re-evaluated
as the finger moves. This matters: if a drag starts in `BOTTOM` and drifts up past a
row boundary mid-gesture, the binding shouldn't switch out from under the user.

```kotlin
/**
 * Pure zone resolution — same spirit as PlayerGeometry.kt's computeVideoLayout:
 * no Compose state, single source of truth, unit-testable without a gesture harness.
 */
fun resolveGestureZone(position: Offset, areaWidth: Float, areaHeight: Float): GestureZone {
    val row = when {
        position.y < areaHeight / 3f -> GestureRow.TOP
        position.y > areaHeight * 2f / 3f -> GestureRow.BOTTOM
        else -> GestureRow.MIDDLE
    }
    val column = when {
        position.x < areaWidth / 3f -> GestureColumn.LEFT
        position.x > areaWidth * 2f / 3f -> GestureColumn.RIGHT
        else -> GestureColumn.CENTER
    }
    return GestureZone(row, column)
}
```

---

## 2. Gesture types — discrete vs. continuous

Not all gestures are the same shape. Tap/double-tap/long-press fire once; drags need a
start/delta-stream/end. Keeping these as two separate lookup dimensions avoids hacking
continuous behavior on top of a single-callback discrete model (roughly what happens
today).

```kotlin
enum class DiscreteGesture { TAP, DOUBLE_TAP, LONG_PRESS_START, LONG_PRESS_END }
enum class ContinuousGesture { VERTICAL_DRAG, HORIZONTAL_DRAG }

fun interface DiscreteAction {
    fun invoke(zone: GestureZone, position: Offset)
}

interface ContinuousAction {
    fun onStart(zone: GestureZone, position: Offset)
    fun onDelta(deltaPx: Float)
    fun onEnd()
}
```

---

## 3. Binding model & precedence

Bindings can be declared at four levels of specificity. Lookup falls back from most to
least specific, **independently per gesture type** — a zone can inherit its continuous
drag behavior from its row while still getting its discrete double-tap behavior from its
column. This is what lets "long-press = 2× speed on top/bottom rows" and "double-tap
left/right columns = seek" coexist without either being defined 9 times.

```kotlin
data class ZoneBindings(
    val discrete: Map<DiscreteGesture, DiscreteAction> = emptyMap(),
    val continuous: Map<ContinuousGesture, ContinuousAction> = emptyMap()
)

data class GestureBindings(
    val byZone: Map<GestureZone, ZoneBindings> = emptyMap(),
    val byRow: Map<GestureRow, ZoneBindings> = emptyMap(),
    val byColumn: Map<GestureColumn, ZoneBindings> = emptyMap(),
    val global: ZoneBindings = ZoneBindings()
) {
    // Precedence: zone-specific > row > column > global. Row beats column
    // deliberately — see the TOP/BOTTOM-row-vs-LEFT/RIGHT-column overlap
    // in the default table below, where row wins for drag and column wins
    // for double-tap (different gesture types, no actual conflict there).
    fun resolveDiscrete(zone: GestureZone, gesture: DiscreteGesture): DiscreteAction? =
        byZone[zone]?.discrete?.get(gesture)
            ?: byRow[zone.row]?.discrete?.get(gesture)
            ?: byColumn[zone.column]?.discrete?.get(gesture)
            ?: global.discrete[gesture]

    fun resolveContinuous(zone: GestureZone, gesture: ContinuousGesture): ContinuousAction? =
        byZone[zone]?.continuous?.get(gesture)
            ?: byRow[zone.row]?.continuous?.get(gesture)
            ?: byColumn[zone.column]?.continuous?.get(gesture)
            ?: global.continuous[gesture]
}
```

### Default binding table

| Scope | Gesture | Action |
|---|---|---|
| Row: TOP | continuous `VERTICAL_DRAG` | drive `fullscreenProgress` (pull down to exit fullscreen) |
| Row: TOP | discrete `LONG_PRESS` | 2× speed |
| Row: BOTTOM | continuous `VERTICAL_DRAG` | drive `miniProgress` (existing minimize-to-floating morph) |
| Row: BOTTOM | discrete `LONG_PRESS` | 2× speed |
| Column: LEFT | continuous `VERTICAL_DRAG` | brightness *(only reachable at MIDDLE_LEFT — TOP/BOTTOM rows win by precedence)* |
| Column: RIGHT | continuous `VERTICAL_DRAG` | volume *(only reachable at MIDDLE_RIGHT, same reason)* |
| Column: LEFT | discrete `DOUBLE_TAP` | seek −5s *(applies to all 3 rows — matches current width-thirds seek behavior)* |
| Column: RIGHT | discrete `DOUBLE_TAP` | seek +5s |
| Column: CENTER | discrete `DOUBLE_TAP` | toggle fullscreen |
| Global | discrete `TAP` | toggle `controlsVisible` |

Net effect on the 3×3 grid:

```
┌─────────────┬─────────────┬─────────────┐
│  fullscreen  │  fullscreen  │  fullscreen  │   TOP row: drag = fullscreen exit,
│  drag+2xhold │  drag+2xhold │  drag+2xhold │            long-press = 2x, all cols
├─────────────┼─────────────┼─────────────┤
│ brightness   │  (tap only)  │   volume     │   MIDDLE row: brightness/volume by
│ + seek -5s   │ + fullscreen │ + seek +5s   │   column, seek/fullscreen by column
├─────────────┼─────────────┼─────────────┤
│  minimize    │  minimize    │  minimize    │   BOTTOM row: drag = minimize,
│  drag+2xhold │  drag+2xhold │  drag+2xhold │            long-press = 2x, all cols
└─────────────┴─────────────┴─────────────┘
```

**Open question for you to confirm:** should `LONG_PRESS → 2× speed` stay scoped to
just the top/bottom rows (as tabled above, matching your literal request), or should it
also apply as a `global` fallback so MIDDLE row keeps today's existing behavior (2×
speed works everywhere right now)? Either is a one-line change; I defaulted to the
narrower row-scoped version since that's what you described, but it's a real behavior
change from what you have today and worth deciding deliberately rather than by default.

---

## 4. The recognizer's job — purely mechanical

One `Modifier`/composable, one `awaitEachGesture` loop, applied once per player region.
It should never know what any gesture *means* — it only classifies and dispatches:

1. On down: `requireUnconsumed = true` (this is the actual fix for the blink bug —
   a touch already consumed by a child button never enters this loop at all).
2. Resolve `zone` once from the down position.
3. Track movement; classify dominant axis + direction once past touch-slop; disambiguate
   tap vs. double-tap (timeout + distance) vs. long-press (hold-still timer) vs. drag.
4. Defensively re-check `change.isConsumed` before claiming the gesture, for any child
   that consumes after down (e.g. a long-press ripple) but before we've claimed it
   ourselves.
5. Look up the resolved action via `bindings.resolveDiscrete(...)` /
   `resolveContinuous(...)` and drive it. No `if (isPlaying) ... else ...`-style
   business logic lives here — that all lives in the `DiscreteAction`/`ContinuousAction`
   implementations supplied by the binding table.

This directly replaces:
- `PlayerUIScaffold.kt`'s `detectTapGestures`/`detectVerticalDragGestures` and both
  `disable*Gestures` flags (deleted entirely, not just disabled)
- `PlayerControls.kt`'s hand-rolled `awaitEachGesture` block (~70 lines, deleted)
- `PlayerGestures.kt` (folded in — please share this file so nothing in it gets lost
  in the migration; it wasn't reviewed this session)

---

## 5. Suggested file layout

```
feature/player/impl/gesture/
  GestureZone.kt            — GestureRow, GestureColumn, GestureZone, resolveGestureZone
  GestureBindings.kt        — DiscreteGesture, ContinuousGesture, actions, GestureBindings
  DefaultPlayerBindings.kt  — the concrete GestureBindings matching today's app behavior
  PlayerGestureRecognizer.kt — the single Modifier/composable implementing the state machine
```

Later, a settings-driven binding table becomes: persist `(GestureZone, GestureType) →
ActionId`, resolve `ActionId → DiscreteAction`/`ContinuousAction` through a small
registry, and construct a `GestureBindings` from it — no change needed to
`PlayerGestureRecognizer.kt` itself.

---

## 6. How this fits the rest of the session's suggestions

- **Single source of truth for alpha** (separately proposed `computeControlsVisibility`)
  and this gesture model are the same pattern applied twice: pure functions /
  data-driven tables instead of logic scattered and duplicated across files.
- **`AnimatedVisibility` over hand-rolled alpha** remains a separate, independent fix —
  not affected by this gesture work.
- **Single owner for `hideControlsJob`**: once `TAP → toggle controlsVisible` is a
  single `DiscreteAction` in the global binding, that's a natural place to also own the
  auto-hide timer restart, consolidating the two current writers into one.

---

## Open questions before implementation

1. Long-press 2× speed: row-scoped only (as tabled) or also global fallback for MIDDLE row?
2. `PlayerGestures.kt` — please share so its current behavior can be folded in rather
   than re-guessed.
3. Any existing gesture not covered above (e.g. horizontal drag / swipe-to-dismiss,
   pinch-to-zoom) that should be reserved a slot in the binding model now, even if
   unbound by default?
