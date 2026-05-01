package org.skepsun.kototoro.settings.sources.unified

import android.content.Context
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.skepsun.kototoro.aniyomi.AniyomiExtensionManager
import org.skepsun.kototoro.core.db.MangaDatabase
import org.skepsun.kototoro.core.db.entity.JsonSourceEntity
import org.skepsun.kototoro.core.db.entity.JsonSourceType
import org.skepsun.kototoro.core.jsonsource.JsonSourceManager
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.explore.data.ContentSourcesRepository
import org.skepsun.kototoro.extensions.repo.ExternalExtensionRepoRepository
import org.skepsun.kototoro.ireader.IReaderExtensionManager
import org.skepsun.kototoro.mihon.MihonExtensionManager

class UnifiedSourceCatalogRepositoryTest : FunSpec({

	test("lnreader package ids stay unique when plugin metadata ids repeat") {
		val packages = testRepository().invokeToJsonPackageItems(
			listOf(
				lnReaderEntity(id = "JSON_LNREADER_ALPHA", name = "Alpha"),
				lnReaderEntity(id = "JSON_LNREADER_BETA", name = "Beta"),
			),
		)

		packages.map { it.id }.distinct() shouldHaveSize 2
		packages.map { it.packageName } shouldBe listOf("+a+", "+a+")
		packages.map { it.sourceNames } shouldBe listOf(listOf("Alpha"), listOf("Beta"))
	}
})

@Suppress("UNCHECKED_CAST")
private fun UnifiedSourceCatalogRepository.invokeToJsonPackageItems(
	sources: List<JsonSourceEntity>,
): List<UnifiedSourcePackageItem> {
	val method = javaClass
		.getDeclaredMethod("toJsonPackageItems", List::class.java)
	method.isAccessible = true
	return method.invoke(this, sources) as List<UnifiedSourcePackageItem>
}

private fun testRepository(): UnifiedSourceCatalogRepository {
	return UnifiedSourceCatalogRepository(
		appContext = mockk<Context>(relaxed = true),
		localizedContext = mockk<Context>(relaxed = true),
		database = mockk<MangaDatabase>(relaxed = true),
		settings = mockk<AppSettings>(relaxed = true),
		contentSourcesRepository = mockk<ContentSourcesRepository>(relaxed = true),
		jsonSourceManager = mockk<JsonSourceManager>(relaxed = true),
		extensionRepoRepository = mockk<ExternalExtensionRepoRepository>(relaxed = true),
		mihonExtensionManager = mockk<MihonExtensionManager>(relaxed = true),
		aniyomiExtensionManager = mockk<AniyomiExtensionManager>(relaxed = true),
		ireaderExtensionManager = mockk<IReaderExtensionManager>(relaxed = true),
		json = Json,
	)
}

private fun lnReaderEntity(id: String, name: String): JsonSourceEntity {
	return JsonSourceEntity(
		id = id,
		name = name,
		type = JsonSourceType.LNREADER,
		config = """
			export default {
				id: '+a+',
				name: '$name',
				site: 'https://example.org/$name',
				version: '1.0.0',
				lang: 'en'
			}
		""".trimIndent(),
		createdAt = 1L,
		updatedAt = 1L,
	)
}
