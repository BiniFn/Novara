package org.skepsun.kototoro.settings.sources.unified

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.extensions.GlobalExtensionManager
import org.skepsun.kototoro.core.jsonsource.JsonSourceManager
import org.skepsun.kototoro.core.network.jsonsource.LegadoHttpClient
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.ui.BaseViewModel
import org.skepsun.kototoro.core.util.ext.call
import org.skepsun.kototoro.core.util.ext.getDisplayMessage
import org.skepsun.kototoro.explore.data.ContentSourcesRepository
import org.skepsun.kototoro.extensions.install.ExtensionInstallDownloadState
import org.skepsun.kototoro.extensions.install.ExtensionInstallService
import org.skepsun.kototoro.extensions.repo.ExternalExtensionRepo
import org.skepsun.kototoro.extensions.repo.ExternalExtensionRepoRepository
import org.skepsun.kototoro.extensions.repo.ExternalExtensionType
import org.skepsun.kototoro.extensions.repo.InstalledExtensionSignatureValidator
import org.skepsun.kototoro.extensions.repo.RepoAvailableExtension
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.settings.sources.extensions.ExtensionBatchUpdateStateMachine
import org.skepsun.kototoro.settings.sources.extensions.normalizePackageNameForMatching
import org.skepsun.kototoro.settings.sources.extensions.toInstalledIReaderPackageName
import javax.inject.Inject

