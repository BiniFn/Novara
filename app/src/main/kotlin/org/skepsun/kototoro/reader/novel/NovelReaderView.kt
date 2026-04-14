package org.skepsun.kototoro.reader.novel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.collection.LruCache
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.GestureDetectorCompat
import coil3.ImageLoader
import coil3.asDrawable
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import okhttp3.Headers
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.util.ext.resolveSp
import org.skepsun.kototoro.core.image.ContentSourceHeaderInterceptor
import org.skepsun.kototoro.local.epub.EpubImageExtractor
import java.io.File
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 小说阅读器视图 - 基于 TextView 的自定义实现
 * 参考 Legado 的分页算法
 */
@AndroidEntryPoint
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
    var chapterContent: String = ""
        private set
    private var activeTranslation: NovelChapterTranslation? = null
    private var pages: List<PageInfo> = emptyList()
    private var currentPageIndex: Int = 0
	private var isDualPage: Boolean = false
	private var footerHeight: Int = 0  // 页脚高度，用于计算可用空间
	private var headerHeight: Int = 0  // 页头高度，用于计算可用空间
	private var suppressPageChangeNotification: Boolean = false  // 抑制页面变化通知
	private var pendingPageIndex: Int = -1  // 待设置的页码（-1 表示最后一页，-2 表示无）
	private var pendingProgressRatio: Float? = null // 通过比例定位时的待设置进度
	private var pendingTargetOffset: Int? = null
	private var pendingBiasToEnd: Boolean = false
    private var paginatedTotalLength: Int = 0  // 经过分页处理（含图片占位符）后的总长度
    
    // Scroll state
    private val scroller = android.widget.OverScroller(context)
    private var maxScrollOffset: Int = 0
    private var isFlinging: Boolean = false
    
    // Image support
    private var epubFile: File? = null  // 当前EPUB文件，用于提取图片
    private var chapterPath: String? = null  // 当前章节路径，用于解析相对图片路径
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    var imageHeadersProvider: ((String) -> Map<String, String>?)? = null

    // TTS Highlight
    private var highlightRange: IntRange? = null
    private val highlightPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            val typedValue = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.colorAccent, typedValue, true)
            val colorAccent = if (typedValue.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT &&
                typedValue.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT) {
                typedValue.data
            } else {
                androidx.core.content.ContextCompat.getColor(context, typedValue.resourceId)
            }
            color = Color.argb(80, Color.red(colorAccent), Color.green(colorAccent), Color.blue(colorAccent))
        }
    }
    
    @Inject
    lateinit var imageLoader: ImageLoader
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val loadingImages = mutableSetOf<String>()
    private val failedImages = mutableSetOf<String>() // Prevent infinite retry loops
    
    // 内存缓存作为第一级（Coil 已经有缓存，但这里保持一份以减少 main thread 闪烁）
    private val imageCache = LruCache<String, Bitmap>(50)

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
            override fun onDown(e: MotionEvent): Boolean {
                if (!scroller.isFinished) {
                    scroller.forceFinished(true)
                }
                return true
            }

            override fun onScroll(
                e1: MotionEvent?, e2: MotionEvent,
                distanceX: Float, distanceY: Float
            ): Boolean {
                if (settings.readingMode == ReadingMode.SCROLL) {
                    val oldY = scrollY
                    val newY = (oldY + distanceY.toInt()).coerceIn(0, maxScrollOffset)
                    if (oldY != newY) {
                        scrollTo(0, newY)
                        invalidate()
                    } else {
                        // 触发边界章节切换逻辑
                        if (oldY <= 0 && distanceY < -touchSlop) {
                            onChapterChangeRequestListener?.invoke(-1) // 上一章
                        } else if (oldY >= maxScrollOffset && distanceY > touchSlop) {
                            onChapterChangeRequestListener?.invoke(1) // 下一章
                        }
                    }
                    return true
                }
                return false
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                // 计算点击区域
                val area = getTapArea(e.x, e.y)
                onTapAreaListener?.invoke(area)
                // 保留旧的回调以兼容
                onTapListener?.invoke(e.x, e.y)
                return true
            }

            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent,
                velocityX: Float, velocityY: Float,
            ): Boolean {
                if (settings.readingMode == ReadingMode.SCROLL) {
                    if (abs(velocityY) > abs(velocityX)) {
                        isFlinging = true
                        scroller.fling(
                            0, scrollY,
                            0, -velocityY.toInt(),
                            0, 0,
                            0, maxScrollOffset
                        )
                        invalidate()
                        return true
                    }
                    return false
                }
                
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

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            val oldY = scrollY
            val y = scroller.currY
            if (oldY != y) {
                scrollTo(0, y)
            }
            invalidate()
        } else if (isFlinging) {
            isFlinging = false
            // Check bounding for chapter transition after fling
            val currentScroll = scrollY
            if (currentScroll <= 0 && scrollY < scroller.startY) {
                onChapterChangeRequestListener?.invoke(-1)
            } else if (currentScroll >= maxScrollOffset && scrollY > scroller.startY) {
                onChapterChangeRequestListener?.invoke(1)
            }
        }
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

        if (settings.readingMode == ReadingMode.SCROLL) {
            if (pages.isNotEmpty()) {
                val page = pages[0]
                drawPage(canvas, page, 0f, width.toFloat())
            }
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
        val y = paddingTop + settings.marginVertical.toFloat() + headerHeight
        canvas.translate(x, y)
        
        // 绘制高亮背景
        highlightRange?.let { range ->
            val intersectStart = max(page.startOffset, range.first)
            val intersectEnd = min(page.endOffset, range.last + 1)
            if (intersectStart < intersectEnd && page.layout != null) {
                val path = android.graphics.Path()
                val localStart = intersectStart - page.startOffset
                val localEnd = intersectEnd - page.startOffset
                page.layout.getSelectionPath(localStart, localEnd, path)
                canvas.drawPath(path, highlightPaint)
            }
        }
        
        // 绘制文本
        page.layout?.draw(canvas)
        
        // 绘制图片
        for (imageSpan in page.images) {
            val bitmap = loadImage(imageSpan.imagePath)
            if (bitmap != null) {
                val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
                
                // 修复布局重叠：计算缩放后的尺寸，且不能超过预留的高度
                val rawTargetHeight = imageSpan.width * bitmap.height / bitmap.width
                val scale = if (rawTargetHeight > imageSpan.height) {
                    imageSpan.height / rawTargetHeight
                } else {
                    1f
                }
                
                val targetWidth = imageSpan.width * scale
                val targetHeight = rawTargetHeight * scale
                
                val leftPos = (imageSpan.width - targetWidth) / 2f
                // 垂直居中绘制在预留空间内
                val topPos = imageSpan.yPosition + (imageSpan.height - targetHeight) / 2f
                
                val dstRect = RectF(
                    leftPos,
                    topPos,
                    leftPos + targetWidth,
                    topPos + targetHeight
                )
                canvas.drawBitmap(bitmap, srcRect, dstRect, imagePaint)
                // android.util.Log.d("NovelReaderView", "Drew image at y=$topPos, size=${targetWidth}x${targetHeight}, reserved height=${imageSpan.height}")
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
                // 根据当前的 paginatedTotalLength 预估偏移（虽然 repaginate 会重新定位，但这有助于保持连贯性）
                val total = if (paginatedTotalLength > 0) paginatedTotalLength else chapterContent.length
                pendingTargetOffset = initialProgressRatio?.let { (total * it).toInt() }
                pendingBiasToEnd = false
                currentPageIndex = 0  // 临时设置为 0，避免 repaginate 时使用旧值
            } else {
                pendingPageIndex = -2  // 不改变页码
                pendingProgressRatio = null
                pendingTargetOffset = null
                pendingBiasToEnd = false
            }
            
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
            // Clear request tracking
            loadingImages.clear()
            failedImages.clear()
        } catch (e: Exception) {
            android.util.Log.e("NovelReaderView", "Failed to set content", e)
        }
    }

    /**
     * 更新翻译结果，触发重新分页和渲染。
     * 传入 null 则清除翻译，恢复显示原文。
     */
    fun setTranslation(translation: NovelChapterTranslation?) {
        activeTranslation = translation
        repaginate()
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
     * 设置页头高度（用于避开顶部的阅读状态栏）
     */
    fun setHeaderHeight(height: Int) {
        if (headerHeight != height) {
            headerHeight = height
            repaginate()
        }
    }

    /**
     * 设置此时此刻的 TTS 高亮游标
     */
    fun setHighlightRange(range: IntRange?) {
        if (highlightRange != range) {
            highlightRange = range
            invalidate()
        }
    }

    /**
     * 跳转到指定页
     */
    fun goToPage(page: Int) {
        if (settings.readingMode == ReadingMode.SCROLL) {
            val h = height - paddingTop - paddingBottom - footerHeight - headerHeight - settings.marginVertical * 2
            if (h > 0) {
                val targetScroll = (page * h).coerceIn(0, maxScrollOffset)
                scrollTo(0, targetScroll)
                notifyPageChanged()
            }
            return
        }
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
        if (settings.readingMode == ReadingMode.SCROLL) {
            val h = height - paddingTop - paddingBottom - footerHeight - headerHeight - settings.marginVertical * 2
            if (h > 0 && scrollY < maxScrollOffset) {
                val targetScroll = (scrollY + h).coerceIn(0, maxScrollOffset)
                scroller.startScroll(0, scrollY, 0, targetScroll - scrollY, 250)
                invalidate()
                return true
            }
            return false
        }
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
        if (settings.readingMode == ReadingMode.SCROLL) {
            val h = height - paddingTop - paddingBottom - footerHeight - headerHeight - settings.marginVertical * 2
            if (h > 0 && scrollY > 0) {
                val targetScroll = (scrollY - h).coerceIn(0, maxScrollOffset)
                scroller.startScroll(0, scrollY, 0, targetScroll - scrollY, 250)
                invalidate()
                return true
            }
            return false
        }
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
    fun getCurrentPage(): Int {
        if (settings.readingMode == ReadingMode.SCROLL) return getDisplayPageIndex()
        return currentPageIndex
    }

    /**
     * 获取总页数
     */
    fun getTotalPages(): Int {
        if (settings.readingMode == ReadingMode.SCROLL) return getDisplayPageCount()
        return pages.size
    }

    /**
     * 获取当前页起始字符偏移
     */
    fun getCurrentCharOffset(): Int {
        if (settings.readingMode == ReadingMode.SCROLL && pages.isNotEmpty()) {
            val layout = pages[0].layout ?: return 0
            val line = layout.getLineForVertical(scrollY)
            return layout.getLineStart(line)
        }
        return pages.getOrNull(currentPageIndex)?.startOffset ?: 0
    }

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
        val total = paginatedTotalLength
        if (total == 0) return 0f
        return (getCurrentCharOffset().toFloat() / total).coerceIn(0f, 1f)
    }

    /**
     * 用于展示/滑块的页索引（双页时按 spread 计）
     */
    fun getDisplayPageIndex(): Int {
        if (settings.readingMode == ReadingMode.SCROLL) {
            val h = height - paddingTop - paddingBottom - footerHeight - headerHeight - settings.marginVertical * 2
            if (h <= 0) return 0
            return (scrollY / h).coerceIn(0, kotlin.math.max(0, getDisplayPageCount() - 1))
        }
        return if (isDualPage) currentPageIndex / 2 else currentPageIndex
    }

    /**
     * 用于展示/滑块的总页数（双页时按 spread 计）
     */
    fun getDisplayPageCount(): Int {
        if (settings.readingMode == ReadingMode.SCROLL) {
            val h = height - paddingTop - paddingBottom - footerHeight - headerHeight - settings.marginVertical * 2
            if (h <= 0) return 1
            val contentHeight = pages.getOrNull(0)?.layout?.height ?: 0
            return kotlin.math.max(1, kotlin.math.ceil(contentHeight.toFloat() / h).toInt())
        }
        return if (isDualPage) {
            (pages.size + 1) / 2
        } else {
            pages.size
        }
    }

    /**
     * 设置按比例定位的待恢复进度
     */
    fun setPendingProgressRatio(ratio: Float) {
        pendingProgressRatio = ratio.coerceIn(0f, 1f)
        val total = if (paginatedTotalLength > 0) paginatedTotalLength else chapterContent.length
        pendingTargetOffset = (total * pendingProgressRatio!!).toInt()
        pendingBiasToEnd = false
    }

    fun setPendingOffset(offset: Int, biasToEnd: Boolean) {
        pendingTargetOffset = offset
        pendingBiasToEnd = biasToEnd
        pendingProgressRatio = null
    }

    private fun findClosestPageForOffset(offset: Int, biasToEnd: Boolean): Int {
        if (pages.isEmpty()) return 0
        val clamped = offset.coerceIn(0, paginatedTotalLength)
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
     * 获取当前页面在章节中的绝对起始下标
     */
    fun getCurrentPageStartOffset(): Int {
        if (pages.isEmpty() || currentPageIndex !in pages.indices) {
            return 0
        }
        val page = pages[currentPageIndex]
        return page.startOffset
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
        
        // 计算阅读进度比例（0.0 到 1.0），基于处理后的长度
        val savedProgressRatio = if (paginatedTotalLength > 0) {
            savedCharPosition.toFloat() / paginatedTotalLength
        } else {
            0f
        }
        
        android.util.Log.d("NovelReaderView", "Repaginating, saved char position: $savedCharPosition/$paginatedTotalLength, ratio: $savedProgressRatio")

        // 计算可用的绘制区域（减去 padding 和 margin）
        val availableWidth = width - paddingLeft - paddingRight
        val availableHeight = height - paddingTop - paddingBottom - footerHeight
        
        val pageWidth = if (isDualPage) {
            (availableWidth / 2) - settings.marginHorizontal * 2
        } else {
            availableWidth - settings.marginHorizontal * 2
        }
        // 减去上下边距
        val pageHeight = if (settings.readingMode == ReadingMode.SCROLL) {
            10000000 // 足够大的高度以生成单页
        } else {
            availableHeight - settings.marginVertical * 2 - headerHeight
        }

        if (pageWidth <= 0 || pageHeight <= 0) {
            android.util.Log.w("NovelReaderView", "Invalid page dimensions: ${pageWidth}x${pageHeight}")
            pages = emptyList()
            paginatedTotalLength = 0
            invalidate()
            return
        }

        android.util.Log.d("NovelReaderView", "Repaginating with dimensions: ${pageWidth}x${pageHeight} (padding: ${paddingTop}, ${paddingBottom})")

        // 使用翻译后内容（如果有）进行分页，否则使用原始内容
        val contentForPagination = applyTranslationToContent(chapterContent, activeTranslation)
        pages = paginateText(contentForPagination, pageWidth, pageHeight)

        // BILINGUAL 模式：用 SpannableStringBuilder 重建每页 layout，使原文呈灰色小字
        if (activeTranslation != null &&
            activeTranslation!!.displayMode == NovelTranslationDisplayMode.BILINGUAL &&
            activeTranslation!!.translations.isNotEmpty()
        ) {
            pages = pages.map { page ->
                val spannable = applyBilingualSpannable(page.text, activeTranslation!!)
                if (spannable !== page.text) {
                    page.copy(layout = createLayout(spannable, pageWidth))
                } else {
                    page
                }
            }
        }
        
        // 处理待设置的页码/偏移
        val targetCharOffset = when {
            pendingTargetOffset != null -> pendingTargetOffset!!
            pendingProgressRatio != null -> (paginatedTotalLength * pendingProgressRatio!!).roundToInt()
            pendingPageIndex == -1 -> paginatedTotalLength
            (savedCharPosition > 0 || savedProgressRatio > 0f) -> (paginatedTotalLength * savedProgressRatio).roundToInt()
            else -> 0
        }.coerceIn(0, paginatedTotalLength)

        when {
            pendingPageIndex == -1 -> {
                currentPageIndex = max(0, pages.size - 1)
                android.util.Log.d("NovelReaderView", "Set to last page: $currentPageIndex")
                pendingPageIndex = -2
            }
            pendingTargetOffset != null || pendingProgressRatio != null -> {
                val targetPage = findClosestPageForOffset(targetCharOffset, pendingBiasToEnd)
                currentPageIndex = targetPage
                pendingTargetOffset = null
                pendingProgressRatio = null
                pendingBiasToEnd = false
                android.util.Log.d("NovelReaderView", "Set to offset page: $currentPageIndex (offset=$targetCharOffset)")
            }
            pendingPageIndex >= 0 -> {
                currentPageIndex = pendingPageIndex.coerceIn(0, max(0, pages.size - 1))
                android.util.Log.d("NovelReaderView", "Set to pending page: $currentPageIndex")
                pendingPageIndex = -2
            }
            (savedCharPosition > 0 || savedProgressRatio > 0f) && pages.isNotEmpty() -> {
                val targetPage = findClosestPageForOffset(targetCharOffset, false)
                currentPageIndex = targetPage
                android.util.Log.d("NovelReaderView", "Restored to page: $currentPageIndex (target char: $targetCharOffset, ratio: $savedProgressRatio)")
            }
            else -> {
                if (currentPageIndex >= pages.size) {
                    currentPageIndex = max(0, pages.size - 1)
                }
                android.util.Log.d("NovelReaderView", "No saved progress, keeping page: $currentPageIndex")
            }
        }
        
        if (settings.readingMode == ReadingMode.SCROLL && pages.isNotEmpty()) {
            val contentHeight = pages[0].layout?.height ?: 0
            val viewportTextHeight = availableHeight - settings.marginVertical * 2 - headerHeight
            maxScrollOffset = kotlin.math.max(0, contentHeight - viewportTextHeight)
            
            // Calculate exact Y scroll position based on targetCharOffset
            val layout = pages[0].layout
            var targetScroll = 0
            if (layout != null && targetCharOffset > 0) {
                if (targetCharOffset >= paginatedTotalLength) {
                    targetScroll = maxScrollOffset
                } else {
                    val line = layout.getLineForOffset(targetCharOffset)
                    targetScroll = layout.getLineTop(line)
                }
            }
            scrollTo(0, targetScroll.coerceIn(0, maxScrollOffset))
        } else {
            maxScrollOffset = 0
            if (scrollY != 0) scrollTo(0, 0)
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
        val htmlImagePattern = Regex("""<img[^>]+src=['"]([^'"]+)['"][^>]*>""", RegexOption.IGNORE_CASE)

        var processedText = text
        imagePattern.findAll(text).forEach { match ->
            val imagePath = match.groupValues[1].trim()
            imagePaths.add(imagePath)
            
            android.util.Log.d("NovelReaderView", "Found image placeholder: $imagePath")
            
            // 替换为占位符标记（用于后续定位）
            processedText = processedText.replaceFirst(
                match.value,
                "[IMAGE_PLACEHOLDER_${imagePaths.size - 1}]"
            )
        }

        // 同时支持直接的 <img> 标签（来自 HTML 内容的插图）
        processedText = processedText.replace(htmlImagePattern) { matchResult ->
            val src = matchResult.groupValues.getOrNull(1)?.trim().orEmpty()
            if (src.isNotEmpty()) {
                imagePaths.add(src)
                "[IMAGE_PLACEHOLDER_${imagePaths.size - 1}]"
            } else {
                ""
            }
        }

        // 将常见的换行标记转为实际换行，并移除其他HTML标签
        processedText = processedText
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p>"), "\n")
            .replace(Regex("(?i)<p[^>]*>"), "")
            .replace(Regex("<[^>]+>"), "")
        
        android.util.Log.d("NovelReaderView", "Parsed ${imagePaths.size} images from text")

        // 应用段落间距：若间距为 0，则保持原始换行；否则规范换行后再扩展空行
        val normalized = processedText.replace(Regex("\\n{3,}"), "\n\n")
        val spacedText = if (settings.paragraphSpacing <= 0f) normalized else applyParagraphSpacing(normalized)
        val indented = applyParagraphIndent(spacedText)
        return Pair(indented, imagePaths)
    }

    private fun applyParagraphSpacing(text: String): String {
        val spacingDp = settings.paragraphSpacing
        if (spacingDp <= 0f) return text
        val spacingPx = spacingDp * resources.displayMetrics.density
        val lineHeight = (textPaint.fontMetrics.descent - textPaint.fontMetrics.ascent) * settings.lineSpacing
        // 改进：即使间距小于一行，也至少增加一个换行，确保视觉上有分隔
        // 除非设置真的非常小。这里使用 ceil 确保 0.1 行也会变成 1 行空行。
        val extraLines = if (spacingPx > 0) kotlin.math.max(1, kotlin.math.ceil(spacingPx / lineHeight).toInt()) else 0
        if (extraLines == 0) return text
        val spacer = "\n".repeat(extraLines)
        // 使用正则分割，避免空行累加导致无限空行
        return text.split(Regex("\\n+")).joinToString(separator = "\n$spacer")
    }

    private fun applyParagraphIndent(text: String): String {
        if (!settings.enableParagraphIndent) return text
        val indent = "　　" // 两个全角空格
        val sb = StringBuilder(text.length + 16)
        text.split("\n").forEachIndexed { idx, line ->
            if (idx > 0) sb.append('\n')
            if (line.isBlank()) {
                sb.append(line)
            } else {
                if (line.startsWith(indent)) {
                    sb.append(line)
                } else {
                    sb.append(indent).append(line.trimStart())
                }
            }
        }
        return sb.toString()
    }
    
    /**
     * 根据翻译结果，将章节内容转换为展示用文本。
     * 与 NovelChapterView 中的同名方法保持逻辑一致。
     */
    private fun applyTranslationToContent(
        content: String,
        translation: NovelChapterTranslation?,
    ): String {
        if (translation == null || translation.translations.isEmpty()) return content
        val paragraphs = translation.paragraphs
        if (paragraphs.isEmpty()) return content

        val sb = StringBuilder()
        for ((i, para) in paragraphs.withIndex()) {
            if (i > 0) sb.append("\n\n")
            if (para.type == NovelParagraphType.IMAGE) {
                sb.append(para.originalText)
                continue
            }
            val translated = translation.translations[para.index]
            if (translated.isNullOrBlank()) {
                sb.append(para.originalText)
                continue
            }
            when (translation.displayMode) {
                NovelTranslationDisplayMode.TRANSLATION_ONLY -> sb.append(translated)
                NovelTranslationDisplayMode.BILINGUAL -> {
                    sb.append(para.originalText)
                    sb.append("\n")
                    sb.append(translated)
                }
            }
        }
        return sb.toString()
    }

    /**
     * 双语模式：在已拼接好的页面文本上，对原文部分叠加灰色 + 小字 Span。
     * 通过定位每个原文段落在页面文本中的位置来施加 Span。
     * TRANSLATION_ONLY 或无翻译时直接返回原字符串（引用不变）。
     */
    private fun applyBilingualSpannable(
        pageText: String,
        translation: NovelChapterTranslation,
    ): CharSequence {
        if (translation.translations.isEmpty()) return pageText
        val grayColor = android.graphics.Color.GRAY
        val smallSize = 0.8f
        val ssb = SpannableStringBuilder(pageText)
        var modified = false

        for (para in translation.paragraphs) {
            if (para.type != NovelParagraphType.TEXT) continue
            val translated = translation.translations[para.index] ?: continue
            if (translated.isBlank()) continue

            val orig = para.originalText
            var searchFrom = 0
            while (searchFrom < ssb.length) {
                val idx = ssb.indexOf(orig, searchFrom)
                if (idx < 0) break
                val afterOrig = idx + orig.length
                if (afterOrig < ssb.length && ssb[afterOrig] == '\n') {
                    ssb.setSpan(
                        ForegroundColorSpan(grayColor),
                        idx, afterOrig,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                    ssb.setSpan(
                        RelativeSizeSpan(smallSize),
                        idx, afterOrig,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                    modified = true
                }
                searchFrom = afterOrig + 1
                break
            }
        }
        return if (modified) ssb else pageText
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
        var (processedText, imagePaths) = parseImages(text)
        val hasImages = imagePaths.isNotEmpty()
        
        if (hasImages) {
            android.util.Log.d("NovelReaderView", "Found ${imagePaths.size} images in text")
            // 为每个图片预留高度：使用固定行高的增加版
            val lineHeight = (textPaint.fontMetrics.descent - textPaint.fontMetrics.ascent) * settings.lineSpacing
            val imageWidth = pageWidth.toFloat()
            val imageHeight = imageWidth * 0.75f
            // 根据设置的间距，保证图片前后有足够的“行”作为间隔
            val paraSpacingPx = settings.paragraphSpacing * resources.displayMetrics.density
            val extraSpacerLines = if (paraSpacingPx > 0) kotlin.math.max(1, kotlin.math.ceil(paraSpacingPx / lineHeight).toInt()) else 0
            val spacerLines = (imageHeight / lineHeight).toInt() + (extraSpacerLines * 2).coerceAtLeast(2)
            val spacer = "\n".repeat(spacerLines)
            
            var newText = processedText
            for (i in imagePaths.indices) {
                val placeholder = "[IMAGE_PLACEHOLDER_$i]"
                // 占位符前后各一个换行，使其完全独立
                newText = newText.replace(placeholder, "\n$placeholder\n$spacer\n")
            }
            processedText = newText
        }
        
        paginatedTotalLength = processedText.length
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
                
                // 检查这一行是否是图片占位符的开始
                // 如果是，我们需要检查这一整块图片（包括前后的 spacers）是否能放入当前页
                // 如果不能，且当前页已经有内容，则提前换页，让图片作为下一页的开头
                val lineStart = fullLayout.getLineStart(endLine)
                val lineEnd = fullLayout.getLineEnd(endLine)
                val lineText = processedText.substring(lineStart, lineEnd)
                
                if (lineText.contains("[IMAGE_PLACEHOLDER")) {
                     // 这是一个包含图片占位符的行。
                     // 简单起见，如果这一行导致（或即将导致）页面高度紧张，我们直接break，将其推到下一页。
                     // 这里的 "紧张" 可以定义为：当前页剩余高度可能不足以显示完整图片。
                     // 由于图片的真实高度是通过 spacer 模拟的，而 spacer 可能跨越多行。
                     // 如果我们在 spacer 的中间切断，图片就会被切断。
                     
                     // 策略：如果当前页已经有了一定的高度（比如 > 20%），遇到图片标志时，
                     // 如果剩下的高度不足以容纳这张图片（预估高度），则换页。
                     // 图片预估高度 ~ 0.75 * width (根据 parseImages 中的设定)
                     val estimatedImageHeight = pageWidth * 0.75f
                     if (accumulatedHeight > 0 && accumulatedHeight + estimatedImageHeight > pageHeight) {
                         // 将图片推到下一页
                         break
                     }
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
                
                // 遍历页面文本中的占位符
                placeholderPattern.findAll(pageText).forEach { match ->
                    val imageIndex = match.groupValues[1].toInt()
                    if (imageIndex < imagePaths.size) {
                        val imagePath = imagePaths[imageIndex]
                        
                        // 1. 追加占位符前的文本
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
                        val imageHeight = imageWidth * 0.75f 
                        
                        pageImages.add(
                            ImageSpan(
                                imagePath = imagePath,
                                yPosition = yPosition,
                                width = imageWidth,
                                height = imageHeight
                            )
                        )
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

    private fun createLayout(text: CharSequence, width: Int): StaticLayout {
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
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope.cancel()
    }

    /**
     * 加载图片（支持网络和EPUB本地提取）
     * 使用 Coil 进行高效加载和磁盘缓存
     */
    private fun loadImage(imagePath: String): Bitmap? {
        val cacheKey = "${epubFile?.absolutePath ?: "remote"}:$imagePath"
        imageCache.get(cacheKey)?.let { return it }

        if (loadingImages.contains(cacheKey) || failedImages.contains(cacheKey)) return null

        loadingImages.add(cacheKey)
        scope.launch {
            try {
                val bitmap = when {
                    imagePath.startsWith("http", ignoreCase = true) -> {
                        loadCoilImage(imagePath)
                    }
                    imagePath.startsWith("file+zip", ignoreCase = true) || 
                    imagePath.startsWith("zip", ignoreCase = true) || 
                    imagePath.startsWith("cbz", ignoreCase = true) -> {
                        // 解析绝对 ZIP URL
                        val uri = java.net.URI(imagePath)
                        val zipPath = uri.schemeSpecificPart.substringBefore('#').removePrefix("///").let { 
                            if (it.startsWith("/")) it else "/$it" 
                        }
                        val entryName = uri.fragment?.removePrefix("/") ?: ""
                        
                        // 临时设置 EPUB 文件用于提取（不修改全局 epubFile，避免影响当前章节其他相对路径图片）
                        if (entryName.isNotEmpty()) {
                            loadEpubImageViaCoil(entryName, customEpubFile = java.io.File(zipPath))
                        } else null
                    }
                    imagePath.startsWith("file://", ignoreCase = true) -> {
                        // 如果是普通本地文件，直接交给 Coil
                        loadCoilImage(imagePath)
                    }
                    else -> {
                        // 默认为 EPUB 相对路径提取
                        loadEpubImageViaCoil(imagePath)
                    }
                }

                if (bitmap != null) {
                    imageCache.put(cacheKey, bitmap)
                    loadingImages.remove(cacheKey)
                    invalidate()
                    android.util.Log.d("NovelReaderView", "Image loaded and cached via Coil: $imagePath")
                } else {
                    loadingImages.remove(cacheKey)
                }
            } catch (e: Exception) {
                loadingImages.remove(cacheKey)
                failedImages.add(cacheKey)
                android.util.Log.e("NovelReaderView", "Failed to load image via Coil: $imagePath", e)
            }
        }

        return null
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (settings.readingMode == ReadingMode.SCROLL && height > 0) {
            val h = height - paddingTop - paddingBottom - footerHeight - headerHeight - settings.marginVertical * 2
            if (h > 0) {
                val newPage = (t / h).coerceIn(0, kotlin.math.max(0, getDisplayPageCount() - 1))
                val oldPage = (oldt / h).coerceIn(0, kotlin.math.max(0, getDisplayPageCount() - 1))
                if (newPage != oldPage) {
                    notifyPageChanged()
                }
            }
        }
    }

    private suspend fun loadCoilImage(url: String): Bitmap? = withContext(Dispatchers.IO) {
        val requestBuilder = ImageRequest.Builder(context)
            .data(url)

        // 为需要防盗链的图片附加头，由外部提供
        imageHeadersProvider?.invoke(url)?.takeIf { it.isNotEmpty() }?.let { extra: Map<String, String> ->
            val headers = NetworkHeaders.Builder().apply {
                extra.forEach { (k, v) -> add(k, v) }
            }.build()
            requestBuilder.httpHeaders(headers)
        }

        val request = requestBuilder.build()
        
        return@withContext when (val result = imageLoader.execute(request)) {
            is SuccessResult -> result.image.toBitmap(
                width = result.image.width,
                height = result.image.height,
            )
            is ErrorResult -> {
                android.util.Log.w("NovelReaderView", "Coil failed to load remote image: $url", result.throwable)
                // Mark as failed in the main scope handler (it catches exceptions but we should also track ErrorResult)
                throw result.throwable // Propagate to catch block
            }
        }
    }

    private suspend fun loadEpubImageViaCoil(imagePath: String, customEpubFile: File? = null): Bitmap? = withContext(Dispatchers.IO) {
        val file = customEpubFile ?: epubFile ?: return@withContext null
        if (!file.exists()) return@withContext null

        val extractor = EpubImageExtractor(file)
        val resolvedPath = if (chapterPath != null) {
            extractor.resolveImagePath(chapterPath!!, imagePath)
        } else {
            imagePath
        }

        val bytes = extractor.extractImage(resolvedPath) ?: return@withContext null
        
        // 使用 Coil 加载字节数组，确保持久缓存和正确解码
        val request = ImageRequest.Builder(context)
            .data(bytes)
            .build()
            
        return@withContext when (val result = imageLoader.execute(request)) {
            is SuccessResult -> result.image.toBitmap(
                width = result.image.width,
                height = result.image.height,
            )
            is ErrorResult -> {
                android.util.Log.w("NovelReaderView", "Coil failed to decode EPUB image: $imagePath", result.throwable)
                throw result.throwable
            }
        }
    }

    private fun loadLocalImage(imagePath: String): Bitmap? = null // Removed, replaced by loadEpubImageViaCoil
    private fun loadRemoteImage(url: String): Bitmap? = null     // Removed, replaced by loadCoilImage
}
