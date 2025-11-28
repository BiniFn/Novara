package org.skepsun.kototoro.local.epub

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.ag2s.epublib.domain.Book
import me.ag2s.epublib.epub.EpubReader as AgEpubReader
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * EPUB文件读取器
 * 支持从CBZ压缩包中读取EPUB文件
 */
class EpubReader {

    /**
     * 从URI读取EPUB文件
     * 支持：
     * - 直接的EPUB文件
     * - CBZ压缩包中的EPUB文件
     */
    suspend fun readEpub(uri: Uri): EpubContent? = withContext(Dispatchers.IO) {
        try {
            val inputStream = getEpubInputStream(uri) ?: return@withContext null
            inputStream.use { stream ->
                parseEpub(stream)
            }
        } catch (e: Exception) {
            android.util.Log.e("EpubReader", "Failed to read EPUB from $uri", e)
            null
        }
    }

    /**
     * 从章节URL获取EPUB输入流
     * 
     * URL格式：
     * - file:///path/to/manga.cbz#chapter/0.epub
     * - zip:file:///path/to/manga.cbz#chapter/0.epub
     */
    private fun getEpubInputStream(uri: Uri): InputStream? {
        try {
            val uriString = uri.toString()
            
            // 解析ZIP URI
            // 格式: zip:file:///path/to/file.cbz#entry/path.epub
            val zipPath: String
            val entryPath: String
            
            if (uriString.startsWith("zip:")) {
                val parts = uriString.removePrefix("zip:").split("#", limit = 2)
                zipPath = parts[0].removePrefix("file://")
                entryPath = parts.getOrNull(1) ?: return null
            } else if (uriString.contains(".cbz#")) {
                val parts = uriString.split("#", limit = 2)
                zipPath = parts[0].removePrefix("file://")
                entryPath = parts.getOrNull(1) ?: return null
            } else {
                // 直接的EPUB文件
                return java.io.FileInputStream(uri.path ?: return null)
            }
            
            // 打开ZIP文件
            val zipFileObj = java.io.File(zipPath)
            if (!zipFileObj.exists()) {
                android.util.Log.e("EpubReader", "ZIP file not found: $zipPath")
                return null
            }
            
            val zip = ZipFile(zipFileObj)
            
            // 查找EPUB文件
            val entry = zip.getEntry(entryPath)
            if (entry == null) {
                android.util.Log.e("EpubReader", "EPUB entry not found: $entryPath")
                zip.close()
                return null
            }
            
            // 读取EPUB文件到内存
            val epubBytes = zip.getInputStream(entry).use { it.readBytes() }
            zip.close()
            
            return epubBytes.inputStream()
        } catch (e: Exception) {
            android.util.Log.e("EpubReader", "Failed to extract EPUB from ZIP", e)
            return null
        }
    }

    /**
     * 解析EPUB文件
     */
    private fun parseEpub(inputStream: InputStream): EpubContent? {
        return try {
            val reader = AgEpubReader()
            val book = reader.readEpub(inputStream)
            
            extractContent(book)
        } catch (e: Exception) {
            android.util.Log.e("EpubReader", "Failed to parse EPUB", e)
            null
        }
    }

    /**
     * 从EPUB Book中提取内容
     */
    private fun extractContent(book: Book): EpubContent {
        val title = book.title ?: "未知标题"
        val author = book.metadata.authors.firstOrNull()?.toString() ?: "未知作者"
        
        // 提取所有章节内容
        val chapters = mutableListOf<EpubChapter>()
        val spine = book.spine
        
        for ((index, spineRef) in spine.spineReferences.withIndex()) {
            try {
                val resource = spineRef.resource
                val chapterTitle = resource.title ?: "第${index + 1}章"
                val htmlContent = String(resource.data, Charsets.UTF_8)
                
                // 转换HTML为纯文本
                val textContent = htmlToText(htmlContent)
                
                chapters.add(
                    EpubChapter(
                        index = index,
                        title = chapterTitle,
                        content = textContent,
                    )
                )
            } catch (e: Exception) {
                android.util.Log.e("EpubReader", "Failed to extract chapter $index", e)
            }
        }
        
        return EpubContent(
            title = title,
            author = author,
            chapters = chapters,
        )
    }

    /**
     * 简单的HTML转文本
     * 移除HTML标签，保留文本内容
     */
    private fun htmlToText(html: String): String {
        return html
            // 移除script和style标签及其内容
            .replace(Regex("<script[^>]*>.*?</script>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<style[^>]*>.*?</style>", RegexOption.DOT_MATCHES_ALL), "")
            // 将<br>和<p>标签转换为换行
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<p[^>]*>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n")
            // 移除所有HTML标签
            .replace(Regex("<[^>]+>"), "")
            // 解码HTML实体
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            // 清理多余的空白
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\n[ \\t]+"), "\n")
            .replace(Regex("[ \\t]+\n"), "\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }
}

/**
 * EPUB内容
 */
data class EpubContent(
    val title: String,
    val author: String,
    val chapters: List<EpubChapter>,
)

/**
 * EPUB章节
 */
data class EpubChapter(
    val index: Int,
    val title: String,
    val content: String,
)
