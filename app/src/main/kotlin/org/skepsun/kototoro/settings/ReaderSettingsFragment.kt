package org.skepsun.kototoro.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
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
import org.skepsun.kototoro.core.model.ZoomMode
import org.skepsun.kototoro.core.network.MangaHttpClient
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.ReaderAnimation
import org.skepsun.kototoro.core.prefs.ReaderBackground
import org.skepsun.kototoro.core.prefs.ReaderControl
import org.skepsun.kototoro.core.prefs.ReaderMode
import org.skepsun.kototoro.core.prefs.ReaderOcrEngine
import org.skepsun.kototoro.core.prefs.ReaderTranslationMode
import org.skepsun.kototoro.core.ui.BasePreferenceFragment
import org.skepsun.kototoro.core.util.ext.setDefaultValueCompat
import org.skepsun.kototoro.parsers.util.mapToSet
import org.skepsun.kototoro.parsers.util.names
import org.skepsun.kototoro.reader.translate.data.TfliteModelManager
import org.skepsun.kototoro.reader.translate.data.NcnnModelManager
import org.skepsun.kototoro.reader.translate.data.OnnxModelManager
import org.skepsun.kototoro.reader.translate.data.OnnxModelCategory
import org.skepsun.kototoro.reader.translate.data.OnnxOfficialModelCatalog
import org.skepsun.kototoro.reader.translate.data.PaddleOfficialModelCatalog
import org.skepsun.kototoro.reader.translate.data.TfliteOfficialModelCatalog
import org.skepsun.kototoro.reader.translate.data.NcnnOfficialModelCatalog
import org.skepsun.kototoro.settings.utils.MultiSummaryProvider
import org.skepsun.kototoro.settings.utils.PercentSummaryProvider
import org.skepsun.kototoro.settings.utils.SliderPreference
import org.skepsun.kototoro.core.util.ext.viewLifecycleScope
import org.skepsun.kototoro.core.util.ext.printStackTraceDebug
import eu.kanade.tachiyomi.network.await
import javax.inject.Inject

