package org.skepsun.kototoro.extensions.runtime

import android.content.Context
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExternalExtensionManagerFacadeTest {

	@Test
	fun `loadExtensions populates caches and supports shared lookup helpers`() = runBlocking {
		val sourceA = FakeSource(id = 1L, name = "Alpha", lang = "en", isCatalogue = true)
		val sourceB = FakeSource(id = 2L, name = "Beta", lang = "ja", isCatalogue = true)
		val sourceC = FakeSource(id = 3L, name = "Hidden", lang = "en", isCatalogue = false)
		val wrappedA = FakeWrappedSource(id = 1L, pkgName = "pkg.one", hasLanguageSuffix = false)
		val wrappedB = FakeWrappedSource(id = 2L, pkgName = "pkg.one", hasLanguageSuffix = false)
		val facade = ExternalExtensionManagerFacade<
			FakeResult,
			FakeResult.Success,
			FakeError,
			FakeSource,
			FakeSource,
			FakeWrappedSource
		>(
			context = mockk<Context>(relaxed = true),
			scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
			logTag = "TestFacade",
			ecosystem = "fake",
			sourceNamePrefix = "FAKE_",
			loadResults = {
				listOf(
					FakeResult.Success(
						pkgName = "pkg.one",
						isNsfw = false,
						sources = listOf(sourceA, sourceB, sourceC),
					),
					FakeResult.Error(FakeError("pkg.err", "broken")),
					FakeResult.Untrusted("pkg.untrusted"),
				)
			},
			successOf = { it as? FakeResult.Success },
			errorOf = { (it as? FakeResult.Error)?.error },
			untrustedPackageNameOf = { (it as? FakeResult.Untrusted)?.pkgName },
			successSources = { it.sources },
			successPackageName = { it.pkgName },
			successIsNsfw = { it.isNsfw },
			successCatalogueSources = { it.sources.filter(FakeSource::isCatalogue) },
			sourceId = { it.id },
			asCatalogueSource = { it.takeIf(FakeSource::isCatalogue) },
			catalogueSourceName = { it.name },
			catalogueSourceLang = { it.lang },
			buildWrappedSource = { source, pkgName, _, hasLanguageSuffix ->
				when (source.id) {
					1L -> wrappedA.copy(hasLanguageSuffix = hasLanguageSuffix)
					2L -> wrappedB.copy(hasLanguageSuffix = hasLanguageSuffix)
					else -> FakeWrappedSource(source.id, pkgName, hasLanguageSuffix)
				}
			},
			errorPackageName = { it.pkgName },
			errorMessage = { it.message },
		)

		assertFalse(facade.hasExtensions())
		facade.loadExtensions()

		assertTrue(facade.hasExtensions())
		assertEquals(1, facade.getInstalledExtensions().size)
		assertEquals(1, facade.failedExtensions.value.size)
		assertEquals(3, facade.getSourceCount())
		assertEquals(listOf(sourceA, sourceB), facade.getCatalogueSources())
		assertEquals(listOf("en", "ja"), facade.getSourcesByLanguage().keys.sorted())
		assertSame(sourceA, facade.getSourceById(1L))
		assertSame(sourceB, facade.getCatalogueSourceById(2L))
		assertNull(facade.getCatalogueSourceById(3L))
		assertEquals(listOf(1L, 2L), facade.getWrappedSources().map { it.id }.sorted())
		assertEquals("pkg.one", facade.getWrappedSourceByName("FAKE_1")?.pkgName)
		assertNull(facade.getWrappedSourceByName("OTHER_1"))
	}

	private sealed interface FakeResult {
		data class Success(
			val pkgName: String,
			val isNsfw: Boolean,
			val sources: List<FakeSource>,
		) : FakeResult

		data class Error(val error: FakeError) : FakeResult

		data class Untrusted(val pkgName: String) : FakeResult
	}

	private data class FakeError(
		val pkgName: String,
		val message: String,
	)

	private data class FakeSource(
		val id: Long,
		val name: String,
		val lang: String,
		val isCatalogue: Boolean,
	)

	private data class FakeWrappedSource(
		val id: Long,
		val pkgName: String,
		val hasLanguageSuffix: Boolean,
	)
}
