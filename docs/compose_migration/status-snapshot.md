# Compose 迁移：当前状态快照

> 最后校对日期：2026-04-19
>
> 本文件只描述**此刻代码的事实状态**。不包含历史决策、未来计划。
> 所有判断均来自对 `main` 分支代码文件的直接审查。

## 迁移深度定义

| 深度 | 定义 | 标准 |
|------|------|------|
| **L1** | Compose UI body | 渲染层用 Compose，但 host 仍是 Fragment / DialogFragment / BaseAdaptiveSheet |
| **L2** | Compose route + ViewModel 边界清楚 | Activity `setContent` 或 NavHost composable，ViewModel 仅暴露 StateFlow/SharedFlow |
| **L3** | 移除 Fragment/Dialog host | 无 Fragment、无 DialogFragment、无 ViewBinding 残留 |
| **L4** | 平台能力抽象，可讨论 commonMain | 状态/渲染层无 Android import，平台能力通过 expect/actual 桥接 |

---

## 整体进度概览

- **Android 端 Compose UI 迁移进度**：约 70%
- **面向 CMP/commonMain 的工程准备度**：< 20%
- 仓库根目录无 `shared/` / `commonMain` / `feature-*` 模块结构

---

## 主壳 / 导航

### MainActivity + KototoroApp — **L2**

| 方面 | 状态 |
|------|------|
| 主布局 | `activity_main.xml` 仍是单个 `ComposeView`（被 `setContentViewWebViewSafe` 托管） |
| 导航 | `KototoroApp.kt` → `AppNavGraph.kt` → Compose `NavHost` |
| 旧导航 | `MainNavigationDelegate` 已删除 |
| `FragmentHostRoute` | 已移除 |
| 搜索 | `onQueryChanged` / `onSearch` / `suggestions` 已接回 |
| MainActivity 残留 | ~~`onSupportActionMode*` / `onFeedCounterChanged` / `onLoadingStateChanged` / `onResumeEnabledChanged` / `setNavFloating/setNavHeight` / `pendingNavigationSyncAfterRestore` / `selectMainNavigationItem` / `addMenuProvider` 透传~~（**已于本日清理**） |
| `onFirstStart()` 回归 | ~~首启初始化 `onFirstStart()` 被误删~~（**已于本日恢复**），重新启用 `LocalStorageCleanupWorker` / `ContentPrefetchService` / `POST_NOTIFICATIONS` / `LocalIndexUpdateService` / `backupStartupCoordinator` / `AdListUpdateService` |
| 顶栏 anchor | `TopBarAnchorIconButton` 改用 `LocalView.current`，不再塞 1×1 隐藏 `AndroidView` |

**降级原因**：主壳 Compose 导航已落地，但仍通过 `ActivityMainBinding` 做 ViewBinding 启动（`setContentViewWebViewSafe`），未直接 `setContent {}`。

---

## 设置系统

### Settings 页面渲染 — **L1**

| 方面 | 状态 |
|------|------|
| Root 入口 | `RootSettingsFragment` 承载 Compose 入口页 |
| 二级页面 | Appearance / StorageAndNetwork / Services / Downloads / Tracker / Sources / Backups / Notification / JsonSources / Extensions 均已 Compose 渲染 |
| 导航 | 仍复用 `SettingsActivity` + Fragment 跳转 |
| 搜索索引 | `SettingsSearchHelper` 仍直接解析 28 个 `pref_*.xml` 作为搜索元数据源 |
| 子页宿主 | `SettingsTabbedFragmentsScreen` 仍用 `AndroidView + FragmentContainerView` 承载子 Fragment |
| Warning card | 设置 DSL 中承诺但**未实现** |

**关键事实**：渲染层已 Compose 化，但语义模型（搜索索引、配置定义）仍绑定老 Preference XML 体系。

---

## 高频内容页

### Home — **L2**

