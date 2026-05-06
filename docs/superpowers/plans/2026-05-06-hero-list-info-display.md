# 浏览页/主页 Hero 与列表项信息展示增强

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在浏览页顶部 Hero、浏览页纵向列表项、主页顶部 Hero 中增加作品信息展示（评分、连载状态、章节数、类别标签），参考 Dantotsu 的展示格式。

**Architecture:** 利用 `Content` 模型已有的 `state`、`chapters`、`tags`、`rating` 字段和已有的 `ContentState.titleResId` 工具，新增一个扩展函数将各字段组合为 info 文本，然后在 Hero carousel、grid 卡片、轮播卡片三个 UI 组件中渲染该文本行。

**Tech Stack:** Kotlin, Jetpack Compose, Material3

---

### Task 1: 创建 Content info 文本构建函数

**Files:**
- Modify: `app/src/main/kotlin/org/skepsun/kototoro/list/ui/model/ContentListModel.kt`

- [ ] **Step 1: 在 `ContentListModel.kt` 末尾新增 info 文本构建函数**

```kotlin
import org.skepsun.kototoro.parsers.model.ContentState

fun ContentListModel.buildInfoText(context: Context): String? {
    val parts = mutableListOf<String>()

    // 评分
    if (!scoreText.isNullOrBlank()) {
        parts += scoreText
    }

    // 连载状态
    manga.state?.let { state ->
        val stateText = context.getString(state.titleResId)
        if (stateText.isNotBlank()) {
            parts += stateText
        }
    }

    // 章节数
    val chapterCount = manga.chapters?.size
    if (chapterCount != null && chapterCount > 0) {
        parts += context.resources.getQuantityString(
            R.plurals.chapters_count,
            chapterCount,
            chapterCount,
        )
    }

    // 类别标签（取前3个）
    if (manga.tags.isNotEmpty()) {
        val tagsText = manga.tags.take(3).joinToString(", ") { it.title }
        if (tagsText.isNotBlank()) {
            parts += tagsText
        }
    }

    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
```

需要确认 `R.plurals.chapters_count` 是否存在，如果不存在则使用简单格式 `"${count}话"`。

- [ ] **Step 2: 验证现有 plurals 资源，若不存在则新增**

先用简单方式：`"${count}话"` 作为后备。

修正后的函数（去掉 plurals 依赖）：

```kotlin
fun ContentListModel.buildInfoText(context: Context): String? {
    val parts = mutableListOf<String>()

    if (!scoreText.isNullOrBlank()) {
        parts += scoreText
    }

    manga.state?.let { state ->
        val stateText = context.getString(state.titleResId)
        if (stateText.isNotBlank()) {
            parts += stateText
        }
    }

    val chapterCount = manga.chapters?.size
    if (chapterCount != null && chapterCount > 0) {
        parts += "$chapterCount章"
    }

    if (manga.tags.isNotEmpty()) {
        val tagsText = manga.tags.take(3).joinToString(", ") { it.title }
        if (tagsText.isNotBlank()) {
            parts += tagsText
        }
    }

    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
```

- [ ] **Step 3: 编译验证**

```bash
cd /d2/chuxiong/code/Kototoro && JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:compileDebugKotlin --no-daemon -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890 2>&1 | tail -20
```

### Task 2: Hero 区域新增 info 行

**Files:**
- Modify: `app/src/main/kotlin/org/skepsun/kototoro/discover/ui/compose/DiscoverHeroCarousel.kt`

- [ ] **Step 1: 在标题和 secondaryTitle 之间插入 info 文本行**

在 `DiscoverHeroCarousel.kt` 中，找到标题 Text（~line 558-565）和 secondaryTitle Text（~line 566-574）之间，插入：

```kotlin
val infoText = remember(item.manga.state, item.manga.chapters?.size, item.manga.tags, item.scoreText, context) {
    item.buildInfoText(context)
}
infoText?.takeIf { it.isNotBlank() }?.let { info ->
    Text(
        text = info,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 2.dp),
    )
}
```

同时需要添加 import：
```kotlin
import org.skepsun.kototoro.list.ui.model.buildInfoText
```

- [ ] **Step 2: 编译验证**

```bash
cd /d2/chuxiong/code/Kototoro && JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:compileDebugKotlin --no-daemon -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890 2>&1 | tail -20
```

### Task 3: 网格卡片新增 info 行

**Files:**
- Modify: `app/src/main/kotlin/org/skepsun/kototoro/list/ui/compose/KototoroContentCard.kt`

- [ ] **Step 1: 在 `KototoroContentCardGrid` 中标题后增加 info 行**

在标题 Text（~line 368-378）之后，subtitle Text 之前（~line 379），插入：

```kotlin
val infoText = remember(item.manga.state, item.manga.chapters?.size, item.manga.tags, item.scoreText, context) {
    item.buildInfoText(context)
}
infoText?.takeIf { it.isNotBlank() }?.let { info ->
    Text(
        text = info,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .widthIn(max = posterStyle.itemWidth)
            .fillMaxWidth()
            .padding(top = 2.dp),
    )
}
```

添加 import：
```kotlin
import org.skepsun.kototoro.list.ui.model.buildInfoText
```

- [ ] **Step 2: 编译验证**

```bash
cd /d2/chuxiong/code/Kototoro && JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:compileDebugKotlin --no-daemon -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890 2>&1 | tail -20
```

### Task 4: 轮播卡片新增 info 行

**Files:**
- Modify: `app/src/main/kotlin/org/skepsun/kototoro/discover/ui/compose/DiscoverCarousel.kt`

- [ ] **Step 1: 在 `DiscoverPosterCard` 标题后增加 info 行**

在标题 Text（~line 224-230）之后，Column 结束之前，插入：

```kotlin
val infoText = remember(model.manga.state, model.manga.chapters?.size, model.manga.tags, model.scoreText, context) {
    model.buildInfoText(context)
}
infoText?.takeIf { it.isNotBlank() }?.let { info ->
    Text(
        text = info,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
```

添加 import：
```kotlin
import org.skepsun.kototoro.list.ui.model.buildInfoText
```

- [ ] **Step 2: 编译验证**

```bash
cd /d2/chuxiong/code/Kototoro && JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:compileDebugKotlin --no-daemon -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890 2>&1 | tail -20
```

### Task 5: 完整编译验证

- [ ] **Step 1: 运行完整编译**

```bash
cd /d2/chuxiong/code/Kototoro && JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:compileDebugKotlin --no-daemon -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890 2>&1 | tail -30
```

确认 "BUILD SUCCESSFUL"。

- [ ] **Step 2: 代码审查**

检查所有改动点：
1. `ContentListModel.kt` - 新增 `buildInfoText` 函数
2. `DiscoverHeroCarousel.kt` - hero 卡片新增 info 行
3. `KototoroContentCard.kt` - grid 卡片新增 info 行
4. `DiscoverCarousel.kt` - 轮播卡片新增 info 行

确认：字段缺失时不崩溃、不影响现有 subtitle/supportingText 显示、OOM 不会因 chapters 列表过大而产生。
