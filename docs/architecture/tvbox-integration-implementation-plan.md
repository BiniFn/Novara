# TVBox Integration Implementation Plan

This document turns TVBox support from a partially stubbed source type into a workable Kototoro feature.

Status date: March 13, 2026.

## Progress Snapshot

As of March 13, 2026, Kototoro has already moved beyond pure TVBox scaffolding:

- TVBox import is wired into the shared JSON import flow.
- Repository JSON with `sites` and multi-repository JSON with `urls` are both supported.
- Imported TVBox sites are visible in source management, filtering, and video-oriented browse group entry points.
- TVBox settings already expose stored metadata such as `site.api`, `site.jar`, `root.spider`, runtime candidates, and support summaries.
- `TVBoxRepository` already supports:
  - direct media URLs
  - M3U playlists
  - plain-text live/channel lists
  - simple JSON playlists
  - a subset of CMS-style TVBox endpoints
- A first local QuickJS runtime path now exists for `type = 4` TVBox JS spiders:
  - script loading from local or remote locator
  - basic host bridge for `http/req/request/local/joinUrl`
  - basic mapping of `home/homeVod/category/search/detail/play`
  - a minimal `js2Proxy -> local HTTP proxy -> spider.proxy()` bridge
  - loopback playback URLs are excluded from secondary proxy wrapping

Still missing:

- `type = 3` / `csp_*` / JAR spider runtime
- `//bb` bytecode support
- ES module-based TVBox JS loading
- `cat.js` dependency chain
- full `proxyLocal` parity for complex binary and multi-stage spider proxy flows
- parity with Android Box for complex spider ecosystems

## Goal

Add `TVBox` as a first-class source origin alongside built-in, Mihon, Aniyomi, and Legado, with these product constraints:

- TVBox sources are video-only.
- Import must support local files and remote URLs.
- The existing JSON import entry should be reused instead of adding a separate TVBox import screen.
- Two input shapes must be supported:
  - repository JSON: one document with many `sites`
  - multi-repository JSON: one document with many child repository URLs in `urls`

## Current State

Kototoro already contains partial TVBox scaffolding:

