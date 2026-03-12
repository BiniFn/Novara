package org.skepsun.kototoro.settings.sources.aniyomi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.skepsun.kototoro.aniyomi.AniyomiExtensionManager
import org.skepsun.kototoro.aniyomi.model.AniyomiLoadResult
import org.skepsun.kototoro.settings.sources.extensions.InstalledExtensionItem
import org.skepsun.kototoro.settings.sources.extensions.InstalledExtensionsScreenModel
import javax.inject.Inject

/**
 * ViewModel for the Aniyomi extensions management screen.
 */
@HiltViewModel
class AniyomiExtensionsViewModel @Inject constructor(
    private val extensionManager: AniyomiExtensionManager,
) : ViewModel(), InstalledExtensionsScreenModel {
    
    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    override val extensions: StateFlow<List<InstalledExtensionItem>> = extensionManager.installedExtensions
        .map { list ->
            list.map { ext ->
                InstalledExtensionItem(
                    pkgName = ext.pkgName,
                    appName = ext.appName,
                    versionName = ext.versionName,
                    lang = ext.lang,
                    isNsfw = ext.isNsfw,
                    sourceCount = ext.sources.size,
                    sourceNames = ext.sources.map { it.name },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    override val extensionCount: StateFlow<Int> = extensionManager.installedExtensions
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)
    
    override val sourceCount: StateFlow<Int> = extensionManager.installedExtensions
        .map { list -> list.sumOf { it.sources.size } }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)
    
    val failedExtensions: StateFlow<List<AniyomiLoadResult.Error>> = extensionManager.failedExtensions
    
    init {
        // Ensure extensions are loaded
        if (!extensionManager.hasExtensions()) {
            refresh()
        }
    }
    
    override fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                extensionManager.loadExtensions()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
