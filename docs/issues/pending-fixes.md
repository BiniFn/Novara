# Pending Fixes

## 1. Filter Panel: Tag Groups Truncation Without Visible Expand Button

**Affected files:**
- `app/src/main/kotlin/org/skepsun/kototoro/filter/ui/sheet/FilterSheetFragment.kt`
- `app/src/main/res/layout/sheet_filter.xml`
- `app/src/main/res/layout/view_filter_field.xml`

**Symptom:** When a source (e.g., Hitomi) has many tags grouped alphabetically, each group shows only the first `TAGS_PREVIEW_LIMIT` (12) tags. The "More" button on `FilterFieldLayout` (`showMoreButton="true"`) should toggle expansion of all groups, but users report it is not visible or not functioning.

**Root cause analysis:**

1. `FilterSheetFragment.kt:295` — `TAGS_PREVIEW_LIMIT = 12` limits visible tags per group.
2. `FilterSheetFragment.kt:448-475` — `renderGroupedTags()` handles two paths:
   - **Flat group** (single "Tags" group): chips rendered in `placeholderChips`, `expanded` flag controls truncation.
   - **Multi-group** (e.g., A, B, C…): dynamic `TextView` + `ChipsView` created per group. Same `expanded` flag applies globally.
3. `FilterSheetFragment.kt:126-128` — `setOnMoreButtonClickListener` on `layoutGenres` toggles `includeTagsExpanded` and re-renders.
4. The `FilterFieldLayout`'s `buttonMore` is a small `MaterialButton` with text "More" (`view_filter_field.xml`). It uses `isInvisible` for visibility — if `showMoreButton="true"`, it should be visible.

**Possible causes for the button not appearing:**
- The `buttonMore` may be too small/unnoticeable (style `Widget.Kototoro.Button.More.Small`).
- The `isInvisible` flag logic may have a state bug where the button remains invisible.
- For multi-group rendering, the dynamic views added via `container.addView()` may push the button off-screen or overlay it.

**Suggested fix directions:**
- Verify `buttonMore` is actually visible with `showMoreButton="true"` (check `isInvisible` logic).
- Consider adding per-group expand/collapse instead of a single global toggle.
- Consider increasing `TAGS_PREVIEW_LIMIT` or making it configurable.
- Make the expand button more prominent (show hidden tag count, use a more visible style).

---

## 2. Settings Page: Scroll Position Lost on Back Navigation

**Affected files:**
- `app/src/main/kotlin/org/skepsun/kototoro/settings/SettingsActivity.kt`

**Symptom:** When navigating from the settings root page to a sub-page (e.g., Appearance, Reader) and pressing back, the root page reloads and scrolls to the top. Only "Extension Management" (`UnifiedSourcesScreen`) preserves its position because it opens as a separate Activity.

**Root cause:**

The settings page uses `renderComposeContent()` which calls `viewBinding.containerCompose.setContent {}` every time a destination is rendered. This destroys the previous Compose tree. When navigating back from a sub-page, the root page is recreated from scratch, losing all scroll state.

**Attempted fix (e903e5b):**

The fix attempted to replace the per-navigation `setContent` calls with a single `setContent` at Activity creation time, using `AnimatedContent` driven by `composePageIsRoot` state to switch between root and sub-page content. The root content (`composeRootContent`) would stay alive while sub-pages (`composeSubpageContent`) overlay.

Key changes:
1. Added state fields: `composePageKey`, `composePageIsRoot`, `composeRootContent`, `composeSubpageContent`
2. Single `setContent` in `onCreate` with `AnimatedContent(targetState = composePageIsRoot)`
3. `renderComposeContent` no longer calls `setContent` — it updates state and increments key
4. Removed `disposeComposition()` call in `closeComposeDestination`

**Why it failed:**

The `closeComposeDestination()` method at line ~1461 hides the ComposeView (`isVisible = false`). When the next destination is rendered, `openComposeDestination` sets `isVisible = true` and calls `renderComposeDestination` which updates the state. However, the `AnimatedContent` approach has issues:

1. `composeRootContent` and `composeSubpageContent` store `@Composable () -> Unit` lambdas. These lambdas capture Compose state references that become stale after the first composition cycle.
2. The `composePageKey` increment forces recomposition via `key()`, but since the root lambda was captured when first created, it references old state.
3. `renderComposeSection` sets `composePageIsRoot = false` **after** `openComposeDestination` already set it — but the order of state updates and recomposition may cause the wrong content to render.

**Better approach:**

Instead of storing composable lambdas, render content based on destination state directly in the single `setContent` block using a `when` expression:

```kotlin
viewBinding.containerCompose.setContent {
    KototoroTheme {
        AnimatedContent(targetState = composeDestination) { dest ->
            when (dest) {
                null -> { /* empty */ }
                SettingsDestination.Root -> { /* SettingsRootScreen(...) */ }
                SettingsDestination.AppearanceSettings -> {
                    SettingsSectionScaffold(title, onBack) { AppearanceSettingsRoute(...) }
                }
                // ... other destinations
            }
        }
    }
}
```

This eliminates the lambda storage problem entirely. Each destination renders its content directly in the composable tree.

**Warning:** This requires moving ~30 destination rendering blocks into the single composable, touching approximately 800 lines of code. The ViewModels and callbacks used by each destination must be accessible at the top level.
