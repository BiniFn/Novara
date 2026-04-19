# Compose 迁移：后续计划

> 当前状态见 `status-snapshot.md`。历史决策见 `decision-log.md`。
> 本文件只列出**尚未完成、需要推进的工作**。
>
> 近期审查洞察：见 `insights-2026-04-18.md`。

---

## 迁移优先级原则

- 按"桥接深度"推进，而非"是否用了 Compose"
- 目标：逐组件从 L1 → L2 → L3，L4 仅在共享层条件成熟时讨论
- 不强推一次性全仓 MVI 架构迁移，保留当前 ViewModel 风格，重点放在 Route / Screen / platform bridge 边界清晰
- **先修用户可感知回归，再做视觉升级，最后做结构收口**

---

## Step 0（当前优先级最高）：同步迁移文档

> 用户已明确要求先暂停实现，把当前任务和进度更新到 `docs/compose_migration`。

- [x] 更新 `status-snapshot.md`，使 Details 当前状态、主页面过滤器接线问题、收藏 grid 错位、hero 空封面回归与实际代码保持一致
- [x] 更新 `decision-log.md`，记录本轮 Details 阅读入口收口、tab 记忆语义化、任务范围从“单点去壳”升级为“联合收口”
- [x] 更新 `task.md`，把后续工作重排为“功能回归 → 视觉升级 → 产品分工收口”

---

## Phase 1（下一步）：功能与回归修复

### 1. 主页面搜索栏过滤器一致性

- [x] 盘点 `AppNavGraph` 各主路由的 filter callback 注册情况
- [x] 对已走 `AppContentListRoute` 的页面补齐语言预设按钮和源类型按钮策略
- [x] 对 Home / Explore 等非通用列表宿主决定统一策略
  - 决策：统一默认都显示（`isSourceTagFilterVisible` 初始值及 `clearActiveFilters()` 改为 `true`）
- [x] 避免出现“只有内容类型过滤器，其余按钮缺失”的半残状态

**Critical files**
- `app/src/main/kotlin/org/skepsun/kototoro/main/ui/compose/KototoroTopBar.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/main/ui/SearchBarFilterViewController.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/list/ui/compose/AppContentListRoute.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/main/ui/compose/AppNavGraph.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/main/ui/compose/KototoroApp.kt`

### 2. 详情页 pane 内容显示修复

- [x] 保持详情页继续使用 `DetailsScreen` 内的 Compose `ModalBottomSheet`
- [x] 修复 `ModalBottomSheet` / `ChaptersPagesTabsContent` 高度约束，让 pager 内容区真正可见且可滚动
  - `ChaptersPagesToolbar` FilterChip 从竖排 `Column` 改为横排 `Row`，toolbar 高度从 ~200dp 降至 ~48dp
  - `ModalBottomSheet` 添加 `rememberModalBottomSheetState(skipPartiallyExpanded = true)`，避免半展开状态进一步压缩内容区
- [x] 保持 `ChaptersScreenRoot` / `PagesScreenRoot` / `BookmarksScreenRoot` 数据链路复用
- [ ] 保持 Reader / Video 继续走 `AppRouter.showChapterPagesSheet(...)`

**Critical files**
- `app/src/main/kotlin/org/skepsun/kototoro/details/ui/compose/DetailsScreen.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/details/ui/compose/ChaptersPagesTabsContent.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/details/ui/pager/pages/compose/PagesScreenRoot.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/details/ui/pager/bookmarks/compose/BookmarksScreenRoot.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/details/ui/pager/chapters/compose/ChaptersScreenRoot.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/details/ui/pager/ChaptersPagesSheet.kt`

### 3. 收藏页单卡片落第二列

- [x] 修复 `KototoroContentListScreen` 在 grid 模式下让非 `ContentGridModel` 也占位的问题
- [x] 保持 load more 触发逻辑不丢

**Critical files**
- `app/src/main/kotlin/org/skepsun/kototoro/list/ui/compose/KototoroContentListScreen.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/favourites/ui/compose/FavoritesListScreen.kt`

### 4. 空封面 Hero / Backdrop 白块

