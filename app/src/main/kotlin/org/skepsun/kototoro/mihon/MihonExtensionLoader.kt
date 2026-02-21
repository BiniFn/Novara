package org.skepsun.kototoro.mihon

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.skepsun.kototoro.mihon.compat.KotoInjektBridge
import org.skepsun.kototoro.mihon.model.MihonExtensionInfo
import org.skepsun.kototoro.mihon.model.MihonLoadResult
import org.skepsun.kototoro.mihon.util.ChildFirstPathClassLoader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loader for Mihon extension APKs.
 * 
 * Scans for installed Mihon extensions and loads their Source implementations.
 */
@Singleton
class MihonExtensionLoader @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    private val injektBridge: dagger.Lazy<KotoInjektBridge>,
) {
    companion object {
        private const val TAG = "MihonExtensionLoader"
        
        // Feature that marks an APK as a Mihon/Tachiyomi extension
        private const val EXTENSION_FEATURE = "tachiyomi.extension"
        
        // Metadata keys in AndroidManifest.xml
        private const val METADATA_SOURCE_CLASS = "tachiyomi.extension.class"
        private const val METADATA_SOURCE_FACTORY = "tachiyomi.extension.factory"
        private const val METADATA_NSFW = "tachiyomi.extension.nsfw"
        
        // Supported library version range
        const val LIB_VERSION_MIN = 1.2
        const val LIB_VERSION_MAX = 1.9
        
        // Package flags for querying extension info
        @Suppress("DEPRECATION")
        private val PACKAGE_FLAGS = PackageManager.GET_CONFIGURATIONS or
            PackageManager.GET_META_DATA or
            PackageManager.GET_SIGNATURES or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) 
                PackageManager.GET_SIGNING_CERTIFICATES else 0)
    }
    
    /**
     * Load all installed Mihon extensions.
     * 
     * @param context Android context
     * @return List of load results (success, error, or untrusted)
     */
    suspend fun loadExtensions(context: Context): List<MihonLoadResult> = withContext(Dispatchers.IO) {
        try {
            // Ensure Injekt is initialized before loading any extensions
            injektBridge.get().initialize()
            
            val pkgManager = context.packageManager
            
            // Get all installed packages
            val installedPkgs = getInstalledPackages(pkgManager)
            android.util.Log.d(TAG, "Filtering ${installedPkgs.size} packages...")
            
            // Filter to only extension packages
            val extPkgs = installedPkgs.filter { pkg ->
                val isExt = isPackageAnExtension(pkg)
                if (pkg.packageName.contains("coomer", ignoreCase = true)) {
                    android.util.Log.d(TAG, "!!! COOMER CHECK !!!: ${pkg.packageName}, isExt: $isExt")
                }
                isExt
            }
            
            if (extPkgs.isEmpty()) {
                android.util.Log.d(TAG, "No Mihon extensions found")
                return@withContext emptyList()
            }
            
            android.util.Log.d(TAG, "Found ${extPkgs.size} Mihon extension(s)")
            
            // Load extensions in parallel
            extPkgs.map { pkgInfo ->
                async { loadExtension(context, pkgInfo) }
            }.awaitAll()
        } catch (e: Throwable) {
            android.util.Log.e(TAG, "Failed to load extensions", e)
            emptyList()
        }
    }
    
    /**
     * Load a single Mihon extension by package name.
     */
    suspend fun loadExtension(context: Context, packageName: String): MihonLoadResult? = withContext(Dispatchers.IO) {
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
     * Get list of installed Mihon extensions (metadata only, without loading).
     */
    fun getInstalledExtensions(context: Context): List<MihonExtensionInfo> {
        val pkgManager = context.packageManager
        val installedPkgs = getInstalledPackages(pkgManager)
        
        return installedPkgs
            .filter { isPackageAnExtension(it) }
            .mapNotNull { extractExtensionInfo(it) }
    }
    
    /**
     * Package flags for initial scanning (lightweight)
     */
    private val SCAN_FLAGS = PackageManager.GET_META_DATA or PackageManager.GET_CONFIGURATIONS

    private fun getInstalledPackages(pkgManager: PackageManager): List<PackageInfo> {
        android.util.Log.d(TAG, "Querying installed packages...")
        return try {
            val pkgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pkgManager.getInstalledPackages(
                    PackageManager.PackageInfoFlags.of(SCAN_FLAGS.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pkgManager.getInstalledPackages(SCAN_FLAGS)
            }
            android.util.Log.d(TAG, "Found ${pkgs.size} total packages installed")
            pkgs
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error querying packages", e)
            emptyList()
        }
    }
    
    private fun isPackageAnExtension(pkgInfo: PackageInfo): Boolean {
        val pkgName = pkgInfo.packageName
        
        // Method 1: Check for explicit feature declaration
        val hasFeature = pkgInfo.reqFeatures?.any { it.name == EXTENSION_FEATURE } == true
        
        // Method 2: Check for package naming convention (optional but helps)
        val hasPackageName = pkgName.contains(".extension") || 
                            pkgName.startsWith("eu.kanade.tachiyomi.") ||
                            pkgName.startsWith("org.keiyoushi.")
        
        // Method 3: Check for metadata in application info
        val hasMetaData = pkgInfo.applicationInfo?.metaData?.containsKey(METADATA_SOURCE_CLASS) == true ||
                         pkgInfo.applicationInfo?.metaData?.containsKey(METADATA_SOURCE_FACTORY) == true
        
        // Mihon strictly checks for the feature, but we can be more inclusive
        val isExtension = hasFeature || (hasPackageName && hasMetaData)
        
        // Enhanced logging for debugging - LOG ALL POTENTIAL MATCHES
        val lowercasePkg = pkgName.lowercase()
        if (lowercasePkg.contains("coomer") || 
            lowercasePkg.contains("keiyoushi") ||
            lowercasePkg.contains("tachiyomi") ||
            lowercasePkg.contains("mihon") ||
            isExtension) {
            android.util.Log.d(TAG, "[SCAN] Package: $pkgName")
            android.util.Log.d(TAG, "  - isExtension: $isExtension (feature=$hasFeature, name=$hasPackageName, meta=$hasMetaData)")
            
            if (lowercasePkg.contains("coomer")) {
                val metaData = pkgInfo.applicationInfo?.metaData
                val keys = metaData?.keySet()?.joinToString(", ") ?: "null"
                android.util.Log.d(TAG, "  - $pkgName Metadata Keys: $keys")
                
                val features = pkgInfo.reqFeatures?.map { it.name }?.joinToString(", ") ?: "null"
                android.util.Log.d(TAG, "  - $pkgName Features: $features")
            }
        }
        
        if (isExtension) {
            android.util.Log.d(TAG, "Package $pkgName recognized as extension (feature: $hasFeature, name: $hasPackageName, meta: $hasMetaData)")
        }
        
        return isExtension
    }
    
    private fun extractExtensionInfo(pkgInfo: PackageInfo): MihonExtensionInfo? {
        val appInfo = pkgInfo.applicationInfo ?: return null
        val metaData = appInfo.metaData ?: return null
        
        val versionName = pkgInfo.versionName ?: return null
        
        // Extract library version - handles different version formats
        val libVersion = try {
            versionName.split('.').let { parts ->
                if (parts.size >= 2) "${parts[0]}.${parts[1]}".toDouble()
                else parts[0].toDouble()
            }
        } catch (e: Exception) {
            1.4 // Default to 1.4 if parsing fails
        }
        
        val sourceClassName = metaData.getString(METADATA_SOURCE_CLASS)
            ?: metaData.getString(METADATA_SOURCE_FACTORY)
            ?: return null
        
        // Get app name safely
        val appName = try {
            applicationContext.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            null
        } ?: pkgInfo.packageName.substringAfterLast('.')
        
        val lang = extractLanguage(pkgInfo.packageName)
        
        return MihonExtensionInfo(
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
    
    private fun loadExtension(context: Context, pkgInfo: PackageInfo): MihonLoadResult {
        val pkgName = pkgInfo.packageName
        val appInfo = pkgInfo.applicationInfo
            ?: return MihonLoadResult.Error(pkgName, "No ApplicationInfo")
        
        val versionName = pkgInfo.versionName
            ?: return MihonLoadResult.Error(pkgName, "No version name")
        val versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo)
        
        // Extract library version - match Mihon's logic (substringBeforeLast)
        val libVersion = versionName.substringBeforeLast('.').toDoubleOrNull()
            ?: try {
                versionName.split('.').let { parts ->
                    if (parts.size >= 2) "${parts[0]}.${parts[1]}".toDouble()
                    else parts[0].toDouble()
                }
            } catch (e: Exception) {
                return MihonLoadResult.Error(pkgName, "Invalid lib version format: $versionName")
            }
        
        // Check library version compatibility
        if (libVersion < LIB_VERSION_MIN || libVersion > LIB_VERSION_MAX) {
            return MihonLoadResult.Error(
                pkgName, 
                "Incompatible lib version: $libVersion (supported: $LIB_VERSION_MIN-$LIB_VERSION_MAX)"
            )
        }
        
        val metaData = appInfo.metaData
            ?: return MihonLoadResult.Error(pkgName, "No meta-data in manifest")
        
        // Get source class name(s)
        val sourceClassName = metaData.getString(METADATA_SOURCE_CLASS)
            ?: metaData.getString(METADATA_SOURCE_FACTORY)
            ?: return MihonLoadResult.Error(pkgName, "No source class specified in manifest")
        
        val isNsfw = metaData.getInt(METADATA_NSFW, 0) == 1
        
        // Get app name and language
        val appName = getAppLabel(context, appInfo)
        val lang = extractLanguage(pkgName)
        
        // Create ClassLoader for this extension
        val classLoader = try {
            ChildFirstPathClassLoader(
                appInfo.sourceDir,
                appInfo.nativeLibraryDir,
                context.classLoader
            )
        } catch (e: Throwable) {
            return MihonLoadResult.Error(pkgName, "Failed to create ClassLoader", e)
        }
        
        // Load source classes
        val sources = try {
            loadSources(pkgName, sourceClassName, classLoader)
        } catch (e: Throwable) {
            android.util.Log.e(TAG, "Failed to load sources from $pkgName", e)
            return MihonLoadResult.Error(pkgName, "Failed to load sources: ${e.message}", e)
        }
        
        android.util.Log.d(TAG, "Loaded ${sources.size} source(s) from $pkgName")
        
        return MihonLoadResult.Success(
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
    ): List<Source> {
        return sourceClassNames.split(";").flatMap { className ->
            val fullClassName = if (className.trim().startsWith(".")) {
                pkgName + className.trim()
            } else {
                className.trim()
            }
            
            android.util.Log.d(TAG, "Loading class: $fullClassName")
            
            val clazz = Class.forName(fullClassName, false, classLoader)
            val instance = clazz.getDeclaredConstructor().newInstance()
            
            when (instance) {
                is Source -> listOf(instance)
                is SourceFactory -> instance.createSources()
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
        // Package names are typically like: eu.kanade.tachiyomi.extension.en.mangadex
        // We want to extract the language code (en)
        val parts = pkgName.split(".")
        val extensionIndex = parts.indexOf("extension")
        return if (extensionIndex >= 0 && extensionIndex + 1 < parts.size) {
            parts[extensionIndex + 1]
        } else {
            "all"
        }
    }
}
