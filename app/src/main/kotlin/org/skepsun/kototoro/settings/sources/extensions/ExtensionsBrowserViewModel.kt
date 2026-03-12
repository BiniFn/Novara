package org.skepsun.kototoro.settings.sources.extensions

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.skepsun.kototoro.R
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
	private val repoRepository: ExternalExtensionRepoRepository,
	private val installService: ExtensionInstallService,
	private val signatureValidator: InstalledExtensionSignatureValidator,
	private val mihonExtensionManager: MihonExtensionManager,
	private val aniyomiExtensionManager: AniyomiExtensionManager,
) : BaseViewModel() {

	val type: ExternalExtensionType = enumValueOf(savedStateHandle.require<String>(ARG_EXTENSION_TYPE))

	private val availableExtensions = MutableStateFlow<List<RepoAvailableExtension>>(emptyList())
	private val installingPackages = MutableStateFlow<Set<String>>(emptySet())
	private val searchQuery = MutableStateFlow("")

	val currentSearchQuery: StateFlow<String> = searchQuery

	val repoCount: StateFlow<Int> = repoRepository.observeByType(type)
		.map { it.size }
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

	private val installedExtensions: StateFlow<List<InstalledExtensionEntry>> = when (type) {
		ExternalExtensionType.MIHON -> mihonExtensionManager.installedExtensions.map { list ->
			list.map { ext ->
				InstalledExtensionEntry(
					pkgName = ext.pkgName,
					name = ext.appName,
					versionName = ext.versionName,
					versionCode = ext.versionCode,
					libVersion = ext.libVersion,
					lang = ext.lang,
					isNsfw = ext.isNsfw,
					sourceNames = ext.sources.map { it.name },
				)
			}
		}
		ExternalExtensionType.ANIYOMI -> aniyomiExtensionManager.installedExtensions.map { list ->
			list.map { ext ->
				InstalledExtensionEntry(
					pkgName = ext.pkgName,
					name = ext.appName,
					versionName = ext.versionName,
					versionCode = ext.versionCode,
					libVersion = ext.libVersion,
					lang = ext.lang,
					isNsfw = ext.isNsfw,
					sourceNames = ext.sources.map { it.name },
				)
			}
		}
	}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

	val installedCount: StateFlow<Int> = installedExtensions.map { it.size }
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

	val updateCount: StateFlow<Int> = combine(installedExtensions, availableExtensions) { installed, available ->
		val installedMap = installed.associateBy { it.pkgName }
		available.count { extension ->
			val installedEntry = installedMap[extension.pkgName] ?: return@count false
			extension.versionCode > installedEntry.versionCode || extension.libVersion > installedEntry.libVersion
		}
	}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

	val availableCount: StateFlow<Int> = combine(installedExtensions, availableExtensions) { installed, available ->
		val installedPackages = installed.mapTo(HashSet()) { it.pkgName }
		available.count { it.pkgName !in installedPackages }
	}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

	val items: StateFlow<List<ExtensionsBrowserListItem>> = combine(
		installedExtensions,
		availableExtensions,
		installingPackages,
		searchQuery,
	) { installed, available, installing, query ->
		buildItems(installed, available, installing, query)
	}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

	val onInstallIntent = MutableEventFlow<Intent>()
	val onUninstallIntent = MutableEventFlow<Intent>()
	val onStateDetails = MutableEventFlow<ExtensionsBrowserListItem.Entry>()

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
		if (item.state == ExtensionsBrowserEntryState.INSTALLED || item.extension.pkgName in installingPackages.value) {
			return
		}
		launchLoadingJob(Dispatchers.IO) {
			installingPackages.value = installingPackages.value + item.extension.pkgName
			try {
				onInstallIntent.call(installService.createInstallIntent(item.extension))
			} finally {
				installingPackages.value = installingPackages.value - item.extension.pkgName
			}
		}
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

	fun uninstall(item: ExtensionsBrowserListItem.Entry) {
		if (item.state == ExtensionsBrowserEntryState.INSTALLING) {
			return
		}
		val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			Intent.ACTION_DELETE
		} else {
			@Suppress("DEPRECATION")
			Intent.ACTION_UNINSTALL_PACKAGE
		}
		onUninstallIntent.call(
			Intent(action, Uri.fromParts("package", item.pkgName, null)),
		)
	}

	fun setSearchQuery(query: String?) {
		searchQuery.value = query?.trim().orEmpty()
	}

	private fun buildItems(
		installed: List<InstalledExtensionEntry>,
		available: List<RepoAvailableExtension>,
		installing: Set<String>,
		query: String,
	): List<ExtensionsBrowserListItem> {
		val installedMap = installed.associateBy { it.pkgName }
		val updates = mutableListOf<ExtensionsBrowserListItem.Entry>()
		val untrusted = mutableListOf<ExtensionsBrowserListItem.Entry>()
		val incompatible = mutableListOf<ExtensionsBrowserListItem.Entry>()
		val availableOnly = mutableListOf<ExtensionsBrowserListItem.Entry>()
		val handledPackages = HashSet<String>()

		available.forEach { extension ->
			val installedEntry = installedMap[extension.pkgName]
			val isInstalling = extension.pkgName in installing
			val isTrusted = installedEntry == null || signatureValidator.isTrusted(extension.pkgName, extension.signatureHash)
			when {
				installedEntry != null && !isTrusted -> {
					handledPackages += extension.pkgName
					untrusted += ExtensionsBrowserListItem.Entry(
						pkgName = extension.pkgName,
						name = extension.name,
						versionName = extension.versionName,
						language = extension.lang,
						isNsfw = extension.isNsfw,
						sourceNames = extension.sourceNames,
						repoLabel = extension.repoName,
						installedVersionName = installedEntry.versionName,
						state = ExtensionsBrowserEntryState.UNTRUSTED,
						extension = extension,
					)
				}

				!extension.isCompatible -> {
					handledPackages += extension.pkgName
					incompatible += ExtensionsBrowserListItem.Entry(
						pkgName = extension.pkgName,
						name = extension.name,
						versionName = extension.versionName,
						language = extension.lang,
						isNsfw = extension.isNsfw,
						sourceNames = extension.sourceNames,
						repoLabel = extension.repoName,
						installedVersionName = installedEntry?.versionName,
						state = ExtensionsBrowserEntryState.INCOMPATIBLE,
						extension = extension,
					)
				}

				installedEntry == null -> {
					availableOnly += ExtensionsBrowserListItem.Entry(
						pkgName = extension.pkgName,
						name = extension.name,
						versionName = extension.versionName,
						language = extension.lang,
						isNsfw = extension.isNsfw,
						sourceNames = extension.sourceNames,
						repoLabel = extension.repoName,
						installedVersionName = null,
						state = if (isInstalling) ExtensionsBrowserEntryState.INSTALLING else ExtensionsBrowserEntryState.AVAILABLE,
						extension = extension,
					)
				}

				extension.versionCode > installedEntry.versionCode || extension.libVersion > installedEntry.libVersion -> {
					handledPackages += extension.pkgName
					updates += ExtensionsBrowserListItem.Entry(
						pkgName = extension.pkgName,
						name = extension.name,
						versionName = extension.versionName,
						language = extension.lang,
						isNsfw = extension.isNsfw,
						sourceNames = extension.sourceNames,
						repoLabel = extension.repoName,
						installedVersionName = installedEntry.versionName,
						state = if (isInstalling) ExtensionsBrowserEntryState.INSTALLING else ExtensionsBrowserEntryState.UPDATE_AVAILABLE,
						extension = extension,
					)
				}
			}
		}

		val installedOnly = installed
			.filter { it.pkgName !in handledPackages }
			.map { entry ->
				ExtensionsBrowserListItem.Entry(
					pkgName = entry.pkgName,
					name = entry.name,
					versionName = entry.versionName,
					language = entry.lang,
					isNsfw = entry.isNsfw,
					sourceNames = entry.sourceNames,
					repoLabel = "",
					installedVersionName = entry.versionName,
					state = ExtensionsBrowserEntryState.INSTALLED,
					extension = RepoAvailableExtension(
						type = type,
						name = entry.name,
						pkgName = entry.pkgName,
						versionName = entry.versionName,
						versionCode = entry.versionCode,
						libVersion = entry.libVersion,
						lang = entry.lang,
						isNsfw = entry.isNsfw,
						sourceNames = entry.sourceNames,
						apkName = "",
						iconUrl = "",
						repoUrl = "",
						repoName = "",
						signatureHash = "",
						isCompatible = true,
					),
				)
			}

		val filteredUpdates = updates.filterByQuery(query)
		val filteredUntrusted = untrusted.filterByQuery(query)
		val filteredIncompatible = incompatible.filterByQuery(query)
		val filteredInstalled = installedOnly.filterByQuery(query)
		val filteredAvailable = availableOnly.filterByQuery(query)

		return buildList {
			if (filteredUpdates.isNotEmpty()) {
				add(ExtensionsBrowserListItem.Header(ExtensionsBrowserSection.UPDATES, filteredUpdates.size))
				addAll(filteredUpdates)
			}
			if (filteredUntrusted.isNotEmpty()) {
				add(ExtensionsBrowserListItem.Header(ExtensionsBrowserSection.UNTRUSTED, filteredUntrusted.size))
				addAll(filteredUntrusted)
			}
			if (filteredIncompatible.isNotEmpty()) {
				add(ExtensionsBrowserListItem.Header(ExtensionsBrowserSection.INCOMPATIBLE, filteredIncompatible.size))
				addAll(filteredIncompatible)
			}
			if (filteredInstalled.isNotEmpty()) {
				add(ExtensionsBrowserListItem.Header(ExtensionsBrowserSection.INSTALLED, filteredInstalled.size))
				addAll(filteredInstalled)
			}
			if (filteredAvailable.isNotEmpty()) {
				add(ExtensionsBrowserListItem.Header(ExtensionsBrowserSection.AVAILABLE, filteredAvailable.size))
				addAll(filteredAvailable)
			}
		}
	}
}

