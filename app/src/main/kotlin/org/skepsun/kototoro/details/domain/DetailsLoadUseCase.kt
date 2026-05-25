package org.skepsun.kototoro.details.domain

import android.text.Html
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.core.text.getSpans
import androidx.core.text.parseAsHtml
import coil3.request.CachePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runInterruptible
import org.skepsun.kototoro.core.model.LocalNovelSource
import org.skepsun.kototoro.core.model.isLocal
import org.skepsun.kototoro.core.nav.ContentIntent
import org.skepsun.kototoro.core.os.NetworkState
import org.skepsun.kototoro.core.parser.CachingContentRepository
import org.skepsun.kototoro.core.parser.ContentDataRepository
import org.skepsun.kototoro.core.parser.ContentRepository
import org.skepsun.kototoro.core.ui.model.ContentOverride
import org.skepsun.kototoro.core.util.ext.sanitize
import org.skepsun.kototoro.details.data.ContentDetails
import org.skepsun.kototoro.explore.domain.RecoverContentUseCase
import org.skepsun.kototoro.local.data.LocalMangaRepository
import org.skepsun.kototoro.local.domain.model.LocalContent
import org.skepsun.kototoro.parsers.exception.NotFoundException
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.util.nullIfEmpty
import org.skepsun.kototoro.parsers.util.recoverNotNull
import org.skepsun.kototoro.parsers.util.runCatchingCancellable
import javax.inject.Inject

