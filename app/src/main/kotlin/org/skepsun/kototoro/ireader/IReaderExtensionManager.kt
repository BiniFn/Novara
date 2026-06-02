package org.skepsun.kototoro.ireader

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import ireader.core.source.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import org.skepsun.kototoro.extensions.runtime.ExternalExtensionManagerFacade
import org.skepsun.kototoro.ireader.model.IReaderLoadResult
import org.skepsun.kototoro.ireader.model.IReaderMangaSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IReaderExtensionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val loader: IReaderExtensionLoader,
) {
    companion object {
        private const val TAG = "IReaderExtensionManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val facade = ExternalExtensionManagerFacade<
        IReaderLoadResult,
        IReaderLoadResult.Success,
        IReaderLoadResult.Error,
        Source,
        Source,
        IReaderMangaSource,
    >(
        context = context,
        scope = scope,
        logTag = TAG,
        ecosystem = "ireader",
        sourceNamePrefix = "IREADER_",
        loadResults = loader::loadExtensions,
        successOf = { it as? IReaderLoadResult.Success },
        errorOf = { it as? IReaderLoadResult.Error },
        untrustedPackageNameOf = { null },
        successSources = { it.sources },
        successPackageName = { it.pkgName },
        successIsNsfw = { it.isNsfw },
        successCatalogueSources = { it.sources },
        sourceId = { it.id },
        asCatalogueSource = { it },
        catalogueSourceName = { it.name },
        catalogueSourceLang = { it.lang },
        buildWrappedSource = { catalogueSource, pkgName, isNsfw, _ ->
            IReaderMangaSource(
                pkgName = pkgName,
                catalogueSource = catalogueSource,
                isNsfw = isNsfw,
                language = catalogueSource.lang,
                displayName = catalogueSource.name,
            )
        },
        errorPackageName = { it.pkgName },
        errorMessage = { it.message },
    )

    val installedExtensions: StateFlow<List<IReaderLoadResult.Success>> = facade.installedExtensions
    val failedExtensions: StateFlow<List<IReaderLoadResult.Error>> = facade.failedExtensions
    val isLoading: StateFlow<Boolean> = facade.isLoading
    val changes: StateFlow<Int> = facade.changes

    fun initialize() {
        facade.initialize()
    }

    suspend fun loadExtensions() {
        facade.loadExtensions()
    }

    fun getCatalogueSources(): List<Source> {
        return facade.getCatalogueSources()
    }

    fun getIReaderMangaSources(): List<IReaderMangaSource> {
        return facade.getWrappedSources()
    }

    fun getSourceById(sourceId: Long): Source? {
        return facade.getSourceById(sourceId)
    }

    fun getIReaderMangaSourceById(sourceId: Long): IReaderMangaSource? {
        return facade.getWrappedSourceById(sourceId)
    }

    fun getSourcesByLanguage(): Map<String, List<Source>> {
        return facade.getSourcesByLanguage()
    }

    fun getSourceCount(): Int = facade.getSourceCount()

    fun hasExtensions(): Boolean = facade.hasExtensions()

    fun getIReaderMangaSourceByName(name: String): IReaderMangaSource? {
        return facade.getWrappedSourceByName(name)
    }
}
