package org.skepsun.kototoro.image.ui

import android.content.Context
import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.skepsun.kototoro.core.util.ext.mangaSourceExtra
import org.skepsun.kototoro.local.epub.EpubImageExtractor
import org.skepsun.kototoro.parsers.model.ContentSource
import java.io.File
import java.net.URI

internal object NovelInlineImageLoader {

    suspend fun loadBitmap(
        context: Context,
        imageLoader: ImageLoader,
        imagePath: String,
        source: ContentSource?,
        epubFilePath: String?,
        chapterPath: String?,
        headers: Map<String, String>,
    ): Bitmap? = withContext(Dispatchers.IO) {
        return@withContext when {
            imagePath.startsWith("http://", ignoreCase = true) ||
                imagePath.startsWith("https://", ignoreCase = true) ||
                imagePath.startsWith("file://", ignoreCase = true) ||
                imagePath.startsWith("content://", ignoreCase = true) -> {
                loadDirectBitmap(context, imageLoader, imagePath, source, headers)
            }

            imagePath.startsWith("file+zip", ignoreCase = true) ||
                imagePath.startsWith("zip", ignoreCase = true) ||
                imagePath.startsWith("cbz", ignoreCase = true) -> {
                val uri = URI(imagePath)
                val zipPath = uri.schemeSpecificPart
                    .substringBefore('#')
                    .removePrefix("///")
                    .let { if (it.startsWith("/")) it else "/$it" }
                val entryPath = uri.fragment?.removePrefix("/").orEmpty()
                if (entryPath.isBlank()) null else loadEmbeddedBitmap(context, imageLoader, File(zipPath), entryPath)
            }

            !epubFilePath.isNullOrBlank() -> {
                val epubFile = File(epubFilePath)
                if (!epubFile.exists()) {
                    null
                } else {
                    val extractor = EpubImageExtractor(epubFile)
                    val resolvedPath = if (chapterPath.isNullOrBlank()) {
                        imagePath
                    } else {
                        extractor.resolveImagePath(chapterPath, imagePath)
                    }
                    loadEmbeddedBitmap(context, imageLoader, epubFile, resolvedPath)
                }
            }

            else -> loadDirectBitmap(context, imageLoader, imagePath, source, headers)
        }
    }

    private suspend fun loadDirectBitmap(
        context: Context,
        imageLoader: ImageLoader,
        imagePath: String,
        source: ContentSource?,
        headers: Map<String, String>,
    ): Bitmap? {
        val requestBuilder = ImageRequest.Builder(context).data(imagePath)
        if (source != null) {
            requestBuilder.mangaSourceExtra(source)
        }
        if (headers.isNotEmpty()) {
            val networkHeaders = NetworkHeaders.Builder().apply {
                headers.forEach { (key, value) -> add(key, value) }
            }.build()
            requestBuilder.httpHeaders(networkHeaders)
        }
        return when (val result = imageLoader.execute(requestBuilder.build())) {
            is SuccessResult -> result.image.toBitmap(
                width = result.image.width,
                height = result.image.height,
            )

            is ErrorResult -> throw result.throwable
        }
    }

    private suspend fun loadEmbeddedBitmap(
        context: Context,
        imageLoader: ImageLoader,
        file: File,
        entryPath: String,
    ): Bitmap? {
        if (!file.exists()) {
            return null
        }
        val bytes = EpubImageExtractor(file).extractImage(entryPath) ?: return null
        return when (val result = imageLoader.execute(ImageRequest.Builder(context).data(bytes).build())) {
            is SuccessResult -> result.image.toBitmap(
                width = result.image.width,
                height = result.image.height,
            )

            is ErrorResult -> throw result.throwable
        }
    }
}
