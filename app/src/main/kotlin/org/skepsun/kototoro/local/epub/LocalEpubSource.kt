package org.skepsun.kototoro.local.epub

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.skepsun.kototoro.core.model.LocalMangaSource
import org.skepsun.kototoro.parsers.model.MangaChapter
import org.skepsun.kototoro.parsers.model.MangaPage
import org.skepsun.kototoro.parsers.util.longHashCode
import java.io.File

/**
 * 本地EPUB数据源
 * 
 * 提供EPUB文件的章节和内容访问
 * 用于NovelReaderActivity显示EPUB内容
 */
class LocalEpubSource {

    /**
     * 从CBZ文件获取EPUB章节列表
     * 
     * @param cbzFile CBZ文件（实际是EPUB）
     * @return EPUB章节列表
     */
    suspend fun getEpubChapters(cbzFile: File): List<MangaChapter>? = withContext(Dispatchers.IO) {
        try {
            val parser = LocalEpubParser(cbzFile)
            val manga = parser.parseManga() ?: return@withContext null
            
            manga.chapters
        } catch (e: Exception) {
            android.util.Log.e("LocalEpubSource", "Failed to get EPUB chapters", e)
            null
        }
    }

    /**
     * 获取EPUB章节的页面（文本内容）
     * 
     * @param chapter 章节
     * @param cbzFile CBZ文件
     * @return 包含文本内容的页面列表
     */
    suspend fun getEpubPages(chapter: MangaChapter, cbzFile: File): List<MangaPage> = withContext(Dispatchers.IO) {
        try {
            // 从章节URL中提取章节索引
            // URL格式：file:///path/to/file.cbz#chapter/0
            val chapterIndex = chapter.url.substringAfterLast("/").toIntOrNull() ?: 0
            
            val parser = LocalEpubParser(cbzFile)
            val content = parser.getChapterContent(chapterIndex) ?: return@withContext emptyList()
            
            // 将文本内容编码为data URL格式
            // NovelChaptersLoader期望的格式：data:text/html;base64,<base64编码的HTML>
            val base64Content = android.util.Base64.encodeToString(
                content.toByteArray(Charsets.UTF_8),
                android.util.Base64.NO_WRAP
            )
            val dataUrl = "data:text/html;charset=utf-8;base64,$base64Content"
            
            listOf(
                MangaPage(
                    id = chapter.url.longHashCode(),
                    url = dataUrl, // 使用data URL格式
                    preview = null,
                    source = LocalMangaSource,
                )
            )
        } catch (e: Exception) {
            android.util.Log.e("LocalEpubSource", "Failed to get EPUB pages", e)
            emptyList()
        }
    }

    /**
     * 获取EPUB章节的文本内容
     * 
     * @param chapter 章节
     * @param cbzFile CBZ文件
     * @return 文本内容
     */
    suspend fun getEpubChapterText(chapter: MangaChapter, cbzFile: File): String? = withContext(Dispatchers.IO) {
        try {
            // 从章节URL中提取章节索引
            val chapterIndex = chapter.url.substringAfterLast("/").toIntOrNull() ?: 0
            
            val parser = LocalEpubParser(cbzFile)
            parser.getChapterContent(chapterIndex)
        } catch (e: Exception) {
            android.util.Log.e("LocalEpubSource", "Failed to get EPUB chapter text", e)
            null
        }
    }

    companion object {
        /**
         * 检查文件是否是EPUB
         */
        suspend fun isEpubFile(file: File): Boolean {
            return try {
                val parser = LocalEpubParser(file)
                parser.isEpubFile()
            } catch (e: Exception) {
                false
            }
        }
    }
}
