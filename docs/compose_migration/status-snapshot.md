# Compose 迁移：当前状态快照

> 最后校对日期：2026-05-01
>
> 本文件只描述当前代码事实，不记录历史决策，不展开未来计划。
>
> 最小校验基线：`./gradlew :app:compileDebugKotlin --no-daemon` 已于 2026-04-30 通过。

## 迁移深度定义

| 深度 | 定义 | 标准 |
|------|------|------|
| **L1** | Compose UI body | 渲染层用 Compose，但 host 仍是 Fragment / DialogFragment / BaseAdaptiveSheet / ViewBinding Activity |
| **L2** | Compose route + ViewModel 边界清楚 | Activity `setContent {}` 或 Compose `NavHost` 承载，主要交互已走 Compose 路由 |
| **L3** | 去壳完成 | 无 Fragment / DialogFragment / XML host 依赖 |
| **L4** | 可讨论共享层 | 状态与 UI 对 Android API 直接依赖很薄，可开始评估 commonMain |

---

## 整体概览

- **Compose UI 覆盖率**：~75%（按页面数估算），但 **代码层面有 153 个文件仍 import ViewBinding，64 个 Fragment 子类存活**
- **主壳导航**：已是 Compose `NavHost`，路由已全面 typed
- **高频内容页**：Home / Explore / History / Favorites / Feed / Local / ContentList 主体已是 Compose 路由
- **设置页**：可见 UI 已 Compose 化，但 host 仍是 30+ 个 Fragment 类
- **详情页**：Compose body 完整（EntityGraph + Tracking 统一已完成），但 Activity 壳仍在
- **阅读器/视频**：核心渲染仍是 ViewBinding + Fragment 体系，仅少量 Compose 辅助组件
- **弹窗/Sheet**：详情页内的对话框已 Compose，但 19 个 `BaseAdaptiveSheet` 和 26 个 `DialogFragment` 仍在
- **列表适配器**：48 个文件使用 AdapterDelegates + ViewBinding 渲染列表项
- **XML 布局**：205 个 XML 文件残留在 `res/layout/`
- **CMP/commonMain**：无基础设施

### ViewBinding / Fragment 详细清单

#### Activity（35 个，全部继承 `BaseActivity<B : ViewBinding>`）

| 类别 | 文件 | 可否去壳 |
|------|------|------|
| 主入口 | `MainActivity`、`SettingsActivity`、`SearchActivity` | 核心阻塞项 |
| 详情 | `DetailsActivity`、`AlternativesActivity` | Phase 1 |
| 阅读器 | `ReaderActivity`、`NovelReaderActivity`、`WebtoonReaderFragment`(Fragment) | 暂缓 |
| 视频 | `VideoPlayerActivity` | 暂缓 |
| 收藏 | `FavouriteCategoriesActivity`、`FavouritesCategoryEditActivity` | L2 可升级 |
| 图片 | `ImageActivity` | L2 |
| 设置子页 | `UnifiedSourcesActivity`、`SourcesCatalogActivity`、`SourcePresetListActivity`、`SourcePresetEditActivity`、`OverrideConfigActivity`、`ProtectSetupActivity`、`ReaderTapGridConfigActivity`、`ContentDirectoriesActivity`、`JsonSourceEditActivity`、`ScrobblerConfigActivity`、`ColorFilterConfigActivity` | 逐个评估 |
| 浏览器 | `BaseBrowserActivity` | 需 WebView |
| OAuth | `KitsuAuthActivity`、`MangaUpdatesAuthActivity`、`SyncAuthActivity` | 低优先 |
| 其他 | `TrackerDebugActivity`、`StatsActivity`、`AppUpdateActivity`、`ProtectActivity`、`ShelfWidgetConfigActivity`、`RecentWidgetConfigActivity`、`PageImagePickActivity` | 低优先 |

#### Sheet（19 个，`BaseAdaptiveSheet<B>` 子类）

