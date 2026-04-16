# Compose Multiplatform 与 Liquid Glass 迁移路线

## 文档信息

- 创建日期：2026-04-16
- 最后更新：2026-04-16
- 状态：执行中
- 负责人：Codex / 仓库维护者协作

## 背景

Kototoro 当前已经在主壳、首页、发现页、详情页等区域引入了 Jetpack Compose，但整体 UI 仍然深度依赖 Android `Fragment`、`ViewBinding`、`PreferenceFragmentCompat`、`RecyclerView` 与平台专有能力。

本路线文档覆盖两件事情：

1. Android 端除阅读器、播放器外的 UI 全量 Compose 化。
2. 为后续 Compose Multiplatform 迁移建立稳定边界，并引入统一的 liquid glass 设计语言。

## 总体目标

### 目标

- 新增 UI 默认使用 Compose，不再继续扩展传统 View UI。
- 除阅读器、播放器外，Android 主流程页面逐步迁移到 Compose。
- 建立统一的玻璃设计系统，而不是在各页面直接散落第三方效果实现。
- 为后续 `commonMain` 共享 Screen、主题和状态模型打基础。

### 非目标

- 第一阶段不迁移阅读器与播放器到 Compose Multiplatform。
- 第一阶段不承诺 Web 平台。
- 第一阶段不强行将 WebView、认证流、通知、Widget、后台任务放入 `commonMain`。

## 当前现状摘要

### 已具备的基础

- `app` 已启用 Compose。
- 主壳已使用 Compose 渲染顶部与底部 chrome。
- 首页、发现页、详情页、书签/章节/分页局部已经存在 Compose Screen。

### 当前阻塞点

- 主容器依然通过 Compose 包裹 `FragmentContainerView` 承载旧页面。
- 设置系统仍依赖 `PreferenceFragmentCompat` 与 XML。
- 绝大多数弹层仍是 `DialogFragment` / `BottomSheetDialogFragment` / 自定义 `BaseAdaptiveSheet`。
- 浏览器认证、WebView、下载、通知、Widget、WorkManager 等仍然是 Android-only 能力。

## 迁移原则

### 架构原则

- 保持 KISS：优先最小正确改造，不一次性完成全模块重构。
- 保持 YAGNI：不预埋没有明确落地时间的平台抽象。
- 保持 DRY：玻璃效果、主题 token、设置项样式必须统一沉淀。
- 保持边界清晰：业务状态可以共享，平台能力必须显式桥接。

### UI 原则

- Liquid glass 只优先用于 chrome 区域：顶栏、底栏、sheet、dialog、关键按钮。
- 内容列表不做大面积实时玻璃与复杂折射。
- 玻璃效果必须支持按设备能力降级。
- 阅读器、播放器维持性能优先，不受本方案绑定。

## 模块演进方案

当前仓库仍是 Android 单模块主导，建议演进到以下结构：

```text
app/
shared/
  designsystem/
  foundation/
  feature-home/
  feature-discover/
  feature-details/
  feature-library/
  feature-history/
  feature-search/
  feature-settings/
platform/
  android-ui/
parser-api/
```

### `commonMain` 可承载内容

- Compose 主题与 design token
- 纯 Compose Screen
- `State / Event / Effect`
- 业务用例接口
- 资源语义层
- 导航意图模型

### 平台层保留内容

- WebView / 浏览器认证
- Activity Result / 权限
- WorkManager / Widget / 通知
- Room Android 实现
- 阅读器 / 播放器
- 文件、分享、系统集成

## 分阶段执行计划

### Phase 0：建立迁移基线

状态：部分完成

目标：

- 建立统一迁移文档。
- 将 liquid glass 设计系统抽象为可复用组件。
- 在现有 Compose 顶栏/底栏接入基础玻璃样式。

交付：

- 本文档
- 玻璃设计系统基础组件
- 首个接入点落地
- Kotlin 编译验证通过

### Phase 1：Settings 全量 Compose 化

状态：执行中

范围：

- `RootSettings`
- `Appearance`
- `StorageAndNetwork`
- `Services`
- `Backups`
- `Tracker`
- `Sources`

目标：

- 停止新增 `PreferenceFragmentCompat`
- 建立 Compose 设置 DSL：section、switch、slider、list、text input、warning card

当前进展：