@HiltViewModel
class UnifiedSourcesViewModel @Inject constructor(
	@ApplicationContext private val appContext: Context,
	private val catalogRepository: UnifiedSourceCatalogRepository,
	private val contentSourcesRepository: ContentSourcesRepository,
	private val jsonSourceManager: JsonSourceManager,
	private val legadoHttpClient: LegadoHttpClient,
	private val extensionRepoRepository: ExternalExtensionRepoRepository,
	private val installService: ExtensionInstallService,
	private val signatureValidator: InstalledExtensionSignatureValidator,
	private val settings: AppSettings,
) : BaseViewModel() {

	private val availableExternalExtensions = MutableStateFlow<List<RepoAvailableExtension>>(emptyList())
	private val batchUpdateState = ExtensionBatchUpdateStateMachine()
	private val filterState = MutableStateFlow(
		UnifiedSourcesFilterState(
			languages = settings.extensionLanguages.mapTo(LinkedHashSet()) { it.normalizeLanguageCode() },
		),
	)
	private val _events = MutableSharedFlow<UnifiedSourcesEvent>(extraBufferCapacity = 1)
	val events: SharedFlow<UnifiedSourcesEvent> = _events.asSharedFlow()
	val updateAllInProgress: StateFlow<Boolean> = batchUpdateState.inProgress

	val uiState: StateFlow<UnifiedSourcesUiState> = combine(
		catalogRepository.observeState(),
		availableExternalExtensions,
		installService.downloadStates,
		filterState,
	) { catalog, availableExtensions, downloadStates, filters ->
		catalog
			.withAvailableExternalPackages(availableExtensions, downloadStates)
			.toUiState(filters)
	}.stateIn(
		scope = viewModelScope,
		started = SharingStarted.WhileSubscribed(5000),
		initialValue = UnifiedSourcesUiState.Loading,
	)

	init {
		refreshPackages(refreshRepositories = false)
	}

	fun setSearchQuery(query: String) {
		filterState.update { it.copy(query = query.trim()) }
	}

	fun toggleKind(kind: UnifiedSourceKind) {
		filterState.update { state ->
			state.copy(kinds = state.kinds.toggle(kind))
		}
	}

	fun setKindFilter(kind: UnifiedSourceKind?) {
		filterState.update { state ->
			state.copy(kinds = kind?.let(::setOf) ?: emptySet())
		}
	}

	fun toggleContentType(contentType: ContentType) {
		filterState.update { state ->
			state.copy(contentTypes = state.contentTypes.toggle(contentType))
		}
	}

	fun setPrimaryContentTypeFilter(contentType: ContentType?) {
		setContentTypeFilter(contentType)
	}

	fun setContentTypeFilter(contentType: ContentType?) {
		filterState.update { state ->
			state.copy(contentTypes = contentType?.let(::setOf) ?: emptySet())
		}
	}

	fun toggleLocationType(locationType: UnifiedRepositoryLocationType) {
		filterState.update { state ->
			state.copy(locationTypes = state.locationTypes.toggle(locationType))
		}
	}

	fun toggleLanguage(language: String) {
		val normalized = language.normalizeLanguageCode()
		filterState.update { state ->
			val updated = state.languages.toggle(normalized)
			settings.extensionLanguages = updated
			state.copy(languages = updated)
		}
	}

	fun setEnabledFilter(filter: UnifiedEnabledFilter) {
		filterState.update { it.copy(enabledFilter = filter) }
	}

	fun clearFilters() {
		filterState.value = UnifiedSourcesFilterState()
	}

	fun refreshPackages(refreshRepositories: Boolean = true) {
		launchLoadingJob(Dispatchers.IO) {
			val types = externalExtensionTypes()
			if (refreshRepositories) {
				types.forEach { type -> extensionRepoRepository.refresh(type) }
			}
			refreshAvailableExternalPackages(types)
		}
	}

	fun installPackage(packageId: String) {
		val item = currentPackage(packageId) ?: return
		if (item.state == UnifiedSourcePackageState.INSTALLED || item.packageName in installService.downloadStates.value) {
			return
		}
		requestInstall(item, fromBatch = false)
	}

	fun cancelPackageInstall(packageId: String) {
		val item = currentPackage(packageId) ?: return
		val packageName = item.packageName ?: return
		if (batchUpdateState.shouldCancelCurrent(packageName)) {
			cancelUpdateAll()
			return
		}
		installService.cancelDownload(packageName)
	}

	fun uninstallPackage(packageId: String) {
		val item = currentPackage(packageId) ?: return
		val packageName = item.packageName ?: return
		if (item.state == UnifiedSourcePackageState.INSTALLING) {
			return
		}

		if (item.kind == UnifiedSourceKind.JAR) {
			val pluginDir = File(appContext.filesDir, "plugins")
			val jarFile = File(pluginDir, "$packageName.jar")
			if (jarFile.exists()) {
				jarFile.delete()
			}
			appContext.getSharedPreferences("jar_plugin_versions", Context.MODE_PRIVATE)
				.edit()
				.remove(packageName)
				.apply()
			GlobalExtensionManager.initialize(appContext)
			viewModelScope.launch { emitMessage(appContext.getString(R.string.removal_completed)) }
			return
		}

		val uninstallPkg = if (item.kind == UnifiedSourceKind.IREADER && packageName.startsWith("ireader-")) {
			packageName.toInstalledIReaderPackageName()
		} else {
			packageName
		}
		val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			Intent.ACTION_DELETE
		} else {
			@Suppress("DEPRECATION")
			Intent.ACTION_UNINSTALL_PACKAGE
		}
		_events.tryEmit(UnifiedSourcesEvent.StartUninstall(Intent(action, Uri.fromParts("package", uninstallPkg, null))))
	}

	fun onPackagePrimaryAction(packageId: String) {
		when (val item = currentPackage(packageId)?.state) {
			UnifiedSourcePackageState.AVAILABLE,
			UnifiedSourcePackageState.UPDATE_AVAILABLE -> installPackage(packageId)

			UnifiedSourcePackageState.UNTRUSTED,
			UnifiedSourcePackageState.INCOMPATIBLE -> currentPackage(packageId)?.let {
				_events.tryEmit(UnifiedSourcesEvent.PackageStateDetails(it))
			}

			UnifiedSourcePackageState.INSTALLING,
			UnifiedSourcePackageState.INSTALLED,
			null -> Unit
		}
	}

	fun onUpdateAllPackagesAction() {
		if (updateAllInProgress.value) {
			cancelUpdateAll()
		} else {
			startUpdateAll()
		}
	}

	fun onInstallActivityResult() {
		handleBatchNextAction(batchUpdateState.onInstallActivityResult())
	}

	fun importLocalJar(uri: Uri) {
		launchLoadingJob(Dispatchers.IO) {
			val fileName = resolveDisplayName(uri)
				?.takeIf { it.isNotBlank() }
				?: "plugin_${System.currentTimeMillis()}.jar"
			val pluginsDir = File(appContext.filesDir, "plugins").apply { mkdirs() }
			val destinationFile = File(pluginsDir, fileName)
			appContext.contentResolver.openInputStream(uri)?.use { input ->
				destinationFile.outputStream().use { output ->
					input.copyTo(output)
				}
			} ?: throw IllegalArgumentException("Cannot open selected JAR")
			GlobalExtensionManager.initialize(appContext)
			emitMessage("Imported plugin: $fileName")
		}
	}

	fun addRepositoryFromUrl(kind: UnifiedSourceKind, url: String, title: String? = null) {
		val cleanUrl = url.trim()
		if (cleanUrl.isBlank()) return
		launchLoadingJob(Dispatchers.IO) {
			when (kind) {
				UnifiedSourceKind.LEGADO,
				UnifiedSourceKind.TVBOX,
				UnifiedSourceKind.JS -> importJsonRepository(
					kind = kind,
					content = fetchRemoteText(cleanUrl),
					sourceLocator = cleanUrl,
					sourceTitle = title,
				)
				UnifiedSourceKind.LNREADER -> addLnReaderRepository(cleanUrl)
				UnifiedSourceKind.MIHON,
				UnifiedSourceKind.ANIYOMI,
				UnifiedSourceKind.IREADER,
				UnifiedSourceKind.JAR -> prepareExternalRepository(kind, cleanUrl)
				UnifiedSourceKind.NATIVE -> emitMessage("Native sources do not use repositories")
			}
		}
	}

	fun addRepositoryFromFile(kind: UnifiedSourceKind, uri: Uri) {
		launchLoadingJob(Dispatchers.IO) {
			val title = resolveDisplayName(uri)
			val content = appContext.contentResolver.openInputStream(uri)
				?.bufferedReader()
				?.use { it.readText() }
				?: throw IllegalArgumentException("Cannot open selected file")
			importJsonRepository(
				kind = kind,
				content = content,
				sourceLocator = uri.toString(),
				sourceTitle = title,
			)
		}
	}

	fun addRepositoryFromInline(kind: UnifiedSourceKind, content: String, title: String? = null) {
		val cleanContent = content.trim()
		if (cleanContent.isBlank()) return
		launchLoadingJob(Dispatchers.Default) {
			val inlineLocator = title
				?.trim()
				?.takeIf { it.isNotBlank() }
				?: "inline:${kind.name.lowercase()}:${System.currentTimeMillis()}"
			importJsonRepository(
				kind = kind,
				content = cleanContent,
				sourceLocator = inlineLocator,
				sourceTitle = title,
			)
		}
	}

	fun refreshRepository(repositoryId: String) {
		val repository = (uiState.value as? UnifiedSourcesUiState.Ready)
			?.allRepositories
			?.firstOrNull { it.id == repositoryId }
			?: return
		launchLoadingJob(Dispatchers.IO) {
			when (repository.kind) {
				UnifiedSourceKind.LEGADO,
				UnifiedSourceKind.TVBOX,
				UnifiedSourceKind.JS -> {
					if (repository.locationType == UnifiedRepositoryLocationType.INLINE_IMPORT ||
						repository.locationType == UnifiedRepositoryLocationType.PRESET_ONLY
					) {
						emitMessage("This repository cannot be refreshed automatically")
						return@launchLoadingJob
					}
					importJsonRepository(
						kind = repository.kind,
						content = loadRepositoryText(repository.url),
						sourceLocator = repository.url,
						sourceTitle = repository.name,
					)
				}
				UnifiedSourceKind.LNREADER -> emitMessage("LNReader repositories are used by the plugin browser")
				UnifiedSourceKind.MIHON,
				UnifiedSourceKind.ANIYOMI,
				UnifiedSourceKind.IREADER,
				UnifiedSourceKind.JAR -> refreshExternalRepository(repository)
				UnifiedSourceKind.NATIVE -> emitMessage("Native sources do not use repositories")
			}
		}
	}

	fun deleteRepository(repositoryId: String) {
		val ready = uiState.value as? UnifiedSourcesUiState.Ready ?: return
		val repository = ready.allRepositories.firstOrNull { it.id == repositoryId } ?: return
		launchLoadingJob(Dispatchers.IO) {
			when (repository.kind) {
				UnifiedSourceKind.LEGADO,
				UnifiedSourceKind.TVBOX,
				UnifiedSourceKind.JS -> {
					val ids = ready.allSources
						.filter { it.repositoryId == repository.id }
						.map { it.id }
					if (ids.isNotEmpty()) {
						jsonSourceManager.deleteSourcesBatch(ids)
					}
					emitMessage("Repository sources deleted")
				}
				UnifiedSourceKind.LNREADER -> {
					settings.lnReaderRepoUrls = settings.lnReaderRepoUrls - repository.url
					emitMessage("Repository deleted")
				}
				UnifiedSourceKind.MIHON,
				UnifiedSourceKind.ANIYOMI,
				UnifiedSourceKind.IREADER,
				UnifiedSourceKind.JAR -> deleteExternalRepository(repository)
				UnifiedSourceKind.NATIVE -> emitMessage("Native sources do not use repositories")
			}
		}
	}

	fun confirmExternalRepository(repo: ExternalExtensionRepo) {
		launchLoadingJob(Dispatchers.IO) {
			when (val result = extensionRepoRepository.confirmAddRepo(repo)) {
				is ExternalExtensionRepoRepository.AddRepoResult.Success -> {
					emitMessage(appContext.getString(R.string.extension_repo_added_message, result.repo.displayName))
					extensionRepoRepository.refresh(repo.type)
					refreshAvailableExternalPackages(listOf(repo.type))
				}
				is ExternalExtensionRepoRepository.AddRepoResult.DuplicateFingerprint -> emitMessage(
					appContext.getString(
						R.string.extension_repo_duplicate_fingerprint_message,
						result.existingRepo.displayName,
					),
				)
				is ExternalExtensionRepoRepository.AddRepoResult.FetchFailed -> emitMessage(
					result.error.getDisplayMessage(appContext.resources),
				)
				ExternalExtensionRepoRepository.AddRepoResult.InvalidUrl -> emitMessage(
					appContext.getString(R.string.extension_repo_invalid_url_message),
				)
				ExternalExtensionRepoRepository.AddRepoResult.RepoAlreadyExists -> emitMessage(
					appContext.getString(R.string.extension_repo_already_exists_message),
				)
			}
		}
	}

	fun setSourceEnabled(sourceId: String, enabled: Boolean) {
		val source = (uiState.value as? UnifiedSourcesUiState.Ready)
			?.allSources
			?.firstOrNull { it.id == sourceId }
			?.source
			?: return
		viewModelScope.launch(Dispatchers.Default) {
			contentSourcesRepository.setSourcesEnabled(setOf(source), enabled)
		}
	}

	fun setSourcePinned(sourceId: String, pinned: Boolean) {
		val source = (uiState.value as? UnifiedSourcesUiState.Ready)
			?.allSources
			?.firstOrNull { it.id == sourceId }
			?.source
			?: return
		viewModelScope.launch(Dispatchers.Default) {
			contentSourcesRepository.setIsPinned(setOf(source), pinned)
		}
	}

	private fun currentPackage(packageId: String): UnifiedSourcePackageItem? {
		return (uiState.value as? UnifiedSourcesUiState.Ready)
			?.allPackages
			?.firstOrNull { it.id == packageId }
	}

	private fun requestInstall(item: UnifiedSourcePackageItem, fromBatch: Boolean) {
		val extension = item.installPayload ?: return
		if (extension.pkgName in installService.downloadStates.value) {
			return
		}
		if (fromBatch) {
			batchUpdateState.beginInstall(extension.pkgName)
		}
		launchLoadingJob(Dispatchers.IO) {
			try {
				val intent = installService.createInstallIntent(extension)
				if (fromBatch) {
					batchUpdateState.markInstallerIntentDispatched()
				}
				if (intent != null) {
					_events.emit(UnifiedSourcesEvent.StartInstall(intent))
				} else {
					emitMessage("Package installed")
					if (fromBatch) {
						handleBatchNextAction(batchUpdateState.onInstallActivityResult())
					}
				}
			} catch (e: CancellationException) {
				if (!fromBatch) {
					emitMessage(appContext.getString(R.string.canceled))
				}
				if (fromBatch) {
					handleBatchNextAction(batchUpdateState.onInstallInterrupted())
				}
			} catch (e: Throwable) {
				errorEvent.call(e)
				if (fromBatch) {
					emitMessage(appContext.getString(R.string.extension_update_failed, item.name))
					handleBatchNextAction(batchUpdateState.onInstallInterrupted())
				}
			}
		}
	}

	private fun startUpdateAll() {
		val updatePackages = currentUpdatePackages()
		if (!batchUpdateState.start(updatePackages.mapNotNull { it.packageName })) {
			viewModelScope.launch { emitMessage(appContext.getString(R.string.no_extension_updates_available)) }
			return
		}
		handleBatchNextAction(batchUpdateState.nextAction())
	}

	private fun cancelUpdateAll() {
		if (!updateAllInProgress.value) {
			return
		}
		batchUpdateState.cancel(installService::cancelDownload)
		viewModelScope.launch { emitMessage(appContext.getString(R.string.extension_update_all_cancelled)) }
	}

	private fun handleBatchNextAction(action: ExtensionBatchUpdateStateMachine.NextAction) {
		when (action) {
			ExtensionBatchUpdateStateMachine.NextAction.None -> Unit
			ExtensionBatchUpdateStateMachine.NextAction.Completed -> {
				viewModelScope.launch { emitMessage(appContext.getString(R.string.extension_update_all_complete)) }
			}
			is ExtensionBatchUpdateStateMachine.NextAction.InstallNext -> {
				val item = currentUpdatePackages().firstOrNull { it.packageName == action.packageName } ?: run {
					handleBatchNextAction(batchUpdateState.nextAction())
					return
				}
				requestInstall(item, fromBatch = true)
			}
		}
	}

	private fun currentUpdatePackages(): List<UnifiedSourcePackageItem> {
		return (uiState.value as? UnifiedSourcesUiState.Ready)
			?.allPackages
			.orEmpty()
			.filter { it.state == UnifiedSourcePackageState.UPDATE_AVAILABLE }
	}

	private suspend fun prepareExternalRepository(kind: UnifiedSourceKind, url: String) {
		val type = kind.toExternalExtensionType()
			?: throw IllegalArgumentException("Unsupported extension repository kind: $kind")
		when (val result = extensionRepoRepository.prepareAddRepo(type, url)) {
			is ExternalExtensionRepoRepository.PrepareAddRepoResult.Ready -> {
				_events.emit(UnifiedSourcesEvent.TrustExternalRepository(result.repo))
			}
			is ExternalExtensionRepoRepository.PrepareAddRepoResult.DuplicateFingerprint -> emitMessage(
				appContext.getString(
					R.string.extension_repo_duplicate_fingerprint_message,
					result.existingRepo.displayName,
				),
			)
			is ExternalExtensionRepoRepository.PrepareAddRepoResult.FetchFailed -> emitMessage(
				result.error.getDisplayMessage(appContext.resources),
			)
			ExternalExtensionRepoRepository.PrepareAddRepoResult.InvalidUrl -> emitMessage(
				appContext.getString(R.string.extension_repo_invalid_url_message),
			)
			ExternalExtensionRepoRepository.PrepareAddRepoResult.RepoAlreadyExists -> emitMessage(
				appContext.getString(R.string.extension_repo_already_exists_message),
			)
		}
	}

	private suspend fun addLnReaderRepository(url: String) {
		val current = settings.lnReaderRepoUrls
		if (url in current) {
			emitMessage("Repository already exists")
			return
		}
		settings.lnReaderRepoUrls = current + url
		emitMessage("Repository added")
	}

	private suspend fun refreshExternalRepository(repository: UnifiedSourceRepositoryItem) {
		val type = repository.kind.toExternalExtensionType()
			?: throw IllegalArgumentException("Unsupported extension repository kind: ${repository.kind}")
		val baseUrl = normalizeRepositoryUrlForAction(repository.url)
		val repo = extensionRepoRepository.getByType(type)
			.firstOrNull { normalizeRepositoryUrlForAction(it.baseUrl) == baseUrl }
		if (repo == null) {
			emitMessage("Repository is not configured")
			return
		}
		extensionRepoRepository.refresh(repo)
		refreshAvailableExternalPackages(listOf(type))
		emitMessage("Repository refreshed")
	}

	private suspend fun deleteExternalRepository(repository: UnifiedSourceRepositoryItem) {
		val type = repository.kind.toExternalExtensionType()
			?: throw IllegalArgumentException("Unsupported extension repository kind: ${repository.kind}")
		val baseUrl = normalizeRepositoryUrlForAction(repository.url)
		val repo = extensionRepoRepository.getByType(type)
			.firstOrNull { normalizeRepositoryUrlForAction(it.baseUrl) == baseUrl }
		if (repo == null) {
			emitMessage("Repository is not configured")
			return
		}
		extensionRepoRepository.delete(repo)
		refreshAvailableExternalPackages()
		emitMessage("Repository deleted")
	}

	private suspend fun importJsonRepository(
		kind: UnifiedSourceKind,
		content: String,
		sourceLocator: String?,
		sourceTitle: String?,
	) {
		val result = when (kind) {
			UnifiedSourceKind.LEGADO -> jsonSourceManager.importLegadoJson(
				jsonContent = content,
				sourceLocator = sourceLocator,
				sourceTitle = sourceTitle,
			)
			UnifiedSourceKind.TVBOX -> jsonSourceManager.importTvBoxJson(
				jsonContent = content,
				sourceLocator = sourceLocator,
				sourceTitle = sourceTitle,
			)
			UnifiedSourceKind.JS -> jsonSourceManager.importJsSource(content)
			UnifiedSourceKind.LNREADER -> jsonSourceManager.importLNReaderPlugin(content)
			else -> Result.failure(IllegalArgumentException("${kind.displayNameForMessage()} cannot import JSON content"))
		}
		result
			.onSuccess { count ->
				if (kind == UnifiedSourceKind.LNREADER &&
					!sourceLocator.isNullOrBlank() &&
					sourceLocator.startsWith("http", ignoreCase = true)
				) {
					settings.lnReaderRepoUrls = settings.lnReaderRepoUrls + sourceLocator
				}
				emitMessage("Imported $count source(s)")
			}
			.onFailure { error -> emitMessage(error.getDisplayMessage(appContext.resources)) }
	}

	private suspend fun loadRepositoryText(locator: String): String {
		return when (resolveRepositoryLocationTypeForAction(locator)) {
			UnifiedRepositoryLocationType.REMOTE_URL -> fetchRemoteText(locator)
			UnifiedRepositoryLocationType.LOCAL_FILE -> {
				appContext.contentResolver.openInputStream(Uri.parse(locator))
					?.bufferedReader()
					?.use { it.readText() }
					?: throw IllegalArgumentException("Cannot open repository file")
			}
			UnifiedRepositoryLocationType.INLINE_IMPORT,
			UnifiedRepositoryLocationType.PRESET_ONLY -> throw IllegalArgumentException("Repository cannot be refreshed")
		}
	}

	private suspend fun fetchRemoteText(url: String): String {
		val response = legadoHttpClient.get(url)
		return try {
			if (!response.isSuccessful) {
				throw IllegalArgumentException("HTTP ${response.code}")
			}
			response.body?.string()?.takeIf { it.isNotBlank() }
				?: throw IllegalArgumentException("Empty response body")
		} finally {
			response.close()
		}
	}

	private fun resolveDisplayName(uri: Uri): String? {
		return runCatching {
			appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
				?.use { cursor ->
					if (!cursor.moveToFirst()) return@use null
					val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
					cursor.getString(index.takeIf { it >= 0 } ?: return@use null)
				}
		}.getOrNull() ?: uri.lastPathSegment
	}

	private suspend fun emitMessage(message: String) {
		_events.emit(UnifiedSourcesEvent.Message(message))
	}

	private suspend fun refreshAvailableExternalPackages(types: List<ExternalExtensionType> = externalExtensionTypes()) {
		availableExternalExtensions.value = types
			.flatMap { type -> extensionRepoRepository.getCatalogExtensions(type) }
			.let { refreshed ->
				val refreshedTypes = types.toSet()
				availableExternalExtensions.value.filterNot { it.type in refreshedTypes } + refreshed
			}
	}

	private fun UnifiedSourceCatalogState.withAvailableExternalPackages(
		availableExtensions: List<RepoAvailableExtension>,
		downloadStates: Map<String, ExtensionInstallDownloadState>,
	): UnifiedSourceCatalogState {
		val externalInstalledPackages = packages
			.filter { it.kind.isExternalExtensionKind() && !it.packageName.isNullOrBlank() }
		val installedByKey = externalInstalledPackages.associateBy { item ->
			item.kind.toExternalExtensionType()?.normalizePackageNameForMatching(item.packageName.orEmpty())
		}
		val handledInstalledKeys = LinkedHashSet<String>()
		val availablePackages = availableExtensions.map { extension ->
			val installedKey = extension.type.normalizePackageNameForMatching(extension.pkgName)
			val installedPackage = installedByKey[installedKey]
			if (installedPackage != null) {
				handledInstalledKeys += installedKey
			}
			extension.toUnifiedPackageItem(
				installedPackage = installedPackage,
				downloadState = downloadStates[extension.pkgName],
			)
		}
		val installedWithoutCatalogMatch = packages.filterNot { item ->
			item.kind.isExternalExtensionKind() &&
				item.kind.toExternalExtensionType()?.normalizePackageNameForMatching(item.packageName.orEmpty()) in handledInstalledKeys
		}
		return copy(
			packages = (installedWithoutCatalogMatch + availablePackages)
				.sortedWith(compareBy({ it.kind.ordinal }, { it.state.sortOrder }, { it.name.lowercase() })),
		)
	}

	private fun RepoAvailableExtension.toUnifiedPackageItem(
		installedPackage: UnifiedSourcePackageItem?,
		downloadState: ExtensionInstallDownloadState?,
	): UnifiedSourcePackageItem {
		val isInstalled = installedPackage != null
		val isTrusted = installedPackage == null ||
			signatureValidator.isTrusted(installedPackage.packageName.orEmpty(), signatureHash)
		val state = when {
			downloadState != null -> UnifiedSourcePackageState.INSTALLING
			isInstalled && !isTrusted -> UnifiedSourcePackageState.UNTRUSTED
			!isCompatible -> UnifiedSourcePackageState.INCOMPATIBLE
			!isInstalled -> UnifiedSourcePackageState.AVAILABLE
			versionCode > (installedPackage.versionCode ?: 0L) ||
				libVersion > (installedPackage.libVersion ?: 0.0) -> UnifiedSourcePackageState.UPDATE_AVAILABLE
			else -> UnifiedSourcePackageState.INSTALLED
		}
		val kind = type.toUnifiedKindForPackage()
		return UnifiedSourcePackageItem(
			id = installedPackage?.id ?: packageIdForAction(kind, pkgName),
			kind = kind,
			name = name,
			packageName = pkgName,
			repositoryId = repositoryIdForAction(kind, repoUrl),
			repositoryName = repoName,
			versionName = versionName,
			versionCode = versionCode,
			libVersion = libVersion,
			language = lang.normalizeLanguageCode(),
			isInstalled = isInstalled,
			isNsfw = isNsfw,
			sourceCount = sourceNames.size,
			sourceNames = sourceNames,
			iconUrl = iconUrl.takeIf { it.isNotBlank() },
			state = state,
			installedVersionName = installedPackage?.versionName,
			installProgressPercent = downloadState?.progressPercent,
			installPayload = this,
		)
	}

	private fun UnifiedSourceCatalogState.toUiState(filters: UnifiedSourcesFilterState): UnifiedSourcesUiState.Ready {
		val repositoriesById = repositories.associateBy { it.id }
		val packagesById = packages.associateBy { it.id }
		val visibleRepositories = repositories.filterBy(filters)
		val visiblePackages = packages.filterBy(filters, repositoriesById)
		val visibleSources = sources.filterBy(filters, repositoriesById, packagesById)

		return UnifiedSourcesUiState.Ready(
			filters = filters,
			repositories = visibleRepositories,
			packages = visiblePackages,
			sources = visibleSources,
			allRepositories = repositories,
			allPackages = packages,
			allSources = sources,
			availableKinds = (repositories.map { it.kind } + packages.map { it.kind } + sources.map { it.kind })
				.distinct()
				.sortedBy { it.ordinal },
			availableContentTypes = sources.map { it.contentType }
				.distinct()
				.sortedBy { it.ordinal },
			availableLocationTypes = repositories.map { it.locationType }
				.distinct()
				.sortedBy { it.ordinal },
			availableLanguages = (packages.mapNotNull { it.language } + sources.mapNotNull { it.language })
				.map { it.normalizeLanguageCode() }
				.distinct()
				.sorted(),
		)
	}

	private fun List<UnifiedSourceRepositoryItem>.filterBy(
		filters: UnifiedSourcesFilterState,
	): List<UnifiedSourceRepositoryItem> {
		return asSequence()
			.filter { filters.kinds.isEmpty() || it.kind in filters.kinds }
			.filter { filters.locationTypes.isEmpty() || it.locationType in filters.locationTypes }
			.filter { filters.query.isBlank() || it.matchesQuery(filters.query) }
			.sortedWith(compareBy({ it.kind.ordinal }, { !it.isConfigured }, { it.name.lowercase() }))
			.toList()
	}

	private fun List<UnifiedSourcePackageItem>.filterBy(
		filters: UnifiedSourcesFilterState,
		repositoriesById: Map<String, UnifiedSourceRepositoryItem>,
	): List<UnifiedSourcePackageItem> {
		return asSequence()
			.filter { filters.kinds.isEmpty() || it.kind in filters.kinds }
			.filter { filters.locationTypes.isEmpty() || it.repositoryLocationType(repositoriesById) in filters.locationTypes }
			.filter { filters.languages.isEmpty() || it.language.matchesLanguageFilter(filters.languages) }
			.filter { filters.query.isBlank() || it.matchesQuery(filters.query) }
			.sortedWith(compareBy({ it.kind.ordinal }, { it.name.lowercase() }))
			.toList()
	}

	private fun List<UnifiedSourceItem>.filterBy(
		filters: UnifiedSourcesFilterState,
		repositoriesById: Map<String, UnifiedSourceRepositoryItem>,
		packagesById: Map<String, UnifiedSourcePackageItem>,
	): List<UnifiedSourceItem> {
		return asSequence()
			.filter { filters.kinds.isEmpty() || it.kind in filters.kinds }
			.filter { filters.contentTypes.isEmpty() || it.contentType in filters.contentTypes }
			.filter { filters.languages.isEmpty() || it.language.matchesLanguageFilter(filters.languages) }
			.filter {
				when (filters.enabledFilter) {
					UnifiedEnabledFilter.ALL -> true
					UnifiedEnabledFilter.ENABLED -> it.isEnabled
					UnifiedEnabledFilter.DISABLED -> !it.isEnabled
				}
			}
			.filter { filters.locationTypes.isEmpty() || it.repositoryLocationType(repositoriesById, packagesById) in filters.locationTypes }
			.filter { filters.query.isBlank() || it.matchesQuery(filters.query) }
			.sortedWith(compareByDescending<UnifiedSourceItem> { it.isPinned }.thenBy { it.title.lowercase() })
			.toList()
	}
}

