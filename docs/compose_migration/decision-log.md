# Compose 迁移：决策记录

> 本文件只记录**历史决策和事件**。不描述当前状态（见 `status-snapshot.md`），不描述未来计划（见 `task.md`）。

---

## 2026-04-16：启动迁移基线

- 新建迁移文档体系
- 确定迁移原则：KISS / YAGNI / DRY / 边界清晰
- 明确非目标：第一阶段不迁移阅读器/播放器、不承诺 Web、不强推 WebView/认证/通知/Widget 进 commonMain
- 建立 `GlassSurface / GlassTopBarContainer / GlassBottomBarContainer` 基础组件
- 将主顶栏与底栏切换为统一玻璃容器承载
- 开始 Settings Phase：`RootSettingsFragment` 从 `PreferenceFragmentCompat` 切到 Compose 入口
- 新增设置入口 DSL 骨架
- `StorageAndNetworkSettingsFragment` → Compose 页面（补齐 choice / switch / text input）
- `AppearanceSettingsFragment` → Compose 页面
- `ServicesSettingsFragment` → Compose 页面（补齐 split switch / action row）
- `DownloadsSettingsFragment` → Compose 页面（补齐 info row）
- `TrackerSettingsFragment` → Compose 页面（补齐 disabled / enabled-state 语义）
- `SourcesSettingsFragment` → Compose 页面
- 扩展设置 DSL：multi-choice / slider
- 保留目录选择 / 文档树授权 / 电池优化等在 Fragment 平台层
- 补齐 Compose 顶栏搜索建议点击链路

## 2026-04-17：主壳重构 + 详情页深化

### 主壳架构变更

- **决策**：从 XML `FragmentContainerView` + Compose 包裹模式切换为纯 Compose `NavHost` 架构
- `MainActivity` 移除 `MainNavigationDelegate` 和 `FragmentContainerView` 深层依赖
- 引入 `AppNavGraph.kt` 接管主导航（Home / Discover / History / Favorites / Explore / Feed / Local / Suggestions / Bookmarks / Updated）
- 引入 `FragmentHostRoute` 作为尚未迁移 Fragment 的透明兼容包壳
- 随后通过逐页迁移，将所有 `FragmentHostRoute` 引用清除（Feed / Local / Suggestions / Updates / Bookmarks / Explore / Favorites）

### 详情页进展

- `ChaptersPagesSheet` tab 切换修复：将 `PagerState.animateScrollToPage()` 迁移到 Compose 协程上下文，修复 `MonotonicFrameClock` 崩溃
- Discover 顶部接入 tracking Hero 轮播
- `DiscoverHeroCarousel` 升级为双层视觉结构
- `DiscoverCarousel` 补齐分类横向卡片动效
- `DetailsHeader` 接入收藏分类、译文标题/简介、来源/作者/标签动作
- `DetailsScreen` 补齐分享、下载、来源跳转、作者/标签弹层、翻译切换
- `DetailsScreen` More 菜单切换为 Compose overflow menu
- `DetailsScreen` 底部阅读 dock 对齐旧 split-button 语义
- `DetailsScreen` 补齐本地删除、override 编辑、创建快捷方式

### 主壳稳定性修复

- 稳定 `FragmentContainerView` 复用、fragment detach/attach 重挂
- 动态导航项菜单重建与选中态保留
- 收敛旋转白屏、导航配置后空白页、双层 Home 叠层问题
- 内容类型筛选默认改回漫画
- Fragment 销毁时释放筛选回调

### 其他

- Home 三标签 Hero 化、tracking Hero 压缩、源名胶囊统一
- Details 封面转场优化、icon-only tabs、折叠工具栏保留动作
- Appearance 页补齐根背景容器
- Settings 残余子页收口：Backups / Notification / JSON Sources / Extensions → Compose 渲染
- 通用 Compose tab-host 建立

## 2026-04-18：Dialog/Sheet Compose 化 + 高频页最终收口

- `ScrobblingSelectorDialog` (Compose 版) 编写完成
- `ContentStatsSheetContent` (Compose 版) 编写完成
- `DownloadDialog` Compose body 编写完成，`DownloadDialogFragment` 改造为轻量代理壳
- `DownloadsActivity` 迁移到 Compose `DownloadsScreen`
- `FavouritesContainerFragment` 重写为 `FavoritesHostScreen`
- `ExploreSourcesFragment` XML RecyclerView 重构为纯 Compose
- `ExploreFragment` 重写为 `ExploreHostScreen` (Compose HorizontalPager)
- `ContentListActivity` 迁移为 Compose 路由
- 所有 `FragmentHostRoute` 引用从代码中清除

