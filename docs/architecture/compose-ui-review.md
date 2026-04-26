# Kototoro Compose UI Architecture Review

## Overview

A structural review of the Kototoro Compose UI implementation (focusing on components like `HomeScreen`) has been conducted against the `@compose-ui` best practices standard. While the codebase demonstrates a good understanding of Jetpack Compose fundamentals (such as theming and derived state), several critical areas require attention to ensure optimal performance, testability, and developer velocity.

## Key Findings & Analysis

### 1. State Hoisting & Unidirectional Data Flow
*   **Current State:** Generally good. Screens like `HomeScreen` correctly receive state (`HomeSummaryState`) and hoist events via callbacks (`onContentClick`, `onSettingsClick`, etc.).
*   **Areas for Improvement:** 
    *   The parameter lists for screen-level composables are extremely long (e.g., `HomeScreen` takes over 15 callback parameters). 
    *   **Recommendation:** Group related UI actions into an `Actions` data class or interface (e.g., `HomeActions(val onContentClick: ..., val onSettingsClick: ...)`), passing a single action dispatcher down the tree.

### 2. Modifier Conventions
*   **Current State:** Internal and smaller components (e.g., `HomeHighlightsSections`, `HomeHeroCard`) correctly accept a `modifier: Modifier = Modifier` as their first optional parameter.
*   **Areas for Improvement:** 
    *   Root-level screen composables (like `HomeScreen`) are missing the `modifier` parameter, taking `contentPadding` instead.
    *   **Recommendation:** Every Composable should expose a `modifier` parameter to allow the caller to adjust its layout footprint without modifying the internal implementation.

### 3. Performance Optimization & Stability
*   **Current State:** 
    *   `remember` is correctly used to cache expensive calculations (e.g., `heroEntries`, `recentSearches`).
    *   `derivedStateOf` is appropriately utilized for observing high-frequency changes without triggering unnecessary recompositions (e.g., `selectedIndex` in `HomeHeroCarousel`).
*   **Areas for Improvement (Lambda Stability):**
    *   In `AppNavGraph.kt`, many lambdas passed to screen composables are instantiated directly in the `composable` block (e.g., `onContentClick = { content, coverBounds, sharedElementKey -> ... }`).
    *   Because these lambdas capture variables like `navController` and are recreated on every recomposition of the NavGraph, they are considered **unstable**. This forces the entire `HomeScreen` and its children to recompose when they otherwise wouldn't need to.
    *   **Recommendation:** Use method references to stable ViewModel functions or `remember { { ... } }` to cache lambdas passed to heavily nested UI trees.

### 4. Theming and Resources
*   **Current State:** Excellent. The UI heavily relies on `MaterialTheme.colorScheme` and `MaterialTheme.typography`. Colors and dimensions are generally derived from the theme rather than hardcoded.

### 5. Compose Previews
*   **Current State:** Previews are almost completely absent from the codebase.
*   **Areas for Improvement:** 
    *   Lack of `@Preview` functions slows down UI iteration and makes it difficult to verify stateless components without running the full application.
    *   **Recommendation:** Establish a convention where every public stateless Composable has a corresponding private `@Preview` function, utilizing mock data to verify Light/Dark mode rendering.

## Next Steps

To elevate the Compose UI quality, we should implement a phased cleanup:
1.  **Quick Wins:** Introduce `modifier` parameters to all root-level screens.
2.  **Performance Check:** Audit lambda stability in `AppNavGraph` and replace inline unstable lambdas with remembered blocks or ViewModel delegates.
3.  **Developer Experience:** Begin adding `@Preview` annotations to core, highly reused components (like Buttons, TopBars, and List Items) to build a robust component catalog.