sealed interface ExtensionsBrowserListItem {
	data class Header(
		val section: ExtensionsBrowserSection,
		val count: Int,
	) : ExtensionsBrowserListItem

	data class Entry(
		val pkgName: String,
		val name: String,
		val versionName: String,
		val language: String,
		val isNsfw: Boolean,
		val sourceNames: List<String>,
		val repoLabel: String,
		val installedVersionName: String?,
		val state: ExtensionsBrowserEntryState,
		val extension: RepoAvailableExtension,
	) : ExtensionsBrowserListItem
}

enum class ExtensionsBrowserEntryState {
	AVAILABLE,
	UPDATE_AVAILABLE,
	INSTALLED,
	INSTALLING,
	UNTRUSTED,
	INCOMPATIBLE,
}

enum class ExtensionsBrowserSection(@StringRes val titleRes: Int) {
	UPDATES(R.string.updates_section),
	UNTRUSTED(R.string.untrusted_section),
	INCOMPATIBLE(R.string.incompatible_section),
	INSTALLED(R.string.installed_section),
	AVAILABLE(R.string.available_section),
}

private fun List<ExtensionsBrowserListItem.Entry>.filterByQuery(query: String): List<ExtensionsBrowserListItem.Entry> {
	if (query.isBlank()) return this
	return filter { entry ->
		entry.name.contains(query, ignoreCase = true) ||
			entry.pkgName.contains(query, ignoreCase = true) ||
			entry.repoLabel.contains(query, ignoreCase = true) ||
			entry.extension.repoUrl.contains(query, ignoreCase = true) ||
			entry.sourceNames.any { it.contains(query, ignoreCase = true) }
	}
}

private data class InstalledExtensionEntry(
	val pkgName: String,
	val name: String,
	val versionName: String,
	val versionCode: Long,
	val libVersion: Double,
	val lang: String,
	val isNsfw: Boolean,
	val sourceNames: List<String>,
)
