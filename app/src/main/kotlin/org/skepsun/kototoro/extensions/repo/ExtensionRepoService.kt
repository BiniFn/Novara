package org.skepsun.kototoro.extensions.repo

import android.util.Log
import androidx.annotation.Keep
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.network.ContentHttpClient
import org.skepsun.kototoro.mihon.MihonExtensionLoader
import org.skepsun.kototoro.aniyomi.AniyomiExtensionLoader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExtensionRepoService @Inject constructor(
	@ContentHttpClient private val httpClient: OkHttpClient,
	private val json: Json,
	private val settings: AppSettings,
) {

	private fun applyMirror(url: String): String {
		if (url.startsWith("https://raw.githubusercontent.com/")) {
			return when (settings.gitHubMirror) {
				AppSettings.GitHubMirror.NATIVE -> url
				AppSettings.GitHubMirror.KKGITHUB -> url.replace("raw.githubusercontent.com", "raw.kkgithub.com")
				AppSettings.GitHubMirror.GHPROXY -> "https://mirror.ghproxy.com/$url"
				AppSettings.GitHubMirror.GHPROXY_NET -> "https://ghproxy.net/$url"
			}
		}
		return url
	}

	private fun deriveRepoName(baseUrl: String, defaultName: String): String {
		val url = baseUrl.toHttpUrlOrNull() ?: return defaultName
		val segments = url.pathSegments.filter { it.isNotEmpty() }
		if (segments.size >= 2 && url.host.contains("githubusercontent.com")) {
			return "${segments[0]}/${segments[1]}"
		} else if (segments.size >= 2 && url.host == "github.com") {
			return "${segments[0]}/${segments[1]}"
		} else if (segments.isNotEmpty()) {
			return segments.last()
		}
		return url.host
	}

	suspend fun fetchRepoDetails(baseUrl: String, type: ExternalExtensionType): ExternalExtensionRepo {
		if (type == ExternalExtensionType.IREADER || type == ExternalExtensionType.JAR) {
			val now = System.currentTimeMillis()
			val derived = deriveRepoName(baseUrl, if (type == ExternalExtensionType.IREADER) "IReader" else "Kototoro")
			val repoName = if (type == ExternalExtensionType.IREADER) "IReader: $derived" else "Kototoro: $derived"
			val repoShort = derived
			return ExternalExtensionRepo(
				type = type,
				baseUrl = baseUrl,
				name = repoName,
				shortName = repoShort,
				website = baseUrl,
				signingKeyFingerprint = baseUrl.hashCode().toString(16), // Use baseUrl hash as pseudo-fingerprint for JAR/IReader
				createdAt = now,
				updatedAt = now,
				lastSuccessAt = now,
				lastError = null,
			)
		}

		val repoJsonUrl = applyMirror("$baseUrl/repo.json")
		val startedAt = System.currentTimeMillis()
		Log.d(TAG, "fetchRepoDetails:start type=$type url=$repoJsonUrl")
		return withTimeout(REPO_DETAILS_TIMEOUT_MS) {
			val body = httpClient.newCall(GET(repoJsonUrl)).awaitSuccess().use { response ->
				response.body.string()
			}
			val dto = json.decodeFromString<RepoMetaWrapperDto>(body)
			val now = System.currentTimeMillis()
			ExternalExtensionRepo(
				type = type,
				baseUrl = baseUrl,
				name = dto.meta.name,
				shortName = dto.meta.shortName,
				website = dto.meta.website,
				signingKeyFingerprint = dto.meta.signingKeyFingerprint,
				createdAt = now,
				updatedAt = now,
				lastSuccessAt = now,
				lastError = null,
			)
		}.also { repo ->
			Log.d(
				TAG,
				"fetchRepoDetails:success type=$type baseUrl=${repo.baseUrl} name=${repo.displayName} elapsedMs=${System.currentTimeMillis() - startedAt}",
			)
		}
	}

	suspend fun fetchAvailableExtensions(repo: ExternalExtensionRepo): List<RepoAvailableExtension> {
		val indexUrl = "${repo.baseUrl}/index.min.json"
		val requestUrl = applyMirror(indexUrl)
		val startedAt = System.currentTimeMillis()
		Log.d(TAG, "fetchAvailableExtensions:start type=${repo.type} url=$requestUrl")
		return runCatching {
			withTimeout(CATALOG_TIMEOUT_MS) {
				val body = httpClient.newCall(GET(requestUrl)).awaitSuccess().use { response ->
					response.body.string()
				}
				if (repo.type == ExternalExtensionType.IREADER) {
					val dto = json.decodeFromString<List<IReaderExtensionIndexDto>>(body)
					dto.asSequence()
						.mapNotNull { item -> item.toAvailableExtension(repo) }
						.toList()
				} else {
					val dto = json.decodeFromString<List<ExtensionIndexDto>>(body)
					dto.asSequence()
						.mapNotNull { item -> item.toAvailableExtension(repo) }
						.toList()
				}
			}
		}.onSuccess { extensions ->
			Log.d(
				TAG,
				"fetchAvailableExtensions:success type=${repo.type} baseUrl=${repo.baseUrl} count=${extensions.size} elapsedMs=${System.currentTimeMillis() - startedAt}",
			)
		}.onFailure { error ->
			Log.e(
				TAG,
				"fetchAvailableExtensions:failed type=${repo.type} baseUrl=${repo.baseUrl} elapsedMs=${System.currentTimeMillis() - startedAt} message=${error.message}",
				error,
			)
		}.getOrDefault(emptyList())
	}

	fun normalizeIndexUrl(input: String): String? {
		val processUrl = input.trim()

		val url = processUrl.toHttpUrlOrNull() ?: return null
		if (url.scheme != "https") {
			return null
		}
		val normalizedSegments = url.pathSegments
			.filter { it.isNotEmpty() }
			.toMutableList()
		if (normalizedSegments.lastOrNull() != "index.min.json") {
			normalizedSegments += "index.min.json"
		}
		val normalizedPath = "/" + normalizedSegments.joinToString("/")
		return url.newBuilder()
			.encodedPath(normalizedPath)
			.fragment(null)
			.query(null)
			.build()
			.toString()
	}

	fun baseUrlFromIndexUrl(indexUrl: String): String {
		return indexUrl.removeSuffix("/index.min.json")
	}

	private fun ExtensionIndexDto.toAvailableExtension(repo: ExternalExtensionRepo): RepoAvailableExtension? {
		val libVersion = runCatching { version.substringBeforeLast('.').toDouble() }.getOrNull() ?: if (repo.type == ExternalExtensionType.IREADER) 0.0 else return null
		val supported = when (repo.type) {
			ExternalExtensionType.MIHON -> libVersion in MihonExtensionLoader.LIB_VERSION_MIN..MihonExtensionLoader.LIB_VERSION_MAX
			ExternalExtensionType.ANIYOMI -> libVersion in AniyomiExtensionLoader.LIB_VERSION_MIN..AniyomiExtensionLoader.LIB_VERSION_MAX
			ExternalExtensionType.IREADER -> true
			ExternalExtensionType.JAR -> true
		}
		val displayName = when (repo.type) {
			ExternalExtensionType.MIHON -> name.removePrefix("Tachiyomi: ")
			ExternalExtensionType.ANIYOMI -> name.removePrefix("Aniyomi: ")
			ExternalExtensionType.IREADER -> name.removePrefix("IReader: ")
			ExternalExtensionType.JAR -> name
		}

		return RepoAvailableExtension(
			type = repo.type,
			name = displayName,
			pkgName = pkg,
			versionName = version,
			versionCode = code,
			libVersion = libVersion,
			lang = lang,
			isNsfw = nsfw == 1,
			sourceNames = sources.orEmpty().map { it.name },
			apkName = apk,
			iconUrl = if (repo.type == ExternalExtensionType.IREADER) "${repo.baseUrl}/icon/${apk.replace(".apk", ".png")}" else "${repo.baseUrl}/icon/$pkg.png",
			repoUrl = repo.baseUrl,
			repoName = repo.displayName,
			signatureHash = repo.signingKeyFingerprint,
			isCompatible = supported,
		)
	}

	private fun IReaderExtensionIndexDto.toAvailableExtension(repo: ExternalExtensionRepo): RepoAvailableExtension {
		val libVersion = runCatching { version.substringBeforeLast('.').toDouble() }.getOrNull() ?: 0.0
		val displayName = name.removePrefix("IReader: ")

		return RepoAvailableExtension(
			type = repo.type,
			name = displayName,
			pkgName = pkg,
			versionName = version,
			versionCode = code,
			libVersion = libVersion,
			lang = lang,
			isNsfw = nsfw,
			sourceNames = emptyList(), // IReader plugins don't declare subset sources natively
			apkName = apk,
			iconUrl = "${repo.baseUrl}/icon/${apk.replace(".apk", ".png")}",
			repoUrl = repo.baseUrl,
			repoName = repo.displayName,
			signatureHash = repo.signingKeyFingerprint,
			isCompatible = true,
		)
	}



	@Keep
	@Serializable
	private data class RepoMetaWrapperDto(
		val meta: RepoMetaDto,
	)

	@Keep
	@Serializable
	private data class RepoMetaDto(
		val name: String,
		@SerialName("shortName")
		val shortName: String? = null,
		val website: String,
		@SerialName("signingKeyFingerprint")
		val signingKeyFingerprint: String,
	)

	@Keep
	@Serializable
	private data class ExtensionIndexDto(
		val name: String,
		val pkg: String,
		val apk: String,
		val lang: String = "all",
		val code: Long,
		val version: String,
		val nsfw: Int = 0,
		val sources: List<ExtensionSourceDto>? = null,
	)

	@Keep
	@Serializable
	private data class ExtensionSourceDto(
		val name: String,
	)

	@Keep
	@Serializable
	private data class IReaderExtensionIndexDto(
		val name: String = "",
		val pkg: String = "",
		val apk: String = "",
		val lang: String = "en",
		val code: Long = 1,
		val version: String = "1.0",
		val nsfw: Boolean = false,
	)

	private companion object {
		const val TAG = "ExtensionRepo"
		const val REPO_DETAILS_TIMEOUT_MS = 15_000L
		const val CATALOG_TIMEOUT_MS = 20_000L
	}
}
