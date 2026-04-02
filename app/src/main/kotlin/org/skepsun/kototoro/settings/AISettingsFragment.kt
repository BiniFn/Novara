package org.skepsun.kototoro.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.preference.Preference
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.reader.translate.data.OnnxModelManager
import org.skepsun.kototoro.reader.translate.data.OnnxModelCategory
import org.skepsun.kototoro.reader.translate.data.OnnxOfficialModelCatalog
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.ui.BasePreferenceFragment
import org.skepsun.kototoro.settings.support.AISettingsSummarySupport
import javax.inject.Inject

@AndroidEntryPoint
class AISettingsFragment :
	BasePreferenceFragment(R.string.ai_settings),
	SharedPreferences.OnSharedPreferenceChangeListener {

	@Inject
	lateinit var onnxModelManager: OnnxModelManager

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_ai)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		settings.subscribe(this)
		installPreferenceTextLayoutTuning()
		updateDynamicSummaries()
	}

	override fun onDestroyView() {
		settings.unsubscribe(this)
		super.onDestroyView()
	}

	override fun onResume() {
		super.onResume()
		updateDynamicSummaries()
	}

	override fun onPreferenceTreeClick(preference: Preference): Boolean {
		if (preference.key == "ai_api" && !isApiConfigured()) {
			Snackbar.make(listView, R.string.ai_api_open_settings_hint, Snackbar.LENGTH_SHORT).show()
		}
		return super.onPreferenceTreeClick(preference)
	}

	override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
		when (key) {
			AppSettings.KEY_READER_TRANSLATION_MODE,
			AppSettings.KEY_READER_TRANSLATION_API_PROVIDER_PRESET,
			AppSettings.KEY_READER_TRANSLATION_ENABLED,
			AppSettings.KEY_READER_TRANSLATION_API_ENDPOINT,
			AppSettings.KEY_READER_TRANSLATION_API_KEY,
			AppSettings.KEY_READER_TRANSLATION_API_MODEL,
			AppSettings.KEY_READER_SUPER_RESOLUTION_ENABLED,
			AppSettings.KEY_READER_SUPER_RESOLUTION_MODEL,
			AppSettings.KEY_READER_SUPER_RESOLUTION_NOISE_LEVEL,
			AppSettings.KEY_VIDEO_SUPER_RES_MODE,
			AppSettings.KEY_VIDEO_SUPER_RES_SHADER,
			AppSettings.KEY_VIDEO_SUPER_RES_QUALITY_SHADER,
			AppSettings.KEY_VIDEO_SUPER_RES_BALANCED_SHADER,
			AppSettings.KEY_VIDEO_SUPER_RES_PERFORMANCE_SHADER,
			-> updateDynamicSummaries()
		}
	}

	private fun updateDynamicSummaries() {
		findPreference<Preference>("ai_translation")?.summary = getString(
			if (settings.isReaderTranslationEnabled) {
				R.string.ai_translation_summary_enabled
			} else {
				R.string.ai_translation_summary_disabled
			},
			AISettingsSummarySupport.getTranslationModeLabel(requireContext(), settings.readerTranslationMode),
			AISettingsSummarySupport.getSourceLanguageLabel(requireContext(), settings.readerTranslationSourceLanguage),
			AISettingsSummarySupport.getTargetLanguageLabel(requireContext(), settings.readerTranslationTargetLanguage),
		)

		findPreference<Preference>("ai_api")?.summary = getString(
			R.string.ai_api_settings_summary_format,
			getApiConfigurationStatus(),
			AISettingsSummarySupport.getApiProviderLabel(requireContext(), settings.readerTranslationApiProviderPreset),
			AISettingsSummarySupport.getTranslationModeLabel(requireContext(), settings.readerTranslationMode),
			settings.readerTranslationApiModel.ifBlank { getString(R.string.ai_api_provider_custom) },
		)
		findPreference<Preference>("ai_api")?.icon = if (isApiConfigured()) {
			AppCompatResources.getDrawable(requireContext(), R.drawable.ic_settings)
		} else {
			getWarningIcon()
		}

		findPreference<Preference>("ai_models")?.summary = getString(
			R.string.ai_models_summary_format,
			getDownloadedModelCount(),
			getTotalModelCount(),
			getDownloadedOcrModelCount(),
			getTotalOcrModelCount(),
			getDownloadedTranslationModelCount(),
			getTotalTranslationModelCount(),
			getDownloadedBubbleModelCount(),
			getTotalBubbleModelCount(),
		)

		findPreference<Preference>("ai_image")?.summary = getString(
			if (settings.isReaderSuperResolutionEnabled) {
				R.string.ai_image_enhancement_summary_enabled
			} else {
				R.string.ai_image_enhancement_summary_disabled
			},
			AISettingsSummarySupport.getReaderSuperResolutionModelLabel(requireContext(), settings.readerSuperResolutionModel),
			AISettingsSummarySupport.getReaderSuperResolutionNoiseLabel(requireContext(), settings.readerSuperResolutionNoiseLevel),
		)

		findPreference<Preference>("ai_video")?.summary = getString(
			R.string.ai_video_enhancement_summary_format,
			AISettingsSummarySupport.getVideoModeLabel(requireContext(), settings.videoSuperResolutionMode),
			getCurrentVideoShaderLabel(),
		)
	}

	private fun getDownloadedModelCount(): Int {
		return OnnxOfficialModelCatalog.models.count { onnxModelManager.isModelDownloaded(it.id) }
	}

	private fun getTotalModelCount(): Int {
		return OnnxOfficialModelCatalog.models.size
	}

	private fun getDownloadedOcrModelCount(): Int {
		return OnnxOfficialModelCatalog.models.count { it.category == OnnxModelCategory.OCR && onnxModelManager.isModelDownloaded(it.id) }
	}

	private fun getTotalOcrModelCount(): Int {
		return OnnxOfficialModelCatalog.models.count { it.category == OnnxModelCategory.OCR }
	}

	private fun getDownloadedTranslationModelCount(): Int {
		return OnnxOfficialModelCatalog.models
			.filter { it.category == OnnxModelCategory.CLASSIC_TRANSLATION || it.category == OnnxModelCategory.GENERAL_LLM }
			.count { onnxModelManager.isModelDownloaded(it.id) }
	}

	private fun getTotalTranslationModelCount(): Int {
		return OnnxOfficialModelCatalog.models.count {
			it.category == OnnxModelCategory.CLASSIC_TRANSLATION || it.category == OnnxModelCategory.GENERAL_LLM
		}
	}

	private fun getDownloadedBubbleModelCount(): Int {
		return OnnxOfficialModelCatalog.models
			.filter { it.category == OnnxModelCategory.BUBBLE_DETECTION }
			.count { onnxModelManager.isModelDownloaded(it.id) }
	}

	private fun getTotalBubbleModelCount(): Int {
		return OnnxOfficialModelCatalog.models.count { it.category == OnnxModelCategory.BUBBLE_DETECTION }
	}

	private fun getApiConfigurationStatus(): String {
		return if (isApiConfigured()) {
			getString(R.string.ai_api_status_configured)
		} else {
			getString(R.string.ai_api_status_not_configured)
		}
	}

	private fun isApiConfigured(): Boolean {
		return settings.readerTranslationApiEndpoint.isNotBlank() && settings.readerTranslationApiKey.isNotBlank()
	}

	private fun getCurrentVideoShaderLabel(): String {
		val shader = when (settings.videoSuperResolutionMode) {
			org.skepsun.kototoro.core.prefs.VideoSuperResolutionMode.QUALITY -> settings.videoSuperResolutionQualityShader
			org.skepsun.kototoro.core.prefs.VideoSuperResolutionMode.BALANCED -> settings.videoSuperResolutionBalancedShader
			org.skepsun.kototoro.core.prefs.VideoSuperResolutionMode.PERFORMANCE -> settings.videoSuperResolutionPerformanceShader
			org.skepsun.kototoro.core.prefs.VideoSuperResolutionMode.ADVANCED -> settings.videoSuperResolutionShader
			org.skepsun.kototoro.core.prefs.VideoSuperResolutionMode.OFF -> null
		}
		return shader?.let { AISettingsSummarySupport.getVideoShaderLabel(requireContext(), it) }
			?: getString(R.string.disabled)
	}

	private fun installPreferenceTextLayoutTuning() {
		listView.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
			override fun onChildViewAttachedToWindow(view: View) {
				tunePreferenceItem(view)
			}

			override fun onChildViewDetachedFromWindow(view: View) = Unit
		})

		for (index in 0 until listView.childCount) {
			tunePreferenceItem(listView.getChildAt(index))
		}
	}

	private fun tunePreferenceItem(view: View) {
		view.findViewById<TextView>(android.R.id.title)?.apply {
			isSingleLine = false
			maxLines = 2
			ellipsize = TextUtils.TruncateAt.END
		}
		view.findViewById<TextView>(android.R.id.summary)?.apply {
			maxLines = 2
			ellipsize = TextUtils.TruncateAt.END
		}
	}
}
