# Kototoro Architecture Epics and Issue Breakdown

## Purpose

This document translates the architecture roadmap into a task structure that can be scheduled, assigned, and tracked.

It is intended to sit below [`architecture-roadmap.md`](./architecture-roadmap.md) and provide a practical epic and issue decomposition for implementation work after the manga-to-content naming migration.

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

Started.

Implemented outcomes:

- manager-level load-result classification has been extracted into a shared runtime processor
- package-change reload observation has been extracted into a shared runtime helper
- duplicate language-display mapping for Mihon and Aniyomi source wrappers has been centralized

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
