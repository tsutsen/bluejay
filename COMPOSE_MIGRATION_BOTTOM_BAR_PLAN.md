# Bottom Bar Port Plan

## Current Architecture (XML)
- `MenuBottomBarFragment` → `MenuBottomBarView` (LinearLayout)
- Two parts:
  1. `bottom_bar_buttons` (LinearLayout, 52dp height) — main nav buttons
  2. `more_overlay` (FrameLayout overlay) — additional buttons + airplane/privacy toggles
- Dynamic button visibility based on screen width
- `ButtonDefinition` data class defines each button
- `MenuButton` / `MenuButtonItemViewHolder` render individual buttons
- Animations for more overlay (alpha, translationY)

## Compose Implementation Plan

### 1. `BottomBar.kt` — Main composable
- `@Composable fun BottomBar(...)` 
- Takes list of `BottomNavItem` and `onNavigate` callback
- Uses `Row` with `weight(1f)` for equal-width buttons
- Height: 52dp (match XML)
- Background: uses `?attr/colorSurface` or drawable

### 2. `BottomNavItem.kt` — Data model
```kotlin
data class BottomNavItem(
    val id: Int,
    val iconActive: String,
    val label: String,
    val isActive: Boolean,
    val onClick: () -> Unit
)
```

### 3. `BottomBarButton.kt` — Individual button
- `@Composable fun BottomBarButton(item: BottomNavItem)`
- Icon + text, vertical layout
- Active state: full opacity vs 50% opacity
- Uses `MaterialIconView` wrapper or Compose icon

### 4. `MoreOverlay.kt` — More menu overlay
- `@Composable fun MoreOverlay(...)`
- Semi-transparent background
- List of additional items (Settings, FAQ, Privacy, Buy)
- Slide-up animation using `animateEnterExit` or `AnimatedVisibility`

### 5. Integration with MainActivity
- Replace `FragmentContainerView` for bottom bar with `CompositionLocalProvider`
- Pass navigation callbacks from MainActivity
- Handle dynamic button visibility based on screen width

## Key Decisions
1. **Icon handling**: Use Compose `Icon` with painter from asset/resource
2. **Animations**: Use `AnimatedVisibility` + `slideInVertically`/`slideOutVertically`
3. **Button visibility**: Calculate based on `LocalConfiguration.current.screenWidthDp`
4. **Active state**: Pass `isActive` lambda from MainActivity
5. **More menu**: Simple column of items with icons, no RecyclerView needed

## Exit Criteria
- All current bottom bar buttons visible and functional
- More overlay opens/closes with animation
- Active button highlighted correctly
- Dynamic button visibility based on screen width
- Privacy mode toggle works
- No regressions in existing functionality
