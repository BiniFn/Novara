package org.skepsun.kototoro.reader.novel

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Region
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

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
    private val simulationPageTurnInterpolator = LinearInterpolator()
    private var pageSwipeDownX: Float = 0f
    private var pageSwipeDownY: Float = 0f
    private var pageSwipeStartX: Float = 0f
    private var pageSwipeStartY: Float = 0f
    private var isPageDragging: Boolean = false
    private var pageSwipeBaseIndex: Int = -1
    private var pageSwipeTargetIndex: Int = -1
    private var pageSwipeOffsetX: Float = 0f
    private var pageSwipeAnimator: ValueAnimator? = null
    private var pageSwipeChapterDelta: Int = 0
    private var pageSwipeDirection: Int = 0
    private var pageSwipeLastX: Float = 0f
    private var pageSwipeCurrentX: Float = 0f
    private var pageSwipeCurrentY: Float = 0f
    private var isSimulationAutoPageTurn: Boolean = false
    private var isAwaitingChapterTransitionContent: Boolean = false
    private var previousChapterPreviewText: String? = null
    private var nextChapterPreviewText: String? = null
    private var previousChapterPreviewPages: List<PageInfo> = emptyList()
    private var nextChapterPreviewPages: List<PageInfo> = emptyList()
    private val foldPath = Path()
    private val foldBackPath = Path()
    private val foldStart1 = PointF()
    private val foldControl1 = PointF()
    private val foldVertex1 = PointF()
    private val foldEnd1 = PointF()
    private val foldStart2 = PointF()
    private val foldControl2 = PointF()
    private val foldVertex2 = PointF()
    private val foldEnd2 = PointF()
    private val foldBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val foldFolderShadowDrawableRL = GradientDrawable(
        GradientDrawable.Orientation.RIGHT_LEFT,
        intArrayOf(0x333333, -0x4fcccccd),
    ).apply { gradientType = GradientDrawable.LINEAR_GRADIENT }
    private val foldFolderShadowDrawableLR = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(0x333333, -0x4fcccccd),
    ).apply { gradientType = GradientDrawable.LINEAR_GRADIENT }
    private val foldBackShadowDrawableRL = GradientDrawable(
        GradientDrawable.Orientation.RIGHT_LEFT,
        intArrayOf(-0xeeeeef, 0x111111),
    ).apply { gradientType = GradientDrawable.LINEAR_GRADIENT }
    private val foldBackShadowDrawableLR = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(-0xeeeeef, 0x111111),
    ).apply { gradientType = GradientDrawable.LINEAR_GRADIENT }
    private val foldFrontShadowDrawableVLR = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(-0x7feeeeef, 0x111111),
    ).apply { gradientType = GradientDrawable.LINEAR_GRADIENT }
    private val foldFrontShadowDrawableVRL = GradientDrawable(
        GradientDrawable.Orientation.RIGHT_LEFT,
        intArrayOf(-0x7feeeeef, 0x111111),
    ).apply { gradientType = GradientDrawable.LINEAR_GRADIENT }
    private val foldFrontShadowDrawableHTB = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(-0x7feeeeef, 0x111111),
    ).apply { gradientType = GradientDrawable.LINEAR_GRADIENT }
    private val foldFrontShadowDrawableHBT = GradientDrawable(
        GradientDrawable.Orientation.BOTTOM_TOP,
        intArrayOf(-0x7feeeeef, 0x111111),
    ).apply { gradientType = GradientDrawable.LINEAR_GRADIENT }
    private var foldCurrentBitmap: Bitmap? = null
    private var foldTargetBitmap: Bitmap? = null
    private var foldCurrentHalfBitmap: Bitmap? = null
    private var foldTargetHalfBitmap: Bitmap? = null
    private val foldBitmapCanvas = Canvas()
    private var foldBitmapBaseIndex: Int = -1
    private var foldBitmapTargetIndex: Int = -1
    private var foldBitmapChapterDelta: Int = 0
    private var foldMaxLength: Float = 0f
    private var foldDegrees: Float = 0f
    private var foldTouchToCornerDistance: Float = 0f
    private var foldCornerX: Float = 0f
    private var foldCornerY: Float = 0f
    private var foldTouchX: Float = 0f
    private var foldTouchY: Float = 0f
    private var foldIsRtOrLb: Boolean = false
    private var foldViewWidth: Int = 0
    private var foldViewHeight: Int = 0
    private var pageSwipeFoldCornerX: Float = 0f
    private var pageSwipeFoldCornerY: Float = 0f
    private var pageSwipeFoldCornerLocked: Boolean = false

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

        if (pageSwipeBaseIndex >= 0 && pageSwipeTargetIndex >= 0) {
            drawPageSwipe(canvas)
            return
        }

        if (currentPageIndex !in pages.indices) return
        drawSpread(canvas, currentPageIndex, 0f)
    }

    private fun drawPageSwipe(canvas: Canvas) {
        if (settings.pageTurnAnimation == NovelPageTurnAnimation.SIMULATION) {
            drawPageSimulationSwipe(canvas)
            return
        }
        if (pageSwipeOffsetX == 0f) {
            drawSpread(canvas, pageSwipeBaseIndex, 0f)
            return
        }
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

    private fun drawPageSimulationSwipe(canvas: Canvas) {
        if (width <= 0 || height <= 0) return
        val forward = pageSwipeDirection < 0
        val currentBitmap = getFoldCurrentBitmap() ?: run {
            drawSpread(canvas, pageSwipeBaseIndex, pageSwipeOffsetX)
            return
        }
        val targetBitmap = getFoldTargetBitmap(forward) ?: run {
            drawSpread(canvas, pageSwipeBaseIndex, pageSwipeOffsetX)
            return
        }
        calcFoldCornerXY(forward)
        calcFoldPoints(
            forward = forward,
            touchX = getSimulationTouchX(),
            touchY = pageSwipeCurrentY,
        )
        drawFoldCurrentPageArea(canvas, currentBitmap)
        drawFoldNextPageAreaAndShadow(canvas, targetBitmap)
        drawFoldCurrentPageShadow(canvas)
        drawFoldCurrentBackArea(canvas)
    }

    @Suppress("unused")
    private fun drawDualPageSimulationSwipe(canvas: Canvas) {
        val forward = pageSwipeDirection < 0
        val currentBitmap = getFoldCurrentBitmap() ?: run {
            drawSpread(canvas, pageSwipeBaseIndex, pageSwipeOffsetX)
            return
        }
        val targetBitmap = getFoldTargetBitmap(forward) ?: run {
            drawSpread(canvas, pageSwipeBaseIndex, pageSwipeOffsetX)
            return
        }
        drawDualPageSimulationBase(canvas, currentBitmap, targetBitmap, forward)
        val halfWidth = width / 2f
        val pageRect = if (forward) {
            RectF(halfWidth, 0f, width.toFloat(), height.toFloat())
        } else {
            RectF(0f, 0f, halfWidth, height.toFloat())
        }
        val currentHalf = cropFoldBitmap(currentBitmap, pageRect) ?: return
        val targetHalf = cropFoldBitmap(
            targetBitmap,
            if (forward) {
                RectF(halfWidth, 0f, width.toFloat(), height.toFloat())
            } else {
                RectF(0f, 0f, halfWidth, height.toFloat())
            },
        ) ?: return

        canvas.save()
        canvas.translate(pageRect.left, 0f)
        calcFoldCornerXY(forward, halfWidth.toInt(), height)
        calcFoldPoints(
            forward = forward,
            viewWidth = halfWidth.toInt(),
            viewHeight = height,
            touchX = pageSwipeCurrentX - pageRect.left,
            touchY = pageSwipeCurrentY,
        )
        drawFoldCurrentPageArea(canvas, currentHalf)
        drawFoldNextPageAreaAndShadow(canvas, targetHalf)
        drawFoldCurrentPageShadow(canvas)
        drawFoldCurrentBackArea(canvas)
        canvas.restore()
    }

    private fun drawDualPageSimulationBase(
        canvas: Canvas,
        currentBitmap: Bitmap,
        targetBitmap: Bitmap,
        forward: Boolean,
    ) {
        canvas.drawBitmap(targetBitmap, 0f, 0f, null)
        val halfWidth = width / 2
        val stableSrc = if (forward) {
            Rect(0, 0, halfWidth, height)
        } else {
            Rect(halfWidth, 0, width, height)
        }
        val stableDst = stableSrc
        canvas.drawBitmap(currentBitmap, stableSrc, stableDst, null)
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

    private fun getFoldCurrentBitmap(): Bitmap? {
        if (
            foldCurrentBitmap == null ||
            foldCurrentBitmap?.width != width ||
            foldCurrentBitmap?.height != height ||
            foldBitmapBaseIndex != pageSwipeBaseIndex
        ) {
            foldCurrentBitmap = renderFoldBitmap(foldCurrentBitmap) { canvas ->
                drawSpread(canvas, pageSwipeBaseIndex, 0f)
            }
            foldBitmapBaseIndex = pageSwipeBaseIndex
        }
        return foldCurrentBitmap
    }

    private fun getFoldTargetBitmap(forward: Boolean): Bitmap? {
        if (
            foldTargetBitmap == null ||
            foldTargetBitmap?.width != width ||
            foldTargetBitmap?.height != height ||
            foldBitmapTargetIndex != pageSwipeTargetIndex ||
            foldBitmapChapterDelta != pageSwipeChapterDelta
        ) {
            foldTargetBitmap = renderFoldBitmap(foldTargetBitmap) { canvas ->
                if (pageSwipeChapterDelta != 0) {
                    val previewPages = if (pageSwipeChapterDelta > 0) {
                        nextChapterPreviewPages
                    } else {
                        previousChapterPreviewPages
                    }
                    val previewIndex = if (pageSwipeChapterDelta < 0) {
                        getLastBoundaryPreviewStartIndex(previewPages.size)
                    } else {
                        0
                    }
                    drawPreviewSpread(canvas, previewPages, previewIndex, 0f)
                } else {
                    drawSpread(canvas, pageSwipeTargetIndex, 0f)
                }
            }
            foldBitmapTargetIndex = pageSwipeTargetIndex
            foldBitmapChapterDelta = pageSwipeChapterDelta
        }
        return foldTargetBitmap?.takeIf { forward || pageSwipeTargetIndex >= 0 || pageSwipeChapterDelta != 0 }
    }

    private inline fun renderFoldBitmap(
        reusable: Bitmap?,
        draw: (Canvas) -> Unit,
    ): Bitmap? {
        if (width <= 0 || height <= 0) return null
        val bitmap = reusable
            ?.takeIf { it.width == width && it.height == height && !it.isRecycled }
            ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        foldBitmapCanvas.setBitmap(bitmap)
        foldBitmapCanvas.drawColor(palette.backgroundColor)
        draw(foldBitmapCanvas)
        foldBitmapCanvas.setBitmap(null)
        return bitmap
    }

    private fun cropFoldBitmap(source: Bitmap, rect: RectF): Bitmap? {
        val targetWidth = rect.width().toInt().coerceAtLeast(1)
        val targetHeight = rect.height().toInt().coerceAtLeast(1)
        val reusable = if (source === foldTargetBitmap) foldTargetHalfBitmap else foldCurrentHalfBitmap
        val bitmap = reusable
            ?.takeIf { it.width == targetWidth && it.height == targetHeight && !it.isRecycled }
            ?: Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        if (source === foldTargetBitmap) {
            foldTargetHalfBitmap = bitmap
        } else {
            foldCurrentHalfBitmap = bitmap
        }
        foldBitmapCanvas.setBitmap(bitmap)
        foldBitmapCanvas.drawColor(palette.backgroundColor)
        val src = Rect(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
        val dst = Rect(0, 0, targetWidth, targetHeight)
        foldBitmapCanvas.drawBitmap(source, src, dst, null)
        foldBitmapCanvas.setBitmap(null)
        return bitmap
    }

    private fun calcFoldCornerXY(forward: Boolean, viewWidth: Int = width, viewHeight: Int = height) {
        foldViewWidth = viewWidth
        foldViewHeight = viewHeight
        if (!pageSwipeFoldCornerLocked) {
            lockFoldCorner(forward = forward)
        }
        foldCornerX = pageSwipeFoldCornerX.coerceIn(0f, viewWidth.toFloat())
        foldCornerY = pageSwipeFoldCornerY.coerceIn(0f, viewHeight.toFloat())
        foldIsRtOrLb = (foldCornerX == 0f && foldCornerY == viewHeight.toFloat()) ||
            (foldCornerY == 0f && foldCornerX == viewWidth.toFloat())
    }

    private fun calcFoldCornerXYFromPoint(x: Float, y: Float, viewWidth: Int, viewHeight: Int) {
        foldCornerX = if (x <= viewWidth / 2f) 0f else viewWidth.toFloat()
        foldCornerY = if (y <= viewHeight / 2f) {
            0f
        } else {
            viewHeight.toFloat()
        }
        foldIsRtOrLb = (foldCornerX == 0f && foldCornerY == viewHeight.toFloat()) ||
            (foldCornerY == 0f && foldCornerX == viewWidth.toFloat())
    }

    private fun getFoldLocalDownX(forward: Boolean, viewWidth: Int): Float {
        return if (viewWidth == width) {
            pageSwipeDownX
        } else if (forward) {
            pageSwipeDownX - width / 2f
        } else {
            pageSwipeDownX
        }.coerceIn(1f, viewWidth - 1f)
    }

    private fun lockFoldCorner(forward: Boolean) {
        if (width <= 0 || height <= 0) return
        val cornerPoint = when {
            !forward && pageSwipeDownX < width / 2f -> {
                pageSwipeDownX to height.toFloat()
            }
            !forward -> {
                (width - pageSwipeDownX) to height.toFloat()
            }
            forward && pageSwipeDownX < width / 2f -> {
                (width - pageSwipeDownX) to pageSwipeDownY
            }
            else -> {
                pageSwipeDownX to pageSwipeDownY
            }
        }
        calcFoldCornerXYFromPoint(
            x = cornerPoint.first.coerceIn(1f, width - 1f),
            y = cornerPoint.second.coerceIn(1f, height - 1f),
            viewWidth = width,
            viewHeight = height,
        )
        pageSwipeFoldCornerX = foldCornerX
        pageSwipeFoldCornerY = foldCornerY
        pageSwipeFoldCornerLocked = true
    }

    private fun calcFoldPoints(
        forward: Boolean,
        viewWidth: Int = width,
        viewHeight: Int = height,
        touchX: Float = pageSwipeCurrentX,
        touchY: Float = pageSwipeCurrentY,
    ) {
        foldViewWidth = viewWidth
        foldViewHeight = viewHeight
        foldMaxLength = hypot(viewWidth.toDouble(), viewHeight.toDouble()).toFloat()
        foldTouchX = touchX
        foldTouchY = touchY.coerceIn(1f, viewHeight - 1f)

        var middleX = (foldTouchX + foldCornerX) / 2f
        var middleY = (foldTouchY + foldCornerY) / 2f
        foldControl1.x = middleX - (foldCornerY - middleY) * (foldCornerY - middleY) /
            safeDenominator(foldCornerX - middleX)
        foldControl1.y = foldCornerY
        foldControl2.x = foldCornerX
        foldControl2.y = middleY - (foldCornerX - middleX) * (foldCornerX - middleX) /
            safeDenominator(foldCornerY - middleY)
        foldStart1.x = foldControl1.x - (foldCornerX - foldControl1.x) / 2f
        foldStart1.y = foldCornerY

        if (foldTouchX > 0f && foldTouchX < viewWidth && (foldStart1.x < 0f || foldStart1.x > viewWidth)) {
            if (foldStart1.x < 0f) {
                foldStart1.x = viewWidth - foldStart1.x
            }
            val f1 = abs(foldCornerX - foldTouchX).coerceAtLeast(0.1f)
            val f2 = viewWidth * f1 / safeDenominator(foldStart1.x)
            foldTouchX = abs(foldCornerX - f2).coerceIn(0.1f, viewWidth - 0.1f)
            val f3 = abs(foldCornerX - foldTouchX) * abs(foldCornerY - foldTouchY) / f1
            foldTouchY = abs(foldCornerY - f3).coerceIn(1f, viewHeight - 1f)
            middleX = (foldTouchX + foldCornerX) / 2f
            middleY = (foldTouchY + foldCornerY) / 2f
            foldControl1.x = middleX - (foldCornerY - middleY) * (foldCornerY - middleY) /
                safeDenominator(foldCornerX - middleX)
            foldControl1.y = foldCornerY
            foldControl2.x = foldCornerX
            foldControl2.y = middleY - (foldCornerX - middleX) * (foldCornerX - middleX) /
                safeDenominator(foldCornerY - middleY)
            foldStart1.x = foldControl1.x - (foldCornerX - foldControl1.x) / 2f
        }

        foldStart2.x = foldCornerX
        foldStart2.y = foldControl2.y - (foldCornerY - foldControl2.y) / 2f
        setFoldCross(foldEnd1, foldTouchX, foldTouchY, foldControl1, foldStart1, foldStart2)
        setFoldCross(foldEnd2, foldTouchX, foldTouchY, foldControl2, foldStart1, foldStart2)
        foldVertex1.x = (foldStart1.x + 2f * foldControl1.x + foldEnd1.x) / 4f
        foldVertex1.y = (2f * foldControl1.y + foldStart1.y + foldEnd1.y) / 4f
        foldVertex2.x = (foldStart2.x + 2f * foldControl2.x + foldEnd2.x) / 4f
        foldVertex2.y = (2f * foldControl2.y + foldStart2.y + foldEnd2.y) / 4f
        foldTouchToCornerDistance = hypot(
            (foldTouchX - foldCornerX).toDouble(),
            (foldTouchY - foldCornerY).toDouble(),
        ).toFloat()
    }

    private fun drawFoldCurrentBackArea(canvas: Canvas) {
        val i = ((foldStart1.x + foldControl1.x) / 2f).toInt()
        val f1 = abs(i - foldControl1.x)
        val i1 = ((foldStart2.y + foldControl2.y) / 2f).toInt()
        val f2 = abs(i1 - foldControl2.y)
        val f3 = min(f1, f2)
        foldBackPath.reset()
        foldBackPath.moveTo(foldVertex2.x, foldVertex2.y)
        foldBackPath.lineTo(foldVertex1.x, foldVertex1.y)
        foldBackPath.lineTo(foldEnd1.x, foldEnd1.y)
        foldBackPath.lineTo(foldTouchX, foldTouchY)
        foldBackPath.lineTo(foldEnd2.x, foldEnd2.y)
        foldBackPath.close()
        val folderShadowDrawable: GradientDrawable
        val left: Int
        val right: Int
        if (foldIsRtOrLb) {
            left = (foldStart1.x - 1).toInt()
            right = (foldStart1.x + f3 + 1).toInt()
            folderShadowDrawable = foldFolderShadowDrawableLR
        } else {
            left = (foldStart1.x - f3 - 1).toInt()
            right = (foldStart1.x + 1).toInt()
            folderShadowDrawable = foldFolderShadowDrawableRL
        }

        canvas.save()
        canvas.clipPath(foldPath)
        clipPathIntersect(canvas, foldBackPath)
        canvas.drawColor(palette.backgroundColor)
        canvas.rotate(foldDegrees, foldStart1.x, foldStart1.y)
        folderShadowDrawable.setBounds(left, foldStart1.y.toInt(), right, (foldStart1.y + foldMaxLength).toInt())
        folderShadowDrawable.draw(canvas)
        canvas.restore()
    }

    private fun drawFoldCurrentPageShadow(canvas: Canvas) {
        val shadowOnRight = foldIsRtOrLb
        val degree = if (shadowOnRight) {
            Math.PI / 4 - atan2(foldControl1.y - foldTouchY, foldTouchX - foldControl1.x)
        } else {
            Math.PI / 4 - atan2(foldTouchY - foldControl1.y, foldTouchX - foldControl1.x)
        }
        val d1 = (25f * 1.414f * cos(degree)).toFloat()
        val d2 = (25f * 1.414f * sin(degree)).toFloat()
        val x = foldTouchX + d1
        val y = if (shadowOnRight) foldTouchY + d2 else foldTouchY - d2
        foldBackPath.reset()
        foldBackPath.moveTo(x, y)
        foldBackPath.lineTo(foldTouchX, foldTouchY)
        foldBackPath.lineTo(foldControl1.x, foldControl1.y)
        foldBackPath.lineTo(foldStart1.x, foldStart1.y)
        foldBackPath.close()
        canvas.save()
        clipOutPath(canvas, foldPath)
        clipPathIntersect(canvas, foldBackPath)

        var leftX: Int
        var rightX: Int
        var currentPageShadow: GradientDrawable
        if (shadowOnRight) {
            leftX = foldControl1.x.toInt()
            rightX = (foldControl1.x + 25).toInt()
            currentPageShadow = foldFrontShadowDrawableVLR
        } else {
            leftX = (foldControl1.x - 25).toInt()
            rightX = (foldControl1.x + 1).toInt()
            currentPageShadow = foldFrontShadowDrawableVRL
        }
        var rotateDegrees = Math.toDegrees(
            atan2(foldTouchX - foldControl1.x, foldControl1.y - foldTouchY).toDouble()
        ).toFloat()
        canvas.rotate(rotateDegrees, foldControl1.x, foldControl1.y)
        currentPageShadow.setBounds(leftX, (foldControl1.y - foldMaxLength).toInt(), rightX, foldControl1.y.toInt())
        currentPageShadow.draw(canvas)
        canvas.restore()

        foldBackPath.reset()
        foldBackPath.moveTo(x, y)
        foldBackPath.lineTo(foldTouchX, foldTouchY)
        foldBackPath.lineTo(foldControl2.x, foldControl2.y)
        foldBackPath.lineTo(foldStart2.x, foldStart2.y)
        foldBackPath.close()
        canvas.save()
        clipOutPath(canvas, foldPath)
        canvas.clipPath(foldBackPath)

        if (shadowOnRight) {
            leftX = foldControl2.y.toInt()
            rightX = (foldControl2.y + 25).toInt()
            currentPageShadow = foldFrontShadowDrawableHTB
        } else {
            leftX = (foldControl2.y - 25).toInt()
            rightX = (foldControl2.y + 1).toInt()
            currentPageShadow = foldFrontShadowDrawableHBT
        }
        rotateDegrees = Math.toDegrees(
            atan2(foldControl2.y - foldTouchY, foldControl2.x - foldTouchX).toDouble()
        ).toFloat()
        canvas.rotate(rotateDegrees, foldControl2.x, foldControl2.y)
        val temp = if (foldControl2.y < 0f) {
            (foldControl2.y - foldViewHeight).toDouble()
        } else {
            foldControl2.y.toDouble()
        }
        val hmg = hypot(foldControl2.x.toDouble(), temp)
        if (hmg > foldMaxLength) {
            currentPageShadow.setBounds(
                (foldControl2.x - 25 - hmg).toInt(),
                leftX,
                (foldControl2.x + foldMaxLength - hmg).toInt(),
                rightX,
            )
        } else {
            currentPageShadow.setBounds(
                (foldControl2.x - foldMaxLength).toInt(),
                leftX,
                foldControl2.x.toInt(),
                rightX,
            )
        }
        currentPageShadow.draw(canvas)
        canvas.restore()
    }

    private fun drawFoldNextPageAreaAndShadow(canvas: Canvas, bitmap: Bitmap) {
        foldBackPath.reset()
        foldBackPath.moveTo(foldStart1.x, foldStart1.y)
        foldBackPath.lineTo(foldVertex1.x, foldVertex1.y)
        foldBackPath.lineTo(foldVertex2.x, foldVertex2.y)
        foldBackPath.lineTo(foldStart2.x, foldStart2.y)
        foldBackPath.lineTo(foldCornerX, foldCornerY)
        foldBackPath.close()
        foldDegrees = Math.toDegrees(
            atan2((foldControl1.x - foldCornerX).toDouble(), foldControl2.y - foldCornerY.toDouble())
        ).toFloat()
        val leftX: Int
        val rightX: Int
        val backShadowDrawable: GradientDrawable
        if (foldIsRtOrLb) {
            leftX = foldStart1.x.toInt()
            rightX = (foldStart1.x + foldTouchToCornerDistance / 4f).toInt()
            backShadowDrawable = foldBackShadowDrawableLR
        } else {
            leftX = (foldStart1.x - foldTouchToCornerDistance / 4f).toInt()
            rightX = foldStart1.x.toInt()
            backShadowDrawable = foldBackShadowDrawableRL
        }
        canvas.save()
        canvas.clipPath(foldPath)
        clipPathIntersect(canvas, foldBackPath)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        canvas.rotate(foldDegrees, foldStart1.x, foldStart1.y)
        backShadowDrawable.setBounds(leftX, foldStart1.y.toInt(), rightX, (foldMaxLength + foldStart1.y).toInt())
        backShadowDrawable.draw(canvas)
        canvas.restore()
    }

    private fun drawFoldCurrentPageArea(canvas: Canvas, bitmap: Bitmap) {
        foldPath.reset()
        foldPath.moveTo(foldStart1.x, foldStart1.y)
        foldPath.quadTo(foldControl1.x, foldControl1.y, foldEnd1.x, foldEnd1.y)
        foldPath.lineTo(foldTouchX, foldTouchY)
        foldPath.lineTo(foldEnd2.x, foldEnd2.y)
        foldPath.quadTo(foldControl2.x, foldControl2.y, foldStart2.x, foldStart2.y)
        foldPath.lineTo(foldCornerX, foldCornerY)
        foldPath.close()

        canvas.save()
        clipOutPath(canvas, foldPath)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        canvas.restore()
    }

    @Suppress("DEPRECATION")
    private fun clipOutPath(canvas: Canvas, path: Path) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            canvas.clipOutPath(path)
        } else {
            canvas.clipPath(path, Region.Op.XOR)
        }
    }

    @Suppress("DEPRECATION")
    private fun clipPathIntersect(canvas: Canvas, path: Path) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            canvas.clipPath(path)
        } else {
            canvas.clipPath(path, Region.Op.INTERSECT)
        }
    }

    private fun setFoldCross(
        out: PointF,
        p1x: Float,
        p1y: Float,
        p2: PointF,
        p3: PointF,
        p4: PointF,
    ) {
        val a1 = (p2.y - p1y) / safeDenominator(p2.x - p1x)
        val b1 = (p1x * p2.y - p2.x * p1y) / safeDenominator(p1x - p2.x)
        val a2 = (p4.y - p3.y) / safeDenominator(p4.x - p3.x)
        val b2 = (p3.x * p4.y - p4.x * p3.y) / safeDenominator(p3.x - p4.x)
        out.x = (b2 - b1) / safeDenominator(a1 - a2)
        out.y = a1 * out.x + b1
    }

    private fun safeDenominator(value: Float): Float {
        if (abs(value) >= 0.1f) return value
        return if (value < 0f) -0.1f else 0.1f
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
        val step = getPageTurnStep()
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
        val step = getPageTurnStep()
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
        foldCurrentBitmap?.recycle()
        foldTargetBitmap?.recycle()
        foldCurrentHalfBitmap?.recycle()
        foldTargetHalfBitmap?.recycle()
        foldCurrentBitmap = null
        foldTargetBitmap = null
        foldCurrentHalfBitmap = null
        foldTargetHalfBitmap = null
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
                pageSwipeDownX = event.x
                pageSwipeDownY = event.y
                pageSwipeStartX = event.x
                pageSwipeStartY = event.y
                pageSwipeLastX = event.x
                pageSwipeCurrentX = event.x
                pageSwipeCurrentY = event.y
                isPageDragging = false
                resetFoldBitmaps()
                resetPageSwipeState(keepOffset = false)
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.x - pageSwipeDownX
                val deltaY = event.y - pageSwipeDownY
                if (!isPageDragging && abs(deltaX) > touchSlop && abs(deltaX) > abs(deltaY)) {
                    val targetIndex = resolveSwipeTargetIndex(deltaX)
                    val chapterDelta = if (targetIndex < 0) resolveSwipeChapterDelta(deltaX) else 0
                    if (targetIndex >= 0 || chapterDelta != 0) {
                        isPageDragging = true
                        pageSwipeBaseIndex = currentPageIndex
                        pageSwipeTargetIndex = if (targetIndex >= 0) targetIndex else currentPageIndex
                        pageSwipeChapterDelta = chapterDelta
                        pageSwipeDirection = deltaX.sign.toInt()
                        pageSwipeLastX = event.x
                        pageSwipeCurrentX = pageSwipeDownX
                        pageSwipeCurrentY = event.y
                        pageSwipeOffsetX = event.x - pageSwipeDownX
                        lockFoldCorner(forward = pageSwipeDirection < 0)
                        applyLegadoTouchYPolicy(pageSwipeDirection)
                        parent?.requestDisallowInterceptTouchEvent(true)
                        invalidate()
                        return true
                    } else {
                        return false
                    }
                }
                if (isPageDragging) {
                    pageSwipeOffsetX = coerceSwipeOffsetForLockedDirection(deltaX)
                    pageSwipeLastX = event.x
                    pageSwipeCurrentX = getSimulationTouchX()
                    pageSwipeCurrentY = event.y
                    applyLegadoTouchYPolicy(pageSwipeDirection)
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_UP -> {
                if (isPageDragging) {
                    val shouldCommit = abs(pageSwipeOffsetX) >= getPageTurnDistance() * pageTurnThresholdFraction
                    animatePageSettle(
                        targetIndex = pageSwipeTargetIndex,
                        commit = shouldCommit,
                        direction = pageSwipeDirection,
                    )
                    isPageDragging = false
                    return true
                }
            }
        }
        return false
    }

    private fun applyLegadoTouchYPolicy(direction: Int) {
        if (pageSwipeStartY > height / 3f && pageSwipeStartY < height * 2f / 3f || direction > 0) {
            pageSwipeCurrentY = height.toFloat()
        }
        if (pageSwipeStartY > height / 3f && pageSwipeStartY < height / 2f && direction < 0) {
            pageSwipeCurrentY = 1f
        }
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
        if (pageSwipeDirection != resolvedDirection) {
            pageSwipeDirection = resolvedDirection
        }
        if (settings.pageTurnAnimation == NovelPageTurnAnimation.SIMULATION && !isPageDragging) {
            prepareSimulationAutoPageTurn(resolvedDirection)
        }

        pageSwipeAnimator?.cancel()
        val settleFromX = getSimulationTouchX()
        val settleFromY = pageSwipeCurrentY
        val settleFromOffset = pageSwipeOffsetX
        val settleToX = if (commit) {
            if (resolvedDirection < 0) -getPageTurnDistance() else getPageTurnDistance()
        } else {
            if (resolvedDirection < 0) getPageTurnDistance() else -getPageTurnDistance()
        }
        val settleToY = if (!commit) {
            pageSwipeCurrentY
        } else if (pageSwipeFoldCornerLocked && pageSwipeFoldCornerY == 0f) {
            1f
        } else {
            height.toFloat()
        }
        val settleToOffset = if (commit) {
            if (resolvedDirection < 0) -getPageTurnDistance() else getPageTurnDistance()
        } else {
            0f
        }
        pageSwipeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            var cancelled = false
            duration = pageTurnSettleDuration(
                fromOffset = settleFromOffset,
                toOffset = settleToOffset,
                commit = commit,
            )
            interpolator = if (settings.pageTurnAnimation == NovelPageTurnAnimation.SIMULATION) {
                simulationPageTurnInterpolator
            } else {
                pageTurnInterpolator
            }
            addUpdateListener { animator ->
                val fraction = animator.animatedFraction
                pageSwipeCurrentX = settleFromX + (settleToX - settleFromX) * fraction
                pageSwipeCurrentY = settleFromY + (settleToY - settleFromY) * fraction
                pageSwipeOffsetX = settleFromOffset + (settleToOffset - settleFromOffset) * fraction
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

    private fun getSimulationTouchX(): Float {
        val anchorX = if (isSimulationAutoPageTurn) pageSwipeStartX else pageSwipeDownX
        return (anchorX + pageSwipeOffsetX).coerceIn(0.1f, width.toFloat() - 0.1f)
    }

    private fun prepareSimulationAutoPageTurn(direction: Int) {
        if (width <= 0 || height <= 0) return
        pageSwipeStartX = if (direction < 0) {
            width * 0.92f
        } else {
            width * 0.08f
        }
        pageSwipeStartY = height * 0.9f
        pageSwipeLastX = pageSwipeStartX
        pageSwipeCurrentX = pageSwipeStartX
        pageSwipeCurrentY = pageSwipeStartY
        pageSwipeOffsetX = 0f
        pageSwipeDownX = pageSwipeStartX
        pageSwipeDownY = pageSwipeStartY
        isSimulationAutoPageTurn = true
        lockFoldCorner(forward = direction < 0)
        resetFoldBitmaps()
    }

    private fun coerceSwipeOffsetForLockedDirection(deltaX: Float): Float {
        val distance = getPageTurnDistance()
        return if (pageSwipeDirection < 0) {
            deltaX.coerceIn(-distance, 0f)
        } else {
            deltaX.coerceIn(0f, distance)
        }
    }

    private fun pageTurnSettleDuration(fromOffset: Float, toOffset: Float, commit: Boolean): Long {
        if (settings.pageTurnAnimation != NovelPageTurnAnimation.SIMULATION) {
            return 180L
        }
        val distanceRatio = (abs(toOffset - fromOffset) / getPageTurnDistance().coerceAtLeast(1f)).coerceIn(0f, 1f)
        val baseDuration = if (isSimulationAutoPageTurn && commit) {
            450L
        } else if (commit) {
            420L
        } else {
            260L
        }
        return (baseDuration * distanceRatio).toLong().coerceIn(120L, baseDuration)
    }

    private fun getPageTurnDistance(): Float {
        return width.toFloat().coerceAtLeast(1f)
    }

    private fun resolveSwipeTargetIndex(deltaX: Float): Int {
        val step = getPageTurnStep()
        return when {
            deltaX < 0f && currentPageIndex + step < pages.size -> currentPageIndex + step
            deltaX > 0f && currentPageIndex - step >= 0 -> currentPageIndex - step
            else -> -1
        }
    }

    private fun resolveSwipeChapterDelta(deltaX: Float): Int {
        val step = getPageTurnStep()
        return when {
            deltaX < 0f && currentPageIndex + step >= pages.size -> 1
            deltaX > 0f && currentPageIndex - step < 0 -> -1
            else -> 0
        }
    }

    private fun getPageTurnStep(): Int {
        return if (isDualPage) 2 else 1
    }

    private fun resetPageSwipeState(keepOffset: Boolean = false) {
        isPageDragging = false
        isAwaitingChapterTransitionContent = false
        pageSwipeBaseIndex = -1
        pageSwipeTargetIndex = -1
        pageSwipeChapterDelta = 0
        pageSwipeDirection = 0
        pageSwipeFoldCornerX = 0f
        pageSwipeFoldCornerY = 0f
        pageSwipeFoldCornerLocked = false
        isSimulationAutoPageTurn = false
        resetFoldBitmaps()
        if (!keepOffset) {
            pageSwipeOffsetX = 0f
        }
    }

    private fun resetFoldBitmaps() {
        foldBitmapBaseIndex = -1
        foldBitmapTargetIndex = -1
        foldBitmapChapterDelta = 0
        foldCurrentHalfBitmap?.eraseColor(Color.TRANSPARENT)
        foldTargetHalfBitmap?.eraseColor(Color.TRANSPARENT)
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
