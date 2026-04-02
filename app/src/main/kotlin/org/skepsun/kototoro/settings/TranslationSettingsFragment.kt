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
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.network.ContentHttpClient
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.ReaderOcrEngine
import org.skepsun.kototoro.core.prefs.ReaderTranslationMode
import org.skepsun.kototoro.core.ui.BasePreferenceFragment
import org.skepsun.kototoro.reader.translate.data.OnnxModelManager
import org.skepsun.kototoro.reader.translate.data.OnnxModelCategory
import org.skepsun.kototoro.reader.translate.data.OnnxOfficialModelCatalog
import org.skepsun.kototoro.settings.support.TranslationApiSettingsSupport
import javax.inject.Inject

@AndroidEntryPoint
class TranslationSettingsFragment :
	BasePreferenceFragment(R.string.translation_settings),
	SharedPreferences.OnSharedPreferenceChangeListener {

	@Inject
	lateinit var onnxModelManager: OnnxModelManager

	@Inject
	@ContentHttpClient
	lateinit var okHttpClient: OkHttpClient

	private var fetchModelsJob: Job? = null

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_translation)

		findPreference<ListPreference>(AppSettings.KEY_READER_TRANSLATION_MODE)?.run {
			entryValues = ReaderTranslationMode.entries.map { it.name }.toTypedArray()
			setDefaultValue(ReaderTranslationMode.LOCAL_FIRST.name)
		}
		findPreference<ListPreference>(AppSettings.KEY_READER_TRANSLATION_API_PROVIDER_PRESET)?.run {
			setDefaultValue("CUSTOM")
			setOnPreferenceChangeListener { _, newValue ->
				TranslationApiSettingsSupport.applyApiProviderPreset(
					sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext()),
					presetInput = (newValue as? String).orEmpty(),
					forceOverride = true,
				)
				true
			}
		}
		findPreference<ListPreference>(AppSettings.KEY_READER_TRANSLATION_BUBBLE_GROUPING_TUNING)?.run {
			setDefaultValue("BALANCED")
		}
		findPreference<SwitchPreferenceCompat>(AppSettings.KEY_READER_TRANSLATION_BUBBLE_DETECTOR_ENABLED)?.run {
			setDefaultValue(true)
		}
		findPreference<SwitchPreferenceCompat>(AppSettings.KEY_READER_TRANSLATION_BUBBLE_GROUPING_ENABLED)?.run {
			setDefaultValue(true)
		}
		findPreference<ListPreference>(AppSettings.KEY_READER_TRANSLATION_OVERLAY_COMPACTNESS)?.run {
			setDefaultValue("BALANCED")
		}
		findPreference<SwitchPreferenceCompat>(AppSettings.KEY_READER_TRANSLATION_PADDLE_OCR_ONLY)?.run {
			setDefaultValue(true)
		}
		findPreference<ListPreference>(AppSettings.KEY_READER_TRANSLATION_OCR_PIPELINE_STRATEGY)?.run {
			setDefaultValue("HYBRID")
		}

		normalizeDeprecatedOcrEngineSelection()
		normalizeDeprecatedOcrModelSelection()
		applyApiProviderPreset(settings.readerTranslationApiProviderPreset)
		updateOcrEngineDependency()
		updateApiPreferenceVisibility()
		updatePaddleOfficialModelEntries()
		updateOnnxOfficialModelEntries()
		updateOnnxBubbleOfficialModelEntries()
		updateBubbleGroupingSummary()
		updateBubbleExperimentVisibility()
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
				updateOnnxOfficialModelEntries()
				updateOnnxBubbleOfficialModelEntries()
				updateBubbleDetectorNmsPreference()
				updateBubbleExperimentVisibility()
			}
			AppSettings.KEY_READER_TRANSLATION_PADDLE_OFFICIAL_MODEL_ID -> {
				normalizeDeprecatedOcrModelSelection()
				applyOfficialPaddleModel()
				updatePaddleOfficialModelEntries()
			}
			AppSettings.KEY_READER_TRANSLATION_ONNX_MODEL_ID -> {
				updateOnnxOfficialModelEntries()
			}
			AppSettings.KEY_READER_TRANSLATION_BUBBLE_DETECTOR_MODEL_ID -> {
				updateOnnxBubbleOfficialModelEntries()
				updateBubbleDetectorNmsPreference()
			}
			AppSettings.KEY_READER_TRANSLATION_MODE -> {
				updateApiPreferenceVisibility()
				updateOnnxOfficialModelEntries()
			}
			AppSettings.KEY_READER_TRANSLATION_BUBBLE_DETECTOR_ENABLED -> {
				updateBubbleGroupingSummary()
				updateOnnxBubbleOfficialModelEntries()
				updateBubbleDetectorNmsPreference()
				updateBubbleExperimentVisibility()
			}
			AppSettings.KEY_READER_TRANSLATION_BUBBLE_GROUPING_ENABLED -> {
				updateBubbleGroupingSummary()
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
		findPreference<Preference>(AppSettings.KEY_READER_TRANSLATION_PADDLE_OFFICIAL_MODEL_ID)?.isVisible = true
	}

	private fun updateBubbleDetectorNmsPreference() {
		val nmsPref = findPreference<androidx.preference.SeekBarPreference>("reader_translation_bubble_detector_nms_dummy")
		nmsPref?.apply {
			isPersistent = false
			val modelId = settings.readerTranslationBubbleDetectorModelId
			val isDetr = modelId.contains("detr", ignoreCase = true) || modelId.contains("transformers", ignoreCase = true)
			val nmsVal = settings.getBubbleDetectorNms(modelId, isDetr)
			value = (nmsVal * 100).toInt()
			isVisible = settings.isReaderTranslationBubbleDetectorEnabled
			
			summary = getString(R.string.reader_translation_bubble_detector_nms_summary)
			
			setOnPreferenceChangeListener { _, newValue ->
				val intValue = newValue as Int
				settings.setBubbleDetectorNms(modelId, intValue / 100f)
				true
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

	private fun updateBubbleGroupingSummary() {
		findPreference<SwitchPreferenceCompat>(AppSettings.KEY_READER_TRANSLATION_BUBBLE_GROUPING_ENABLED)?.apply {
			title = getString(
				if (settings.isReaderTranslationBubbleDetectorEnabled) {
					R.string.reader_translation_bubble_grouping_enabled
				} else {
					R.string.reader_translation_bubble_grouping_enabled_no_detector
				},
			)
			summary = getString(
				if (settings.isReaderTranslationBubbleDetectorEnabled) {
					R.string.reader_translation_bubble_grouping_enabled_summary
				} else {
					R.string.reader_translation_bubble_grouping_enabled_summary_no_detector
				},
			)
		}
	}

	private fun updateBubbleExperimentVisibility() {
		// ROI catch-all has been removed from runtime settings.
	}

	private fun applyOfficialPaddleModel() {
		// The active Paddle path now uses ONNX OCR bundles resolved by model id.
	}

	private fun updatePaddleOfficialModelEntries() {
		val models = OnnxOfficialModelCatalog.models.filter {
			it.category == OnnxModelCategory.OCR_RECOGNIZER && it.id.startsWith("ppocr")
		}
		findPreference<ListPreference>(AppSettings.KEY_READER_TRANSLATION_PADDLE_OFFICIAL_MODEL_ID)?.run {
			entries = models.map { model ->
				val suffix = if (onnxModelManager.isModelDownloaded(model.id)) ""
				else getString(R.string.reader_translation_ocr_model_selection_not_downloaded_suffix)
				model.title + suffix
			}.toTypedArray()
			entryValues = models.map { it.id }.toTypedArray()
			setDefaultValue(models.firstOrNull()?.id ?: "")
			summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
			val values = entryValues?.map { it.toString() }.orEmpty()
			if (value.isNullOrBlank() || value !in values) {
				value = models.firstOrNull()?.id ?: ""
			}
			isVisible = true
		}
	}

	private fun updateOnnxOfficialModelEntries() {
		val models = OnnxOfficialModelCatalog.models.filter { model ->
			model.category == OnnxModelCategory.CLASSIC_TRANSLATION
		}
		findPreference<ListPreference>(AppSettings.KEY_READER_TRANSLATION_ONNX_MODEL_ID)?.run {
			entries = arrayOf(getString(R.string.reader_translation_local_model_mlkit)) + models.map { model ->
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

	private fun updateOnnxBubbleOfficialModelEntries() {
		val models = OnnxOfficialModelCatalog.models.filter { model ->
			model.category == OnnxModelCategory.BUBBLE_DETECTION
		}
		findPreference<ListPreference>(AppSettings.KEY_READER_TRANSLATION_BUBBLE_DETECTOR_MODEL_ID)?.run {
			entries = arrayOf(getString(R.string.reader_translation_ocr_model_onnx_automatic)) + models.map { model ->
				val suffix = if (onnxModelManager.isModelDownloaded(model.id)) ""
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
			isVisible = settings.isReaderTranslationBubbleDetectorEnabled
		}
	}

	private fun normalizeDeprecatedOcrEngineSelection() {
		val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
		val raw = sharedPreferences.getString(AppSettings.KEY_READER_TRANSLATION_OCR_ENGINE, ReaderOcrEngine.MLKIT.name)
		if (raw == "TFLITE" || raw == "HYBRID" || raw == "NCNN") {
			sharedPreferences.edit {
				putString(AppSettings.KEY_READER_TRANSLATION_OCR_ENGINE, ReaderOcrEngine.PADDLE.name)
			}
		}
	}

	private fun normalizeDeprecatedOcrModelSelection() {
		val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
		when (sharedPreferences.getString(AppSettings.KEY_READER_TRANSLATION_PADDLE_OFFICIAL_MODEL_ID, "")) {
			"ppocrv5_mobile_onnx" -> sharedPreferences.edit {
				putString(AppSettings.KEY_READER_TRANSLATION_PADDLE_OFFICIAL_MODEL_ID, "ppocrv5_mobile_rec_onnx")
			}
			"ppocrv5_server_onnx" -> sharedPreferences.edit {
				putString(AppSettings.KEY_READER_TRANSLATION_PADDLE_OFFICIAL_MODEL_ID, "ppocrv5_server_rec_onnx")
			}
		}
	}

	private fun applyApiProviderPreset(presetInput: String, forceOverride: Boolean = false) {
		TranslationApiSettingsSupport.applyApiProviderPreset(
			sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext()),
			presetInput = presetInput,
			forceOverride = forceOverride,
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