## 2026-04-18：文档修正

- **发现搜索断链回归**：`MainActivity` 调用 `KototoroApp()` 时未传 `onQueryChanged` / `onSearch` / `suggestions`，导致主壳搜索功能失效
- **修复**：补齐三个参数传递，清理 `FragmentContainerView` 残留 import 和字段
- **文档重组**：将 5 份混合文档拆为 status-snapshot / decision-log / task 三文件结构
- **引入 L1-L4 迁移深度框架**，用于准确标注各组件真实迁移层级

## 2026-04-18（晚）：全量审查 + P0 修复 + Phase 2 首轮清理

### 审查

- 对 compose 分支全量代码与 `docs/compose_migration` 规划做对比审查，产出 `insights-2026-04-18.md`
- 明确 Android 端 Compose 覆盖约 70%，但存在多处"已迁移但旧路径未清理"的重影代码
- 发现 `9d40c7ac9 "more compose"` 提交无意间删除了 `onFirstStart()` 调用，导致首启必要初始化全部失效（后在本日修复）

### 第一轮：P0 Bug 修复 + 代码清理（`+232 / -2845`）

- 接回 `AppContentListRoute.onLoadMore`：新增参数并让 `FavoritesListScreen` 传 `viewModel::requestMoreItems`，恢复收藏夹分页
- 修复 `FavoritesHostScreen` 顶部 padding 双重应用：外层 Column 消费 top，传给内层列表的 `PaddingValues` 剥离 top
- 删除 `DetailsAction.OpenTracking/OpenStatistics` 在 `DetailsActivity` 端的死分支（仅做 `intent.putExtra` 无效动作）
- `HomeScreen.kt` 清掉 ~1900 行未引用的私有 composable（`HomeHeroSection` / `HomeUtilityHubCard` / `HomePulseCard` / `HomeCollectionSection` / `UtilitySection` / `TrackingSpotlightRow` / `ResumePanel` / `OverviewMetricCard` / `HeroFeatureArtwork` / `HeroPoster` / `HeroThumbnail` / `ContentLazyRow`/`Column`/`ListItem`/`CoverItem` 等）
- Home → History / Favorites / Local 导航改走 NavHost `navigate(...)`，不再绕 Activity
- `KototoroContentListScreen` 的 `"No content available"` 硬编码改用 `stringResource(R.string.nothing_found)`
- 去掉 `DiscoverViewModel.loadData` 的 `Log.d("DiscoverPagination", ...)` 生产日志
- `DetailsActivity` 分享元素转场：去掉硬编码 350 ms `postDelayed`，只保留图片加载回调 + 1200 ms fallback
- `KototoroTopBar.TopBarAnchorIconButton`：弃用 1×1 隐藏 `AndroidView`，改用 `LocalView` 拿 anchor

### 第二轮：Phase 2 清理 + 启动回归修复

- 删除 `download/ui/dialog/compose/DownloadDialog.kt`（622 行孤立副本，与 `download/ui/compose/DownloadDialog.kt` 功能重叠但零引用）
- 删除 `details/ui/DetailsMenuProvider.kt`（171 行，Compose 迁移后没人 `new` 过）
- 清理 `ReadButtonDelegate` 6 处 `Log.d/Log.w` 噪音
- 清理 `FavouritesContainerViewModel` 的 `println` 日志桥，保留无副作用的 `logImport/logSync` stub
- 清理 `MainActivity` 一堆空/透传方法：`selectMainNavigationItem`、`addMenuProvider` 覆写、`onSupportActionMode*`、`onFeedCounterChanged`、`onIncognitoModeChanged`、`onLoadingStateChanged`、`onResumeEnabledChanged`、`setNavFloating/setNavHeight`、`isNavFloating` 字段、`onRestoreInstanceState/onResume` 空桥、`pendingNavigationSyncAfterRestore` 死字段
- 清理 `HomeFragment` 对已删除 `selectMainNavigationItem` 的调用
- **修复 `onFirstStart` 回归**：恢复 `savedInstanceState == null` 时的调用链，重新启用 `LocalStorageCleanupWorker` / `ContentPrefetchService.prefetchLast` / `POST_NOTIFICATIONS` 权限请求 / `LocalIndexUpdateService` / `backupStartupCoordinator.startOnFirstLaunch` / `AdListUpdateService`

