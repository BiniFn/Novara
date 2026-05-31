---
name: haze-performance
description: Expert guidance on Haze performance optimization, platform-specific backends (Android API levels, Skiko/Skia), benchmarks, and known limitations. Use when diagnosing blur performance issues, choosing platform strategies, or benchmarking Haze in production.
---

# Haze Performance — Platform Support & Optimization

## Instructions

### 1. Input Scale — Primary Optimization

Reduce source rendering resolution before applying the blur, then scale back up.

```kotlin
Modifier.hazeEffect(state = hazeState) {
    inputScale = HazeInputScale.Auto
    blurEffect { /* ... */ }
}
```

| Value | Pixel Reduction | Visual Impact | Recommended For |
|---|---|---|---|
| `None` (default) | 0% | Best quality | Development |
| `Auto` | Platform default | Imperceptible | Production |
| `Fixed(0.66)` | ~55% | Imperceptible | Quality-critical |
| `Fixed(0.5)` | ~75% | Noticeable | Balanced |
| `Fixed(0.33)` | ~89% | Visibly different | Performance-critical |

Reduces Haze cost by 5-20% (Android benchmark), translating to ~3-5% total frame duration reduction.

### 2. Progressive Blur vs Masking

| Technique | Cost | Visual Quality | When to Use |
|---|---|---|---|
| **Masking** | Negligible (~+5%) | Good (opacity fade) | Performance-sensitive UIs |
| **Progressive** | ~+25% (API 33+), ~2x (API 31-32) | Excellent (radius fade) | High-quality iOS-style effects |

> Prefer masking for most cases. Use progressive only when the visual difference matters.

### 3. Platform Backend Selection

Haze auto-selects the optimal backend per platform:

| Platform | Backend | Quality | Notes |
|---|---|---|---|
| Android 13+ (API 33+) | `RenderEffect.createBlurEffect()` | Optimal | All features supported |
| Android 12/12L (API 31-32) | `RenderNode` + shader | Good | Linear progressive only; others → mask fallback |
| Android 11- (API ≤30) | RenderScript (experimental) | Laggy | Disabled by default; enable with `blurEnabled = true` |
| Desktop (JVM) | Skia shaders | Optimal | All features supported |
| iOS | Skia shaders | Optimal | All features supported |
| Web/Wasm | Canvas filters / custom shaders | Good | Limited testing |

### 4. Android 12/12L Workarounds

Only linear gradient progressive is natively supported. Force performance fallback:

```kotlin
blurEffect {
    progressive = HazeProgressive.verticalGradient(
        startIntensity = 1f, endIntensity = 0f,
        preferPerformance = true  // Use mask instead of multi-draw
    )
}
```

Other progressive types (`RadialGradient`, `Brush`) always fall back to masks on API 31-32.

### 5. Android 11 and Below — RenderScript

Blur is **disabled by default** (falls back to scrim). Enable explicitly:

```kotlin
blurEffect { blurEnabled = true }
```

Drawbacks:
- Slower — requires copying content for RenderScript processing
- Frame-behind — background thread processing means updates lag by 1+ frames
- Skips frames — only processes one frame at a time; drops new draws during processing
- Haze uses `ViewTreeObserver.OnPreDrawListener` for manual invalidation (more global than ideal)

### 6. Android 12 and Below — Manual Invalidation

Haze subscribes to `ViewTreeObserver.OnPreDrawListener` for layer invalidation when content changes. This is more global than needed (fires on *any* draw, not just `hazeSource` nodes) but should not cause performance issues. Disable blur if needed:

```kotlin
blurEffect { blurEnabled = false }
```

### 7. Benchmark Reference (Pixel 6, Android latest)

| Scenario | Without Haze | With Haze | Overhead |
|---|---|---|---|
| Scaffold | 7.5ms | 9.7ms | +29% |
| Images List | 6.6ms | 9.6ms | +45% |
| Credit Card | 6.6ms | 13.1ms | +98% |

*P90 frame durations. Varies by device and scenario.*

**Feature cost on top of Haze baseline:**

| Feature | Cost |
|---|---|
| Masking | +5% (negligible) |
| Progressive (API 33+ shader) | ~0% (negligible) |
| `inputScale = 0.5` | -5% to -20% of Haze cost |

### 8. Disable Blur Per Effect

```kotlin
blurEffect { blurEnabled = false }
```

Also disable globally at `HazeState` level for older APIs if needed.

### 9. Screenshot Testing

Android Robolectric: run at SDK 35+ for correct `RenderEffect` tile mode:

```kotlin
@Config(sdk = [35])
class MyScreenshotTest { /* ... */ }
```

### 10. Skiko Platforms (Desktop, iOS, Web)

Desktop JVM, iOS, and Web (Wasm/JS) are all Compose Multiplatform targets built on [Skiko](https://github.com/JetBrains/skiko), which bundles the Skia Graphics Library. This means:

- The host platform is not particularly important to Haze — Skiko/Skia handles the abstraction.
- All Skiko targets run Haze in its **optimal environment** (Skia `ImageFilter`-based blur).
- Haze is built against the latest stable Compose Multiplatform version.
- Pre-release CMP versions are occasionally verified but not guaranteed — test thoroughly.

**Key takeaway:** Desktop, iOS, and Web all share the same high-quality Skia shader backend. No platform-specific caveats like Android API-level fragmentation.

### 11. Emulator Rendering

The RenderScript path (API ≤30) does NOT render blur on the Android Emulator. Works on physical devices only.

### 12. Checklist

- [ ] Use `HazeInputScale.Auto` or `Fixed(0.5)` in production.
- [ ] Prefer masking over progressive for performance.
- [ ] Test progressive on Android 12 devices if targeting that API level.
- [ ] Consider `blurEnabled = false` on API ≤30 unless RenderScript tradeoffs are acceptable.
- [ ] Write Macrobenchmark tests for your specific UI.
- [ ] Validate on physical devices, not just emulator (especially for API ≤30).
