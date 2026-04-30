# UI Bug 修复实现计划 A（P0+P1）

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 修复 5 个 P0/P1 级 bug：源设置导航错误、横屏多余背景、视频章节跳转、视频下载目录不显示、metadata source 搜索类型过滤缺失。

**架构：** 每个 bug 独立修复，互不依赖。问题 1 修改路由层；问题 2 修改 Compose UI 层；问题 5 修改 ViewModel 状态管理；问题 6 修改设置 UI 层；问题 9 修改 tracking 服务层和调用层。

**技术栈：** Kotlin、Jetpack Compose、Hilt、StateFlow、Android Intent

---

## 任务 1：修复源设置导航（问题 1）

**文件：**
- 修改：`app/src/main/kotlin/org/skepsun/kototoro/core/nav/AppRouter.kt:1123`

### 背景

`sourceSettingsIntent` 对 `ExternalContentSource` 跳转到系统应用详情页（`ACTION_APPLICATION_DETAILS_SETTINGS`），应改为跳转到 `UnifiedSourcesActivity` 并传入对应的 `kind`。

`ExternalContentSource` 没有 `extensionType` 字段，需要通过 `packageName` 前缀或其他方式判断 kind。查看 `ExternalContentSource.kt`：它只有 `packageName` 和 `authority` 字段，没有直接的 kind 信息。

需要通过 `UnifiedSourceCatalogRepository` 或 `UnifiedSourcesViewModel` 查找对应的 `UnifiedSourceItem`，从中获取 `kind`。但 `sourceSettingsIntent` 是同步函数，不能直接查询 ViewModel。

**解决方案：** 在 `AppRouter` 中注入 `UnifiedSourceCatalogRepository`，通过 `packageName` 查找 `UnifiedSourceItem.kind`，找不到时 fallback 到 `UnifiedSourcesActivity`（不传 kind）。

- [ ] **步骤 1：编写失败的测试**

