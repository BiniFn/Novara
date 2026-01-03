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
    
    /**
     * Initialize the extension manager and load all extensions.
     */
    fun initialize() {
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
            android.util.Log.d(TAG, "Loading Mihon extensions...")
            
            val results = loader.loadExtensions(context)
            
            val successful = mutableListOf<MihonLoadResult.Success>()
            val failed = mutableListOf<MihonLoadResult.Error>()
            
            // First pass: collect all sources and their names to detect multi-language sources
            val allSourcesWithMeta = mutableListOf<Triple<CatalogueSource, String, Boolean>>()
            
            results.forEach { result ->
                when (result) {
                    is MihonLoadResult.Success -> {
                        successful.add(result)
                        result.sources.forEach { source ->
                            sourceCache[source.id] = source
                            val catalogueSource = source as? CatalogueSource ?: return@forEach
                            allSourcesWithMeta.add(Triple(catalogueSource, result.pkgName, result.isNsfw))
                        }
                    }
                    is MihonLoadResult.Error -> {
                        failed.add(result)
                        android.util.Log.e(TAG, "Failed to load ${result.pkgName}: ${result.message}")
                    }
                    is MihonLoadResult.Untrusted -> {
                        android.util.Log.w(TAG, "Untrusted extension: ${result.pkgName}")
                    }
                }
            }
            
            // Count how many sources share each name (to detect multi-language)
            val nameCountMap = allSourcesWithMeta.groupBy { it.first.name }.mapValues { it.value.size }
            
            // Second pass: create MihonMangaSource with appropriate language suffix
            allSourcesWithMeta.forEach { (catalogueSource, pkgName, isNsfw) ->
                val needsLanguageSuffix = nameCountMap[catalogueSource.name]?.let { it > 1 } ?: false
                mangaSourceCache[catalogueSource.id] = MihonMangaSource(
                    catalogueSource = catalogueSource,
                    pkgName = pkgName,
                    isNsfw = isNsfw,
                    hasLanguageSuffix = needsLanguageSuffix,
                )
            }
            
            _installedExtensions.value = successful
            _failedExtensions.value = failed
            
            android.util.Log.d(TAG, "Loaded ${successful.size} extension(s), ${failed.size} failed, ${mangaSourceCache.size} sources total")
            
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
}
