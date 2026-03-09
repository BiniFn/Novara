package org.skepsun.kototoro.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.network.MangaHttpClient
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.ReaderOcrEngine
import org.skepsun.kototoro.core.prefs.ReaderTranslationMode
import org.skepsun.kototoro.core.ui.BasePreferenceFragment
import org.skepsun.kototoro.reader.translate.data.NcnnModelManager
import org.skepsun.kototoro.reader.translate.data.NcnnOfficialModelCatalog
import org.skepsun.kototoro.reader.translate.data.OnnxModelManager
import org.skepsun.kototoro.reader.translate.data.OnnxOfficialModelCatalog
import org.skepsun.kototoro.reader.translate.data.PaddleOfficialModelCatalog
import org.skepsun.kototoro.reader.translate.data.TfliteModelManager
import org.skepsun.kototoro.reader.translate.data.TfliteOfficialModelCatalog
import javax.inject.Inject

@AndroidEntryPoint
class TranslationSettingsFragment :
	BasePreferenceFragment(R.string.translation_settings),
	SharedPreferences.OnSharedPreferenceChangeListener {

	@Inject
	lateinit var tfliteModelManager: TfliteModelManager

	@Inject
	lateinit var ncnnModelManager: NcnnModelManager

	@Inject
	lateinit var onnxModelManager: OnnxModelManager

	@Inject
	@MangaHttpClient
	lateinit var okHttpClient: OkHttpClient

	private var fetchModelsJob: Job? = null

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_translation)

		findPreference<ListPreference>(AppSettings.KEY_READER_TRANSLATION_MODE)?.run {
			entryValues = ReaderTranslationMode.entries.map { it.name }.toTypedArray()
			setDefaultValue(ReaderTranslationMode.LOCAL_FIRST.name)
		}
		findPreference<ListPreference>(AppSettings.KEY_READER_TRANSLATION_OCR_ENGINE)?.run {
			entryValues = arrayOf(
				ReaderOcrEngine.MLKIT.name,
				ReaderOcrEngine.TFLITE.name,
				ReaderOcrEngine.HYBRID.name,
				ReaderOcrEngine.NCNN.name,
			)
			setDefaultValue(ReaderOcrEngine.MLKIT.name)
		}
		findPreference<ListPreference>(AppSettings.KEY_READER_TRANSLATION_API_PROVIDER_PRESET)?.run {
			setDefaultValue("CUSTOM")
		}
		findPreference<ListPreference>(AppSettings.KEY_READER_TRANSLATION_BUBBLE_GROUPING_TUNING)?.run {
			setDefaultValue("BALANCED")
		}
		findPreference<ListPreference>(AppSettings.KEY_READER_TRANSLATION_OVERLAY_COMPACTNESS)?.run {
			setDefaultValue("BALANCED")
		}
		findPreference<SwitchPreferenceCompat>(AppSettings.KEY_READER_TRANSLATION_PADDLE_OCR_ONLY)?.run {
			setDefaultValue(true)
		}

		normalizeDeprecatedOcrEngineSelection()
		updateOcrEngineDependency()
		updateApiPreferenceVisibility()
		updatePaddleOfficialModelEntries()
		updateTfliteOfficialModelEntries()
		updateNcnnOfficialModelEntries()
		updateOnnxOfficialModelEntries()
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
			AppSettings.KEY_READER_TRANSLATION_OCR_ENGINE -> {
				updateOcrEngineDependency()
				updatePaddleOfficialModelEntries()
				updateTfliteOfficialModelEntries()
				updateNcnnOfficialModelEntries()
				updateOnnxOfficialModelEntries()
			}
			AppSettings.KEY_READER_TRANSLATION_PADDLE_OFFICIAL_MODEL_ID -> {
				applyOfficialPaddleModel()
				updatePaddleOfficialModelEntries()
			}
			AppSettings.KEY_READER_TRANSLATION_TFLITE_MODEL_ID -> {
				applyOfficialTfliteModel()
				updateTfliteOfficialModelEntries()
			}
			AppSettings.KEY_READER_TRANSLATION_NCNN_MODEL_ID -> {
				updateNcnnOfficialModelEntries()
			}
			AppSettings.KEY_READER_TRANSLATION_ONNX_MODEL_ID -> {
				updateOnnxOfficialModelEntries()
			}
			AppSettings.KEY_READER_TRANSLATION_MODE -> {
				updateApiPreferenceVisibility()
				updateOnnxOfficialModelEntries()
			}
			AppSettings.KEY_READER_TRANSLATION_API_PROVIDER_PRESET -> {
				applyApiProviderPreset()
			}
			AppSettings.KEY_READER_TRANSLATION_PADDLE_MODEL_URL,
			AppSettings.KEY_READER_TRANSLATION_PADDLE_MODEL_VERSION,
			AppSettings.KEY_READER_TRANSLATION_PADDLE_MODEL_SHA256 -> applyOfficialPaddleModel()
		}
	}

	private fun updateOcrEngineDependency() {
		val isTflite = settings.readerTranslationOcrEngine == ReaderOcrEngine.TFLITE
		val isHybrid = settings.readerTranslationOcrEngine == ReaderOcrEngine.HYBRID
		val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

		if (isHybrid || isTflite) {
			if (sharedPreferences.getString(AppSettings.KEY_READER_TRANSLATION_TFLITE_MODEL_URL, "").isNullOrBlank()) {
				sharedPreferences.edit {
					putString(
						AppSettings.KEY_READER_TRANSLATION_TFLITE_MODEL_URL,
						getString(R.string.reader_translation_tflite_model_url_default),
					)
				}
			}
		}
	}

	private fun updateApiPreferenceVisibility() {
		val showApi = settings.readerTranslationMode != ReaderTranslationMode.LOCAL_ONLY
		findPreference<Preference>(AppSettings.KEY_READER_TRANSLATION_API_ENDPOINT)?.isVisible = showApi
		findPreference<Preference>(AppSettings.KEY_READER_TRANSLATION_API_KEY)?.isVisible = showApi
		findPreference<Preference>(AppSettings.KEY_READER_TRANSLATION_API_PROVIDER_PRESET)?.isVisible = showApi
		findPreference<Preference>(AppSettings.KEY_READER_TRANSLATION_API_MODEL)?.isVisible = showApi
		findPreference<Preference>(AppSettings.KEY_READER_TRANSLATION_API_FETCH_MODELS)?.isVisible = showApi
	}

	private fun applyOfficialPaddleModel() {
		val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
		val modelId = sharedPreferences.getString(AppSettings.KEY_READER_TRANSLATION_PADDLE_OFFICIAL_MODEL_ID, null)
		val model = PaddleOfficialModelCatalog.findById(modelId)
		sharedPreferences.edit {
			if (model == null) {
				remove(AppSettings.KEY_READER_TRANSLATION_PADDLE_MODEL_URL)
				remove(AppSettings.KEY_READER_TRANSLATION_PADDLE_MODEL_VERSION)
				remove(AppSettings.KEY_READER_TRANSLATION_PADDLE_MODEL_SHA256)
			} else {
				putString(AppSettings.KEY_READER_TRANSLATION_PADDLE_MODEL_URL, model.downloadUrl)
				putString(AppSettings.KEY_READER_TRANSLATION_PADDLE_MODEL_VERSION, model.version)
				remove(AppSettings.KEY_READER_TRANSLATION_PADDLE_MODEL_SHA256)
			}
		}
	}

	private fun updatePaddleOfficialModelEntries() {
		findPreference<ListPreference>(AppSettings.KEY_READER_TRANSLATION_PADDLE_OFFICIAL_MODEL_ID)?.run {
			isVisible = false
		}
	}

	private fun updateTfliteOfficialModelEntries() {
		val isTflite = settings.readerTranslationOcrEngine == ReaderOcrEngine.TFLITE
		val isHybrid = settings.readerTranslationOcrEngine == ReaderOcrEngine.HYBRID
		val models = TfliteOfficialModelCatalog.models

		findPreference<ListPreference>(AppSettings.KEY_READER_TRANSLATION_TFLITE_MODEL_ID)?.run {
			entries = arrayOf(getString(R.string.disabled)) + models.map { model ->
				val suffix = if (tfliteModelManager.isModelDownloaded(model.version)) ""
				else getString(R.string.reader_translation_ocr_model_selection_not_downloaded_suffix)
				model.title + suffix
			}.toTypedArray()
			entryValues = arrayOf("") + models.map { it.id }.toTypedArray()
			setDefaultValue("")
			summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
			val values = entryValues?.map { it.toString() }.orEmpty()
			if (value != null && value !in values) {
				value = ""
				applyOfficialTfliteModel()
			}
			isVisible = isTflite || isHybrid
		}
	}

	private fun updateNcnnOfficialModelEntries() {
		val isNcnnFamily = when (settings.readerTranslationOcrEngine) {
			ReaderOcrEngine.HYBRID,
			ReaderOcrEngine.NCNN,
			-> true
			else -> false
		}
		val models = NcnnOfficialModelCatalog.models

		findPreference<ListPreference>(AppSettings.KEY_READER_TRANSLATION_NCNN_MODEL_ID)?.run {
			entries = arrayOf(getString(R.string.disabled)) + models.map { model ->
				val suffix = if (ncnnModelManager.isModelDownloaded(model.version)) ""
				else getString(R.string.reader_translation_ocr_model_selection_not_downloaded_suffix)
				model.title + suffix
			}.toTypedArray()
			entryValues = arrayOf("") + models.map { it.id }.toTypedArray()
			setDefaultValue("")
			summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
			val values = entryValues?.map { it.toString() }.orEmpty()
			if (value != null && value !in values) {
				value = ""
			}
			isVisible = isNcnnFamily
		}
	}

	private fun updateOnnxOfficialModelEntries() {
		val models = OnnxOfficialModelCatalog.models
		findPreference<ListPreference>(AppSettings.KEY_READER_TRANSLATION_ONNX_MODEL_ID)?.run {
			entries = arrayOf(getString(R.string.disabled)) + models.map { model ->
				val suffix = if (onnxModelManager.isModelDownloaded(model.id)) ""
				else getString(R.string.reader_translation_ocr_model_selection_not_downloaded_suffix)
				model.title + suffix
			}.toTypedArray()
			entryValues = arrayOf("") + models.map { it.id }.toTypedArray()
			setDefaultValue("")
			summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
			val values = entryValues?.map { it.toString() }.orEmpty()
			if (value != null && value !in values) {
				value = ""
			}
			isVisible = settings.readerTranslationMode != ReaderTranslationMode.API_ONLY
		}
	}

	private fun normalizeDeprecatedOcrEngineSelection() {
		val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
		val raw = sharedPreferences.getString(AppSettings.KEY_READER_TRANSLATION_OCR_ENGINE, ReaderOcrEngine.MLKIT.name)
		if (raw == ReaderOcrEngine.PADDLE.name) {
			sharedPreferences.edit {
				putString(AppSettings.KEY_READER_TRANSLATION_OCR_ENGINE, ReaderOcrEngine.NCNN.name)
			}
		}
	}

	private fun applyOfficialTfliteModel() {
		val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
		val modelId = sharedPreferences.getString(AppSettings.KEY_READER_TRANSLATION_TFLITE_MODEL_ID, null)
		val model = TfliteOfficialModelCatalog.findById(modelId)
		sharedPreferences.edit {
			if (model == null) {
				remove(AppSettings.KEY_READER_TRANSLATION_TFLITE_MODEL_URL)
				remove(AppSettings.KEY_READER_TRANSLATION_TFLITE_MODEL_PATH)
			} else {
				putString(AppSettings.KEY_READER_TRANSLATION_TFLITE_MODEL_URL, model.encoderUrl)
			}
		}
	}

	private fun applyApiProviderPreset() {
		val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
		val preset = sharedPreferences.getString(AppSettings.KEY_READER_TRANSLATION_API_PROVIDER_PRESET, "CUSTOM")
		val endpointAndModel = when (preset) {
			"OPENAI" -> "https://api.openai.com/v1/chat/completions" to "gpt-4o-mini"
			"DEEPSEEK" -> "https://api.deepseek.com/chat/completions" to "deepseek-chat"
			"ANTHROPIC" -> "https://api.anthropic.com/v1/chat/completions" to "claude-sonnet-4-6"
			"GEMINI" -> "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions" to "gemini-3-flash-preview"
			"OPENROUTER" -> "https://openrouter.ai/api/v1/chat/completions" to "openai/gpt-4o-mini"
			else -> null
		}
		endpointAndModel ?: return
		sharedPreferences.edit {
			putString(AppSettings.KEY_READER_TRANSLATION_API_ENDPOINT, endpointAndModel.first)
			putString(AppSettings.KEY_READER_TRANSLATION_API_MODEL, endpointAndModel.second)
		}
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
				val modelsUrl = buildModelsUrl(endpoint)
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
						parseModelIds(body)
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

	private fun buildModelsUrl(endpoint: String): String {
		val trimmed = endpoint.trim().trimEnd('/')
		return when {
			trimmed.endsWith("/v1/chat/completions", ignoreCase = true) -> trimmed.removeSuffix("/v1/chat/completions") + "/v1/models"
			trimmed.endsWith("/chat/completions", ignoreCase = true) -> trimmed.removeSuffix("/chat/completions") + "/models"
			trimmed.endsWith("/v1", ignoreCase = true) -> "$trimmed/models"
			trimmed.endsWith("/models", ignoreCase = true) -> trimmed
			else -> "$trimmed/models"
		}
	}

	private fun parseModelIds(body: String): List<String> {
		if (body.isBlank()) return emptyList()
		val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
		val data = root.optJSONArray("data") ?: return emptyList()
		val ids = linkedSetOf<String>()
		for (i in 0 until data.length()) {
			val id = data.optJSONObject(i)?.optString("id").orEmpty().trim()
			if (id.isNotBlank()) ids.add(id)
		}
		return ids.toList().sorted()
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
