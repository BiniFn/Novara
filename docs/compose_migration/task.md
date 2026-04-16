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
- [/] Phase 3: Pure Compose TopAppBar & Native Search
  - [ ] Overhaul `activity_main.xml` to pure `FrameLayout` overlay
  - [ ] Implement `KototoroTopBar.kt` (M3 TopAppBar & SearchBar)
  - [ ] Integrate explicit NestedScrolling dispatch from `MainActivity` FrameLayout to Compose
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
- [ ] Phase 3: 高频内容页迁移
- [ ] Phase 4: Dialog / Sheet Compose 化
- [ ] Phase 5: 共享层抽取