sealed interface UnifiedSourcesEvent {
	data class Message(val message: String) : UnifiedSourcesEvent
	data class TrustExternalRepository(val repo: ExternalExtensionRepo) : UnifiedSourcesEvent
	data class StartInstall(val intent: Intent) : UnifiedSourcesEvent
	data class StartUninstall(val intent: Intent) : UnifiedSourcesEvent
	data class PackageStateDetails(val item: UnifiedSourcePackageItem) : UnifiedSourcesEvent
}

data class UnifiedSourcesFilterState(
	val query: String = "",
	val kinds: Set<UnifiedSourceKind> = emptySet(),
	val contentTypes: Set<ContentType> = emptySet(),
	val languages: Set<String> = emptySet(),
	val locationTypes: Set<UnifiedRepositoryLocationType> = emptySet(),
	val enabledFilter: UnifiedEnabledFilter = UnifiedEnabledFilter.ALL,
)

enum class UnifiedEnabledFilter {
	ALL,
	ENABLED,
	DISABLED,
}

sealed interface UnifiedSourcesUiState {
	data object Loading : UnifiedSourcesUiState

	data class Ready(
		val filters: UnifiedSourcesFilterState,
		val repositories: List<UnifiedSourceRepositoryItem>,
		val packages: List<UnifiedSourcePackageItem>,
		val sources: List<UnifiedSourceItem>,
		val allRepositories: List<UnifiedSourceRepositoryItem>,
		val allPackages: List<UnifiedSourcePackageItem>,
		val allSources: List<UnifiedSourceItem>,
		val availableKinds: List<UnifiedSourceKind>,
		val availableContentTypes: List<ContentType>,
		val availableLocationTypes: List<UnifiedRepositoryLocationType>,
		val availableLanguages: List<String>,
	) : UnifiedSourcesUiState
}

