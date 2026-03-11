# Kototoro OCR Pipeline V2 Design

## 1. 背景

当前阅读器翻译链路已经具备以下能力：

- 整页 OCR（NCNN / HYBRID / TFLite / MLKit）
- OCR fallback
- OCR block grouping
- 第一版 `CV Bubble Detector` 辅助 grouping
- 单页诊断埋点与章节级 benchmark 汇总

当前主流程仍然是典型的 **Full-page OCR pipeline**：

```text
page image
  -> OCR(det + rec)
  -> text blocks
  -> bubble grouping
  -> translation
  -> render
```

该流程已经能工作，但存在两个结构性问题：

1. **文本归属依赖后处理猜测**
   - OCR 返回的是整页 text blocks。
   - `groupFragmentsByBubble(...)` / `groupFragmentsForTranslation(...)` 需要再去推断哪些 block 属于同一气泡。
   - 这一步天然容易把相邻 bubble、旁白框、SFX 误并或漏并。

2. **OCR 识别区域过大**
   - 漫画页里大量像素区域并非对白。
   - 整页 det + rec 会扫描大量无关区域，造成无效 latency 与误识别噪声。

因此，下一阶段的核心目标不是继续增强 grouping，而是逐步过渡到 **ROI-aware OCR pipeline**。

## 2. v1 当前架构

### 2.1 当前数据流

```text
page image
  -> recognizeTextWithFallback(...)
  -> drawableBlocks
  -> sourceFragments
  -> groupFragmentsForTranslation(...)
       -> CvBubbleDetector.detect(bitmap, fragmentRects)
       -> fallback groupFragmentsByBubble(...)
  -> bubbleInputs
  -> translateBlocksCached(...)
  -> render
```

### 2.2 当前实现特点

- `CvBubbleDetector` 只是 **辅助分组**，不是 OCR 主入口。
- `readerTranslationDetModelId` 当前只用于 **NCNN OCR det 模型选择**，不控制 bubble detector。
- `NcnnReaderOcrEngine.detectBoxes(...)` 已存在，但当前主流程没有消费它。
- 当前 `bubble.detector.coverage_rate` 仍偏低，不能支撑纯 ROI OCR。

### 2.3 当前阶段结论

当前版本适合作为 **过渡态**：

- 保留整页 OCR 的稳定性。
- 用 `CvBubbleDetector` 收集 recall / coverage /误检数据。
- 为 ROI OCR 铺设接口和 fallback 机制。

## 3. v2 目标架构

### 3.1 目标流程

```text
page image
  -> bubble detect
  -> bubble bbox list
  -> ROI OCR
  -> coverage check
  -> if coverage low:
       fallback page OCR
  -> merge
  -> translation
  -> render
```

### 3.2 设计原则

- **兼容优先**：不删除现有整页 OCR 路径。
- **渐进迁移**：先支持 ROI OCR，再决定默认策略。
- **ROI 优先，整页兜底**：避免因 detector recall 不足导致漏字。
- **统一接口**：整页 OCR 与 ROI OCR 走同一套 request / cache / metrics 语义。
- **可灰度**：支持按开关、阈值、样本集逐步验证。

## 4. 为什么不能直接切纯 ROI Pipeline

当前 `CvBubbleDetector` 的真实埋点已经说明：

- `bubble.detector.coverage_rate` 在不少页面接近 `0.0`
- `matched_fragments` / `used_groups` 仍然偏低

这意味着：

```text
bubble detect
  -> ROI OCR
```

在当前阶段会直接退化成：

```text
很多页面没有任何文本输入
```

因此 v2 的正确方向不是：

```text
ROI OCR only
```

而是：

```text
ROI OCR + fallback page OCR
```

## 5. OCR 接口抽象

### 5.1 当前接口问题

当前 `ReaderOcrService` 是：

```kotlin
interface ReaderOcrService {
    suspend fun recognize(sourceUri: Uri, sourceLang: String, pageId: Long? = null): List<OcrTextBlock>
}
```

问题：

- 无法统一描述 ROI 请求。
- 无法自然表达 request type / debug tag / area ratio。
- page OCR 与 ROI OCR 后续会出现两套 cache / metrics 语义。

### 5.2 建议接口

