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

For a task-level decomposition into epics and issues, see the **Epics and Issue Breakdown** section below.

For the UI improvement workstream, use this document as the entry point:

- [`ui_improvement.md`](./ui_improvement.md): source design proposal, UI execution tasks, and implementation priority.

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


## How to Use This Document

Use this document as a planning template for:

- creating epics
- splitting implementation work into smaller issues
- identifying dependencies and safe parallelization opportunities
- defining minimal acceptance criteria for each work item

The goal is not to predict every implementation detail in advance. The goal is to make sure each work item is small enough to execute safely and clear enough to review.

## Planning Rules

When converting these items into real issues, keep the following constraints:

- prefer behavior-preserving refactors before semantic changes
- keep each issue focused on one architectural responsibility
- do not mix diagnostics work with broad behavior rewrites unless the behavior change is required
- require tests or verification notes for high-traffic paths
- prefer additive seams before invasive replacement

## Epic A: Repository Factory Decomposition

### Objective

Decompose the current repository factory path into smaller responsibilities so source access becomes easier to test, extend, and debug.

### Status

Substantially complete.

Implemented outcomes:

- source resolution, repository selection, and repository instance caching have been extracted into dedicated components
- the legacy repository factory entry point now behaves as a thin facade over the new implementation
- characterization tests and structured diagnostics cover the main repository creation path

### Why this epic exists

- this is the main architectural choke point in multi-source content access
- it currently mixes resolution, selection, construction, and caching concerns
- later source and extension work becomes riskier if this area remains dense

### Suggested issues

#### A1. Document current repository creation flow

Goal:

- capture the current execution path before refactoring

Deliverables:

- a short design note or inline architecture comment describing the current source-to-repository flow
- a list of current input types, branches, and cache behaviors

Acceptance criteria:

- contributors can identify where source normalization, repository selection, and caching currently happen

Parallelization:

- should happen first

#### A2. Add characterization tests for repository resolution and cache behavior

Goal:

- protect current behavior before structural extraction

Deliverables:

- tests covering representative source types
- tests covering repository cache reuse behavior

Acceptance criteria:

- the existing behavior is exercised by tests before decomposition starts

Parallelization:

- can start after A1

#### A3. Extract `SourceResolver`

Goal:

- isolate source normalization and source identity handling

Deliverables:

- a dedicated resolver abstraction
- migration of current resolution branches into the resolver

Acceptance criteria:

- repository construction no longer owns source normalization logic
- resolver behavior is testable independently

Parallelization:

- depends on A2

#### A4. Extract `RepositoryProvider`

Goal:

- isolate repository selection and instance construction

Deliverables:

- a provider abstraction responsible for mapping resolved source information to repository implementations

Acceptance criteria:

- selection and construction logic is removed from the old central factory path

Parallelization:

- depends on A3

#### A5. Extract `RepositoryInstanceCache`

Goal:

- make repository lifecycle and reuse policy explicit

Deliverables:

- dedicated cache component
- explicit cache key and invalidation policy

Acceptance criteria:

- cache behavior is no longer implicit inside orchestration code
- cache behavior can be explained and tested directly

Parallelization:

- depends on A4

#### A6. Collapse old orchestration entry point into a thin facade

Goal:

- preserve external behavior while reducing internal responsibility density

Deliverables:

- the previous central entry point remains as a compatibility facade or is reduced to minimal coordination

Acceptance criteria:

- the public call path remains stable
- core responsibilities now live in dedicated components

Parallelization:

- depends on A5

## Epic B: Diagnostics and Observability Foundation

### Objective

Create a minimal but structured diagnostics surface for compatibility-heavy subsystems.

### Status

In progress.

Implemented outcomes:

- structured repository creation diagnostics now include resolution status, provider status, cache status, candidate providers, attempted providers, resolution trace, and classified failure reasons
- diagnostics are consumed by content link resolution, exception resolution, download, page loading, and content page fetching flows
- extension manager load flows now emit more structured start, completion, error, and untrusted-package logs
- sync and backup entry points now emit distinguishable flow-prefixed diagnostics on key trigger paths

### Why this epic exists

