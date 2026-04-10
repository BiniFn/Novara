# Phase 3 & 4: Seamless Compose Chrome & Standard UI

The final phase of migrating the main activity interface focuses on fully transitioning the "Chrome" (Top App Bar and Search Elements) to pure Jetpack Compose while establishing a clean boundary that shields legacy `Fragments` from structural dependency logic. We are completely ripping out outdated glue bindings (XML Behaviors) in favor of standard reactive Compose code.

## Proposed Changes

### 1. Unified Overlay Root (`activity_main.xml`)
We will completely eliminate `CoordinatorLayout`, `AppBarLayout`, `MaterialToolbar`, and `SearchView`. Instead, the layout becomes a fundamentally uncoupled stack:
*   **`FragmentContainerView` (Bottom layer)**: Native Fragment layer. Ensures pure, synchronous stability during Activity/Fragment recreation without causing `FragmentManager` timing crashes. 
*   **`ComposeView` (Overlay)**: We render `KototoroTopBar` and `KototoroBottomNav` transparently over the entire screen dynamically.

### 2. Standardized Jetpack Compose Search & TopBar
*   Create a purely declarative `KototoroTopBar.kt`.
*   Replace Android's deprecated `SearchView` with Compose's `SearchBar` standard component.
*   Rewrite the autocomplete `SearchSuggestionAdapter` and `RecyclerView` using a slick Compose `LazyColumn` for search predictions overlay.

### 3. Deep Fragment Spacing Unification
*   **Delete `AppBarOwner` interface**: Currently, various Fragments (like `DiscoverFragment`, `HomeFragment`) literally pull the Activity's `AppBarLayout` object generically to read its dimension so they can insert spacing into their internal Legacy components or forcefully collapse it.
*   **Inject Dynamic Insets Instead**: The `KototoroApp` Compose Shell will globally calculate the active Top and Bottom Bar physical dimension bounds (accounting for Floating layout, Search Bar expansion, and Blur Modes) and assign it uniformly as a generic `WindowInset`/`Padding` dimension back down to the Base container.
*   Legacy Fragments will now strictly depend only on universal Android View bounds and won't know (or care) if the App Bar is implemented via XML, Compose, or Magic. 

### 4. Reactive Nested Scrolling (Without Glue)
Since `CoordinatorLayout` will be discarded, the Legacy Fragment's nested scrolls will no longer automatically link to Compose.
*   We'll leverage Android's native `NestedScrollingParent3` natively up at the `FrameLayout` level strictly to monitor raw global layout swipes.
*   We intercept the Y-offsets and dispatch them abstractly into a `MutableFloatState` which the Compose `KototoroTopBar` seamlessly consumes to orchestrate its own collapsing animations.

## User Review Required

> [!WARNING]
> This phase fundamentally severs the old Android View connection tying `HomeFragment`, `ContentListFragment`, and `SettingsActivity` to the `AppBarLayout`. While it vastly simplifies the application architectural boundaries, compiling will initially break widely across the app package before I implement the generic fix.

> [!IMPORTANT]
> The Kotatsu app has a custom Autocomplete overlay `SearchSuggestionAdapter` used exclusively when clicking the `SearchBar`. I intend to port the UI of these autocomplete drops directly to Jetpack Compose logic within the new `SearchBar` wrapper rather than embedding the old RecyclerView. 

## Verification Plan

### Automated Tests
1. Verify immediate compilation success across all 15 previously-tangled fragment classes.
2. Ensure Zero-Crash stability on device rotation (retaining the Fix achieved in Phase 2).

### Manual Verification
1. We will verify the `TopAppBar` appropriately slides completely out of view precisely when scrolling down through manga sources.
2. Ensure Edge-To-Edge list elements aren't accidentally completely hidden underneath the Compose transparent overlays using the uniform Padding approach.
