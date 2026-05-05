package org.skepsun.kototoro.core.ui.image

import android.graphics.Bitmap
import coil3.request.ImageRequest
import coil3.request.allowRgb565
import coil3.request.bitmapConfig
import coil3.request.transformations
import coil3.size.Size
import coil3.transform.Transformation
import kotlin.math.roundToInt

fun ImageRequest.Builder.panoramaBlur(blurPercent: Int): ImageRequest.Builder {
    return if (blurPercent > 0) {
        if (blurPercent < 30) {
            allowRgb565(false)
                .bitmapConfig(Bitmap.Config.ARGB_8888)
        }
        transformations(PanoramaBlurTransformation(blurPercent))
    } else {
        this
    }
}

class PanoramaBlurTransformation(
    blurPercent: Int,
) : Transformation() {

    private val radius = ((blurPercent.coerceIn(0, 100) / 100f) * MAX_RADIUS_PX)
        .roundToInt()
        .coerceIn(1, MAX_RADIUS_PX)

    override val cacheKey: String = "${javaClass.name}-$radius"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        if (input.width <= 1 || input.height <= 1) {
            return input
        }
        val source = if (input.config == Bitmap.Config.ARGB_8888) {
            input
        } else {
            input.copy(Bitmap.Config.ARGB_8888, false)
        }
        val blurred = source.copy(Bitmap.Config.ARGB_8888, true)
        stackBlur(blurred, radius)
        return blurred
    }

    private fun stackBlur(bitmap: Bitmap, radius: Int) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val tmp = IntArray(pixels.size)
        blurHorizontal(pixels, tmp, width, height, radius)
        blurVertical(tmp, pixels, width, height, radius)

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    private fun blurHorizontal(input: IntArray, output: IntArray, width: Int, height: Int, radius: Int) {
        val windowSize = radius * 2 + 1
        for (y in 0 until height) {
            val row = y * width
            var alpha = 0
            var red = 0
            var green = 0
            var blue = 0

            for (i in -radius..radius) {
                val pixel = input[row + i.coerceIn(0, width - 1)]
                alpha += pixel ushr 24
                red += (pixel shr 16) and 0xFF
                green += (pixel shr 8) and 0xFF
                blue += pixel and 0xFF
            }

            for (x in 0 until width) {
                output[row + x] = ((alpha / windowSize) shl 24) or
                    ((red / windowSize) shl 16) or
                    ((green / windowSize) shl 8) or
                    (blue / windowSize)

                val removeX = (x - radius).coerceIn(0, width - 1)
                val addX = (x + radius + 1).coerceIn(0, width - 1)
                val removePixel = input[row + removeX]
                val addPixel = input[row + addX]
                alpha += (addPixel ushr 24) - (removePixel ushr 24)
                red += ((addPixel shr 16) and 0xFF) - ((removePixel shr 16) and 0xFF)
                green += ((addPixel shr 8) and 0xFF) - ((removePixel shr 8) and 0xFF)
                blue += (addPixel and 0xFF) - (removePixel and 0xFF)
            }
        }
    }

    private fun blurVertical(input: IntArray, output: IntArray, width: Int, height: Int, radius: Int) {
        val windowSize = radius * 2 + 1
        for (x in 0 until width) {
            var alpha = 0
            var red = 0
            var green = 0
            var blue = 0

            for (i in -radius..radius) {
                val pixel = input[i.coerceIn(0, height - 1) * width + x]
                alpha += pixel ushr 24
                red += (pixel shr 16) and 0xFF
                green += (pixel shr 8) and 0xFF
                blue += pixel and 0xFF
            }

            for (y in 0 until height) {
                output[y * width + x] = ((alpha / windowSize) shl 24) or
                    ((red / windowSize) shl 16) or
                    ((green / windowSize) shl 8) or
                    (blue / windowSize)

                val removeY = (y - radius).coerceIn(0, height - 1)
                val addY = (y + radius + 1).coerceIn(0, height - 1)
                val removePixel = input[removeY * width + x]
                val addPixel = input[addY * width + x]
                alpha += (addPixel ushr 24) - (removePixel ushr 24)
                red += ((addPixel shr 16) and 0xFF) - ((removePixel shr 16) and 0xFF)
                green += ((addPixel shr 8) and 0xFF) - ((removePixel shr 8) and 0xFF)
                blue += (addPixel and 0xFF) - (removePixel and 0xFF)
            }
        }
    }

    private companion object {
        const val MAX_RADIUS_PX = 24
    }
}
