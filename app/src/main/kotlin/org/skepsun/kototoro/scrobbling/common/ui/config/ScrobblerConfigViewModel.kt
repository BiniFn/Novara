package org.skepsun.kototoro.scrobbling.common.ui.config

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.db.MangaDatabase
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.ui.BaseViewModel
import org.skepsun.kototoro.core.util.ext.MutableEventFlow
import org.skepsun.kototoro.core.util.ext.call
import org.skepsun.kototoro.core.util.ext.onFirst
import org.skepsun.kototoro.core.util.ext.require
import org.skepsun.kototoro.list.ui.model.EmptyState
import org.skepsun.kototoro.list.ui.model.ListModel
import org.skepsun.kototoro.scrobbling.common.domain.Scrobbler
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerUser
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblingInfo
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblingStatus
import org.skepsun.kototoro.core.parser.MangaDataRepository
import org.skepsun.kototoro.core.parser.MangaRepository
import org.skepsun.kototoro.core.model.isLocal
import org.skepsun.kototoro.history.data.HistoryRepository
import org.skepsun.kototoro.parsers.model.Manga
import org.skepsun.kototoro.scrobbling.common.data.ScrobblingEntity
import android.content.Context
import org.skepsun.kototoro.core.LocalizedAppContext
import org.skepsun.kototoro.favourites.domain.FavouritesRepository
import org.skepsun.kototoro.core.model.getTitle
import org.skepsun.kototoro.core.model.getOriginLabel
import javax.inject.Inject

