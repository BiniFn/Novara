# OCR Architecture Review

## Scope

This document describes the OCR pipeline that is currently implemented in Kototoro on this branch, why the real-world manga quality is still poor, and which design choices differ from a manga-oriented OCR stack such as `manga-translator-ui`.

It is a review of the implementation that exists today. The target replacement architecture is documented separately in [OCR Pipeline](./ocr-pipeline-v2.md).

## Executive Summary

The branch has completed a major upgrade cycle focused on manga OCR quality.

The current primary path is now:

```text
page image
  -> MLKit text detection (primary) / Paddle ONNX detection (alternative)
  -> text merge
  -> bubble grouping with YSG YOLO v11 OBB auxiliary detection
  -> Paddle ONNX region recognition (PP-OCRv5 Server)
  -> translation
  -> render
```

This pipeline is now substantially aligned with `manga-translator-ui`'s architecture, with the key improvement being that bubble detection (YOLO OBB) is used exclusively as a **post-OCR grouping assistant**, not as the OCR gate.

### Key Changes Since Last Review

1. **YSG YOLO v11 OBB** bubble detector integrated with proper angle→AABB conversion
2. **PP-OCRv5 Server** recognizer added (same weights as `manga-translator-ui`)
3. **Rendering area double-compression** bug fixed in `ReaderBubbleGroupingCoordinator`
4. **OBB-specific NMS/filtering** parameters tuned for manga (IoU≥0.85, max 64 boxes, min 12px side)
5. Legacy `ReaderTextHybridDetector` removed — native pipeline already implements the same logic

For manga OCR, the architecture now closely follows:

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

## 5. The active detector quality has improved significantly

With the integration of YSG YOLO v11 OBB as a bubble grouping assistant:

- MLKit now serves as the primary text detector with strong dialogue-region coverage
- YOLO OBB provides structural bubble geometry that groups MLKit fragments into logical units
- PP-OCRv5 Server recognizer provides much stronger Japanese/Chinese character recognition than the Mobile variant

Observed runtime metrics on device:

- MLKit detects 30+ text fragments per page
- YOLO OBB produces 40-60 bubble boxes after NMS
- Fragment coverage rate reaches 100% (all fragments matched to bubbles)
- 13/13 bubbles translated and rendered on test pages

The remaining quality issue is now primarily about **rendering area sizing** — ensuring the translated text fills the full bubble area rather than being clipped to a narrow strip around the original text fragments.

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

The refactoring has been substantially completed. The pipeline now follows:

```text
page image
  -> MLKit text detection (全図テキスト検出)
  -> text merge (フラグメント結合)
  -> YOLO OBB bubble grouping (気泡辅助分组)
  -> PP-OCRv5 Server recognition (可选升级)
  -> translation
  -> render
```

Remaining optimization areas:

- further tuning of `tightenDetectedBubbleRect` padding for edge cases
- evaluating PP-OCRv5 Server vs Mobile recognizer quality on diverse manga styles
- potential integration of dedicated manga OCR models (MangaOCR) as an alternative recognizer

The target design and migration plan are documented in [OCR Pipeline](./ocr-pipeline-v2.md).