```kotlin
data class OcrRequest(
    val sourceUri: Uri,
    val sourceLang: String,
    val roi: Rect? = null,
    val pageId: Long? = null,
    val requestType: OcrRequestType = OcrRequestType.PAGE,
    val debugTag: String? = null,
)

enum class OcrRequestType {
    PAGE,
    ROI,
    FALLBACK,
}

interface ReaderOcrService {
    suspend fun recognize(request: OcrRequest): List<OcrTextBlock>
}
```

### 5.3 为什么用 `OcrRequest`

- **统一缓存 key**
- **统一 debug log**
- **统一埋点模型**
- **便于未来扩展 batch ROI OCR**

## 6. ROI OCR 流程

### 6.1 最小实现

1. `CvBubbleDetector.detect(bitmap, fragmentRects)` 或未来 detector 返回 `List<Rect>`
2. 对每个 `Rect` 生成 ROI 请求
3. OCR engine 内部执行 crop
4. 对 ROI 结果排序、合并、归一化
5. 若 coverage 低于阈值，则触发整页 OCR fallback

### 6.2 OCR engine 内部建议

建议不要在 `ReaderPageTranslationProcessor` 手动 crop，再把临时 bitmap 传满链路。

更稳的方式是：

- Processor 构造 `OcrRequest(roi=...)`
- OCR engine 自己决定如何 crop / decode / preprocess

优点：

- cache key 更统一
- 日志更统一
- 后续更容易做引擎级优化

### 6.3 ROI 结果的最小语义

ROI OCR 输出仍可沿用 `List<OcrTextBlock>`，但要求：

- bbox 坐标使用 **页坐标系**，不要保留裁剪局部坐标
- 保持与整页 OCR 输出结构一致
- 允许后续 merge / dedup 复用同一工具函数

## 7. Fallback 策略

### 7.1 基本策略

```text
roiResults = runRoiOcr()

if coverage(roiResults) < threshold:
    pageResults = runPageOcr()
    finalResults = merge(roiResults, pageResults)
else:
    finalResults = roiResults
```

### 7.2 为什么 fallback 必须存在

- bubble detector recall 目前不稳定
- 对话框外文本依然存在
- 旁白框、SFX、标题字不一定落在 bubble detector 范畴内

### 7.3 merge 规则

建议优先级：

```text
ROI result > Page result
```

理由：

- ROI 天然分组更准确
- Page OCR 主要负责补漏

### 7.4 去重建议

最小去重规则：

- 若 page block 与 ROI block 有明显 bbox overlap，保留 ROI
- 若文本完全相同且中心点距离很近，保留 ROI
- 若 page block 落在已命中的 bubble rect 内，优先视为重复

## 8. Coverage 设计

### 8.1 不建议的算法

不要用：

- `roiResultCount > 0`
- `matchedBubbleCount / detectedBubbleCount`

这些指标容易造成假高 coverage。

### 8.2 建议的 coverage 语义

建议至少落两种指标：

1. **bubble coverage**

```text
recognizedBubbleCount / detectedBubbleCount
```

2. **area coverage**

```text
recognizedTextArea / detectedBubbleArea
```

在 Phase 2 MVP 中，先实现更简单但可用的版本：

```text
roiRecognizedArea / detectorBubbleArea
```

并辅以：

```text
roiRecognizedCharCount
```

作为调试辅助指标。

### 8.3 fallback 判定建议

可先使用双阈值：

```text
if bubbleCoverage < 0.5 || areaCoverage < 0.12:
    fallback page OCR
```

阈值不应写死为最终值，应通过真机样本调优。

## 9. Cache 设计

### 9.1 当前风险

当前缓存更偏向整页维度：

- OCR cache
- render cache
- text cache

如果直接引入 ROI OCR 而不扩展 key，会出现：

- page OCR 与 ROI OCR 命中冲突
- bbox 调整后缓存失效不正确

### 9.2 建议 cache key

建议统一成：

```text
ocr_cache_key =
    pipeline_version
  + source_uri
  + source_lang
  + request_type
  + roi_hash
  + engine_signature
```

其中：

- `roi_hash`
  - `PAGE` 请求时为空
  - `ROI` / `FALLBACK` 时由 `Rect(left,top,right,bottom)` 生成
- `engine_signature`
  - 包含 OCR engine、det model、rec model、fallback threshold 等

### 9.3 Phase 2 的最低要求

Phase 2 不要求立即把所有缓存拆到最优，但必须保证：

- ROI OCR 与 page OCR key 不冲突
- pipeline version 升级后可整体失效

## 10. Metrics & Debug Logging

### 10.1 新增埋点建议

在现有 `metric.ocr.*`、`metric.translation.*` 基础上新增：

