package org.skepsun.kototoro.extensions.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExternalExtensionRuntimeProcessorTest {

	@Test
	fun `processExternalExtensionResults groups outcomes and adds language suffix only for duplicate names`() {
		val errors = mutableListOf<FakeError>()
		val untrusted = mutableListOf<String>()
		val processed = processExternalExtensionResults(
			results = listOf(
				FakeResult.Success(
					pkgName = "pkg.one",
					isNsfw = false,
					sources = listOf(
						FakeSource(id = 1L, name = "Alpha", lang = "en", isCatalogue = true),
						FakeSource(id = 2L, name = "Alpha", lang = "zh", isCatalogue = true),
						FakeSource(id = 3L, name = "Hidden", lang = "en", isCatalogue = false),
					),
				),
				FakeResult.Success(
					pkgName = "pkg.two",
					isNsfw = true,
					sources = listOf(
						FakeSource(id = 4L, name = "Beta", lang = "ja", isCatalogue = true),
					),
				),
				FakeResult.Error(FakeError("pkg.err", "broken")),
				FakeResult.Untrusted("pkg.untrusted"),
			),
			successOf = { it as? FakeResult.Success },
			errorOf = { (it as? FakeResult.Error)?.error },
			untrustedPackageNameOf = { (it as? FakeResult.Untrusted)?.pkgName },
			successSources = { it.sources },
			successPackageName = { it.pkgName },
			successIsNsfw = { it.isNsfw },
			sourceId = { it.id },
			asCatalogueSource = { it.takeIf(FakeSource::isCatalogue) },
			catalogueSourceName = { it.name },
			buildWrappedSource = { source, pkgName, isNsfw, hasLanguageSuffix ->
				FakeWrappedSource(
					id = source.id,
					pkgName = pkgName,
					isNsfw = isNsfw,
					hasLanguageSuffix = hasLanguageSuffix,
				)
			},
			onError = errors::add,
			onUntrusted = untrusted::add,
		)

		assertEquals(2, processed.successful.size)
		assertEquals(listOf(FakeError("pkg.err", "broken")), processed.failed)
		assertEquals(setOf(1L, 2L, 3L, 4L), processed.sourceById.keys)
		assertEquals(setOf(1L, 2L, 4L), processed.wrappedSourceById.keys)
		assertTrue(processed.wrappedSourceById.getValue(1L).hasLanguageSuffix)
		assertTrue(processed.wrappedSourceById.getValue(2L).hasLanguageSuffix)
		assertFalse(processed.wrappedSourceById.getValue(4L).hasLanguageSuffix)
		assertEquals(listOf("pkg.untrusted"), processed.untrustedPackages)
		assertEquals(listOf(FakeError("pkg.err", "broken")), errors)
		assertEquals(listOf("pkg.untrusted"), untrusted)
	}

	@Test
	fun `getExternalExtensionLanguageDisplayName falls back to uppercase`() {
		assertEquals("English", getExternalExtensionLanguageDisplayName("en"))
		assertEquals("XX", getExternalExtensionLanguageDisplayName("xx"))
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
		val isNsfw: Boolean,
		val hasLanguageSuffix: Boolean,
	)
}
