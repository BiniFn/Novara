package org.skepsun.kototoro.core.jsonsource

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.skepsun.kototoro.cloudstream.model.CloudstreamSource

class SourceGroupManagerTest {

	private val sourceGroupManager = SourceGroupManager(
		sourceTypeIdentifier = SourceTypeIdentifier(),
		jsonSourceManager = mockk(relaxed = true),
		json = Json,
	)

	@Test
	fun `cloudstream source is classified as video group`() {
		val api = mockk<MainAPI> {
			every { name } returns "Test Provider"
			every { lang } returns "en"
			every { supportedTypes } returns setOf(TvType.Movie)
		}
		val source = CloudstreamSource(
			api = api,
			pluginFileName = "test.cs3",
			pluginPackageName = "org.example.test",
		)

		val result = sourceGroupManager.getContentGroup(source)

		assertEquals(ContentGroup.VIDEO, result)
	}
}
