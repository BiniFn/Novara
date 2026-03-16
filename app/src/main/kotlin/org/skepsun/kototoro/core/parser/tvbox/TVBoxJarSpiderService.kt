package org.skepsun.kototoro.core.parser.tvbox

import android.app.Application
import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.util.Log
import androidx.multidex.MultiDex
import com.github.tvbox.osc.base.App
import com.github.tvbox.osc.util.OkGoHelper
import com.whl.quickjs.android.QuickJSLoader
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.skepsun.kototoro.core.network.jsonsource.LegadoHttpClient
import org.skepsun.kototoro.core.util.ext.getParcelableCompat
import org.skepsun.kototoro.tvbox.bridge.TVBoxJarSpiderRequest
import org.skepsun.kototoro.tvbox.bridge.TVBoxJarSpiderResponse
import org.skepsun.kototoro.tvbox.bridge.TVBoxJarSpiderWorkerProtocol
import java.util.concurrent.atomic.AtomicBoolean

internal class TVBoxJarSpiderService : Service() {

	companion object {
		private const val TAG = "TVBoxJarSpiderSvc"
		private const val SHUTDOWN_DELAY_MS = 100L
	}

	private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
	private val mainHandler = Handler(Looper.getMainLooper())
	private val requestAccepted = AtomicBoolean(false)
	private val shutdownScheduled = AtomicBoolean(false)
	private val shutdownRunnable = Runnable {
		Log.i(TAG, "Stopping TVBox spider worker process: pid=${Process.myPid()}")
		stopSelf()
		Process.killProcess(Process.myPid())
	}
	private val messenger by lazy(LazyThreadSafetyMode.NONE) {
		Messenger(IncomingHandler())
	}

	override fun onCreate() {
		super.onCreate()
		runCatching {
			MultiDex.install(this)
			Log.i(TAG, "Installed MultiDex for TVBox worker process: pid=${Process.myPid()}")
		}.onFailure {
			Log.w(TAG, "Failed to install MultiDex for TVBox worker process", it)
		}
		App.init(applicationContext)
		runCatching { OkGoHelper.init() }
			.onFailure { Log.w(TAG, "Failed to initialize TVBox OkGoHelper in worker process", it) }
		runCatching { QuickJSLoader.init() }
			.onFailure { Log.w(TAG, "Failed to initialize TVBox QuickJSLoader in worker process", it) }
		Log.i(TAG, "TVBox spider worker service created: pid=${Process.myPid()} process=${currentProcessName()}")
	}

	override fun onBind(intent: Intent?): IBinder {
		Log.i(TAG, "TVBox spider worker service bound: pid=${Process.myPid()} process=${currentProcessName()}")
		return messenger.binder
	}

	override fun onDestroy() {
		mainHandler.removeCallbacks(shutdownRunnable)
		Log.i(
			TAG,
			"TVBox spider worker service destroyed: pid=${Process.myPid()} requestAccepted=${requestAccepted.get()} shutdownScheduled=${shutdownScheduled.get()}",
		)
		serviceScope.cancel()
		super.onDestroy()
	}

	private inner class IncomingHandler : Handler(Looper.getMainLooper()) {
		override fun handleMessage(message: Message) {
			if (message.what != TVBoxJarSpiderWorkerProtocol.MESSAGE_EXECUTE) {
				super.handleMessage(message)
				return
			}
			val replyTo = message.replyTo ?: return
			val data = message.data ?: Bundle.EMPTY
			data.classLoader = TVBoxJarSpiderRequest::class.java.classLoader
			val request = data.getParcelableCompat<TVBoxJarSpiderRequest>(
				TVBoxJarSpiderWorkerProtocol.KEY_REQUEST,
			)
			if (request == null) {
				sendResponse(
					replyTo = replyTo,
					response = TVBoxJarSpiderResponse(
						errorCode = TVBoxJarSpiderWorkerProtocol.ERROR_EXECUTION,
						errorMessage = "TVBox spider worker received an empty request",
					),
				)
				scheduleShutdown()
				return
			}
			if (!requestAccepted.compareAndSet(false, true)) {
				sendResponse(
					replyTo = replyTo,
					response = TVBoxJarSpiderResponse(
						errorCode = TVBoxJarSpiderWorkerProtocol.ERROR_WORKER_SPENT,
						errorMessage = "TVBox spider worker already handled a request",
					),
				)
				scheduleShutdown()
				return
			}
			Log.i(
				TAG,
				"TVBox spider worker accepted request: source=${request.sourceDisplayName} action=${request.action} pid=${Process.myPid()}",
			)
			serviceScope.launch(Dispatchers.IO) {
				val httpClient = loadHttpClient()
				Log.i(TAG, "TVBox spider worker starting execution: source=${request.sourceDisplayName} action=${request.action}")
				val response = runCatching {
					TVBoxJarSpiderExecutor(
						context = applicationContext,
						httpClient = httpClient,
						request = request,
					).execute()
				}.getOrElse { error ->
					Log.w(TAG, "TVBox spider worker execution failed for ${request.sourceDisplayName}", error)
					TVBoxJarSpiderResponse(
						errorCode = TVBoxJarSpiderWorkerProtocol.ERROR_EXECUTION,
						errorMessage = error.message ?: error.toString(),
					)
				}
				Log.i(
					TAG,
					"TVBox spider worker finished execution: source=${request.sourceDisplayName} action=${request.action} code=${response.errorCode ?: "ok"} message=${response.errorMessage ?: "-"}",
				)
				sendResponse(replyTo, response)
				scheduleShutdown()
			}
		}
	}

	private fun sendResponse(replyTo: Messenger, response: TVBoxJarSpiderResponse) {
		runCatching {
			replyTo.send(
				Message.obtain(null, TVBoxJarSpiderWorkerProtocol.MESSAGE_RESULT).apply {
					data = Bundle(1).apply {
						putParcelable(TVBoxJarSpiderWorkerProtocol.KEY_RESPONSE, response)
					}
				},
			)
		}.onFailure { error ->
			Log.w(TAG, "Failed to send TVBox spider worker response", error)
		}
	}

	private fun scheduleShutdown() {
		if (!shutdownScheduled.compareAndSet(false, true)) {
			return
		}
		Log.i(TAG, "Scheduling TVBox spider worker shutdown: pid=${Process.myPid()} delayMs=$SHUTDOWN_DELAY_MS")
		mainHandler.postDelayed(shutdownRunnable, SHUTDOWN_DELAY_MS)
	}

	private fun loadHttpClient(): LegadoHttpClient {
		return EntryPointAccessors.fromApplication(
			applicationContext,
			TVBoxJarSpiderServiceEntryPoint::class.java,
		).legadoHttpClient
	}

	private fun currentProcessName(): String {
		return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
			Application.getProcessName()
		} else {
			runCatching {
				Class.forName("android.app.ActivityThread")
					.getDeclaredMethod("currentProcessName")
					.invoke(null) as? String
			}.getOrNull().orEmpty().ifBlank { applicationInfo.processName }
		}
	}
}
