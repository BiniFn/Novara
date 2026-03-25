package org.skepsun.kototoro.settings.sources.jsonsource

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.skepsun.kototoro.core.db.entity.JsonSourceType
import org.skepsun.kototoro.core.jsonsource.JsonSourceManager
import org.skepsun.kototoro.core.lnreader.LNReaderPluginInfo
import org.skepsun.kototoro.core.lnreader.LNReaderRepository
import org.skepsun.kototoro.core.network.jsonsource.JsonSourceHttpClient
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.ui.BaseViewModel
import javax.inject.Inject

@HiltViewModel
class LNReaderRepoViewModel @Inject constructor(
	private val jsonSourceManager: JsonSourceManager,
	@JsonSourceHttpClient private val okHttpClient: OkHttpClient,
	private val appSettings: AppSettings,
) : BaseViewModel() {

	private val repository = LNReaderRepository(okHttpClient, jsonSourceManager)

	private val _plugins = MutableStateFlow<List<LNReaderPluginInfo>>(emptyList())
	
	private val _searchQuery = MutableStateFlow("")
	val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
	
	private val _langFilter = MutableStateFlow<String?>(null)
	val langFilter: StateFlow<String?> = _langFilter.asStateFlow()

	private val _uiState = MutableStateFlow<RepoUiState>(RepoUiState.Idle)
	val uiState: StateFlow<RepoUiState> = _uiState.asStateFlow()

	private val _installingPluginIds = MutableStateFlow<Set<String>>(emptySet())
	val installingPluginIds: StateFlow<Set<String>> = _installingPluginIds.asStateFlow()

	/** IDs of locally installed LNREADER sources */
	private val installedSourceIds: StateFlow<Set<String>> = jsonSourceManager
		.observeAllJsonSources()
		.combine(MutableStateFlow(Unit)) { sources, _ ->
			sources.filter { it.type == JsonSourceType.LNREADER }
				.map { it.id }
				.toSet()
		}
		.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

	/** Available languages from the plugin list */
	val availableLanguages: StateFlow<List<String>> = _plugins
		.combine(MutableStateFlow(Unit)) { plugins, _ ->
			plugins.map { it.lang }
				.distinct()
				.sorted()
		}
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

	/** Filtered + grouped plugin list for display */
	val displayPlugins: StateFlow<List<PluginDisplayItem>> = combine(
		_plugins,
		_searchQuery,
		_langFilter,
		installedSourceIds,
	) { plugins, query, langFilter, installed ->
		val filtered = plugins
			.filter { p ->
				(query.isBlank() || p.name.contains(query, ignoreCase = true) || p.site.contains(query, ignoreCase = true))
					&& (langFilter == null || p.lang == langFilter)
			}

		// Group by language
		val grouped = filtered.groupBy { it.lang }
		val items = mutableListOf<PluginDisplayItem>()
		for ((lang, langPlugins) in grouped.entries.sortedBy { it.key }) {
			items.add(PluginDisplayItem.LangHeader(lang, langPlugins.size))
			for (plugin in langPlugins.sortedBy { it.name }) {
				val isInstalled = installed.any { id ->
					// Match by hash-based ID pattern
					id.contains(plugin.site.hashCode().toUInt().toString(16).uppercase()) ||
						id.contains(plugin.id.hashCode().toUInt().toString(16).uppercase())
				}
				items.add(PluginDisplayItem.Plugin(plugin, isInstalled))
			}
		}
		items
	}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

	fun loadPlugins() {
		viewModelScope.launch(Dispatchers.IO) {
			_uiState.value = RepoUiState.Loading
			val repoUrls = appSettings.lnReaderRepoUrls
			val allPlugins = mutableListOf<LNReaderPluginInfo>()

			for (url in repoUrls) {
				repository.fetchPluginIndex(url)
					.onSuccess { allPlugins.addAll(it) }
					.onFailure { e ->
						android.util.Log.w("LNReaderRepoVM", "Failed to fetch $url", e)
					}
			}

			if (allPlugins.isNotEmpty()) {
				_plugins.value = allPlugins
				_uiState.value = RepoUiState.Loaded(allPlugins.size)
			} else {
				_uiState.value = RepoUiState.Error("No plugins found")
			}
		}
	}

	fun installPlugin(plugin: LNReaderPluginInfo) {
		viewModelScope.launch(Dispatchers.IO) {
			_installingPluginIds.value = _installingPluginIds.value + plugin.id
			repository.installPlugin(plugin)
				.onSuccess {
					android.util.Log.d("LNReaderRepoVM", "Installed ${plugin.name}")
				}
				.onFailure { e ->
					android.util.Log.e("LNReaderRepoVM", "Failed to install ${plugin.name}", e)
				}
			_installingPluginIds.value = _installingPluginIds.value - plugin.id
		}
	}

	fun setSearchQuery(query: String) {
		_searchQuery.value = query
	}

	fun setLangFilter(lang: String?) {
		_langFilter.value = lang
	}
}

sealed class RepoUiState {
	object Idle : RepoUiState()
	object Loading : RepoUiState()
	data class Loaded(val count: Int) : RepoUiState()
	data class Error(val message: String) : RepoUiState()
}

sealed class PluginDisplayItem {
	data class LangHeader(val lang: String, val count: Int) : PluginDisplayItem()
	data class Plugin(val info: LNReaderPluginInfo, val isInstalled: Boolean) : PluginDisplayItem()
}
