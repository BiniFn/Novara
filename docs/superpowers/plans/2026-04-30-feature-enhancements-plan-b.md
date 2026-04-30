# UI 功能增强实现计划 B（P1+P2）

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 实现 2 个功能增强：搜索结果卡片增强（reading source + metadata source）、本地作品页过滤胶囊栏。

**架构：** 问题 7 修改 `AlternativesSheetContent.kt`（确认触发路径）和 `TrackingLocalSearchSheet.kt`（新增章节数、迁移按钮、长按回调）；问题 8 在 `AppNavGraph.kt` 的 `LocalRoute` composable 中注册 `SearchBarFilterViewController` 回调。

**技术栈：** Kotlin、Jetpack Compose、combinedClickable、StateFlow、Hilt

---

## 任务 1：确认 reading source 触发路径（问题 7A）

**文件：**
- 读取：`app/src/main/kotlin/org/skepsun/kototoro/core/nav/AppRouter.kt`（`openAlternatives` 函数）
- 可能修改：`AppRouter.kt`

### 背景

`AlternativesSheetContent` 中 `ChaptersDiffText` 和 `FilledTonalButton` 已实现，但用户反馈"完全没有显示"。根因可能是 `AppRouter.openAlternatives` 仍然跳转到旧的 `AlternativesActivity`（View 系统），而不是 show `AlternativesSheet`（Compose BottomSheet）。

- [ ] **步骤 1：读取 AppRouter.kt 中的 openAlternatives 函数**

搜索 `openAlternatives` 在 `AppRouter.kt` 中的实现：

```bash
grep -n "openAlternatives\|AlternativesActivity\|AlternativesSheet" app/src/main/kotlin/org/skepsun/kototoro/core/nav/AppRouter.kt
```

- [ ] **步骤 2：如果 openAlternatives 仍跳转到 AlternativesActivity，改为 show AlternativesSheet**

如果当前实现是：

```kotlin
fun openAlternatives(manga: Content) {
    startActivity(AlternativesActivity.newIntent(context, manga))
}
```

改为（在 Fragment/Activity 上下文中 show BottomSheet）：

```kotlin
fun openAlternatives(manga: Content) {
    val sheet = AlternativesSheet.newInstance(manga)
    sheet.show(fragmentManager, AlternativesSheet.TAG)
}
```

> **注意：** `AppRouter` 的实现方式决定了如何 show sheet。如果 `AppRouter` 持有 `FragmentManager` 引用，直接调用；否则通过 Activity 的 `supportFragmentManager`。读取文件后确认。

- [ ] **步骤 3：确认 AlternativesSheet 有 newInstance 工厂方法**

读取 `AlternativesSheet.kt`，确认是否有 `newInstance(manga: Content)` 方法。如果没有，添加：

```kotlin
companion object {
    const val TAG = "AlternativesSheet"

    fun newInstance(manga: Content): AlternativesSheet {
        return AlternativesSheet().apply {
            arguments = Bundle().apply {
                putParcelable(AppRouter.KEY_MANGA, ParcelableContent(manga))
            }
        }
    }
}
```

- [ ] **步骤 4：编译验证**

```bash
./gradlew :app:compileDebugKotlin --no-daemon
```

- [ ] **步骤 5：Commit（如有改动）**

```bash
git add app/src/main/kotlin/org/skepsun/kototoro/core/nav/AppRouter.kt
git add app/src/main/kotlin/org/skepsun/kototoro/alternatives/ui/AlternativesSheet.kt
git commit -m "fix(alternatives): route openAlternatives to AlternativesSheet instead of AlternativesActivity"
```

---

## 任务 2：增强 metadata source 搜索卡片（问题 7B）

**文件：**
- 修改：`app/src/main/kotlin/org/skepsun/kototoro/discover/ui/details/TrackingLocalSearchSheet.kt`

### 背景

`TrackingCandidateCard` 只有封面图和标题，需要新增：章节数显示、迁移按钮、长按触发迁移确认框、短按打开详情页。

当前 `TrackingCandidateCard` 签名：
```kotlin
@Composable
private fun TrackingCandidateCard(
    content: Content,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
)
```

当前调用处（第 205-210 行）：
```kotlin
TrackingCandidateCard(
    content = c,
    onClick = {
        onCandidateClick(c)
        closeSheet()
    },
)
```

- [ ] **步骤 1：读取 TrackingLocalSearchSheet.kt 完整内容**

确认：
1. `TrackingLocalSearchSheet` 的完整参数列表
2. `onCandidateClick` 回调的当前类型
3. `closeSheet()` 的实现
4. 是否已有 `onMigrateClick` 参数

- [ ] **步骤 2：编写失败的测试**

