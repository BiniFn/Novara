package org.skepsun.kototoro.local.data.input

import android.net.Uri
import androidx.core.net.toFile
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import okio.openZip
import org.jetbrains.annotations.Blocking
import org.skepsun.kototoro.core.model.LocalMangaSource
import org.skepsun.kototoro.core.model.LocalNovelSource
import org.skepsun.kototoro.core.model.LocalVideoSource
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.core.util.AlphanumComparator
import org.skepsun.kototoro.core.util.MimeTypes
import org.skepsun.kototoro.core.util.ext.URI_SCHEME_ZIP
import org.skepsun.kototoro.core.util.ext.isDirectory
import org.skepsun.kototoro.core.util.ext.isFileUri
import org.skepsun.kototoro.core.util.ext.isImage
import org.skepsun.kototoro.core.util.ext.isRegularFile
import org.skepsun.kototoro.core.util.ext.isZipUri
import org.skepsun.kototoro.core.util.ext.printStackTraceDebug
import org.skepsun.kototoro.core.util.ext.toFileNameSafe
import org.skepsun.kototoro.core.util.ext.toListSorted
import org.skepsun.kototoro.local.data.ContentIndex
import org.skepsun.kototoro.local.data.hasZipExtension
import org.skepsun.kototoro.local.data.isZipArchive
import org.skepsun.kototoro.local.data.output.LocalContentOutput.Companion.ENTRY_NAME_INDEX
import org.skepsun.kototoro.local.domain.model.LocalContent
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentChapter
import org.skepsun.kototoro.parsers.model.ContentPage
import org.skepsun.kototoro.parsers.util.longHashCode
import org.skepsun.kototoro.parsers.util.runCatchingCancellable
import org.skepsun.kototoro.parsers.util.toTitleCase
import java.io.File

/**
 * Content root {dir or zip file}
 * |--- index.json (optional)
 * |--- Page 1.png
 * |--- Page 2.png
 * |---Chapter 1/(dir or zip, optional)
 * |------Page 1.1.png
 * :
 * L--- Page x.png
 */
class LocalContentParser(private val uri: Uri) {

	constructor(file: File) : this(file.toUri())

	private val rootFile: File = File(uri.schemeSpecificPart)

	suspend fun getContent(withDetails: Boolean): LocalContent = runInterruptible(Dispatchers.IO) {
		(uri.resolveFsAndPath()).use { (fileSystem, rootPath) ->
			val index = ContentIndex.read(fileSystem, rootPath / ENTRY_NAME_INDEX)
			val mangaInfo = index?.getContentInfo()
			if (mangaInfo != null) {
				val coverEntry: Path? = index.getCoverEntry()?.let { rootPath / it }?.takeIf {
					fileSystem.exists(it)
				}
					// 获取隐藏的章节ID列表
					val hiddenChapterIds = index.getHiddenChapterIds()
					
					val resolvedLocalSource = when (mangaInfo.source?.contentType) {
						ContentType.NOVEL, ContentType.HENTAI_NOVEL -> LocalNovelSource
						ContentType.VIDEO, ContentType.HENTAI_VIDEO -> LocalVideoSource
						else -> LocalMangaSource
					}

					mangaInfo.copy(
					chapters = if (withDetails) {
						mangaInfo.chapters?.mapNotNull { c ->
							// 过滤掉隐藏的章节
							if (c.id in hiddenChapterIds) {
								return@mapNotNull null
							}
							
							val fileName = index.getChapterFileName(c.id)
							val path = fileName?.toPath()
							if (path != null && fileSystem.exists(rootPath / path)) {
								// 已加载的本地章节
								c.copy(
									url = uri.child(path, resolve = false).toString()
								)
							} else {
								// 未下载的在线章节（保留原始 URL 和 Source）
								c
							}
						}
					} else {
						// 如果不需要详情，也按索引过滤出实际存在的章节，并统一来源和URL
						mangaInfo.chapters?.mapNotNull { c ->
							val fileName = index.getChapterFileName(c.id)
							val path = fileName?.toPath()
							if (path != null && fileSystem.exists(rootPath / path)) {
								c.copy(
									url = uri.child(path, resolve = false).toString()
								)
							} else {
								// 如果不需要详情，通常是列表页，保留原始章节以显示进度条等
								c
							}
						}
					},
				)
			} else {
				val title = rootFile.name.fileNameToTitle()
				var inferedSource: org.skepsun.kototoro.parsers.model.ContentSource = LocalMangaSource
				val flatFiles = fileSystem.listRecursively(rootPath).toList()
				if (flatFiles.any { it.name.endsWith(".mp4", true) || it.name.endsWith(".mkv", true) || it.name.endsWith(".ts", true) || it.name.endsWith(".webm", true) }) inferedSource = LocalVideoSource
				else if (flatFiles.any { it.name.endsWith(".epub", true) || it.name.endsWith(".txt", true) }) inferedSource = LocalNovelSource

				Content(
					id = rootFile.absolutePath.longHashCode(),
					title = title,
					url = rootFile.toUri().toString(),
					publicUrl = rootFile.toUri().toString(),
					source = inferedSource,
					coverUrl = fileSystem.findFirstImageUri(rootPath)?.toString(),
					chapters = if (withDetails) {
						var detectedSource: org.skepsun.kototoro.parsers.model.ContentSource = LocalMangaSource
						val chapters = fileSystem.listRecursively(rootPath)
							.mapNotNullTo(HashSet()) { path ->
								when {
									!fileSystem.isRegularFile(path) -> null
									path.isImage() -> path.parent
									hasZipExtension(path.name) -> path
									path.name.endsWith(".mp4", true) || path.name.endsWith(".mkv", true) || path.name.endsWith(".ts", true) || path.name.endsWith(".webm", true) -> {
										detectedSource = LocalVideoSource
										path.parent ?: Path.DIRECTORY_SEPARATOR.toPath()
									}
									path.name.endsWith(".epub", true) || path.name.endsWith(".txt", true) -> {
										detectedSource = LocalNovelSource
										path.parent ?: Path.DIRECTORY_SEPARATOR.toPath()
									}
									else -> null
								}
							}.sortedWith(compareBy(AlphanumComparator()) { x -> x.toString() })
						chapters.mapIndexed { i, p ->
							val s = if (p.root == rootPath.root) {
								p.relativeTo(rootPath).toString()
							} else {
								p
							}.toString().removePrefix(Path.DIRECTORY_SEPARATOR)
							ContentChapter(
								id = "$i$s".longHashCode(),
								title = p.userFriendlyName().takeIf { it.isNotBlank() } ?: "1",
								number = 0f,
								volume = 0,
								source = detectedSource,
								uploadDate = 0L,
								url = uri.child(p.relativeTo(rootPath), resolve = false).toString(),
								scanlator = null,
								branch = null,
							)
						}
					} else {
						null
					},
					altTitles = emptySet(),
					rating = -1f,
					contentRating = null,
					tags = emptySet(),
					state = null,
					authors = emptySet(),
					largeCoverUrl = null,
					description = null,
				)
			}.let { LocalContent(it, rootFile) }
		}
	}

