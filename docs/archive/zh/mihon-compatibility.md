# Kototoro 兼容 Mihon 插件的技术报告

## 1. 背景与目标
Kototoro 在 0.2.x 版本开始内置对 Mihon（原 Tachiyomi）扩展生态的兼容能力，核心目标：
- 直接识别已安装的 Mihon 扩展 APK，并加载其中的 `Source`/`CatalogueSource`。
- 复用 Kototoro 现有的网络栈、缓存与 UI，同时保证插件沙箱隔离与稳定性。
- 将 Mihon 的数据/过滤器模型转为 Kototoro 的内部模型，做到无感接入。

## 2. 架构示意（简图）
```
[Mihon 扩展 APK] 
      | (包扫描/Manifest metadata: tachiyomi.extension.*)
      v
[MihonExtensionLoader] -- ChildFirstPathClassLoader --> [Source 实例列表]
      |                     ^
      |                     |
      |             [KotoInjektBridge + KotoNetworkHelper]
      v
[MihonExtensionManager] -- 缓存 Source/包装器 --> [MihonMangaSource]
      |
      v
[MihonMangaRepository] -- 数据/过滤器映射 --> [Kototoro Parser 层] --> [UI: Mihon Sources 列表、阅读流程]
```

## 3. 核心流程拆解
### 3.1 扩展发现与元数据解析
- 文件：`Kototoro/app/src/main/kotlin/org/Kototoro-app/Kototoro/mihon/MihonExtensionLoader.kt`
- 通过 `PackageManager` 扫描已安装包，检测特征：
  - Manifest feature：`tachiyomi.extension`
  - 包名约定：包含 `.extension`/`eu.kanade.tachiyomi.`/`org.keiyoushi.`
  - Manifest meta-data：`tachiyomi.extension.class` 或 `tachiyomi.extension.factory`
- 版本窗口校验：`LIB_VERSION_MIN=1.2`，`LIB_VERSION_MAX=1.9`，超出范围直接报错。
- NSFW 标记读取：`tachiyomi.extension.nsfw`。
- 语言提取：从包名 `extension.<lang>.xxx` 拆出语言码。

### 3.2 Injekt/依赖桥接
- 文件：`mihon/MihonModule.kt`、`mihon/compat/KotoInjektBridge.kt`
- 启动前调用 `injektBridge.initialize()`，向 Injekt 注入：
  - `Application`、`Context`
  - `OkHttpClient`、`CookieJar`
  - `NetworkHelper` 实现：`KotoNetworkHelper`（移除 GZipInterceptor，避免错误的 `Content-Encoding:gzip` 导致部分站点失败）
  - `Json/StringFormat/SerialFormat`
- 作用：让 Mihon 扩展内部依赖（网络、序列化等）在 Kototoro 进程内可用。

### 3.3 ClassLoader 隔离
- 文件：`mihon/util/ChildFirstPathClassLoader.kt`
- 采用 Child-First 策略加载扩展 dex，避免与宿主的依赖版本冲突。
- 白名单前缀（如 `kotlin.`、`android.`、`eu.kanade.tachiyomi.*`）强制走父加载器，确保核心 API 共享。

### 3.4 Source 加载与缓存
- `MihonExtensionLoader.loadSources(...)` 通过反射实例化：
  - 若是 `Source` 直接使用
  - 若是 `SourceFactory` 调用 `createSources()`
- `MihonExtensionManager`（`mihon/MihonExtensionManager.kt`）负责：
  - 异步加载、分发成功/失败列表
  - 按 `source.id` 缓存 `Source` 与包装后的 `MihonMangaSource`
  - 检测同名多语言源，决定是否追加语言后缀

### 3.5 数据模型转换
- 文件：`mihon/model/MihonDataConverters.kt`
- 关键转换：
  - `SManga` ↔ `Manga`：处理绝对/相对 URL、封面兜底、成人分级、作者/状态映射
  - `SChapter` ↔ `MangaChapter`：稳定 ID 生成，必要时用索引反推章节号避免排序反转
  - `Page` ↔ `MangaPage`：组合章节 URL 与页索引生成唯一 ID，解决缓存冲突
  - 公共 URL 获取：`HttpSource.getMangaUrl/getChapterUrl` 安全包装
