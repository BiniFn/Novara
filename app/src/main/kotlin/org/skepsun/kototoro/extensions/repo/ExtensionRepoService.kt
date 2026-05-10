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

	private fun isCloudstreamIndexEntry(segment: String?): Boolean {
		val value = segment?.lowercase().orEmpty()
		return value == "plugins.json" ||
			value == "repo.json" ||
			value == "repo" ||
			value.endsWith(".json")
	}

	private fun normalizeCloudstreamBaseUrl(url: okhttp3.HttpUrl): String {
		val normalizedSegments = url.pathSegments
			.filter { it.isNotEmpty() }
			.toMutableList()
		if (isCloudstreamIndexEntry(normalizedSegments.lastOrNull())) {
			normalizedSegments.removeLastOrNull()
		}
		val normalizedPath = if (normalizedSegments.isEmpty()) "/" else "/" + normalizedSegments.joinToString("/")
		return url.newBuilder()
			.encodedPath(normalizedPath)
			.fragment(null)
			.query(null)
			.build()
			.toString()
			.trimEnd('/')
	}

	suspend fun fetchRepoDetails(baseUrl: String, type: ExternalExtensionType): ExternalExtensionRepo {
		if (type == ExternalExtensionType.CLOUDSTREAM) {
			val normalizedInputUrl = baseUrl.toHttpUrlOrNull() ?: error("Invalid Cloudstream repository URL: $baseUrl")
			val metadataUrl = resolveCloudstreamRepoMetadataUrl(normalizedInputUrl)
			val body = withTimeout(REPO_DETAILS_TIMEOUT_MS) {
				httpClient.newCall(GET(applyMirror(metadataUrl))).awaitSuccess().use { response ->
					response.body.string()
				}
			}
			val dto = json.decodeFromString<CloudstreamRepositoryMetaDto>(body)
			val now = System.currentTimeMillis()
			val normalizedBaseUrl = normalizeCloudstreamBaseUrl(normalizedInputUrl)
			val derived = deriveRepoName(normalizedBaseUrl, "Cloudstream")
			return ExternalExtensionRepo(
				type = type,
				baseUrl = normalizedBaseUrl,
				name = dto.name.ifBlank { "Cloudstream: $derived" },
				shortName = dto.name.takeIf { it.isNotBlank() } ?: derived,
				website = dto.repositoryUrl ?: dto.website ?: normalizedBaseUrl,
				signingKeyFingerprint = normalizedBaseUrl.hashCode().toString(16),
				createdAt = now,
				updatedAt = now,
				lastSuccessAt = now,
				lastError = null,
				version = dto.manifestVersion?.toString(),
			)
		}

		if (type == ExternalExtensionType.IREADER || type == ExternalExtensionType.JAR) {
			val now = System.currentTimeMillis()
			val fallbackName = when (type) {
				ExternalExtensionType.IREADER -> "IReader"
				ExternalExtensionType.JAR -> "Kototoro"
				ExternalExtensionType.CLOUDSTREAM -> error("Cloudstream handled separately")
				else -> error("Unsupported local metadata extension type: $type")
			}
			val derived = deriveRepoName(baseUrl, fallbackName)
			val repoName = when (type) {
				ExternalExtensionType.IREADER -> "IReader: $derived"
				ExternalExtensionType.JAR -> "Kototoro: $derived"
				ExternalExtensionType.CLOUDSTREAM -> error("Cloudstream handled separately")
				else -> error("Unsupported local metadata extension type: $type")
			}
			val repoShort = derived
			var version: String? = null
			if (type == ExternalExtensionType.JAR) {
				val indexUrl = applyMirror("$baseUrl/index.min.json")
				runCatching {
					withTimeout(REPO_DETAILS_TIMEOUT_MS) {
						val body = httpClient.newCall(GET(indexUrl)).awaitSuccess().use { response ->
							response.body.string()
						}
						val dto = json.decodeFromString<List<ExtensionIndexDto>>(body)
						version = dto.firstOrNull()?.version
					}
				}
			}

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
				version = version,
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
		val indexUrls = if (repo.type == ExternalExtensionType.CLOUDSTREAM) {
			fetchCloudstreamPluginListUrls(repo)
		} else {
			listOf("${repo.baseUrl}/index.min.json")
		}
		val requestUrls = indexUrls.map(::applyMirror)
		val startedAt = System.currentTimeMillis()
		Log.d(TAG, "fetchAvailableExtensions:start type=${repo.type} urls=$requestUrls")
		return runCatching {
			withTimeout(CATALOG_TIMEOUT_MS) {
				requestUrls.flatMap { requestUrl ->
					val body = httpClient.newCall(GET(requestUrl)).awaitSuccess().use { response ->
						response.body.string()
					}
					when (repo.type) {
						ExternalExtensionType.IREADER -> {
							val dto = json.decodeFromString<List<IReaderExtensionIndexDto>>(body)
							dto.asSequence()
								.mapNotNull { item -> item.toAvailableExtension(repo) }
								.toList()
						}
						ExternalExtensionType.CLOUDSTREAM -> {
							val dto = json.decodeFromString<List<CloudstreamPluginIndexDto>>(body)
							dto.asSequence()
								.mapNotNull { item -> item.toAvailableExtension(repo) }
								.toList()
						}
						else -> {
							val dto = json.decodeFromString<List<ExtensionIndexDto>>(body)
							dto.asSequence()
								.mapNotNull { item -> item.toAvailableExtension(repo) }
								.toList()
						}
					}
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
		if (looksLikeCloudstreamRepoUrl(url)) {
			return url.newBuilder()
				.fragment(null)
				.query(null)
				.build()
				.toString()
		}
		val normalizedSegments = url.pathSegments
			.filter { it.isNotEmpty() }
			.toMutableList()
		val lastSegment = normalizedSegments.lastOrNull()
		if (lastSegment != "index.min.json" && lastSegment != "plugins.json") {
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
		val url = indexUrl.toHttpUrlOrNull()
		if (url != null && looksLikeCloudstreamRepoUrl(url)) {
			return normalizeCloudstreamBaseUrl(url)
		}
		return indexUrl
			.removeSuffix("/index.min.json")
			.removeSuffix("/plugins.json")
	}

	private fun ExtensionIndexDto.toAvailableExtension(repo: ExternalExtensionRepo): RepoAvailableExtension? {
		val libVersion = runCatching { version.substringBeforeLast('.').toDouble() }.getOrNull() ?: if (repo.type == ExternalExtensionType.IREADER) 0.0 else return null
		val supported = when (repo.type) {
			ExternalExtensionType.MIHON -> libVersion in MihonExtensionLoader.LIB_VERSION_MIN..MihonExtensionLoader.LIB_VERSION_MAX
			ExternalExtensionType.ANIYOMI -> libVersion in AniyomiExtensionLoader.LIB_VERSION_MIN..AniyomiExtensionLoader.LIB_VERSION_MAX
			ExternalExtensionType.IREADER -> true
			ExternalExtensionType.JAR -> true
			ExternalExtensionType.CLOUDSTREAM -> true
		}
		val displayName = when (repo.type) {
			ExternalExtensionType.MIHON -> name.removePrefix("Tachiyomi: ")
			ExternalExtensionType.ANIYOMI -> name.removePrefix("Aniyomi: ")
			ExternalExtensionType.IREADER -> name.removePrefix("IReader: ")
			ExternalExtensionType.JAR -> name
			ExternalExtensionType.CLOUDSTREAM -> name
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
			archiveName = apk,
			archiveUrl = null,
			iconUrl = applyMirror(if (repo.type == ExternalExtensionType.IREADER) "${repo.baseUrl}/icon/${apk.replace(".apk", ".png")}" else "${repo.baseUrl}/icon/$pkg.png"),
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
			archiveName = apk,
			archiveUrl = null,
			iconUrl = applyMirror("${repo.baseUrl}/icon/${apk.replace(".apk", ".png")}"),
			repoUrl = repo.baseUrl,
			repoName = repo.displayName,
			// IReader repos currently don't expose a verifiable APK signing fingerprint.
			// `repo.signingKeyFingerprint` is a synthetic repo identifier for repo management,
			// not the package certificate fingerprint, so using it for trust checks would
			// always misclassify installed IReader extensions as untrusted.
			signatureHash = "",
			isCompatible = true,
		)
	}

	private fun CloudstreamPluginIndexDto.toAvailableExtension(repo: ExternalExtensionRepo): RepoAvailableExtension? {
		val normalizedUrl = pluginUrlFrom(repo.baseUrl, url)
		val archiveName = normalizedUrl.substringAfterLast('/').ifBlank { "$internalName.cs3" }
		val normalizedLanguage = language ?: "all"
		val normalizedVersionName = version.toString()
		return RepoAvailableExtension(
			type = repo.type,
			name = name,
			pkgName = internalName,
			versionName = normalizedVersionName,
			versionCode = version.toLong(),
			libVersion = apiVersion.toDouble(),
			lang = normalizedLanguage,
			isNsfw = false,
			sourceNames = listOf(name),
			archiveName = archiveName,
			archiveUrl = normalizedUrl,
			iconUrl = iconUrl?.let(::applyMirror).orEmpty(),
			repoUrl = repo.baseUrl,
			repoName = repo.displayName,
			// Cloudstream catalogs expose file hashes, not Android package signing fingerprints.
			// Treating fileHash as a signature would incorrectly mark every installed plugin as untrusted.
			signatureHash = "",
			isCompatible = true,
		)
	}

	private suspend fun fetchCloudstreamPluginListUrls(repo: ExternalExtensionRepo): List<String> {
		val metadataUrl = resolveCloudstreamRepoMetadataUrl(requireNotNull(repo.baseUrl.toHttpUrlOrNull()))
		val body = httpClient.newCall(GET(applyMirror(metadataUrl))).awaitSuccess().use { response ->
			response.body.string()
		}
		val dto = json.decodeFromString<CloudstreamRepositoryMetaDto>(body)
		return dto.pluginLists
			.map { pluginUrlFrom(repo.baseUrl, it) }
			.ifEmpty { listOf(pluginUrlFrom(repo.baseUrl, "plugins.json")) }
	}

	private fun resolveCloudstreamRepoMetadataUrl(url: okhttp3.HttpUrl): String {
		val lastSegment = url.pathSegments.filter { it.isNotEmpty() }.lastOrNull()
		if (isCloudstreamIndexEntry(lastSegment)) {
			return url.newBuilder()
				.fragment(null)
				.query(null)
				.build()
				.toString()
		}
		return url.newBuilder()
			.addPathSegment("repo.json")
			.fragment(null)
			.query(null)
			.build()
			.toString()
	}

	private fun looksLikeCloudstreamRepoUrl(url: okhttp3.HttpUrl): Boolean {
		val joinedPath = url.encodedPath.lowercase()
		return joinedPath.contains("/builds/") ||
			joinedPath.endsWith("/repo.json") ||
			joinedPath.endsWith("/plugins.json") ||
			joinedPath.endsWith("/repo") ||
			url.host.contains("codeberg.org")
	}

	private fun pluginUrlFrom(baseUrl: String, url: String): String {
		if (url.startsWith("http://") || url.startsWith("https://")) {
			return applyMirror(url)
		}
		val base = baseUrl.trimEnd('/')
		val suffix = url.removePrefix("/")
		return applyMirror("$base/$suffix")
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

	@Keep
	@Serializable
	private data class CloudstreamPluginIndexDto(
		val url: String,
		val status: Int = 1,
		val version: Int,
		val apiVersion: Int = 1,
		val name: String,
		val internalName: String,
		val authors: List<String> = emptyList(),
		val description: String? = null,
		val repositoryUrl: String? = null,
		val tvTypes: List<String>? = null,
		val language: String? = null,
		val iconUrl: String? = null,
		val fileSize: Long? = null,
		val fileHash: String? = null,
	)

	@Keep
	@Serializable
	private data class CloudstreamRepositoryMetaDto(
		val name: String = "",
		val iconUrl: String? = null,
		val description: String? = null,
		val manifestVersion: Int? = null,
		val pluginLists: List<String> = emptyList(),
		val repositoryUrl: String? = null,
		val website: String? = null,
	)

	private companion object {
		const val TAG = "ExtensionRepo"
		const val REPO_DETAILS_TIMEOUT_MS = 15_000L
		const val CATALOG_TIMEOUT_MS = 20_000L
	}
}