	suspend fun getContentInfo(): Content? = runInterruptible(Dispatchers.IO) {
		uri.resolveFsAndPath().use { (fileSystem, rootPath) ->
			val index = ContentIndex.read(fileSystem, rootPath / ENTRY_NAME_INDEX)
			index?.getContentInfo()
		}
	}

	suspend fun getPages(chapter: ContentChapter): List<ContentPage> = runInterruptible(Dispatchers.IO) {
		val chapterUri = chapter.url.toUri().resolve()
		chapterUri.resolveFsAndPath().use { (fileSystem, rootPath) ->
			if (fileSystem.metadataOrNull(rootPath)?.isDirectory != true) {
				return@runInterruptible listOf(
					ContentPage(
						id = chapterUri.toString().longHashCode(),
						url = chapterUri.toString(),
						preview = null,
						source = chapter.source ?: LocalMangaSource,
					)
				)
			}
			val index = ContentIndex.read(fileSystem, rootPath / ENTRY_NAME_INDEX)
			val entries = fileSystem.listRecursively(rootPath)
				.filter { fileSystem.isRegularFile(it) }
			if (index != null) {
				val pattern = index.getChapterNamesPattern(chapter)
				entries.filter { x -> x.name.substringBefore('.').matches(pattern) }
			} else {
				entries.filter { x -> (x.isImage() || x.name.endsWith(".html", ignoreCase = true) || x.name.endsWith(".xhtml", ignoreCase = true)) && x.parent == rootPath }
			}.toListSorted(compareBy(AlphanumComparator()) { x -> x.toString() })
				.map { x ->
					val entryUri = chapterUri.child(x, resolve = true).toString()
					ContentPage(
						id = entryUri.longHashCode(),
						url = entryUri,
						preview = null,
						source = chapter.source ?: LocalMangaSource,
					)
				}
		}
	}

	private fun Uri.child(path: Path, resolve: Boolean): Uri {
		val file = fileFromPath()
		val builder = buildUpon()
		val isZip = isZipUri() || file.isZipArchive
		if (isZip) {
			builder.scheme(URI_SCHEME_ZIP)
		}
		if (isZip || !resolve) {
			builder.fragment(path.toString().removePrefix(Path.DIRECTORY_SEPARATOR))
		} else {
			builder.appendEncodedPath(path.relativeTo(file.toOkioPath()).toString())
		}
		return builder.build()
	}

