package org.koitharu.kotatsu.settings.sources

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import androidx.preference.EditTextPreference
import androidx.preference.EditTextPreferenceDialogFragmentCompat
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filterNotNull
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.exceptions.resolve.SnackbarErrorObserver
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.nav.router
import org.koitharu.kotatsu.core.parser.EmptyMangaRepository
import org.koitharu.kotatsu.core.parser.ParserMangaRepository
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.SourceSettings
import org.koitharu.kotatsu.core.ui.BasePreferenceFragment
import org.koitharu.kotatsu.core.ui.util.ReversibleActionObserver
import org.koitharu.kotatsu.core.util.ext.observe
import org.koitharu.kotatsu.core.util.ext.observeEvent
import org.koitharu.kotatsu.core.util.ext.withArgs
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.MangaParserCredentialsAuthProvider
import org.koitharu.kotatsu.settings.utils.EditTextBindListener
import org.koitharu.kotatsu.settings.utils.PasswordSummaryProvider
import java.io.File

@AndroidEntryPoint
class SourceSettingsFragment : BasePreferenceFragment(0), Preference.OnPreferenceChangeListener {

	private val viewModel: SourceSettingsViewModel by viewModels()

	override fun onResume() {
		super.onResume()
		context?.let { ctx ->
			setTitle(viewModel.source.getTitle(ctx))
		}
		viewModel.onResume()
	}

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		preferenceManager.sharedPreferencesName = viewModel.source.name.replace(File.separatorChar, '$')
		addPreferencesFromResource(R.xml.pref_source)
		addPreferencesFromRepository(viewModel.repository)
		val isValidSource = viewModel.repository !is EmptyMangaRepository

		findPreference<SwitchPreferenceCompat>(KEY_ENABLE)?.run {
			isVisible = isValidSource && !settings.isAllSourcesEnabled
			onPreferenceChangeListener = this@SourceSettingsFragment
		}
        // 显示 Web 登录入口：当解析器支持“网页登录”但不支持“凭证登录”时才显示
        findPreference<Preference>(KEY_AUTH)?.run {
            val repo = (viewModel.repository as? ParserMangaRepository)
            val authProvider = repo?.getAuthProvider()
            val credentialsProvider = authProvider as? MangaParserCredentialsAuthProvider
            isVisible = authProvider != null && credentialsProvider == null
        }

