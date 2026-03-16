package org.skepsun.kototoro.tvbox.companion

import android.app.Activity
import android.os.Bundle
import android.os.Process
import android.util.Log
import com.github.tvbox.osc.base.App

class TVBoxCompanionBootstrapActivity : Activity() {

	companion object {
		private const val TAG = "TVBoxCompanionBoot"
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		App.init(applicationContext)
		Log.i(
			TAG,
			"TVBox companion bootstrap activity created: pid=${Process.myPid()}",
		)
		finish()
		overridePendingTransition(0, 0)
	}
}
