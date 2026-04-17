# Compose 迁移执行日志

## 说明

本页用于持续记录 Compose Multiplatform 与 liquid glass 迁移的真实进度，只记录已经完成或正在执行的事项，不记录空计划。

## 2026-04-16

### 已完成

- 新建迁移主文档：`compose_migration/cmp-liquid-glass-migration.md`
- 明确阶段划分、非目标与模块边界
- 新增基础玻璃组件：`GlassSurface`、`GlassTopBarContainer`、`GlassBottomBarContainer`
- 主顶栏与底栏已切换到统一玻璃容器
- `./gradlew :app:compileDebugKotlin --no-daemon` 验证通过
- `npm run docs:build` 验证通过
- `RootSettingsFragment` 已切换为 Compose 入口页
- 新增第一版设置入口组件：`SettingsRootScreen`
- Root Settings 改造后再次通过 Kotlin 编译与文档构建验证
- `StorageAndNetworkSettingsFragment` 已迁移到 Compose
- `StorageAndNetwork` 已补齐 choice / switch / text input 设置组件
- `StorageAndNetwork` 中 SSL bypass 提示已改为仅在用户开启时触发
- `AppearanceSettingsFragment` 已迁移到 Compose
- Compose 设置 DSL 已补齐 multi-choice / slider
- Appearance 页保留 `NavConfigFragment` 与 `ProtectSetupActivity` 作为平台特定路由
- 本轮再次通过 `./gradlew :app:compileDebugKotlin --no-daemon` 验证
- `ServicesSettingsFragment` 已迁移到 Compose
- Compose 设置 DSL 已补齐 split switch，并为 action row 增加可选图标
- `Services` 页中的 Sync / Suggestions / Discord 继续复用既有 Android 路由，追踪服务授权确认已切换为 Compose `AlertDialog`
- `Services` 迁移后再次通过 `./gradlew :app:compileDebugKotlin --no-daemon` 验证
- `DownloadsSettingsFragment` 已迁移到 Compose
- Compose 设置 DSL 已补齐 info row
- `Downloads` 页中的目录选择、文档树授权、电池优化跳转继续保留为 Fragment 侧平台桥接
- `Downloads` 迁移后再次通过 `./gradlew :app:compileDebugKotlin --no-daemon` 验证
- `TrackerSettingsFragment` 已迁移到 Compose
- Compose 设置 DSL 已补齐 disabled / enabled-state 语义，覆盖旧 `Preference` 的 `dependency` 行为
- `Tracker` 页中的通知设置跳转、分类配置 sheet、电池优化请求和外部说明链接继续保留为 Fragment 侧平台桥接
- `Tracker` 迁移后再次通过 `./gradlew :app:compileDebugKotlin --no-daemon` 验证
- `SourcesSettingsFragment` 已迁移到 Compose
- `Sources` 页中的 manage/catalog/json/extensions 子入口继续复用既有路由，`handle_links` 改为 Compose 驱动的平台开关
- 为 `Sources` 迁移补齐 `jar_priority_order` 等偏好可写接口
- `Sources` 迁移后再次通过 `./gradlew :app:compileDebugKotlin --no-daemon` 验证
- `BackupsSettingsFragment` 已迁移到 Compose
- Compose 设置 DSL 已为文本输入补齐 placeholder / password 能力，`AppSettings` 已补齐 Backups 所需的 WebDAV 与周期备份可写接口
- `Backups` 页中的文档选择、持久化授权、备份服务启动与恢复对话框继续保留为 Fragment 侧平台桥接
- `Backups` 迁移后再次通过 `./gradlew :app:compileDebugKotlin --no-daemon` 验证
- `NotificationSettingsLegacyFragment` 已迁移到 Compose
- `AppSettings` 已补齐通知振动与灯光偏好的可写接口
- `Notification` 页中的系统铃声选择继续保留为 Fragment 侧 `ActivityResultContract` 平台桥接
- `Notification` 迁移后再次通过 `./gradlew :app:compileDebugKotlin --no-daemon` 验证
- `JsonSourcesRootFragment` 已迁移到 Compose
- 新增通用 Compose tab-host，用于承载 `childFragmentManager` 下的设置子 Fragment
- `Json Sources` 页中的 Legado / TVBox / LNReader 子 Fragment 继续保留为平台桥接，宿主本身不再依赖 `TabLayoutMediator` 与 `ViewPager2`
- `Json Sources` 迁移后再次通过 `./gradlew :app:compileDebugKotlin --no-daemon` 验证
- `ExtensionsRootFragment` 已迁移到 Compose
- `Extensions` 页复用同一套 Compose tab-host，Mihon / Aniyomi / IReader 子 Fragment 继续保留为平台桥接
- `Extensions` 迁移后再次通过 `./gradlew :app:compileDebugKotlin --no-daemon` 验证
- `MainActivity` 已开始接管主壳内容避让，`FragmentContainerView` 的顶栏/底栏可见 inset 改由 Compose chrome 回传并统一注入 padding
- `KototoroApp` 不再直接对 Android `Fragment` 宿主施加内部 padding，浮动底栏时也不再通过伪造 child insets 间接避让
- Compose 顶栏搜索建议点击已对齐旧实现：`Content` 直达详情、`Tag` 保持筛选条件进入标签搜索、`Source/SourceTip` 直达列表、`Author` 进入作者搜索
- 普通搜索、最近搜索与提示词点击已补齐链接直达逻辑，主壳内容类型与来源筛选也会同步到 `SearchSuggestionViewModel`
- Compose 顶栏过滤器已补齐旧 Fragment 语义：根据当前页面动态控制内容类型/来源筛选的可见性、可用项、来源列表，并支持来源筛选按钮复用旧页面自定义弹层入口
- Compose 顶栏查询状态已提升到主壳统一管理，语音输入结果现在会直接回填 Compose `SearchBar`，并继续驱动搜索建议刷新
- Compose 顶栏已恢复全局语言预设按钮：显示时机沿用旧页面回调，点击默认打开源预设管理页，存在激活预设时会以高亮状态反馈
- `Home` / `Explore` / `FavouritesContainer` / `ContentList` 已在 `onDestroyView()` 主动释放顶栏筛选回调，避免 `MainActivity` 持有已销毁 Fragment 的陈旧筛选状态
- `HomeScreen` 已按 legacy 对齐规范推进首轮改造：快捷入口改为自适应矩阵，首页三合一卡在列表模式宽屏下可并排展示，WebDAV 最近同步时间改为可读时间文本
- `HomeScreen` 已补齐概览卡，开始消费 `resumeState`、收藏数、分类数、启用源数量与默认 tracking 站点等首页聚合状态，减少首页已有 Compose 状态未落地的问题
- `DetailsHeader` 已开始按 legacy 对齐规范收口：接入收藏分类、译文标题/简介、来源/作者/标签动作，去除 Compose 占位文案与空回调
- `DetailsScreen` / `DetailsActivity` 已补齐分享、下载、来源跳转、作者/标签弹层与翻译切换，Compose 详情头部现已复用现有 router 与 `DetailsViewModel` 行为
- `DetailsScreen` 顶栏 `More` 菜单已改为 Compose overflow menu，并补齐翻译、相似内容、在线版本、浏览器、追踪、统计与 NSFW 切换等旧菜单核心动作
- `DetailsScreen` 底部阅读 dock 已改为 Compose 分段入口：主按钮文案会跟随加载/继续阅读/播放状态变化，右侧分支下拉可直接回写 `selectedBranch`
- `DetailsActivity` 的 Compose 阅读入口已开始带上当前分支，并在章节缺失时直接给出 Snackbar 反馈，避免底部 dock 仍停留在最简行为
- `DetailsScreen` 底部阅读 dock 已补齐旧 split-button 菜单语义：无痕阅读、从历史移除、下载入口与分支切换都已回接到现有动作链路
- `DetailsScreen` 顶栏 `More` 菜单已补齐低频动作：本地删除、override 编辑、创建快捷方式，`DetailsActivity` 侧复用了旧确认弹窗、override 返回刷新与快捷方式请求逻辑
- `DetailsActivity` 已统一章节、页面、书签三个按钮到内嵌 `ChaptersPagesSheet`，并按动态 tab 可用性映射真实索引，修复点击后闪退问题
- 主壳收口后再次通过 `./gradlew :app:compileDebugKotlin --no-daemon` 验证
- 文档站在本轮结束后再次通过 `npm run docs:build` 验证

