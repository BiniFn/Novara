  # Kototoro UI 改进方案                                                                                                                                                                                                                                                                                                           
                                                                                                                                                                                                                                                                                                                                 
  ## 文档版本                                                                                                                                                                                                                                                                                                                      
  - 创建日期：2026-03-18                                                                                                                                                                                                                                                                                                           
  - 最后更新：2026-03-19                                                                                                                                                                                                                                                                                                           
  - 状态：进行中（部分方案已落地）                                                                                                                                                                                                                                                                                                                 
                                                                                                                                                                                                                                                                                                                                   
  ---

  ## 目录
  1. [主页（Dashboard）设计](#1-主页dashboard设计)
  2. [导航栏优化](#2-导航栏优化)
  3. [过滤器界面改进](#3-过滤器界面改进)
  4. [追踪站点集成](#4-追踪站点集成)
  5. [实现优先级](#5-实现优先级)

  ---

  ## 1. 主页（Dashboard）设计

  ### 1.1 设计目标
  - 提供统一的信息聚合中心
  - 快速访问常用功能
  - 支持多种内容类型和来源的展示
  - 降低导航复杂度

  ### 1.2 页面结构

  ┌─────────────────────────────────────┐
  │  🔍 搜索框                           │  ← AppBar
  ├─────────────────────────────────────┤
  │  📊 同步状态卡片                     │
  │  ┌─────────────────────────────────┐│
  │  │ WebDAV 同步                      ││
  │  │ ✓ 已同步 · 2分钟前               ││
  │  │ [立即同步]                       ││
  │  └─────────────────────────────────┘│
  │                                      │
  │  📖 历史 (8)         [筛选][更多]   │
  │  ┌─────┬─────┬─────┬─────┬─────┐   │
  │  │     │     │     │     │     │ → │  ← 横向滚动
  │  └─────┴─────┴─────┴─────┴─────┘   │
  │                                      │
  │  ⭐ 更新 (8)          [筛选][更多]   │
  │  ┌─────┬─────┬─────┬─────┬─────┐   │
  │  │     │     │     │     │     │ → │  ← 横向滚动
  │  └─────┴─────┴─────┴─────┴─────┘   │
  │                                      │
  │  🔔 推荐 (8)          [筛选][更多]   │
  │  ┌─────┬─────┬─────┬─────┬─────┐   │
  │  │     │     │     │     │     │ → │  ← 横向滚动
  │  └─────┴─────┴─────┴─────┴─────┘   │
  │                                      │
  │  🔌 源管理                    [管理>]│
  │  ┌─────────────────────────────────┐│
  │  │ 已启用: 12 个源                  ││
  │  │ 内置(3) · Mihon(4) · Legado(5)  ││
  │  └─────────────────────────────────┘│
  └─────────────────────────────────────┘

  ### 1.3 卡片设计

  #### 1.3.1 同步状态卡片
  - 显示 WebDAV/云同步状态
  - 最后同步时间
  - 快速同步按钮
  - 同步冲突提示

  #### 1.3.2 历史卡片
  - 横向滚动封面列表
  - 默认预览 8 个最近阅读记录
  - 封面 + 标题 + 阅读进度
  - 点击直接继续阅读
  - 过滤器右侧提供紧凑型"更多"按钮
  - "更多"按钮跳转到完整历史页面

  #### 1.3.3 更新卡片
  - 横向滚动封面列表
  - 默认预览 8 个最近更新作品
  - 封面 + 更新信息
  - 点击跳转到详情页
  - 过滤器右侧提供紧凑型"更多"按钮
  - "更多"按钮跳转到完整更新页面

  #### 1.3.4 订阅动态卡片
  - 显示订阅源的更新摘要
  - 源名称 + 更新数量
  - 点击查看该源的更新列表
  - "更多"按钮跳转到完整订阅页面

  #### 1.3.5 源管理卡片
  - 显示已启用源的统计
  - 按源类型分组显示数量
  - 快速启用/禁用常用源
  - "管理"按钮跳转到源设置

  ### 1.4 技术实现

  #### 1.4.1 数据模型
  ```kotlin
  data class DashboardState(
      val syncStatus: SyncStatus,
      val recentHistory: List<HistoryItem>,
      val favouriteUpdates: List<UpdateItem>,
      val feedSummary: List<FeedSummary>,
      val sourcesStats: SourcesStats
  )

  data class SyncStatus(
      val isEnabled: Boolean,
      val lastSyncTime: Long?,
      val isSyncing: Boolean,
      val hasConflicts: Boolean
  )

  data class UpdateItem(
      val content: Content,
      val newChaptersCount: Int,
      val latestChapter: String
  )

  data class FeedSummary(
      val source: ContentSource,
      val updatesCount: Int
  )

  data class SourcesStats(
      val totalEnabled: Int,
      val byType: Map<SourceType, Int>
  )

  1.4.2 ViewModel

  @HiltViewModel
  class HomeViewModel @Inject constructor(
      private val historyRepository: HistoryRepository,
      private val favouritesRepository: FavouritesRepository,
      private val feedRepository: FeedRepository,
      private val sourcesRepository: ContentSourcesRepository,
      private val syncCoordinator: BackupStartupCoordinator
  ) : ViewModel() {

      val dashboardState: StateFlow<DashboardState> = combine(
          historyRepository.observeRecentHistory(limit = 10),
          favouritesRepository.observeUpdates(),
          feedRepository.observeSummary(),
          sourcesRepository.observeStats(),
          syncCoordinator.observeSyncStatus()
      ) { history, updates, feed, sources, sync ->
          DashboardState(
              syncStatus = sync,
              recentHistory = history,
              favouriteUpdates = updates,
              feedSummary = feed,
              sourcesStats = sources
          )
      }.stateIn(
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(5000),
          initialValue = DashboardState.Empty
      )
  }

  1.4.3 Fragment

  @AndroidEntryPoint
  class HomeFragment : BaseFragment<FragmentHomeBinding>() {

      private val viewModel by viewModels<HomeViewModel>()

      override fun onViewBindingCreated(binding: FragmentHomeBinding, savedInstanceState: Bundle?) {
          super.onViewBindingCreated(binding, savedInstanceState)

          val adapter = HomeDashboardAdapter(
              onHistoryClick = { router.openReader(it) },
              onUpdateClick = { router.openDetails(it) },
              onFeedClick = { router.openFeed(it) },
              onSyncClick = { viewModel.triggerSync() },
              onManageSourcesClick = { router.openSourceSettings() }
          )

          binding.recyclerView.adapter = adapter

          viewModel.dashboardState.observe(viewLifecycleOwner) { state ->
              adapter.submitList(state.toCardList())
          }
      }
  }

  1.5 卡片配置

  - 用户可以自定义显示哪些卡片
  - 可以调整卡片顺序
  - 可以设置每个卡片显示的数量

  ---
  2. 导航栏优化

  2.1 当前问题

  - 导航项过多（历史、收藏、浏览、订阅 + 可选项）
  - 最多支持 6 个导航项，容易拥挤
  - 新增主页后需要重新规划

  2.2 推荐方案：精简导航栏

  2.2.1 默认导航栏（5项）

  ┌─────────────────────────────────────┐
  │ [🏠]  [📖]  [⭐]  [🔍]  [🔔]        │
  │ 主页  历史  收藏  浏览  订阅         │
  └─────────────────────────────────────┘

  - 主页 (Home)：新增，作为默认启动页
  - 历史 (History)：高频使用
  - 收藏 (Favourites)：高频使用
  - 浏览 (Browse/Explore)：发现新内容
  - 订阅 (Feed)：追踪更新

  2.2.2 其他功能访问方式

  - 本地 (Local)：通过主页的源管理卡片或设置访问
  - 建议 (Suggestions)：通过主页或浏览页面访问
  - 书签 (Bookmarks)：通过详情页或阅读器访问
  - 更新 (Updates)：合并到订阅页面
  - 发现 (Discover)：新增独立页面（见第4节）

  2.2.3 可配置导航栏

  保留现有的 settings.mainNavItems 配置功能：
  - 用户可以从 8-10 个可选项中选择
  - 最多显示 5-6 个导航项
  - 支持拖拽排序
  - 主页建议默认开启但可选

  2.3 导航栏实现

  2.3.1 NavItem 枚举扩展

  enum class NavItem(
      @IdRes val id: Int,
      @StringRes val title: Int,
      @DrawableRes val icon: Int,
      val isDefault: Boolean = false
  ) {
      HOME(R.id.nav_home, R.string.home, R.drawable.ic_home, isDefault = true),
      HISTORY(R.id.nav_history, R.string.history, R.drawable.ic_history, isDefault = true),
      FAVORITES(R.id.nav_favorites, R.string.favourites, R.drawable.ic_favourite, isDefault = true),
      EXPLORE(R.id.nav_explore, R.string.explore, R.drawable.ic_explore, isDefault = true),
      FEED(R.id.nav_feed, R.string.feed, R.drawable.ic_feed, isDefault = true),
      DISCOVER(R.id.nav_discover, R.string.discover, R.drawable.ic_discover, isDefault = false),
      LOCAL(R.id.nav_local, R.string.local_storage, R.drawable.ic_storage, isDefault = false),
      SUGGESTIONS(R.id.nav_suggestions, R.string.suggestions, R.drawable.ic_suggestions, isDefault = false),
      BOOKMARKS(R.id.nav_bookmarks, R.string.bookmarks, R.drawable.ic_bookmark, isDefault = false),
      UPDATED(R.id.nav_updated, R.string.updated, R.drawable.ic_updated, isDefault = false);

      companion object {
          fun getDefaultItems(): List<NavItem> = entries.filter { it.isDefault }
      }
  }

  2.3.2 MainNavigationDelegate 更新

  private fun onNavigationItemSelected(@IdRes itemId: Int): Boolean {
      val newFragment = when (itemId) {
          R.id.nav_home -> HomeFragment::class.java
          R.id.nav_history -> HistoryListFragment::class.java
          R.id.nav_favorites -> FavouritesContainerFragment::class.java
          R.id.nav_explore -> ExploreFragment::class.java
          R.id.nav_feed -> FeedFragment::class.java
          R.id.nav_discover -> DiscoverFragment::class.java  // 新增
          R.id.nav_local -> LocalListFragment::class.java
          R.id.nav_suggestions -> SuggestionsFragment::class.java
          R.id.nav_bookmarks -> AllBookmarksFragment::class.java
          R.id.nav_updated -> UpdatesFragment::class.java
          else -> return false
      }
      // ...
  }

  2.4 启动页设置

  - 默认启动页：主页
  - 用户可以在设置中修改默认启动页
  - 记住上次退出时的页面（可选）

  ---
  3. 过滤器界面改进

  3.1 当前问题

  - 两组 Chip（内容类型 + 源类型）在同一行，容易溢出
  - 使用 HorizontalScrollView，但体验不够直观
  - 搜索框在 AppBar 中，与过滤条分离

  3.2 源类型说明

  - 内容类型：漫画、小说、视频（媒体类型）
  - 源类型：内置源、Mihon源、Aniyomi源、Legado源、TVBox源（插件类型）

  3.3 兼容性矩阵

                内置源  Mihon源  Aniyomi源  Legado源  TVBox源
  漫画            ✓       ✓        ✗         ✓        ✗
  小说            ✓       ✗        ✗         ✓        ✗
  视频            ✓       ✗        ✓         ✗        ✓

  3.4 推荐方案：两行紧凑图标布局

  3.4.1 界面设计

  ┌─────────────────────────────────────┐
  │  🔍 搜索框                           │  ← AppBar
  ├─────────────────────────────────────┤
  │  [📚] [📖] [🎬]                      │  ← 内容类型（紧凑图标）
  │  [内置] [Mihon] [Aniyomi] [Legado] [TV] │  ← 源类型（紧凑图标）
  ├─────────────────────────────────────┤
  │  [分类标签页...]                     │  ← TabLayout
  └─────────────────────────────────────┘

  3.4.2 设计要点

  1. 使用纯图标 Chip（无文字，只有图标 + Tooltip）
  2. 两行固定布局，不使用 HorizontalScrollView
  3. 自动禁用不兼容的选项（已实现）
  4. 每个 Chip 宽度约 40-48dp，5个源类型总宽度约 240dp，不会溢出

  3.4.3 布局实现

  <LinearLayout
      android:id="@+id/filterChipsContainer"
      android:layout_width="match_parent"
      android:layout_height="wrap_content"
      android:orientation="vertical"
      android:paddingHorizontal="@dimen/list_spacing_normal"
      android:paddingVertical="8dp">

      <!-- 内容类型行 -->
      <com.google.android.material.chip.ChipGroup
          android:id="@+id/chipGroupContentType"
          android:layout_width="match_parent"
          android:layout_height="wrap_content"
          app:chipSpacingHorizontal="8dp"
          app:singleLine="true"
          app:singleSelection="true"
          app:selectionRequired="false" />

      <!-- 源类型行 -->
      <com.google.android.material.chip.ChipGroup
          android:id="@+id/chipGroupSourceTag"
          android:layout_width="match_parent"
          android:layout_height="wrap_content"
          android:layout_marginTop="8dp"
          app:chipSpacingHorizontal="8dp"
          app:singleLine="true"
          app:singleSelection="false"
          app:selectionRequired="false" />

  </LinearLayout>

  3.4.4 Chip 样式优化

  private fun createCompactChip(
      text: String,
      @DrawableRes iconRes: Int,
      colors: ChipColors,
      density: Float,
  ): Chip {
      return Chip(requireContext()).apply {
          id = View.generateViewId()
          this.text = ""  // 关键：不显示文字
          contentDescription = text
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
              tooltipText = text  // 长按显示完整名称
          }

          isCheckable = true
          isChipIconVisible = true
          setChipIconResource(iconRes)
          chipIconSize = 24f * density  // 稍大的图标

          // 更紧凑的尺寸
          chipMinHeight = 40 * density
          minHeight = 0
          chipStartPadding = 12 * density  // 左右各12dp
          chipEndPadding = 12 * density
          textStartPadding = 0f
          textEndPadding = 0f
          setEnsureMinTouchTargetSize(true)  // 保持48dp触摸区域

          chipStrokeWidth = 0f
          chipBackgroundColor = colors.bg
          setTextColor(colors.text)
      }
  }

  3.5 替代方案

  3.5.1 方案 B：底部菜单 + 指示器

  ┌─────────────────────────────────────┐
  │  🔍 搜索框          [🎯 过滤器]      │  ← AppBar
  ├─────────────────────────────────────┤
  │  当前过滤: 漫画 · 内置 · Mihon  ×   │  ← 过滤指示器
  ├─────────────────────────────────────┤
  │  [分类标签页...]                     │
  └─────────────────────────────────────┘

  点击"过滤器"按钮弹出 BottomSheet，包含所有过滤选项。

  优点：
  - 界面最简洁，不占用垂直空间
  - 支持更复杂的过滤逻辑
  - 当前过滤状态清晰可见

  缺点：
  - 需要额外点击才能修改过滤

  3.5.2 方案 C：智能折叠

  默认状态下隐藏过滤条，点击按钮展开，已过滤时显示过滤指示器。

  3.6 图标资源需求

  需要为以下源类型设计图标：
  - ic_source_builtin.xml - 内置源
  - ic_source_mihon.xml - Mihon 源
  - ic_source_aniyomi.xml - Aniyomi 源
  - ic_source_legado.xml - Legado 源
  - ic_source_tvbox.xml - TVBox 源

  ---
  4. 追踪站点集成

  4.1 站点无关产品层

  4.1.1 功能概述

  集成 Bangumi 等二次元追踪网站，提供：
  - 权威的内容库和元数据
  - 排行榜和推荐
  - 跨源内容聚合
  - 阅读进度同步
  - 当前进度：`Discover`、追踪站点详情页、详情页追踪增强与本地缓存骨架已落地，后续继续补同步增强与更完整的多站点交互

  4.1.2 新增"发现"页面（Discover）

  4.1.2.1 页面结构

  ┌─────────────────────────────────────┐
  │  🔍 搜索发现内容                     │
  ├─────────────────────────────────────┤
  │  [📚 漫画] [📖 小说] [🎬 动画]       │  ← 内容类型切换
  ├─────────────────────────────────────┤
  │  📊 排行榜                           │
  │  ┌─────┬─────┬─────┬─────┐         │
  │  │ 1   │ 2   │ 3   │ 4   │ →       │  ← 横向滚动
  │  └─────┴─────┴─────┴─────┘         │
  │                                      │
  │  🔥 本季新番                         │
  │  ┌─────┬─────┬─────┬─────┐         │
  │  │     │     │     │     │ →       │
  │  └─────┴─────┴─────┴─────┘         │
  │                                      │
  │  🏷️  按标签浏览                       │
  │  [恋爱] [冒险] [科幻] [更多...]      │
  │                                      │
  │  📅 按年份/季度                      │
  │  [2026] [2025] [2024]...            │
  └─────────────────────────────────────┘

  4.1.2.2 功能模块

  1. 排行榜：显示追踪站点的热门作品（默认 Bangumi）
  2. 本季新番：当季新作品
  3. 标签浏览：按类型、标签筛选
  4. 年份/季度：按时间筛选
  5. 搜索：搜索追踪站点数据库（默认 Bangumi，当前已落地 MVP）

  4.1.3 追踪站点详情页设计

  当前实现已落地最小详情页容器，可展示远端详情、已绑定状态、本地入口、绑定/管理入口与站外打开入口。

  4.1.3.1 页面结构

  ┌─────────────────────────────────────┐
  │  ← 返回                              │
  │                                      │
  │  [封面图]    作品名称                │
  │              ⭐ 8.5 (站点评分)       │
  │              📅 2024年1月            │
  │              🏷️  恋爱 · 校园          │
  │                                      │
  │  [❤️  想看] [📖 在看] [✓ 看过]       │  ← 站点状态同步
  │                                      │
  ├─────────────────────────────────────┤
  │  📚 可用来源 (3)                     │  ← 核心功能
  │  ┌─────────────────────────────────┐│
  │  │ ✓ 源1 (内置) - 已匹配            ││
  │  │   12话 · 更新至第10话            ││
  │  │   [📖 开始阅读] [❤️  加入收藏]    ││
  │  ├─────────────────────────────────┤│
  │  │ ✓ 源2 (Mihon) - 已匹配           ││
  │  │   12话 · 更新至第12话 (完结)     ││
  │  │   [📖 开始阅读]                  ││
  │  ├─────────────────────────────────┤│
  │  │ ? 未找到匹配                     ││
  │  │   [🔍 手动搜索] [➕ 添加源]      ││
  │  └─────────────────────────────────┘│
  │                                      │
  ├─────────────────────────────────────┤
  │  📝 简介                             │
  │  这是一部关于...                     │
  │                                      │
  │  💬 站点评论 (123)                  │
  │  [查看更多评论]                      │
  │                                      │
  │  🔗 相关作品                         │
  │  [续集] [前传] [同系列]              │
  └─────────────────────────────────────┘

  4.1.3.2 核心功能：自动源匹配

  - 自动搜索所有已启用的源
  - 使用多维度匹配算法（标题、年份、作者）
  - 显示匹配置信度
  - 支持手动搜索和关联

  4.1.4 现有详情页增强

  4.1.4.1 添加追踪信息卡片

  当前实现仍复用既有 `scrobbling` 区块作为详情页入口，不再单独保留平行卡片。

  在现有的内容详情页（DetailsActivity）中添加：

  ┌─────────────────────────────────────┐
  │  [封面]  作品标题                    │
  │          来源：某个源                │
  │                                      │
  ├─────────────────────────────────────┤
  │  🔗 追踪信息                         │
  │  ┌─────────────────────────────────┐│
  │  │ 追踪站点                         ││
  │  │ ⭐ 8.5 · #123                    ││
  │  │ [❤️  想看] [📖 在看] [✓ 看过]    ││
  │  │ [查看详情]                       ││
  │  └─────────────────────────────────┘│
  │                                      │
  │  📖 章节列表                         │
  │  ...                                 │
  └─────────────────────────────────────┘

  4.1.4.2 自动关联功能

  - 打开详情页时自动搜索默认追踪站点
  - 显示匹配结果和置信度
  - 用户可以确认或手动选择
  - 关联后保存到数据库
  - 当前实现已支持自动建议、已关联/推荐动作菜单与手动确认落表

  4.2 Bangumi 默认实现层

  4.2.1 数据模型

  说明：
  - 产品层统一面向站点无关概念，例如 `TrackingSiteItem`、`TrackingSiteState`、`TrackingSiteLink`
  - `BangumiItem`、`BangumiTrackingEntity`、`BangumiSourceLinkEntity` 只是默认实现层的具体命名
  - 后续如接入 AniList / MAL，应保持通用接口不变，仅新增对应站点的实现模型与映射层
  - 当前进度：`TrackingSiteItem` / `TrackingSiteItemDetails` / `TrackingSiteLink` 已落地，`TrackingSiteState` 仍预留给后续同步增强

  4.2.1.1 Bangumi 数据

  推荐的通用抽象命名如下：

  data class TrackingSiteItem(
      val site: TrackingSite,
      val remoteId: String,
      val title: String,
      val altTitles: List<String>,
      val type: TrackingContentType,
      val rating: Float?,
      val rank: Int?,
      val summary: String?,
      val tags: List<String>,
      val year: Int?,
      val authors: List<String>,
      val coverUrl: String?,
      val totalEpisodes: Int?,
      val publishDate: String?
  )

  data class TrackingSiteState(
      val site: TrackingSite,
      val remoteId: String,
      val status: TrackingStatus,
      val matchedSources: List<MatchedSource>,
      val lastSyncTime: Long,
      val currentEpisode: Int?,
      val score: Int?
  )

  data class TrackingSiteLink(
      val site: TrackingSite,
      val remoteId: String,
      val contentId: Long,
      val sourceName: String,
      val confidence: Float,
      val isManual: Boolean,
      val createdAt: Long
  )

  其中，Bangumi 默认实现负责把 `BangumiItem` / `BangumiTrackingEntity` / `BangumiSourceLinkEntity`
  映射到这些通用模型，而不是让上层 UI 或交互直接依赖 Bangumi-specific 类型。

  data class BangumiItem(
      val id: Long,
      val name: String,
      val nameCn: String,
      val nameJp: String,
      val type: BangumiType, // 书籍、动画、游戏等
      val rating: Float,
      val rank: Int,
      val summary: String,
      val tags: List<String>,
      val year: Int?,
      val authors: List<String>,
      val coverUrl: String,
      val totalEpisodes: Int?,
      val airDate: String?
  )

  enum class BangumiType {
      BOOK,    // 书籍（漫画、小说）
      ANIME,   // 动画
      MUSIC,   // 音乐
      GAME,    // 游戏
      REAL     // 三次元
  }

  4.2.1.2 匹配结果

  data class MatchedSource(
      val source: ContentSource,
      val content: Content,
      val confidence: Float, // 0.0 - 1.0
      val isManuallyLinked: Boolean = false
  )

  4.2.1.3 追踪状态

  data class TrackingState(
      val bangumiId: Long,
      val status: TrackingStatus,
      val matchedSources: List<MatchedSource>,
      val lastSyncTime: Long,
      val currentEpisode: Int?,
      val score: Int? // 用户评分 1-10
  )

  enum class TrackingStatus {
      WISH,      // 想看
      WATCHING,  // 在看
      COMPLETED, // 看过
      ON_HOLD,   // 搁置
      DROPPED    // 弃坑
  }

  4.2.2 数据库设计

  4.2.2.1 Bangumi 条目表

  @Entity(tableName = "bangumi_items")
  data class BangumiItemEntity(
      @PrimaryKey val id: Long,
      val name: String,
      val nameCn: String,
      val nameJp: String,
      val type: String,
      val rating: Float,
      val rank: Int,
      val summary: String,
      val tags: String, // JSON array
      val year: Int?,
      val authors: String, // JSON array
      val coverUrl: String,
      val totalEpisodes: Int?,
      val airDate: String?,
      val cachedAt: Long
  )

  4.2.2.2 追踪状态表

  @Entity(
      tableName = "bangumi_tracking",
      foreignKeys = [
          ForeignKey(
              entity = BangumiItemEntity::class,
              parentColumns = ["id"],
              childColumns = ["bangumiId"],
              onDelete = ForeignKey.CASCADE
          )
      ]
  )
  data class BangumiTrackingEntity(
      @PrimaryKey val bangumiId: Long,
      val status: String,
      val currentEpisode: Int?,
      val score: Int?,
      val lastSyncTime: Long
  )

  4.2.2.3 源关联表

  @Entity(
      tableName = "bangumi_source_links",
      foreignKeys = [
          ForeignKey(
              entity = BangumiItemEntity::class,
              parentColumns = ["id"],
              childColumns = ["bangumiId"],
              onDelete = ForeignKey.CASCADE
          ),
          ForeignKey(
              entity = MangaEntity::class,
              parentColumns = ["manga_id"],
              childColumns = ["contentId"],
              onDelete = ForeignKey.CASCADE
          )
      ],
      indices = [
          Index("bangumiId"),
          Index("contentId")
      ]
  )
  data class BangumiSourceLinkEntity(
      @PrimaryKey(autoGenerate = true) val id: Long = 0,
      val bangumiId: Long,
      val contentId: Long,
      val sourceName: String,
      val confidence: Float,
      val isManual: Boolean,
      val createdAt: Long
  )

  4.2.3 自动匹配算法

  4.2.3.1 匹配流程

  class ContentMatcher @Inject constructor(
      private val sourcesRepository: ContentSourcesRepository,
      private val searchRepository: SearchRepository
  ) {
      suspend fun findSources(
          bangumiItem: BangumiItem,
          enabledSources: List<ContentSource>
      ): List<MatchedSource> {
          // 1. 准备搜索关键词（多语言标题）
          val titles = listOf(
              bangumiItem.name,
              bangumiItem.nameCn,
              bangumiItem.nameJp
          ).filter { it.isNotBlank() }

          // 2. 并行搜索所有源
          val results = enabledSources.map { source ->
              async(Dispatchers.IO) {
                  searchInSource(source, titles)
              }


## 6. Implementation Priority & Sequence


## 目标

定义 `ui_improvement.md` 的推荐实施顺序，明确：

- 哪些任务必须先做
- 哪些任务可以并行
- 哪些任务属于增强项，不应阻塞主线
- 如何避免把追踪站点能力写死为 Bangumi 单站点实现

---

## 当前状态概览（2026-03-19）

### 已完成或已实质完成的阶段

- `P0` 信息架构稳定：已完成
  - 主页导航入口已接入
  - 默认启动链路已切到主页
  - 导航配置页已兼容 `Home`
- `P1` 主页最小可用版本：已实质完成
  - 主页 Dashboard 首版已可用
  - 真实数据聚合已接入
- `P2` Explore 过滤器体验优化：已实质完成
  - 两行固定布局已落地
  - 横向滚动已移除
  - `JavaScript` 源类型标签与图标已补齐
- `P3` 追踪站点抽象层：已继续推进
  - discovery / matcher / preferred-site 抽象已落地
  - 详情页增强方案已收敛为复用既有 `scrobbling` 入口，并补齐空态引导

### 尚未开始的阶段

- `P4` Discover 一期
- `P5` 自动关联与同步增强
- `P6` 主页配置化增强

---

## 总体优先级

### P0：信息架构稳定
- 主页导航入口
- 默认启动页策略
- 导航配置兼容 `Home`

状态：已完成

### P1：主页最小可用版本
- 主页容器与卡片列表
- 主页状态聚合
- 继续阅读、最近阅读、Library、最近更新、源概览、同步状态

状态：已实质完成

### P2：Explore 过滤器体验优化
- 两行固定布局
- 去除横向滚动
- 补齐源类型图标资源

状态：已实质完成

### P3：追踪站点抽象与详情页增强
- 梳理现有 `scrobbling/common`
- 建立站点无关 discovery 抽象
- 详情页追踪信息卡增强

状态：抽象层已落地，详情页增强已复用既有 scrobbling 区块并补齐空态与首选站点优先展示

### P4：发现页 Discover 一期
- 页面结构
- 站点切换
- 默认 Bangumi 实现
- 搜索、热门列表、详情跳转

状态：进行中，已落地导航入口、最小站点切换、热门列表、搜索与站内详情跳转

### P4.5：追踪站点详情页一期
- 远端详情展示
- 已绑定状态与本地入口
- 绑定/管理入口
- 站外打开入口
- 后续接入本地入口与绑定能力

状态：进行中，已落地最小详情页容器、远端详情展示、已绑定状态、本地入口与绑定/管理入口

### P5：自动关联与同步增强
- 自动匹配
- 手动纠正
- 状态、评分、进度同步增强

状态：未开始

### P6：配置化增强
- 主页卡片显隐
- 主页卡片数量
- 主页卡片顺序

状态：未开始

---

## 推荐阶段划分

### 阶段 1：入口稳定
- Issue 1：新增主页导航入口
- Issue 4：导航配置页兼容 Home
- Issue 5：主页默认启动页策略

状态：已完成

### 阶段 2：主页 MVP
- Issue 2：主页模块 MVP 落地
- Issue 3：主页数据聚合与状态模型收敛

状态：已实质完成

### 阶段 3：Explore 过滤器改造
- Issue 6：Explore 过滤器改为两行固定布局
- Issue 7：补齐 Explore 源类型图标资源

状态：已实质完成

### 阶段 4：追踪站点抽象层
- Issue 9：梳理并复用现有追踪站点基础能力
- Issue 10：预留追踪站点切换接口
- Issue 12：追踪站点本地模型与缓存表设计

状态：前两项已完成基础骨架，Issue 12 已进入读写接入阶段

### 阶段 5：详情页追踪增强
- Issue 11：详情页追踪信息卡一期

状态：进行中（方案已收敛到复用既有入口，空态引导已落地）

### 阶段 6：Discover 一期
- Issue 14：发现页 Discover 一期
- Issue 15：追踪站点详情页一期

状态：进行中（Discover MVP 与追踪站点详情页一期均已进入可运行状态）

### 阶段 7：自动关联与同步增强
- Issue 13：追踪站点自动关联基础版
- Issue 16：追踪状态同步增强

状态：进行中（Issue 13 已进入 matcher、关联落表与推荐项动作菜单阶段）

### 阶段 8：主页配置化增强
- Issue 8：主页卡片配置能力

状态：未开始

---

## 推荐执行顺序

当前推荐顺序如下：

1. 基于既有 scrobbling 入口完善详情页追踪增强
2. 发现页 Discover 一期
3. 追踪站点详情页一期
4. 追踪站点本地模型与缓存表设计
5. 自动关联基础版
6. 追踪状态同步增强
7. 主页卡片配置能力

原因：

- 主页与 Explore 主线已经收敛到可交付状态
- discovery 抽象已落地，下一步应尽快接到真实 UI 入口
- 详情页追踪卡比 Discover 更贴近当前主阅读链路，价值更直接
- 自动关联和同步增强复杂度最高，应排在展示链路稳定之后

---

## 并行开发建议

### 可并行组合 A
- 分支 1：详情页追踪信息卡
- 分支 2：追踪站点本地模型与缓存表

原因：

- 一个偏 UI 聚合，一个偏数据层
- 改动边界相对独立

### 可并行组合 B
- 分支 3：Discover 一期
- 分支 4：主页配置化增强

原因：

- 两者业务边界独立
- 主页配置化不阻塞追踪站点主线

---

## 推荐分支顺序

1. `feature/details-tracking-card`
2. `feature/discover-page-v1`
3. `feature/tracking-site-cache-model`
4. `feature/tracking-auto-match`
5. `feature/tracking-sync-enhancement`
6. `feature/home-dashboard-config`

---

## 判断原则

- KISS：先把现有抽象接到真实页面，不先做通用平台
- YAGNI：未进入当前排期的评论聚合、跨站统一排序先不做
- DRY：继续复用 `scrobbling/common` 与现有详情页能力
- SOLID：UI 仅依赖站点无关接口，不直接依赖 `BangumiRepository`


## 7. Tasks & Issues


## 目标

将 `ui_improvement.md` 中的设计方案拆分为可执行任务，并明确当前已落地进度、未完成部分和下一步顺序。

约束如下：

- 先稳定主页与主导航信息架构
- 再完成 `Explore` 过滤器体验优化
- 追踪站点能力必须基于站点无关抽象扩展，不能把 Bangumi 写死为唯一实现
- 优先交付最小可运行闭环，避免过度设计

---

## 当前实现进度（2026-03-19）

### 已完成或已实质落地

- `Issue 1` 已完成：`Home` 已接入主导航，默认导航顺序已调整，并兼容现有导航配置入口。
- `Issue 2` 已实质完成：主页已从占位页推进为可用的 Dashboard 首版。
- `Issue 3` 已实质完成：主页状态已接入真实数据聚合，覆盖阅读历史、收藏、分类、更新、源数量、源分布、首选追踪站点与同步状态。
- `Issue 4` 已完成：导航配置页已兼容 `Home`。
- `Issue 5` 已完成：主页已作为默认导航首项接入启动链路。
- `Issue 6` 已完成：`Explore` 过滤器已改为两行固定布局，移除了横向滚动依赖。
- `Issue 7` 已实质完成：源类型图标体系已补齐到当前实现范围，额外新增了 `JavaScript` 源类型与图标，并打通到 `Explore`、搜索等共享标签模型。
- 追踪站点切换预留接口已落地：已新增站点无关的 discovery / matcher / preferred-site 抽象，以及默认实现骨架与 `AppSettings` 持久化入口。
- tracking/discovery 术语已开始对齐：发现列表模型已收敛到 `TrackingSiteItem`，并明确 `State` / `Link` 属于后续同步与持久化层。
- tracking/discovery 术语继续收敛：详情模型已对齐为 `TrackingSiteItemDetails`，与 `TrackingSiteItem` 命名保持同层级一致。
- `Issue 11` 已推进：详情页原有 `scrobbling` 区块已补齐基础空态、未关联引导与首选站点优先展示，后续增强继续基于现有追踪入口合并演进，不再新增平行卡片。
- `Issue 14` 已推进：`Discover` 最小骨架已接入主导航，已支持基于首选追踪站点的最小站点切换、热门列表、站内搜索与站内详情跳转。
- `Issue 12` 已推进：已落地追踪站点本地缓存骨架，并接入 `Discover` 与详情页的缓存读写链路，后续再补状态表与更完整的同步能力。
- `Issue 15` 已推进：追踪站点详情页已落地最小容器，可展示远端详情、绑定/管理入口与站外打开入口，绑定状态与本地内容入口正统一到 `tracking_site_links` 链路。

### 当前主页首版包含内容

- 总览卡：最近阅读数量、未读更新数量、首选追踪站点、同步状态
- 继续阅读卡：阅读入口与详情入口
- 历史卡：默认 8 个最近阅读封面，支持横向滑动
- Library 卡：收藏总数、分类数、进入收藏页入口
- 更新卡：默认 8 个最近更新封面，支持横向滑动
- 源概览卡：启用源数量、来源分布、进入源管理入口
- 快捷入口卡：`Reader settings` 与 `Settings`

### 已落地代码入口

- 主页与导航：
  - `app/src/main/kotlin/org/skepsun/kototoro/core/prefs/NavItem.kt`
  - `app/src/main/kotlin/org/skepsun/kototoro/core/prefs/AppSettings.kt`
  - `app/src/main/kotlin/org/skepsun/kototoro/main/ui/MainNavigationDelegate.kt`
  - `app/src/main/kotlin/org/skepsun/kototoro/settings/nav/NavConfigViewModel.kt`
  - `app/src/main/kotlin/org/skepsun/kototoro/home/ui/HomeFragment.kt`
  - `app/src/main/kotlin/org/skepsun/kototoro/home/ui/HomeViewModel.kt`
  - `app/src/main/res/layout/fragment_home.xml`
- Explore：
  - `app/src/main/res/layout/fragment_explore.xml`
  - `app/src/main/kotlin/org/skepsun/kototoro/explore/ui/model/SourceTag.kt`
  - `app/src/main/kotlin/org/skepsun/kototoro/explore/ui/model/BrowseGroupTab.kt`
  - `app/src/main/kotlin/org/skepsun/kototoro/search/domain/SearchSourceTypes.kt`
  - `app/src/main/res/drawable/ic_source_js.xml`
- 追踪站点 discovery 抽象：
  - `app/src/main/kotlin/org/skepsun/kototoro/tracking/discovery/domain/TrackingSiteDiscoveryService.kt`
  - `app/src/main/kotlin/org/skepsun/kototoro/tracking/discovery/domain/TrackingSiteMatcher.kt`
  - `app/src/main/kotlin/org/skepsun/kototoro/tracking/discovery/domain/PreferredTrackingSiteProvider.kt`
  - `app/src/main/kotlin/org/skepsun/kototoro/tracking/discovery/domain/TrackingDiscoveryModels.kt`
  - `app/src/main/kotlin/org/skepsun/kototoro/tracking/discovery/data/DefaultTrackingSiteDiscoveryService.kt`
  - `app/src/main/kotlin/org/skepsun/kototoro/tracking/discovery/data/AppSettingsPreferredTrackingSiteProvider.kt`
  - `app/src/main/kotlin/org/skepsun/kototoro/tracking/discovery/TrackingDiscoveryModule.kt`
  - `app/src/main/kotlin/org/skepsun/kototoro/discover/ui/DiscoverFragment.kt`
  - `app/src/main/kotlin/org/skepsun/kototoro/discover/ui/DiscoverViewModel.kt`
  - `app/src/main/kotlin/org/skepsun/kototoro/discover/ui/DiscoverAdapter.kt`
  - `app/src/main/res/layout/item_discover_site.xml`

### 尚未开始或尚未进入可交付状态

- `Issue 8`：主页卡片配置化尚未开始
- 详情页追踪增强尚未覆盖自动关联与更完整的多站点交互
- `Discover` 页面一期已进入 MVP 阶段
- 自动关联、手动纠正与详情页消费 `tracking_site_links` 的最小闭环已进入收尾阶段，追踪状态增强同步尚未开始
- Bangumi 本地缓存表与跨站点详情模型已进入数据库骨架阶段，但尚未进入最终形态

---

## 里程碑

### M1：主页与导航稳定
- `Issue 1`
- `Issue 2`
- `Issue 3`
- `Issue 4`
- `Issue 5`

状态：已完成或已实质完成

### M2：Explore 过滤器优化
- `Issue 6`
- `Issue 7`

状态：已完成或已实质完成

### M3：追踪站点最小价值闭环
- `Issue 9`
- `Issue 10`
- `Issue 11`

状态：已完成抽象层骨架，详情页增强方案已收敛到复用既有 scrobbling 入口

### M4：追踪站点扩展能力
- `Issue 12`
- `Issue 13`
- `Issue 14`
- `Issue 15`

状态：已进入 Discover MVP 与站点详情页最小闭环阶段

### M5：主页增强
- `Issue 8`

状态：未开始

---

## Issue 列表

### Issue 1：新增主页导航入口
- 目标：在主导航中引入 `Home`，并作为默认信息聚合入口
- 工作量：S
- 状态：已完成

### Issue 2：主页模块 MVP 落地
- 目标：实现最小可用 Dashboard
- 工作量：M
- 状态：已实质完成

### Issue 3：主页数据聚合与状态模型收敛
- 目标：让主页基于真实数据流渲染
- 工作量：M
- 状态：已实质完成

### Issue 4：导航配置页兼容 Home
- 目标：让现有导航配置能力支持 `Home`
- 工作量：S
- 状态：已完成

### Issue 5：主页默认启动页策略
- 目标：把主页变成默认启动页
- 工作量：S
- 状态：已完成

### Issue 6：Explore 过滤器改为两行固定布局
- 目标：消除横向滚动，改成两行布局
- 工作量：M
- 状态：已完成

### Issue 7：补齐 Explore 源类型图标资源
- 目标：为过滤器提供统一图标，并补齐共享标签模型
- 工作量：S
- 状态：已实质完成

### Issue 8：主页卡片配置能力
- 目标：支持卡片显隐、数量和顺序配置
- 工作量：M
- 状态：未开始

### Issue 9：梳理并复用现有追踪站点基础能力
- 目标：明确现有 `scrobbling/common` 能力边界
- 工作量：S
- 状态：已完成基础梳理

### Issue 10：预留追踪站点切换接口
- 目标：站点发现与详情能力通过站点无关接口暴露
- 工作量：M
- 状态：已完成接口与默认实现骨架

### Issue 11：详情页追踪信息卡一期
- 目标：在现有详情页中显示站点无关追踪信息
- 工作量：M
- 状态：进行中（已复用既有 scrobbling 区块，并补齐空态引导、首选站点优先展示与推荐项动作菜单）

### Issue 12：追踪站点本地模型与缓存表设计
- 目标：为发现页和详情页提供本地可缓存模型
- 工作量：M
- 状态：进行中（已落地通用缓存表、链接表、DAO 与数据库迁移，并接入 `Discover`/详情页读写链路）

### Issue 13：追踪站点自动关联基础版
- 目标：实现本地内容与远端条目的最小可用匹配
- 工作量：L
- 状态：进行中（已落地 matcher、自动建议、已关联/推荐动作菜单与手动确认落表，详情页对 `tracking_site_links` 的消费链路正在补齐）

### Issue 14：发现页 Discover 一期
- 目标：实现站点切换、搜索、热门列表和详情跳转
- 工作量：M
- 状态：进行中（已落地导航入口、最小站点切换、热门列表、搜索与站内详情跳转）

### Issue 15：追踪站点详情页一期
- 目标：展示远端详情与本地入口
- 工作量：M
- 状态：进行中（已落地最小详情页容器、远端详情展示、绑定/管理入口与站外打开入口，绑定状态与本地内容入口正统一到 `tracking_site_links` 链路）

### Issue 16：追踪状态同步增强
- 目标：支持站点状态、评分、进度同步增强
- 工作量：M
- 状态：未开始

---

## 当前建议的下一步顺序

1. 基于 `tracking_site_links` 推进自动关联基础版
2. 在此基础上推进状态同步增强
3. 继续增强追踪站点详情页与本地详情页的双向联动
4. 最后做主页卡片配置化
