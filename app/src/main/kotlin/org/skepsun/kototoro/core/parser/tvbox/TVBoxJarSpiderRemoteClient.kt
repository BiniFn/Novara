package org.skepsun.kototoro.core.parser.tvbox

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import androidx.core.os.bundleOf
import com.github.tvbox.osc.base.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.skepsun.kototoro.core.util.ext.getParcelableCompat
import org.skepsun.kototoro.tvbox.bridge.TVBoxCompanionContract
import org.skepsun.kototoro.tvbox.bridge.TVBoxJarSpiderRequest
import org.skepsun.kototoro.tvbox.bridge.TVBoxJarSpiderResponse
import org.skepsun.kototoro.tvbox.bridge.TVBoxJarSpiderWorkerProtocol
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

internal class TVBoxJarSpiderRemoteClient(context: Context) {

	companion object {
		private const val TAG = "TVBoxJarRemote"
		private const val MAX_ATTEMPTS = 3
		private const val RETRY_DELAY_MS = 250L
		private const val CALL_TIMEOUT_BUFFER_MS = 5_000L
		private const val WORKER_RECYCLE_DELAY_MS = 250L

		private val companionProbeLogged = AtomicBoolean(false)
		private val workerMutex = Mutex()
	}

	private val applicationContext = context.applicationContext

	suspend fun execute(request: TVBoxJarSpiderRequest): TVBoxJarSpiderResponse {
		return workerMutex.withLock {
			Log.i(TAG, "Executing TVBox spider remote call for ${request.sourceDisplayName}: action=${request.action} attempts=$MAX_ATTEMPTS")
			repeat(MAX_ATTEMPTS - 1) {
				val response = executeOnce(request)
				if (!shouldRetry(response)) {
					waitForWorkerRecycle(response)
					return@withLock response
				}
				delay(RETRY_DELAY_MS)
			}
			val response = executeOnce(request)
			waitForWorkerRecycle(response)
			response
		}
	}

