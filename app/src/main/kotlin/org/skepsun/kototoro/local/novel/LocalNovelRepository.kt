package org.skepsun.kototoro.local.novel

import androidx.annotation.WorkerThread
import androidx.core.net.toUri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import org.json.JSONObject
import org.skepsun.kototoro.core.model.LocalNovelSource
import org.skepsun.kototoro.core.model.ContentSource
import org.skepsun.kototoro.core.parser.ContentRepository
import org.skepsun.kototoro.local.data.LocalStorageManager
import org.skepsun.kototoro.local.domain.model.LocalContent
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentChapter
import org.skepsun.kototoro.parsers.model.ContentListFilter
import org.skepsun.kototoro.parsers.model.ContentListFilterCapabilities
import org.skepsun.kototoro.parsers.model.ContentListFilterOptions
import org.skepsun.kototoro.parsers.model.ContentPage
import org.skepsun.kototoro.parsers.model.SortOrder
import java.io.File
import java.util.EnumSet
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalNovelRepository @Inject constructor(
	private val storageManager: LocalStorageManager,
) : ContentRepository {

	override val source = LocalNovelSource

	override val sortOrders: Set<SortOrder> = EnumSet.of(SortOrder.ALPHABETICAL)
	override var defaultSortOrder: SortOrder
		get() = SortOrder.ALPHABETICAL
		set(@Suppress("UNUSED_PARAMETER") value) {}

	override val filterCapabilities: ContentListFilterCapabilities
		get() = ContentListFilterCapabilities(isSearchSupported = true)

	override suspend fun getFilterOptions(): ContentListFilterOptions = ContentListFilterOptions()

	private suspend fun findNovelDirs(): List<File> {
		// 统一使用 files/novel 存储根，不再复用漫画存储根
		return storageManager.getNovelReadableDirs().flatMap { root ->
			root.listFiles().orEmpty().filter { 
				it.isDirectory || (it.isFile && (it.extension.equals("cbz", ignoreCase = true) || it.extension.equals("zip", ignoreCase = true)))
			}
		}
	}

	override suspend fun getList(offset: Int, order: SortOrder?, filter: ContentListFilter?): List<Content> {
		if (offset > 0) return emptyList()
		val items = mutableListOf<Content>()
		findNovelDirs().forEach { dir ->
			parseIndex(dir)?.let { items.add(it.first) }
		}
		val query = filter?.query?.trim().orEmpty()
		return if (query.isNotEmpty()) {
			items.filter { it.title.contains(query, ignoreCase = true) }
		} else items
	}


	override suspend fun getDetails(manga: Content): Content {
		// Fast path: use manga.url if it points to a local file
		if (manga.url.startsWith("file://") || manga.url.startsWith("zip://") || manga.url.startsWith("cbz://")) {
			val uri = runCatching { android.net.Uri.parse(manga.url) }.getOrNull()
			var filePath = uri?.path
			if (uri?.scheme == "zip" || uri?.scheme == "cbz") {
				// URI for zip looks like zip:///path/to/file.cbz
				filePath = uri.path
			}
			val file = filePath?.let { File(it) }
			if (file != null && file.exists()) {
				val parsed = parseIndex(file)
				if (parsed != null && parsed.first.id == manga.id) {
					return parsed.first
				}
				if (parsed != null) {
					return parsed.first
				}
			}
		}

		// Try to find by filename pattern first (for multi-CBZ format)
		val dirByName = findNovelDirs().firstOrNull { it.name.startsWith(manga.id.toString()) }
		if (dirByName != null) {
			return parseIndex(dirByName)?.first ?: manga
		}
		
		// For single CBZ files, we need to parse each file to find the matching manga ID
		val allDirs = findNovelDirs()
		for (dir in allDirs) {
			val parsed = parseIndex(dir)
			if (parsed != null && parsed.first.id == manga.id) {
				return parsed.first
			}
		}
		
		return manga
	}

	override suspend fun getPages(chapter: ContentChapter, nextChapterUrl: String?): List<ContentPage> {
		val uri = runCatching { android.net.Uri.parse(chapter.url) }.getOrNull()
		if (uri != null && (uri.scheme == "file" || uri.scheme == "zip" || uri.scheme == "cbz")) {
			return org.skepsun.kototoro.local.data.input.LocalContentParser(uri).getPages(chapter)
		}
		
		return listOf(
			ContentPage(
				id = chapter.id,
				url = chapter.url,
				preview = null,
				source = source,
			),
		)
	}

	override suspend fun getPageUrl(page: ContentPage): String = page.url

	override suspend fun getRelated(seed: Content): List<Content> = emptyList()

	/**
	 * 列出所有本地小说（用于本地索引）。
	 */
	suspend fun getAllLocalNovels(): List<LocalContent> {
		return findNovelDirs()
			.filter { it.isDirectory && File(it, "index.json").exists() }
			.mapNotNull { dir -> getLocalNovel(dir, withDetails = false) }
	}

	/**
	 * 从目录读取小说并包装为 LocalContent；若目录不合法返回 null。
	 */
	fun getLocalNovel(dir: File, withDetails: Boolean): LocalContent? {
		val parsed = parseIndex(dir) ?: return null
		val manga = if (withDetails) {
			parsed.first
		} else {
			parsed.first.copy(chapters = null)
		}
		return LocalContent(manga, dir)
	}

	@WorkerThread
	internal fun parseIndex(dir: File): Pair<Content, List<ContentChapter>>? {
		return runCatching {
			val parser = org.skepsun.kototoro.local.data.input.LocalContentParser(dir)
			val localContent = runBlocking { parser.getContent(withDetails = true) }
			
			// Map chapters to ensure they have the correct local source when applicable
			val transformedChapters = localContent.manga.chapters?.map { chapter ->
				if (chapter.source != null && !chapter.source!!.name.startsWith("LOCAL", ignoreCase = true)) {
					chapter
				} else {
					chapter.copy(source = source)
				}
			}
			
			val transformedContent = localContent.manga.copy(
				chapters = transformedChapters,
				source = source
			)
			transformedContent to (transformedChapters ?: emptyList())
		}.onFailure {
			it.printStackTrace()
		}.getOrNull()
	}
}
