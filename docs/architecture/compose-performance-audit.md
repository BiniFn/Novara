# Kototoro Compose Performance Audit

## Overview

Based on the `@[/compose-performance-audit]` workflow, a code-first review of the Kototoro application (specifically the `Home` module) has been conducted to identify performance bottlenecks, layout thrashing, and recomposition storms.

## Findings & Root Cause Analysis

### 1. Recomposition Storms due to Unstable State (Critical)
**Symptom:** The entire `HomeScreen` recomposes frequently, even when unrelated state changes occur in the parent navigation graph.
**Root Cause:** The `HomeSummaryState` data class (and its nested types like `HomeRecentItem`, `HomeUpdateItem`) contains `List<T>` properties. In Jetpack Compose, standard Kotlin `List` collections are considered **unstable**. Because `HomeSummaryState` lacks an `@Immutable` or `@Stable` annotation, the Compose compiler marks the entire state object as unstable. 
Consequently, `HomeScreen(state: HomeSummaryState)` is deemed "skippable: false", forcing a full recomposition on every parent pass.

### 2. Expensive Work in Composition (High)
**Symptom:** UI jank during scrolling or when the home screen refreshes.
**Root Cause:** Inside `HomeHighlightsSections`, lists are mapped to display items using `remember(historyItems)`:
```kotlin
val historyDisplayItems = remember(historyItems) {
    historyItems.map { HomeCoverDisplayItem(...) }
}
```
Because `historyItems` is an unstable `List`, Compose considers its identity changed on *every* recomposition. This causes the `remember` block to execute continuously, allocating new lists and objects (`HomeCoverDisplayItem`) directly in the composition phase, leading to GC pressure and dropped frames.

### 3. Lambda Instability (High)
**Symptom:** `HomeScreen` is forced to recompose because its callback parameters change.
**Root Cause:** In `AppNavGraph.kt`, lambdas such as `onContentClick = { ... navController.navigate(...) }` are instantiated anonymously during the NavGraph's composition. Because they capture `navController` (which is often dynamic or not considered stable depending on context), a new lambda instance is created each time. Compose sees a new lambda object and invalidates the `HomeScreen`.

### 4. LazyRow Key Configuration (Good)
**Finding:** The `HomeContentRowSection` correctly implements `key = { _, item -> item.content.id }` within its `LazyRow`. This prevents complete recreation of the row items when the list changes. However, because the parent `items` list (`historyDisplayItems`) is constantly re-allocated (as per point 2), the `LazyRow` still has to do diffing work on every recomposition.

## Remediation Plan

To eliminate these performance bottlenecks, the following fixes must be implemented:

1. **Stabilize Data Classes:**
   - Annotate `HomeSummaryState`, `HomeRecentItem`, `HomeUpdateItem`, `HomeRecommendationItem`, and `HomeCoverDisplayItem` with `@Immutable`.
   - Alternatively, migrate the standard `List` properties to `ImmutableList` from `kotlinx.collections.immutable`. (Using `@Immutable` on the data class is the easiest quick fix, provided the lists are genuinely treated as read-only).

2. **Stabilize Lambdas in NavGraph:**
   - Wrap all inline navigation lambdas passed to screens with `remember`:
     ```kotlin
     val onContentClick = remember { { content, coverBounds, sharedElementKey ->
         PendingDetailsNavigation.set(content, sharedElementKey)
         navController.navigate("details")
     } }
     ```

3. **Defer State Reads (Minor):**
   - Review uses of `scrollState.value` to ensure they are read in the `layout` or `drawBehind` phases rather than the composition phase, preventing full UI tree invalidation on scroll.

These changes will allow the Compose compiler to mark `HomeScreen` as `skippable`, drastically reducing CPU overhead and layout thrashing.
