# TVBox Runtime 对齐计划

状态日期：2026-03-14

本文档只讨论 Kototoro 与 Box 在 `TVBox 源 / 多仓源 / runtime` 这三条主线上的对齐情况，不以照搬 Box UI 为目标。

## 目标边界

对齐目标：

- 对齐 TVBox 源导入能力
- 对齐多仓源解析、存储与激活逻辑
- 对齐 TVBox runtime 分流能力
- 对齐常见 TVBox spider 的运行时宿主能力

明确不做：

- 不复制 Box 的页面结构和交互细节
- 不为了“看起来像 Box”而引入额外 UI 复杂度
- 不承诺一次性兼容所有 TVBox fork、所有 jar、所有 guard 变种

## 当前结论

当前状态不是“还没做 TVBox runtime”，而是已经进入“runtime 已接通，但兼容层仍不完整”的阶段。

已经明确完成的部分：

- TVBox 单仓与多仓导入链路已落地
- TVBox 源已经按站点归一化存储，而不是按整份仓库文档存储
- TVBox 在内容源管理、Explore、Search 中已有独立上下文
- `type = 4` QuickJS runtime 已存在
- `type = 3` / `csp_*` jar runtime 已有独立隔离执行通路
- 播放链路已有缓存、单飞、直链绕过和最终 URL 复用优化

当前最大的未对齐点：

- Guard 类 jar 仍未与 Box runtime 对齐
- 多仓源导入“是否完整吃满所有子仓”仍缺精确诊断
- `proxyLocal`、复杂代理链、复杂 JS 依赖链还没有达到 Box 级兼容

## 与 Box 的对齐矩阵

### 1. TVBox 源导入

已实现：

- 复用统一 JSON 导入入口导入 TVBox
- 支持根对象为 `sites` 的单仓 JSON
- 支持根对象为 `urls` 的多仓 JSON
- 支持去 BOM、跳过前导 `//` 注释、清理空白
- 支持多仓条目为对象或字符串
- 支持对象字段 `url/api/ext/link/file`
- 支持“标题 + URL”形式的字符串仓库条目
- 支持递归抓取子仓，当前深度上限已提升到 `3`
- 支持对中文域名子仓做 punycode 规范化后再拉取

已对齐到的数据模型：

- 每个 `site` 单独存为一个 `JsonSourceEntity`
- 存储结构保留 `site`、`root`、`meta`
- `meta` 已保留 `sourceLocator`、`sourceTitle`、`siteIndex`、`siteKey`、`siteApi`
- 仓库标题优先使用多仓 `urls[].name/title`，其次回退 root `name/title/siteName`

对应落点：

- [`JsonSourceManager.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/core/jsonsource/JsonSourceManager.kt)
- [`TVBoxStoredConfig.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/core/model/jsonsource/TVBoxStoredConfig.kt)

未完全对齐：

- 还没有详细日志说明每个子仓 URL 为什么成功或失败
- 用户样例 `http://z.qiqiv.cn/123.txt` 仍存在“理论 14 个仓，实导仅 5 个”的待验证问题
- 目前只能确认主流程可用，不能确认多仓完整率已经和 Box 一致

### 2. 内容源管理与多仓激活

已实现：

- 设置页中的 `Legado源目录` 已改为 `Json源目录`
- `导入 JSON 源` 已上移到外层“内容源”页面
- JSON 源目录已拆分页，按 `Legado / TVBox / JS` 展示
- TVBox 页面支持仓库分组、仓库过滤、仓库切换
- 当前激活的 TVBox 仓库会写入设置并在 Explore/Search 中显示
- 列表项已可展示所属仓库以及 active/inactive 状态

对应落点：

- [`pref_sources.xml`](../../app/src/main/res/xml/pref_sources.xml)
- [`JsonSourcesRootFragment.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/settings/sources/jsonsource/JsonSourcesRootFragment.kt)
- [`JsonSourcesFragment.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/settings/sources/jsonsource/JsonSourcesFragment.kt)
- [`JsonSourcesViewModel.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/settings/sources/jsonsource/JsonSourcesViewModel.kt)
- [`GroupedJsonSourcesAdapter.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/settings/sources/jsonsource/GroupedJsonSourcesAdapter.kt)
- [`AppSettings.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/core/prefs/AppSettings.kt)

结论：

- 这部分已经从“只有导入”推进到“有仓库上下文的 TVBox 源管理”
- 在产品层面，这已经明显偏向 Box 的“多仓 + 当前仓”模型，而不是单纯把 JSON 混在一个页面里

### 3. Runtime 分流

已实现：

- `type = 4` 走 `TVBoxQuickJsSpiderRuntime`
- `type = 3` 或 `api = csp_*` 走 `TVBoxJarSpiderIsolatedRuntime`
- 直接媒体、M3U、纯文本直播列表、简单 JSON 播放列表、部分 CMS 风格接口已具备可用支持

对应落点：

- [`TVBoxSpiderRuntimeFactory.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/core/parser/tvbox/TVBoxSpiderRuntimeFactory.kt)
- `TVBoxRepository` 相关实现

