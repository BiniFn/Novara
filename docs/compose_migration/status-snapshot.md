# Compose 迁移：当前状态快照

> 最后校对日期：2026-04-21
>
> 本文件只描述当前代码事实，不记录历史决策，不展开未来计划。
>
> 最小校验基线：`./gradlew :app:compileDebugKotlin --no-daemon` 已于 2026-04-21 通过。

## 迁移深度定义

| 深度 | 定义 | 标准 |
|------|------|------|
| **L1** | Compose UI body | 渲染层用 Compose，但 host 仍是 Fragment / DialogFragment / BaseAdaptiveSheet / ViewBinding Activity |
| **L2** | Compose route + ViewModel 边界清楚 | Activity `setContent {}` 或 Compose `NavHost` 承载，主要交互已走 Compose 路由 |
| **L3** | 去壳完成 | 无 Fragment / DialogFragment / XML host 依赖 |
| **L4** | 可讨论共享层 | 状态与 UI 对 Android API 直接依赖很薄，可开始评估 commonMain |

---

## 整体概览

- **Android 端 Compose UI 覆盖率**：约 70%+
- **主壳导航**：已是 Compose `NavHost`
- **高频页**：Home / Explore / History / Favorites / Feed / Local / ContentList 基本已进入 Compose 路由
- **仍然集中在 L1 的区域**：`DetailsActivity`、若干 dialog/sheet 壳、Source Settings legacy 页面
- **CMP/commonMain 准备度**：仍然很低；仓库内无 `shared/` / `commonMain/` / `expect/actual` 基础设施

---

## 主壳 / 导航

### MainActivity + KototoroApp — **L2**

| 方面 | 当前状态 |
|------|------|
| Activity 宿主 | `MainActivity` 仍继承 `BaseActivity<ActivityMainBinding>`，通过 `setContentViewWebViewSafe { ActivityMainBinding.inflate(...) }` 托管根视图 |
| 主内容入口 | `viewBinding.composeRoot.setContent { KototoroApp(...) }` |
| 导航 | `KototoroApp.kt` + `AppNavGraph.kt` 使用 Compose `NavHost` |
| 搜索链路 | `onQueryChanged` / `onSearch` / `suggestions` 已完整接回 |
| 过滤器默认态 | `clearActiveFilters()` 会把语言预设 / 内容类型 / 源标签三类过滤器全部重置为可见 |
| 顶栏更多菜单 | `KototoroTopBar` 直接用 Compose `DropdownMenu`；展示视图与网格大小已合并为统一“Display options”面板，并以下拉浮层承载 |
| 顶栏 anchor | 已不再使用隐藏 `AndroidView`；当前实现直接依赖 Compose 侧按钮与回调 |
| 首启初始化 | `savedInstanceState == null` 时仍会调用 `onFirstStart()`，首启服务链路已恢复 |
| 残留问题 | Activity 里仍持有多组 `mutableStateOf` 顶栏/过滤器/inset 状态，主壳仍未直接 `setContent {}` |

**结论**：主壳导航已稳定进入 Compose 路由层，但 Activity 根宿主和部分状态仍停留在旧结构里，不能记为 L3。

---

## 设置系统

### Settings 总体 — **L1/L2 混合**

| 方面 | 当前状态 |
|------|------|
| 根入口 | `RootSettingsFragment` 已是 `Fragment + ComposeView` 宿主 |
| 主页面渲染 | Appearance / StorageAndNetwork / Services / Downloads / Tracker / Sources / Backups / Notification / AI / Translation / Playback / Reader / About 等均已走 Compose Screen 或 ComposeView 宿主 |
| AI 子树 | `AISettingsScreen`、`TranslationSettingsScreen`、`TranslationApiSettingsScreen`、`TranslationE2ESettingsScreen`、`AIImageEnhancementSettingsScreen`、`TtsSettingsScreen`、`OcrModelsSettingsScreen` 已落地 |
| BasePreferenceFragment | 仓库内仍保留类定义，但**当前无任何子类/直接使用点** |
| 设置搜索 | `SettingsSearchHelper` 已改为大批 Kotlin key lists，不再依赖先前 AI/翻译/TTS/OCR/E2E 那批 XML |
| 仍保留的 Fragment 导航 | `SettingsActivity` 仍以 Fragment 切页为主 |
| 仍保留的 tab host | `SettingsTabbedFragmentsScreen` 仍通过 `AndroidView + FragmentContainerView` 承载 tab 页面 |
| SourceSettings 分流 | `SourceSettingsHostFragment` 会把标准仓库分到 Compose `SourceComposeSettingsFragment`，复杂来源仍回退旧 `SourceSettingsFragment` |
| 仍保留的 PreferenceFragmentCompat | 主要集中在 `SourceSettingsFragment` 及其动态 preference 拼装链路 |

### 仍保留的 XML / Preference 资源

当前 `app/src/main/res/xml` 下与设置迁移直接相关、尚未清理的资源主要是：

| 资源 | 当前用途 |
|------|------|
| `pref_source.xml` | `SourceSettingsFragment` 基础配置入口 |
| `pref_source_parser.xml` | `SourceSettingsExt.kt` 动态 parser preference 拼装时使用 |
| `pref_root.xml` | 资源仍在仓库中，但当前 Kotlin 入口已不再依赖它作为根设置页 |
| `pref_sync_header.xml` | 由 `authenticator_sync.xml` 引用，属于同步账号设置头部资源 |

**结论**：设置页的大多数可见 UI 已经 Compose 化，但“真正阻碍去壳”的问题已经收敛到两块：`SettingsTabbedFragmentsScreen` 和 `SourceSettingsFragment`。

