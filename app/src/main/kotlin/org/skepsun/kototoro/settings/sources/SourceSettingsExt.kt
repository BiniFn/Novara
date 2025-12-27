package org.skepsun.kototoro.settings.sources

import android.view.inputmethod.EditorInfo
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.parser.EmptyMangaRepository
import org.skepsun.kototoro.core.parser.MangaRepository
import org.skepsun.kototoro.core.parser.ParserMangaRepository
import org.skepsun.kototoro.parsers.config.ConfigKey
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.parsers.model.MangaParserSource
import org.skepsun.kototoro.parsers.network.UserAgents
import org.skepsun.kototoro.parsers.util.mapToArray
import org.skepsun.kototoro.settings.utils.AutoCompleteTextViewPreference
import org.skepsun.kototoro.settings.utils.EditTextBindListener
import org.skepsun.kototoro.settings.utils.EditTextDefaultSummaryProvider
import org.skepsun.kototoro.settings.utils.validation.DomainValidator
import org.skepsun.kototoro.settings.utils.validation.HeaderValidator
import org.skepsun.kototoro.core.model.getDomainTitleResId
import org.skepsun.kototoro.core.model.unwrap

fun PreferenceFragmentCompat.addPreferencesFromRepository(repository: MangaRepository) = when (repository) {
	is ParserMangaRepository -> addPreferencesFromParserRepository(repository)
	is EmptyMangaRepository -> addPreferencesFromEmptyRepository()
	else -> Unit
}

private fun PreferenceFragmentCompat.addPreferencesFromParserRepository(repository: ParserMangaRepository) {
	addPreferencesFromResource(R.xml.pref_source_parser)
	val configKeys = repository.getConfigKeys()
	val screen = preferenceScreen
	for (key in configKeys) {
		val preference: Preference = when (key) {
			is ConfigKey.Domain -> {
				val presetValues = key.presetValues
				if (presetValues.size <= 1) {
					EditTextPreference(screen.context)
				} else {
					AutoCompleteTextViewPreference(screen.context).apply {
						entries = presetValues.toStringArray()
					}
				}.apply {
					summaryProvider = EditTextDefaultSummaryProvider(key.defaultValue)
					setOnBindEditTextListener(
						EditTextBindListener(
							inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_URI,
							hint = key.defaultValue,
							validator = DomainValidator(),
						),
					)
					val contentType = (repository.source.unwrap() as? MangaParserSource)?.contentType ?: ContentType.MANGA
					val domainTitleResId = contentType.getDomainTitleResId()
					setTitle(domainTitleResId)
					setDialogTitle(domainTitleResId)
				}
			}

			is ConfigKey.Text -> {
				EditTextPreference(screen.context).apply {
					summaryProvider = EditTextDefaultSummaryProvider(key.defaultValue)
					setOnBindEditTextListener(
						EditTextBindListener(
							inputType = EditorInfo.TYPE_CLASS_TEXT,
							hint = key.defaultValue,
							validator = null,
						),
					)
					title = key.title
					setDialogTitle(key.title)
				}
			}

			is ConfigKey.UserAgent -> {
				AutoCompleteTextViewPreference(screen.context).apply {
					entries = arrayOf(
						UserAgents.FIREFOX_MOBILE,
						UserAgents.CHROME_MOBILE,
						UserAgents.FIREFOX_DESKTOP,
						UserAgents.CHROME_DESKTOP,
					)
					summaryProvider = EditTextDefaultSummaryProvider(key.defaultValue)
					setOnBindEditTextListener(
						EditTextBindListener(
							inputType = EditorInfo.TYPE_CLASS_TEXT,
							hint = key.defaultValue,
							validator = HeaderValidator(),
						),
					)
					setTitle(R.string.user_agent)
					setDialogTitle(R.string.user_agent)
				}
			}

			is ConfigKey.ShowSuspiciousContent -> {
				SwitchPreferenceCompat(screen.context).apply {
					setDefaultValue(key.defaultValue)
					setTitle(R.string.show_suspicious_content)
				}
			}

			is ConfigKey.Toggle -> {
				SwitchPreferenceCompat(screen.context).apply {
					setDefaultValue(key.defaultValue)
					title = key.title
				}
			}

			is ConfigKey.SplitByTranslations -> {
				SwitchPreferenceCompat(screen.context).apply {
					setDefaultValue(key.defaultValue)
					setTitle(R.string.split_by_translations)
					setSummary(R.string.split_by_translations_summary)
				}
			}

			is ConfigKey.PreferredImageServer -> {
				ListPreference(screen.context).apply {
					entries = key.presetValues.values.mapToArray {
						it ?: context.getString(R.string.automatic)
					}
					entryValues = key.presetValues.keys.mapToArray { it.orEmpty() }
					setDefaultValue(key.defaultValue.orEmpty())
					setTitle(R.string.image_server)
					setDialogTitle(R.string.image_server)
					summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
				}
			}
		}
		preference.isIconSpaceReserved = false
		preference.key = key.key
		preference.order = 10
		screen.addPreference(preference)
	}
}

private fun PreferenceFragmentCompat.addPreferencesFromEmptyRepository() {
	val preference = Preference(requireContext())
	preference.setIcon(R.drawable.ic_alert_outline)
	preference.isPersistent = false
	preference.isSelectable = false
	preference.order = 200
	preference.setSummary(R.string.unsupported_source)
	preferenceScreen.addPreference(preference)
}

private fun Array<out String?>.toStringArray(): Array<String> {
	return Array(size) { i -> this[i].orEmpty() }
}
