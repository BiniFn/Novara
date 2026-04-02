# OCR Architecture Review

## Scope

This document describes the OCR pipeline that is currently implemented in Kototoro on this branch, why the real-world manga quality is still poor, and which design choices differ from a manga-oriented OCR stack such as `manga-translator-ui`.

It is a review of the implementation that exists today. The target replacement architecture is documented separately in [OCR Pipeline](./ocr-pipeline-v2.md).

## Executive Summary

The branch no longer runs the original bubble-first main path.

The current primary path is now:

```text
page image
  -> Paddle ONNX text detection
  -> Paddle ONNX region recognition
  -> text merge
  -> bubble grouping / optional bubble hints
  -> translation
  -> render
```

This is substantially closer to a manga-oriented OCR stack, but it is still not fully aligned yet.

The most important practical update is that the branch is no longer failing because of the old bubble-first structure. The current visible failure mode is different:

- `PADDLE` now runs as the active local manga OCR path
- but `PADDLE` page detection currently misses many dialogue regions
- and the regions it does detect are often too tight, which shortens or corrupts recognition results
- `MLKIT` currently produces much better dialogue-region coverage on the same pages

For manga OCR, the more reliable architecture is usually:

```text
page image
  -> text detection
  -> text-region recognition
  -> line / block merge
  -> optional bubble assignment
  -> translation
  -> render
```

The old Kototoro pipeline optimized for bubble rendering too early and asked the bubble detector to decide the OCR search space. That was a weak assumption for manga pages containing:

- vertical Japanese text
- long narrow bubbles
- narration boxes
- SFX
- dark bubbles / colored bubbles
- broken or overlapping bubble boundaries

## Current Runtime Behavior

## OCR engine selection is now truthful

The current runtime only exposes real OCR engine choices:

- `MLKIT`
- `PADDLE`

Legacy `TFLITE`, `HYBRID`, and `NCNN` values have been removed from the active OCR runtime. Existing saved values are migrated to `PADDLE` where needed.

## The current Paddle engine is a real ONNX implementation

[PaddleReaderOcrEngine.kt](../../app/src/main/kotlin/org/skepsun/kototoro/reader/translate/domain/PaddleReaderOcrEngine.kt) now runs local `PP-OCRv5` ONNX detection and recognition directly. It no longer delegates to NCNN.

The engine now also exposes formal text-pipeline contracts:

- `ReaderTextDetector`
- `ReaderTextRecognizer`

Those contracts now drive the page-level `PADDLE` path in the reader pipeline, but they are not yet the only OCR orchestration boundary in the codebase.

## The reader primary path is page-text-first

[ReaderPageTranslationProcessor.kt](../../app/src/main/kotlin/org/skepsun/kototoro/reader/translate/domain/ReaderPageTranslationProcessor.kt) now uses `PAGE_FIRST` as the default OCR strategy.

`ReaderOcrPipelineCoordinator.kt` now coordinates only the page-text-first path. The older ROI-first logic is no longer part of the active pipeline coordinator.

## The current coordination flow

The main path coordinated by [ReaderOcrPipelineCoordinator.kt](../../app/src/main/kotlin/org/skepsun/kototoro/reader/translate/domain/ReaderOcrPipelineCoordinator.kt) is now:

1. Run page OCR.
2. Merge OCR fragments through `ReaderTextMergeCoordinator`.
3. Group merged fragments for translation.
4. Use bubble-related logic only as downstream grouping and rendering assistance.

This is a major improvement over the previous bubble-gated path.

## Why quality can still be poor

## 1. The detector / recognizer split is not yet the primary pipeline boundary

The codebase now has `ReaderTextDetector` and `ReaderTextRecognizer`, and the `PADDLE` page path in [ReaderPageTranslationProcessor.kt](../../app/src/main/kotlin/org/skepsun/kototoro/reader/translate/domain/ReaderPageTranslationProcessor.kt) already uses them directly.

That means the architecture is improved, but the reader pipeline still does not express:

```text
detect -> recognize -> merge
```

as the only orchestration contract across all OCR entry paths.

In practice, this now matters less than detector quality. The active branch already follows the intended order closely enough that the current on-device bottleneck is the detector output itself.

## 2. Legacy ROI-first code is no longer on the active OCR path

[ReaderBubbleRoiOcrCoordinator.kt](../../app/src/main/kotlin/org/skepsun/kototoro/reader/translate/domain/ReaderBubbleRoiOcrCoordinator.kt) still exists in the codebase, and it preserves old assumptions such as:

