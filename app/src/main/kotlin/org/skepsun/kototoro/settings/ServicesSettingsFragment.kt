package org.skepsun.kototoro.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.preference.Preference
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.ui.BasePreferenceFragment
import org.skepsun.kototoro.core.ui.dialog.buildAlertDialog
import org.skepsun.kototoro.core.util.ext.getDisplayMessage
import org.skepsun.kototoro.core.util.ext.printStackTraceDebug
import org.skepsun.kototoro.core.util.ext.viewLifecycleScope
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService
import org.skepsun.kototoro.scrobbling.common.ui.ScrobblerAuthHelper
import org.skepsun.kototoro.settings.utils.SplitSwitchPreference
import javax.inject.Inject

@AndroidEntryPoint
class ServicesSettingsFragment : BasePreferenceFragment(R.string.services),
	SharedPreferences.OnSharedPreferenceChangeListener {

	@Inject
	lateinit var scrobblerAuthHelper: ScrobblerAuthHelper

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_services)
		findPreference<SplitSwitchPreference>(AppSettings.KEY_STATS_ENABLED)?.let {
			it.onContainerClickListener = Preference.OnPreferenceClickListener {
				router.openStatistic()
				true
			}
		}
		// Populate preferred tracking site ListPreference
		findPreference<androidx.preference.ListPreference>(AppSettings.KEY_PREFERRED_TRACKING_SITE)?.let { pref ->
			val services = ScrobblerService.entries
			pref.entries = services.map { getString(it.titleResId) }.toTypedArray()
			pref.entryValues = services.map { it.name }.toTypedArray()
			if (pref.value == null) {
				pref.value = settings.preferredTrackingSite.name
			}
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		bindSuggestionsSummary()
		bindStatsSummary()
		settings.subscribe(this)
	}

	override fun onDestroyView() {
		settings.unsubscribe(this)
		super.onDestroyView()
	}

	override fun onResume() {
		super.onResume()
		bindScrobblerSummary(AppSettings.KEY_SHIKIMORI, ScrobblerService.SHIKIMORI)
		bindScrobblerSummary(AppSettings.KEY_ANILIST, ScrobblerService.ANILIST)
		bindScrobblerSummary(AppSettings.KEY_MAL, ScrobblerService.MAL)
		bindScrobblerSummary(AppSettings.KEY_KITSU, ScrobblerService.KITSU)
		bindScrobblerSummary(AppSettings.KEY_BANGUMI, ScrobblerService.BANGUMI)
		bindScrobblerSummary(AppSettings.KEY_MANGAUPDATES, ScrobblerService.MANGAUPDATES)
	}

	override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
		when (key) {
			AppSettings.KEY_SUGGESTIONS -> bindSuggestionsSummary()
			AppSettings.KEY_STATS_ENABLED -> bindStatsSummary()
		}
	}


	override fun onPreferenceTreeClick(preference: Preference): Boolean {
		return when (preference.key) {
			AppSettings.KEY_SHIKIMORI -> {
				handleScrobblerClick(ScrobblerService.SHIKIMORI)
				true
			}

			AppSettings.KEY_MAL -> {
				handleScrobblerClick(ScrobblerService.MAL)
				true
			}

			AppSettings.KEY_ANILIST -> {
				handleScrobblerClick(ScrobblerService.ANILIST)
				true
			}

			AppSettings.KEY_KITSU -> {
				handleScrobblerClick(ScrobblerService.KITSU)
				true
			}

			AppSettings.KEY_BANGUMI -> {
				handleScrobblerClick(ScrobblerService.BANGUMI)
				true
			}

			AppSettings.KEY_MANGAUPDATES -> {
				handleScrobblerClick(ScrobblerService.MANGAUPDATES)
				true
			}

			else -> super.onPreferenceTreeClick(preference)
		}
	}

	private fun bindScrobblerSummary(
		key: String,
		scrobblerService: ScrobblerService
	) {
		val pref = findPreference<Preference>(key) ?: return
		if (!scrobblerAuthHelper.isAuthorized(scrobblerService)) {
			pref.setSummary(R.string.disabled)
			return
		}
		val username = scrobblerAuthHelper.getCachedUser(scrobblerService)?.nickname
		if (username != null) {
			pref.summary = getString(R.string.logged_in_as, username)
		} else {
			pref.setSummary(R.string.loading_)
			viewLifecycleScope.launch {
				pref.summary = withContext(Dispatchers.Default) {
					runCatching {
						val user = scrobblerAuthHelper.getUser(scrobblerService)
						getString(R.string.logged_in_as, user.nickname)
					}.getOrElse {
						it.printStackTraceDebug()
						it.getDisplayMessage(resources)
					}
				}
			}
		}
	}

	private fun handleScrobblerClick(scrobblerService: ScrobblerService) {
		if (!scrobblerAuthHelper.isAuthorized(scrobblerService)) {
			confirmScrobblerAuth(scrobblerService)
		} else {
			router.openScrobblerSettings(scrobblerService)
		}
	}

	private fun bindSuggestionsSummary() {
		findPreference<Preference>(AppSettings.KEY_SUGGESTIONS)?.setSummary(
			if (settings.isSuggestionsEnabled) R.string.enabled else R.string.disabled,
		)
	}

	private fun bindStatsSummary() {
		findPreference<Preference>(AppSettings.KEY_STATS_ENABLED)?.setSummary(
			if (settings.isStatsEnabled) R.string.enabled else R.string.disabled,
		)
	}

	private fun confirmScrobblerAuth(scrobblerService: ScrobblerService) {
		buildAlertDialog(context ?: return, isCentered = true) {
			setIcon(scrobblerService.iconResId)
			setTitle(scrobblerService.titleResId)
			setMessage(context.getString(R.string.scrobbler_auth_intro, context.getString(scrobblerService.titleResId)))
			setPositiveButton(R.string.sign_in) { _, _ ->
				scrobblerAuthHelper.startAuth(context, scrobblerService).onFailure {
					Snackbar.make(listView, it.getDisplayMessage(resources), Snackbar.LENGTH_LONG).show()
				}
			}
			setNegativeButton(android.R.string.cancel, null)
		}.show()
	}
}
