package org.skepsun.kototoro.extensions.runtime

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
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

	@Test
	fun `looksLikeMihonPackage matches known extension naming patterns`() {
		assertTrue(ExternalExtensionLoaderSupport.looksLikeMihonPackage("eu.kanade.tachiyomi.extension.en.mangadex"))
		assertTrue(ExternalExtensionLoaderSupport.looksLikeMihonPackage("org.keiyoushi.extension.zh.example"))
		assertTrue(ExternalExtensionLoaderSupport.looksLikeMihonPackage("dev.yuzono.foo.extension.bar"))
		assertFalse(ExternalExtensionLoaderSupport.looksLikeMihonPackage("org.example.reader"))
	}

	@Test
	fun `looksLikeAniyomiPackage matches anime extension package prefix`() {
		assertTrue(ExternalExtensionLoaderSupport.looksLikeAniyomiPackage("eu.kanade.tachiyomi.animeextension.en.zoro"))
		assertTrue(ExternalExtensionLoaderSupport.looksLikeAniyomiPackage("dev.yuzono.foo.animeextension.bar"))
		assertFalse(ExternalExtensionLoaderSupport.looksLikeAniyomiPackage("eu.kanade.tachiyomi.extension.en.mangadex"))
	}

	@Test
	fun `refreshPackageInfoIfNeeded reloads incomplete package info`() {
		val packageManager = mockk<PackageManager>()
		val originalAppInfo = mockk<ApplicationInfo>()
		every { originalAppInfo.metaData } returns null
		val original = mockk<PackageInfo>()
		every { original.applicationInfo } returns originalAppInfo
		every { original.reqFeatures } returns null
		every { original.packageName } returns "eu.kanade.tachiyomi.extension.en.example"

		val refreshed = mockk<PackageInfo>()
		every {
			packageManager.getPackageInfo(
				"eu.kanade.tachiyomi.extension.en.example",
				ExternalExtensionLoaderSupport.packageQueryFlags,
			)
		} returns refreshed

		assertSame(
			refreshed,
			ExternalExtensionLoaderSupport.refreshPackageInfoIfNeeded(packageManager, original),
		)
	}

	@Test
	fun `refreshPackageInfoIfNeeded keeps complete package info`() {
		val packageManager = mockk<PackageManager>()
		val appInfo = mockk<ApplicationInfo>()
		every { appInfo.metaData } returns mockk()
		val pkgInfo = mockk<PackageInfo>()
		every { pkgInfo.applicationInfo } returns appInfo
		every { pkgInfo.reqFeatures } returns emptyArray()

		assertSame(pkgInfo, ExternalExtensionLoaderSupport.refreshPackageInfoIfNeeded(packageManager, pkgInfo))
	}
}
