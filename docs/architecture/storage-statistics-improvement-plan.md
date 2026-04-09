# Storage Statistics Improvement Plan
# 设置页存储统计面板改进计划

## 背景 (Background)
目前 `设置 -> 存储与网络` 下拉出的“存储使用量”图表统计精度不够，漏掉了许多现代 Kototoro 引入的重量级多媒体和 AI 资产。该计划旨在详细列出需要修复并计入总量的各级路径与界面模块。

## 待覆盖盲区 (Unaccounted Areas)

### 1. AI 本地下载模型 (Local AI Models)
- **漏统分析**：
  在内置的离线文本翻译（ML Kit / NLLB）以及图片动漫超分辨（Real-ESRGAN / Anime4K）中，应用会下载数百 MB 甚至上 GB 的本地模型。这些文件存储于独立存放目录中，目前未被计入任何图表的分段中。
- **改进方案**：
  在 `StorageUsage.kt` 中新增细分块 `StorageUsage.Item(aiModels)`。
  在 `LocalStorageManager.kt` 中新增专门获取模型体积的聚合函数：
  计算 `getFilesDir("models")`、`getFilesDir("realesrgan")` 等独立资源。
  在 UI (`StorageUsagePreference.kt`) 分配独立的视觉进度条颜色色块，并命名为 `AI 模型`。

### 2. 细分缓存类型 (Segmented Media Caches)
- **漏统分析**：
  目前统一归化在 `Other Cache` 或根本没有计算部分外置缓存的根目录。视频流媒体播放产生的几十MB到几百MB的 `mpv_cache` 流媒体分段、发音人引擎 (TTS) 自动下载生成的音频切片、小说缓冲目录 `novel_cache` 等皆属于盲区。
- **改进方案**：
  将当前底层的 `computeCacheSize` 修改或合并。考虑到 UI 条形图宽度，最佳方案依然是将所有这些“临时文件”作为大类总计，但需要根本上修复 `computeCacheSize()` 里的缺陷（利用新的 `getVideoCacheDir()` 等补充进去）。

### 3. 被遗漏的“已保存内容” (Saved Content Miscalculations)
- **漏统分析**：
  在 `LocalStorageManager.kt` 的原生代码中：
  ```kotlin
  suspend fun computeStorageSize() = withContext(Dispatchers.IO) {
      getConfiguredStorageDirs().sumOf { it.computeSize() }
  }
  ```
  `getConfiguredStorageDirs()` **仅包含了 Manga (漫画) 目录**。这使得本地下载/导入的小说 (`getAvailableNovelStorageDirs()`) 和本地视频 (`getAvailableVideoStorageDirs()`) 的巨量空间被计作 "Other" 或被彻底无视。
- **改进方案**：
  1. 将计算函数扩容：覆盖全核心架构目录。不仅统一入口，也使“跨平台泛用媒体”理念落地。
  2. 修改前端文案，将现在的 “已保存的漫画” (“Saved Manga” / `R.string.saved_manga`) 文案通用化为 **“已保存的媒体内容” (Saved Media Content)**。完美契合全媒体资源的跨领域存放特性。

---

## 实施路线图 (Implementation Roadmap)

#### 数据库与布局 (Database & Layout)
- 修改 `app/src/main/res/layout/preference_memory_usage.xml`
- 更新 `strings.xml` 中关于 Manga 字眼的过时词汇至 Media 内容集合。

#### 核心文件系统调度器 (Manager & Handlers)
- 扩展 `app/src/main/kotlin/org/skepsun/kototoro/local/data/LocalStorageManager.kt`，并发读取全量视频、TTS、模型库、以及分片小说的底层占用字节总和。
- 修改 `StorageAndNetworkSettingsViewModel.kt` 实现上报。
