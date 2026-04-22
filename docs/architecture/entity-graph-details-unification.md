# Entity Graph — DetailsScreen Unification Progress

## Overview

This document tracks the progress of unifying the Kototoro detail page architecture.
The original plan (see `entity-graph-implementation-plan.md`) envisioned three parallel
detail modes converging to two. This work goes further: it converges all three into a
single `DetailsActivity`/`DetailsScreen` entry point, using a sealed `DetailsOrigin`
argument to distinguish Local, EntityGraph, and TrackingItem entries.

## Architecture Decision Update

The original plan recommended keeping `EntityDetailsActivity` as a separate shell.
During implementation it became clear that:

- `DetailsScreen` already has the richest UI (hero backdrop, panorama cover, chapter
  pager, translation, favorites, history integration)
- duplicating that surface into a second Activity would create ongoing maintenance
  burden
- the "Instant First Paint" pattern (show whatever data we have immediately, resolve
  the full graph in background) eliminates the UX risk of merging

**Decision**: merge all detail entry points into the existing `DetailsActivity` +
`DetailsScreen`, extending `DetailsViewModel` with entity-graph and tracking awareness.

## Phases and Progress

### Phase 1–3: Entity Graph Core (DONE — prior work)

- [x] Room schema: `entity`, `entity_binding`, `relation` tables
- [x] DAO + Repository for graph CRUD
- [x] Tracking-to-entity ingestion pipeline
- [x] Relation creation for WORK ↔ CHARACTER, WORK ↔ PERSON edges
- [x] Cross-site binding matcher with auto-bind thresholds
- [x] `EntityGraphSourceAdapter` contract

### Phase 4: Entity Detail Shell (DONE — prior work)

- [x] `EntityDetailsActivity` / `EntityDetailsViewModel` (standalone, now deprecated)
- [x] `PeripheralEntityScreen` / `UnifiedEntityScreen` composables (now deprecated)
- [x] Router support for `openEntityDetails(entityId)`

### Phase 5: DetailsScreen Unification (DONE)

#### 5.1 Routing & DetailsOrigin

- [x] Created `DetailsOrigin` sealed interface with four variants:
  - `LocalMangaId(mangaId: Long)`
  - `LocalMangaContent(parcelableContent: ParcelableContent)`
  - `EntityGraph(entityId: Long)`
  - `TrackingItem(serviceId: String, remoteId: Long, urlHint: String?)`
- [x] Updated `AppRouter.openEntityDetails()` → routes through unified `detailsIntent`
- [x] Updated `AppRouter.openTrackingSiteDetails()` → routes through unified `detailsIntent`
- [x] Added `AppRouter.detailsIntent(Context, Content)` overload for backwards
      compatibility with background components (notifications, suggestions, etc.)
- [x] `DetailsActivity` parses `DetailsOrigin` from intent extras

#### 5.2 ViewModel Convergence

- [x] `DetailsViewModel` accepts `DetailsOrigin` in `init` block
- [x] Instant First Paint for EntityGraph: creates synthetic `Content` from entity
      `primaryName` immediately, populates `mangaDetails`
- [x] Instant First Paint for TrackingItem: reads cached `TrackingSiteItemDetails`,
      creates synthetic `Content`, populates `mangaDetails`
- [x] Background resolution for EntityGraph:
  - Fetches entity bindings → resolves local manga ID → updates `activeMangaIdFlow`
  - Fetches relations → maps to `EntityRelationSection` / `EntityRelationItem`
  - Updates `entityRelationSections` state
- [x] Background resolution for TrackingItem:
  - Fetches remote details via `TrackingSiteDiscoveryService.getDetails()`
  - Caches result via `TrackingSiteCacheRepository`
  - Resolves tracking links → updates `activeMangaIdFlow` if bound
- [x] `activeMangaIdFlow` drives reactive re-binding of history, favorites, stats

#### 5.3 UI Integration

- [x] `DetailsScreen.kt` accepts `entityRelationSections` state parameter
- [x] `EntityRelationCarousels` composable injected into scrollable content
- [x] `EntityRelationCard` composable renders individual relation items
- [x] All Compose imports resolved (LazyRow, items, FontWeight, TextAlign, etc.)

