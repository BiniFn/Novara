package org.skepsun.kototoro.settings

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.TwoStatePreference
import androidx.preference.SwitchPreferenceCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.os.AppShortcutManager
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.ListMode
import org.skepsun.kototoro.core.prefs.ProgressIndicatorMode
import org.skepsun.kototoro.core.prefs.ScreenshotsPolicy
import org.skepsun.kototoro.core.prefs.SearchSuggestionType
import org.skepsun.kototoro.core.prefs.TriStateOption
import org.skepsun.kototoro.explore.ui.model.BrowseGroupTab
import org.skepsun.kototoro.explore.ui.model.SourceTag
import org.skepsun.kototoro.explore.data.SourcePresetsRepository
import org.skepsun.kototoro.core.ui.BasePreferenceFragment
import org.skepsun.kototoro.core.ui.util.ActivityRecreationHandle
import org.skepsun.kototoro.core.util.LocaleComparator
import org.skepsun.kototoro.core.util.ext.getLocalesConfig
import org.skepsun.kototoro.core.util.ext.postDelayed
import org.skepsun.kototoro.core.util.ext.setDefaultValueCompat
import org.skepsun.kototoro.core.util.ext.sortedWithSafe
import org.skepsun.kototoro.core.util.ext.toList
import org.skepsun.kototoro.parsers.util.mapToSet
import org.skepsun.kototoro.parsers.util.names
import org.skepsun.kototoro.parsers.util.toTitleCase
import org.skepsun.kototoro.settings.protect.ProtectSetupActivity
import org.skepsun.kototoro.settings.utils.ActivityListPreference
import org.skepsun.kototoro.settings.utils.MultiSummaryProvider
import org.skepsun.kototoro.settings.utils.PercentSummaryProvider
import org.skepsun.kototoro.settings.utils.SliderPreference
import javax.inject.Inject

