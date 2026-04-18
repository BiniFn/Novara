# Compose 迁移：当前状态快照

> 最后校对日期：2026-04-18
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

- **Android 端 Compose UI 迁移进度**：约 65%~75%
- **面向 CMP/commonMain 的工程准备度**：< 20%
- 仓库根目录无 `shared/` / `commonMain` / `feature-*` 模块结构

---

## 主壳 / 导航

### MainActivity + KototoroApp — **L2**

| 方面 | 状态 |
|------|------|
| 主布局 | `activity_main.xml` 已精简为单个 `ComposeView` |
| 导航 | `KototoroApp.kt` → `AppNavGraph.kt` → Compose `NavHost`，已落地 |
| 旧导航 | `MainNavigationDelegate` 已不再作为主路径依赖 |
| `FragmentHostRoute` | 代码中已无任何 `.kt` 引用（已移除） |
| 搜索 | ~~`onQueryChanged` / `onSearch` / `suggestions` 未传入 KototoroApp~~（**已于本次修复**） |
| 残留 | ~~`FragmentContainerView` import + 未使用字段~~（**已于本次清理**） |

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

已通过 Compose NavHost 路由（`AppNavGraph` → `"home"` → `HomeScreen`）。
Hero 轮播、tracking 首页、概览卡等已全部 Compose 实现。

### Discover (Merged into Explore)

The standalone `DiscoverScreen` has been removed. The `"discover"` and `"explore"` routes now both map to the unified `KototoroExploreHostRoute`.

### History — **L2**

已通过 Compose NavHost 路由。

### Favorites — **L2**

已通过 Compose NavHost 路由。`FavouritesActivity` 已使用 `setContent` 包 Compose route。
旧 `FavouritesContainerFragment` 已重写为 `FavoritesHostScreen`。

### Explore (Unified Discovery Page) — **L2**

已通过 Compose NavHost 路由。`ExploreHostScreen` 现已重写为统一下拉加载的发现页，顶部为 Source 卡片（由原 ExploreSourcesFragment 演化），下方为追踪类别卡片（原 Discover 的 Hero 和 Carousel），完全去除了旧版的双标签结构。

### Feed — **L2**

已通过 Compose NavHost 路由。`FeedFragment → FeedScreen` 已完成。

### Local — **L2**

已通过 Compose NavHost 路由。

### Downloads — **L2**

`DownloadsActivity` 已使用 `setContent`。

### ContentList / Search — **L2**

`ContentListActivity` 已迁移为 Compose 路由。

### Details — **L1**

| 方面 | 状态 |
|------|------|
| Activity | `DetailsActivity` 仍使用 `ActivityDetailsBinding`（XML binding） |
| Compose 接入 | 通过 `viewBinding.composeView?.setContent { DetailsScreen(...) }` |
| 头部 / 操作 | Compose `DetailsScreen` 已完成：封面、元数据、收藏、分享、翻译、More 菜单、底部 dock |
| 底部 sheet | 仍是 `ChaptersPagesSheet`（Fragment），通过 `supportFragmentManager` 挂载 |
| 转场 | 依赖 XML 层 `imageViewCover` 做共享元素转场 |
| 删除确认 | 已切换为 Compose `AlertDialog` |

**降级原因**：XML Activity 壳 + ComposeView + BottomSheet Fragment 的三层混合体，离 L2 还需去掉 XML binding 和 Fragment sheet。

---

## Dialog / Sheet

| 组件 | 深度 | 状态 |
|------|------|------|
| 详情页作者/标签搜索弹窗 | **L3** | 纯 Compose `AlertDialog` |
| 详情页分享选项弹窗 | **L3** | 纯 Compose `AlertDialog` |
| 详情页收藏分类弹窗 | **L3** | 纯 Compose `FavoriteCategoryDialog` |
| 详情页本地删除确认 | **L3** | 纯 Compose `AlertDialog` |
| 章节视频下载清晰度选择 | **L3** | 纯 Compose `AlertDialog` |
| `DownloadDialogFragment` | **L1** | Compose body (`DownloadDialog`) + `DialogFragment` 外壳，外壳保留用于 Tag 查找兼容 |
| `ContentStatsSheet` | **L1** | Compose body (`ContentStatsSheetContent`) + `BaseAdaptiveSheet` 外壳 |
| `ScrobblingSelectorSheet` | **Legacy** | 完整旧 ViewBinding / RecyclerView / TabLayout 实现。新 Compose 版 `ScrobblingSelectorDialog` 已写好但旧 sheet **未删除**，路由可能仍指向旧版 |
| `ChaptersPagesSheet` | **L1** | 重混合宿主：`BaseAdaptiveSheet` + XML `TabLayout` + Compose `HorizontalPager`（内部 Chapters/Pages/Bookmarks 已为 Compose） |

---

## 设计系统 / Liquid Glass

| 组件 | 状态 |
|------|------|
| `GlassSurface` | ✅ 已实现，但注释标注 "Temporary fallback"，退化为稳定不透明容器 |
| `GlassTopBarContainer` | ✅ 已实现，主壳顶栏使用 |
| `GlassBottomBarContainer` | ✅ 已实现，主壳底栏使用 |
| `GlassCard` | ❌ 文档规划中，代码不存在 |
| `GlassSheet` | ❌ 文档规划中，代码不存在 |

---

## CMP / 共享层

| 方面 | 状态 |
|------|------|
| `shared/` 模块 | ❌ 不存在 |
| `commonMain` 结构 | ❌ 不存在 |
| `feature-*` 模块 | ❌ 不存在 |
| `expect/actual` 桥接 | ❌ 不存在 |

**结论**：CMP 共享层目前完全处于设计方向阶段，无任何工程落地。
