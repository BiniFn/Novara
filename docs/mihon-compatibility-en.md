# Kototoro – Mihon Extension Compatibility Report

## 1. Goal
Kototoro ships Mihon (ex-Tachiyomi) extension support:
- Detect installed Mihon extension APKs and load their `Source`/`CatalogueSource`.
- Reuse Kototoro networking, cache, and UI while keeping isolation and stability.
- Map Mihon data/filter models into Kototoro’s parser layer seamlessly.

## 2. Architecture (diagram)
```
[Mihon Extension APK]
      | (Manifest: tachiyomi.extension.*)
      v
[MihonExtensionLoader] -- ChildFirstPathClassLoader --> [Source instances]
      |                       ^
      |                       |
      |              [KotoInjektBridge + KotoNetworkHelper]
      v
[MihonExtensionManager] -- caches --> [MihonMangaSource wrappers]
      |
      v
[MihonMangaRepository] -- mapping --> [Kototoro Parser layer] --> [UI: Mihon sources, reader]
```

## 3. Flow Breakdown
### 3.1 Discovery and metadata
- File: `mihon/MihonExtensionLoader.kt`
- Scan installed packages via `PackageManager`; detect by:
  - Feature `tachiyomi.extension`
  - Package hints: `.extension`/`eu.kanade.tachiyomi.`/`org.keiyoushi.`
  - Manifest meta-data: `tachiyomi.extension.class` or `tachiyomi.extension.factory`
- Version gate: `LIB_VERSION_MIN=1.2`, `LIB_VERSION_MAX=1.9`; out-of-range is rejected.
- NSFW flag: `tachiyomi.extension.nsfw`.
- Language: parsed from package name segment after `extension.`.

### 3.2 Injekt bridge
- Files: `MihonModule.kt`, `compat/KotoInjektBridge.kt`
- Before loading, call `initialize()` to inject:
  - `Application`/`Context`
  - `OkHttpClient`, `CookieJar`
  - `NetworkHelper` impl: `KotoNetworkHelper` (drops `GZipInterceptor` to avoid bad `Content-Encoding:gzip`)
  - `Json`/`StringFormat`/`SerialFormat`

### 3.3 ClassLoader isolation
- File: `util/ChildFirstPathClassLoader.kt`
- Child-first loading of extension dex to avoid dependency clashes.
- Whitelisted prefixes (e.g., `kotlin.`, `android.`, `eu.kanade.tachiyomi.*`) always delegate to parent for shared API.

### 3.4 Source loading and caching
- `loadSources(...)` instantiates `Source` or `SourceFactory#createSources()`.
- `MihonExtensionManager` caches by `source.id`, wraps into `MihonMangaSource`, and appends language suffix when names collide.

### 3.5 Model conversion
- File: `model/MihonDataConverters.kt`
- Conversions:
  - `SManga` ↔ `Manga`: absolute URLs, cover fallbacks, adult rating, author/state mapping.
  - `SChapter` ↔ `MangaChapter`: stable IDs, reverse-list fallback numbering to fix order.
  - `Page` ↔ `MangaPage`: unique page IDs from chapter URL + index to avoid cache collisions.
  - Public URLs: safe wrappers around `HttpSource.getMangaUrl/getChapterUrl`.
- URL cleanup handles duplicated base URLs and malformed protocols (`https//`).

### 3.6 Filter mapping
- File: `MihonFilterMapper.kt`
- Map Mihon `FilterList` to Kototoro `MangaListFilterOptions` (Header/Group/Select/Sort/Text).
- Reverse mapping applies selected Kototoro tags back to Mihon filters (TriState/include-exclude, Sort).

### 3.7 Repository adaptation
- File: `MihonMangaRepository.kt`
- Lists: map `SortOrder` to Mihon popular/latest/search with aligned pagination.
- Details/chapters: retry on IO, fill missing fields, reverse + renumber chapters to enforce ascending order.
- Images:
  - Copy headers from Mihon `HttpSource`; add Referer if missing.
  - For page URLs needing resolution, use `mihon://resolve` then call `getImageUrl`.
  - Cover requests prefer `imageRequest`, fallback to base implementation.

### 3.8 UI integration
- Entry: `res/xml/pref_sources.xml` (Mihon settings link); layout `fragment_mihon_extensions.xml`.
- Strings around `res/values/strings.xml` (1125+).
- Use case: `GetMihonSourcesUseCase.kt` produces `MihonSourceItem` with language suffix and NSFW flag.

## 4. Key files
- Loader/Manager: `MihonExtensionLoader.kt`, `MihonExtensionManager.kt`
- Bridge: `MihonModule.kt`, `compat/KotoInjektBridge.kt`, `compat/KotoNetworkHelper.kt`
- Isolation: `util/ChildFirstPathClassLoader.kt`
- Models: `model/MihonDataConverters.kt`, `model/MihonMangaSource.kt`
- Filters: `MihonFilterMapper.kt`
- Repository: `MihonMangaRepository.kt`
- UI/Use case: `GetMihonSourcesUseCase.kt` + XML resources

## 5. Compatibility risks
- Version window 1.2–1.9 only.
- Dependency clashes: adjust parent-package whitelist if class conflicts arise.
- Network interceptors: Mihon client omits `GZipInterceptor`; new host interceptors must be copied/filtered in `KotoNetworkHelper`.
- Chapter ordering: some sources may still misorder; add per-source sorting if needed.
- Trust: signature verification is not enforced (`Untrusted` not used); keep extension channels controlled.

## 6. Debug tips
- Scan logs: look for `MihonExtensionLoader` in logcat for feature/name/meta detection.
- Version errors: “Incompatible lib version” → upgrade/downgrade extension.
- Network: `MihonNetwork` logs request/response code and 200-char preview on failures.
- URL issues: heed `MihonDataConverters` warnings (duplicate baseUrl, bad protocol); patch per-source if necessary.
- Filters: confirm `MihonFilterMapper` applies TriState/Sort choices correctly.