## 2026-04-18：主导航与 UI 架构重构

- **修复状态栏重叠**：修正 `ExploreHostScreen` 和 `FavoritesHostScreen` 根布局因未正确消耗 `contentPadding.calculateTopPadding()` 导致 TabRow 和状态栏重叠的问题。
- **全局内容类型筛选**：将内容筛选器（漫画/小说/动画）在 `appNavGraph` 范围内的默认可见性设为 `true`，使得在所有主页面搜索栏中都可见。
- **重构 Home 页面**：移除由单卡片承载多 Tab 的 `HomeHeroSection`。重构成分别对应“历史记录”、“最新更新”、“建议”三个独立且样式类似 tracking site feed 的 Hero Backdrop Card 轮播组件。
- **重构 Discover 与 Explore（合并发现页）**：废弃了独立的 `DiscoverScreen`，将发现模块的内容合并进了 `ExploreHostScreen` 中，打造了一个上层展示 `SourcesQuickAccessCard`（原 Explore 源横滑列表）、下层紧接 Tracking 系列卡片（原 Discover 横滑卡片）的统一纯粹发现页，路由指向全部重定向，从而精简了层级结构。

## 2026-04-18（夜）：Details 收口与后续优先级重排

- `ReadButtonDelegate` 抽出 `openDetailsReader(...)` 共享阅读入口，`DetailsActivity` 也改为复用同一逻辑，统一了 EPUB 历史修正、视频恢复播放、incognito、缺失章节提示等行为。
- `ChaptersPagesSheet` 的 `lastDetailsTab` 持久化从 pager index 改为语义 tab id，避免小说 / 视频等可见 tab 集变化时污染详情页默认 tab。
- 确认详情页章节 / 页面 / 书签主入口已经迁入 `DetailsScreen` 内部的 Compose `ModalBottomSheet`，`ChaptersPagesSheet` 不再是详情页主路径，而只保留给 Reader / Video 的通用 adaptive sheet 入口。
- 新一轮审查中发现：当前详情页 pane 的主要问题不是“未接入 Compose 内容”，而是 **sheet 内容测量 / 高度分配异常**，导致 toolbar 正常显示但 pager 内容区被压缩。
- 决策上不再把当前工作定义为单纯的“`ChaptersPagesSheet` 去壳”，而是升级为一次 **主导航过滤器一致性 + 详情页交互回归 + Browse/Home 视觉统一 + Home/Browse 信息架构收口** 的联合治理。
- 产品层结论：Home 与 Browse 不做物理合并，采用“弱合并 + 明确分工”——Home 保持个人仪表盘职责，Browse 聚焦发现入口与推荐首屏。

## 2026-04-18（夜·续）：详情页 pane 高度修复

- **根因**：`ChaptersPagesToolbar` 在 Chapters tab 下将 3 个 `FilterChip` 竖排 `Column` + `spacedBy(8.dp)`，toolbar 独占约 200dp；叠加 `ModalBottomSheet` 默认半展开（~50% 屏高），pager 内容区被挤到几乎不可见。
- **修复 1**：`ChaptersPagesToolbar` FilterChip 容器从竖排 `Column` 改为横排 `Row`，toolbar 高度从 ~200dp 降至 ~48dp。
- **修复 2**：`DetailsScreen` 的 `ModalBottomSheet` 添加 `rememberModalBottomSheetState(skipPartiallyExpanded = true)`，确保 sheet 直接全展开，给予内容区最大空间。
- `./gradlew :app:compileDebugKotlin --no-daemon` 通过。

## 2026-04-18（夜·续²）：收藏页 grid 错位修复

- **根因**：`KototoroContentListScreen` 在 `GRID` 模式下，`LazyVerticalGrid.items(items)` 给每个 `ListModel` 分配 1 个 grid cell。非 `ContentGridModel` 项（如 header / divider）渲染为空但仍占位，将后续 grid 卡片挤到下一列。
- **修复**：为 `items()` 添加 `span` 参数——`ContentGridModel` 占 1 cell，其余类型占 `maxLineSpan`（满行），避免空 cell 错位。`onLoadMore` 触发逻辑保持不变。
- `./gradlew :app:compileDebugKotlin --no-daemon` 通过。

