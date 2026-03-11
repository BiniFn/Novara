# Kototoro OCR Roadmap Review Pack (Current Branch Status)

## 1. 当前已落地

- **Hybrid fallback 已实现**（NCNN 主 + TFLite 低置信 fallback）
  - 相关文件：
    - [HybridReaderOcrEngine.kt](../app/src/main/kotlin/org/skepsun/kototoro/reader/translate/domain/HybridReaderOcrEngine.kt)
    - [NcnnReaderOcrEngine.kt](../app/src/main/kotlin/org/skepsun/kototoro/reader/translate/domain/NcnnReaderOcrEngine.kt)
    - [AppSettings.kt](../app/src/main/kotlin/org/skepsun/kototoro/core/prefs/AppSettings.kt)
    - [TranslationSettingsFragment.kt](../app/src/main/kotlin/org/skepsun/kototoro/settings/TranslationSettingsFragment.kt)
    - [pref_translation.xml](../app/src/main/res/xml/pref_translation.xml)
  - 状态说明：
    - 已可灰度调节 fallback threshold
    - 已通过 `compileDebugKotlin`
    - 尚未完成真机 benchmark 与识别回归抽样
    - 无需 JNI 改动
    - 不依赖新模型或 cache schema 变更

## 2. 待验证埋点

| 埋点 | 说明 / 目标 |
| --- | --- |
| **Fallback rate** | 统计单页 / 单气泡 NCNN -> TFLite fallback 次数 |
| **Single-page OCR latency** | 每页 OCR 总耗时，便于发现瓶颈 |
| **p50 / p95 latency** | 观察延迟分布，评估设备差异 |
| **Cache hit** | 检查现有 page / OCR cache 命中率 |
| **识别回归样本** | 标记识别错误气泡或 SFX，用于回归测试 |

> 目的：建立真实设备数据基线，为下一步优化决策提供量化依据。

## 3. 下一阶段优先级

| 优先级 | 阶段 / 功能 | 说明 | 文件 / 位置 |
| --- | --- | --- | --- |
| 1 | **processingMutex -> 按页并发** | 全局串行瓶颈最明显，改造可提升吞吐量和多页并发体验 | `ReaderPageTranslationProcessor.processingMutex` / [ReaderPageTranslationProcessor.kt](../app/src/main/kotlin/org/skepsun/kototoro/reader/translate/domain/ReaderPageTranslationProcessor.kt) / [PageLoader.kt](../app/src/main/kotlin/org/skepsun/kototoro/reader/domain/PageLoader.kt) |
| 2 | **CV Bubble Detector** | 新能力接入，提升气泡 recall 与 OCR 效率；需新增 bubble bbox -> OCR ROI 路径，可能调整 fragment merge 逻辑，并新增 recall 埋点 | - |
| 3 | **YOLO fallback** | 建议等待 CV detector 数据出来后再决定是否落地，用于漏检 bubble 覆盖；需要新增轻量模型接入（ONNX / NCNN / TFLite 其一），不一定需要 JNI | - |

> 说明：排序按“当前仓库可落地成本 + 预期收益 + 风险”排列。

## 4. 决策建议

1. **先收集真机数据**：通过埋点获取 fallback rate、latency、cache 命中率等指标，建立真实基线。
2. **Hybrid fallback 阈值调优**：在 A/B 测试下确认不同 threshold 对 fallback 频率和延迟的影响。
3. **CV / YOLO 阶段**：
   - 等待 CV bubble detector recall 数据出来后再决定是否推进 YOLO fallback。
   - 避免在没有 benchmark 的情况下写死延迟或吞吐改善预期，例如 `500ms -> 120ms`。
4. **灰度可控**：所有 fallback / threshold / 并发改造均可逐步灰度上线，保证回归风险可控。

## 总结

- 当前仓库已实现核心 Hybrid fallback，可立即做真机 A/B 验证。
- 真正的全局吞吐瓶颈在 `processingMutex`，按页并发改造是下一阶段最值得做的优化。
- 新能力接入（CV bubble detector + YOLO fallback）属于结构升级，应以真实数据为依据再决定是否落地。
- 所有改动均可灰度测试，风险可控。