private fun <T> Set<T>.toggle(value: T): Set<T> {
	return if (value in this) this - value else this + value
}

private fun UnifiedSourceRepositoryItem.matchesQuery(query: String): Boolean {
	return name.contains(query, ignoreCase = true) ||
		url.contains(query, ignoreCase = true) ||
		website.contains(query, ignoreCase = true)
}

private fun UnifiedSourcePackageItem.matchesQuery(query: String): Boolean {
	return name.contains(query, ignoreCase = true) ||
		packageName.orEmpty().contains(query, ignoreCase = true) ||
		repositoryName.orEmpty().contains(query, ignoreCase = true) ||
		sourceNames.any { it.contains(query, ignoreCase = true) }
}

private fun UnifiedSourceItem.matchesQuery(query: String): Boolean {
	return title.contains(query, ignoreCase = true) ||
		id.contains(query, ignoreCase = true) ||
		packageName.orEmpty().contains(query, ignoreCase = true) ||
		repositoryName.orEmpty().contains(query, ignoreCase = true)
}

private fun UnifiedSourcePackageItem.repositoryLocationType(
	repositoriesById: Map<String, UnifiedSourceRepositoryItem>,
): UnifiedRepositoryLocationType? {
	return repositoryId?.let(repositoriesById::get)?.locationType
}

