# Compose 迁移：当前状态快照

> 最后校对日期：2026-04-19
>
> 本文件描述**此刻代码的事实状态**，并补充 2026-04-19 本轮用户验收反馈中已明确出现的 UI 回归。
> 不包含历史决策、未来计划。

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

### Settings 页面渲染 — **L1（AI/翻译模块已提升至 L2）**

| 方面 | 状态 |
|------|------|
| Root 入口 | `RootSettingsFragment` 承载 Compose 入口页 |
| 二级页面 | Appearance / StorageAndNetwork / Services / Downloads / Tracker / Sources / Backups / Notification / JsonSources / Extensions 均已 Compose 渲染 |
| AI 设置 | `AISettingsScreen` 纯 Compose 路由页（已删除 `pref_ai.xml`） |
| 翻译设置 | `TranslationSettingsScreen` 完整 Compose 化，含 OCR/Bubble/Pipeline 全部设置项（已删除 `pref_translation.xml`） |
| API 设置 | `TranslationApiSettingsScreen` Compose 化，含 text input / fetch models（已删除 `pref_translation_api.xml`） |
| 图像增强 | `AIImageEnhancementSettingsScreen` Compose 化（已删除 `pref_ai_image.xml`） |
| 导航 | 仍复用 `SettingsActivity` + Fragment 跳转 |
| 搜索索引 | `SettingsSearchHelper` AI/翻译/TTS/OCR/E2E 模块已改用 Kotlin key lists，深层 SourceSettings 仍未纳入设置搜索索引 |
| 子页宿主 | `SettingsTabbedFragmentsScreen` 仍用 `AndroidView + FragmentContainerView` 承载子 Fragment |
| 仍使用 BasePreferenceFragment 的页面 | 无 |

**关键事实**：AI/翻译相关 4 个 XML preference 文件已全部删除并替换为 Compose 屏幕；`TtsSettingsFragment`、`OcrModelsFragment`、`TranslationEndToEndApiSettingsFragment` 已迁到 `Fragment + ComposeView` 宿主，且 `SettingsSearchHelper` 已补齐 AI 子树的 TTS / OCR / E2E 搜索索引。`pref_tts_settings.xml` 已确认删除。`SourceSettingsFragment` 现已从 `BasePreferenceFragment` 脱钩，改为直接继承 `PreferenceFragmentCompat` 并自行承接标题、异常解析、insets 与搜索定位逻辑，因此设置系统里已无页面直接依赖 `BasePreferenceFragment`。在此基础上，新增了 `SourceSettingsHostFragment` 分流：标准 `ParserContentRepository` / `KotatsuParserRepository` / `EmptyContentRepository` 已进入 Compose `SourceSettingsScreen` 路径；JS / Legado / TVBox / Mihon / Aniyomi 等复杂来源仍自动回退旧动态 `Preference` 页面。

---

## 高频内容页

### Home — **L2**

- 通过 Compose NavHost 路由（`"home"` → `HomeScreen`）
- 三个 `HomeContentCarouselCard`（历史/更新/推荐）+ `QuickActionsCard`
- Home 重点被确认为个人仪表盘，不再承载任何与 Tracking、发现相关的推荐及聚合轮播（Phase 3 职责归拢结果）
- Home → History / Favorites / Local 导航走 `navController.navigate`，不再绕 Activity
- `HomeScreen.kt` 已清掉 ~1900 行未引用死代码，并于 Phase 3 期间进一步移除了追踪组件与状态，剩余 ~500 余行
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

- `ExploreHostScreen` 为统一发现页：Hero 轮播置顶 + Sources FlowRow 快捷卡 + 追踪类别 LazyRow 紧凑卡片，完全接管 Browse 及 Tracking 相关全部呈现
- 与旧 Discover 的 Hero / Carousel 结构合并
- **Phase 2**：`DiscoverHeroCarousel` 从 470dp 大卡重写为 280dp 边到边紧凑布局（poster 100×140dp + 信息列）；`SourcesQuickAccessCard` 从 LazyRow 改为 FlowRow chip 风格；`TrackingCategoryCarouselCard` 从 HorizontalPager 改为 LazyRow + 小封面列表
- **Phase 3**：承接了由 Home 移出的与 `TrackingSiteDiscoveryService` 等 Tracking Discoverability 数据流。完全确立探索发现的模块界限。

### Feed — **L2**

`FeedFragment → FeedScreen` 已完成。

