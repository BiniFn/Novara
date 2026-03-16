package org.skepsun.kototoro

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.StrictMode
import android.util.Log
import androidx.core.content.edit
import androidx.fragment.app.strictmode.FragmentStrictMode
import leakcanary.LeakCanary
import org.skepsun.kototoro.core.BaseApp

class KototoroApp : BaseApp() {

	var isLeakCanaryEnabled: Boolean
		get() = getDebugPreferences(this).getBoolean(KEY_LEAK_CANARY, false)
		set(value) {
			getDebugPreferences(this).edit { putBoolean(KEY_LEAK_CANARY, value) }
			configureLeakCanary()
		}

	override fun onCreate() {
		installUncaughtExceptionLogger()
		super.onCreate()
	}

	override fun attachBaseContext(base: Context) {
		super.attachBaseContext(base)
		enableStrictMode()
		configureLeakCanary()
	}

	private fun configureLeakCanary() {
		val isOplusDevice = isOplusFamilyDevice()
		LeakCanary.config = LeakCanary.config.copy(
			dumpHeap = isLeakCanaryEnabled && !isOplusDevice,
		)
	}

	private fun installUncaughtExceptionLogger() {
		val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
		if (previousHandler is DebugLoggingExceptionHandler) {
			return
		}
		Thread.setDefaultUncaughtExceptionHandler(
			DebugLoggingExceptionHandler(previousHandler),
		)
	}

	private fun isOplusFamilyDevice(): Boolean {
		val manufacturer = Build.MANUFACTURER.lowercase()
		val brand = Build.BRAND.lowercase()
		return manufacturer.contains("oppo") ||
			manufacturer.contains("oneplus") ||
			manufacturer.contains("realme") ||
			brand.contains("oppo") ||
			brand.contains("oneplus") ||
			brand.contains("realme")
	}

	private fun enableStrictMode() {
		val notifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			StrictModeNotifier(this)
		} else {
			null
		}
		// OPPO / OnePlus / realme 设备使用 Oplus UIFirst，会在主线程布局阶段写入 /proc 节点，触发 StrictMode 磁盘写入告警。
		// 这是厂商系统行为，不是应用代码问题。为避免无意义噪声，在这些设备上跳过 detectDiskWrites。
		val isOplusDevice = run {
			val m = Build.MANUFACTURER.lowercase()
			val b = Build.BRAND.lowercase()
			m.contains("oppo") || m.contains("oneplus") || m.contains("realme") ||
				b.contains("oppo") || b.contains("oneplus") || b.contains("realme")
		}
		StrictMode.setThreadPolicy(
			StrictMode.ThreadPolicy.Builder().apply {
				detectNetwork()
				if (!isOplusDevice) {
					detectDiskWrites()
				}
				detectCustomSlowCalls()
				detectResourceMismatches()
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) detectUnbufferedIo()
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) detectExplicitGc()
				penaltyLog()
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && notifier != null) {
					penaltyListener(notifier.executor, notifier)
				}
			}.build(),
		)
		StrictMode.setVmPolicy(
			StrictMode.VmPolicy.Builder().apply {
				detectActivityLeaks()
				detectLeakedSqlLiteObjects()
				detectLeakedClosableObjects()
				detectLeakedRegistrationObjects()
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
					detectContentUriWithoutPermission()
				}
				detectFileUriExposure()
				penaltyLog()
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && notifier != null) {
					penaltyListener(notifier.executor, notifier)
				}
			}.build(),
		)
		FragmentStrictMode.defaultPolicy = FragmentStrictMode.Policy.Builder().apply {
			detectWrongFragmentContainer()
			detectFragmentTagUsage()
			detectRetainInstanceUsage()
			detectSetUserVisibleHint()
			detectWrongNestedHierarchy()
			detectFragmentReuse()
			penaltyLog()
			if (notifier != null) {
				penaltyListener(notifier)
			}
		}.build()
	}

	private companion object {

		const val PREFS_DEBUG = "_debug"
		const val KEY_LEAK_CANARY = "leak_canary"
		private const val TAG = "KototoroFatal"

		fun getDebugPreferences(context: Context): SharedPreferences =
			context.getSharedPreferences(PREFS_DEBUG, MODE_PRIVATE)
	}

	private class DebugLoggingExceptionHandler(
		private val delegate: Thread.UncaughtExceptionHandler?,
	) : Thread.UncaughtExceptionHandler {

		override fun uncaughtException(thread: Thread, throwable: Throwable) {
			Log.e(TAG, "Uncaught exception on thread=${thread.name}", throwable)
			delegate?.uncaughtException(thread, throwable)
		}
	}
}
