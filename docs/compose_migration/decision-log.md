# Compose 迁移：决策记录

> 本文件只记录历史决策和校线事件。
> 当前状态看 `status-snapshot.md`，后续工作看 `task.md`。

---

## 2026-04-16：启动迁移基线

- 新建迁移文档体系。
- 确定迁移原则：KISS / YAGNI / DRY / 边界清晰。
- 明确非目标：第一阶段不迁移阅读器/播放器，不承诺 Web，不强推 WebView/认证/通知/Widget 进 commonMain。
- 建立 `GlassSurface` / `GlassTopBarContainer` / `GlassBottomBarContainer` 基础组件。
- 开始 Settings Phase：`RootSettingsFragment` 从 `PreferenceFragmentCompat` 切到 Compose 入口。
- 首批设置页切到 Compose 渲染：StorageAndNetwork / Appearance / Services / Downloads / Tracker / Sources。

## 2026-04-17：主壳重构 + 详情页深化

### 主壳架构变更

- 决策：主壳从 XML `FragmentContainerView` + Compose 包裹模式切到 Compose `NavHost`。
- `MainActivity` 移除 `MainNavigationDelegate` 和深层 `FragmentContainerView` 依赖。
- 引入 `AppNavGraph.kt` 接管主导航。
- 以 `FragmentHostRoute` 作为过渡壳，再逐步将其从主路由中清空。

### 详情页进展

- `DetailsScreen` 补齐更多 Compose 入口：分享、下载、来源跳转、作者/标签动作、翻译切换、底部阅读 dock。
- `ChaptersPagesSheet` 的 tab 切换迁到 Compose 协程上下文，修复 `MonotonicFrameClock` 崩溃。
- `DetailsScreen` 的 overflow menu 切到 Compose。

## 2026-04-18：Dialog/Sheet Compose 化 + 高频页收口

- `ScrobblingSelectorDialog` Compose 版完成。
- `ContentStatsSheetContent` Compose 版完成。
- `DownloadDialog` Compose body 完成，`DownloadDialogFragment` 退化为轻量壳。
- `DownloadsActivity` 切到 Compose `DownloadsScreen`。
- `FavouritesContainerFragment` 重写为 `FavoritesHostScreen`。
- `ExploreFragment` / `ExploreSourcesFragment` 进入 Compose host 路径。
- `ContentListActivity` 迁为 Compose route。
- 主路由中的 `FragmentHostRoute` 引用被清空。

## 2026-04-18（晚）：首轮全量审查与 P0 清理

- 以代码对比文档的方式产出 `insights-2026-04-18.md`。
- 接回 `AppContentListRoute.onLoadMore`，恢复分页。
- 修复 Favorites 顶部双重 padding。
- 删除孤立的旧 `download/ui/dialog/compose/DownloadDialog.kt`。
- 删除 `DetailsMenuProvider`。
- 恢复 `MainActivity.onFirstStart()` 调用链。
- 主壳顶栏 anchor 从隐藏 `AndroidView` 改为 Compose 实现。

## 2026-04-18（夜）：详情页 pane 收口

- 确认普通详情页主路径里的章节 / 页面 / 书签入口已经迁入 `DetailsScreen` 内部的 Compose `ModalBottomSheet`。
- 决策：`ChaptersPagesSheet` 不再视作普通详情页主 pane 宿主，只保留给 Reader / Video 等兼容路径。
- 修复 pane 内容高度被 toolbar 挤压的问题。

## 2026-04-19：Tracking 统一体验与 Settings Compose 扩张

- 决策：tracking 详情页必须 Compose 化，不能停留在 Fragment/XML 终态。
- 引入共享绑定卡片模型：`LinkedTrackingItemUiModel` + `DetailsBindingCard`。
- `TrackingSiteDetailsActivity` 改为 `AppCompatActivity` + `setContent {}`。
- AI/翻译/TTS/OCR/E2E 设置页继续迁到 Compose Screen/ComposeView 宿主。
- `SettingsSearchHelper` 大量改为 Kotlin key list，逐步摆脱 XML 搜索索引。

## 2026-04-21：按当前代码重新校线文档基线

### 校线动作

- 重新以代码事实而不是 2026-04-19 的验收反馈文案更新 `docs/compose_migration`。
- 执行 `./gradlew :app:compileDebugKotlin --no-daemon`，结果为 **BUILD SUCCESSFUL**，因此本轮文档以“当前主线可编译”为前提。

### 新确认的事实

