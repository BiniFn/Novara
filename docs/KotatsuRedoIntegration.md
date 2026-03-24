# Kotatsu-Redo Integration Reference

> Developer reference for Kototoro's integration with the `kotatsu-parsers-redo` parser library.

## Overview

Kototoro uses `com.github.Kotatsu-Redo:kotatsu-parsers-redo` as a supplementary parser library. This provides additional built-in manga sources beyond Kototoro's own native parsers. Parsers appear as regular built-in sources — users do not need to install anything.

## Architecture

### Adapter Layer

The integration works through a bridging pattern:

| Kototoro | Kotatsu-Redo |
| :--- | :--- |
| `ContentLoaderContext` | → `KotatsuLoaderContextAdapter` → `MangaLoaderContext` |
| `ContentSourceConfig` | → `KotatsuConfigAdapter` → `MangaSourceConfig` |
| `ContentSource` | → `KotatsuParserSource` → `MangaParserSource` |
| `ContentRepository` | → `KotatsuParserRepository` → `MangaParser` |

### Key Files

- `KotatsuLoaderContextAdapter.kt` — bridges Kototoro's loader context to Kotatsu's
- `KotatsuConfigAdapter.kt` — maps config keys between the two systems
- `KotatsuParserSource.kt` — wraps Kotatsu `MangaParserSource` as a Kototoro `ContentSource`
- `KotatsuParserRepository.kt` — wraps Kotatsu `MangaParser` as a Kototoro `ContentRepository`
- `KotatsuParsersProvider.kt` — lists all Kotatsu-Redo sources, creating parser instances

### Cloudflare Handling

Kotatsu-Redo parsers can trigger Cloudflare interception via `ConfigKey.InterceptCloudflare` and `requestBrowserAction`:

1. **HTTP-level detection** — `CloudFlareInterceptor` detects CF-protected responses globally
2. **Headless resolution** — `CaptchaHandler` attempts to resolve via `WebViewExecutor.tryResolveCaptcha`
3. **Interactive fallback** — `requestBrowserAction` throws `InteractiveActionRequiredException`, which `ExceptionResolver` handles by opening `BrowserActivity`

### WebView Integration

- `interceptWebViewRequests` and `captureWebViewUrls` are routed through `WebViewRequestInterceptorExecutor`
- Wired through Dagger Hilt into `ContentLoaderContextImpl`

## CI Pipeline

A GitHub Actions workflow (`.github/scripts/sync_parsers.py`) polls the Kotatsu-Redo upstream for new commits and auto-updates the pinned parser version.

## Config Key Mapping

| Kotatsu-Redo ConfigKey | Kototoro Mapping |
| :--- | :--- |
| `Domain` | `ConfigKey.Domain` |
| `ShowSuspiciousContent` | `ConfigKey.ShowSuspiciousContent` |
| `UserAgent` | `ConfigKey.UserAgent` |
| `SplitByTranslations` | `ConfigKey.SplitByTranslations` |
| `PreferredImageServer` | `ConfigKey.PreferredImageServer` |
| `DisableUpdateChecking` | Falls back to default (not applicable) |
| `InterceptCloudflare` | Falls back to default (handled via existing CF pipeline) |
