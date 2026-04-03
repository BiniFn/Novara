package org.skepsun.kototoro.settings

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.preference.Preference
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.ui.BasePreferenceFragment
import org.skepsun.kototoro.core.util.ext.viewLifecycleScope

import javax.inject.Inject
import androidx.preference.ListPreference
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.reader.translate.data.OnnxModelCategory
import org.skepsun.kototoro.reader.translate.data.OnnxModelManager
import org.skepsun.kototoro.reader.translate.data.OnnxOfficialModelCatalog

@AndroidEntryPoint
class AIImageEnhancementSettingsFragment : BasePreferenceFragment(R.string.ai_image_enhancement_settings) {

	@Inject
	lateinit var onnxModelManager: OnnxModelManager

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_ai_image)
		updateSuperResolutionModelEntries()
	}

	private fun updateSuperResolutionModelEntries() {
		val models = OnnxOfficialModelCatalog.models.filter {
			it.category == OnnxModelCategory.IMAGE_SUPER_RESOLUTION
		}
		findPreference<ListPreference>(AppSettings.KEY_READER_SUPER_RESOLUTION_MODEL)?.run {
			entries = models.map { model ->
				val suffix = if (onnxModelManager.isModelDownloaded(model.id)) ""
				else getString(R.string.reader_translation_ocr_model_selection_not_downloaded_suffix)
				model.title + suffix
			}.toTypedArray()
			entryValues = models.map { it.id }.toTypedArray()

			if (value == null || models.none { it.id == value }) {
				value = models.firstOrNull()?.id
			}
		}

		val enginePref = findPreference<ListPreference>(AppSettings.KEY_READER_SUPER_RESOLUTION_ENGINE)
		val anime4kPref = findPreference<ListPreference>(AppSettings.KEY_READER_SUPER_RESOLUTION_ANIME4K_MODE)
		val modelPref = findPreference<ListPreference>(AppSettings.KEY_READER_SUPER_RESOLUTION_MODEL)

		fun updateVisibility(engineValue: String?) {
			anime4kPref?.isVisible = (engineValue == "ANIME4K")
			modelPref?.isVisible = (engineValue == "NCNN")
		}

		enginePref?.setOnPreferenceChangeListener { _, newValue ->
			updateVisibility(newValue as? String)
			true
		}
		updateVisibility(enginePref?.value)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
	}

	override fun onPreferenceTreeClick(preference: Preference): Boolean {
		return when (preference.key) {
			"reader_super_resolution_clear_cache" -> {
				clearSrCache()
				true
			}
			else -> super.onPreferenceTreeClick(preference)
		}
	}

	private fun clearSrCache() {
		viewLifecycleScope.launch(Dispatchers.IO) {
			val srCacheDir = java.io.File(requireContext().cacheDir, "sr_cache")
			var deletedCount = 0
			if (srCacheDir.exists() && srCacheDir.isDirectory) {
				srCacheDir.listFiles()?.forEach { file ->
					if (file.delete()) deletedCount++
				}
			}
			withContext(Dispatchers.Main) {
				Toast.makeText(
					requireContext(),
					getString(R.string.reader_super_resolution_cache_cleared) + " ($deletedCount files)",
					Toast.LENGTH_SHORT,
				).show()
			}
		}
	}
}
