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
