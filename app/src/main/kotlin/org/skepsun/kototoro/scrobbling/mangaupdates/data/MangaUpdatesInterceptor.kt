package org.skepsun.kototoro.scrobbling.mangaupdates.data

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.internal.closeQuietly
import org.skepsun.kototoro.core.network.CommonHeaders
import org.skepsun.kototoro.scrobbling.common.data.ScrobblerStorage
import org.skepsun.kototoro.scrobbling.common.domain.ScrobblerAuthRequiredException
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService
import java.net.HttpURLConnection

class MangaUpdatesInterceptor(private val storage: ScrobblerStorage) : Interceptor {

	override fun intercept(chain: Interceptor.Chain): Response {
		val sourceRequest = chain.request()
		val request = sourceRequest.newBuilder()
		
		val isAuthRequest = sourceRequest.url.pathSegments.contains("account") && sourceRequest.url.pathSegments.contains("login")
		if (!isAuthRequest) {
			storage.accessToken?.let {
				request.header(CommonHeaders.AUTHORIZATION, "Bearer $it")
			}
		}
		
		val response = chain.proceed(request.build())
		
		if (!isAuthRequest && response.code == HttpURLConnection.HTTP_UNAUTHORIZED) {
			response.closeQuietly()
			throw ScrobblerAuthRequiredException(ScrobblerService.MANGAUPDATES)
		}
		
		return response
	}
}
