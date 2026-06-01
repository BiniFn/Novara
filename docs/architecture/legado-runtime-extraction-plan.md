# Legado Runtime 抽取方案

## 目的

本文档定义一条可执行的方案：将 `../legado-with-MD3` 中真正有复用价值的规则运行时能力抽成独立模块，并以宿主适配接口的形式接入 Kototoro。

目标不是把 Legado 现有 APK、AAR 或 `app` 模块整体嵌入 Kototoro。

目标是：

- 复用 Legado 规则语义与运行时行为
- 避免把 Legado 的应用层、存储层、UI 层一并带入
- 降低 Kototoro 继续手工对齐 Legado 行为的维护成本
- 为后续兼容测试和行为回归建立稳定边界

## 非目标

以下内容不在本方案范围内：

- 直接把 `../legado-with-MD3/app` 打成产物并嵌入 Kototoro
- 复用 Legado 的 Compose UI、Activity、Service、Receiver
- 复用 Legado 的 Room 实体、数据库和 DAO
- 复用 Legado 的 Firebase、Cronet、Koin、Media3、Glide、Startup 等应用级依赖
- 一次性替换 Kototoro 当前全部 Legado 实现

## 现状判断

### 1. Legado 当前“规则核心”并不是独立模块

Legado 的规则主实现位于：

- [AnalyzeRule.kt](/d1/chuxiong/code/legado-with-MD3/app/src/main/java/io/legado/app/model/analyzeRule/AnalyzeRule.kt:52)
- [AnalyzeUrl.kt](/d1/chuxiong/code/legado-with-MD3/app/src/main/java/io/legado/app/model/analyzeRule/AnalyzeUrl.kt:81)
- [RuleAnalyzer.kt](/d1/chuxiong/code/legado-with-MD3/app/src/main/java/io/legado/app/model/analyzeRule/RuleAnalyzer.kt:4)
- [RuleData.kt](/d1/chuxiong/code/legado-with-MD3/app/src/main/java/io/legado/app/model/analyzeRule/RuleData.kt:5)

这些代码目前仍然放在 `app` 模块，而不是独立 `library` 模块。

### 2. 规则运行时与宿主实现存在重耦合

当前耦合点包括：

- `AnalyzeRule` 直接依赖 `BaseSource`、`Book`、`BookChapter`、`BookSource`、`RssArticle`
- `AnalyzeUrl` 直接依赖 HTTP、Cookie、缓存、WebView、媒体 URL 处理
- `BaseSource` 本身带有登录、变量、Cookie、加密、JS 扩展行为
- `JsExtensions` 直接依赖 Android API、WebView、应用配置、UI 跳转
- `CookieStore` 直接落库到 Legado 自己的 `appDb`

相关代码：

- [BaseSource.kt](/d1/chuxiong/code/legado-with-MD3/app/src/main/java/io/legado/app/data/entities/BaseSource.kt:33)
- [BookSource.kt](/d1/chuxiong/code/legado-with-MD3/app/src/main/java/io/legado/app/data/entities/BookSource.kt:31)
- [JsExtensions.kt](/d1/chuxiong/code/legado-with-MD3/app/src/main/java/io/legado/app/help/JsExtensions.kt:89)
- [CookieStore.kt](/d1/chuxiong/code/legado-with-MD3/app/src/main/java/io/legado/app/help/http/CookieStore.kt:20)

### 3. Kototoro 当前路线已经是“源码级行为对齐”

Kototoro 已经在维护自己的 Legado 兼容实现，例如：

- [AnalyzeRule.kt](/d1/chuxiong/code/Kototoro/app/src/main/kotlin/org/skepsun/kototoro/core/parser/legado/AnalyzeRule.kt:16)
- [AnalyzeUrl.kt](/d1/chuxiong/code/Kototoro/app/src/main/kotlin/org/skepsun/kototoro/core/parser/legado/AnalyzeUrl.kt:1)
- [LegadoRepository.kt](/d1/chuxiong/code/Kototoro/app/src/main/kotlin/org/skepsun/kototoro/core/parser/legado/LegadoRepository.kt:1)