	private suspend fun executeOnce(request: TVBoxJarSpiderRequest): TVBoxJarSpiderResponse {
		logCompanionAvailabilityOnce()
		return withTimeoutOrNull(request.timeoutMs + CALL_TIMEOUT_BUFFER_MS) {
			val bindingIntent = resolveBindingIntent()
			ensureCompanionReady(bindingIntent)
			suspendCancellableCoroutine { continuation ->
				val callStartedAt = System.currentTimeMillis()
				val finished = AtomicBoolean(false)
				val bound = AtomicBoolean(false)

				fun release(connection: ServiceConnection) {
					if (bound.compareAndSet(true, false)) {
						runCatching { applicationContext.unbindService(connection) }
					}
				}

				fun finish(connection: ServiceConnection, response: TVBoxJarSpiderResponse) {
					if (!finished.compareAndSet(false, true)) {
						return
					}
					Log.i(
						TAG,
						"Finishing TVBox spider remote call for ${request.sourceDisplayName}: action=${request.action} code=${response.errorCode ?: "ok"} message=${response.errorMessage ?: "-"}",
					)
					release(connection)
					continuation.resume(response)
				}

				var serviceConnection: ServiceConnection? = null
				val replyHandler = object : Handler(Looper.getMainLooper()) {
					override fun handleMessage(message: Message) {
						if (message.what != TVBoxJarSpiderWorkerProtocol.MESSAGE_RESULT) {
							return
						}
						val data = message.data ?: Bundle.EMPTY
						data.classLoader = TVBoxJarSpiderResponse::class.java.classLoader
						val response = data.getParcelableCompat<TVBoxJarSpiderResponse>(
							TVBoxJarSpiderWorkerProtocol.KEY_RESPONSE,
						) ?: errorResponse(
							code = TVBoxJarSpiderWorkerProtocol.ERROR_EXECUTION,
							message = "TVBox spider worker returned an empty response",
						)
						serviceConnection?.let { finish(connection = it, response = response) }
					}
				}
				val replyMessenger = Messenger(replyHandler)

				serviceConnection = object : ServiceConnection {
					override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
						Log.i(
							TAG,
							"TVBox spider worker connected for ${request.sourceDisplayName}: action=${request.action} component=${name?.flattenToShortString() ?: "-"} binder=${service != null}",
						)
						if (service == null) {
							finish(
								connection = this,
								response = errorResponse(
									code = TVBoxJarSpiderWorkerProtocol.ERROR_DISCONNECTED,
									message = "TVBox spider worker returned a null binder",
								),
							)
							return
						}
						val message = Message.obtain(null, TVBoxJarSpiderWorkerProtocol.MESSAGE_EXECUTE).apply {
							replyTo = replyMessenger
							data = bundleOf(TVBoxJarSpiderWorkerProtocol.KEY_REQUEST to request)
						}
						runCatching {
							Messenger(service).send(message)
						}.onFailure { error ->
							finish(
								connection = this,
								response = errorResponse(
									code = TVBoxJarSpiderWorkerProtocol.ERROR_EXECUTION,
									message = error.message ?: "Failed to send TVBox spider request",
								),
							)
						}
					}

					override fun onServiceDisconnected(name: ComponentName?) {
						val exitInfo = describeRecentWorkerExit(callStartedAt)
						finish(
							connection = this,
							response = errorResponse(
								code = exitInfo.toErrorCode(),
								message = buildString {
									append("TVBox spider worker disconnected before responding")
									exitInfo?.let {
										append(" (")
										append(it)
										append(')')
									}
								},
							),
						)
					}

					override fun onBindingDied(name: ComponentName?) {
						val exitInfo = describeRecentWorkerExit(callStartedAt)
						finish(
							connection = this,
							response = errorResponse(
								code = exitInfo.toErrorCode(),
								message = buildString {
									append("TVBox spider worker binding died")
									exitInfo?.let {
										append(" (")
										append(it)
										append(')')
									}
								},
							),
						)
					}

					override fun onNullBinding(name: ComponentName?) {
						finish(
							connection = this,
							response = errorResponse(
								code = TVBoxJarSpiderWorkerProtocol.ERROR_DISCONNECTED,
								message = "TVBox spider worker returned a null binding",
							),
						)
					}
				}

				continuation.invokeOnCancellation {
					if (finished.compareAndSet(false, true)) {
						serviceConnection?.let(::release)
					}
				}

				val boundNow = runCatching {
					val companionBound = applicationContext.bindService(
						bindingIntent,
						checkNotNull(serviceConnection),
						Context.BIND_AUTO_CREATE,
					)
					if (!companionBound && bindingIntent.component?.packageName == TVBoxCompanionContract.PACKAGE_NAME) {
						Log.w(
							TAG,
							"TVBox companion bind returned false; state=${TVBoxCompanionAvailability.describeState(applicationContext)}; falling back to in-app worker service",
						)
						applicationContext.bindService(
							Intent(applicationContext, TVBoxJarSpiderService::class.java),
							checkNotNull(serviceConnection),
							Context.BIND_AUTO_CREATE,
						)
					} else {
						companionBound
					}
				}.getOrElse { error ->
					if (error is SecurityException) {
						Log.w(
							TAG,
							"Binding TVBox companion was denied; falling back to in-app worker service",
							error,
						)
						applicationContext.bindService(
							Intent(applicationContext, TVBoxJarSpiderService::class.java),
							checkNotNull(serviceConnection),
							Context.BIND_AUTO_CREATE,
						)
					} else {
						throw error
					}
				}
				Log.i(TAG, "Binding TVBox spider worker for ${request.sourceDisplayName}: action=${request.action} success=$boundNow")
				if (!boundNow) {
					finish(
						connection = checkNotNull(serviceConnection),
						response = errorResponse(
							code = TVBoxJarSpiderWorkerProtocol.ERROR_BIND_FAILED,
							message = "Unable to bind TVBox spider worker service",
						),
					)
					return@suspendCancellableCoroutine
				}
				bound.set(true)
			}
		} ?: errorResponse(
			code = TVBoxJarSpiderWorkerProtocol.ERROR_TIMEOUT,
			message = buildString {
				append("TVBox spider worker timed out after ${request.timeoutMs}ms")
				describeRecentWorkerExit(System.currentTimeMillis() - request.timeoutMs - CALL_TIMEOUT_BUFFER_MS)?.let {
					append(" (")
					append(it)
					append(')')
				}
			},
		)
	}

	private fun errorResponse(code: String, message: String): TVBoxJarSpiderResponse {
		return TVBoxJarSpiderResponse(
			errorCode = code,
			errorMessage = message,
		)
	}

	private fun shouldRetry(response: TVBoxJarSpiderResponse): Boolean {
		return response.errorCode == TVBoxJarSpiderWorkerProtocol.ERROR_WORKER_SPENT ||
			response.errorCode == TVBoxJarSpiderWorkerProtocol.ERROR_DISCONNECTED
	}

	private fun logCompanionAvailabilityOnce() {
		if (!companionProbeLogged.compareAndSet(false, true)) {
			return
		}
		val companionComponent = TVBoxCompanionAvailability.resolveService(applicationContext)
		if (companionComponent == null) {
			Log.i(
				TAG,
				"TVBox companion service is not installed or not visible yet; continuing with in-app worker service (${TVBoxCompanionAvailability.describeState(applicationContext)})",
			)
			return
		}
		Log.i(
			TAG,
			"TVBox companion service resolved: component=${companionComponent.flattenToShortString()} state=${TVBoxCompanionAvailability.describeState(applicationContext)} (remote client will prefer companion binding)",
		)
	}

	private fun resolveBindingIntent(): Intent {
		val companionComponent = TVBoxCompanionAvailability.resolveService(applicationContext)
		if (companionComponent != null) {
			Log.i(
				TAG,
				"Binding TVBox companion runtime: component=${companionComponent.flattenToShortString()}",
			)
			return TVBoxCompanionAvailability.companionIntent().setComponent(companionComponent)
		}
		Log.i(
			TAG,
			"TVBox companion runtime unavailable at bind time; using in-app worker service (${TVBoxCompanionAvailability.describeState(applicationContext)})",
		)
		return Intent(applicationContext, TVBoxJarSpiderService::class.java)
	}

	private suspend fun ensureCompanionReady(bindingIntent: Intent) {
		if (bindingIntent.component?.packageName != TVBoxCompanionContract.PACKAGE_NAME) {
			return
		}
		val warmUp = TVBoxCompanionAvailability.warmUp(applicationContext)
		if (warmUp.success) {
			Log.i(
				TAG,
				"TVBox companion bootstrap ping succeeded: ${warmUp.detail}",
			)
			return
		}
		Log.w(
			TAG,
			"TVBox companion bootstrap ping failed: ${warmUp.detail}; state=${TVBoxCompanionAvailability.describeState(applicationContext)}",
		)
		launchBootstrapActivityIfNeeded(warmUp.detail)
	}

	private suspend fun launchBootstrapActivityIfNeeded(reason: String) {
		val intent = TVBoxCompanionAvailability.bootstrapActivityIntent()
		runCatching {
			applicationContext.startActivity(intent)
		}.onSuccess {
			Log.i(
				TAG,
				"TVBox companion bootstrap activity launched: reason=$reason component=${intent.component?.flattenToShortString() ?: "-"}",
			)
		}.onFailure { error ->
			Log.w(
				TAG,
				"TVBox companion bootstrap activity launch failed: reason=$reason",
				error,
			)
			return
		}
		withContext(Dispatchers.Default) {
			delay(200L)
		}
	}

	private suspend fun waitForWorkerRecycle(response: TVBoxJarSpiderResponse) {
		if (response.isSuccess || shouldRetry(response)) {
			delay(WORKER_RECYCLE_DELAY_MS)
		}
	}

	private fun describeRecentWorkerExit(sinceMs: Long): String? {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
			return null
		}
		val activityManager = applicationContext.getSystemService(ActivityManager::class.java) ?: return null
		val processNames = setOf(
			"${applicationContext.packageName}:tvbox_spider",
			"${App.HOST_PACKAGE_NAME}:tvbox_spider",
		)
		val exitInfo = runCatching {
			activityManager.getHistoricalProcessExitReasons(applicationContext.packageName, 0, 16)
				.firstOrNull { info ->
					info.processName in processNames && info.timestamp >= sinceMs - 2_000L
				}
		}.getOrNull() ?: return null
		return buildString {
			append("process=")
			append(exitInfo.processName)
			append(" reason=")
			append(exitReasonName(exitInfo.reason))
			append(" status=")
			append(exitInfo.status)
			append(" importance=")
			append(exitInfo.importance)
			exitInfo.description?.takeIf { it.isNotBlank() }?.let {
				append(" description=")
				append(it.take(160))
			}
			readExitTracePreview(exitInfo)?.let {
				append(" trace=")
				append(it)
			}
			writeExitTraceDump(exitInfo)?.let {
				append(" traceFile=")
				append(it)
			}
		}
	}

	private fun String?.toErrorCode(): String {
		if (this == null) {
			return TVBoxJarSpiderWorkerProtocol.ERROR_DISCONNECTED
		}
		return if (
			contains("reason=CRASH_NATIVE", ignoreCase = true) ||
			contains("JNI DETECTED ERROR IN APPLICATION", ignoreCase = true)
		) {
			TVBoxJarSpiderWorkerProtocol.ERROR_FATAL_NATIVE_CRASH
		} else {
			TVBoxJarSpiderWorkerProtocol.ERROR_DISCONNECTED
		}
	}

	private fun readExitTracePreview(exitInfo: ApplicationExitInfo): String? {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
			return null
		}
		return runCatching {
			exitInfo.traceInputStream?.bufferedReader()?.useLines { lines ->
				lines.asSequence()
					.map { it.trim() }
					.filter { it.isNotEmpty() }
					.take(24)
					.joinToString(" | ")
					.take(2048)
			}
		}.getOrNull()?.takeIf { it.isNotBlank() }
	}

	private fun writeExitTraceDump(exitInfo: ApplicationExitInfo): String? {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
			return null
		}
		return runCatching {
			val traceText = exitInfo.traceInputStream?.bufferedReader()?.use { reader ->
				reader.readText()
			}.orEmpty().trim()
			if (traceText.isBlank()) {
				return@runCatching null
			}
			val dumpDir = File(applicationContext.filesDir, "tvbox_guard_crash").apply { mkdirs() }
			val file = File(
				dumpDir,
				"exit_${exitInfo.timestamp}_${exitInfo.pid}_${exitReasonName(exitInfo.reason)}.log",
			)
			file.writeText(traceText, Charsets.UTF_8)
			file.absolutePath
		}.getOrNull()
	}

	private fun exitReasonName(reason: Int): String {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
			return reason.toString()
		}
		return when (reason) {
			ApplicationExitInfo.REASON_ANR -> "ANR"
			ApplicationExitInfo.REASON_CRASH -> "CRASH"
			ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
			ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
			ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "RESOURCE_USAGE"
			ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
			ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INIT_FAILURE"
			ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
			ApplicationExitInfo.REASON_OTHER -> "OTHER"
			ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
			ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
			ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
			ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
			else -> reason.toString()
		}
	}
}