### 进行中

- 整理 Compose 设置 DSL
- 继续推进 Main Shell 与导航壳收口

### 下一步

- 继续推进 Main Shell 与导航壳收口
- 继续补齐设置 DSL 中剩余复杂组件

## 2026-04-17

### 已完成

- `DetailsActivity` / `ChaptersPagesSheet` 已修复章节、页面、书签 Tab 切换闪退；`PagerState.animateScrollToPage()` 现改为在 Compose `rememberCoroutineScope()` 上下文内触发，避免 `MonotonicFrameClock` 缺失导致的崩溃
- `ChaptersPagesSheet` 已按动态可见 tab 建立真实索引映射，章节 / 页面 / 书签被裁剪时不再选中错误页签
- `DiscoverScreen` 已为 tracking 首页接入首轮 Dantotsu 风格 Hero：顶部轮播、模糊背景联动、自动翻页、页间缩放 / 位移 / 轻微旋转过渡已落地
- `DiscoverHeroCarousel` 已从单层大图推进为双层视觉结构：背景 banner 氛围 + 前景浮动 cover，顶部首页开始具备更强的“发现页”层次
- `DiscoverCarousel` 已补齐分类横向卡片滑动时的位移 / 缩放 / 透明度过渡，分类浏览不再是静态 `LazyRow`
- `DiscoverScreen` 已补齐 carousel 与 search grid 两种模式下的 Compose 空态，修复 `EmptyState` 被过滤后出现白屏的问题
- `Discover` 本轮 Kotlin 编译已通过；受本地 Hilt/KSP 生成目录异常影响，视觉深化阶段额外使用 `./gradlew :app:compileDebugKotlin --no-daemon -x kspDebugKotlin "-Pkotlin.incremental=false"` 完成语法与类型校验
- `DetailsHeader` 已按 Dantotsu 视觉继续收口：封面改为带外框与阴影的浮动卡面占位，标题区新增状态 / 语言 / 评分徽标，收藏按钮与翻译按钮层级更清晰
- `DetailsHeader` 信息区已重构为分组卡片：`Basic info` 使用双列元数据布局承载来源 / 作者 / 语言 / 章节 / 评分，简介改为独立卡片，标签区也与正文分层
- `DetailsScreen` 顶部导航按钮与 `More` 入口已统一为半透明悬浮圆形按钮，底部阅读 dock 也已升级为更接近 Dantotsu 的浮动分段容器
- `Details` 本轮再次通过 `./gradlew :app:compileDebugKotlin --no-daemon` 验证
- `HomeScreen` 已开始第二轮 Dantotsu 风格重构：首页从简单 dashboard 卡片改为 Hero + 继续发现入口 + 内容分区的门户结构，`Discover` 不再作为替代首页，而是由 `Home` 中的显式入口承接
- `HomeFragment` 已补齐首页直达 `Discover` 的导航动作，`Home` 内的“继续发现”卡片会直接切换到底部 `Discover` 页签
- `Home` 本轮再次通过 `./gradlew :app:compileDebugKotlin --no-daemon` 验证
- `HomeViewModel` 已开始接入首选 tracking 站点精选流：会按当前首页内容类型筛选可见分类，优先读取 category cache，再补齐远端 trending 数据
- `HomeScreen` 已新增 tracking spotlight 分区：首页现在会直接展示首选 tracking 站点的多组精选卡片，点击内容可进入 tracking 详情，点击 `更多` 可直接进入对应分类页
- `Home` 接入 tracking 精选流后再次通过 `./gradlew :app:compileDebugKotlin --no-daemon` 验证
- `HomeScreen` 已将首个 tracking section 升级为独立 Hero 区：新增横向分页卡组、自动轮播、模糊背景联动与当前选中项详情区，首页顶部现在形成“本地 Hero + tracking Hero”的双层发现结构
- `Home` 首组 tracking Hero 化后再次通过 `./gradlew :app:compileDebugKotlin --no-daemon` 验证
- 已抽取共享 Hero Compose 基础层：新增 `HeroBackdropCard`、`HeroBackdropScrim`、`HeroAutoAdvanceEffect` 与 `HeroPagerIndicator`，开始统一 `Home` / `Discover` 的 Hero 底座能力
- `DiscoverHeroCarousel` 与 `HomeTrackingHeroSection` 已回接共享 Hero 基础层，轮播自动翻页、模糊背景容器与分页指示器不再各自平行维护
- `Home` tracking Hero 已补齐分页指示器，首页与发现页的 Hero 交互提示进一步统一
- 共享 Hero 层抽取后再次通过 `./gradlew :app:compileDebugKotlin --no-daemon` 验证
- `Home` 顶部 Hero 已改为本地阅读入口卡：数据源从随机推荐收回到 `resume + history`，不再混用 tracking 更新数 / 推荐数，语义与下方 tracking Hero 分层明确
- `Home` 顶部 Hero 已补齐窄屏纵向布局：新增全宽前景封面、竖向按钮堆叠与本地阅读文案，修复竖屏下封面底部露白与次按钮被横向压扁的问题
- `Home` 顶部 Hero 收口后再次通过 `./gradlew :app:compileDebugKotlin --no-daemon` 验证
- `Home` 首屏结构已继续压缩：顶部本地 Hero 的最小高度、内边距与缩略行尺寸已下调，竖屏首屏不再被单张大卡完全占满
- `Home` 首个 tracking Hero 已从完整大卡收口为短条带式 pager：保留模糊背景与自动轮播，但去掉独立大段详情区，首页顶部改为“单主 Hero + 压缩 tracking strip”
- `Home` 首屏节奏压缩后再次通过 `./gradlew :app:compileDebugKotlin --no-daemon` 验证
- `Home` 的 `Quick access` 与 `At a glance` 已在窄屏下合并为单张工具卡：快捷入口改为紧凑 tile 样式，概览指标同步压缩，移除与顶部 Hero 重复的 `ResumePanel`
- `Home` 工具区单卡化后再次通过 `./gradlew :app:compileDebugKotlin --no-daemon` 验证
- `Home` 内容区已统一 section 头部样式：`更新 / 历史 / 推荐` 现改为同一套标题、副标题、数量徽标与 `更多` 动作结构，减少首页中段的模块割裂感
- `Home` 横向内容卡与纵向列表卡已同步压缩到更贴近首页的规格：封面卡宽度、列表项缩略图与标题层级已下调，并为横向卡补齐来源信息
- `Home` 内容区规格统一后再次通过 `./gradlew :app:compileDebugKotlin --no-daemon` 验证
- `Home` 页内视觉 token 已继续统一：`DashboardCard`、工具卡、空态卡、统计 pill、badge 与 tracking spotlight 卡的圆角、边框透明度、标题层级与内边距已收口到更一致的一套首页语言
- `Home` 首页视觉 token 收口后再次通过 `./gradlew :app:compileDebugKotlin --no-daemon` 验证
- `Home` 顶部本地 Hero 已补齐本地推荐数统计，并将 `更新 / 历史` 两组内容合并进顶部卡片：新增合并预览卡，页面中段不再重复出现独立的 `Updates` 与 `History` 区块
- `Home` 首个 tracking Hero 已恢复下半段文本信息：当前选中项的标题与副标题重新进入条带底部，缓解压缩后背景断层与信息缺失的问题
- 本轮 `Home` 首页问题收口后再次通过 `./gradlew :app:compileDebugKotlin --no-daemon` 验证
- `Home` 顶部本地 Hero 已重新收口为单一语义的“当前作品卡”：移除内嵌 `更新 / 历史` 列表预览，保留当前作品信息、全局本地统计与跳转动作，避免顶部卡片继续混合多种内容层级
- `Home` 顶部本地 Hero 的主按钮文案已改为 `Open details`，次按钮改为 `Suggestions`，并新增 `Updates / History` 快捷跳转 chip，修复原本 `Discover` 入口语义错位的问题
- `Home` 顶部卡片语义重构后再次通过 `./gradlew :app:compileDebugKotlin --no-daemon` 验证
- `Home` 顶部本地 Hero 已重构为 `History / Updates / Suggestions` 三标签主卡：当前作品不再独占整张卡，而是作为当前标签列表中的选中项展示，下方独立 `Suggestions` 区块已移除
- `Home` 顶部主卡已补齐标签切换与缩略列表联动：点击标签可切换历史 / 更新 / 推荐数据源，点击底部缩略项可切换当前展示内容，历史标签下会优先识别当前续读作品并将主按钮切换为继续阅读
- `Home` 首个 tracking Hero 已继续压缩：移除条带下半段重复标题 / 副标题，只保留头部、分页条带与指示器/评分，减少首页首屏纵向占用
- `Home` 快捷入口已收口为单张卡片：同步与源入口并入 `Quick access`，原先独立的同步 / 源概览卡与窄屏冗余统计层已从首页主流中移除
- `Home` 首页结构重排后再次通过 `./gradlew :app:compileDebugKotlin --no-daemon` 验证
- `Home` 顶部三标签主卡已继续减重：移除左上角 `Home` 角标与重复的历史 / 更新 / 收藏 / 推荐统计 pill，内容区改为标题 + 可读源名 + 简介摘要，避免首页首卡重复堆叠状态信息
- `Home` 顶部主卡已改用 `ContentSource.getTitle(context)` 展示源标题，Mihon / JSON / IReader 等动态源不再直接暴露 `MIHON_xxx` 这类内部 ID
- `Home` 顶部主卡 CTA 已压缩为紧凑动作行：宽屏保留单行动作，窄屏直接悬浮到封面右下角；首页 tracking 区也已收口为仅保留首张每日放送卡，不再继续展示后续 tracking 列表
- `MainActivity` 已修复横竖屏切换恢复时序崩溃：`onRestoreInstanceState()` 早于 Compose 容器挂载时会先挂起导航同步，待 `navigationDelegate` 初始化完成后再补做 `syncSelectedItem()`，避免 `lateinit property navigationDelegate has not been initialized`
- 本轮首页收口与旋转崩溃修复后再次通过 `./gradlew :app:compileDebugKotlin --no-daemon` 验证
- `Home` 顶部三标签按钮已改为固定单行等宽布局，并对大计数做压缩显示，避免过滤器切换时按钮换行把 Hero 高度撑大且无法恢复
- `Home` 顶部 Hero 已接入更稳的动态源解析：会通过 Mihon / Aniyomi / IReader 扩展管理器回查真实源标题，避免首页长期停留在 `Loading Mihon source...`
- `Home` 顶部 Hero 的封面已补齐源胶囊：左下角会显示来源图标与简短来源/语言信息；下方缩略卡尺寸也已进一步收窄，减少顶部卡片整体纵向占用
- `Home` 首张 tracking Hero 已继续压缩固定高度与 pager 尺寸，去掉首卡下半段的大块空白，并减弱不同封面比例对卡片高度的影响
- 本轮首页细节收口后再次通过 `./gradlew :app:compileDebugKotlin --no-daemon` 验证
- `Main` Compose 壳已改为复用稳定的 `FragmentContainerView` 实例，并在 `AndroidView` 创建时立即回传容器；不再依赖 `post { onContainerReady(...) }`，减少横竖屏恢复阶段容器晚到导致的白屏窗口
- `MainNavigationDelegate` 已补上 fragment 视图重挂逻辑：恢复后的当前页如果 view 丢失或未重新附着，会强制 `detach/attach` 到新容器，再同步当前底栏选中态，针对“旋转后所有页面空白”做第二轮修复
- `Home` tracking Hero 已进一步固定高度并压缩标题区、pager 行与海报规格，优先消除切换作品后高度瞬间正常又被重新撑长的问题
- `Home` 首页内容卡、续读卡与封面小卡已统一改用真实可读源名；封面左下来源胶囊也改为优先显示真实语言，而不是把 `Mihon` 当作语言文本
- `Details` 头部封面已在 Compose 内直接渲染真实封面，并保留点击查看大图；标题区加入分段式左移淡出，滚动时会按“封面 -> 文本 -> 收藏/翻译按钮”的顺序收口到简化工具栏
- 本轮 Main/Home/Details 收口后再次通过 `./gradlew :app:compileDebugKotlin --no-daemon` 验证
- `DetailsActivity` 已继续收紧共享元素封面过渡：XML 封面只保留转场职责，Compose 封面在图片加载完成后会触发转场层淡出，减少详情页下拉过程中的封面重影与底部残留
- `Details` 章节 / 页面 / 书签顶部切换已重新改回纯图标 tab；收藏 / 翻译按钮移除 `IconButton` 内层默认底板，基础信息卡与简介卡也统一压暗容器色，降低详情页中多处违和浅色背景块
- `DetailsScreen` 折叠后的顶部工具栏现已保留返回、分享、下载与 `More` 动作，标题只在收口后显示并带单行省略，避免滚动时右侧动作栏突然消失
- `Home` 顶部三标签主卡的“推荐”图标已改回 `feed` 语义；首张 tracking Hero 继续减重，指示器与评分被下移到 pager 下方独立一行，同时进一步压低容器底色，减少与封面的重叠和浅底割裂感
- `Home` 通用 `DashboardCard` 已继续收口背景 token：首页常用卡片统一改为更低透明度的 `surfaceContainer`，用于压制当前首页多处偏亮背景块
- `AppearanceSettingsScreen` 已补根级 `Surface(background)`，修复 `设置 -> 外观` 整页透出旧 View 宿主底色的问题
- `ComposeAppNavBarDelegator` 与 `AppNavBarDelegatorAdapter` 已补上动态导航项同步：重建菜单时会清空旧 menu、尽量保留当前选中项，并监听 `KEY_NAV_MAIN`，减少导航配置变更后的空白页与选中态错乱
- `MainNavigationDelegate` 已继续收口主壳恢复时序：设置变更只在当前主 Fragment 与目标页不一致时才主动切页，优先缓解主壳出现“双层 Home”叠加渲染的问题
- `SwipeableFilterChip` 已把内容类型按钮的默认单击目标改回漫画：当当前未选中或状态失效时，会优先高亮 `MANGA`，不再默认切到视频
- 本轮 Main/Home/Details/Settings 收口后再次通过 `./gradlew :app:compileDebugKotlin --no-daemon` 验证

