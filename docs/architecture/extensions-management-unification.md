# Unified Mihon / Aniyomi Extensions Management Design

## Goal

Kototoro should evolve from:

- scanning already-installed Mihon extension APKs
- scanning already-installed Aniyomi extension APKs

to a full in-app extension management workflow:

- add compatible extension repository URLs
- fetch repository metadata
- fetch available extensions
- compare installed, available, updatable, and untrusted extensions
- download and install APKs
- present Mihon and Aniyomi extension management in one consistent UI

This should match the practical capability level of Mihon and Aniyomi, while fitting Kototoro's current Android architecture and UI stack.

## Current State

Kototoro already has two separate flows:

- Mihon installed-extension loading:
  - [`MihonExtensionLoader.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/mihon/MihonExtensionLoader.kt)
  - [`MihonExtensionManager.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/mihon/MihonExtensionManager.kt)
- Aniyomi installed-extension loading:
  - [`AniyomiExtensionLoader.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/aniyomi/AniyomiExtensionLoader.kt)
  - [`AniyomiExtensionManager.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/aniyomi/AniyomiExtensionManager.kt)

Current settings entry points:

- [`pref_sources.xml`](../../app/src/main/res/xml/pref_sources.xml)
- [`MihonExtensionsFragment.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/settings/sources/mihon/MihonExtensionsFragment.kt)
- [`AniyomiExtensionsFragment.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/settings/sources/aniyomi/AniyomiExtensionsFragment.kt)

Current UI status:

- two separate fragments
- two separate view models
- two near-duplicate adapters and layouts
- only installed extensions are shown
- no repository layer
- no available/update/untrusted/install state UI
- no in-app download and install flow

Near-duplicate UI files today:

- [`fragment_mihon_extensions.xml`](../../app/src/main/res/layout/fragment_mihon_extensions.xml)
- [`fragment_aniyomi_extensions.xml`](../../app/src/main/res/layout/fragment_aniyomi_extensions.xml)
- [`MihonExtensionsAdapter.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/settings/sources/mihon/MihonExtensionsAdapter.kt)
- [`AniyomiExtensionsAdapter.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/settings/sources/aniyomi/AniyomiExtensionsAdapter.kt)

## Upstream Reference

The relevant upstream reference is not just UI styling. The important part is the domain model and flow.

Key upstream implementation references:

- Manga extension manager:
  - `aniyomi/app/src/main/java/eu/kanade/tachiyomi/extension/manga/MangaExtensionManager.kt`
- Anime extension manager:
  - `aniyomi/app/src/main/java/eu/kanade/tachiyomi/extension/anime/AnimeExtensionManager.kt`
- Manga extension API:
  - `aniyomi/app/src/main/java/eu/kanade/tachiyomi/extension/manga/api/MangaExtensionApi.kt`
- Extension repository metadata:
  - `aniyomi/domain/src/main/java/mihon/domain/extensionrepo/model/ExtensionRepo.kt`
  - `aniyomi/domain/src/main/java/mihon/domain/extensionrepo/service/ExtensionRepoService.kt`
- Upstream UI grouping logic:
  - `aniyomi/app/src/main/java/eu/kanade/tachiyomi/ui/browse/manga/extension/MangaExtensionsScreenModel.kt`
  - `aniyomi/app/src/main/java/eu/kanade/tachiyomi/ui/browse/anime/extension/AnimeExtensionsScreenModel.kt`
  - `mihon/app/src/main/java/eu/kanade/presentation/browse/ExtensionsScreen.kt`

## Important Constraint

Kototoro should not treat this as “download arbitrary APK from arbitrary URL”.

The intended scope is:

- repository URLs compatible with the Mihon / Aniyomi extension repository format
- repository metadata from `repo.json`
- extension index from `index.min.json`
- APK path conventions such as `apk/...`
- repository signing fingerprint and trust workflow

This is an extension ecosystem feature, not a generic APK sideload feature.

## Product Direction

### What users should be able to do

1. Open one unified Extensions screen.
2. Switch between `Manga` and `Anime`.
3. Add and manage repository URLs.
4. Refresh available extensions from repositories.
5. See:
   - updates
   - installed
   - available
   - untrusted
6. Search and filter by language or source name.
7. Install or update extensions from inside Kototoro.
8. Trust signed repositories or extensions when necessary.

### What should not be done

