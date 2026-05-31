---
name: haze
description: Expert guidance on the Haze library for Compose Multiplatform visual effects (blur, glassmorphism). Use when asked about background blur, glass effects, haze setup, or any Haze-related question. This is the routing skill — load specific sub-skills for detailed guidance.
---

# Haze — Visual Effects for Compose Multiplatform

Haze is a Compose Multiplatform library providing visual effects such as blur (glassmorphism). Supports Android, Desktop (JVM), iOS, Wasm, JS/Canvas.

## Available Sub-Skills

| Sub-Skill | Use When |
|---|---|
| `haze-blur` | Configuring blur radius, tint/color effects, noise, materials (HazeMaterials/Cupertino/Fluent), progressive blur, masking, input scale |
| `haze-patterns` | Implementing common recipes: Scaffold blur, sticky headers, overlapping effects, dialog blur, deep hierarchies |
| `haze-performance` | Platform-specific backends (Android API levels, Skia/Skiko), benchmarks, optimization strategies, input scaling |
| `haze-custom-effects` | Building custom `VisualEffect` implementations, lifecycle methods, builder extensions, platform-specific rendering |
| `haze-faq` | Haze vs Modifier.blur, cross-platform consistency, Android version support, emulator limitations |
| `haze-migration` | Migrating from Haze v1.x to v2.0: dependency updates, import changes, API mapping, rename table |

## Architecture

Haze v2 uses a modular architecture:

```
haze (core: VisualEffect, HazeState, modifiers)
├── haze-blur (blur effect)
│   └── haze-materials (pre-built styles: Material, Cupertino, Fluent)
└── haze-utils (platform utilities)
```

Each effect lives in its own module. Future effects (e.g., `haze-liquidglass`) follow the same pattern. Effects register via builder extensions on `HazeEffectScope`:

```kotlin
// Effects are set via HazeEffectScope.visualEffect property
Modifier.hazeEffect(state) {
    blurEffect { style = HazeMaterials.thin() }     // sets visualEffect = BlurVisualEffect
    // liquidglassEffect { ... }                     // future: sets visualEffect = LiquidGlassEffect
}
```

## Quick Start

```kotlin
// Dependencies
implementation("dev.chrisbanes.haze:haze:<version>")
implementation("dev.chrisbanes.haze:haze-blur:<version>")
implementation("dev.chrisbanes.haze:haze-materials:<version>")
```

```kotlin
// Basic background blur
val hazeState = rememberHazeState()

Box {
    LazyColumn(modifier = Modifier.fillMaxSize().hazeSource(state = hazeState)) { /* content */ }
    TopAppBar(
        colors = TopAppBarDefaults.largeTopAppBarColors(Color.Transparent),
        modifier = Modifier.hazeEffect(state = hazeState) {
            blurEffect { style = HazeMaterials.thin() }
        }
    )
}
```

## Core Concepts Summary

| Concept | Description |
|---|---|
| `hazeSource` | Marks content to be blurred |
| `hazeEffect` | Applies visual effect to destination (omit `state` for foreground blur) |
| `HazeState` | Shared state connecting source and effect via `rememberHazeState()` |
| `zIndex` | Determines draw order for overlapping effects |
| `key` | Optional identifier for `canDrawArea` filtering |
| `positionStrategy` | Controls coordinate calculation (Auto/Local/Screen) |

For detailed guidance, load the appropriate sub-skill above.
