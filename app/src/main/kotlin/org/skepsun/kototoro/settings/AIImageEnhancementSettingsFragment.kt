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

@AndroidEntryPoint
class AIImageEnhancementSettingsFragment : BasePreferenceFragment(R.string.ai_image_enhancement_settings) {

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_ai_image)
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
