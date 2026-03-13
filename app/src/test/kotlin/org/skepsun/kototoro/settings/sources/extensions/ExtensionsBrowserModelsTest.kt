package org.skepsun.kototoro.settings.sources.extensions

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.skepsun.kototoro.extensions.install.ExtensionInstallDownloadState
import org.skepsun.kototoro.extensions.repo.ExternalExtensionType
import org.skepsun.kototoro.extensions.repo.RepoAvailableExtension

class ExtensionsBrowserModelsTest : FunSpec({

	test("buildExtensionsBrowserItems groups updates untrusted incompatible installed and available entries") {
		val items = buildExtensionsBrowserItems(
			type = ExternalExtensionType.MIHON,
			installed = listOf(
				installedEntry("pkg.update"),
				installedEntry("pkg.untrusted"),
				installedEntry("pkg.installed"),
			),
			available = listOf(
				availableExtension("pkg.update", "Update Source", versionName = "1.3.0", versionCode = 2),
				availableExtension("pkg.untrusted", "Untrusted Source", versionName = "1.3.0", versionCode = 2),
				availableExtension("pkg.incompatible", "Old Source", versionName = "1.1.0", isCompatible = false),
				availableExtension("pkg.available", "New Source"),
			),
			downloadStates = mapOf(
				"pkg.update" to ExtensionInstallDownloadState("pkg.update", bytesRead = 50L, contentLength = 100L),
			),
			languageFilter = ExtensionsLanguageFilter.All,
			selectedContentLanguages = setOf("en"),
			collapsedLanguageGroups = emptySet(),
			query = "",
			isTrustedPackage = { packageName, _ -> packageName != "pkg.untrusted" },
		)

		items.filterIsInstance<ExtensionsBrowserListItem.SectionHeader>().map { it.section } shouldContainExactly listOf(
			ExtensionsBrowserSection.UPDATES,
			ExtensionsBrowserSection.UNTRUSTED,
			ExtensionsBrowserSection.INCOMPATIBLE,
			ExtensionsBrowserSection.INSTALLED,
			ExtensionsBrowserSection.AVAILABLE,
		)

		val entries = items.filterIsInstance<ExtensionsBrowserListItem.Entry>()
		entries shouldHaveSize 5
		entries[0].pkgName shouldBe "pkg.update"
		entries[0].state shouldBe ExtensionsBrowserEntryState.INSTALLING
		entries[0].installProgressPercent shouldBe 50
		entries[1].pkgName shouldBe "pkg.untrusted"
		entries[1].state shouldBe ExtensionsBrowserEntryState.UNTRUSTED
		entries[2].pkgName shouldBe "pkg.incompatible"
		entries[2].state shouldBe ExtensionsBrowserEntryState.INCOMPATIBLE
		entries[3].pkgName shouldBe "pkg.installed"
		entries[3].state shouldBe ExtensionsBrowserEntryState.INSTALLED
		entries[4].pkgName shouldBe "pkg.available"
		entries[4].state shouldBe ExtensionsBrowserEntryState.AVAILABLE
	}

	test("buildExtensionsBrowserItems filters by repository label and source name") {
		val items = buildExtensionsBrowserItems(
			type = ExternalExtensionType.MIHON,
			installed = emptyList(),
			available = listOf(
				availableExtension("pkg.alpha", "Alpha", repoName = "Repo Alpha", sourceNames = listOf("Foo Source")),
				availableExtension("pkg.beta", "Beta", repoName = "Repo Beta", sourceNames = listOf("Bar Source")),
			),
			downloadStates = emptyMap(),
			languageFilter = ExtensionsLanguageFilter.All,
			selectedContentLanguages = setOf("en"),
			collapsedLanguageGroups = emptySet(),
			query = "foo",
			isTrustedPackage = { _, _ -> true },
		)

		items.filterIsInstance<ExtensionsBrowserListItem.SectionHeader>().map { it.section } shouldContainExactly listOf(
			ExtensionsBrowserSection.AVAILABLE,
		)
		items.filterIsInstance<ExtensionsBrowserListItem.Entry>().map { it.pkgName } shouldContainExactly listOf("pkg.alpha")
	}

	test("buildExtensionsBrowserItems filters by selected content languages") {
		val items = buildExtensionsBrowserItems(
			type = ExternalExtensionType.MIHON,
			installed = listOf(
				installedEntry("pkg.multi", lang = "all"),
				installedEntry("pkg.zh", lang = "zh"),
			),
			available = listOf(
				availableExtension("pkg.multi", "Multi Source", lang = "all"),
				availableExtension("pkg.zh.available", "Chinese Source", lang = "zh"),
			),
			downloadStates = emptyMap(),
			languageFilter = ExtensionsLanguageFilter.SelectedContent,
			selectedContentLanguages = setOf("zh"),
			collapsedLanguageGroups = emptySet(),
			query = "",
			isTrustedPackage = { _, _ -> true },
		)

		items.filterIsInstance<ExtensionsBrowserListItem.Entry>().map { it.pkgName } shouldContainExactly listOf(
			"pkg.zh",
			"pkg.multi",
			"pkg.zh.available",
		)
		items.filterIsInstance<ExtensionsBrowserListItem.SectionHeader>()
			.first { it.section == ExtensionsBrowserSection.INSTALLED }
			.count shouldBe 2
	}

	test("normalizeExtensionLanguageCode maps all to multi-language bucket") {
		"all".normalizeExtensionLanguageCode() shouldBe ""
		"ALL".normalizeExtensionLanguageCode() shouldBe ""
		"zh".normalizeExtensionLanguageCode() shouldBe "zh"
	}

	test("buildExtensionsBrowserItems creates language headers and respects collapsed groups") {
		val collapsedGroup = ExtensionsLanguageGroupKey(ExtensionsBrowserSection.AVAILABLE, "zh")
		val items = buildExtensionsBrowserItems(
			type = ExternalExtensionType.MIHON,
			installed = emptyList(),
			available = listOf(
				availableExtension("pkg.zh.one", "Chinese One", lang = "zh"),
				availableExtension("pkg.zh.two", "Chinese Two", lang = "zh"),
				availableExtension("pkg.en.one", "English One", lang = "en"),
			),
			downloadStates = emptyMap(),
			languageFilter = ExtensionsLanguageFilter.All,
			selectedContentLanguages = setOf("zh"),
			collapsedLanguageGroups = setOf(collapsedGroup),
			query = "",
			isTrustedPackage = { _, _ -> true },
		)

		val languageHeaders = items.filterIsInstance<ExtensionsBrowserListItem.LanguageHeader>()
		languageHeaders.shouldHaveSize(2)
		languageHeaders.first { it.language == "zh" }.isCollapsed shouldBe true
		items.filterIsInstance<ExtensionsBrowserListItem.Entry>().map { it.pkgName } shouldContainExactly listOf("pkg.en.one")
		items.find { it is ExtensionsBrowserListItem.LanguageHeader && it.language == "zh" } shouldNotBe null
	}

	test("multi-language card contributes weighted counts while remaining a single card") {
		val items = buildExtensionsBrowserItems(
			type = ExternalExtensionType.MIHON,
			installed = listOf(
				installedEntry("pkg.multi", lang = "all", sourceNames = listOf("源 A", "源 B")),
			),
			available = emptyList(),
			downloadStates = emptyMap(),
			languageFilter = ExtensionsLanguageFilter.All,
			selectedContentLanguages = setOf("zh", "ja"),
			collapsedLanguageGroups = emptySet(),
			query = "",
			isTrustedPackage = { _, _ -> true },
		)

		items.filterIsInstance<ExtensionsBrowserListItem.Entry>().shouldHaveSize(1)
		items.filterIsInstance<ExtensionsBrowserListItem.SectionHeader>()
			.first { it.section == ExtensionsBrowserSection.INSTALLED }
			.count shouldBe 2
		items.filterIsInstance<ExtensionsBrowserListItem.LanguageHeader>()
			.first { it.language.isBlank() }
			.count shouldBe 2
	}
})

