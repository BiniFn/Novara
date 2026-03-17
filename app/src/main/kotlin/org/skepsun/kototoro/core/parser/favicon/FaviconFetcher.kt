package org.skepsun.kototoro.core.parser.favicon

import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Build
import coil3.ColorImage
import org.json.JSONObject
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.size.pxOrElse
import coil3.toAndroidUri
import coil3.toBitmap
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import okio.FileSystem
import okio.IOException
import okio.Path.Companion.toOkioPath
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.exceptions.CloudFlareProtectedException
import org.skepsun.kototoro.core.parser.EmptyContentRepository
import org.skepsun.kototoro.core.parser.ContentRepository
import org.skepsun.kototoro.core.parser.ParserContentRepository
import org.skepsun.kototoro.core.parser.legado.LegadoRepository
import org.skepsun.kototoro.core.parser.external.ExternalContentRepository
import org.skepsun.kototoro.core.parser.JsContentRepository
import org.skepsun.kototoro.mihon.MihonMangaRepository
import org.skepsun.kototoro.core.util.MimeTypes
import org.skepsun.kototoro.core.util.ext.fetch
import org.skepsun.kototoro.core.util.ext.printStackTraceDebug
import org.skepsun.kototoro.core.util.ext.toMimeTypeOrNull
import org.skepsun.kototoro.local.data.FaviconCache
import org.skepsun.kototoro.local.data.LocalMangaRepository
import org.skepsun.kototoro.local.data.LocalStorageCache
import org.skepsun.kototoro.core.parser.kotatsu.KotatsuParserRepository
import org.skepsun.kototoro.parsers.util.runCatchingCancellable
import java.io.File
import javax.inject.Inject
import coil3.Uri as CoilUri
import org.skepsun.kototoro.parsers.model.ContentSource as ParserContentSource
import org.skepsun.kototoro.core.jsonsource.JsonContentSource

