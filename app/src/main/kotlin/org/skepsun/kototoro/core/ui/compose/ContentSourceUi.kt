package org.skepsun.kototoro.core.ui.compose

import android.content.Context
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.Image
import coil3.compose.AsyncImage
import coil3.asImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import dagger.hilt.android.EntryPointAccessors
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.BaseApp
import org.skepsun.kototoro.core.exceptions.resolve.CaptchaHandler.Companion.suppressCaptchaErrors
import org.skepsun.kototoro.core.model.getLocale
import org.skepsun.kototoro.core.model.getOriginLabel
import org.skepsun.kototoro.core.model.getTitle
import org.skepsun.kototoro.core.BaseAppHolder
import org.skepsun.kototoro.core.parser.favicon.faviconUri
import org.skepsun.kototoro.core.ui.image.FaviconDrawable
import org.skepsun.kototoro.core.util.ext.mangaSourceExtra
import org.skepsun.kototoro.parsers.model.ContentSource
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class ContentSourceChipMeta(
    val iconRes: Int,
    val text: String,
)

@Composable
fun rememberResolvedContentSource(source: ContentSource): ContentSource {
    val context = LocalContext.current
    val entryPoint = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            BaseApp.BaseAppEntryPoint::class.java,
        )
    }
    val mihonInstalled by entryPoint.mihonExtensionManager().installedExtensions.collectAsState()
    val aniyomiInstalled by entryPoint.aniyomiExtensionManager().installedExtensions.collectAsState()
    val ireaderInstalled by entryPoint.ireaderExtensionManager().installedExtensions.collectAsState()
    val mihonLoading by entryPoint.mihonExtensionManager().isLoading.collectAsState()
    val aniyomiLoading by entryPoint.aniyomiExtensionManager().isLoading.collectAsState()
    val ireaderLoading by entryPoint.ireaderExtensionManager().isLoading.collectAsState()
    return remember(
        source.name,
        mihonInstalled,
        aniyomiInstalled,
        ireaderInstalled,
        mihonLoading,
        aniyomiLoading,
        ireaderLoading,
    ) {
        resolveDynamicContentSource(source, entryPoint) ?: source
    }
}

@Composable
fun rememberResolvedSourceTitle(source: ContentSource): String {
    val context = LocalContext.current
    val resolvedSource = rememberResolvedContentSource(source)
    return remember(source.name, resolvedSource.javaClass.name) {
        resolveReadableSourceTitle(
            context = context,
            originalSource = source,
            resolvedSource = resolvedSource,
        )
    }
}

@Composable
fun rememberSourceChipMeta(source: ContentSource): ContentSourceChipMeta? {
    val context = LocalContext.current
    val resolvedSource = rememberResolvedContentSource(source)
    return remember(source.name, resolvedSource.javaClass.name, resolvedSource.locale) {
        val locale = resolvedSource.getLocale()
            ?.language
            ?.takeIf { it.isNotBlank() }
            ?.uppercase(Locale.ROOT)
            .orEmpty()
        val origin = resolvedSource.getOriginLabel(context)
            ?: source.getOriginLabel(context)
            .orEmpty()
        val text = when {
            locale.isNotBlank() -> locale
            origin.isNotBlank() -> origin
            else -> ""
        }
        text.takeIf { it.isNotBlank() }?.let {
            ContentSourceChipMeta(
                iconRes = resolvedSource.iconResForUi(),
                text = it,
            )
        }
    }
}

@Composable
fun ContentSourceIcon(
    source: ContentSource,
    modifier: Modifier = Modifier,
    styleResId: Int = R.style.FaviconDrawable_Small,
    animated: Boolean = false,
    contentDescription: String? = null,
) {
    val resolvedSource = rememberResolvedContentSource(source)
    ContentSourceResolvedIcon(
        source = resolvedSource,
        modifier = modifier,
        styleResId = styleResId,
        animated = animated,
        contentDescription = contentDescription,
    )
}

