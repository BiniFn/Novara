# Issue #179 滚动滑块增强 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将章节列表的纯视觉滚动条改为可拖拽的 thumb，并增强阅读器底部 slider 的视觉效果。

**架构：** 扩展现有的 `VerticalScrollbar.kt` Compose modifier，新增 `draggable` 参数和 `labelProvider` 参数，用 `pointerInput` 实现拖拽逻辑。阅读器 slider 仅修改 XML 属性，不改逻辑。

**技术栈：** Jetpack Compose、`pointerInput`、`LazyListState.scrollToItem()`、Material3 Slider XML 属性

---

## 文件清单

| 文件 | 操作 | 职责 |
|------|------|------|
| `app/src/main/kotlin/org/skepsun/kototoro/core/ui/compose/VerticalScrollbar.kt` | 修改 | 新增可拖拽 thumb + 气泡 label |
| `app/src/main/kotlin/org/skepsun/kototoro/details/ui/pager/chapters/compose/ChaptersScreen.kt` | 修改 | 传入 labelProvider |
| `app/src/main/kotlin/org/skepsun/kototoro/details/ui/pager/pages/compose/PagesScreen.kt` | 修改 | 传入 labelProvider |
| `app/src/main/res/layout/layout_reader_actions.xml` | 修改 | 增大 slider thumb 和 track |

---

### 任务 1：扩展 VerticalScrollbar 支持可拖拽 thumb（LazyListState 版本）

**文件：**
- 修改：`app/src/main/kotlin/org/skepsun/kototoro/core/ui/compose/VerticalScrollbar.kt`

- [ ] **步骤 1：替换 LazyListState 版本的 verticalScrollbar 实现**

将文件中 `fun Modifier.verticalScrollbar(state: LazyListState, ...)` 函数（lines 18-52）替换为以下实现：

```kotlin
fun Modifier.verticalScrollbar(
    state: LazyListState,
    width: Dp = 8.dp,
    color: Color = Color.Gray.copy(alpha = 0.5f),
    draggable: Boolean = true,
    labelProvider: ((Int) -> String)? = null,
): Modifier = composed {
    val coroutineScope = rememberCoroutineScope()
    val isScrollInProgress = state.isScrollInProgress
    val totalItems = state.layoutInfo.totalItemsCount
    val visibleItems = state.layoutInfo.visibleItemsInfo.size
    val showScrollbar = totalItems > visibleItems

    var isDragging by remember { mutableStateOf(false) }
    var dragLabelIndex by remember { mutableIntStateOf(0) }

    val alpha by animateFloatAsState(
        targetValue = if ((isScrollInProgress || isDragging) && showScrollbar) 1f else 0f,
        animationSpec = tween(durationMillis = if (isScrollInProgress || isDragging) 150 else 1000),
        label = "scrollbar_alpha",
    )

    val thumbWidthPx = with(LocalDensity.current) { width.toPx() }
    val touchWidthPx = with(LocalDensity.current) { 24.dp.toPx() }

    this
        .then(
            if (draggable) {
                Modifier.pointerInput(totalItems, visibleItems) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val barHeightFraction = (visibleItems.toFloat() / totalItems).coerceIn(0.05f, 1f)
                            val barHeight = size.height * barHeightFraction
                            val firstVisible = state.firstVisibleItemIndex
                            val scrollFraction = firstVisible.toFloat() / (totalItems - visibleItems).coerceAtLeast(1)
                            val barTop = (size.height - barHeight) * scrollFraction
                            val touchX = offset.x
                            val touchY = offset.y
                            val inThumbX = touchX >= size.width - touchWidthPx
                            val inThumbY = touchY >= barTop - 24f && touchY <= barTop + barHeight + 24f
                            if (inThumbX && inThumbY) {
                                isDragging = true
                            }
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onDrag = { change, dragAmount ->
                            if (isDragging) {
                                change.consume()
                                val newY = change.position.y.coerceIn(0f, size.height.toFloat())
                                val fraction = newY / size.height
                                val targetIndex = (fraction * totalItems).toInt().coerceIn(0, totalItems - 1)
                                dragLabelIndex = targetIndex
                                coroutineScope.launch {
                                    state.scrollToItem(targetIndex)
                                }
                            }
                        },
                    )
                }
            } else Modifier,
        )
        .drawWithContent {
            drawContent()
            if (alpha > 0f && totalItems > 0 && visibleItems > 0) {
                val firstVisible = if (isDragging) dragLabelIndex else state.firstVisibleItemIndex
                val scrollFraction = firstVisible.toFloat() / (totalItems - visibleItems).coerceAtLeast(1)
                val barHeightFraction = (visibleItems.toFloat() / totalItems).coerceIn(0.05f, 1f)
                val barHeight = size.height * barHeightFraction
                val barTop = (size.height - barHeight) * scrollFraction

                // track
                drawRoundRect(
                    color = color.copy(alpha = color.alpha * alpha * 0.3f),
                    topLeft = Offset(size.width - thumbWidthPx, 0f),
                    size = Size(thumbWidthPx, size.height),
                    cornerRadius = CornerRadius(thumbWidthPx / 2f),
                )
                // thumb
                drawRoundRect(
                    color = color.copy(alpha = color.alpha * alpha),
                    topLeft = Offset(size.width - thumbWidthPx, barTop),
                    size = Size(thumbWidthPx, barHeight.coerceAtLeast(48.dp.toPx())),
                    cornerRadius = CornerRadius(thumbWidthPx / 2f),
                )
            }
        }
}
```

新增所需 import（在文件顶部）：
```kotlin
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
```

- [ ] **步骤 2：同样更新 LazyGridState 版本**

