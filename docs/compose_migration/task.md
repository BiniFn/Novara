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

- [ ] 更新 `status-snapshot.md`，使 Details 当前状态、主页面过滤器接线问题、收藏 grid 错位、hero 空封面回归与实际代码保持一致
- [ ] 更新 `decision-log.md`，记录本轮 Details 阅读入口收口、tab 记忆语义化、任务范围从“单点去壳”升级为“联合收口”
- [ ] 更新 `task.md`，把后续工作重排为“功能回归 → 视觉升级 → 产品分工收口”

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

## Phase 2：Browse / Home 视觉升级

### 5. Browse 顶部”每日放松”Hero 重做

- [x] 提升为 Browse 首屏主视觉，位置置顶
- [x] 背景从状态栏开始渲染，弱化卡片边界感
- [x] 正式封面缩小，右侧改为信息列（标题 / 共享状态 / 章节 / 源名 / 分类）
- [x] 超出信息使用省略号而非挤压布局

### 6. 内容源入口改为 2~3 行紧凑网格

- [x] 缩小单个 source 图标按钮视觉尺寸
- [x] 从横向滚动优先改为 2~3 行紧凑展示
- [x] 保持可点击热区足够大

### 7. Browse 追踪网页卡片改信息密集样式

- [x] 从 Hero 式大卡改为小封面 + 下侧标题
- [x] 提升单屏信息量，降低视觉噪音

### 8. Home 快捷入口视觉统一

- [x] 弱化”工具面板感”
- [x] 与 Home 其他卡片保持统一材质、间距和层次

**Critical files**
- `app/src/main/kotlin/org/skepsun/kototoro/discover/ui/compose/DiscoverHeroCarousel.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/explore/ui/compose/ExploreHostScreen.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/explore/ui/compose/ExploreSourcesScreen.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/home/ui/compose/HomeScreen.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/core/ui/compose/HeroComponents.kt`

---

## Phase 3：产品结构与页面分工收口

### 9. Home 与 Browse 的产品建议

**建议：保留 Home + Browse 双页，不做完全合并；采用“弱合并 + 明确分工”。**

- [ ] Home 保留继续阅读 / 历史 / 更新 / 快捷入口等个人仪表盘职责
- [ ] Browse 保留每日放松 Hero、内容源入口、追踪网页、发现型推荐等探索职责
- [ ] 统一卡片语言、空态、封面 fallback、标题信息层级
- [ ] 如需代码层调整，优先做职责收敛而不是路由合并

**Critical files**
- `app/src/main/kotlin/org/skepsun/kototoro/home/ui/compose/HomeScreen.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/explore/ui/compose/ExploreHostScreen.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/main/ui/compose/AppNavGraph.kt`

---

## 暂缓 / 不在本轮范围内

- [ ] Reader / Video 的通用 `ChaptersPagesSheet` 全量 Compose 化
- [ ] 大规模重构 `ViewModel` 层职责
- [ ] 全局导航结构重做或删页
- [ ] 全局卡片系统抽象重写
- [ ] CMP / commonMain 抽取（继续保持远期事项）

---

## 中长期迁移项（保持追踪，但不抢当前优先级）

### Settings 语义源统一
- [ ] 用 typed descriptor / registry 替代 `pref_*.xml` 作为搜索和导航元数据源
- [ ] 补齐设置 DSL 中的 warning card 组件
- [ ] 评估 `SettingsTabbedFragmentsScreen` 是否可用 Compose tabs 替代

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
- [ ] `status-snapshot.md` / `decision-log.md` / `task.md` 三者仍保持“状态 / 历史 / 计划”分工
- [ ] 文档对 Details 当前状态的表述与现有代码一致
- [ ] 文档中的优先级顺序与当前真实需求一致

### 构建 / 静态验证
- [ ] `./gradlew :app:compileDebugKotlin --no-daemon`
- [ ] 如主导航 / 资源 / 布局改动较多，再跑 `./gradlew :app:assembleDebug`

### 端到端检查
- [ ] 主页面搜索栏过滤按钮显示与行为一致
- [ ] 详情页章节 / 页面 / 书签 pane 真正可见、可滚动、可操作
- [ ] 收藏页单卡片出现在首列
- [ ] 历史 / 更新 / 推荐 / Browse hero / 追踪网页无空封面白块
- [ ] Browse Hero / 来源入口 / 追踪卡 / Home 快捷入口符合新的视觉目标
- [ ] 覆盖窄屏、常规宽度和至少一种大屏场景

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
