---
name: kototoro-main-loading-stability
description: Diagnose and fix Kototoro main screen loading flicker, blank flashes, chrome jumps, transient empty states, and route switching flashes. Use for KototoroApp, AppNavGraph, FavoritesHostScreen, FavouritesContainerViewModel, DiscoverViewModel, collectAsStateWithLifecycle initial values, top/bottom bar offsets, and main route transitions.
---

# Kototoro Main Loading Stability

Use this skill when a main tab briefly flashes empty/loading UI, top or bottom chrome jumps, route switching fades incorrectly, or disabled browse recommendation loading still causes visible churn.

## Root Cause Pattern

Main screen flicker usually comes from ambiguous state, not drawing alone:

- UI infers loading from `categories.isEmpty()` or `items.isEmpty()`.
- `collectAsStateWithLifecycle(initialValue = emptyList())` creates a fake empty first frame.
- Favorites filtering emits empty counts before raw favorites are loaded.
- Disabled recommendation features still emit loading/refresh changes.
- Top/bottom chrome offsets from the previous destination are reused by the next destination.
- Main route transitions are either absent when a small fade is needed or applied to non-main routes where they create visible churn.

## Preferred Fix Pattern

- Introduce explicit UI state with `isLoading`, data, and `isEmpty`.
- Use ViewModel `stateIn` initial values that represent real loading, not fake content.
- Avoid `initialValue = emptyList()` when the underlying `StateFlow` already has a meaningful value.
- Gate recommendation/discover loading with a predicate like:

```kotlin
private fun shouldLoadBrowseRecommendations(query: String = searchQuery.value.trim()): Boolean {
    return isBrowseTrackingRecommendationsEnabled.value || query.isNotBlank()
}
```

- Reset chrome offsets when the current main destination changes.
- Scope effective top/bottom offsets to the destination that produced them.
- Limit route transition fade to main-route to main-route transitions.

## Favorites-Specific Pattern

Use a single host UI state instead of separate `categories` and `isEmpty` streams:

```kotlin
data class FavoritesHostUiState(
    val isLoading: Boolean = true,
    val categories: List<FavouriteTabModel> = emptyList(),
    val isEmpty: Boolean = false,
)
```

When source/language filters are inactive, emit `NotFiltered` rather than waiting on the raw favorites stream. When filters are active, emit `Loading` before filtered counts arrive.

## Navigation Chrome Pattern

- Keep `offsetDestinationRoute`.
- Compute `effectiveTopBarOffset` and `effectiveBottomNavOffset` only when the offset belongs to the current destination.
- On main destination change, reset top and bottom offsets to zero.
- In `AppNavGraph`, use a short fade only if both initial and target destinations are main routes.

## Pitfalls

- Do not show empty state while data is still loading.
- Do not start disabled recommendation loads just to clear state; explicitly clear and return.
- Do not reuse scroll-derived top bar offsets across route changes.
- Do not apply main-route fade transitions to details/search/player-like routes.

## Verification

Run:

```bash
JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64" ./gradlew :app:compileDebugKotlin --no-daemon
```

Manual acceptance:

- Main tabs do not flash empty state before real content arrives.
- Favorites shows loading, real empty, and content states distinctly.
- Browse recommendation disabled state does not keep refreshing hidden content.
- Top/bottom chrome does not jump when switching tabs or returning from details/player.
