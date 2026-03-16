package org.skepsun.kototoro

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.preference.PreferenceManager
import leakcanary.LeakCanary
import org.skepsun.kototoro.core.BaseApp

class KototoroApp : BaseApp(), SharedPreferences.OnSharedPreferenceChangeListener {

	override fun attachBaseContext(base: Context) {
		super.attachBaseContext(base)
		val prefs = PreferenceManager.getDefaultSharedPreferences(this)
		configureLeakCanary(isEnabled = prefs.getBoolean(KEY_LEAK_CANARY, false))
		prefs.registerOnSharedPreferenceChangeListener(this)
	}

	override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
		if (key == KEY_LEAK_CANARY) {
			configureLeakCanary(sharedPreferences.getBoolean(KEY_LEAK_CANARY, false))
		}
	}

	private fun configureLeakCanary(isEnabled: Boolean) {
		val manufacturer = Build.MANUFACTURER.lowercase()
		val brand = Build.BRAND.lowercase()
		val isOplusDevice = manufacturer.contains("oppo") ||
			manufacturer.contains("oneplus") ||
			manufacturer.contains("realme") ||
			brand.contains("oppo") ||
			brand.contains("oneplus") ||
			brand.contains("realme")
		LeakCanary.config = LeakCanary.config.copy(
			dumpHeap = isEnabled && !isOplusDevice,
		)
	}

	private companion object {

		const val KEY_LEAK_CANARY = "debug.leak_canary"
	}
}