- width or height is below `24dp`
- ROI area is larger than `22%` of the page
- `isLikelySpeechBubbleRegion(bitmap, rect)` returns `false`

These gates are too aggressive for manga:

- small bubbles are common
- tall narrow vertical dialogue is common
- large dialogue bubbles are common
- narration boxes can be large

That is now a cleanup issue rather than an active architecture problem, because the main OCR coordinator no longer routes through ROI-first logic.

## 3. Bubble-region validation is still based on brightness heuristics

[ReaderPageTranslationProcessor.kt](../../app/src/main/kotlin/org/skepsun/kototoro/reader/translate/domain/ReaderPageTranslationProcessor.kt) implements `isLikelySpeechBubbleRegion(...)` by sampling luminance and measuring bright-pixel ratio.

This works only for a subset of pages that look like:

- white bubble
- black text
- relatively clean background

It fails or becomes unstable on:

- dark bubbles
- colored bubbles
- grayscale gradients
- transparent or weak bubble outlines
- dense screentones
- text outside bubbles

This is no longer the dominant OCR failure for the active path, because OCR no longer enters through bubble gating. It remains a downstream rendering / grouping weakness.

## 4. Bubble grouping is still too close to OCR structure rebuilding

`ReaderTextMergeCoordinator` now exists, which is the correct direction. `ReaderBubbleGroupingCoordinator` has already been reduced so unmatched merged fragments remain independent translation units by default, and detector-side subdivision has been removed.

For closer alignment with `manga-translator-ui`, text structure rebuilding should continue to be owned primarily by merge, with bubble-related grouping reduced further into optional bubble assignment and render hints. The branch has already moved merge toward explicit reading-order composition, lightweight direction hints, lightweight angle / axis-aligned hints, optional quad-point carriage, quad-aware edge-distance pruning, region-geometry-based cropping, lightweight four-point warp support, and away from blind newline concatenation.

## 5. The active Paddle detector is currently the main quality bottleneck

The current `PaddleReaderOcrEngine.kt` detector path is functionally correct, but the present lightweight postprocess is still too weak for manga pages.

Observed behavior on device:

- many dialogue areas are missed entirely
- detected text boxes are often too small
- tight boxes lead to short, partial, or incorrect recognition results

This is consistent with the current implementation: the detector path still uses a simplified probability-map connected-component decode rather than a stronger manga-oriented text-box decode.

So the current issue is no longer "the architecture is bubble-first". The current issue is "the active detector geometry is weaker than the recognizer and weaker than ML Kit's page text blocks".

## 6. Remaining gap versus manga-translator-ui

The remaining architectural gap is now much narrower:

- Kototoro already has text-first page OCR
- Kototoro already has post-recognition merge
- Kototoro has removed NCNN and TFLite OCR baggage

What still remains is to make the runtime orchestration itself explicitly follow:

- text detection as a first-class stage
- region recognition as a first-class stage
- merge as the main text-structure reconstruction stage
- bubble logic as non-authoritative post-OCR assistance

## Comparison With manga-translator-ui

The most important difference is architectural.

`manga-translator-ui` uses a modular manga OCR stack:

```text
detector -> recognizer -> merge -> translation -> render
```

Characteristics:

- text detection is independent from recognition
- recognition is applied on text-oriented crops
- merge happens after recognition
- bubble-like structure is optional downstream information, not the OCR gate

The old Kototoro architecture used to do the opposite:

```text
bubble detector -> ROI OCR -> fallback page OCR
```

This makes rendering easier, but OCR weaker.

## What Should Be Preserved

Not everything in the current implementation is wrong. The following parts are still useful:

- `OcrRequest` as a unified OCR request model
- per-page debug metrics and logs
- cached OCR and render outputs
- a dedicated bubble grouping / rendering coordinator
- model management for ONNX assets

These are good building blocks. The problem is the order of operations and which module is allowed to define OCR search space.

## Main Refactor Direction

Kototoro should move toward a text-first manga OCR pipeline:

```text
page image
  -> text detector
  -> text-region OCR
  -> merge text fragments into logical bubbles / blocks
  -> optional bubble detector for render refinement only
  -> translation
  -> render
```

This means:

- bubble detection stops being the OCR gate
- ROI stops meaning "bubble crop"
- page-level detection becomes the authoritative geometry source
- recognizers become pluggable recognition backends
- merge becomes the stage that rebuilds speech blocks

The target design and migration plan are documented in [OCR Pipeline](./ocr-pipeline-v2.md).
