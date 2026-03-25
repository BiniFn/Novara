package org.skepsun.kototoro.mihon

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.skepsun.kototoro.extensions.runtime.ExternalExtensionLoaderSupport
import org.skepsun.kototoro.extensions.runtime.ExternalExtensionMetadataSupport
import org.skepsun.kototoro.extensions.runtime.ExternalExtensionSourceLoaderSupport
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
            val installedPkgs = ExternalExtensionLoaderSupport.getInstalledPackages(pkgManager)
            android.util.Log.d(TAG, "Filtering ${installedPkgs.size} packages...")
            
            // Filter to only extension packages
            val extPkgs = installedPkgs.map { pkgInfo ->
                ExternalExtensionLoaderSupport.refreshPackageInfoIfNeeded(pkgManager, pkgInfo)
            }.filter { pkg: PackageInfo ->
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
            extPkgs.map { pkgInfo: PackageInfo ->
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
        val pkgInfo = ExternalExtensionLoaderSupport.getPackageInfoOrNull(pkgManager, packageName)
            ?: return@withContext null
        
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
        val installedPkgs = ExternalExtensionLoaderSupport.getInstalledPackages(pkgManager)
        
        return installedPkgs
            .map { ExternalExtensionLoaderSupport.refreshPackageInfoIfNeeded(pkgManager, it) }
            .filter { isPackageAnExtension(it) }
            .mapNotNull { extractExtensionInfo(it) }
    }
    
    private fun isPackageAnExtension(pkgInfo: PackageInfo): Boolean {
        val pkgName = pkgInfo.packageName
        
        // Method 1: Check for explicit feature declaration
        val hasFeature = pkgInfo.reqFeatures?.any { it.name == EXTENSION_FEATURE } == true
        
        // Method 2: Check for package naming convention (optional but helps)
        val hasPackageName = ExternalExtensionLoaderSupport.looksLikeMihonPackage(pkgName)
        
        // Method 3: Check for metadata in application info
        val hasMetaData = ExternalExtensionMetadataSupport.hasDeclaredSource(
            metaData = pkgInfo.applicationInfo?.metaData,
            sourceClassKey = METADATA_SOURCE_CLASS,
            sourceFactoryKey = METADATA_SOURCE_FACTORY,
        )
        
        // Mihon strictly checks for the feature, but we can be more inclusive
        val isExtension = hasFeature || hasMetaData || hasPackageName
        
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
        val completePkgInfo = ExternalExtensionLoaderSupport.refreshPackageInfoIfNeeded(
            applicationContext.packageManager,
            pkgInfo,
        )
        val appInfo = completePkgInfo.applicationInfo ?: return null
        val metaData = ExternalExtensionMetadataSupport.getMetaDataOrNull(appInfo) ?: return null
        
        val versionName = completePkgInfo.versionName ?: return null
        
        // Extract library version - handles different version formats
        val libVersion = try {
            versionName.split('.').let { parts ->
                if (parts.size >= 2) "${parts[0]}.${parts[1]}".toDouble()
                else parts[0].toDouble()
            }
        } catch (e: Exception) {
            1.4 // Default to 1.4 if parsing fails
        }
        
        val declaredSource = ExternalExtensionMetadataSupport.getDeclaredSourceMetadataOrNull(
            metaData = metaData,
            sourceClassKey = METADATA_SOURCE_CLASS,
            sourceFactoryKey = METADATA_SOURCE_FACTORY,
            nsfwKey = METADATA_NSFW,
        )
            ?: return null
        
        // Get app name safely
        val appName = try {
            ExternalExtensionLoaderSupport.getAppLabel(applicationContext, appInfo)
        } catch (e: Exception) {
            null
        } ?: pkgInfo.packageName.substringAfterLast('.')
        
        val lang = ExternalExtensionLoaderSupport.extractLanguage(completePkgInfo.packageName, "extension")
        
        return MihonExtensionInfo(
            pkgName = completePkgInfo.packageName,
            appName = appName,
            versionCode = PackageInfoCompat.getLongVersionCode(completePkgInfo),
            versionName = versionName,
            libVersion = libVersion,
            lang = lang,
            isNsfw = declaredSource.isNsfw,
            sourceClassName = declaredSource.sourceClassName,
            apkPath = appInfo.sourceDir ?: return null,
        )
    }
    
    private fun loadExtension(context: Context, pkgInfo: PackageInfo): MihonLoadResult {
        val completePkgInfo = ExternalExtensionLoaderSupport.refreshPackageInfoIfNeeded(
            context.packageManager,
            pkgInfo,
        )
        val pkgName = completePkgInfo.packageName
        val appInfo = completePkgInfo.applicationInfo
            ?: return MihonLoadResult.Error(pkgName, "No ApplicationInfo")
        
        val versionName = completePkgInfo.versionName
            ?: return MihonLoadResult.Error(pkgName, "No version name")
        val versionCode = PackageInfoCompat.getLongVersionCode(completePkgInfo)
        
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
        
        val metaData = ExternalExtensionMetadataSupport.getMetaDataOrNull(appInfo)
            ?: return MihonLoadResult.Error(pkgName, "No meta-data in manifest")
        
        // Get source class name(s)
        val declaredSource = ExternalExtensionMetadataSupport.getDeclaredSourceMetadataOrNull(
            metaData = metaData,
            sourceClassKey = METADATA_SOURCE_CLASS,
            sourceFactoryKey = METADATA_SOURCE_FACTORY,
            nsfwKey = METADATA_NSFW,
        )
            ?: return MihonLoadResult.Error(pkgName, "No source class specified in manifest")
        
        // Get app name and language
        val appName = ExternalExtensionLoaderSupport.getAppLabel(context, appInfo)
        val lang = ExternalExtensionLoaderSupport.extractLanguage(pkgName, "extension")
        
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
            loadSources(pkgName, declaredSource.sourceClassName, classLoader)
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
            isNsfw = declaredSource.isNsfw,
            sources = sources,
        )
    }
    
    private fun loadSources(
        pkgName: String,
        sourceClassNames: String,
        classLoader: ClassLoader,
    ): List<Source> {
        return ExternalExtensionSourceLoaderSupport.loadSources(
            pkgName = pkgName,
            sourceClassNames = sourceClassNames,
            classLoader = classLoader,
            asSource = { it as? Source },
            createSourcesFromFactory = { (it as? SourceFactory)?.createSources() },
            onUnknownInstance = { className ->
                android.util.Log.w(TAG, "Unknown instance type: $className")
            },
        )
    }
    
}
