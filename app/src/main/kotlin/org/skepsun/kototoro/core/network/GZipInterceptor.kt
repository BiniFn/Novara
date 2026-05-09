package org.skepsun.kototoro.core.network

import okhttp3.Interceptor
import okhttp3.MultipartBody
import okhttp3.Response
import okio.IOException
import org.skepsun.kototoro.core.exceptions.WrapperIOException
import org.skepsun.kototoro.core.network.CommonHeaders.CONTENT_ENCODING
import org.skepsun.kototoro.parsers.network.GZipOptions

class GZipInterceptor : Interceptor {

	override fun intercept(chain: Interceptor.Chain): Response = try {
		val request = chain.request()
		val skipGZip = request.tag(GZipOptions::class.java)?.skip == true
		if (request.body is MultipartBody || request.header(CONTENT_ENCODING) != null || skipGZip) {
			chain.proceed(request)
		} else {
			val newRequest = request.newBuilder()
				.addHeader(CONTENT_ENCODING, "gzip")
				.build()
			chain.proceed(newRequest)
		}
	} catch (e: IOException) {
		throw e
	} catch (e: Exception) {
		throw WrapperIOException(e)
	}
}
