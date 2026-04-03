package org.skepsun.kototoro.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Toast
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
import org.skepsun.kototoro.core.ui.BasePreferenceFragment
import org.skepsun.kototoro.settings.support.TranslationApiSettingsSupport
import javax.inject.Inject
import org.json.JSONObject
import org.json.JSONArray

@AndroidEntryPoint
class TranslationEndToEndApiSettingsFragment :
	BasePreferenceFragment(R.string.translation_settings),
	SharedPreferences.OnSharedPreferenceChangeListener {

	@Inject
	@ContentHttpClient
	lateinit var okHttpClient: OkHttpClient

	private var fetchModelsJob: Job? = null

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_translation_e2e_api)

		findPreference<ListPreference>(AppSettings.KEY_READER_E2E_API_PROVIDER_PRESET)?.run {
			setDefaultValue("GEMINI")
			setOnPreferenceChangeListener { _, newValue ->
				val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
				TranslationApiSettingsSupport.applyApiProviderPreset(
					sharedPreferences = preferences,
					presetInput = (newValue as? String).orEmpty(),
					forceOverride = true,
					endpointKey = AppSettings.KEY_READER_E2E_API_ENDPOINT,
					modelKey = AppSettings.KEY_READER_E2E_API_MODEL,
				)
				true
			}
		}

		findPreference<EditTextPreference>(AppSettings.KEY_READER_E2E_API_MODEL)?.run {
			setDefaultValue("gemini-2.0-flash")
		}
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
			AppSettings.KEY_READER_E2E_API_FETCH_MODELS -> {
				fetchAndPickApiModel()
				true
			}
			else -> super.onPreferenceTreeClick(preference)
		}
	}

	override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
		when (key) {
			AppSettings.KEY_READER_E2E_API_PROVIDER_PRESET -> {
				val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
				TranslationApiSettingsSupport.applyApiProviderPreset(
					sharedPreferences = preferences,
					presetInput = settings.readerE2eApiProviderPreset,
					forceOverride = true,
					endpointKey = AppSettings.KEY_READER_E2E_API_ENDPOINT,
					modelKey = AppSettings.KEY_READER_E2E_API_MODEL,
				)
			}
		}
	}

	private fun fetchAndPickApiModel() {
		if (fetchModelsJob?.isActive == true) return

		val endpoint = settings.readerE2eApiEndpoint
		val apiKey = settings.readerE2eApiKey
		if (endpoint.isEmpty() || apiKey.isEmpty()) {
			Toast.makeText(requireContext(), R.string.reader_translation_api_endpoint_missing, Toast.LENGTH_SHORT).show()
			return
		}

		// Similar to TranslationApiSettingsFragment fetch implementation
		val url = endpoint.removeSuffix("/chat/completions").removeSuffix("/") + "/models"

		val request = Request.Builder()
			.url(url)
			.get()
			.header("Authorization", "Bearer $apiKey")
			.build()

		fetchModelsJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
			try {
				val response = okHttpClient.newCall(request).execute()
				val bodyStr = response.body?.string()

				if (!response.isSuccessful || bodyStr == null) {
					withContext(Dispatchers.Main) {
						Toast.makeText(requireContext(), R.string.reader_translation_api_models_fetch_failed, Toast.LENGTH_SHORT).show()
					}
					return@launch
				}

				val json = JSONObject(bodyStr)
				val dataArr = json.optJSONArray("data") ?: JSONArray()
				val models = mutableListOf<String>()
				for (i in 0 until dataArr.length()) {
					val obj = dataArr.optJSONObject(i)
					val id = obj?.optString("id")
					if (!id.isNullOrBlank()) {
						models.add(id)
					}
				}

				models.sort()

				withContext(Dispatchers.Main) {
					if (models.isEmpty()) {
						Toast.makeText(requireContext(), R.string.reader_translation_api_models_fetch_failed, Toast.LENGTH_SHORT).show()
						return@withContext
					}
					MaterialAlertDialogBuilder(requireContext())
						.setTitle(R.string.reader_translation_api_models_fetch)
						.setItems(models.toTypedArray()) { _, which ->
							val chosen = models[which]
							findPreference<EditTextPreference>(AppSettings.KEY_READER_E2E_API_MODEL)?.text = chosen
						}
						.setNegativeButton(android.R.string.cancel, null)
						.show()
				}

			} catch (e: Exception) {
				withContext(Dispatchers.Main) {
					Toast.makeText(requireContext(), R.string.reader_translation_api_models_fetch_failed, Toast.LENGTH_SHORT).show()
				}
			}
		}
	}
}