```kotlin
// app/src/test/kotlin/org/skepsun/kototoro/core/nav/AppRouterSourceSettingsTest.kt
package org.skepsun.kototoro.core.nav

import android.content.Intent
import android.provider.Settings
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.skepsun.kototoro.core.parser.external.ExternalContentSource
import org.skepsun.kototoro.settings.sources.unified.UnifiedSourceCatalogRepository
import org.skepsun.kototoro.settings.sources.unified.UnifiedSourceItem
import org.skepsun.kototoro.settings.sources.unified.UnifiedSourceKind
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AppRouterSourceSettingsTest {

    @Test
    fun `sourceSettingsIntent for ExternalContentSource returns UnifiedSourcesActivity intent`() {
        val source = ExternalContentSource(
            packageName = "eu.kanade.tachiyomi.extension.en.mangadex",
            authority = "eu.kanade.tachiyomi.extension.en.mangadex.provider",
        )
        val context = mockk<android.content.Context>(relaxed = true)
        val router = AppRouter(mockk(relaxed = true), mockk(relaxed = true))

        val intent = router.sourceSettingsIntent(context, source)

        assertNotEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

```bash
./gradlew :app:testDebugUnitTest --tests "org.skepsun.kototoro.core.nav.AppRouterSourceSettingsTest" --no-daemon
```

预期：FAIL，因为当前实现仍返回 `ACTION_APPLICATION_DETAILS_SETTINGS`

- [ ] **步骤 3：读取 AppRouter.kt 确认当前实现**

读取 `app/src/main/kotlin/org/skepsun/kototoro/core/nav/AppRouter.kt` 第 1110-1140 行，确认 `sourceSettingsIntent` 函数签名和 `ExternalContentSource` 分支。

- [ ] **步骤 4：读取 UnifiedSourcesActivity.kt 确认 newIntent 签名**

读取 `app/src/main/kotlin/org/skepsun/kototoro/settings/sources/unified/UnifiedSourcesActivity.kt`，确认 `newIntent(context, initialRepositoryKind)` 的参数。

- [ ] **步骤 5：修改 sourceSettingsIntent**

在 `AppRouter.kt` 的 `sourceSettingsIntent` 函数中，将 `ExternalContentSource` 分支改为：

```kotlin
is ExternalContentSource -> {
    // 通过 packageName 前缀推断 kind，找不到时不传 kind（显示全部源列表）
    val kind = inferUnifiedSourceKind(source.packageName)
    UnifiedSourcesActivity.newIntent(context, initialRepositoryKind = kind)
}
```

在同一文件中添加私有辅助函数（如果 AppRouter 是 class，加为成员函数；如果是 object，加为顶层函数）：

```kotlin
private fun inferUnifiedSourceKind(packageName: String): UnifiedSourceKind? {
    return when {
        packageName.startsWith("eu.kanade.tachiyomi") -> UnifiedSourceKind.MIHON
        packageName.startsWith("eu.kanade.aniyomi") -> UnifiedSourceKind.ANIYOMI
        packageName.startsWith("ireader") -> UnifiedSourceKind.IREADER
        else -> null  // JAR 或未知，显示全部
    }
}
```

> **注意：** 如果 `AppRouter` 不是 class 而是 object 或顶层函数集合，调整函数定义位置。读取文件后确认。

- [ ] **步骤 6：运行测试验证通过**

```bash
./gradlew :app:testDebugUnitTest --tests "org.skepsun.kototoro.core.nav.AppRouterSourceSettingsTest" --no-daemon
```

预期：PASS

- [ ] **步骤 7：编译验证**

```bash
./gradlew :app:compileDebugKotlin --no-daemon
```

预期：BUILD SUCCESSFUL，无编译错误

- [ ] **步骤 8：Commit**

```bash
git add app/src/main/kotlin/org/skepsun/kototoro/core/nav/AppRouter.kt
git add app/src/test/kotlin/org/skepsun/kototoro/core/nav/AppRouterSourceSettingsTest.kt
git commit -m "fix(nav): route ExternalContentSource settings to UnifiedSourcesActivity"
```

---

## 任务 2：修复横屏详情页多余背景（问题 2）

**文件：**
- 修改：`app/src/main/kotlin/org/skepsun/kototoro/details/ui/compose/DetailsScreen.kt:769`

### 背景

横屏布局中右侧面板用 `Surface(tonalElevation = 4.dp, shape = RoundedCornerShape(28.dp))` 包裹，产生浅色矩形背景。将 `tonalElevation` 改为 `0.dp` 即可消除多余背景，同时保留圆角形状。

- [ ] **步骤 1：读取 DetailsScreen.kt 第 760-790 行**

确认 `Surface` 的完整参数，特别是 `color`、`tonalElevation`、`shape` 的当前值。

- [ ] **步骤 2：修改 Surface 的 tonalElevation**

将第 769 行附近的：

```kotlin
Surface(
    modifier = Modifier
        .fillMaxHeight()
        .widthIn(min = 360.dp, max = 440.dp),
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
    shape = RoundedCornerShape(28.dp),
    tonalElevation = 4.dp,
) {
```

改为：

```kotlin
Surface(
    modifier = Modifier
        .fillMaxHeight()
        .widthIn(min = 360.dp, max = 440.dp),
    color = Color.Transparent,
    shape = RoundedCornerShape(28.dp),
    tonalElevation = 0.dp,
) {
```

> 如果 `Color` 未导入，添加 `import androidx.compose.ui.graphics.Color`。

- [ ] **步骤 3：编译验证**

```bash
./gradlew :app:compileDebugKotlin --no-daemon
```

预期：BUILD SUCCESSFUL

- [ ] **步骤 4：Commit**

```bash
git add app/src/main/kotlin/org/skepsun/kototoro/details/ui/compose/DetailsScreen.kt
git commit -m "fix(details): remove extra background in landscape right panel"
```

---

## 任务 3：修复视频章节跳转（问题 5）

**文件：**
- 修改：`app/src/main/kotlin/org/skepsun/kototoro/video/ui/VideoChaptersViewModel.kt`
- 修改：`app/src/main/kotlin/org/skepsun/kototoro/video/ui/VideoPlayerActivity.kt:3111`

### 背景

`updateChapterNavButtons`（`:3111`）从 `intent.getParcelableExtraCompat<ParcelableContent>(KEY_MANGA)?.manga?.chapters` 读取章节列表，这是 Activity 启动时的快照，可能为空。应改为从 `VideoChaptersViewModel` 读取当前章节列表。

- [ ] **步骤 1：读取 VideoChaptersViewModel.kt 完整内容**

确认 ViewModel 的继承关系、现有 StateFlow、以及如何获取章节列表。

- [ ] **步骤 2：读取 VideoPlayerActivity.kt 第 3000-3130 行**

确认 `onChapterSelected` 和 `updateChapterNavButtons` 的完整实现，以及 ViewModel 的引用方式。

- [ ] **步骤 3：在 VideoChaptersViewModel 中暴露章节列表**

读取文件后，在 `VideoChaptersViewModel.kt` 中添加 `chapters` StateFlow。

如果 `VideoChaptersViewModel` 继承自 `ChaptersPagesViewModel`，检查父类是否已有章节列表。如果父类有 `chapters: StateFlow<List<ContentChapter>>`，直接使用；否则添加：

```kotlin
// 在 VideoChaptersViewModel 中
val chapters: StateFlow<List<ContentChapter>> = _chapters.asStateFlow()
```

具体实现取决于读取文件后的结果。

- [ ] **步骤 4：修改 updateChapterNavButtons 使用 ViewModel 章节列表**

将 `VideoPlayerActivity.kt:3111` 的 `updateChapterNavButtons` 改为：

```kotlin
private fun updateChapterNavButtons() {
    val ctl = findViewById<PlayerControlView>(R.id.controller) ?: return
    val prev = ctl.findViewById<View>(R.id.button_prev_chapter)
    val next = ctl.findViewById<View>(R.id.button_next_chapter)

    // 从 ViewModel 读取，而不是 intent 快照
    val chapters = videoChaptersViewModel.chapters.value
    if (chapters.isEmpty()) {
        prev?.isEnabled = false
        prev?.alpha = 0.4f
        next?.isEnabled = false
        next?.alpha = 0.4f
        return
    }

    val currentId = readerState?.chapterId ?: chapters.first().id
    val currentIndex = chapters.indexOfFirst { it.id == currentId }.takeIf { it >= 0 } ?: 0
    val hasPrev = currentIndex > 0
    val hasNext = currentIndex < chapters.lastIndex

    prev?.isEnabled = hasPrev
    prev?.alpha = if (hasPrev) 1f else 0.4f
    next?.isEnabled = hasNext
    next?.alpha = if (hasNext) 1f else 0.4f
}
```

> **注意：** `videoChaptersViewModel` 的变量名需要与 Activity 中实际的 ViewModel 引用名一致，读取文件后确认。

- [ ] **步骤 5：编译验证**

```bash
./gradlew :app:compileDebugKotlin --no-daemon
```

预期：BUILD SUCCESSFUL

- [ ] **步骤 6：Commit**

```bash
git add app/src/main/kotlin/org/skepsun/kototoro/video/ui/VideoChaptersViewModel.kt
git add app/src/main/kotlin/org/skepsun/kototoro/video/ui/VideoPlayerActivity.kt
git commit -m "fix(video): use ViewModel chapter list in updateChapterNavButtons"
```

---

## 任务 4：修复视频下载目录不显示（问题 6）

**文件：**
- 修改：`app/src/main/kotlin/org/skepsun/kototoro/settings/DownloadsSettingsFragment.kt`

### 背景

`videoStorageSummary` 通过 `loadStorageSummary(context, storageManager.getDefaultVideoWriteableDir(), storageManager)` 计算。当 `getDefaultVideoWriteableDir()` 返回 null 时，`loadStorageSummary` 返回 `context.getString(R.string.not_available)`（"不可用"），但 UI 可能不渲染空 summary 的 preference 项。

需要确认：
1. `loadStorageSummary` 在 dir 为 null 时的返回值
2. `DownloadsSettingsScreen` 是否在 summary 为空时隐藏了视频目录项

- [ ] **步骤 1：读取 DownloadsSettingsFragment.kt 第 190-220 行**

确认 `videoStorageSummary` 的计算逻辑和 `loadStorageSummary` 的调用。

- [ ] **步骤 2：读取 DownloadsSettingsScreen.kt 完整内容**

找到视频存储目录对应的 `SettingsActionPreference` 或类似组件，确认是否有条件渲染逻辑（`if (summary.isNotEmpty())`）。

- [ ] **步骤 3：修复 summary 为空时的显示**

如果 `DownloadsSettingsScreen.kt` 中有类似：

```kotlin
if (uiState.videoStorageSummary.isNotEmpty()) {
    SettingsActionPreference(...)
}
```

改为始终显示，并在 summary 为空时显示占位文本：

```kotlin
SettingsActionPreference(
    title = stringResource(R.string.pref_video_storage_location),
    summary = uiState.videoStorageSummary.ifEmpty {
        stringResource(R.string.not_set)
    },
    onClick = { /* 打开目录选择器 */ },
)
```

如果 `loadStorageSummary` 在 dir 为 null 时返回空字符串（而不是 `R.string.not_available`），修改 `DownloadsSettingsFragment.kt` 中的 `loadStorageSummary`：

```kotlin
private suspend fun loadStorageSummary(
    context: Context,
    storage: File?,
    storageManager: LocalStorageManager,
): String {
    return if (storage != null) {
        storageManager.getDirectoryDisplayName(storage, isFullPath = true)
    } else {
        context.getString(R.string.not_set)  // 改为"未设置"而不是空字符串
    }
}
```

> **注意：** `R.string.not_set` 可能需要添加到 `strings.xml`，检查是否已存在。

- [ ] **步骤 4：检查 strings.xml 是否有 not_set 字符串**

```bash
grep -r "not_set\|未设置\|not_available" app/src/main/res/values/strings.xml
```

如果没有 `not_set`，在 `strings.xml` 中添加：

```xml
<string name="not_set">未设置</string>
```

- [ ] **步骤 5：编译验证**

```bash
./gradlew :app:compileDebugKotlin --no-daemon
```

预期：BUILD SUCCESSFUL

- [ ] **步骤 6：Commit**

```bash
git add app/src/main/kotlin/org/skepsun/kototoro/settings/DownloadsSettingsFragment.kt
git add app/src/main/kotlin/org/skepsun/kototoro/settings/compose/DownloadsSettingsScreen.kt
git add app/src/main/res/values/strings.xml
git commit -m "fix(settings): show video storage directory even when path is null"
```

---

## 任务 5：修复 metadata source 搜索类型过滤（问题 9）

**文件：**
- 修改：`app/src/main/kotlin/org/skepsun/kototoro/tracking/discovery/data/DefaultTrackingSiteDiscoveryService.kt`
- 修改：调用 `TrackingLocalSearchSheet` 的 ViewModel（需读取文件确认路径）

### 背景

`TrackingSiteCatalog.contentType` 字段已定义但 `search()` 未使用。需要根据 `contentType` 决定只搜 anime 端点还是 manga 端点。

- [ ] **步骤 1：读取 DefaultTrackingSiteDiscoveryService.kt 完整 search() 方法**

确认各平台（AniList、MAL、Kitsu、Shikimori、MangaUpdates、Bangumi）的搜索逻辑和端点调用方式。

- [ ] **步骤 2：编写失败的测试**

```kotlin
// app/src/test/kotlin/org/skepsun/kototoro/tracking/discovery/DefaultTrackingSiteDiscoveryServiceTest.kt
package org.skepsun.kototoro.tracking.discovery

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteCatalog
import org.skepsun.kototoro.tracker.ScrobblerService

class DefaultTrackingSiteDiscoveryServiceTest {

    @Test
    fun `search with VIDEO contentType only calls anime endpoint for AniList`() = runTest {
        val service = mockk<DefaultTrackingSiteDiscoveryService>(relaxed = true)
        val catalog = TrackingSiteCatalog(
            service = ScrobblerService.ANILIST,
            query = "attack on titan",
            contentType = ContentType.VIDEO,
        )
        // 验证只调用 anime 端点，不调用 manga 端点
        // 具体断言取决于读取文件后的实现细节
    }
}
```

- [ ] **步骤 3：修改 search() 方法添加 contentType 过滤**

读取文件后，在 `search()` 方法中为每个平台添加 contentType 判断。以 Kitsu 为例（当前已合并搜索）：

```kotlin
if (catalog.service == ScrobblerService.KITSU) {
    val offset = catalog.page * 20
    return when {
        catalog.contentType == ContentType.VIDEO -> {
            runCatching { kitsuRepository.findAnime(query, offset) }.getOrElse { emptyList() }
                .map { it.toTrackingListItem(ScrobblerService.KITSU) }
        }
        catalog.contentType != null -> {
            // MANGA/NOVEL/COMICS 等
            runCatching { kitsuRepository.findContent(query, offset) }.getOrElse { emptyList() }
                .map { it.toTrackingListItem(ScrobblerService.KITSU) }
        }
        else -> {
            // contentType 为 null，保持现有行为（两者都搜）
            val anime = runCatching { kitsuRepository.findAnime(query, offset) }.getOrElse { emptyList() }
            val manga = runCatching { kitsuRepository.findContent(query, offset) }.getOrElse { emptyList() }
            (anime + manga).distinctBy { it.id }.map { it.toTrackingListItem(ScrobblerService.KITSU) }
        }
    }
}
```

对 AniList，添加辅助函数判断搜索类型：

```kotlin
private fun ContentType?.toAniListMediaType(): String? = when (this) {
    ContentType.VIDEO -> "ANIME"
    ContentType.MANGA, ContentType.MANHWA, ContentType.MANHUA, ContentType.COMICS -> "MANGA"
    ContentType.NOVEL -> "MANGA"  // 小说在 AniList 归类为 MANGA
    null -> null  // 两者都搜
    else -> null
}
```

> **注意：** 具体实现取决于读取文件后各平台的 API 调用方式。

- [ ] **步骤 4：找到 TrackingLocalSearchSheet 的 ViewModel 并传入 contentType**

搜索调用 `TrackingLocalSearchSheet` 的地方：

```bash
grep -r "TrackingLocalSearchSheet\|TrackingSiteCatalog" app/src/main/kotlin --include="*.kt" -l
```

找到对应 ViewModel，在创建 `TrackingSiteCatalog` 时传入当前内容的 `contentType`：

```kotlin
// 在 DetailsViewModel 或 TrackingViewModel 中
val catalog = TrackingSiteCatalog(
    service = service,
    query = manga.title,
    contentType = manga.contentType,  // 传入当前内容类型
)
```

- [ ] **步骤 5：编译验证**

```bash
./gradlew :app:compileDebugKotlin --no-daemon
```

预期：BUILD SUCCESSFUL

- [ ] **步骤 6：运行单元测试**

```bash
./gradlew :app:testDebugUnitTest --no-daemon
```

预期：所有测试通过

- [ ] **步骤 7：Commit**

```bash
git add app/src/main/kotlin/org/skepsun/kototoro/tracking/discovery/data/DefaultTrackingSiteDiscoveryService.kt
git add app/src/test/kotlin/org/skepsun/kototoro/tracking/discovery/DefaultTrackingSiteDiscoveryServiceTest.kt
# 加上调用层文件
git commit -m "fix(tracking): filter search results by content type in TrackingSiteDiscoveryService"
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