- current and future failures often come from external change
- later refactors will be significantly safer with better diagnostics

### Suggested issues

#### B1. Define diagnostic categories and logging conventions

Goal:

- standardize how compatibility-heavy paths report state and failure

Deliverables:

- a small internal convention for categories such as source resolution, repository creation, extension loading, sync, OCR, and network compatibility

Acceptance criteria:

- contributors have one agreed pattern instead of ad hoc logs

Parallelization:

- can begin immediately

#### B2. Instrument source resolution and repository creation

Goal:

- make content-access failures observable

Deliverables:

- structured logs around resolver input, repository selection, fallback, and failure

Acceptance criteria:

- failures in source access are diagnosable without temporary local print-style logging

Parallelization:

- depends on B1
- should track Epic A progress

#### B3. Instrument extension loading and runtime state transitions

Goal:

- make extension lifecycle failures observable

Deliverables:

- diagnostics for discovery, load, install, update, and failure states

Acceptance criteria:

- extension-related failures have enough structured context to classify quickly

Parallelization:

- depends on B1

#### B4. Instrument OCR pipeline stage transitions and fallback decisions

Goal:

- expose where OCR or translation degrades or fails

Deliverables:

- diagnostics for stage start, fallback, partial completion, and error classification

Acceptance criteria:

- OCR failures can be localized to a pipeline stage rather than treated as generic reader issues

Parallelization:

- depends on B1

#### B5. Instrument sync and backup triggers separately

Goal:

- support the later boundary cleanup by making execution paths visible

Deliverables:

- diagnostics that clearly identify sync triggers versus backup or restore triggers

Acceptance criteria:

- major background flows can be classified from logs without source inspection

Parallelization:

- depends on B1

#### B6. Add a debug export or diagnostics collection path

Goal:

- make troubleshooting data easier to capture and share internally

Deliverables:

- a development or support-oriented export path for recent diagnostics

Acceptance criteria:

- useful debugging artifacts can be collected without patching the app locally

Parallelization:

- depends on at least B2 and one of B3, B4, or B5

## Epic C: External Extension Runtime Consolidation

### Objective

Reduce duplicated orchestration between Mihon and Aniyomi runtime management.

### Status

Materially advanced, but not complete.

Implemented outcomes:

- manager-level load-result classification has been extracted into a shared runtime processor
- package-change reload observation has been extracted into a shared runtime helper
- duplicate language-display mapping for Mihon and Aniyomi source wrappers has been centralized
- manager-level extension state flows, source caches, wrapped-source caches, and reload orchestration have been consolidated into a shared runtime implementation
- extension browser and available-install screens now share installed-extension projection and available-state resolution helpers, reducing repeated install/update state shaping across view-models
- the extension-browser batch update queue and installer-result progression now run through a dedicated state machine instead of remaining inlined in the browser view-model
- Mihon and Aniyomi installed-extension management screens now share a dedicated screen runtime for loading, refresh, and aggregated installed/source counts, backed by unit tests

Remaining work:

- shared install and update orchestration
- shared extension state model beyond manager internals
- deeper loader/runtime consolidation

### Suggested issues

#### C1. Map shared lifecycle responsibilities and ecosystem-specific differences

Goal:

- distinguish true duplication from justified divergence

Acceptance criteria:

- there is a clear list of shared flows versus adapter-only differences

#### C2. Extract shared extension state model

Goal:

- remove repeated handling of install/update/availability state

Acceptance criteria:

- common state concepts are represented once

#### C3. Extract shared install and update orchestration

Goal:

- centralize repeated workflow control

Acceptance criteria:

- install/update flow logic no longer has duplicated control paths in both managers

#### C4. Isolate ecosystem-specific adapters

Goal:

- keep compatibility details explicit while reducing spread

Acceptance criteria:

- ecosystem-specific differences are confined to adapter boundaries

#### C5. Verify no behavior drift in browser and management UI flows

Goal:

- ensure consolidation does not silently change user-visible behavior

Acceptance criteria:

- representative extension management flows remain consistent

## Epic D: Reader Core and Enhancement Boundary

### Objective

Protect reader correctness and performance from enhancement-system complexity.

### Status

Started.