class DetailsLoadUseCase @Inject constructor(
	private val mangaDataRepository: ContentDataRepository,
	private val localContentRepository: LocalMangaRepository,
	private val mangaRepositoryFactory: ContentRepository.Factory,
	private val recoverUseCase: RecoverContentUseCase,
	private val imageGetter: Html.ImageGetter,
	private val networkState: NetworkState,
	private val mangaDatabase: org.skepsun.kototoro.core.db.MangaDatabase,
) {

	operator fun invoke(intent: ContentIntent, force: Boolean): Flow<ContentDetails> = flow {
		val manga = requireNotNull(mangaDataRepository.resolveIntent(intent, withChapters = true)) {
			"Cannot resolve intent $intent"
		}
		val override = mangaDataRepository.getOverride(manga.id)
		emit(
			ContentDetails(
				manga = manga,
				localContent = null,
				override = override,
				description = manga.description?.parseAsHtml(withImages = false),
				isLoaded = false,
			),
		)
		if (manga.isLocal) {
			loadLocal(manga, override, force)
		} else {
			loadRemote(manga, override, force)
		}
	}.distinctUntilChanged()
		.flowOn(Dispatchers.Default)

	/**
	 * Load local manga + try to load the linked remote one if network is not restricted
	 * Suppress any network errors
	 */
	private suspend fun FlowCollector<ContentDetails>.loadLocal(manga: Content, override: ContentOverride?, force: Boolean) {
		val skipNetworkLoad = !force && networkState.isOfflineOrRestricted()
		val localDetails = localContentRepository.getDetails(manga)
		emit(
			ContentDetails(
				manga = localDetails,
				localContent = null,
				override = override,
				description = localDetails.description?.parseAsHtml(withImages = false),
				isLoaded = skipNetworkLoad,
			),
		)
		if (skipNetworkLoad) {
			return
		}
		val remoteContent = localContentRepository.getRemoteContent(manga)
		if (remoteContent == null || remoteContent.url.startsWith("file://")) {
			emit(
				ContentDetails(
					manga = localDetails,
					localContent = null,
					override = override,
					description = localDetails.description?.parseAsHtml(withImages = true),
					isLoaded = true,
				),
			)
		} else {
			val remoteDetails = getDetails(remoteContent, force).getOrNull()
			emit(
				ContentDetails(
					manga = remoteDetails ?: remoteContent,
					localContent = LocalContent(localDetails),
					override = override,
					description = (remoteDetails ?: localDetails).description?.parseAsHtml(withImages = true),
					isLoaded = true,
				),
			)
			if (remoteDetails != null) {
				mangaDataRepository.updateChapters(remoteDetails)
			}
		}
	}

	/**
	 * Load remote manga + saved one if available
	 * Throw network errors after loading local manga only
	 */
	private suspend fun FlowCollector<ContentDetails>.loadRemote(
		manga: Content,
		override: ContentOverride?,
		force: Boolean
	) = coroutineScope {
		val localContent = localContentRepository.findSavedContent(manga, withDetails = true)
		val skipNetworkLoad = !force && networkState.isOfflineOrRestricted()

		if (skipNetworkLoad && localContent != null) {
			emit(
				ContentDetails(
					manga = manga,
					localContent = localContent,
					override = override,
					description = localContent.manga.description?.parseAsHtml(withImages = true),
					isLoaded = true,
				),
			)
			return@coroutineScope
		}

		val remoteDeferred = async {
			getDetails(manga, force)
		}
		if (localContent != null) {
			emit(
				ContentDetails(
					manga = manga,
					localContent = localContent,
					override = override,
					description = localContent.manga.description?.parseAsHtml(withImages = true),
					isLoaded = false,
				),
			)
		}
		val remoteResult = remoteDeferred.await()
		val remoteDetails = if (localContent != null) {
			// If we have local content, don't let network errors crash the flow
			remoteResult.getOrNull()
		} else {
			// No local fallback — propagate error
			remoteResult.getOrThrow()
		}
		if (remoteDetails != null) {
			emit(
				ContentDetails(
					manga = remoteDetails,
					localContent = localContent,
					override = override,
					description = (remoteDetails.description
						?: localContent?.manga?.description)?.parseAsHtml(withImages = true),
					isLoaded = true,
				),
			)
			mangaDataRepository.updateChapters(remoteDetails)
		} else if (localContent != null) {
			// Network failed but we have local content — mark as loaded with local data
			emit(
				ContentDetails(
					manga = manga,
					localContent = localContent,
					override = override,
					description = localContent.manga.description?.parseAsHtml(withImages = true),
					isLoaded = true,
				),
			)
		}
	}

	private suspend fun getDetails(seed: Content, force: Boolean) = runCatchingCancellable {
		val repository = mangaRepositoryFactory.create(seed.source)
		
		// 对于EPUB源（NoveliaWenku等），强制从服务器获取最新章节列表
		// 这样可以确保未下载的EPUB临时章节不会丢失
		val isEpubSource = seed.source.name.contains("WENKU", ignoreCase = true) || 
		                   seed.source.name.contains("EPUB", ignoreCase = true)
		val shouldForceRefresh = force || isEpubSource
		
		val manga = if (repository is CachingContentRepository) {
			repository.getDetails(seed, if (shouldForceRefresh) CachePolicy.WRITE_ONLY else CachePolicy.ENABLED)
		} else {
			repository.getDetails(seed)
		}
		
		android.util.Log.d("DetailsLoadUseCase", "getDetails: source=${seed.source.name}, isEpubSource=$isEpubSource, force=$force, shouldForceRefresh=$shouldForceRefresh")
		android.util.Log.d("DetailsLoadUseCase", "getDetails: manga has ${manga.chapters?.size ?: 0} chapters from server")
		
		// 检查是否有EPUB内部章节需要加载
		expandEpubChaptersIfNeeded(manga)
	}.recoverNotNull { e ->
		if (e is NotFoundException) {
			recoverUseCase(seed)
		} else {
			null
		}
	}
	
	/**
	 * 如果manga有EPUB下载章节，从数据库加载内部章节并展开
	 * 
	 * 策略：
	 * 1. 对于已下载的EPUB（有内部章节映射），用内部章节替换父章节
	 * 2. 对于未下载的EPUB，保留原始下载章节
	 * 3. 保留父章节的volume和branch信息到内部章节
	 */
	private suspend fun expandEpubChaptersIfNeeded(manga: Content): Content {
		val chapters = manga.chapters ?: return manga
		
		// 从数据库加载所有内部章节映射
		// 不再依赖URL模式检测，直接查询数据库
		val epubChapterMappingDao = mangaDatabase.getEpubChapterMappingDao()
		val allMappings = epubChapterMappingDao.findByContentId(manga.id)
		
		if (allMappings.isEmpty()) {
			// 没有EPUB章节映射，返回原始章节
			return manga
		}
		
		android.util.Log.d("DetailsLoadUseCase", "Found EPUB chapters, expanding internal chapters for manga ${manga.id}")
		
		android.util.Log.d("DetailsLoadUseCase", "Found ${allMappings.size} EPUB chapter mappings")
		
		// 按父章节ID分组
		val mappingsByParent = allMappings.groupBy { it.parentChapterId }
		val downloadedParentIds = mappingsByParent.keys
		
		android.util.Log.d("DetailsLoadUseCase", "Downloaded parent chapter IDs: $downloadedParentIds")
		
		// 构建新的章节列表
		val expandedChapters = mutableListOf<org.skepsun.kototoro.parsers.model.ContentChapter>()
		
		android.util.Log.d("DetailsLoadUseCase", "Processing ${chapters.size} chapters...")
		for ((index, chapter) in chapters.withIndex()) {
			android.util.Log.d("DetailsLoadUseCase", "  Chapter[$index]: id=${chapter.id}, title=${chapter.title}, isDownloaded=${chapter.id in downloadedParentIds}")
			
			if (chapter.id in downloadedParentIds) {
				// 这个EPUB已下载，用内部章节替换
				val mappings = mappingsByParent[chapter.id] ?: continue
				
				android.util.Log.d("DetailsLoadUseCase", "  -> Expanding with ${mappings.size} internal chapters")
				
				// 生成内部章节
				// IMPORTANT: Set branch to null for EPUB internal chapters
				// This ensures they can be found when selectedBranch is null
				val internalChapters = mappings
					.sortedBy { it.chapterIndex }
					.map { mapping ->
						org.skepsun.kototoro.parsers.model.ContentChapter(
							id = mapping.internalChapterId,
							title = mapping.chapterTitle,
							number = mapping.chapterIndex.toFloat(),
							volume = chapter.volume,  // 保留父章节的volume
						url = "epub://${manga.id}/chapter/${mapping.chapterIndex}",
						scanlator = mapping.epubFileName,
						uploadDate = mapping.createdAt,
						branch = null,  // EPUB internal chapters have no branch
						source = LocalNovelSource,
						)
					}
				
				expandedChapters.addAll(internalChapters)
			} else {
				// 这个EPUB未下载，保留原始下载章节
				// IMPORTANT: Set branch to null to match internal chapters
				android.util.Log.d("DetailsLoadUseCase", "  -> Keeping as download link")
				expandedChapters.add(chapter.copy(branch = null))
			}
		}
		
		android.util.Log.d("DetailsLoadUseCase", "Expanded chapters: ${chapters.size} -> ${expandedChapters.size}")
		android.util.Log.d("DetailsLoadUseCase", "Original chapters: ${chapters.take(3).map { "${it.id}:${it.title}" }}")
		android.util.Log.d("DetailsLoadUseCase", "Expanded chapters (first 3): ${expandedChapters.take(3).map { "${it.id}:${it.title}" }}")
		android.util.Log.d("DetailsLoadUseCase", "Expanded chapters (last 3): ${expandedChapters.takeLast(3).map { "${it.id}:${it.title}" }}")
		android.util.Log.d("DetailsLoadUseCase", "Final chapter count: ${expandedChapters.size}")
		
		val result = manga.copy(chapters = expandedChapters)
		android.util.Log.d("DetailsLoadUseCase", "Returning manga with ${result.chapters?.size ?: 0} chapters")
		return result
	}

	private suspend fun String.parseAsHtml(withImages: Boolean): CharSequence? = if (withImages) {
		runInterruptible(Dispatchers.IO) {
			parseAsHtml(imageGetter = imageGetter)
		}.filterSpans()
	} else {
		runInterruptible(Dispatchers.Default) {
			parseAsHtml()
		}.filterSpans().sanitize()
	}.trim().nullIfEmpty()

	private fun Spanned.filterSpans(): Spanned {
		val spannable = SpannableString.valueOf(this)
		val spans = spannable.getSpans<ForegroundColorSpan>()
		for (span in spans) {
			spannable.removeSpan(span)
		}
		return spannable
	}
}
