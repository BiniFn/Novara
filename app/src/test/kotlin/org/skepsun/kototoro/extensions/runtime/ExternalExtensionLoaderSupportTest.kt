package org.skepsun.kototoro.extensions.runtime

import android.content.pm.PackageManager
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ExternalExtensionLoaderSupportTest {

	@Test
	fun `extractLanguage returns marker suffix or all`() {
		assertEquals(
			"en",
			ExternalExtensionLoaderSupport.extractLanguage(
				"eu.kanade.tachiyomi.extension.en.mangadex",
				"extension",
			),
		)
		assertEquals(
			"ja",
			ExternalExtensionLoaderSupport.extractLanguage(
				"eu.kanade.tachiyomi.animeextension.ja.somepkg",
				"animeextension",
			),
		)
		assertEquals(
			"all",
			ExternalExtensionLoaderSupport.extractLanguage(
				"org.example.no.marker",
				"extension",
			),
		)
	}

	@Test
	fun `getPackageInfoOrNull returns null when package is missing`() {
		val packageManager = mockk<PackageManager>()
		every {
			packageManager.getPackageInfo(any<String>(), any<Int>())
		} throws PackageManager.NameNotFoundException("missing")

		val result = ExternalExtensionLoaderSupport.getPackageInfoOrNull(packageManager, "missing.pkg")

		assertNull(result)
	}
}
