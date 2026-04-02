# OCR Pipeline

## Goal

This document defines the OCR pipeline Kototoro should move toward.

The target is to align with the manga-oriented design used by projects such as `manga-translator-ui`:

```text
page image
  -> text detection
  -> text-region recognition
  -> line / block merge
  -> optional bubble assignment
  -> translation
  -> render
```

The current implementation review is documented in [OCR Architecture Review](./ocr-architecture-review.md).

## Why This Architecture

For manga OCR, text geometry is more reliable than bubble geometry.

Text-first OCR has several advantages:

- it detects actual text regions instead of guessed bubble regions
- it supports narration boxes and SFX without special routing
- it separates detection quality from recognition quality
- it allows recognizers to specialize in recognition instead of page structure
- it makes merge a text-structure problem instead of a bubble-guessing problem

This is the main architectural change Kototoro needs.

## Alignment With manga-translator-ui

This plan is aligned with the **core OCR architecture** of `manga-translator-ui`, but not yet identical in every detail.

The local implementation of `manga-translator-ui` on this machine confirms the following main order:

```text
dispatch_detection(...)
  -> dispatch_ocr(...)
  -> dispatch_textline_merge(...)
```

Relevant local sources:

- [manga_translator.py](/Users/sunchuxiong/kotatsu_demo/manga-translator-ui/manga_translator/manga_translator.py)
- [detection/common.py](/Users/sunchuxiong/kotatsu_demo/manga-translator-ui/manga_translator/detection/common.py)
- [ocr/common.py](/Users/sunchuxiong/kotatsu_demo/manga-translator-ui/manga_translator/ocr/common.py)

That means the strongest architectural invariants are:

- detection is text-first, not bubble-first
- OCR consumes detector regions
- merge happens after recognition
- bubble-related logic is auxiliary, not the OCR gate

So the strict alignment target for Kototoro should be interpreted as:

```text
page image
  -> text detection
  -> text-region recognition
  -> textline / block merge
  -> translation
  -> render
```

If a bubble detector remains in Kototoro, it must be demoted to one of these roles only:

- render anchor refinement
- optional grouping hint
- optional filtering hint

It must not define the OCR search space.

## What Is Still Not Fully Aligned Yet

Compared with `manga-translator-ui`, the current target design in Kototoro still preserves one extra concept:

- `optional bubble assignment`

That is acceptable only as a downstream rendering helper.

If the goal is to align **as closely as possible** with `manga-translator-ui`, then Kototoro should adopt the stricter rule:

```text
OCR core path = detection -> recognition -> merge
bubble logic = non-authoritative post-OCR helper
```

This document follows that stricter interpretation from this point onward.

## Current Branch Status

The branch has completed these architectural moves:

- the active OCR coordinator is page-text-first only
- `ROI_FIRST_FALLBACK` is no longer part of the active OCR coordination path
- local OCR runtime has been reduced to `MLKIT` and `PADDLE`
- `PADDLE` is now a real ONNX `det + rec` implementation
- merge now sits between OCR and bubble-related downstream logic
- bubble logic no longer defines the OCR search space
- **YSG YOLO v11 OBB** integrated as post-OCR bubble grouping assistant
- **PP-OCRv5 Server** recognizer available as higher-quality alternative to Mobile variant
- OBB angle→AABB conversion handles rotated manga text correctly
- OBB-specific NMS (≥0.85), max boxes (64), and min side (12px) tuned for manga
- rendering area double-compression bug fixed in `tightenDetectedBubbleRect`

The current quality profile on device:

- MLKit detects 30+ text fragments per page
- YOLO OBB successfully groups fragments into 13+ logical bubbles with 100% coverage
- All detected bubbles are translated and rendered
- Remaining issue: rendering area padding still needs tuning for edge cases

## Target Pipeline

## Stage 1. Page-level text detection

Input:

- full page bitmap

Output:

- `List<TextRegion>`

Minimum region fields:

- bounding box or quadrilateral
- confidence
- optional orientation hint
- optional detector class

Notes:

- this stage is authoritative for OCR geometry
- detector output must use page coordinates
- detector should not depend on bubble brightness heuristics

## Stage 2. Region recognition

Input:

- page bitmap
- detector regions

Output:

- `List<OcrTextBlock>`

Rules:

- each detector region is cropped and normalized
- recognition backends only recognize text for that region
- recognition does not decide the page search space

Recognition engines may differ, but they must all consume the same region list.

