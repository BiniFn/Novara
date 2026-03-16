package org.skepsun.kototoro.core

import android.content.Context
import android.os.Build
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AppCompatDelegate
import androidx.room.InvalidationTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttp
import org.acra.ACRA
import org.acra.ReportField
import org.acra.config.dialog
import org.acra.config.httpSender
import org.acra.data.StringFormat
import org.acra.ktx.initAcra
import org.acra.sender.HttpSender
import org.conscrypt.Conscrypt
import org.skepsun.kototoro.BuildConfig
import org.skepsun.kototoro.R
import com.github.tvbox.osc.base.App
import org.skepsun.kototoro.core.db.MangaDatabase
import org.skepsun.kototoro.core.os.AppValidator
import org.skepsun.kototoro.core.os.RomCompat
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.util.ext.processLifecycleScope
import org.skepsun.kototoro.local.data.LocalStorageChanges
import org.skepsun.kototoro.local.data.index.LocalMangaIndex
import org.skepsun.kototoro.local.domain.model.LocalManga
import org.skepsun.kototoro.parsers.util.suspendlazy.getOrNull
import java.security.Security

open class BaseApp : App() {

	override fun onCreate() {
		super.onCreate()
		try {
			OkHttp.initialize(this)
		} catch (e: Throwable) {
			// Ignore initialization errors
		}
		if (ACRA.isACRASenderServiceProcess()) {
			return
		}
		AppCompatDelegate.setDefaultNightMode(settings.theme)
		// TLS 1.3 support for Android < 10
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
			try {
				Security.insertProviderAt(Conscrypt.newProvider(), 1)
			} catch (e: Throwable) {
				// Ignore
			}
		}
		setupActivityLifecycleCallbacks()
		processLifecycleScope.launch {
			runCatching {
				ACRA.errorReporter.putCustomData("isOriginalApp", appValidator.isOriginalApp.getOrNull().toString())
				ACRA.errorReporter.putCustomData("isMiui", RomCompat.isMiui.getOrNull().toString())
			}
		}
		processLifecycleScope.launch(Dispatchers.Default) {
			runCatching {
				setupDatabaseObservers()
				localStorageChanges.collect(localMangaIndexProvider.get())
			}
		}
		try {
			workScheduleManager.init()
		} catch (e: Throwable) {
			e.printStackTrace()
		}
		try {
			mihonExtensionManager.initialize()
		} catch (e: Throwable) {
			e.printStackTrace()
		}
		try {
			aniyomiExtensionManager.initialize()
		} catch (e: Throwable) {
			e.printStackTrace()
		}
	}

	override fun attachBaseContext(base: Context) {
		super.attachBaseContext(base)
		if (ACRA.isACRASenderServiceProcess()) {
			return
		}
		initAcra {
			buildConfigClass = BuildConfig::class.java
			reportFormat = StringFormat.JSON
			httpSender {
				uri = getString(R.string.url_error_report)
				basicAuthLogin = getString(R.string.acra_login)
				basicAuthPassword = getString(R.string.acra_password)
				httpMethod = HttpSender.Method.POST
			}
			reportContent = listOf(
				ReportField.PACKAGE_NAME,
				ReportField.INSTALLATION_ID,
				ReportField.APP_VERSION_CODE,
				ReportField.APP_VERSION_NAME,
				ReportField.ANDROID_VERSION,
				ReportField.PHONE_MODEL,
				ReportField.STACK_TRACE,
				ReportField.CRASH_CONFIGURATION,
				ReportField.CUSTOM_DATA,
			)

			dialog {
				text = getString(R.string.crash_text)
				title = getString(R.string.error_occurred)
				positiveButtonText = getString(R.string.send)
				resIcon = R.drawable.ic_alert_outline
				resTheme = android.R.style.Theme_Material_Light_Dialog_Alert
			}
		}
	}

	@WorkerThread
	private fun setupDatabaseObservers() {
		val tracker = database.get().invalidationTracker
		databaseObserversProvider.get().forEach {
			tracker.addObserver(it)
		}
	}

	private fun setupActivityLifecycleCallbacks() {
		activityLifecycleCallbacks.forEach {
			registerActivityLifecycleCallbacks(it)
		}
	}
}