---

## 高频内容页

| 页面/模块 | 深度 | 当前状态 |
|------|------|------|
| Home | **L2** | Compose 路由，接入全局顶栏过滤器回调 |
| Explore / Discover | **L2** | 统一走 `KototoroExploreHostRoute`，`discover` 与 `explore` 路由都汇入 Compose Host |
| History | **L2** | Compose `HistoryScreen`，列表/清理对话框均在 Compose 路径 |
| Favorites | **L2** | `FavouritesActivity` 已 `setContent`，宿主为 `FavoritesHostScreen` |
| Feed | **L2** | Compose 路由，仍通过 ViewModel + filter callback 与主顶栏联动 |
| Local / Suggestions / Updated / Bookmarks 等主列表 | **L2** | 已走 `AppContentListRoute` 或等价 Compose route |
| Downloads | **L2** | `DownloadsActivity` 使用 `setContent {}` |
| ContentList / Search | **L2** | 主体列表 UI 已是 Compose，但下载对话框等外围壳仍有 legacy host |

---

## 详情页 / Tracking

### Details — **L1**

| 方面 | 当前状态 |
|------|------|
| Activity 宿主 | `DetailsActivity` 仍继承 `BaseActivity<ActivityDetailsBinding>` |
| Compose 接入 | 通过 `viewBinding.composeView?.setContent { DetailsScreen(...) }` |
| Pane 主入口 | 普通详情页里的章节 / 页面 / 书签入口已在 `DetailsScreen` 内使用 Compose `ModalBottomSheet` |
| 详情头部动作 | 收藏按钮已显式使用 `onPrimary/onSurface` 配色；翻译按钮只在作品语言与当前目标翻译语言不一致时显示，且支持长按提示“翻译名称和简介” |
| 紧凑底部 pane | 紧凑态 pane 现在可收窄为居中的悬浮宽度；展开透明度改为连续过渡，不再在全屏临界点突变为纯黑 |
| Reader / Video 兼容 | `AppRouter.showChapterPagesSheet(...)` 仍保留给 Reader / Video 路径 |
| Tracking 统一体验 | `DetailsHeader` 已接入 `DetailsBindingCard`、`linkedTrackingItems`、`trackingSuggestion` |
| 自动推荐绑定 | `DetailsViewModel.refreshTrackingMatchSuggestion()` 仍显式要求 `content.isLocal`，尚未扩展到非本地内容 |
| Activity 遗留 action 分支 | `DetailsActivity` 的 `onActionClick` 里仍保留 `Download` / `OpenTracking` / `OpenStatistics` / 若干 toggle action 的空分支 |
| 转场 | 仍依赖 XML `imageViewCover` 与共享元素转场；当前保留 `1200 ms` fallback 启动兜底 |

**结论**：详情页的 Compose body 已很重，但 Activity 壳、转场锚点和若干遗留 action 仍在，当前只能保守记为 L1。

### Tracking Site Details — **L2**

| 方面 | 当前状态 |
|------|------|
| Activity | `TrackingSiteDetailsActivity` 已改为 `AppCompatActivity` + `setContent {}` |
| Fragment | `TrackingSiteDetailsFragment` 已是 `ComposeView` host |
| Screen | `TrackingSiteDetailsScreen` 已与普通详情页共享更多 Compose 视觉结构 |
| ViewModel | 已暴露 `linkedTrackingItem` 等绑定信息，能复用统一绑定卡片模型 |

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
- tracking 站点数据缓存当前已形成“双层”结构：详情数据走 Room 持久缓存，分类发现数据走内存 + `SharedPreferences` TTL 缓存。

---

## 当前代码可直接确认的遗留问题

- `MainActivity` 仍依赖 `ActivityMainBinding` 作为 Compose 根宿主，尚未直接 `setContent {}`。
- `DetailsActivity` 仍依赖 `ActivityDetailsBinding`，并保留若干空 action 分支，说明详情页去壳尚未收口。
- `SettingsTabbedFragmentsScreen` 仍通过 `AndroidView + FragmentContainerView` 承载 tab 子页，不是纯 Compose 导航。
- `SourceSettingsFragment` 仍是 `PreferenceFragmentCompat`，并继续依赖 `pref_source.xml` / `pref_source_parser.xml`。
- `DownloadDialogFragment`、`ContentStatsSheet`、`ScrobblingSelectorSheet`、`ChaptersPagesSheet` 仍是旧壳。
- tracking 自动推荐绑定仍局限于本地内容。
- Compose 主列表到详情页已新增一层轻量封面过渡缓存：主列表卡片会记录点击时的封面 bounds，`DetailsActivity` 启动后用这份 bounds 驱动 XML `imageViewCover` 做一次自绘入场插值。
- 这套封面 bounds 透传现在已覆盖 Compose 主列表、Home 三合一卡片、Feed 动态卡片、更新轮播，以及通用 `DiscoverHeroCarousel` / `DiscoverCarousel` / `DiscoverScreen` 组件。
- `KototoroExploreHostRoute` 里的 tracking hero / 热门卡片当前未接入这套缓存；这是有意边界，因为这些入口主路径打开的是 `TrackingSiteDetailsActivity` 或外部浏览器，而不是 `DetailsActivity`。
- 因此 Hero 开关在已接入的 Compose 详情链路上已不再只是 root-view scale-up；但系统级共享元素锚点仍未完全收口。
- 仓库内仍无可复用的 `shared/` / `commonMain` / `expect/actual` 结构，当前还谈不上 L4。
