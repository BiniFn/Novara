# Kototoro Compose Navigation Review

## Overview

A review of the Jetpack Compose navigation implementation within the Kototoro application has been conducted, referencing the `@compose-navigation` standard workflow. While Kototoro successfully utilizes `NavHost` and Jetpack Compose Navigation for core routing, it currently relies on legacy string-based patterns rather than the modern type-safe approaches introduced in recent Navigation Compose versions.

## Current State Analysis

Based on the analysis of `AppNavGraph.kt`, `KototoroApp.kt`, and `SearchNavigation.kt`:

1.  **String-Based Routing (Legacy Pattern)**
    *   **Current:** Routes are defined using raw strings (e.g., `startDestination = "home"`, `composable("explore")`, `navController.navigate("updated")`).
    *   **Issue:** This approach is fragile, prone to typos, and lacks compile-time safety when defining and navigating between screens.

2.  **Argument Passing Anti-Patterns**
    *   **String interpolation for complex arguments:** `SearchNavigationRequest` parameters are appended to a URL-like string (`?query={query}&kind={kind}...`), requiring manual URL encoding/decoding (`Uri.encode()`) and complex `navArgument` declarations in `AppNavGraph`.
    *   **Global Side-Effect for Complex Data:** The `details` screen relies on a global singleton (`PendingDetailsNavigation.set(content, sharedElementKey)`) to pass complex `Content` objects across destinations.
    *   **Issue:** The `@compose-navigation` standard strictly recommends passing only IDs/primitives as arguments and retrieving complex data from the data layer within the destination's ViewModel. Global state passing breaks standard Navigation state restoration and deep linking capabilities.

3.  **Adaptive Navigation**
    *   **Current:** Custom `KototoroBottomNav` is implemented and positioned manually within a `Box`, hooked up via `currentBackStackEntryAsState()`.
    *   **Issue:** It works effectively for phones but requires custom logic for tablet/desktop form factors (navigation rails).

---

## Recommended Refactoring Strategy

To align Kototoro with standard best practices and improve architectural robustness, the following changes are recommended:

### 1. Migrate to Type-Safe Navigation (Kotlinx Serialization)

Replace string routes with `@Serializable` Kotlin classes and objects.

```kotlin
// Define type-safe routes
@Serializable data object Home
@Serializable data object Explore
@Serializable data object History
@Serializable data object Favorites
@Serializable data object Bookmarks

@Serializable
data class Search(
    val query: String,
    val kind: String,
    // other primitives
)

@Serializable
data class Details(
    val contentId: String, // Pass ID instead of complex object
    val sourceName: String
)
```

Update the `NavHost` to use these type-safe definitions:

```kotlin
NavHost(navController = navController, startDestination = Home) {
    composable<Home> { HomeScreen(...) }
    composable<Explore> { ExploreHostRoute(...) }
    // ...
}
```

### 2. Refactor Argument Passing for `DetailsScreen`

Instead of relying on `PendingDetailsNavigation.set(content)`, the `Details` route should accept the unique identifiers for the `Content` (e.g., source ID and content URL/ID).

*   **Action:** Modify `DetailsViewModel` to use `savedStateHandle.toRoute<Details>()` to extract the IDs, and then fetch the `Content` directly from the repository.
*   **Benefit:** This ensures the Details screen is fully independent, deep-linkable, and properly handles process death restoration.
*   *Note:* The shared element transition key (`sharedElementKey`) can still be deterministically generated using the source name and cover URL on both sides of the navigation.

### 3. Simplify Search Navigation

Remove the boilerplate `SearchNavigation` route string builder and `navArgument` lists. Navigation Compose natively supports serializing complex data classes (as long as they contain primitive/serializable fields).

```kotlin
// Navigate safely
navController.navigate(Search(
    query = request.query,
    kind = request.kind.name,
    pinnedOnly = request.pinnedOnly
))

// In AppNavGraph
composable<Search> { backStackEntry ->
    val searchParams: Search = backStackEntry.toRoute()
    val viewModel = hiltViewModel<SearchViewModel>()
    // ...
}
```

### 4. Evaluate Adaptive Navigation UI (`NavigationSuiteScaffold`)

Consider migrating `KototoroApp.kt`'s custom bottom navigation placement to use `NavigationSuiteScaffold`. This official Material 3 component automatically switches between a `NavigationBar` (bottom) on compact screens and a `NavigationRail` (side) on medium/expanded screens without manual calculation.

## Next Steps

If you would like to proceed with modernizing the navigation architecture, we can begin by introducing the `kotlinx-serialization-json` dependency and incrementally migrating leaf nodes (like `home` or `settings`) before tackling complex argument routes like `search` and `details`.
