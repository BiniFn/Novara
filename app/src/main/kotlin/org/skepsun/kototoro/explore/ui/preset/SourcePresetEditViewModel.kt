package org.skepsun.kototoro.explore.ui.preset

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.model.isNsfw
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.ui.BaseViewModel
import org.skepsun.kototoro.core.util.ext.MutableEventFlow
import org.skepsun.kototoro.core.util.ext.call
import org.skepsun.kototoro.explore.data.SourcePreset
import org.skepsun.kototoro.explore.data.SourcePresetsRepository
import org.skepsun.kototoro.explore.data.ContentSourcesRepository
import org.skepsun.kototoro.parsers.model.MangaParserSource
import javax.inject.Inject

@HiltViewModel
class SourcePresetEditViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	private val presetsRepository: SourcePresetsRepository,
	private val sourcesRepository: ContentSourcesRepository,
	private val settings: AppSettings,
) : BaseViewModel() {

	private val presetId = savedStateHandle[AppRouter.KEY_ID] ?: NO_ID

	val onSaved = MutableEventFlow<Unit>()
	val preset = MutableStateFlow<SourcePreset?>(null)

	val allLocales: Set<String> = sourcesRepository.allContentSources
		.mapNotNullTo(LinkedHashSet()) { org.skepsun.kototoro.core.model.getLocale(it)?.language.takeIf { l -> !l.isNullOrEmpty() } }

	init {
		launchLoadingJob(Dispatchers.Default) {
			preset.value = if (presetId != NO_ID) {
				presetsRepository.getById(presetId)
			} else {
				null
			}
		}
	}

	fun save(title: String, selectedLanguages: Set<String>) {
		launchLoadingJob(Dispatchers.Default) {
			check(title.isNotEmpty())
			if (presetId == NO_ID) {
				val initialSources = getSourcesForLanguages(selectedLanguages)
				presetsRepository.createPreset(title, selectedLanguages, initialSources)
			} else {
				presetsRepository.updatePreset(presetId, title, selectedLanguages)
			}
			onSaved.call(Unit)
		}
	}

	private fun getSourcesForLanguages(languages: Set<String>): Set<String> {
		if (languages.isEmpty()) return emptySet()
		val skipNsfw = settings.isNsfwContentDisabled
		return sourcesRepository.allContentSources
			.filter { org.skepsun.kototoro.core.model.getLocale(it)?.language in languages && (!skipNsfw || !it.isNsfw()) }
			.mapTo(HashSet()) { it.name }
	}

	companion object {
		const val NO_ID = -1L
	}
}
