# Compose Migration Tasks

## 当前主任务

- [/] Phase 0: 建立 Compose Multiplatform 与 Liquid Glass 迁移基线
  - [x] 新建迁移主文档
  - [x] 明确阶段目标、边界与非目标
  - [x] 建立基础玻璃设计系统组件
  - [x] 将基础玻璃组件接入现有主壳
  - [x] 完成首轮 Kotlin 编译验证

## 已完成的主壳迁移阶段

- [x] Phase 1: Create Compose Root Shell (`KototoroApp.kt` & `AppNavGraph.kt`)
  - `KototoroApp` Scaffold structure
  - Interop placeholder for Fragment host
- [x] Phase 2: Refactor `MainActivity.kt` and remove Legacy XML
  - Delete `activity_main.xml`
  - Refactor `MainActivity` layout inflation to use `setContent { KototoroApp() }`
  - Remove/Deprecate `MainNavigationDelegate` and XML binding logic
- [x] Phase 3: Pure Compose TopAppBar & Native Search
  - [x] Overhaul `activity_main.xml` to pure `FrameLayout` overlay
  - [x] Implement `KototoroTopBar.kt` (M3 TopAppBar & SearchBar)
  - [x] Integrate explicit NestedScrolling dispatch from `MainActivity` FrameLayout to Compose
- [x] Phase 4: Refactor Fragment Spacing (`AppBarOwner` Removal)
  - [x] Remove `AppBarOwner` interface
  - [x] Remove `AppBarOwner` dependencies from fragment layout logic
  - [x] Inject uniform Top/Bottom padding via `MainActivity` into `FragmentContainerView`
- [ ] Final Verification & Walkthrough

## 后续阶段

- [/] Phase 1: Settings 全量 Compose 化
  - [x] Root Settings 入口切换为 Compose
  - [x] 建立第一版设置入口 DSL
  - [x] 迁移首个二级设置页（`StorageAndNetwork`）
  - [x] 迁移第二个二级设置页（`Appearance`）
  - [/] 建立 Compose switch / slider / list item / text input / multi-choice / warning card / split switch / info row / enabled-state 组件语义
  - [x] 迁移 `Services`
  - [x] 迁移 `Downloads`
  - [x] 迁移 `Tracker`
  - [x] 迁移 `Sources`
  - [x] 收口 Settings 残余 Android-only 子页（`Backups` / `Notification legacy` / `JSON Sources` / `Extensions`）
- [/] Phase 2: Main Shell 与导航壳收口
  - [x] 补齐 Compose 顶栏搜索建议点击与旧路由行为对齐（`Content` / `Tag` / `Source` / `Author` / 链接直达）
  - [x] 补齐 Compose 顶栏过滤器与旧 Fragment 语义对齐（可见性 / 禁用态 / 来源入口 / 自定义筛选点击）
  - [x] 打通 Compose 顶栏查询状态与语音输入回填链路
  - [x] 恢复 Compose 顶栏全局语言预设入口，并与全局源预设激活状态对齐
  - [x] 补齐 Fragment 销毁时的筛选回调释放，避免主壳持有陈旧顶栏状态
  - [x] 收口导航壳稳定性问题：旋转恢复、容器重挂、动态导航项同步与选中态保留已补齐第一轮修复
- [/] Phase 3: 高频内容页迁移
  - [x] Home Compose 对齐首轮落地：快捷入口改为自适应矩阵，列表模式在宽屏下支持三栏并排，Sync 状态时间改为可读文本
  - [x] Home 概览卡落地：继续阅读、收藏/分类、启用源、默认 tracking 站点已接入首页 Compose 展示
  - [x] Home Dantotsu 第二轮骨架落地：首页已重构为 Hero 门户、继续发现入口、分层内容区与工具区，且可从 `Home` 直接切换到 `Discover`
  - [x] Home tracking 精选流接入：首选 tracking 站点的多组 trending 分类已注入首页，并支持首页直达 tracking 详情与分类页
  - [x] Home 首组 tracking section Hero 化：已升级为分页 Hero 卡组与详情联动区，首页顶部形成双层发现结构
  - [x] Discover tracking 首页首轮视觉强化：顶部 Hero 轮播、模糊背景联动与自动轮播已接入 Compose 首页
  - [x] Discover tracking 分类横滑动效收口：分类行卡片已补齐位移/缩放/透明度过渡，并修复 carousel / grid 空态白屏
  - [x] Details Header 收口：接入收藏分类、译文标题/简介、来源/作者/标签动作，移除 Compose 占位文案
  - [x] Details Compose 行为回接：分享、下载、来源跳转、作者/标签弹层与翻译切换已复用现有 router / `DetailsViewModel`
  - [x] Details Overflow Menu 收口：`More` 菜单已补齐翻译、相似内容、在线版本、浏览器、追踪、统计与 NSFW 切换等旧菜单核心动作
  - [x] Details Overflow Menu 低频动作收口：已补齐本地删除、override 编辑与创建快捷方式，并复用旧确认弹窗、override 返回刷新与快捷方式请求链路
  - [x] Details Bottom Dock 收口：底部阅读入口已接入动态主按钮文案、分支选择下拉与当前分支回写
  - [x] Details Bottom Dock 旧 split-button 语义收口：已补齐无痕阅读、从历史移除、下载入口与分支菜单
  - [x] Details 章节/页面/书签内嵌 sheet 稳定性修复：切换 Tab 改为使用 Compose 协程上下文驱动 `PagerState.animateScrollToPage()`，修复 `MonotonicFrameClock` 崩溃
  - [x] Details Dantotsu 风格视觉收口：封面外框 / 状态徽标 / 分组信息卡 / 悬浮顶栏按钮 / 浮动阅读 dock 已统一到 Compose 视觉语言
  - [x] Home/Details 第三轮细节收口：真实源名与来源胶囊、三标签 Hero、tracking strip 压缩、折叠工具栏保留动作、icon-only tabs 与详情卡背景统一已落地
- [ ] Phase 4: Dialog / Sheet Compose 化
- [/] Phase 5: 共享层抽取
  - [x] 抽取 `Home` / `Discover` 共用 Hero 底座：背景容器、自动轮播、分页指示器
  - [ ] 继续评估 Hero 前景卡片、badge、CTA 是否需要进一步组件化