@AndroidEntryPoint
class AppearanceSettingsFragment :
    BasePreferenceFragment(R.string.appearance),
    SharedPreferences.OnSharedPreferenceChangeListener {

    @Inject
    lateinit var activityRecreationHandle: ActivityRecreationHandle

    @Inject
    lateinit var appShortcutManager: AppShortcutManager

    @Inject
    lateinit var sourcePresetsRepository: SourcePresetsRepository

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_appearance)
        findPreference<SliderPreference>(AppSettings.KEY_GRID_SIZE)?.summaryProvider = PercentSummaryProvider()
        findPreference<ListPreference>(AppSettings.KEY_LIST_MODE)?.run {
            entryValues = ListMode.entries.names()
            setDefaultValueCompat(ListMode.GRID.name)
        }
        findPreference<ListPreference>(AppSettings.KEY_PROGRESS_INDICATORS)?.run {
            entryValues = ProgressIndicatorMode.entries.names()
            setDefaultValueCompat(ProgressIndicatorMode.PERCENT_READ.name)
        }
        findPreference<ActivityListPreference>(AppSettings.KEY_APP_LOCALE)?.run {
            initLocalePicker(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activityIntent = Intent(
                    Settings.ACTION_APP_LOCALE_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                )
            }
            summaryProvider = Preference.SummaryProvider<ActivityListPreference> {
                val locale = AppCompatDelegate.getApplicationLocales().get(0)
                locale?.getDisplayName(locale)?.toTitleCase(locale) ?: getString(R.string.follow_system)
            }
            setDefaultValueCompat("")
        }
        findPreference<MultiSelectListPreference>(AppSettings.KEY_MANGA_LIST_BADGES)?.run {
            summaryProvider = MultiSummaryProvider(R.string.none)
        }
        findPreference<Preference>(AppSettings.KEY_SHORTCUTS)?.isVisible =
            appShortcutManager.isDynamicShortcutsAvailable()
        findPreference<TwoStatePreference>(AppSettings.KEY_PROTECT_APP)
            ?.isChecked = !settings.appPassword.isNullOrEmpty()
        findPreference<ListPreference>(AppSettings.KEY_SCREENSHOTS_POLICY)?.run {
            entryValues = ScreenshotsPolicy.entries.names()
            setDefaultValueCompat(ScreenshotsPolicy.ALLOW.name)
        }
        findPreference<MultiSelectListPreference>(AppSettings.KEY_SEARCH_SUGGESTION_TYPES)?.let { pref ->
            pref.entryValues = SearchSuggestionType.entries.names()
            pref.entries = SearchSuggestionType.entries.map { pref.context.getString(it.titleResId) }.toTypedArray()
            pref.summaryProvider = MultiSummaryProvider(R.string.none)
            pref.values = settings.searchSuggestionTypes.mapToSet { it.name }
        }
        findPreference<SliderPreference>(AppSettings.KEY_PANORAMA_BLUR)?.summaryProvider = PercentSummaryProvider()
        findPreference<SliderPreference>(AppSettings.KEY_PANORAMA_EXTRA_HEIGHT)?.summaryProvider =
            Preference.SummaryProvider<SliderPreference> { "${it.value}dp" }
        findPreference<SliderPreference>(AppSettings.KEY_PANORAMA_BOTTOM_GRADIENT_ALPHA)?.summaryProvider = PercentSummaryProvider()
        bindNavSummary()
        
        initSearchBarFilters()
    }

    private fun initSearchBarFilters() {
        findPreference<ListPreference>(AppSettings.KEY_HIDDEN_CONTENT_TYPE)?.apply {
            val tabs = BrowseGroupTab.getAllTabs()
            entries = tabs.map { context.getString(it.titleRes) }.toTypedArray()
            entryValues = tabs.map { it.id }.toTypedArray()
            if (value == null && tabs.isNotEmpty()) {
                value = tabs[0].id
            }
        }
        
        findPreference<ListPreference>(AppSettings.KEY_HIDDEN_SOURCE_TAG)?.apply {
            val tags = SourceTag.quickFilterEntries
            entries = arrayOf(context.getString(R.string.all)) + tags.map { context.getString(it.titleRes) }.toTypedArray()
            entryValues = arrayOf("all") + tags.map { it.name }.toTypedArray()
            if (value == null) {
                value = "all"
            }
        }
        
        lifecycleScope.launch {
            val presets = sourcePresetsRepository.getAll()
            findPreference<ListPreference>(AppSettings.KEY_HIDDEN_LANGUAGE_PRESET)?.apply {
                entries = arrayOf(context.getString(R.string.all)) + presets.map { it.title }.toTypedArray()
                entryValues = arrayOf("all") + presets.map { it.id.toString() }.toTypedArray()
                if (value == null) {
                    value = "all"
                }
            }
        }

        // Show dialog automatically when switch is toggled off
        val setupAutoPopup = { switchKey: String, listKey: String ->
            findPreference<SwitchPreferenceCompat>(switchKey)?.setOnPreferenceChangeListener { _, newValue ->
                val isChecked = newValue as Boolean
                if (!isChecked) {
                    val listPref = findPreference<ListPreference>(listKey)
                    if (listPref != null && listPref.entries?.isNotEmpty() == true) {
                        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                            .setTitle(listPref.title)
                            .setSingleChoiceItems(listPref.entries, listPref.findIndexOfValue(listPref.value)) { dialog, which ->
                                listPref.value = listPref.entryValues[which].toString()
                                dialog.dismiss()
                            }
                            .setNegativeButton(android.R.string.cancel, null)
                            .show()
                    }
                }
                true
            }
        }

        setupAutoPopup(AppSettings.KEY_SHOW_CONTENT_TYPE_FILTER, AppSettings.KEY_HIDDEN_CONTENT_TYPE)
        setupAutoPopup(AppSettings.KEY_SHOW_SOURCE_TAG_FILTER, AppSettings.KEY_HIDDEN_SOURCE_TAG)
        setupAutoPopup(AppSettings.KEY_SHOW_LANGUAGE_PRESET_FILTER, AppSettings.KEY_HIDDEN_LANGUAGE_PRESET)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settings.subscribe(this)
    }

    override fun onDestroyView() {
        settings.unsubscribe(this)
        super.onDestroyView()
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        when (key) {
            AppSettings.KEY_THEME -> {
                AppCompatDelegate.setDefaultNightMode(settings.theme)
            }

            AppSettings.KEY_COLOR_THEME,
            AppSettings.KEY_THEME_AMOLED,
            AppSettings.KEY_LOADING_CIRCLE_STYLE,
            AppSettings.KEY_BLUR_MODE,
                -> {
                postRestart()
            }

            AppSettings.KEY_APP_LOCALE -> {
                AppCompatDelegate.setApplicationLocales(settings.appLocales)
            }

            AppSettings.KEY_NAV_MAIN -> {
                bindNavSummary()
            }

            AppSettings.KEY_APP_PASSWORD -> {
                findPreference<TwoStatePreference>(AppSettings.KEY_PROTECT_APP)
                    ?.isChecked = !settings.appPassword.isNullOrEmpty()
            }
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        return when (preference.key) {
            AppSettings.KEY_PROTECT_APP -> {
                val pref = (preference as? TwoStatePreference ?: return false)
                if (pref.isChecked) {
                    pref.isChecked = false
                    startActivity(Intent(preference.context, ProtectSetupActivity::class.java))
                } else {
                    settings.appPassword = null
                }
                true
            }

            else -> super.onPreferenceTreeClick(preference)
        }
    }

    private fun postRestart() {
        viewLifecycleOwner.lifecycle.postDelayed(400) {
            activityRecreationHandle.recreateAll()
        }
    }

    private fun initLocalePicker(preference: ListPreference) {
        val locales = preference.context.getLocalesConfig()
            .toList()
            .sortedWithSafe(LocaleComparator())
        preference.entries = Array(locales.size + 1) { i ->
            if (i == 0) {
                getString(R.string.follow_system)
            } else {
                val lc = locales[i - 1]
                lc.getDisplayName(lc).toTitleCase(lc)
            }
        }
        preference.entryValues = Array(locales.size + 1) { i ->
            if (i == 0) {
                ""
            } else {
                locales[i - 1].toLanguageTag()
            }
        }
    }

    private fun bindNavSummary() {
        val pref = findPreference<Preference>(AppSettings.KEY_NAV_MAIN) ?: return
        pref.summary = settings.mainNavItems.joinToString {
            getString(it.title)
        }
    }
}
