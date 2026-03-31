package org.skepsun.kototoro.core.github

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.skepsun.kototoro.BuildConfig
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.network.BaseHttpClient
import org.skepsun.kototoro.core.os.AppValidator
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.parsers.util.json.mapJSONNotNull
import org.skepsun.kototoro.parsers.util.parseJsonArray
import org.skepsun.kototoro.parsers.util.runCatchingCancellable
import org.skepsun.kototoro.parsers.util.suspendlazy.getOrNull
import javax.inject.Inject
import javax.inject.Singleton

private const val CONTENT_TYPE_APK = "application/vnd.android.package-archive"
private const val BUILD_TYPE_RELEASE = "release"
private const val ABI_ARM64_V8A = "arm64-v8a"
private const val ABI_ARMEABI_V7A = "armeabi-v7a"
private const val ABI_X86_64 = "x86_64"
private const val ABI_X86 = "x86"
private const val ABI_UNIVERSAL = "universal"

@Singleton
class AppUpdateRepository @Inject constructor(
	private val appValidator: AppValidator,
	private val settings: AppSettings,
	@BaseHttpClient private val okHttp: OkHttpClient,
	@ApplicationContext context: Context,
) {

	private val availableUpdate = MutableStateFlow<AppVersion?>(null)
	private val releasesUrl = buildString {
		append("https://api.github.com/repos/")
		append(context.getString(R.string.github_updates_repo))
		append("/releases?page=1&per_page=10")
	}

	val isUpdateAvailable: Boolean
		get() = availableUpdate.value != null

	fun observeAvailableUpdate() = availableUpdate.asStateFlow()

	suspend fun getAvailableVersions(): List<AppVersion> {
		val request = Request.Builder()
			.get()
			.url(releasesUrl)
		val jsonArray = okHttp.newCall(request.build()).execute().use { it.parseJsonArray() }
		return jsonArray.mapJSONNotNull { json ->
			val assets = json.optJSONArray("assets")
				?.toAssetList()
				.orEmpty()
			
			val apkAssets = assets.filter { it.isApkAsset() }
			if (apkAssets.isEmpty()) {
				return@mapJSONNotNull null
			}
			val apkAsset = apkAssets.findBestAssetForCurrentDevice() ?: apkAssets.first()
			val patchAsset = assets.filter { it.isPatchAsset() }.findBestAssetForCurrentDevice()
			
			AppVersion(
				id = json.getLong("id"),
				url = json.getString("html_url"),
				name = json.getString("name").removePrefix("v"),
				apkSize = apkAsset.getLong("size"),
				apkUrl = apkAsset.getString("browser_download_url"),
				patchSize = patchAsset?.getLong("size"),
				patchUrl = patchAsset?.getString("browser_download_url"),
				description = json.getString("body"),
			)
		}
	}

	suspend fun fetchUpdate(): AppVersion? = withContext(Dispatchers.Default) {
		if (!isUpdateSupported()) {
			return@withContext null
		}
		runCatchingCancellable {
			val currentVersion = parseVersionId(BuildConfig.VERSION_NAME)
			val available = getAvailableVersions().toMutableList()
			available.sortBy { it.versionId }
			if (currentVersion.variantType.isEmpty() && !settings.isUnstableUpdatesAllowed) {
				available.retainAll { it.versionId.variantType.isEmpty() }
			}
			
			val latest = available.maxByOrNull { it.versionId }?.takeIf { it.versionId > currentVersion }
			if (latest != null && latest.patchUrl != null) {
				// The patch is generated against the immediate prior release.
				val sorted = available.sortedByDescending { it.versionId }
				val previousReleaseIndex = sorted.indexOf(latest) + 1
				val isImmediatePrecursor = previousReleaseIndex < sorted.size && 
					currentVersion == sorted[previousReleaseIndex].versionId
					
				if (!isImmediatePrecursor) {
					// Fallback to full APK if the user skipped a version
					return@runCatchingCancellable latest.copy(patchUrl = null, patchSize = null)
				}
			}
			latest
		}.onFailure {
			it.printStackTrace()
		}.onSuccess {
			availableUpdate.value = it
		}.getOrNull()
	}

	@Suppress("KotlinConstantConditions")
	suspend fun isUpdateSupported(): Boolean {
		return BuildConfig.BUILD_TYPE != BUILD_TYPE_RELEASE || appValidator.isOriginalApp.getOrNull() == true
	}

	private fun parseVersionId(versionName: String): VersionId {
		val normalized = versionName.trim()
		if (normalized.startsWith('n', ignoreCase = true)) {
			val nightlyBuild = normalized.filter { it.isDigit() }.toIntOrNull() ?: 0
			return VersionId(0, 0, nightlyBuild, "n", 0)
		}
		val parts = normalized.substringBeforeLast('-').split('.')
		val variant = normalized.substringAfterLast('-', "")
		return VersionId(
			major = parts.getOrNull(0)?.toIntOrNull() ?: 0,
			minor = parts.getOrNull(1)?.toIntOrNull() ?: 0,
			build = parts.getOrNull(2)?.toIntOrNull() ?: 0,
			variantType = variant.filter(Char::isLetter),
			variantNumber = variant.filter(Char::isDigit).toIntOrNull() ?: 0,
		)
	}

	private fun JSONArray.toAssetList(): List<JSONObject> {
		val assetList = ArrayList<JSONObject>(length())
		val size = length()
		for (i in 0 until size) {
			val jo = getJSONObject(i)
			if (jo.isApkAsset() || jo.isPatchAsset()) {
				assetList += jo
			}
		}
		return assetList
	}

	private fun JSONObject.isApkAsset(): Boolean {
		val contentType = optString("content_type")
		if (contentType == CONTENT_TYPE_APK) {
			return true
		}
		return optString("name").endsWith(".apk", ignoreCase = true)
	}

	private fun JSONObject.isPatchAsset(): Boolean {
		return optString("name").endsWith(".patch", ignoreCase = true)
	}

	private fun List<JSONObject>.findBestAssetForCurrentDevice(): JSONObject? {
		val supportedAbis = Build.SUPPORTED_ABIS
			.mapNotNull { it.normalizeAbi() }
			.distinct()
		for (abi in supportedAbis) {
			firstOrNull { it.detectAssetAbi() == abi }?.let { return it }
		}
		return firstOrNull { it.detectAssetAbi() == ABI_UNIVERSAL }
			?: firstOrNull { it.detectAssetAbi() == null }
	}

	private fun String.normalizeAbi(): String? = when (lowercase()) {
		ABI_ARM64_V8A -> ABI_ARM64_V8A
		ABI_ARMEABI_V7A -> ABI_ARMEABI_V7A
		ABI_X86_64 -> ABI_X86_64
		ABI_X86 -> ABI_X86
		else -> null
	}

	private fun JSONObject.detectAssetAbi(): String? {
		val name = optString("name").lowercase()
		return when {
			"-$ABI_ARM64_V8A-" in name -> ABI_ARM64_V8A
			"-$ABI_ARMEABI_V7A-" in name -> ABI_ARMEABI_V7A
			"-$ABI_X86_64-" in name -> ABI_X86_64
			"-$ABI_X86-" in name -> ABI_X86
			"-$ABI_UNIVERSAL-" in name -> ABI_UNIVERSAL
			else -> null
		}
	}
}