## 2026-04-18（夜·续³）：搜索栏过滤器一致性修复

- **根因**：`MainActivity.isSourceTagFilterVisible` 初始值为 `false`，`clearActiveFilters()` 重置时也设为 `false`。当从有 filter callback 的页面（Favorites / Suggestions / Updated）导航到 Home / Explore / Feed / History 时，旧 callback 被清理后 source tag 按钮消失，只剩 content type 按钮，用户感知为"按钮缺失"。
- **决策**：统一默认显示所有过滤器按钮——将 `isSourceTagFilterVisible` 初始值和 `clearActiveFilters()` 中的值均改为 `true`。Source tag 在无 callback 时仅影响搜索建议过滤，可安全展示。
- 改动文件：`MainActivity.kt`（字段初始值 + `clearActiveFilters()`）

## 2026-04-18（夜·续⁴）：空封面 Hero/Backdrop 白块修复

- **根因**：`AsyncImage(model = null)` 或加载失败时不渲染任何内容，暴露 `HeroBackdropCard` 的 `containerColor`（`surface.copy(alpha = 0.18f)`），在浅色主题下表现为白块。各 poster 缩略图的 clip Box 也无兜底色。
- **修复 1**：`HeroBackdropCard` 在 `background()` 前新增一层 `surfaceContainerHighest → surfaceContainerLow` 线性渐变 Box，作为全局兜底。
- **修复 2**：`HomeScreen.kt` 的 `ContentCarouselPoster`、`TrackingHeroPoster` 和 `ExploreHostScreen.kt` 的 `TrackingCategoryPoster` 的 poster clip Box 添加 `.background(surfaceVariant)`。
- `./gradlew :app:compileDebugKotlin --no-daemon` 通过。**Phase 1 全部完成。**

## 2026-04-19：Phase 2 Browse / Home 视觉升级

### 任务 6：源入口改 FlowRow 紧凑网格

- `ExploreHostScreen.SourcesQuickAccessCard`：`LazyRow` → `FlowRow` chip 风格，单项从竖排 Column(icon 28dp + text, 80dp 宽) 改为横排 Row(icon 16dp + text, wrap)
- 项间距从 8dp 改为 6dp（水平 + 垂直），最多展示 20 个源

### 任务 7：追踪卡片改信息密集样式

- `ExploreHostScreen.TrackingCategoryCarouselCard`：`HorizontalPager` → `LazyRow`，移除自动翻页和 pager 指示器
- 海报从 Row(poster + info 右侧) 改为 Column(poster 72×100dp + 居中标题)，卡片高度从固定 184dp 改为 wrapContent
- 背景图固定用第一项 coverUrl，不再随 pager 切换

### 任务 5：Browse Hero 重做

- `DiscoverHeroCarousel` 整体重写：`HeroBackdropCard` 从 470dp/RoundedCornerShape(28dp) 改为 280dp/RoundedCornerShape(0dp)（边到边）
- 每页内容从大海报卡片 `DiscoverHeroCard`（148~212dp 海报 + 底部叠层信息）改为紧凑 Row(poster 100×140dp + Column(title + source pill))
- 删除 `DiscoverHeroCard` composable（~120 行），保留 `DiscoverHeroPill`
- 保留 `HeroAutoAdvanceEffect`、背景动画（缩放/平移/模糊 Crossfade）和 `HeroPagerIndicator`
- `ExploreHostScreen` 中 Hero 从 sources_card 之后移至 LazyColumn 首项
- `ExploreHostScreen` LazyColumn 水平 contentPadding 从全局 12dp 改为各非 Hero 项独立 12dp，使 Hero 边到边

### 任务 8：Home 快捷入口视觉统一

- `DashboardCard`：`GlassSurface(GlassDefaults.regularStyle(), 24dp)` → `Surface(surfaceContainerLow, 26dp)`
- `QuickActionsCard`：移除 subtitle 文本，`FlowRow` 间距 10dp → 8dp
- `QuickAccessButton`：移除 `BorderStroke`，颜色从 `GlassDefaults.nestedCardColor()` 改为 `surfaceContainer.copy(alpha=0.6f)`，移除 `CircleShape` 图标容器，图标直接渲染
- 清理 `GlassDefaults`、`GlassSurface`、`CircleShape` 未使用 import

