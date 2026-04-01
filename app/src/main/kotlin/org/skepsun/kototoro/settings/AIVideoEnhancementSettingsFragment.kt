package org.skepsun.kototoro.settings

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.VideoSuperResolutionMode
import org.skepsun.kototoro.core.prefs.VideoSuperResolutionShader
import org.skepsun.kototoro.core.ui.BasePreferenceFragment
import org.skepsun.kototoro.core.util.ext.setDefaultValueCompat
import org.skepsun.kototoro.parsers.util.names

@AndroidEntryPoint
class AIVideoEnhancementSettingsFragment : BasePreferenceFragment(R.string.ai_video_enhancement_settings) {

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_ai_video)

		findPreference<ListPreference>(AppSettings.KEY_VIDEO_SUPER_RES_MODE)?.run {
			entryValues = VideoSuperResolutionMode.entries.names()
			setDefaultValueCompat(VideoSuperResolutionMode.BALANCED.name)
		}

		findPreference<ListPreference>(AppSettings.KEY_VIDEO_SUPER_RES_QUALITY_SHADER)?.run {
			entryValues = VideoSuperResolutionShader.entries.names()
			setDefaultValueCompat(VideoSuperResolutionShader.MODE_A.name)
		}

		findPreference<ListPreference>(AppSettings.KEY_VIDEO_SUPER_RES_BALANCED_SHADER)?.run {
			entryValues = VideoSuperResolutionShader.entries.names()
			setDefaultValueCompat(VideoSuperResolutionShader.MODE_B.name)
		}

		findPreference<ListPreference>(AppSettings.KEY_VIDEO_SUPER_RES_PERFORMANCE_SHADER)?.run {
			entryValues = VideoSuperResolutionShader.entries.names()
			setDefaultValueCompat(VideoSuperResolutionShader.MODE_C.name)
		}

		findPreference<Preference>("video_super_resolution_advanced_settings_button")?.setOnPreferenceClickListener {
			org.skepsun.kototoro.video.ui.VideoSuperResolutionAdvancedSheet().show(
				parentFragmentManager,
				"VideoSuperResolutionAdvancedSheet",
			)
			true
		}
	}
}