### Phase 6: Legacy Cleanup (IN PROGRESS)

- [x] Removed `TrackingSiteDetailsActivity.kt`
- [x] Removed `TrackingSiteDetailsViewModel.kt`
- [x] Removed `TrackingSiteDetailsFragment.kt`
- [x] Removed `UnifiedEntityScreen.kt`
- [x] Removed `EntityDetailsActivity.kt`
- [x] Removed `EntityDetailsViewModel.kt`
- [x] Removed `PeripheralEntityScreen.kt`
- [x] Removed AndroidManifest entries for both legacy Activities
- [x] Removed unused imports from `AppRouter.kt`
- [x] Extracted `EntityRelationSection` / `EntityRelationItem` to standalone
      `EntityRelationModels.kt`
- [x] Extracted `LocalSearchState` to standalone `LocalSearchState.kt`
- [ ] Verify clean compile after extraction (build daemon interrupted, pending retest)
- [ ] Remove `TrackingSiteDetailsScreen.kt` if orphaned
- [ ] Remove `TrackingLocalSourcesPanel.kt` if orphaned
- [ ] Audit remaining references to deleted types

### Phase 7: Refinement (TODO)

- [ ] Style `EntityRelationCarousels` to match the existing DetailsScreen design
      language (glassmorphism cards, cover art thumbnails instead of initial letters)
- [ ] Add "Active Local Source" switcher when multiple sources are bound to entity
- [ ] Wire `onEntityClick` in carousels to `AppRouter.openEntityDetails()`
- [ ] Add cover art / thumbnail display to `EntityRelationCard` using entity bindings
- [ ] Handle entity-graph chapter source (from meta vs from reader source)
- [ ] Performance: debounce relation section updates to avoid UI flicker

## File Inventory

### New Files Created

| File | Purpose |
|------|---------|
| `details/ui/model/DetailsOrigin.kt` | Sealed interface for navigation origin |
| `entitygraph/ui/details/EntityRelationModels.kt` | Shared data classes for relation UI |
| `discover/ui/details/LocalSearchState.kt` | Search state model extracted from deleted VM |

### Files Modified

| File | Changes |
|------|---------|
| `core/nav/AppRouter.kt` | Unified routing, removed legacy imports |
| `details/ui/DetailsActivity.kt` | Parses `DetailsOrigin` from intent |
| `details/ui/DetailsViewModel.kt` | Multi-origin init, entity/tracking resolution |
| `details/ui/compose/DetailsScreen.kt` | EntityRelationCarousels injection |
| `tracker/work/TrackerNotificationHelper.kt` | Uses `detailsIntent(Content)` overload |
| `app/src/main/AndroidManifest.xml` | Removed 2 legacy Activity entries |

### Files Deleted

| File | Reason |
|------|--------|
| `discover/ui/details/TrackingSiteDetailsActivity.kt` | Replaced by unified DetailsActivity |
| `discover/ui/details/TrackingSiteDetailsViewModel.kt` | Merged into DetailsViewModel |
| `discover/ui/details/TrackingSiteDetailsFragment.kt` | No longer needed |
| `entitygraph/ui/details/EntityDetailsActivity.kt` | Replaced by unified DetailsActivity |
| `entitygraph/ui/details/EntityDetailsViewModel.kt` | Merged into DetailsViewModel |
| `entitygraph/ui/details/PeripheralEntityScreen.kt` | Replaced by EntityRelationCarousels |

## Known Issues

1. **Build not fully verified**: Last build was interrupted by daemon stop. Need to
   recompile and fix any remaining reference errors in `TrackingLocalSearchSheet.kt`.
2. **Entity cover art**: Currently using letter-avatar fallback in `EntityRelationCard`.
   Should display actual cover images when available via entity bindings.
3. **Tracking-to-entity resolution**: The `serviceId` field in `DetailsOrigin.TrackingItem`
   is a `String`, but `ScrobblerService.id` is an `Int`. Conversion uses `.toIntOrNull()`.
