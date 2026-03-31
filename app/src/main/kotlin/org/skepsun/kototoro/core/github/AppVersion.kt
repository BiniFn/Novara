package org.skepsun.kototoro.core.github

import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class AppVersion(
	val id: Long,
	val name: String,
	val url: String,
	val apkSize: Long,
	val apkUrl: String,
	val patchSize: Long? = null,
	val patchUrl: String? = null,
	val description: String,
) : Parcelable {

	@IgnoredOnParcel
	val versionId = VersionId(name)
}