| 类别 | 文件 |
|------|------|
| 核心 | `ChaptersPagesSheet`、`ContentStatsSheet`、`AlternativesSheet`、`ScrobblingInfoSheet`、`ScrobblingSelectorSheet` |
| 视频 | `VideoSettingsSheet`、`VideoSuperResolutionSheet`、`VideoSuperResolutionAdvancedSheet`、`VideoSubtitleSettingsSheet`、`VideoDanmakuSettingsSheet`、`DlnaDeviceSheet` |
| 阅读器 | `ReaderConfigSheet`、`TranslationTaskPanelSheet`、`NovelReaderConfigSheet` |
| 筛选/标签 | `FilterSheetFragment`、`TagsCatalogSheet` |
| 列表 | `ListConfigBottomSheet` |
| 其他 | `WelcomeSheet`、`TrackerCategoriesConfigSheet` |

#### DialogFragment（26 个）

关键条目：`DownloadDialogFragment`、`BackupDialogFragment`、`RestoreDialogFragment`、`ImportDialogFragment`、`ImportJsonDialogFragment`、`SyncHostDialogFragment`、`LocalInfoDialog`、`ContentDirectorySelectDialog`、`FavoriteDialog`、`ErrorDetailsDialog`、`NovelChaptersSheet` 等。

#### Fragment（64 个，含设置页 30+ 个）

设置页的每个子页面仍然是一个独立的 `Fragment` 子类（如 `AppearanceSettingsFragment`、`ReaderSettingsFragment` 等），虽然其内容已通过 `ComposeView.setContent {}` 渲染。真正 Compose 用 `*Screen` composable 实现内容，`*Fragment` 仅作为宿主壳存在。

#### AdapterDelegates（48 个文件）

列表项使用 `adapterDelegateViewBinding` 模式渲染，绑定到 XML 布局。主要分布在 `list/ui/adapter/`、`search/ui/suggestion/adapter/`、`details/ui/adapter/`、`settings/` 子树。

#### XML 布局文件（205 个）

包含 Activity 布局、Sheet 布局、列表项布局、对话框布局、自定义 View 布局等。

---

## 主壳 / 导航

### MainActivity + KototoroApp — **L2**

| 方面 | 当前状态 |
|------|------|
| Activity 宿主 | `MainActivity` 仍继承 `BaseActivity<ActivityMainBinding>`，通过 `setContentViewWebViewSafe { ActivityMainBinding.inflate(...) }` 托管根视图 |
| 主内容入口 | `viewBinding.composeRoot.setContent { KototoroApp(...) }` |
| 导航 | `KototoroApp.kt` + `AppNavGraph.kt` 使用 Compose `NavHost`，路由已全面 typed（`@Serializable` data class） |
| 搜索链路 | `onQueryChanged` / `onSearch` / `suggestions` 已完整接回 |
| 过滤器默认态 | `clearActiveFilters()` 会把语言预设 / 内容类型 / 源标签三类过滤器全部重置为可见 |
| 顶栏更多菜单 | `KototoroTopBar` 直接用 Compose `DropdownMenu`；展示视图与网格大小已合并为统一"Display options"面板 |
| 顶栏 anchor | 已改用 `LocalView.current`，不再依赖隐藏 `AndroidView` |
| 首启初始化 | `savedInstanceState == null` 时调用 `onFirstStart()`，首启服务链路已恢复（4/18 修复） |
| 残留问题 | Activity 里仍持有多组 `mutableStateOf` 顶栏/过滤器/inset 状态，主壳仍未直接 `setContent {}` |

**结论**：主壳导航已稳定进入 Compose 路由层，但 Activity 根宿主和部分状态仍停留在旧结构里，不能记为 L3。

---

## 核心 UI 组件

### VerticalScrollbar — **独立 composable，对标 Android FastScroll**

| 方面 | 当前状态 |
|------|------|
| API 形态 | 从 `Modifier.verticalScrollbar()` 扩展函数重写为独立 `BoxScope.VerticalScrollbar()` composable，同时支持 `LazyListState` 和 `LazyGridState` |
| 拖拽交互 | 改用 `pointerInput` + `awaitEachGesture` 实现完整拖拽，替代旧 `detectDragGestures`；拖拽时通过 `requestDisallowInterceptTouchEvent` 防止父容器拦截 |
| 视觉渲染 | 使用 `Canvas` 绘制 thumb，尺寸取自 `R.dimen.fastscroll_handle_width/height/radius`；颜色默认使用 `colorControlNormal` 主题属性 |
| 标签气泡 | 拖拽时以 Material3 `Surface` + `Text` 显示章节标签（如"第N章"），自动约束在 track 范围内 |
| 滚动目标批处理 | 使用 `Channel<Int>(CONFLATED)` + `withFrameNanos` 去抖动，避免拖拽时密集调用 `requestScrollToItem` |
| 可见性 | 滚动或拖拽时保持可见，停止后延迟 1s 渐隐；不满足总项数 > 可见项数时自动隐藏 |
| 调用方迁移 | `ChaptersScreen`、`PagesScreen`、`BookmarksScreen` 已从旧 `Modifier.verticalScrollbar()` 迁到新 `VerticalScrollbar()` |

