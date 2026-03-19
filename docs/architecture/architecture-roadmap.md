# Kototoro Architecture Roadmap

## Purpose

This document turns the architecture review into an execution-oriented roadmap.

It is not meant to replace the high-level review in [`architecture-review.md`](./architecture-review.md). Instead, it provides a practical sequencing guide for architectural work that should happen after the broad manga-to-content naming migration.

## Scope

This roadmap excludes the generic naming migration from `Manga` to `Content`, because that work is already being handled separately.

The focus here is the remaining structural work that most affects maintainability, extensibility, and debugging cost.

## Current Implementation Status

The roadmap is now partially implemented.

Completed or materially advanced items:

- Phase 1.1 is functionally complete:
  source resolution, repository selection, and repository instance caching have been split into dedicated components while preserving the existing factory entry contract
- Phase 1.2 is partially complete:
  repository creation diagnostics now expose structured statuses and failure reasons, and several high-traffic call sites consume those diagnostics directly
- Phase 2.3 has started:
  Mihon and Aniyomi extension managers now share common runtime processing for load-result classification, duplicate-name language suffix handling, and package-change reload observation
- Phase 2.4 is materially advanced:
  reader translation and enhancement orchestration has been split out of `PageLoader` into a dedicated enhancement controller, and reader UI paths now consume enhancement state and display-variant logic without routing those concerns back through the page-loading core
- Phase 2.5 has started:
  sync and backup trigger paths now emit distinguishable `sync_flow` and `backup_flow` diagnostics in key controllers and services

Still pending:

- broader extension lifecycle consolidation beyond manager-level shared runtime support
- reader core versus enhancement boundary extraction
- sync versus backup boundary cleanup beyond initial trigger diagnostics
- external extension platform model consolidation
- the remaining system-view documentation

## Planning Principles

The prioritization in this roadmap follows four simple rules:

- reduce architectural pressure at the most central choke points first
- prefer changes that improve future changeability over changes that only improve terminology
- invest early in diagnostics for high-variance subsystems
- avoid large top-down platform rewrites before the current boundaries are clearer

## Phase 1: Stabilize Core Structural Pressure

### 1. Separate source resolution, repository selection, and instance caching

Goal:

- reduce responsibility density in the current repository factory path
- make source access easier to reason about, test, and evolve

Why this is first:

- it sits on the main path for multi-source content access
- it is already a visible complexity hotspot
- later extension and platform work depends on this area becoming clearer

Expected benefits:

- lower cognitive load for contributors
- easier testing of source lookup and repository creation logic
- safer integration of additional source ecosystems

Main risks:

- behavior regressions in source resolution
- accidental cache semantics changes

Suggested implementation shape:

- introduce a `SourceResolver` responsible for source normalization and source identity resolution
- introduce a `RepositoryProvider` responsible for choosing and constructing repository instances
- introduce a `RepositoryInstanceCache` responsible for lifecycle and cache policy
- keep the existing entry contract stable during the refactor

Dependency notes:

- no major upstream dependency
- should be done before deeper extension-platform generalization

Definition of done:

- source resolution logic is no longer mixed with cache management
- repository selection can be tested independently
- cache behavior is explicit rather than incidental

### 2. Add observability to compatibility-heavy paths

Goal:

- reduce diagnosis cost in subsystems that fail due to external change or compatibility variance

Why this is first-phase work:

- it improves engineering throughput immediately
- it lowers the risk of subsequent refactors
- it is useful even if architecture cleanup takes multiple iterations

Expected benefits:

- faster debugging of source failures and extension issues
- better visibility into sync triggers and OCR fallbacks
- clearer reproduction data for user-reported issues

Main risks:

- noisy logging without clear structure
- exposing too much internal detail without consistent categorization

Suggested implementation shape:

- define structured log categories for source resolution, repository creation, extension loading, sync execution, OCR stages, and network compatibility
- add stable failure reasons where possible instead of only free-form text
- add debug toggles or diagnostics export paths for development and support use

Dependency notes:

- should start early and continue incrementally
- pairs well with phase-1 refactors because it makes regressions easier to spot

Definition of done:

- major compatibility flows have clear start, success, fallback, and failure diagnostics
- contributor debugging no longer depends on ad hoc log insertion

## Phase 2: Reduce Redundant Orchestration and Protect Core Boundaries

### 3. Reduce Mihon and Aniyomi runtime duplication

Goal:

- stop parallel implementations from drifting and multiplying maintenance cost

Why this is phase 2:

