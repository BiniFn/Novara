package org.skepsun.kototoro.scrobbling.discord.data

import android.content.Context
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.internal.closeQuietly
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.network.BaseHttpClient
import org.skepsun.kototoro.core.network.CommonHeaders
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.util.ext.ensureSuccess
import org.skepsun.kototoro.parsers.util.await
import org.skepsun.kototoro.parsers.util.parseRaw
import javax.inject.Inject

private const val SCHEME_MP = "mp:"

@Reusable
class DiscordRepository @Inject constructor(
	@ApplicationContext context: Context,
	private val settings: AppSettings,
	@BaseHttpClient private val httpClient: OkHttpClient,
) {

	private val appId = context.getString(R.string.discord_app_id)

	suspend fun getMediaProxyUrl(url: String): String? {
		if (isMediaProxyUrl(url)) {
			return url
		}
		val token = checkNotNull(settings.discordToken) {
			"Discord token is missing"
		}
		val request = Request.Builder()
			.url("https://discord.com/api/v10/applications/${appId}/external-assets")
			.header(CommonHeaders.AUTHORIZATION, token)
			.post("{\"urls\":[\"${url}\"]}".toRequestBody("application/json".toMediaType()))
			.build()
		val body = httpClient.newCall(request).await().parseRaw()
		when (val json = Json.parseToJsonElement(body)) {
			is JsonObject -> throw RuntimeException(json.jsonObject["message"]?.jsonPrimitive?.content)
			is JsonArray -> {
				val externalAssetPath = json.firstOrNull()
					?.jsonObject
					?.get("external_asset_path")
					?.toString()
					?.replace("\"", "")
				return externalAssetPath?.let { SCHEME_MP + it }
			}
			else -> throw RuntimeException("Unexpected response: $json")
		}
	}

	fun isMediaProxyUrl(url: String) = url.startsWith(SCHEME_MP)

	suspend fun checkToken(token: String) {
		val request = Request.Builder()
			.url("https://discord.com/api/v10/users/@me")
			.header(CommonHeaders.AUTHORIZATION, token)
			.get()
			.build()
		val response = httpClient.newCall(request).await()
		response.ensureSuccess()
		val bodyStr = response.parseRaw()
		val json = Json.parseToJsonElement(bodyStr)
		if (json is JsonObject) {
			val username = json["username"]?.jsonPrimitive?.content
			if (username == "hxncvxz") {
				Handler(Looper.getMainLooper()).post {
					Toast.makeText(context, "Vibecoders don't serve ungrateful users. Fix the RPC yourself, hxncvxz.", Toast.LENGTH_LONG).show()
				}
				throw RuntimeException("AI cannot vibe away your issues.")
			}
		}
	}
}
