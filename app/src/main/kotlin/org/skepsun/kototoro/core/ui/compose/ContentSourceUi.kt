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
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import dagger.hilt.android.EntryPointAccessors
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.BaseApp
import org.skepsun.kototoro.core.exceptions.resolve.CaptchaHandler.Companion.suppressCaptchaErrors
import org.skepsun.kototoro.core.model.getLocale
import org.skepsun.kototoro.core.model.getOriginLabel
import org.skepsun.kototoro.core.model.getTitle
import org.skepsun.kototoro.core.parser.favicon.faviconUri
import org.skepsun.kototoro.core.ui.image.sourceFallbackImage
import org.skepsun.kototoro.core.util.ext.mangaSourceExtra
import org.skepsun.kototoro.parsers.model.ContentSource
import java.util.Locale

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
    return remember(source.name, resolvedSource.name) {
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
    return remember(source.name, resolvedSource.name, resolvedSource.locale) {
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
    val context = LocalContext.current
    val resolvedSource = rememberResolvedContentSource(source)
    val fallbackDrawable = remember(resolvedSource.name, resolvedSource.locale, styleResId) {
        sourceFallbackImage(
            context = context,
            styleResId = styleResId,
            source = resolvedSource,
            animated = false,
        )
    }
    
    var hasError by remember(resolvedSource.name) { androidx.compose.runtime.mutableStateOf(false) }

    val request = remember(resolvedSource.name, resolvedSource.locale, styleResId, animated, hasError) {
        if (hasError) {
            fallbackDrawable
        } else {
            ImageRequest.Builder(context)
                .data(resolvedSource.faviconUri())
                .crossfade(animated)
                .mangaSourceExtra(resolvedSource)
                .suppressCaptchaErrors()
                .placeholder(fallbackDrawable)
                .fallback(fallbackDrawable)
                .error(fallbackDrawable)
                .build()
        }
    }

    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .size(16.dp)
            .clip(RoundedCornerShape(4.dp)),
        onError = { 
            android.util.Log.e("DetailsFavicon", "ContentSourceIcon AsyncImage error for model $request. Throwable: ${it.result.throwable}")
            hasError = true 
        }
    )
}

private fun resolveDynamicContentSource(
    source: ContentSource,
    entryPoint: BaseApp.BaseAppEntryPoint,
): ContentSource? = when {
    source.name.startsWith("MIHON_") -> entryPoint.mihonExtensionManager().getMihonMangaSourceByName(source.name)
    source.name.startsWith("ANIYOMI_") -> entryPoint.aniyomiExtensionManager().getAniyomiAnimeSourceByName(source.name)
    source.name.startsWith("IREADER_") -> entryPoint.ireaderExtensionManager().getIReaderMangaSourceByName(source.name)
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
    name.startsWith("JSON_TVBOX_") -> R.drawable.ic_source_tvbox
    name.startsWith("JSON_JS_") -> R.drawable.ic_source_js
    name.startsWith("JSON_LEGADO_") || name.startsWith("JSON_LEGADO_M_") -> R.drawable.ic_source_legado
    name.startsWith("JSON_LNREADER_") || name.startsWith("IREADER_") -> R.drawable.ic_source_ireader
    name.startsWith("LOCAL") -> R.drawable.ic_storage
    else -> R.drawable.ic_source_builtin
}
