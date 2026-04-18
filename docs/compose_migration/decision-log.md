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

## 2026-04-18：主导航与 UI 架构重构

- **修复状态栏重叠**：修正 `ExploreHostScreen` 和 `FavoritesHostScreen` 根布局因未正确消耗 `contentPadding.calculateTopPadding()` 导致 TabRow 和状态栏重叠的问题。
- **全局内容类型筛选**：将内容筛选器（漫画/小说/动画）在 `appNavGraph` 范围内的默认可见性设为 `true`，使得在所有主页面搜索栏中都可见。
- **重构 Home 页面**：移除由单卡片承载多 Tab 的 `HomeHeroSection`。重构成分别对应“历史记录”、“最新更新”、“建议”三个独立且样式类似 tracking site feed 的 Hero Backdrop Card 轮播组件。
- **重构 Discover 与 Explore（合并发现页）**：废弃了独立的 `DiscoverScreen`，将发现模块的内容合并进了 `ExploreHostScreen` 中，打造了一个上层展示 `SourcesQuickAccessCard`（原 Explore 源横滑列表）、下层紧接 Tracking 系列卡片（原 Discover 横滑卡片）的统一纯粹发现页，路由指向全部重定向，从而精简了层级结构。
