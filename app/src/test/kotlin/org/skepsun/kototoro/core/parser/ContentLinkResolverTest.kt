package org.skepsun.kototoro.core.parser

import android.net.Uri
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.skepsun.kototoro.parsers.ContentLoaderContext
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentChapter
import org.skepsun.kototoro.parsers.model.ContentListFilter
import org.skepsun.kototoro.parsers.model.ContentListFilterCapabilities
import org.skepsun.kototoro.parsers.model.ContentListFilterOptions
import org.skepsun.kototoro.parsers.model.ContentPage
import org.skepsun.kototoro.parsers.model.ContentSource
import org.skepsun.kototoro.parsers.model.SortOrder

class ContentLinkResolverTest {

	private val repositoryFactory = mockk<ContentRepository.Factory>()
	private val dataRepository = mockk<ContentDataRepository>()
	private val context = mockk<ContentLoaderContext>(relaxed = true)
	private val resolver = ContentLinkResolver(repositoryFactory, dataRepository, context)

	@Test
	fun `resolve app link uses repository diagnostics and finds matching content`() = runTest {
		val sourceName = "MIHON_SOURCE_OK"
		val source = namedSource(sourceName)
		val target = content(source = source, title = "Target", url = "https://example.com/item")
		val uri = appLinkUri(
			sourceName = sourceName,
			url = "https://example.com/item",
			name = "Target",
		)
		val repository = FakeRepository(
			source = source,
			listResult = listOf(target),
		)
		coEvery { dataRepository.findContentById(any(), any()) } returns null
		every { repositoryFactory.createWithDiagnostics(match { it.name == sourceName }) } returns ContentRepositoryFactory.CreationResult(
			requestedSource = source,
			resolvedSource = source,
			repository = repository,
			resolutionStatus = ContentRepositoryFactory.ResolutionStatus.UNCHANGED,
			providerStatus = ContentRepositoryFactory.ProviderStatus.SELECTED,
			cacheStatus = ContentRepositoryFactory.CacheStatus.MISS,
			selectedProvider = "TestProvider",
			candidateProviders = listOf("TestProvider"),
			attemptedProviders = listOf("TestProvider"),
			resolutionTrace = emptyList(),
			failureReason = null,
		)

		val result = resolver.resolve(uri)

		assertEquals(target, result)
	}

	@Test
	fun `resolve app link fails early when diagnostics report empty repository fallback`() = runTest {
		val sourceName = "MIHON_SOURCE_UNAVAILABLE"
		val source = namedSource(sourceName)
		val uri = appLinkUri(
			sourceName = sourceName,
			url = "https://example.com/item",
			name = "Target",
		)
		coEvery { dataRepository.findContentById(any(), any()) } returns null
		every { repositoryFactory.createWithDiagnostics(match { it.name == sourceName }) } returns ContentRepositoryFactory.CreationResult(
			requestedSource = source,
			resolvedSource = source,
			repository = EmptyContentRepository(source),
			resolutionStatus = ContentRepositoryFactory.ResolutionStatus.UNCHANGED,
			providerStatus = ContentRepositoryFactory.ProviderStatus.FALLBACK_EMPTY,
			cacheStatus = ContentRepositoryFactory.CacheStatus.MISS,
			selectedProvider = null,
			candidateProviders = emptyList(),
			attemptedProviders = emptyList(),
			resolutionTrace = emptyList(),
			failureReason = ContentRepositoryFactory.FailureReason.NO_SUPPORTED_PROVIDER,
		)

		val error = try {
			resolver.resolve(uri)
			fail("Expected resolver to reject unavailable source")
		} catch (e: IllegalStateException) {
			e
		}

		assertEquals("Content source $sourceName is not available", error.message)
	}

	private fun namedSource(name: String): ContentSource = object : ContentSource {
		override val name: String = name
	}

	private fun appLinkUri(sourceName: String, url: String, name: String): Uri {
		return mockk<Uri>().also { uri ->
			every { uri.scheme } returns "kototoro"
			every { uri.host } returns "kototoro.app"
			every { uri.pathSegments } returns listOf("manga")
			every { uri.getQueryParameter("id") } returns null
			every { uri.getQueryParameter("source") } returns sourceName
			every { uri.getQueryParameter("url") } returns url
			every { uri.getQueryParameter("name") } returns name
		}
	}

	private fun content(source: ContentSource, title: String, url: String): Content = Content(
		id = 1L,
		title = title,
		altTitle = null,
		url = url,
		publicUrl = url,
		rating = 0f,
		isNsfw = false,
		coverUrl = null,
		tags = emptySet(),
		state = null,
		author = null,
		largeCoverUrl = null,
		description = null,
		chapters = null,
		source = source,
	)

	private class FakeRepository(
		override val source: ContentSource,
		private val listResult: List<Content>,
	) : ContentRepository {
		override val sortOrders: Set<SortOrder> = emptySet()
		override var defaultSortOrder: SortOrder = SortOrder.NEWEST
		override val filterCapabilities: ContentListFilterCapabilities = ContentListFilterCapabilities()
		override suspend fun getList(offset: Int, order: SortOrder?, filter: ContentListFilter?): List<Content> = listResult
		override suspend fun getDetails(manga: Content): Content = manga
		override suspend fun getPages(chapter: ContentChapter, nextChapterUrl: String?): List<ContentPage> = emptyList()
		override suspend fun getPageUrl(page: ContentPage): String = ""
		override suspend fun getFilterOptions(): ContentListFilterOptions = ContentListFilterOptions()
		override suspend fun getRelated(seed: Content): List<Content> = emptyList()
	}
}