- Do not keep Mihon and Aniyomi as permanently duplicated UI stacks.
- Do not only add a repo URL text field on top of the current installed-only list.
- Do not skip signing/trust handling.
- Do not try to directly transplant Mihon Compose UI into Kototoro's existing Fragment/XML settings area.

## Recommended Architecture

## 1. Unify the domain model first

Introduce a Kototoro-level external extension domain independent of current Mihon/Aniyomi installed-loader implementations.

Suggested core enums and models:

```kotlin
enum class ExternalExtensionType {
    MIHON_MANGA,
    ANIYOMI_ANIME,
}

enum class ExternalExtensionStatus {
    AVAILABLE,
    INSTALLED,
    UPDATE_AVAILABLE,
    UNTRUSTED,
    OBSOLETE,
    INCOMPATIBLE,
    INSTALLING,
    FAILED,
}

data class ExternalExtensionRepo(
    val id: Long,
    val type: ExternalExtensionType,
    val baseUrl: String,
    val name: String,
    val shortName: String?,
    val website: String,
    val signingKeyFingerprint: String,
)

data class ExternalExtensionSourceStub(
    val id: Long,
    val name: String,
    val lang: String,
    val baseUrl: String?,
)

data class ExternalExtensionItem(
    val type: ExternalExtensionType,
    val pkgName: String,
    val name: String,
    val versionName: String,
    val versionCode: Long,
    val libVersion: Double?,
    val lang: String,
    val isNsfw: Boolean,
    val repoUrl: String?,
    val iconUrl: String?,
    val sources: List<ExternalExtensionSourceStub>,
    val status: ExternalExtensionStatus,
)
```

The key point is that the UI should consume one model, not separate Mihon- and Aniyomi-specific item classes.

## 2. Add a repository layer

Create a persistent repository table or storage for extension repositories.

Suggested responsibilities:

- validate user-entered URL
- normalize trailing slash handling
- fetch `repo.json`
- persist repository metadata
- prevent duplicates by `baseUrl`
- prevent unsafe ambiguity by signing fingerprint

Suggested package:

- `org.skepsun.kototoro.extensions.repo`

Suggested components:

- `ExternalExtensionRepoEntity`
- `ExternalExtensionRepoDao` or equivalent repository persistence
- `ExternalExtensionRepoService`
- `CreateExternalExtensionRepoUseCase`
- `DeleteExternalExtensionRepoUseCase`
- `RefreshExternalExtensionReposUseCase`

## 3. Add a remote extension index layer

This layer should fetch and parse repository extension indexes.

For Mihon-compatible manga repositories:

- fetch `index.min.json`
- map to `ExternalExtensionItem`
- filter by supported `libVersion`

For Aniyomi-compatible anime repositories:

- same pattern, but mapped to anime-capable source stubs

Suggested package:

- `org.skepsun.kototoro.extensions.catalog`

Suggested components:

- `MihonRemoteExtensionApi`
- `AniyomiRemoteExtensionApi`
- `ExternalExtensionCatalogRepository`

## 4. Merge installed + available + trust state

The current installed-loader flows are still useful, but they should become one input into a larger state merger.

Inputs:

- installed Mihon extensions
- installed Aniyomi extensions
- available Mihon extensions from repos
- available Aniyomi extensions from repos
- current install/download state
- trust state

Output:

- grouped UI sections:
  - updates
  - installed
  - available
  - untrusted

Suggested orchestrator:

- `ExternalExtensionsManager`

This manager should not replace the loaders immediately. Instead, it should compose:

- `MihonInstalledExtensionProvider`
- `AniyomiInstalledExtensionProvider`
- `MihonRemoteExtensionProvider`
- `AniyomiRemoteExtensionProvider`
- `ExternalExtensionInstaller`

## 5. Add installation pipeline

This is the stage where Kototoro becomes functionally comparable to Mihon/Aniyomi.

Required behaviors:

- start download from resolved APK URL
- report progress
- cancel installation
- hand off to PackageInstaller or system installer
- refresh installed state after successful install

Suggested design:

```kotlin
sealed interface ExternalInstallStep {
    data object Idle : ExternalInstallStep
    data class Downloading(val progress: Int) : ExternalInstallStep
    data object Installing : ExternalInstallStep
    data class Error(val message: String) : ExternalInstallStep
    data object Installed : ExternalInstallStep
}
```

