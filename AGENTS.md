# Repository Guidelines

## Project Structure & Module Organization
`app/` contains the Android application. Main Kotlin sources live under `app/src/main/kotlin/org/skepsun/kototoro` and are organized by feature with `data`, `domain`, and `ui` layers. Android resources, menus, themes, and preference XML files live in `app/src/main/res`. Shared parser contracts are in `parser-api/`. Unit tests belong in `app/src/test/kotlin`, instrumented tests in `app/src/androidTest/kotlin`, and Room schemas in `app/schemas/`. Documentation lives in `docs/` and is built with VitePress from the root `package.json`.

## Build, Test, and Development Commands
Use the bundled wrappers and keep commands scoped:

- `./gradlew :app:assembleDebug` builds a debug APK.
- `./gradlew :app:compileDebugKotlin --no-daemon` is the fastest compile-only validation for Kotlin changes.
- `./gradlew :app:testDebugUnitTest --no-daemon` runs JVM unit tests.
- `./gradlew :app:connectedDebugAndroidTest` runs instrumented tests on a device or emulator.
- `npm ci && npm run docs:dev` starts the local docs site.
- `npm run docs:build` builds the static docs output.

## Coding Style & Naming Conventions
Follow `.editorconfig`: UTF-8, LF, 4-space indentation, and 120-character line width. Kotlin uses the official style with trailing commas enabled. Name classes and test classes in `PascalCase`, methods and properties in `camelCase`, and Android resources in lowercase underscore style such as `pref_appearance.xml` or `mode_chapters.xml`. Prefer extending existing feature modules instead of creating parallel implementations. Keep comments short and in the same language already used nearby.

## Testing Guidelines
The project uses JUnit5, Kotest, MockK, and MockWebServer for unit tests, plus AndroidX Test, Hilt, and Room testing. Match test file names to the target type, typically `*Test.kt`, `*IntegrationTest.kt`, or `*PropertyTest.kt`. Changes to parser flows, networking, database code, downloads, or reader behavior should include focused coverage or at least a compile/test pass before review.

## Commit & Pull Request Guidelines
Recent history mixes short imperative subjects with Conventional Commits, and the latter is preferred, for example `feat: ...`, `fix(scope): ...`, `docs: ...`, or `chore: ...`. Keep each commit narrowly scoped. Pull requests should explain motivation, affected areas, validation commands, and risks. Include screenshots for UI, reader, download, or documentation changes, and link the related issue when one exists.

## Security & Contributor Notes
Do not commit `local.properties`, signing files, secrets, caches, or generated artifacts. Translation content is managed through Weblate, so avoid bulk manual string rewrites unless fixing a clear defect. Keep `README.md` product-focused; put deeper engineering notes in `docs/`. When touching download, translation, super-resolution, or local storage flows, verify both UI entry points and the background task pipeline.


<claude-mem-context>
# Memory Context

# [Kototoro] recent context, 2026-04-29 8:51pm GMT+8

Legend: 🎯session 🔴bugfix 🟣feature 🔄refactor ✅change 🔵discovery ⚖️decision 🚨security_alert 🔐security_note
Format: ID TIME TYPE TITLE
Fetch details: get_observations([IDs]) | Search: mem-search skill

Stats: 50 obs (13,516t read) | 546,542t work | 98% savings

