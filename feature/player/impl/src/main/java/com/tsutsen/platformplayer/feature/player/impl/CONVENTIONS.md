# Player UI Composition Conventions

## Rules

1. **Never `if`-gate animated content.** Use `AnimatedVisibility` for anything whose
   visibility is driven by a progress/alpha value. `if` is for structural/enum state only
   (e.g., `PlayerUiState` mode dispatch).

2. **One source of truth for alpha.** All visibility values come from
   `computeControlsVisibility()` in `PlayerVisibility.kt`. Never compute alpha independently
   in a composable.

3. **One gesture owner per region.** No stacked recognizers with disable-flags. Each gesture
   region gets exactly one `pointerInput` block.

4. **`graphicsLayer` for transforms, `AnimatedVisibility` for visibility.** Use
   `graphicsLayer { alpha = ... }` only for visual effects on content that is always composed.
   For content that fades in/out, use `AnimatedVisibility` which guarantees exit transitions
   complete before removing from composition.

5. **Pure functions for geometry and visibility.** `computeVideoLayout()` and
   `computeControlsVisibility()` are pure — no Compose state, no side effects, unit-testable.
