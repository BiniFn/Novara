package org.skepsun.kototoro.local.data.importer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalImportSupportTest {

	@Test
	fun `supports media and novel files in addition to archives`() {
		assertTrue(LocalImportSupport.supportsFileName("episode-01.mp4"))
		assertTrue(LocalImportSupport.supportsFileName("volume-01.epub"))
		assertTrue(LocalImportSupport.supportsFileName("chapter-01.txt"))
		assertTrue(LocalImportSupport.supportsFileName("series.cbz"))
		assertFalse(LocalImportSupport.supportsFileName("cover.jpg"))
	}

	@Test
	fun `classifies file names by media kind`() {
		assertEquals(LocalImportKind.VIDEO, LocalImportSupport.classifyFileName("episode.mkv"))
		assertEquals(LocalImportKind.NOVEL, LocalImportSupport.classifyFileName("chapter.txt"))
		assertEquals(LocalImportKind.MANGA, LocalImportSupport.classifyFileName("archive.cbz"))
	}

	@Test
	fun `derives stable folder name from imported file name`() {
		assertEquals("episode-01", LocalImportSupport.contentFolderName("episode-01.mp4"))
		assertEquals("volume-01", LocalImportSupport.contentFolderName("volume-01.epub"))
		assertEquals("README", LocalImportSupport.contentFolderName("README"))
	}
}
