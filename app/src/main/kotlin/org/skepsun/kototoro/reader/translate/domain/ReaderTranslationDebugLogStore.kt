package org.skepsun.kototoro.reader.translate.domain

import androidx.collection.LongSparseArray
import dagger.hilt.android.scopes.ActivityRetainedScoped
import javax.inject.Inject

@ActivityRetainedScoped
class ReaderTranslationDebugLogStore @Inject constructor() {

	private val lock = Any()
	private val pageLogs = LongSparseArray<ArrayDeque<String>>()

	fun append(pageId: Long, message: String) {
		if (pageId <= 0L || message.isBlank()) return
		synchronized(lock) {
			val queue = pageLogs[pageId] ?: ArrayDeque<String>().also { pageLogs.put(pageId, it) }
			if (queue.size >= MAX_PAGE_LOG_LINES) {
				repeat(queue.size - MAX_PAGE_LOG_LINES + 1) { queue.removeFirstOrNull() }
			}
			queue.addLast(message)
		}
	}

	fun metric(pageId: Long, key: String, value: Any) {
		append(pageId, "metric.$key=$value")
	}

	fun get(pageId: Long): String {
		synchronized(lock) {
			return pageLogs[pageId]?.joinToString(separator = "\n").orEmpty()
		}
	}

	fun clearPage(pageId: Long) {
		synchronized(lock) {
			pageLogs.remove(pageId)
		}
	}

	fun clearAll() {
		synchronized(lock) {
			pageLogs.clear()
		}
	}

	private companion object {
		const val MAX_PAGE_LOG_LINES = 160
	}
}
