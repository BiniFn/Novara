---
name: haze-migration
description: Guide for migrating from Haze v1.x to v2.0. Use when upgrading Haze dependencies, fixing compilation errors after a version bump, or converting v1 API calls to v2 syntax.
---

# Haze — Migrating from v1.x to v2.0

## Instructions

Haze 2.0 introduces a pluggable visual effects system. Key changes: blur moves to `haze-blur` module, all blur props require `blurEffect {}` wrapper, and many class renames.

### 1. Dependency Changes

```kotlin
// v1
implementation("dev.chrisbanes.haze:haze:1.x.x")

// v2 (both required)
implementation("dev.chrisbanes.haze:haze:2.0.0-alpha01")
implementation("dev.chrisbanes.haze:haze-blur:2.0.0-alpha01")
```

### 2. Import Changes

| v1 | v2 |
|---|---|
| `dev.chrisbanes.haze.HazeStyle` | `dev.chrisbanes.haze.blur.HazeBlurStyle` |
| `dev.chrisbanes.haze.HazeTint` | `dev.chrisbanes.haze.blur.HazeColorEffect` |
| `dev.chrisbanes.haze.HazeProgressive` | `dev.chrisbanes.haze.blur.HazeProgressive` |
| `dev.chrisbanes.haze.LocalHazeStyle` | `dev.chrisbanes.haze.blur.LocalHazeBlurStyle` |
| *(none)* | `dev.chrisbanes.haze.blur.blurEffect` (new) |

### 3. API Mapping — Properties

All blur-related properties move from `HazeEffectScope` to inside `blurEffect {}`:

| v1 Location | v2 Location |
|---|---|
| `HazeEffectScope.blurRadius` | `BlurVisualEffect.blurRadius` (inside `blurEffect {}`) |
| `HazeEffectScope.tints` | `BlurVisualEffect.colorEffects` |
| `HazeEffectScope.noiseFactor` | `BlurVisualEffect.noiseFactor` |
| `HazeEffectScope.progressive` | `BlurVisualEffect.progressive` |
| `HazeEffectScope.mask` | `BlurVisualEffect.mask` |
| `HazeEffectScope.style` | `BlurVisualEffect.style` |
| `HazeEffectScope.backgroundColor` | `BlurVisualEffect.backgroundColor` |
| `HazeEffectScope.blurredEdgeTreatment` | `BlurVisualEffect.blurredEdgeTreatment` |
| `HazeEffectScope.fallbackTint` | `BlurVisualEffect.fallbackTint` |
| `HazeEffectScope.alpha` | `BlurVisualEffect.alpha` |
| `HazeEffectScope.blurEnabled` | `BlurVisualEffect.blurEnabled` |

**Unchanged** — still on `HazeEffectScope`:
- `inputScale`
- `drawContentBehind`
- `clipToAreasBounds`
- `canDrawArea`

### 4. API Mapping — Renames

| v1 | v2 |
|---|---|
| `HazeArea.positionOnScreen` | `HazeArea.position` |
| `VisualEffectContext.positionOnScreen` | `VisualEffectContext.position` |
| `VisualEffectContext.rootBoundsOnScreen` | `VisualEffectContext.rootBounds` |
| `rememberHazeState(blurEnabled)` | **Removed** — use `blurEffect { blurEnabled = ... }` |

### 5. Migration Examples

**Basic blur:**
```kotlin
// v1
Modifier.hazeEffect(state = hazeState) {
    blurRadius = 20.dp
    colorEffects = listOf(HazeColorEffect.tint(Color.Black.copy(alpha = 0.7f)))
    noiseFactor = 0.15f
}

// v2
Modifier.hazeEffect(state = hazeState) {
    blurEffect {
        blurRadius = 20.dp
        colorEffects = listOf(HazeColorEffect.tint(Color.Black.copy(alpha = 0.7f)))
        noiseFactor = 0.15f
    }
}
```

**Material styles:**
```kotlin
// v1
Modifier.hazeEffect(state = hazeState, style = HazeMaterials.ultraThin())

// v2
Modifier.hazeEffect(state = hazeState) {
    blurEffect { style = HazeMaterials.ultraThin() }
}
```

**Progressive blur:**
```kotlin
// v1
Modifier.hazeEffect(state = hazeState) {
    progressive = HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f)
}

// v2
Modifier.hazeEffect(state = hazeState) {
    blurEffect {
        progressive = HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f)
    }
}
```

**Masking:**
```kotlin
// v1
Modifier.hazeEffect(state = hazeState) {
    mask = Brush.verticalGradient(...)
}

// v2
Modifier.hazeEffect(state = hazeState) {
    blurEffect { mask = Brush.verticalGradient(...) }
}
```

**Enabling/disabling blur:**
```kotlin
// v1
val hazeState = rememberHazeState(blurEnabled = true)
// or
Modifier.hazeEffect(state = hazeState) { blurEnabled = true }

// v2
val hazeState = rememberHazeState()
Modifier.hazeEffect(state = hazeState) {
    blurEffect { blurEnabled = true }
}
```

**Foreground blur:**
```kotlin
// v1
Modifier.hazeEffect {
    colorEffects = listOf(HazeColorEffect.tint(...))
    progressive = HazeProgressive.verticalGradient(...)
}

// v2
Modifier.hazeEffect {
    blurEffect {
        colorEffects = listOf(HazeColorEffect.tint(...))
        progressive = HazeProgressive.verticalGradient(...)
    }
}
```

### 6. Step-by-Step Migration Checklist

1. Add `haze-blur` dependency
2. Update imports: `HazeStyle` → `HazeBlurStyle`, `HazeTint` → `HazeColorEffect`, `HazeProgressive` stays (package change), `LocalHazeStyle` → `LocalHazeBlurStyle`, add `blurEffect`
3. Find all `Modifier.hazeEffect { ... }` blocks
4. Wrap blur properties in `blurEffect { ... }` (leave `inputScale`/`drawContentBehind`/`clipToAreasBounds`/`canDrawArea` outside)
5. Remove `blurEnabled` from `rememberHazeState(...)` calls
6. Change `hazeEffect(state, style = ...)` to `hazeEffect(state) { blurEffect { style = ... } }`
7. If using v2.0.0-alpha02+:
   - `VisualEffect.detach()` now receives context: `detach(context: VisualEffectContext)`
   - `VisualEffectContext.visualEffect` property removed — access your own state directly
   - `DrawScope.shouldDrawContentBehind(context)` renamed to `shouldDrawContentBehind(context)`

### 7. Position Strategy (New in v2)

```kotlin
// Default — handles same-window + cross-window automatically
val hazeState = rememberHazeState()
val hazeState = rememberHazeState(positionStrategy = HazePositionStrategy.Auto)

// Force same-window (immune to split-window offsets)
val hazeState = rememberHazeState(positionStrategy = HazePositionStrategy.Local)

// Force screen-level (cross-window)
val hazeState = rememberHazeState(positionStrategy = HazePositionStrategy.Screen)
```

### 8. What Has NOT Changed

- `hazeSource` and `hazeEffect` modifier signatures
- Platform support (all Compose Multiplatform targets)
- Performance characteristics
- `HazeEffectScope` properties: `inputScale`, `drawContentBehind`, `canDrawArea`, `clipToAreasBounds`
