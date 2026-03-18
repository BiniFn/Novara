package org.skepsun.kototoro.extensions.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExternalExtensionSourceLoaderSupportTest {

	@Test
	fun `resolveSourceClassNames expands relative names and ignores blanks`() {
		val result = ExternalExtensionSourceLoaderSupport.resolveSourceClassNames(
			pkgName = "org.example.pkg",
			sourceClassNames = " .Foo ;org.example.Bar;; ",
		)

		assertEquals(
			listOf("org.example.pkg.Foo", "org.example.Bar"),
			result,
		)
	}

	@Test
	fun `loadSources supports direct source and factory results`() {
		val result = ExternalExtensionSourceLoaderSupport.loadSources(
			pkgName = "org.skepsun.kototoro.extensions.runtime",
			sourceClassNames = ".ExternalExtensionSourceLoaderSupportTest\$FakeDirectSource;.ExternalExtensionSourceLoaderSupportTest\$FakeFactory",
			classLoader = requireNotNull(javaClass.classLoader),
			asSource = { it as? FakeSource },
			createSourcesFromFactory = { (it as? FakeFactoryContract)?.createSources() },
		)

		assertEquals(
			listOf("direct", "factory-a", "factory-b"),
			result.map { it.id },
		)
	}

	interface FakeSource {
		val id: String
	}

	interface FakeFactoryContract {
		fun createSources(): List<FakeSource>
	}

	class FakeDirectSource : FakeSource {
		override val id: String = "direct"
	}

	class FakeFactory : FakeFactoryContract {
		override fun createSources(): List<FakeSource> {
			return listOf(
				FakeFactorySource("factory-a"),
				FakeFactorySource("factory-b"),
			)
		}
	}

	data class FakeFactorySource(
		override val id: String,
	) : FakeSource
}
