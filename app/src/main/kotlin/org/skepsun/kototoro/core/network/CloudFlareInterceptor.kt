package org.skepsun.kototoro.core.network

import okhttp3.Interceptor
import okhttp3.Response
import okio.IOException
import org.skepsun.kototoro.core.exceptions.CloudFlareBlockedException
import org.skepsun.kototoro.core.exceptions.CloudFlareProtectedException
import org.skepsun.kototoro.parsers.model.ContentSource
import org.skepsun.kototoro.parsers.network.CloudFlareHelper

class CloudFlareInterceptor : Interceptor {

	override fun intercept(chain: Interceptor.Chain): Response {
		val request = chain.request()
		val response = chain.proceed(request)
		return when (CloudFlareHelper.checkResponseForProtection(response)) {
			CloudFlareHelper.PROTECTION_BLOCKED -> response.closeThrowing(
				CloudFlareBlockedException(
					url = request.url.toString(),
					source = request.tag(ContentSource::class.java),
				),
			)

			CloudFlareHelper.PROTECTION_CAPTCHA -> response.closeThrowing(
				CloudFlareProtectedException(
					url = request.url.toString(),
					source = request.tag(ContentSource::class.java),
					headers = request.headers,
				),
			)

			else -> response
		}
	}

	private fun Response.closeThrowing(error: IOException): Nothing {
		try {
			close()
		} catch (e: Exception) {
			error.addSuppressed(e)
		}
		throw error
	}
}
