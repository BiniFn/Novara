package org.skepsun.kototoro.details.ui

import android.content.Context
import org.skepsun.kototoro.R
import org.skepsun.kototoro.bookmarks.domain.Bookmark
import org.skepsun.kototoro.details.data.MangaDetails
import org.skepsun.kototoro.details.ui.model.ChapterListItem
import org.skepsun.kototoro.details.ui.model.toListItem
import org.skepsun.kototoro.list.ui.model.ListHeader
import org.skepsun.kototoro.list.ui.model.ListModel
import org.skepsun.kototoro.parsers.util.mapToSet

fun MangaDetails.mapChapters(
	currentChapterId: Long,
	newCount: Int,
	branch: String?,
	bookmarks: List<Bookmark>,
	isGrid: Boolean,
	isDownloadedOnly: Boolean,
): List<ChapterListItem> {
	// 过滤掉EPUB内部章节
	// 1. URL包含#chapter/的章节（阅读器内部生成的）
	// 2. 本地下载的EPUB展开章节（通过检查是否只有1个remote章节但有多个local章节来判断）
	val allRemoteChapters = chapters[branch].orEmpty()
	val allLocalChapters = local?.manga?.getChapters(branch).orEmpty()
	
	// 检查是否为EPUB：
	// 1. 只有1个remote章节，且URL以.epub结尾
	// 2. local章节要么是0（未下载），要么远大于1（已下载并展开）
	val isSingleEpub = allRemoteChapters.size == 1 && 
		allRemoteChapters.firstOrNull()?.url?.endsWith(".epub", ignoreCase = true) == true
	val isEpubExpanded = isSingleEpub && allLocalChapters.size > 1
	
	// 过滤章节：
	// 1. 移除URL包含#chapter/的章节（阅读器内部生成的）
	// 2. 如果有EPUB文件，移除所有.cbz文件（这些是EPUB展开后保存的）
	val hasEpubChapter = allRemoteChapters.any { it.url.endsWith(".epub", ignoreCase = true) } ||
		allLocalChapters.any { it.url.endsWith(".epub", ignoreCase = true) }
	
	val remoteChapters = allRemoteChapters.filter { chapter ->
		!chapter.url.contains("#chapter/") && 
		(!hasEpubChapter || !chapter.url.endsWith(".cbz", ignoreCase = true))
	}
	
	val localChapters = if (isEpubExpanded) {
		// 如果是EPUB展开的章节，只保留第一个（原始EPUB章节）
		android.util.Log.d("ChaptersMapper", "Detected EPUB expansion, filtering local chapters")
		emptyList()
	} else {
		allLocalChapters.filter { chapter ->
			!chapter.url.contains("#chapter/") &&
			(!hasEpubChapter || !chapter.url.endsWith(".cbz", ignoreCase = true))
		}
	}

	
	if (remoteChapters.isEmpty() && localChapters.isEmpty()) {
		return emptyList()
	}
	val bookmarked = bookmarks.mapToSet { it.chapterId }
	val newFrom = if (newCount == 0 || remoteChapters.isEmpty()) Int.MAX_VALUE else remoteChapters.size - newCount
	val ids = buildSet(maxOf(remoteChapters.size, localChapters.size)) {
		remoteChapters.mapTo(this) { it.id }
		localChapters.mapTo(this) { it.id }
	}
	val result = ArrayList<ChapterListItem>(ids.size)
	val localMap = if (localChapters.isNotEmpty()) {
		localChapters.associateByTo(LinkedHashMap(localChapters.size)) { it.id }
	} else {
		null
	}
	var isUnread = currentChapterId !in ids
	if (!isDownloadedOnly || local?.manga?.chapters == null) {
		for (chapter in remoteChapters) {
			val local = localMap?.remove(chapter.id)
			if (chapter.id == currentChapterId) {
				isUnread = true
			}
			result += (local ?: chapter).toListItem(
				isCurrent = chapter.id == currentChapterId,
				isUnread = isUnread,
				isNew = isUnread && result.size >= newFrom,
				isDownloaded = local != null,
				isBookmarked = chapter.id in bookmarked,
				isGrid = isGrid,
			)
		}
	}
	if (!localMap.isNullOrEmpty()) {
		for (chapter in localMap.values) {
			if (chapter.id == currentChapterId) {
				isUnread = true
			}
			result += chapter.toListItem(
				isCurrent = chapter.id == currentChapterId,
				isUnread = isUnread,
				isNew = false,
				isDownloaded = !isLocal,
				isBookmarked = chapter.id in bookmarked,
				isGrid = isGrid,
			)
		}
	}
	return result
}

fun List<ChapterListItem>.withVolumeHeaders(context: Context): MutableList<ListModel> {
	var prevVolume = 0
	val result = ArrayList<ListModel>((size * 1.4).toInt())
	for (item in this) {
		val chapter = item.chapter
		if (chapter.volume != prevVolume) {
			val text = if (chapter.volume == 0) {
				context.getString(R.string.volume_unknown)
			} else {
				context.getString(R.string.volume_, chapter.volume)
			}
			result.add(ListHeader(text))
			prevVolume = chapter.volume
		}
		result.add(item)
	}
	return result
}