## Stage 3. Text merge

Input:

- recognized text blocks

Output:

- logical text groups for translation

Responsibilities:

- merge fragments that belong to the same speech unit
- preserve vertical Japanese ordering when needed
- keep narration and non-bubble text representable
- deduplicate overlapping or repeated fragments

This is where manga structure should be rebuilt.

## Stage 4. Optional post-OCR bubble hinting

Input:

- merged text groups
- optional bubble detector output

Output:

- text groups with optional improved render anchors

Important:

- bubble hinting is not OCR gating
- bubble detection is only used to improve render placement and, when safe, merge confidence
- if bubble detection fails, OCR should still succeed

## Stage 5. Translation

Input:

- merged source text groups

Output:

- translated groups

The translation stage should operate on stable merged text units produced by the page-text-first OCR path.

## Stage 6. Render

Input:

- translated groups
- target render rects

Output:

- translated page overlay or translated page bitmap

Render should consume the final merged and translated groups. It should not decide OCR behavior.

## Processes To Remove Or Demote

The following behaviors should not remain in the OCR critical path.

## 1. Bubble-first OCR

Current shape:

```text
bubble detector -> bubble ROI OCR
```

Target:

```text
text detector -> region OCR
```

Bubble detector becomes optional downstream metadata, not the OCR entrance.

## 2. Brightness-based speech-bubble gating

The current `isLikelySpeechBubbleRegion(...)` style luminance gate is too brittle for manga and comics.

Target:

- do not block OCR because a region is not bright enough
- if heuristics remain, keep them as ranking hints only

## 3. ROI re-running full OCR

Current ROI behavior in NCNN re-runs detect + rec on each cropped image.

Target:

- page detector runs once
- ROI recognition only performs recognition and crop normalization
- no temporary PNG files in the critical path

## 4. Hidden engine aliasing

The runtime must stop silently mapping multiple OCR engine choices back to `HYBRID`.

Target:

- UI engine selection must match actual runtime behavior
- `PADDLE`, `NCNN`, `TFLITE`, `HYBRID`, and future engines must be explicit

## 5. Destructive early text rewriting

Whitespace stripping, forced full-width conversion, and punctuation rewriting should not run as mandatory OCR output normalization.

Target:

- keep OCR output close to raw recognition
- move heavy normalization into optional language-specific postprocessing

## Minimal Target Interfaces

## Text detector

```kotlin
data class TextRegion(
    val rect: Rect,
    val confidence: Float,
    val angle: Float? = null,
    val classId: Int? = null,
)

interface ReaderTextDetector {
    suspend fun detect(sourceUri: Uri, pageId: Long? = null): List<TextRegion>
}
```

## OCR recognizer

```kotlin
data class OcrRequest(
    val sourceUri: Uri,
    val sourceLang: String,
    val roi: Rect? = null,
    val pageId: Long? = null,
    val requestType: OcrRequestType = OcrRequestType.PAGE,
    val debugTag: String? = null,
)

interface ReaderOcrService {
    suspend fun recognize(request: OcrRequest): List<OcrTextBlock>
}
```

The recognizer remains region-aware, but the region list must come from the detector stage rather than from bubble detection.

## Bubble hint provider

```kotlin
interface ReaderBubbleHintProvider {
    suspend fun provideHints(
        bitmap: Bitmap,
        groups: List<GroupedBubbleSource>,
    ): List<GroupedBubbleSource>
}
```

This stage is optional and non-authoritative.

## Migration Plan

## Phase 1. Stop making bubble detection authoritative

Changes:

- keep current detector and recognizers
- introduce a dedicated text-detector abstraction
- route page OCR through page-level text detection first
- keep bubble detector only for optional post-OCR hinting and render assist

Expected result:

- immediate reduction in ROI-related missed text
- cleaner debug metrics

## Phase 1 Implementation Checklist

The first implementation phase should be intentionally narrow and should not attempt a full rewrite.

### 1. Make runtime engine selection honest

Files:

- [AppSettings.kt](../../app/src/main/kotlin/org/skepsun/kototoro/core/prefs/AppSettings.kt)
- [TranslationSettingsFragment.kt](../../app/src/main/kotlin/org/skepsun/kototoro/settings/TranslationSettingsFragment.kt)

Changes:

- stop mapping `PADDLE`, `TFLITE`, and `NCNN` to `HYBRID`
- show the actual active engine in settings and debug metrics
- if an engine is not implemented, hide it or mark it unavailable instead of silently aliasing it

