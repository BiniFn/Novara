package org.skepsun.kototoro.reader.novel

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
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
import kotlin.math.sign

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

    private var settings: NovelReaderSettings = NovelReaderSettings.load(context)
    private var palette: NovelReaderPalette = novelReaderPalette(
        preset = settings.themePreset,
        isDarkTheme = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
    )

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.textColor
        textSize = resources.resolveSp(17f)
        isSubpixelText = true
        letterSpacing = 0.01f
    }

    var chapterContent: String = ""
        private set
    private var activeTranslation: NovelChapterTranslation? = null
    private var pages: List<PageInfo> = emptyList()
    private var currentPageIndex: Int = 0
	private var isDualPage: Boolean = false
	private var footerHeight: Int = 0  // 页脚高度，用于计算可用空间
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
            color = palette.highlightColor
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
    private val pageTurnThresholdFraction = 0.18f
    private val pageTurnInterpolator = DecelerateInterpolator()
    private var pageSwipeStartX: Float = 0f
    private var pageSwipeStartY: Float = 0f
    private var isPageDragging: Boolean = false
    private var pageSwipeBaseIndex: Int = -1
    private var pageSwipeTargetIndex: Int = -1
    private var pageSwipeOffsetX: Float = 0f
    private var pageSwipeAnimator: ValueAnimator? = null
    private var pageSwipeChapterDelta: Int = 0
    private var isAwaitingChapterTransitionContent: Boolean = false
    private var previousChapterPreviewText: String? = null
    private var nextChapterPreviewText: String? = null
    private var previousChapterPreviewPages: List<PageInfo> = emptyList()
    private var nextChapterPreviewPages: List<PageInfo> = emptyList()

    var onPageChangeListener: ((page: Int, total: Int) -> Unit)? = null
    var onTapListener: ((x: Float, y: Float) -> Unit)? = null
    var onTapAreaListener: ((area: org.skepsun.kototoro.reader.domain.TapGridArea) -> Unit)? = null
    var onChapterChangeRequestListener: ((delta: Int) -> Unit)? = null  // 请求切换章节的回调
    var onImageClickListener: ((NovelInlineImageRequest) -> Unit)? = null

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
                findInlineImageAt(e.x, e.y)?.let { image ->
                    onImageClickListener?.invoke(image)
                    return true
                }
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
                
                return false
            }
        })
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (settings.readingMode != ReadingMode.SCROLL && isAwaitingChapterTransitionContent) {
            return true
        }
        if (settings.readingMode != ReadingMode.SCROLL && handlePagedTouch(event)) {
            return true
        }
        val handled = gestureDetector.onTouchEvent(event)
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
        canvas.drawColor(palette.backgroundColor)
        
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

        if (pageSwipeBaseIndex >= 0 && pageSwipeTargetIndex >= 0 && pageSwipeOffsetX != 0f) {
            drawPageSwipe(canvas)
            return
        }

        if (currentPageIndex !in pages.indices) return
        drawSpread(canvas, currentPageIndex, 0f)
    }

    private fun drawPageSwipe(canvas: Canvas) {
        drawSpread(canvas, pageSwipeBaseIndex, pageSwipeOffsetX)
        if (pageSwipeChapterDelta != 0) {
            drawChapterBoundaryPreview(canvas, pageSwipeChapterDelta, pageSwipeOffsetX)
            return
        }
        val targetOffset = if (pageSwipeOffsetX < 0f) {
            pageSwipeOffsetX + width
        } else {
            pageSwipeOffsetX - width
        }
        drawSpread(canvas, pageSwipeTargetIndex, targetOffset)
    }

    private fun drawSpread(canvas: Canvas, startIndex: Int, offsetX: Float) {
        val page = pages.getOrNull(startIndex) ?: return
        if (isDualPage && startIndex < pages.lastIndex) {
            val nextPage = pages[startIndex + 1]
            drawPage(canvas, page, offsetX, offsetX + width / 2f)
            drawPage(canvas, nextPage, offsetX + width / 2f, offsetX + width.toFloat())
        } else {
            drawPage(canvas, page, offsetX, offsetX + width.toFloat())
        }
    }

    private fun drawChapterBoundaryPreview(canvas: Canvas, chapterDelta: Int, offsetX: Float) {
        val previewPages = if (chapterDelta > 0) nextChapterPreviewPages else previousChapterPreviewPages
        if (previewPages.isEmpty()) {
            return
        }
        val previewOffset = if (offsetX < 0f) {
            offsetX + width
        } else {
            offsetX - width
        }
        val startIndex = if (chapterDelta < 0) {
            getLastBoundaryPreviewStartIndex(previewPages.size)
        } else {
            0
        }
        drawPreviewSpread(canvas, previewPages, startIndex, previewOffset)
    }

    private fun drawPreviewSpread(
        canvas: Canvas,
        previewPages: List<PageInfo>,
        startIndex: Int,
        offsetX: Float,
    ) {
        val page = previewPages.getOrNull(startIndex) ?: return
        if (isDualPage) {
            val nextPage = previewPages.getOrNull(startIndex + 1)
            drawPage(canvas, page, offsetX, offsetX + width / 2f)
            if (nextPage != null) {
                drawPage(canvas, nextPage, offsetX + width / 2f, offsetX + width.toFloat())
            }
        } else {
            drawPage(canvas, page, offsetX, offsetX + width.toFloat())
        }
    }
    private fun drawPage(canvas: Canvas, page: PageInfo, left: Float, right: Float) {
        canvas.save()
        // 考虑 View 的 padding（用于避开状态栏等系统 UI）和设置的边距
        val x = left + paddingLeft + settings.marginHorizontal
        val y = paddingTop + settings.marginVertical.toFloat()
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
                val dstRect = getNovelImageDisplayRect(
                    imagePath = imageSpan.imagePath,
                    reservedWidth = imageSpan.width,
                    reservedHeight = imageSpan.height,
                    yPosition = imageSpan.yPosition,
                )
                canvas.drawBitmap(bitmap, srcRect, dstRect, imagePaint)
            } else {
                // 绘制占位符（灰色矩形）
                val placeholderPaint = Paint().apply {
                    color = palette.placeholderColor
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
                    color = palette.placeholderTextColor
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
            repaginateBoundaryPreviews()
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

            if (tryAdoptChapterBoundaryPreview(content, resetPage, initialPageIndex, initialProgressRatio)) {
                loadingImages.clear()
                failedImages.clear()
                return
            }
            
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

    private fun tryAdoptChapterBoundaryPreview(
        content: String,
        resetPage: Boolean,
        initialPageIndex: Int,
        initialProgressRatio: Float?,
    ): Boolean {
        if (
            settings.readingMode == ReadingMode.SCROLL ||
            !resetPage ||
            activeTranslation != null ||
            width <= 0 ||
            height <= 0
        ) {
            return false
        }
        val adoptingNext = isAwaitingChapterTransitionContent &&
            initialPageIndex == 0 &&
            nextChapterPreviewText == content &&
            nextChapterPreviewPages.isNotEmpty()
        val adoptingPrevious = isAwaitingChapterTransitionContent &&
            initialPageIndex == -1 &&
            previousChapterPreviewText == content &&
            previousChapterPreviewPages.isNotEmpty()
        if (!adoptingNext && !adoptingPrevious) {
            return false
        }
        val adoptedPages = if (adoptingNext) nextChapterPreviewPages else previousChapterPreviewPages
        val adoptedPageIndex = when {
            initialProgressRatio != null -> {
                val targetOffset = (content.length * initialProgressRatio).roundToInt()
                adoptedPages.indexOfFirst { targetOffset in it.startOffset until it.endOffset }
                    .takeIf { it >= 0 }
                    ?: 0
            }
            initialPageIndex == -1 -> getLastBoundaryPreviewStartIndex(adoptedPages.size)
            else -> initialPageIndex.coerceIn(0, max(0, adoptedPages.size - 1))
        }
        pages = adoptedPages
        paginatedTotalLength = content.length
        currentPageIndex = adoptedPageIndex
        pendingPageIndex = -2
        pendingProgressRatio = null
        pendingTargetOffset = null
        pendingBiasToEnd = false
        maxScrollOffset = 0
        if (scrollY != 0) {
            scrollTo(0, 0)
        }
        resetPageSwipeState()
        invalidate()
        notifyPageChanged()
        return true
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
        updatePalette()
        repaginate()
        repaginateBoundaryPreviews()
    }

    fun updatePalette(
        palette: NovelReaderPalette = novelReaderPalette(
            preset = settings.themePreset,
            isDarkTheme = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        )
    ) {
        this.palette = palette
        textPaint.color = palette.textColor
        highlightPaint.color = palette.highlightColor
        invalidate()
    }

    /**
     * 设置是否双页模式
     */
    fun setDualPageMode(enabled: Boolean) {
        if (isDualPage != enabled) {
            isDualPage = enabled
            repaginate()
            repaginateBoundaryPreviews()
        }
    }

    /**
     * 设置页脚高度（用于计算可用空间）
     */
    fun setFooterHeight(height: Int) {
        if (footerHeight != height) {
            footerHeight = height
            repaginate()
            repaginateBoundaryPreviews()
        }
    }

    fun setChapterBoundaryPreview(chapterDelta: Int, content: String?) {
        when {
            chapterDelta > 0 -> nextChapterPreviewText = content?.takeIf { it.isNotBlank() }
            chapterDelta < 0 -> previousChapterPreviewText = content?.takeIf { it.isNotBlank() }
            else -> return
        }
        repaginateBoundaryPreview(chapterDelta)
    }

    fun clearChapterBoundaryPreviews() {
        previousChapterPreviewText = null
        nextChapterPreviewText = null
        previousChapterPreviewPages = emptyList()
        nextChapterPreviewPages = emptyList()
        invalidate()
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
            val h = height - paddingTop - paddingBottom - footerHeight - settings.marginVertical * 2
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
            val h = height - paddingTop - paddingBottom - footerHeight - settings.marginVertical * 2
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
            animatePageSettle(targetIndex = currentPageIndex + step, commit = true, direction = -1)
            return true
        }
        return false
    }

    /**
     * 上一页
     */
    fun previousPage(): Boolean {
        if (settings.readingMode == ReadingMode.SCROLL) {
            val h = height - paddingTop - paddingBottom - footerHeight - settings.marginVertical * 2
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
            animatePageSettle(targetIndex = currentPageIndex - step, commit = true, direction = 1)
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
            val h = height - paddingTop - paddingBottom - footerHeight - settings.marginVertical * 2
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
            val h = height - paddingTop - paddingBottom - footerHeight - settings.marginVertical * 2
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
        resetPageSwipeState()
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
            availableHeight - settings.marginVertical * 2
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
                currentPageIndex = getLastBoundaryPreviewStartIndex(pages.size)
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
            val viewportTextHeight = availableHeight - settings.marginVertical * 2
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

    private fun repaginateBoundaryPreviews() {
        repaginateBoundaryPreview(-1)
        repaginateBoundaryPreview(1)
    }

    private fun repaginateBoundaryPreview(chapterDelta: Int) {
        if (width == 0 || height == 0 || settings.readingMode == ReadingMode.SCROLL) {
            if (chapterDelta > 0) {
                nextChapterPreviewPages = emptyList()
            } else {
                previousChapterPreviewPages = emptyList()
            }
            return
        }
        val previewText = if (chapterDelta > 0) nextChapterPreviewText else previousChapterPreviewText
        val previewPages = previewText
            ?.takeIf { it.isNotBlank() }
            ?.let(::paginatePreviewText)
            .orEmpty()
        if (chapterDelta > 0) {
            nextChapterPreviewPages = previewPages
        } else {
            previousChapterPreviewPages = previewPages
        }
        invalidate()
    }

    private fun paginatePreviewText(text: String): List<PageInfo> {
        val availableWidth = width - paddingLeft - paddingRight
        val availableHeight = height - paddingTop - paddingBottom - footerHeight
        val pageWidth = if (isDualPage) {
            (availableWidth / 2) - settings.marginHorizontal * 2
        } else {
            availableWidth - settings.marginHorizontal * 2
        }
        val pageHeight = availableHeight - settings.marginVertical * 2
        if (pageWidth <= 0 || pageHeight <= 0) {
            return emptyList()
        }
        val previousTotalLength = paginatedTotalLength
        return try {
            paginateText(text, pageWidth, pageHeight)
        } finally {
            paginatedTotalLength = previousTotalLength
        }
    }

    private fun getLastBoundaryPreviewStartIndex(pageCount: Int): Int {
        if (pageCount <= 1) {
            return 0
        }
        if (!isDualPage) {
            return pageCount - 1
        }
        return ((pageCount - 1) / 2) * 2
    }

    private fun prepareContentText(text: String): String {
        return NovelTypography.prepareContentText(text, settings, textPaint)
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
        var hasVisibleParagraph = false
        for (para in paragraphs) {
            if (para.originalText.isBlank()) continue
            if (hasVisibleParagraph) sb.append("\n\n")
            if (para.type == NovelParagraphType.IMAGE) {
                sb.append(para.originalText)
                hasVisibleParagraph = true
                continue
            }
            val translated = translation.translations[para.index]
            if (translated.isNullOrBlank()) {
                sb.append(para.originalText)
                hasVisibleParagraph = true
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
            hasVisibleParagraph = true
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
        if (translation.translations.isEmpty()) {
            return NovelTypography.styleChapterTitles(pageText, palette.secondaryTextColor)
        }
        return NovelTypography.applyBilingualSpannable(
            processedText = pageText,
            translation = translation,
            secondaryColor = palette.secondaryTextColor,
        )
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
        
        val parsedImages = parseNovelImages(text)
        var processedText = prepareContentText(parsedImages.text)
        val blockImagePaths = parsedImages.blockImagePaths
        val inlineImagePaths = parsedImages.inlineImagePaths
        val hasImages = blockImagePaths.isNotEmpty()
        
        if (hasImages) {
            android.util.Log.d("NovelReaderView", "Found ${blockImagePaths.size} block images and ${inlineImagePaths.size} inline images in text")
            // 为每个图片预留高度：使用固定行高的增加版
            val lineHeight = (textPaint.fontMetrics.descent - textPaint.fontMetrics.ascent) * settings.lineSpacing
            val maxImageHeight = resources.displayMetrics.heightPixels * 1.5f
            // 根据设置的间距，保证图片前后有足够的“行”作为间隔
            val paraSpacingPx = settings.paragraphSpacing * resources.displayMetrics.density
            val extraSpacerLines = if (paraSpacingPx > 0) kotlin.math.max(1, kotlin.math.ceil(paraSpacingPx / lineHeight).toInt()) else 0
            
            var newText = processedText
            for (i in blockImagePaths.indices) {
                val placeholder = "[IMAGE_PLACEHOLDER_$i]"
                val imageHeight = getReservedNovelImageHeight(
                    imagePath = blockImagePaths[i],
                    maxWidth = pageWidth.toFloat(),
                    maxHeight = maxImageHeight,
                )
                val spacerLines = (imageHeight / lineHeight).toInt() + (extraSpacerLines * 2).coerceAtLeast(2)
                val spacer = "\n".repeat(spacerLines)
                // 占位符前后各一个换行，使其完全独立
                newText = newText.replace(placeholder, "\n$placeholder\n$spacer\n")
            }
            processedText = newText
        }
        
        paginatedTotalLength = processedText.length
        val result = mutableListOf<PageInfo>()
        
        // 首先为整个文本创建一个完整的布局
        val fullLayout = try {
            createLayout(
                applyInlineImageSpans(processedText, inlineImagePaths) { path ->
                    loadImage(path)
                },
                pageWidth,
            )
        } catch (e: Exception) {
            android.util.Log.e("NovelReaderView", "Failed to create full layout", e)
            return listOf(PageInfo("布局创建失败", null, 0, 0, emptyList()))
        }
        
        val totalLines = fullLayout.lineCount
        android.util.Log.d("NovelReaderView", "Total lines in text: $totalLines")
        
        var startLine = 0
        var pageCount = 0
        val maxImageHeight = resources.displayMetrics.heightPixels * 1.5f
        
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
                     val imageIndex = Regex("""\[IMAGE_PLACEHOLDER_(\d+)\]""")
                         .find(lineText)
                         ?.groupValues
                         ?.getOrNull(1)
                         ?.toIntOrNull()
                     val estimatedImageHeight = imageIndex
                         ?.takeIf { it in blockImagePaths.indices }
                         ?.let {
                             getReservedNovelImageHeight(
                                 imagePath = blockImagePaths[it],
                                 maxWidth = pageWidth.toFloat(),
                                 maxHeight = maxImageHeight,
                             )
                         }
                         ?: pageWidth * 0.75f
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
                    if (imageIndex < blockImagePaths.size) {
                        val imagePath = blockImagePaths[imageIndex]
                        
                        // 1. 追加占位符前的文本
                        val before = pageText.substring(lastIndex, match.range.first)
                        displayBuilder.append(before)
                        lastIndex = match.range.last + 1

                        // 创建临时布局来计算Y坐标（基于当前已写入的显示文本）
                        val tempLayout = createLayout(
                            applyInlineImageSpans(displayBuilder.toString(), inlineImagePaths) { path ->
                                loadImage(path)
                            },
                            pageWidth,
                        )
                        val yPosition = if (tempLayout.lineCount > 0) {
                            tempLayout.getLineBottom(tempLayout.lineCount - 1).toFloat()
                        } else {
                            0f
                        }
                        
                        val imageWidth = pageWidth.toFloat()
                        val imageHeight = getReservedNovelImageHeight(
                            imagePath = imagePath,
                            maxWidth = imageWidth,
                            maxHeight = maxImageHeight,
                        )
                        
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
                val pageLayoutText = applyInlineImageSpans(
                    if (activeTranslation != null &&
                        activeTranslation!!.displayMode == NovelTranslationDisplayMode.BILINGUAL &&
                        activeTranslation!!.translations.isNotEmpty()
                    ) {
                        applyBilingualSpannable(displayText, activeTranslation!!)
                    } else {
                        displayText
                    },
                    inlineImagePaths,
                ) { path ->
                    loadImage(path)
                }
                val pageLayout = createLayout(pageLayoutText, pageWidth)
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
                val fallbackLayoutText = applyInlineImageSpans(fallbackText, inlineImagePaths) { path ->
                    loadImage(path)
                }
                listOf(PageInfo(fallbackText, createLayout(fallbackLayoutText, pageWidth), 0, fallbackText.length, emptyList()))
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

    private fun findInlineImageAt(x: Float, y: Float): NovelInlineImageRequest? {
        if (pages.isEmpty() || settings.readingMode == ReadingMode.SCROLL) {
            return null
        }
        val availableWidth = width - paddingLeft - paddingRight
        val pageWidth = if (isDualPage) {
            (availableWidth / 2f) - settings.marginHorizontal * 2f
        } else {
            availableWidth - settings.marginHorizontal * 2f
        }
        if (pageWidth <= 0f) {
            return null
        }
        val pageTop = paddingTop + settings.marginVertical.toFloat()
        val pageSlots = buildList {
            add(currentPageIndex to 0f)
            if (isDualPage && currentPageIndex < pages.lastIndex) {
                add((currentPageIndex + 1) to width / 2f)
            }
        }
        for ((index, left) in pageSlots) {
            val page = pages.getOrNull(index) ?: continue
            val pageLeft = left + paddingLeft + settings.marginHorizontal
            val localX = x - pageLeft
            val localY = y - pageTop
            if (localX !in 0f..pageWidth || localY < 0f) {
                continue
            }
            val inlineImagePath = page.layout?.let { layout ->
                findInlineImagePathAt(layout, localX, localY)
            }
            if (inlineImagePath != null) {
                return NovelInlineImageRequest(
                    imagePath = inlineImagePath,
                    epubFilePath = epubFile?.absolutePath,
                    chapterPath = chapterPath,
                    headers = imageHeadersProvider?.invoke(inlineImagePath).orEmpty(),
                )
            }
            val image = page.images.firstOrNull { span ->
                getNovelImageDisplayRect(
                    imagePath = span.imagePath,
                    reservedWidth = span.width,
                    reservedHeight = span.height,
                    yPosition = span.yPosition,
                ).contains(localX, localY)
            } ?: continue
            return NovelInlineImageRequest(
                imagePath = image.imagePath,
                epubFilePath = epubFile?.absolutePath,
                chapterPath = chapterPath,
                headers = imageHeadersProvider?.invoke(image.imagePath).orEmpty(),
            )
        }
        return null
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
        pageSwipeAnimator?.cancel()
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
                    val metrics = NovelImageMetrics(bitmap.width, bitmap.height)
                    val previousMetrics = NovelImageMetricsCache.get(imagePath)
                    NovelImageMetricsCache.put(imagePath, metrics)
                    if (previousMetrics != metrics) {
                        repaginate()
                    } else {
                        invalidate()
                    }
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
            val h = height - paddingTop - paddingBottom - footerHeight - settings.marginVertical * 2
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

    private fun handlePagedTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pageSwipeAnimator?.cancel()
                pageSwipeStartX = event.x
                pageSwipeStartY = event.y
                isPageDragging = false
                resetPageSwipeState(keepOffset = false)
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.x - pageSwipeStartX
                val deltaY = event.y - pageSwipeStartY
                if (!isPageDragging && abs(deltaX) > touchSlop && abs(deltaX) > abs(deltaY)) {
                    val targetIndex = resolveSwipeTargetIndex(deltaX)
                    val chapterDelta = if (targetIndex < 0) resolveSwipeChapterDelta(deltaX) else 0
                    if (targetIndex >= 0 || chapterDelta != 0) {
                        isPageDragging = true
                        pageSwipeBaseIndex = currentPageIndex
                        pageSwipeTargetIndex = if (targetIndex >= 0) targetIndex else currentPageIndex
                        pageSwipeChapterDelta = chapterDelta
                        parent?.requestDisallowInterceptTouchEvent(true)
                    } else {
                        return false
                    }
                }
                if (isPageDragging) {
                    pageSwipeOffsetX = deltaX.coerceIn(-width.toFloat(), width.toFloat())
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_UP -> {
                if (isPageDragging) {
                    val shouldCommit = abs(pageSwipeOffsetX) >= width * pageTurnThresholdFraction
                    animatePageSettle(
                        targetIndex = pageSwipeTargetIndex,
                        commit = shouldCommit,
                        direction = pageSwipeOffsetX.sign.toInt(),
                    )
                    isPageDragging = false
                    return true
                }
            }
        }
        return false
    }

    private fun animatePageSettle(targetIndex: Int, commit: Boolean, direction: Int) {
        if (direction == 0 || pageSwipeBaseIndex == -1) {
            pageSwipeBaseIndex = currentPageIndex
            pageSwipeTargetIndex = targetIndex
        }
        val resolvedDirection = if (direction == 0) {
            when {
                targetIndex > currentPageIndex -> -1
                targetIndex < currentPageIndex -> 1
                else -> 0
            }
        } else {
            direction
        }
        if (resolvedDirection == 0) return

        pageSwipeAnimator?.cancel()
        val targetOffset = if (commit) {
            if (resolvedDirection > 0) width.toFloat() else -width.toFloat()
        } else {
            0f
        }
        pageSwipeAnimator = ValueAnimator.ofFloat(pageSwipeOffsetX, targetOffset).apply {
            var cancelled = false
            duration = 180L
            interpolator = pageTurnInterpolator
            addUpdateListener { animator ->
                pageSwipeOffsetX = animator.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled && commit) {
                        if (pageSwipeChapterDelta != 0) {
                            isAwaitingChapterTransitionContent = true
                            onChapterChangeRequestListener?.invoke(pageSwipeChapterDelta)
                            invalidate()
                            return
                        } else {
                            currentPageIndex = targetIndex
                            notifyPageChanged()
                        }
                    }
                    resetPageSwipeState()
                    invalidate()
                }
            })
            start()
        }
    }

    private fun resolveSwipeTargetIndex(deltaX: Float): Int {
        val step = if (isDualPage) 2 else 1
        return when {
            deltaX < 0f && currentPageIndex + step < pages.size -> currentPageIndex + step
            deltaX > 0f && currentPageIndex - step >= 0 -> currentPageIndex - step
            else -> -1
        }
    }

    private fun resolveSwipeChapterDelta(deltaX: Float): Int {
        val step = if (isDualPage) 2 else 1
        return when {
            deltaX < 0f && currentPageIndex + step >= pages.size -> 1
            deltaX > 0f && currentPageIndex - step < 0 -> -1
            else -> 0
        }
    }

    private fun resetPageSwipeState(keepOffset: Boolean = false) {
        isPageDragging = false
        isAwaitingChapterTransitionContent = false
        pageSwipeBaseIndex = -1
        pageSwipeTargetIndex = -1
        pageSwipeChapterDelta = 0
        if (!keepOffset) {
            pageSwipeOffsetX = 0f
        }
    }

    fun cancelPendingChapterTransition() {
        if (isAwaitingChapterTransitionContent || pageSwipeChapterDelta != 0 || pageSwipeBaseIndex >= 0) {
            resetPageSwipeState()
            invalidate()
        }
    }

    private fun loadLocalImage(imagePath: String): Bitmap? = null // Removed, replaced by loadEpubImageViaCoil
    private fun loadRemoteImage(url: String): Bitmap? = null     // Removed, replaced by loadCoilImage
}