### 编译验证

- 所有四个任务均通过 `./gradlew :app:compileDebugKotlin --no-daemon`。**Phase 2 全部完成。**


## 2026-04-19：Details / Tracking 统一体验进入实现期

- 用户明确要求：**不要推倒重写现有绑定机制，而是在原生已有 tracking 绑定实现基础上继续增强**，目标是“无论是原生作品详情页还是追踪作品详情页，体验上完全一致，并且可以方便绑定和跳转”。
- 初始方案曾倾向于“先让 tracking 详情页仅在视觉上向普通详情页靠拢”，但用户随后进一步明确：**追踪网站作品详情页既然需要和普通作品详情页一致，那就必须 Compose 化**。这使得 tracking details 的目标从“样式对齐”升级为“宿主与交互模型一并迁移”。
- 为减少双端实现分叉，开始引入共享绑定展示层：新增 `LinkedTrackingItemUiModel` 与 `DetailsBindingCard`，作为普通详情页与 tracking 详情页共同复用的绑定信息卡片基础。
- 普通详情页侧开始接入两类新能力：
  - 观察当前内容已绑定的 tracking 项并渲染“已绑定 tracking 卡片”；
  - 在详情 header 内显示“推荐绑定卡片”，并打通打开 tracking 详情、进入绑定管理、执行绑定/解绑等动作。
- tracking 详情页侧开始 Compose 化：`TrackingSiteDetailsActivity` 被改造成 Compose host，新增 `activity_tracking_site_details.xml` 与 `TrackingSiteDetailsScreen.kt`，并在 `TrackingSiteDetailsViewModel` 暴露 `linkedTrackingItem` 以支持共享卡片渲染。
- 当前结论：
  1. **共享绑定卡片** 是 details/tracking 双页统一体验的基础；
  2. **tracking details Compose 化** 是用户明确指定的必须项，不再接受停留在 Fragment/XML 终态；
  3. 自动推荐绑定逻辑仍应继续建立在既有 `TrackingSiteMatcher` / cache / DAO 上扩展，而不是另起一套新机制；
  4. 在文档与交接中必须明确记录：目前该批改动仍处于 working tree 中，且尚有编译阻塞未清完，不能误记为已完成。

## 2026-04-19（夜）：Settings 脚手架提权决议

- **事实背景**：虽然目前大部分设置页面的 UI body 已经切换为 Compose 渲染（L1 迁移），但仍极度依赖 `BasePreferenceFragment`、XML 声明的 Preference Hierarchy 和 `androidx.preference` 等全套包袱，导致大量中间桥接层存在，阻碍了后续更深层次的 Compose/多平台融合。
- **决议内容**：采纳构建统一的**纯 Compose Settings Scaffold（设置脚手架）**方案，将其设为下一步高优先级任务（新增 Phase 4）。
- **预期成果**：最终通过搭建基础 UI 层、数据 Flow 同步层与 DSL 声明层，将完全铲除全仓库的 `preference_*.xml` 资源，并全面删除相关 AndroidX Preference 及旧 Fragment/Activity 绑定逻辑。这不但可以解决目前状态散乱的问题，还将显著削减 APK 布局打包并加速构建体验。

## 2026-04-19（续）：Phase 4 首轮执行 — Tracker Compose 化 + AI/翻译设置迁移

### Tracker Details Compose 化（已完成）

- `TrackingSiteDetailsActivity` 从 `BaseActivity<ActivityTrackingSiteDetailsBinding>` 改为 `AppCompatActivity` + `setContent {}`，彻底移除 XML layout 依赖
- `TrackingSiteDetailsScreen` 注入 `collapseProgress` 滚动折叠算法、panorama blur 背景、`graphicsLayer` 偏移动画，与 `DetailsScreen` 风格一致
- `TrackingSiteDetailsFragment` 改为纯 `ComposeView` 宿主
- 删除 `activity_tracking_site_details.xml`
- 之前记录的编译阻塞（`router` import、`DetailsBindingCard` unresolved 等）已全部修复

### AI/翻译设置 Compose 迁移（已完成）