- `RootSettingsFragment` 已切换为 Compose 入口页。
- `StorageAndNetworkSettingsFragment` 已切换为 Compose 页面，并修正 SSL bypass 提示仅在用户开启时触发。
- `AppearanceSettingsFragment` 已切换为 Compose 页面。
- `ServicesSettingsFragment` 已切换为 Compose 页面。
- `DownloadsSettingsFragment` 已切换为 Compose 页面。
- `TrackerSettingsFragment` 已切换为 Compose 页面。
- `SourcesSettingsFragment` 已切换为 Compose 页面。
- `BackupsSettingsFragment` 已切换为 Compose 页面。
- `NotificationSettingsLegacyFragment` 已切换为 Compose 页面。
- `JsonSourcesRootFragment` 已切换为 Compose 页面。
- `ExtensionsRootFragment` 已切换为 Compose 页面。
- 已建立设置页基础 DSL：section、entry row、hero card、switch、choice、text input、multi-choice、slider、split switch、info row，并补齐设置项 disabled / enabled-state 语义以及文本输入 placeholder / password 能力。
- 已建立通用 Compose tab-host，用于承载 Settings 下仍需保留的子 Fragment 宿主。
- 设置页二级导航仍复用现有 `SettingsActivity` 与旧 Fragment，保证迁移范围可控。
- `NavConfigFragment` 与应用保护设置流程暂时保留既有路由，但入口页本身已 Compose 化。
- `Services` 页中的 Sync、Suggestions、Discord 继续复用既有 Android 路由；追踪服务授权提示已改为 Compose `AlertDialog`，不再依赖 `PreferenceFragmentCompat`。
- `Downloads` 页中的目录选择、系统文档树授权与电池优化请求继续保留在 Fragment 层，页面渲染本身已完全 Compose 化。
- `Tracker` 页中的通知设置跳转、分类配置 sheet、电池优化请求和外部说明链接继续保留在 Fragment 层，页面渲染本身已完全 Compose 化。
- `Sources` 页中的 manage/catalog/json/extensions 子入口仍复用既有路由，但相关设置宿主层已经切换为 Compose。
- `Backups` 页中的文件选择、持久化授权、备份服务与恢复对话框继续保留在 Fragment 层，设置表单与状态展示改由 Compose Screen 承载。
- `Notification` 页中的系统铃声选择继续通过 `ActivityResultContract` 保留在 Fragment 层，通知开关与本地偏好设置改由 Compose Screen 承载。
- `Json Sources` 与 `Extensions` 页的旧 `TabLayout + ViewPager2` 宿主已收敛为 Compose tabs，但各自的业务子 Fragment 继续通过 `childFragmentManager` 复用；Settings 主页面与剩余设置宿主层现已全部 Compose 化。

### Phase 2：Main Shell 与导航壳收口

状态：执行中

范围：

- `MainActivity`
- 顶栏、底栏、搜索、筛选、弹层入口

目标：

- 新页面不再依赖 `FragmentContainerView` 作为主承载
- Compose 成为主导航壳

当前进展：

- `MainActivity` 已开始接管主壳内容避让，`FragmentContainerView` 的顶栏/底栏可见 inset 改由 Compose chrome 回传并统一注入 padding。
- `KototoroApp` 不再直接通过 `AndroidView` padding 驱动 Fragment 内容区位移，主壳边界开始向 Activity 侧收口。
- 浮动底栏场景下，Fragment 宿主不再依赖伪造的 child insets 间接避让，为后续统一导航壳奠定边界。
- Compose 顶栏搜索建议点击已补齐旧路由语义：`Content` 打开详情、`Tag` 进入标签搜索、`Source/SourceTip` 打开来源列表、`Author` 进入作者搜索。
- 主壳内容类型与来源筛选现已同步到 `SearchSuggestionViewModel`，普通搜索、最近搜索与提示词点击也会沿用当前筛选条件，并在简单搜索场景支持链接直达详情。

### Phase 3：高频内容页迁移

状态：未开始

优先级：

1. Home
2. Discover
3. History
4. Bookmarks
5. Favourites
6. Local
7. Search
8. Details

目标：

- 收敛成统一的 `State / Event / Effect`
- 把 Compose Screen 与平台桥接明确拆开

### Phase 4：Dialog / Sheet Compose 化

状态：未开始

范围：