private fun installedEntry(
	packageName: String,
	lang: String = "en",
	sourceNames: List<String> = listOf("Source $packageName"),
): InstalledExtensionEntry {
	return InstalledExtensionEntry(
		pkgName = packageName,
		name = packageName.substringAfter('.'),
		versionName = "1.2.0",
		versionCode = 1L,
		libVersion = 1.2,
		lang = lang,
		isNsfw = false,
		sourceNames = sourceNames,
	)
}

private fun availableExtension(
	packageName: String,
	name: String,
	versionName: String = "1.2.0",
	versionCode: Long = 1L,
	isCompatible: Boolean = true,
	repoName: String = "Repo",
	lang: String = "en",
	sourceNames: List<String> = listOf(name),
): RepoAvailableExtension {
	return RepoAvailableExtension(
		type = ExternalExtensionType.MIHON,
		name = name,
		pkgName = packageName,
		versionName = versionName,
		versionCode = versionCode,
		libVersion = versionName.substringBeforeLast('.').toDouble(),
		lang = lang,
		isNsfw = false,
		sourceNames = sourceNames,
		apkName = "$packageName.apk",
		iconUrl = "",
		repoUrl = "https://example.org/repo",
		repoName = repoName,
		signatureHash = "aa",
		isCompatible = isCompatible,
	)
}