@Composable
fun ContentSourceResolvedIcon(
    source: ContentSource,
    modifier: Modifier = Modifier,
    styleResId: Int = R.style.FaviconDrawable_Small,
    animated: Boolean = false,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    val resolvedSource = source
    val sourceFailureKey = remember(resolvedSource.name, styleResId) {
        "${resolvedSource.name}#$styleResId"
    }
    val fallbackDrawable = remember(resolvedSource.name, resolvedSource.locale, styleResId) {
        FaviconDrawable(context, styleResId, resolveFallbackTitle(context, resolvedSource)).asImage()
    }
    val fallbackFactory: (ImageRequest) -> Image? = remember(fallbackDrawable) {
        { fallbackDrawable }
    }

    var hasError by remember(sourceFailureKey) {
        androidx.compose.runtime.mutableStateOf(failedSourceIcons.contains(sourceFailureKey))
    }

    val request: Any = remember(resolvedSource.name, resolvedSource.locale, styleResId, animated, hasError) {
        if (hasError) {
            fallbackDrawable
        } else {
            ImageRequest.Builder(context)
                .data(resolvedSource.faviconUri())
                .crossfade(animated)
                .mangaSourceExtra(resolvedSource)
                .suppressCaptchaErrors()
                .placeholder(fallbackFactory)
                .fallback(fallbackFactory)
                .error(fallbackFactory)
                .build()
        }
    }

    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier.clip(RoundedCornerShape(4.dp)),
        onError = {
            failedSourceIcons[sourceFailureKey] = true
            hasError = true
        }
    )
}

private val failedSourceIcons = ConcurrentHashMap<String, Boolean>()

fun clearFailedContentSourceIcons() {
    failedSourceIcons.clear()
}

private fun resolveFallbackTitle(
    context: Context,
    source: ContentSource,
): String {
    val title = source.getTitle(context)
    return title.takeIf { it.isNotBlank() && !it.startsWith("Loading ", ignoreCase = true) }
        ?: source.getOriginLabel(context)
        ?: source.name
}

private fun resolveDynamicContentSource(
    source: ContentSource,
    entryPoint: BaseApp.BaseAppEntryPoint,
): ContentSource? = when {
    source.name.startsWith("MIHON_") -> entryPoint.mihonExtensionManager().getMihonMangaSourceByName(source.name)
    source.name.startsWith("ANIYOMI_") -> entryPoint.aniyomiExtensionManager().getAniyomiAnimeSourceByName(source.name)
    source.name.startsWith("IREADER_") -> entryPoint.ireaderExtensionManager().getIReaderMangaSourceByName(source.name)
    source.name.startsWith("CLOUDSTREAM_") -> BaseAppHolder.get()?.findSourceByName(source.name)
    else -> null
}

private fun resolveReadableSourceTitle(
    context: Context,
    originalSource: ContentSource,
    resolvedSource: ContentSource,
): String {
    val resolvedTitle = resolvedSource.getTitle(context)
    if (resolvedTitle.isNotBlank() && !resolvedTitle.startsWith("Loading ", ignoreCase = true)) {
        return resolvedTitle
    }
    val fallbackTitle = originalSource.getTitle(context)
    return if (fallbackTitle.startsWith("Loading ", ignoreCase = true)) {
        originalSource.getOriginLabel(context) ?: originalSource.name
    } else {
        fallbackTitle
    }
}

fun ContentSource.iconResForUi(): Int = when {
    name.startsWith("MIHON_") -> R.drawable.ic_source_mihon
    name.startsWith("ANIYOMI_") -> R.drawable.ic_source_aniyomi
    name.startsWith("CLOUDSTREAM_") -> R.drawable.ic_source_cloudstream
    name.startsWith("JSON_TVBOX_") -> R.drawable.ic_source_tvbox
    name.startsWith("JSON_JS_") -> R.drawable.ic_source_js
    name.startsWith("JSON_LEGADO_") || name.startsWith("JSON_LEGADO_M_") -> R.drawable.ic_source_legado
    name.startsWith("JSON_LNREADER_") || name.startsWith("IREADER_") -> R.drawable.ic_source_ireader
    name.startsWith("LOCAL") -> R.drawable.ic_storage
    else -> R.drawable.ic_source_builtin
}
