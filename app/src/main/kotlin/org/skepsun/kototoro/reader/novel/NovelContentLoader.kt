package org.skepsun.kototoro.reader.novel

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.buffer
import okio.source
import org.skepsun.kototoro.core.db.MangaDatabase
import org.skepsun.kototoro.core.parser.MangaRepository
import org.skepsun.kototoro.local.data.LocalStorageCache
import org.skepsun.kototoro.local.data.NovelCache
import org.skepsun.kototoro.parsers.model.MangaChapter
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 小说内容加载器
 * 负责加载和缓存小说章节内容
 * 复用漫画阅读器的缓存机制
 * 
 * 支持：
 * - 在线章节（通过repository加载）
 * - EPUB章节（通过EpubReader加载，使用epub://协议）
 */
@Singleton
class NovelContentLoader @Inject constructor(
    @NovelCache private val cache: LocalStorageCache,
    private val epubStorageManager: org.skepsun.kototoro.local.epub.EpubStorageManager,
    private val mangaDatabase: MangaDatabase,
) {

    /**
     * 加载章节内容（带缓存）
     * @param repository 漫画仓库
     * @param chapter 章节信息
     * @return 章节的纯文本内容
     */
    suspend fun loadChapterContent(
        repository: MangaRepository,
        chapter: MangaChapter,
    ): String = withContext(Dispatchers.IO) {
        // Check if this is an EPUB chapter (NEW ARCHITECTURE)
        if (org.skepsun.kototoro.local.epub.LocalEpubSource.isEpubUrl(chapter.url)) {
            android.util.Log.d("NovelContentLoader", "Loading EPUB chapter: ${chapter.url}")
            return@withContext loadEpubChapterContent(chapter)
        }
        
        // 生成缓存key：使用章节ID作为唯一标识
        val cacheKey = generateCacheKey(chapter)
        android.util.Log.d("NovelContentLoader", "Loading chapter: id=${chapter.id}, name=${chapter.name}, cacheKey=$cacheKey")
        
        // 1. 尝试从缓存读取
        cache.get(cacheKey)?.let { cachedFile ->
            android.util.Log.d("NovelContentLoader", "Cache hit for $cacheKey, file size: ${cachedFile.length()}")
            val content = readTextFromFile(cachedFile)
            android.util.Log.d("NovelContentLoader", "Loaded from cache, content length: ${content.length}")
            return@withContext content
        }
        
        android.util.Log.d("NovelContentLoader", "Cache miss for $cacheKey, loading from network")
        
        // 2. 从网络加载
        val pages = repository.getPages(chapter)
        val html = pages.firstOrNull()?.url?.let(::decodeChapterHtml) ?: ""
        val plainText = htmlToPlainText(html)
        
        android.util.Log.d("NovelContentLoader", "Loaded from network, content length: ${plainText.length}")
        
        // 3. 保存到缓存
        if (plainText.isNotBlank()) {
            saveToCache(cacheKey, plainText)
            android.util.Log.d("NovelContentLoader", "Saved to cache: $cacheKey")
        } else {
            android.util.Log.w("NovelContentLoader", "Content is blank, not caching")
        }
        
        plainText
    }
    
    /**
     * Load EPUB chapter content (NEW ARCHITECTURE)
     * 
     * Parses epub:// URL and loads content from EPUB file
     * Format: epub://{manga_id}/chapter/{index}
     * 
     * Uses EpubChapterMappingDao to find the correct EPUB file path
     * (supports multiple EPUB files per manga, e.g., Z-Library)
     */
    private suspend fun loadEpubChapterContent(chapter: MangaChapter): String = withContext(Dispatchers.IO) {
        try {
            // Parse epub:// URL
            val regex = Regex("epub://(-?\\d+)/chapter/(\\d+)")
            val match = regex.matchEntire(chapter.url)
                ?: throw IllegalStateException("Invalid EPUB URL: ${chapter.url}")
            
            val mangaId = match.groupValues[1].toLong()
            val chapterIndex = match.groupValues[2].toInt()
            
            android.util.Log.d("NovelContentLoader", "Loading EPUB: mangaId=$mangaId, chapterIndex=$chapterIndex")
            
            // Query database for EPUB file path using chapter index
            val epubChapterMappingDao = mangaDatabase.getEpubChapterMappingDao()
            val allMappings = epubChapterMappingDao.findByMangaId(mangaId)
            
            // Sort mappings by parentChapterId and chapterIndex to match LocalEpubSource ordering
            val sortedMappings = allMappings.sortedWith(compareBy({ it.parentChapterId }, { it.chapterIndex }))
            
            // Find mapping by global index (the index in the URL corresponds to the position in sorted list)
            val mapping = sortedMappings.getOrNull(chapterIndex)
                ?: throw IllegalStateException("EPUB chapter mapping not found for manga $mangaId, index $chapterIndex (total mappings: ${sortedMappings.size})")
            
            android.util.Log.d("NovelContentLoader", "Found EPUB file: ${mapping.epubFilePath}")
            
            // Get EPUB file from mapping
            val epubFile = java.io.File(mapping.epubFilePath)
            if (!epubFile.exists()) {
                throw IllegalStateException("EPUB file not found: ${mapping.epubFilePath}")
            }
            
            // Load chapter content using EpubReaderImpl
            val reader = org.skepsun.kototoro.local.epub.EpubReaderImpl()
            val epubContent = reader.readEpub(epubFile)
                ?: throw IllegalStateException("Failed to parse EPUB file")
            
            // Get chapter by index (use mapping.chapterIndex which is the index within the EPUB file)
            val epubChapter = epubContent.chapters.getOrNull(mapping.chapterIndex)
                ?: throw IllegalStateException("Chapter index ${mapping.chapterIndex} not found in EPUB")
            
            val content = epubChapter.content
            
            android.util.Log.d("NovelContentLoader", "Loaded EPUB chapter, content length: ${content.length}")
            
            content
        } catch (e: Exception) {
            android.util.Log.e("NovelContentLoader", "Failed to load EPUB chapter", e)
            throw e
        }
    }

    /**
     * 清除所有小说缓存
     */
    suspend fun clearCache() {
        cache.clear()
    }

    /**
     * 生成缓存key
     * 只使用章节ID，因为URL可能包含动态参数（时间戳、token等）
     */
    private fun generateCacheKey(chapter: MangaChapter): String {
        // 只使用章节ID作为key，确保稳定性
        return "novel_chapter_${chapter.id}"
    }

    /**
     * 从文件读取文本
     */
    private fun readTextFromFile(file: File): String {
        return runCatching {
            file.source().buffer().use { source ->
                source.readUtf8()
            }
        }.getOrElse { e ->
            android.util.Log.e("NovelContentLoader", "Failed to read from cache file: ${file.absolutePath}", e)
            "" // 返回空字符串，触发重新加载
        }
    }

    /**
     * 保存文本到缓存
     */
    private suspend fun saveToCache(key: String, content: String) {
        withContext(Dispatchers.IO) {
            runCatching {
                val bytes = content.toByteArray(Charsets.UTF_8)
                okio.Buffer().write(bytes).use { buffer ->
                    cache.set(key, buffer, null)
                }
                android.util.Log.d("NovelContentLoader", "Successfully saved to cache: $key, size: ${bytes.size} bytes")
            }.onFailure { e ->
                android.util.Log.e("NovelContentLoader", "Failed to save to cache: $key", e)
            }
        }
    }

    /**
     * 解码章节HTML（从data URL）
     */
    private fun decodeChapterHtml(url: String): String {
        if (url.startsWith("data:", ignoreCase = true)) {
            val commaIndex = url.indexOf(',')
            if (commaIndex != -1) {
                val meta = url.substring(5, commaIndex)
                val data = url.substring(commaIndex + 1)
                return if (meta.contains("base64", ignoreCase = true)) {
                    val decoded = Base64.decode(data, Base64.DEFAULT)
                    String(decoded, Charsets.UTF_8)
                } else {
                    data
                }
            }
        }
        return ""
    }

    /**
     * 将HTML转换为纯文本
     */
    private fun htmlToPlainText(html: String): String {
        return html
            .replace(Regex("<script[^>]*>.*?</script>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<style[^>]*>.*?</style>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .trim()
            .lines()
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
    }
}