- it is important, but easier to execute once source/repository responsibilities are clearer

Expected benefits:

- less duplicated orchestration logic
- fewer double-fixes for install, update, and state handling
- clearer platform surface for future extension ecosystems

Main risks:

- over-abstracting real ecosystem differences
- moving too much too quickly and making failures harder to localize

Suggested implementation shape:

- extract common lifecycle concerns first
- centralize shared state, install/update flow, and repository metadata handling
- keep ecosystem-specific adapters explicit

Dependency notes:

- depends on clearer source and repository responsibilities
- benefits from the observability work in phase 1

Definition of done:

- shared runtime concerns live in one place
- ecosystem-specific differences are isolated rather than duplicated everywhere

Current status:

- initial shared runtime support is now in place for manager-level load-result processing and package observer registration
- Mihon and Aniyomi managers now also share manager-level state-flow, source-cache, wrapped-source-cache, and reload orchestration through a common runtime helper
- extension install-browser screens now share installed-extension projection and available-state resolution helpers instead of duplicating ecosystem-specific installed-map shaping in multiple view-models
- batch update queue state and installer-result progression in the extension browser have been moved into a dedicated state machine instead of remaining embedded in the browser view-model
- legacy Mihon and Aniyomi installed-extension screens now share a dedicated installed-screen runtime for refresh, loading state, extension-count aggregation, and source-count aggregation, with focused unit coverage for initialization and state projection
- duplicate orchestration still exists deeper in the loader and lifecycle layers, so this phase is not complete yet

### 4. Separate reader core from reader enhancements

Goal:

- keep base reading correctness and performance independent from OCR, translation, overlays, and advanced analysis

Why this is phase 2:

- the reader is one of the strongest product areas and should be protected before more enhancement complexity accumulates

Expected benefits:

- stronger reader stability
- cleaner performance reasoning
- simpler fallback behavior when enhancement stages fail

Main risks:

- hidden coupling between enhancement features and current reader flow
- interface design that is either too narrow or too generic

Suggested implementation shape:

- define a reader core that remains functional without OCR or translation
- expose enhancement hooks with clear lifecycle boundaries
- treat enhancement output as optional augmentation, not a prerequisite for rendering correctness

Dependency notes:

- can be started in parallel with extension-runtime cleanup if ownership is kept separate

Definition of done:

- reader core is conceptually and operationally complete on its own
- enhancement failures do not compromise core reading flow

Current status:

- `PageLoader` no longer owns the full translation-enhancement orchestration path by itself
- translation state, translation job lifecycle, and rendered-variant resolution now live in a dedicated enhancement controller
- reader view-model and pager view-model paths consume enhancement-controller APIs directly instead of using `PageLoader` as an enhancement proxy
- `PageLoader` has shed its temporary enhancement proxy methods and is now closer to a pure page-loading core
- deeper reader/OCR boundary cleanup is still pending

### 5. Clarify sync and backup as separate systems

Goal:

- make system responsibilities, triggers, and user expectations explicit

Why this is phase 2:

- it is a broad clarity improvement with relatively low implementation risk
- it becomes easier once diagnostic surfaces are in place

Expected benefits:

- clearer contributor understanding
- cleaner product wording and settings structure
- reduced ambiguity in bug reports and future feature design

Main risks:

- terminology cleanup without actual ownership cleanup
- retaining overlapping triggers that still confuse behavior

Suggested implementation shape:

- define sync, backup, restore, and auto-restore separately in docs and code ownership
- map each trigger path to a clear system responsibility
- align settings and execution paths with that distinction over time

Dependency notes:

- does not require major architectural rewrites
- should inform subsequent documentation work

Definition of done:

- sync and backup are described, configured, and implemented as different systems
- major flows are no longer overloaded under one mental label

Current status:

- initial implementation work has started through explicit `sync_flow` versus `backup_flow` diagnostics
- key sync and backup services now share a typed background-flow diagnostics helper instead of scattering raw flow-name strings and hand-built `reason=...` fragments
- backup-related startup orchestration has started moving out of `MainActivity` into a dedicated coordinator, so periodical backup, WebDAV auto-sync upload observation, and delayed WebDAV auto-restore startup no longer share a single UI entry-point block
- the new backup startup coordinator now has focused unit coverage for incomplete-config skip behavior and delayed auto-restore startup behavior
- terminology and ownership cleanup across settings, services, and docs still remain

## Phase 3: Consolidate Platform Direction

### 6. Define an explicit external extension platform model

Goal:

- turn current extension-management capabilities into a more explicit platform contract