Settings design rules:

- OCR engine options must describe the real runtime path, not an internal codename
- model-selection preferences must only appear for engines that actually use them
- bubble-detector preferences must be placed under post-OCR grouping / render assistance, not under OCR entrance semantics
- summary text must explain that the primary OCR path is page-first
- ROI catch-all experiments should not remain in runtime settings once page-first is the only supported primary path

### 2. Remove `ROI_FIRST_FALLBACK` as the default OCR strategy

Files:

- [ReaderPageTranslationProcessor.kt](../../app/src/main/kotlin/org/skepsun/kototoro/reader/translate/domain/ReaderPageTranslationProcessor.kt)
- [ReaderOcrPipelineCoordinator.kt](../../app/src/main/kotlin/org/skepsun/kototoro/reader/translate/domain/ReaderOcrPipelineCoordinator.kt)

Changes:

- stop hardcoding `ROI_FIRST_FALLBACK` in the main page-processing path
- introduce a text-first default path:
  - page OCR or page text detection first
  - merge second
  - optional bubble hints later
Current incremental status:

- `PAGE_FIRST` is already the default OCR strategy
- ROI catch-all has been removed from runtime settings and coordinator wiring
- `ReaderOcrPipelineCoordinator` now exposes only the page-text-first execution path
- any remaining ROI-first code is now inactive legacy code rather than a live coordinator branch
- the practical quality bottleneck has shifted to `PADDLE` detector quality rather than pipeline routing

### 3. Introduce a dedicated text-detector abstraction

Files:

- new file under `reader/translate/domain`, for example `ReaderTextDetector.kt`
- [PaddleReaderOcrEngine.kt](../../app/src/main/kotlin/org/skepsun/kototoro/reader/translate/domain/PaddleReaderOcrEngine.kt)
- [ReaderPageTranslationProcessor.kt](../../app/src/main/kotlin/org/skepsun/kototoro/reader/translate/domain/ReaderPageTranslationProcessor.kt)

Changes:

- add `ReaderTextDetector.detect(...)`
- let Paddle ONNX expose page-level text boxes through that abstraction
- make page-level detector output the authoritative OCR region list

Current incremental status:

- the old NCNN/TFLite branches have been removed
- Paddle ONNX is now the only model-backed local OCR path
- page OCR already runs in a text-first path and no longer depends on bubble ROI admission
- `ReaderTextDetector` and `ReaderTextRecognizer` now exist as formal pipeline contracts
- `ReaderPageTranslationProcessor` now orchestrates the `PADDLE` page path through `ReaderTextDetector -> ReaderTextRecognizer`
- the next remaining step is to extend that first-class orchestration boundary so it becomes the OCR-core default shape rather than a `PADDLE`-specific branch
- current on-device validation shows that the `PADDLE` detector itself is still underperforming compared with `MLKIT` region geometry

### 4. Split Paddle page detection from region recognition

Files:

- [PaddleReaderOcrEngine.kt](../../app/src/main/kotlin/org/skepsun/kototoro/reader/translate/domain/PaddleReaderOcrEngine.kt)

Changes:

- separate page detection and crop recognition into distinct components
- keep one page-level detection pass
- feed detector regions into the recognizer
- preserve page coordinates after recognition

Current incremental status:

- `PaddleReaderOcrEngine` now has explicit internal detector and recognizer components
- the outer engine now also exposes formal `detect(...)` and region-level `recognize(...)` entry points
- the reader pipeline now consumes those interfaces directly for page-level `PADDLE` OCR
- the remaining work is to simplify away the remaining legacy OCR-service-only paths

### 5. Move grouping after recognition

Files:

- [ReaderBubbleGroupingCoordinator.kt](../../app/src/main/kotlin/org/skepsun/kototoro/reader/translate/domain/ReaderBubbleGroupingCoordinator.kt)
- [ReaderPageTranslationProcessor.kt](../../app/src/main/kotlin/org/skepsun/kototoro/reader/translate/domain/ReaderPageTranslationProcessor.kt)

Changes:

- feed grouping with recognized text fragments, not bubble-gated ROI results
- make grouping the place where fragmented lines become translation units
- allow grouping to operate even when bubble hints are absent

Current incremental status:

- a dedicated [ReaderTextMergeCoordinator.kt](../../app/src/main/kotlin/org/skepsun/kototoro/reader/translate/domain/ReaderTextMergeCoordinator.kt) now sits between page OCR and bubble grouping
- the page-first path is now:
  - page OCR blocks
  - text merge
  - bubble grouping
