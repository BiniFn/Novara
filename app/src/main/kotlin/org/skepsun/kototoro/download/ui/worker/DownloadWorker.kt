package org.skepsun.kototoro.download.ui.worker

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.hilt.work.WorkerAssistedFactory
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import dagger.Reusable
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.assisted.AssistedFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.internal.closeQuietly
import okio.IOException
import okio.buffer
import okio.sink
import okio.use
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.image.BitmapDecoderCompat
import org.skepsun.kototoro.core.model.ids
import org.skepsun.kototoro.core.model.isLocal
import org.skepsun.kototoro.core.network.MangaHttpClient
import org.skepsun.kototoro.core.network.imageproxy.ImageProxyInterceptor
import org.skepsun.kototoro.core.parser.MangaDataRepository
import org.skepsun.kototoro.core.parser.MangaRepository
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.DownloadFormat
import org.skepsun.kototoro.core.util.MimeTypes
import org.skepsun.kototoro.core.util.Throttler
import org.skepsun.kototoro.core.util.ext.MimeType
import org.skepsun.kototoro.core.util.ext.awaitFinishedWorkInfosByTag
import org.skepsun.kototoro.core.util.ext.awaitUpdateWork
import org.skepsun.kototoro.core.util.ext.awaitWorkInfosByTag
import org.skepsun.kototoro.core.util.ext.deleteAwait
import org.skepsun.kototoro.core.util.ext.deleteWork
import org.skepsun.kototoro.core.util.ext.deleteWorks
import org.skepsun.kototoro.core.util.ext.ensureSuccess
import org.skepsun.kototoro.core.util.ext.getDisplayMessage
import org.skepsun.kototoro.core.util.ext.getWorkInputData
import org.skepsun.kototoro.core.util.ext.getWorkSpec
import org.skepsun.kototoro.core.util.ext.openSource
import org.skepsun.kototoro.core.util.ext.printStackTraceDebug
import org.skepsun.kototoro.core.util.ext.toFileOrNull
import org.skepsun.kototoro.core.util.ext.toMimeType
import org.skepsun.kototoro.core.util.ext.toMimeTypeOrNull
import org.skepsun.kototoro.core.util.ext.withTicker
import org.skepsun.kototoro.core.util.ext.writeAllCancellable
import org.skepsun.kototoro.core.util.progress.RealtimeEtaEstimator
import org.skepsun.kototoro.core.model.unwrap
import org.skepsun.kototoro.download.domain.DownloadProgress
import org.skepsun.kototoro.download.domain.DownloadState
import org.skepsun.kototoro.local.data.LocalMangaRepository
import org.skepsun.kototoro.local.data.LocalStorageCache
import org.skepsun.kototoro.local.data.LocalStorageChanges
import org.skepsun.kototoro.local.data.PageCache
import org.skepsun.kototoro.local.data.TempFileFilter
import org.skepsun.kototoro.local.data.input.LocalMangaParser
import org.skepsun.kototoro.local.data.output.LocalMangaOutput
import org.skepsun.kototoro.local.data.output.LocalMangaDirOutput
import org.skepsun.kototoro.local.domain.MangaLock
import org.skepsun.kototoro.local.domain.model.LocalManga
import org.skepsun.kototoro.parsers.exception.TooManyRequestExceptions
import org.skepsun.kototoro.parsers.model.Manga
import org.skepsun.kototoro.parsers.model.MangaChapter
import org.skepsun.kototoro.parsers.model.MangaPage
import org.skepsun.kototoro.parsers.model.MangaSource
import org.skepsun.kototoro.parsers.model.NovelChapterContent
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.parsers.model.MangaParserSource
import org.skepsun.kototoro.parsers.util.ifNullOrEmpty
import org.skepsun.kototoro.parsers.util.mapToSet
import org.skepsun.kototoro.parsers.util.requireBody
import org.skepsun.kototoro.parsers.util.await
import org.skepsun.kototoro.parsers.util.runCatchingCancellable
import org.skepsun.kototoro.reader.domain.PageLoader
import org.jsoup.Jsoup
import java.io.File
import java.net.URLDecoder
import java.util.UUID
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