Implemented outcomes:

- translation enhancement orchestration has started moving out of [`PageLoader.kt`](../../app/src/main/kotlin/org/Kototoro-app/Kototoro/reader/domain/PageLoader.kt)
- a dedicated [`ReaderPageEnhancementController.kt`](../../app/src/main/kotlin/org/Kototoro-app/Kototoro/reader/domain/ReaderPageEnhancementController.kt) now owns translation job lifecycle, enhancement state emission, and translated-variant resolution
- reader-facing view-models and pager holders now consume enhancement-controller APIs directly instead of calling translation-related proxy methods on [`PageLoader.kt`](../../app/src/main/kotlin/org/Kototoro-app/Kototoro/reader/domain/PageLoader.kt)
- translation-related proxy methods have been removed from [`PageLoader.kt`](../../app/src/main/kotlin/org/Kototoro-app/Kototoro/reader/domain/PageLoader.kt), reducing its responsibility back toward page loading and cache coordination

Remaining work:

- continue separating OCR and translation concerns from page loading internals
- narrow the remaining contracts between reader core loading and enhancement execution
- add targeted verification for fallback and regression-sensitive reader flows

### Suggested issues

#### D1. Document current reader-to-enhancement coupling points

Goal:

- identify where OCR, translation, overlays, or analysis currently affect core reading flow

Acceptance criteria:

- coupling points are explicitly listed before refactoring begins

#### D2. Define a minimal reader core contract

Goal:

- establish what must remain functional without enhancement systems

Acceptance criteria:

- there is a concrete definition of reader-core responsibilities

#### D3. Introduce enhancement hook boundaries

Goal:

- give OCR and related systems stable integration points

Acceptance criteria:

- enhancement systems attach through explicit hooks rather than scattered direct dependencies

#### D4. Make fallback behavior explicit

Goal:

- ensure enhancement failure cannot break reading correctness

Acceptance criteria:

- failures degrade gracefully and predictably

#### D5. Add targeted verification for performance and regression risk

Goal:

- protect reading smoothness while moving boundaries

Acceptance criteria:

- critical reader flows are verified after boundary changes

## Epic E: Sync and Backup Boundary Clarification

### Objective

Separate sync, backup, restore, and auto-restore as distinct systems in both language and implementation ownership.

### Status

Started.

Implemented outcomes:

- key background paths now log `sync_flow` and `backup_flow` separately
- periodic backup, WebDAV auto-sync upload, and WebDAV auto-restore now produce distinct diagnostic prefixes
- sync and backup logging now use a shared typed flow helper, which reduces stringly-typed flow names and standardizes `reason` plus key-value detail formatting across the main services
- backup startup orchestration has begun moving out of [`MainActivity.kt`](../../app/src/main/kotlin/org/Kototoro-app/Kototoro/main/ui/MainActivity.kt) into [`BackupStartupCoordinator.kt`](../../app/src/main/kotlin/org/Kototoro-app/Kototoro/backups/domain/BackupStartupCoordinator.kt), which gives periodical backup, WebDAV auto-sync upload observation, and WebDAV auto-restore separate ownership at app startup
- [`BackupStartupCoordinatorTest.kt`](../../app/src/test/kotlin/org/Kototoro-app/Kototoro/backups/domain/BackupStartupCoordinatorTest.kt) now covers incomplete-config skip behavior and delayed auto-restore scheduling

Remaining work:

- align naming in settings and entry points
- map ownership more explicitly across sync, backup, restore, and auto-restore paths
- add dedicated system-view documentation after the code boundary is clearer

### Suggested issues

#### E1. Audit current trigger paths and user-facing terminology

Goal:

- inventory where the project currently overloads the idea of sync

Acceptance criteria:

- trigger paths and terms are mapped in one place

#### E2. Define target ownership and terminology

Goal:

- establish a stable conceptual model before cleanup

Acceptance criteria:

- each flow has a distinct name, purpose, and owner

#### E3. Align diagnostics with the new boundary

Goal:

- make logs reflect the clarified system distinction

Acceptance criteria:

- sync-related and backup-related execution paths are distinguishable

#### E4. Update settings and entry points where naming is misleading

