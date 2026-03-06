package org.skepsun.kototoro.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.ZoomMode
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
import org.skepsun.kototoro.reader.translate.data.PaddleModelManager
import org.skepsun.kototoro.reader.translate.data.TfliteModelManager
import org.skepsun.kototoro.reader.translate.data.NcnnModelManager
import org.skepsun.kototoro.reader.translate.data.PaddleOfficialModelCatalog
import org.skepsun.kototoro.reader.translate.data.TfliteOfficialModelCatalog
import org.skepsun.kototoro.reader.translate.data.NcnnOfficialModelCatalog
import org.skepsun.kototoro.settings.utils.MultiSummaryProvider
import org.skepsun.kototoro.settings.utils.PercentSummaryProvider
import org.skepsun.kototoro.settings.utils.SliderPreference
import javax.inject.Inject

@AndroidEntryPoint
class ReaderSettingsFragment :
	BasePreferenceFragment(R.string.reader_settings),
	SharedPreferences.OnSharedPreferenceChangeListener {

	@Inject
	lateinit var paddleModelManager: PaddleModelManager

	@Inject
	lateinit var tfliteModelManager: TfliteModelManager

	@Inject
	lateinit var ncnnModelManager: NcnnModelManager

	private var paddleDownloadJob: Job? = null
	private var tfliteDownloadJob: Job? = null

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
			entryValues = ReaderOcrEngine.entries.names()
			setDefaultValueCompat(ReaderOcrEngine.MLKIT.name)
		}
		findPreference<SwitchPreferenceCompat>(AppSettings.KEY_READER_TRANSLATION_PADDLE_OCR_ONLY)?.run {
			setDefaultValue(true)
		}
		updatePaddleOfficialModelEntries()
		updateTfliteOfficialModelEntries()
		updateNcnnOfficialModelEntries()
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
		paddleDownloadJob?.cancel()
		settings.unsubscribe(this)
		super.onDestroyView()
	}

	override fun onPreferenceTreeClick(preference: Preference): Boolean {
		return when (preference.key) {
			AppSettings.KEY_READER_TAP_ACTIONS -> {
				router.openReaderTapGridSettings()
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
			AppSettings.KEY_READER_TRANSLATION_PADDLE_MODEL_URL,
			AppSettings.KEY_READER_TRANSLATION_PADDLE_MODEL_VERSION,
			AppSettings.KEY_READER_TRANSLATION_PADDLE_MODEL_SHA256 -> applyOfficialPaddleModel()
		}
	}

	private fun updateOcrEngineDependency() {
		val isPaddle = settings.readerTranslationOcrEngine == ReaderOcrEngine.PADDLE
		val isTflite = settings.readerTranslationOcrEngine == ReaderOcrEngine.TFLITE
		val isHybrid = settings.readerTranslationOcrEngine == ReaderOcrEngine.HYBRID

		val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

		findPreference<Preference>("reader_translation_advanced_settings")?.isVisible = false

		// Auto-fill defaults if empty
		if (isHybrid || isPaddle) {
			if (sharedPreferences.getString(AppSettings.KEY_READER_TRANSLATION_PADDLE_MODEL_URL, "").isNullOrBlank()) {
				sharedPreferences.edit().apply {
					putString(AppSettings.KEY_READER_TRANSLATION_PADDLE_MODEL_URL, getString(R.string.reader_translation_paddle_model_url_default))
					putString(AppSettings.KEY_READER_TRANSLATION_PADDLE_MODEL_VERSION, getString(R.string.reader_translation_paddle_model_version_default))
					apply()
				}
			}
		}
		if (isHybrid || isTflite) {
			if (sharedPreferences.getString(AppSettings.KEY_READER_TRANSLATION_TFLITE_MODEL_URL, "").isNullOrBlank()) {
				sharedPreferences.edit().apply {
					putString(AppSettings.KEY_READER_TRANSLATION_TFLITE_MODEL_URL, getString(R.string.reader_translation_tflite_model_url_default))
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
		val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
		val isPaddle = settings.readerTranslationOcrEngine == ReaderOcrEngine.PADDLE
		val isHybrid = settings.readerTranslationOcrEngine == ReaderOcrEngine.HYBRID
		
		val ocrOnly = sharedPreferences.getBoolean(AppSettings.KEY_READER_TRANSLATION_PADDLE_OCR_ONLY, true)
		val models = if (ocrOnly) {
			PaddleOfficialModelCatalog.ocrModels
		} else {
			PaddleOfficialModelCatalog.models
		}
		findPreference<ListPreference>(AppSettings.KEY_READER_TRANSLATION_PADDLE_OFFICIAL_MODEL_ID)?.run {
			entries = arrayOf(getString(R.string.disabled)) + models.map { model ->
				val suffix = if (paddleModelManager.isModelDownloaded(model.version)) "" 
							 else getString(R.string.reader_translation_ocr_model_selection_not_downloaded_suffix)
				model.title + suffix
			}.toTypedArray()
			entryValues = arrayOf("") + models.map { it.id }.toTypedArray()
			setDefaultValueCompat("")
			summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
			val values = entryValues?.map { it.toString() }.orEmpty()
			if (value != null && value !in values) {
				value = ""
				applyOfficialPaddleModel()
			}
			isVisible = isPaddle || isHybrid
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
			setDefaultValueCompat("")
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
		val isNcnn = settings.readerTranslationOcrEngine == ReaderOcrEngine.NCNN
		val models = NcnnOfficialModelCatalog.models

		findPreference<ListPreference>(AppSettings.KEY_READER_TRANSLATION_NCNN_MODEL_ID)?.run {
			entries = arrayOf(getString(R.string.disabled)) + models.map { model ->
				val suffix = if (ncnnModelManager.isModelDownloaded(model.version)) ""
				else getString(R.string.reader_translation_ocr_model_selection_not_downloaded_suffix)
				model.title + suffix
			}.toTypedArray()
			entryValues = arrayOf("") + models.map { it.id }.toTypedArray()
			setDefaultValueCompat("")
			summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
			val values = entryValues?.map { it.toString() }.orEmpty()
			if (value != null && value !in values) {
				value = ""
			}
			isVisible = isNcnn
		}
	}

	private fun applyOfficialTfliteModel() {
		val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
		val modelId = sharedPreferences.getString(AppSettings.KEY_READER_TRANSLATION_TFLITE_MODEL_ID, null)
		val model = TfliteOfficialModelCatalog.findById(modelId)
		sharedPreferences.edit().apply {
			if (model == null) {
				remove(AppSettings.KEY_READER_TRANSLATION_TFLITE_MODEL_URL)
				remove(AppSettings.KEY_READER_TRANSLATION_TFLITE_MODEL_PATH)
			} else {
				putString(AppSettings.KEY_READER_TRANSLATION_TFLITE_MODEL_URL, model.encoderUrl) // Legacy compat
			}
			apply()
		}
	}

}
