package org.skepsun.kototoro.core.parser.tvbox

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import org.skepsun.kototoro.tvbox.bridge.TVBoxCompanionContract

internal object TVBoxCompanionAvailability {
	data class WarmUpResult(
		val success: Boolean,
		val detail: String,
	)

	fun companionIntent(): Intent {
		return Intent(TVBoxCompanionContract.ACTION_BIND_RUNTIME)
			.setComponent(
				ComponentName(
					TVBoxCompanionContract.PACKAGE_NAME,
					TVBoxCompanionContract.SERVICE_CLASS_NAME,
				),
			)
			.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
	}

	fun bootstrapActivityIntent(): Intent {
		return Intent().setComponent(
			ComponentName(
				TVBoxCompanionContract.PACKAGE_NAME,
				TVBoxCompanionContract.BOOTSTRAP_ACTIVITY_CLASS_NAME,
			),
		).addFlags(
			Intent.FLAG_ACTIVITY_NEW_TASK or
				Intent.FLAG_ACTIVITY_NO_ANIMATION or
				Intent.FLAG_INCLUDE_STOPPED_PACKAGES,
		)
	}

	data class State(
		val installed: Boolean,
		val component: ComponentName? = null,
		val exported: Boolean? = null,
		val permission: String? = null,
		val appEnabledSetting: Int? = null,
		val serviceEnabledSetting: Int? = null,
	)

	fun resolveService(context: Context): ComponentName? {
		return context.packageManager.resolveService(companionIntent(), 0)?.serviceInfo?.let { serviceInfo ->
			ComponentName(serviceInfo.packageName, serviceInfo.name)
		}
	}

	fun describeState(context: Context): String {
		val state = loadState(context)
		return buildString {
			append("installed=")
			append(state.installed)
			append(" resolved=")
			append(state.component?.flattenToShortString() ?: "null")
			append(" exported=")
			append(state.exported?.toString() ?: "null")
			append(" permission=")
			append(state.permission ?: "null")
			append(" appEnabled=")
			append(enabledSettingName(state.appEnabledSetting))
			append(" serviceEnabled=")
			append(enabledSettingName(state.serviceEnabledSetting))
		}
	}

	fun warmUp(context: Context): WarmUpResult {
		val packageManager = context.packageManager
		val provider = packageManager.resolveContentProvider(TVBoxCompanionContract.BOOTSTRAP_AUTHORITY, 0)
		if (provider == null) {
			return WarmUpResult(
				success = false,
				detail = "provider_unresolved",
			)
		}
		val uri = Uri.Builder()
			.scheme("content")
			.authority(TVBoxCompanionContract.BOOTSTRAP_AUTHORITY)
			.build()
		val bundle = runCatching {
			context.contentResolver.call(
				uri,
				TVBoxCompanionContract.BOOTSTRAP_METHOD_PING,
				null,
				null,
			)
		}.getOrElse { error ->
			return WarmUpResult(
				success = false,
				detail = "${error.javaClass.simpleName}: ${error.message ?: "unknown"}",
			)
		}
		if (bundle == null) {
			return WarmUpResult(
				success = false,
				detail = "null_bundle",
			)
		}
		return bundle.let { result ->
			val pid = result.getInt("pid", -1)
			val process = result.getString("process").orEmpty().ifBlank { "unknown" }
			WarmUpResult(
				success = pid > 0,
				detail = "pid=$pid process=$process",
			)
		}
	}

	private fun loadState(context: Context): State {
		val packageManager = context.packageManager
		val companionComponent = ComponentName(
			TVBoxCompanionContract.PACKAGE_NAME,
			TVBoxCompanionContract.SERVICE_CLASS_NAME,
		)
		val serviceInfo = runCatching {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				packageManager.getServiceInfo(
					companionComponent,
					PackageManager.ComponentInfoFlags.of(0),
				)
			} else {
				@Suppress("DEPRECATION")
				packageManager.getServiceInfo(companionComponent, 0)
			}
		}.getOrNull()
		val installed = serviceInfo != null || runCatching {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				packageManager.getPackageInfo(
					TVBoxCompanionContract.PACKAGE_NAME,
					PackageManager.PackageInfoFlags.of(0),
				)
			} else {
				@Suppress("DEPRECATION")
				packageManager.getPackageInfo(TVBoxCompanionContract.PACKAGE_NAME, 0)
			}
		}.isSuccess
		return State(
			installed = installed,
			component = resolveService(context),
			exported = serviceInfo?.exported,
			permission = serviceInfo?.permission,
			appEnabledSetting = runCatching {
				packageManager.getApplicationEnabledSetting(TVBoxCompanionContract.PACKAGE_NAME)
			}.getOrNull(),
			serviceEnabledSetting = runCatching {
				packageManager.getComponentEnabledSetting(companionComponent)
			}.getOrNull(),
		)
	}

	private fun enabledSettingName(setting: Int?): String {
		return when (setting) {
			null -> "null"
			PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> "default"
			PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> "enabled"
			PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> "disabled"
			PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER -> "disabled_user"
			PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> "disabled_until_used"
			else -> setting.toString()
		}
	}
}
