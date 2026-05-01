# Issue #182 迁移功能体验重设计 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将 `AlternativesActivity` 改为 `BaseAdaptiveSheet`（BottomSheet），顶部加拖拽手柄和操作提示，新增长按 item 直接触发迁移确认。

**架构：** 新建 `AlternativesSheet` 继承 `BaseAdaptiveSheet`，迁移 `AlternativesActivity` 的 RecyclerView 逻辑和 ViewModel 绑定。`AppRouter.openAlternatives()` 改为 show sheet。`AlternativeAD.kt` 新增长按监听器。`AlternativesActivity` 保留作为深链接兼容入口（内部改为 show sheet）。

**技术栈：** `BaseAdaptiveSheet`、`ActivityAlternativesBinding`（或新建 `SheetAlternativesBinding`）、`AlternativesViewModel`（Hilt）、`AlternativeAD`

---

## 文件清单

| 文件 | 操作 | 职责 |
|------|------|------|
| `app/src/main/kotlin/org/skepsun/kototoro/alternatives/ui/AlternativesSheet.kt` | 新建 | BottomSheet 实现，迁移 Activity 逻辑 |
| `app/src/main/res/layout/sheet_alternatives.xml` | 新建 | Sheet 布局（拖拽手柄 + 提示文案 + RecyclerView） |
| `app/src/main/kotlin/org/skepsun/kototoro/alternatives/ui/AlternativeAD.kt` | 修改 | 新增长按监听器 |
| `app/src/main/kotlin/org/skepsun/kototoro/core/nav/AppRouter.kt` | 修改 | `openAlternatives()` 改为 show sheet |
| `app/src/main/kotlin/org/skepsun/kototoro/alternatives/ui/AlternativesActivity.kt` | 修改 | 保留为兼容入口，内部 show sheet |

---

### 任务 1：新建 sheet_alternatives.xml 布局

**文件：**
- 新建：`app/src/main/res/layout/sheet_alternatives.xml`

- [ ] **步骤 1：创建布局文件**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <com.google.android.material.bottomsheet.BottomSheetDragHandleView
        android:layout_width="match_parent"
        android:layout_height="wrap_content" />

    <TextView
        android:id="@+id/textView_title"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:paddingHorizontal="16dp"
        android:paddingTop="4dp"
        android:text="@string/migration_select_source"
        android:textAppearance="?attr/textAppearanceTitleMedium" />

    <TextView
        android:id="@+id/textView_hint"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:paddingHorizontal="16dp"
        android:paddingTop="2dp"
        android:paddingBottom="8dp"
        android:text="@string/migration_hint"
        android:textAppearance="?attr/textAppearanceBodySmall"
        android:textColor="?attr/colorOnSurfaceVariant" />

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/recyclerView"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:clipToPadding="false"
        android:padding="@dimen/list_spacing_normal" />

</LinearLayout>
```

- [ ] **步骤 2：添加字符串资源**

检查字符串是否已存在：
```bash
grep -n "migration_select_source\|migration_hint" app/src/main/res/values/strings.xml | head -5
```

如不存在，在 `app/src/main/res/values/strings.xml` 中添加：
```xml
<string name="migration_select_source">选择替换来源</string>
<string name="migration_hint">短按预览详情 · 长按直接替换</string>
```

- [ ] **步骤 3：Commit**

```bash
git add app/src/main/res/layout/sheet_alternatives.xml
git add app/src/main/res/values/strings.xml
git commit -m "feat(#182): add sheet_alternatives layout with drag handle and hint"
```

---

### 任务 2：新建 AlternativesSheet

**文件：**
- 新建：`app/src/main/kotlin/org/skepsun/kototoro/alternatives/ui/AlternativesSheet.kt`

- [ ] **步骤 1：确认 BaseAdaptiveSheet 的泛型参数和 show 方法**

```bash
grep -n "class BaseAdaptiveSheet\|fun show\|abstract fun\|open fun" \
  app/src/main/kotlin/org/skepsun/kototoro/core/ui/sheet/BaseAdaptiveSheet.kt | head -20
```

确认 `BaseAdaptiveSheet<VB : ViewBinding>` 的泛型约束和 `show(fragmentManager, tag)` 方法。

- [ ] **步骤 2：确认 AlternativesViewModel 的 Hilt 注入方式**

```bash
grep -n "by viewModels\|@HiltViewModel\|@Inject\|SavedStateHandle\|KEY_MANGA\|ParcelableContent" \
  app/src/main/kotlin/org/skepsun/kototoro/alternatives/ui/AlternativesViewModel.kt | head -15
