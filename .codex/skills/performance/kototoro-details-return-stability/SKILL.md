---
name: kototoro-details-return-stability
description: Diagnose and fix Kototoro Android issues where returning from reader or video player to details causes flicker, cover reload, panorama reload, progress reset, status bar jump, Compose subtree disposal, or Activity recreation. Use for DetailsScreen, VideoPlayerActivity, MainActivity, system bars/insets, shared transition, Coil cache key, and reader page identity regressions.
---

# Kototoro Details Return Stability

Use this skill when a return path to details looks visually unstable: cover flashes, panorama flashes, source icon reloads, progress briefly resets, top area jumps, or logs suggest `DetailsScreen` is disposed and recreated.

## Debug Order

1. Prove whether the details subtree is recreated before touching image code.
2. Check whether `MainActivity` is recreated. If the Activity instance changes, local Coil or Compose fixes only mask the symptom.
3. Check system bar visibility and inset stability. Transparent bars and hidden bars are different; hidden bars change insets.
4. Only after lifecycle/insets are stable, inspect Coil request identity, cache keys, and crossfade.
5. For reader page regressions, verify page identity includes chapter and URL, not just duplicate parser page IDs.

## Minimal Diagnostics

Add temporary logs, then remove them before final commit:

```kotlin
DisposableEffect(content?.id, content?.source?.name, content?.url) {
    Log.d("DetailsLifecycle", "DetailsScreen enter id=${content?.id} source=${content?.source?.name} url=${content?.url}")
    onDispose {
        Log.d("DetailsLifecycle", "DetailsScreen dispose id=${content?.id} source=${content?.source?.name} url=${content?.url}")
    }
}
```

Also log `MainActivity onCreate/onDestroy` if the details subtree is disposed. A changing `MainActivity@...` in `ImageRequest.context` is strong evidence of Activity recreation.

## Known Fix Pattern

- `MainActivity` should handle the same relevant config changes as the player when video orientation or fullscreen UI can affect the background Activity:
  `orientation|screenSize|keyboardHidden|smallestScreenSize|screenLayout|uiMode`.
- `VideoPlayerActivity` can use transient immersive bars for visual fullscreen, but avoid explicit show/hide churn on exit.
- Details should use stable status bar insets such as `WindowInsets.statusBarsIgnoringVisibility`; keep a fallback from Android `status_bar_height` when current inset is `0.dp`.
- Coil requests used by details covers, panorama, source options, and home hero should use stable `memoryCacheKey`/`diskCacheKey` derived from source, owner URL/id, and image URL.
- Disable crossfade for already-present details hero/cover/panorama surfaces when the goal is return stability.
- If a log only shows non-current content keys, it may be disposal of previous home hero or transition residue, not current details reload.

## Reader Duplicate Page Guardrail

Some Mihon sources emit many pages with duplicate image IDs. Reader identity must not rely only on `page.id`.

Use composite identity including:

- `chapterId`
- page index
- page URL
- split part if applicable
- source key in loader task caches

If cached manga always opens or renders as page one, inspect `ReaderPage.readerKey`, adapter diff keys, `PageLoader` task cache keys, and repository dedupe logic.

## Verification

Run:

```bash
JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64" ./gradlew :app:compileDebugKotlin --no-daemon
```

Manual acceptance signals:

- Returning from player does not log `MainActivity onDestroy/onCreate`.
- Returning from player does not log current `DetailsScreen dispose/enter`.
- Cover, source icon, progress, and panorama remain visually stable.
- Reader cached duplicate-image manga no longer collapses to the first page.