Suggested implementation:

- `ExternalExtensionInstaller`
- subtype-specific helpers for URL resolution if needed
- PackageInstaller-based install path

## 6. Trust and compatibility

Do not defer this to “later”.

At minimum:

- validate repo metadata before persisting
- surface signing fingerprint
- mark untrusted extensions clearly
- reject incompatible library versions
- distinguish:
  - `untrusted`
  - `incompatible`
  - `obsolete`
  - `failed`

Without this, the feature is risky and hard to debug.

## UI Recommendation

## Preferred information architecture

Replace the current separate settings pages with a unified page:

- `Extensions`
  - tab: `Manga`
  - tab: `Anime`

Within each tab:

- top app bar with refresh and repository management
- optional search
- sections:
  - Updates
  - Installed
  - Available
  - Untrusted

Secondary entry:

- `Repositories`
  - tab: `Manga`
  - tab: `Anime`

This matches the upstream mental model better than two isolated installed-only pages.

## Why not copy Mihon UI directly

Reasons:

- Mihon current extension UI is Compose-based
- Kototoro current settings area is Fragment/XML-based
- direct code transplant would create stack inconsistency and larger integration cost

What should be copied:

- page structure
- section semantics
- item states
- install/update/trust workflow

What should not be copied blindly:

- Compose screen implementation
- upstream-specific dependencies and app wiring

## Suggested Kototoro UI Components

Reusable pieces:

- `ExternalExtensionsFragment`
- `ExternalExtensionsViewModel`
- `ExternalExtensionsAdapter`
- `ExternalExtensionRepositoriesFragment`
- `ExternalExtensionRepositoriesViewModel`

Tab or mode parameter:

- `ExternalExtensionType.MIHON_MANGA`
- `ExternalExtensionType.ANIYOMI_ANIME`

This removes current duplication and gives one consistent management style.

## Data Flow

```text
User adds repo URL
  -> Repo service fetches repo.json
  -> Repo persisted
  -> Remote catalog refresh fetches index.min.json
  -> Available extension items created
  -> Installed extensions loaded from current APK loaders
  -> Unified manager merges states
  -> UI renders grouped sections
  -> User taps install/update
  -> Installer downloads APK
  -> System install flow starts
  -> Package added / replaced
  -> Installed loader refreshes
  -> Unified state updates
```

## Implementation Phases

## Phase 1: Unify installed-only UI and architecture

Scope:

- replace separate Mihon/Aniyomi settings fragments with one shared UI implementation
- keep existing installed-only behavior
- unify adapters, item models, and state holders

Deliverable:

- same user-visible capability as today
- less duplication
- cleaner base for next phases

Why first:

- low risk
- immediately removes code duplication
- prepares the surface for remote catalogs

## Phase 2: Repository management

Scope:

- persistent repo storage
- add/delete/refresh repository URLs
- fetch and validate `repo.json`

Deliverable:

- repository management screen
- valid repo metadata stored locally

## Phase 3: Available extensions catalog

Scope:

- fetch `index.min.json`
- parse and merge available extensions
- show installed / available / update states

Deliverable:

- real extension marketplace view inside Kototoro

## Phase 4: Download and install

Scope:

- download APK
- installation progress
- PackageInstaller handoff
- refresh after install/update

Deliverable:

- install and update from inside Kototoro

## Phase 5: Trust and polish

Scope:

- trust prompts
- untrusted list
- incompatibility messaging
- update-all
- search and language filters

Deliverable:

- production-quality extension management

## Recommended Minimum Viable Scope

If implementation bandwidth is limited, the best MVP is:

1. unify Mihon/Aniyomi installed-extension UI
2. add repo storage and validation
3. fetch available extensions
4. support install/update through system installer

This is already a meaningful jump over current behavior.

## Risks

## Security risk

Downloading APKs from repositories requires strict trust handling. This is the highest-risk part of the feature.

## Maintenance risk

If Mihon and Aniyomi remain duplicated across model, manager, UI, and installer layers, maintenance cost will grow quickly.

## UX risk

If repository management is added without available/update/untrusted grouping, the feature will feel incomplete and confusing.

## Recommendation

The correct direction is:

- reference Mihon and Aniyomi domain design
- reuse their repository and extension concepts
- build a Kototoro-specific unified UI and orchestration layer

The first implementation step should be architectural unification, not direct APK download support.
