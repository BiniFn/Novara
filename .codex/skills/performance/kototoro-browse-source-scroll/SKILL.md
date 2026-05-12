---
name: kototoro-browse-source-scroll
description: Diagnose and fix Kototoro Browse/Explore page content source scrolling jank when many sources exist. Use for ExploreHostScreen, ExploreViewModel, source quick access, LazyColumn source rows, source grouping, browse recommendation loading, and tab switching performance.
---

# Kototoro Browse Source Scroll

Use this skill when the Browse/Explore page stalls, freezes, or shows a blank frame while switching to the sources area, especially with hundreds or thousands of installed/enabled sources.

## Root Cause Pattern

Do not treat this as only an image/icon loading problem. In Kototoro the expensive path can be:

- Rendering all sources inside one large composable block.
- Grouping/chunking sources during every recomposition.
- Showing source quick access fully expanded while tracking recommendation hero content is also present.
- Mixing discover recommendation loading state with source loading state.
- Recreating source rows on every tab/filter change because keys/content types are too coarse.

## Preferred Fix Pattern

- Split source quick access into `LazyListScope` items instead of a single detached block.
- Render source cards as lazy rows with stable row keys and `contentType`.
- Precompute source metrics with `remember(gridScale)`.
- Precompute groups with `remember(sources, isGroupedByLanguage, context)`.
- Default to a collapsed source section when recommendations are enabled; expand only on user request.
- Force expanded only when recommendations are disabled, so source-only mode preserves the old direct-access behavior.
- Track source loading separately from discover/recommendation loading.

## Implementation Checklist

- Add a `BrowseSourceItems.isLoadingOnly` or equivalent source-specific loading state.
- Keep recommendation loading in a separate `isDiscoverLoadingOnly`.
- Compute `sourceColumns` from list mode and metrics.
- Use a small collapsed count such as `columns * 5`.
- Build `visibleSourceGroups` with a max source count.
- Use `itemsIndexed` rows:

```kotlin
itemsIndexed(
    items = rows,
    key = { rowIndex, rowSources ->
        val firstId = rowSources.firstOrNull()?.id ?: rowIndex.toLong()
        "source_row_${groupIndex}_${rowIndex}_$firstId"
    },
    contentType = { _, _ -> "source_row" },
) { _, rowSources ->
    SourceQuickAccessRow(...)
}
```

## Pitfalls

- Do not put a large nested source grid inside one `item` if the page itself is a `LazyColumn`.
- Do not use only index keys for source rows if groups can change.
- Do not let disabled recommendation features still trigger recommendation load or refresh.
- Do not remove source long-click selection behavior while refactoring rows.

## Verification

Run:

```bash
JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64" ./gradlew :app:compileDebugKotlin --no-daemon
```

Manual acceptance:

- Switching to Browse with many sources does not pause before first paint.
- Scrolling remains responsive with hundreds or thousands of sources.
- "Show more/less" works and preserves source click/long-click behavior.
- Source-only mode still shows all sources without hiding access behind recommendations.