### Apr 29, 2026
5 7:15p 🔵 SettingsActivity 仍引用已删除的 UnifiedSourcesFragment
10 " 🔴 删除 UnifiedSourcesFragment 后编译成功，SettingsActivity 引用已修复
6 7:16p 🔵 Kototoro 项目结构与待修复 Issues 发现
11 7:17p 🔵 Settings 模块 Compose 迁移整体进度：大量 Fragment 已修改，新增多个 Compose Screen 文件
7 " 🔵 Kototoro 项目现有代码文件结构确认
8 " 🔵 Kototoro 项目包含两套代码：demo 练习代码与完整 Kotatsu 源码
9 7:19p 🔵 Kototoro 四个 issue 相关组件深度探索完成
14 7:20p 🟣 HomeScreen 添加 TopAppBar 和设置导航入口（Issue #179）
15 " 🔵 Kototoro 与 Kototoro_devel 的 ReaderActionsView 差异确认
12 7:22p 🔵 SettingsActivity 已完全迁移至 Compose 导航，Fragment 路由仅保留兼容层
13 7:23p 🔵 UnifiedSourcesScreen.kt 已完全独立，不再依赖 Fragment 层
17 " 🔵 用户发现拓展管理页面有加载过程且像内嵌页面
16 7:28p 🔵 FastScroller 自定义快速滚动条完整实现细节
19 7:36p ⚖️ Kototoro UI Issues #178/#179/#182/#184 — All Option A Selected
18 " 🔵 SettingsActivity 使用单一 ComposeView 容器渲染所有 Compose 目的地
24 " 🔵 用户对 UnifiedSources 页面提出 UI 布局需求
20 7:40p ⚖️ Kototoro #179 VerticalScrollbar Enhancement Design Approved
21 7:45p 🔵 Kototoro ChapterItemCard Already Has onLongClick — Different from Cached Version
22 7:46p 🔵 SearchViewModel Has Full Filter State — AssistChips Are Disconnected Display-Only
S23 修复 Kototoro Android 项目四个 UI 问题，#184 设计方案细化讨论中，#179 已开始写代码 (Apr 29 at 7:57 PM)
23 7:58p ⚖️ #182 AlternativesActivity → BaseAdaptiveSheet 设计方案确认
S24 修复 Kototoro Android 项目四个 UI 问题，#184 设计方案最终确认讨论，#179 代码已写入 (Apr 29 at 8:00 PM)
S22 修复 Kototoro Android 项目四个 UI 问题，当前展示 #184 搜索过滤交互重设计方案 (Apr 29 at 8:00 PM)
25 8:00p 🔴 SettingsActivity.kt 编译通过，UnifiedSources 嵌入式渲染链路断开
26 8:01p 🔵 UnifiedSourcesActivity.kt 已存在，基于 BaseActivity + XML binding 而非 FragmentActivity
27 " 🔵 UnifiedSourcesScreen 布局结构：ToolbarControls 在 Column 内，FilterTabs 有 vertical=6.dp padding
54 " 🔴 UnifiedSourcesScreen crash: LazyColumn infinite height in PullToRefreshBox
S25 修复 Kototoro Android 项目四个 UI 问题，设计规格已提交，#179 实现完成，等待用户审查规格文档 (Apr 29 at 8:03 PM)
28 8:03p 🟣 #179 VerticalScrollbar.kt 重写为 Composable 包装器
29 8:06p ✅ UI 问题 #178/#179/#182/#184 设计规格文档已写入
30 " 🟣 #179 VerticalScrollbar.kt 完成：coroutineScope 拖拽滚动已接通
S26 修复 Kototoro Android 项目四个 UI 问题 (#178 章节多选、#179 滚动滑块、#182 迁移底部弹窗、#184 搜索过滤器) (Apr 29 at 8:08 PM)
S27 修复 Kototoro Android 项目四个 UI 问题 (#178 #179 #182 #184) — 计划完成，开始执行阶段 (Apr 29 at 8:08 PM)
S28 保留进度到文档，方便在下一个机器接手 — 创建交接文档并提交 (Apr 29 at 8:19 PM)
42 8:20p 🟣 #178 ChapterSelectionBar composable created
43 " 🟣 #178 ChaptersPagesSheet wired to chapter selection state
44 " ✅ #178 and #179 pending git commits
31 8:27p 🔴 Kototoro 设置页旋转时崩溃：LazyColumn 嵌套无限高度约束
32 " 🔵 Kototoro 项目使用 Superpowers 技能框架进行结构化开发
33 " 🔵 Kototoro 设置页架构：所有设置 Screen 均为 Compose，通过 Fragment 托管于 SettingsActivity
34 " 🔵 SettingsActivity 布局结构：ComposeView 直接挂在 CoordinatorLayout 下，无 LinearLayout weight 问题
35 8:28p 🔵 旋转崩溃根因定位：SettingsTabbedFragmentsScreen 中 Fragment 托管的 ComposeView 在旋转时获得无限高度约束
36 " 🔵 根因确认：ReaderSettingsScreen 使用 Column + verticalScroll，被 SettingsSectionScaffold 的 Box(weight=1f) 包裹，旋转时约束正常
37 8:29p 🔵 SettingsTabbedFragmentsScreen 布局安全，PeriodicalBackupSettingsFragment 的 ComposeView 无高度约束保护
38 " 🔵 NavConfigScreen.kt:108 的嵌套 LazyColumn 带有 heightIn(max=360.dp)，约束安全
39 " 🔵 SettingsActivityLayoutBinding 是代码动态构建的布局，单栏模式使用 LinearLayout + weight=1f 包裹 ComposeView
40 8:30p 🔵 设置页存在大量 Fragment 直接返回裸 ComposeView，且 UnifiedSourcesScreen 是唯一同时含 verticalScroll 和 LazyColumn 的文件
41 " 🔵 SettingsActivity 是本次大规模重构的新文件，从 ActivitySettingsBinding + Fragment 架构迁移到 SettingsActivityLayoutBinding + Compose 导航架构
45 8:31p 🔴 修复设置页旋转闪退：SettingsActivityLayoutBinding 改用 XML 布局
S29 保留进度到文档，方便在下一个机器接手 — 创建并提交 HANDOFF.md 交接文档 (Apr 29 at 8:42 PM)
S30 保留进度到文档供下一台机器接手 — HANDOFF.md 已创建并提交，会话结束 (Apr 29 at 8:44 PM)
46 8:46p 🟣 Kototoro 设置页旋转崩溃修复已提交 git commit
47 " 🔵 Kototoro 设置页重构范围远超旋转崩溃修复
48 8:47p ✅ 旋转崩溃修复三文件已暂存，准备提交
49 " 🔵 git index.lock 冲突：两个进程同时操作 git 暂存区
50 8:48p ✅ SettingsActivity.kt 从暂存区移除，commit 范围缩小为纯修复文件
51 8:49p 🔵 SettingsActivity.kt 中 setLegacyTopBarVisible 和 appbarDetail 修复确认在工作区未暂存变更中
52 " ✅ SettingsActivity.kt 重新加入暂存区，三文件全部就绪
53 " 🔴 旋转崩溃修复已提交到 compose 分支
S31 用户报告设置页面横屏切换闪退（IllegalStateException: LazyColumn infinite height），要求修复；同时继续之前的 UnifiedSourcesScreen UI 重构工作 (Apr 29 at 8:50 PM)
**Investigated**: - 读取了 adb logcat 确认崩溃详情：PID 20350 在 04-29 20:05:43 崩溃，IllegalStateException: Vertically scrollable component measured with infinity height，来自 LazyList.kt:206
    - 读取了 UnifiedSourcesScreen.kt 行 860-1031 确认当前代码结构
    - 读取了 UnifiedSourcesActivity.kt 行 44-72 确认 Activity 代码正确
    - 读取了 UnifiedSourcesRoute 调用点（行 210-253）确认参数正确
    - 运行 git diff --stat 确认只有 UnifiedSourcesScreen.kt 有改动（209 insertions, 184 deletions）
    - 读取了 .codex/skills/using-superpowers/SKILL.md 和 chinese-commit-conventions/SKILL.md
    - 运行 git status 查看完整工作区状态

**Learned**: - 崩溃（PID 20350）来自旧版本 APK，不是当前编译的新版本
    - 当前代码中 KototoroPullToRefreshBox 使用 Modifier.fillMaxWidth().weight(1f) 放在 Column(fillMaxSize) 内，已正确给定有界高度，不会触发 LazyColumn 无限高度崩溃
    - UnifiedSourcesActivity.kt 是新增文件（untracked），不在 git diff 中，这是正常的
    - 工作区有大量修改文件，包括多个 Settings Fragment 文件、布局文件、strings.xml 等，以及新增的 .codex/skills/ 目录
    - ADB daemon 在第二次调用时失败（port conflict），但第一次调用已获取到所需日志
    - 项目使用中文提交规范（.codex/skills/chinese-commit-conventions 存在）

**Completed**: - UnifiedSourcesScreen.kt 重构完成并编译通过（BUILD SUCCESSFUL）：
      * TopAppBar actions 槽放置搜索/语言/更多过滤按钮
      * 过滤器栏（UnifiedSourcesFilterTabs）直接放在 topBar Column 内，紧贴标题栏
      * 两行过滤器间距压缩为 spacedBy(2.dp)，padding top=0.dp bottom=4.dp
      * KototoroPullToRefreshBox 包裹内容列表，支持下拉刷新
      * weight(1f) 确保 PullToRefreshBox 有界高度
    - 所有编译错误已修复（imports、@OptIn、smart cast、参数不匹配）
    - 确认崩溃是旧版本问题，新版本代码已修复该问题

**Next Steps**: - 运行 ./gradlew :app:installDebug 将新版本安装到设备（3B15AM00WXS00000）
    - 在设备上验证 UI 效果：标题栏右侧图标、过滤器栏紧凑布局、下拉刷新
    - 验证横屏旋转不再崩溃
    - 准备 git commit（使用中文提交规范）


Access 547k tokens of past work via get_observations([IDs]) or mem-search skill.
</claude-mem-context>