@HiltViewModel
class ScrobblerConfigViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	scrobblers: Set<@JvmSuppressWildcards Scrobbler>,
	private val db: MangaDatabase,
	private val mangaDataRepository: MangaDataRepository,
	private val historyRepository: HistoryRepository,
	private val mangaRepositoryFactory: MangaRepository.Factory,
	private val favouritesRepository: FavouritesRepository,
	@LocalizedAppContext private val context: Context,
) : BaseViewModel() {

	private val scrobblerService = getScrobblerService(savedStateHandle)
	private val scrobbler = scrobblers.first { it.scrobblerService == scrobblerService }

	val titleResId = scrobbler.scrobblerService.titleResId

	val user = MutableStateFlow<ScrobblerUser?>(null)
	val onLoggedOut = MutableEventFlow<Unit>()

	val content = scrobbler.observeAllScrobblingInfo()
		.onStart { loadingCounter.increment() }
		.onFirst { loadingCounter.decrement() }
		.withErrorHandling()
		.map { buildContentList(it) }
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, emptyList())

	init {
		scrobbler.user
			.onEach { user.value = it }
			.launchIn(viewModelScope + Dispatchers.Default)
	}

	fun onAuthCodeReceived(authCode: String) {
		launchLoadingJob(Dispatchers.Default) {
			val newUser = scrobbler.authorize(authCode)
			user.value = newUser
		}
	}

	fun logout() {
		launchLoadingJob(Dispatchers.Default) {
			scrobbler.logout()
			user.value = null
			onLoggedOut.call(Unit)
		}
	}

	val onSyncResult = MutableEventFlow<Int>()
	val onBindResult = MutableEventFlow<String>()

	fun syncLibrary() {
		launchLoadingJob(Dispatchers.Default) {
			val count = scrobbler.syncLibrary()
			onSyncResult.call(count)
		}
	}

	fun bindManga(info: ScrobblingInfo, pickedManga: Manga) {
		launchLoadingJob(Dispatchers.Default) {
			android.util.Log.d("ScrobblerConfigVM", "bindManga: info.mangaId=${info.mangaId}, info.targetId=${info.targetId}, info.chapter=${info.chapter}, pickedManga.id=${pickedManga.id}, pickedManga.title=${pickedManga.title}")
			// 1. Insert the online Manga result into MangaDatabase via MangaDataRepository
			mangaDataRepository.storeManga(pickedManga, replaceExisting = false)
			val mangaId = pickedManga.id
			android.util.Log.d("ScrobblerConfigVM", "bindManga: stored manga, mangaId=$mangaId")

			// 2. Re-link the tracker
			val currentEntity = db.getScrobblingDao().find(scrobbler.scrobblerService.id, info.mangaId)
			android.util.Log.d("ScrobblerConfigVM", "bindManga: currentEntity=$currentEntity")
			if (currentEntity != null) {
				db.getScrobblingDao().delete(currentEntity)
				val newEntity = currentEntity.copy(mangaId = mangaId)
				android.util.Log.d("ScrobblerConfigVM", "bindManga: deleted old, upserting new entity=$newEntity")
				db.getScrobblingDao().upsert(newEntity)
			} else {
				val newEntity = ScrobblingEntity(
					scrobbler = scrobbler.scrobblerService.id,
					id = info.targetId.toInt(),
					targetId = info.targetId,
					mangaId = mangaId,
					status = info.status?.name,
					chapter = info.chapter,
					comment = info.comment,
					rating = info.rating,
				)
				android.util.Log.d("ScrobblerConfigVM", "bindManga: no existing entity, upserting new=$newEntity")
				db.getScrobblingDao().upsert(newEntity)
			}
			android.util.Log.d("ScrobblerConfigVM", "bindManga: upsert done")

			// 3. Sync Reading Progress
			if (info.chapter > 0) {
				try {
					var mangaToSync = pickedManga
					if (mangaToSync.chapters.isNullOrEmpty() && !mangaToSync.isLocal) {
						val repo = mangaRepositoryFactory.create(mangaToSync.source)
						val details = repo.getDetails(mangaToSync)
						mangaToSync = details.copy(chapters = details.chapters)
						mangaDataRepository.updateChapters(mangaToSync)
					}
					
					val chapters = mangaToSync.chapters ?: emptyList()
					val targetChapterIndex = (info.chapter - 1).coerceIn(0, chapters.size - 1)
					android.util.Log.d("ScrobblerConfigVM", "bindManga: syncing progress, chapters.size=${chapters.size}, targetChapterIndex=$targetChapterIndex")
					if (chapters.isNotEmpty() && targetChapterIndex >= 0) {
						val targetChapter = chapters[targetChapterIndex]
						historyRepository.addOrUpdate(
							manga = mangaToSync,
							chapterId = targetChapter.id,
							page = 0,
							scroll = 0,
							percent = 1f, // Mark as completed
							force = true
						)
						android.util.Log.d("ScrobblerConfigVM", "bindManga: history synced for chapter=${targetChapter.id}")
					}
				} catch (e: Exception) {
					android.util.Log.e("ScrobblerConfigVM", "Failed to sync reading progress", e)
				}
			}
			
			autoAssignSourceCategory(pickedManga)
			
			android.util.Log.d("ScrobblerConfigVM", "bindManga: completed successfully")
			onBindResult.call(pickedManga.title)
		}
	}

	suspend fun hasLocalManga(mangaId: Long): Boolean {
		if (mangaId == 0L) return false
		return db.getMangaDao().find(mangaId) != null
	}

	private suspend fun autoAssignSourceCategory(manga: Manga) {
		val source = manga.source
		val origin = source.getOriginLabel(context)
		val title = if (origin != null && origin != "内置") {
			"${source.getTitle(context)} ($origin)"
		} else {
			source.getTitle(context)
		}
		val target = favouritesRepository.findCategoryByTitle(title)
			?: favouritesRepository.createCategory(
				title = title,
				sortOrder = org.skepsun.kototoro.list.domain.ListSortOrder.NEWEST,
				isTrackerEnabled = false,
				isVisibleOnShelf = true,
			)
		favouritesRepository.addToCategory(target.id, listOf(manga))
	}

	private fun buildContentList(list: List<ScrobblingInfo>): List<ListModel> {
		if (list.isEmpty()) {
			return listOf(
				EmptyState(
					icon = R.drawable.ic_empty_history,
					textPrimary = R.string.nothing_here,
					textSecondary = R.string.scrobbling_empty_hint,
					actionStringRes = 0,
				),
			)
		}
		val grouped = list.groupBy { it.status }
		val statuses = ScrobblingStatus.entries
		val result = ArrayList<ListModel>(list.size + statuses.size)
		for (st in statuses) {
			val subList = grouped[st]
			if (subList.isNullOrEmpty()) {
				continue
			}
			result.add(st)
			result.addAll(subList)
		}
		return result
	}

	private fun getScrobblerService(
		savedStateHandle: SavedStateHandle,
	): ScrobblerService {
		val serviceId = savedStateHandle.get<Int>(AppRouter.KEY_ID) ?: 0
		if (serviceId != 0) {
			return ScrobblerService.entries.first { it.id == serviceId }
		}
		val uri = savedStateHandle.require<Uri>(AppRouter.KEY_DATA)
		return when (uri.host) {
			ScrobblerConfigActivity.HOST_SHIKIMORI_AUTH -> ScrobblerService.SHIKIMORI
			ScrobblerConfigActivity.HOST_ANILIST_AUTH -> ScrobblerService.ANILIST
			ScrobblerConfigActivity.HOST_MAL_AUTH -> ScrobblerService.MAL
			ScrobblerConfigActivity.HOST_KITSU_AUTH -> ScrobblerService.KITSU
			ScrobblerConfigActivity.HOST_BANGUMI_AUTH -> ScrobblerService.BANGUMI
			ScrobblerConfigActivity.HOST_MANGAUPDATES_AUTH -> ScrobblerService.MANGAUPDATES
			else -> error("Wrong scrobbler uri: $uri")
		}
	}
}