```

记录 ViewModel 如何获取 manga 参数（通过 `SavedStateHandle` 还是 `Intent`）。

- [ ] **步骤 3：创建 AlternativesSheet.kt**

```kotlin
package org.skepsun.kototoro.alternatives.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.viewModels
import coil3.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.core.exceptions.resolve.SnackbarErrorObserver
import org.skepsun.kototoro.core.model.getTitle
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.ui.BaseListAdapter
import org.skepsun.kototoro.core.ui.list.OnListItemClickListener
import org.skepsun.kototoro.core.ui.sheet.BaseAdaptiveSheet
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.core.util.ext.observeEvent
import org.skepsun.kototoro.databinding.SheetAlternativesBinding
import org.skepsun.kototoro.list.ui.adapter.ListItemType
import org.skepsun.kototoro.list.ui.adapter.ListStateHolderListener
import org.skepsun.kototoro.list.ui.adapter.TypedListSpacingDecoration
import org.skepsun.kototoro.list.ui.adapter.buttonFooterAD
import org.skepsun.kototoro.list.ui.adapter.emptyStateListAD
import org.skepsun.kototoro.list.ui.adapter.loadingFooterAD
import org.skepsun.kototoro.list.ui.adapter.loadingStateAD
import org.skepsun.kototoro.list.ui.model.ListModel
import org.skepsun.kototoro.parsers.model.Content
import javax.inject.Inject