- 筛选弹层
- 统计弹层
- 欢迎弹层
- 下载弹窗
- 导入弹窗
- 详情页章节/分页弹层

### Phase 5：共享层抽取

状态：未开始

目标：

- 抽取 `shared/designsystem`
- 抽取首批 feature module 到共享层
- 打通 Android 与下一个目标平台的最小壳

## 首批迁移清单

### 优先迁移

- 设置系统
- 主导航壳
- Home / Discover / History / Bookmarks / Favourites / Local / Search / Details

### 暂缓迁移

- 阅读器
- 播放器
- 浏览器 / 认证流
- Widget / WorkManager / 通知深度集成

## Liquid Glass 设计系统规范

### 组件层级

- `GlassSurface`
- `GlassTopBarContainer`
- `GlassBottomBarContainer`
- `GlassCard`
- `GlassSheet`

### 能力等级

- `Prominent`：更强的边框、阴影、透明度，给顶栏/底栏/重要浮层。
- `Regular`：默认玻璃容器，用于普通卡片与 section。
- `Subtle`：最轻玻璃，仅保留轻微半透明与边框。

### 实现约束

- 组件层封装第三方效果，不在业务页面直接依赖第三方 liquid 库 API。
- Android 上先允许使用轻量 fallback；后续再按平台能力接入更强实现。
- 页面只使用仓库内部的玻璃组件，不直接写散装透明 Surface。

## 执行记录

### 2026-04-16

- 新增本路线文档。
- 将 Phase 0 标记为部分完成。
- 建立 `GlassSurface / GlassTopBarContainer / GlassBottomBarContainer` 基础组件。
- 将主顶栏与底栏切换为统一玻璃容器承载。
- 执行 `./gradlew :app:compileDebugKotlin --no-daemon` 并通过。
- 执行 `npm run docs:build` 并通过。
- 启动 Phase 1。
- 将 `RootSettingsFragment` 从 `PreferenceFragmentCompat` 入口切换为 Compose 入口页。
- 新增设置入口 Compose 组件，作为后续设置页迁移骨架。
- 将 `StorageAndNetworkSettingsFragment` 迁移为 Compose 页面，并补齐网络/存储设置所需的 choice、switch、text input 组件。
- 修正 `StorageAndNetwork` 中 SSL bypass 提示的触发时机，避免页面初始渲染时误弹。
- 将 `AppearanceSettingsFragment` 迁移为 Compose 页面。
- 将 `ServicesSettingsFragment` 迁移为 Compose 页面，并补齐 split switch 与带图标 action row。
- 将 `DownloadsSettingsFragment` 迁移为 Compose 页面，并补齐 info row。
- 将 `TrackerSettingsFragment` 迁移为 Compose 页面，并为设置 DSL 补齐 disabled / enabled-state 语义。
- 将 `SourcesSettingsFragment` 迁移为 Compose 页面，并补齐 `jar_priority_order` 等偏好可写接口。
- 扩展设置 DSL，新增 multi-choice 与 slider 组件，覆盖 `Appearance` 页核心交互。
- 保留目录选择、文档树授权与电池优化跳转在 Fragment 平台层，Compose 页面仅负责 `Downloads` 设置渲染。
- 保留通知设置跳转、分类配置 sheet、电池优化请求与外部说明链接在 Fragment 平台层，Compose 页面仅负责 `Tracker` 设置渲染。
- 保留 manage/catalog/json/extensions 等来源管理子入口在既有路由层，Compose 页面仅负责 `Sources` 设置渲染。
- 保留 `NavConfigFragment` 与 `ProtectSetupActivity` 作为 Android 特有流程入口，Compose 页面仅负责状态展示与路由。
- 再次执行 `./gradlew :app:compileDebugKotlin --no-daemon` 并通过。
- 补齐 Compose 顶栏搜索建议点击链路，恢复与旧 `SearchSuggestionListenerImpl` 等价的主路由行为，并把主壳筛选状态同步到搜索建议 ViewModel。

## 下一步

1. 收口 Settings 残余 Android-only 子页，优先处理 `Backups` / `Notification legacy` / `JSON Sources` / `Extensions`。
2. 视需要补齐 Compose 设置 DSL 中尚未落地的 warning card / 更复杂输入模式。
3. 继续把玻璃组件扩展到 card、sheet 与 dialog。
