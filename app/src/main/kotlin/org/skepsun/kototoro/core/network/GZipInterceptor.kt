package org.skepsun.kototoro.core.network

import okhttp3.Interceptor
import okhttp3.MultipartBody
import okhttp3.Response
import okio.IOException
import org.skepsun.kototoro.core.exceptions.WrapperIOException
import org.skepsun.kototoro.core.network.CommonHeaders.CONTENT_ENCODING

class GZipInterceptor : Interceptor {

	override fun intercept(chain: Interceptor.Chain): Response = try {
		val request = chain.request()
		// Only add Content-Encoding: gzip for bodyless requests (e.g. GET).
		// For requests with a body (POST/PUT), the body is NOT actually compressed,
		// so adding this header causes servers that respect it (e.g. kagane) to
		// fail with "corrupt gzip stream" when they try to decompress.
		if (request.body != null || request.body is MultipartBody) {
			chain.proceed(request)
		} else {
			val newRequest = request.newBuilder()
			newRequest.addHeader(CONTENT_ENCODING, "gzip")
			chain.proceed(newRequest.build())
		}
	} catch (e: IOException) {
		throw e
	} catch (e: Exception) {
		throw WrapperIOException(e)
	}
}
