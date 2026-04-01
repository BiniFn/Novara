package org.skepsun.kototoro.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.network.ContentHttpClient
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.ReaderTranslationMode
import org.skepsun.kototoro.core.ui.BasePreferenceFragment
import org.skepsun.kototoro.settings.support.TranslationApiSettingsSupport
import javax.inject.Inject

@AndroidEntryPoint
class TranslationApiSettingsFragment :
	BasePreferenceFragment(R.string.ai_api_settings),
	SharedPreferences.OnSharedPreferenceChangeListener {

	@Inject
	@ContentHttpClient
	lateinit var okHttpClient: OkHttpClient

	private var fetchModelsJob: Job? = null

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_translation_api)

		findPreference<ListPreference>(AppSettings.KEY_READER_TRANSLATION_API_PROVIDER_PRESET)?.run {
			setDefaultValue("CUSTOM")
			setOnPreferenceChangeListener { _, newValue ->
				val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
				TranslationApiSettingsSupport.applyApiProviderPreset(
					sharedPreferences = preferences,
					presetInput = (newValue as? String).orEmpty(),
					forceOverride = true,
				)
				true
			}
		}

		findPreference<EditTextPreference>(AppSettings.KEY_READER_TRANSLATION_API_MODEL)?.run {
			setDefaultValue("gpt-4o-mini")
		}

		updateApiModeSummary()
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		settings.subscribe(this)
	}

	override fun onDestroyView() {
		settings.unsubscribe(this)
		super.onDestroyView()
	}

	override fun onPreferenceTreeClick(preference: Preference): Boolean {
		return when (preference.key) {
			AppSettings.KEY_READER_TRANSLATION_API_FETCH_MODELS -> {
				fetchAndPickApiModel()
				true
			}
			else -> super.onPreferenceTreeClick(preference)
		}
	}

	override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
		when (key) {
			AppSettings.KEY_READER_TRANSLATION_MODE -> updateApiModeSummary()
			AppSettings.KEY_READER_TRANSLATION_API_PROVIDER_PRESET -> {
				val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
				TranslationApiSettingsSupport.applyApiProviderPreset(
					sharedPreferences = preferences,
					presetInput = settings.readerTranslationApiProviderPreset,
					forceOverride = true,
				)
			}
		}
	}

	private fun updateApiModeSummary() {
		findPreference<Preference>("reader_translation_api_usage_info")?.summary = getString(
			when (settings.readerTranslationMode) {
				ReaderTranslationMode.LOCAL_ONLY -> R.string.ai_api_usage_summary_local_only
				ReaderTranslationMode.API_ONLY -> R.string.ai_api_usage_summary_api_only
				ReaderTranslationMode.LOCAL_FIRST -> R.string.ai_api_usage_summary_local_first
			},
		)
	}

	private fun fetchAndPickApiModel() {
		fetchModelsJob?.cancel()
		fetchModelsJob = viewLifecycleOwner.lifecycleScope.launch {
			val pref = findPreference<Preference>(AppSettings.KEY_READER_TRANSLATION_API_FETCH_MODELS)
			pref?.isEnabled = false
			pref?.summary = getString(R.string.loading_)
			try {
				val endpoint = settings.readerTranslationApiEndpoint.trim()
				if (endpoint.isBlank()) {
					Toast.makeText(requireContext(), R.string.reader_translation_api_endpoint_missing, Toast.LENGTH_SHORT).show()
					return@launch
				}
				val modelsUrl = TranslationApiSettingsSupport.buildModelsUrl(endpoint)
				val key = settings.readerTranslationApiKey.trim()
				val models = withContext(Dispatchers.IO) {
					val requestBuilder = Request.Builder().get().url(modelsUrl)
					if (key.isNotBlank()) {
						requestBuilder.header("Authorization", "Bearer $key")
						requestBuilder.header("X-API-Key", key)
					}
					okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
						if (!response.isSuccessful) return@withContext emptyList<String>()
						val body = response.body?.string().orEmpty()
						TranslationApiSettingsSupport.parseModelIds(body)
					}
				}
				if (models.isEmpty()) {
					Toast.makeText(requireContext(), R.string.reader_translation_api_models_fetch_failed, Toast.LENGTH_SHORT).show()
					return@launch
				}
				showModelPickerDialog(models)
			} catch (e: Throwable) {
				e.printStackTrace()
				Toast.makeText(requireContext(), R.string.reader_translation_api_models_fetch_failed, Toast.LENGTH_SHORT).show()
			} finally {
				pref?.isEnabled = true
				pref?.summary = getString(R.string.reader_translation_api_models_fetch_summary)
			}
		}
	}

	private fun showModelPickerDialog(models: List<String>) {
		val current = settings.readerTranslationApiModel.trim()
		val selected = models.indexOf(current).coerceAtLeast(0)
		MaterialAlertDialogBuilder(requireContext())
			.setTitle(R.string.reader_translation_api_models_pick_title)
			.setSingleChoiceItems(models.toTypedArray(), selected) { dialog, which ->
				val chosen = models.getOrNull(which).orEmpty()
				if (chosen.isNotBlank()) {
					PreferenceManager.getDefaultSharedPreferences(requireContext()).edit {
						putString(AppSettings.KEY_READER_TRANSLATION_API_MODEL, chosen)
					}
				}
				dialog.dismiss()
			}
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}
}
