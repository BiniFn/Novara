  # Kototoro UI 改进方案                                                                                                                                                                                                                                                                                                           
                                                                                                                                                                                                                                                                                                                                 
  ## 文档版本                                                                                                                                                                                                                                                                                                                      
  - 创建日期：2026-03-18                                                                                                                                                                                                                                                                                                           
  - 最后更新：2026-03-18                                                                                                                                                                                                                                                                                                           
  - 状态：设计阶段                                                                                                                                                                                                                                                                                                                 
                                                                                                                                                                                                                                                                                                                                   
  ---

  ## 目录
  1. [主页（Dashboard）设计](#1-主页dashboard设计)
  2. [导航栏优化](#2-导航栏优化)
  3. [过滤器界面改进](#3-过滤器界面改进)
  4. [追踪网站集成（Bangumi）](#4-追踪网站集成bangumi)
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
  │  📖 最近阅读 (5)              [更多>]│
  │  ┌─────┬─────┬─────┬─────┬─────┐   │
  │  │     │     │     │     │     │ → │  ← 横向滚动
  │  └─────┴─────┴─────┴─────┴─────┘   │
  │                                      │
  │  ⭐ 收藏更新 (3)              [更多>]│
  │  ┌─────────────────────────────────┐│
  │  │ 作品A - 新增第10话               ││
  │  │ 作品B - 新增第5话                ││
  │  │ 作品C - 新增第12话               ││
  │  └─────────────────────────────────┘│
  │                                      │
  │  🔔 订阅动态 (2)              [更多>]│
  │  ┌─────────────────────────────────┐│
  │  │ 源1 更新了 5 部作品              ││
  │  │ 源2 更新了 3 部作品              ││
  │  └─────────────────────────────────┘│
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

  #### 1.3.2 最近阅读卡片
  - 横向滚动列表
  - 显示最近 5-10 个阅读记录
  - 封面 + 标题 + 阅读进度
  - 点击直接继续阅读
  - "更多"按钮跳转到完整历史页面

  #### 1.3.3 收藏更新卡片
  - 显示有新章节的收藏作品
  - 作品名 + 更新信息
  - 点击跳转到详情页
  - "更多"按钮跳转到完整收藏页面

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
  - 发现 (Discover/Bangumi)：新增独立页面（见第4节）

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
  4. 追踪网站集成（Bangumi）

  4.1 功能概述

  集成 Bangumi 等二次元追踪网站，提供：
  - 权威的内容库和元数据
  - 排行榜和推荐
  - 跨源内容聚合
  - 阅读进度同步

  4.2 新增"发现"页面

  4.2.1 页面结构

  ┌─────────────────────────────────────┐
  │  🔍 搜索 Bangumi                     │
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

  4.2.2 功能模块

  1. 排行榜：显示 Bangumi 的热门作品
  2. 本季新番：当季新作品
  3. 标签浏览：按类型、标签筛选
  4. 年份/季度：按时间筛选
  5. 搜索：搜索 Bangumi 数据库

  4.3 Bangumi 详情页设计

  4.3.1 页面结构

  ┌─────────────────────────────────────┐
  │  ← 返回                              │
  │                                      │
  │  [封面图]    作品名称                │
  │              ⭐ 8.5 (Bangumi)       │
  │              📅 2024年1月            │
  │              🏷️  恋爱 · 校园          │
  │                                      │
  │  [❤️  想看] [📖 在看] [✓ 看过]       │  ← Bangumi 状态同步
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
  │  💬 Bangumi 评论 (123)              │
  │  [查看更多评论]                      │
  │                                      │
  │  🔗 相关作品                         │
  │  [续集] [前传] [同系列]              │
  └─────────────────────────────────────┘

  4.3.2 核心功能：自动源匹配

  - 自动搜索所有已启用的源
  - 使用多维度匹配算法（标题、年份、作者）
  - 显示匹配置信度
  - 支持手动搜索和关联

  4.4 现有详情页增强

  4.4.1 添加 Bangumi 信息卡片

  在现有的内容详情页（DetailsActivity）中添加：

  ┌─────────────────────────────────────┐
  │  [封面]  作品标题                    │
  │          来源：某个源                │
  │                                      │
  ├─────────────────────────────────────┤
  │  🔗 追踪信息                         │
  │  ┌─────────────────────────────────┐│
  │  │ Bangumi                          ││
  │  │ ⭐ 8.5 · #123                    ││
  │  │ [❤️  想看] [📖 在看] [✓ 看过]    ││
  │  │ [查看详情]                       ││
  │  └─────────────────────────────────┘│
  │                                      │
  │  📖 章节列表                         │
  │  ...                                 │
  └─────────────────────────────────────┘

  4.4.2 自动关联功能

  - 打开详情页时自动搜索 Bangumi
  - 显示匹配结果和置信度
  - 用户可以确认或手动选择
  - 关联后保存到数据库

  4.5 数据模型

  4.5.1 Bangumi 数据

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

  4.5.2 匹配结果

  data class MatchedSource(
      val source: ContentSource,
      val content: Content,
      val confidence: Float, // 0.0 - 1.0
      val isManuallyLinked: Boolean = false
  )

  4.5.3 追踪状态

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

  4.6 数据库设计

  4.6.1 Bangumi 条目表

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

  4.6.2 追踪状态表

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

  4.6.3 源关联表

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

  4.7 自动匹配算法

  4.7.1 匹配流程

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