Why this is phase 3:

- it is strategically important, but should be built on cleaner runtime and repository foundations

Expected benefits:

- clearer model for trust, package state, repository catalogs, and runtime integration
- better basis for future UI and policy consistency

Main risks:

- creating a vocabulary layer that does not yet map well to implementation
- over-designing a platform before shared behavior is actually stable

Suggested implementation shape:

- define minimal core entities such as repository, package, install state, trust state, update state, and runtime handle
- use those entities to guide incremental cleanup rather than forcing a full rewrite

Dependency notes:

- depends on phase-1 and phase-2 cleanup for best results

Definition of done:

- extension concepts are represented consistently across docs, state handling, and runtime orchestration

### 7. Expand system-view documentation

Goal:

- preserve architectural intent and reduce communication overhead during continued growth

Why this is phase 3:

- docs are most valuable when they reflect clarified boundaries rather than aspirational ones

Expected benefits:

- better onboarding
- easier design review
- lower risk of architecture drift from contributor misunderstanding

Main risks:

- documentation going stale if written too early
- duplicate explanations across too many files

Suggested first documents:

- content access architecture
- extension runtime overview
- reader subsystem overview
- sync vs backup boundary explanation

Dependency notes:

- should start after or alongside the architecture cleanup it describes

Definition of done:

- key subsystem boundaries are documented in dedicated system-view pages
- architectural intent is discoverable without reading only the code

## Phase 4: Long-Term Convergence

### 8. Formalize the content-platform layer incrementally

Goal:

- let Kototoro's long-term architecture reflect its real product center: unified content access and consumption

Why this is long-term:

- the direction is already correct
- the remaining work is to converge safely rather than to impose a premature framework

Expected benefits:

- more coherent platform vocabulary
- better alignment across source access, content kinds, and consumption entry contracts

Main risks:

- introducing abstractions before there are enough stable use cases
- turning a useful architectural direction into a large speculative rewrite

Suggested implementation shape:

- evolve the vocabulary gradually
- introduce abstractions only when at least two or more real subsystems need them
- let earlier refactors reveal the right seams

Dependency notes:

- depends on the previous phases producing clearer boundaries

Definition of done:

- content-platform concepts emerge from real shared behavior rather than top-down speculation

## Recommended Sequence

The practical execution order should be:

1. separate source resolution, repository selection, and caching
2. add observability across compatibility-heavy paths
3. reduce Mihon and Aniyomi runtime duplication
4. separate reader core from reader enhancements
5. clarify sync and backup boundaries
6. define the external extension platform model
7. expand system-view architecture documentation
8. continue long-term convergence toward a content-platform layer

## Suggested Work Breakdown Strategy

To keep the roadmap aligned with KISS, DRY, SOLID, and YAGNI, implementation should follow these constraints:

- prefer extracting one responsibility at a time over broad subsystem rewrites
- add tests around current behavior before changing high-traffic paths where possible
- do not introduce generic platform abstractions until at least two concrete callers require them
- keep new interfaces small and role-focused
- treat documentation updates as part of definition-of-done for boundary-setting work

For a task-level decomposition into epics and issues, see [`architecture-epics.md`](./architecture-epics.md).

For the UI improvement workstream, use this document set as the entry point:

- [`ui_improvement.md`](./ui_improvement.md): source design proposal and target interaction model
- [`ui_improvement_tasks.md`](./ui_improvement_tasks.md): execution-oriented issue and milestone breakdown
- [`ui_improvement_priority.md`](./ui_improvement_priority.md): phase order, dependency rules, and recommended branch sequence

Current UI workstream status as of 2026-03-19:

- Home navigation integration and default-home startup path are complete
- the first dashboard-style Home screen is materially complete and backed by real reading, library, source, tracking-site, and sync summary data
- Explore filters have been converted from a horizontal scroll layout to a two-row fixed layout
- shared source-tag support now includes JavaScript sources and icon coverage for the current UI path
- tracking-site discovery abstractions have been added so future Discover and details-page work does not depend directly on a Bangumi-only implementation

## Success Criteria

This roadmap is succeeding if the project becomes easier to change in the following ways:

- new source ecosystems can be added with fewer edits to central orchestration code
- extension-management changes do not require duplicated logic across parallel managers
- reader enhancements can evolve without destabilizing basic reading
- sync and backup bugs are easier to classify and reason about
- compatibility-heavy failures are diagnosable without invasive manual debugging
- new contributors can identify subsystem boundaries from docs and code structure more quickly
