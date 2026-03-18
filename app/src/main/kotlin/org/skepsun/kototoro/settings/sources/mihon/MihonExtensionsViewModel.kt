package org.skepsun.kototoro.settings.sources.mihon

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.skepsun.kototoro.mihon.MihonExtensionManager
import org.skepsun.kototoro.mihon.model.MihonLoadResult
import org.skepsun.kototoro.settings.sources.extensions.ExternalInstalledExtensionsScreenRuntime
import org.skepsun.kototoro.settings.sources.extensions.InstalledExtensionItem
import org.skepsun.kototoro.settings.sources.extensions.InstalledExtensionsScreenModel
import org.skepsun.kototoro.settings.sources.extensions.observeMihonInstalledExtensionEntries
import javax.inject.Inject

/**
 * ViewModel for the Mihon extensions management screen.
 */
@HiltViewModel
class MihonExtensionsViewModel @Inject constructor(
    private val extensionManager: MihonExtensionManager,
) : ViewModel(), InstalledExtensionsScreenModel {

    private val runtime = ExternalInstalledExtensionsScreenRuntime(
        scope = viewModelScope,
        installedEntries = observeMihonInstalledExtensionEntries(extensionManager)
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList()),
        hasExtensions = extensionManager::hasExtensions,
        reloadAction = extensionManager::loadExtensions,
    )

    override val isLoading: StateFlow<Boolean> = runtime.isLoading
    override val extensions: StateFlow<List<InstalledExtensionItem>> = runtime.extensions
    override val extensionCount: StateFlow<Int> = runtime.extensionCount
    override val sourceCount: StateFlow<Int> = runtime.sourceCount

    val failedExtensions: StateFlow<List<MihonLoadResult.Error>> = extensionManager.failedExtensions

    init {
        runtime.initialize()
    }

    override fun refresh() {
        runtime.refresh()
    }
}