class FaviconFetcher(
	private val uri: Uri,
	private val options: Options,
	private val imageLoader: ImageLoader,
	private val mangaRepositoryFactory: ContentRepository.Factory,
	private val localStorageCache: LocalStorageCache,
) : Fetcher {

	override suspend fun fetch(): FetchResult? {
		val mangaSource = org.skepsun.kototoro.core.model.ContentSource(uri.schemeSpecificPart)

		return when (val repo = mangaRepositoryFactory.create(mangaSource)) {
			is ParserContentRepository -> fetchParserFavicon(repo)
			is KotatsuParserRepository -> fetchKotatsuFavicon(repo)
			is ExternalContentRepository -> fetchPluginIcon(repo)
			is EmptyContentRepository -> ImageFetchResult(
				image = ColorImage(Color.WHITE),
				isSampled = false,
				dataSource = DataSource.MEMORY,
			)
			
			// JSON/Legado sources: try to derive favicon from bookSourceUrl in config
			is LegadoRepository -> {
				val config = (repo.source as? JsonContentSource)?.entity?.config
				val siteUrl = try {
					config?.let { JSONObject(it).optString("bookSourceUrl") }
				} catch (e: Exception) {
					null
				}
				val faviconUrl = siteUrl?.let { url ->
					val uri = Uri.parse(url)
					if (uri.scheme != null && uri.host != null) {
						"${uri.scheme}://${uri.host}/favicon.ico"
					} else null
				}
				faviconUrl?.let { url ->
					runCatchingCancellable { imageLoader.fetch(url, options) }.getOrNull()
				} ?: fetchDefaultIcon(mangaSource)
			}

			is LocalMangaRepository -> imageLoader.fetch(R.drawable.ic_storage, options)

			is MihonMangaRepository -> fetchMihonIcon(repo)

			is org.skepsun.kototoro.aniyomi.AniyomiAnimeRepository -> fetchAniyomiIcon(repo)

			// JS sources: try to derive favicon from config; fallback to neutral
			is JsContentRepository -> {
				val guessed = guessFaviconUrl((repo.source as? JsonContentSource)?.entity?.config)
				guessed?.let { url ->
					runCatchingCancellable { imageLoader.fetch(url, options) }.getOrNull()
				} ?: fetchDefaultIcon(mangaSource)
			}

			else -> fetchDefaultIcon(mangaSource)
		}
	}

	private suspend fun fetchDefaultIcon(mangaSource: ParserContentSource): FetchResult? {
		return imageLoader.fetch(defaultIconRes(mangaSource), options)
	}

    private suspend fun fetchMihonIcon(repository: MihonMangaRepository): FetchResult {
        val pm = options.context.packageManager
        val pkgName = repository.source.pkgName
        val icon = runInterruptible {
            try {
                pm.getApplicationIcon(pkgName)
            } catch (e: Exception) {
                null
            }
        }
        return if (icon != null) {
            ImageFetchResult(
                image = icon.nonAdaptive().asImage(),
                isSampled = false,
                dataSource = DataSource.DISK,
            )
        } else {
            imageLoader.fetch(R.drawable.ic_storage, options)!!
        }
    }

    private suspend fun fetchAniyomiIcon(repository: org.skepsun.kototoro.aniyomi.AniyomiAnimeRepository): FetchResult {
        val pm = options.context.packageManager
        val pkgName = repository.source.pkgName
        val icon = runInterruptible {
            try {
                pm.getApplicationIcon(pkgName)
            } catch (e: Exception) {
                null
            }
        }
        return if (icon != null) {
            ImageFetchResult(
                image = icon.nonAdaptive().asImage(),
                isSampled = false,
                dataSource = DataSource.DISK,
            )
        } else {
            imageLoader.fetch(R.drawable.ic_storage, options)!!
        }
    }

	private suspend fun fetchKotatsuFavicon(repository: KotatsuParserRepository): FetchResult {
		val sizePx = maxOf(
			options.size.width.pxOrElse { FALLBACK_SIZE },
			options.size.height.pxOrElse { FALLBACK_SIZE },
		)
		val cacheKey = options.diskCacheKey ?: "${repository.source.name}_$sizePx"
		if (options.diskCachePolicy.readEnabled) {
			localStorageCache[cacheKey]?.let { file ->
				return SourceFetchResult(
					source = ImageSource(file.toOkioPath(), FileSystem.SYSTEM),
					mimeType = MimeTypes.probeMimeType(file)?.toString(),
					dataSource = DataSource.DISK,
				)
			}
		}
		var favicons = repository.getFavicons()
		var lastError: Exception? = null
		while (favicons.isNotEmpty()) {
			currentCoroutineContext().ensureActive()
			val icon = favicons.find(sizePx) ?: throwNSEE(lastError)
			try {
				val result = imageLoader.fetch(icon.url, options)
				if (result != null) {
					return if (options.diskCachePolicy.writeEnabled) {
						writeToCache(cacheKey, result)
					} else {
						result
					}
				} else {
					favicons -= icon
				}
			} catch (e: CloudFlareProtectedException) {
				throw e
			} catch (e: IOException) {
				lastError = e
				favicons -= icon
			}
		}
		throwNSEE(lastError)
	}

	private suspend fun fetchParserFavicon(repository: ParserContentRepository): FetchResult {
		val sizePx = maxOf(
			options.size.width.pxOrElse { FALLBACK_SIZE },
			options.size.height.pxOrElse { FALLBACK_SIZE },
		)
		val cacheKey = options.diskCacheKey ?: "${repository.source.name}_$sizePx"
		if (options.diskCachePolicy.readEnabled) {
			localStorageCache[cacheKey]?.let { file ->
				return SourceFetchResult(
					source = ImageSource(file.toOkioPath(), FileSystem.SYSTEM),
					mimeType = MimeTypes.probeMimeType(file)?.toString(),
					dataSource = DataSource.DISK,
				)
			}
		}
		var favicons = repository.getFavicons()
		var lastError: Exception? = null
		while (favicons.isNotEmpty()) {
			currentCoroutineContext().ensureActive()
			val icon = favicons.find(sizePx) ?: throwNSEE(lastError)
			try {
				val result = imageLoader.fetch(icon.url, options)
				if (result != null) {
					return if (options.diskCachePolicy.writeEnabled) {
						writeToCache(cacheKey, result)
					} else {
						result
					}
				} else {
					favicons -= icon
				}
			} catch (e: CloudFlareProtectedException) {
				throw e
			} catch (e: IOException) {
				lastError = e
				favicons -= icon
			}
		}
		throwNSEE(lastError)
	}

	private suspend fun fetchPluginIcon(repository: ExternalContentRepository): FetchResult {
		val source = repository.source
		val pm = options.context.packageManager
		val icon = runInterruptible {
			val provider = pm.resolveContentProvider(source.authority, 0)
			provider?.loadIcon(pm) ?: pm.getApplicationIcon(source.packageName)
		}
		return ImageFetchResult(
			image = icon.nonAdaptive().asImage(),
			isSampled = false,
			dataSource = DataSource.DISK,
		)
	}

	private suspend fun writeToCache(key: String, result: FetchResult): FetchResult = runCatchingCancellable {
		when (result) {
			is ImageFetchResult -> {
				if (result.dataSource == DataSource.NETWORK) {
					localStorageCache.set(key, result.image.toBitmap()).asFetchResult()
				} else {
					result
				}
			}

			is SourceFetchResult -> {
				if (result.dataSource == DataSource.NETWORK) {
					result.source.source().use {
						localStorageCache.set(key, it, result.mimeType?.toMimeTypeOrNull()).asFetchResult()
					}
				} else {
					result
				}
			}
		}
	}.onFailure {
		it.printStackTraceDebug()
	}.getOrDefault(result)

	private fun File.asFetchResult() = SourceFetchResult(
		source = ImageSource(toOkioPath(), FileSystem.SYSTEM),
		mimeType = MimeTypes.probeMimeType(this)?.toString(),
		dataSource = DataSource.DISK,
	)

	class Factory @Inject constructor(
		private val mangaRepositoryFactory: ContentRepository.Factory,
		@FaviconCache private val faviconCache: LocalStorageCache,
	) : Fetcher.Factory<CoilUri> {

		override fun create(
			data: CoilUri,
			options: Options,
			imageLoader: ImageLoader
		): Fetcher? = if (data.scheme == URI_SCHEME_FAVICON) {
			FaviconFetcher(data.toAndroidUri(), options, imageLoader, mangaRepositoryFactory, faviconCache)
		} else {
			null
		}
	}

	private companion object {

		const val FALLBACK_SIZE = 9999 // largest icon

		private fun defaultIconRes(source: ParserContentSource): Int {
			return if (source is JsonContentSource || source.name.startsWith("JSON_")) {
				R.drawable.ic_source_builtin
			} else {
				R.drawable.ic_storage
			}
		}

		private fun guessFaviconUrl(config: String?): String? {
			if (config.isNullOrBlank()) return null
			val urlRegex = Regex("https?://[A-Za-z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]+")
			val keyRegex = Regex(
				"\"(?:homepage|homePage|root|baseUrl|base_url|defaultBaseUrl|apiUrl|api_base|baseURL)\"\\s*:\\s*\"(https?://[^\"]+)\"",
				RegexOption.IGNORE_CASE
			)
			val candidates = mutableListOf<String>()
			keyRegex.findAll(config).forEach { m -> candidates.add(m.groupValues.getOrNull(1).orEmpty()) }
			urlRegex.findAll(config).forEach { m -> candidates.add(m.value) }
			val site = candidates.firstOrNull { isLikelySiteUrl(it) } ?: return null
			val uri = Uri.parse(site)
			if (uri.scheme.isNullOrBlank() || uri.host.isNullOrBlank()) return null
			return "${uri.scheme}://${uri.host}/favicon.ico"
		}

		private fun isLikelySiteUrl(url: String?): Boolean {
			if (url.isNullOrBlank()) return false
			if (!url.startsWith("http://") && !url.startsWith("https://")) return false
			if (url.endsWith(".js", true)) return false
			val lowered = url.lowercase()
			val blacklist = listOf(
				"venera-configs",
				"raw.githubusercontent.com",
				"git.nyne.dev",
				"gitlab",
				"/raw/",
				"/blob/",
			)
			if (blacklist.any { lowered.contains(it) }) return false
			return Uri.parse(url).host?.isNotBlank() == true
		}

		private fun throwNSEE(lastError: Exception?): Nothing {
			if (lastError != null) {
				throw lastError
			} else {
				throw NoSuchElementException("No favicons found")
			}
		}

		private fun Drawable.nonAdaptive() =
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && this is AdaptiveIconDrawable) {
				LayerDrawable(arrayOf(background, foreground))
			} else {
				this
			}

	}
}
