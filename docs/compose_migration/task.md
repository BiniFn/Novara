# Compose 迁移：后续计划

> 当前状态看 `status-snapshot.md`。
> 历史决策看 `decision-log.md`。
> 本文件只保留**尚未完成且仍与当前代码相符**的工作。

---

## 迁移优先级原则

- 按桥接深度推进，而不是只看“有没有用了 Compose”。
- 优先把仍停在 L1 的高频入口拉到 L2 / L3。
- 先清真实旧壳，再做共享层与 CMP 方向评估。
- 只记录当前代码还能直接支持的待办，不继续堆积失真的验收文案。

---

## Step 0：文档基线重校

- [x] 用 2026-04-21 当前代码重写 `status-snapshot.md`
- [x] 在 `decision-log.md` 记录本次重新校线与结论回滚
- [x] 重排 `task.md`，只保留当前仍成立的迁移任务
- [x] 以 `./gradlew :app:compileDebugKotlin --no-daemon` 作为本轮文档基线校验

### 0.1 迁移兼容收口

- [ ] 为设置备份建立显式 schema/versioned migration，避免未来继续只靠 key 级 sanitize 兜底
- [ ] 为应用直接升级场景补齐更细粒度的版本分段迁移，不要长期停留在“版本变化即全量 sanitize”这一层
- [ ] 把 pre-Compose 旧备份恢复的高风险偏好整理成回归用例，至少覆盖 nav/grid/panorama/popup/search suggestions/list badges
- [ ] 继续梳理哪些 UI 偏好仍可能因为旧 key 或旧值域在 Compose 首屏读取阶段造成异常
- [ ] 为主页恢复备份场景补一条真实 UI 回归用例，至少覆盖高亮三合一卡片可渲染、无 Haze 渲染崩溃

---

## Phase 1（当前优先级最高）：详情页从 L1 向 L2 收口

### 1. `DetailsActivity` 去壳

- [ ] 将 `DetailsActivity` 从 `ActivityDetailsBinding` + `composeView.setContent {}` 迁到直接 `setContent {}` 的 Compose host
- [ ] 保留现有共享元素转场与封面 bounds 同步能力，不因为去壳破坏转场体验
- [ ] 继续审计仍会打开 `DetailsActivity` 的非统一 Compose 卡片链路；Home、Feed 和通用 Discover 组件已接入，剩余入口按真实跳转目标收口
- [ ] 评估是否还需要保留系统级共享元素锚点方案，还是直接以自绘封面过渡作为 Compose 终态
- [ ] 收敛 `DetailsActivity.onActionClick` 中的空分支，避免 Activity 继续承担无效 action 中转
- [ ] 明确普通详情页里的 Download / Stats / Scrobbling 交互是否全部以下沉到 `DetailsScreen` 为终态

### 2. Tracking 统一体验收尾

- [ ] 将 `DetailsViewModel.refreshTrackingMatchSuggestion()` 从“仅本地内容”扩展到非本地内容
- [ ] 明确多绑定场景的展示策略：继续只显示首个绑定卡片，或升级为多卡片/列表
- [ ] 统一普通详情页与 tracking 详情页的打开、绑定管理、解绑、外链跳转语义

**Critical files**
- `app/src/main/kotlin/org/skepsun/kototoro/details/ui/DetailsActivity.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/details/ui/DetailsViewModel.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/details/ui/compose/DetailsScreen.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/details/ui/compose/DetailsHeader.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/details/ui/compose/DetailsBindingCard.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/discover/ui/details/TrackingSiteDetailsActivity.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/discover/ui/details/TrackingSiteDetailsScreen.kt`

---

## Phase 2：设置系统最后的 legacy 壳清理

### 3. 清理 tabbed host

- [ ] 用纯 Compose tabs / nav 替换 `SettingsTabbedFragmentsScreen`
- [ ] 去掉 `AndroidView + FragmentContainerView` 的 tab 承载方式
- [ ] 收口 `JsonSourcesRootFragment` / `ExtensionsRootFragment` 的 legacy 子页挂载

### 4. 清理 Source Settings legacy 路径

- [ ] 继续扩展 `SourceComposeSettingsFragment` 覆盖范围，逐步替换复杂来源的 `SourceSettingsFragment`
- [ ] 把 `SourceSettingsFragment` 中的动态 `PreferenceFragmentCompat` 逻辑迁到 Compose DSL / Screen
- [ ] 删除 `pref_source.xml` 与 `pref_source_parser.xml`
- [ ] 评估 `pref_root.xml` 与 `pref_sync_header.xml` 的去留及替代方案
- [ ] 在 Source Settings 去壳完成后，再评估移除 `androidx.preference` 依赖

**Critical files**
- `app/src/main/kotlin/org/skepsun/kototoro/settings/compose/SettingsTabbedFragmentsScreen.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/settings/sources/SourceSettingsHostFragment.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/settings/sources/SourceComposeSettingsFragment.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/settings/sources/SourceSettingsFragment.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/settings/sources/SourceSettingsExt.kt`
- `app/src/main/res/xml/pref_source.xml`
- `app/src/main/res/xml/pref_source_parser.xml`

---

## Phase 3：Dialog / Sheet 外围壳收口

- [ ] `DownloadDialogFragment` 去壳，统一到 Compose dialog 路径
- [ ] `ContentStatsSheet` 去壳，避免 `AppRouter.openStatistic()` 继续依赖 `BaseAdaptiveSheet`
- [ ] `ScrobblingSelectorSheet` 去壳，逐步统一到 Compose selector dialog
- [ ] `ChaptersPagesSheet` 仅在 Reader / Video 路径保留的现状继续收窄，最终改为纯 Compose 宿主

**Critical files**
- `app/src/main/kotlin/org/skepsun/kototoro/download/ui/dialog/DownloadDialogFragment.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/stats/ui/sheet/ContentStatsSheet.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/scrobbling/common/ui/selector/ScrobblingSelectorSheet.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/details/ui/pager/ChaptersPagesSheet.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/core/nav/AppRouter.kt`

---

## Phase 4：主壳深化

- [ ] 将 `MainActivity` 从 `ActivityMainBinding` + `setContentViewWebViewSafe` 迁到直接 Compose host
- [ ] 收敛 Activity 里的顶栏、过滤器、insets 等 `mutableStateOf` 状态
- [ ] 视需要为主壳引入更清晰的 app state/controller 边界，减少 `KototoroApp(...)` 参数面继续膨胀

**Critical files**
- `app/src/main/kotlin/org/skepsun/kototoro/main/ui/MainActivity.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/main/ui/compose/KototoroApp.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/main/ui/compose/AppNavGraph.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/main/ui/SearchBarFilterViewController.kt`

---

## 暂缓项

- [ ] Reader / Video 全量架构重写
- [ ] 全仓 ViewModel 模式重构
- [ ] 全局设计系统大拆分
- [ ] 共享层 / CMP 的工程化落地

---

## 中长期方向

- [ ] 评估 `shared/designsystem` 或等价模块拆分时机
- [ ] 在 Source Settings 与主壳去壳完成后，再评估首批 `commonMain` 友好组件
- [ ] 为未来 `expect/actual` 桥接保留边界，但当前不提前实现

---

## 持续校验要求

- [ ] 后续继续实现前，先跑 `./gradlew :app:compileDebugKotlin --no-daemon`
- [ ] 每次更新文档时，只记录代码能直接证明的状态
- [ ] `status-snapshot.md` / `decision-log.md` / `task.md` 继续保持“状态 / 历史 / 计划”分工
