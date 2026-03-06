package org.skepsun.kototoro.core.util.ext

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun <T> Task<T>.awaitCancellable(): T = suspendCancellableCoroutine { continuation ->
	addOnSuccessListener { result ->
		if (continuation.isActive) {
			continuation.resume(result)
		}
	}
	addOnFailureListener { error ->
		if (continuation.isActive) {
			continuation.resumeWithException(error)
		}
	}
	addOnCanceledListener {
		if (continuation.isActive) {
			continuation.cancel()
		}
	}
}
