package org.skepsun.kototoro.main.ui.welcome

import android.content.Context
import androidx.core.os.ConfigurationCompat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import org.skepsun.kototoro.core.LocalizedAppContext
import org.skepsun.kototoro.core.ui.BaseViewModel
import org.skepsun.kototoro.core.util.LocaleComparator
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.util.ext.mapSortedByCount
import org.skepsun.kototoro.core.util.ext.sortedWithSafe
import org.skepsun.kototoro.core.util.ext.toList
import org.skepsun.kototoro.core.util.ext.toLocale
import org.skepsun.kototoro.explore.data.ContentSourcesRepository
import org.skepsun.kototoro.filter.ui.model.FilterProperty
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.parsers.model.ContentParserSource
import org.skepsun.kototoro.core.model.getContentType
import org.skepsun.kototoro.core.model.getLocale
import org.skepsun.kototoro.parsers.util.mapToSet
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class WelcomeViewModel @Inject constructor(
	private val repository: ContentSourcesRepository,
	private val settings: AppSettings,
	@LocalizedAppContext context: Context,
) : BaseViewModel() {

private val allSources = repository.allContentSources
private val localesGroups by lazy { allSources.groupBy { it.getLocale() ?: Locale.ROOT } }

	private var updateJob: Job

	val locales = MutableStateFlow(
		FilterProperty<Locale>(
			availableItems = listOf(Locale.ROOT),
			selectedItems = setOf(Locale.ROOT),
			isLoading = true,
			error = null,
		),
	)

	val types = MutableStateFlow(
		FilterProperty(
			availableItems = listOf(ContentType.MANGA),
			selectedItems = setOf(ContentType.MANGA),
			isLoading = true,
			error = null,
		),
	)

	init {
		updateJob = launchJob(Dispatchers.Default) {
			// Map adult content types to their base types for display
			val contentTypes = allSources
				.map { source ->
					when (source.getContentType()) {
						ContentType.HENTAI_MANGA -> ContentType.MANGA
						ContentType.HENTAI_NOVEL -> ContentType.NOVEL
						ContentType.HENTAI_VIDEO -> ContentType.VIDEO
						else -> source.getContentType()
					}
				}
				.groupingBy { it }
				.eachCount()
				.toList()
				.sortedByDescending { it.second }
				.map { it.first }
			types.value = types.value.copy(
				availableItems = contentTypes,
				isLoading = false,
			)
			val previouslySelectedLanguages = settings.contentLanguages
			val selectedLocales = if (previouslySelectedLanguages.isNotEmpty()) {
				localesGroups.keys.filterTo(HashSet()) { it.language in previouslySelectedLanguages }
			} else {
				val languagesMap = localesGroups.keys.associateBy { x -> x.language }
				val set = HashSet<Locale>(2)
				ConfigurationCompat.getLocales(context.resources.configuration).toList()
					.firstNotNullOfOrNull { lc -> languagesMap[lc.language] }
					?.let { set += it }
				set += Locale.ROOT
				set
			}
			locales.value = locales.value.copy(
				availableItems = localesGroups.keys.sortedWithSafe(LocaleComparator()),
				selectedItems = selectedLocales,
				isLoading = false,
			)

			val enabledSources = repository.getEnabledSources().map { it.name }.toSet()
			val selectedTypes = allSources
				.filter { it.name in enabledSources }
				.map { source ->
					when (source.getContentType()) {
						ContentType.HENTAI_MANGA -> ContentType.MANGA
						ContentType.HENTAI_NOVEL -> ContentType.NOVEL
						ContentType.HENTAI_VIDEO -> ContentType.VIDEO
						else -> source.getContentType()
					}
				}
				.toSet()
			if (selectedTypes.isNotEmpty()) {
				types.value = types.value.copy(selectedItems = selectedTypes)
			}

			repository.clearNewSourcesBadge()
			commit()
		}
	}

	fun setLocaleChecked(locale: Locale, isChecked: Boolean) {
		val snapshot = locales.value
		locales.value = snapshot.copy(
			selectedItems = if (isChecked) {
				snapshot.selectedItems + locale
			} else {
				snapshot.selectedItems - locale
			},
		)
		val prevJob = updateJob
		updateJob = launchJob(Dispatchers.Default) {
			prevJob.join()
			commit()
		}
	}

	fun setTypeChecked(type: ContentType, isChecked: Boolean) {
		val snapshot = types.value
		types.value = snapshot.copy(
			selectedItems = if (isChecked) {
				snapshot.selectedItems + type
			} else {
				snapshot.selectedItems - type
			},
		)
		val prevJob = updateJob
		updateJob = launchJob(Dispatchers.Default) {
			prevJob.join()
			commit()
		}
	}

	private suspend fun commit() {
		val languages = locales.value.selectedItems.mapToSet { it.language }
		val selectedTypes = types.value.selectedItems
		// Expand selected types to include adult variants
		val expandedTypes = selectedTypes.flatMapTo(HashSet()) { type ->
			when (type) {
				ContentType.MANGA -> listOf(ContentType.MANGA, ContentType.HENTAI_MANGA)
				ContentType.NOVEL -> listOf(ContentType.NOVEL, ContentType.HENTAI_NOVEL)
				ContentType.VIDEO -> listOf(ContentType.VIDEO, ContentType.HENTAI_VIDEO)
				else -> listOf(type)
			}
		}
		val enabledSources = allSources
			.filterTo(HashSet()) { x ->
				x.getContentType() in expandedTypes && (x.getLocale()?.language ?: "") in languages
			}
		repository.setSourcesEnabledExclusive(enabledSources)
		settings.contentLanguages = languages
	}
}
