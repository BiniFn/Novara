# Extensions Management Implementation Plan

This document turns the unified extensions management design into a practical execution plan for Kototoro.

Related design:

- [Unified Mihon / Aniyomi Extensions Management Design](./extensions-management-unification.md)

## Delivery Strategy

The implementation should be incremental.

Recommended order:

1. Remove UI duplication first.
2. Introduce shared domain and persistence for repositories.
3. Add remote catalog fetching and merged state.
4. Add APK download and install.
5. Add trust, update-all, and polish.

This avoids building repository and installer features on top of duplicated Mihon/Aniyomi screens.

## Phase 1: Unify Installed-Only Management

## Goal

Replace the separate Mihon and Aniyomi installed-extension screens with one shared architecture while keeping current capability unchanged.

## Tasks

- Create shared UI models for installed extensions.
- Create one shared adapter for extension rows.
- Create one shared fragment or screen container parameterized by extension type.
- Create one shared view model contract for installed-only state.
- Keep Mihon and Aniyomi loaders as the data source for this phase.
- Replace duplicate layouts if possible with one reusable layout.

## Expected files

- new: `settings/sources/extensions/`
- migrate or replace:
  - `settings/sources/mihon/MihonExtensionsFragment.kt`
  - `settings/sources/aniyomi/AniyomiExtensionsFragment.kt`
  - `settings/sources/mihon/MihonExtensionsAdapter.kt`
  - `settings/sources/aniyomi/AniyomiExtensionsAdapter.kt`

## Acceptance criteria

- Users can still open Mihon and Aniyomi extension entries from settings.
- Both entries render through the same underlying UI stack.
- No regression in installed extension detection.
- No regression in source count or refresh behavior.

## Phase 2: Repository Persistence

## Goal

Add storage and lifecycle management for compatible extension repositories.

## Tasks

- Define `ExternalExtensionRepo` data model.
- Add persistence storage for repositories.
- Add create, delete, and refresh use cases.
- Add repo URL normalization and validation.
- Fetch `repo.json` and persist returned metadata.
- Reject duplicates by `baseUrl`.
- Detect suspicious fingerprint conflicts and surface them as an error state.

## Suggested modules

- `extensions/repo/`
- `extensions/model/`

## Acceptance criteria

- Users can add a compatible repository URL.
- Repository metadata is fetched and stored.
- Invalid repo URLs fail with a user-visible error.
- Stored repos survive app restart.

## Phase 3: Remote Catalog Fetching

## Goal

Fetch available extensions from configured repositories and merge them with installed extensions.

## Tasks

- Implement Mihon-compatible remote extension API.
- Implement Aniyomi-compatible remote extension API.
- Parse `index.min.json`.
- Filter incompatible `libVersion` ranges.
- Convert remote entries into shared `ExternalExtensionItem` models.
- Merge installed and remote states into:
  - updates
  - installed
  - available
  - untrusted
  - incompatible

## Suggested modules

- `extensions/catalog/`
- `extensions/merge/`

## Acceptance criteria

- Available extensions are shown from at least one configured repo.
- Installed extensions with newer remote versions are marked as updatable.
- Incompatible remote entries are not treated as installable.

## Phase 4: Unified Screen for Installed and Available Extensions

## Goal

Expose a proper in-app extensions screen, not just installed-only management.

## Tasks

- Replace current simple list screen with grouped sections.
- Add `Manga` and `Anime` top-level modes or tabs.
- Add repository management entry point from the extensions screen.
- Add search by source name, package name, and base URL where relevant.
- Add item actions:
  - install
  - update
  - cancel
  - uninstall

## Suggested UX structure

- `Extensions`
  - `Manga`
  - `Anime`
- `Repositories`
  - `Manga`
  - `Anime`

## Acceptance criteria

- Users can see installed and available extensions on one screen.
- Users can distinguish updates, installed entries, and available entries visually.
- Search works across sectioned data.

## Phase 5: APK Download and Installation

## Goal

Support in-app download and install/update of extension APKs.

## Tasks

- Implement APK URL resolution from repo metadata.
- Add installer state model.
- Download APK with progress updates.
- Hand off installation through PackageInstaller or system installer flow.
- Refresh installed extensions after package add/replace.
- Support cancellation.

## Suggested modules

- `extensions/install/`

## Acceptance criteria

- Install action downloads the APK and starts Android installation flow.
- Update action replaces an installed extension through the same workflow.
- Download progress is visible to the user.
- Install completion refreshes the extension list.

## Phase 6: Trust and Security

## Goal

Bring the feature to a safe and supportable state.

## Tasks

- Add untrusted extension state and UI.
- Surface repository signing fingerprint.
- Add trust confirmation flow where needed.
- Keep `libVersion` compatibility checks enforced.
- Distinguish user-facing error cases:
  - invalid repo
  - network failure
  - incompatible extension
  - untrusted extension
  - install failure

## Acceptance criteria

- Untrusted extensions are clearly separated from normal entries.
- Incompatible extensions cannot be installed accidentally.
- Error states are visible and actionable.

## Phase 7: Polish

## Goal

Reach usability close to Mihon / Aniyomi without blindly cloning their UI code.

## Tasks

- Add update-all.
- Add better language grouping and sorting.
- Add repo last-refresh state.
- Add empty states for:
  - no repos
  - no available extensions
  - no search results
- Add icons if repository format provides them.
- Improve strings and help text.

## Acceptance criteria

- Extensions management feels like a complete first-class feature.
- The workflow is understandable without reading external documentation.

## Cross-Cutting Technical Tasks

These tasks should be considered during all phases.

## State model

- Keep one shared state model for Mihon and Aniyomi.
- Avoid separate parallel hierarchies unless the data shape is truly different.

## Testing

- Unit test repo validation.
- Unit test remote index parsing.
- Unit test merge logic for installed/available/update states.
- Add at least one integration-style test for installation state transitions if practical.

## Telemetry and logging

- Log repo refresh failures with enough detail to debug.
- Log extension compatibility rejection reasons.
- Log install/update transitions.

## Migration guidance

- Preserve current settings entry points initially to avoid breaking navigation.
- Internally reroute them to the shared screen implementation.
- Only simplify settings labels after the unified screen is stable.

## Recommended First PR Sequence

If the work is split into pull requests, this is the most practical order:

1. Shared UI and shared installed-extension model only.
2. Repository persistence and validation only.
3. Remote extension catalog fetching only.
4. Merged installed/available screen.
5. Installer and update workflow.
6. Trust and polish.

## Suggested Out-of-Scope Items For Initial Delivery

Keep these out of the first implementation unless they are unexpectedly cheap:

- direct Compose migration of the entire settings flow
- silent or privileged install strategies
- generic arbitrary APK repository support
- aggressive auto-update behavior
- broad cross-ecosystem abstraction beyond Mihon manga and Aniyomi anime

## Definition of Done

This feature should be considered complete only when:

- users can add compatible repositories
- users can discover available extensions from those repositories
- users can install and update extensions in-app
- installed, available, update, and untrusted states are clearly represented
- Mihon and Aniyomi management flows share one coherent UI and architecture
