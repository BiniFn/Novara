# Extensions Management Handoff (March 2026)

This document is the practical handoff note for the ongoing unified Mihon / Aniyomi extension management work.

Use it together with:

- [Unified Mihon / Aniyomi Extensions Management Design](./extensions-management-unification.md)
- [Extensions Management Implementation Plan](./extensions-management-implementation-plan.md)

## Current Status

As of March 12, 2026, the unified extensions management work has moved past repository persistence and remote catalog fetching and now covers the full intended shared management flow, including batch updates, installer download progress, and core logic test coverage.

What is already working in code:

- Settings now route to one top-level `Extensions` entry instead of separate Mihon and Aniyomi entries.
- The main entry opens a shared root screen with `Manga` and `Video` tabs.
- Each tab opens the same shared browser flow backed by `ExternalExtensionType`.
- Compatible extension repositories can be added, refreshed, and deleted.
- Adding a repository now requires an explicit fingerprint trust confirmation step.
- Repository metadata is loaded from `repo.json`.
- Available extensions are loaded from `index.min.json`.
- The shared browser merges installed and remote entries into:
  - updates
  - untrusted
  - incompatible
  - installed
  - available
- Search works on the merged list.
- Install, update, and uninstall actions are wired.
- `Update all` now runs as a serialized queue through the shared browser flow.
- Installer downloads now expose live progress and allow cancellation before the system installer is launched.
- Extension icons are loaded from repository icon URLs, with Mihon / Aniyomi fallback icons.
- `UNTRUSTED` and `INCOMPATIBLE` entries open explanatory dialogs instead of pretending they are installable.
- State dialogs can jump directly to repository management.
- Repository trust and mismatch dialogs now show grouped SHA-256 fingerprints for easier manual verification.
- Repository management messages have been moved into Android string resources.
- Dedicated unit tests now cover:
  - repository URL normalization
  - repo metadata parsing
  - remote catalog parsing
  - merged state grouping
  - signature trust decisions
- Legacy `Available` / Mihon / Aniyomi extension screens now redirect into the unified browser flow instead of maintaining their own installed-only path.

## Verified Build State

The current implementation has been verified with:

```bash
./gradlew :app:compileDebugKotlin --no-daemon
./gradlew :app:testDebugUnitTest --no-daemon \
  --tests "org.skepsun.kototoro.extensions.repo.ExtensionFingerprintTrustTest" \
  --tests "org.skepsun.kototoro.extensions.repo.ExtensionRepoServiceTest" \
  --tests "org.skepsun.kototoro.settings.sources.extensions.ExtensionsBrowserModelsTest"
```

These are the verifications that have been run in the current workstream. There is still no dedicated instrumentation coverage for the system installer round-trip itself.

## Main User Flow

Current primary navigation:

1. `Settings`
2. `Sources`
3. `Extensions`
4. `Manga` or `Video`

From there, the shared browser supports:

- pull to refresh
- search
- install / update
- uninstall
- open repository management from the toolbar
- inspect untrusted / incompatible entries

Repository management supports:

- add repository URL
- trust fingerprint before persisting
- open repository website
- delete repository
- refresh repository metadata

## Product Decisions Locked In

These decisions are already reflected in the code and should not be casually changed during follow-up work:

- Repository add is a two-step flow:
  - normalize and validate URL
  - fetch metadata and ask the user to trust the fingerprint
- Duplicate repositories are blocked both by `baseUrl` and by signing fingerprint.
- Only `https` repository URLs are accepted.
- Incompatible remote entries stay visible, but are not installable.
- `UNTRUSTED` currently means:
  - the package is already installed
  - its installed signing certificate does not match the selected repository fingerprint
- `UNTRUSTED` does not offer a fake “force update” path:
  - Android package signature rules would reject that update anyway
  - the correct action is to explain the mismatch and offer removal

## Important Files

Primary files for the current implementation:

