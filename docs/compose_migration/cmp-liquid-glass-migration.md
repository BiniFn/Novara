# Compose Multiplatform 与 Liquid Glass 迁移路线

## 文档信息

- 创建日期：2026-04-16
- 最后更新：2026-04-17
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

状态：已完成

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
- Compose 顶栏过滤器现已对齐旧 Fragment 过滤语义：可见性、禁用态、来源入口列表以及来源按钮自定义点击行为都由当前页面回传给主壳统一渲染。
- Compose 顶栏查询状态现已由主壳统一持有，语音输入与手动输入、建议点击共用同一状态链路，避免 Compose 搜索栏与 Activity 回调脱节。
- Compose 顶栏已重新接入旧版的全局语言预设入口，并通过全局 `activeSourcePresetId` 状态反馈当前是否存在已启用的源预设。
- `SearchBarFilterViewController` 关联的 Fragment 现已在 `onDestroyView()` 主动释放回调，主壳不会继续持有已销毁页面的筛选语义，降低导航切换与重建时的状态残留风险。
- 主壳恢复链路已继续补强：稳定 `FragmentContainerView` 复用、fragment `detach/attach` 重挂、动态导航项菜单重建与选中态保留已接入第一轮修复，开始收敛旋转白屏、导航配置后空白页与双层 Home 叠层等问题。
- Compose 内容类型筛选按钮的默认单击目标已改回漫画，避免主壳筛选在未显式选择时默认跳到视频。
- **已完整卸载 Main Shell 的冗余 XML 布局：`MainActivity` 已移除所有对 `MainNavigationDelegate` 与 `FragmentContainerView` 的依赖。**
- **应用导航完全基于 Compose `NavHost` (`AppNavGraph.kt`) 重构。**
- **残留的老旧 Fragment 已通过 `FragmentHostRoute` 单层被透明兼容包壳。**

### Phase 3：高频内容页迁移

状态：执行中

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

当前进展：

- `HomeScreen` 已开始按 legacy 对齐规范收口：快捷入口改为自适应矩阵，列表模式在足够宽度下会把历史 / 更新 / 推荐三栏并排展示，窄屏继续维持纵向堆叠。
- Home 中 WebDAV 最近同步时间已由原始时间戳改为本地可读时间文本，避免 Compose 首页信息卡继续暴露迁移期占位实现。
- Home 已新增概览卡，接入继续阅读、收藏/分类统计、启用源数量与默认 tracking 站点等聚合信息，让首页 Compose 宿主开始完整消费 `HomeSummaryState` 的关键状态。
- `DiscoverScreen` 已为 tracking 首页接入首轮 Hero 轮播：从分类数据中提取顶部内容，补齐模糊背景联动、自动轮播、页间缩放 / 位移 / 轻微旋转过渡，开始借鉴 Dantotsu 的首页视觉结构。
- `DiscoverHeroCarousel` 已从单层大图推进为“双层 banner 氛围 + 前景 cover”的卡面结构，Compose 首页现已具备更明确的视觉焦点与顶部内容节奏。
- `DiscoverCarousel` 已补齐分类横向卡片滑动时的位移 / 缩放 / 透明度过渡；同时 `DiscoverScreen` 已修复 carousel 与 search grid 模式下 `EmptyState` 被过滤后的白屏问题。
- `DetailsHeader` 已接入收藏分类、译文标题/简介与来源 / 作者 / 标签动作，开始对齐 legacy metadata 卡片的真实信息与交互语义。
- `DetailsScreen` / `DetailsActivity` 已补齐分享、下载、来源跳转、作者 / 标签弹层与翻译切换动作，Compose 详情页头部不再停留在占位实现。
- `DetailsScreen` 顶栏 `More` 菜单已切换为 Compose overflow menu，补齐翻译、相似内容、在线版本、浏览器、追踪、统计与 NSFW 切换等旧菜单核心能力。
- `DetailsScreen` 底部阅读 dock 已开始对齐旧 split-button 语义：主按钮会跟随加载 / 继续阅读 / 播放状态变化，右侧分支段支持直接切换 `selectedBranch`。
- `DetailsActivity` 当前 Compose 阅读入口已回接章节缺失提示与分支透传，底部 dock 不再只是固定“阅读”按钮的静态占位。
- `DetailsScreen` 底部阅读 dock 已补齐旧 split-button 菜单中的无痕阅读、从历史移除、下载入口与分支菜单，Compose 端已覆盖旧 `popup_read` 的核心语义。
- `DetailsScreen` 顶栏 `More` 菜单已补齐本地删除、override 编辑与创建快捷方式，`DetailsActivity` 复用现有删除确认、override 返回刷新与快捷方式请求逻辑。
- `DetailsActivity` 已统一章节、页面、书签三个底部入口到内嵌 `ChaptersPagesSheet`，并按动态 tab 可用性映射索引。
- `ChaptersPagesSheet` 中 tab 切页现已改为在 Compose 协程上下文内驱动 `PagerState.animateScrollToPage()`，修复 `MonotonicFrameClock` 缺失导致的章节 / 页面 / 书签按钮闪退。
- `Home` 首页已进入第三轮 Dantotsu 化细节收口：顶部主卡收敛为 `History / Updates / Suggestions` 三标签 Hero，动态源名与来源胶囊已统一为可读信息，tracking Hero 进一步压缩并重新安放指示器 / 评分区域。
- `Details` 详情页已继续打磨：封面共享元素转场只保留单层过渡、章节 / 页面 / 书签改回 icon-only tabs、顶部折叠工具栏保留分享 / 下载 / 更多动作，基础信息卡与简介卡的 Compose 背景 token 也进一步统一。
- `Appearance` 页已补齐根背景容器，避免设置页透出旧 View 宿主底色。

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

