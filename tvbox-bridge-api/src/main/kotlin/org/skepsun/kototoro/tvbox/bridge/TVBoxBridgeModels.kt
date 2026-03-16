package org.skepsun.kototoro.tvbox.bridge

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

object TVBoxJarSpiderWorkerProtocol {
	const val PROCESS_SUFFIX = ":tvbox_spider"

	const val MESSAGE_EXECUTE = 1
	const val MESSAGE_RESULT = 2

	const val KEY_REQUEST = "tvbox_request"
	const val KEY_RESPONSE = "tvbox_response"

	const val ACTION_HOME = "home"
	const val ACTION_HOME_VOD = "home_vod"
	const val ACTION_CATEGORY = "category"
	const val ACTION_SEARCH = "search"
	const val ACTION_DETAIL = "detail"
	const val ACTION_PLAY = "play"
	const val ACTION_PROXY = "proxy"

	const val ERROR_WORKER_SPENT = "worker_spent"
	const val ERROR_BIND_FAILED = "bind_failed"
	const val ERROR_TIMEOUT = "timeout"
	const val ERROR_DISCONNECTED = "disconnected"
	const val ERROR_FATAL_NATIVE_CRASH = "fatal_native_crash"
	const val ERROR_EXECUTION = "execution"
}

object TVBoxCompanionContract {
	const val PACKAGE_NAME = "com.github.tvbox.osc"
	const val ACTION_BIND_RUNTIME = "com.github.tvbox.osc.action.BIND_TVBOX_RUNTIME"
	const val BIND_PERMISSION = "org.skepsun.kototoro.permission.BIND_TVBOX_COMPANION"
	const val SERVICE_CLASS_NAME = "org.skepsun.kototoro.tvbox.companion.TVBoxCompanionService"
	const val BOOTSTRAP_ACTIVITY_CLASS_NAME = "org.skepsun.kototoro.tvbox.companion.TVBoxCompanionBootstrapActivity"
	const val BOOTSTRAP_AUTHORITY = "com.github.tvbox.osc.tvbox.bootstrap"
	const val BOOTSTRAP_METHOD_PING = "ping"
}

@Parcelize
data class TVBoxJarSpiderRequest(
	val sourceId: String,
	val sourceDisplayName: String,
	val sourceConfig: String,
	val action: String,
	val timeoutMs: Long,
	val categoryId: String? = null,
	val page: Int? = null,
	val query: String? = null,
	val itemId: String? = null,
	val flag: String? = null,
	val playId: String? = null,
	val proxySpec: String? = null,
	val queryParameters: Map<String, String> = emptyMap(),
	val headers: Map<String, String> = emptyMap(),
) : Parcelable

@Parcelize
data class TVBoxJarSpiderResponse(
	val payload: String? = null,
	val statusCode: Int = 200,
	val contentType: String = "application/octet-stream",
	val headers: Map<String, String> = emptyMap(),
	val body: ByteArray = ByteArray(0),
	val bodyFilePath: String? = null,
	val redirectUrl: String? = null,
	val errorCode: String? = null,
	val errorMessage: String? = null,
) : Parcelable {
	val isSuccess: Boolean
		get() = errorCode.isNullOrBlank()
}