- [x] 为 Home / Browse / Discover 的 hero/backdrop 统一 fallback 背景逻辑
  - `HeroBackdropCard` 新增 `surfaceContainerHighest -> surfaceContainerLow` 线性渐变兆底层
- [x] 避免 `AsyncImage(model = null)` 直接暴露容器底色
  - 各 poster 缩略图 Box 添加 `.background(surfaceVariant)` fallback
- [x] 统一空封面时的主题渐变 / 低对比占位策略

**Critical files**
- `app/src/main/kotlin/org/skepsun/kototoro/home/ui/compose/HomeScreen.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/discover/ui/compose/DiscoverHeroCarousel.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/explore/ui/compose/ExploreHostScreen.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/core/ui/compose/HeroComponents.kt`

---

## Phase 2（当前优先级最高）：验收回归与交互修复

### 5. Browse 顶部“每日放送”Hero 二次收口

- [ ] 背景真正从状态栏顶部开始渲染，而不是仍被限制在搜索栏下方
- [ ] 统一 Hero 总高度，避免下半部分出现明显空白
- [ ] Hero 下边缘增加平滑渐变，过渡到当前主题背景色（浅色 / 深色）
- [ ] 在“每日放送”区域补上追踪网站切换按钮

### 6. Browse 追踪卡片背景与高度修正

- [ ] 其他追踪卡片补齐下边缘渐变过渡，避免生硬截断到主背景
- [ ] 整体高度继续压缩，避免信息密度不足和大面积空白
- [ ] 保持封面 / 标题信息在窄屏与常规宽度下都不过度拉伸

### 7. 主页面搜索栏过滤器真正一致

- [ ] 除 Favorites 外，为 Home / Explore / History / Feed / Local 等主页面补齐语言预设按钮
- [ ] 修正横屏下搜索栏按钮组居中问题，改为靠右对齐
- [ ] 修复搜索栏“更多”按钮点击无响应
- [ ] 复查语言预设 / 内容类型 / 源类型 / 更多按钮在各主页面的显示与行为一致性

### 8. Details 首帧与 pane 交互回归

- [ ] 详情页首次进入时封面即保持圆角，避免先直角后圆角
- [ ] 统一底部工具栏与弹出控件的交互模型，避免悬浮工具栏却弹出可拖拽 sheet 的割裂感
- [ ] 修复章节 / 页面 / 书签 pane 的实际内容显示，确保真正出现章节列表而非空白或错误内容
- [ ] 继续保持 Reader / Video 入口不被本轮修复回归影响

### 9. Home 卡片改为可横向拖拽列表

- [ ] 将历史 / 更新 / 推荐改为可左右拖拽的卡片列表
- [ ] 卡片样式统一为“封面 + 下方名称”
- [ ] 在窄屏、常规宽度和大屏下验证拖拽、分页与点击行为

### 10. 固定导航栏 UI 的底部 inset 修复

- [ ] 开启“设置 → 外观 → 固定导航栏 UI”后，主页面底部内容应正好滚到导航栏上方
- [ ] 复查 Home / Explore / History / Favorites / Feed / Local 等主页面的 bottom content padding

**Critical files**
- `app/src/main/kotlin/org/skepsun/kototoro/discover/ui/compose/DiscoverHeroCarousel.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/explore/ui/compose/ExploreHostScreen.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/home/ui/compose/HomeScreen.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/main/ui/compose/KototoroTopBar.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/main/ui/compose/AppNavGraph.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/main/ui/SearchBarFilterViewController.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/details/ui/compose/DetailsScreen.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/details/ui/compose/ChaptersPagesTabsContent.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/details/ui/pager/ChaptersPagesSheet.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/core/ui/compose/HeroComponents.kt`

---

## Phase 3（当前进行中）：Details / Tracking 体验统一

### 11. 普通详情页与追踪详情页统一绑定体验

- [x] 修复当前 working tree 中 details/tracking 改造产生的编译阻塞
  - `DetailsBindingCard.kt` 的 Compose layout import 缺失
  - `DetailsHeader.kt` 缺少 `stringArrayResource` import
  - `TrackingSiteDetailsActivity.kt` 缺少 `router` import