- this is the first concrete step toward the `manga-translator-ui` style `det -> rec -> merge` structure
- unmatched merged text fragments now stay as independent translation units instead of being regrouped heuristically a second time
- bubble grouping is being reduced toward a post-merge bubble-assignment helper rather than a second text-structure reconstruction layer
- detector-side subdivision of merged text groups has been removed; bubble assignment now preserves merge output instead of rewriting it again
- merge now receives `sourceLang` and uses explicit reading-order composition instead of fixed newline concatenation
- merge now prunes disproportionate long-chain edges to reduce accidental over-merge across nearby but unrelated fragments
- detector and OCR results now carry a lightweight direction hint, and merge uses that hint to distinguish horizontal and vertical chains
- detector, OCR blocks, and merged fragments now also carry lightweight `angle` / `axis-aligned` hints, which merge uses as an additional guard before connecting fragments
- text regions, OCR blocks, and merged fragments now support optional `quadPoints`; current implementations derive them from `Rect`, but the data path is ready for detector-provided quadrilaterals later
- merge edge-distance pruning now consults `quadPoints` in addition to `Rect` gaps, so the geometry path is no longer strictly rectangle-only
- the Paddle recognizer now crops from `TextRegion` geometry rather than a bare `Rect`, using axis-aligned quad-compatible cropping as the current compatibility step toward transformed-region recognition
- the Paddle recognizer now also attempts a four-point `Matrix.setPolyToPoly` warp for non-axis-aligned quads before falling back to bounding-rect cropping
- the remaining quality issue is now dominated by detector recall and detector box size, not by recognizer wiring

### 5.1 Immediate quality work

The following quality improvements have been completed:

1. ✅ YSG YOLO v11 OBB integrated as bubble grouping assistant (100% fragment coverage)
2. ✅ PP-OCRv5 Server recognizer added for stronger Japanese/Chinese recognition
3. ✅ OBB angle correction ensures rotated manga text is correctly bounded
4. ✅ OBB-specific NMS/filtering parameters prevent adjacent bubble suppression
5. ✅ Rendering area double-compression fixed

Remaining quality work:

- evaluate PP-OCRv5 Server vs Mobile on diverse manga styles
- consider dedicated MangaOCR integration as alternative recognizer
- further tune `tightenDetectedBubbleRect` padding for edge cases with very small text fragments

### 6. Demote bubble detection to a non-authoritative helper

Files:

- [OnnxBubbleDetectorEngine.kt](../../app/src/main/kotlin/org/skepsun/kototoro/reader/translate/domain/OnnxBubbleDetectorEngine.kt)
- [ReaderBubbleRoiOcrCoordinator.kt](../../app/src/main/kotlin/org/skepsun/kototoro/reader/translate/domain/ReaderBubbleRoiOcrCoordinator.kt)
- [ReaderBubbleGroupingCoordinator.kt](../../app/src/main/kotlin/org/skepsun/kototoro/reader/translate/domain/ReaderBubbleGroupingCoordinator.kt)

Changes:

- remove bubble detector from the OCR front door
- keep it as:
  - optional grouping hint
  - optional render anchor hint
- remove bright-region gating from the OCR admission path

Current incremental status:

- bubble detection no longer defines OCR entry or OCR search space
- the active OCR coordinator always starts from page-level text detection and region recognition
- bubble-related logic now runs after merge as grouping / render assistance
- **YSG YOLO v11 OBB** model integrated with proper OBB angle→AABB conversion
- OBB-specific parameters tuned: NMS ≥0.85, max 64 boxes, min 12px side
- `tightenDetectedBubbleRect` fixed: removed double-compression, padding now based on detector box dimensions
- **PP-OCRv5 Server** recognizer added to model catalog (same `ch_PP-OCRv5_rec_server_infer.onnx` weights as `manga-translator-ui`)
- model catalog now offers both Mobile (lightweight) and Server (high-accuracy) PP-OCRv5 variants

### 7. Reduce destructive OCR postprocessing

Files:

- [TextPostprocessor.kt](../../app/src/main/kotlin/org/skepsun/kototoro/reader/translate/domain/TextPostprocessor.kt)

Changes:

- keep only minimal cleanup in the default path
- avoid unconditional whitespace stripping and forced full-width conversion
- move aggressive normalization behind explicit language-specific handling