- `JsonSourceType.TVBOX` already exists in [`JsonSourceEntity.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/core/db/entity/JsonSourceEntity.kt).
- TVBox prefixes and labels already exist in [`SourceTypeIdentifier.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/core/jsonsource/SourceTypeIdentifier.kt).
- TVBox is already mapped to video content in [`MangaSource.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/core/model/MangaSource.kt) and [`SourceGroupManager.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/core/jsonsource/SourceGroupManager.kt).
- A basic TVBox model exists in [`TVBoxConfig.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/core/model/jsonsource/TVBoxConfig.kt).

But the end-to-end feature is not implemented:

- [`ImportJsonDialogFragment.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/settings/sources/jsonsource/ImportJsonDialogFragment.kt) hardcodes Legado as the selected type.
- [`ImportJsonViewModel.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/settings/sources/jsonsource/ImportJsonViewModel.kt) returns `UnsupportedOperationException` for `TVBOX`.
- [`JsonSourceManager.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/core/jsonsource/JsonSourceManager.kt) has no TVBox importer.
- [`MangaRepository.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/core/parser/MangaRepository.kt) returns `EmptyMangaRepository` for TVBox.
- [`SearchSourceTypes.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/search/domain/SearchSourceTypes.kt), [`SourceTag.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/explore/ui/model/SourceTag.kt), and [`BrowseGroupTab.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/explore/ui/model/BrowseGroupTab.kt) do not expose TVBox as a selectable source origin.
- [`JsonSourcesViewModel.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/settings/sources/jsonsource/JsonSourcesViewModel.kt) assumes JSON sources are Legado-shaped for parsing, validation, and export.

## External Reality Check

The referenced [`takagen99/Box`](https://github.com/takagen99/Box) repository is a fork of `TVBoxOSC`. That matters because real-world TVBox compatibility is not just “parse some JSON”:

- many sources are `type = 3`
- many sources depend on `spider` plus `api = csp_*`
- the upstream client includes QuickJS-related runtime pieces

Inference from that repo and from the provided sample JSON:

- a large share of practically used TVBox sources require a spider runtime, not just direct HTTP fetching
- importer-only work is easy compared with full playback compatibility

The plan therefore separates basic TVBox ingestion from full spider compatibility.

## DecoTV Reference

The local reference project [`DecoTV`](../../../DecoTV/) is useful, but its spider support model is not the same as an Android in-process runtime.

Observed reference points:

- [`spiderJar.ts`](../../../DecoTV/src/lib/spiderJar.ts) provides a public `spider.jar` with fallback and cache logic.
- [`config/route.ts`](../../../DecoTV/src/app/api/tvbox/config/route.ts) rewrites exported TVBox config so clients receive a reachable spider URL.
- [`proxy/cms/route.ts`](../../../DecoTV/src/app/api/proxy/cms/route.ts), [`proxy/m3u8/route.ts`](../../../DecoTV/src/app/api/proxy/m3u8/route.ts), and [`proxy/stream/route.ts`](../../../DecoTV/src/app/api/proxy/stream/route.ts) proxy remote resources over HTTP.

Inference:

- DecoTV supports many `spider` sources through a server-side bridge model.
- It does not prove that Kototoro can already execute TVBox spider or JAR logic locally on Android.
- Reusing its ideas is realistic for architecture, but not as a drop-in code transplant.

Practical implication for Kototoro:

- current Kototoro work should continue to import and expose `spider/jar/csp` metadata
- direct media, playlist, and CMS-like candidates can run locally when they do not need spider execution
- full `spider` compatibility should be treated as a separate delivery track

## Box Reference

The local Android client [`Box`](../../../Box/) is the opposite reference point: it does implement a native Android TVBox runtime.

Observed reference points:

- [`ApiConfig.java`](../../../Box/app/src/main/java/com/github/tvbox/osc/api/ApiConfig.java) parses root `spider`, per-site `jar`, `sites`, `rules`, `parses`, and `lives`, then dispatches runtime by source type.
- [`JarLoader.java`](../../../Box/app/src/main/java/com/github/catvod/crawler/JarLoader.java) downloads JAR files, caches them, and loads `com.github.catvod.spider.*` through `DexClassLoader`.
- [`JsLoader.java`](../../../Box/app/src/main/java/com/github/catvod/crawler/JsLoader.java) downloads optional JS API JARs and bridges JS spiders into the same runtime contract.
- [`Spider.java`](../../../Box/app/src/main/java/com/github/catvod/crawler/Spider.java) defines the runtime interface: `homeContent`, `categoryContent`, `detailContent`, `searchContent`, `playerContent`, `proxyLocal`.
- [`JsSpider.java`](../../../Box/app/src/main/java/com/github/tvbox/osc/util/js/JsSpider.java) shows that Box also embeds a QuickJS-based execution path, not just direct HTTP parsing.

Inference:

- Box proves that full TVBox `spider/jar/csp` compatibility is feasible on Android.
- But it also shows the real cost: Kototoro would need a dedicated runtime subsystem, not a small parser patch.
- The minimum local-runtime feature set is larger than expected:
  - dynamic JAR loading
  - Spider lifecycle management and caching
  - JS-to-host bindings for the TVBox spider contract
  - a proxy bridge for `proxyLocal`
  - a TVBox-shaped JSON result adapter layer

Practical implication for Kototoro:

- direct media, playlists, and CMS fallback remain the fastest path to useful TVBox support
- Kototoro already embeds QuickJS for existing JavaScript-based sources, so `type = 4` TVBox JS spiders are a more realistic near-term target than `csp/jar`
- if Kototoro later chooses full local compatibility, Box is the better code reference than DecoTV
- if Kototoro wants lower implementation and security cost first, DecoTV-style gateway bridging is still the smaller step

## Format Support

### 1. Repository JSON

Detect by root fields such as:

- `sites`
- optional `spider`, `parses`, `lives`, `rules`, `headers`, `ads`, `doh`

Import result:

- each supported `site` becomes one Kototoro source entry
- shared root-level fields are attached to each imported site as runtime metadata

### 2. Multi-Repository JSON

Detect by root field:

- `urls`

Import result:

- fetch each child URL
- parse each child as a repository JSON
- flatten all imported sites into normal TVBox source entries

### 3. Input Hygiene

The importer should preprocess:

- UTF-8 BOM
- leading `//` comment lines
- blank lines around JSON

This is necessary because common TVBox lists often contain banner comments before the JSON object.

## Recommended Data Model

Do not persist raw TVBox repositories as one `JsonSourceEntity` per document.

Recommended storage unit:

- one `JsonSourceEntity` per TVBox site

Recommended persisted config:

- introduce a normalized `TVBoxSourceConfig` wrapper instead of storing only the current `TVBoxConfig`

The wrapper should keep:

- site identity: `key`, `name`, `type`, `api`, `ext`, `jar`, `playUrl`
- root context: `spider`, `parses`, `rules`, `headers`, `lives`, `ads`
- import provenance: source URL or local locator, parent repository name, child repository URL for multi-repo imports
- raw JSON fragments when needed so unknown fields are not lost

This is important because the current [`TVBoxConfig.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/core/model/jsonsource/TVBoxConfig.kt) model does not preserve enough of the provided sample:

- `headers`
- `rules`
- `doh`
- live `playerType` and `ua`
- site-level fields such as `changeable`, `timeout`, `style`, `indexs`

If the importer parses then reserializes only the current model, those fields are dropped before runtime ever sees them.

## Source Identity Strategy

Current JSON ID generation is URL-based and works for Legado:

- [`JsonSourceManager.generateSourceId()`](../../app/src/main/kotlin/org/skepsun/kototoro/core/jsonsource/JsonSourceManager.kt)

That strategy is not sufficient for TVBox because one repository URL contains many sites.

Recommended TVBox source fingerprint:

- canonical import locator
- parent repository URL if available
- site `key`
- site `api`
- normalized `ext` when present

This prevents all sites from one repository collapsing into the same `JSON_TVBOX_*` ID.

## Delivery Phases

## Phase 1: Import Pipeline

### Goal

Make TVBox import usable from the existing JSON import dialog.

### Tasks

- Add real source-type selection to [`ImportJsonDialogFragment.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/settings/sources/jsonsource/ImportJsonDialogFragment.kt).
- Keep using the existing local-file and remote-URL flows.
- Add `JsonSourceManager.importTvBoxJson(...)`.
- Detect repository JSON vs multi-repository JSON.
- Add recursive fetch for multi-repository import with:
  - max depth `1`
  - dedup by canonical URL
  - bounded concurrency
  - per-child timeout
- Normalize imported entries into one entity per site.

### Acceptance Criteria

- A local TVBox repository JSON imports successfully.
- A remote TVBox repository JSON imports successfully.
- A multi-repository JSON imports all reachable child repositories.
- Import result reports the number of imported sites, not the number of repository documents.

## Phase 2: Source Catalog Visibility

### Goal

Make imported TVBox sites discoverable anywhere users already manage sources.

### Tasks

- Add TVBox to search type options in [`SearchSourceTypes.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/search/domain/SearchSourceTypes.kt).
- Split `SourceTag.LEGADO` into explicit JSON-origin tags or add a dedicated `TVBOX` tag in [`SourceTag.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/explore/ui/model/SourceTag.kt).
- Allow video browse tab to expose TVBox in [`BrowseGroupTab.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/explore/ui/model/BrowseGroupTab.kt).
- Update JSON source management screens to parse TVBox configs without assuming Legado fields.
- Ensure source chips, summaries, and origin labels consistently show `TVBox`.

### Acceptance Criteria

- Imported TVBox sources appear in source catalog and JSON source management.
- Video browse filters can narrow to TVBox sources.
- Search source-type dialog can include or exclude TVBox explicitly.

## Phase 3: TVBox Runtime Repository

### Goal

Back imported TVBox sources with a real video repository instead of `EmptyMangaRepository`.

### Tasks

- Introduce `TVBoxRepository` or `TVBoxVideoRepository`.
- Wire it from [`MangaRepository.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/core/parser/MangaRepository.kt).
- Adapt TVBox runtime into Kototoro’s existing unified model:
  - source home or categories -> `getList()`
  - site details -> `getDetailsImpl()`
  - episodes or play groups -> `MangaChapter`
  - stream candidates -> `MangaPage`
  - final playback URL -> `getPageUrl()`
- Reuse the existing video playback path used by Aniyomi repositories.

### Notes

Kototoro’s video path already expects this shape:

- details page holds chapters for episodes
- video player resolves the stream through repository `getPageUrl()`

That existing contract should be reused rather than creating a parallel TVBox-only playback stack.

### Acceptance Criteria

- At least one supported TVBox source can:
  - show a list
  - open details
  - enumerate playable episodes
  - resolve a playable stream URL

## Phase 4: Compatibility Tiers

### Goal

Avoid pretending that all TVBox sources are equally supported.

### Tier A: Direct sources

Support first:

- `type = 0`
- `type = 1`
- `type = 4` cases that can be adapted onto Kototoro's existing QuickJS host runtime

### Tier B: Spider sources

Support later:

- `type = 3`
- `api = csp_*`
- `spider` JAR or script loading

### Bridge option

An intermediate path exists before a full local runtime:

- define a gateway or bridge endpoint for spider-dependent TVBox sites
- let Kototoro forward requests to that gateway when direct playback and CMS fallback are not enough
- keep the Android client focused on HTTP contracts instead of arbitrary JAR execution

This is the closest model to the current DecoTV implementation.

### Local runtime option

The full Android-native path would look closer to Box:

- introduce a Kototoro-side `TVBoxSpiderRuntime` abstraction
- define a narrow interface matching the TVBox spider lifecycle:
  - home
  - category
  - detail
  - search
  - play
  - optional proxy
- decide whether Kototoro is willing to ship:
  - `DexClassLoader`-based JAR execution
  - a TVBox-specific QuickJS binding layer built on top of Kototoro's existing QuickJS runtime
  - extra sandboxing and kill-switches for untrusted source code

This path offers the highest compatibility, but it is much more complex and security-sensitive than the gateway approach.

### Why this split matters

The provided sample is mostly Tier B. Without a spider runtime, importer success would still lead to non-playable sources.

### Acceptance Criteria

- The app can label unsupported spider-dependent sources clearly instead of failing silently.
- Supported and unsupported TVBox sites are distinguishable in UI and logs.

## Phase 5: Spider Runtime

### Goal

Reach practical compatibility with common TVBox repositories.

### Tasks

- Evaluate whether to load spider implementations through:
  - the already embedded QuickJS runtime for script-based spiders
  - controlled Dex or JAR loading for Java-based spiders
- define a TVBox JS host bridge that exposes the functions TVBox scripts expect, instead of treating QuickJS availability itself as the missing piece
- Define a narrow compatibility surface rather than executing arbitrary app code.
- Add security restrictions:
  - allowlist protocol handling
  - timeout and memory limits
  - no unrestricted filesystem access
- Surface per-source diagnostics when spider loading fails.

### Non-goal for first delivery

- full compatibility with every TVBoxOSC ecosystem fork

The first milestone should prefer a clear support matrix over an unsafe “try to execute everything” design.

## Phase 6: Validation, Export, and Maintenance

### Tasks

- Add importer tests for:
  - repository JSON
  - multi-repository JSON
  - comment-prefixed JSON
  - duplicate site IDs
- Add runtime tests for at least one direct TVBox source fixture.
- Decide export behavior:
  - either export normalized per-site TVBox configs
  - or explicitly mark repository reconstruction as unsupported for now
- Extend logging in [`JsonSourceLogger.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/core/jsonsource/JsonSourceLogger.kt) with TVBox-specific stages.

## Open Design Decisions

### 1. How should `lives` be exposed?

Options:

- import `lives` as pseudo-series with one chapter per channel
- ignore `lives` in V1

Recommendation:

- ignore `lives` in V1 unless a concrete playback requirement appears

Reason:

- it keeps the first delivery focused on on-demand video repositories

### 2. Should unsupported spider sources still be importable?

Recommendation:

- yes, but mark them as `imported / not yet runnable`

Reason:

- users can keep their catalog
- implementation can progress without changing import data later

### 3. Should TVBox reuse Legado validation switches?

Recommendation:

- partially reuse the dialog, not the validation semantics

Reason:

- `skip unreachable` and `skip no explore` are Legado-specific concepts
- TVBox needs different validation, such as child-repository fetch failures and unsupported spider mode

## Primary File Touchpoints

- importer UI:
  - [`ImportJsonDialogFragment.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/settings/sources/jsonsource/ImportJsonDialogFragment.kt)
  - [`ImportJsonViewModel.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/settings/sources/jsonsource/ImportJsonViewModel.kt)
- importer backend:
  - [`JsonSourceManager.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/core/jsonsource/JsonSourceManager.kt)
  - [`TVBoxConfig.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/core/model/jsonsource/TVBoxConfig.kt)
- source classification and browse UI:
  - [`SourceTypeIdentifier.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/core/jsonsource/SourceTypeIdentifier.kt)
  - [`SourceGroupManager.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/core/jsonsource/SourceGroupManager.kt)
  - [`SourceTag.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/explore/ui/model/SourceTag.kt)
  - [`BrowseGroupTab.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/explore/ui/model/BrowseGroupTab.kt)
  - [`SearchSourceTypes.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/search/domain/SearchSourceTypes.kt)
- runtime:
  - [`MangaRepository.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/core/parser/MangaRepository.kt)
  - new `TVBoxRepository` package under `core/parser/`
- management:
  - [`JsonSourcesViewModel.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/settings/sources/jsonsource/JsonSourcesViewModel.kt)
  - [`GroupedJsonSourcesAdapter.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/settings/sources/jsonsource/GroupedJsonSourcesAdapter.kt)

## Recommended Delivery Order

1. Implement import normalization and per-site storage.
2. Make TVBox visible in browse, search, and source management.
3. Deliver direct-source playback with a real TVBox repository.
4. Add clear unsupported-state handling for spider-dependent sources.
5. Only then attempt spider runtime support.

This keeps the first usable milestone small, testable, and consistent with KISS and YAGNI.
