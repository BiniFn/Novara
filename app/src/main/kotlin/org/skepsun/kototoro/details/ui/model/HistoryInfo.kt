package org.skepsun.kototoro.details.ui.model

import org.skepsun.kototoro.core.model.MangaHistory
import org.skepsun.kototoro.details.data.MangaDetails
import org.skepsun.kototoro.details.data.ReadingTime

data class HistoryInfo(
	val totalChapters: Int,
	val currentChapter: Int,
	val history: MangaHistory?,
	val isIncognitoMode: Boolean,
	val isChapterMissing: Boolean,
	val canDownload: Boolean,
	val estimatedTime: ReadingTime?,
) {
	val isValid: Boolean
		get() = totalChapters >= 0

	val canContinue
		get() = currentChapter >= 0

	val percent: Float
		get() = if (history != null && (canContinue || isChapterMissing)) {
			history.percent
		} else {
			0f
		}
}

fun HistoryInfo(
	manga: MangaDetails?,
	branch: String?,
	history: MangaHistory?,
	isIncognitoMode: Boolean,
	estimatedTime: ReadingTime?,
): HistoryInfo {
	val chapters = if (manga?.chapters?.isEmpty() == true) {
		emptyList()
	} else {
		manga?.chapters?.get(branch)
	}
	val currentChapter = if (history != null && !chapters.isNullOrEmpty()) {
		// First try exact match
		var index = chapters.indexOfFirst { it.id == history.chapterId }
		
		// If no exact match, try fuzzy match for EPUB chapters (parent chapter ID vs internal chapter ID)
		// Find the chapter with the smallest ID difference (closest match)
		if (index < 0) {
			val matchedChapter = chapters
				.filter { chapter ->
					val diff = kotlin.math.abs(chapter.id - history.chapterId)
					diff in 1..1000000  // Within the range of one EPUB file's chapters
				}
				.minByOrNull { chapter ->
					kotlin.math.abs(chapter.id - history.chapterId)
				}
			
			if (matchedChapter != null) {
				index = chapters.indexOf(matchedChapter)
			}
		}
		
		index
	} else {
		-2
	}
	// Check if chapter is missing
	// For EPUB chapters, also check if the history chapter ID is a parent chapter ID
	// by checking if any internal chapter ID is within 1000000 of the history chapter ID
	val isChapterMissing = if (history != null && manga?.isLoaded == true) {
		val directMatch = manga.allChapters.any { it.id == history.chapterId }
		if (directMatch) {
			false
		} else {
			// Check if this might be a parent chapter ID (off by 1 from internal chapter ID)
			// Internal chapter IDs are: parentChapterId + (index * 1000000L) + 1
			// So if history.chapterId is parentChapterId, we should find internal chapters nearby
			val hasNearbyInternalChapter = manga.allChapters.any { chapter ->
				val diff = kotlin.math.abs(chapter.id - history.chapterId)
				diff in 1..1000000  // Within the range of one EPUB file's chapters
			}
			!hasNearbyInternalChapter
		}
	} else {
		false
	}
	
	if (history != null && manga?.isLoaded == true) {
		android.util.Log.d("HistoryInfo", "Checking chapter: history.chapterId=${history.chapterId}")
		android.util.Log.d("HistoryInfo", "Total allChapters: ${manga.allChapters.size}")
		android.util.Log.d("HistoryInfo", "First 3 chapter IDs: ${manga.allChapters.take(3).map { it.id }}")
		android.util.Log.d("HistoryInfo", "currentChapter index=$currentChapter")
		if (currentChapter >= 0 && chapters != null && currentChapter < chapters.size) {
			android.util.Log.d("HistoryInfo", "Matched chapter: id=${chapters[currentChapter].id}, title=${chapters[currentChapter].title}")
		}
		android.util.Log.d("HistoryInfo", "isChapterMissing=$isChapterMissing")
	}
	
	return HistoryInfo(
		totalChapters = chapters?.size ?: -1,
		currentChapter = currentChapter,
		history = history,
		isIncognitoMode = isIncognitoMode,
		isChapterMissing = isChapterMissing,
		canDownload = manga?.isLocal == false,
		estimatedTime = estimatedTime,
	)
}