```kotlin
// app/src/test/kotlin/org/skepsun/kototoro/discover/ui/details/TrackingLocalSearchSheetTest.kt
package org.skepsun.kototoro.discover.ui.details

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test
import org.skepsun.kototoro.parsers.model.Content

class TrackingLocalSearchSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `TrackingCandidateCard shows migrate button`() {
        val content = mockk<Content>(relaxed = true)
        val onMigrateClick = mockk<(Content) -> Unit>(relaxed = true)

        composeTestRule.setContent {
            // 渲染增强后的卡片
        }

        composeTestRule.onNodeWithText("迁移").assertExists()
    }
}
```

- [ ] **步骤 3：修改 TrackingLocalSearchSheet 添加 onMigrateClick 参数**

在 `TrackingLocalSearchSheet` 函数签名中添加 `onMigrateClick` 参数：

```kotlin
@Composable
fun TrackingLocalSearchSheet(
    // ... 现有参数
    onCandidateClick: (Content) -> Unit,
    onMigrateClick: (Content) -> Unit,  // 新增
    // ...
)
```

- [ ] **步骤 4：修改 TrackingCandidateCard 为增强版**

将 `TrackingCandidateCard` 改为 `TrackingCandidateCardEnhanced`（或直接修改现有函数），新增章节数、迁移按钮、长按回调：

```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackingCandidateCard(
    content: Content,
    onClick: () -> Unit,
    onMigrateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.width(108.dp),
    ) {
        Column(
            modifier = Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onMigrateClick,
                ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AsyncImage(
                model = content.coverUrl,
                contentDescription = content.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(13f / 18f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
            )
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = content.title,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val chaptersCount = content.chaptersCount()
                if (chaptersCount > 0) {
                    Text(
                        text = pluralStringResource(R.plurals.chapters, chaptersCount, chaptersCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(
                    onClick = onMigrateClick,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_replace),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.migrate),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
```

需要添加的 import：
```kotlin
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import org.skepsun.kototoro.core.util.ext.chaptersCount
```

- [ ] **步骤 5：更新卡片调用处传入 onMigrateClick**

在 `TrackingLocalSearchSheet` 内部调用 `TrackingCandidateCard` 的地方，传入 `onMigrateClick`：

```kotlin
TrackingCandidateCard(
    content = c,
    onClick = {
        onCandidateClick(c)
        closeSheet()
    },
    onMigrateClick = { onMigrateClick(c) },
)
```

- [ ] **步骤 6：找到 TrackingLocalSearchSheet 的调用处并传入 onMigrateClick**

搜索调用处：

```bash
grep -rn "TrackingLocalSearchSheet" app/src/main/kotlin --include="*.kt"
```

在调用处添加 `onMigrateClick` 回调，触发迁移确认框（与 `AlternativesSheet.confirmMigration` 一致的样式）：

```kotlin
TrackingLocalSearchSheet(
    // ... 现有参数
    onCandidateClick = { content -> router.openDetails(content) },
    onMigrateClick = { target ->
        showMigrationConfirmDialog(target)
    },
)
```

迁移确认框（在调用方的 Fragment/Composable 中）：

```kotlin
private fun showMigrationConfirmDialog(target: Content) {
    buildAlertDialog(requireContext(), isCentered = true) {
        setIcon(R.drawable.ic_replace)
        setTitle(R.string.manga_migration)
        setMessage(
            getString(
                R.string.migrate_confirmation,
                currentManga.title,
                currentManga.source.getTitle(context),
                target.title,
                target.source.getTitle(context),
            ),
        )
        setNegativeButton(android.R.string.cancel, null)
        setPositiveButton(R.string.migrate) { _, _ ->
            viewModel.migrate(target)
        }
    }.show()
}
```

> **注意：** 调用方的具体实现取决于读取文件后的上下文。如果调用方是 Compose，使用 `AlertDialog` composable 而不是 `buildAlertDialog`。

- [ ] **步骤 7：编译验证**

```bash
./gradlew :app:compileDebugKotlin --no-daemon
```

预期：BUILD SUCCESSFUL

- [ ] **步骤 8：Commit**

```bash
git add app/src/main/kotlin/org/skepsun/kototoro/discover/ui/details/TrackingLocalSearchSheet.kt
# 加上调用方文件
git commit -m "feat(tracking): enhance TrackingCandidateCard with chapter count and migrate button"
```

---

## 任务 3：本地作品页添加过滤胶囊栏（问题 8）

**文件：**
- 修改：`app/src/main/kotlin/org/skepsun/kototoro/main/ui/AppNavGraph.kt`（LocalRoute composable）

### 背景

`LocalListViewModel` 已实现 `QuickFilterListener`。`AppNavGraph.kt` 中 `LocalRoute` 的 `AppContentListRoute` 调用已有 `isContentTypeFilterVisible = false` 和 `isSourceTagFilterVisible = true` 参数，但没有注册 `SearchBarFilterViewController.Callback`。

收藏页（`FavoritesHostScreen.kt`）通过 `DisposableEffect` 注册 `SearchBarFilterViewController.Callback` 到 `mainActivity`，本地作品页需要同样的机制。