- URL 清洗：修正重复 baseUrl、协议缺失（如 `https//`）。

### 3.6 过滤器映射
- 文件：`mihon/MihonFilterMapper.kt`
- 将 Mihon `FilterList` 映射为 Kototoro `MangaListFilterOptions`：
  - Header/Group/Select/Sort/Text 等转为 `MangaTagGroup`/`MangaTag`
  - 支持 include/exclude（TriState）与排序前缀标记
- 反向更新：将用户选中的 Kototoro 过滤条件回填到 Mihon FilterList，供搜索/分类请求使用。

### 3.7 仓库适配与请求管道
- 文件：`mihon/MihonMangaRepository.kt`
- 列表：按 `SortOrder` 映射到 Mihon 的 popular/latest/search，分页计数器对齐。
- 详情/章节：对网络异常做一次重试；缺失字段兜底；章节列表反转+虚拟编号，保证阅读顺序稳定。
- 图片请求：
  - 复制 Mihon `HttpSource` headers，缺失 Referer 时补 baseUrl。
  - 对于需要先解析图片 URL 的页面，构造 `mihon://resolve` 伪协议再调用 `getImageUrl`。
  - 封面请求优先走 `imageRequest`，失败则回退父类实现。

### 3.8 UI 集成
- 入口资源：`res/xml/pref_sources.xml` 中的 Mihon 扩展入口；`res/layout/fragment_mihon_extensions.xml` 负责列表展示、空态。
- 文案：`res/values/strings.xml`（1125+ 行附近）定义 Mihon 标签、计数、提示语。
- Use case：`mihon/GetMihonSourcesUseCase.kt` 将 `MihonMangaSource` 转为 UI 可直接消费的 `MihonSourceItem`，附加语言后缀/NSFW 标记。

## 4. 关键类职责与所在位置
- 扫描/加载：`MihonExtensionLoader.kt`、`MihonExtensionManager.kt`
- 依赖桥：`MihonModule.kt`、`compat/KotoInjektBridge.kt`、`compat/KotoNetworkHelper.kt`
- 隔离：`util/ChildFirstPathClassLoader.kt`
- 模型转换：`model/MihonDataConverters.kt`、`model/MihonMangaSource.kt`
- 过滤器映射：`MihonFilterMapper.kt`
- 仓库适配：`MihonMangaRepository.kt`
- UI/Use case：`GetMihonSourcesUseCase.kt`，以及相关 XML 资源。

## 5. 兼容性与风险点
- 版本兼容：扩展库版本需落在 1.2–1.9，超出会被拒绝加载。
- 依赖冲突：Child-First 已隔离大部分第三方库，但共享前缀列表过窄/过宽都可能引入类冲突，需在问题出现时调整白名单。
- 网络拦截：为避免错误 GZip 头，Mihon 客户端不包含宿主的 `GZipInterceptor`；若未来拦截链有新依赖，需同步在 `KotoNetworkHelper` 复制或过滤。
- 章节顺序：部分源返回“最新在前”，当前策略是反转+顺序编号，极个别源若返回乱序仍可能异常，需要按源特性定制排序。
- 安全性：未做签名校验（`Untrusted` 类型未使用），默认信任已安装扩展，分发渠道需可控。

## 6. 调试与排错建议
- 检查扫描结果：查看 logcat 中 `MihonExtensionLoader` 输出，确认包被识别（feature/name/meta）。
- 版本问题：遇到 “Incompatible lib version” 直接升级/降级扩展。
- 网络异常：MihonNetwork 日志会打印请求/响应码与 200 字符预览，便于定位拦截/反爬。
- URL 处理：关注 `MihonDataConverters` 的警告日志（重复 baseUrl、协议缺失）；必要时针对具体源添加 URL 清洗特例。
- 过滤器：确认 `MihonFilterMapper` 是否正确回填选项，尤其 TriState/Sort 组合。