因此更合理的方向不是“整包引入”，而是为现有适配实现找到一个长期可维护的共享内核。

## 设计原则

### KISS

只抽“规则内核 + 宿主接口 + Android 默认桥接”三层。

不要一开始就引入更多模块、更多平台适配、更多抽象。

### YAGNI

只为 Kototoro 当前真实需要的 Legado 能力抽取公共运行时：

- 规则切分
- JS/CSS/XPath/JSONPath 解析
- URL 模板解析
- 变量读写
- HTTP 请求计划生成
- JS 运行时与宿主桥接

不为暂时不用的 RSS、字体处理、外部 UI、完整应用配置体系预留过度接口。

### DRY

避免继续在 Kototoro 和 Legado 之间长期维护两套不断漂移的规则语义实现。

### SOLID

- 单一职责：规则解释、HTTP 执行、变量存储、Cookie 管理分别拆开
- 开闭原则：新增宿主只需实现接口，不改规则核心
- 依赖倒置：规则核心依赖抽象接口，不依赖 Kototoro 或 Legado 的具体实现

## 目标结构

建议在 Legado 侧或独立仓库中抽出以下模块。

### `:modules:legado-rule-core`

职责：

- 规则语法模型
- 规则分发与解释
- 字符串替换、占位符处理
- JS/CSS/XPath/JSONPath 基础解析
- 请求计划模型

要求：

- 尽量纯 Kotlin
- 最多依赖必要的解析库和脚本引擎抽象
- 不依赖 Android `Context`
- 不依赖 Room、UI、应用配置、数据库

建议放入的内容：

- `RuleAnalyzer`
- `RuleData`
- `RuleDataInterface`
- `AnalyzeByJSoup`
- `AnalyzeByJSonPath`
- `AnalyzeByXPath`
- `AnalyzeByRegex`
- `AnalyzeRule` 的核心逻辑
- 从 `AnalyzeUrl` 中拆出的 URL 模板与请求计划逻辑

### `:modules:legado-rule-bridge`

职责：

- 定义宿主能力接口
- 定义运行时上下文对象
- 定义日志、缓存、变量、Cookie、HTTP、WebView、JS 绑定接口

要求：

- 不依赖具体宿主实现
- 不绑定 Kototoro 或 Legado 的实体类型

### `:modules:legado-rule-android`

职责：

- 提供 Android 默认实现
- 对接 `SharedPreferences`、`OkHttp`、可选 `WebView`
- 提供默认的 `CookieStorePort`、`VariableStorePort`、`LoggerPort`

要求：

- 不直接依赖 Legado `appDb`
- 不直接依赖 Legado UI Activity
- 可以依赖 Android 和 OkHttp

## 不建议复用的现有类型

以下类型不应该原样进入抽出的 runtime：

- `io.legado.app.data.entities.BookSource`
- `io.legado.app.data.entities.Book`
- `io.legado.app.data.entities.BookChapter`
- `io.legado.app.data.entities.BaseSource`
- `io.legado.app.help.JsExtensions`
- `io.legado.app.help.http.CookieStore`

原因：

- 混合了实体、持久化、宿主行为、Android 能力
- 会把运行时边界重新污染回应用层

## 建议的新抽象

### 1. Source 描述对象

用纯 DTO 替代 `BookSource` / `BaseSource` 的运行时输入。

```kotlin
data class LegadoSourceDescriptor(
    val key: String,
    val name: String,
    val baseUrl: String?,
    val headerRule: String?,
    val loginUrlRule: String?,
    val loginUiRule: String?,
    val jsLib: String?,
    val enabledCookieJar: Boolean,
    val concurrentRate: String?,
    val exploreUrl: String?,
    val searchUrl: String?,
    val bookInfoRule: LegadoBookInfoRule?,
    val tocRule: LegadoTocRule?,
    val contentRule: LegadoContentRule?,
)
```

这个对象只承担规则运行时所需数据，不带 Room、Parcelable、数据库注解。

### 2. Rule 数据上下文

替代 `Book`、`BookChapter`、`RssArticle` 直接入栈的做法。