	private fun FileSystem.findFirstImageUri(
		rootPath: Path,
		recursive: Boolean = false
	): Uri? = runCatchingCancellable {
		val list = list(rootPath)
		for (file in list.sortedWith(compareBy(AlphanumComparator()) { x -> x.name })) {
			if (isRegularFile(file)) {
				if (file.isImage()) {
					return@runCatchingCancellable uri.child(file, resolve = true)
				}
				if (recursive && file.isZip()) {
					openZip(file).use { zipFs ->
						zipFs.findFirstImageUri(Path.DIRECTORY_SEPARATOR.toPath())?.let { subUri ->
							val subPath = subUri.path.orEmpty().removePrefix(uri.path.orEmpty())
								.replace(REGEX_PARENT_PATH_PREFIX, "")
							return@runCatchingCancellable uri.child(file, resolve = true)
								.child(subPath.toPath(), resolve = false)
						}
					}
				}
			} else if (recursive && isDirectory(file)) {
				findFirstImageUri(file, true)?.let {
					return@runCatchingCancellable it
				}
			}
		}
		if (recursive) {
			null
		} else {
			findFirstImageUri(rootPath, recursive = true)
		}
	}.onFailure { e ->
		e.printStackTraceDebug()
	}.getOrNull()

	private fun Path.userFriendlyName(): String = name.substringBeforeLast('.')
		.replace('_', ' ')
		.toTitleCase()

	private class FsAndPath(
		val fileSystem: FileSystem,
		val path: Path,
		private val isCloseable: Boolean,
	) : AutoCloseable {

		override fun close() {
			if (isCloseable) {
				fileSystem.close()
			}
		}

		operator fun component1() = fileSystem

		operator fun component2() = path
	}

	companion object {

		private val REGEX_PARENT_PATH_PREFIX = Regex("^(/\\.\\.)+")

		@Blocking
		fun getOrNull(file: File): LocalContentParser? = if ((file.isDirectory || file.isZipArchive) && file.canRead()) {
			LocalContentParser(file)
		} else {
			null
		}

		suspend fun find(roots: Iterable<File>, manga: Content): LocalContentParser? = channelFlow {
			val fileName = manga.title.toFileNameSafe()
			val idFileName = "${manga.id}_$fileName"
			for (root in roots) {
				launch {
					val parser = getOrNull(File(root, fileName)) 
						?: getOrNull(File(root, "$fileName.cbz"))
						?: getOrNull(File(root, idFileName))
						?: getOrNull(File(root, "$idFileName.cbz"))
					val info = runCatchingCancellable { parser?.getContentInfo() }.getOrNull()
					if (info?.id == manga.id) {
						send(parser)
					} else if (parser != null && root.name == manga.title.toFileNameSafe()) {
						send(parser)
					}
				}
			}
		}.flowOn(Dispatchers.Default).firstOrNull()

		private fun Path.isImage(): Boolean = MimeTypes.getMimeTypeFromExtension(name)?.isImage == true

		private fun Path.isZip(): Boolean = hasZipExtension(name)

		private fun Uri.resolve(): Uri = if (isFileUri()) {
			val file = toFile()
			if (file.isZipArchive) {
				this
			} else if (file.isDirectory) {
				file.resolve(fragment.orEmpty()).toUri()
			} else {
				this
			}
		} else {
			this
		}

		private fun Uri.fileFromPath(): File = File(requireNotNull(path) { "Uri path is null: $this" })

		@Blocking
		private fun Uri.resolveFsAndPath(): FsAndPath {
			val resolved = resolve()
			return when {
				resolved.isZipUri() -> FsAndPath(
					FileSystem.SYSTEM.openZip(resolved.schemeSpecificPart.toPath()),
					resolved.fragment.orEmpty().toRootedPath(),
					isCloseable = true,
				)

				isFileUri() -> {
					val file = toFile()
					if (file.isZipArchive) {
						FsAndPath(
							FileSystem.SYSTEM.openZip(schemeSpecificPart.toPath()),
							fragment.orEmpty().toRootedPath(),
							isCloseable = true,
						)
					} else {
						FsAndPath(FileSystem.SYSTEM, file.toOkioPath(), isCloseable = false)
					}
				}

				else -> throw IllegalArgumentException("Unsupported uri $resolved")
			}
		}

		private fun String.toRootedPath(): Path = if (startsWith(Path.DIRECTORY_SEPARATOR)) {
			this
		} else {
			Path.DIRECTORY_SEPARATOR + this
		}.toPath()

		private fun String.fileNameToTitle() = substringBeforeLast('.')
			.replace('_', ' ')
			.replaceFirstChar { it.uppercase() }
	}
}
