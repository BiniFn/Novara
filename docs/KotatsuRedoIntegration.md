# Kotatsu-Redo Integration Handoff

**Current Status:** The first 4 critical stages of migrating Kototoro's supplementary parser layer to `com.github.Kotatsu-Redo:kotatsu-parsers-redo` are **COMPLETE** and fully compiling (`:app:assembleDebug`).

## 1. Accomplished Work

### Dependencies & CI Sync
*   Swapped `com.github.YakaTeam:kotatsu-parsers` -> `com.github.Kotatsu-Redo:kotatsu-parsers-redo`.
*   Pinned in `libs.versions.toml:kotatsuParsers = "e5fa47d829"`.
*   Re-wrote GitHub Action CI pipelines (`.github/scripts/sync_parsers.py` & `.github/scripts/test_sync_parsers.py`) to systematically poll and auto-update against the `Kotatsu-Redo` upstream.

### API Bridging (`KotatsuLoaderContextAdapter`)
*   Dropped the deprecated `evaluateJs(url, script)` signature in favor of the new mandatory `timeout` variant (`evaluateJs(url, script, timeout)`).
*   Introduced nullable mappings for the newly added `DisableUpdateChecking` and `InterceptCloudflare` configs inside `KotatsuConfigAdapter` (using `getOrNull()` mappings to avoid exhaustive enum compiler crashes).

### Native Headless WebView Interceptor Port
*   Ported `WebViewRequestInterceptorExecutor` and `RequestInterceptorWebViewClient` entirely into `org.skepsun.kototoro.core.network.webview`.
*   Rewrote their imports to natively inherit from Kototoro's `BrowserClient` and `BrowserCallback` while extracting their payloads into the parser's expected `InterceptedRequest`.
*   Wired the new executor through Dagger Hilt into `ContentLoaderContextImpl` and routed down perfectly into `KotatsuLoaderContextAdapter` (`interceptWebViewRequests` / `captureWebViewUrls`).
*   **Resolved all Kotlin Smart Cast failures** at the module boundaries directly.

## 2. Next Steps To Complete (Cross-Device Handoff)

You opted to pause before tackling the final Cloudflare alignment. When you switch devices, **pick up the development here**:

### Step 5: Port Cloudflare Interception (Optional but Recommended)
Kotatsu-Redo parsers have introduced the ability for **parsers to actively ask for Cloudflare overrides** via `ConfigKey.InterceptCloudflare`.

**Action Items:**
1. You will need to extract `CloudFlareInterceptClient.kt` from the `Kotatsu-Redo` repository (`app/src/main/kotlin/org/koitharu/kotatsu/core/network/`).
2. Adapt it securely to replace or augment Kototoro's native `org.skepsun.kototoro.browser.cloudflare.CloudFlareClient` intercept logic.
3. Wire the interceptor so that when `KotatsuParserRepository` sees a source configuration marked with `InterceptCloudflare`, it bypasses the standard HTTP pipeline in favor of the specialized CF wrapper.

---
*Happy coding on your other workstation! Once you finish Step 5, you'll have 100% feature and parser protection parity with Kotatsu-Redo!*