```kotlin
data class LegadoRuleContext(
    val source: LegadoSourceDescriptor?,
    val book: Map<String, Any?> = emptyMap(),
    val chapter: Map<String, Any?> = emptyMap(),
    val item: Any? = null,
    val extras: Map<String, Any?> = emptyMap(),
)
```

这样可以把运行时依赖从具体实体改为稳定的数据视图。

### 3. 宿主能力接口

#### HTTP

```kotlin
interface LegadoHttpExecutor {
    suspend fun execute(plan: LegadoRequestPlan): LegadoHttpResponse
}
```

#### Cookie

```kotlin
interface LegadoCookieStorePort {
    fun getCookie(urlOrKey: String): String?
    fun setCookie(urlOrKey: String, cookie: String?)
    fun replaceCookie(urlOrKey: String, cookie: String)
    fun removeCookie(urlOrKey: String)
}
```

#### 变量与登录信息

```kotlin
interface LegadoVariableStorePort {
    fun getSourceVariable(sourceKey: String): String?
    fun putSourceVariable(sourceKey: String, value: String?)
    fun getLoginInfo(sourceKey: String): String?
    fun putLoginInfo(sourceKey: String, value: String?)
    fun getLoginHeader(sourceKey: String): String?
    fun putLoginHeader(sourceKey: String, value: String?)
}
```

#### WebView 抓取

```kotlin
interface LegadoWebViewFetcher {
    suspend fun fetch(request: LegadoWebViewRequest): LegadoWebViewResult
}
```

#### 日志

```kotlin
interface LegadoRuntimeLogger {
    fun debug(tag: String, message: String)
    fun warn(tag: String, message: String, throwable: Throwable? = null)
}
```

### 4. JS 运行时桥接

`JsExtensions` 不应原样暴露。

建议拆成显式 bridge：

```kotlin
interface LegadoJsHostApi {
    suspend fun ajax(url: String, options: LegadoAjaxOptions? = null): String?
    suspend fun connect(url: String, headersJson: String? = null, callTimeout: Long? = null): LegadoHttpResponse
    suspend fun webView(request: LegadoWebViewRequest): String?
    fun getCookie(tag: String): String?
    fun putVariable(key: String, value: String?)
    fun getVariable(key: String): String?
}
```

JS 中需要哪些方法，由 bridge 显式注册，而不是把整个应用辅助能力全部挂进脚本上下文。

## 当前推进状态

截至当前版本，Kototoro 侧已经完成第一阶段的“运行时边界收口”：

- `AnalyzeUrl` 已拆出最小 JS 求值与请求计划构建
- `AnalyzeRule` 已切到 `LegadoRuleRuntimeContext`
- 模板替换、规则分类、segment 切分等纯逻辑已经从 `AnalyzeRule` 本体中抽离
- `BookList`、`BookInfo`、`BookChapterList`、`BookContent` 的主解析路径已改为显式注入 runtime context

这说明方案已经从“架构上可行”推进到“代码组织上初步落地”。

但这并不等于可以立即删除 `LegadoSandbox`。当前真正阻塞 runtime core 独立运行的，已经不是规则拆分本身，而是剩余的宿主状态与副作用能力。

## 剩余 Sandbox 强依赖清单

### A. JS 执行上下文写入

这些能力目前仍然依赖 `LegadoSandbox` 直接操作 JS 上下文：

- `setResult(result)`
- `eval(script)`
- `execute(script)`
- `putVariable(key, value)` / `getVariable(key)` / `getVariableAny(key)`

当前使用位置包括：

- `AnalyzeRule` 通过 `LegadoSandboxRuleRuntimeContext` 间接依赖这些能力
- `AnalyzeUrl` 通过 `LegadoSandboxJsEvaluator` 依赖 `key/page/baseUrl/result` 注入后再执行 JS
- `BookChapterList` 的 `formatJs`
- `BookContent` 的 `callBackJs`
- `LegadoRepository.runUserScript()` / `evalUserExpression()`

结论：

- 规则核心并不真正需要整个 `LegadoSandbox`
- 但它仍然需要一个“可写入变量、设置 result、执行 JS 并读回变量”的最小 JS runtime port

### B. 书籍 / 章节上下文注入

