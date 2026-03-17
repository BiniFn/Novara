package org.skepsun.kototoro.extensions.repo

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.skepsun.kototoro.core.network.ContentHttpClient
import org.skepsun.kototoro.mihon.MihonExtensionLoader
import org.skepsun.kototoro.aniyomi.AniyomiExtensionLoader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExtensionRepoService @Inject constructor(
	@ContentHttpClient private val httpClient: OkHttpClient,
	private val json: Json,
) {

	suspend fun fetchRepoDetails(baseUrl: String, type: ExternalExtensionType): ExternalExtensionRepo {
		val repoJsonUrl = "$baseUrl/repo.json"
		val startedAt = System.currentTimeMillis()
		Log.d(TAG, "fetchRepoDetails:start type=$type url=$repoJsonUrl")
		return withTimeout(REPO_DETAILS_TIMEOUT_MS) {
			val body = httpClient.newCall(GET("$baseUrl/repo.json")).awaitSuccess().use { response ->
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
		val startedAt = System.currentTimeMillis()
		Log.d(TAG, "fetchAvailableExtensions:start type=${repo.type} url=$indexUrl")
		return runCatching {
			withTimeout(CATALOG_TIMEOUT_MS) {
				val body = httpClient.newCall(GET(indexUrl)).awaitSuccess().use { response ->
					response.body.string()
				}
				val dto = json.decodeFromString<List<ExtensionIndexDto>>(body)
				dto.asSequence()
					.mapNotNull { item -> item.toAvailableExtension(repo) }
					.toList()
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
		val url = input.trim().toHttpUrlOrNull() ?: return null
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
		val libVersion = runCatching { version.substringBeforeLast('.').toDouble() }.getOrNull() ?: return null
		val supported = when (repo.type) {
			ExternalExtensionType.MIHON -> libVersion in MihonExtensionLoader.LIB_VERSION_MIN..MihonExtensionLoader.LIB_VERSION_MAX
			ExternalExtensionType.ANIYOMI -> libVersion in AniyomiExtensionLoader.LIB_VERSION_MIN..AniyomiExtensionLoader.LIB_VERSION_MAX
		}
		val displayName = when (repo.type) {
			ExternalExtensionType.MIHON -> name.removePrefix("Tachiyomi: ")
			ExternalExtensionType.ANIYOMI -> name.removePrefix("Aniyomi: ")
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
			iconUrl = "${repo.baseUrl}/icon/$pkg.png",
			repoUrl = repo.baseUrl,
			repoName = repo.displayName,
			signatureHash = repo.signingKeyFingerprint,
			isCompatible = supported,
		)
	}

	@Serializable
	private data class RepoMetaWrapperDto(
		val meta: RepoMetaDto,
	)

	@Serializable
	private data class RepoMetaDto(
		val name: String,
		@SerialName("shortName")
		val shortName: String? = null,
		val website: String,
		@SerialName("signingKeyFingerprint")
		val signingKeyFingerprint: String,
	)

	@Serializable
	private data class ExtensionIndexDto(
		val name: String,
		val pkg: String,
		val apk: String,
		val lang: String,
		val code: Long,
		val version: String,
		val nsfw: Int,
		val sources: List<ExtensionSourceDto>? = null,
	)

	@Serializable
	private data class ExtensionSourceDto(
		val name: String,
	)

	private companion object {
		const val TAG = "ExtensionRepo"
		const val REPO_DETAILS_TIMEOUT_MS = 15_000L
		const val CATALOG_TIMEOUT_MS = 20_000L
	}
}
