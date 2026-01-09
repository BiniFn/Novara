package org.skepsun.kototoro.local.novel

import androidx.annotation.WorkerThread
import androidx.core.net.toUri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import org.json.JSONObject
import org.skepsun.kototoro.core.model.LocalNovelSource
import org.skepsun.kototoro.core.model.MangaSource
import org.skepsun.kototoro.core.parser.MangaRepository
import org.skepsun.kototoro.local.data.LocalStorageManager
import org.skepsun.kototoro.local.domain.model.LocalManga
import org.skepsun.kototoro.parsers.model.Manga
import org.skepsun.kototoro.parsers.model.MangaChapter
import org.skepsun.kototoro.parsers.model.MangaListFilter
import org.skepsun.kototoro.parsers.model.MangaListFilterCapabilities
import org.skepsun.kototoro.parsers.model.MangaListFilterOptions
import org.skepsun.kototoro.parsers.model.MangaPage
import org.skepsun.kototoro.parsers.model.SortOrder
import java.io.File
import java.util.EnumSet
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalNovelRepository @Inject constructor(
	private val storageManager: LocalStorageManager,
) : MangaRepository {

	override val source = LocalNovelSource

	override val sortOrders: Set<SortOrder> = EnumSet.of(SortOrder.ALPHABETICAL)
	override var defaultSortOrder: SortOrder
		get() = SortOrder.ALPHABETICAL
		set(@Suppress("UNUSED_PARAMETER") value) {}

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(isSearchSupported = true)

	override suspend fun getFilterOptions(): MangaListFilterOptions = MangaListFilterOptions()

	private suspend fun findNovelDirs(): List<File> {
		// 统一使用 files/novel 存储根，不再复用漫画存储根
		return storageManager.getNovelReadableDirs().flatMap { root ->
			root.listFiles().orEmpty().filter { 
				it.isDirectory || (it.isFile && (it.extension.equals("cbz", ignoreCase = true) || it.extension.equals("zip", ignoreCase = true)))
			}
		}
	}

	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
		if (offset > 0) return emptyList()
		val items = mutableListOf<Manga>()
		findNovelDirs().forEach { dir ->
			parseIndex(dir)?.let { items.add(it.first) }
		}
		val query = filter?.query?.trim().orEmpty()
		return if (query.isNotEmpty()) {
			items.filter { it.title.contains(query, ignoreCase = true) }
		} else items
	}


	override suspend fun getDetails(manga: Manga): Manga {
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

	override suspend fun getPages(chapter: MangaChapter, nextChapterUrl: String?): List<MangaPage> {
		val uri = runCatching { android.net.Uri.parse(chapter.url) }.getOrNull()
		if (uri != null && (uri.scheme == "file" || uri.scheme == "zip" || uri.scheme == "cbz")) {
			return org.skepsun.kototoro.local.data.input.LocalMangaParser(uri).getPages(chapter)
		}
		
		return listOf(
			MangaPage(
				id = chapter.id,
				url = chapter.url,
				preview = null,
				source = source,
			),
		)
	}

	override suspend fun getPageUrl(page: MangaPage): String = page.url

	override suspend fun getRelated(seed: Manga): List<Manga> = emptyList()

	/**
	 * 列出所有本地小说（用于本地索引）。
	 */
	suspend fun getAllLocalNovels(): List<LocalManga> {
		return findNovelDirs()
			.filter { it.isDirectory && File(it, "index.json").exists() }
			.mapNotNull { dir -> getLocalNovel(dir, withDetails = false) }
	}

	/**
	 * 从目录读取小说并包装为 LocalManga；若目录不合法返回 null。
	 */
	fun getLocalNovel(dir: File, withDetails: Boolean): LocalManga? {
		val parsed = parseIndex(dir) ?: return null
		val manga = if (withDetails) {
			parsed.first
		} else {
			parsed.first.copy(chapters = null)
		}
		return LocalManga(manga, dir)
	}

	@WorkerThread
	internal fun parseIndex(dir: File): Pair<Manga, List<MangaChapter>>? {
		// Handle single CBZ files
		if (dir.isFile && (dir.extension.equals("cbz", ignoreCase = true) || dir.extension.equals("zip", ignoreCase = true))) {
			return runCatching {
				val parser = org.skepsun.kototoro.local.data.input.LocalMangaParser(dir)
				val localManga = runBlocking { parser.getManga(withDetails = true) }
				val transformedChapters = localManga.manga.chapters?.map { chapter ->
					chapter.copy(source = source)
				}
				val transformedManga = localManga.manga.copy(
					chapters = transformedChapters,
					source = source
				)
				transformedManga to (transformedChapters ?: emptyList())
			}.getOrNull()
		}
		
		// Handle multi-CBZ format (directory-based)
		val indexFile = File(dir, "index.json")
		val index = org.skepsun.kototoro.local.data.MangaIndex.read(indexFile) ?: return null
		val mangaInfo = index.getMangaInfo() ?: return null
		
		val transformedChapters = mangaInfo.chapters?.map { chapter ->
			val fileName = index.getChapterFileName(chapter.id)
			if (fileName != null) {
				val localFile = File(dir, fileName)
				// Check if file actually exists
				if (localFile.exists()) {
					// File exists - use local source
					val url = if (fileName.endsWith(".cbz", ignoreCase = true) || fileName.endsWith(".zip", ignoreCase = true)) {
						"zip://${localFile.absolutePath}"
					} else {
						localFile.toUri().toString()
					}
					chapter.copy(
						url = url,
						source = source  // LocalNovelSource
					)
				} else {
					// File doesn't exist - keep original URL and source for online reading
					Log.d("LocalNovelRepository", "Chapter ${chapter.title} not downloaded, will use online source")
					chapter  // Keep original chapter with remote URL and source
				}
			} else {
				// No file mapping - keep original for online reading
				Log.d("LocalNovelRepository", "Chapter ${chapter.title} has no file mapping, will use online source")
				chapter  // Keep original chapter
			}
		}
		
		val transformedManga = mangaInfo.copy(
			id = mangaInfo.id,
			title = mangaInfo.title,
			chapters = transformedChapters,
			url = dir.toUri().toString(),
			source = if (mangaInfo.source != org.skepsun.kototoro.core.model.UnknownMangaSource && 
						!mangaInfo.source.name.startsWith("LOCAL")) mangaInfo.source else source
		)
		
		return transformedManga to (transformedChapters ?: emptyList())
	}
}