### Local — **L2**

通过 Compose NavHost 路由。

### Downloads — **L2**

`DownloadsActivity` 使用 `setContent`。

### ContentList / Search — **L2**

`ContentListActivity` 已迁移为 Compose 路由。

### Details — **L1（持续向 L2 推进，当前编译基线已恢复）**

| 方面 | 状态 |
|------|------|
| Activity | `DetailsActivity` 仍使用 `ActivityDetailsBinding` |
| Compose 接入 | 通过 `viewBinding.composeView?.setContent { DetailsScreen(...) }` |
| 头部 / 操作 | Compose `DetailsScreen` 完成 |
| pane 宿主 | `DetailsScreen` 已在 Compose 内使用 `ModalBottomSheet` 打开章节 / 页面 / 书签 pane |
| pane 内容 | `ChaptersPagesTabsContent` 已承载 Chapters / Pages / Bookmarks 与 Compose toolbar |
| 通用 adaptive sheet | `ChaptersPagesSheet` 仍保留给 Reader / Video 入口，不再应视为详情页的主 pane 宿主 |
| 阅读入口 | `DetailsActivity` 与 `ReadButtonDelegate` 已共用 `openDetailsReader(...)`，EPUB 历史修正 / 视频恢复播放 / incognito 逻辑已收敛 |
| tab 记忆 | `ChaptersPagesSheet` 已改为持久化语义 tab id（chapters / pages / bookmarks），避免把 pager index 写回 `lastDetailsTab` |
| 绑定体验（进行中） | working tree 中已新增"已绑定 tracking 卡片"数据流和共享 `DetailsBindingCard` 组件，并开始把推荐绑定卡片接入普通详情页 header |
| 当前阻塞 | ~~details/tracking 新增代码编译错误~~（**已修复**）。自动推荐绑定仍仅对 `content.isLocal` 生效 |
| 当前回归 | ~~详情页 Compose pane 内容区高度分配异常~~（**高度挤压已修复**），当前已补上 `HazeState` 根采样、底栏/顶部圆按钮/绑定卡片徽章的真实 glass 化；根据 2026-04-19 最新反馈，**首帧封面先直角后圆角** 已收口，后续仍需继续验证 **章节 / 页面 / 书签 pane 未稳定显示真实列表内容** |
| 转场 | 依赖 XML `imageViewCover` 做共享元素转场；**已去除 350 ms 硬编码启动**，仅保留图片加载回调 + 1200 ms fallback，当前首帧圆角问题已不再作为阻塞项 |
| 删除确认 | Compose `AlertDialog` |
| `DetailsMenuProvider` | 已删除（171 行未被引用） |
| `OpenTracking/OpenStatistics` Action | Activity 端死分支已删除，仅由 Compose 内 dialog state 驱动 |

**降级原因**：仍是 XML Activity 壳 + ComposeView 混合宿主；虽然详情页 pane 已进入 Compose，但 Activity / 转场 / 平台桥仍未去壳，且新的 tracking 统一体验改造尚未编译收口。

### Tracking Site Details — **L2（已完成 Compose host 迁移）**

| 方面 | 状态 |
|------|------|
| Activity | `TrackingSiteDetailsActivity` 已改为 `AppCompatActivity` + `setContent {}` 纯 Compose host，不再使用 `ViewBinding` |
| Screen | `TrackingSiteDetailsScreen` 已实现 collapseProgress / panorama blur / graphicsLayer 动画，与 `DetailsScreen` 风格对齐 |
| Fragment | `TrackingSiteDetailsFragment` 已改为 `ComposeView` host |
| ViewModel | `TrackingSiteDetailsViewModel` 已暴露 `linkedContent`、`scrobblingEntity`、`linkedTrackingItem` 供 Compose 卡片渲染 |
| 旧 XML | `activity_tracking_site_details.xml` 已删除，`ActivityTrackingSiteDetailsBinding` 不再使用 |
| 当前结论 | 追踪详情页与普通详情页在 header 结构、滚动折叠动画、模糊背景上已一致 |

### 当前已识别的 UI 回归（待修）

