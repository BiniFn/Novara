package org.skepsun.kototoro.ireader.model

data class IReaderExtensionInfo(
    val pkgName: String,
    val appName: String,
    val versionCode: Long,
    val versionName: String,
    val libVersion: Double,
    val isNsfw: Boolean,
    val sourceClassName: String,
    val apkPath: String,
)
