package org.skepsun.kototoro.extensions.runtime

import android.content.pm.ApplicationInfo
import android.os.Bundle

object ExternalExtensionMetadataSupport {

	data class DeclaredSourceMetadata(
		val sourceClassName: String,
		val isNsfw: Boolean,
	)

	fun getMetaDataOrNull(appInfo: ApplicationInfo?): Bundle? = appInfo?.metaData

	fun hasDeclaredSource(
		metaData: Bundle?,
		sourceClassKey: String,
		sourceFactoryKey: String,
	): Boolean {
		return metaData?.containsKey(sourceClassKey) == true ||
			metaData?.containsKey(sourceFactoryKey) == true
	}

	fun getSourceClassNameOrNull(
		metaData: Bundle,
		sourceClassKey: String,
		sourceFactoryKey: String,
	): String? {
		return metaData.getString(sourceClassKey)
			?: metaData.getString(sourceFactoryKey)
	}

	fun isNsfw(metaData: Bundle, nsfwKey: String): Boolean {
		return metaData.getInt(nsfwKey, 0) == 1
	}

	fun getDeclaredSourceMetadataOrNull(
		metaData: Bundle,
		sourceClassKey: String,
		sourceFactoryKey: String,
		nsfwKey: String,
	): DeclaredSourceMetadata? {
		val sourceClassName = getSourceClassNameOrNull(
			metaData = metaData,
			sourceClassKey = sourceClassKey,
			sourceFactoryKey = sourceFactoryKey,
		) ?: return null
		return DeclaredSourceMetadata(
			sourceClassName = sourceClassName,
			isNsfw = isNsfw(metaData, nsfwKey),
		)
	}
}