- 通过 Compose NavHost 路由（`"home"` → `HomeScreen`）
- 三个 `HomeContentCarouselCard`（历史/更新/推荐）+ `HomeTrackingHeroSection` + `QuickActionsCard`
- Home → History / Favorites / Local 导航走 `navController.navigate`，不再绕 Activity
- `HomeScreen.kt` 已清掉 ~1900 行未引用死代码，仅保留主体 ~650 行
- **Phase 2**：`QuickActionsCard` 从 `GlassSurface` 改为 `Surface(surfaceContainerLow)`，移除 subtitle、button border 和 CircleShape icon 容器，FlowRow 间距收紧至 8dp

### Discover (Merged into Explore)

独立 `DiscoverScreen` 已移除。`"discover"` / `"explore"` 路由都映射到 `KototoroExploreHostRoute`。

### History — **L2**

已通过 Compose NavHost 路由。

### Favorites — **L2**

- 通过 Compose NavHost 路由，`FavouritesActivity` 已用 `setContent` 包 Compose route
- `FavouritesContainerFragment` 已重写为 `FavoritesHostScreen`
- 顶部 padding 双重应用 bug（tab 下方多余空白）已修复：host 消费 top，列表只收 bottom + horizontal
- 分页加载已接回：`AppContentListRoute` 暴露 `onLoadMore` 参数，`FavoritesListScreen` 传 `viewModel::requestMoreItems`

### Explore — **L2**

- `ExploreHostScreen` 为统一发现页：Hero 轮播置顶 + Sources FlowRow 快捷卡 + 追踪类别 LazyRow 紧凑卡片
- 与旧 Discover 的 Hero / Carousel 结构合并
- **Phase 2**：`DiscoverHeroCarousel` 从 470dp 大卡重写为 280dp 边到边紧凑布局（poster 100×140dp + 信息列）；`SourcesQuickAccessCard` 从 LazyRow 改为 FlowRow chip 风格；`TrackingCategoryCarouselCard` 从 HorizontalPager 改为 LazyRow + 小封面列表

### Feed — **L2**

`FeedFragment → FeedScreen` 已完成。

### Local — **L2**

通过 Compose NavHost 路由。

### Downloads — **L2**

`DownloadsActivity` 使用 `setContent`。

### ContentList / Search — **L2**

`ContentListActivity` 已迁移为 Compose 路由。

### Details — **L1**

| 方面 | 状态 |
|------|------|
| Activity | `DetailsActivity` 仍使用 `ActivityDetailsBinding` |
| Compose 接入 | 通过 `viewBinding.composeView?.setContent { DetailsScreen(...) }` |
| 头部 / 操作 | Compose `DetailsScreen` 完成 |
| 详情页 pane 宿主 | `DetailsScreen` 已在 Compose 内使用 `ModalBottomSheet` 打开章节 / 页面 / 书签 pane |
| pane 内容 | `ChaptersPagesTabsContent` 已承载 Chapters / Pages / Bookmarks 与 Compose toolbar |
| 通用 adaptive sheet | `ChaptersPagesSheet` 仍保留给 Reader / Video 入口，不再应视为详情页的主 pane 宿主 |
| 阅读入口 | `DetailsActivity` 与 `ReadButtonDelegate` 已共用 `openDetailsReader(...)`，EPUB 历史修正 / 视频恢复播放 / incognito 逻辑已收敛 |
| tab 记忆 | `ChaptersPagesSheet` 已改为持久化语义 tab id（chapters / pages / bookmarks），避免把 pager index 写回 `lastDetailsTab` |
| 当前回归 | ~~详情页 Compose pane 内容区高度分配异常~~（**已修复**）：`ChaptersPagesToolbar` FilterChip 改为横排 `Row`，`ModalBottomSheet` 加 `skipPartiallyExpanded = true` |
| 转场 | 依赖 XML `imageViewCover` 做共享元素转场；**已去除 350 ms 硬编码启动**，仅保留图片加载回调 + 1200 ms fallback |
| 删除确认 | Compose `AlertDialog` |
| `DetailsMenuProvider` | 已删除（171 行未被引用） |
| `OpenTracking/OpenStatistics` Action | Activity 端死分支已删除，仅由 Compose 内 dialog state 驱动 |

**降级原因**：仍是 XML Activity 壳 + ComposeView 混合宿主；虽然详情页 pane 已进入 Compose，但 Activity / 转场 / 平台桥仍未去壳。

