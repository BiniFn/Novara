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
import javax.inject.Inject

/**
 * ViewModel for the Aniyomi extensions management screen.
 */
@HiltViewModel
class AniyomiExtensionsViewModel @Inject constructor(
    private val extensionManager: AniyomiExtensionManager,
) : ViewModel() {
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    val extensions: StateFlow<List<AniyomiExtensionItem>> = extensionManager.installedExtensions
        .map { list ->
            list.map { ext ->
                AniyomiExtensionItem(
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
    
    val extensionCount: StateFlow<Int> = extensionManager.installedExtensions
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)
    
    val sourceCount: StateFlow<Int> = extensionManager.installedExtensions
        .map { list -> list.sumOf { it.sources.size } }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)
    
    val failedExtensions: StateFlow<List<AniyomiLoadResult.Error>> = extensionManager.failedExtensions
    
    init {
        // Ensure extensions are loaded
        if (!extensionManager.hasExtensions()) {
            refresh()
        }
    }
    
    fun refresh() {
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

/**
 * UI model for an Aniyomi extension.
 */
data class AniyomiExtensionItem(
    val pkgName: String,
    val appName: String,
    val versionName: String,
    val lang: String,
    val isNsfw: Boolean,
    val sourceCount: Int,
    val sourceNames: List<String>,
)
