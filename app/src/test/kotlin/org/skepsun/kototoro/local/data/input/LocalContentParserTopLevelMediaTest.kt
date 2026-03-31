package org.skepsun.kototoro.local.data.input

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.skepsun.kototoro.core.model.LocalNovelSource
import org.skepsun.kototoro.core.model.LocalVideoSource
import java.nio.file.Files
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.writeText

@OptIn(ExperimentalPathApi::class)
class LocalContentParserTopLevelMediaTest {

	@Test
	fun `top level epub file becomes local novel chapter`() = runTest {
		val root = Files.createTempDirectory("local-parser-epub")
		try {
			val contentDir = Files.createDirectory(root.resolve("Novel Title"))
			contentDir.resolve("chapter-01.epub").writeText("stub")

			val parsed = LocalContentParser(contentDir.toFile()).getContent(withDetails = true).manga
			val chapter = parsed.chapters?.singleOrNull()

			assertEquals(LocalNovelSource, parsed.source)
			assertNotNull(chapter)
			assertEquals(LocalNovelSource, chapter?.source)
			assertEquals("chapter-01.epub", chapter?.url?.substringAfterLast('/'))
		} finally {
			root.deleteRecursively()
		}
	}

	@Test
	fun `top level video file becomes local video chapter`() = runTest {
		val root = Files.createTempDirectory("local-parser-video")
		try {
			val contentDir = Files.createDirectory(root.resolve("Anime Title"))
			contentDir.resolve("episode-01.mp4").writeText("stub")

			val parsed = LocalContentParser(contentDir.toFile()).getContent(withDetails = true).manga
			val chapter = parsed.chapters?.singleOrNull()

			assertEquals(LocalVideoSource, parsed.source)
			assertNotNull(chapter)
			assertEquals(LocalVideoSource, chapter?.source)
			assertEquals("episode-01.mp4", chapter?.url?.substringAfterLast('/'))
		} finally {
			root.deleteRecursively()
		}
	}
}
