# 实现进度交接文档

**日期：** 2026-04-29
**分支：** compose
**当前状态：** #179 已完成，#178 进行中（任务 1/2 待实现）

---

## 已完成

### #179 滚动滑块增强 ✅

所有 3 个任务已完成并通过两阶段审查（规格 + 代码质量）。

**提交记录：**
- `19d0a52af` — fix(#179): enhance verticalScrollbar with draggable thumb and track
- `0037148db` — fix(#179): address code quality issues in VerticalScrollbar
- `4ce47c360` — fix(#179): pass labelProvider to verticalScrollbar in chapters and pages screens
- `f9e296700` — fix(#179): increase reader slider thumb radius and track height

**变更摘要：**
- `VerticalScrollbar.kt`：LazyListState 版本新增可拖拽 thumb（`draggable=true` 默认），track 背景，最小 thumb 高度 48dp，默认宽度 8dp；LazyGridState 版本同样更新视觉但不支持拖拽
- `ChaptersScreen.kt`：LazyColumn scrollbar 传入 `labelProvider`，LazyVerticalGrid scrollbar 传入 `draggable=false`
- `PagesScreen.kt`：scrollbar 传入 `labelProvider`
- `layout_reader_actions.xml`：Slider 增加 `thumbRadius=10dp`、`trackHeight=4dp`

---

## 待完成

### #178 阅读器章节选择控件与详情页体验一致 ⏳

**计划文件：** `docs/superpowers/plans/2026-04-29-issue-178-reader-chapter-selection.md`

**背景知识（已调研）：**

`ChaptersPagesSheet` 结构：
- View 层：`binding.toolbar`（`MaterialToolbar`）内含 `TabLayout`（id: `tabs`）
- Compose 层：`binding.composePager.setContent { }` 内含 `HorizontalPager`
- `ChaptersScreenRoot` 在 `HorizontalPager` 的 `TAB_CHAPTERS` 页渲染（约 line 142-148）
- `ChaptersScreenRoot` 已有 `onSelectionStateChange: (ChapterSelectionUiState?) -> Unit` 回调，但 `ChaptersPagesSheet` 调用时未传入

**需要做的事：**

**任务 1：新建 `ChapterSelectionBar.kt`**

路径：`app/src/main/kotlin/org/skepsun/kototoro/details/ui/pager/chapters/compose/ChapterSelectionBar.kt`

内容见计划文件任务 1 步骤 1。注意：
- 先 grep 确认 `R.string.selected_count`、`R.string.select_all`、`R.string.mark_as_current` 是否存在
- 如不存在，在 `app/src/main/res/values/strings.xml` 中添加对应字符串

**任务 2：修改 `ChaptersPagesSheet.kt`**

路径：`app/src/main/kotlin/org/skepsun/kototoro/details/ui/pager/ChaptersPagesSheet.kt`

需要做三处修改：

1. 在 `setContent { }` 顶层添加状态变量：
```kotlin
var chapterSelectionState by remember { mutableStateOf<ChapterSelectionUiState?>(null) }
```

2. 在 `ChaptersScreenRoot(...)` 调用（约 line 142-148）添加回调：
```kotlin
onSelectionStateChange = { state ->
    chapterSelectionState = state
    viewBinding?.toolbar?.isVisible = state == null
},
```

3. 在 `setContent { }` 中，用 `Column` 包裹 `HorizontalPager`，在 pager 上方添加：
```kotlin
Column {
    chapterSelectionState?.let { selState ->
        ChapterSelectionBar(
            state = selState,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    HorizontalPager(...) { ... }
}
```

需要添加的 import：
```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.isVisible
import org.skepsun.kototoro.details.ui.pager.chapters.compose.ChapterSelectionBar
import org.skepsun.kototoro.details.ui.pager.chapters.compose.ChapterSelectionUiState
```

---

### #184 搜索过滤交互重设计 ⏳

**计划文件：** `docs/superpowers/plans/2026-04-29-issue-184-search-filter.md`

**背景知识（已调研）：**
- `SearchResultsScreen.kt` 中 `SearchSummaryRow`（约 line 521）的 chip 是 `AssistChip`，`onClick = {}`（空）
- `SearchViewModel` 已有 `setSourceTypes()`、`setContentKinds()`、`setPinnedOnly()`、`setHideEmpty()` 方法
- 需要确认 `setKind()` 方法是否存在（grep `SearchViewModel.kt`）
- `SearchKind` 枚举：SIMPLE、TITLE、AUTHOR、TAG、ADVANCED
- `SourceType` 枚举：需 grep 确认具体值
- `SearchContentKind` 枚举：需 grep 确认具体值（MANGA/NOVEL/VIDEO）

**需要做的事：** 见计划文件，共 9 步。核心是将 `AssistChip` 改为 `FilterChip`，添加 `onClick` 回调，来源/内容类型 chip 点击弹出 `DropdownMenu`，搜索类型 chip 循环切换 `SearchKind`，ADVANCED 时展开高级搜索输入框。

---

### #182 迁移功能体验重设计 ⏳

**计划文件：** `docs/superpowers/plans/2026-04-29-issue-182-alternatives-sheet.md`

**背景知识（已调研）：**
- `AlternativesActivity` 通过 `AppRouter.openAlternatives()` 启动（line 424-429）
- `BaseAdaptiveSheet<VB>` 是现有的 BottomSheet 基类
- `AlternativesViewModel` 有 `setPinnedOnly()`、`retry()`、`continueSearch()` 方法
- 需要确认：`viewModel.migrate()` 的实际方法名、`KEY_MANGA` 常量位置、`BaseAdaptiveSheet` 的 `inflateViewBinding` 方法名

**需要做的事：** 见计划文件，共 4 个任务。核心是新建 `AlternativesSheet`（继承 `BaseAdaptiveSheet`）、新建 `sheet_alternatives.xml`（含拖拽手柄和提示文案）、`AlternativeAD` 添加长按回调、`AppRouter` 改为 show sheet。

---

## 规格文档

`docs/superpowers/specs/2026-04-29-ui-issues-178-179-182-184-design.md`

## 执行方式

推荐使用 `subagent-driven-development` skill 继续执行，从 #178 任务 1 开始。