private fun UnifiedSourceItem.repositoryLocationType(
	repositoriesById: Map<String, UnifiedSourceRepositoryItem>,
	packagesById: Map<String, UnifiedSourcePackageItem>,
): UnifiedRepositoryLocationType? {
	repositoryId?.let(repositoriesById::get)?.locationType?.let { return it }
	val packageRepositoryId = packageId?.let(packagesById::get)?.repositoryId
	return packageRepositoryId?.let(repositoriesById::get)?.locationType
}

private fun String?.matchesLanguageFilter(languages: Set<String>): Boolean {
	val normalized = this?.normalizeLanguageCode().orEmpty()
	return normalized.isBlank() || normalized in languages
}

private fun String.normalizeLanguageCode(): String {
	return if (equals("all", ignoreCase = true)) "" else lowercase()
}

private fun UnifiedSourceKind.toExternalExtensionType(): ExternalExtensionType? {
	return when (this) {
		UnifiedSourceKind.MIHON -> ExternalExtensionType.MIHON
		UnifiedSourceKind.ANIYOMI -> ExternalExtensionType.ANIYOMI
		UnifiedSourceKind.IREADER -> ExternalExtensionType.IREADER
		UnifiedSourceKind.JAR -> ExternalExtensionType.JAR
		else -> null
	}
}

