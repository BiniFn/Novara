package org.skepsun.kototoro.aniyomi

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.skepsun.kototoro.aniyomi.compat.KotoAniyomiInjektBridge
import org.skepsun.kototoro.aniyomi.model.AniyomiExtensionInfo
import org.skepsun.kototoro.aniyomi.model.AniyomiLoadResult
import org.skepsun.kototoro.aniyomi.util.ChildFirstPathClassLoader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loader for Aniyomi extension APKs.
 * 
 * Scans for installed Aniyomi extensions and loads their AnimeSource implementations.
 */
@Singleton
class AniyomiExtensionLoader @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    private val injektBridge: dagger.Lazy<KotoAniyomiInjektBridge>,
) {
    companion object {
        private const val TAG = "AniyomiExtensionLoader"
        
        // Feature that marks an APK as an Aniyomi anime extension
        private const val EXTENSION_FEATURE = "tachiyomi.animeextension"
        
        // Metadata keys in AndroidManifest.xml
        private const val METADATA_SOURCE_CLASS = "tachiyomi.animeextension.class"
        private const val METADATA_SOURCE_FACTORY = "tachiyomi.animeextension.factory"
        private const val METADATA_NSFW = "tachiyomi.animeextension.nsfw"
        
        // Supported library version range for Aniyomi
        const val LIB_VERSION_MIN = 12.0
        const val LIB_VERSION_MAX = 16.0
        
        // Package flags for querying extension info
        @Suppress("DEPRECATION")
        private val PACKAGE_FLAGS = PackageManager.GET_CONFIGURATIONS or
            PackageManager.GET_META_DATA or
            PackageManager.GET_SIGNATURES or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) 
                PackageManager.GET_SIGNING_CERTIFICATES else 0)
    }
    
    /**
     * Load all installed Aniyomi extensions.
     */
    suspend fun loadExtensions(context: Context): List<AniyomiLoadResult> = withContext(Dispatchers.IO) {
        // Ensure Injekt is initialized before loading any extensions
        injektBridge.get().initialize()
        
        val pkgManager = context.packageManager
        
        // Get all installed packages
        val installedPkgs = getInstalledPackages(pkgManager)
        android.util.Log.d(TAG, "Filtering ${installedPkgs.size} packages...")
        
        // Filter to only extension packages
        val extPkgs = installedPkgs.filter { isPackageAnExtension(it) }
        
        if (extPkgs.isEmpty()) {
            android.util.Log.d(TAG, "No Aniyomi extensions found")
            return@withContext emptyList()
        }
        
        android.util.Log.d(TAG, "Found ${extPkgs.size} Aniyomi extension(s)")
        
        // Load extensions in parallel
        extPkgs.map { pkgInfo ->
            async { loadExtension(context, pkgInfo) }
        }.awaitAll()
    }
    
    /**
     * Load a single Aniyomi extension by package name.
     */
    suspend fun loadExtension(context: Context, packageName: String): AniyomiLoadResult? = withContext(Dispatchers.IO) {
        injektBridge.get().initialize()
        
        val pkgManager = context.packageManager
        val pkgInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pkgManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PACKAGE_FLAGS.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pkgManager.getPackageInfo(packageName, PACKAGE_FLAGS)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            return@withContext null
        }
        
        if (!isPackageAnExtension(pkgInfo)) {
            return@withContext null
        }
        
        loadExtension(context, pkgInfo)
    }
    
    /**
     * Get list of installed Aniyomi extensions (metadata only, without loading).
     */
    fun getInstalledExtensions(context: Context): List<AniyomiExtensionInfo> {
        val pkgManager = context.packageManager
        val installedPkgs = getInstalledPackages(pkgManager)
        
        return installedPkgs
            .filter { isPackageAnExtension(it) }
            .mapNotNull { extractExtensionInfo(it) }
    }
    
    private val SCAN_FLAGS = PackageManager.GET_META_DATA or PackageManager.GET_CONFIGURATIONS

    private fun getInstalledPackages(pkgManager: PackageManager): List<PackageInfo> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pkgManager.getInstalledPackages(
                    PackageManager.PackageInfoFlags.of(SCAN_FLAGS.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pkgManager.getInstalledPackages(SCAN_FLAGS)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error querying packages", e)
            emptyList()
        }
    }
    
    private fun isPackageAnExtension(pkgInfo: PackageInfo): Boolean {
        val pkgName = pkgInfo.packageName
        
        // Method 1: Check for explicit feature declaration
        val hasFeature = pkgInfo.reqFeatures?.any { it.name == EXTENSION_FEATURE } == true
        
        // Method 2: Check for package naming convention
        val hasPackageName = pkgName.startsWith("eu.kanade.tachiyomi.animeextension.")
        
        // Method 3: Check for metadata in application info
        val hasMetaData = pkgInfo.applicationInfo?.metaData?.containsKey(METADATA_SOURCE_CLASS) == true ||
                         pkgInfo.applicationInfo?.metaData?.containsKey(METADATA_SOURCE_FACTORY) == true
        
        return hasFeature || (hasPackageName && hasMetaData)
    }
    
    private fun extractExtensionInfo(pkgInfo: PackageInfo): AniyomiExtensionInfo? {
        val appInfo = pkgInfo.applicationInfo ?: return null
        val metaData = appInfo.metaData ?: return null
        
        val versionName = pkgInfo.versionName ?: return null
        
        // Aniyomi lib version is usually the first part of the version name
        val libVersion = try {
            versionName.substringBefore('.').toDoubleOrNull() ?: 12.0
        } catch (e: Exception) {
            12.0
        }
        
        val sourceClassName = metaData.getString(METADATA_SOURCE_CLASS)
            ?: metaData.getString(METADATA_SOURCE_FACTORY)
            ?: return null
        
        val appName = getAppLabel(applicationContext, appInfo)
        val lang = extractLanguage(pkgInfo.packageName)
        
        return AniyomiExtensionInfo(
            pkgName = pkgInfo.packageName,
            appName = appName,
            versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo),
            versionName = versionName,
            libVersion = libVersion,
            lang = lang,
            isNsfw = metaData.getInt(METADATA_NSFW, 0) == 1,
            sourceClassName = sourceClassName,
            apkPath = appInfo.sourceDir ?: return null,
        )
    }
    
    private fun loadExtension(context: Context, pkgInfo: PackageInfo): AniyomiLoadResult {
        val pkgName = pkgInfo.packageName
        val appInfo = pkgInfo.applicationInfo
            ?: return AniyomiLoadResult.Error(pkgName, "No ApplicationInfo")
        
        val versionName = pkgInfo.versionName
            ?: return AniyomiLoadResult.Error(pkgName, "No version name")
        val versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo)
        
        val libVersion = versionName.substringBefore('.').toDoubleOrNull()
            ?: return AniyomiLoadResult.Error(pkgName, "Invalid lib version format: $versionName")
        
        if (libVersion < LIB_VERSION_MIN || libVersion > LIB_VERSION_MAX) {
            return AniyomiLoadResult.Error(
                pkgName, 
                "Incompatible lib version: $libVersion (supported: $LIB_VERSION_MIN-$LIB_VERSION_MAX)"
            )
        }
        
        val metaData = appInfo.metaData
            ?: return AniyomiLoadResult.Error(pkgName, "No meta-data in manifest")
        
        val sourceClassName = metaData.getString(METADATA_SOURCE_CLASS)
            ?: metaData.getString(METADATA_SOURCE_FACTORY)
            ?: return AniyomiLoadResult.Error(pkgName, "No source class specified in manifest")
        
        val isNsfw = metaData.getInt(METADATA_NSFW, 0) == 1
        val appName = getAppLabel(context, appInfo)
        val lang = extractLanguage(pkgName)
        
        val classLoader = try {
            val apkFile = java.io.File(appInfo.sourceDir)
            android.util.Log.i(TAG, "Loading extension $pkgName from ${appInfo.sourceDir} (exists=${apkFile.exists()}, readable=${apkFile.canRead()}, size=${apkFile.length()})")
            
            if (!apkFile.exists() || !apkFile.canRead()) {
                android.util.Log.e(TAG, "Extension APK file is missing or not readable!")
            }

            ChildFirstPathClassLoader(
                appInfo.sourceDir,
                appInfo.nativeLibraryDir,
                context.classLoader
            )
        } catch (e: Exception) {
            return AniyomiLoadResult.Error(pkgName, "Failed to create ClassLoader", e)
        }
        
        val sources = try {
            loadSources(pkgName, sourceClassName, classLoader)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to load sources from $pkgName", e)
            return AniyomiLoadResult.Error(pkgName, "Failed to load sources: ${e.message}", e)
        }
        
        return AniyomiLoadResult.Success(
            pkgName = pkgName,
            appName = appName,
            versionCode = versionCode,
            versionName = versionName,
            libVersion = libVersion,
            lang = lang,
            isNsfw = isNsfw,
            sources = sources,
        )
    }
    
    private fun loadSources(
        pkgName: String,
        sourceClassNames: String,
        classLoader: ClassLoader,
    ): List<AnimeSource> {
        return sourceClassNames.split(";").flatMap { className ->
            val fullClassName = if (className.trim().startsWith(".")) {
                pkgName + className.trim()
            } else {
                className.trim()
            }
            
            val clazz = Class.forName(fullClassName, false, classLoader)
            val instance = clazz.getDeclaredConstructor().newInstance()
            
            when (instance) {
                is AnimeSource -> listOf(instance)
                is AnimeSourceFactory -> instance.createSources()
                else -> {
                    android.util.Log.w(TAG, "Unknown instance type: ${instance.javaClass.name}")
                    emptyList()
                }
            }
        }
    }
    
    private fun getAppLabel(context: Context, appInfo: ApplicationInfo): String {
        return try {
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            appInfo.packageName.substringAfterLast('.')
        }
    }
    
    private fun extractLanguage(pkgName: String): String {
        val parts = pkgName.split(".")
        val extensionIndex = parts.indexOf("animeextension")
        return if (extensionIndex >= 0 && extensionIndex + 1 < parts.size) {
            parts[extensionIndex + 1]
        } else {
            "all"
        }
    }
}
