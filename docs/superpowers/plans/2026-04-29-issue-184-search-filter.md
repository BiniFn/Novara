# Issue #184 搜索过滤交互重设计 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将搜索框下方的展示用 `AssistChip` 改为可交互的 `FilterChip`，修正标签文案，将高级搜索合并到搜索类型 chip，使下方 chip 与右上角 sheet 状态同步。

**架构：** `SearchSummaryRow` 目前接收状态但 chip 的 `onClick = {}` 为空。需要新增回调参数（`onSearchKindCycle`、`onSourceTypesChange`、`onContentKindsChange`），在调用方（`SearchResultsTopBar`）从 `SearchViewModel` 获取 setter 并传入。高级搜索 chip 合并到搜索类型 chip 的 ADVANCED 选项，选中 ADVANCED 时自动展开高级搜索输入框。

**技术栈：** Jetpack Compose、`FilterChip`、`DropdownMenu`、`SearchViewModel`、`SearchKind`、`SourceType`、`SearchContentKind`

---

## 文件清单

| 文件 | 操作 | 职责 |
|------|------|------|
| `app/src/main/kotlin/org/skepsun/kototoro/search/ui/compose/SearchResultsScreen.kt` | 修改 | SearchSummaryRow 改为可交互 FilterChip，合并高级搜索入口 |

---

### 任务 1：更新 SearchSummaryRow 为可交互 FilterChip

**文件：**
- 修改：`app/src/main/kotlin/org/skepsun/kototoro/search/ui/compose/SearchResultsScreen.kt`

- [ ] **步骤 1：确认 SearchSummaryRow 的完整签名和调用方**

```bash
grep -n "fun SearchSummaryRow\|SearchSummaryRow(" \
  app/src/main/kotlin/org/skepsun/kototoro/search/ui/compose/SearchResultsScreen.kt
```

记录：
- `SearchSummaryRow` 定义行号（约 line 521）
- `SearchSummaryRow` 调用行号（约 line 465）

- [ ] **步骤 2：更新 SearchSummaryRow 函数签名，新增回调参数**

找到 `SearchSummaryRow` 的函数定义（约 line 521），将其签名从：

```kotlin
@Composable
private fun SearchSummaryRow(
    searchKind: SearchKind,
    selectedSourceTypes: Set<SourceType>,
    selectedContentKinds: Set<SearchContentKind>,
    pinnedOnly: Boolean,
    hideEmpty: Boolean,
    isAdvancedExpanded: Boolean,
    onAdvancedExpandedChange: (Boolean) -> Unit,
)
```

改为：

```kotlin
@Composable
private fun SearchSummaryRow(
    searchKind: SearchKind,
    selectedSourceTypes: Set<SourceType>,
    selectedContentKinds: Set<SearchContentKind>,
    pinnedOnly: Boolean,
    hideEmpty: Boolean,
    isAdvancedExpanded: Boolean,
    onAdvancedExpandedChange: (Boolean) -> Unit,
    onSearchKindCycle: (() -> Unit)? = null,
    onSourceTypesChange: ((Set<SourceType>) -> Unit)? = null,
    onContentKindsChange: ((Set<SearchContentKind>) -> Unit)? = null,
)
```

- [ ] **步骤 3：替换 SearchSummaryRow 函数体内的 chip 实现**

找到 `SearchSummaryRow` 函数体（约 lines 530-580），将 `FlowRow` 内的 chip 内容替换为：

