package org.skepsun.kototoro.favourites.ui.container

import android.content.Context
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import org.skepsun.kototoro.core.model.getTitle
import org.skepsun.kototoro.core.model.isNsfw
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.FavouriteCategory
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.observeAsFlow
import org.skepsun.kototoro.core.ui.BaseViewModel
import org.skepsun.kototoro.core.ui.util.ReversibleAction
import org.skepsun.kototoro.core.ui.util.ReversibleHandle
import org.skepsun.kototoro.core.util.ext.MutableEventFlow
import org.skepsun.kototoro.core.util.ext.call
import org.skepsun.kototoro.explore.data.MangaSourcesRepository
import org.skepsun.kototoro.favourites.domain.FavouritesRepository
import org.skepsun.kototoro.favourites.ui.list.FavouritesListFragment.Companion.NO_ID
import org.skepsun.kototoro.core.parser.MangaRepository
import org.skepsun.kototoro.core.parser.ParserMangaRepository
import org.skepsun.kototoro.parsers.exception.AuthRequiredException
import org.skepsun.kototoro.parsers.model.MangaParserSource
import org.skepsun.kototoro.core.model.unwrap
import javax.inject.Inject

@HiltViewModel
class FavouritesContainerViewModel @Inject constructor(
	@ApplicationContext private val appContext: Context,
	private val settings: AppSettings,
	private val favouritesRepository: FavouritesRepository,
	private val sourcesRepository: MangaSourcesRepository,
	private val mangaRepositoryFactory: MangaRepository.Factory,
) : BaseViewModel() {

	data class ImportSource(
		val source: MangaParserSource,
		val title: String,
	)

	val onActionDone = MutableEventFlow<ReversibleAction>()
val importMessages = MutableEventFlow<String>()
	val syncMessages = MutableEventFlow<String>()
	private fun logImport(msg: String) = runCatching { println("[FavouritesImport] $msg") }
	private fun logSync(msg: String) = runCatching { println("[FavouritesSync] $msg") }

	private val categoriesStateFlow = favouritesRepository.observeCategoriesForLibrary()
		.withErrorHandling()
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, null)

	val categories = combine(
		categoriesStateFlow.filterNotNull(),
		observeAllFavouritesVisibility(),
	) { list, showAll ->
		list.toUi(showAll)
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, emptyList())

	val isEmpty = categoriesStateFlow.map {
		it?.isEmpty() == true
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, false)

	private fun List<FavouriteCategory>.toUi(showAll: Boolean): List<FavouriteTabModel> {
		if (isEmpty()) {
			return emptyList()
		}
		val result = ArrayList<FavouriteTabModel>(if (showAll) size + 1 else size)
		if (showAll) {
			result.add(FavouriteTabModel(NO_ID, null))
		}
		mapTo(result) { FavouriteTabModel(it.id, it.title) }
		return result
	}

	fun hide(categoryId: Long) {
		launchJob(Dispatchers.Default) {
			if (categoryId == NO_ID) {
				settings.isAllFavouritesVisible = false
			} else {
				favouritesRepository.updateCategory(categoryId, isVisibleInLibrary = false)
				val reverse = ReversibleHandle {
					favouritesRepository.updateCategory(categoryId, isVisibleInLibrary = true)
				}
				onActionDone.call(ReversibleAction(R.string.category_hidden_done, reverse))
			}
		}
	}

	fun deleteCategory(categoryId: Long) {
		launchJob(Dispatchers.Default) {
			favouritesRepository.removeCategories(setOf(categoryId))
		}
	}

	private fun observeAllFavouritesVisibility() = settings.observeAsFlow(
		key = AppSettings.KEY_ALL_FAVOURITES_VISIBLE,
		valueProducer = { isAllFavouritesVisible },
	)

	suspend fun loadImportCandidates(): List<ImportSource> {
		val enabledSources = sourcesRepository.getEnabledSources()
		val candidates = ArrayList<ImportSource>()
		logImport("loadImportCandidates: enabled=${enabledSources.size}, hideNsfw=${settings.isNsfwContentDisabled}")
		for (item in enabledSources) {
			val unwrapped = item.unwrap()
			val parserSource = (unwrapped as? MangaParserSource)
			if (parserSource == null) {
				logImport("skip ${item.name}: not a parser source (${unwrapped?.javaClass?.simpleName})")
				continue
			}
			val repository = mangaRepositoryFactory.create(parserSource) as? ParserMangaRepository
			if (repository == null) {
				logImport("skip ${parserSource.name}: repository not parser")
				continue
			}
			val authProvider = repository.getAuthProvider()
			val favoritesProvider = repository.favoritesProvider()
			if (favoritesProvider == null) {
				logImport(
					"skip ${parserSource.name}: no FavoritesProvider, parser=${repository.javaClass.simpleName}," +
						" interfaces=${repository.javaClass.interfaces.joinToString { it.simpleName ?: it.name }}"
				)
				continue
			}
			val isAuthed = authProvider?.let { runCatching { it.isAuthorized() }.getOrDefault(false) } ?: true
			logImport("candidate ${parserSource.name}: authed=$isAuthed, nsfw=${parserSource.isNsfw()}, hasFavoritesProvider=true")
			if (!isAuthed) {
				logImport("skip ${parserSource.name}: unauthorized")
				continue
			}
			candidates.add(ImportSource(parserSource, parserSource.getTitle(appContext)))
		}
		logImport("loadImportCandidates: final=${candidates.size}, names=${candidates.joinToString { it.source.name }}")
		return candidates.sortedBy { it.title.lowercase() }
	}

	fun importFavorites(sources: List<ImportSource>) {
		if (sources.isEmpty()) {
			importMessages.call(appContext.getString(R.string.import_favourites_none_selected))
			return
		}
		launchLoadingJob(Dispatchers.IO) {
			for (item in sources) {
				importMessages.call(appContext.getString(R.string.import_favourites_progress, item.title))
				logImport("import start source=${item.source.name}")
				val repository = mangaRepositoryFactory.create(item.source) as? ParserMangaRepository ?: continue
				val favProvider = repository.favoritesProvider() ?: continue
				try {
					val category = ensureCategory(item.title) // 即使没有收藏也先创建分组
					val favs = favProvider.fetchFavorites()
					logImport("import fetched source=${item.source.name} count=${favs.size}")
					if (favs.isEmpty()) {
						logImport("import empty favourites for source=${item.source.name}, category=${category.title}")
						continue
					}
					favouritesRepository.addToCategory(category.id, favs)
					logImport("import saved source=${item.source.name} into category=${category.title}")
				} catch (e: AuthRequiredException) {
					importMessages.call(appContext.getString(R.string.import_favourites_auth_expired))
					logImport("import auth required source=${item.source.name}")
				} catch (_: Exception) {
					logImport("import failed source=${item.source.name} with generic exception")
					// 忽略单个来源失败，继续下一个
				}
			}
			importMessages.call(appContext.getString(R.string.import_favourites_done))
			logImport("import done")
		}
	}

	suspend fun loadSyncCandidates(): List<ImportSource> {
		val enabledSources = sourcesRepository.getEnabledSources()
		val candidates = ArrayList<ImportSource>()
		logSync("loadSyncCandidates: enabled=${enabledSources.size}, hideNsfw=${settings.isNsfwContentDisabled}")
		for (item in enabledSources) {
			val unwrapped = item.unwrap()
			val parserSource = (unwrapped as? MangaParserSource)
			if (parserSource == null) {
				logSync("skip ${item.name}: not a parser source (${unwrapped?.javaClass?.simpleName})")
				continue
			}
			val repository = mangaRepositoryFactory.create(parserSource) as? ParserMangaRepository
			if (repository == null) {
				logSync("skip ${parserSource.name}: repository not parser")
				continue
			}
			val authProvider = repository.getAuthProvider()
			val syncProvider = repository.favoritesSyncProvider()
			if (syncProvider == null) {
				logSync(
					"skip ${parserSource.name}: no FavoritesSyncProvider, parser=${repository.javaClass.simpleName}," +
						" interfaces=${repository.javaClass.interfaces.joinToString { it.simpleName ?: it.name }}"
				)
				continue
			}
			val isAuthed = authProvider?.let { runCatching { it.isAuthorized() }.getOrDefault(false) } ?: true
			logSync("candidate ${parserSource.name}: authed=$isAuthed, hasSyncProvider=true")
			if (!isAuthed) {
				logSync("skip ${parserSource.name}: unauthorized")
				continue
			}
			candidates.add(ImportSource(parserSource, parserSource.getTitle(appContext)))
		}
		logSync("loadSyncCandidates: final=${candidates.size}, names=${candidates.joinToString { it.source.name }}")
		return candidates.sortedBy { it.title.lowercase() }
	}

	fun syncFavorites(sources: List<ImportSource>) {
		if (sources.isEmpty()) {
			syncMessages.call(appContext.getString(R.string.sync_favourites_none_selected))
			return
		}
		launchLoadingJob(Dispatchers.IO) {
			for (item in sources) {
				logSync("sync start source=${item.source.name}")
				val repository = mangaRepositoryFactory.create(item.source) as? ParserMangaRepository ?: continue
				val syncProvider = repository.favoritesSyncProvider() ?: continue
				val favProvider = repository.favoritesProvider()
				val category = favouritesRepository.findCategoryByTitle(item.title)
				if (category == null) {
					logSync("sync skip source=${item.source.name} no local category")
					continue
				}
				val local = favouritesRepository.getManga(category.id)
				val remote = runCatching { favProvider?.fetchFavorites() ?: emptyList() }
					.onFailure { logSync("sync ${item.source.name} fetch remote failed") }
					.getOrDefault(emptyList())
				val localKeys = local.associateBy { it.url }
				val remoteKeys = remote.associateBy { it.url }
				// 先把远程新增的（本地没有的）合并进本地分组
				val remoteExtras = remoteKeys.keys.minus(localKeys.keys).mapNotNull { remoteKeys[it] }
				if (remoteExtras.isNotEmpty()) {
					logSync("sync merge remote extras source=${item.source.name} extras=${remoteExtras.size}")
					favouritesRepository.addToCategory(category.id, remoteExtras)
				}
				val localMerged = local + remoteExtras
				val localMergedKeys = localMerged.associateBy { it.url }
				val toAdd = localMergedKeys.keys.minus(remoteKeys.keys).mapNotNull { localMergedKeys[it] }
				val toRemove = remoteKeys.keys.minus(localMergedKeys.keys).mapNotNull { remoteKeys[it] }
				logSync("sync source=${item.source.name} local=${localMerged.size} remote=${remote.size} add=${toAdd.size} remove=${toRemove.size}")
				toAdd.forEach { runCatching { syncProvider.addFavorite(it) }.onFailure { logSync("sync add fail ${item.source.name}") } }
				toRemove.forEach { runCatching { syncProvider.removeFavorite(it) }.onFailure { logSync("sync remove fail ${item.source.name}") } }
			}
			syncMessages.call(appContext.getString(R.string.sync_favourites_done))
			logSync("sync done")
		}
	}

	private suspend fun ensureCategory(title: String): FavouriteCategory {
		return favouritesRepository.findCategoryByTitle(title)
			?: favouritesRepository.createCategory(
				title = title,
				sortOrder = org.skepsun.kototoro.list.domain.ListSortOrder.NEWEST,
				isTrackerEnabled = false,
				isVisibleOnShelf = true,
			)
	}
}