**结论**：VerticalScrollbar 已从轻量 modifier 升级为功能完整的独立组件，支持拖拽、标签气泡、平滑定位，对标准 Android `FastScroller`。

---

### Settings 总体 — **L1/L2 混合，显著向 L2 推进**

| 方面 | 当前状态 |
|------|------|
| 根入口 | `RootSettingsFragment` 已是 `Fragment + ComposeView` 宿主 |
| 主页面渲染 | Appearance / StorageAndNetwork / Services / Downloads / Tracker / Sources / Backups / Notification / AI / Translation / Playback / Reader / About 等均已走 Compose Screen 或 ComposeView 宿主 |
| 设置导航 | 新增 `SettingsDestination.kt`（4/29），定义 typed 设置目的地枚举，替代硬编码路由 |
| 设置搜索 | `SettingsSearchHelper` 已改为大批 Kotlin key lists，不再依赖先前 AI/翻译/TTS/OCR/E2E 那批 XML。`SettingsSearchViewModel` + `SettingsSearchMenuProvider` 覆盖搜索入口 |
| AI 子树 | `AISettingsScreen`、`TranslationSettingsScreen`、`TranslationApiSettingsScreen`、`TranslationE2ESettingsScreen`、`AIImageEnhancementSettingsScreen`、`TtsSettingsScreen`、`OcrModelsSettingsScreen` 已落地 |
| BasePreferenceFragment | 仓库内仍保留类定义，但**当前无任何子类/直接使用点**（4/30 验证） |
| 仍保留的 Fragment 导航 | `SettingsActivity` 仍以 Fragment 切页为主 |
| 仍保留的 tab host | `SettingsTabbedFragmentsScreen` 仍通过 `AndroidView + FragmentContainerView` 承载 tab 页面 |
| SourceSettings 分流 | `SourceSettingsHostFragment` 会把标准仓库分到 Compose `SourceComposeSettingsFragment`，复杂来源仍回退旧 `SourceSettingsFragment` |
| 仍保留的 PreferenceFragmentCompat | 主要集中在 `SourceSettingsFragment` 及其动态 preference 拼装链路 |

### 源管理统一化（4/28–4/29 新增）

| 方面 | 当前状态 |
|------|------|
| 统一入口 | 新增 `UnifiedSourcesActivity`（`BaseActivity<ActivityUnifiedSourcesBinding>` + ComposeView），承载 `UnifiedSourcesScreen` |
| 统一 ViewModel | `UnifiedSourcesViewModel` 管理扩展仓库、JSON 源、本地 JAR 导入的统一状态；5/1 新增 LNReader 插件浏览/安装/卸载链路、中文语言码归一化（`zh`） |
| 旧路径重定向 | `LegacySourceRedirects.kt` 将旧的分散设置入口路由到统一界面 |
| 涉及组件 | `ExtensionsBrowserFragment`、`ExtensionRepositoriesFragment`、`JsonSourcesFragment`、`LNReaderRepositoriesFragment` 等仍保留但入口逐步被统一界面替代 |
| LNReader 集成（5/1） | 通过 `LNReaderRepository` 拉取插件索引；`LnReaderAvailablePlugin` 内部模型映射到 `UnifiedSourcePackageItem`；安装时 fetch JS 内容→`jsonSourceManager.importLNReaderPlugin()` |
| Language 归一化（5/1） | `normalizeLanguageCode()` 将中文变体（中文/简体中文/繁體中文/chinese/zh-cn/zh-hans/zh-hant 等）统一归一到 `"zh"`，并过滤 Unicode format 字符 |
| SettingsActivity（5/1） | 修复 master-details 导航：非 `ACTION_SOURCES`/`ACTION_SOURCE` 的 intent 回退到 `SettingsDestination.Root` 而非 `AppearanceSettings` |