@AndroidEntryPoint
class AlternativesSheet :
    BaseAdaptiveSheet<SheetAlternativesBinding>(),
    ListStateHolderListener,
    OnListItemClickListener<ContentAlternativeModel> {

    @Inject
    lateinit var coil: ImageLoader

    private val viewModel by viewModels<AlternativesViewModel>()

    override fun onViewBindingCreated(binding: SheetAlternativesBinding, savedInstanceState: Bundle?) {
        val listAdapter = BaseListAdapter<ListModel>()
            .addDelegate(
                ListItemType.MANGA_LIST_DETAILED,
                alternativeAD(
                    coil = coil,
                    clickListener = this,
                    longClickListener = { item ->
                        showMigrateConfirmDialog(item)
                    },
                ),
            )
            .addDelegate(ListItemType.STATE_EMPTY, emptyStateListAD(null))
            .addDelegate(ListItemType.FOOTER_LOADING, loadingFooterAD())
            .addDelegate(ListItemType.STATE_LOADING, loadingStateAD())
            .addDelegate(ListItemType.FOOTER_BUTTON, buttonFooterAD(this))

        with(binding.recyclerView) {
            setHasFixedSize(true)
            addItemDecoration(TypedListSpacingDecoration(requireContext(), addHorizontalPadding = false))
            adapter = listAdapter
        }

        viewModel.onError.observeEvent(viewLifecycleOwner, SnackbarErrorObserver(binding.recyclerView, this))
        viewModel.list.observe(viewLifecycleOwner, listAdapter)
        viewModel.onMigrated.observeEvent(viewLifecycleOwner) {
            dismissWithAnimation()
        }
    }

    override fun onItemClick(item: ContentAlternativeModel, view: View) {
        when (view.id) {
            org.skepsun.kototoro.R.id.chip_source -> {
                router.openSearch(item.manga.source)
            }
            org.skepsun.kototoro.R.id.button_migrate -> {
                showMigrateConfirmDialog(item)
            }
            else -> {
                router.openDetails(item.manga)
            }
        }
    }

    private fun showMigrateConfirmDialog(item: ContentAlternativeModel) {
        org.skepsun.kototoro.core.ui.dialog.buildAlertDialog(requireContext())
            .setTitle(org.skepsun.kototoro.R.string.migration_confirm_title)
            .setMessage(
                getString(
                    org.skepsun.kototoro.R.string.migration_confirm_message,
                    item.manga.getTitle(requireContext()),
                ),
            )
            .setPositiveButton(org.skepsun.kototoro.R.string.migrate) { _, _ ->
                viewModel.migrate(item.manga)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun inflateViewBinding(inflater: android.view.LayoutInflater) =
        SheetAlternativesBinding.inflate(inflater)

    companion object {
        const val TAG = "AlternativesSheet"

        fun newInstance(manga: Content): AlternativesSheet {
            return AlternativesSheet().apply {
                arguments = Bundle().apply {
                    putParcelable(AlternativesViewModel.KEY_MANGA, org.skepsun.kototoro.core.model.ParcelableContent(manga))
                }
            }
        }
    }
}
```

**注意：** 实际实现前需确认：
1. `BaseAdaptiveSheet` 的 `inflateViewBinding` 方法名（grep 确认）
2. `AlternativesViewModel.KEY_MANGA` 是否为 companion object 常量（grep 确认）
3. `viewModel.migrate()` 方法名（grep 确认 AlternativesViewModel 中的迁移方法名）

```bash
grep -n "fun migrate\|fun startMigration\|KEY_MANGA\|fun inflateViewBinding\|abstract.*inflate" \
  app/src/main/kotlin/org/skepsun/kototoro/alternatives/ui/AlternativesViewModel.kt \
  app/src/main/kotlin/org/skepsun/kototoro/core/ui/sheet/BaseAdaptiveSheet.kt | head -20
```

- [ ] **步骤 4：编译验证**

```bash
./gradlew :app:compileDebugKotlin --no-daemon 2>&1 | grep -E "error:" | head -20
```

预期：无编译错误（设置相关错误可忽略）。

- [ ] **步骤 5：Commit**

```bash
git add app/src/main/kotlin/org/skepsun/kototoro/alternatives/ui/AlternativesSheet.kt
git commit -m "feat(#182): add AlternativesSheet as BottomSheet with drag handle and long-press migrate"
```

---

### 任务 3：更新 AlternativeAD 支持长按回调

**文件：**
- 修改：`app/src/main/kotlin/org/skepsun/kototoro/alternatives/ui/AlternativeAD.kt`

- [ ] **步骤 1：读取 AlternativeAD.kt 当前的函数签名**

```bash
grep -n "fun alternativeAD\|clickListener\|OnListItemClickListener" \
  app/src/main/kotlin/org/skepsun/kototoro/alternatives/ui/AlternativeAD.kt
```

- [ ] **步骤 2：更新 alternativeAD 函数签名，新增 longClickListener 参数**

找到 `fun alternativeAD(...)` 的定义，将签名从：

```kotlin
fun alternativeAD(
    coil: ImageLoader,
    clickListener: OnListItemClickListener<ContentAlternativeModel>,
    context: Context,
): AdapterDelegate<List<ListModel>>
```

改为：

```kotlin
fun alternativeAD(
    coil: ImageLoader,
    clickListener: OnListItemClickListener<ContentAlternativeModel>,
    context: Context,
    longClickListener: ((ContentAlternativeModel) -> Unit)? = null,
): AdapterDelegate<List<ListModel>>
```

- [ ] **步骤 3：在 bind 方法中添加长按监听器**

在 `alternativeAD` 的 `bind` lambda 内，找到 item 根 view 的点击监听器设置处，添加长按监听：

```kotlin
// 在现有 click listener 设置之后添加
if (longClickListener != null) {
    itemView.setOnLongClickListener {
        longClickListener(item)
        true
    }
} else {
    itemView.setOnLongClickListener(null)
}
```

- [ ] **步骤 4：更新 AlternativesActivity 中的 alternativeAD 调用（保持兼容）**

`AlternativesActivity` 中调用 `alternativeAD(coil, this, this)` 时，`longClickListener` 参数有默认值 `null`，无需修改。

- [ ] **步骤 5：编译验证**

```bash
./gradlew :app:compileDebugKotlin --no-daemon 2>&1 | grep -E "error:" | head -20
```

预期：无编译错误。

- [ ] **步骤 6：Commit**

```bash
git add app/src/main/kotlin/org/skepsun/kototoro/alternatives/ui/AlternativeAD.kt
git commit -m "fix(#182): add longClickListener parameter to alternativeAD"
```

---

### 任务 4：更新 AppRouter 使用 AlternativesSheet

**文件：**
- 修改：`app/src/main/kotlin/org/skepsun/kototoro/core/nav/AppRouter.kt`

- [ ] **步骤 1：确认 AppRouter 如何获取 FragmentManager**

```bash
grep -n "fragmentManager\|supportFragmentManager\|fun openAlternatives\|fun show\|BaseAdaptiveSheet" \
  app/src/main/kotlin/org/skepsun/kototoro/core/nav/AppRouter.kt | head -20
```

- [ ] **步骤 2：更新 openAlternatives 方法**

找到 `openAlternatives` 方法（约 lines 424-429），将其改为：

```kotlin
fun openAlternatives(manga: Content) {
    val fm = (contextOrNull() as? androidx.fragment.app.FragmentActivity)?.supportFragmentManager
    if (fm != null) {
        AlternativesSheet.newInstance(manga).show(fm, AlternativesSheet.TAG)
    } else {
        // 降级到 Activity（深链接等场景）
        startActivity(
            Intent(contextOrNull() ?: return, AlternativesActivity::class.java)
                .putExtra(KEY_MANGA, ParcelableContent(manga)),
        )
    }
}
```

添加 import：
```kotlin
import org.skepsun.kototoro.alternatives.ui.AlternativesSheet
```

- [ ] **步骤 3：编译验证**

```bash
./gradlew :app:compileDebugKotlin --no-daemon 2>&1 | grep -E "error:" | head -20
```

预期：无编译错误。

- [ ] **步骤 4：Commit**

```bash
git add app/src/main/kotlin/org/skepsun/kototoro/core/nav/AppRouter.kt
git commit -m "fix(#182): openAlternatives now shows BottomSheet instead of Activity"
```