### 2026-04-17

- 修复 `ChaptersPagesSheet` 中章节、页面、书签 Tab 切换崩溃：将 `PagerState.animateScrollToPage()` 的触发迁移到 Compose `rememberCoroutineScope()`，避免 `MonotonicFrameClock` 缺失。
- 为 `ChaptersPagesSheet` 增加动态 tab 到真实 pager 页的索引映射，裁剪不可用 tab 后仍能正确切页。
- 在 `DiscoverScreen` 顶部接入 tracking 首页 Hero 轮播，补齐模糊背景联动、自动翻页和页间过渡动画。
- 将 `DiscoverHeroCarousel` 从单层大图进一步推进到双层视觉结构，形成“背景 banner 氛围 + 前景浮动 cover”的首页 Hero。
- 为 `DiscoverCarousel` 分类行补齐位移 / 缩放 / 透明度动效，并修复 carousel / search grid 模式下 Compose 空态白屏。
- 本轮 Discover 视觉深化阶段受本地 Hilt/KSP 生成目录问题影响，额外以 `./gradlew :app:compileDebugKotlin --no-daemon -x kspDebugKotlin "-Pkotlin.incremental=false"` 完成 Kotlin 语法与类型校验。
- 解决 `KototoroTopBar` 和 `BottomNav` 以及各级 Compose Screen `WindowInsets` 丢失、状态栏和导航栏重叠的问题。
- 完成 `activity_main.xml` 架构重构：舍弃嵌套在 Compose `AndroidView` 内的 `FragmentContainerView`，改为在 XML 层面以 `NestedScrollBridgingFrameLayout` 作为根视图，将 `FragmentContainerView` 和 `ComposeView` 作为平行层叠（Sibling）视图。
- 移除 `KototoroApp.kt` 对内部 Legacy 容器渲染流程的干涉，将依赖反转回 `MainActivity` 驱动。
- 重构 Compose 嵌套滑动链路：剥离依赖层级的反向冒泡，改为在 `MainActivity` 捕捉 `NestedScrollBridgingFrameLayout` 的滑动事件并通过 `nestedScrollDeltaYFlow` 显式向 Compose UI（搜索栏、底栏）下发滚动偏移补偿量，实现丝滑联动的自适应避让表现。
- 修正沉浸式导航栏的 `padding` 应用时机和对象：利用原生 WindowInsets 分发机制确保 Legacy Fragment 层获得基于状态栏高度计算的实际安全边界，同时在 `isFloating` 状态下保持 Compose 层玻璃效果底栏正确的 UI 叠加透出行为。

## 下一步

1. 收口 Settings 残余 Android-only 子页，优先处理 `Backups` / `Notification legacy` / `JSON Sources` / `Extensions`。
2. 视需要补齐 Compose 设置 DSL 中尚未落地的 warning card / 更复杂输入模式。
3. 继续把玻璃组件扩展到 card、sheet 与 dialog。

### 2026-04-17
- 已完成 Phase 2 主导航壳与 MainActivity 余量 XML 卸载。
- `MainActivity` 彻底移除了 `FragmentContainerView` 和 `MainNavigationDelegate` 的深层依赖，架构演进到纯 Jetpack Compose NavHost。
- 新增 `FragmentHostRoute` 作为尚未迁移 Fragment 的无缝 Compose 包装器，确保迭代迁移的平滑进行。