- 2026-04-19 用户验收反馈显示：**Browse 顶部“每日放送” Hero 仍未真正延伸到状态栏顶部**，视觉上仍被搜索栏下沿截断；当前除补强底部渐变、压缩 Hero 与 sources 区块之间的割裂带外，又进一步将 Hero 背景改为**单层 Crossfade + 持续兜底底色/scrim**，sources 区块改成更深的上压接与更长的透明到背景渐变，并将标题与追踪站点切换按钮并排放在同一行；但仍需设备端继续确认轮播切换完成后是否彻底无割裂。
- Browse 的 **内容源入口** 已从文字 chip 改为更接近方形的 favicon 卡片（图标上、标题下），且右侧动作固定进入 **内容源管理页**，不再按条件跳到未激活源目录；favicon 现在走 `faviconUri()` + fallback 路径，而不再统一使用占位 storage 图标。
- **Browse 其他追踪卡片** 当前仍缺少下边缘背景过渡，且整体高度偏高，信息密度与视觉重心仍未收口。
- ~~主页面搜索栏过滤器接线不一致：`KototoroTopBar` 已支持语言预设 / 内容类型 / 源类型，但只有部分页面正确桥接了 `SearchBarFilterViewController`~~（此前的“统一默认展示”只能避免按钮整体消失）；**根据最新验收，除 Favorites 外其余主页面仍缺语言预设按钮**，且横屏时按钮组会居中，“更多”按钮点击无响应。
- **主壳搜索栏厚度** 已于 2026-04-19 收紧一轮：`SearchBarDefaults.InputField` 压到 collapsed `48dp` / expanded `52dp`，右侧 action button 与筛选 chip 统一收紧到 `40dp` 级别；若后续验收仍嫌厚，应继续从 text field 内边距而非顶端 offset 入手。
- ~~Browse 顶部“每日放送”区域当前缺少追踪网站切换按钮~~（**已恢复切换入口**），并已将 Compose 中追踪站点图标的 `painterResource()` 替换为 `rememberSafePainter()`，修复 MangaUpdates 等 bitmap-wrapper xml 图标导致的弹出菜单闪退；仍需真机回归确认菜单定位与交互体验。
- **设置 → 外观** 已新增 Haze 模糊风格与玻璃不透明度入口，`KototoroTheme` 也已改为读取当前 Activity 实际主题属性，Compose UI 不再只吃默认 Material3 配色；仍需继续验收旧 View 页与 Compose 页在主题切换时的一致性。
- 为避免普通详情页在旧系统设备上因 `HazeNode.draw` 路径崩溃，`DetailsScreen` 与 `GlassSurface` 现已对 **Android 12 以下** 自动降级为非 Haze fallback；新系统继续保留真实 Haze。
- ~~收藏页 grid 模式下，单个内容卡片可能被前置非 `ContentGridModel` 项挤到第二列~~（**已修复**：`LazyVerticalGrid` 的 `items` 添加 `span` 参数，非 `ContentGridModel` 项占满整行）
- ~~Home / Browse / Discover 的 hero/backdrop 在封面为空时仍可能出现割裂的白色矩形占位~~（**基础 fallback 已修复**），但 Hero / Tracking 卡片底部的主题过渡处理仍未全部完成。
- **Home 的历史 / 更新 / 推荐** 目前虽已合并为更紧凑的三合一卡片，但用户期望的最终形态仍是可左右拖拽的封面卡片列表。
- **设置页** 当前已收紧 section 间距并移除部分嵌套层次，但用户新增要求还包括：移除“透明特效等级”设置入口，并继续永久消除 section 内嵌浅色背景感。
- **普通详情页与 tracking 详情页统一绑定体验** 当前只完成了数据模型与首轮 UI 接线，自动推荐绑定仍仅对 `content.isLocal` 生效，尚未完成非本地内容、类型过滤、优先站点与缓存策略扩展。
- **开启“设置 → 外观 → 固定导航栏 UI”后**，主页面底部内容仍可能被导航栏遮挡，说明 bottom inset / content padding 仍未完全统一。

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
| `GlassSurface` | ✅ 已接入真实 `hazeChild` 后端；在无可采样背景时仍由 `Surface + tint` 提供稳定 fallback |
| `GlassTopBarContainer` | ✅ 组件存在；主壳顶栏当前直接使用 `GlassSurface + DockedSearchBar` |
| `GlassBottomBarContainer` | ✅ 主壳底栏使用，且已可从主内容层采样真实 blur |
| 详情页 Glass 化 | ✅ `DetailsScreen` 根层已提供 `HazeState`，底栏 / 顶部圆按钮 / 绑定卡片 badge / header badge 已切到真实 glass 路径 |
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
