package org.skepsun.kototoro.reader.novel

import android.content.Context
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Base64
import androidx.annotation.WorkerThread
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.skepsun.kototoro.core.parser.ContentRepository
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentChapter

private const val PAGES_TRIM_THRESHOLD = 50  // 小说页面较多，设置较小的阈值

/**
 * 章节加载器
 * 负责加载、分页和管理多个章节的内容
 */
class NovelChaptersLoader(
    private val context: Context,
    private val repository: ContentRepository,
    private val settings: NovelReaderSettings,
) {

    private val chapterPages = NovelChapterPages()
    private val mutex = Mutex()
    private val chapters = mutableMapOf<Long, ContentChapter>()

    val size: Int
        get() = chapterPages.size

    /**
     * 初始化章节列表
     */
    suspend fun init(manga: Content) = mutex.withLock {
        chapters.clear()
        manga.chapters?.forEach {
            chapters[it.id] = it
        }
    }

    /**
     * 加载单个章节
     */
    suspend fun loadSingleChapter(
        chapterId: Long,
        pageWidth: Int,
        pageHeight: Int,
    ): Boolean = mutex.withLock {
        val pages = loadAndPaginateChapter(chapterId, pageWidth, pageHeight)
        chapterPages.clear()
        chapterPages.addLast(chapterId, pages)
        pages.isNotEmpty()
    }

    /**
     * 加载前一个或后一个章节
     */
    suspend fun loadPrevNextChapter(
        manga: Content,
        currentId: Long,
        isNext: Boolean,
        pageWidth: Int,
        pageHeight: Int,
    ): Boolean {
        val allChapters = manga.chapters ?: return false
        val index = allChapters.indexOfFirst { it.id == currentId }
        if (index == -1) return false

        val newChapter = allChapters.getOrNull(if (isNext) index + 1 else index - 1) ?: return false
        val newPages = loadAndPaginateChapter(newChapter.id, pageWidth, pageHeight)

        mutex.withLock {
            // 修剪过多的页面
            if (chapterPages.chaptersSize > 1 && chapterPages.size > PAGES_TRIM_THRESHOLD) {
                if (isNext) {
                    chapterPages.removeFirst()
                } else {
                    chapterPages.removeLast()
                }
            }

            // 添加新章节
            if (isNext) {
                chapterPages.addLast(newChapter.id, newPages)
            } else {
                chapterPages.addFirst(newChapter.id, newPages)
            }
        }

        return true
    }

    /**
     * 检查是否已加载指定章节
     */
    fun hasChapter(chapterId: Long): Boolean {
        return chapterId in chapterPages
    }

    /**
     * 获取指定章节的页面数量
     */
    fun getChapterPagesCount(chapterId: Long): Int {
        return chapterPages.size(chapterId)
    }

    /**
     * 获取指定章节的第一页全局索引
     */
    fun getChapterFirstPageIndex(chapterId: Long): Int {
        return chapterPages.getFirstPageIndex(chapterId)
    }

    /**
     * 获取指定章节的最后一页全局索引
     */
    fun getChapterLastPageIndex(chapterId: Long): Int {
        return chapterPages.getLastPageIndex(chapterId)
    }

    /**
     * 获取所有页面的快照
     */
    fun snapshot(): List<NovelPage> {
        return chapterPages.snapshot()
    }

    /**
     * 加载并分页章节内容
     * 关键：每个章节独立分页
     */
    @WorkerThread
    private suspend fun loadAndPaginateChapter(
        chapterId: Long,
        pageWidth: Int,
        pageHeight: Int,
    ): List<NovelPage> {
        val chapter = chapters[chapterId] ?: return emptyList()

        // 1. 加载章节内容
        val pages = repository.getPages(chapter)
        val html = pages.firstOrNull()?.url?.let(::decodeChapterHtml) ?: return emptyList()
        val plainText = htmlToPlainText(html)

        if (plainText.isBlank()) {
            return emptyList()
        }

        // 2. 为章节内容分页
        return paginateChapter(
            chapterId = chapterId,
            chapterIndex = 0,  // 这个值在 Activity 中会被更新
            content = plainText,
            pageWidth = pageWidth,
            pageHeight = pageHeight,
        )
    }

    /**
     * 为章节内容分页
     * 使用与 NovelReaderView 相同的分页算法
     */
    @WorkerThread
    private fun paginateChapter(
        chapterId: Long,
        chapterIndex: Int,
        content: String,
        pageWidth: Int,
        pageHeight: Int,
    ): List<NovelPage> {
        if (content.isBlank() || pageWidth <= 0 || pageHeight <= 0) {
            return emptyList()
        }

        val textPaint = createTextPaint()
        val result = mutableListOf<NovelPage>()

        // 创建完整布局
        val fullLayout = createLayout(content, pageWidth, textPaint)
        val totalLines = fullLayout.lineCount

        var startLine = 0
        var pageIndexInChapter = 0

        while (startLine < totalLines) {
            var endLine = startLine
            var accumulatedHeight = 0f

            // 逐行累加高度
            while (endLine < totalLines) {
                val lineHeight = fullLayout.getLineBottom(endLine) - fullLayout.getLineTop(endLine)

                if (accumulatedHeight + lineHeight > pageHeight && endLine > startLine) {
                    break
                }

                accumulatedHeight += lineHeight
                endLine++
            }

            // 确保至少包含一行
            if (endLine == startLine) {
                endLine = startLine + 1
            }

            // 提取页面文本
            val startOffset = fullLayout.getLineStart(startLine)
            val endOffset = if (endLine < totalLines) {
                fullLayout.getLineEnd(endLine - 1)
            } else {
                content.length
            }

            val pageText = content.substring(startOffset, endOffset)
            val pageLayout = createLayout(pageText, pageWidth, textPaint)

            // 创建 NovelPage
            result.add(
                NovelPage(
                    chapterId = chapterId,
                    chapterIndex = chapterIndex,
                    pageIndex = pageIndexInChapter,
                    text = pageText,
                    layout = pageLayout,
                    charStartPosition = startOffset,
                    charEndPosition = endOffset,
                )
            )

            startLine = endLine
            pageIndexInChapter++
        }

        return result
    }

    private fun createTextPaint(): TextPaint {
        return TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            val typedValue = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
            color = if (typedValue.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT &&
                typedValue.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT
            ) {
                typedValue.data
            } else {
                androidx.core.content.ContextCompat.getColor(context, typedValue.resourceId)
            }
            textSize = context.resources.displayMetrics.scaledDensity * settings.fontSizeSp
        }
    }

    private fun createLayout(text: String, width: Int, paint: TextPaint): StaticLayout {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setLineSpacing(0f, settings.lineSpacing)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(
                text,
                paint,
                width,
                android.text.Layout.Alignment.ALIGN_NORMAL,
                settings.lineSpacing,
                0f,
                false,
            )
        }
    }

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
