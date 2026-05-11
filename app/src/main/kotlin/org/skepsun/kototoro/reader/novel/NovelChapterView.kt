package org.skepsun.kototoro.reader.novel

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
import androidx.collection.LruCache
import androidx.core.view.GestureDetectorCompat
import coil3.ImageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.util.ext.resolveSp
import org.skepsun.kototoro.local.epub.EpubImageExtractor
import java.io.File
import javax.inject.Inject
import kotlin.math.max

/**
 * 核心文本渲染视图，用于连续滚动模式中的 RecyclerView 列表项。
 * 纯静态绘制，无内部滚动机制。
 */
@AndroidEntryPoint
class NovelChapterView @JvmOverloads constructor(
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
    
    private var displayLayout: StaticLayout? = null
    var processedText: String = ""
        private set
    var paginatedTotalLength: Int = 0
        private set
    
    private var highlightRange: IntRange? = null
    private val highlightPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.highlightColor
            style = Paint.Style.FILL
        }
    }
    private var imageSpans: List<ChapterImageSpan> = emptyList()

    private var epubFile: File? = null
    private var chapterPath: String? = null
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    var imageHeadersProvider: ((String) -> Map<String, String>?)? = null
    var onImageClickListener: ((NovelInlineImageRequest) -> Unit)? = null
    
    @Inject
    lateinit var imageLoader: ImageLoader
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val imageCache = LruCache<String, Bitmap>(20)
    private val loadingImages = mutableSetOf<String>()
    private val failedImages = mutableSetOf<String>()
    private val gestureDetector: GestureDetectorCompat

    init {
        isClickable = true
        isFocusable = true
        gestureDetector = GestureDetectorCompat(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                findInlineImageAt(e.x, e.y)?.let { image ->
                    onImageClickListener?.invoke(image)
                    return true
                }
                return false
            }
        })
    }

    fun setContent(
        content: String,
        epub: File?,
        path: String?
    ) {
        this.chapterContent = content
        this.epubFile = epub
        this.chapterPath = path
        this.displayLayout = null
        loadingImages.clear()
        failedImages.clear()
        requestLayout()
        invalidate()
    }

    /**
     * 更新翻译结果，触发重新排版和渲染。
     * 传入 null 则清除翻译，恢复显示原文。
     */
    fun setTranslation(translation: NovelChapterTranslation?) {
        if (activeTranslation == translation) return
        activeTranslation = translation
        displayLayout = null
        requestLayout()
        invalidate()
    }

    fun updateSettings(newSettings: NovelReaderSettings) {
        settings = newSettings
        textPaint.textSize = resources.resolveSp(settings.fontSizeSp)
        updatePalette()
        displayLayout = null
        requestLayout()
        invalidate()
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

    fun setHighlightRange(range: IntRange?) {
        if (highlightRange != range) {
            highlightRange = range
            invalidate()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = gestureDetector.onTouchEvent(event)
        return handled || super.onTouchEvent(event)
    }

    fun getLineTopForOffset(charOffset: Int): Float {
        val layout = displayLayout ?: return 0f
        return try {
            val line = layout.getLineForOffset(charOffset)
            layout.getLineTop(line).toFloat() + paddingTop + settings.marginVertical
        } catch (e: Exception) {
            0f
        }
    }

    fun getOffsetForVertical(y: Float): Int {
        val layout = displayLayout ?: return 0
        return try {
            val adjustedY = y - paddingTop - settings.marginVertical
            val clampedY = adjustedY.coerceIn(0f, layout.height.toFloat())
            val line = layout.getLineForVertical(clampedY.toInt())
            layout.getOffsetForHorizontal(line, 0f)
        } catch (e: Exception) {
            0
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        
        if (widthSize <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        val availableWidth = widthSize - paddingLeft - paddingRight - (settings.marginHorizontal * 2)
        
        if (displayLayout == null && chapterContent.isNotEmpty() && availableWidth > 0) {
            buildLayout(availableWidth)
        }

        val contentHeight = displayLayout?.height ?: 0
        // 对于 continuous 模式，我们只需基于文本高度计算测量高度
        val desiredHeight = paddingTop + paddingBottom + (settings.marginVertical * 2) + contentHeight
        
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        
        val finalHeight = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> kotlin.math.min(desiredHeight, heightSize)
            else -> desiredHeight
        }

        setMeasuredDimension(widthSize, finalHeight)
    }

    private fun buildLayout(pageWidth: Int) {
        // 如果有激活的翻译，先对内容进行段落级替换/拼接
        val contentForLayout = applyTranslationToContent(chapterContent, activeTranslation)
        val parsedImages = parseNovelImages(contentForLayout)
        var processedText = prepareContentText(parsedImages.text)
        val blockImagePaths = parsedImages.blockImagePaths
        val inlineImagePaths = parsedImages.inlineImagePaths
        val hasImages = blockImagePaths.isNotEmpty()
        val tempImageSpans = mutableListOf<ChapterImageSpan>()
        
        if (hasImages) {
            val lineHeight = (textPaint.fontMetrics.descent - textPaint.fontMetrics.ascent) * settings.lineSpacing
            val maxScreenHeight = resources.displayMetrics.heightPixels * 1.5f
            val paraSpacingPx = settings.paragraphSpacing * resources.displayMetrics.density
            val extraSpacerLines = if (paraSpacingPx > 0) max(1, kotlin.math.ceil(paraSpacingPx / lineHeight).toInt()) else 0
            
            var newText = processedText
            for (i in blockImagePaths.indices) {
                val placeholder = "[IMAGE_PLACEHOLDER_$i]"
                val imagePath = blockImagePaths[i]
                val imageHeight = getReservedNovelImageHeight(
                    imagePath = imagePath,
                    maxWidth = pageWidth.toFloat(),
                    maxHeight = maxScreenHeight,
                )
                
                val spacerLines = (imageHeight / lineHeight).toInt() + (extraSpacerLines * 2).coerceAtLeast(2)
                val spacer = "\n".repeat(spacerLines)
                newText = newText.replace(placeholder, "\n$placeholder\n$spacer\n")
            }
            processedText = newText
        }

        paginatedTotalLength = processedText.length

        if (hasImages) {
            val placeholderPattern = Regex("""\[IMAGE_PLACEHOLDER_(\d+)\]""")
            val displayBuilder = StringBuilder()
            var lastIndex = 0
            
            placeholderPattern.findAll(processedText).forEach { match ->
                val imageIndex = match.groupValues[1].toInt()
                if (imageIndex < blockImagePaths.size) {
                    val imagePath = blockImagePaths[imageIndex]
                    val before = processedText.substring(lastIndex, match.range.first)
                    displayBuilder.append(before)
                    lastIndex = match.range.last + 1

                    val tempLayout = createStaticLayout(
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
                    val maxScreenHeight = resources.displayMetrics.heightPixels * 1.5f
                    val imageHeight = getReservedNovelImageHeight(
                        imagePath = imagePath,
                        maxWidth = imageWidth,
                        maxHeight = maxScreenHeight,
                    )
                    
                    tempImageSpans.add(ChapterImageSpan(imagePath, yPosition, imageWidth, imageHeight))
                }
            }
            if (lastIndex < processedText.length) {
                displayBuilder.append(processedText.substring(lastIndex))
            }
            processedText = displayBuilder.toString()
        }

        imageSpans = tempImageSpans
        this.processedText = processedText
        val layoutText = applyInlineImageSpans(
            applyBilingualSpannable(processedText, activeTranslation),
            inlineImagePaths,
        ) { path ->
            loadImage(path)
        }
        displayLayout = createStaticLayout(layoutText, pageWidth)
    }

    private fun prepareContentText(text: String): String {
        return NovelTypography.prepareContentText(text, settings, textPaint)
    }

    /**
     * 根据翻译结果，将章节内容转换为展示用文本。
     *
     * - TRANSLATION_ONLY：每个 TEXT 段落替换为译文（IMAGE 段落原样保留）
     * - BILINGUAL：每个 TEXT 段落变为 [原文(灰色小字)]\n[译文(正常样式)]
     * - translation 为 null：直接返回原始内容
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
     * 仅用于双语模式：在已拼接好的 processedText 上查找原文段落，
     * 将原文部分设置为灰色小字 Span，译文部分保持默认样式。
     * 其他模式直接返回原字符串。
     */
    private fun applyBilingualSpannable(
        processedText: String,
        translation: NovelChapterTranslation?,
    ): CharSequence {
        return NovelTypography.applyBilingualSpannable(
            processedText = processedText,
            translation = translation,
            secondaryColor = palette.secondaryTextColor,
        )
    }

    private fun createStaticLayout(text: CharSequence, width: Int): StaticLayout {
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
            @Suppress("DEPRECATION")
            StaticLayout(
                text, textPaint, width, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0f, false
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val layout = displayLayout ?: return
        canvas.drawColor(palette.backgroundColor)

        canvas.save()
        val x = paddingLeft + settings.marginHorizontal.toFloat()
        val y = paddingTop + settings.marginVertical.toFloat()
        canvas.translate(x, y)
        
        highlightRange?.let { range ->
            val intersectStart = kotlin.math.max(0, range.first)
            val intersectEnd = kotlin.math.min(processedText.length, range.last + 1)
            if (intersectStart < intersectEnd) {
                val path = android.graphics.Path()
                layout.getSelectionPath(intersectStart, intersectEnd, path)
                canvas.drawPath(path, highlightPaint)
            }
        }
        
        layout.draw(canvas)

        for (imageSpan in imageSpans) {
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
                val placeholderPaint = Paint().apply { color = palette.placeholderColor; style = Paint.Style.FILL }
                val placeholderRect = RectF(0f, imageSpan.yPosition, imageSpan.width, imageSpan.yPosition + imageSpan.height)
                canvas.drawRect(placeholderRect, placeholderPaint)
                val errorPaint = Paint().apply { color = palette.placeholderTextColor; textSize = 14f * resources.displayMetrics.density; textAlign = Paint.Align.CENTER }
                canvas.drawText("图片加载失败/Loading", imageSpan.width / 2, imageSpan.yPosition + imageSpan.height / 2, errorPaint)
            }
        }
        canvas.restore()
    }

    private fun findInlineImageAt(x: Float, y: Float): NovelInlineImageRequest? {
        val localX = x - paddingLeft - settings.marginHorizontal
        val localY = y - paddingTop - settings.marginVertical
        if (localX < 0f || localY < 0f) {
            return null
        }
        val inlineImagePath = displayLayout?.let { layout ->
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
        val image = imageSpans.firstOrNull { span ->
            getNovelImageDisplayRect(
                imagePath = span.imagePath,
                reservedWidth = span.width,
                reservedHeight = span.height,
                yPosition = span.yPosition,
            ).contains(localX, localY)
        } ?: return null
        return NovelInlineImageRequest(
            imagePath = image.imagePath,
            epubFilePath = epubFile?.absolutePath,
            chapterPath = chapterPath,
            headers = imageHeadersProvider?.invoke(image.imagePath).orEmpty(),
        )
    }

    private fun loadImage(imagePath: String): Bitmap? {
        if (!::imageLoader.isInitialized) return null
        val cacheKey = "${chapterPath ?: "unknown"}_$imagePath"
        imageCache.get(cacheKey)?.let { return it }

        if (!loadingImages.contains(cacheKey) && !failedImages.contains(cacheKey)) {
            loadingImages.add(cacheKey)
            scope.launch {
                try {
                    val bitmap = when {
                        imagePath.startsWith("http", ignoreCase = true) -> loadCoilImage(imagePath)
                        epubFile != null -> loadEpubImageViaCoil(imagePath)
                        else -> loadCoilImage(imagePath)
                    }
                    if (bitmap != null) {
                        imageCache.put(cacheKey, bitmap)
                        loadingImages.remove(cacheKey)
                        val metrics = NovelImageMetrics(bitmap.width, bitmap.height)
                        val previousMetrics = NovelImageMetricsCache.get(imagePath)
                        NovelImageMetricsCache.put(imagePath, metrics)
                        if (previousMetrics != metrics) {
                            displayLayout = null
                            requestLayout()
                        } else {
                            invalidate()
                        }
                    } else {
                        loadingImages.remove(cacheKey)
                        failedImages.add(cacheKey)
                    }
                } catch (e: Exception) {
                    loadingImages.remove(cacheKey)
                    failedImages.add(cacheKey)
                }
            }
        }
        return null
    }

    private suspend fun loadCoilImage(url: String): Bitmap? = withContext(Dispatchers.IO) {
        val requestBuilder = ImageRequest.Builder(context).data(url)
        imageHeadersProvider?.invoke(url)?.takeIf { it.isNotEmpty() }?.let { extra ->
            val headers = NetworkHeaders.Builder().apply { extra.forEach { (k, v) -> add(k, v) } }.build()
            requestBuilder.httpHeaders(headers)
        }
        when (val result = imageLoader.execute(requestBuilder.build())) {
            is SuccessResult -> result.image.toBitmap(width = result.image.width, height = result.image.height)
            is ErrorResult -> throw result.throwable
        }
    }

    private suspend fun loadEpubImageViaCoil(imagePath: String): Bitmap? = withContext(Dispatchers.IO) {
        val file = epubFile ?: return@withContext null
        if (!file.exists()) return@withContext null
        val extractor = EpubImageExtractor(file)
        val resolvedPath = if (chapterPath != null) extractor.resolveImagePath(chapterPath!!, imagePath) else imagePath
        val bytes = extractor.extractImage(resolvedPath) ?: return@withContext null
        val request = ImageRequest.Builder(context).data(bytes).build()
        when (val result = imageLoader.execute(request)) {
            is SuccessResult -> result.image.toBitmap(width = result.image.width, height = result.image.height)
            is ErrorResult -> throw result.throwable
        }
    }

}

/** 供图片渲染使用的简单数据类，移出 NovelReaderView 以复用 */
data class ChapterImageSpan(
    val imagePath: String,
    val yPosition: Float,
    val width: Float,
    val height: Float
)
