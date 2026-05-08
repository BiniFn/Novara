package org.skepsun.kototoro.extensions.runtime

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import java.io.File

object LocalApkExtensionSupport {

    private const val EXTENSIONS_DIR = "extensions"
    private const val LOAD_CACHE_DIR = "extension_apk_cache"

    fun getManagedExtensionsDir(context: Context, ecosystem: String): File {
        return File(File(context.filesDir, EXTENSIONS_DIR), ecosystem).apply { mkdirs() }
    }

    fun findLocalApkFiles(context: Context, ecosystem: String): List<File> {
        val root = getManagedExtensionsDir(context, ecosystem)
        val directApks = root.listFiles()
            ?.filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
            .orEmpty()
        val nestedApks = root.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { dir ->
                val preferred = File(dir, "${dir.name}.apk")
                when {
                    preferred.isFile -> preferred
                    else -> dir.listFiles()
                        ?.firstOrNull { it.isFile && it.extension.equals("apk", ignoreCase = true) }
                }
            }
            .orEmpty()
        return (directApks + nestedApks).distinctBy { it.absolutePath }
    }

    fun getLocalArchivePackages(
        context: Context,
        pkgManager: PackageManager,
        ecosystem: String,
    ): List<PackageInfo> {
        return findLocalApkFiles(context, ecosystem).mapNotNull { apkFile ->
            ExternalExtensionLoaderSupport.getPackageArchiveInfoOrNull(pkgManager, apkFile)
        }
    }

    fun getLocalArchivePackageInfoOrNull(
        context: Context,
        pkgManager: PackageManager,
        ecosystem: String,
        packageName: String,
    ): PackageInfo? {
        return getLocalArchivePackages(context, pkgManager, ecosystem)
            .firstOrNull { it.packageName == packageName }
    }

    fun prepareLoadableApkPath(
        context: Context,
        ecosystem: String,
        pkgName: String,
        sourcePath: String,
    ): String {
        val sourceFile = File(sourcePath)
        if (!sourceFile.exists()) {
            return sourcePath
        }
        val managedRoot = getManagedExtensionsDir(context, ecosystem)
        if (!sourceFile.isWithin(managedRoot)) {
            return sourcePath
        }
        val cacheRoot = File(context.codeCacheDir, LOAD_CACHE_DIR).apply { mkdirs() }
        val targetFile = File(cacheRoot, "$ecosystem-$pkgName.apk")
        sourceFile.copyTo(targetFile, overwrite = true)
        targetFile.setReadOnly()
        return targetFile.absolutePath
    }

    fun storeManagedApk(
        context: Context,
        ecosystem: String,
        packageName: String,
        sourceFile: File,
    ): File {
        val managedRoot = getManagedExtensionsDir(context, ecosystem)
        val targetDir = File(managedRoot, packageName).apply { mkdirs() }
        val targetFile = File(targetDir, "$packageName.apk")
        sourceFile.copyTo(targetFile, overwrite = true)
        targetFile.setReadOnly()
        return targetFile
    }

    fun isManagedLocalPackage(
        context: Context,
        ecosystem: String,
        sourcePath: String?,
    ): Boolean {
        if (sourcePath.isNullOrBlank()) {
            return false
        }
        return File(sourcePath).isWithin(getManagedExtensionsDir(context, ecosystem))
    }

    fun deleteManagedLocalPackage(
        context: Context,
        ecosystem: String,
        packageName: String,
    ): Boolean {
        val root = getManagedExtensionsDir(context, ecosystem)
        val directMatches = root.listFiles()
            ?.filter { it.isFile && it.extension.equals("apk", ignoreCase = true) && archiveNameMatches(it, packageName) }
            .orEmpty()
        val nestedDirs = root.listFiles()
            ?.filter { it.isDirectory && it.name.equals(packageName, ignoreCase = true) }
            .orEmpty()
        var deleted = false
        directMatches.forEach { deleted = it.delete() || deleted }
        nestedDirs.forEach { dir -> deleted = dir.deleteRecursively() || deleted }
        return deleted
    }

    private fun File.isWithin(root: File): Boolean {
        val filePath = runCatching { canonicalPath }.getOrElse { absolutePath }
        val rootPath = runCatching { root.canonicalPath }.getOrElse { root.absolutePath }
        return filePath.startsWith(rootPath)
    }

    private fun archiveNameMatches(file: File, packageName: String): Boolean {
        return file.nameWithoutExtension.equals(packageName, ignoreCase = true)
    }
}