@HiltWorker
class DownloadWorker @AssistedInject constructor(
	@Assisted appContext: Context,
	@Assisted params: WorkerParameters,
	@MangaHttpClient private val okHttp: OkHttpClient,
	@PageCache private val cache: LocalStorageCache,
	private val localMangaRepository: LocalMangaRepository,
	private val mangaLock: MangaLock,
	private val mangaDataRepository: MangaDataRepository,
	private val mangaRepositoryFactory: MangaRepository.Factory,
	private val settings: AppSettings,
	@LocalStorageChanges private val localStorageChanges: MutableSharedFlow<LocalManga?>,
	private val slowdownDispatcher: DownloadSlowdownDispatcher,
	private val imageProxyInterceptor: ImageProxyInterceptor,
	notificationFactoryFactory: DownloadNotificationFactory.Factory,
	private val mangaDatabase: org.skepsun.kototoro.core.db.MangaDatabase,
	private val epubStorageManager: org.skepsun.kototoro.local.epub.EpubStorageManager,
	private val localStorageManager: org.skepsun.kototoro.local.data.LocalStorageManager,
) : CoroutineWorker(appContext, params) {

	private val task = DownloadTask(params.inputData)
	private val notificationFactory = notificationFactoryFactory.create(uuid = params.id, isSilent = task.isSilent)
	private val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

	@Volatile
	private var lastPublishedState: DownloadState? = null
	private val currentState: DownloadState
		get() = checkNotNull(lastPublishedState)

	private val etaEstimator = RealtimeEtaEstimator()
	private val notificationThrottler = Throttler(400)

	override suspend fun doWork(): Result = withContext(org.skepsun.kototoro.core.parser.legado.RequestPriority(org.skepsun.kototoro.core.parser.legado.RequestPriority.BACKGROUND)) {
		setForeground(getForegroundInfo())
		val manga = mangaDataRepository.findMangaById(task.mangaId, withChapters = true) ?: return@withContext Result.failure()
		publishState(DownloadState(manga = manga, isIndeterminate = true).also { lastPublishedState = it })
		val downloadedIds = getDoneChapters(manga)
		try {
			val pausingHandle = PausingHandle()
			if (task.isPaused) {
				pausingHandle.pause()
			}
			withContext(pausingHandle) {
				downloadMangaImpl(manga, task, downloadedIds)
			}
			Result.success(currentState.toWorkData())
		} catch (_: CancellationException) {
			withContext(NonCancellable) {
				val notification = notificationFactory.create(currentState.copy(isStopped = true))
				notificationManager.notify(id.hashCode(), notification)
			}
			Result.failure(
				currentState.copy(eta = -1L, isStuck = false).toWorkData(),
			)
		} catch (e: Exception) {
			e.printStackTraceDebug()
			Result.failure(
				currentState.copy(
					error = e,
					errorMessage = e.getDisplayMessage(applicationContext.resources),
					eta = -1L,
					isStuck = false,
				).toWorkData(),
			)
		} finally {
			notificationManager.cancel(id.hashCode())
		}
	}

	override suspend fun getForegroundInfo() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
		ForegroundInfo(
			id.hashCode(),
			notificationFactory.create(lastPublishedState),
			ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
		)
	} else {
		ForegroundInfo(
			id.hashCode(),
			notificationFactory.create(lastPublishedState),
		)
	}

	private suspend fun downloadMangaImpl(
		subject: Manga,
		task: DownloadTask,
		excludedIds: Set<Long>,
	) {
		var manga = subject
		val chaptersToSkip = excludedIds.toMutableSet()
		val pausingReceiver = PausingReceiver(id, PausingHandle.current())
		mangaLock.withLock(manga) {
			ContextCompat.registerReceiver(
				applicationContext,
				pausingReceiver,
				PausingReceiver.createIntentFilter(id),
				ContextCompat.RECEIVER_NOT_EXPORTED,
			)
			var destination = localMangaRepository.getOutputDir(manga, task.destination)
			checkNotNull(destination) { applicationContext.getString(R.string.cannot_find_available_storage) }
			var output: LocalMangaOutput? = null
			try {
				if (manga.isLocal) {
					manga = localMangaRepository.getRemoteManga(manga)
						?: error("Cannot obtain remote manga instance")
				}
				val repo = mangaRepositoryFactory.create(manga.source)
				val mangaDetails = if (manga.chapters.isNullOrEmpty() || manga.description.isNullOrEmpty()) repo.getDetails(manga) else manga
				val parserSource = mangaDetails.source.unwrap() as? MangaParserSource
				val isNovel = when (parserSource?.contentType) {
					ContentType.NOVEL, ContentType.HENTAI_NOVEL -> true
					else -> false
				} || mangaDetails.source.name.uppercase() in setOf("BILINOVEL", "LKNOVEL_US", "LIGHTNOVEL_WIKI", "NOVELIA", "WENKU8", "BIQUGE")
				
				// 检测是否包含EPUB章节
				val hasEpubChapters = runCatchingCancellable {
					val chaptersToCheck = getChapters(mangaDetails, task)
					chaptersToCheck.any { chapter ->
						val pages = repo.getPages(chapter.value)
						pages.size == 1 && pages[0].preview == "EPUB"
					}
				}.getOrNull() ?: false
				
				// 如果包含EPUB章节，强制使用MULTIPLE_CBZ格式
				val downloadFormat = if (hasEpubChapters) {
					println("DownloadWorker: Detected EPUB chapters, using MULTIPLE_CBZ format")
					android.util.Log.i("DownloadWorker", "Detected EPUB chapters, automatically using MULTIPLE_CBZ format for proper chapter extraction")
					DownloadFormat.MULTIPLE_CBZ
				} else {
					task.format ?: settings.preferredDownloadFormat
				}

				if (isNovel && !hasEpubChapters) {
					// 尝试获取小说专用的输出目录
					destination = localStorageManager.getDefaultNovelWriteableDir() ?: localStorageManager.getNovelWriteableDirs().firstOrNull() ?: destination
				}

				output = LocalMangaOutput.getOrCreate(
					root = destination,
					manga = mangaDetails,
					format = downloadFormat,
				)
				val coverUrl = mangaDetails.largeCoverUrl.ifNullOrEmpty { mangaDetails.coverUrl }
				if (!coverUrl.isNullOrEmpty()) {
					downloadFile(repo, coverUrl, destination, isCover = true).let { file ->
						output.addCover(file, getMediaType(coverUrl, file))
						file.deleteAwait()
					}
				}
				if (isNovel && !hasEpubChapters) {
					downloadNovelChapters(mangaDetails, task, repo, destination, output, chaptersToSkip)
					output.mergeWithExisting()
					output.finish()
					val localManga = LocalMangaParser(output.rootFile).getManga(withDetails = true)
					// 刷新缓存，确保 UI 能识别到本地 icon
					localMangaRepository.findSavedManga(mangaDetails)
					android.util.Log.d("DownloadWorker", "Novel download completed, emitting localStorageChanges for ${output.rootFile}")
					localStorageChanges.emit(localManga)
					publishState(currentState.copy(localManga = localManga, eta = -1L, isStuck = false))
					return@withLock
				}
				processStandardChapters(mangaDetails, task, repo, destination, chaptersToSkip, output)
				publishState(currentState.copy(isIndeterminate = true, eta = -1L, isStuck = false))
				output.mergeWithExisting()
				output.finish()
				val localManga = LocalMangaParser(output.rootFile).getManga(withDetails = true)
				// 刷新缓存
				localMangaRepository.findSavedManga(mangaDetails)
				localStorageChanges.emit(localManga)
				publishState(currentState.copy(localManga = localManga, eta = -1L, isStuck = false))
			} catch (e: Exception) {
				if (e !is CancellationException) {
					publishState(
						currentState.copy(
							error = e,
							errorMessage = e.getDisplayMessage(applicationContext.resources),
						),
					)
				}
				throw e
			} finally {
				withContext(NonCancellable) {
					applicationContext.unregisterReceiver(pausingReceiver)
					output?.closeQuietly()
					output?.cleanup()
					val tempFiles = destination.listFiles(TempFileFilter())
					if (tempFiles != null) {
						for (file in tempFiles) {
							runCatchingCancellable { file.deleteAwait() }
						}
					}
				}
			}
		}
	}

	private suspend fun processStandardChapters(
		mangaDetails: Manga,
		task: DownloadTask,
		repo: MangaRepository,
		destination: File,
		chaptersToSkip: MutableSet<Long>,
		output: LocalMangaOutput,
	) {
		val chapters = getChapters(mangaDetails, task)
		for ((chapterIndex, chapter) in chapters.withIndex()) {
			checkIsPaused()

			val pages = runFailsafe {
				repo.getPages(chapter.value)
			} ?: continue

			println("DownloadWorker: Chapter ${chapter.index}: ${chapter.value.title}")
			println("DownloadWorker: Pages count: ${pages.size}")
			if (pages.isNotEmpty()) {
				println("DownloadWorker: First page preview: ${pages[0].preview}")
				println("DownloadWorker: First page url: ${pages[0].url}")
			}

			val isEpubChapter = pages.size == 1 && pages[0].preview == "EPUB"
			if (!isEpubChapter && chaptersToSkip.remove(chapter.value.id)) {
				println("DownloadWorker: Skipping already downloaded chapter")
				publishState(currentState.copy(downloadedChapters = currentState.downloadedChapters + 1))
				continue
			}

			if (isEpubChapter) {
				println("DownloadWorker: EPUB detected! Using NEW ARCHITECTURE")
				android.util.Log.i("DownloadWorker", "EPUB chapter detected, using new LocalEpubSource architecture")
				chaptersToSkip.remove(chapter.value.id)

				// Publish initial progress for EPUB download
				publishState(currentState.copy(
					totalChapters = chapters.size,
					currentChapter = chapterIndex,
					totalPages = 1,
					currentPage = 0,
					isIndeterminate = false
				))

				val result = runFailsafe {
					downloadEpubToStorage(
						manga = mangaDetails,
						chapter = chapter,
						epubUrl = pages[0].url,
						destination = destination,
						repo = repo,
					)
					true
				}
				if (result == true) {
					publishState(currentState.copy(
						downloadedChapters = currentState.downloadedChapters + 1,
						currentChapter = chapterIndex + 1,
						currentPage = 1
					))
					android.util.Log.i("DownloadWorker", "EPUB downloaded successfully to epub storage")

					runCatchingCancellable {
						val epubDir = epubStorageManager.getEpubDir(mangaDetails.id)
						val epubFileName = "chapter_${chapter.value.id}.epub"
						val epubFile = File(epubDir, epubFileName)

						if (!epubFile.exists()) {
							android.util.Log.e("DownloadWorker", "EPUB file not found: ${epubFile.absolutePath}")
							return@runCatchingCancellable
						}

						val parser = org.skepsun.kototoro.local.epub.LocalEpubParser(epubFile)
						val epubContent = parser.parseManga() ?: run {
							android.util.Log.e("DownloadWorker", "Failed to parse EPUB file")
							return@runCatchingCancellable
						}

						android.util.Log.d("DownloadWorker", "Parsed ${epubContent.chapters?.size} chapters from EPUB")

						val epubChapterMappingDao = mangaDatabase.getEpubChapterMappingDao()
						for ((index, epubChapter) in epubContent.chapters.orEmpty().withIndex()) {
							val internalChapterId = chapter.value.id + (index * 1000000L) + 1

							val mapping = org.skepsun.kototoro.core.db.entity.EpubChapterMappingEntity(
								internalChapterId = internalChapterId,
								parentChapterId = chapter.value.id,
								epubFilePath = epubFile.absolutePath,
								epubFileName = chapter.value.title ?: epubFileName,
								chapterIndex = index,
								chapterTitle = epubChapter.title ?: "Chapter ${index + 1}",
							)
							epubChapterMappingDao.insert(mapping)
						}

						android.util.Log.i("DownloadWorker", "EPUB chapters parsed and saved to database: ${epubContent.chapters?.size} chapters")
						android.util.Log.i("DownloadWorker", "EPUB file saved at: ${epubFile.absolutePath}")
						
						// Notify UI about the new local chapters
						localStorageChanges.emit(LocalMangaParser(output.rootFile).getManga(withDetails = false))
					}.onFailure { e ->
						android.util.Log.e("DownloadWorker", "Failed to parse EPUB chapters", e)
						e.printStackTrace()
					}
				}
				continue
			} else {
				println("DownloadWorker: Not EPUB, using normal download")
			}

			val pageCounter = AtomicInteger(0)
			channelFlow {
				val semaphore = Semaphore(MAX_PAGES_PARALLELISM)
				for ((pageIndex, page) in pages.withIndex()) {
					checkIsPaused()
					launch {
						semaphore.withPermit {
							runFailsafe {
								val url = repo.getPageUrl(page)
								val file = cache[url]
									?: downloadFile(repo, url, destination, page = page)
								output.addPage(
									chapter = chapter,
									file = file,
									pageNumber = pageIndex,
									type = getMediaType(url, file),
								)
								if (file.extension == "tmp") {
									file.deleteAwait()
								}
							}
							send(pageIndex)
						}
					}
				}
			}.map {
				DownloadProgress(
					totalChapters = chapters.size,
					currentChapter = chapterIndex,
					totalPages = pages.size,
					currentPage = pageCounter.getAndIncrement(),
				)
			}.withTicker(2L, TimeUnit.SECONDS).collect { progress ->
				publishState(
					currentState.copy(
						totalChapters = progress.totalChapters,
						currentChapter = progress.currentChapter,
						totalPages = progress.totalPages,
						currentPage = progress.currentPage,
						isIndeterminate = false,
						eta = etaEstimator.getEta(),
						isStuck = etaEstimator.isStuck(),
					),
				)
			}
			if (output.flushChapter(chapter.value)) {
				runCatchingCancellable {
					localStorageChanges.emit(LocalMangaParser(output.rootFile).getManga(withDetails = false))
				}.onFailure(Throwable::printStackTraceDebug)
			}
			publishState(currentState.copy(downloadedChapters = currentState.downloadedChapters + 1))
		}
	}

	private suspend fun <R> runFailsafe(
		block: suspend () -> R,
	): R? {
		checkIsPaused()
		var countDown = MAX_FAILSAFE_ATTEMPTS
		failsafe@ while (true) {
			try {
				return block()
			} catch (e: IOException) {
				val retryDelay = if (e is TooManyRequestExceptions) {
					e.getRetryDelay()
				} else {
					DOWNLOAD_ERROR_DELAY
				}
				if (countDown <= 0 || retryDelay < 0 || retryDelay > MAX_RETRY_DELAY) {
					val pausingHandle = PausingHandle.current()
					if (pausingHandle.skipAllErrors()) {
						return null
					}
					publishState(
						currentState.copy(
							isPaused = true,
							error = e,
							errorMessage = e.getDisplayMessage(applicationContext.resources),
							eta = -1L,
							isStuck = false,
						),
					)
					countDown = MAX_FAILSAFE_ATTEMPTS
					pausingHandle.pause()
					try {
						pausingHandle.awaitResumed()
						if (pausingHandle.skipCurrentError()) {
							return null
						}
					} finally {
						publishState(currentState.copy(isPaused = false, error = null, errorMessage = null))
					}
				} else {
					countDown--
					delay(retryDelay)
				}
			}
		}
	}

	private suspend fun checkIsPaused() {
		val pausingHandle = PausingHandle.current()
		if (pausingHandle.isPaused) {
			publishState(currentState.copy(isPaused = true, eta = -1L, isStuck = false))
			try {
				pausingHandle.awaitResumed()
			} finally {
				publishState(currentState.copy(isPaused = false))
			}
		}
	}

	private suspend fun getMediaType(url: String, file: File): MimeType? = runInterruptible(Dispatchers.IO) {
		BitmapDecoderCompat.probeMimeType(file)?.let {
			return@runInterruptible it
		}
		MimeTypes.getMimeTypeFromUrl(url)
	}

	/**
	 * 小说章节下载：复用漫画的输出格式（单本/多本 CBZ），章节内写入 HTML + 插图。
	 */
	private suspend fun downloadNovelChapters(
		manga: Manga,
		task: DownloadTask,
		repo: MangaRepository,
		destination: File,
		output: LocalMangaOutput,
		chaptersToSkip: MutableSet<Long>,
	) {
			val chapters = getChapters(manga, task)

		for ((chapterIndex, chapter) in chapters.withIndex()) {
			checkIsPaused()
			if (chaptersToSkip.remove(chapter.value.id)) {
				publishState(currentState.copy(downloadedChapters = currentState.downloadedChapters + 1))
				continue
			}

			val content = runFailsafe { repo.getChapterContent(chapter.value) }
				?: runFailsafe { decodeDataPage(repo.getPages(chapter.value).firstOrNull()) }
				?: run {
					android.util.Log.w("DownloadWorker", "downloadNovelChapters: skip chapter ${chapter.value.title} (no content)")
					continue
				}

			val imageHeaderMap = LinkedHashMap<String, Map<String, String>>()
			content.images.forEach { imageHeaderMap[it.url] = it.headers }
			runCatching {
				val parsed = Jsoup.parse(content.html)
				parsed.select("img").forEach { img ->
					val src = img.attr("data-src").ifBlank { img.attr("src") }.trim()
					if (src.isNotBlank() && !src.startsWith("data:", true)) {
						imageHeaderMap.putIfAbsent(src, emptyMap())
					}
				}
			}

			val nameMap = LinkedHashMap<String, ImageDownload>()
			var pageNumber = 1
			imageHeaderMap.entries.forEach { entry ->
				val originalUrl = entry.key
				if (originalUrl.startsWith("data:", ignoreCase = true) || originalUrl.startsWith("file:", ignoreCase = true)) {
					return@forEach
				}
				val ext = MimeTypes.getNormalizedExtension(originalUrl.substringAfterLast('/').substringBefore('?'))?.ifBlank { "jpg" } ?: "jpg"
				val name = buildPageName(chapter, pageNumber, ext)
				nameMap[originalUrl] = ImageDownload(
					url = originalUrl,
					headers = entry.value,
					name = name,
					pageNumber = pageNumber,
					mime = MimeTypes.getMimeTypeFromExtension(ext),
				)
				pageNumber++
			}

				val rewrittenHtml = rewriteHtmlWithCustomNames(content.html, nameMap.mapValues { it.value.name })
				val htmlFile = destination.createTempFile("html").apply {
					writeText(rewrittenHtml)
				}
				val htmlName = buildPageName(chapter, 0, "html")
				output.addPage(
					chapter = chapter,
					file = htmlFile,
					pageNumber = 0,
					type = "text/html".toMimeTypeOrNull(),
				)

			val totalImages = nameMap.size
			val normalizedTotal = 100
			
			// 初始章节进度：设为 1% 以显示已开始
			publishState(currentState.copy(
				totalChapters = chapters.size,
				currentChapter = chapterIndex,
				totalPages = normalizedTotal,
				currentPage = 1,
				isIndeterminate = false,
				eta = etaEstimator.getEta(),
				isStuck = etaEstimator.isStuck(),
			))

			nameMap.values.forEachIndexed { imageIndex, download ->
				val headers = download.headers.toMutableMap()
				if (headers.none { it.key.equals("referer", ignoreCase = true) }) {
					headers["Referer"] = deriveReferer(download.url, manga)
				}
				runCatching {
					val file = downloadFile(
						repo = repo,
						url = download.url,
						destination = destination,
						headers = headers,
					)
					val type = download.mime ?: getMediaType(download.url, file)
					output.addPage(
						chapter = chapter,
						file = file,
						pageNumber = download.pageNumber,
						type = type,
					)
					if (file.extension == "tmp") file.deleteAwait()
					
					// 归一化当前进度
					val imageProgress = ((imageIndex + 1).toFloat() / totalImages * normalizedTotal).toInt().coerceIn(1, normalizedTotal)
					publishState(currentState.copy(
						totalChapters = chapters.size,
						currentChapter = chapterIndex,
						totalPages = normalizedTotal,
						currentPage = imageProgress,
						eta = etaEstimator.getEta(),
						isStuck = etaEstimator.isStuck(),
					))
				}.onFailure {
					android.util.Log.w("DownloadWorker", "downloadNovelChapters: image download failed ${it.message}")
				}
			}

			val mapping = nameMap.mapValues { it.value.name }
			output.putChapterImages(chapter.value.id, mapping)
			if (output.flushChapter(chapter.value)) {
				runCatchingCancellable {
					localStorageChanges.emit(LocalMangaParser(output.rootFile).getManga(withDetails = false))
				}.onFailure(Throwable::printStackTraceDebug)
			}

			publishState(currentState.copy(
				downloadedChapters = currentState.downloadedChapters + 1,
				currentChapter = chapterIndex + 1,
				currentPage = 0
			))

			// Apply delay between chapters if configured (to avoid rate limiting)
			val delaySeconds = settings.downloadChapterDelay
			if (delaySeconds > 0 && chapterIndex < chapters.size - 1) {
				// Only delay if not the last chapter
				kotlinx.coroutines.delay(delaySeconds * 1000L)
			}
		}
	}

	private fun buildPageName(chapter: IndexedValue<MangaChapter>, pageNumber: Int, ext: String): String {
		val branchHash = chapter.value.branch?.hashCode() ?: 0
		return buildString {
			append(PAGE_NAME_PATTERN.format(branchHash, chapter.index + 1, pageNumber))
			if (ext.isNotBlank()) {
				append('.')
				append(ext)
			}
		}
	}

	private fun rewriteHtmlWithCustomNames(html: String, nameMap: Map<String, String>): String {
		if (nameMap.isEmpty()) return html
		return runCatching {
			val doc = Jsoup.parse(html)
			doc.select("img").forEach { img ->
				val src = (img.attr("data-src").ifBlank { img.attr("src") }).trim()
				val local = nameMap[src]
				if (local != null) {
					img.attr("src", local)
					img.attr("referrerpolicy", "no-referrer")
				}
			}
			doc.outerHtml()
		}.getOrDefault(html)
	}

	private data class ImageDownload(
		val url: String,
		val headers: Map<String, String>,
		val name: String,
		val pageNumber: Int,
		val mime: MimeType?,
	)

	private fun decodeDataPage(page: MangaPage?): NovelChapterContent? {
		if (page == null) return null
		val url = page.url
		if (!url.startsWith("data:", ignoreCase = true)) return null
		val data = url.removePrefix("data:")
		val commaIndex = data.indexOf(',')
		if (commaIndex <= 0) return null
		val meta = data.substring(0, commaIndex)
		val contentPart = data.substring(commaIndex + 1)
		val isBase64 = meta.contains(";base64", ignoreCase = true)
		val html = if (isBase64) {
			String(Base64.getDecoder().decode(contentPart), Charsets.UTF_8)
		} else {
			URLDecoder.decode(contentPart, "UTF-8")
		}
		return NovelChapterContent(html = html, images = emptyList())
	}

	private fun deriveReferer(url: String, manga: Manga): String {
		return runCatching {
			val uri = java.net.URI(url)
			val scheme = if (uri.scheme.isNullOrBlank()) "https" else uri.scheme
			val host = uri.host ?: return@runCatching manga.publicUrl
			"$scheme://$host/"
		}.getOrElse { manga.publicUrl }
	}

	private suspend fun downloadFile(
		repo: MangaRepository,
		url: String,
		destination: File,
		useProxy: Boolean = true,
		headers: Map<String, String> = emptyMap(),
		page: MangaPage? = null,
		isCover: Boolean = false,
	): File {
		if (url.startsWith("data:", ignoreCase = true)) {
			val data = url.removePrefix("data:")
			val commaIndex = data.indexOf(',')
			require(commaIndex >= 0) { "Invalid data URL: missing comma separator" }
			val meta = data.substring(0, commaIndex)
			val contentPart = data.substring(commaIndex + 1)
			val isBase64 = meta.contains(";base64", ignoreCase = true)
			val mimeType = meta.substringBefore(';').takeIf { it.isNotBlank() }?.toMimeTypeOrNull()
			val ext = MimeTypes.getExtension(mimeType)
			val bytes = if (isBase64) {
				Base64.getDecoder().decode(contentPart)
			} else {
				URLDecoder.decode(contentPart, "UTF-8").toByteArray(Charsets.UTF_8)
			}
			val file = destination.createTempFile(ext)
			file.sink(append = false).buffer().use { sink ->
				sink.write(bytes)
			}
			return file
		}
		if (url.startsWith("content:", ignoreCase = true) || url.startsWith("file:", ignoreCase = true)) {
			val uri = url.toUri()
			val cr = applicationContext.contentResolver
			val ext = uri.toFileOrNull()?.let {
				MimeTypes.getNormalizedExtension(it.name)
			} ?: cr.getType(uri)?.toMimeTypeOrNull()?.let { MimeTypes.getExtension(it) }
			val file = destination.createTempFile(ext)
			try {
				cr.openSource(uri).use { input ->
					file.sink(append = false).buffer().use {
						it.writeAllCancellable(input)
					}
				}
			} catch (e: Exception) {
				file.delete()
				throw e
			}
			return file
		}

		val request = when {
			page != null -> repo.createPageRequest(url, page)
			isCover -> repo.createCoverRequest(url)
			else -> org.skepsun.kototoro.reader.domain.PageLoader.createPageRequest(url, repo.source)
		}

		val requestBuilder = request.newBuilder()
		headers.forEach { (k, v) -> requestBuilder.header(k, v) }
		val finalRequest = requestBuilder.build()

		slowdownDispatcher.delay(repo.source)
		val response = if (useProxy) {
			imageProxyInterceptor.interceptPageRequest(finalRequest, okHttp)
		} else {
			okHttp.newCall(finalRequest).await()
		}
		return response
			.ensureSuccess()
			.use { response ->
				var file: File? = null
				try {
					val body = response.body ?: error("Response body is null")
					body.use {
						file = destination.createTempFile(
							ext = MimeTypes.getExtension(body.contentType()?.toMimeType())
						)
						file.sink(append = false).buffer().use { sink ->
							sink.writeAllCancellable(body.source())
						}
					}
				} catch (e: Exception) {
					file?.delete()
					throw e
				}
				checkNotNull(file)
			}
	}

	private fun File.createTempFile(ext: String?): File {
		// Ensure parent directory exists
		if (!exists()) {
			mkdirs()
		}
		return File(
			this,
			buildString {
				append(UUID.randomUUID().toString())
				if (!ext.isNullOrEmpty()) {
					append('.')
					append(ext)
				}
				append(".tmp")
			},
		)
	}

	/**
	 * 下载EPUB章节
	 * 
	 * EPUB本质上是ZIP格式，保存为.epub文件以符合标准
	 * 
	 * 特殊处理：
	 * - 对于LocalMangaDirOutput：使用addEpubChapter直接保存EPUB
	 * - 对于LocalMangaZipOutput：会导致ZIP嵌套（暂不支持）
	 * 
	 * Requirements: 1.1, 1.2, 1.3, 1.4
	 * - 1.1: Save with .epub extension
	 * - 1.2: Preserve EPUB format without converting to CBZ
	 * - 1.3: Store in dedicated EPUB directory
	 * - 1.4: Generate unique filename using parent chapter ID
	 */
	private suspend fun downloadEpubChapter(
		chapter: IndexedValue<MangaChapter>,
		epubUrl: String,
		output: LocalMangaOutput,
		destination: File,
		repo: MangaRepository,
	) {
		println("DownloadWorker.downloadEpubChapter: Starting EPUB download")
		println("DownloadWorker.downloadEpubChapter: URL = $epubUrl")
		println("DownloadWorker.downloadEpubChapter: Destination = ${destination.absolutePath}")
		
		// 下载EPUB文件到临时位置
		val tempFile = try {
			println("DownloadWorker.downloadEpubChapter: Calling downloadFile...")
			val file = downloadFile(repo, epubUrl, destination, useProxy = false)
			println("DownloadWorker.downloadEpubChapter: Downloaded to ${file.absolutePath}, size=${file.length()} bytes")
			file
		} catch (e: Exception) {
			println("DownloadWorker.downloadEpubChapter: Download failed - ${e.javaClass.simpleName}: ${e.message}")
			e.printStackTrace()
			throw e
		}
		
		try {
			// 验证文件是否真的是EPUB/ZIP
			if (!isValidEpubFile(tempFile)) {
				val fileHead = readFileHead(tempFile, 200)
				println("DownloadWorker.downloadEpubChapter: ERROR - Downloaded file is not a valid EPUB!")
				println("DownloadWorker.downloadEpubChapter: File content: $fileHead")
				println("DownloadWorker.downloadEpubChapter: URL: $epubUrl")
				println("DownloadWorker.downloadEpubChapter: Source: ${repo.source.name}")
				
				tempFile.deleteAwait()
				
				// Check if it's an HTML login/error page
				val lowerContent = fileHead.lowercase()
				when {
					lowerContent.contains("login") || lowerContent.contains("sign in") || lowerContent.contains("authentication") -> {
						throw IOException("Authentication required. Please log in to ${repo.source.name} again in the app settings.")
					}
					lowerContent.contains("not found") || lowerContent.contains("404") -> {
						throw IOException("Book not found or no longer available on ${repo.source.name}.")
					}
					lowerContent.contains("access denied") || lowerContent.contains("forbidden") -> {
						throw IOException("Access denied. You may not have permission to download this book.")
					}
					lowerContent.contains("<!doctype") || lowerContent.contains("<html") -> {
						throw IOException("Downloaded an HTML page instead of EPUB. This usually means authentication failed or the download link is invalid.")
					}
					else -> {
						throw IOException("Downloaded file is not a valid EPUB format. The file may be corrupted or the download link may be incorrect.")
					}
				}
			}
			
			println("DownloadWorker.downloadEpubChapter: File validation passed - is valid EPUB/ZIP")
			
			// Requirement 1.1 & 1.2: Preserve .epub extension (do NOT convert to .cbz)
			// Requirement 1.4: Generate unique filename using parent chapter ID
			val epubFileName = generateEpubFileName(chapter.value.id)
			val epubFile = if (tempFile.name.endsWith(".epub", ignoreCase = true)) {
				// If already has .epub extension, rename to use our naming pattern
				val newFile = File(tempFile.parentFile, epubFileName)
				if (tempFile.renameTo(newFile)) {
					newFile
				} else {
					newFile.outputStream().use { output ->
						tempFile.inputStream().use { input ->
							input.copyTo(output)
						}
					}
					tempFile.deleteAwait()
					newFile
				}
			} else {
				// Add .epub extension if missing
				val newFile = File(tempFile.parentFile, epubFileName)
				if (tempFile.renameTo(newFile)) {
					newFile
				} else {
					newFile.outputStream().use { output ->
						tempFile.inputStream().use { input ->
							input.copyTo(output)
						}
					}
					tempFile.deleteAwait()
					newFile
				}
			}
			
			println("DownloadWorker.downloadEpubChapter: Renamed to ${epubFile.absolutePath}")
			println("DownloadWorker.downloadEpubChapter: Extension preserved as: ${epubFile.extension}")
			
			// 根据output类型选择处理方式
			when (output) {
				is LocalMangaDirOutput -> {
					// MULTIPLE_CBZ格式：保存EPUB并解析章节
					println("DownloadWorker.downloadEpubChapter: Using MULTIPLE_CBZ format - saving as EPUB file")
					
					// Get the DAO for storing chapter mappings (Requirements 5.3)
					val epubChapterMappingDao = mangaDatabase.getEpubChapterMappingDao()
					
					// 保存EPUB文件（保持.epub扩展名）并存储章节映射到数据库
					output.addEpubChapter(chapter, epubFile, epubChapterMappingDao)
					println("DownloadWorker.downloadEpubChapter: Successfully saved EPUB with .epub extension and stored chapter mappings")
				}
				else -> {
					// SINGLE_CBZ格式：不支持EPUB解析，抛出错误提示用户更改下载格式
					println("DownloadWorker.downloadEpubChapter: ERROR - SINGLE_CBZ format does not support EPUB chapters")
					epubFile.deleteAwait()
					throw IOException("EPUB chapters require MULTIPLE_CBZ download format. Please change download format in settings to 'Multiple CBZ files' and try again.")
				}
			}
			
			// 通知本地存储变化
			runCatchingCancellable {
				localStorageChanges.emit(LocalMangaParser(output.rootFile).getManga(withDetails = false))
			}.onFailure(Throwable::printStackTraceDebug)
			
			println("DownloadWorker.downloadEpubChapter: Completed successfully")
			
		} catch (e: Exception) {
			println("DownloadWorker.downloadEpubChapter: ERROR - ${e.javaClass.simpleName}: ${e.message}")
			e.printStackTraceDebug()
			// Clean up the file (might be tempFile or epubFile depending on where error occurred)
			tempFile.deleteAwait()
			// Also try to delete epubFile if it was created
			val possibleEpubFile = File(tempFile.parentFile, generateEpubFileName(chapter.value.id))
			if (possibleEpubFile.exists() && possibleEpubFile != tempFile) {
				possibleEpubFile.deleteAwait()
			}
			throw e
		}
	}
	
	/**
	 * Generates a unique EPUB filename using the parent chapter ID.
	 * Pattern: chapter_{chapterId}_{timestamp}.epub
	 * 
	 * Requirement 1.4: Generate unique filenames using parent chapter ID
	 */
	private fun generateEpubFileName(chapterId: Long): String {
		val timestamp = System.currentTimeMillis()
		return "chapter_${chapterId}_${timestamp}.epub"
	}
	
	/**
	 * Download EPUB file to independent epub storage (NEW ARCHITECTURE)
	 * 
	 * This method implements the new EPUB architecture where:
	 * - EPUB files are stored in files/epub/{manga_id}/book.epub
	 * - No parsing or chapter extraction during download
	 * - LocalEpubSource will handle parsing when needed
	 * 
	 * @param manga The manga being downloaded
	 * @param chapter The chapter (EPUB download link)
	 * @param epubUrl The URL to download EPUB from
	 * @param destination Temporary download destination
	 * @param repo The manga repository
	 */
	private suspend fun downloadEpubToStorage(
		manga: Manga,
		chapter: IndexedValue<MangaChapter>,
		epubUrl: String,
		destination: File,
		repo: MangaRepository,
	) {
		android.util.Log.i("DownloadWorker", "========================================")
		android.util.Log.i("DownloadWorker", "downloadEpubToStorage: Starting NEW ARCHITECTURE EPUB download")
		android.util.Log.i("DownloadWorker", "downloadEpubToStorage: Manga ID=${manga.id}")
		android.util.Log.i("DownloadWorker", "downloadEpubToStorage: Manga Title=${manga.title}")
		android.util.Log.i("DownloadWorker", "downloadEpubToStorage: Chapter=${chapter.value.title}")
		android.util.Log.i("DownloadWorker", "downloadEpubToStorage: URL=$epubUrl")
		android.util.Log.i("DownloadWorker", "========================================")
		
		// 1. Download EPUB file to temporary location
		// IMPORTANT: useProxy = true to ensure cookies are sent for authentication
		val tempFile = try {
			android.util.Log.d("DownloadWorker", "downloadEpubToStorage: Downloading file with authentication...")
			downloadFile(repo, epubUrl, destination, useProxy = true)
		} catch (e: Exception) {
			android.util.Log.e("DownloadWorker", "downloadEpubToStorage: Download failed", e)
			throw e
		}
		
		android.util.Log.d("DownloadWorker", "downloadEpubToStorage: Downloaded to ${tempFile.absolutePath}")
		android.util.Log.d("DownloadWorker", "downloadEpubToStorage: File size=${tempFile.length()} bytes")
		
		try {
			// 2. Validate file is actually EPUB/ZIP
			if (!isValidEpubFile(tempFile)) {
				val fileHead = readFileHead(tempFile, 200)
				android.util.Log.e("DownloadWorker", "downloadEpubToStorage: Invalid EPUB file!")
				android.util.Log.e("DownloadWorker", "downloadEpubToStorage: File head: $fileHead")
				tempFile.deleteAwait()
				throw IOException("Downloaded file is not a valid EPUB (possible authentication error or HTML error page)")
			}
			
			android.util.Log.d("DownloadWorker", "downloadEpubToStorage: File validated successfully")
			
			// 3. Save to epub storage using EpubStorageManager
			// 使用chapter ID来区分同一manga的多个EPUB文件
			val savedFile = epubStorageManager.saveEpubFile(manga.id, tempFile, chapter.value.id)
			android.util.Log.i("DownloadWorker", "downloadEpubToStorage: Saved to ${savedFile.absolutePath}, size=${savedFile.length()} bytes")
			
			// 4. Delete temporary file
			tempFile.deleteAwait()
			
			android.util.Log.i("DownloadWorker", "downloadEpubToStorage: Completed successfully")
			android.util.Log.i("DownloadWorker", "========================================")
			
		} catch (e: Exception) {
			android.util.Log.e("DownloadWorker", "downloadEpubToStorage: Error during save", e)
			tempFile.deleteAwait()
			throw e
		}
	}

	private suspend fun publishState(state: DownloadState) {
		val previousState = currentState
		lastPublishedState = state
		if (previousState.isParticularProgress && state.isParticularProgress) {
			etaEstimator.onProgressChanged(state.progress, state.max)
		} else {
			etaEstimator.reset()
			notificationThrottler.reset()
		}
		val notification = notificationFactory.create(state)
		if (state.isFinalState) {
			if (!notificationFactory.isSilent) {
				notificationManager.notify(id.toString(), id.hashCode(), notification)
			}
		} else if (notificationThrottler.throttle()) {
			notificationManager.notify(id.hashCode(), notification)
		} else {
			return
		}
		setProgress(state.toWorkData())
	}

	private suspend fun getDoneChapters(manga: Manga) = runCatchingCancellable {
		localMangaRepository.getDetails(manga).chapters?.ids()
	}.getOrNull().orEmpty()

	private fun getChapters(
		manga: Manga,
		task: DownloadTask,
	): List<IndexedValue<MangaChapter>> {
		val chapters = checkNotNull(manga.chapters) { "Chapters list must not be null" }
		val chaptersIdsSet = task.chaptersIds?.toMutableSet()
		val result = ArrayList<IndexedValue<MangaChapter>>((chaptersIdsSet ?: chapters).size)
		val counters = HashMap<String?, Int>()
		for (chapter in chapters) {
			val index = counters[chapter.branch] ?: 0
			counters[chapter.branch] = index + 1
			if (chaptersIdsSet != null && !chaptersIdsSet.remove(chapter.id)) {
				continue
			}
			result.add(IndexedValue(index, chapter))
		}
		if (chaptersIdsSet != null) {
			check(chaptersIdsSet.isEmpty()) {
				"${chaptersIdsSet.size} of ${task.chaptersIds.size} requested chapters not found in manga"
			}
		}
		check(result.isNotEmpty()) { "Chapters list must not be empty" }
		return result
	}

	@Reusable
	class Scheduler @Inject constructor(
		@ApplicationContext private val context: Context,
		private val mangaDataRepository: MangaDataRepository,
		private val workManager: WorkManager,
	) {

		fun observeWorks(): Flow<List<WorkInfo>> = workManager
			.getWorkInfosByTagFlow(TAG)

		@SuppressLint("RestrictedApi")
		suspend fun getInputData(id: UUID): Data? {
			val spec = workManager.getWorkSpec(id) ?: return null
			return Data.Builder()
				.putAll(spec.input)
				.putLong(DownloadState.DATA_TIMESTAMP, spec.scheduleRequestedAt)
				.build()
		}

		suspend fun getTask(workId: UUID): DownloadTask? {
			return workManager.getWorkInputData(workId)?.let { DownloadTask(it) }
		}

		suspend fun cancel(id: UUID) {
			workManager.cancelWorkById(id).await()
		}

		suspend fun cancelAll() {
			workManager.cancelAllWorkByTag(TAG).await()
		}

		fun pause(id: UUID) = context.sendBroadcast(
			PausingReceiver.getPauseIntent(context, id),
		)

		fun resume(id: UUID) = context.sendBroadcast(
			PausingReceiver.getResumeIntent(context, id),
		)

		fun skip(id: UUID) = context.sendBroadcast(
			PausingReceiver.getSkipIntent(context, id),
		)

		fun skipAll(id: UUID) = context.sendBroadcast(
			PausingReceiver.getSkipAllIntent(context, id),
		)

		suspend fun delete(id: UUID) {
			workManager.deleteWork(id)
		}

		suspend fun delete(ids: Collection<UUID>) {
			val wm = workManager
			ids.forEach { id -> wm.cancelWorkById(id).await() }
			workManager.deleteWorks(ids)
		}

		suspend fun removeCompleted() {
			val finishedWorks = workManager.awaitFinishedWorkInfosByTag(TAG)
			workManager.deleteWorks(finishedWorks.mapToSet { it.id })
		}

		suspend fun updateConstraints(allowMeteredNetwork: Boolean) {
			val constraints = createConstraints(allowMeteredNetwork)
			val works = workManager.awaitWorkInfosByTag(TAG)
			for (work in works) {
				if (work.state.isFinished) {
					continue
				}
				val request = OneTimeWorkRequestBuilder<DownloadWorker>()
					.setConstraints(constraints)
					.addTag(TAG)
					.setId(work.id)
					.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
					.build()
				workManager.awaitUpdateWork(request)
			}
		}

		suspend fun schedule(tasks: Collection<Pair<Manga, DownloadTask>>) {
			if (tasks.isEmpty()) {
				return
			}
			val requests = tasks.map { (manga, task) ->
				mangaDataRepository.storeManga(manga, replaceExisting = true)
				OneTimeWorkRequestBuilder<DownloadWorker>()
					.setConstraints(createConstraints(task.allowMeteredNetwork))
					.addTag(TAG)
					.keepResultsForAtLeast(30, TimeUnit.DAYS)
					.setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
					.setInputData(task.toData())
					.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
					.build()
			}
			workManager.enqueue(requests).await()
		}

		private fun createConstraints(allowMeteredNetwork: Boolean) = Constraints.Builder()
			.setRequiredNetworkType(if (allowMeteredNetwork) NetworkType.CONNECTED else NetworkType.UNMETERED)
			.build()
	}

	/**
	 * 验证文件是否为有效的EPUB/ZIP文件
	 * EPUB文件本质上是ZIP格式，magic bytes应该是 PK (0x50 0x4B)
	 */
	private fun isValidEpubFile(file: File): Boolean {
		if (!file.exists() || file.length() < 4) {
			return false
		}
		
		return try {
			file.inputStream().use { input ->
				val header = ByteArray(4)
				val read = input.read(header)
				if (read < 2) return false
				
				// ZIP/EPUB magic bytes: PK\x03\x04 (0x50 0x4B 0x03 0x04)
				header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
			}
		} catch (e: Exception) {
			false
		}
	}

	/**
	 * 读取文件头部用于调试
	 */
	private fun readFileHead(file: File, maxBytes: Int): String {
		if (!file.exists()) return "[File does not exist]"
		
		return try {
			file.inputStream().use { input ->
				val bytes = ByteArray(minOf(maxBytes, file.length().toInt()))
				input.read(bytes)
				
				// 尝试作为文本读取（如果是HTML错误页）
				val text = String(bytes, Charsets.UTF_8)
				if (text.contains("<!DOCTYPE", ignoreCase = true) || 
				    text.contains("<html", ignoreCase = true)) {
					"[HTML detected] $text"
				} else {
					// 显示hex dump
					bytes.joinToString(" ") { "%02X".format(it) }
				}
			}
		} catch (e: Exception) {
			"[Error reading file: ${e.message}]"
		}
	}

	private companion object {

		const val MAX_FAILSAFE_ATTEMPTS = 5
		const val MAX_PAGES_PARALLELISM = 2
		const val DOWNLOAD_ERROR_DELAY = 2_000L
		const val MAX_RETRY_DELAY = 7_200_000L // 2 hours
		const val TAG = "download"
		private const val PAGE_NAME_PATTERN = "%08d_%04d%04d"
	}

	@AssistedFactory
	interface Factory : WorkerAssistedFactory<DownloadWorker>
}