- **新增 4 个 Compose Screen**：
  - `AISettingsScreen` — 纯路由页，使用 `SettingsActionPreference` 导航到各子设置
  - `TranslationSettingsScreen` — 完整 Compose 化，含 General/OCR/Bubble 三个 section，动态显隐基于 pipeline mode
  - `TranslationApiSettingsScreen` — 含 provider preset、endpoint/key/model text input、fetch models action
  - `AIImageEnhancementSettingsScreen` — 含 SR 开关、engine/mode/model 选择、cache 管理
- **改造 4 个 Fragment**：`AISettingsFragment`、`TranslationSettingsFragment`、`TranslationApiSettingsFragment`、`AIImageEnhancementSettingsFragment` 从 `BasePreferenceFragment` 改为 `Fragment` + `ComposeView` 宿主
- **删除 4 个 XML**：`pref_ai.xml`、`pref_translation.xml`、`pref_translation_api.xml`、`pref_ai_image.xml`
- **SettingsSearchHelper** 对应的 `inflateTo(R.xml.pref_*)` 调用替换为手工 key lists（`pref_ai_video` 保留）

### 设计决策

1. **Fragment 层仍保留**：虽然 Screen 已 Compose 化，但 Fragment 宿主仍保留以兼容 `SettingsActivity` 的 Fragment 导航架构。完全去 Fragment 化需等 `SettingsActivity` 整体重构。
2. **模型选项列表在 Fragment 构建**：`TranslationSettingsFragment` 中 ONNX 模型选项列表在 `onViewCreated` 中通过 `OnnxModelManager` 构建后传入 Compose Screen，而非在 Compose 中直接注入 Manager（避免 Compose 层对 Hilt 注入的直接依赖）。
3. **数据绑定模式**：采用 `settings.observeAsState(KEY) { getter }` + `settings.prefs.edit { putX(KEY, value) }` 模式，与已有的 Reader/Playback 设置 Screen 保持一致。

## 2026-04-19（续²）：Settings Batch 2 — TTS / OCR Models 收口

- `TtsSettingsFragment` 从 `BasePreferenceFragment + pref_tts_settings.xml` 改为 `Fragment + ComposeView` 宿主，并新增 `TtsSettingsScreen`
- TTS 页面保留了原有能力边界：
  - 系统 TTS 异步初始化与语音枚举
  - OEM 设备 `availableLanguages` 回退
  - Legado JSON 从剪贴板 / URL 导入
  - 已导入音源批量删除
  - 本地 / Legado 引擎试听
- `OcrModelsFragment` 从运行时构造 `PreferenceScreen` 改为 `Fragment + ComposeView` 宿主，并新增 `OcrModelsSettingsScreen`
- OCR Models 页面现在以分组列表承载 ONNX 翻译 / OCR / 气泡检测 / 超分模型，支持：
  - 已下载 / 未下载状态展示
  - 下载过程中的进度摘要
  - 已下载模型删除确认
- 为减少 Settings DSL 分叉，`SettingsActionPreference` 增加了 `showChevron` 开关，允许“动作行”复用同一组件而不强行表现成跳转
- `AISettingsScreen` 现已接入 TTS 资源化标题与摘要，AI 子树入口文案不再继续硬编码
- `./gradlew :app:compileDebugKotlin --no-daemon` 已通过
- 用户已明确确认后，`pref_tts_settings.xml` 已物理删除
- 随后继续收口了 `End-to-End API` 分支：
  - `AISettingsScreen` / `TranslationSettingsScreen` 的 E2E 入口标题与摘要改为资源化
  - `TranslationE2ESettingsScreen` 去掉硬编码文案，并切回 `observeAsState + SharedPreferences.edit` 绑定模式
  - 修复 `reader_e2e_api_concurrency` 持久化类型错误：从错误的 `putInt(...)` 改为与 `AppSettings` getter 一致的字符串存储
  - `SettingsSearchHelper` 删除失效的 `PreferenceScreen.inflateTo` 桥接，并补齐 TTS / OCR Models / End-to-End API 的索引项

## 2026-04-19（续³）：Settings 深层源页去 BasePreferenceFragment