private fun ExternalExtensionType.toUnifiedKindForPackage(): UnifiedSourceKind {
	return when (this) {
		ExternalExtensionType.MIHON -> UnifiedSourceKind.MIHON
		ExternalExtensionType.ANIYOMI -> UnifiedSourceKind.ANIYOMI
		ExternalExtensionType.IREADER -> UnifiedSourceKind.IREADER
		ExternalExtensionType.JAR -> UnifiedSourceKind.JAR
	}
}

private fun UnifiedSourceKind.isExternalExtensionKind(): Boolean {
	return when (this) {
		UnifiedSourceKind.JAR,
		UnifiedSourceKind.MIHON,
		UnifiedSourceKind.ANIYOMI,
		UnifiedSourceKind.IREADER -> true
		else -> false
	}
}

private val UnifiedSourcePackageState.sortOrder: Int
	get() = when (this) {
		UnifiedSourcePackageState.UPDATE_AVAILABLE -> 0
		UnifiedSourcePackageState.UNTRUSTED -> 1
		UnifiedSourcePackageState.INCOMPATIBLE -> 2
		UnifiedSourcePackageState.INSTALLING -> 3
		UnifiedSourcePackageState.INSTALLED -> 4
		UnifiedSourcePackageState.AVAILABLE -> 5
	}