### 仍保留的 XML / Preference 资源

当前 `app/src/main/res/xml` 下与设置迁移直接相关、尚未清理的资源主要是：

| 资源 | 当前用途 |
|------|------|
| `pref_source.xml` | `SourceSettingsFragment` 基础配置入口 |
| `pref_source_parser.xml` | `SourceSettingsExt.kt` 动态 parser preference 拼装时使用 |
| `pref_root.xml` | 资源仍在仓库中，但当前 Kotlin 入口已不再依赖它作为根设置页 |
| `pref_sync_header.xml` | 由 `authenticator_sync.xml` 引用，属于同步账号设置头部资源 |

**结论**：设置页的大多数可见 UI 已经 Compose 化，设置导航和搜索已完成 Compose 重构。主要遗留块仍然是 `SettingsTabbedFragmentsScreen`（AndroidView tab host）和 `SourceSettingsFragment`（PreferenceFragmentCompat）。源管理统一界面新增了一个 ViewBinding 壳的 Compose Activity。

---

## 高频内容页

| 页面/模块 | 深度 | 当前状态 |
|------|------|------|
| Home | **L2** | Compose 路由，接入全局顶栏过滤器回调。HomeScreen 已从 2593 行精简至 ~650 行（4/18），actions 已归组为 `HomeScreenActions` data class |
| Explore / Discover | **L2** | 统一走 `KototoroExploreHostRoute`，`discover` 与 `explore` 路由都汇入 Compose Host。`ExploreHostScreen` 移除 4 行 showcase 上限（5/1）。`DiscoverViewModel` 通过 `GlobalFavoritesState` 读取 group tab，服务切换时清理 tab 状态（5/1） |
| History | **L2** | Compose `HistoryScreen`，列表/清理对话框均在 Compose 路径。通过 NavHost 导航（4/18 修复） |
| Favorites | **L2** | `FavouritesActivity` 已 `setContent`，宿主为 `FavoritesHostScreen`。双 padding 问题已修复（4/18） |
| Feed | **L2** | Compose 路由，仍通过 ViewModel + filter callback 与主顶栏联动 |
| Local / Suggestions / Updated / Bookmarks 等主列表 | **L2** | 已走 `AppContentListRoute` 或等价 Compose route。`onLoadMore` 分页已恢复（4/18）。Local 页新增 `LocalContentTypeFilterBar` 过滤胶囊条（5/1） |
| Downloads | **L2** | `DownloadsActivity` 使用 `setContent {}` |
| ContentList / Search | **L2** | 主体列表 UI 已是 Compose，但下载对话框等外围壳仍有 legacy host |

### AppContentListRoute — **L2**

| 方面 | 当前状态 |
|------|------|
| 新参数 `listHeader` | 5/1 新增，允许调用方注入自定义 header composable（如 Local 页的 `LocalContentTypeFilterBar`） |

---

## 详情页 / Tracking

### Details — **L1（Compose body 基本完成，Activity 壳仍在）**