- **目标变化**：在继续推进 Settings Compose 化前，先清掉设置系统中最后一个真实继承 `BasePreferenceFragment` 的页面，避免公共基类继续绑住迁移边界。
- **实现策略**：不做一次性全 Compose 重写，采用低风险过渡路径，让 `SourceSettingsFragment` 先从 `BasePreferenceFragment` 改为直接继承 `PreferenceFragmentCompat`，保留现有动态 `Preference` 生成、Mihon/Aniyomi 扩展配置注入、JS/Legado/TVBox 特殊项等业务逻辑。
- **迁移内容**：
  - 在 `SourceSettingsFragment` 内联原来由 `BasePreferenceFragment` 提供的最小能力集：`exceptionResolver` 注入、`SettingsActivity.setSectionTitle(...)` 标题同步、window insets/padding 处理、`ARG_PREF_KEY` 搜索定位与高亮。
  - 保持 `SnackbarErrorObserver(listView, this, exceptionResolver)` 与 `ReversibleActionObserver(listView)` 现有交互路径不变，避免牵连 source 设置业务。
- **结果**：
  1. Settings 系统里已无页面直接继承 `BasePreferenceFragment`。
  2. `SourceSettingsFragment` 仍是旧 `PreferenceFragmentCompat` 动态树，而不是 Compose Screen；这次只完成“去公共基类依赖”，未完成“深层源设置 Compose 化”。
  3. `./gradlew :app:compileDebugKotlin --no-daemon` 已通过，说明该过渡没有破坏现有设置页编译基线。

## 2026-04-19（续⁴）：SourceSettings 分流到首批 Compose 路径

- **问题拆解**：深层 SourceSettings 全量 Compose 化的真实难点不在标准 parser 源，而在 Mihon / Aniyomi 的 `setupPreferenceScreen(screen)` 外部注入，以及 JS / Legado / TVBox 的高耦合动态偏好构造。一次性重写整页风险过高。
- **实现策略**：新增 `SourceSettingsHostFragment` 作为路由分流宿主，先将标准 `ParserContentRepository` / `KotatsuParserRepository` / `EmptyContentRepository` 切到新的 `SourceComposeSettingsFragment + SourceSettingsScreen`，其它复杂来源继续自动回退旧 `SourceSettingsFragment`。
- **新增能力**：
  - 新增 `SourceSettingsScreen` 与 `SourceSettings*RowUiState`，为 source 设置页补上通用的 Compose DSL。
  - 在 `SettingsPreferenceComponents` 新增 `SettingsDialogTextPreference`，用于 domain / user-agent / 账号密码等对话式文本输入。
  - 标准 parser/kotatsu Compose 页已覆盖：启用开关、验证码通知、限速、配置项（domain/text/user-agent/toggle/list）、认证、浏览器打开、清理 cookies、unsupported 占位提示。
- **显式保留**：JS / Legado / TVBox / Mihon / Aniyomi 仍走 legacy 页面；这是有意的增量分治，不是遗漏。
- **验证**：`./gradlew :app:compileDebugKotlin --no-daemon` 已通过。

## 2026-04-19（续⁵）：主壳真实 Haze 启用 + 搜索栏厚度收紧

- **Haze 接线**：
  - `KototoroApp` 在主壳根层创建并提供 `HazeState`
  - `AppNavGraph` 主内容层增加 `Modifier.haze(hazeState)` 作为采样源
  - `GlassSurface` 改为 `Surface + Modifier.hazeChild(...)`，保留边框与 tint fallback，避免无背景时直接透明失真
- **顶栏策略**：
  - 主壳顶栏不再使用普通 `Surface` 包裹，改为 `GlassSurface + DockedSearchBar`
  - 搜索栏厚度从默认 Material3 规格收紧为 collapsed `48dp` / expanded `52dp`
  - 顶栏 action button、语言按钮、来源按钮、内容类型筛选 chip 统一压到 `40dp` 级别，直接针对用户反馈的“太厚”问题，而不是改顶部位置
- **验证**：`./gradlew :app:compileDebugKotlin --no-daemon` 已通过。

## 2026-04-19（续⁶）：Haze 进入可配置阶段 + Tracking Compose 崩溃修复

- **外观设置页补强**：
  - `AppSettings` 新增 `hazeOpacityPercent / KEY_HAZE_OPACITY`
  - `AppearanceSettingsScreen` / `AppearanceSettingsFragment` 增加 Haze 模糊风格与玻璃不透明度入口
  - `SettingsSearchHelper` 补入 `haze_opacity`
  - `pref_blur_mode*` 文案改写为 Haze 语义，而不再沿用模糊等级的旧 View 时代描述
