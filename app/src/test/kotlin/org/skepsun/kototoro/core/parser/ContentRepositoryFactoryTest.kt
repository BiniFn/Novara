package org.skepsun.kototoro.core.parser

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Response
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.skepsun.kototoro.core.cache.MemoryContentCache
import org.skepsun.kototoro.core.db.dao.JsonSourceDao
import org.skepsun.kototoro.core.db.entity.JsonSourceEntity
import org.skepsun.kototoro.core.db.entity.JsonSourceType
import org.skepsun.kototoro.core.jsonsource.JsonContentSource
import org.skepsun.kototoro.core.jsonsource.JsonSourceManager
import org.skepsun.kototoro.core.model.ContentSourceInfo
import org.skepsun.kototoro.core.model.UnknownContentSource
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.local.data.LocalContentRepository
import org.skepsun.kototoro.mihon.MihonExtensionManager
import org.skepsun.kototoro.mihon.model.MihonMangaSource
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentChapter
import org.skepsun.kototoro.parsers.model.ContentListFilter
import org.skepsun.kototoro.parsers.model.ContentListFilterCapabilities
import org.skepsun.kototoro.parsers.model.ContentListFilterOptions
import org.skepsun.kototoro.parsers.model.ContentPage
import org.skepsun.kototoro.parsers.model.ContentSource
import org.skepsun.kototoro.parsers.model.NovelChapterContent
import org.skepsun.kototoro.parsers.model.SortOrder
import org.skepsun.kototoro.aniyomi.AniyomiExtensionManager
import org.skepsun.kototoro.aniyomi.model.AniyomiAnimeSource

class ContentRepositoryFactoryTest {

	private val context = mockk<android.content.Context>(relaxed = true)
	private val localContentRepository = mockk<LocalContentRepository>(relaxed = true)
	private val localNovelRepository = mockk<org.skepsun.kototoro.local.novel.LocalNovelRepository>(relaxed = true)
	private val contentSourceInfoResolver = mockk<ContentSourceInfoResolver>()
	private val jsonContentSourceResolver = mockk<JsonContentSourceResolver>()
	private val mihonContentSourceResolver = mockk<MihonContentSourceResolver>()
	private val aniyomiContentSourceResolver = mockk<AniyomiContentSourceResolver>()
	private val parserContentRepositoryProvider = mockk<ParserContentRepositoryProvider>()
	private val kotatsuContentRepositoryProvider = mockk<KotatsuContentRepositoryProvider>()
	private val testContentRepositoryProvider = mockk<TestContentRepositoryProvider>()
	private val externalContentRepositoryProvider = mockk<ExternalContentRepositoryProvider>()
	private val mihonContentRepositoryProvider = mockk<MihonContentRepositoryProvider>()
	private val aniyomiContentRepositoryProvider = mockk<AniyomiContentRepositoryProvider>()
	private val jsonContentRepositoryProvider = mockk<JsonContentRepositoryProvider>()

	private lateinit var factory: ContentRepository.Factory

	@Before
	fun setUp() {
		listOf(
			contentSourceInfoResolver,
			jsonContentSourceResolver,
			mihonContentSourceResolver,
			aniyomiContentSourceResolver,
		).forEach { resolver ->
			every { resolver.resolve(any()) } returns null
		}
		listOf(
			parserContentRepositoryProvider,
			kotatsuContentRepositoryProvider,
			testContentRepositoryProvider,
			externalContentRepositoryProvider,
			mihonContentRepositoryProvider,
			aniyomiContentRepositoryProvider,
			jsonContentRepositoryProvider,
		).forEach { provider ->
			every { provider.create(any()) } returns null
		}
		factory = ContentRepository.Factory(
			context = context,
			localContentRepository = localContentRepository,
			localNovelRepository = localNovelRepository,
			contentSourceInfoResolver = contentSourceInfoResolver,
			jsonContentSourceResolver = jsonContentSourceResolver,
			mihonContentSourceResolver = mihonContentSourceResolver,
			aniyomiContentSourceResolver = aniyomiContentSourceResolver,
			parserContentRepositoryProvider = parserContentRepositoryProvider,
			kotatsuContentRepositoryProvider = kotatsuContentRepositoryProvider,
			testContentRepositoryProvider = testContentRepositoryProvider,
			externalContentRepositoryProvider = externalContentRepositoryProvider,
			mihonContentRepositoryProvider = mihonContentRepositoryProvider,
			aniyomiContentRepositoryProvider = aniyomiContentRepositoryProvider,
			jsonContentRepositoryProvider = jsonContentRepositoryProvider,
		)
	}

	@Test
	fun `source info resolver unwraps nested source`() {
		val inner = namedSource("REAL")
		val wrapped = ContentSourceInfo(inner, isEnabled = true, isPinned = false)

		assertSame(inner, ContentSourceInfoResolver().resolve(wrapped))
	}

	@Test
	fun `json source resolver resolves stored source`() = runTest {
		val entity = jsonEntity(id = "JSON_JS_TEST", type = JsonSourceType.JS)
		val manager = JsonSourceManager(
			jsonSourceDao = FakeJsonSourceDao(listOf(entity)),
			appSettings = mockk<AppSettings>(relaxed = true),
		)
		val resolver = JsonContentSourceResolver(manager)

		val resolved = resolver.resolve(namedSource(entity.id))

		assertTrue(resolved is JsonContentSource)
		assertSame(entity.id, resolved?.name)
	}

