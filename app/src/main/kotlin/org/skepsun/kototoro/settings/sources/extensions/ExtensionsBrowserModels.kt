package org.skepsun.kototoro.settings.sources.extensions

import androidx.annotation.StringRes
import org.skepsun.kototoro.R
import org.skepsun.kototoro.extensions.install.ExtensionInstallDownloadState
import org.skepsun.kototoro.extensions.repo.ExternalExtensionType
import org.skepsun.kototoro.extensions.repo.RepoAvailableExtension

internal data class InstalledExtensionEntry(
	val pkgName: String,
	val name: String,
	val versionName: String,
	val versionCode: Long,
	val libVersion: Double,
	val lang: String,
	val isNsfw: Boolean,
	val sourceNames: List<String>,
)

sealed interface ExtensionsBrowserListItem {
	data class SectionHeader(
		val section: ExtensionsBrowserSection,
		val count: Int,
	) : ExtensionsBrowserListItem

	data class LanguageHeader(
		val section: ExtensionsBrowserSection,
		val language: String,
		val count: Int,
		val isCollapsed: Boolean,
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
		val installProgressPercent: Int?,
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

	data class ExtensionsLanguageGroupKey(
	val section: ExtensionsBrowserSection,
	val language: String,
)

internal fun buildExtensionsBrowserItems(
	type: ExternalExtensionType,
	installed: List<InstalledExtensionEntry>,
	available: List<RepoAvailableExtension>,
	downloadStates: Map<String, ExtensionInstallDownloadState>,
	selectedExtensionLanguages: Set<String>,
	collapsedLanguageGroups: Set<ExtensionsLanguageGroupKey>,
	query: String,
	isTrustedPackage: (packageName: String, expectedFingerprint: String) -> Boolean,
): List<ExtensionsBrowserListItem> {
	val installedMap = installed.associateBy { type.normalizePackageNameForMatching(it.pkgName) }
	val updates = mutableListOf<ExtensionsBrowserListItem.Entry>()
	val untrusted = mutableListOf<ExtensionsBrowserListItem.Entry>()
	val incompatible = mutableListOf<ExtensionsBrowserListItem.Entry>()
	val availableOnly = mutableListOf<ExtensionsBrowserListItem.Entry>()
	val handledPackages = HashSet<String>()

	available.forEach { extension ->
		val installedSearchKey = type.normalizePackageNameForMatching(extension.pkgName)

		val normalizedLanguage = extension.lang.normalizeExtensionLanguageCode()
		val installedEntry = installedMap[installedSearchKey]
		val downloadState = downloadStates[extension.pkgName]
		val isDownloading = downloadState != null
		val isTrusted = installedEntry == null || isTrustedPackage(installedEntry.pkgName, extension.signatureHash)
		when {
			installedEntry != null && !isTrusted -> {
				handledPackages += installedSearchKey
				untrusted += ExtensionsBrowserListItem.Entry(
					pkgName = extension.pkgName,
					name = extension.name,
					versionName = extension.versionName,
					language = normalizedLanguage,
					isNsfw = extension.isNsfw,
					sourceNames = extension.sourceNames,
					repoLabel = extension.repoName,
					installedVersionName = installedEntry.versionName,
					state = ExtensionsBrowserEntryState.UNTRUSTED,
					extension = extension,
					installProgressPercent = null,
				)
			}

			!extension.isCompatible -> {
				handledPackages += installedSearchKey
				incompatible += ExtensionsBrowserListItem.Entry(
					pkgName = extension.pkgName,
					name = extension.name,
					versionName = extension.versionName,
					language = normalizedLanguage,
					isNsfw = extension.isNsfw,
					sourceNames = extension.sourceNames,
					repoLabel = extension.repoName,
					installedVersionName = installedEntry?.versionName,
					state = ExtensionsBrowserEntryState.INCOMPATIBLE,
					extension = extension,
					installProgressPercent = null,
				)
			}

			installedEntry == null -> {
				availableOnly += ExtensionsBrowserListItem.Entry(
					pkgName = extension.pkgName,
					name = extension.name,
					versionName = extension.versionName,
					language = normalizedLanguage,
					isNsfw = extension.isNsfw,
					sourceNames = extension.sourceNames,
					repoLabel = extension.repoName,
					installedVersionName = null,
					state = if (isDownloading) ExtensionsBrowserEntryState.INSTALLING else ExtensionsBrowserEntryState.AVAILABLE,
					extension = extension,
					installProgressPercent = downloadState?.progressPercent,
				)
			}

			extension.versionCode > installedEntry.versionCode || extension.libVersion > installedEntry.libVersion -> {
				handledPackages += installedSearchKey
				updates += ExtensionsBrowserListItem.Entry(
					pkgName = extension.pkgName,
					name = extension.name,
					versionName = extension.versionName,
					language = normalizedLanguage,
					isNsfw = extension.isNsfw,
					sourceNames = extension.sourceNames,
					repoLabel = extension.repoName,
					installedVersionName = installedEntry.versionName,
					state = if (isDownloading) ExtensionsBrowserEntryState.INSTALLING else ExtensionsBrowserEntryState.UPDATE_AVAILABLE,
					extension = extension,
					installProgressPercent = downloadState?.progressPercent,
				)
			}
		}
	}

	val installedOnly = installed
		.filter { entry -> type.normalizePackageNameForMatching(entry.pkgName) !in handledPackages }
		.map { entry ->
			ExtensionsBrowserListItem.Entry(
				pkgName = entry.pkgName,
				name = entry.name,
				versionName = entry.versionName,
				language = entry.lang.normalizeExtensionLanguageCode(),
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
				installProgressPercent = null,
			)
		}

	val allowedLanguages = if (selectedExtensionLanguages.isEmpty()) {
		null
	} else {
		selectedExtensionLanguages.mapTo(LinkedHashSet()) { it.normalizeExtensionLanguageCode() }
	}
	val filteredUpdates = updates.filterByQuery(query)
		.filterByLanguage(allowedLanguages)
	val filteredUntrusted = untrusted.filterByQuery(query)
		.filterByLanguage(allowedLanguages)
	val filteredIncompatible = incompatible.filterByQuery(query)
		.filterByLanguage(allowedLanguages)
	val filteredInstalled = installedOnly.filterByQuery(query)
		.filterByLanguage(allowedLanguages)
	val filteredAvailable = availableOnly.filterByQuery(query)
		.filterByLanguage(allowedLanguages)

	return buildList {
		addSection(ExtensionsBrowserSection.UPDATES, filteredUpdates, collapsedLanguageGroups, selectedExtensionLanguages)
		addSection(ExtensionsBrowserSection.UNTRUSTED, filteredUntrusted, collapsedLanguageGroups, selectedExtensionLanguages)
		addSection(ExtensionsBrowserSection.INCOMPATIBLE, filteredIncompatible, collapsedLanguageGroups, selectedExtensionLanguages)
		addSection(ExtensionsBrowserSection.INSTALLED, filteredInstalled, collapsedLanguageGroups, selectedExtensionLanguages)
		addSection(ExtensionsBrowserSection.AVAILABLE, filteredAvailable, collapsedLanguageGroups, selectedExtensionLanguages)
	}
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

private fun List<ExtensionsBrowserListItem.Entry>.filterByLanguage(allowedLanguages: Set<String>?): List<ExtensionsBrowserListItem.Entry> {
	if (allowedLanguages == null) return this
	return filter { it.language.isBlank() || it.language in allowedLanguages }
}

internal fun String.normalizeExtensionLanguageCode(): String {
	return if (equals("all", ignoreCase = true)) {
		""
	} else {
		lowercase()
	}
}

private fun MutableList<ExtensionsBrowserListItem>.addSection(
	section: ExtensionsBrowserSection,
	entries: List<ExtensionsBrowserListItem.Entry>,
	collapsedLanguageGroups: Set<ExtensionsLanguageGroupKey>,
	selectedContentLanguages: Set<String>,
) {
	if (entries.isEmpty()) {
		return
	}
	add(ExtensionsBrowserListItem.SectionHeader(section, entries.sumOf { it.countWeight(selectedContentLanguages) }))
	entries.groupEntriesByLanguage().forEach { (language, groupedEntries) ->
		val key = ExtensionsLanguageGroupKey(section, language)
		val isCollapsed = key in collapsedLanguageGroups
		add(
			ExtensionsBrowserListItem.LanguageHeader(
				section = section,
				language = language,
				count = groupedEntries.sumOf { it.countWeight(selectedContentLanguages) },
				isCollapsed = isCollapsed,
			),
		)
		if (!isCollapsed) {
			addAll(groupedEntries)
		}
	}
}

private fun List<ExtensionsBrowserListItem.Entry>.groupEntriesByLanguage(): List<Pair<String, List<ExtensionsBrowserListItem.Entry>>> {
	return groupBy { it.language }
		.toList()
		.sortedWith(
			compareBy<Pair<String, List<ExtensionsBrowserListItem.Entry>>> { it.first.isBlank() }
				.thenBy { it.first },
		)
}

private fun ExtensionsBrowserListItem.Entry.countWeight(selectedContentLanguages: Set<String>): Int {
	return if (language.isBlank()) {
		selectedContentLanguages.count { it.isNotBlank() }.coerceAtLeast(1)
	} else {
		1
	}
}
