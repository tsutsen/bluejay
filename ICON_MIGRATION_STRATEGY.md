# Material Symbols Icon Migration Strategy

## Goal
Replace all app icons with Material Symbols Rounded gradually, one screen/component at a time.

## Approach
**Never break the build.** Each commit must compile successfully.

## Infrastructure (Do First - One-Time Setup)

### 1. Add the Material Symbols Font
- Download `MaterialSymbolsRounded[FILL,GRAD,opsz,wght].ttf` (15MB variable font)
- Place in `app/src/main/res/font/`
- No code changes needed - just add the font file

### 2. Create Icons.kt Mapping File
- Create `app/src/main/java/com/futo/platformplayer/utils/Icons.kt`
- Maps icon names to Unicode codepoints
- Example:
```kotlin
object Icons {
    private val map = mapOf(
        "ic_home" to "\uE88A",
        "ic_settings" to "\uE8B8",
        // ... add icons as needed
    )
    operator fun get(name: String): String = map[name] ?: ""
}
```

### 3. Create MaterialIconView Widget
- Create `app/src/main/java/com/futo/platformplayer/widget/MaterialIconView.kt`
- Extends `AppCompatTextView`
- Reads `app:iconName` and `app:iconSize` attributes
- Sets text to the Material Symbol Unicode character
- No other code changes needed

### 4. Add Custom Attributes
- Add to `app/src/main/res/values/attrs.xml`:
```xml
<declare-styleable name="MaterialIconView">
    <attr name="iconName" format="string" />
    <attr name="iconSize" format="dimension" />
</declare-styleable>
```

## Migration Phases

### Phase 1: Simple ImageView Replacements (Easy)
**Goal:** Replace static icons in layouts that don't change at runtime.

**Pattern:**
```xml
<!-- Before -->
<ImageView android:src="@drawable/ic_home" ... />

<!-- After -->
<com.futo.platformplayer.widget.MaterialIconView
    app:iconName="ic_home"
    app:iconSize="24sp" ... />
```

**Files to target:**
- `view_bottom_menu_button.xml` - Tab bar icons
- `view_bottom_more_menu_button.xml` - More menu icons
- `fragment_overview_bottom_bar.xml` - Bottom bar toggle icons
- Any layout with static icons that don't change

**Steps:**
1. Replace `<ImageView>` with `<com.futo.platformplayer.widget.MaterialIconView>`
2. Change `android:src="@drawable/ic_xxx"` to `app:iconName="xxx"`
3. Add `xmlns:app="http://schemas.android.com/apk/res-auto"` if missing
4. Test on device
5. Commit

### Phase 2: BigButton/ShortsButton Kotlin Code (Medium)
**Goal:** Update BigButton and ShortsButton to accept icon names instead of resource IDs.

**Current API:**
```kotlin
BigButton(context, "Title", "Subtext", R.drawable.ic_icon) { ... }
```

**New API:**
```kotlin
BigButton(context, "Title", "Subtext", "icon_name") { ... }
```

**Steps:**
1. Modify `BigButton.kt` constructor to accept `String` for icon
2. Modify `BigButton.kt` `withIcon()` method to use `MaterialIconView.setIconName()`
3. Update `ShortsButton.kt` similarly
4. Update callers one file at a time:
   - `HomeFragment.kt`
   - `SourceDetailFragment.kt`
   - `ShortView.kt`
   - `LibraryFilesFragment.kt`
   - etc.
5. Test each file after updating
6. Commit after each file or group of files

### Phase 3: Custom Views with Icon Attributes (Medium)
**Goal:** Update custom views that have their own icon attributes.

**Examples:**
- `PillButton` with `app:pillIcon`
- `ToggleBar.Toggle` with icon parameter
- `NoResultsView` with icon parameter

**Steps:**
1. Update the custom view's attribute definition from `reference` to `string`
2. Update the view's Kotlin code to use `MaterialIconView` or `setIconName()`
3. Update XML layouts that use the custom view
4. Test and commit

### Phase 4: Programmatic Icon Setting (Hard)
**Goal:** Replace `setImageResource(R.drawable.ic_xxx)` calls in Kotlin code.

**Current Pattern:**
```kotlin
imageView.setImageResource(R.drawable.ic_home)
buttonIcon.setImageResource(R.drawable.ic_settings)
```

**New Pattern:**
```kotlin
// Option A: Change view type to MaterialIconView in XML
(materialIconView as MaterialIconView).setIconName("ic_home")

// Option B: Keep ImageView, use tint approach (fallback)
imageView.setImageResource(R.drawable.ic_home) // Keep original drawable
imageView.imageTintList = ColorStateList.valueOf(color)
```

**Strategy:**
- For views that can be changed to `MaterialIconView` in XML, do Phase 1 first
- For views that must stay as `ImageView` (e.g., custom drawables, animations), keep the drawable files
- Gradually replace `setImageResource()` with `setIconName()` as views are migrated

### Phase 5: Cleanup (Last)
**Goal:** Remove unused drawable files.

**Steps:**
1. Run grep to find any remaining `R.drawable.ic_*` references
2. Verify each one is either:
   - Still needed (keep the file)
   - Can be replaced with Material Symbol
   - Is a special drawable (gradient, shape, etc.) that can't be replaced
3. Delete unused icon drawable files
4. Final build test

## File-by-File Migration Checklist

### Priority Order (by impact/complexity):
1. **Tab Bar** (`MenuBottomBarFragment.kt`) - High visibility, moderate complexity
2. **Video Player Controls** - Critical for UX
3. **Home Screen** (`HomeFragment.kt`) - High visibility, moderate complexity
4. **Source Detail** (`SourceDetailFragment.kt`) - Moderate visibility, moderate complexity
5. **Shorts** (`ShortView.kt`) - High visibility, high complexity
6. **Library** (`LibraryFragment.kt`, `LibraryFilesFragment.kt`) - Moderate visibility
7. **Settings** (`Settings.kt`) - Low visibility, low priority
8. **Dialogs** (`UIDialogs.kt`) - Low visibility, low priority
9. **Other Fragments/Activities** - As needed

## Testing Checklist
- [ ] Build succeeds (`./gradlew :app:compileUnstableDebugKotlin`)
- [ ] APK installs on device
- [ ] Icons render correctly in light mode
- [ ] Icons render correctly in dark mode
- [ ] Icons adapt to theme color (tinting works)
- [ ] No layout issues or overlapping elements
- [ ] All screens navigate correctly

## Rollback Plan
If a commit breaks the build:
1. `git revert <commit-hash>`
2. Fix the issue
3. Re-commit with clear message about what was fixed

## Notes
- Keep the original drawable files until all references are migrated
- Use `git grep 'R\.drawable\.ic_'` to find remaining references
- Test on the target device (AYN Thor) after each commit
- The Material Symbols font is large (~15MB) - consider APK size impact
