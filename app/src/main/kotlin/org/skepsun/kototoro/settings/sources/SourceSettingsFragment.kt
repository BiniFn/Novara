package org.skepsun.kototoro.settings.sources

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.EditTextPreferenceDialogFragmentCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.SwitchPreferenceCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.skepsun.kototoro.settings.utils.EditTextBindListener
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.exceptions.resolve.SnackbarErrorObserver
import org.skepsun.kototoro.core.model.getEnableSourceTitleResId
import org.skepsun.kototoro.core.model.getRecommendationTermResId
import org.skepsun.kototoro.core.model.getTitle
import org.skepsun.kototoro.core.model.unwrap
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.parser.EmptyMangaRepository
import org.skepsun.kototoro.core.parser.JsMangaRepository
import org.skepsun.kototoro.core.parser.ParserMangaRepository
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.SourceSettings
import org.skepsun.kototoro.core.ui.BasePreferenceFragment
import org.skepsun.kototoro.core.ui.util.ReversibleActionObserver
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.core.util.ext.observeEvent
import org.skepsun.kototoro.core.util.ext.withArgs
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.parsers.model.MangaParserSource
import org.skepsun.kototoro.parsers.MangaParserCredentialsAuthProvider
import org.skepsun.kototoro.settings.utils.PasswordSummaryProvider
import java.io.File
import java.util.regex.Pattern
import android.widget.Toast

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

		val contentType = (viewModel.source.unwrap() as? MangaParserSource)?.contentType ?: ContentType.MANGA
		findPreference<SwitchPreferenceCompat>(KEY_ENABLE)?.run {
			isVisible = isValidSource && !settings.isAllSourcesEnabled
			onPreferenceChangeListener = this@SourceSettingsFragment
			setTitle(contentType.getEnableSourceTitleResId())
		}
		findPreference<SwitchPreferenceCompat>("no_captcha")?.run {
			summary = getString(R.string.disable_captcha_notifications_summary, getString(contentType.getRecommendationTermResId()))
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
		tryAddJsPreferences()
		viewLifecycleOwner.lifecycleScope.launchWhenStarted {
			viewModel.jsAccountMeta.collect { meta ->
				if (meta != null) {
					addJsLoginPreferences(meta)
				}
			}
		}
		viewModel.jsLoginState.observeEvent(viewLifecycleOwner) { ok ->
			val msg = if (ok) getString(R.string.auth_complete) else getString(R.string.auth_required)
			Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
		}
		viewModel.jsWebLoginState.observeEvent(viewLifecycleOwner) { ok ->
			val msg = if (ok) getString(R.string.auth_complete) else getString(R.string.auth_required)
			Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
		}
		findPreference<Preference>(KEY_JS_COOKIE_SUBMIT)?.setOnPreferenceClickListener {
			val meta = viewModel.jsAccountMeta.value ?: return@setOnPreferenceClickListener true
			val values = meta.cookieFields.mapIndexedNotNull { idx, field ->
				val key = "$KEY_JS_COOKIE_PREFIX$idx"
				val valStr = findPreference<EditTextPreference>(key)?.text
				if (valStr.isNullOrBlank()) null else field to valStr
			}.toMap()
			if (values.isNotEmpty()) {
				val ok = viewModel.setJsCookies(values)
				Toast.makeText(requireContext(), if (ok) R.string.cookies_saved else R.string.cookies_cleared, Toast.LENGTH_SHORT).show()
			}
			true
		}
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

	private fun addJsLoginPreferences(meta: JsMangaRepository.JsAccountMeta) {
		if (!meta.hasLogin && meta.cookieFields.isEmpty()) return
		val screen = preferenceScreen ?: return
		if (findPreference<PreferenceCategory>(KEY_JS_ACCOUNT_CATEGORY) == null) {
			val cat = PreferenceCategory(preferenceManager.context).apply {
				key = KEY_JS_ACCOUNT_CATEGORY
				title = getString(R.string.auth_title)
			}
			screen.addPreference(cat)
		}
		val category = findPreference<PreferenceCategory>(KEY_JS_ACCOUNT_CATEGORY) ?: return
		if (meta.hasLogin) {
			val userPref = findPreference<EditTextPreference>(KEY_JS_USERNAME) ?: EditTextPreference(preferenceManager.context).apply {
				key = KEY_JS_USERNAME
				title = getString(R.string.username)
				summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
				category.addPreference(this)
			}
			val pwdPref = findPreference<EditTextPreference>(KEY_JS_PASSWORD) ?: EditTextPreference(preferenceManager.context).apply {
				key = KEY_JS_PASSWORD
				title = getString(R.string.password)
				summaryProvider = PasswordSummaryProvider()
				setOnBindEditTextListener(
					EditTextBindListener(
						inputType = android.view.inputmethod.EditorInfo.TYPE_CLASS_TEXT or android.view.inputmethod.EditorInfo.TYPE_TEXT_VARIATION_PASSWORD,
						hint = null,
						validator = null,
					),
				)
				category.addPreference(this)
			}
			if (findPreference<Preference>(KEY_JS_LOGIN) == null) {
				Preference(preferenceManager.context).apply {
					key = KEY_JS_LOGIN
					title = getString(R.string.sign_in)
					setOnPreferenceClickListener {
						val user = userPref.text.orEmpty()
						val pwd = pwdPref.text.orEmpty()
						viewLifecycleOwner.lifecycleScope.launch {
							viewModel.loginJs(user, pwd)
						}
						true
					}
					category.addPreference(this)
				}
			}
			if (findPreference<Preference>(KEY_JS_LOGOUT) == null) {
				Preference(preferenceManager.context).apply {
					key = KEY_JS_LOGOUT
					title = getString(R.string.logout)
					setOnPreferenceClickListener {
						viewLifecycleOwner.lifecycleScope.launch { viewModel.logoutJs() }
						true
					}
					category.addPreference(this)
				}
			}
		}
		if (meta.hasWebLogin && !meta.webLoginUrl.isNullOrBlank()) {
			if (findPreference<Preference>(KEY_JS_WEB_LOGIN) == null) {
				Preference(preferenceManager.context).apply {
					key = KEY_JS_WEB_LOGIN
					title = getString(R.string.login_with_browser)
					summary = meta.webLoginUrl
					setOnPreferenceClickListener {
						viewLifecycleOwner.lifecycleScope.launch {
							viewModel.loginJsWithWebview()
						}
						true
					}
					category.addPreference(this)
				}
			}
		}
		if (meta.cookieFields.isNotEmpty()) {
			meta.cookieFields.forEachIndexed { idx, field ->
				val key = "$KEY_JS_COOKIE_PREFIX$idx"
				if (findPreference<EditTextPreference>(key) == null) {
					EditTextPreference(preferenceManager.context).apply {
						this.key = key
						title = field
						summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
						category.addPreference(this)
					}
				}
			}
			if (findPreference<Preference>(KEY_JS_COOKIE_SUBMIT) == null) {
				Preference(preferenceManager.context).apply {
					key = KEY_JS_COOKIE_SUBMIT
					title = getString(R.string.save)
					setOnPreferenceClickListener {
						val values = meta.cookieFields.mapIndexedNotNull { idx, field ->
							val valStr = findPreference<EditTextPreference>("$KEY_JS_COOKIE_PREFIX$idx")?.text
							if (valStr.isNullOrBlank()) null else field to valStr
						}.toMap()
						if (values.isNotEmpty()) {
							val ok = viewModel.setJsCookies(values)
							Toast.makeText(requireContext(), if (ok) R.string.cookies_saved else R.string.cookies_cleared, Toast.LENGTH_SHORT).show()
						}
						true
					}
					category.addPreference(this)
				}
			}
		}
	}

	override fun onPreferenceTreeClick(preference: Preference): Boolean {
		return when (preference.key) {
			KEY_AUTH -> {
				router.openSourceAuth(viewModel.source)
				true
			}

			KEY_AUTH_LOGIN -> {
				val username = findPreference<EditTextPreference>(KEY_AUTH_USERNAME)?.text?.trim().orEmpty()
				val password = findPreference<EditTextPreference>(KEY_AUTH_PASSWORD)?.text?.trim().orEmpty()
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
		private const val KEY_JS_ACCOUNT_CATEGORY = "js_account"
		private const val KEY_JS_USERNAME = "js_username"
		private const val KEY_JS_PASSWORD = "js_password"
		private const val KEY_JS_LOGIN = "js_login"
		private const val KEY_JS_LOGOUT = "js_logout"
		private const val KEY_JS_COOKIE_PREFIX = "js_cookie_"
		private const val KEY_JS_COOKIE_SUBMIT = "js_cookie_submit"
		private const val KEY_JS_WEB_LOGIN = "js_web_login"

		fun newInstance(source: org.skepsun.kototoro.parsers.model.MangaSource) = SourceSettingsFragment().withArgs(1) {
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

	private fun tryAddJsPreferences() {
		val repo = viewModel.repository as? JsMangaRepository ?: return
		viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
			val schema = runCatching { repo.fetchSettingsSchema() }.getOrDefault(emptyList())
			if (schema.isEmpty()) return@launch
			withContext(Dispatchers.Main) {
				var order = 600
				schema.forEach { item ->
					when (item.type.lowercase()) {
						"select" -> {
							val pref = ListPreference(requireContext()).apply {
								key = "js_${item.key}"
								title = item.title
								isIconSpaceReserved = false
								entries = item.options.map { it.text }.toTypedArray()
								entryValues = item.options.map { it.value }.toTypedArray()
								val current = repo.getJsSettingValue(item.key) as? String
									?: item.defaultValue
									?: item.options.firstOrNull()?.value
								value = current
								order = order++
								summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
								setOnPreferenceChangeListener { _, newValue ->
									repo.saveJsSettingValue(item.key, newValue as? String)
									true
								}
							}
							preferenceScreen.addPreference(pref)
						}
						"callback" -> {
							val pref = Preference(requireContext()).apply {
								key = "js_${item.key}"
								title = item.title
								summary = item.buttonText
								isIconSpaceReserved = false
								order = order++
								setOnPreferenceClickListener {
									viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
										repo.executeSettingCallback(item.key)
									}
									true
								}
							}
							preferenceScreen.addPreference(pref)
						}
						else -> {
							val pref = EditTextPreference(requireContext()).apply {
								key = "js_${item.key}"
								title = item.title
								isIconSpaceReserved = false
								order = order++
								summary = ""
								val current = repo.getJsSettingValue(item.key) as? String
									?: item.defaultValue
								text = current
								val pattern = item.validator?.takeIf { it.isNotBlank() }?.let { Pattern.compile(it) }
								setOnBindEditTextListener(
									EditTextBindListener(
										inputType = android.view.inputmethod.EditorInfo.TYPE_CLASS_TEXT,
										hint = item.defaultValue,
										validator = null,
									),
								)
								setOnPreferenceChangeListener { _, newValue ->
									val str = newValue as? String ?: ""
									if (pattern != null && !pattern.matcher(str).matches()) {
										return@setOnPreferenceChangeListener false
									}
									repo.saveJsSettingValue(item.key, str)
									true
								}
							}
							preferenceScreen.addPreference(pref)
						}
					}
				}
			}
		}
	}
}
