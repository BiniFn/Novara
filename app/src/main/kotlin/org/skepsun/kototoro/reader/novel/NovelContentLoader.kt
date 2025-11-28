package org.skepsun.kototoro.reader.novel

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.buffer
import okio.source
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
 */
@Singleton
class NovelContentLoader @Inject constructor(
    @NovelCache private val cache: LocalStorageCache,
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
        // 生成缓存key：使用章节URL作为唯一标识
        val cacheKey = generateCacheKey(chapter)
        
        // 1. 尝试从缓存读取
        cache.get(cacheKey)?.let { cachedFile ->
            return@withContext readTextFromFile(cachedFile)
        }
        
        // 2. 从网络加载
        val pages = repository.getPages(chapter)
        val html = pages.firstOrNull()?.url?.let(::decodeChapterHtml) ?: ""
        val plainText = htmlToPlainText(html)
        
        // 3. 保存到缓存
        if (plainText.isNotBlank()) {
            saveToCache(cacheKey, plainText)
        }
        
        plainText
    }

    /**
     * 清除所有小说缓存
     */
    suspend fun clearCache() {
        cache.clear()
    }

    /**
     * 生成缓存key
     */
    private fun generateCacheKey(chapter: MangaChapter): String {
        // 使用章节ID和URL的组合作为key，确保唯一性
        return "novel_${chapter.id}_${chapter.url.hashCode()}"
    }

    /**
     * 从文件读取文本
     */
    private fun readTextFromFile(file: File): String {
        return file.source().buffer().use { source ->
            source.readUtf8()
        }
    }

    /**
     * 保存文本到缓存
     */
    private suspend fun saveToCache(key: String, content: String) {
        withContext(Dispatchers.IO) {
            val bytes = content.toByteArray(Charsets.UTF_8)
            okio.Buffer().write(bytes).use { buffer ->
                cache.set(key, buffer, null)
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
