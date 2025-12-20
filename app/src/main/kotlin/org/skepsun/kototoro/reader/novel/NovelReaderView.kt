package org.skepsun.kototoro.reader.novel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.LruCache
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.GestureDetectorCompat
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.util.ext.resolveSp
import org.skepsun.kototoro.local.epub.EpubImageExtractor
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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
	private var pendingProgressRatio: Float? = null // 通过比例定位时的待设置进度
	private var pendingTargetOffset: Int? = null
	private var pendingBiasToEnd: Boolean = false
    
    // Image support
    private var epubFile: File? = null  // 当前EPUB文件，用于提取图片
    private var chapterPath: String? = null  // 当前章节路径，用于解析相对图片路径
    private val imageCache = LruCache<String, Bitmap>(10 * 1024 * 1024)  // 10MB图片缓存
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

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
            val emptyText = try {
                context.getString(R.string.no_chapters_in_manga)
            } catch (e: Exception) {
                "No content"
            }
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
        
        // 绘制文本
        page.layout?.draw(canvas)
        
        // 绘制图片
        for (imageSpan in page.images) {
            val bitmap = loadImage(imageSpan.imagePath)
            if (bitmap != null) {
                val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
                val targetWidth = min(imageSpan.width, bitmap.width.toFloat())
                val targetHeight = targetWidth * bitmap.height / bitmap.width
                val leftPos = (imageSpan.width - targetWidth) / 2f
                val dstRect = RectF(
                    leftPos,
                    imageSpan.yPosition,
                    leftPos + targetWidth,
                    imageSpan.yPosition + targetHeight
                )
                canvas.drawBitmap(bitmap, srcRect, dstRect, imagePaint)
                android.util.Log.d("NovelReaderView", "Drew image at y=${imageSpan.yPosition}, size=${targetWidth}x${targetHeight}")
            } else {
                // 绘制占位符（灰色矩形）
                val placeholderPaint = Paint().apply {
                    color = 0xFFCCCCCC.toInt()
                    style = Paint.Style.FILL
                }
                val placeholderRect = RectF(
                    0f,
                    imageSpan.yPosition,
                    imageSpan.width,
                    imageSpan.yPosition + imageSpan.height
                )
                canvas.drawRect(placeholderRect, placeholderPaint)
                
                // 绘制"图片加载失败"文字
                val errorPaint = Paint().apply {
                    color = 0xFF666666.toInt()
                    textSize = 14f * resources.displayMetrics.density
                    textAlign = Paint.Align.CENTER
                }
                val errorText = "图片加载失败"
                canvas.drawText(
                    errorText,
                    imageSpan.width / 2,
                    imageSpan.yPosition + imageSpan.height / 2,
                    errorPaint
                )
            }
        }
        
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
        initialPageIndex: Int = 0,
        initialProgressRatio: Float? = null,
    ) {
        try {
            android.util.Log.d("NovelReaderView", "setContent called, content length: ${content.length}, resetPage: $resetPage, suppressNotification: $suppressNotification, initialPageIndex: $initialPageIndex")
            chapterContent = content
            
            // 设置是否抑制页面变化通知
            suppressPageChangeNotification = suppressNotification
            
            // 设置待处理的页码
            if (resetPage) {
                pendingPageIndex = initialPageIndex
                pendingProgressRatio = initialProgressRatio
                pendingTargetOffset = initialProgressRatio?.let { (chapterContent.length * it).toInt() }
                pendingBiasToEnd = false
                currentPageIndex = 0  // 临时设置为 0，避免 repaginate 时使用旧值
            } else {
                pendingPageIndex = -2  // 不改变页码
                pendingProgressRatio = null
                pendingTargetOffset = null
                pendingBiasToEnd = false
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
     * 获取当前页起始字符偏移
     */
    fun getCurrentCharOffset(): Int = pages.getOrNull(currentPageIndex)?.startOffset ?: 0

    /**
     * 获取当前章节总字符数
     */
    fun getChapterLength(): Int = chapterContent.length
    fun getCurrentPageEndOffset(): Int = pages.getOrNull(currentPageIndex)?.endOffset ?: chapterContent.length
    fun isDualPage(): Boolean = isDualPage

    /**
     * 当前章节进度（0f-1f），基于字符偏移
     */
    fun getProgressRatio(): Float {
        val total = chapterContent.length
        if (total == 0) return 0f
        return (getCurrentCharOffset().toFloat() / total).coerceIn(0f, 1f)
    }

    /**
     * 用于展示/滑块的页索引（双页时按 spread 计）
     */
    fun getDisplayPageIndex(): Int = if (isDualPage) currentPageIndex / 2 else currentPageIndex

    /**
     * 用于展示/滑块的总页数（双页时按 spread 计）
     */
    fun getDisplayPageCount(): Int = if (isDualPage) {
        (pages.size + 1) / 2
    } else {
        pages.size
    }

    /**
     * 设置按比例定位的待恢复进度
     */
    fun setPendingProgressRatio(ratio: Float) {
        pendingProgressRatio = ratio.coerceIn(0f, 1f)
        pendingTargetOffset = (chapterContent.length * pendingProgressRatio!!).toInt()
        pendingBiasToEnd = false
    }

    fun setPendingOffset(offset: Int, biasToEnd: Boolean) {
        pendingTargetOffset = offset
        pendingBiasToEnd = biasToEnd
        pendingProgressRatio = null
    }

    private fun findClosestPageForOffset(offset: Int, biasToEnd: Boolean): Int {
        if (pages.isEmpty()) return 0
        val clamped = offset.coerceIn(0, chapterContent.length)
        // 先尝试精确命中
        val exact = pages.indexOfFirst { clamped in it.startOffset until it.endOffset }
        if (exact != -1) {
            if (!biasToEnd) return exact
            var idx = exact
            while (idx + 1 < pages.size && clamped >= pages[idx + 1].startOffset) {
                idx++
            }
            return idx
        }
        // 否则选择 startOffset 最接近的页
        var bestIndex = 0
        var bestDiff = Int.MAX_VALUE
        pages.forEachIndexed { index, page ->
            val diff = kotlin.math.abs(page.startOffset - clamped)
            if (diff < bestDiff) {
                bestDiff = diff
                bestIndex = index
            }
        }
        return bestIndex
    }

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
        val savedCharPosition = pages.getOrNull(currentPageIndex)?.startOffset ?: 0
        
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
        
        // 处理待设置的页码/偏移
        when {
            pendingPageIndex == -1 -> {
                currentPageIndex = max(0, pages.size - 1)
                android.util.Log.d("NovelReaderView", "Set to last page: $currentPageIndex")
                pendingPageIndex = -2
            }
            pendingTargetOffset != null || pendingProgressRatio != null -> {
                val offset = pendingTargetOffset
                    ?: ((chapterContent.length * (pendingProgressRatio!!.coerceIn(0f, 1f))).toInt())
                val targetPage = findClosestPageForOffset(offset, pendingBiasToEnd)
                currentPageIndex = targetPage
                pendingTargetOffset = null
                pendingProgressRatio = null
                pendingBiasToEnd = false
                android.util.Log.d("NovelReaderView", "Set to offset page: $currentPageIndex (offset=$offset)")
            }
            pendingPageIndex >= 0 -> {
                currentPageIndex = pendingPageIndex.coerceIn(0, max(0, pages.size - 1))
                android.util.Log.d("NovelReaderView", "Set to pending page: $currentPageIndex")
                pendingPageIndex = -2
            }
            (savedCharPosition > 0 || savedProgressRatio > 0f) && pages.isNotEmpty() -> {
                val targetCharPosition = if (savedCharPosition > 0) {
                    savedCharPosition.coerceAtMost(chapterContent.length)
                } else {
                    (chapterContent.length * savedProgressRatio).toInt()
                }
                val targetPage = findClosestPageForOffset(targetCharPosition, false)
                currentPageIndex = targetPage
                android.util.Log.d("NovelReaderView", "Restored to page: $currentPageIndex (target char: $targetCharPosition, ratio: $savedProgressRatio)")
            }
            else -> {
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
     * 解析文本中的图片占位符
     * 格式：📷 [图片: filename.jpg]
     * @return Pair<处理后的文本, 图片路径列表>
     */
    private fun parseImages(text: String): Pair<String, List<String>> {
        val imagePaths = mutableListOf<String>()
        val imagePattern = Regex("""📷\s*\[图片:\s*([^\]]+)\]""")
        
        var processedText = text
        imagePattern.findAll(text).forEach { match ->
            val imagePath = match.groupValues[1].trim()
            imagePaths.add(imagePath)
            
            android.util.Log.d("NovelReaderView", "Found image placeholder: $imagePath")
            
            // 替换为占位符标记（用于后续定位）
            // 注意：占位符前后不加换行，避免影响布局
            processedText = processedText.replaceFirst(
                match.value,
                "[IMAGE_PLACEHOLDER_${imagePaths.size - 1}]"
            )
        }
        
        android.util.Log.d("NovelReaderView", "Parsed ${imagePaths.size} images from text")
        
        return Pair(processedText, imagePaths)
    }
    
    /**
     * 文本分页算法 - 基于逐行精确测量，确保不漏字
     * 支持图片：解析图片占位符，计算图片位置和大小
     */
    private fun paginateText(text: String, pageWidth: Int, pageHeight: Int): List<PageInfo> {
        if (text.isBlank()) {
            return listOf(PageInfo("内容为空", null, 0, 0, emptyList()))
        }
        
        android.util.Log.d("NovelReaderView", "Starting pagination: pageWidth=$pageWidth, pageHeight=$pageHeight, textLength=${text.length}")
        
        // 解析图片
        val (processedText, imagePaths) = parseImages(text)
        val hasImages = imagePaths.isNotEmpty()
        
        if (hasImages) {
            android.util.Log.d("NovelReaderView", "Found ${imagePaths.size} images in text")
        }
        
        val result = mutableListOf<PageInfo>()
        
        // 首先为整个文本创建一个完整的布局
        val fullLayout = try {
            createLayout(processedText, pageWidth)
        } catch (e: Exception) {
            android.util.Log.e("NovelReaderView", "Failed to create full layout", e)
            return listOf(PageInfo("布局创建失败", null, 0, 0, emptyList()))
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
                processedText.length
            }
            
            val pageText = processedText.substring(startOffset, endOffset)
            
            android.util.Log.d("NovelReaderView", "Page ${pageCount + 1}: lines $startLine-${endLine-1}, chars $startOffset-$endOffset, height=$accumulatedHeight")
            
            // 检查这一页是否包含图片占位符
            val pageImages = mutableListOf<ImageSpan>()
            var displayText = pageText  // 用于显示的文本（移除占位符）
            
            if (hasImages) {
                val placeholderPattern = Regex("""\[IMAGE_PLACEHOLDER_(\d+)\]""")
                val displayBuilder = StringBuilder()
                var lastIndex = 0
                placeholderPattern.findAll(pageText).forEach { match ->
                    val imageIndex = match.groupValues[1].toInt()
                    if (imageIndex < imagePaths.size) {
                        val imagePath = imagePaths[imageIndex]
                        
                        // 先追加占位符前的文本，便于后续计算位置信息
                        val before = pageText.substring(lastIndex, match.range.first)
                        displayBuilder.append(before)
                        lastIndex = match.range.last + 1

                        // 创建临时布局来计算Y坐标（基于当前已写入的显示文本）
                        val tempLayout = createLayout(displayBuilder.toString(), pageWidth)
                        val yPosition = if (tempLayout.lineCount > 0) {
                            tempLayout.getLineBottom(tempLayout.lineCount - 1).toFloat()
                        } else {
                            0f
                        }
                        
                        // 计算图片尺寸（保持宽高比，宽度填满页面）
                        val imageWidth = pageWidth.toFloat()
                        // 预设高度（实际应该根据图片真实尺寸计算，这里先用固定比例）
                        val imageHeight = imageWidth * 0.75f  // 4:3 比例

                        // 为图片留出行高空间，避免覆盖文字
                        val lineHeight = textPaint.fontSpacing
                        val spacerLines = max(1, kotlin.math.ceil(imageHeight / lineHeight).toInt())
                        repeat(spacerLines) { displayBuilder.append("\n") }
                        
                        pageImages.add(
                            ImageSpan(
                                imagePath = imagePath,
                                yPosition = yPosition,
                                width = imageWidth,
                                height = imageHeight
                            )
                        )
                        
                        android.util.Log.d("NovelReaderView", "Added image to page: $imagePath at y=$yPosition")
                    }
                }
                // 追加剩余文本
                if (lastIndex < pageText.length) {
                    displayBuilder.append(pageText.substring(lastIndex))
                }
                displayText = displayBuilder.toString()
            }
            
            // 为这一页创建布局（使用移除占位符后的文本）
            try {
                val pageLayout = createLayout(displayText, pageWidth)
                result.add(PageInfo(displayText, pageLayout, startOffset, endOffset, pageImages))
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
                val fallbackText = processedText.take(500)
                listOf(PageInfo(fallbackText, createLayout(fallbackText, pageWidth), 0, fallbackText.length, emptyList()))
            } catch (e: Exception) {
                android.util.Log.e("NovelReaderView", "Failed to create fallback page", e)
                listOf(PageInfo("加载失败", null, 0, 0, emptyList()))
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
        val startOffset: Int,
        val endOffset: Int,
        val images: List<ImageSpan> = emptyList(),  // 页面中的图片
    )
    
    /**
     * 图片信息
     * @param imagePath EPUB中的图片路径
     * @param yPosition 图片在页面中的Y坐标
     * @param width 图片宽度
     * @param height 图片高度
     */
    private data class ImageSpan(
        val imagePath: String,
        val yPosition: Float,
        val width: Float,
        val height: Float,
    )
    
    /**
     * 设置EPUB文件信息（用于提取图片）
     */
    fun setEpubInfo(file: File?, chapterPath: String?) {
        this.epubFile = file
        this.chapterPath = chapterPath
        android.util.Log.d("NovelReaderView", "Set EPUB info: file=${file?.name}, chapterPath=$chapterPath")
    }
    
    /**
     * 清除图片缓存
     */
    fun clearImageCache() {
        imageCache.evictAll()
        android.util.Log.d("NovelReaderView", "Image cache cleared")
    }
    
    /**
     * 从EPUB中提取并缓存图片
     */
    private fun loadImage(imagePath: String): Bitmap? {
        val file = epubFile
        if (file == null) {
            android.util.Log.w("NovelReaderView", "Cannot load image: epubFile is null")
            return null
        }
        
        if (!file.exists()) {
            android.util.Log.w("NovelReaderView", "Cannot load image: EPUB file does not exist: ${file.absolutePath}")
            return null
        }
        
        // 检查缓存
        val cacheKey = "${file.absolutePath}:$imagePath"
        imageCache.get(cacheKey)?.let { 
            android.util.Log.d("NovelReaderView", "Image loaded from cache: $imagePath")
            return it 
        }
        
        // 从EPUB提取图片
        try {
            val extractor = EpubImageExtractor(file)
            
            // 解析相对路径
            val resolvedPath = if (chapterPath != null) {
                val resolved = extractor.resolveImagePath(chapterPath!!, imagePath)
                android.util.Log.d("NovelReaderView", "Resolved path: $imagePath -> $resolved (chapterPath: $chapterPath)")
                resolved
            } else {
                android.util.Log.d("NovelReaderView", "No chapterPath, using image path as-is: $imagePath")
                imagePath
            }
            
            android.util.Log.d("NovelReaderView", "Extracting image from EPUB: $resolvedPath")
            
            val bitmap = extractor.extractImageAsBitmap(resolvedPath)
            if (bitmap != null) {
                // 缓存图片
                imageCache.put(cacheKey, bitmap)
                android.util.Log.d("NovelReaderView", "✅ Image loaded successfully: ${bitmap.width}x${bitmap.height}")
            } else {
                android.util.Log.w("NovelReaderView", "❌ Failed to extract image from EPUB: $resolvedPath")
                // 列出EPUB中的所有图片，帮助调试
                val allImages = extractor.listImages()
                android.util.Log.d("NovelReaderView", "Available images in EPUB (${allImages.size}): ${allImages.take(10)}")
            }
            
            return bitmap
        } catch (e: Exception) {
            android.util.Log.e("NovelReaderView", "Error loading image: $imagePath", e)
            return null
        }
    }
}