### 当前已识别的 UI 回归（待修）

- 主页面搜索栏过滤器接线不一致：`KototoroTopBar` 已支持语言预设 / 内容类型 / 源类型，但只有部分页面正确桥接了 `SearchBarFilterViewController`
- ~~收藏页 grid 模式下，单个内容卡片可能被前置非 `ContentGridModel` 项挤到第二列~~（**已修复**：`LazyVerticalGrid` 的 `items` 添加 `span` 参数，非 `ContentGridModel` 项占满整行）
- Home / Browse / Discover 的 hero/backdrop 在封面为空时仍可能出现割裂的白色矩形占位

---

## Dialog / Sheet

| 组件 | 深度 | 状态 |
|------|------|------|
| 详情页作者/标签搜索弹窗 | **L3** | 纯 Compose `AlertDialog` |
| 详情页分享选项弹窗 | **L3** | 纯 Compose `AlertDialog` |
| 详情页收藏分类弹窗 | **L3** | 纯 Compose `FavoriteCategoryDialog` |
| 详情页本地删除确认 | **L3** | 纯 Compose `AlertDialog` |
| 章节视频下载清晰度选择 | **L3** | 纯 Compose `AlertDialog` |
| Compose `DownloadDialog`（`download/ui/compose/`） | **L1** body | Details 页直接用；`DownloadDialogFragment` 也复用同一 body |
| 旧 Compose `DownloadDialog`（`download/ui/dialog/compose/`） | — | 622 行孤立副本，**已删除** |
| `DownloadDialogFragment` | **L1** | `DialogFragment` 壳 + Compose body，仍被 `ContentListFragment` / `SearchActivity` / `ReadButtonDelegate` 使用 |
| `ContentStatsDialog`（Compose） | **L2** | Details 页 overflow 菜单直接用 |
| `ContentStatsSheet`（Fragment） | **L1** | 仍被 `StatsActivity.onItemClick` 使用 |
| `ScrobblingSelectorDialog`（Compose） | **L2** | Details 页直接用 |
| `ScrobblingSelectorSheet`（legacy） | **Legacy** | 仅被 `ScrobblingInfoSheet` 调用，短期保留 |
| `ChaptersPagesSheet` | **L1** | `BaseAdaptiveSheet` + XML `TabLayout` + Compose `HorizontalPager` 混合宿主；内部 Chapters/Pages/Bookmarks 已 Compose |

---

## 列表基础设施

### AppContentListRoute — **L2**

- `onLoadMore` 现在是正式参数（上一轮是写死的 `{}`，阻断了所有分页）
- Favorites / Suggestions / Local / Updates / Feed 通过该 route 承载
- `KototoroContentListScreen` 空态文案已资源化为 `R.string.nothing_found`

---

## 设计系统 / Liquid Glass

| 组件 | 状态 |
|------|------|
| `GlassSurface` | ✅ 已实现，但注释标注 "Temporary fallback"，退化为稳定不透明容器 |
| `GlassTopBarContainer` | ✅ 主壳顶栏使用 |
| `GlassBottomBarContainer` | ✅ 主壳底栏使用 |
| `GlassCard` | ❌ 文档规划中，代码不存在 |
| `GlassSheet` | ❌ 文档规划中，代码不存在 |

---

## 代码量/卫生

- 本日累计净削减 **≈ 2613 行**（`+232 / -2845`）
- 主要来自 `HomeScreen.kt`（-2079）、`download/ui/dialog/compose/DownloadDialog.kt`（-622）、`DetailsMenuProvider.kt`（-171）
- 生产日志噪音清理：`DiscoverViewModel` / `ReadButtonDelegate` / `FavouritesContainerViewModel`

---

## CMP / 共享层

| 方面 | 状态 |
|------|------|
| `shared/` 模块 | ❌ 不存在 |
| `commonMain` 结构 | ❌ 不存在 |
| `feature-*` 模块 | ❌ 不存在 |
| `expect/actual` 桥接 | ❌ 不存在 |

**结论**：CMP 共享层目前完全处于设计方向阶段，无任何工程落地。