这些能力目前仍由 `LegadoSandbox` 持有并同步到 JS context：

- `setBook(BookContext)`
- `setChapter(ChapterContext)`

当前使用位置包括：

- `BookInfo.parse()` 在详情解析前后更新书籍上下文
- `LegadoRepository.getPagesFlow()` 在正文解析前设置章节上下文
- 若规则脚本通过 `book.xxx` / `chapter.xxx` / `book.getVariable(...)` 取值，这部分仍然必须保留

结论：

- 后续需要把 `book` / `chapter` 从 `LegadoSandbox` 的隐式上下文，提升为显式 runtime context 输入
- 在此之前，详情、目录、正文链路都还不能完全脱离 sandbox

### C. 持久变量与登录状态

当前 `LegadoSandbox` 仍承担变量持久化和恢复：

- source variable
- login info
- 临时变量回填到 JS context

虽然底层已经开始走：

- `KototoroLegadoVariableStore`
- `KototoroLegadoCookieStore`

但上层规则执行仍然默认通过 `LegadoSandbox` 访问这些状态。

结论：

- 变量和 cookie 的“存储端口”已具备
- 还缺一个不依赖 sandbox 的“运行时状态装配器”

### D. 规则后的副作用脚本

以下脚本不是纯规则求值，而是带宿主副作用的流程脚本：

- `formatJs`
- `callBackJs`
- 登录相关脚本
- 设置页用户手动触发脚本

这些脚本能不能迁到独立 runtime，不取决于 `AnalyzeRule` 是否继续拆，而取决于：

- runtime 是否能暴露与当前 JS context 足够接近的 API
- 是否允许继续复用 Kototoro 的 `JavaScriptEngine`

结论：

- 这是第二阶段问题
- 不应和第一阶段“规则内核抽离”混在一起一起做

## 最小 Runtime 试跑链路

下一步最有价值的工作，不是继续拆 `AnalyzeRule`，而是做一条最小可运行链路，验证 runtime core 能否脱离 `LegadoSandbox` 跑通。

建议只覆盖以下四步：

1. 输入 `LegadoSourceDescriptor`
2. 构建 `LegadoRequestPlan`
3. 执行一次 HTTP 请求
4. 用 `AnalyzeRule` 返回字符串或列表结果

### 试跑范围

建议只选“列表页解析”做第一条链：

- 不含 `setBook`
- 不含 `setChapter`
- 不含 `callBackJs`
- 不含 `formatJs`
- 不含正文翻页与图片解码

原因：

- 这是当前最短闭环
- 既能覆盖 URL 构建，也能覆盖请求和规则求值
- 成功后可以证明“最小 runtime core + bridge”已具备真实执行能力

### 试跑输入

最小输入应包含：

- source 基本信息
- `searchUrl` 或 `exploreUrl`
- header 规则
- key / page / baseUrl
- variable store / cookie store / http executor / js evaluator

### 试跑输出

最小输出只要求：

- 最终请求 URL
- 请求方法 / header / body
- 原始响应文本
- 解析出的 `List<String>` 或 `List<Any>`

不要求：

- 直接产出完整 `Content`
- 直接替换现有 `LegadoRepository`
- 覆盖登录、正文、目录分页

### 试跑成功标准

如果以下条件满足，就说明方案进入下一阶段：

- `AnalyzeUrl` 不依赖 `LegadoSandbox` 也能产出正确 `LegadoRequestPlan`
- `AnalyzeRule` 不依赖 `LegadoSandbox` 也能跑完列表规则
- JS 规则中的 `result`、`baseUrl`、`page`、`key` 能通过最小 port 正常注入
- 变量读写不需要通过 `SharedPreferences` 直连 JS 环境

## 下一阶段建议

下一阶段不建议继续做大规模文件拆分，而是按下面顺序推进：

### 1. 建一个无 Sandbox 的最小 Rule Runtime Adapter

最少需要提供：

- `evalJs(script, result, baseUrl)`
- `putVariable(key, value)`
- `getVariable(key)`
- `getVariableAny(key)`

并补充：

- `key`
- `page`
- `baseUrl`
- `result`

这些运行时注入能力。