Current incremental status:

- the old destructive `TextPostprocessor.kt` has already been removed from the active codebase

## Phase 2. Promote `det -> rec -> merge` to first-class pipeline objects

Changes:

- introduce shared `ReaderTextDetector` and `ReaderTextRecognizer` interfaces
- move Paddle ONNX detector/recognizer out of the monolithic engine wrapper
- let the page processor coordinate explicit `det -> rec -> merge`
- keep coordinates in page space

Expected result:

- lower latency
- better geometry consistency
- easier engine comparison

## Phase 3. Keep bubble handling strictly post-OCR

Changes:

- keep bubble detection as optional grouping/render metadata only
- prevent bubble hints from changing OCR search space
- continue reducing ROI-first legacy code until it is isolated as an experiment or removed

Expected result:

- predictable tuning
- reliable benchmarks

## Phase 4. Rebuild merge around text structure

Changes:

- make merge operate on recognized text fragments
- support vertical text order explicitly
- deduplicate overlapping blocks
- treat narration and SFX as first-class text groups

Expected result:

- behavior closer to `manga-translator-ui`
- less incorrect merging based on bubble guesses

## Phase 5. Restrict bubble detection to rendering concerns

Changes:

- bubble detector only refines render anchor and, when safe, post-OCR group boundary hints
- OCR still succeeds when bubble detector misses everything

Expected result:

- stable OCR independent of bubble-detector recall

## Concrete Refactor Targets In Current Code

## High priority

- [AppSettings.kt](../../app/src/main/kotlin/org/skepsun/kototoro/core/prefs/AppSettings.kt)
  Remove OCR engine aliasing that rewrites explicit engine choices into `HYBRID`.

- [PaddleReaderOcrEngine.kt](../../app/src/main/kotlin/org/skepsun/kototoro/reader/translate/domain/PaddleReaderOcrEngine.kt)
  Replace delegation placeholder with either a real implementation or remove the engine option until it exists.

- [ReaderPageTranslationProcessor.kt](../../app/src/main/kotlin/org/skepsun/kototoro/reader/translate/domain/ReaderPageTranslationProcessor.kt)
  Stop hardcoding `ROI_FIRST_FALLBACK` as the authoritative OCR path.

- [ReaderOcrPipelineCoordinator.kt](../../app/src/main/kotlin/org/skepsun/kototoro/reader/translate/domain/ReaderOcrPipelineCoordinator.kt)
  Rework orchestration so text detection is the first-class front-end, not bubble detection.

- [NcnnReaderOcrEngine.kt](../../app/src/main/kotlin/org/skepsun/kototoro/reader/translate/domain/NcnnReaderOcrEngine.kt)
  Split page detection from region recognition. Remove temp-file ROI re-detect path.

## Medium priority

- [ReaderBubbleRoiOcrCoordinator.kt](../../app/src/main/kotlin/org/skepsun/kototoro/reader/translate/domain/ReaderBubbleRoiOcrCoordinator.kt)
  Rename or redesign around region OCR requests instead of bubble OCR requests.

- [ReaderBubbleGroupingCoordinator.kt](../../app/src/main/kotlin/org/skepsun/kototoro/reader/translate/domain/ReaderBubbleGroupingCoordinator.kt)
  Move it later in the pipeline so it groups recognized text rather than deciding OCR scope.

- [TextPostprocessor.kt](../../app/src/main/kotlin/org/skepsun/kototoro/reader/translate/domain/TextPostprocessor.kt)
  Reduce default normalization to minimal cleanup.

## Benchmark Criteria

The refactor should be judged by these metrics:

- detector recall on representative manga pages
- OCR text coverage per page
- merged bubble count stability
- translated bubble count
- p50 and p95 OCR latency
- fallback rate by engine
- render success rate

Quality should be measured on real manga samples, not only synthetic pages.

## Success Criteria

The OCR redesign is successful when:

- OCR quality no longer depends on bubble brightness heuristics
- missing bubble boxes do not cause missing OCR
- recognition engines can be compared fairly under the same detected regions
- merge quality improves on vertical Japanese dialogue and narration
- the runtime behavior matches the engine and model settings shown in UI

## Related Documents

- [OCR Architecture Review](./ocr-architecture-review.md)
- [Automatic Translation](../automatic-translation.md)
- [OCR Roadmap Review (2026-03)](../archive/ocr-roadmap-review-2026-03.md)
