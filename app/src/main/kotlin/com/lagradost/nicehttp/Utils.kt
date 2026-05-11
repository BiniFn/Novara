@file:JvmName("UtilsKt")

package com.lagradost.nicehttp

import okhttp3.OkHttpClient

fun ignoreAllSSLErrors(builder: OkHttpClient.Builder): OkHttpClient.Builder {
	return builder.ignoreAllSSLErrors()
}