        // 如果解析器支持用户名/密码登录，在当前页面插入输入框与登录按钮
        val credentialsProvider = (viewModel.repository as? ParserMangaRepository)?.getAuthProvider() as? MangaParserCredentialsAuthProvider
        if (credentialsProvider != null) {
            addCredentialsPreferences()
        }
		findPreference<Preference>(SourceSettings.KEY_SLOWDOWN)?.isVisible = isValidSource
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		viewModel.isAuthorized.filterNotNull().observe(viewLifecycleOwner) { isAuthorized ->
			findPreference<Preference>(KEY_AUTH)?.isEnabled = !isAuthorized
			findPreference<Preference>(KEY_AUTH_STATUS)?.summary = if (isAuthorized) {
				viewModel.username.value?.let { getString(R.string.logged_in_as, it) } ?: getString(R.string.auth_complete)
			} else {
				getString(R.string.auth_required)
			}
		}
		viewModel.username.observe(viewLifecycleOwner) { username ->
			findPreference<Preference>(KEY_AUTH)?.summary = username?.let {
				getString(R.string.logged_in_as, it)
			}
			findPreference<Preference>(KEY_AUTH_STATUS)?.summary = username?.let {
				getString(R.string.logged_in_as, it)
			} ?: getString(R.string.auth_required)
		}
		viewModel.onError.observeEvent(
			viewLifecycleOwner,
			SnackbarErrorObserver(
				listView,
				this,
				exceptionResolver,
			) { viewModel.onResume() },
		)
		viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
			findPreference<Preference>(KEY_AUTH)?.isEnabled = !isLoading
			findPreference<Preference>(KEY_AUTH_LOGIN)?.isEnabled = !isLoading
		}
		viewModel.isEnabled.observe(viewLifecycleOwner) { enabled ->
			findPreference<SwitchPreferenceCompat>(KEY_ENABLE)?.isChecked = enabled
		}
		viewModel.browserUrl.observe(viewLifecycleOwner) {
			findPreference<Preference>(AppSettings.KEY_OPEN_BROWSER)?.run {
				isVisible = it != null
				summary = it
			}
		}
		viewModel.onActionDone.observeEvent(viewLifecycleOwner, ReversibleActionObserver(listView))
	}

	override fun onPreferenceTreeClick(preference: Preference): Boolean {
		return when (preference.key) {
			KEY_AUTH -> {
				router.openSourceAuth(viewModel.source)
				true
			}

			KEY_AUTH_LOGIN -> {
				val username = findPreference<EditTextPreference>(KEY_AUTH_USERNAME)?.text.orEmpty()
				val password = findPreference<EditTextPreference>(KEY_AUTH_PASSWORD)?.text.orEmpty()
				viewModel.loginByCredentials(username, password)
				true
			}

			AppSettings.KEY_OPEN_BROWSER -> {
				router.openBrowser(
					url = viewModel.browserUrl.value ?: return false,
					source = viewModel.source,
					title = viewModel.source.getTitle(preference.context),
				)
				true
			}

			AppSettings.KEY_COOKIES_CLEAR -> {
				viewModel.clearCookies()
				true
			}

			else -> super.onPreferenceTreeClick(preference)
		}
	}

	override fun onDisplayPreferenceDialog(preference: Preference) {
		if (preference.key == SourceSettings.KEY_DOMAIN) {
			if (parentFragmentManager.findFragmentByTag(DomainDialogFragment.DIALOG_FRAGMENT_TAG) != null) {
				return
			}
			val f = DomainDialogFragment.newInstance(preference.key)
			@Suppress("DEPRECATION")
			f.setTargetFragment(this, 0)
			f.show(parentFragmentManager, DomainDialogFragment.DIALOG_FRAGMENT_TAG)
			return
		}
		super.onDisplayPreferenceDialog(preference)
	}

	override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
		when (preference.key) {
			KEY_ENABLE -> viewModel.setEnabled(newValue == true)
			else -> return false
		}
		return true
	}

	class DomainDialogFragment : EditTextPreferenceDialogFragmentCompat() {

		override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
			super.onPrepareDialogBuilder(builder)
			builder.setNeutralButton(R.string.reset) { _, _ ->
				resetValue()
			}
		}

		private fun resetValue() {
			val editTextPreference = preference as EditTextPreference
			if (editTextPreference.callChangeListener("")) {
				editTextPreference.text = ""
			}
		}

		companion object {

			const val DIALOG_FRAGMENT_TAG: String = "androidx.preference.PreferenceFragment.DIALOG"

			fun newInstance(key: String) = DomainDialogFragment().withArgs(1) {
				putString(ARG_KEY, key)
			}
		}
	}

	companion object {

		private const val KEY_AUTH = "auth"
		private const val KEY_ENABLE = "enable"
		private const val KEY_AUTH_STATUS = "auth_status"
		private const val KEY_AUTH_USERNAME = "auth_username"
		private const val KEY_AUTH_PASSWORD = "auth_password"
		private const val KEY_AUTH_LOGIN = "auth_login"

		fun newInstance(source: MangaSource) = SourceSettingsFragment().withArgs(1) {
			putString(AppRouter.KEY_SOURCE, source.name)
		}
	}

	private fun addCredentialsPreferences() {
		// 登录状态展示（位于用户名/密码输入之前）
		Preference(requireContext()).apply {
			key = KEY_AUTH_STATUS
			order = 97
			isIconSpaceReserved = false
			summary = getString(R.string.auth_required)
			setPersistent(false)
			preferenceScreen.addPreference(this)
		}
		// 用户名输入
		EditTextPreference(requireContext()).apply {
			key = KEY_AUTH_USERNAME
			order = 98
			isIconSpaceReserved = false
			title = getString(R.string.enter_name)
			setOnBindEditTextListener(
				EditTextBindListener(
					inputType = android.view.inputmethod.EditorInfo.TYPE_CLASS_TEXT,
					hint = null,
					validator = null,
				),
			)
			preferenceScreen.addPreference(this)
		}
		// 密码输入
		EditTextPreference(requireContext()).apply {
			key = KEY_AUTH_PASSWORD
			order = 99
			isIconSpaceReserved = false
			title = getString(R.string.enter_password)
			summaryProvider = PasswordSummaryProvider()
			setOnBindEditTextListener(
				EditTextBindListener(
					inputType = android.view.inputmethod.EditorInfo.TYPE_CLASS_TEXT or android.view.inputmethod.EditorInfo.TYPE_TEXT_VARIATION_PASSWORD,
					hint = null,
					validator = null,
				),
			)
			preferenceScreen.addPreference(this)
		}
		// 登录按钮
		Preference(requireContext()).apply {
			key = KEY_AUTH_LOGIN
			order = 100
			isIconSpaceReserved = false
			title = getString(R.string.sign_in)
			setPersistent(false)
			preferenceScreen.addPreference(this)
		}
	}
}
