package org.skepsun.kototoro.settings.sources.aniyomi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.skepsun.kototoro.aniyomi.AniyomiExtensionManager
import org.skepsun.kototoro.aniyomi.model.AniyomiLoadResult
import org.skepsun.kototoro.settings.sources.extensions.ExternalInstalledExtensionsScreenRuntime
import org.skepsun.kototoro.settings.sources.extensions.InstalledExtensionItem
import org.skepsun.kototoro.settings.sources.extensions.InstalledExtensionsScreenModel
import org.skepsun.kototoro.settings.sources.extensions.observeAniyomiInstalledExtensionEntries
import javax.inject.Inject

/**
 * ViewModel for the Aniyomi extensions management screen.
 */
@HiltViewModel
class AniyomiExtensionsViewModel @Inject constructor(
    private val extensionManager: AniyomiExtensionManager,
) : ViewModel(), InstalledExtensionsScreenModel {

    private val runtime = ExternalInstalledExtensionsScreenRuntime(
        scope = viewModelScope,
        installedEntries = observeAniyomiInstalledExtensionEntries(extensionManager)
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList()),
        hasExtensions = extensionManager::hasExtensions,
        reloadAction = extensionManager::loadExtensions,
    )

    override val isLoading: StateFlow<Boolean> = runtime.isLoading
    override val extensions: StateFlow<List<InstalledExtensionItem>> = runtime.extensions
    override val extensionCount: StateFlow<Int> = runtime.extensionCount
    override val sourceCount: StateFlow<Int> = runtime.sourceCount

    val failedExtensions: StateFlow<List<AniyomiLoadResult.Error>> = extensionManager.failedExtensions

    init {
        runtime.initialize()
    }

    override fun refresh() {
        runtime.refresh()
    }
}