- [ ] **步骤 1：读取 AppNavGraph.kt 中 LocalRoute 的完整 composable**

确认：
1. `LocalRoute` composable 的完整内容
2. 是否已有 `mainActivity` 引用
3. `LocalListViewModel` 的 `filterCoordinator` 或 `selectedSourceTags` 的暴露方式

- [ ] **步骤 2：读取 LocalListViewModel.kt 确认过滤状态的 StateFlow**

确认 `LocalListViewModel` 暴露了哪些 StateFlow 用于过滤状态（`selectedGroupTab`、`selectedSourceTags` 等）。

- [ ] **步骤 3：在 LocalRoute composable 中注册 SearchBarFilterViewController.Callback**

参考 `FavoritesHostScreen.kt` 的实现，在 `LocalRoute` composable 中添加：

```kotlin
composable<LocalRoute> {
    val viewModel = hiltViewModel<LocalListViewModel>()
    val mainActivity = LocalActivity.current as? MainActivity

    // 收集过滤状态
    val selectedGroupTab by viewModel.selectedGroupTab.collectAsStateWithLifecycle()
    val selectedSourceTags by viewModel.selectedSourceTags.collectAsStateWithLifecycle()

    DisposableEffect(mainActivity, selectedGroupTab, selectedSourceTags) {
        val callback = object : SearchBarFilterViewController.Callback {
            override fun isContentTypeFilterVisible(): Boolean = true
            override fun isSourceTagFilterVisible(): Boolean = true
            override fun getSourceTagEntries(): List<SourceTag> = SourceTag.quickFilterEntries
            override fun getSelectedContentType(): BrowseGroupTab = selectedGroupTab
            override fun onContentTypeSelected(tab: BrowseGroupTab) {
                viewModel.setContentTypeFilter(
                    if (selectedGroupTab == tab) BrowseGroupTab.All else tab
                )
            }
            override fun getSelectedSourceTags(): Set<SourceTag> = selectedSourceTags
            override fun onSourceTagSelected(tag: SourceTag?) {
                when {
                    tag == null -> viewModel.clearFilter()
                    else -> viewModel.toggleFilterOption(ListFilterOption.Tag(tag))
                }
            }
        }
        mainActivity?.setActiveFilterCallback(callback)
        onDispose {
            mainActivity?.clearActiveFilterCallback(callback)
        }
    }

    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
        AppContentListRoute(
            viewModel = viewModel,
            contentPadding = contentPadding,
            appRouter = appRouter,
            showRemoveOption = true,
            isContentTypeFilterVisible = false,  // 由 SearchBarFilterViewController 控制
            isSourceTagFilterVisible = true,
            onRemoveSelection = { ids -> viewModel.delete(ids) },
        )
    }
}
```

> **注意：** `viewModel.selectedGroupTab` 和 `viewModel.selectedSourceTags` 的具体名称取决于读取 `LocalListViewModel.kt` 后的结果。如果 ViewModel 没有直接暴露这些 StateFlow，需要从 `filterCoordinator` 中获取。

- [ ] **步骤 4：确认 LocalListViewModel 暴露了必要的 StateFlow**

如果 `LocalListViewModel` 没有 `selectedGroupTab` 和 `selectedSourceTags` StateFlow，在 ViewModel 中添加：

```kotlin
// 在 LocalListViewModel.kt 中
val selectedGroupTab: StateFlow<BrowseGroupTab> = filterCoordinator.state
    .map { it.listFilter.contentType?.toBrowseGroupTab() ?: BrowseGroupTab.All }
    .stateIn(viewModelScope, SharingStarted.Eagerly, BrowseGroupTab.All)

val selectedSourceTags: StateFlow<Set<SourceTag>> = filterCoordinator.state
    .map { it.listFilter.tags }
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

fun setContentTypeFilter(tab: BrowseGroupTab) {
    val contentType = tab.toContentType()
    filterCoordinator.setContentType(contentType)
}
```

> **注意：** 具体实现取决于 `filterCoordinator` 的 API。读取文件后确认。

- [ ] **步骤 5：编译验证**

```bash
./gradlew :app:compileDebugKotlin --no-daemon
```

预期：BUILD SUCCESSFUL

- [ ] **步骤 6：Commit**

```bash
git add app/src/main/kotlin/org/skepsun/kototoro/main/ui/AppNavGraph.kt
git add app/src/main/kotlin/org/skepsun/kototoro/local/ui/LocalListViewModel.kt
git commit -m "feat(local): add filter capsule bar to local works page"
```

---

## 最终验证

- [ ] **全量编译**

```bash
./gradlew :app:compileDebugKotlin --no-daemon
```

- [ ] **全量单元测试**

```bash
./gradlew :app:testDebugUnitTest --no-daemon
```

- [ ] **构建 debug APK**

```bash
./gradlew :app:assembleDebug --no-daemon
```