结论：

- Kototoro 现在已经有“按源类型分 runtime”的骨架，这一点与 Box 的方向是一致的
- 但“有分流”不等于“Guard 生态已兼容”

### 4. Jar Runtime 与进程隔离

已实现：

- 新增隔离执行通路，避免 jar spider 直接跑在主进程主类加载器
- 支持 jar 下载、缓存、MD5 校验、优化目录准备
- 支持远程 worker 请求模型与 IPC 结果封装
- 支持 `home/homeVod/category/search/detail/play/proxy` 基本动作分发
- 支持反射调用静态 `Init.init(context)` 作为兜底
- 支持对 `Init`、`DexNative`、`BaseSpiderGuard` 做静态上下文注入尝试

关键实现文件：

- [`TVBoxJarSpiderIsolatedRuntime.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/core/parser/tvbox/TVBoxJarSpiderIsolatedRuntime.kt)
- [`TVBoxJarSpiderExecutor.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/core/parser/tvbox/TVBoxJarSpiderExecutor.kt)
- [`TVBoxJarSpiderService.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/core/parser/tvbox/TVBoxJarSpiderService.kt)
- [`TVBoxJarSpiderRemoteClient.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/core/parser/tvbox/TVBoxJarSpiderRemoteClient.kt)
- [`TVBoxJarSpiderRemoteModels.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/core/parser/tvbox/TVBoxJarSpiderRemoteModels.kt)

结论：

- `jar runtime 不存在` 这个阶段已经结束
- 当前问题集中在 `jar runtime 的宿主兼容层不够完整`

### 5. 播放链路优化

已实现：

- `playerContent()` 结果缓存
- `playerContent()` 单飞，避免并发重复请求
- 详情页预取首个章节的播放结果
- 播放超时已提高到 45 秒
- 对 `magnet/thunder/ed2k/rtmp/...` 等直链协议直接绕过二次 `playerContent()`
- 播放器对“已解析出的最终 `http(s)` + headers”不再递归二次解析

对应落点：

- [`TVBoxJarSpiderIsolatedRuntime.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/core/parser/tvbox/TVBoxJarSpiderIsolatedRuntime.kt)
- [`VideoPlayerActivity.kt`](../../app/src/main/kotlin/org/skepsun/kototoro/video/ui/VideoPlayerActivity.kt)

结论：

- 这部分已经不再是纯接线，而是在做实际可用性的 runtime 优化
- 最近日志里普通 jar / 普通 spider 源的改善，和这些改动方向是一致的

### 6. Box 宿主兼容层

已实现的兼容垫片：

- 新增 Box 风格的宿主类与工具类 stub
- `App` 已扩展为 `MultiDexApplication`
- 已在 `init()` 中接入 base context
- `ChildFirstDexClassLoader` 已对部分 Box 相关包走父加载器委派

当前已补的类：

- [`App.java`](../../app/src/main/java/com/github/tvbox/osc/base/App.java)
- [`LOG.java`](../../app/src/main/java/com/github/tvbox/osc/util/LOG.java)
- [`MD5.java`](../../app/src/main/java/com/github/tvbox/osc/util/MD5.java)
- [`FileUtils.java`](../../app/src/main/java/com/github/tvbox/osc/util/FileUtils.java)
- [`OkGoHelper.java`](../../app/src/main/java/com/github/tvbox/osc/util/OkGoHelper.java)
- [`StringUtils.java`](../../app/src/main/java/com/github/tvbox/osc/util/StringUtils.java)

当前结论：

- 兼容层已经从“完全没有”推进到“有最小宿主骨架”
- 但仅靠这些 stub 还不足以覆盖 Guard jar 的真实依赖链

## 已知缺口

### P0. Guard 源仍然是主阻塞项

从最近多轮日志看，以下类仍稳定失败：

- `NewWoggGuard`
- `NewDouBanGuard`
- `WexBtTwoGuard`
- `BookShiJieGuard`
- `LiveHuYaGuard`

稳定错误链路：

- 构造函数进入 `BaseSpiderGuard.<init>`
- 调用 `Init.getSpider`
- 进入 `DexNative.<clinit>`
- 抛出 `NullPointerException: Context.getCacheDir() on null object reference`
- 外层表现为 `NoClassDefFoundError: com.github.catvod.spider.DexNative`

