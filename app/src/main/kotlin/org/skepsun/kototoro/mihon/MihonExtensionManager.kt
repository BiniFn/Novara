package org.skepsun.kototoro.mihon

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.skepsun.kototoro.extensions.runtime.processExternalExtensionResults
import org.skepsun.kototoro.extensions.runtime.registerExternalExtensionPackageObserver
import org.skepsun.kototoro.mihon.model.MihonLoadResult
import org.skepsun.kototoro.mihon.model.MihonMangaSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for Mihon extensions.
 * 
 * Handles loading, caching, and providing access to Mihon extension sources.
 */
@Singleton
class MihonExtensionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val loader: MihonExtensionLoader,
) {
    companion object {
        private const val TAG = "MihonExtensionManager"
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Loaded extensions
    private val _installedExtensions = MutableStateFlow<List<MihonLoadResult.Success>>(emptyList())
    val installedExtensions: StateFlow<List<MihonLoadResult.Success>> = _installedExtensions.asStateFlow()
    
    // Failed extensions
    private val _failedExtensions = MutableStateFlow<List<MihonLoadResult.Error>>(emptyList())
    val failedExtensions: StateFlow<List<MihonLoadResult.Error>> = _failedExtensions.asStateFlow()
    
    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // Cache of source ID -> Source
    private val sourceCache = mutableMapOf<Long, Source>()
    
    // Cache of source ID -> MihonMangaSource wrapper
    private val mangaSourceCache = mutableMapOf<Long, MihonMangaSource>()

    @Volatile
    private var isPackageObserverRegistered = false
    
    /**
     * Initialize the extension manager and load all extensions.
     */
    fun initialize() {
        registerPackageObserver()
        scope.launch {
            loadExtensions()
        }
    }
    
    /**
     * Reload all extensions.
     */
    suspend fun loadExtensions() {
        if (_isLoading.value) return
        
        _isLoading.value = true
        
        try {
            android.util.Log.d(TAG, "load_start ecosystem=mihon")

            sourceCache.clear()
            mangaSourceCache.clear()
            val processed = processExternalExtensionResults(
                results = loader.loadExtensions(context),
                successOf = { it as? MihonLoadResult.Success },
                errorOf = { it as? MihonLoadResult.Error },
                untrustedPackageNameOf = { (it as? MihonLoadResult.Untrusted)?.pkgName },
                successSources = { it.sources },
                successPackageName = { it.pkgName },
                successIsNsfw = { it.isNsfw },
                sourceId = { it.id },
                asCatalogueSource = { it as? CatalogueSource },
                catalogueSourceName = { it.name },
                buildWrappedSource = { catalogueSource, pkgName, isNsfw, hasLanguageSuffix ->
                    MihonMangaSource(
                        catalogueSource = catalogueSource,
                        pkgName = pkgName,
                        isNsfw = isNsfw,
                        hasLanguageSuffix = hasLanguageSuffix,
                    )
                },
                onError = { error ->
                    android.util.Log.e(TAG, "load_error ecosystem=mihon pkg=${error.pkgName} message=${error.message}")
                },
                onUntrusted = { pkgName ->
                    android.util.Log.w(TAG, "load_untrusted ecosystem=mihon pkg=$pkgName")
                },
            )
            sourceCache.putAll(processed.sourceById)
            mangaSourceCache.putAll(processed.wrappedSourceById)
            _installedExtensions.value = processed.successful
            _failedExtensions.value = processed.failed
            android.util.Log.d(
                TAG,
                "load_complete ecosystem=mihon success=${processed.successful.size} failed=${processed.failed.size} untrusted=${processed.untrustedPackages.size} sources=${processed.wrappedSourceById.size}",
            )
            
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Get all available CatalogueSource instances.
     */
    fun getCatalogueSources(): List<CatalogueSource> {
        return _installedExtensions.value.flatMap { it.catalogueSources }
    }
    
    /**
     * Get all MihonMangaSource wrappers.
     */
    fun getMihonMangaSources(): List<MihonMangaSource> {
        return mangaSourceCache.values.toList()
    }
    
    /**
     * Get a source by its ID.
     */
    fun getSourceById(sourceId: Long): Source? {
        return sourceCache[sourceId]
    }
    
    /**
     * Get a CatalogueSource by its ID.
     */
    fun getCatalogueSourceById(sourceId: Long): CatalogueSource? {
        return sourceCache[sourceId] as? CatalogueSource
    }
    
    /**
     * Get a MihonMangaSource wrapper by source ID.
     */
    fun getMihonMangaSourceById(sourceId: Long): MihonMangaSource? {
        return mangaSourceCache[sourceId]
    }
    
    /**
     * Get a MihonMangaSource by its name (format: "MIHON_{sourceId}").
     */
    fun getMihonMangaSourceByName(name: String): MihonMangaSource? {
        if (!name.startsWith("MIHON_")) return null
        val sourceId = name.substringAfter("MIHON_").toLongOrNull() ?: return null
        return getMihonMangaSourceById(sourceId)
    }
    
    /**
     * Get sources grouped by language.
     */
    fun getSourcesByLanguage(): Map<String, List<CatalogueSource>> {
        return getCatalogueSources().groupBy { it.lang }
    }
    
    /**
     * Get the number of loaded sources.
     */
    fun getSourceCount(): Int = sourceCache.size
    
    /**
     * Check if any Mihon extensions are loaded.
     */
    fun hasExtensions(): Boolean = _installedExtensions.value.isNotEmpty()

    private fun registerPackageObserver() {
        if (isPackageObserverRegistered) return
        registerExternalExtensionPackageObserver(context) {
            loadExtensions()
        }
        isPackageObserverRegistered = true
    }
}