- `MainActivity` 仍然是 `BaseActivity<ActivityMainBinding>` + `setContentViewWebViewSafe` 宿主，主壳状态保持 **L2**。
- `DetailsActivity` 仍然是 `BaseActivity<ActivityDetailsBinding>` + `composeView.setContent {}` 宿主，普通详情页状态回到更保守的 **L1** 表述。
- `DetailsActivity.onActionClick` 中仍保留 `Download` / `OpenTracking` / `OpenStatistics` / 若干 toggle action 的空分支，之前文档里“已删除 Activity 端死分支”的表述不能再作为当前事实保留。
- `TrackingSiteDetailsActivity` 的 Compose host 迁移仍成立，tracking 详情页维持 **L2**。
- `BasePreferenceFragment` 虽然还保留类定义，但当前仓库内已无任何子类或直接使用点；设置系统的 legacy 债务已收敛到 `SettingsTabbedFragmentsScreen` 和 `SourceSettingsFragment`。
- 设置迁移剩余的 XML 资源已明显收缩，当前重点是 `pref_source.xml`、`pref_source_parser.xml`、`pref_root.xml`、`pref_sync_header.xml`。
- `SearchBarFilterViewController.Callback` 的默认过滤器可见性为全开，`MainActivity.clearActiveFilters()` 也会把语言预设 / 内容类型 / 源标签全部重置为可见；因此先前文档中那批“主壳过滤器默认缺失”的描述不再适合作为当前状态记录。

### 文档策略调整

- `status-snapshot.md` 以后只保留**代码可直接验证**的现状。
- 设备验收型问题只有在代码仍能直接支持该判断，或者再次复现后，才继续写入“当前状态”。
- `task.md` 改为围绕当前真正还没完成的迁移项排优先级，不再保留已经被代码现实推翻的旧待办。

## 2026-04-21（晚）：补齐 Compose 回归兼容修复

- `KototoroContentCard` 的来源角标从悬浮胶囊回调到贴合封面左下角的 badge 形态，避免与卡片圆角错位。
- Compose 侧来源 favicon 请求与旧 `FaviconView` 对齐，补上 `.suppressCaptchaErrors()`，减少来源图标在受验证码/Cloudflare 影响时的失败噪音。
- `BackupRepository.restoreBackup()` 仍通过 `AppSettings.upsertAll()` 恢复设置，但 `upsertAll()` 现在会在写入后立即执行 legacy UI 偏好清洗。
- 当前清洗范围覆盖导航栏高度、网格尺寸、全景封面参数、popup radius、glass opacity、搜索建议类型和列表 badge。
- 决策：旧备份兼容先采用“getter 安全读取 + restore 后立即回写规范值”的低侵入策略，不在这一步引入新的备份 schema。
- 补充决策：直接从 pre-Compose 旧包覆盖安装升级时，不走备份恢复链路，因此同一套 legacy UI 偏好清洗也要挂到应用启动；当前通过 `KEY_APP_VERSION` 与 `BuildConfig.VERSION_CODE` 对比，在版本变化时执行一次规范化并写回当前版本。

## 2026-04-21（夜）：主页 / 顶栏 / 详情页 Compose 体验修补

- 首页三合一卡片内部补齐水平 padding，历史 / 更新 / 推荐三段的标题行与卡片内容不再贴左边缘。
- 决策：各列表页原先分散在更多菜单里的“展示视图 + 网格大小”统一收口为单一 `Display options` 入口，并以下拉浮层承载控件。
- 详情页翻译按钮改为只在“作品语言 != 当前目标翻译语言”时显示，避免无效常驻。
- 详情页翻译按钮补上长按提示，明确行为是“翻译名称和简介”。
- 详情页紧凑底部 pane 改为可居中悬浮，并把全屏过程中的遮罩/容器透明度改为连续插值，避免滑到全屏时突变成纯黑。
- Compose 来源图标进一步抽成 `ContentSourceIcon` 组件，开始从来源卡片和封面角标路径统一复用。
- 同轮确认：tracking 站点缓存逻辑当前没有发现结构性问题，详情数据走 Room，发现分类走 TTL 本地缓存。
- Compose 主链路里的详情跳转开始透传根视图 anchor，先恢复 Hero 开关的基础转场效果；但共享元素级别的精确锚点仍是后续项。
- 后续继续收口时，选择了“不依赖系统共享元素视图树”的更稳路径：主列表点击时缓存封面 bounds，详情页启动后复用已有 `imageViewCover + syncCoverBounds(...)` 机制做自绘封面过渡。
- 同轮继续把 `Rect?` 透传能力抬升到 `KototoroContentCard`、`DiscoverHeroCarousel`、Home 卡片和 Feed 卡片，确保非统一 Compose 卡片也能把封面 bounds 传到详情页。
- 补充边界决策：`KototoroExploreHostRoute` 中主路径进入 tracking 详情或外部浏览器的卡片，不强行接入 `DetailsCoverTransitionStore`，避免把只对 `DetailsActivity` 生效的缓存塞进错误链路。
