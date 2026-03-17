package org.skepsun.kototoro.core.parser

import android.util.Log
import androidx.collection.MutableLongSet
import coil3.request.CachePolicy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import org.skepsun.kototoro.BuildConfig
import org.skepsun.kototoro.core.cache.MemoryContentCache
import org.skepsun.kototoro.core.cache.SafeDeferred
import org.skepsun.kototoro.core.util.MultiMutex
import org.skepsun.kototoro.core.util.ext.processLifecycleScope
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentChapter
import org.skepsun.kototoro.parsers.model.ContentPage
import org.skepsun.kototoro.parsers.util.runCatchingCancellable

abstract class CachingContentRepository(
	private val cache: MemoryContentCache,
) : ContentRepository {

	private val detailsMutex = MultiMutex<Long>()
	private val relatedContentMutex = MultiMutex<Long>()
	private val pagesMutex = MultiMutex<Long>()

	final override suspend fun getDetails(manga: Content): Content = getDetails(manga, CachePolicy.ENABLED)

	final override suspend fun getPages(chapter: ContentChapter, nextChapterUrl: String?): List<ContentPage> = pagesMutex.withLock(chapter.id) {
		cache.getPages(source, chapter.url)?.let { return it }
		val pages = asyncSafe {
			getPagesImpl(chapter, nextChapterUrl).distinctById()
		}
		cache.putPages(source, chapter.url, pages)
		pages
	}.await()

	final override suspend fun getRelated(seed: Content): List<Content> = relatedContentMutex.withLock(seed.id) {
		cache.getRelatedContent(source, seed.url)?.let { return it }
		val related = asyncSafe {
			getRelatedContentImpl(seed).filterNot { it.id == seed.id }
		}
		cache.putRelatedContent(source, seed.url, related)
		related
	}.await()

	suspend fun getDetails(manga: Content, cachePolicy: CachePolicy): Content = detailsMutex.withLock(manga.id) {
		if (cachePolicy.readEnabled) {
			cache.getDetails(source, manga.url)?.let { return it }
		}
		val details = asyncSafe {
			getDetailsImpl(manga)
		}
		if (cachePolicy.writeEnabled) {
			cache.putDetails(source, manga.url, details)
		}
		details
	}.await()

	suspend fun peekDetails(manga: Content): Content? {
		return cache.getDetails(source, manga.url)
	}

	fun invalidateCache() {
		cache.clear(source)
	}

	protected abstract suspend fun getDetailsImpl(manga: Content): Content

	protected abstract suspend fun getRelatedContentImpl(seed: Content): List<Content>

	protected abstract suspend fun getPagesImpl(chapter: ContentChapter, nextChapterUrl: String? = null): List<ContentPage>

	override suspend fun getChapterContent(chapter: ContentChapter, nextChapterUrl: String?): org.skepsun.kototoro.parsers.model.NovelChapterContent? = null

	private suspend fun <T> asyncSafe(block: suspend CoroutineScope.() -> T): SafeDeferred<T> {
		var dispatcher = currentCoroutineContext()[CoroutineDispatcher.Key]
		if (dispatcher == null || dispatcher is MainCoroutineDispatcher) {
			dispatcher = Dispatchers.Default
		}
		return SafeDeferred(
			processLifecycleScope.async(dispatcher) {
				runCatchingCancellable { block() }
			},
		)
	}

	private fun List<ContentPage>.distinctById(): List<ContentPage> {
		if (isEmpty()) {
			return emptyList()
		}
		val result = ArrayList<ContentPage>(size)
		val set = MutableLongSet(size)
		for (page in this) {
			if (set.add(page.id)) {
				result.add(page)
			} else if (BuildConfig.DEBUG) {
				Log.w(null, "Duplicate page: $page")
			}
		}
		return result
	}
}
