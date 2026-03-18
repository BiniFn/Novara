package org.skepsun.kototoro.core.util

import android.util.Log

fun logSyncFlow(tag: String, event: String, details: String = "") {
	Log.d(tag, buildString {
		append("sync_flow event=")
		append(event)
		if (details.isNotBlank()) {
			append(' ')
			append(details)
		}
	})
}

fun logBackupFlow(tag: String, flow: String, event: String, details: String = "") {
	Log.d(tag, buildString {
		append("backup_flow flow=")
		append(flow)
		append(" event=")
		append(event)
		if (details.isNotBlank()) {
			append(' ')
			append(details)
		}
	})
}