	@Test
	fun `mihon source resolver resolves prefixed source`() {
		val manager = mockk<MihonExtensionManager>()
		val resolvedSource = mockk<MihonMangaSource>()
		every { manager.getMihonMangaSourceByName("MIHON_42") } returns resolvedSource

		val resolved = MihonContentSourceResolver(manager).resolve(namedSource("MIHON_42"))

		assertSame(resolvedSource, resolved)
	}

	@Test
	fun `aniyomi source resolver resolves prefixed source`() {
		val manager = mockk<AniyomiExtensionManager>()
		val resolvedSource = mockk<AniyomiAnimeSource>()
		every { manager.getAniyomiAnimeSourceByName("ANIYOMI_7") } returns resolvedSource

		val resolved = AniyomiContentSourceResolver(manager).resolve(namedSource("ANIYOMI_7"))

		assertSame(resolvedSource, resolved)
	}

	@Test
	fun `factory caches by resolved source`() {
		val shellA = namedSource("SHELL_A")
		val shellB = namedSource("SHELL_B")
		val resolved = namedSource("REAL")
		val repository = FakeRepository(resolved)

		every { contentSourceInfoResolver.resolve(shellA) } returns resolved
		every { contentSourceInfoResolver.resolve(shellB) } returns resolved
		every { contentSourceInfoResolver.resolve(resolved) } returns null
		every { parserContentRepositoryProvider.create(resolved) } returns repository

		val first = factory.create(shellA)
		val second = factory.create(shellB)

		assertSame(repository, first)
		assertSame(repository, second)
		verify(exactly = 1) { parserContentRepositoryProvider.create(resolved) }
	}

	@Test
	fun `factory falls back to empty repository for unknown source`() {
		val repository = factory.create(UnknownContentSource)

		assertTrue(repository is EmptyContentRepository)
		assertSame(UnknownContentSource, repository.source)
	}

	@Test
	fun `factory falls back to empty repository when providers do not match`() {
		val source = namedSource("UNMATCHED")

		val repository = factory.create(source)

		assertTrue(repository is EmptyContentRepository)
		assertSame(source, repository.source)
	}

	private fun namedSource(name: String): ContentSource = object : ContentSource {
		override val name: String = name
	}

	private fun jsonEntity(id: String, type: JsonSourceType) = JsonSourceEntity(
		id = id,
		name = id,
		type = type,
		config = "{}",
		createdAt = 1L,
		updatedAt = 1L,
	)

	private class FakeJsonSourceDao(
		private val sources: List<JsonSourceEntity>,
	) : JsonSourceDao {
		override fun observeEnabled(): Flow<List<JsonSourceEntity>> = throw UnsupportedOperationException()
		override fun observeAll(): Flow<List<JsonSourceEntity>> = throw UnsupportedOperationException()
		override fun observeByType(type: JsonSourceType): Flow<List<JsonSourceEntity>> = throw UnsupportedOperationException()
		override fun observeEnabledByType(type: JsonSourceType): Flow<List<JsonSourceEntity>> = throw UnsupportedOperationException()
		override fun observeRecentlyUsed(limit: Int): Flow<List<JsonSourceEntity>> = throw UnsupportedOperationException()
		override suspend fun getById(id: String): JsonSourceEntity? = sources.find { it.id == id }
		override suspend fun getByIds(ids: List<String>): List<JsonSourceEntity> = sources.filter { it.id in ids }
		override suspend fun countByType(type: JsonSourceType): Int = sources.count { it.type == type }
		override suspend fun countEnabled(): Int = sources.count { it.enabled }
		override suspend fun insert(source: JsonSourceEntity) = Unit
		override suspend fun insertAll(sources: List<JsonSourceEntity>) = Unit
		override suspend fun update(source: JsonSourceEntity) = Unit
		override suspend fun setEnabled(id: String, enabled: Boolean, timestamp: Long) = Unit
		override suspend fun setEnabledBatch(ids: List<String>, enabled: Boolean, timestamp: Long) = Unit
		override suspend fun setLastUsed(id: String, timestamp: Long) = Unit
		override suspend fun delete(source: JsonSourceEntity) = Unit
		override suspend fun deleteById(id: String) = Unit
		override suspend fun deleteByIds(ids: List<String>) = Unit
		override suspend fun deleteByType(type: JsonSourceType) = Unit
	}

	private class FakeRepository(
		override val source: ContentSource,
	) : ContentRepository {
		override val sortOrders: Set<SortOrder> = emptySet()
		override var defaultSortOrder: SortOrder = SortOrder.NEWEST
		override val filterCapabilities: ContentListFilterCapabilities = ContentListFilterCapabilities()
		override suspend fun getList(offset: Int, order: SortOrder?, filter: ContentListFilter?): List<Content> = emptyList()
		override suspend fun getDetails(manga: Content): Content = manga
		override suspend fun getPages(chapter: ContentChapter, nextChapterUrl: String?): List<ContentPage> = emptyList()
		override suspend fun getPageUrl(page: ContentPage): String = ""
		override suspend fun getFilterOptions(): ContentListFilterOptions = ContentListFilterOptions()
		override suspend fun getChapterContent(chapter: ContentChapter, nextChapterUrl: String?): NovelChapterContent? = null
		override suspend fun getRelated(seed: Content): List<Content> = emptyList()
	}
}