@AndroidEntryPoint
class ReaderSettingsFragment :
	BasePreferenceFragment(R.string.reader_settings),
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
		addPreferencesFromResource(R.xml.pref_reader)
		findPreference<ListPreference>(AppSettings.KEY_READER_MODE)?.run {
			entryValues = ReaderMode.entries.names()
			setDefaultValueCompat(ReaderMode.STANDARD.name)
		}
		findPreference<ListPreference>(AppSettings.KEY_READER_ORIENTATION)?.run {
			entryValues = arrayOf(
				ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED.toString(),
				ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR.toString(),
				ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT.toString(),
				ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE.toString(),
			)
			setDefaultValueCompat(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED.toString())
		}
		findPreference<MultiSelectListPreference>(AppSettings.KEY_READER_CONTROLS)?.run {
			entryValues = ReaderControl.entries.names()
			setDefaultValueCompat(ReaderControl.DEFAULT.mapToSet { it.name })
			summaryProvider = MultiSummaryProvider(R.string.none)
		}
		findPreference<ListPreference>(AppSettings.KEY_READER_BACKGROUND)?.run {
			entryValues = ReaderBackground.entries.names()
			setDefaultValueCompat(ReaderBackground.DEFAULT.name)
		}
		findPreference<ListPreference>(AppSettings.KEY_READER_ANIMATION)?.run {
			entryValues = ReaderAnimation.entries.names()
			setDefaultValueCompat(ReaderAnimation.DEFAULT.name)
		}
		findPreference<ListPreference>(AppSettings.KEY_ZOOM_MODE)?.run {
			entryValues = ZoomMode.entries.names()
			setDefaultValueCompat(ZoomMode.FIT_CENTER.name)
		}
		findPreference<ListPreference>(AppSettings.KEY_READER_TRANSLATION_MODE)?.run {
			entryValues = ReaderTranslationMode.entries.names()
			setDefaultValueCompat(ReaderTranslationMode.LOCAL_FIRST.name)
		}
			findPreference<ListPreference>(AppSettings.KEY_READER_TRANSLATION_OCR_ENGINE)?.run {
				entryValues = arrayOf(
					ReaderOcrEngine.MLKIT.name,
					ReaderOcrEngine.HYBRID.name,
				)
				setDefaultValueCompat(ReaderOcrEngine.MLKIT.name)
			}
		findPreference<ListPreference>(AppSettings.KEY_READER_TRANSLATION_API_PROVIDER_PRESET)?.run {
			setDefaultValueCompat("CUSTOM")
		}
		findPreference<ListPreference>(AppSettings.KEY_READER_TRANSLATION_BUBBLE_GROUPING_TUNING)?.run {
			setDefaultValueCompat("BALANCED")
		}
		findPreference<ListPreference>(AppSettings.KEY_READER_TRANSLATION_OVERLAY_COMPACTNESS)?.run {
			setDefaultValueCompat("BALANCED")
		}
		findPreference<SwitchPreferenceCompat>(AppSettings.KEY_READER_TRANSLATION_PADDLE_OCR_ONLY)?.run {
			setDefaultValue(true)
		}
		normalizeDeprecatedOcrEngineSelection()
		updatePaddleOfficialModelEntries()
		updateTfliteOfficialModelEntries()
		updateNcnnOfficialModelEntries()
		updateOnnxOfficialModelEntries()
		findPreference<MultiSelectListPreference>(AppSettings.KEY_READER_CROP)?.run {
			summaryProvider = MultiSummaryProvider(R.string.disabled)
		}
		findPreference<SliderPreference>(AppSettings.KEY_WEBTOON_ZOOM_OUT)?.summaryProvider = PercentSummaryProvider()
		updateReaderModeDependency()
		updateOcrEngineDependency()
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
			AppSettings.KEY_READER_TAP_ACTIONS -> {
				router.openReaderTapGridSettings()
				true
			}
			AppSettings.KEY_READER_TRANSLATION_API_FETCH_MODELS -> {
				fetchAndPickApiModel()
				true
			}


			else -> super.onPreferenceTreeClick(preference)
		}
	}

	override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
		when (key) {
			AppSettings.KEY_READER_MODE -> updateReaderModeDependency()
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
		val isHybrid = settings.readerTranslationOcrEngine == ReaderOcrEngine.HYBRID

		val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

		findPreference<Preference>("reader_translation_advanced_settings")?.isVisible = false

		// Auto-fill defaults if empty
		if (isHybrid) {
			if (sharedPreferences.getString(AppSettings.KEY_READER_TRANSLATION_REC_MODEL_URL, "").isNullOrBlank()) {
				sharedPreferences.edit().apply {
					putString(AppSettings.KEY_READER_TRANSLATION_REC_MODEL_URL, getString(R.string.reader_translation_rec_model_url_default))
					apply()
				}
			}
		}
	}

	private fun updateReaderModeDependency() {
		findPreference<Preference>(AppSettings.KEY_READER_MODE_DETECT)?.run {
			isEnabled = settings.defaultReaderMode != ReaderMode.WEBTOON
		}
	}

	private fun applyOfficialPaddleModel() {
		val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
		val modelId = sharedPreferences.getString(AppSettings.KEY_READER_TRANSLATION_PADDLE_OFFICIAL_MODEL_ID, null)
		val model = PaddleOfficialModelCatalog.findById(modelId)
		sharedPreferences.edit().apply {
			if (model == null) {
				remove(AppSettings.KEY_READER_TRANSLATION_PADDLE_MODEL_URL)
				remove(AppSettings.KEY_READER_TRANSLATION_PADDLE_MODEL_VERSION)
				remove(AppSettings.KEY_READER_TRANSLATION_PADDLE_MODEL_SHA256)
			} else {
				putString(AppSettings.KEY_READER_TRANSLATION_PADDLE_MODEL_URL, model.downloadUrl)
				putString(AppSettings.KEY_READER_TRANSLATION_PADDLE_MODEL_VERSION, model.version)
				remove(AppSettings.KEY_READER_TRANSLATION_PADDLE_MODEL_SHA256)
			}
			apply()
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
			setDefaultValueCompat("PPOCR_V5_REC")
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
			setDefaultValueCompat("AUTO")
			summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
			val values = entryValues?.map { it.toString() }.orEmpty()
			if (value != null && value !in values) {
					value = "AUTO"
			}
			isVisible = isNcnnFamily
		}
	}

	private fun updateOnnxOfficialModelEntries() {
		val models = OnnxOfficialModelCatalog.models.filter { model ->
			model.category == OnnxModelCategory.CLASSIC_TRANSLATION ||
				model.category == OnnxModelCategory.GENERAL_LLM
		}
		findPreference<ListPreference>(AppSettings.KEY_READER_TRANSLATION_ONNX_MODEL_ID)?.run {
			entries = arrayOf(getString(R.string.reader_translation_local_model_mlkit)) + models.map { model ->
				val suffix = if (onnxModelManager.isModelDownloaded(model.id)) ""
				else getString(R.string.reader_translation_ocr_model_selection_not_downloaded_suffix)
				val title = when (model.category) {
					OnnxModelCategory.GENERAL_LLM -> "${model.title} [LLM]"
					OnnxModelCategory.CLASSIC_TRANSLATION -> model.title
					OnnxModelCategory.BUBBLE_DETECTION -> "${model.title} [Detector]"
				}
				title + suffix
			}.toTypedArray()
			entryValues = arrayOf("") + models.map { it.id }.toTypedArray()
			setDefaultValueCompat("")
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
		sharedPreferences.edit().apply {
			if (model == null || modelId == "PPOCR_V5_REC") {
				remove(AppSettings.KEY_READER_TRANSLATION_REC_MODEL_URL)
				remove(AppSettings.KEY_READER_TRANSLATION_REC_MODEL_PATH)
			} else {
				putString(AppSettings.KEY_READER_TRANSLATION_REC_MODEL_URL, model.encoderUrl) // Legacy compat
			}
			apply()
		}
	}

	private fun applyApiProviderPreset() {
		val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
		val preset = sharedPreferences.getString(AppSettings.KEY_READER_TRANSLATION_API_PROVIDER_PRESET, "CUSTOM")
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
		sharedPreferences.edit {
			putString(AppSettings.KEY_READER_TRANSLATION_API_ENDPOINT, endpointAndModel.first)
			putString(AppSettings.KEY_READER_TRANSLATION_API_MODEL, endpointAndModel.second)
		}
	}

	private fun fetchAndPickApiModel() {
		fetchModelsJob?.cancel()
		fetchModelsJob = viewLifecycleScope.launch {
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
					okHttpClient.newCall(requestBuilder.build()).await().use { response ->
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
				e.printStackTraceDebug()
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