- [ ] 完成普通详情页顶部绑定卡片收口
  - 已开始接入“已绑定 tracking 卡片”和“推荐绑定卡片”
  - 仍需校正解绑语义，不应把已绑定卡片的移除动作继续绑在 `trackingSuggestion?.isLinked`
  - 仍需确认多绑定场景是否展示首个卡片还是支持多卡片
- [x] 将 tracking 详情页正式 Compose 化并与普通详情页视觉对齐
  - `TrackingSiteDetailsActivity` 已改为 `AppCompatActivity` + `setContent {}` 纯 Compose host
  - `TrackingSiteDetailsScreen` 已实现 collapseProgress / panorama blur / graphicsLayer 动画
  - `TrackingSiteDetailsFragment` 已改为 ComposeView host
  - 旧 `activity_tracking_site_details.xml` 已删除
- [ ] 扩展 tracking 自动推荐绑定逻辑
  - 当前 `DetailsViewModel.refreshTrackingMatchSuggestion()` 仍只对 `content.isLocal` 生效
  - 需要扩展到非本地内容，并补齐内容类型过滤、优先站点策略和缓存复用
- [ ] 打通双向跳转与绑定管理体验
  - 普通详情页：打开 tracking 详情 / 进入绑定管理 / 绑定建议 / 解除匹配
  - tracking 详情页：打开本地详情 / 进入绑定管理 / 打开外部页面

### 12. 文档交接要求

- [ ] 在 `status-snapshot.md` 中持续维护 details/tracking 的真实迁移状态与当前阻塞
- [ ] 在 `decision-log.md` 中记录“追踪详情页必须 Compose 化”和共享绑定卡片方案的后续决策
- [ ] 所有后续推进者在继续实现前，先以 `./gradlew :app:compileDebugKotlin --no-daemon` 作为最小校验基线

---

## 暂缓 / 不在本轮范围内

- [ ] Reader / Video 的通用 `ChaptersPagesSheet` 全量 Compose 化
- [ ] 大规模重构 `ViewModel` 层职责
- [ ] 全局导航结构重做或删页
- [ ] 全局卡片系统抽象重写
---

## Phase 4（全新高优先级）：Settings 系统彻底 Compose 化与去壳

> 目标：构建 Compose Settings 脚手架，彻底移除所有 `preference_*.xml` 布局、`BasePreferenceFragment` 壳以及 `androidx.preference` 依赖。

### 13. 基础 Component 脚手架搭建
- [x] 实现 `PreferenceCategory`、`TextPreference`、`SwitchPreference` 等基础声明式 UI 组件
- [x] 实现 `ListPreference` / `MultiSelectListPreference`、`SliderPreference` 组件及带弹窗或内联操作的交互
- [ ] 建立统一的 `SettingsScreenScaffold` (包含 TopAppBar, 通用 Padding 与 Insets 规避处理)

### 14. 路由、存储层桥接与搜索源替换
- [x] AI/翻译模块：将 `pref_ai.xml`, `pref_ai_image.xml`, `pref_translation.xml`, `pref_translation_api.xml` 改为纯 Kotlin Key Lists
- [x] 利用 `observeAsState` + `SharedPreferences.edit {}` 搭建设置数据持久化绑定桥
- [ ] 彻底重写 `SettingsSearchHelper`，改变其从零散 XML 提取搜索提示的历史路径（已部分完成：AI/翻译模块）

### 15. 各大子设置页面的重写映射
- [x] `AISettingsScreen` — 纯路由页，导航到各子页面
- [x] `TranslationSettingsScreen` — 完整 Compose 化，含 OCR/Bubble/Pipeline 全部设置项
- [x] `TranslationApiSettingsScreen` — 含 endpoint/key/model/custom headers 输入
- [x] `AIImageEnhancementSettingsScreen` — 含引擎/模式/模型/缓存设置项
- [ ] 核心单页（Storage, Downloads, Tracking, Sync 等）映射翻译为 Compose DSL
- [ ] 多子页与深层源管理（ExtensionsRoot, JsonSources 等）的纯 Compose 化跳转
- [ ] 将相关入口整合至主 `NavHost` 结构中

