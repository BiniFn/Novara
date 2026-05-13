package org.skepsun.kototoro.core.parser.tvbox

import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.skepsun.kototoro.core.model.jsonsource.TVBoxStoredConfig

class TVBoxSupportStatusClassifierTest {

	@Test
	fun `direct source without runtime artifacts is classified as direct`() {
		val config = tvboxConfig(
			type = 0,
			api = "https://example.test/video.mp4",
		)

		val result = TVBoxSupportStatusClassifier.classify(config, emptyList())

		assertEquals(TVBoxSupportStatus.DIRECT, result)
	}

	@Test
	fun `CMS candidate without spider artifacts is classified as partial runtime`() {
		val config = tvboxConfig(type = 0)

		val result = TVBoxSupportStatusClassifier.classify(
			config = config,
			candidates = listOf("https://example.test/api.php/provide/vod"),
		)

		assertEquals(TVBoxSupportStatus.PARTIAL_RUNTIME, result)
	}

	@Test
	fun `type four source is classified as QuickJS partial`() {
		val config = tvboxConfig(
			type = 4,
			api = "https://example.test/cat.js",
		)

		val result = TVBoxSupportStatusClassifier.classify(config, emptyList())

		assertEquals(TVBoxSupportStatus.QUICKJS_PARTIAL, result)
	}

	@Test
	fun `ordinary csp source is classified as ordinary jar`() {
		val config = tvboxConfig(
			type = 3,
			api = "csp_XPath",
			rootSpider = "https://example.test/spider.jar",
		)

		val result = TVBoxSupportStatusClassifier.classify(config, emptyList())

		assertEquals(TVBoxSupportStatus.ORDINARY_JAR, result)
	}

	@Test
	fun `Guard native signal takes precedence over ordinary jar`() {
		val config = tvboxConfig(
			type = 3,
			api = "csp_Guard",
			siteJar = "https://example.test/DexNative.jar",
		)

		val result = TVBoxSupportStatusClassifier.classify(config, emptyList())

		assertEquals(TVBoxSupportStatus.GUARD_NATIVE, result)
	}

	@Test
	fun `playable candidate is classified as partial runtime`() {
		val config = tvboxConfig(type = 0)

		val result = TVBoxSupportStatusClassifier.classify(
			config = config,
			candidates = listOf("https://example.test/live.m3u8"),
		)

		assertEquals(TVBoxSupportStatus.PARTIAL_RUNTIME, result)
	}

	private fun tvboxConfig(
		type: Int,
		api: String = "",
		name: String = "Test",
		rootSpider: String? = null,
		siteJar: String? = null,
		ext: Any? = null,
	): TVBoxStoredConfig {
		return TVBoxStoredConfig.parse(
			JSONObject().apply {
				put("schemaVersion", 1)
				put("importType", "tvbox_site")
				put(
					"site",
					JSONObject().apply {
						put("key", "test")
						put("name", name)
						put("type", type)
						put("api", api)
						siteJar?.let { put("jar", it) }
						ext?.let { put("ext", it) }
					},
				)
				put(
					"root",
					JSONObject().apply {
						rootSpider?.let { put("spider", it) }
					},
				)
				put("meta", JSONObject())
			}.toString(),
		)
	}
}
