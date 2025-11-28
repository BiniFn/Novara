package org.skepsun.kototoro.details.ui

import android.content.Context
import android.graphics.Color
import android.text.style.DynamicDrawableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.RelativeSizeSpan
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import androidx.core.view.MenuCompat
import androidx.core.view.get
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialSplitButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.combine
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.isLocal
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.nav.ReaderIntent
import org.skepsun.kototoro.reader.ui.ReaderState
import org.skepsun.kototoro.core.util.ext.getThemeColor
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.details.ui.model.HistoryInfo
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.parsers.model.MangaParserSource
import org.skepsun.kototoro.core.model.unwrap

class ReadButtonDelegate(
	private val splitButton: MaterialSplitButton,
	private val viewModel: DetailsViewModel,
	private val router: AppRouter,
) : View.OnClickListener, PopupMenu.OnMenuItemClickListener, PopupMenu.OnDismissListener {

	private val buttonRead = splitButton[0] as MaterialButton
	private val buttonMenu = splitButton[1] as MaterialButton

	private val context: Context
		get() = buttonRead.context

	override fun onClick(v: View) {
		when (v.id) {
			R.id.button_read -> openReader(isIncognitoMode = false)
			R.id.button_read_menu -> showMenu()
		}
	}

	override fun onMenuItemClick(item: MenuItem): Boolean {
		when (item.itemId) {
			R.id.action_incognito -> openReader(isIncognitoMode = true)
			R.id.action_forget -> viewModel.removeFromHistory()
			R.id.action_download -> {
				router.showDownloadDialog(
					manga = setOf(viewModel.getMangaOrNull() ?: return false),
					snackbarHost = splitButton,
				)
			}

			Menu.NONE -> {
				val branch = viewModel.branches.value.getOrNull(item.order) ?: return false
				viewModel.setSelectedBranch(branch.name)
			}

			else -> return false
		}
		return true
	}

	override fun onDismiss(menu: PopupMenu?) {
		buttonMenu.isChecked = false
	}

	fun attach(lifecycleOwner: LifecycleOwner) {
		buttonRead.setOnClickListener(this)
		buttonMenu.setOnClickListener(this)
		combine(viewModel.isLoading, viewModel.historyInfo, ::Pair)
			.observe(lifecycleOwner) { (isLoading, historyInfo) ->
				onHistoryChanged(isLoading, historyInfo)
			}
	}

	private fun showMenu() {
		val menu = PopupMenu(context, buttonMenu)
		menu.inflate(R.menu.popup_read)
		prepareMenu(menu.menu)
		menu.setOnMenuItemClickListener(this)
		menu.setForceShowIcon(true)
		menu.setOnDismissListener(this)
		if (menu.menu.hasVisibleItems()) {
			buttonMenu.isChecked = true
			menu.show()
		} else {
			buttonMenu.isChecked = false
		}
	}

	private fun prepareMenu(menu: Menu) {
		MenuCompat.setGroupDividerEnabled(menu, true)
		menu.populateBranchList()
		val history = viewModel.historyInfo.value
		menu.findItem(R.id.action_incognito)?.isVisible = !history.isIncognitoMode
		menu.findItem(R.id.action_forget)?.isVisible = history.history != null
		menu.findItem(R.id.action_download)?.isVisible = viewModel.getMangaOrNull()?.isLocal == false
	}

    private fun openReader(isIncognitoMode: Boolean) {
        val manga = viewModel.getMangaOrNull() ?: return
        if (viewModel.historyInfo.value.isChapterMissing) {
            Snackbar.make(buttonRead, R.string.chapter_is_missing, Snackbar.LENGTH_SHORT)
                .show() // TODO
        } else {
            val intentBuilder = ReaderIntent.Builder(context)
                .manga(manga)
                .branch(viewModel.selectedBranchValue)

            // 对视频内容：传入 ReaderState，优先使用历史记录中的状态
            runCatching {
                val source = manga.source.unwrap()
                if (source is MangaParserSource && source.contentType == ContentType.VIDEO && !manga.chapters.isNullOrEmpty()) {
                    val history = viewModel.historyInfo.value.history
                    val state = if (history != null) {
                        // 使用历史记录中的状态（包含正确的章节ID）
                        ReaderState(history)
                    } else {
                        // 没有历史记录时，使用第一个章节
                        val preferredBranch = viewModel.selectedBranchValue
                        ReaderState(manga, preferredBranch)
                    }
                    intentBuilder.state(state)
                }
            }.getOrElse { /* 忽略异常，保持默认行为 */ }
            if (isIncognitoMode) {
                intentBuilder.incognito()
            }
            router.openReader(intentBuilder.build())
            if (isIncognitoMode) {
                Toast.makeText(context, R.string.incognito_mode, Toast.LENGTH_SHORT).show()
            }
        }
    }

	private fun onHistoryChanged(isLoading: Boolean, info: HistoryInfo) {
		val isChaptersLoading = isLoading && (info.totalChapters <= 0 || info.isChapterMissing)
		
		// 根据内容类型选择合适的文案
		val manga = viewModel.getMangaOrNull()
		val source = manga?.source?.unwrap()
		val contentType = (source as? MangaParserSource)?.contentType
		
		val readText = when (contentType) {
			ContentType.VIDEO -> R.string.play // 播放
			ContentType.NOVEL -> R.string.read // 阅读
			else -> R.string.read // 默认：阅读
		}
		
		val continueText = when (contentType) {
			ContentType.VIDEO -> R.string._continue_play // 继续播放
			else -> R.string._continue // 继续
		}
		
		buttonRead.setText(
			when {
				isChaptersLoading -> R.string.loading_
				info.isIncognitoMode -> R.string.incognito
				info.canContinue -> continueText
				else -> readText
			},
		)
		splitButton.isEnabled = !isChaptersLoading && info.isValid
	}

	private fun Menu.populateBranchList() {
		val branches = viewModel.branches.value
		if (branches.size <= 1) {
			return
		}
		for ((i, branch) in branches.withIndex()) {
			val title = buildSpannedString {
				if (branch.isCurrent) {
					inSpans(
						ImageSpan(
							context,
							R.drawable.ic_current_chapter,
							DynamicDrawableSpan.ALIGN_BASELINE,
						),
					) {
						append(' ')
					}
					append(' ')
				}
				append(branch.name ?: context.getString(R.string.system_default))
				append(' ')
				append(' ')
				inSpans(
					ForegroundColorSpan(
						context.getThemeColor(
							android.R.attr.textColorSecondary,
							Color.LTGRAY,
						),
					),
					RelativeSizeSpan(0.74f),
				) {
					append(branch.count.toString())
				}
			}
			val item = add(R.id.group_branches, Menu.NONE, i, title)
			item.isCheckable = true
			item.isChecked = branch.isSelected
		}
		setGroupCheckable(R.id.group_branches, true, true)
	}
}
