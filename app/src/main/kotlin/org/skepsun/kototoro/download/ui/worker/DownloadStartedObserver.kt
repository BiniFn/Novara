package org.skepsun.kototoro.download.ui.worker

import android.view.View
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.FlowCollector
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.util.ext.findActivity
import org.skepsun.kototoro.core.util.ext.getThemeColor
import org.skepsun.kototoro.main.ui.owners.BottomNavOwner

class DownloadStartedObserver(
	private val snackbarHost: View,
) : FlowCollector<Unit> {

	override suspend fun emit(value: Unit) {
		val snackbar = Snackbar.make(snackbarHost, R.string.download_started, Snackbar.LENGTH_LONG)
		(snackbarHost.context.findActivity() as? BottomNavOwner)?.let {
			snackbar.anchorView = it.bottomNav
		}
		val router = AppRouter.from(snackbarHost)
		if (router != null) {
			snackbar.setAction(R.string.details) { router.openDownloads() }
			snackbar.setActionTextColor(
				snackbarHost.context.getThemeColor(androidx.appcompat.R.attr.colorPrimary),
			)
		}
		snackbar.show()
	}
}
