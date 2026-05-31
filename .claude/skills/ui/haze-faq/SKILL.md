---
name: haze-faq
description: Frequently asked questions about Haze. Use when asked about Haze vs Modifier.blur, cross-platform blur consistency, or which Android versions Haze supports.
---

# Haze — FAQs

## Instructions

### 1. What's the difference between Haze and Modifier.blur?

[Modifier.blur](https://developer.android.com/reference/kotlin/androidx/compose/ui/Modifier#\(androidx.compose.ui.Modifier\).blur\(androidx.compose.ui.unit.Dp,androidx.compose.ui.unit.Dp,androidx.compose.ui.draw.BlurredEdgeTreatment\)) and Haze provide different things:

- **Haze**: primarily **background blurring** — blurs content behind a composable. Required for glass-like effects (glassmorphism).
- **Modifier.blur**: performs **foreground blurring** (content blurring) — blurs the composable itself.

Haze can also do foreground blurring (omit `state` from `hazeEffect`), but background blurring is its primary strength. Haze additionally provides: tint overlays, noise texture, progressive gradients, masking, pre-built materials (Material/Cupertino/Fluent), and multi-platform support.

Use Haze when you need content-behind-blur (app bars, dialogs, cards). Use `Modifier.blur` for simple content-only blurring.

### 2. Are the blur implementations the same across different platforms?

Yes. The majority of the implementation is shared across all platforms.

The only differences are in platform-specific blur backends:

| Platform | Blur Backend |
|---|---|
| Android 13+ | `RenderEffect.createBlurEffect()` |
| Android 12 | `RenderNode` + shader |
| Android 11- | RenderScript |
| Desktop / iOS | Skia shaders |
| Web / Wasm | Canvas filters / custom shaders |

All platforms implement blur in very similar ways — only the platform classes differ.

### 3. What versions of Android does Haze work on?

Haze works on **Android API 11+**. The experience varies by API level:

| API Level | Blur | Quality | Default |
|---|---|---|---|
| 33+ (Android 13+) | ✅ | Optimal | Enabled |
| 31-32 (Android 12) | ✅ | Good | Enabled |
| ≤30 (Android 11-) | ⚠️ | Laggy (RenderScript) | Disabled (scrim fallback) |

- Android 12 blur was previously disabled but is now enabled by default since Haze 1.6.
- Android 11- requires explicit opt-in: `blurEffect { blurEnabled = true }`. Expect frame-behind updates.

### 4. Does Haze work on Desktop/iOS/Web?

Yes. Haze supports all Compose Multiplatform targets:

| Platform | Supported |
|---|---|
| Android | ✅ |
| Desktop (JVM) | ✅ |
| iOS | ✅ |
| Wasm | ✅ |
| JS/Canvas | ✅ |

Desktop, iOS, and Web all use Skia/Skiko-backed implementations (optimal environment).

### 5. Can I use pre-release versions of Compose Multiplatform with Haze?

Haze is built, tested, and released against the latest stable Compose Multiplatform. Pre-release CMP versions are occasionally verified but not guaranteed. Test thoroughly if using pre-release CMP.

### 6. Does Haze blur work on the Android Emulator?

The RenderScript path (API ≤30, Android 11-) does **not** render blur on the emulator. It works on physical devices only. `RenderEffect`-based paths (API 31+) work on emulator images that support GPU acceleration.