### 16. 历史包袱最终清理
- [x] 清理 `pref_ai.xml`, `pref_ai_image.xml`, `pref_translation.xml`, `pref_translation_api.xml`
- [ ] 清理剩余 `preference_*.xml`（`pref_ai_video.xml` 等）与 `fragment_settings_sources.xml`
- [ ] 删除不再需要的各种设置 `Fragment` 基类和实现
- [ ] 清理 `build.gradle` 内的传统 AndroidX Preference 依赖项

---

## 中长期迁移项（保持追踪，但不抢当前优先级）

### Details / Dialog / Sheet 深化去壳
- [ ] `DetailsActivity` 从 `ActivityDetailsBinding` 迁到 `setContent {}`
- [ ] 共享元素转场从 XML `imageViewCover` 迁到 Compose 方案
- [ ] `DownloadDialogFragment` 去壳
- [ ] `ContentStatsSheet` 去壳
- [ ] `ScrobblingSelectorSheet` 随上层 Compose 化一并移除

### Liquid Glass 组件补齐
- [ ] 为 `GlassSurface` 接入真实 haze / blur 后端
- [ ] 实现 `GlassCard`
- [ ] 实现 `GlassSheet`

### 共享层 / CMP
- [ ] 评估 `shared/designsystem` 模块拆分
- [ ] 评估首批 feature module 的 commonMain 可行性
- [ ] 建立 `expect/actual` 平台桥接基础架构

---

## 验证

### 文档校验
- [x] `status-snapshot.md` / `decision-log.md` / `task.md` 三者仍保持“状态 / 历史 / 计划”分工
- [x] 文档优先级顺序已按 2026-04-19 本轮用户验收反馈重排
- [x] 已将“已完成但仍存在回归/未收口”的事项重新打开

### 构建 / 静态验证
- [ ] `./gradlew :app:compileDebugKotlin --no-daemon`
- [ ] 如主导航 / 资源 / 布局改动较多，再跑 `./gradlew :app:assembleDebug`

### 端到端检查
- [ ] Browse Hero 真正延伸到状态栏，且底部过渡自然
- [ ] 追踪卡片高度压缩且下边缘过渡自然
- [ ] 各主页面搜索栏都有语言预设按钮，横屏按钮组靠右，“更多”按钮可点击
- [ ] 详情页封面首帧即圆角
- [ ] 详情页章节 / 页面 / 书签 pane 显示真实内容，且交互模型统一
- [ ] Home 的历史 / 更新 / 推荐支持横向拖拽卡片列表
- [ ] 开启固定导航栏 UI 后，底部内容不再被导航栏遮挡
- [ ] 覆盖窄屏、常规宽度、横屏和至少一种大屏场景

---

## 已完成（2026-04-18 本轮之前）

- [x] `AppContentListRoute.onLoadMore` 从 no-op 改回参数化回调，收藏夹 / Suggestions / Updates / Local 分页恢复
- [x] `FavoritesHostScreen` 顶部 padding 双重应用修复
- [x] Home → History / Favorites / Local 导航改走 NavHost
- [x] `DetailsActivity` 共享元素转场：去掉硬编码 350 ms `postDelayed`，仅保留图片加载回调 + 1200 ms fallback
- [x] `KototoroTopBar.TopBarAnchorIconButton` 弃用隐藏 AndroidView，改用 `LocalView`
- [x] 恢复 `MainActivity.onFirstStart()` 首启初始化调用
- [x] `HomeScreen.kt` 大量死代码清理
- [x] 删除 `download/ui/dialog/compose/DownloadDialog.kt` 孤立副本
- [x] 删除 `DetailsMenuProvider.kt`
- [x] `MainActivity` 大量空 / 透传方法与死字段清理
- [x] `ReadButtonDelegate` / `FavouritesContainerViewModel` / `DiscoverViewModel` 生产日志清理
- [x] `KototoroContentListScreen` 空态文案资源化
- [x] `ReadButtonDelegate` 与 `DetailsActivity` 已共用 `openDetailsReader(...)`
- [x] `ChaptersPagesSheet` 已改为持久化语义 tab id，而非 pager index
- [x] 详情页 Download 动作已重新接回 Compose `DetailsScreen`
- [x] `./gradlew :app:compileDebugKotlin --no-daemon` 在上述修复后通过
