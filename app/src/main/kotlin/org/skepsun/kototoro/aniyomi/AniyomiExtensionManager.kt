package org.skepsun.kototoro.aniyomi

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import org.skepsun.kototoro.extensions.runtime.ExternalExtensionManagerRuntime
import org.skepsun.kototoro.extensions.runtime.processExternalExtensionResults
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
    
    private val runtime = ExternalExtensionManagerRuntime<
        AniyomiLoadResult,
        AniyomiLoadResult.Success,
        AniyomiLoadResult.Error,
        AnimeSource,
        AniyomiAnimeSource,
    >(
        context = context,
        scope = scope,
    )
    val installedExtensions: StateFlow<List<AniyomiLoadResult.Success>> = runtime.installedExtensions
    val failedExtensions: StateFlow<List<AniyomiLoadResult.Error>> = runtime.failedExtensions
    val isLoading: StateFlow<Boolean> = runtime.isLoading
    
    /**
     * Initialize the extension manager and load all extensions.
     */
    fun initialize() {
        runtime.initialize(::loadExtensions)
    }
    
    /**
     * Reload all extensions.
     */
    suspend fun loadExtensions() {
        runtime.loadExtensions(
            loadResults = loader::loadExtensions,
            processResults = { results ->
            android.util.Log.d(TAG, "load_start ecosystem=aniyomi")
                processExternalExtensionResults(
                    results = results,
                    successOf = { it as? AniyomiLoadResult.Success },
                    errorOf = { it as? AniyomiLoadResult.Error },
                    untrustedPackageNameOf = { (it as? AniyomiLoadResult.Untrusted)?.pkgName },
                    successSources = { it.sources },
                    successPackageName = { it.pkgName },
                    successIsNsfw = { it.isNsfw },
                    sourceId = { it.id },
                    asCatalogueSource = { it as? AnimeCatalogueSource },
                    catalogueSourceName = { it.name },
                    buildWrappedSource = { catalogueSource, pkgName, isNsfw, hasLanguageSuffix ->
                        AniyomiAnimeSource(
                            animeCatalogueSource = catalogueSource,
                            pkgName = pkgName,
                            isNsfw = isNsfw,
                            hasLanguageSuffix = hasLanguageSuffix,
                        )
                    },
                    onError = { error ->
                        android.util.Log.e(TAG, "load_error ecosystem=aniyomi pkg=${error.pkgName} message=${error.message}")
                    },
                    onUntrusted = { pkgName ->
                        android.util.Log.w(TAG, "load_untrusted ecosystem=aniyomi pkg=$pkgName")
                    },
                ).also { processed ->
                    android.util.Log.d(
                        TAG,
                        "load_complete ecosystem=aniyomi success=${processed.successful.size} failed=${processed.failed.size} untrusted=${processed.untrustedPackages.size} sources=${processed.wrappedSourceById.size}",
                    )
                }
            },
        )
    }
    
    /**
     * Get all available AnimeCatalogueSource instances.
     */
    fun getCatalogueSources(): List<AnimeCatalogueSource> {
        return installedExtensions.value.flatMap { it.catalogueSources }
    }
    
    /**
     * Get all AniyomiAnimeSource wrappers.
     */
    fun getAniyomiAnimeSources(): List<AniyomiAnimeSource> {
        return runtime.getWrappedSources()
    }
    
    /**
     * Get a source by its ID.
     */
    fun getSourceById(sourceId: Long): AnimeSource? {
        return runtime.getSourceById(sourceId)
    }
    
    /**
     * Get an AnimeCatalogueSource by its ID.
     */
    fun getCatalogueSourceById(sourceId: Long): AnimeCatalogueSource? {
        return runtime.getSourceById(sourceId) as? AnimeCatalogueSource
    }
    
    /**
     * Get an AniyomiAnimeSource wrapper by source ID.
     */
    fun getAniyomiAnimeSourceById(sourceId: Long): AniyomiAnimeSource? {
        return runtime.getWrappedSourceById(sourceId)
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
    fun getSourceCount(): Int = runtime.getSourceCount()
    
    /**
     * Check if any Aniyomi extensions are loaded.
     */
    fun hasExtensions(): Boolean = runtime.hasExtensions()
}
