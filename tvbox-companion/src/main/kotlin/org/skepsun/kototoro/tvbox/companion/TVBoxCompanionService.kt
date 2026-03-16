package org.skepsun.kototoro.tvbox.companion

import android.app.Application
import android.app.Service
import android.content.Intent
import android.os.Build
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.skepsun.kototoro.core.network.jsonsource.LegadoHttpClient
import org.skepsun.kototoro.core.parser.tvbox.TVBoxJarSpiderExecutor
import org.skepsun.kototoro.tvbox.bridge.TVBoxJarSpiderRequest
import org.skepsun.kototoro.tvbox.bridge.TVBoxJarSpiderResponse
import org.skepsun.kototoro.tvbox.bridge.TVBoxJarSpiderWorkerProtocol
import java.util.concurrent.atomic.AtomicInteger

class TVBoxCompanionService : Service() {

	companion object {
		private const val TAG = "TVBoxCompanionSvc"
		private const val IDLE_SHUTDOWN_DELAY_MS = 60_000L
		private const val PRESTART_SHUTDOWN_DELAY_MS = 15_000L
	}

	private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
	private val mainHandler = Handler(Looper.getMainLooper())
	private val runningRequests = AtomicInteger(0)
	private val shutdownRunnable = Runnable {
		if (runningRequests.get() > 0) {
			Log.i(TAG, "Skipping TVBox companion shutdown because requests are still running: pid=${Process.myPid()} active=${runningRequests.get()}")
			scheduleShutdown()
			return@Runnable
		}
		val processName = currentProcessName()
		Log.i(TAG, "Stopping TVBox companion worker process: pid=${Process.myPid()} process=$processName")
		stopSelf()
		if (shouldKillProcess(processName)) {
			Process.killProcess(Process.myPid())
		}
	}
	private val messenger by lazy(LazyThreadSafetyMode.NONE) {
		Messenger(IncomingHandler())
	}
	private val selfStartIntent by lazy(LazyThreadSafetyMode.NONE) {
		Intent(this, TVBoxCompanionService::class.java).apply {
			setPackage(packageName)
			action = "org.skepsun.kototoro.action.TVBOX_COMPANION_KEEP_ALIVE"
		}
	}

	override fun onCreate() {
		super.onCreate()
		runCatching {
			MultiDex.install(this)
			Log.i(TAG, "Installed MultiDex for TVBox companion process: pid=${Process.myPid()}")
		}.onFailure {
			Log.w(TAG, "Failed to install MultiDex for TVBox companion process", it)
		}
		Log.i(TAG, "Initializing TVBox companion application bridge: pid=${Process.myPid()}")
		App.init(applicationContext)
		runCatching { OkGoHelper.init() }
			.onFailure { Log.w(TAG, "Failed to initialize TVBox OkGoHelper in companion process", it) }
		runCatching { QuickJSLoader.init() }
			.onFailure { Log.w(TAG, "Failed to initialize TVBox QuickJSLoader in companion process", it) }
		Log.i(TAG, "TVBox companion service created: pid=${Process.myPid()} process=${currentProcessName()}")
	}

	override fun onBind(intent: Intent?): IBinder {
		runCatching {
			startService(selfStartIntent)
		}.onFailure {
			Log.w(TAG, "Failed to mark TVBox companion as started service", it)
		}
		Log.i(TAG, "TVBox companion service bound: pid=${Process.myPid()} process=${currentProcessName()}")
		return messenger.binder
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		Log.i(
			TAG,
			"TVBox companion service started: pid=${Process.myPid()} process=${currentProcessName()} startId=$startId action=${intent?.action ?: "-"}",
		)
		scheduleShutdown(PRESTART_SHUTDOWN_DELAY_MS)
		return START_NOT_STICKY
	}

	override fun onDestroy() {
		mainHandler.removeCallbacks(shutdownRunnable)
		Log.i(
			TAG,
			"TVBox companion service destroyed: pid=${Process.myPid()} activeRequests=${runningRequests.get()}",
		)
		serviceScope.cancel()
		super.onDestroy()
	}

	override fun onUnbind(intent: Intent?): Boolean {
		Log.i(
			TAG,
			"TVBox companion service unbound: pid=${Process.myPid()} activeRequests=${runningRequests.get()}",
		)
		if (runningRequests.get() == 0) {
			scheduleShutdown()
		}
		return super.onUnbind(intent)
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
			val request = data.readRequest()
			if (request == null) {
				sendResponse(
					replyTo = replyTo,
					response = TVBoxJarSpiderResponse(
						errorCode = TVBoxJarSpiderWorkerProtocol.ERROR_EXECUTION,
						errorMessage = "TVBox companion received an empty request",
					),
				)
				scheduleShutdown()
				return
			}
			mainHandler.removeCallbacks(shutdownRunnable)
			val activeRequests = runningRequests.incrementAndGet()
			Log.i(
				TAG,
				"TVBox companion accepted request: source=${request.sourceDisplayName} action=${request.action} pid=${Process.myPid()} active=$activeRequests",
			)
			serviceScope.launch(Dispatchers.IO) {
				try {
					val response = runCatching {
						TVBoxJarSpiderExecutor(
							context = applicationContext,
							httpClient = LegadoHttpClient(applicationContext),
							request = request,
						).execute()
					}.getOrElse { error ->
						Log.w(TAG, "TVBox companion execution failed for ${request.sourceDisplayName}", error)
						TVBoxJarSpiderResponse(
							errorCode = TVBoxJarSpiderWorkerProtocol.ERROR_EXECUTION,
							errorMessage = error.message ?: error.toString(),
						)
					}
					Log.i(
						TAG,
						"TVBox companion finished execution: source=${request.sourceDisplayName} action=${request.action} code=${response.errorCode ?: "ok"} message=${response.errorMessage ?: "-"}",
					)
					sendResponse(replyTo, response)
				} finally {
					val remainingRequests = runningRequests.decrementAndGet().coerceAtLeast(0)
					runningRequests.set(remainingRequests)
					if (remainingRequests == 0) {
						scheduleShutdown()
					} else {
						Log.i(TAG, "Keeping TVBox companion alive because more requests are active: pid=${Process.myPid()} active=$remainingRequests")
					}
				}
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
			Log.w(TAG, "Failed to send TVBox companion response", error)
		}
	}

	private fun scheduleShutdown(delayMs: Long = IDLE_SHUTDOWN_DELAY_MS) {
		mainHandler.removeCallbacks(shutdownRunnable)
		Log.i(TAG, "Scheduling TVBox companion shutdown: pid=${Process.myPid()} delayMs=$delayMs active=${runningRequests.get()}")
		mainHandler.postDelayed(shutdownRunnable, delayMs)
	}

	private fun currentProcessName(): String {
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			Application.getProcessName()
		} else {
			runCatching {
				Class.forName("android.app.ActivityThread")
					.getDeclaredMethod("currentProcessName")
					.invoke(null) as? String
			}.getOrNull().orEmpty().ifBlank { applicationInfo.processName }
		}
	}

	private fun shouldKillProcess(processName: String): Boolean {
		return processName.isNotBlank() && processName != packageName
	}

	private fun Bundle.readRequest(): TVBoxJarSpiderRequest? {
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			getParcelable(TVBoxJarSpiderWorkerProtocol.KEY_REQUEST, TVBoxJarSpiderRequest::class.java)
		} else {
			@Suppress("DEPRECATION")
			getParcelable(TVBoxJarSpiderWorkerProtocol.KEY_REQUEST)
		}
	}
}
