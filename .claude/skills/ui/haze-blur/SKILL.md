---
name: haze-blur
description: Expert guidance on Haze blur effect configuration, styling, materials, progressive blur, masking, and input scale. Use when configuring blur radius, tint/color effects, noise, HazeMaterials/CupertinoMaterials/FluentMaterials, gradient blurs, or blur masking.
---

# Haze Blur — Configuration & Styling

## Instructions

All blur-specific properties go inside `blurEffect { }` within `Modifier.hazeEffect`.

### 1. Styling Resolution (Precedence)

1. Value set directly in `blurEffect { }` (highest)
2. Value set via `style` property in `blurEffect { }`
3. Value from `LocalHazeBlurStyle` composition local
4. Default value

### 2. Blur Radius

Controls blur strength. Default: `20.dp`.

```kotlin
blurEffect {
    blurRadius = 20.dp
}
```

Larger values may be needed to keep foreground text legible.

### 3. Tint / Color Effects

Applied to maintain contrast and legibility. Default: background color at 70% opacity. Multiple effects applied in sequence:

```kotlin
blurEffect {
    colorEffects = listOf(
        HazeColorEffect.tint(Color.Black.copy(alpha = 0.2f)),
        HazeColorEffect.tint(Color.Blue.copy(alpha = 0.1f))
    )
}
```

### 4. Noise

Visual grain texture. Default: `0.15f` (15% strength). Disable with `0f`:

```kotlin
blurEffect { noiseFactor = 0.15f }
blurEffect { noiseFactor = 0f }   // Disable
```

### 5. Alpha

Overall opacity of the blur effect:

```kotlin
blurEffect { alpha = 0.9f }
```

### 6. Enabling/Disabling Blur

```kotlin
blurEffect { blurEnabled = false }
```

On Android 11 and below, blur is disabled by default (falls back to scrim). Enable explicitly with `blurEnabled = true`.

### 7. Edge Treatment

```kotlin
blurEffect {
    blurredEdgeTreatment = BlurredEdgeTreatment.Unbounded
}
```

### 8. Pre-Built Materials

Requires `haze-materials` dependency.

**HazeMaterials** (Material Design inspired):

```kotlin
blurEffect {
    style = HazeMaterials.thin()
    style = HazeMaterials.regular()
    style = HazeMaterials.thick()
    style = HazeMaterials.ultraThin()
    style = HazeMaterials.ultraThick()
    style = HazeMaterials.chrome()
    style = HazeMaterials.bar()
    style = HazeMaterials.titlebar()
    style = HazeMaterials.menu()
    style = HazeMaterials.popover()
    style = HazeMaterials.sheet()
}
```

**CupertinoMaterials** (iOS 18 styles):

```kotlin
blurEffect {
    style = CupertinoMaterials.thin()
    style = CupertinoMaterials.regular()
    style = CupertinoMaterials.thick()
    // etc.
}
```

**FluentMaterials** (WinUI 3 styles):

```kotlin
blurEffect {
    style = FluentMaterials.thin()
    style = FluentMaterials.regular()
    style = FluentMaterials.thick()
    // etc.
}
```

### 9. Custom Styles

Use `HazeBlurStyle` data class or set properties directly:

```kotlin
val customStyle = HazeBlurStyle(
    blurRadius = 15.dp,
    colorEffects = listOf(HazeColorEffect.tint(Color.Black.copy(alpha = 0.2f))),
    noiseFactor = 0.1f
)

blurEffect { style = customStyle }
```

Or inline:

```kotlin
blurEffect {
    blurRadius = 15.dp
    colorEffects = listOf(HazeColorEffect.tint(Color.Black.copy(alpha = 0.2f)))
    noiseFactor = 0.1f
}
```

### 10. Progressive Blur (Gradient)

Vary blur radius across a dimension (iOS-style). Comes with performance cost.

```kotlin
blurEffect {
    // Vertical gradient (top-to-bottom fade)
    progressive = HazeProgressive.verticalGradient(
        startIntensity = 1f,
        endIntensity = 0f
    )

    // Horizontal gradient
    progressive = HazeProgressive.horizontalGradient(
        startIntensity = 1f,
        endIntensity = 0f
    )

    // Radial gradient
    progressive = HazeProgressive.RadialGradient()

    // Custom brush as alpha mask
    progressive = HazeProgressive.Brush(
        brush = Brush.verticalGradient(...)
    )
}
```

**Performance:** ~25% more cost on Android 13+, ~2x on Android 12. On Android 12/12L, only linear gradients are natively supported — others fall back to masks. Force mask fallback:

```kotlin
progressive = HazeProgressive.verticalGradient(
    startIntensity = 1f, endIntensity = 0f,
    preferPerformance = true
)
```

### 11. Masking

Apply any `Brush` as an opacity mask. Negligible performance cost, but less refined than progressive (fades opacity only, not blur radius).

```kotlin
blurEffect {
    mask = Brush.verticalGradient(
        colors = listOf(Color.Black, Color.Transparent)
    )
}
```

> Prefer masking over progressive for performance-sensitive UIs.

### 12. Dynamic Styling

Values in `blurEffect { }` can be read reactively:

```kotlin
TopAppBar(
    modifier = Modifier.hazeEffect(state = hazeState) {
        blurEffect {
            alpha = if (listState.firstVisibleItemIndex == 0) {
                listState.layoutInfo.visibleItemsInfo.first().let {
                    (it.offset / it.size.height.toFloat()).absoluteValue
                }
            } else { 1f }
        }
    }
)
```

### 13. Input Scale (HazeEffectScope)

Set on `HazeEffectScope`, NOT inside `blurEffect { }`:

```kotlin
Modifier.hazeEffect(state = hazeState) {
    inputScale = HazeInputScale.Auto
    blurEffect { /* ... */ }
}
```

| Scale | Pixel Reduction | Visual Impact |
|---|---|---|
| `None` (1.0) | 0% | Best quality |
| `Auto` | Platform default | Imperceptible |
| `Fixed(0.66)` | ~55% | Imperceptible |
| `Fixed(0.5)` | ~75% | Noticeable but acceptable |
| `Fixed(0.33)` | ~89% | Visibly different |

### 14. Checklist

- [ ] All blur props inside `blurEffect { }`; common props (`inputScale`, `drawContentBehind`, `canDrawArea`) outside.
- [ ] Use `HazeMaterials` for quick consistent styling.
- [ ] For dialogs, use translucent surface color instead of `colorEffects`.
- [ ] Prefer masking over progressive for performance.
- [ ] Enable blur explicitly on Android ≤11 if needed: `blurEnabled = true`.
