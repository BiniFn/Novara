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
import org.skepsun.kototoro.reader.translate.data.OnnxModelCategory
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
				ReaderOcrEngine.HYBRID.name,
			)
			setDefaultValue(ReaderOcrEngine.MLKIT.name)
		}
		findPreference<ListPreference>(AppSettings.KEY_READER_TRANSLATION_API_PROVIDER_PRESET)?.run {
			setDefaultValue("CUSTOM")
			setOnPreferenceChangeListener { _, newValue ->
				applyApiProviderPreset((newValue as? String).orEmpty(), forceOverride = true)
				true
			}
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
		applyApiProviderPreset(settings.readerTranslationApiProviderPreset)
		updateOcrEngineDependency()
		updateHybridFallbackThresholdPreference()
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
			AppSettings.KEY_READER_TRANSLATION_HYBRID_FALLBACK_THRESHOLD -> {
				showHybridFallbackThresholdDialog()
				true
			}
			else -> super.onPreferenceTreeClick(preference)
		}
	}

	override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
		when (key) {
			AppSettings.KEY_READER_TRANSLATION_OCR_ENGINE -> {
				updateOcrEngineDependency()
				updateHybridFallbackThresholdPreference()
				updatePaddleOfficialModelEntries()
				updateTfliteOfficialModelEntries()
				updateNcnnOfficialModelEntries()
				updateOnnxOfficialModelEntries()
			}
			AppSettings.KEY_READER_TRANSLATION_HYBRID_FALLBACK_THRESHOLD -> {
				updateHybridFallbackThresholdPreference()
			}
			AppSettings.KEY_READER_TRANSLATION_PADDLE_OFFICIAL_MODEL_ID -> {
				applyOfficialPaddleModel()
				updatePaddleOfficialModelEntries()
			}
			AppSettings.KEY_READER_TRANSLATION_REC_MODEL_ID -> {
				applyOfficialTfliteModel()
				updateTfliteOfficialModelEntries()
			}
			AppSettings.KEY_READER_TRANSLATION_DET_MODEL_ID -> {
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
				applyApiProviderPreset(settings.readerTranslationApiProviderPreset, forceOverride = true)
			}
			AppSettings.KEY_READER_TRANSLATION_PADDLE_MODEL_URL,
			AppSettings.KEY_READER_TRANSLATION_PADDLE_MODEL_VERSION,
			AppSettings.KEY_READER_TRANSLATION_PADDLE_MODEL_SHA256 -> applyOfficialPaddleModel()
		}
	}

	private fun updateOcrEngineDependency() {
		val isHybrid = settings.readerTranslationOcrEngine == ReaderOcrEngine.HYBRID
		val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

		if (isHybrid) {
			if (sharedPreferences.getString(AppSettings.KEY_READER_TRANSLATION_REC_MODEL_URL, "").isNullOrBlank()) {
				sharedPreferences.edit {
					putString(
						AppSettings.KEY_READER_TRANSLATION_REC_MODEL_URL,
						getString(R.string.reader_translation_rec_model_url_default),
					)
				}
			}
		}
		findPreference<Preference>(AppSettings.KEY_READER_TRANSLATION_HYBRID_FALLBACK_THRESHOLD)?.isVisible = isHybrid
	}

	private fun updateHybridFallbackThresholdPreference() {
		findPreference<Preference>(AppSettings.KEY_READER_TRANSLATION_HYBRID_FALLBACK_THRESHOLD)?.apply {
			isVisible = settings.readerTranslationOcrEngine == ReaderOcrEngine.HYBRID
			summary = getString(
				R.string.reader_translation_hybrid_fallback_threshold_summary,
				(settings.readerTranslationHybridFallbackThreshold * 100).toInt(),
			)
		}
	}

	private fun showHybridFallbackThresholdDialog() {
		val values = floatArrayOf(0.7f, 0.75f, 0.8f, 0.85f, 0.9f, 0.95f)
		val labels = values.map { "${(it * 100).toInt()}%" }.toTypedArray()
		val current = settings.readerTranslationHybridFallbackThreshold
		val selected = values.indexOfFirst { kotlin.math.abs(it - current) < 0.001f }
			.takeIf { it >= 0 } ?: values.indices.minBy { kotlin.math.abs(values[it] - current) }
		MaterialAlertDialogBuilder(requireContext())
			.setTitle(R.string.reader_translation_hybrid_fallback_threshold)
			.setSingleChoiceItems(labels, selected) { dialog, which ->
				settings.readerTranslationHybridFallbackThreshold = values[which]
				dialog.dismiss()
			}
			.setNegativeButton(android.R.string.cancel, null)
			.show()
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
		val isHybrid = settings.readerTranslationOcrEngine == ReaderOcrEngine.HYBRID
		val models = TfliteOfficialModelCatalog.models

		findPreference<ListPreference>(AppSettings.KEY_READER_TRANSLATION_REC_MODEL_ID)?.run {
			entries = arrayOf(
				getString(R.string.reader_translation_ocr_model_rec_ppocr_v5),
				getString(R.string.reader_translation_ocr_model_rec_autodetect),
			) + models.map { model ->
				val suffix = if (tfliteModelManager.isModelDownloaded(model.version)) ""
				else getString(R.string.reader_translation_ocr_model_selection_not_downloaded_suffix)
				model.title + suffix
			}.toTypedArray()
			entryValues = arrayOf("PPOCR_V5_REC", "AUTO") + models.map { it.id }.toTypedArray()
			setDefaultValue("PPOCR_V5_REC")
			summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
			val values = entryValues?.map { it.toString() }.orEmpty()
			if (value != null && value !in values) {
				value = "PPOCR_V5_REC"
				applyOfficialTfliteModel()
			}
			isVisible = isHybrid
		}
	}

	private fun updateNcnnOfficialModelEntries() {
		val isNcnnFamily = settings.readerTranslationOcrEngine == ReaderOcrEngine.HYBRID
		val models = NcnnOfficialModelCatalog.models

		findPreference<ListPreference>(AppSettings.KEY_READER_TRANSLATION_DET_MODEL_ID)?.run {
			entries = arrayOf(getString(R.string.reader_translation_ocr_model_det_autodetect)) + models.map { model ->
				val suffix = if (ncnnModelManager.isModelDownloaded(model.version)) ""
				else getString(R.string.reader_translation_ocr_model_selection_not_downloaded_suffix)
				model.title + suffix
			}.toTypedArray()
			entryValues = arrayOf("AUTO") + models.map { it.id }.toTypedArray()
			setDefaultValue("AUTO")
			summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
			val values = entryValues?.map { it.toString() }.orEmpty()
			if (value != null && value !in values) {
					value = "AUTO"
			}
			isVisible = isNcnnFamily
		}
	}

	private fun updateOnnxOfficialModelEntries() {
		val models = OnnxOfficialModelCatalog.models
		findPreference<ListPreference>(AppSettings.KEY_READER_TRANSLATION_ONNX_MODEL_ID)?.run {
			entries = arrayOf(getString(R.string.reader_translation_local_model_mlkit)) + models.map { model ->
				val suffix = if (onnxModelManager.isModelDownloaded(model.id)) ""
				else getString(R.string.reader_translation_ocr_model_selection_not_downloaded_suffix)
				val title = when (model.category) {
					OnnxModelCategory.GENERAL_LLM -> "${model.title} [LLM]"
					OnnxModelCategory.CLASSIC_TRANSLATION -> model.title
				}
				title + suffix
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
		if (raw == ReaderOcrEngine.PADDLE.name || raw == ReaderOcrEngine.TFLITE.name || raw == ReaderOcrEngine.NCNN.name) {
			sharedPreferences.edit {
				putString(AppSettings.KEY_READER_TRANSLATION_OCR_ENGINE, ReaderOcrEngine.HYBRID.name)
			}
		}
	}

	private fun applyOfficialTfliteModel() {
		val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
		val modelId = sharedPreferences.getString(AppSettings.KEY_READER_TRANSLATION_REC_MODEL_ID, null)
		val model = TfliteOfficialModelCatalog.findById(modelId)
		sharedPreferences.edit {
			if (model == null || modelId == "PPOCR_V5_REC") {
				remove(AppSettings.KEY_READER_TRANSLATION_REC_MODEL_URL)
				remove(AppSettings.KEY_READER_TRANSLATION_REC_MODEL_PATH)
			} else {
				putString(AppSettings.KEY_READER_TRANSLATION_REC_MODEL_URL, model.encoderUrl)
			}
		}
	}

	private fun applyApiProviderPreset(presetInput: String, forceOverride: Boolean = false) {
		val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
		val preset = presetInput.trim().uppercase()
		if (preset.isBlank() || preset == "CUSTOM") return
		val endpointAndModel = when (preset) {
			"OPENAI" -> "https://api.openai.com/v1/chat/completions" to "gpt-4o-mini"
			"DEEPSEEK" -> "https://api.deepseek.com/chat/completions" to "deepseek-chat"
			"ZHIPU" -> "https://open.bigmodel.cn/api/paas/v4/chat/completions" to "glm-4-plus"
			"ALIBABA" -> "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions" to "qwen-plus"
			"MOONSHOT" -> "https://api.moonshot.cn/v1/chat/completions" to "moonshot-v1-8k"
			"MINIMAX" -> "https://api.minimax.chat/v1/text/chatcompletion_v2" to "minimax-m2.5"
			"BAIDU" -> "https://qianfan.baidubce.com/v2/chat/completions" to "ernie-4.0-8k"
			"ANTHROPIC" -> "https://api.anthropic.com/v1/chat/completions" to "claude-sonnet-4-6"
			"GEMINI" -> "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions" to "gemini-3-flash-preview"
			"OPENROUTER" -> "https://openrouter.ai/api/v1/chat/completions" to "openai/gpt-4o-mini"
			else -> null
		}
		endpointAndModel ?: return
		val currentEndpoint = sharedPreferences.getString(AppSettings.KEY_READER_TRANSLATION_API_ENDPOINT, "").orEmpty().trim()
		val currentModel = sharedPreferences.getString(AppSettings.KEY_READER_TRANSLATION_API_MODEL, "").orEmpty().trim()
		sharedPreferences.edit {
			if (forceOverride || currentEndpoint.isBlank()) {
				putString(AppSettings.KEY_READER_TRANSLATION_API_ENDPOINT, endpointAndModel.first)
			}
			if (forceOverride || currentModel.isBlank()) {
				putString(AppSettings.KEY_READER_TRANSLATION_API_MODEL, endpointAndModel.second)
			}
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