```kotlin
FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
) {
    // 搜索类型 chip（循环切换，ADVANCED 时展开高级搜索）
    val searchKindLabel = when (searchKind) {
        SearchKind.SIMPLE -> stringResource(R.string.search_type_default)
        SearchKind.TITLE -> stringResource(R.string.search_type_title)
        SearchKind.AUTHOR -> stringResource(R.string.search_type_author)
        SearchKind.TAG -> stringResource(R.string.search_type_tag)
        SearchKind.ADVANCED -> stringResource(R.string.search_type_advanced)
    }
    FilterChip(
        selected = searchKind != SearchKind.SIMPLE,
        onClick = {
            if (onSearchKindCycle != null) {
                onSearchKindCycle()
            } else {
                // 选中 ADVANCED 时展开高级搜索
                if (searchKind == SearchKind.ADVANCED) {
                    onAdvancedExpandedChange(!isAdvancedExpanded)
                }
            }
        },
        label = { Text(searchKindLabel) },
    )

    // 来源类型 chip（多选 DropdownMenu）
    var showSourceMenu by remember { mutableStateOf(false) }
    val allSourceTypes = remember { SourceType.entries.toList() }
    val sourceLabel = if (selectedSourceTypes.size == allSourceTypes.size || selectedSourceTypes.isEmpty()) {
        stringResource(R.string.source_type_all)
    } else {
        stringResource(R.string.source_type_count, selectedSourceTypes.size)
    }
    Box {
        FilterChip(
            selected = selectedSourceTypes.size < allSourceTypes.size && selectedSourceTypes.isNotEmpty(),
            onClick = { if (onSourceTypesChange != null) showSourceMenu = true },
            label = { Text(sourceLabel) },
        )
        if (onSourceTypesChange != null) {
            DropdownMenu(
                expanded = showSourceMenu,
                onDismissRequest = { showSourceMenu = false },
            ) {
                allSourceTypes.forEach { type ->
                    val isSelected = type in selectedSourceTypes
                    DropdownMenuItem(
                        text = { Text(type.name) },
                        onClick = {
                            val updated = selectedSourceTypes.toMutableSet().apply {
                                if (isSelected) remove(type) else add(type)
                            }
                            onSourceTypesChange(updated)
                        },
                        leadingIcon = {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    // 内容类型 chip（多选 DropdownMenu）
    var showContentMenu by remember { mutableStateOf(false) }
    val allContentKinds = remember { SearchContentKind.entries.toList() }
    val contentLabel = if (selectedContentKinds.size == allContentKinds.size || selectedContentKinds.isEmpty()) {
        stringResource(R.string.content_type_all)
    } else {
        selectedContentKinds.joinToString("/") { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
    }
    Box {
        FilterChip(
            selected = selectedContentKinds.size < allContentKinds.size && selectedContentKinds.isNotEmpty(),
            onClick = { if (onContentKindsChange != null) showContentMenu = true },
            label = { Text(contentLabel) },
        )
        if (onContentKindsChange != null) {
            DropdownMenu(
                expanded = showContentMenu,
                onDismissRequest = { showContentMenu = false },
            ) {
                allContentKinds.forEach { kind ->
                    val isSelected = kind in selectedContentKinds
                    val kindLabel = when (kind) {
                        SearchContentKind.MANGA -> stringResource(R.string.content_type_manga)
                        SearchContentKind.NOVEL -> stringResource(R.string.content_type_novel)
                        SearchContentKind.VIDEO -> stringResource(R.string.content_type_video)
                    }
                    DropdownMenuItem(
                        text = { Text(kindLabel) },
                        onClick = {
                            val updated = selectedContentKinds.toMutableSet().apply {
                                if (isSelected) remove(kind) else add(kind)
                            }
                            onContentKindsChange(updated)
                        },
                        leadingIcon = {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    // pinnedOnly chip（保持原有逻辑，仅展示）
    if (pinnedOnly) {
        AssistChip(
            onClick = {},
            label = { Text(stringResource(R.string.pinned_sources_only)) },
        )
    }

    // hideEmpty chip（保持原有逻辑，仅展示）
    if (hideEmpty) {
        AssistChip(
            onClick = {},
            label = { Text(stringResource(R.string.hide_empty_sources)) },
        )
    }
}
```

**注意：** 移除原有的独立"高级搜索 chip"（`onAdvancedExpandedChange` 相关的 AssistChip），其功能已合并到搜索类型 chip 的 ADVANCED 选项。

- [ ] **步骤 4：更新 SearchSummaryRow 的调用方，传入回调**

找到 `SearchSummaryRow` 的调用（约 line 465），添加新参数：

