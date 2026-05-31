---
name: kototoro-haze-sheet-artifacts
description: Diagnose and fix Kototoro-specific Compose Material3 Sheet/Dialog/Popup plus Haze/GlassSurface visual artifacts, especially extra light/gray rectangles, white haze plates, rectangular bounds around rounded glass panels, or backgrounds that appear to grow while dragging display options and other glass sheets.
---

# Kototoro Haze Sheet Artifacts

Use this with the generic `haze` and `compose-ui` skills when a Kototoro glass panel shows a pale rectangle, double background, washed-out sheet, or rectangular edge outside rounded corners.

## Core Lesson

Do not assume Haze is broken. In Kototoro, these artifacts usually come from stacked drawing layers:

- Material3 `ModalBottomSheet`, `Dialog`, `DropdownMenu`, `Surface`, or `Card` default container/elevation.
- `GlassSurface` Haze tint/background plus `Surface` color both tinting the same area.
- Shape mismatch: rectangular Haze/elevation layer behind rounded content.
- `Surface` shadow/elevation in dialog-like panels. In the display options issue, the final remaining rectangle was caused by `GlassSurface(dialogSurface = true)` still using `prominentStyle()` shadow elevation; setting dialog surface elevation to zero fixed it.

## First Checks

1. Locate the exact composable, usually `DisplayOptionsSheet`, top bar menu, search sheet, details popup, or another `GlassSurface` caller.
2. Inspect all enclosing Material containers. For glass panels, explicitly set:

```kotlin
ModalBottomSheet(
    containerColor = Color.Transparent,
    tonalElevation = 0.dp,
    shape = RoundedCornerShape(0.dp),
    dragHandle = null,
)
```

3. Keep the visual rounded panel in one place, normally the inner `GlassSurface`, not both the Material sheet and the glass content.
4. For dialog/sheet glass surfaces, pass a dialog-specific flag instead of disabling Haze entirely:

```kotlin
GlassSurface(
    shape = RoundedCornerShape(28.dp),
    style = GlassDefaults.prominentStyle(),
    dialogSurface = true,
)
```

## GlassSurface Rules

For `dialogSurface = true`, ensure `GlassSurface` behaves differently from inline cards/bars:

- Keep `clip(shape)` before the Haze effect.
- Use `BlurredEdgeTreatment(shape)`.
- Set `expandLayerBounds = false` for dialog/sheet panels.
- Make Haze tint/background transparent for the dialog path when a same-shape `Surface` supplies the translucent container color.
- Force `tonalElevation = 0.dp` and `shadowElevation = 0.dp` for dialog surfaces. This prevents a rectangular Compose shadow/layer from appearing behind rounded glass.
- Do not use `prominentStyle()` shadow elevation directly in a sheet/dialog without the dialog override.

Canonical pattern:

```kotlin
val effectiveStyle = if (dialogSurface) {
    style.copy(tonalElevation = 0.dp, shadowElevation = 0.dp)
} else {
    style
}

Surface(
    modifier = modifier
        .clip(shape)
        .hazeChild(hazeState, hazeStyle) {
            backgroundColor = if (dialogSurface) Color.Transparent else glassColors.containerColor
            blurredEdgeTreatment = BlurredEdgeTreatment(shape)
            clipToAreasBounds = true
            expandLayerBounds = !dialogSurface
        },
    shape = shape,
    color = if (useRuntimeHaze && !dialogSurface) Color.Transparent else glassColors.containerColor,
    tonalElevation = effectiveStyle.tonalElevation,
    shadowElevation = effectiveStyle.shadowElevation,
)
```

Adapt to the current Haze API if the project has migrated from `hazeChild` to `hazeEffect`.

## Diagnostic Logging

When visual inspection is ambiguous, add temporary debug-only bounds logs to both the Material content slot and the inner glass surface.

Use `onGloballyPositioned`, `boundsInWindow()`, and `BuildConfig.DEBUG`. Log:

- content slot size and window bounds
- glass surface size and window bounds
- `useRuntimeHaze`
- `dialogSurface`
- material preset
- opacity/blur/noise preferences
- surface alpha
- Haze background alpha
- Haze style background alpha
- tonal and shadow elevation

Interpretation:

- If outer slot grows but inner glass does not, suspect Material sheet/dialog container or window background.
- If inner glass bounds match the artifact, suspect `GlassSurface` color, Haze tint/background, shape clipping, or `Surface` elevation.
- If only coordinates change while sizes remain fixed, drag motion is not the source; the artifact is a fixed layer being moved.
- If the artifact gets larger while dragging and logs show inner glass height is already large, check whether the panel content itself is taller than expected before blaming Haze.

Example from the fixed display options issue:

```text
modal_content_slot size=1272x1370
display_options_glass surface size=1188x1314
```

Both sizes were stable while dragging, so the remaining rectangle belonged to the inner `GlassSurface`; zeroing dialog `Surface` elevation fixed it.

## Fix Order

Apply fixes in this order to avoid masking the real cause:

1. Make outer Material containers transparent and zero elevation.
2. Ensure only one layer owns the rounded glass shape.
3. Align Haze clipping and edge treatment with the same shape.
4. Remove Haze tint/background for dialog surfaces if a same-shape `Surface` color is used.
5. Zero `Surface` elevation for dialog surfaces.
6. Only then consider disabling runtime Haze as a diagnostic, not as the preferred final fix.

## Cleanup

After the fix is confirmed:

- Remove temporary verbose bounds logs unless the user explicitly wants them retained.
- Keep reusable API improvements such as `dialogSurface` if they prevent recurrence.
- Run at least `./gradlew ":app:compileDebugKotlin" --no-daemon`.