### 2. 用一条列表链路做试跑

优先在 `exploreUrl` 或 `searchUrl` 上验证：

- URL 构建
- HTTP
- 规则解析

先不要碰：

- 正文
- 目录分页
- 登录
- callback / formatJs

### 3. 试跑通过后，再处理上下文型副作用

包括：

- `setBook`
- `setChapter`
- `callBackJs`
- `formatJs`

这一步才是真正决定是否能完全替换 `LegadoSandbox` 的阶段。

## 结论

到当前阶段，继续机械拆分 `AnalyzeRule` 的收益已经不高。

真正有价值的下一步是：

- 明确剩余 `sandbox` 强依赖
- 搭一条最小 runtime 试跑链
- 用运行结果判断哪些能力必须进入 bridge，哪些可以继续留在 Kototoro 宿主层

也就是说，方向已经从“继续拆代码”切换为“验证最小可运行 runtime”。

## `AnalyzeUrl` 的拆分建议

`AnalyzeUrl` 当前职责过重，不应整体搬入 runtime。

建议拆成三个部分。

### `UrlTemplateEvaluator`

职责：

- 处理 `@js` / `<js>`
- 处理 `&#123;&#123;...}}`
- 处理 `&#123;&#123;page}}`
- 处理 `<a,b,c>` 这类分页模板

输出：

- 已替换的规则字符串

### `RequestPlanBuilder`

职责：

- 从规则字符串解析出 URL、本体、header、charset、method、body、retry、cookie、webView 等选项

输出：

- `LegadoRequestPlan`

```kotlin
data class LegadoRequestPlan(
    val url: String,
    val method: HttpMethod,
    val headers: Map<String, String>,
    val body: String?,
    val charset: String?,
    val retryCount: Int,
    val useWebView: Boolean,
    val webJs: String?,
    val bodyJs: String?,
    val proxy: String?,
    val readTimeoutMillis: Long?,
    val callTimeoutMillis: Long?,
)
```

### `RequestExecutorFacade`

职责：

- 调用 `LegadoHttpExecutor`
- 必要时走 `LegadoWebViewFetcher`
- 组合 Cookie、重试、响应解码

这样一拆，核心解析逻辑与网络执行逻辑就能彻底解耦。

## `AnalyzeRule` 的拆分建议

`AnalyzeRule` 当前既做规则解释，又做上下文读写，还做 JS bridge 入口。

建议保留一个核心解释器，再把环境相关能力下沉到 `RuntimeContext`。

```kotlin
data class LegadoRuntimeContext(
    val source: LegadoSourceDescriptor?,
    val ruleContext: LegadoRuleContext,
    val httpExecutor: LegadoHttpExecutor,
    val cookieStore: LegadoCookieStorePort,
    val variableStore: LegadoVariableStorePort,
    val webViewFetcher: LegadoWebViewFetcher?,
    val logger: LegadoRuntimeLogger,
)
```

核心解释器只依赖 `LegadoRuntimeContext`，不直接访问任何 App 单例。

## Kototoro 侧映射建议

Kototoro 不需要等待 Legado 先完成抽取，完全可以先按未来模块边界整理自己的实现。

### Kototoro 中已有可对应的能力

- HTTP：`LegadoRepository` / 现有网络层
- 变量存储：`SharedPreferences` 与当前 source auth 流程
- JS sandbox：`LegadoSandbox`
- 规则实体：`LegadoBookSource`
- 兼容测试基线：`docs/reference/legado-adaptation-gap-analysis.md`

### 建议在 Kototoro 先形成的边界

#### `core/parser/legado/runtime/`

建议新增或整理：

- `LegadoRequestPlan`
- `LegadoHttpExecutor`
- `LegadoVariableStore`
- `LegadoCookieStore`
- `LegadoRuntimeContext`

#### `core/parser/legado/model/`

收敛纯运行时模型：

- source descriptor
- rule context
- response model

#### `core/parser/legado/bridge/`

收敛宿主实现：

- `KototoroLegadoHttpExecutor`
- `KototoroLegadoVariableStore`
- `KototoroLegadoCookieStore`
- `KototoroLegadoWebViewFetcher`

