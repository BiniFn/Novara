# Issue #178 阅读器章节选择控件与详情页体验一致 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在阅读器内的 `ChaptersPagesSheet` 章节 tab 中启用与详情页相同的长按多选操作模式。

**架构：** `ChaptersScreenRoot` 已有完整的多选逻辑和 `onSelectionStateChange` 回调，但 `ChaptersPagesSheet` 调用时未传入该回调。需要在 sheet 中接收选择状态，并在多选激活时将 tab 栏替换为操作栏。操作栏复用 `ChapterSelectionUiState` 数据，新建一个 `ChapterSelectionBar` Compose 组件。

**技术栈：** Jetpack Compose、`ChapterSelectionUiState`、`ChaptersPagesSheet`（继承 `BaseAdaptiveSheet`）

---

## 文件清单

| 文件 | 操作 | 职责 |
|------|------|------|
| `app/src/main/kotlin/org/skepsun/kototoro/details/ui/pager/chapters/compose/ChapterSelectionBar.kt` | 新建 | 多选操作栏 Compose 组件 |
| `app/src/main/kotlin/org/skepsun/kototoro/details/ui/pager/ChaptersPagesSheet.kt` | 修改 | 接收选择状态，切换 tab 栏/操作栏 |

---

### 任务 1：新建 ChapterSelectionBar 组件

**文件：**
- 新建：`app/src/main/kotlin/org/skepsun/kototoro/details/ui/pager/chapters/compose/ChapterSelectionBar.kt`

- [ ] **步骤 1：创建 ChapterSelectionBar.kt**

```kotlin
package org.skepsun.kototoro.details.ui.pager.chapters.compose

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.skepsun.kototoro.R

@Composable
fun ChapterSelectionBar(
    state: ChapterSelectionUiState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = state.onClearSelection) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.cancel),
            )
        }
        Text(
            text = stringResource(R.string.selected_count, state.selectedCount),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        if (state.canSelectAll) {
            IconButton(onClick = state.onSelectAll) {
                Icon(
                    imageVector = Icons.Filled.SelectAll,
                    contentDescription = stringResource(R.string.select_all),
                )
            }
        }
        if (state.canDownload) {
            IconButton(onClick = state.onDownload) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = stringResource(R.string.download),
                )
            }
        }
        if (state.canDelete) {
            IconButton(onClick = state.onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.delete),
                )
            }
        }
        if (state.canBookmark) {
            IconButton(onClick = state.onBookmark) {
                Icon(
                    imageVector = Icons.Filled.Bookmark,
                    contentDescription = stringResource(R.string.bookmark_add),
                )
            }
        }
        if (state.canMarkCurrent) {
            IconButton(onClick = state.onMarkCurrent) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.mark_as_current),
                )
            }
        }
    }
}
```

**注意：** 如果 `R.string.selected_count` 不存在，使用 `"已选 %d 章"` 格式字符串，需在 `res/values/strings.xml` 中添加：
```xml
<string name="selected_count">已选 %d 章</string>
```
先用 grep 确认该字符串是否已存在：
```bash
grep -r "selected_count\|已选" app/src/main/res/values/strings.xml | head -5
```

- [ ] **步骤 2：编译验证**

```bash
./gradlew :app:compileDebugKotlin --no-daemon 2>&1 | grep -E "error:" | head -20
```

预期：无编译错误。

- [ ] **步骤 3：Commit**

```bash
git add app/src/main/kotlin/org/skepsun/kototoro/details/ui/pager/chapters/compose/ChapterSelectionBar.kt
git commit -m "feat(#178): add ChapterSelectionBar composable for multi-select action bar"
```

---

### 任务 2：在 ChaptersPagesSheet 中接收选择状态并切换 UI

**文件：**
- 修改：`app/src/main/kotlin/org/skepsun/kototoro/details/ui/pager/ChaptersPagesSheet.kt`

- [ ] **步骤 1：读取 ChaptersPagesSheet.kt 当前内容**

先确认 `ChaptersPagesSheet.kt` 中 `ChaptersScreenRoot` 的调用位置（约 lines 142-148）和 tab 栏的 Compose 实现位置。

```bash
grep -n "ChaptersScreenRoot\|TabLayout\|tabLayout\|onSelectionStateChange\|composeView\|setContent" \
  app/src/main/kotlin/org/skepsun/kototoro/details/ui/pager/ChaptersPagesSheet.kt
```

- [ ] **步骤 2：在 sheet 的 Compose 内容中添加选择状态管理**

在 `ChaptersPagesSheet.kt` 的 `onViewBindingCreated` 方法中，找到设置 Compose 内容的地方（`setContent { ... }` 或 `ComposeView`）。

在 Compose 内容的顶层添加选择状态变量，并根据状态切换 tab 栏和操作栏：

```kotlin
// 在 setContent { } 内部顶层添加
var chapterSelectionState by remember { mutableStateOf<ChapterSelectionUiState?>(null) }
```

找到 `ChaptersScreenRoot` 的调用（约 lines 142-148），添加 `onSelectionStateChange` 参数：

```kotlin
ChaptersScreenRoot(
    viewModel = viewModel,
    router = router,
    context = requireContext(),
    viewForSnackbar = viewBinding.root,
    lifecycleOwner = viewLifecycleOwner,
    handleSelectionBackPressInternally = true,
    onSelectionStateChange = { state ->
        chapterSelectionState = state
    },
)
```

- [ ] **步骤 3：在 tab 栏位置根据选择状态切换显示**

找到 sheet 中渲染 `TabLayout`（`viewBinding.tabLayout`）的 Compose 代码区域。在 tab 栏的 Compose 容器上方添加条件渲染：

```kotlin
// 在 HorizontalPager 上方，tab 栏区域
val selState = chapterSelectionState
if (selState != null) {
    ChapterSelectionBar(state = selState)
} else {
    // 原有 tab 栏代码保持不变
    AndroidView(factory = { viewBinding.tabLayout })
    // 或原有的 TabRow/Tab 代码
}
```

**注意：** `ChaptersPagesSheet` 使用 View 体系的 `TabLayout`（XML），不是 Compose Tab。需要用 `AndroidView` 包裹，或者直接在 View 层控制可见性。

如果 tab 栏是 View 体系（`TabLayout`），改为在 `onSelectionStateChange` 回调中直接控制 View 可见性：

```kotlin
onSelectionStateChange = { state ->
    chapterSelectionState = state
    // 控制 View 层 tab 栏可见性
    viewBinding.tabLayout.isVisible = state == null
    viewBinding.toolbar.isVisible = state == null
}
```

并在 Compose 内容中，在 `HorizontalPager` 上方添加：

```kotlin
chapterSelectionState?.let { selState ->
    ChapterSelectionBar(
        state = selState,
        modifier = Modifier.fillMaxWidth(),
    )
}
```

- [ ] **步骤 4：添加必要 import**

```kotlin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.isVisible
import org.skepsun.kototoro.details.ui.pager.chapters.compose.ChapterSelectionBar
import org.skepsun.kototoro.details.ui.pager.chapters.compose.ChapterSelectionUiState
```

- [ ] **步骤 5：编译验证**

```bash
./gradlew :app:compileDebugKotlin --no-daemon 2>&1 | grep -E "error:" | head -20
```

预期：无编译错误。

- [ ] **步骤 6：Commit**

```bash
git add app/src/main/kotlin/org/skepsun/kototoro/details/ui/pager/ChaptersPagesSheet.kt
git commit -m "fix(#178): enable chapter multi-select in reader ChaptersPagesSheet"
```