将 `fun Modifier.verticalScrollbar(state: LazyGridState, ...)` 函数（lines 54-88）替换为：

```kotlin
fun Modifier.verticalScrollbar(
    state: LazyGridState,
    width: Dp = 8.dp,
    color: Color = Color.Gray.copy(alpha = 0.5f),
    draggable: Boolean = false,
    labelProvider: ((Int) -> String)? = null,
): Modifier = composed {
    val isScrollInProgress = state.isScrollInProgress
    val totalItems = state.layoutInfo.totalItemsCount
    val visibleItems = state.layoutInfo.visibleItemsInfo.size
    val showScrollbar = totalItems > visibleItems

    val alpha by animateFloatAsState(
        targetValue = if (isScrollInProgress && showScrollbar) 1f else 0f,
        animationSpec = tween(durationMillis = if (isScrollInProgress) 150 else 1000),
        label = "scrollbar_alpha",
    )

    drawWithContent {
        drawContent()
        if (alpha > 0f && totalItems > 0 && visibleItems > 0) {
            val firstVisible = state.firstVisibleItemIndex
            val scrollFraction = firstVisible.toFloat() / (totalItems - visibleItems).coerceAtLeast(1)
            val barHeightFraction = (visibleItems.toFloat() / totalItems).coerceIn(0.05f, 1f)
            val barHeight = size.height * barHeightFraction
            val barTop = (size.height - barHeight) * scrollFraction
            val widthPx = width.toPx()

            drawRoundRect(
                color = color.copy(alpha = color.alpha * alpha * 0.3f),
                topLeft = Offset(size.width - widthPx, 0f),
                size = Size(widthPx, size.height),
                cornerRadius = CornerRadius(widthPx / 2f),
            )
            drawRoundRect(
                color = color.copy(alpha = color.alpha * alpha),
                topLeft = Offset(size.width - widthPx, barTop),
                size = Size(widthPx, barHeight.coerceAtLeast(48.dp.toPx())),
                cornerRadius = CornerRadius(widthPx / 2f),
            )
        }
    }
}
```

- [ ] **步骤 3：编译验证**

```bash
./gradlew :app:compileDebugKotlin --no-daemon 2>&1 | grep -E "error:|warning:" | head -30
```

预期：无编译错误（设置相关错误可忽略）。

- [ ] **步骤 4：Commit**

```bash
git add app/src/main/kotlin/org/skepsun/kototoro/core/ui/compose/VerticalScrollbar.kt
git commit -m "fix(#179): enhance verticalScrollbar with draggable thumb and track"
```

---

### 任务 2：更新 ChaptersScreen 和 PagesScreen 传入 labelProvider

**文件：**
- 修改：`app/src/main/kotlin/org/skepsun/kototoro/details/ui/pager/chapters/compose/ChaptersScreen.kt`
- 修改：`app/src/main/kotlin/org/skepsun/kototoro/details/ui/pager/pages/compose/PagesScreen.kt`

- [ ] **步骤 1：更新 ChaptersScreen.kt 的 LazyColumn scrollbar 调用**

找到 `ChaptersScreen.kt` 中 `verticalScrollbar(listState)` 的调用（line 176），替换为：

```kotlin
.verticalScrollbar(
    state = listState,
    labelProvider = { index -> "${index + 1}" },
)
```

- [ ] **步骤 2：更新 ChaptersScreen.kt 的 LazyVerticalGrid scrollbar 调用**

找到 `ChaptersScreen.kt` 中 `verticalScrollbar(gridState)` 的调用（line 133），替换为：

```kotlin
.verticalScrollbar(
    state = gridState,
    draggable = false,
)
```

- [ ] **步骤 3：更新 PagesScreen.kt 的 scrollbar 调用**

找到 `PagesScreen.kt` 中 `verticalScrollbar(listState)` 的调用（line 252），替换为：

```kotlin
.verticalScrollbar(
    state = listState,
    labelProvider = { index -> "${index + 1}" },
)
```

- [ ] **步骤 4：编译验证**

```bash
./gradlew :app:compileDebugKotlin --no-daemon 2>&1 | grep -E "error:" | head -20
```

预期：无编译错误。

- [ ] **步骤 5：Commit**

```bash
git add app/src/main/kotlin/org/skepsun/kototoro/details/ui/pager/chapters/compose/ChaptersScreen.kt
git add app/src/main/kotlin/org/skepsun/kototoro/details/ui/pager/pages/compose/PagesScreen.kt
git commit -m "fix(#179): pass labelProvider to verticalScrollbar in chapters and pages screens"
```

---

### 任务 3：增强阅读器底部 slider 视觉

**文件：**
- 修改：`app/src/main/res/layout/layout_reader_actions.xml`

- [ ] **步骤 1：修改 Slider 属性**

找到 `layout_reader_actions.xml` 中的 `<com.google.android.material.slider.Slider>` 元素（lines 25-35），添加以下属性：

```xml
<com.google.android.material.slider.Slider
    android:id="@+id/slider"
    android:layout_width="0dp"
    android:layout_height="wrap_content"
    android:layout_weight="1"
    android:stepSize="1.0"
    android:valueFrom="0"
    android:visibility="visible"
    app:labelBehavior="floating"
    app:thumbRadius="10dp"
    app:trackHeight="4dp"
    tools:value="6"
    tools:valueTo="20" />
```

- [ ] **步骤 2：编译验证**

```bash
./gradlew :app:compileDebugKotlin --no-daemon 2>&1 | grep -E "error:" | head -20
```

预期：无编译错误。

- [ ] **步骤 3：Commit**

```bash
git add app/src/main/res/layout/layout_reader_actions.xml
git commit -m "fix(#179): increase reader slider thumb radius and track height"
```
