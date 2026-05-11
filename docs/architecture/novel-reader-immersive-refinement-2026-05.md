# Novel Reader Immersive Refinement (2026-05)

## Context

The novel reader had reached functional parity quickly, but its interaction tone still felt too much like a generic Android control surface instead of a reading space. This round focused on reducing control noise, improving default typography, and tightening the fullscreen behavior so the reader better matches a long-form reading workflow.

The target is not "more features on screen". The target is a calmer default reading state and a cleaner separation between:

- reading mode
- navigation mode

This direction is closer to products like WeRead and Readest, where navigation is available on demand but does not dominate the default reading surface.

## Implemented in This Round

### 1. Typography and spacing defaults

- Increased default horizontal and vertical margins to `36dp`
- Expanded margin adjustment range to `12dp..72dp` with `4dp` steps
- Changed default paragraph spacing to `0`, meaning "follow line spacing"
- Added paragraph spacing copy to make that behavior explicit in settings

These changes make the default page width more conservative and reduce the need for immediate manual tuning.

### 2. Blank-line cleanup

- Collapsed redundant blank lines during paragraph splitting
- Normalized whitespace-only paragraphs in typography preprocessing
- Skipped truly empty translated paragraphs when rebuilding chapter content

This keeps imported or source-side noisy content from producing artificial vertical gaps.

### 3. Fullscreen overlay behavior

- Reader content no longer shifts vertically when top/bottom chrome appears
- Status bar and toolbars now overlay the reading surface instead of reflowing it
- `infoBar` no longer reserves content height in the text area

The reading surface stays spatially stable, which is critical for immersion.

### 4. Navigation slider crash fix

- Normalized persisted line-height and spacing values to valid slider steps
- Normalized bottom-sheet slider initialization to the same step/range rules

This fixes the `BaseSlider.validateValues()` crash caused by legacy floating-point values that do not align with Material step sizes.

### 5. Page drag animation for paged mode

- Added direct-manipulation horizontal dragging in paged mode
- Current page and adjacent page move together during drag
- Release completes with a short settle animation

This is intentionally simple and tactile. The goal is a controllable page transition, not a decorative animation.

### 6. Secondary navigation state in the bottom toolbar

- Novel reader now uses a compact bottom toolbar by default
- The first toolbar reveal stays in a lighter "reading support" state
- Tapping the chapter/progress entry expands a secondary navigation state
- The secondary state reveals `prev / slider / next`
- Opening chapters or settings collapses the secondary state again

This is the first structural step toward separating reading and navigation instead of showing a full control bar immediately.

### 7. Default startup behavior

- When fullscreen reading is enabled, the novel reader now starts in the pure reading state instead of showing toolbars first

This aligns the actual startup behavior with the intended immersive design.

## Technical Notes

### Shared component containment

`ReaderActionsView` is shared by manga, video, and novel readers. To avoid regressing other readers, the compact secondary-navigation behavior is enabled only by the novel reader.

This keeps the current change narrow:

- shared component gains the capability
- novel reader opts into it explicitly
- other readers retain their existing behavior

### Compose migration assessment

A full Compose rewrite of the novel reader is still not the right next step.

Recommended path:

- keep `NovelReaderView` for pagination, drag turning, inline images, and TTS highlighting
- continue improving the shell layer first: toolbars, sheets, theme transitions, state overlays
- migrate those outer layers incrementally when the interaction model is stable

This captures most UI/animation upside without immediately paying the cost of rewriting the pagination engine.

## Next Iteration

The next high-value step is not "more controls". It is reducing the remaining system-panel feel when UI is shown:

- make top/bottom chrome thinner and lighter
- reduce Material/elevation presence further
- move more progress-related affordances behind the secondary navigation state
- audit toolbar animation timing and easing for a softer entrance

## Verification

Primary validation command used during this round:

```bash
JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64" ./gradlew :app:compileDebugKotlin --no-daemon -Pksp.incremental=false
```
