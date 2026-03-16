package org.skepsun.kototoro.tvbox.companion

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Log
import org.skepsun.kototoro.tvbox.bridge.TVBoxCompanionContract

class TVBoxCompanionBootstrapProvider : ContentProvider() {

	companion object {
		private const val TAG = "TVBoxCompanionBoot"
	}

	override fun onCreate(): Boolean {
		Log.i(
			TAG,
			"TVBox companion bootstrap provider created: pid=${Process.myPid()} process=${currentProcessName()}",
		)
		return true
	}

	override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
		if (method == TVBoxCompanionContract.BOOTSTRAP_METHOD_PING) {
			return Bundle().apply {
				putInt("pid", Process.myPid())
				putString("process", currentProcessName())
			}
		}
		return super.call(method, arg, extras) ?: Bundle.EMPTY
	}

	override fun query(
		uri: Uri,
		projection: Array<out String>?,
		selection: String?,
		selectionArgs: Array<out String>?,
		sortOrder: String?,
	): Cursor? = null

	override fun getType(uri: Uri): String? = null

	override fun insert(uri: Uri, values: ContentValues?): Uri? = null

	override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

	override fun update(
		uri: Uri,
		values: ContentValues?,
		selection: String?,
		selectionArgs: Array<out String>?,
	): Int = 0

	private fun currentProcessName(): String {
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			Application.getProcessName()
		} else {
			runCatching {
				Class.forName("android.app.ActivityThread")
					.getDeclaredMethod("currentProcessName")
					.invoke(null) as? String
			}.getOrNull().orEmpty().ifBlank { context?.applicationInfo?.processName.orEmpty() }
		}
	}
}
