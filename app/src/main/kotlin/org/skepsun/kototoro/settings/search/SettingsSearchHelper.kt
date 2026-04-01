package org.skepsun.kototoro.settings.search

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.XmlRes
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.preference.get
import dagger.Reusable
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.LocalizedAppContext
import org.skepsun.kototoro.settings.AISettingsFragment
import org.skepsun.kototoro.settings.AppearanceSettingsFragment
import org.skepsun.kototoro.settings.AIImageEnhancementSettingsFragment
import org.skepsun.kototoro.settings.DownloadsSettingsFragment
import org.skepsun.kototoro.settings.ProxySettingsFragment
import org.skepsun.kototoro.settings.ReaderSettingsFragment
import org.skepsun.kototoro.settings.PlaybackSettingsFragment
import org.skepsun.kototoro.settings.ServicesSettingsFragment
import org.skepsun.kototoro.settings.StorageAndNetworkSettingsFragment
import org.skepsun.kototoro.settings.SuggestionsSettingsFragment
import org.skepsun.kototoro.settings.TranslationApiSettingsFragment
import org.skepsun.kototoro.settings.TranslationSettingsFragment
import org.skepsun.kototoro.settings.AIVideoEnhancementSettingsFragment
import org.skepsun.kototoro.settings.about.AboutSettingsFragment
import org.skepsun.kototoro.settings.discord.DiscordSettingsFragment
import org.skepsun.kototoro.settings.sources.SourcesSettingsFragment
import org.skepsun.kototoro.settings.tracker.TrackerSettingsFragment
import org.skepsun.kototoro.settings.userdata.BackupsSettingsFragment
import org.skepsun.kototoro.settings.userdata.storage.DataCleanupSettingsFragment
import javax.inject.Inject

@Reusable
@SuppressLint("RestrictedApi")
class SettingsSearchHelper @Inject constructor(
    @LocalizedAppContext private val context: Context,
) {

    fun inflatePreferences(): List<SettingsItem> {
        val preferenceManager = PreferenceManager(context)
		val result = ArrayList<SettingsItem>()
		preferenceManager.inflateTo(result, R.xml.pref_appearance, emptyList(), AppearanceSettingsFragment::class.java)
		preferenceManager.inflateTo(result, R.xml.pref_ai, emptyList(), AISettingsFragment::class.java)
		preferenceManager.inflateTo(result, R.xml.pref_ai_image, listOf(context.getString(R.string.ai_settings)), AIImageEnhancementSettingsFragment::class.java)
		preferenceManager.inflateTo(result, R.xml.pref_ai_video, listOf(context.getString(R.string.ai_settings)), AIVideoEnhancementSettingsFragment::class.java)
		preferenceManager.inflateTo(result, R.xml.pref_playback, emptyList(), PlaybackSettingsFragment::class.java)
		preferenceManager.inflateTo(result, R.xml.pref_sources, emptyList(), SourcesSettingsFragment::class.java)
		preferenceManager.inflateTo(result, R.xml.pref_reader, emptyList(), ReaderSettingsFragment::class.java)
		preferenceManager.inflateTo(result, R.xml.pref_translation, emptyList(), TranslationSettingsFragment::class.java)
		preferenceManager.inflateTo(
			result,
			R.xml.pref_translation_api,
			listOf(context.getString(R.string.ai_settings)),
			TranslationApiSettingsFragment::class.java,
		)
        preferenceManager.inflateTo(
            result,
            R.xml.pref_network_storage,
            emptyList(),
            StorageAndNetworkSettingsFragment::class.java,
        )
        preferenceManager.inflateTo(
            result,
            R.xml.pref_backups,
            listOf(context.getString(R.string.services)),
            BackupsSettingsFragment::class.java,
        )
        preferenceManager.inflateTo(
            result,
            R.xml.pref_data_cleanup,
            listOf(context.getString(R.string.storage_and_network)),
            DataCleanupSettingsFragment::class.java,
        )
        preferenceManager.inflateTo(result, R.xml.pref_downloads, emptyList(), DownloadsSettingsFragment::class.java)
        preferenceManager.inflateTo(result, R.xml.pref_tracker, emptyList(), TrackerSettingsFragment::class.java)
        preferenceManager.inflateTo(result, R.xml.pref_services, emptyList(), ServicesSettingsFragment::class.java)
        preferenceManager.inflateTo(result, R.xml.pref_about, emptyList(), AboutSettingsFragment::class.java)
        preferenceManager.inflateTo(
            result,
            R.xml.pref_proxy,
            listOf(context.getString(R.string.storage_and_network)),
            ProxySettingsFragment::class.java,
        )
        preferenceManager.inflateTo(
            result,
            R.xml.pref_suggestions,
            listOf(context.getString(R.string.services)),
            SuggestionsSettingsFragment::class.java,
        )
        preferenceManager.inflateTo(
            result,
            R.xml.pref_discord,
            listOf(context.getString(R.string.services)),
            DiscordSettingsFragment::class.java,
        )
        preferenceManager.inflateTo(
            result,
            R.xml.pref_sources,
            listOf(),
            SourcesSettingsFragment::class.java,
        )
        return result
    }

    private fun PreferenceManager.inflateTo(
        result: MutableList<SettingsItem>,
        @XmlRes resId: Int,
        breadcrumbs: List<String>,
        fragmentClass: Class<out PreferenceFragmentCompat>
    ) {
        val screen = inflateFromResource(context, resId, null)
        val screenTitle = screen.title?.toString()
        screen.inflateTo(
            result = result,
            breadcrumbs = if (screenTitle.isNullOrEmpty()) breadcrumbs else breadcrumbs + screenTitle,
            fragmentClass = fragmentClass,
        )
    }

    private fun PreferenceScreen.inflateTo(
        result: MutableList<SettingsItem>,
        breadcrumbs: List<String>,
        fragmentClass: Class<out PreferenceFragmentCompat>
    ): Unit = repeat(preferenceCount) { i ->
        val pref = this[i]
        if (pref is PreferenceScreen) {
            val screenTitle = pref.title?.toString()
            pref.inflateTo(
                result = result,
                breadcrumbs = if (screenTitle.isNullOrEmpty()) breadcrumbs else breadcrumbs + screenTitle,
                fragmentClass = fragmentClass,
            )
        } else {
            result.add(
                SettingsItem(
                    key = pref.key ?: return@repeat,
                    title = pref.title ?: return@repeat,
                    breadcrumbs = breadcrumbs,
                    fragmentClass = fragmentClass,
                ),
            )
        }
    }
}