```text
metric.ocr.request.page=1
metric.ocr.request.roi_count=8
metric.ocr.roi.total_ms=420
metric.ocr.roi.coverage.bubbles=0.62
metric.ocr.roi.coverage.area=0.18
metric.ocr.roi.char_count=57
metric.ocr.roi.fallback=1
metric.ocr.roi.fallback_reason=LOW_COVERAGE
metric.ocr.merge.roi_blocks=14
metric.ocr.merge.page_blocks=22
metric.ocr.merge.deduped_blocks=9
```

### 10.2 debugTag 的作用

`OcrRequest.debugTag` 建议用于把日志直接对齐到业务语义：

```text
OCR[ROI bubble#3]
OCR[FALLBACK page]
```

对真机调试比只看引擎耗时更有帮助。

## 11. ReaderPageTranslationProcessor 改造建议

### 11.1 当前职责

当前 `ReaderPageTranslationProcessor` 已经很大，继续把 ROI / merge / fallback 逻辑直接塞进去会进一步失控。

### 11.2 建议拆分

新增一个中间协调层，例如：

```kotlin
class ReaderOcrPipelineCoordinator {
    suspend fun recognizeForTranslation(
        sourceUri: Uri,
        sourceLang: String,
        bitmap: Bitmap,
        pageId: Long,
    ): OcrPipelineResult
}
```

返回：

```kotlin
data class OcrPipelineResult(
    val textBlocks: List<OcrTextBlock>,
    val usedMode: OcrPipelineMode,
    val roiMetrics: RoiMetrics?,
)
```

这样 `ReaderPageTranslationProcessor` 只消费最终 blocks，而不承担全部细节。

### 11.3 Phase 2 不强制拆类

若当前迭代目标是快速验证，也可以先在 processor 内部加私有 helper，但建议：

- 新逻辑至少抽成独立函数
- 避免再把 `processImpl(...)` 继续拉长

## 12. 并发模型

### 12.1 风险点

ROI OCR 引入后，最容易失控的是：

- 每页多个 bubble 并发 OCR
- 多页翻译并发
- 引擎本身是否线程安全

当前已有：

- 按页并发：`MAX_PARALLEL_TRANSLATION_PAGES = 2`

新增 ROI 后建议：

- 页级并发继续保守
- ROI 级并发先串行或小并发
- 不要同时放开页并发和 ROI 并发

### 12.2 建议起步策略

```text
page parallelism = 2
roi parallelism per page = 1
```

待真机稳定后再考虑：

```text
roi parallelism per page = 2~3
```

## 13. Rollout Plan

### Phase 2-A: 接口准备

- 引入 `OcrRequest`
- OCR engine 兼容 `roi != null`
- cache key 支持 request type / roi hash
- 不改默认 pipeline

### Phase 2-B: MVP 接线

- `CvBubbleDetector` 产出 ROI candidate
- 跑 ROI OCR
- 计算 coverage
- coverage 低时触发 page OCR fallback
- 加 merge / dedup

### Phase 2-C: 数据验证

- 真机样本看 `roi coverage`
- 看 fallback rate
- 看 merge 后识别质量
- 调 detector 阈值与 coverage 阈值

### Phase 2-D: 策略灰度

- 开关形式支持：
  - `Page OCR only`
  - `ROI + fallback`
- 不建议在数据不足时默认切纯 ROI

## 14. 不建议现在做的事

- 不建议把 bubble detector 塞进 `det model` 选择项
  - 当前二者不是同一语义
  - `det model` 是 OCR 检测模型，不是 bubble detector 策略

- 不建议直接删除整页 OCR 路径
  - 当前 detector coverage 明显不足

- 不建议立即上更重模型
  - 在 `CV Bubble Detector + ROI fallback` 还没跑出数据前，YOLO/ONNX detector 的收益无法判断

## 15. 最终结论

Kototoro 的 OCR v2 正确方向是：

```text
Bubble detect
  -> ROI OCR
  -> coverage check
  -> fallback page OCR
  -> merge
  -> translation
```

这不是对现有 pipeline 的推翻，而是一次 **兼容式架构升级**。

当前最合理的推进方式：

1. 先引入 `OcrRequest`
2. 支持 ROI OCR
3. 保留 page OCR fallback
4. 通过 coverage / fallback / merge metrics 做真机验证

在 `CvBubbleDetector` recall 未经验证前，不应切纯 ROI pipeline。
