package org.skepsun.kototoro.aniyomi

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.skepsun.kototoro.aniyomi.model.AniyomiAnimeSource
import org.skepsun.kototoro.aniyomi.model.AniyomiLoadResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for Aniyomi extensions.
 * 
 * Handles loading, caching, and providing access to Aniyomi extension sources.
 */
@Singleton
class AniyomiExtensionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val loader: AniyomiExtensionLoader,
) {
    companion object {
        private const val TAG = "AniyomiExtensionManager"
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Loaded extensions
    private val _installedExtensions = MutableStateFlow<List<AniyomiLoadResult.Success>>(emptyList())
    val installedExtensions: StateFlow<List<AniyomiLoadResult.Success>> = _installedExtensions.asStateFlow()
    
    // Failed extensions
    private val _failedExtensions = MutableStateFlow<List<AniyomiLoadResult.Error>>(emptyList())
    val failedExtensions: StateFlow<List<AniyomiLoadResult.Error>> = _failedExtensions.asStateFlow()
    
    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // Cache of source ID -> Source
    private val sourceCache = mutableMapOf<Long, AnimeSource>()
    
    // Cache of source ID -> AniyomiAnimeSource wrapper
    private val animeSourceCache = mutableMapOf<Long, AniyomiAnimeSource>()
    
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
            android.util.Log.d(TAG, "Loading Aniyomi extensions...")
            
            val results = loader.loadExtensions(context)
            
            val successful = mutableListOf<AniyomiLoadResult.Success>()
            val failed = mutableListOf<AniyomiLoadResult.Error>()
            
            // First pass: collect all sources and their names to detect multi-language sources
            val allSourcesWithMeta = mutableListOf<Triple<AnimeCatalogueSource, String, Boolean>>()
            
            results.forEach { result ->
                when (result) {
                    is AniyomiLoadResult.Success -> {
                        successful.add(result)
                        result.sources.forEach { source ->
                            sourceCache[source.id] = source
                            val catalogueSource = source as? AnimeCatalogueSource ?: return@forEach
                            allSourcesWithMeta.add(Triple(catalogueSource, result.pkgName, result.isNsfw))
                        }
                    }
                    is AniyomiLoadResult.Error -> {
                        failed.add(result)
                        android.util.Log.e(TAG, "Failed to load ${result.pkgName}: ${result.message}")
                    }
                    is AniyomiLoadResult.Untrusted -> {
                        android.util.Log.w(TAG, "Untrusted extension: ${result.pkgName}")
                    }
                }
            }
            
            // Count how many sources share each name
            val nameCountMap = allSourcesWithMeta.groupBy { it.first.name }.mapValues { it.value.size }
            
            // Second pass: create AniyomiAnimeSource with appropriate language suffix
            allSourcesWithMeta.forEach { (catalogueSource, pkgName, isNsfw) ->
                val needsLanguageSuffix = nameCountMap[catalogueSource.name]?.let { it > 1 } ?: false
                animeSourceCache[catalogueSource.id] = AniyomiAnimeSource(
                    animeCatalogueSource = catalogueSource,
                    pkgName = pkgName,
                    isNsfw = isNsfw,
                    hasLanguageSuffix = needsLanguageSuffix,
                )
            }
            
            _installedExtensions.value = successful
            _failedExtensions.value = failed
            
            android.util.Log.d(TAG, "Loaded ${successful.size} extension(s), ${failed.size} failed, ${animeSourceCache.size} sources total")
            
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Get all available AnimeCatalogueSource instances.
     */
    fun getCatalogueSources(): List<AnimeCatalogueSource> {
        return _installedExtensions.value.flatMap { it.catalogueSources }
    }
    
    /**
     * Get all AniyomiAnimeSource wrappers.
     */
    fun getAniyomiAnimeSources(): List<AniyomiAnimeSource> {
        return animeSourceCache.values.toList()
    }
    
    /**
     * Get a source by its ID.
     */
    fun getSourceById(sourceId: Long): AnimeSource? {
        return sourceCache[sourceId]
    }
    
    /**
     * Get an AnimeCatalogueSource by its ID.
     */
    fun getCatalogueSourceById(sourceId: Long): AnimeCatalogueSource? {
        return sourceCache[sourceId] as? AnimeCatalogueSource
    }
    
    /**
     * Get an AniyomiAnimeSource wrapper by source ID.
     */
    fun getAniyomiAnimeSourceById(sourceId: Long): AniyomiAnimeSource? {
        return animeSourceCache[sourceId]
    }
    
    /**
     * Get an AniyomiAnimeSource by its name (format: "ANIYOMI_{sourceId}").
     */
    fun getAniyomiAnimeSourceByName(name: String): AniyomiAnimeSource? {
        if (!name.startsWith("ANIYOMI_")) return null
        val sourceId = name.substringAfter("ANIYOMI_").toLongOrNull() ?: return null
        return getAniyomiAnimeSourceById(sourceId)
    }
    
    /**
     * Get sources grouped by language.
     */
    fun getSourcesByLanguage(): Map<String, List<AnimeCatalogueSource>> {
        return getCatalogueSources().groupBy { it.lang }
    }
    
    /**
     * Get the number of loaded sources.
     */
    fun getSourceCount(): Int = sourceCache.size
    
    /**
     * Check if any Aniyomi extensions are loaded.
     */
    fun hasExtensions(): Boolean = _installedExtensions.value.isNotEmpty()
}