这说明：

- 当前失败点不在“找不到 jar”
- 也不只是“没有调 `Init.init(context)`”
- 更像是 `DexNative` 所依赖的 Box 宿主初始化语义仍未满足

判断：

- 这是当前 TVBox runtime 对齐的第一优先级

### P0. 多仓完整率仍未证实

已知现象：

- 用户样例 `http://z.qiqiv.cn/123.txt` 原始文件可见 14 个条目
- 实际导入结果用户观察到只有 5 个仓或 5 组来源

当前缺失：

- 每个子仓条目的解析结果日志
- 每个子仓 URL 的规范化结果日志
- 每个子仓的校验失败原因
- 每个子仓拉取后的根结构识别结果
- 每个子仓最终产出的站点数量

判断：

- 这一项还不能宣称已经与 Box 多仓行为对齐

### P1. `proxyLocal` 复杂行为未对齐

当前状态：

- 已有基础 `proxy` 动作分发
- 但复杂二进制流、分段代理、多阶段 guard 代理链尚未证明可用

影响：

- 即使 spider 能初始化成功，也不代表所有播放链都能跑通

### P1. JS 生态兼容面仍不完整

仍未确认或未实现：

- `//bb` bytecode 支持
- ES module 风格 TVBox JS 加载
- `cat.js` 依赖链
- 更完整的 JS host bridge

### P2. 可观测性与测试仍不足

当前缺失：

- 缺少针对多仓导入的细粒度诊断日志
- 缺少 Guard jar 兼容问题的显式分类日志
- 缺少固定夹具测试来覆盖 `sites`、`urls`、child fetch、runtime 选择、play 缓存

## 参考日志解读

结合 2026-03-14 的最新日志，可以得出两个重要判断。

第一，普通 jar / 非 Guard 源不是完全不工作，说明当前 runtime 主链路已经跑起来了。

第二，Guard 源的失败高度一致，全部指向 `DexNative` 的静态初始化和宿主环境假设，这说明下一步不应继续泛化修补，而应聚焦 Guard 兼容层本身。

## 下一阶段实施计划

### 阶段 A：补全多仓诊断

目标：

- 先确认 `123.txt` 为什么没有完整导入

任务：

- 给 `processTvBoxDocument()` 增加每个 child entry 的解析日志
- 给 `fetchTvBoxChildRepository()` 增加规范化 URL、校验结果、HTTP 结果日志
- 给每个 child root 增加 `sites/urls/unsupported` 结构判定日志
- 给每个 child 增加最终导入站点数日志

完成标准：

- 能明确解释“14 个条目为什么只导入了 5 个”

### 阶段 B：聚焦 Guard 兼容层

目标：

- 让 Guard jar 至少完成实例化并进入有效 spider 初始化

任务：

- 提升 `seedStaticContextIfPossible()` 的日志等级，确认是否真的找到并写入了上下文字段
- 对照本地 Box 运行时继续补齐 `DexNative` 相关宿主依赖
- 评估是否需要引入受控的 `DexNative` 兼容 stub 或替代装配路径
- 继续核对 `App`、`ApplicationInfo`、MultiDex、cache/codeCache 相关语义

完成标准：

- 至少一个 Guard 源不再死于 `DexNative.<clinit>`

### 阶段 C：补 `proxyLocal` 与复杂播放链

目标：

- 解决“能进详情但不能播”这类后半段兼容问题

任务：

- 为复杂 `proxyLocal` 返回类型补适配
- 为二进制代理、header 透传和中间重定向补日志与兼容处理
- 选 2 到 3 个 Guard/非 Guard 源建立真实回归样例

### 阶段 D：建立支持矩阵

目标：

- 给用户明确的“什么能用、什么不能用”

建议矩阵：

- A 级：直链、M3U、简单 CMS、已验证普通 spider
- B 级：QuickJS spider，部分可用
- C 级：普通 jar spider，持续补齐
- D 级：Guard jar，当前重点攻关
- X 级：暂未支持的 `//bb`、复杂 `cat.js`、未知 fork 特性

## 当前优先级排序

1. Guard jar 的 `DexNative` 宿主兼容
2. 多仓导入完整率诊断
3. `proxyLocal` 复杂代理链
4. JS 依赖链和 `//bb`
5. 测试与支持矩阵固化

## 简要判断

如果以 Box 为参照，Kototoro 当前已经对齐了“导入、多仓管理、runtime 分流、隔离执行骨架”。

真正没有对齐的，不是 UI，也不是入口，而是 Guard jar 所需的宿主兼容层，以及多仓完整率和复杂代理链的最终可靠性。
