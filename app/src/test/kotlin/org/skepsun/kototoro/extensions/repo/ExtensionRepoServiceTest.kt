package org.skepsun.kototoro.extensions.repo

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import io.mockk.mockk
import org.skepsun.kototoro.core.prefs.AppSettings

class ExtensionRepoServiceTest : FunSpec({

	lateinit var server: MockWebServer
	lateinit var service: ExtensionRepoService

	beforeSpec {
		server = MockWebServer()
		server.start()
		service = ExtensionRepoService(
			httpClient = OkHttpClient(),
			json = Json {
				ignoreUnknownKeys = true
				isLenient = true
				encodeDefaults = true
				prettyPrint = true
				coerceInputValues = true
			},
			settings = mockk<AppSettings>(relaxed = true),
		)
	}

	afterSpec {
		server.shutdown()
	}

	test("normalizeIndexUrl appends index path and strips query and fragment") {
		service.normalizeIndexUrl(" https://example.org/extensions/?foo=1#bar ") shouldBe
			"https://example.org/extensions/index.min.json"
	}

	test("normalizeIndexUrl rejects non-https urls") {
		service.normalizeIndexUrl("http://example.org/extensions").shouldBeNull()
	}

	test("fetchRepoDetails parses repository metadata") {
		runBlocking {
			server.enqueue(
				MockResponse().setBody(
					"""
					{
					  "meta": {
					    "name": "Keiyoushi",
					    "shortName": "Kei",
					    "website": "https://keiyoushi.example",
					    "signingKeyFingerprint": "AA:BB:CC"
					  }
					}
					""".trimIndent(),
				),
			)

			val repo = service.fetchRepoDetails(
				baseUrl = server.url("/mihon").toString().removeSuffix("/"),
				type = ExternalExtensionType.MIHON,
			)

			repo.shouldNotBeNull()
			repo.type shouldBe ExternalExtensionType.MIHON
			repo.baseUrl shouldBe server.url("/mihon").toString().removeSuffix("/")
			repo.name shouldBe "Keiyoushi"
			repo.shortName shouldBe "Kei"
			repo.website shouldBe "https://keiyoushi.example"
			repo.signingKeyFingerprint shouldBe "AA:BB:CC"
		}
	}

	test("fetchAvailableExtensions parses catalog entries and compatibility") {
		runBlocking {
			server.enqueue(
				MockResponse().setBody(
					"""
					[
					  {
					    "name": "Tachiyomi: Asura Scans",
					    "pkg": "ext.asura",
					    "apk": "asura.apk",
					    "lang": "en",
					    "code": 2,
					    "version": "1.9.0",
					    "nsfw": 1,
					    "sources": [{"name": "Asura Scans"}]
					  },
					  {
					    "name": "Tachiyomi: Legacy",
					    "pkg": "ext.legacy",
					    "apk": "legacy.apk",
					    "lang": "en",
					    "code": 1,
					    "version": "1.1.0",
					    "nsfw": 0,
					    "sources": [{"name": "Legacy"}]
					  },
					  {
					    "name": "Tachiyomi: Broken",
					    "pkg": "ext.broken",
					    "apk": "broken.apk",
					    "lang": "en",
					    "code": 1,
					    "version": "broken",
					    "nsfw": 0,
					    "sources": [{"name": "Broken"}]
					  }
					]
					""".trimIndent(),
				),
			)

			val repo = ExternalExtensionRepo(
				type = ExternalExtensionType.MIHON,
				baseUrl = server.url("/mihon").toString().removeSuffix("/"),
				name = "Keiyoushi",
				shortName = "Kei",
				website = "https://keiyoushi.example",
				signingKeyFingerprint = "AA:BB:CC",
				createdAt = 1L,
				updatedAt = 1L,
				lastSuccessAt = 1L,
				lastError = null,
			)

			val extensions = service.fetchAvailableExtensions(repo)

			extensions shouldHaveSize 2
			extensions[0].name shouldBe "Asura Scans"
			extensions[0].isCompatible.shouldBeTrue()
			extensions[0].isNsfw.shouldBeTrue()
			extensions[0].sourceNames shouldBe listOf("Asura Scans")
			extensions[0].iconUrl shouldBe "${repo.baseUrl}/icon/ext.asura.png"
			extensions[0].signatureHash shouldBe "AA:BB:CC"
			extensions[0].repoName shouldBe "Kei"

			extensions[1].name shouldBe "Legacy"
			extensions[1].isCompatible.shouldBeFalse()
		}
	}
})