- **Compose 主题决议**：
  - `KototoroTheme` 不再依赖默认 `lightColorScheme()/darkColorScheme()` 静态方案
  - 改为根据当前 Activity 已应用的主题 overlay 动态解析 `colorSurface` / `colorPrimary` / `colorSurfaceContainer*` / `colorError` 等属性名，构造 Compose `ColorScheme`
  - 这样“设置 → 外观”中的配色主题、夜间模式、AMOLED 等旧主题设置终于能真实影响 Compose UI
- **Tracking Compose 闪退根因确认**：
  - 根因并非 DropdownMenu 逻辑，而是 `ScrobblerService.iconResId` 中存在 bitmap-wrapper XML（如 `ic_shikimori.xml`），Compose `painterResource()` 无法直接加载
  - 新增 `rememberSafePainter()` 基础设施，通过 `ContextCompat.getDrawable()` 统一兼容 VectorDrawable 与 bitmap-wrapped xml
  - `DiscoverHeroCarousel`、`ScrobblingSelectorDialog`、详情页 tracking 绑定卡片与 badge 已切到安全 painter 路径
  - 用户报告的 MangaUpdates 站点切换闪退因此被纳入同一类问题收口
- **详情页 glass 收口决议**：
  - `DetailsScreen` 根层增加 `HazeState`
  - 底部工具栏、顶部圆按钮、header badge 和 tracking 绑定卡片的图标入口统一切入真实 glass / haze 渲染路径
- **Browse Hero 衔接**：
  - `DiscoverHeroCarousel` 补强底部渐变并对背景图增加 crossfade
  - `ExploreHostScreen` 的 sources 区块向上压接 Hero，减少 Hero 底部与 sources 区之间的割裂带
- **验证**：`./gradlew :app:compileDebugKotlin --no-daemon` 已通过（Kotlin daemon 因缓存目录删除失败回退到非 daemon 编译，但最终构建成功）。

## 2026-04-19（续⁷）：Browse Hero 连续过渡二次收口

- **背景切换策略调整**：
  - `DiscoverHeroCarousel` 不再同时叠加 Compose `Crossfade` 和 Coil `crossfade(true)` 处理同一层背景图，避免切换完成后再出现第二次视觉断层
  - 改为保留单层 Compose `Crossfade`，并在 Hero 根层增加持续存在的兜底底色与 scrim
- **过渡层连续化**：
  - Hero 根层新增 `surfaceContainerHighest → background` 的常驻底色，避免封面加载/切换过程中露出不稳定底层
  - 底部 blend 高度继续加深，sources 区块背景改为 `Transparent → background` 连续渐变，并进一步向上压接 Hero
- **现阶段结论**：
  - 本轮修复目标是消除“轮播切换最开始正常、随后又出现割裂”的结构性诱因
  - 是否完全消除设备端割裂仍需真机验收，暂不记为 Browse Hero 全部完成

## 2026-04-19（续⁸）：Browse 源入口改 favicon 方卡 + Details Haze 降级

- **Browse 顶部控件重排**：
  - `DiscoverHeroCarousel` 移除右上角多余的 “more” 按钮
  - 追踪站点切换按钮提升到右上角，Hero 标题留在同一行左侧
- **内容源入口决议**：
  - `ExploreHostScreen` 的 sources 区块从横向文字 chip 改为更接近方形的统一卡片，使用 “图标在上、标题在下” 的结构
  - 图标来源改走 `faviconUri()` 与 `sourceFallbackImage(...)`，不再统一显示 storage 占位图标
  - 右上角动作固定进入 `openManageSources()`，不再根据 `isAllSourcesEnabled` 条件跳去 `openSourcesCatalog()`
- **详情页稳定性决议**：
  - 用户真机反馈普通详情页进入即在 `dev.chrisbanes.haze.HazeNode.draw` 链路崩溃
  - 当前采用保守策略：`DetailsScreen`、主壳 `AppNavGraph` 的 `.haze(...)` 和 `GlassSurface.hazeChild(...)` 仅在 **Android 12+** 启用；旧系统自动退回普通 glass tint fallback，优先保证稳定性