```kotlin
SearchSummaryRow(
    searchKind = searchKind,
    selectedSourceTypes = selectedSourceTypes,
    selectedContentKinds = selectedContentKinds,
    pinnedOnly = pinnedOnly,
    hideEmpty = hideEmpty,
    isAdvancedExpanded = isAdvancedExpanded,
    onAdvancedExpandedChange = onAdvancedExpandedChange,
    onSearchKindCycle = onSearchKindCycle,
    onSourceTypesChange = onSourceTypesChange,
    onContentKindsChange = onContentKindsChange,
)
```

然后找到 `SearchResultsTopBar`（或包含 `SearchSummaryRow` 调用的父 composable）的签名，添加对应参数：

```kotlin
onSearchKindCycle: () -> Unit,
onSourceTypesChange: (Set<SourceType>) -> Unit,
onContentKindsChange: (Set<SearchContentKind>) -> Unit,
```

- [ ] **步骤 5：在 SearchResultsScreen 顶层 composable 中从 ViewModel 获取 setter 并传入**

找到 `SearchResultsScreen` 顶层 composable（接收 `viewModel` 参数的地方），添加：

```kotlin
onSearchKindCycle = {
    val next = when (viewModel.searchKind) {
        SearchKind.SIMPLE -> SearchKind.TITLE
        SearchKind.TITLE -> SearchKind.AUTHOR
        SearchKind.AUTHOR -> SearchKind.TAG
        SearchKind.TAG -> SearchKind.ADVANCED
        SearchKind.ADVANCED -> SearchKind.SIMPLE
    }
    viewModel.setKind(next)
    if (next == SearchKind.ADVANCED) {
        isAdvancedExpanded = true
    }
},
onSourceTypesChange = { types -> viewModel.setSourceTypes(types) },
onContentKindsChange = { kinds -> viewModel.setContentKinds(kinds) },
```

**注意：** 先用 grep 确认 `SearchViewModel` 是否有 `setKind` 方法：
```bash
grep -n "fun setKind\|fun setSearchKind\|kind\s*=" \
  app/src/main/kotlin/org/skepsun/kototoro/search/ui/multi/SearchViewModel.kt | head -10
```
如果没有 `setKind`，需要在 `SearchViewModel` 中添加：
```kotlin
fun setKind(kind: SearchKind) {
    this.kind = kind
    retry()
}
```

- [ ] **步骤 6：添加必要 import**

确认以下 import 已存在，如不存在则添加：
```kotlin
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.layout.onGloballyPositioned
```

- [ ] **步骤 7：添加缺失的字符串资源**

检查以下字符串是否存在：
```bash
grep -n "search_type_default\|search_type_title\|search_type_author\|search_type_tag\|search_type_advanced\|source_type_all\|source_type_count\|content_type_all\|content_type_manga\|content_type_novel\|content_type_video" \
  app/src/main/res/values/strings.xml | head -20
```

对于不存在的字符串，在 `app/src/main/res/values/strings.xml` 中添加（找到合适位置插入）：
```xml
<string name="search_type_default">全部类型</string>
<string name="search_type_title">标题</string>
<string name="search_type_author">作者</string>
<string name="search_type_tag">标签</string>
<string name="search_type_advanced">高级搜索</string>
<string name="source_type_all">全部来源</string>
<string name="source_type_count">来源 %d</string>
<string name="content_type_all">全部内容</string>
<string name="content_type_manga">漫画</string>
<string name="content_type_novel">小说</string>
<string name="content_type_video">视频</string>
```

- [ ] **步骤 8：编译验证**

```bash
./gradlew :app:compileDebugKotlin --no-daemon 2>&1 | grep -E "error:" | head -20
```

预期：无编译错误。

- [ ] **步骤 9：Commit**

```bash
git add app/src/main/kotlin/org/skepsun/kototoro/search/ui/compose/SearchResultsScreen.kt
git add app/src/main/kotlin/org/skepsun/kototoro/search/ui/multi/SearchViewModel.kt
git add app/src/main/res/values/strings.xml
git commit -m "fix(#184): make search filter chips interactive, merge advanced search into kind chip"
```