Goal:

- reduce user and contributor confusion

Acceptance criteria:

- user-visible and contributor-visible naming no longer overloads one concept

#### E5. Add dedicated system-view documentation

Goal:

- prevent the ambiguity from returning later

Acceptance criteria:

- a dedicated doc explains the sync-versus-backup boundary clearly

## Epic F: External Extension Platform Model

### Objective

Create a minimal explicit platform model for external extension management.

### Suggested issues

#### F1. Define core extension platform entities

Goal:

- formalize repository, package, trust, install state, update state, and runtime handle concepts

Acceptance criteria:

- platform terms are stable across docs and code discussions

#### F2. Map current state handling to the target model

Goal:

- identify mismatches and duplication

Acceptance criteria:

- current code paths can be explained using the target entity model

#### F3. Introduce the model in shared orchestration code

Goal:

- use the explicit model to simplify coordination logic

Acceptance criteria:

- shared flows consume the platform model instead of ad hoc parallel structures

#### F4. Align UI and repository metadata handling with the model

Goal:

- reduce conceptual drift between backend and UI behavior

Acceptance criteria:

- state shown in extension UI matches the platform model consistently

## Epic G: System-View Documentation Expansion

### Objective

Add architectural reference pages that explain stable subsystem boundaries.

### Suggested issues

#### G1. Write content access architecture overview

Acceptance criteria:

- source resolution, repository selection, and caching responsibilities are documented

#### G2. Write extension runtime overview

Acceptance criteria:

- extension platform and runtime responsibilities are documented clearly

#### G3. Write reader subsystem overview

Acceptance criteria:

- reader core and enhancement boundaries are documented

#### G4. Write sync versus backup boundary overview

Acceptance criteria:

- the distinction is documented in a dedicated system-view page

## Epic H: Long-Term Content Platform Convergence

### Objective

Guide long-term abstraction work without forcing premature platformization.

### Suggested issues

#### H1. Identify repeated cross-content concepts from real implementations

Acceptance criteria:

- proposed shared abstractions are backed by at least two concrete use cases

#### H2. Introduce shared vocabulary only where reuse is proven

Acceptance criteria:

- new abstractions eliminate real duplication rather than merely renaming concepts

#### H3. Periodically review whether platform concepts are stabilizing

Acceptance criteria:

- abstraction decisions are revisited using actual implementation pressure, not speculation

## Suggested Dependency Graph

Use this as the default dependency order:

1. Epic A starts first
2. Epic B starts immediately and continues alongside later work
3. Epic C depends on progress in Epic A and benefits from Epic B
4. Epic D can start after initial diagnostics and current-boundary mapping
5. Epic E can start after diagnostic categories exist
6. Epic F should follow meaningful progress in Epics A and C
7. Epic G should follow the subsystem clarifications it documents
8. Epic H is ongoing and should remain incremental

## Suggested Parallel Work Streams

The safest parallelization pattern is:

- Stream 1: Epic A and the source/repository diagnostics from Epic B
- Stream 2: OCR and reader-boundary diagnostics, then Epic D
- Stream 3: sync/backup trigger diagnostics, then Epic E
- Stream 4: extension-runtime diagnostics, then Epic C

This keeps ownership reasonably separated while allowing shared diagnostic conventions to emerge early.

## Suggested Milestones

### Milestone 1: Core access path is safer to change

Includes:

- A1 to A6
- B1
- B2

### Milestone 2: Compatibility failures are diagnosable

Includes:

- B3 to B6
- initial outputs from E1
- initial outputs from D1

### Milestone 3: Parallel runtime duplication is under control

Includes:

- C1 to C5
- F1

### Milestone 4: Reader and background systems have clearer boundaries

Includes:

- D2 to D5
- E2 to E5

### Milestone 5: Platform model and docs catch up to implementation

Includes:

- F2 to F4
- G1 to G4
- H1 to H3 as needed

## Definition of Planning Success

This issue breakdown is working if:

- epics can be assigned without large overlaps in ownership
- issues are small enough to review independently
- acceptance criteria focus on boundaries and behavior rather than vague cleanup language
- the roadmap remains adaptable without losing architectural intent
