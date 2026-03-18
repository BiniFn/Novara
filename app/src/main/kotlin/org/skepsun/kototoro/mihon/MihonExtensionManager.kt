package org.skepsun.kototoro.mihon

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import org.skepsun.kototoro.extensions.runtime.ExternalExtensionManagerRuntime
import org.skepsun.kototoro.extensions.runtime.processExternalExtensionResults
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
    
    private val runtime = ExternalExtensionManagerRuntime<
        MihonLoadResult,
        MihonLoadResult.Success,
        MihonLoadResult.Error,
        Source,
        MihonMangaSource,
    >(
        context = context,
        scope = scope,
    )
    val installedExtensions: StateFlow<List<MihonLoadResult.Success>> = runtime.installedExtensions
    val failedExtensions: StateFlow<List<MihonLoadResult.Error>> = runtime.failedExtensions
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
            android.util.Log.d(TAG, "load_start ecosystem=mihon")
                processExternalExtensionResults(
                    results = results,
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
                ).also { processed ->
                    android.util.Log.d(
                        TAG,
                        "load_complete ecosystem=mihon success=${processed.successful.size} failed=${processed.failed.size} untrusted=${processed.untrustedPackages.size} sources=${processed.wrappedSourceById.size}",
                    )
                }
            },
        )
    }
    
    /**
     * Get all available CatalogueSource instances.
     */
    fun getCatalogueSources(): List<CatalogueSource> {
        return installedExtensions.value.flatMap { it.catalogueSources }
    }
    
    /**
     * Get all MihonMangaSource wrappers.
     */
    fun getMihonMangaSources(): List<MihonMangaSource> {
        return runtime.getWrappedSources()
    }
    
    /**
     * Get a source by its ID.
     */
    fun getSourceById(sourceId: Long): Source? {
        return runtime.getSourceById(sourceId)
    }
    
    /**
     * Get a CatalogueSource by its ID.
     */
    fun getCatalogueSourceById(sourceId: Long): CatalogueSource? {
        return runtime.getSourceById(sourceId) as? CatalogueSource
    }
    
    /**
     * Get a MihonMangaSource wrapper by source ID.
     */
    fun getMihonMangaSourceById(sourceId: Long): MihonMangaSource? {
        return runtime.getWrappedSourceById(sourceId)
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
    fun getSourceCount(): Int = runtime.getSourceCount()
    
    /**
     * Check if any Mihon extensions are loaded.
     */
    fun hasExtensions(): Boolean = runtime.hasExtensions()
}
