package org.skepsun.kototoro.settings

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.ReaderBackground
import org.skepsun.kototoro.core.prefs.VideoDecoderMode
import org.skepsun.kototoro.core.prefs.VideoRendererMode
import org.skepsun.kototoro.core.ui.BasePreferenceFragment
import org.skepsun.kototoro.core.util.ext.setDefaultValueCompat
import org.skepsun.kototoro.parsers.util.names
import org.skepsun.kototoro.settings.utils.SliderPreference

@AndroidEntryPoint
class PlaybackSettingsFragment : BasePreferenceFragment(R.string.playback_settings) {

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_playback)

		findPreference<ListPreference>(AppSettings.KEY_VIDEO_DECODER_MODE)?.run {
			entryValues = VideoDecoderMode.entries.names()
			setDefaultValueCompat(VideoDecoderMode.HARDWARE.name)
		}

		findPreference<ListPreference>(AppSettings.KEY_VIDEO_RENDERER_MODE)?.run {
			entryValues = VideoRendererMode.entries.names()
			setDefaultValueCompat(VideoRendererMode.AUTO.name)
		}

		findPreference<ListPreference>(AppSettings.KEY_VIDEO_BACKGROUND)?.run {
			entryValues = ReaderBackground.entries.names()
			setDefaultValueCompat(ReaderBackground.DEFAULT.name)
		}

		findPreference<SliderPreference>(AppSettings.KEY_VIDEO_CACHE_MB)?.run {
			summaryProvider = Preference.SummaryProvider<SliderPreference> { pref ->
				val valueMb = pref.value.toInt()
				getString(R.string.video_cache_size_summary, valueMb)
			}
		}

		findPreference<SliderPreference>(AppSettings.KEY_VIDEO_CONTROLS_ALPHA)?.run {
			summaryProvider = Preference.SummaryProvider<SliderPreference> { pref ->
				"${pref.value.toInt()}%"
			}
		}

		findPreference<SliderPreference>(AppSettings.KEY_VIDEO_GRADIENT_ALPHA)?.run {
			summaryProvider = Preference.SummaryProvider<SliderPreference> { pref ->
				"${pref.value.toInt()}%"
			}
		}
	}
}