| 方面 | 当前状态 |
|------|------|
| Activity 宿主 | `DetailsActivity` 仍继承 `BaseActivity<ActivityDetailsBinding>` |
| Compose 接入 | 通过 `viewBinding.composeView.setContent { DetailsScreen(...) }` |
| Pane 主入口 | 普通详情页里的章节 / 页面 / 书签入口已在 `DetailsScreen` 内使用 Compose `ModalBottomSheet` |
| EntityGraph + Tracking 统一 | `DetailsScreen` 已统一接入 EntityGraph（4/22）和 Tracking origins（Phase 5+6），`DetailsHeader` 已接入 `DetailsBindingCard`、`linkedTrackingItems`、`trackingSuggestion` |
| 详情头部动作 | 收藏按钮已显式使用 `onPrimary/onSurface` 配色；翻译按钮只在作品语言与当前目标翻译语言不一致时显示 |
| 紧凑底部 pane | 紧凑态 pane 可收窄为居中的悬浮宽度；展开透明度改为连续过渡 |
| 自动推荐绑定 | `DetailsViewModel.refreshTrackingMatchSuggestion()` 仍显式要求 `content.isLocal`，尚未扩展到非本地内容 |
| Activity 遗留 action 分支 | `OpenTracking` / `OpenStatistics` 死分支已清理（4/18），`Download` 等 toggle action 仍保留空分支 |
| 转场 | 仍依赖 XML `imageViewCover` 与共享元素转场；仅保留 1200ms fallback 兜底（4/18 修复竞态） |
| 封面过渡 | Compose 主列表到详情页封面 bounds 透传已覆盖 Compose 主列表、Home 三合一卡片、Feed 动态卡片、更新轮播、通用 Discover 组件 |
| ChaptersScreen（5/1） | 中文章节标签（`fastScrollLabels`，如"第N章"）；`activeDetailsPaneState` derived 状态在全屏 pane + 列表可回滚时抑制 pane；独立 `VerticalScrollbar` 替代旧 modifier |
| PagesScreen / BookmarksScreen（5/1） | 同 ChaptersScreen 模式：`activeDetailsPaneState` + 独立 `VerticalScrollbar`；Pane 嵌套滚动联动优化 |
| ChaptersScreenRoot（5/1） | 视频内容点击章节时优先走 `ReaderNavigationCallback.onChapterSelected`，避免打开新的 Reader Activity |
| ChaptersPagesSheet（5/1） | Tab 改用图标 + `contentDescription`（不再用文字标签）；新增 `disableFitToContents()` + `nestedScroll` 互联 |

**结论**：详情页的 Compose body 已很重，EntityGraph 和 Tracking 统一已完成。但 Activity 壳、转场锚点和若干遗留 action 仍在，当前只能保守记为 L1。

### Tracking Site Details — **L2**

| 方面 | 当前状态 |
|------|------|
| Activity | `TrackingSiteDetailsActivity` 已改为 `AppCompatActivity` + `setContent {}` |
| Fragment | `TrackingSiteDetailsFragment` 已是 `ComposeView` host |
| Screen | `TrackingSiteDetailsScreen` 已与普通详情页共享更多 Compose 视觉结构 |
| ViewModel | 已暴露 `linkedTrackingItem` 等绑定信息，能复用统一绑定卡片模型 |
| 扩展（4/27） | 新增 AniList、MAL、Simkl 等平台支持；`TrackingCandidateCard` 增强显示章节数和迁移按钮 |
| 内容类型过滤（5/1） | `VIDEO_CONTENT_TYPES` 扩展包含 `HENTAI_VIDEO`，修复搜索时仅判断 `ContentType.VIDEO` 导致 hentai 视频被错误分发到漫画搜索 |

**结论**：tracking 详情页已经完成 Compose host 迁移，当前比普通 `DetailsActivity` 更接近 L2 终态。

---

## Dialog / Sheet

| 组件 | 深度 | 当前状态 |
|------|------|------|
| 详情页作者/标签搜索弹窗 | **L3** | Compose `AlertDialog` |
| 详情页分享选项弹窗 | **L3** | Compose `AlertDialog` |
| 详情页收藏分类弹窗 | **L3** | Compose `FavoriteCategoryDialog` |
| 详情页本地删除确认 | **L3** | Compose `AlertDialog` |
| 章节视频下载清晰度选择 | **L3** | Compose `AlertDialog` |
| `AlternativesSheet` | **L1** | 4/30 转为 Compose body（`AlternativesSheetContent`），壳仍是 `BaseAdaptiveSheet<SheetAlternativesBinding>` |
| `download/ui/compose/DownloadDialog.kt` | **L1 body** | Details 页直接复用的 Compose body |
| `DownloadDialogFragment` | **L1** | `DialogFragment` 壳，仍由 `ContentListFragment` / `SearchActivity` / `ReadButtonDelegate` 等路径触发 |
| `ContentStatsDialog` | **L2** | Details 页可直接使用 Compose dialog |
| `ContentStatsSheet` | **L1** | `BaseAdaptiveSheet` 壳仍由 `AppRouter.openStatistic()` 触发 |
| `ScrobblingSelectorDialog` | **L2** | Details 页直接使用 |
| `ScrobblingSelectorSheet` | **Legacy** | 仍被 `ScrobblingInfoSheet` 调用 |
| `ChaptersPagesSheet` | **L1** | `BaseAdaptiveSheet` + XML/Fragment 宿主仍保留给 Reader / Video 入口 |

