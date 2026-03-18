package org.skepsun.kototoro.extensions.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import org.skepsun.kototoro.core.util.ext.goAsync

fun registerExternalExtensionPackageObserver(
	context: Context,
	onPackageChanged: suspend () -> Unit,
): BroadcastReceiver {
	val receiver = object : BroadcastReceiver() {
		override fun onReceive(context: Context?, intent: Intent?) {
			goAsync {
				onPackageChanged()
			}
		}
	}
	val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
		ContextCompat.RECEIVER_EXPORTED
	} else {
		0
	}
	ContextCompat.registerReceiver(
		context,
		receiver,
		IntentFilter().apply {
			addAction(Intent.ACTION_PACKAGE_ADDED)
			addAction(Intent.ACTION_PACKAGE_REPLACED)
			addAction(Intent.ACTION_PACKAGE_REMOVED)
			addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
			addDataScheme("package")
		},
		flags,
	)
	return receiver
}
