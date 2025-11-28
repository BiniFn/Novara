package org.skepsun.kototoro.local.data.output

import androidx.core.net.toFile
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.internal.closeQuietly
import org.skepsun.kototoro.core.model.isLocal
import org.skepsun.kototoro.core.util.MimeTypes
import org.skepsun.kototoro.core.util.ext.MimeType
import org.skepsun.kototoro.core.util.ext.deleteAwait
import org.skepsun.kototoro.core.util.ext.takeIfReadable
import org.skepsun.kototoro.core.util.ext.toFileNameSafe
import org.skepsun.kototoro.core.zip.ZipOutput
import org.skepsun.kototoro.local.data.MangaIndex
import org.skepsun.kototoro.local.data.input.LocalMangaParser
import org.skepsun.kototoro.parsers.model.Manga
import org.skepsun.kototoro.parsers.model.MangaChapter
import org.skepsun.kototoro.parsers.util.nullIfEmpty
import java.io.File

class LocalMangaDirOutput(
	rootFile: File,
	manga: Manga,
) : LocalMangaOutput(rootFile) {

	private val chaptersOutput = HashMap<MangaChapter, ZipOutput>()
	private val index = MangaIndex(File(rootFile, ENTRY_NAME_INDEX).takeIfReadable()?.readText())
	private val mutex = Mutex()

	init {
		if (!manga.isLocal) {
			index.setMangaInfo(manga)
		}
	}

	override suspend fun mergeWithExisting() = Unit

	override suspend fun addCover(file: File, type: MimeType?) = mutex.withLock {
		val name = buildString {
			append("cover")
			MimeTypes.getExtension(type)?.let { ext ->
				append('.')
				append(ext)
			}
		}
		runInterruptible(Dispatchers.IO) {
			file.copyTo(File(rootFile, name), overwrite = true)
		}
		index.setCoverEntry(name)
		flushIndex()
	}

	override suspend fun addPage(chapter: IndexedValue<MangaChapter>, file: File, pageNumber: Int, type: MimeType?) =
		mutex.withLock {
			val output = chaptersOutput.getOrPut(chapter.value) {
				ZipOutput(File(rootFile, chapterFileName(chapter) + SUFFIX_TMP))
			}
			val name = buildString {
				append(FILENAME_PATTERN.format(chapter.value.branch.hashCode(), chapter.index + 1, pageNumber))
				MimeTypes.getExtension(type)?.let { ext ->
					append('.')
					append(ext)
				}
			}
			runInterruptible(Dispatchers.IO) {
				output.put(name, file)
			}
			index.addChapter(chapter, chapterFileName(chapter))
		}

	override suspend fun flushChapter(chapter: MangaChapter): Boolean = mutex.withLock {
		val output = chaptersOutput.remove(chapter) ?: return@withLock false
		output.flushAndFinish()
		flushIndex()
		true
	}

	override suspend fun finish() = mutex.withLock {
		flushIndex()
		for (output in chaptersOutput.values) {
			output.flushAndFinish()
		}
		chaptersOutput.clear()
	}

	override suspend fun cleanup() = mutex.withLock {
		for (output in chaptersOutput.values) {
			output.file.deleteAwait()
		}
	}

	override fun close() {
		for (output in chaptersOutput.values) {
			output.closeQuietly()
		}
	}

	suspend fun deleteChapters(ids: Set<Long>) = mutex.withLock {
		val chapters = checkNotNull(
			(index.getMangaInfo() ?: LocalMangaParser(rootFile).getManga(withDetails = true).manga).chapters,
		) {
			"No chapters found"
		}.withIndex()
		val victimsIds = ids.toMutableSet()
		for (chapter in chapters) {
			if (!victimsIds.remove(chapter.value.id)) {
				continue
			}
			val chapterFile = index.getChapterFileName(chapter.value.id)?.let {
				File(rootFile, it)
			} ?: chapter.value.url.toUri().toFile()
			chapterFile.deleteAwait()
			index.removeChapter(chapter.value.id)
		}
		check(victimsIds.isEmpty()) {
			"${victimsIds.size} of ${ids.size} chapters was not removed: not found"
		}
	}

	fun setIndex(newIndex: MangaIndex) {
		index.setFrom(newIndex)
	}

	private suspend fun ZipOutput.flushAndFinish() = runInterruptible(Dispatchers.IO) {
		val e: Throwable? = try {
			finish()
			null
		} catch (e: Throwable) {
			e
		} finally {
			close()
		}
		if (e == null) {
			val resFile = File(file.absolutePath.removeSuffix(SUFFIX_TMP))
			file.renameTo(resFile)
		} else {
			file.delete()
			throw e
		}
	}

	private fun chapterFileName(chapter: IndexedValue<MangaChapter>): String {
		index.getChapterFileName(chapter.value.id)?.let {
			return it
		}
		val baseName = buildString {
			append(chapter.index)
			chapter.value.title?.nullIfEmpty()?.let {
				append('_')
				append(it.toFileNameSafe())
			}
			if (length > 32) {
				deleteRange(31, lastIndex)
			}
		}
		var i = 0
		while (true) {
			val name = (if (i == 0) baseName else baseName + "_$i") + ".cbz"
			if (!File(rootFile, name).exists()) {
				return name
			}
			i++
		}
	}

	private suspend fun flushIndex() = runInterruptible(Dispatchers.IO) {
		File(rootFile, ENTRY_NAME_INDEX).writeText(index.toString())
	}

	/**
	 * 添加完整的EPUB章节文件
	 * 
	 * EPUB本身就是ZIP格式，直接保存为CBZ文件，不需要再次打包
	 * 这个方法跳过ZipOutput，直接保存文件并更新index
	 * 
	 * 同时解析EPUB内容，为每个EPUB内部章节创建MangaChapter
	 * 
	 * 重要：删除原始章节，避免章节重复
	 */
	suspend fun addEpubChapter(chapter: IndexedValue<MangaChapter>, epubFile: File) = mutex.withLock {
		android.util.Log.i("LocalMangaDirOutput", "addEpubChapter: Starting, epubFile=${epubFile.absolutePath}, exists=${epubFile.exists()}, size=${epubFile.length()}")
		android.util.Log.i("LocalMangaDirOutput", "addEpubChapter: Original chapter ID=${chapter.value.id}, title=${chapter.value.title}")
		
		// 第一步：记录需要隐藏的原始章节ID（用于过滤在线章节）
		try {
			android.util.Log.i("LocalMangaDirOutput", "addEpubChapter: Step 1 - Hiding original chapter")
			
			// 删除本地章节文件（如果存在）
			val originalChapterFileName = index.getChapterFileName(chapter.value.id)
			android.util.Log.i("LocalMangaDirOutput", "addEpubChapter: Original chapter file name: $originalChapterFileName")
			
			val originalChapterFile = originalChapterFileName?.let {
				File(rootFile, it)
			}
			if (originalChapterFile != null && originalChapterFile.exists()) {
				android.util.Log.i("LocalMangaDirOutput", "addEpubChapter: Deleting original chapter file: ${originalChapterFile.absolutePath}")
				originalChapterFile.deleteAwait()
				android.util.Log.i("LocalMangaDirOutput", "addEpubChapter: Original chapter file deleted")
			} else {
				android.util.Log.i("LocalMangaDirOutput", "addEpubChapter: No original chapter file to delete (file=$originalChapterFile, exists=${originalChapterFile?.exists()})")
			}
			
			// 从index中删除章节记录
			android.util.Log.i("LocalMangaDirOutput", "addEpubChapter: Removing chapter from index")
			val removed = index.removeChapter(chapter.value.id)
			android.util.Log.i("LocalMangaDirOutput", "addEpubChapter: Chapter removed from index: $removed")
			
			// 添加到隐藏列表（用于过滤在线章节）
			android.util.Log.i("LocalMangaDirOutput", "addEpubChapter: Adding chapter to hidden list")
			index.addHiddenChapterId(chapter.value.id)
			android.util.Log.i("LocalMangaDirOutput", "addEpubChapter: Chapter added to hidden list, hidden IDs: ${index.getHiddenChapterIds()}")
			
			android.util.Log.i("LocalMangaDirOutput", "addEpubChapter: Step 1 completed - original chapter hidden")
		} catch (e: Exception) {
			android.util.Log.e("LocalMangaDirOutput", "addEpubChapter: ERROR in step 1: ${e.message}", e)
			// 继续执行，不影响EPUB添加
		}
		
		// 第二步：保存EPUB文件
		val chapterFile = chapterFileName(chapter)
		val targetFile = File(rootFile, chapterFile)
		
		android.util.Log.i("LocalMangaDirOutput", "addEpubChapter: Target file=${targetFile.absolutePath}")
		
		runInterruptible(Dispatchers.IO) {
			// 复制EPUB文件到目标位置
			if (!epubFile.renameTo(targetFile)) {
				println("LocalMangaDirOutput.addEpubChapter: Rename failed, copying instead")
				targetFile.outputStream().use { output ->
					epubFile.inputStream().use { input ->
						input.copyTo(output)
					}
				}
				epubFile.delete()
			} else {
				println("LocalMangaDirOutput.addEpubChapter: Rename succeeded")
			}
		}
		
		println("LocalMangaDirOutput.addEpubChapter: File saved, size=${targetFile.length()}")
		
		// 第三步：解析EPUB并添加内部章节
		try {
			val epubParser = org.skepsun.kototoro.local.epub.LocalEpubParser(targetFile)
			val epubManga = epubParser.parseManga()
			val epubChapters = epubManga?.chapters
			
			if (epubChapters != null && epubChapters.isNotEmpty()) {
				println("LocalMangaDirOutput.addEpubChapter: Found ${epubChapters.size} chapters in EPUB")
				
				// 为每个EPUB内部章节创建MangaChapter
				// 使用原始章节的index作为基础，确保章节顺序正确
				epubChapters.forEachIndexed { epubChapterIndex, epubChapter ->
					val subChapter = IndexedValue(
						index = chapter.index, // 所有EPUB内部章节使用相同的index（原始卷章节的index）
						value = epubChapter.copy(
							url = "file://${targetFile.absolutePath}#chapter/$epubChapterIndex",
							branch = chapter.value.branch,
							source = org.skepsun.kototoro.core.model.LocalMangaSource,
						)
					)
					index.addChapter(subChapter, chapterFile)
					println("LocalMangaDirOutput.addEpubChapter: Added EPUB chapter ${epubChapterIndex}: ${epubChapter.title}, ID=${epubChapter.id}")
				}
				
				println("LocalMangaDirOutput.addEpubChapter: Successfully added ${epubChapters.size} EPUB chapters")
			} else {
				println("LocalMangaDirOutput.addEpubChapter: Failed to parse EPUB or no chapters found, adding as single chapter")
				// 如果解析失败，添加为单个章节
				index.addChapter(chapter, chapterFile)
			}
		} catch (e: Exception) {
			println("LocalMangaDirOutput.addEpubChapter: Error parsing EPUB: ${e.message}")
			e.printStackTrace()
			// 出错时添加为单个章节
			index.addChapter(chapter, chapterFile)
		}
		
		flushIndex()
		
		println("LocalMangaDirOutput.addEpubChapter: Index updated successfully")
	}

	companion object {

		private const val FILENAME_PATTERN = "%08d_%04d%04d"
	}
}
