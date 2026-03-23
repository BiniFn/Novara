package org.skepsun.kototoro.settings.sources.extensions

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.observeAsFlow
import org.skepsun.kototoro.aniyomi.AniyomiExtensionManager
import org.skepsun.kototoro.core.ui.BaseViewModel
import org.skepsun.kototoro.core.util.ext.MutableEventFlow
import org.skepsun.kototoro.core.util.ext.call
import org.skepsun.kototoro.core.util.ext.require
import org.skepsun.kototoro.extensions.install.ExtensionInstallService
import org.skepsun.kototoro.extensions.repo.ExternalExtensionRepoRepository
import org.skepsun.kototoro.extensions.repo.ExternalExtensionType
import org.skepsun.kototoro.extensions.repo.InstalledExtensionSignatureValidator
import org.skepsun.kototoro.extensions.repo.RepoAvailableExtension
import org.skepsun.kototoro.mihon.MihonExtensionManager
import javax.inject.Inject

@HiltViewModel
class ExtensionsBrowserViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	@ApplicationContext private val appContext: Context,
	private val settings: AppSettings,
	private val repoRepository: ExternalExtensionRepoRepository,
	private val installService: ExtensionInstallService,
	private val signatureValidator: InstalledExtensionSignatureValidator,
	private val mihonExtensionManager: MihonExtensionManager,
	private val aniyomiExtensionManager: AniyomiExtensionManager,
	private val ireaderExtensionManager: org.skepsun.kototoro.ireader.IReaderExtensionManager,
) : BaseViewModel() {

	val type: ExternalExtensionType = enumValueOf(savedStateHandle.require<String>(ARG_EXTENSION_TYPE))

	private val availableExtensions = MutableStateFlow<List<RepoAvailableExtension>>(emptyList())
	private val searchQuery = MutableStateFlow("")
	private val languageFilter = MutableStateFlow(
		if (settings.isExtensionsFilterLangEnabled) {
			ExtensionsLanguageFilter.SelectedContent
		} else {
			ExtensionsLanguageFilter.All
		},
	)
	private val collapsedLanguageGroups = MutableStateFlow<Set<ExtensionsLanguageGroupKey>>(emptySet())
	private val batchUpdateState = ExtensionBatchUpdateStateMachine()

	val currentSearchQuery: StateFlow<String> = searchQuery
	val updateAllInProgress: StateFlow<Boolean> = batchUpdateState.inProgress
	val currentLanguageFilter: StateFlow<ExtensionsLanguageFilter> = languageFilter
	val currentCollapsedLanguageGroups: StateFlow<Set<ExtensionsLanguageGroupKey>> = collapsedLanguageGroups

	private val selectedContentLanguages: StateFlow<Set<String>> = settings.observeAsFlow(
		AppSettings.KEY_CONTENT_LANGUAGES,
	) { contentLanguages }
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), settings.contentLanguages)

	val repoCount: StateFlow<Int> = repoRepository.observeByType(type)
		.map { it.size }
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

	private val installedExtensions: StateFlow<List<InstalledExtensionEntry>> = observeInstalledExtensionEntries(
		type = type,
		mihonExtensionManager = mihonExtensionManager,
		aniyomiExtensionManager = aniyomiExtensionManager,
		ireaderExtensionManager = ireaderExtensionManager,
	).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

	private val browserInputsBase: StateFlow<BrowserInputs> = combine(
		installedExtensions,
		availableExtensions,
		installService.downloadStates,
		languageFilter,
		selectedContentLanguages,
	) { installed, available, downloads, currentLanguageFilter, selectedLanguages ->
		BrowserInputs(
			installed = installed,
			available = available,
			downloads = downloads,
			languageFilter = currentLanguageFilter,
			selectedLanguages = selectedLanguages,
			collapsedGroups = emptySet(),
		)
	}.stateIn(
		viewModelScope,
		SharingStarted.WhileSubscribed(5000),
		BrowserInputs(
			installed = emptyList(),
			available = emptyList(),
			downloads = emptyMap(),
			languageFilter = languageFilter.value,
			selectedLanguages = selectedContentLanguages.value,
			collapsedGroups = emptySet(),
		),
	)

	private val browserInputs: StateFlow<BrowserInputs> = combine(
		browserInputsBase,
		collapsedLanguageGroups,
	) { base, collapsedGroups ->
		base.copy(collapsedGroups = collapsedGroups)
	}.stateIn(
		viewModelScope,
		SharingStarted.WhileSubscribed(5000),
		browserInputsBase.value.copy(collapsedGroups = collapsedLanguageGroups.value),
	)

	val items: StateFlow<List<ExtensionsBrowserListItem>> = combine(
		browserInputs,
		searchQuery,
	) { inputs, query ->
		buildExtensionsBrowserItems(
			type = type,
			installed = inputs.installed,
			available = inputs.available,
			downloadStates = inputs.downloads,
			languageFilter = inputs.languageFilter,
			selectedContentLanguages = inputs.selectedLanguages,
			collapsedLanguageGroups = inputs.collapsedGroups,
			query = query,
			isTrustedPackage = signatureValidator::isTrusted,
		)
	}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

	val installedCount: StateFlow<Int> = items.map { list ->
		list.filterIsInstance<ExtensionsBrowserListItem.SectionHeader>()
			.firstOrNull { it.section == ExtensionsBrowserSection.INSTALLED }
			?.count ?: 0
	}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

	val updateCount: StateFlow<Int> = items.map { list ->
		list.filterIsInstance<ExtensionsBrowserListItem.SectionHeader>()
			.firstOrNull { it.section == ExtensionsBrowserSection.UPDATES }
			?.count ?: 0
	}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

	val availableCount: StateFlow<Int> = items.map { list ->
		list.filterIsInstance<ExtensionsBrowserListItem.SectionHeader>()
			.firstOrNull { it.section == ExtensionsBrowserSection.AVAILABLE }
			?.count ?: 0
	}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

	val availableLanguageCodes: StateFlow<List<String>> = combine(installedExtensions, availableExtensions) { installed, available ->
		(installed.asSequence().map { it.lang.normalizeExtensionLanguageCode() } + available.asSequence().map { it.lang.normalizeExtensionLanguageCode() })
			.filter { it.isNotBlank() || it == "" }
			.distinct()
			.sorted()
			.toList()
	}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

	val onInstallIntent = MutableEventFlow<Intent>()
	val onUninstallIntent = MutableEventFlow<Intent>()
	val onStateDetails = MutableEventFlow<ExtensionsBrowserListItem.Entry>()
	val onMessage = MutableEventFlow<String>()

	init {
		refresh()
	}

	fun refresh() {
		launchLoadingJob(Dispatchers.IO) {
			repoRepository.refresh(type)
			availableExtensions.value = repoRepository.getCatalogExtensions(type)
		}
	}

	fun install(item: ExtensionsBrowserListItem.Entry) {
		if (item.state == ExtensionsBrowserEntryState.INSTALLED || item.extension.pkgName in installService.downloadStates.value) {
			return
		}
		requestInstall(item, fromBatch = false)
	}

	fun onPrimaryAction(item: ExtensionsBrowserListItem.Entry) {
		when (item.state) {
			ExtensionsBrowserEntryState.AVAILABLE,
			ExtensionsBrowserEntryState.UPDATE_AVAILABLE -> install(item)

			ExtensionsBrowserEntryState.UNTRUSTED,
			ExtensionsBrowserEntryState.INCOMPATIBLE -> onStateDetails.call(item)

			ExtensionsBrowserEntryState.INSTALLING,
			ExtensionsBrowserEntryState.INSTALLED -> Unit
		}
	}

	fun cancelInstall(item: ExtensionsBrowserListItem.Entry) {
		if (batchUpdateState.shouldCancelCurrent(item.pkgName)) {
			cancelUpdateAll()
			return
		}
		installService.cancelDownload(item.pkgName)
	}

	fun uninstall(item: ExtensionsBrowserListItem.Entry) {
		if (item.state == ExtensionsBrowserEntryState.INSTALLING) {
			return
		}

		val uninstallPkg = if (item.extension.type == ExternalExtensionType.IREADER && item.pkgName.startsWith("ireader-")) {
			val parts = item.pkgName.split("-")
			if (parts.size >= 3) {
				val namePart = parts.drop(2).joinToString("-")
				"ireader.${namePart}.${parts[1]}"
			} else item.pkgName
		} else item.pkgName

		val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			Intent.ACTION_DELETE
		} else {
			@Suppress("DEPRECATION")
			Intent.ACTION_UNINSTALL_PACKAGE
		}
		onUninstallIntent.call(
			Intent(action, Uri.fromParts("package", uninstallPkg, null)),
		)
	}

	fun setSearchQuery(query: String?) {
		searchQuery.value = query?.trim().orEmpty()
	}

	fun setLanguageFilter(filter: ExtensionsLanguageFilter) {
		languageFilter.value = filter
	}

	fun toggleLanguageGroup(item: ExtensionsBrowserListItem.LanguageHeader) {
		val key = ExtensionsLanguageGroupKey(item.section, item.language)
		collapsedLanguageGroups.value = collapsedLanguageGroups.value.toMutableSet().apply {
			if (!add(key)) {
				remove(key)
			}
		}
	}

	fun onUpdateAllAction() {
		if (updateAllInProgress.value) {
			cancelUpdateAll()
		} else {
			startUpdateAll()
		}
	}

	fun onInstallActivityResult() {
		handleBatchNextAction(batchUpdateState.onInstallActivityResult())
	}

	private fun requestInstall(item: ExtensionsBrowserListItem.Entry, fromBatch: Boolean) {
		if (item.extension.pkgName in installService.downloadStates.value) {
			return
		}
		if (fromBatch) {
			batchUpdateState.beginInstall(item.pkgName)
		}
		launchLoadingJob(Dispatchers.IO) {
			try {
				val intent = installService.createInstallIntent(item.extension)
				if (fromBatch) {
					batchUpdateState.markInstallerIntentDispatched()
				}
				if (intent != null) {
					onInstallIntent.call(intent)
				}
			} catch (e: CancellationException) {
				if (!fromBatch) {
					onMessage.call(appContext.getString(R.string.canceled))
				}
				if (fromBatch) {
					handleBatchNextAction(batchUpdateState.onInstallInterrupted())
				}
			} catch (e: Throwable) {
				errorEvent.call(e)
				if (fromBatch) {
					onMessage.call(appContext.getString(R.string.extension_update_failed, item.name))
					handleBatchNextAction(batchUpdateState.onInstallInterrupted())
				}
			}
		}
	}

	private fun startUpdateAll() {
		val updatePackages = currentUpdateEntries().map { it.pkgName }
		if (!batchUpdateState.start(updatePackages)) {
			onMessage.call(appContext.getString(R.string.no_extension_updates_available))
			return
		}
		handleBatchNextAction(batchUpdateState.nextAction())
	}

	private fun cancelUpdateAll() {
		if (!updateAllInProgress.value) {
			return
		}
		batchUpdateState.cancel(installService::cancelDownload)
		onMessage.call(appContext.getString(R.string.extension_update_all_cancelled))
	}

	private fun handleBatchNextAction(action: ExtensionBatchUpdateStateMachine.NextAction) {
		when (action) {
			ExtensionBatchUpdateStateMachine.NextAction.None -> Unit
			ExtensionBatchUpdateStateMachine.NextAction.Completed -> {
				onMessage.call(appContext.getString(R.string.extension_update_all_complete))
			}
			is ExtensionBatchUpdateStateMachine.NextAction.InstallNext -> {
				val item = currentUpdateEntries().firstOrNull { it.pkgName == action.packageName } ?: run {
					handleBatchNextAction(batchUpdateState.nextAction())
					return
				}
				requestInstall(item, fromBatch = true)
			}
		}
	}

	private fun currentUpdateEntries(): List<ExtensionsBrowserListItem.Entry> {
		return buildExtensionsBrowserItems(
			type = type,
			installed = browserInputs.value.installed,
			available = browserInputs.value.available,
			downloadStates = browserInputs.value.downloads,
			languageFilter = browserInputs.value.languageFilter,
			selectedContentLanguages = browserInputs.value.selectedLanguages,
			collapsedLanguageGroups = browserInputs.value.collapsedGroups,
			query = "",
			isTrustedPackage = signatureValidator::isTrusted,
		).filterIsInstance<ExtensionsBrowserListItem.Entry>()
			.filter { it.state == ExtensionsBrowserEntryState.UPDATE_AVAILABLE }
	}

	private data class BrowserInputs(
		val installed: List<InstalledExtensionEntry>,
		val available: List<RepoAvailableExtension>,
		val downloads: Map<String, org.skepsun.kototoro.extensions.install.ExtensionInstallDownloadState>,
		val languageFilter: ExtensionsLanguageFilter,
		val selectedLanguages: Set<String>,
		val collapsedGroups: Set<ExtensionsLanguageGroupKey>,
	)
}
