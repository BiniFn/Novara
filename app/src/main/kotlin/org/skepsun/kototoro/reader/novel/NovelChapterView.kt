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
import android.view.View
import androidx.collection.LruCache
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

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        val typedValue = android.util.TypedValue()
        context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
        color = if (typedValue.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT &&
            typedValue.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT) {
            typedValue.data
        } else {
            androidx.core.content.ContextCompat.getColor(context, typedValue.resourceId)
        }
        textSize = resources.resolveSp(18f)
    }

    private var settings: NovelReaderSettings = NovelReaderSettings.load(context)
    var chapterContent: String = ""
        private set
    
    private var displayLayout: StaticLayout? = null
    var paginatedTotalLength: Int = 0
        private set
    private var imageSpans: List<ChapterImageSpan> = emptyList()

    private var epubFile: File? = null
    private var chapterPath: String? = null
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    var imageHeadersProvider: ((String) -> Map<String, String>?)? = null
    
    @Inject
    lateinit var imageLoader: ImageLoader
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val imageCache = LruCache<String, Bitmap>(20)
    private val loadingImages = mutableSetOf<String>()
    private val failedImages = mutableSetOf<String>()

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

    fun updateSettings(newSettings: NovelReaderSettings) {
        settings = newSettings
        textPaint.textSize = resources.resolveSp(settings.fontSizeSp)
        displayLayout = null
        requestLayout()
        invalidate()
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
        var (processedText, imagePaths) = parseImages(chapterContent)
        val hasImages = imagePaths.isNotEmpty()
        val tempImageSpans = mutableListOf<ChapterImageSpan>()
        
        if (hasImages) {
            val lineHeight = (textPaint.fontMetrics.descent - textPaint.fontMetrics.ascent) * settings.lineSpacing
            val imageWidth = pageWidth.toFloat()
            val imageHeight = imageWidth * 0.75f
            val paraSpacingPx = settings.paragraphSpacing * resources.displayMetrics.density
            val extraSpacerLines = if (paraSpacingPx > 0) max(1, kotlin.math.ceil(paraSpacingPx / lineHeight).toInt()) else 0
            val spacerLines = (imageHeight / lineHeight).toInt() + (extraSpacerLines * 2).coerceAtLeast(2)
            val spacer = "\n".repeat(spacerLines)
            
            var newText = processedText
            for (i in imagePaths.indices) {
                val placeholder = "[IMAGE_PLACEHOLDER_$i]"
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
                if (imageIndex < imagePaths.size) {
                    val imagePath = imagePaths[imageIndex]
                    val before = processedText.substring(lastIndex, match.range.first)
                    displayBuilder.append(before)
                    lastIndex = match.range.last + 1

                    val tempLayout = createStaticLayout(displayBuilder.toString(), pageWidth)
                    val yPosition = if (tempLayout.lineCount > 0) {
                        tempLayout.getLineBottom(tempLayout.lineCount - 1).toFloat()
                    } else {
                        0f
                    }
                    
                    val imageWidth = pageWidth.toFloat()
                    val imageHeight = imageWidth * 0.75f 
                    tempImageSpans.add(ChapterImageSpan(imagePath, yPosition, imageWidth, imageHeight))
                }
            }
            if (lastIndex < processedText.length) {
                displayBuilder.append(processedText.substring(lastIndex))
            }
            processedText = displayBuilder.toString()
        }

        imageSpans = tempImageSpans
        displayLayout = createStaticLayout(processedText, pageWidth)
    }

    private fun createStaticLayout(text: String, width: Int): StaticLayout {
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

    private fun parseImages(text: String): Pair<String, List<String>> {
        val imagePaths = mutableListOf<String>()
        val imagePattern = Regex("""📷\s*\[图片:\s*([^\]]+)\]""")
        val htmlImagePattern = Regex("""<img[^>]+src=['"]([^'"]+)['"][^>]*>""", RegexOption.IGNORE_CASE)

        var processedText = text
        imagePattern.findAll(text).forEach { match ->
            val imagePath = match.groupValues[1].trim()
            imagePaths.add(imagePath)
            processedText = processedText.replaceFirst(match.value, "[IMAGE_PLACEHOLDER_${imagePaths.size - 1}]")
        }

        processedText = processedText.replace(htmlImagePattern) { matchResult ->
            val src = matchResult.groupValues.getOrNull(1)?.trim().orEmpty()
            if (src.isNotEmpty()) {
                imagePaths.add(src)
                "[IMAGE_PLACEHOLDER_${imagePaths.size - 1}]"
            } else ""
        }

        processedText = processedText
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p>"), "\n")
            .replace(Regex("(?i)<p[^>]*>"), "")
            .replace(Regex("<[^>]+>"), "")
        
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
        val extraLines = if (spacingPx > 0) max(1, kotlin.math.ceil(spacingPx / lineHeight).toInt()) else 0
        if (extraLines == 0) return text
        val spacer = "\n".repeat(extraLines)
        return text.split(Regex("\\n+")).joinToString(separator = "\n$spacer")
    }

    private fun applyParagraphIndent(text: String): String {
        if (!settings.enableParagraphIndent) return text
        val indent = "　　"
        val sb = StringBuilder(text.length + 16)
        text.split("\n").forEachIndexed { idx, line ->
            if (idx > 0) sb.append('\n')
            if (line.isBlank() || line.startsWith(indent)) sb.append(line)
            else sb.append(indent).append(line.trimStart())
        }
        return sb.toString()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val layout = displayLayout ?: return

        canvas.save()
        val x = paddingLeft + settings.marginHorizontal.toFloat()
        val y = paddingTop + settings.marginVertical.toFloat()
        canvas.translate(x, y)
        
        layout.draw(canvas)

        for (imageSpan in imageSpans) {
            val bitmap = loadImage(imageSpan.imagePath)
            if (bitmap != null) {
                val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
                val rawTargetHeight = imageSpan.width * bitmap.height / bitmap.width
                val scale = if (rawTargetHeight > imageSpan.height) imageSpan.height / rawTargetHeight else 1f
                val targetWidth = imageSpan.width * scale
                val targetHeight = rawTargetHeight * scale
                val leftPos = (imageSpan.width - targetWidth) / 2f
                val topPos = imageSpan.yPosition + (imageSpan.height - targetHeight) / 2f
                val dstRect = RectF(leftPos, topPos, leftPos + targetWidth, topPos + targetHeight)
                canvas.drawBitmap(bitmap, srcRect, dstRect, imagePaint)
            } else {
                val placeholderPaint = Paint().apply { color = 0xFFCCCCCC.toInt(); style = Paint.Style.FILL }
                val placeholderRect = RectF(0f, imageSpan.yPosition, imageSpan.width, imageSpan.yPosition + imageSpan.height)
                canvas.drawRect(placeholderRect, placeholderPaint)
                val errorPaint = Paint().apply { color = 0xFF666666.toInt(); textSize = 14f * resources.displayMetrics.density; textAlign = Paint.Align.CENTER }
                canvas.drawText("图片加载失败/Loading", imageSpan.width / 2, imageSpan.yPosition + imageSpan.height / 2, errorPaint)
            }
        }
        canvas.restore()
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
                        invalidate()
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
