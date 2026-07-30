# 9-Sector Gesture System — Implementation Plan

## Overview

Replace the current stacked-gesture-box approach with a unified, configurable 9-sector gesture system driven by continuous gesture frames.

## Current State

`PlayerGestures.kt` uses 3 stacked `pointerInput` Boxes per mode with hardcoded actions:
- Vertical drag → brightness (left half) / volume (right half)
- Double-tap on left/right thirds → seek ±5s
- Long-press drag → 2x speed hold

`PlayerControls.kt` has its own `awaitEachGesture` for morph drag (downward swipe → minimize) + tap-to-toggle-controls.

Problems: hardcoded, non-configurable, gesture conflicts between overlapping detectors.

## New Architecture

```
PlayerContent (single gesture authority)
  │
  ├── PlayerGestureSystem  ← unified awaitEachGesture, replaces PlayerGestures
  │     ├── Sector detection (touch position → 3×3 grid)
  │     ├── Gesture recognition (swipe/hold/double-tap)
  │     ├── Morph drag precedence (NORMAL/COMPACT: downward swipe → minimize)
  │     └── Frame dispatcher → GestureActionHandler
  │
  └── PlayerControls (pure rendering, no gesture detection)
```

### Principles
- One `awaitEachGesture` loop owns all pointer events on the player surface
- Morph drag checked first; if it matches, 9-sector gestures skipped for that gesture
- `isScrubbing = true` blocks all 9-sector gesture recognition
- Only one action fires per gesture cycle
- Floating mode drag handled separately within the same system

## Continuous Gesture Frame Model

Gestures emit a stream of `GestureFrame` objects instead of discrete events. Each frame carries delta info for action strength control.

```
DOWN → WAITING
  │
  ├─ movement > SWIPE_THRESHOLD (30px) → SWIPE
  │   → START frame (delta=0)
  │   → ACTIVE frames (instantDelta, totalDelta each frame)
  │   → END frame
  │
  └─ time > HOLD_TIMEOUT (500ms) AND movement < JITTER_THRESHOLD (15px) → HOLD
      → START frame (modulateDelta=0)
      → ACTIVE frames (totalDelta = offset from hold start position)
      → END frame
```

### Delta Semantics

| Type | `instantDelta` | `totalDelta` | `elapsedMs` |
|------|---------------|-------------|-------------|
| SWIPE_VERTICAL | px since last frame | total px from swipe start | time since swipe started |
| SWIPE_HORIZONTAL | px since last frame | total px from swipe start | time since swipe started |
| HOLD | movement since last frame | offset from hold start (modulation) | time since hold activated |

### Hold + Swipe Modulation

While holding, `totalDelta` tracks finger drift from the initial hold position. Actions like SPEEDUP use this to modulate:
- Hold → base speed 2x
- Hold + swipe right → speed increases from 2x
- Hold + swipe left → speed decreases from 2x
- Return to hold position → back to 2x

## Gesture Configs Per Mode

### FULLSCREEN
| Sector | HOLD | DOUBLE_TAP | SWIPE_H | SWIPE_V |
|--------|------|-----------|---------|---------|
| TOP_LEFT | SPEEDUP | REWIND_BACK | NONE | MORPH_TO_FLOATING |
| TOP_CENTER | NONE | NONE | NONE | MORPH_TO_FLOATING |
| TOP_RIGHT | SPEEDUP | REWIND_FORWARD | NONE | MORPH_TO_FLOATING |
| MIDDLE_LEFT | SPEEDUP | REWIND_BACK | NONE | BRIGHTNESS |
| MIDDLE_CENTER | NONE | NONE | NONE | NONE |
| MIDDLE_RIGHT | SPEEDUP | REWIND_FORWARD | NONE | VOLUME |
| BOTTOM_LEFT | SPEEDUP | REWIND_BACK | NONE | BRIGHTNESS |
| BOTTOM_CENTER | NONE | NONE | NONE | NONE |
| BOTTOM_RIGHT | SPEEDUP | REWIND_FORWARD | NONE | VOLUME |

### NORMAL — same as FULLSCREEN, except MORPH_TO_FLOATING instead of VOLUME and BRIGHTNESS

### COMPACT — same as NORMAL

### FLOATING — all NONE

## Actions

| Action | Trigger | Behavior |
|--------|---------|----------|
| NONE | any | no-op |
| VOLUME | swipe | adjust system volume proportional to delta |
| BRIGHTNESS | swipe | adjust screen brightness proportional to delta |
| SPEEDUP | hold | 2x base, ± modulate with horizontal swipe |
| SPEEDDOWN | hold | 0.5x base, ± modulate with horizontal swipe |
| REWIND_FORWARD | double-tap | seek +5s |
| REWIND_BACK | double-tap | seek -5s |
| CONTEXT_MENU | double-tap | show context menu (stub) |
| MORPH_TO_FLOATING | swipe-down | minimize player |
| MORPH_TO_FULLSCREEN | swipe-up | enter fullscreen (stub) |

## Constants

| Constant | Value | Purpose |
|----------|-------|---------|
| SWIPE_THRESHOLD | 30px | movement to recognize swipe vs jitter |
| HOLD_JITTER_THRESHOLD | 15px | max drift before hold still activates |
| HOLD_TIMEOUT_MS | 500ms | time to trigger hold |
| DOUBLE_TAP_TIMEOUT_MS | 300ms | max time between taps for double-tap |
| TOUCH_SLOP | 12px | tap drift tolerance |

## Files

### New (feature/player/impl/src/main/java/.../gesture/)

| File | Purpose |
|------|---------|
| `GestureSector.kt` | 3×3 sector enum + `fromPosition()` |
| `GestureType.kt` | SWIPE_VERTICAL, SWIPE_HORIZONTAL, DOUBLE_TAP, HOLD |
| `GestureAction.kt` | Action enum (NONE, VOLUME, BRIGHTNESS, SPEEDUP, etc.) |
| `GestureConfig.kt` | SlotConfig + GestureConfig + GestureConfigs |
| `GestureFrame.kt` | GesturePhase + GestureFrame + InstantActionEvent |
| `DefaultGestureConfigs.kt` | Factory for 4-mode default configs |
| `GestureActionHandler.kt` | Frame-based handler interface + composite impl |
| `PlayerGestureSystem.kt` | Unified gesture composable |

### Modified

| File | Change |
|------|--------|
| `PlayerContent.kt` | Replace `PlayerGestures` with `PlayerGestureSystem` |
| `PlayerView.kt` | Build GestureConfigs + wire handler to ViewModel |
| `PlayerControls.kt` | Strip gesture detection (morph drag → PlayerGestureSystem) |

### Deleted

| File |
|------|
| `PlayerGestures.kt` |

## Timeline Blocking

`isScrubbing` flag passed to PlayerGestureSystem. When true, gesture loop consumes pointer events without dispatching 9-sector actions. Timeline Slider handles its own interaction independently.

## Future: Settings Integration

`GestureConfigs` is serializable. Future extension:
1. Store in `AppPreferences.gestures` via DataStore
2. Settings screen reads/writes per-sector configs
3. `buildDefaultGestureConfigs()` = first-launch defaults
