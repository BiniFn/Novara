package org.skepsun.kototoro.settings.sources

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Toast
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.TwoStatePreference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.TriStateOption
import org.skepsun.kototoro.core.ui.BasePreferenceFragment
import org.skepsun.kototoro.core.util.ext.getQuantityStringSafe
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.core.util.ext.setDefaultValueCompat
import org.skepsun.kototoro.explore.data.SourcesSortOrder
import org.skepsun.kototoro.parsers.util.names
import org.skepsun.kototoro.settings.SettingsActivity

@AndroidEntryPoint
class SourcesSettingsFragment : BasePreferenceFragment(R.string.remote_sources),
	SharedPreferences.OnSharedPreferenceChangeListener {

	private val viewModel by viewModels<SourcesSettingsViewModel>()



	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_sources)
		findPreference<ListPreference>(AppSettings.KEY_SOURCES_ORDER)?.run {
			entryValues = SourcesSortOrder.entries.names()
			entries = SourcesSortOrder.entries.map { context.getString(it.titleResId) }.toTypedArray()
			setDefaultValueCompat(SourcesSortOrder.MANUAL.name)
		}
        findPreference<ListPreference>(AppSettings.KEY_INCOGNITO_NSFW)?.run {
            entryValues = TriStateOption.entries.names()
            setDefaultValueCompat(TriStateOption.ASK.name)
        }
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		
		viewModel.loadPlugins()
		viewModel.loadPlugins()

		findPreference<Preference>(AppSettings.KEY_REMOTE_SOURCES)?.let { pref ->
			viewModel.enabledSourcesCount.observe(viewLifecycleOwner) {
				pref.summary = if (it >= 0) {
					resources.getQuantityStringSafe(R.plurals.items, it, it)
				} else {
					null
				}
			}
		}
		findPreference<Preference>(AppSettings.KEY_SOURCES_CATALOG)?.let { pref ->
			pref.setTitle(R.string.built_in_sources_directory)
			viewModel.builtInSourcesCount.observe(viewLifecycleOwner) {
				pref.summary = getString(R.string.available_sources_count, it)
			}
		}
		findPreference<Preference>(AppSettings.KEY_JSON_SOURCES)?.let { pref ->
			pref.setTitle(R.string.json_sources_directory)
			viewModel.jsonSourcesCount.observe(viewLifecycleOwner) {
				pref.summary = getString(R.string.available_sources_count, it)
			}
		}
		findPreference<Preference>(AppSettings.KEY_EXTENSIONS)?.let { pref ->
			pref.setTitle(R.string.extensions)
			viewModel.extensionsSummary.observe(viewLifecycleOwner) {
				pref.summary = it
			}
		}
		findPreference<TwoStatePreference>(AppSettings.KEY_HANDLE_LINKS)?.let { pref ->
			viewModel.isLinksEnabled.observe(viewLifecycleOwner) {
				pref.isChecked = it
			}
		}
		updateEnableAllDependencies()
		settings.subscribe(this)
	}

	override fun onDestroyView() {
		settings.unsubscribe(this)
		super.onDestroyView()
	}

	override fun onPreferenceTreeClick(preference: Preference): Boolean = when (preference.key) {
        AppSettings.KEY_REMOTE_SOURCES -> {
            (activity as? SettingsActivity)?.openFragment(
                fragmentClass = org.skepsun.kototoro.settings.sources.manage.SourcesManageFragment::class.java,
                args = null,
                isFromRoot = false
            )
            true
        }

		AppSettings.KEY_SOURCES_CATALOG -> {
			router.openSourcesCatalog()
			true
		}

		AppSettings.KEY_HANDLE_LINKS -> {
			viewModel.setLinksEnabled((preference as TwoStatePreference).isChecked)
			true
		}

		"setup_wizard" -> {
			router.showWelcomeSheet()
			true
		}



		else -> super.onPreferenceTreeClick(preference)
	}

	override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
		when (key) {
			AppSettings.KEY_SOURCES_ENABLED_ALL -> updateEnableAllDependencies()
		}
	}

	private fun updateEnableAllDependencies() {
		findPreference<Preference>(AppSettings.KEY_SOURCES_CATALOG)?.isEnabled = !settings.isAllSourcesEnabled
	}
}