这样即使短期不真正拆仓，也能先把 Kototoro 内部结构对齐到未来可抽取的形态。

## 分阶段迁移顺序

### Phase 1: 先把 Kototoro 自己的运行时边界整理出来

目标：

- 不改行为
- 先把 HTTP、变量、Cookie、WebView、日志抽成接口

任务：

- 从 `LegadoRepository` 中抽出 `LegadoHttpExecutor`
- 从 `LegadoSandbox` / source auth 中抽出 `LegadoVariableStore`
- 从现有 Cookie 读写路径抽出 `LegadoCookieStore`
- 把 `AnalyzeUrl` 的网络执行逻辑迁出

完成标准：

- `AnalyzeUrl` 不再直接发请求
- `AnalyzeRule` 不再直接触碰宿主持久化

### Phase 2: 让 Kototoro 的规则核心只依赖运行时接口

目标：

- 让 `AnalyzeRule` / `AnalyzeUrl` 只依赖 runtime 抽象

任务：

- 引入 `LegadoRuntimeContext`
- 清理 `Book/Chapter/Source` 直接实体依赖
- 把规则执行上下文统一改为 DTO 或 Map 视图

完成标准：

- 核心解析逻辑可以脱离 Kototoro App 单例单测

### Phase 3: 在 Legado 侧验证最小抽取集

目标：

- 从 `../legado-with-MD3` 抽出一组最小 library 模块

任务：

- 先抽 `RuleAnalyzer`、`RuleData`、`AnalyzeBy*`
- 再抽 `AnalyzeRule` 核心部分
- 最后再考虑 `AnalyzeUrl` 的模板和请求计划部分

完成标准：

- library 模块不依赖 Legado `appDb`
- library 模块不依赖 Legado UI
- library 模块可由 Kototoro 以实现接口的形式接入

### Phase 4: 双侧行为对比与回归测试

目标：

- 避免抽取后行为漂移

任务：

- 基于 [legado-adaptation-gap-analysis.md](../reference/legado-adaptation-gap-analysis.md) 整理规则样例
- 建立 golden tests
- 用同一组样例对比：
  - Legado 原实现
  - Kototoro 当前实现
  - 抽出的共享 runtime

完成标准：

- 关键规则行为回归可自动发现

## 风险评估

### 风险 1：抽象过早，边界错误

表现：

- 一开始就为大量低频能力设计复杂接口

应对：

- 只覆盖 Kototoro 当前真实使用的规则与 API

### 风险 2：把 Legado 的应用级能力误认为运行时必需

表现：

- 把 Firebase、Room、UI、Cronet 一并带进 runtime

应对：

- 以“无 Android App 状态依赖”为硬约束做审查

### 风险 3：行为兼容性回退

表现：

- 抽取后规则语义和原始 Legado 再次漂移

应对：

- 先建 golden tests，再做重构

### 风险 4：Kototoro 现有在研改动冲突

表现：

- 直接改现有 `core/parser/legado/*` 文件，干扰当前兼容工作

应对：

- 第一阶段优先文档化和接口化
- 以增量方式引入新 runtime 包，不在一轮里重写全部实现

## 建议的近期执行项

优先级从高到低：

1. 在 Kototoro 中补一层 `runtime` 抽象目录，只做接口和模型，不做大改
2. 为当前 `AnalyzeRule` / `AnalyzeUrl` 建 golden tests
3. 从 `AnalyzeUrl` 中分离请求计划模型
4. 从当前 source auth / variable 逻辑中分离 `VariableStore`
5. 再决定是否同步改造 `../legado-with-MD3`

## 结论

“把 Legado runtime 打包进 Kototoro”这件事本身是可行的，但成立的前提不是直接消费现有 `app` 产物，而是先把它重构成一个最小、纯粹、宿主无关的规则运行时。

短期最稳的路线不是整包嵌入，而是：

- 先在 Kototoro 里对齐未来 runtime 边界
- 再从 Legado 提炼共享内核
- 最后用回归测试保障行为兼容

这条路径实现成本更高，但技术债更少，也更符合 Kototoro 当前已经在走的源码级兼容路线。