---

## 迁移兼容性

- `KototoroContentCard` 的来源角标已回归贴边 badge 形态，不再以悬浮胶囊方式覆盖封面左下角。
- Compose 侧来源 favicon 请求已抽到可复用的 `ContentSourceIcon`，并与旧 `FaviconView` 的关键行为对齐，包含 `mangaSourceExtra(...)` 和 `.suppressCaptchaErrors()`。
- 首页三合一卡片内部内容已补齐水平内边距，历史 / 更新 / 推荐三段不再贴左边缘。
- `BackupRepository.restoreBackup()` 在恢复 `BackupSection.SETTINGS` 时仍调用 `AppSettings.upsertAll()`；当前 `upsertAll()` 会在写入后立即规范化 legacy UI 偏好。
- 直接从 pre-Compose 旧包覆盖升级时，`BaseApp` 启动阶段会比较 `KEY_APP_VERSION` 与当前 `BuildConfig.VERSION_CODE`，若发生版本变化则同样执行一次 legacy UI 偏好规范化。
- 已纳入规范化的高风险偏好包括：导航栏高度、主列表/页面网格尺寸、全景封面 blur/动画速度/额外高度/渐变强度、popup radius、glass opacity、搜索建议类型、列表 badge。
- 这条迁移链路的目标是避免 pre-Compose 备份在首启时因为非法枚举、脏字符串集合或越界 UI 数值导致崩溃。
- tracking 站点数据缓存当前已形成"双层"结构：详情数据走 Room 持久缓存，分类发现数据走内存 + `SharedPreferences` TTL 缓存。

---

## 自上次快照（2026-04-21）以来的变化

### 新完成

| 日期 | 变更 | 影响范围 |
|------|------|------|
| 4/22 | `DetailsScreen` 统一接入 EntityGraph + Tracking origins (Phase 5+6) | 详情页 Compose body 进一步完整 |
| 4/24 | "finish compose details migration and stabilize hero transitions" | 详情页/Feed hero 转场稳定化 |
| 4/26 | 减少动画和 glass 渲染开销 | 全局性能 |
| 4/27 | 扩展 tracking site 集成（AniList、MAL、Simkl） | Tracking 站点详情 |
| 4/28–29 | 源管理统一为 `UnifiedSourcesActivity` + `UnifiedSourcesScreen` | 设置-源管理 |
| 4/29 | 设置 Compose 导航（`SettingsDestination`）+ 搜索重构 | 设置全局 |
| 4/29 | VerticalScrollbar 增强：可拖拽 thumb + track + `labelProvider` 无障碍 | 章节列表/页面缩略图滚动条 |
| 4/30 | Alternatives 转为 Compose 底部弹窗（`AlternativesSheetContent`） | 替代源弹窗 |
| 4/30 | Local 作品页新增过滤胶囊条 | 本地内容 |

### 5/1 当前工作树变更（未提交）

| 领域 | 变更 | 影响范围 |
|------|------|------|
| VerticalScrollbar | 重写为独立 `BoxScope` composable，支持拖拽 thumb + 标签气泡 + 批量滚动 | ChaptersScreen、PagesScreen、BookmarksScreen |
| ChaptersScreen | 中文章节标签（`fastScrollLabels`）；`activeDetailsPaneState` 优化 pane 联动 | 章节列表体验 |
| PagesScreen / BookmarksScreen | `activeDetailsPaneState` + 独立 `VerticalScrollbar` | 页面/书签列表体验 |
| ChaptersPagesSheet | Tab 图标化；`disableFitToContents()` + `nestedScroll` | Sheet 交互 |
| ChaptersScreenRoot | 视频内容走 `ReaderNavigationCallback` | 视频章节选择 |
| LocalContentTypeFilterBar | 新增 Compose 组件；`AppContentListRoute` 新增 `listHeader` 参数 | 本地作品页 |
| DiscoverViewModel | 通过 `GlobalFavoritesState` 读取 tab；切换服务时清理 tab 状态 | Discover 过滤 |
| ExploreHostScreen | 移除 4 行 showcase 上限 | Explore 展示 |
| UnifiedSourcesViewModel | LNReader 插件浏览/安装/卸载链路；语言码归一化；`LnReaderAvailablePlugin` 内部模型 | 源管理 |
| SettingsActivity | master-details 导航修复 | 设置导航 |
| TrackingDiscovery | `VIDEO_CONTENT_TYPES` 包含 `HENTAI_VIDEO` | 搜索派发 |
| VideoPlayerActivity | 章节导航优先 ViewModel 列表 | 视频播放 |
| LocalStorageManager | `getConfiguredVideoStorageDirs()` 包含用户配置目录 | 视频存储 |
| AlternativesSheet | `disableFitToContents()` | Sheet 行为 |
| 字符串 | 新增 `details_temporary_read_only_notice` | 详情页提示 |

