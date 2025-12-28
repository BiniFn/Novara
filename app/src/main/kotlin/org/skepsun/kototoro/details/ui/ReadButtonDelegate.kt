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

            // 对视频内容和EPUB内容：传入 ReaderState，优先使用历史记录中的状态
            runCatching {
                val source = manga.source.unwrap()
                val history = viewModel.historyInfo.value.history
                
                if (source is MangaParserSource && (source.contentType == ContentType.VIDEO || source.contentType == ContentType.HENTAI_VIDEO) && !manga.chapters.isNullOrEmpty()) {
                    val state = if (history != null) {
                        // 使用历史记录中的状态（包含正确的章节ID）
                        ReaderState(history)
                    } else {
                        // 没有历史记录时，使用第一个章节
                        val preferredBranch = viewModel.selectedBranchValue
                        ReaderState(manga, preferredBranch)
                    }
                    intentBuilder.state(state)
                } else if (history != null && !manga.chapters.isNullOrEmpty()) {
                    // 对于EPUB章节，使用parentChapterId来精确定位
                    val preferredBranch = viewModel.selectedBranchValue
                    val chapters = manga.chapters?.filter { it.branch == preferredBranch } ?: manga.chapters
                    
                    // 尝试精确匹配章节ID
                    var matchedChapter = chapters?.find { it.id == history.chapterId }
                    
                    // 如果没有精确匹配，检查是否是EPUB父章节ID（旧数据兼容）
                    if (matchedChapter == null) {
                        // 检查history.chapterId是否是父章节ID（旧数据）
                        val potentialParentChapter = chapters?.find { it.id == history.chapterId }
                        if (potentialParentChapter != null && potentialParentChapter.url.endsWith(".epub", ignoreCase = true)) {
                            android.util.Log.d("ReadButtonDelegate", "Detected old history with parent chapter ID: ${history.chapterId}, skipping to avoid overwriting correct history")
                            // 这是父章节ID（旧数据），不要打开阅读器，避免覆盖正确的历史记录
                            // 用户需要手动选择章节重新开始阅读
                            return@runCatching
                        }
                    }
                    
                    // 如果没有精确匹配，且有parentChapterId，使用它来查找内部章节
                    if (matchedChapter == null && history.parentChapterId != null) {
                        android.util.Log.d("ReadButtonDelegate", "Using parentChapterId for EPUB internal chapter lookup: parentChapterId=${history.parentChapterId}, chapterId=${history.chapterId}")
                        
                        // 找到父章节
                        val parentChapter = chapters?.find { it.id == history.parentChapterId }
                        if (parentChapter != null) {
                            // 找到所有属于这个父章节的内部章节
                            val internalChapters = chapters?.filter { chapter ->
                                // 内部章节的URL包含父章节URL作为前缀
                                chapter.url.startsWith(parentChapter.url) && chapter.url.contains("#chapter/")
                            }
                            
                            // 在内部章节中查找匹配的章节ID
                            matchedChapter = internalChapters?.find { it.id == history.chapterId }
                            
                            if (matchedChapter != null) {
                                android.util.Log.d("ReadButtonDelegate", "Found EPUB internal chapter: chapterId=${matchedChapter.id}, url=${matchedChapter.url}")
                            } else {
                                android.util.Log.w("ReadButtonDelegate", "EPUB internal chapter not found: chapterId=${history.chapterId}, parentChapterId=${history.parentChapterId}")
                            }
                        }
                    }
                    
                    // 如果还是没找到，尝试模糊匹配（向后兼容旧数据）
                    if (matchedChapter == null) {
                        android.util.Log.d("ReadButtonDelegate", "Falling back to fuzzy matching for chapterId=${history.chapterId}")
                        matchedChapter = chapters
                            ?.filter { chapter ->
                                val diff = kotlin.math.abs(chapter.id - history.chapterId)
                                diff in 1..1000000
                            }
                            ?.minByOrNull { chapter ->
                                kotlin.math.abs(chapter.id - history.chapterId)
                            }
                    }
                    
                    // 如果找到匹配的章节，使用它创建state
                    if (matchedChapter != null) {
                        android.util.Log.d("ReadButtonDelegate", "Matched chapter: history.chapterId=${history.chapterId}, matched.id=${matchedChapter.id}")
                        val correctedHistory = history.copy(chapterId = matchedChapter.id)
                        intentBuilder.state(ReaderState(correctedHistory))
                    }
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
		
		// Check if this is LocalEpubSource (NEW ARCHITECTURE)
		val isLocalEpub = source is org.skepsun.kototoro.local.epub.LocalEpubSource
		
		val contentType = when {
			isLocalEpub -> ContentType.NOVEL  // LocalEpubSource is always NOVEL
			source is MangaParserSource -> source.contentType
			else -> null
		}
		
		val readText = when (contentType) {
			ContentType.VIDEO, ContentType.HENTAI_VIDEO -> R.string.play // 播放
			ContentType.NOVEL, ContentType.HENTAI_NOVEL -> R.string.read // 阅读
			else -> R.string.read // 默认：阅读
		}
		
		val continueText = when (contentType) {
			ContentType.VIDEO, ContentType.HENTAI_VIDEO -> R.string._continue_play // 继续播放
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