### 进行中

- 继续推进 Discover tracking 首页的视觉语言统一
- 继续评估哪些 Dialog / Sheet 优先迁移到 Compose，避免 Details 仍混用过多 XML 宿主

### 下一步

- 继续推进 Main Shell 与导航壳收口
- 继续强化 Discover / Details 的 Compose 视觉一致性
- 选择一组高频 Dialog / Sheet 进入 Compose 化，优先处理详情页上下文动作链路
- 继续收口 `Home` / `Discover` Hero 的共享视觉 token，优先评估 CTA、badge 与卡片前景构图是否继续抽成更细粒度组件
- 继续推进 `Home` 首页的 Dantotsu 化内容层，评估本地 Hero 与 tracking Hero 之间的层级关系是否还需进一步压缩

- 已完成应用外壳大重构：纯 Jetpack Compose Navigation 架构落地，全面替换 XML 布局残留
- `MainActivity` 已彻底移除对 `FragmentContainerView` 与 `MainNavigationDelegate` 的依赖，解决了因硬件加速或 Fragment 挂载生命周期导致的崩溃
- 新增 `AppNavGraph.kt` 组件接管了主导航（Home, Discover 等 Tab 结构），消除了 `KototoroApp.kt` 里的 `AndroidView { FragmentContainerView(it) }` 套壳
- 针对目前尚未 Compose 化的老旧纯 Fragment 设计，加入了兼容层 `FragmentHostRoute`
- 已在 `gradle/libs.versions.toml` 与 `app/build.gradle` 引入并集成了 `androidx.hilt:hilt-navigation-compose` 并成功走通依赖流程
- 解决了因为 `DiscoverScreen` 和 `HomeScreen` 的 Compose 导航签入产生的所有相关 Kotlin 编译类型和 Lambda 参数校验问题，验证通过。
- 本轮架构级别重构后再次通过 `./gradlew :app:assembleDebug` 验证

### 进行中

- 验证 Compose NavHost 下 BottomNavBar 的状态反向同步并排查边缘体验和白屏问题。

### 下一步

- 彻底将 `FragmentHostRoute` 包壳托管的页面重构为纯 Compose 页面。