private fun externalExtensionTypes(): List<ExternalExtensionType> {
	return listOf(
		ExternalExtensionType.JAR,
		ExternalExtensionType.MIHON,
		ExternalExtensionType.ANIYOMI,
		ExternalExtensionType.IREADER,
	)
}

private fun repositoryIdForAction(kind: UnifiedSourceKind, url: String): String {
	return "repo:${kind.name}:${normalizeRepositoryUrlForAction(url)}"
}

private fun packageIdForAction(kind: UnifiedSourceKind, value: String): String {
	return "package:${kind.name}:${value.trim()}"
}

private fun UnifiedSourceKind.displayNameForMessage(): String {
	return when (this) {
		UnifiedSourceKind.NATIVE -> "Native"
		UnifiedSourceKind.JAR -> "JAR"
		UnifiedSourceKind.MIHON -> "Mihon"
		UnifiedSourceKind.ANIYOMI -> "Aniyomi"
		UnifiedSourceKind.IREADER -> "IReader"
		UnifiedSourceKind.LEGADO -> "Legado"
		UnifiedSourceKind.TVBOX -> "TVBox"
		UnifiedSourceKind.JS -> "JS"
		UnifiedSourceKind.LNREADER -> "LNReader"
	}
}

private fun normalizeRepositoryUrlForAction(url: String): String {
	return url.trim()
		.trimEnd('/')
		.removeSuffix("/index.min.json")
		.trimEnd('/')
}

private fun resolveRepositoryLocationTypeForAction(locator: String): UnifiedRepositoryLocationType {
	return when {
		locator.startsWith("content://", ignoreCase = true) -> UnifiedRepositoryLocationType.LOCAL_FILE
		locator.startsWith("file://", ignoreCase = true) -> UnifiedRepositoryLocationType.LOCAL_FILE
		locator.startsWith("http://", ignoreCase = true) -> UnifiedRepositoryLocationType.REMOTE_URL
		locator.startsWith("https://", ignoreCase = true) -> UnifiedRepositoryLocationType.REMOTE_URL
		else -> UnifiedRepositoryLocationType.INLINE_IMPORT
	}
}
