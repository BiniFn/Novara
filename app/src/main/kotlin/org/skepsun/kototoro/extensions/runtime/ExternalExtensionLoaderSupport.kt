package org.skepsun.kototoro.extensions.runtime

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

object ExternalExtensionLoaderSupport {

	@Suppress("DEPRECATION")
	val packageQueryFlags: Int = PackageManager.GET_CONFIGURATIONS or
		PackageManager.GET_META_DATA or
		PackageManager.GET_SIGNATURES or
		(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)

	val scanFlags: Int = PackageManager.GET_META_DATA or PackageManager.GET_CONFIGURATIONS

	fun getInstalledPackages(pkgManager: PackageManager): List<PackageInfo> {
		return try {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				pkgManager.getInstalledPackages(
					PackageManager.PackageInfoFlags.of(scanFlags.toLong()),
				)
			} else {
				@Suppress("DEPRECATION")
				pkgManager.getInstalledPackages(scanFlags)
			}
		} catch (e: Exception) {
			emptyList()
		}
	}

	fun getPackageInfoOrNull(pkgManager: PackageManager, packageName: String): PackageInfo? {
		return try {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				pkgManager.getPackageInfo(
					packageName,
					PackageManager.PackageInfoFlags.of(packageQueryFlags.toLong()),
				)
			} else {
				@Suppress("DEPRECATION")
				pkgManager.getPackageInfo(packageName, packageQueryFlags)
			}
		} catch (_: PackageManager.NameNotFoundException) {
			null
		}
	}

	fun getAppLabel(context: Context, appInfo: ApplicationInfo): String {
		return try {
			context.packageManager.getApplicationLabel(appInfo).toString()
		} catch (_: Exception) {
			appInfo.packageName.substringAfterLast('.')
		}
	}

	fun extractLanguage(pkgName: String, marker: String): String {
		val parts = pkgName.split(".")
		val markerIndex = parts.indexOf(marker)
		return if (markerIndex >= 0 && markerIndex + 1 < parts.size) {
			parts[markerIndex + 1]
		} else {
			"all"
		}
	}
}
