package org.skepsun.kototoro.local.data.output

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.Closeable
import org.skepsun.kototoro.core.prefs.DownloadFormat
import org.skepsun.kototoro.core.util.ext.MimeType
import org.skepsun.kototoro.core.util.ext.printStackTraceDebug
import org.skepsun.kototoro.core.util.ext.toFileNameSafe
import org.skepsun.kototoro.local.data.input.LocalContentParser
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentChapter
import org.skepsun.kototoro.parsers.util.runCatchingCancellable
import java.io.File

sealed class LocalContentOutput(
	val rootFile: File,
) : Closeable {

	abstract suspend fun mergeWithExisting()

	abstract suspend fun addCover(file: File, type: MimeType?)

	abstract suspend fun addPage(chapter: IndexedValue<ContentChapter>, file: File, pageNumber: Int, type: MimeType?)
	
	abstract suspend fun putChapterImages(chapterId: Long, remoteImages: Map<String, String>)

	abstract suspend fun flushChapter(chapter: ContentChapter): Boolean

	abstract suspend fun finish()

	abstract suspend fun cleanup()

	companion object {

		const val ENTRY_NAME_INDEX = "index.json"
		const val SUFFIX_TMP = ".tmp"
		private val mutex = Mutex()

		suspend fun getOrCreate(
			root: File,
			manga: Content,
			format: DownloadFormat,
		): LocalContentOutput = withContext(Dispatchers.IO) {
			val targetFormat = if (format == DownloadFormat.AUTOMATIC) {
				if (manga.chapters.let { it != null && it.size <= 3 }) {
					DownloadFormat.SINGLE_CBZ
				} else {
					DownloadFormat.MULTIPLE_CBZ
				}
			} else {
				format
			}
			checkNotNull(getImpl(root, manga, onlyIfExists = false, format = targetFormat))
		}

		suspend fun get(root: File, manga: Content): LocalContentOutput? = withContext(Dispatchers.IO) {
			getImpl(root, manga, onlyIfExists = true, format = DownloadFormat.AUTOMATIC)
		}

		private suspend fun getImpl(
			root: File,
			manga: Content,
			onlyIfExists: Boolean,
			format: DownloadFormat,
		): LocalContentOutput? {
			mutex.withLock {
				var i = 0
				val baseName = manga.title.toFileNameSafe()
				while (true) {
					val fileName = if (i == 0) baseName else baseName + "_$i"
					val dir = File(root, fileName)
					val zip = File(root, "$fileName.cbz")
					i++
					return when {
						dir.isDirectory -> {
							if (canWriteTo(dir, manga)) {
								LocalContentDirOutput(dir, manga)
							} else {
								continue
							}
						}

						zip.isFile -> if (canWriteTo(zip, manga)) {
							LocalContentZipOutput(zip, manga)
						} else {
							continue
						}

						!onlyIfExists -> when (format) {
							DownloadFormat.AUTOMATIC -> null
							DownloadFormat.SINGLE_CBZ -> LocalContentZipOutput(zip, manga)
							DownloadFormat.MULTIPLE_CBZ -> LocalContentDirOutput(dir, manga)
						}

						else -> null
					}
				}
			}
		}

		private suspend fun canWriteTo(file: File, manga: Content): Boolean {
			val info = runCatchingCancellable {
				LocalContentParser(file).getContentInfo()
			}.onFailure {
				it.printStackTraceDebug()
			}.getOrNull() ?: return false
			return info.id == manga.id
		}
	}
}
