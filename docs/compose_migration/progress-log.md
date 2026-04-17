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

### 进行中

- 继续推进 Discover tracking 首页的视觉语言统一
- 评估是否将更多横向分类行升级为更强层级的卡片布局与动效

### 下一步

- 继续推进 Main Shell 与导航壳收口
- 继续强化 Discover / Details 的 Compose 视觉一致性