- Settings entry:
  - [`app/src/main/res/xml/pref_sources.xml`](../../app/src/main/res/xml/pref_sources.xml)
  - [`app/src/main/kotlin/org/skepsun/kototoro/settings/sources/SourcesSettingsFragment.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/settings/sources/SourcesSettingsFragment.kt)
- Shared extensions entry and browser:
  - [`app/src/main/kotlin/org/skepsun/kototoro/settings/sources/extensions/ExtensionsRootFragment.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/settings/sources/extensions/ExtensionsRootFragment.kt)
  - [`app/src/main/kotlin/org/skepsun/kototoro/settings/sources/extensions/ExtensionsBrowserFragment.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/settings/sources/extensions/ExtensionsBrowserFragment.kt)
  - [`app/src/main/kotlin/org/skepsun/kototoro/settings/sources/extensions/ExtensionsBrowserViewModel.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/settings/sources/extensions/ExtensionsBrowserViewModel.kt)
  - [`app/src/main/kotlin/org/skepsun/kototoro/settings/sources/extensions/ExtensionsBrowserAdapter.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/settings/sources/extensions/ExtensionsBrowserAdapter.kt)
- Repository management:
  - [`app/src/main/kotlin/org/skepsun/kototoro/settings/sources/extensions/ExtensionRepositoriesFragment.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/settings/sources/extensions/ExtensionRepositoriesFragment.kt)
  - [`app/src/main/kotlin/org/skepsun/kototoro/settings/sources/extensions/ExtensionRepositoriesViewModel.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/settings/sources/extensions/ExtensionRepositoriesViewModel.kt)
- Repository domain and remote catalog:
  - [`app/src/main/kotlin/org/skepsun/kototoro/extensions/repo/ExternalExtensionRepoRepository.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/extensions/repo/ExternalExtensionRepoRepository.kt)
  - [`app/src/main/kotlin/org/skepsun/kototoro/extensions/repo/ExtensionRepoService.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/extensions/repo/ExtensionRepoService.kt)
  - [`app/src/main/kotlin/org/skepsun/kototoro/extensions/repo/RepoAvailableExtension.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/extensions/repo/RepoAvailableExtension.kt)
  - [`app/src/main/kotlin/org/skepsun/kototoro/extensions/repo/InstalledExtensionSignatureValidator.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/extensions/repo/InstalledExtensionSignatureValidator.kt)
- Installation:
  - [`app/src/main/kotlin/org/skepsun/kototoro/extensions/install/ExtensionInstallService.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/extensions/install/ExtensionInstallService.kt)

## Transitional / Legacy Files

These files still exist for compatibility, but they are no longer a distinct user flow:

- [`app/src/main/kotlin/org/skepsun/kototoro/settings/sources/extensions/BaseInstalledExtensionsFragment.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/settings/sources/extensions/BaseInstalledExtensionsFragment.kt)
- [`app/src/main/kotlin/org/skepsun/kototoro/settings/sources/extensions/AvailableExtensionsFragment.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/settings/sources/extensions/AvailableExtensionsFragment.kt)
- [`app/src/main/kotlin/org/skepsun/kototoro/settings/sources/mihon/MihonExtensionsFragment.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/settings/sources/mihon/MihonExtensionsFragment.kt)
- [`app/src/main/kotlin/org/skepsun/kototoro/settings/sources/aniyomi/AniyomiExtensionsFragment.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/settings/sources/aniyomi/AniyomiExtensionsFragment.kt)

Treat them as transitional compatibility code only. New work should stay on the shared browser / repository flow.

## Remaining Risks

The original March 2026 gap list has been addressed in code. The main residual risks are:

- The batch updater still depends on the Android system installer returning control between packages; there is no instrumentation test covering that full platform interaction.
- Compatibility scaffolding files still exist in the tree for old fragment entry points, even though the actual navigation path is now rerouted to the shared browser.

## Recommended Next Steps

If development resumes on another machine, the recommended order is:

1. Add instrumentation or UI automation around the system installer callback path if you want stronger regression protection for `Update all`.
2. Delete the remaining dead legacy scaffolding files once you are comfortable dropping compatibility with restored old fragment stacks.
3. Add polish only after the current queue / trust / installer lifecycle proves stable under manual QA.

## Manual QA Checklist

Before changing more behavior, re-run these checks:

1. Add a valid `https` repository URL and confirm the trust dialog appears.
2. Cancel the trust dialog and confirm the repository is not saved.
3. Confirm the same repository can be added after accepting trust.
4. Confirm duplicate `baseUrl` and duplicate fingerprint are rejected.
5. Verify available extensions load after repository refresh.
6. Verify updates, installed, and available sections all render.
7. Verify an installed package with mismatched signature lands in `Untrusted`.
8. Verify an out-of-range `libVersion` lands in `Incompatible`.
9. Verify `Untrusted` and `Incompatible` open detail dialogs.
10. Verify those dialogs can navigate to repository management.

## Notes For Resume Work

- Start from the shared browser flow, not from the legacy Mihon / Aniyomi fragments.
- Keep Mihon and Aniyomi under one state model unless the data shape truly diverges.
- Prefer finishing the current trust / update / lifecycle path before adding more filters or visual polish.
