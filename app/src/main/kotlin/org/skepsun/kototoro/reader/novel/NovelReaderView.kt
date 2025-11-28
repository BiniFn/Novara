package org.skepsun.kototoro.reader.novel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.GestureDetectorCompat
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.util.ext.resolveSp
import kotlin.math.abs
import kotlin.math.max

/**
 * 小说阅读器视图 - 基于 TextView 的自定义实现
 * 参考 Legado 的分页算法
 */
class NovelReaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        // 使用主题的文本颜色，自动适配暗黑模式
        val typedValue = android.util.TypedValue()
        context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
        // 如果是颜色资源，需要通过 getColor 获取实际颜色值
        color = if (typedValue.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT &&
            typedValue.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT) {
            // 直接是颜色值
            typedValue.data
        } else {
            // 是颜色资源引用，需要解析
            androidx.core.content.ContextCompat.getColor(context, typedValue.resourceId)
        }
        textSize = resources.resolveSp(18f)
    }

    private var settings: NovelReaderSettings = NovelReaderSettings.load(context)
    private var chapterContent: String = ""
    private var pages: List<PageInfo> = emptyList()
    private var currentPageIndex: Int = 0
    private var isDualPage: Boolean = false
    private var footerHeight: Int = 0  // 页脚高度，用于计算可用空间
    private var suppressPageChangeNotification: Boolean = false  // 抑制页面变化通知
    private var pendingPageIndex: Int = -1  // 待设置的页码（-1 表示最后一页，-2 表示无）

    private val gestureDetector: GestureDetectorCompat
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    var onPageChangeListener: ((page: Int, total: Int) -> Unit)? = null
    var onTapListener: ((x: Float, y: Float) -> Unit)? = null
    var onTapAreaListener: ((area: org.skepsun.kototoro.reader.domain.TapGridArea) -> Unit)? = null
    var onChapterChangeRequestListener: ((delta: Int) -> Unit)? = null  // 请求切换章节的回调

    init {
        // 确保 View 可以接收触摸事件
        isClickable = true
        isFocusable = true
        
        gestureDetector = GestureDetectorCompat(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                // 计算点击区域
                val area = getTapArea(e.x, e.y)
                onTapAreaListener?.invoke(area)
                // 保留旧的回调以兼容
                onTapListener?.invoke(e.x, e.y)
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float,
            ): Boolean {
                if (e1 == null) return false
                val deltaX = e2.x - e1.x
                val deltaY = e2.y - e1.y
                
                if (abs(deltaX) > abs(deltaY) && abs(deltaX) > touchSlop) {
                    if (deltaX > 0) {
                        // 向右滑动 - 上一页，如果失败则请求上一章
                        if (!previousPage()) {
                            onChapterChangeRequestListener?.invoke(-1)
                        }
                    } else {
                        // 向左滑动 - 下一页，如果失败则请求下一章
                        if (!nextPage()) {
                            onChapterChangeRequestListener?.invoke(1)
                        }
                    }
                    return true
                }
                return false
            }
        })
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = gestureDetector.onTouchEvent(event)
        // 确保消费所有触摸事件
        return handled || super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (pages.isEmpty()) {
            // 显示空状态
            val emptyText = context.getString(R.string.no_chapters_in_manga)
            val x = (width - textPaint.measureText(emptyText)) / 2
            val y = height / 2f
            canvas.drawText(emptyText, x, y, textPaint)
            return
        }

        val page = pages.getOrNull(currentPageIndex) ?: return
        
        if (isDualPage && currentPageIndex < pages.lastIndex) {
            // 双页模式
            val nextPage = pages[currentPageIndex + 1]
            drawPage(canvas, page, 0f, width / 2f)
            drawPage(canvas, nextPage, width / 2f, width.toFloat())
        } else {
            // 单页模式
            drawPage(canvas, page, 0f, width.toFloat())
        }
    }

    private fun drawPage(canvas: Canvas, page: PageInfo, left: Float, right: Float) {
        canvas.save()
        // 考虑 View 的 padding（用于避开状态栏等系统 UI）和设置的边距
        val x = left + paddingLeft + settings.marginHorizontal
        val y = paddingTop + settings.marginVertical.toFloat()
        canvas.translate(x, y)
        page.layout?.draw(canvas)
        canvas.restore()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            repaginate()
        }
    }

    /**
     * 设置章节内容
     */
    fun setContent(
        content: String, 
        resetPage: Boolean = true, 
        suppressNotification: Boolean = false,
        initialPageIndex: Int = 0
    ) {
        try {
            android.util.Log.d("NovelReaderView", "setContent called, content length: ${content.length}, resetPage: $resetPage, suppressNotification: $suppressNotification, initialPageIndex: $initialPageIndex")
            chapterContent = content
            
            // 设置是否抑制页面变化通知
            suppressPageChangeNotification = suppressNotification
            
            // 设置待处理的页码
            if (resetPage) {
                pendingPageIndex = initialPageIndex
                currentPageIndex = 0  // 临时设置为 0，避免 repaginate 时使用旧值
            } else {
                pendingPageIndex = -2  // 不改变页码
            }
            
            // 确保在 View 已经测量后再分页
            if (width > 0 && height > 0) {
                android.util.Log.d("NovelReaderView", "View measured: ${width}x${height}, repaginating now")
                repaginate()
            } else {
                android.util.Log.d("NovelReaderView", "View not measured yet, posting repaginate")
                // 如果 View 还没有测量，等待 onSizeChanged
                post { 
                    try {
                        repaginate()
                    } catch (e: Exception) {
                        android.util.Log.e("NovelReaderView", "Failed to repaginate in post", e)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("NovelReaderView", "Failed to set content", e)
        }
    }

    /**
     * 更新设置
     */
    fun updateSettings(newSettings: NovelReaderSettings) {
        settings = newSettings
        textPaint.textSize = resources.resolveSp(settings.fontSizeSp)
        repaginate()
    }

    /**
     * 设置是否双页模式
     */
    fun setDualPageMode(enabled: Boolean) {
        if (isDualPage != enabled) {
            isDualPage = enabled
            repaginate()
        }
    }

    /**
     * 设置页脚高度（用于计算可用空间）
     */
    fun setFooterHeight(height: Int) {
        if (footerHeight != height) {
            footerHeight = height
            repaginate()
        }
    }

    /**
     * 跳转到指定页
     */
    fun goToPage(page: Int) {
        if (page in pages.indices) {
            currentPageIndex = page
            invalidate()
            notifyPageChanged()
        }
    }

    /**
     * 下一页
     */
    fun nextPage(): Boolean {
        val step = if (isDualPage) 2 else 1
        if (currentPageIndex + step < pages.size) {
            currentPageIndex += step
            invalidate()
            notifyPageChanged()
            return true
        }
        return false
    }

    /**
     * 上一页
     */
    fun previousPage(): Boolean {
        val step = if (isDualPage) 2 else 1
        if (currentPageIndex - step >= 0) {
            currentPageIndex -= step
            invalidate()
            notifyPageChanged()
            return true
        }
        return false
    }

    /**
     * 获取当前页码
     */
    fun getCurrentPage(): Int = currentPageIndex

    /**
     * 获取总页数
     */
    fun getTotalPages(): Int = pages.size

    /**
     * 获取当前页面的文本内容
     */
    fun getCurrentPageText(): String {
        if (pages.isEmpty() || currentPageIndex !in pages.indices) {
            return ""
        }
        val page = pages[currentPageIndex]
        return page.text
    }

    /**
     * 重新分页
     */
    private fun repaginate() {
        if (width == 0 || height == 0 || chapterContent.isEmpty()) {
            pages = emptyList()
            invalidate()
            return
        }

        // 保存当前阅读位置（使用字符位置比例）
        val savedCharPosition = if (pages.isNotEmpty() && currentPageIndex in pages.indices) {
            // 获取当前页的第一个字符在章节中的位置
            val currentPageText = pages[currentPageIndex].text
            if (currentPageText.isNotEmpty()) {
                // 找到当前页文本在章节中的起始位置
                val searchText = currentPageText.trim().take(50)
                if (searchText.isNotEmpty()) {
                    chapterContent.indexOf(searchText).coerceAtLeast(0)
                } else {
                    0
                }
            } else {
                0
            }
        } else {
            0
        }
        
        // 计算阅读进度比例（0.0 到 1.0）
        val savedProgressRatio = if (chapterContent.isNotEmpty()) {
            savedCharPosition.toFloat() / chapterContent.length
        } else {
            0f
        }
        
        android.util.Log.d("NovelReaderView", "Repaginating, saved char position: $savedCharPosition/${chapterContent.length}, ratio: $savedProgressRatio (current page: $currentPageIndex/${pages.size})")

        // 计算可用的绘制区域（减去 padding 和 margin）
        val availableWidth = width - paddingLeft - paddingRight
        val availableHeight = height - paddingTop - paddingBottom - footerHeight
        
        val pageWidth = if (isDualPage) {
            (availableWidth / 2) - settings.marginHorizontal * 2
        } else {
            availableWidth - settings.marginHorizontal * 2
        }
        // 减去上下边距
        val pageHeight = availableHeight - settings.marginVertical * 2

        if (pageWidth <= 0 || pageHeight <= 0) {
            android.util.Log.w("NovelReaderView", "Invalid page dimensions: ${pageWidth}x${pageHeight}")
            pages = emptyList()
            invalidate()
            return
        }

        android.util.Log.d("NovelReaderView", "Repaginating with dimensions: ${pageWidth}x${pageHeight} (padding: ${paddingTop}, ${paddingBottom})")
        
        pages = paginateText(chapterContent, pageWidth, pageHeight)
        
        // 处理待设置的页码
        when {
            pendingPageIndex == -1 -> {
                // -1 表示最后一页
                currentPageIndex = max(0, pages.size - 1)
                android.util.Log.d("NovelReaderView", "Set to last page: $currentPageIndex")
                pendingPageIndex = -2  // 重置
            }
            pendingPageIndex >= 0 -> {
                // 设置为指定页码
                currentPageIndex = pendingPageIndex.coerceIn(0, max(0, pages.size - 1))
                android.util.Log.d("NovelReaderView", "Set to pending page: $currentPageIndex")
                pendingPageIndex = -2  // 重置
            }
            savedProgressRatio > 0f && pages.isNotEmpty() -> {
                // 根据保存的进度比例恢复阅读位置
                val targetCharPosition = (chapterContent.length * savedProgressRatio).toInt()
                
                // 找到包含该字符位置的页面
                var targetPage = 0
                var accumulatedChars = 0
                
                for (i in pages.indices) {
                    val pageText = pages[i].text
                    val pageLength = pageText.length
                    
                    if (accumulatedChars + pageLength >= targetCharPosition) {
                        targetPage = i
                        break
                    }
                    
                    accumulatedChars += pageLength
                    targetPage = i
                }
                
                currentPageIndex = targetPage
                android.util.Log.d("NovelReaderView", "Restored to page: $currentPageIndex (target char: $targetCharPosition, ratio: $savedProgressRatio)")
            }
            else -> {
                // 确保当前页在有效范围内
                if (currentPageIndex >= pages.size) {
                    currentPageIndex = max(0, pages.size - 1)
                }
                android.util.Log.d("NovelReaderView", "No saved progress, keeping page: $currentPageIndex")
            }
        }
        
        invalidate()
        notifyPageChanged()
    }

    /**
     * 文本分页算法 - 基于逐行精确测量，确保不漏字
     */
    private fun paginateText(text: String, pageWidth: Int, pageHeight: Int): List<PageInfo> {
        if (text.isBlank()) {
            return listOf(PageInfo("内容为空", null))
        }
        
        android.util.Log.d("NovelReaderView", "Starting pagination: pageWidth=$pageWidth, pageHeight=$pageHeight, textLength=${text.length}")
        
        val result = mutableListOf<PageInfo>()
        
        // 首先为整个文本创建一个完整的布局
        val fullLayout = try {
            createLayout(text, pageWidth)
        } catch (e: Exception) {
            android.util.Log.e("NovelReaderView", "Failed to create full layout", e)
            return listOf(PageInfo("布局创建失败", null))
        }
        
        val totalLines = fullLayout.lineCount
        android.util.Log.d("NovelReaderView", "Total lines in text: $totalLines")
        
        var startLine = 0
        var pageCount = 0
        
        while (startLine < totalLines) {
            var endLine = startLine
            var accumulatedHeight = 0f
            
            // 逐行累加，直到超出页面高度
            while (endLine < totalLines) {
                val lineTop = fullLayout.getLineTop(endLine)
                val lineBottom = fullLayout.getLineBottom(endLine)
                val lineHeight = lineBottom - lineTop
                
                // 检查加上这一行是否会超出页面
                if (accumulatedHeight + lineHeight > pageHeight && endLine > startLine) {
                    // 超出了，使用上一行作为结束
                    break
                }
                
                accumulatedHeight += lineHeight
                endLine++
            }
            
            // 确保至少包含一行（处理单行超高的情况）
            if (endLine == startLine) {
                endLine = startLine + 1
            }
            
            // 提取这一页的文本
            val startOffset = fullLayout.getLineStart(startLine)
            val endOffset = if (endLine < totalLines) {
                fullLayout.getLineEnd(endLine - 1)
            } else {
                text.length
            }
            
            val pageText = text.substring(startOffset, endOffset)
            
            android.util.Log.d("NovelReaderView", "Page ${pageCount + 1}: lines $startLine-${endLine-1}, chars $startOffset-$endOffset, height=$accumulatedHeight")
            
            // 为这一页创建布局
            try {
                val pageLayout = createLayout(pageText, pageWidth)
                result.add(PageInfo(pageText, pageLayout))
                pageCount++
            } catch (e: Exception) {
                android.util.Log.e("NovelReaderView", "Failed to create page layout for page $pageCount", e)
            }
            
            // 移动到下一页的起始行
            startLine = endLine
        }
        
        android.util.Log.d("NovelReaderView", "Pagination complete: $pageCount pages created")
        
        // 确保至少有一页
        return result.ifEmpty { 
            android.util.Log.w("NovelReaderView", "No pages created, using fallback")
            try {
                val fallbackText = text.take(500)
                listOf(PageInfo(fallbackText, createLayout(fallbackText, pageWidth)))
            } catch (e: Exception) {
                android.util.Log.e("NovelReaderView", "Failed to create fallback page", e)
                listOf(PageInfo("加载失败", null))
            }
        }
    }

    private fun createLayout(text: String, width: Int): StaticLayout {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width)
                    .setLineSpacing(0f, settings.lineSpacing)
                    .setIncludePad(false)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                StaticLayout(
                    text,
                    textPaint,
                    width,
                    Layout.Alignment.ALIGN_NORMAL,
                    settings.lineSpacing,
                    0f,
                    false,
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("NovelReaderView", "Failed to create layout for text", e)
            // 返回一个简单的布局
            @Suppress("DEPRECATION")
            StaticLayout(
                text,
                textPaint,
                width,
                Layout.Alignment.ALIGN_NORMAL,
                1.0f,
                0f,
                false,
            )
        }
    }

    private fun notifyPageChanged() {
        if (!suppressPageChangeNotification) {
            onPageChangeListener?.invoke(currentPageIndex, pages.size)
        }
    }
    
    /**
     * 恢复页面变化通知并立即通知一次
     */
    fun resumePageChangeNotification() {
        suppressPageChangeNotification = false
        notifyPageChanged()
    }

    /**
     * 根据点击位置计算点击区域
     * 屏幕按 3x3 网格划分
     */
    private fun getTapArea(x: Float, y: Float): org.skepsun.kototoro.reader.domain.TapGridArea {
        val w = width.toFloat()
        val h = height.toFloat()
        
        val col = when {
            x < w / 3 -> 0  // 左
            x < w * 2 / 3 -> 1  // 中
            else -> 2  // 右
        }
        
        val row = when {
            y < h / 3 -> 0  // 上
            y < h * 2 / 3 -> 1  // 中
            else -> 2  // 下
        }
        
        return when (row * 3 + col) {
            0 -> org.skepsun.kototoro.reader.domain.TapGridArea.TOP_LEFT
            1 -> org.skepsun.kototoro.reader.domain.TapGridArea.TOP_CENTER
            2 -> org.skepsun.kototoro.reader.domain.TapGridArea.TOP_RIGHT
            3 -> org.skepsun.kototoro.reader.domain.TapGridArea.CENTER_LEFT
            4 -> org.skepsun.kototoro.reader.domain.TapGridArea.CENTER
            5 -> org.skepsun.kototoro.reader.domain.TapGridArea.CENTER_RIGHT
            6 -> org.skepsun.kototoro.reader.domain.TapGridArea.BOTTOM_LEFT
            7 -> org.skepsun.kototoro.reader.domain.TapGridArea.BOTTOM_CENTER
            8 -> org.skepsun.kototoro.reader.domain.TapGridArea.BOTTOM_RIGHT
            else -> org.skepsun.kototoro.reader.domain.TapGridArea.CENTER
        }
    }

    /**
     * 页面信息
     */
    private data class PageInfo(
        val text: String,
        val layout: StaticLayout?,
    )
}
