package com.lagradost.nicehttp

import kotlinx.coroutines.CancellableContinuation
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.Unit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.jvm.functions.Function1

class ContinuationCallback(
	private val call: Call,
	private val continuation: CancellableContinuation<Response>,
) : Callback, Function1<Throwable?, Unit> {

	override fun onFailure(
		call: Call,
		e: IOException,
	) {
		if (continuation.isCancelled) return
		continuation.resumeWithException(e)
	}

	override fun onResponse(
		call: Call,
		response: Response,
	) {
		if (continuation.isCancelled) {
			response.close()
			return
		}
		continuation.resume(response)
	}

	override fun invoke(cause: Throwable?) {
		call.cancel()
	}
}