### 仍未变化

- `MainActivity` 仍依赖 `ActivityMainBinding` 作为 Compose 根宿主，尚未直接 `setContent {}`。
- `DetailsActivity` 仍依赖 `ActivityDetailsBinding`，并保留若干空 action 分支，说明详情页去壳尚未收口。
- `SettingsTabbedFragmentsScreen` 仍通过 `AndroidView + FragmentContainerView` 承载 tab 子页，不是纯 Compose 导航。
- `SourceSettingsFragment` 仍是 `PreferenceFragmentCompat`，并继续依赖 `pref_source.xml` / `pref_source_parser.xml`。
- `DownloadDialogFragment`、`ContentStatsSheet`、`ScrobblingSelectorSheet`、`ChaptersPagesSheet` 仍是旧壳。
- tracking 自动推荐绑定仍局限于本地内容。
- `KototoroExploreHostRoute` 里的 tracking hero / 热门卡片未接入封面 bounds 缓存；这是有意边界，因为这些入口主路径打开的是 `TrackingSiteDetailsActivity` 或外部浏览器。
- 系统级共享元素锚点仍未完全收口。
- 仓库内仍无可复用的 `shared/` / `commonMain` / `expect/actual` 结构，当前还谈不上 L4。

---

## 当前代码可直接确认的遗留问题

### 结构性遗留（量大，需分阶段处理）

- **35 个 Activity** 继承 `BaseActivity<B : ViewBinding>`，其中仅少数（如 `TrackingSiteDetailsActivity`）已改为纯 Compose host
- **64 个 Fragment 子类**，设置页占 30+。这些 Fragment 多数仅作为 ComposeView 宿主壳存在，内容早已 Compose 化
- **19 个 `BaseAdaptiveSheet` 子类**，涵盖核心章节列表、统计、视频设置、筛选等
- **26 个 `DialogFragment` 子类**，包括下载对话框、备份恢复、导入等
- **48 个 AdapterDelegates 文件** 使用 `adapterDelegateViewBinding` 渲染列表项
- **205 个 XML 布局文件** 残留在 `res/layout/`
- **5 个 PreferenceFragmentCompat** 引用，集中在 `SourceSettingsFragment` 链路

### 关键阻塞项（Phase 1–2）

- `MainActivity` 仍依赖 `ActivityMainBinding` 作为 Compose 根宿主，尚未直接 `setContent {}`
- `DetailsActivity` 仍依赖 `ActivityDetailsBinding`，并保留若干空 action 分支
- `SettingsTabbedFragmentsScreen` 仍通过 `AndroidView + FragmentContainerView` 承载 tab 子页
- `SourceSettingsFragment` 仍是 `PreferenceFragmentCompat`，并继续依赖 `pref_source.xml` / `pref_source_parser.xml`
- `ChaptersPagesSheet`、`ContentStatsSheet`、`DownloadDialogFragment` 等高频 Sheet/Dialog 仍是旧壳
- tracking 自动推荐绑定仍局限于本地内容
- 系统级共享元素锚点仍未完全收口

### 质量收口（Phase 5）

- 多处 `collectAsState` 未改用 `collectAsStateWithLifecycle`
- `ReadButtonDelegate`（ViewBinding）与 Compose `ReadDock` 双按钮共存
- 仓库内仍无可复用的 `shared/` / `commonMain/` / `expect/actual` 结构
