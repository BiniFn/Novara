package org.skepsun.kototoro.settings.sources

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.EditTextPreferenceDialogFragmentCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import androidx.preference.get
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.skepsun.kototoro.settings.utils.EditTextBindListener
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.exceptions.resolve.ExceptionResolver
import org.skepsun.kototoro.core.exceptions.resolve.SnackbarErrorObserver
import org.skepsun.kototoro.core.model.getEnableSourceTitleResId
import org.skepsun.kototoro.core.model.getContentType
import org.skepsun.kototoro.core.model.getRecommendationTermResId
import org.skepsun.kototoro.core.model.getTitle
import org.skepsun.kototoro.core.model.unwrap
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.parser.EmptyContentRepository
import org.skepsun.kototoro.core.parser.JsContentRepository
import org.skepsun.kototoro.core.parser.ParserContentRepository
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.SourceSettings
import org.skepsun.kototoro.core.ui.BaseActivityEntryPoint
import org.skepsun.kototoro.core.util.ext.consumeAllSystemBarsInsets
import org.skepsun.kototoro.core.util.ext.container
import org.skepsun.kototoro.core.util.ext.end
import org.skepsun.kototoro.core.util.ext.getThemeColor
import org.skepsun.kototoro.core.util.ext.getThemeDrawable
import org.skepsun.kototoro.core.ui.util.ReversibleActionObserver
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.core.util.ext.observeEvent
import org.skepsun.kototoro.core.util.ext.parentView
import org.skepsun.kototoro.core.util.ext.start
import org.skepsun.kototoro.core.util.ext.systemBarsInsets
import org.skepsun.kototoro.core.util.ext.withArgs
import org.skepsun.kototoro.parsers.model.ContentType

import org.skepsun.kototoro.parsers.ContentParserCredentialsAuthProvider
import org.skepsun.kototoro.settings.utils.PasswordSummaryProvider
import android.widget.Toast
import eu.kanade.tachiyomi.source.ConfigurableSource
import org.skepsun.kototoro.mihon.MihonMangaRepository
import java.io.File
import java.util.regex.Pattern
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.skepsun.kototoro.core.jsonsource.JsonContentSource
import org.skepsun.kototoro.core.model.jsonsource.LegadoBookSource
import org.skepsun.kototoro.core.model.jsonsource.TVBoxStoredConfig
import org.skepsun.kototoro.core.parser.tvbox.TVBoxRepository
import org.skepsun.kototoro.settings.utils.EditTextDefaultSummaryProvider
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.skepsun.kototoro.settings.SettingsActivity
import javax.inject.Inject
import com.google.android.material.R as materialR

@AndroidEntryPoint
class SourceSettingsFragment :
	PreferenceFragmentCompat(),
	OnApplyWindowInsetsListener,
	Preference.OnPreferenceChangeListener {

	@Inject
	lateinit var settings: AppSettings

	private lateinit var exceptionResolver: ExceptionResolver

	private val viewModel: SourceSettingsViewModel by viewModels()

	private val legadoJson by lazy {
		Json { ignoreUnknownKeys = true; isLenient = true; allowTrailingComma = true }
	}

	override fun onAttach(context: Context) {
		super.onAttach(context)
		val entryPoint = EntryPointAccessors.fromApplication<BaseActivityEntryPoint>(context)
		exceptionResolver = entryPoint.exceptionResolverFactory.create(this)
	}

	override fun onResume() {
		super.onResume()
		context?.let { ctx ->
			(activity as? SettingsActivity)?.setSectionTitle(viewModel.source.getTitle(ctx))
		}
		arguments?.getString(SettingsActivity.ARG_PREF_KEY)?.let { key ->
			focusPreference(key)
			arguments?.remove(SettingsActivity.ARG_PREF_KEY)
		}
		viewModel.onResume()
	}

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		val repo = viewModel.repository
		if (repo is MihonMangaRepository) {
			preferenceManager.sharedPreferencesName = "source_${repo.mihonSource.id}"
		} else if (repo is org.skepsun.kototoro.aniyomi.AniyomiAnimeRepository) {
			preferenceManager.sharedPreferencesName = "source_${repo.aniyomiSource.id}"
		} else {
			preferenceManager.sharedPreferencesName = viewModel.source.name.replace(File.separatorChar, '$')
		}
		
		addPreferencesFromResource(R.xml.pref_source)
		addPreferencesFromRepository(viewModel.repository)
		val isValidSource = viewModel.repository !is EmptyContentRepository

		val contentType = viewModel.source.getContentType()
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
            val parserRepo = (viewModel.repository as? ParserContentRepository)
            val authProvider = parserRepo?.getAuthProvider()
            val credentialsProvider = authProvider as? ContentParserCredentialsAuthProvider
            isVisible = authProvider != null && credentialsProvider == null
        }

        // 如果解析器支持用户名/密码登录，在当前页面插入输入框与登录按钮
        val credentialsProvider = (viewModel.repository as? ParserContentRepository)?.getAuthProvider() as? ContentParserCredentialsAuthProvider
		if (credentialsProvider != null) {
            addCredentialsPreferences()
        }
		findPreference<Preference>(SourceSettings.KEY_SLOWDOWN)?.isVisible = isValidSource
		tryAddTvBoxPreferences()
		tryAddLegadoVariablePreferences()
		tryAddLegadoAuthPreferences()
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		ViewCompat.setOnApplyWindowInsetsListener(view, this)
		val themedContext = (view.parentView ?: view).context
		view.setBackgroundColor(themedContext.getThemeColor(android.R.attr.colorBackground))
		listView.clipToPadding = false
		tryAddJsPreferences()
		tryAddMihonPreferences()
		tryAddAniyomiPreferences()
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

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val barsInsets = insets.systemBarsInsets
		val isTablet = !resources.getBoolean(R.bool.is_tablet)
		val isMaster = container?.id == R.id.container_master
		listView.setPaddingRelative(
			if (isTablet && !isMaster) 0 else barsInsets.start(v),
			0,
			if (isTablet && isMaster) 0 else barsInsets.end(v),
			barsInsets.bottom,
		)
		return insets.consumeAllSystemBarsInsets()
	}

	private fun tryAddTvBoxPreferences() {
		val (repo, config) = getTvBoxRepoAndConfigOrNull() ?: return
		val screen = preferenceScreen ?: return
		val candidates = buildTvBoxResourceCandidates(config)
		val runtimeSummary = buildTvBoxRuntimeSummary(repo, config)
		val category = findPreference<PreferenceCategory>(KEY_TVBOX_CATEGORY)
			?: PreferenceCategory(requireContext()).apply {
				key = KEY_TVBOX_CATEGORY
				title = getString(R.string.source_type_tvbox)
				order = 30
				isIconSpaceReserved = false
				screen.addPreference(this)
			}

		fun addInfoPreference(key: String, title: String, summary: String, order: Int) {
			val preference = findPreference<Preference>(key) ?: Preference(requireContext()).apply {
				this.key = key
				this.title = title
				this.order = order
				isIconSpaceReserved = false
				isPersistent = false
				category.addPreference(this)
			}
			preference.summary = summary
		}

		addInfoPreference(
			key = KEY_TVBOX_SITE_KEY,
			title = getString(R.string.tvbox_site_key_title),
			summary = config.site.key.ifBlank { getString(R.string.not_specified) },
			order = 31,
		)
		addInfoPreference(
			key = KEY_TVBOX_SITE_API,
			title = getString(R.string.tvbox_site_api_title),
			summary = config.site.api.ifBlank { getString(R.string.not_specified) },
			order = 32,
		)
		addInfoPreference(
			key = KEY_TVBOX_SITE_TYPE,
			title = getString(R.string.tvbox_site_type_title),
			summary = getTvBoxTypeSummary(config),
			order = 33,
		)
		config.root.spider?.takeIf { it.isNotBlank() }?.let { spider ->
			addInfoPreference(
				key = KEY_TVBOX_ROOT_SPIDER,
				title = getString(R.string.tvbox_root_spider_title),
				summary = spider,
				order = 34,
			)
		}
		config.site.jar?.takeIf { it.isNotBlank() }?.let { jar ->
			addInfoPreference(
				key = KEY_TVBOX_SITE_JAR,
				title = getString(R.string.tvbox_site_jar_title),
				summary = jar,
				order = 35,
			)
		}

		config.meta.sourceLocator?.takeIf { it.isNotBlank() }?.let { locator ->
			addInfoPreference(
				key = KEY_TVBOX_SOURCE_LOCATOR,
				title = getString(R.string.tvbox_source_locator_title),
				summary = locator,
				order = 36,
			)
		}
		runtimeSummary?.let { summary ->
			addInfoPreference(
				key = KEY_TVBOX_RUNTIME_STRATEGY,
				title = getString(R.string.tvbox_runtime_strategy_title),
				summary = summary,
				order = 37,
			)
		}

		if (candidates.isNotEmpty()) {
			addInfoPreference(
				key = KEY_TVBOX_RUNTIME_CANDIDATES,
				title = getString(R.string.tvbox_runtime_candidates_title),
				summary = candidates.joinToString(separator = "\n"),
				order = 38,
			)
		}

		if (findPreference<Preference>(KEY_TVBOX_STATUS) == null) {
			Preference(requireContext()).apply {
				key = KEY_TVBOX_STATUS
				title = getString(R.string.tvbox_support_status_title)
				order = 39
				isIconSpaceReserved = false
				isPersistent = false
				category.addPreference(this)
			}
		}
		findPreference<Preference>(KEY_TVBOX_STATUS)?.summary = getTvBoxSupportStatusSummary(config, candidates)
	}

	private fun getTvBoxSupportStatusSummary(config: TVBoxStoredConfig, candidates: List<String>): String {
		val hasPlayableCandidate = candidates.any(::looksLikeTvBoxPlayableCandidate)
		val hasCmsCandidate = candidates.any(::looksLikeTvBoxCmsCandidate)
		val hasSpiderArtifacts = hasTvBoxSpiderArtifacts(config)
		if (config.site.type == 4) {
			return getString(R.string.tvbox_support_status_quickjs_partial)
		}
		if (hasPlayableCandidate || hasCmsCandidate) {
			return if (hasSpiderArtifacts) {
				getString(R.string.tvbox_support_status_bridgeable)
			} else {
				getString(R.string.tvbox_support_status_partial_runtime)
			}
		}
		return when {
			hasSpiderArtifacts -> getString(R.string.tvbox_support_status_spider_bridge)
			else -> getString(R.string.tvbox_support_status_direct)
		}
	}

	private fun buildTvBoxRuntimeSummary(repo: TVBoxRepository, config: TVBoxStoredConfig): String? {
		val capability = repo.getRuntimeCapabilitySummary()
		val note = repo.getRuntimeUnavailabilitySummary()
		if (capability == null && note == null) {
			return if (hasTvBoxSpiderArtifacts(config)) {
				getString(R.string.tvbox_runtime_strategy_none)
			} else {
				null
			}
		}
		return buildString {
			capability?.let { append(it) }
			note?.takeIf { it.isNotBlank() }?.let {
				if (isNotEmpty()) {
					append('\n')
				}
				append(it)
			}
		}.ifBlank { null }
	}

	private fun hasTvBoxSpiderArtifacts(config: TVBoxStoredConfig): Boolean {
		return !config.root.spider.isNullOrBlank() ||
			!config.site.jar.isNullOrBlank() ||
			config.site.type == 3 ||
			config.site.type == 4 ||
			config.site.api.startsWith("csp_", ignoreCase = true)
	}

	private fun buildTvBoxResourceCandidates(config: TVBoxStoredConfig): List<String> {
		val dedup = linkedSetOf<String>()

		fun add(rawValue: String?) {
			resolveTvBoxCandidateUrl(config, rawValue)?.let(dedup::add)
		}

		add(config.site.api)
		add(config.site.playUrl)
		when (val ext = config.site.ext) {
			is String -> add(ext)
			is org.json.JSONObject -> {
				listOf("url", "api", "playUrl", "link", "file", "m3u", "m3u8")
					.forEach { key -> add(ext.optString(key).trim().ifBlank { null }) }
			}
		}
		return dedup.toList()
	}

	private fun resolveTvBoxCandidateUrl(config: TVBoxStoredConfig, rawValue: String?): String? {
		val value = rawValue?.trim().orEmpty()
		if (value.isBlank()) {
			return null
		}
		if (value.startsWith("http://", ignoreCase = true) ||
			value.startsWith("https://", ignoreCase = true) ||
			value.startsWith("content://", ignoreCase = true) ||
			value.startsWith("file://", ignoreCase = true)
		) {
			return value
		}
		if (value.startsWith("//")) {
			return "https:$value"
		}
		val baseUrl = config.meta.sourceLocator
		val baseHttpUrl = baseUrl?.toHttpUrlOrNull()
		if (baseHttpUrl != null) {
			return baseHttpUrl.resolve(value)?.toString()
		}
		return null
	}

	private fun looksLikeTvBoxPlayableCandidate(url: String): Boolean {
		val normalized = url.lowercase()
		return normalized.contains(".m3u8") ||
			normalized.contains(".mp4") ||
			normalized.contains(".flv") ||
			normalized.contains(".mpd") ||
			normalized.contains(".mkv") ||
			normalized.contains(".webm") ||
			normalized.contains(".avi") ||
			normalized.contains(".mov") ||
			normalized.endsWith(".m3u")
	}

	private fun looksLikeTvBoxCmsCandidate(url: String): Boolean {
		val normalized = url.lowercase()
		return normalized.contains("provide/vod") ||
			normalized.contains("api.php") ||
			normalized.contains(".php")
	}

	private fun tryAddLegadoVariablePreferences() {
		val (repo, config) = getLegadoRepoAndConfigOrNull() ?: return
		val sourceKey = config.bookSourceUrl.trim().ifBlank { return }

		val screen = preferenceScreen ?: return
		val sourcePrefs = requireContext().getSharedPreferences(LEGADO_SOURCE_PREFS, Context.MODE_PRIVATE)
		val bookPrefs = requireContext().getSharedPreferences(LEGADO_BOOK_PREFS, Context.MODE_PRIVATE)

		val category = findPreference<PreferenceCategory>(KEY_LEGADO_VARIABLES_CATEGORY)
			?: PreferenceCategory(requireContext()).apply {
				key = KEY_LEGADO_VARIABLES_CATEGORY
				title = "Legado 变量"
				order = 40
				isIconSpaceReserved = false
				screen.addPreference(this)
			}

		val sourceVariablePref = findPreference<EditTextPreference>(KEY_LEGADO_SOURCE_VARIABLE)
			?: EditTextPreference(requireContext()).apply {
				key = KEY_LEGADO_SOURCE_VARIABLE
				title = "源变量"
				dialogTitle = "源变量"
				dialogMessage = "用于控制书籍列表加载数量（脚本通常读取末尾数字）。留空表示不设置。"
				order = 41
				isIconSpaceReserved = false
				isPersistent = false
				summaryProvider = EditTextDefaultSummaryProvider("未设置")
				setOnBindEditTextListener(
					EditTextBindListener(
						inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED,
						hint = "",
						validator = null,
					),
				)
				category.addPreference(this)
			}
		sourceVariablePref.text = sourcePrefs.getString(sourceVariableKey(sourceKey), "").orEmpty()
		sourceVariablePref.setOnPreferenceChangeListener { _, newValue ->
			val value = (newValue as? String)?.trim().orEmpty()
			if (!isSignedIntOrBlank(value)) {
				Toast.makeText(requireContext(), "请输入整数（可为空）", Toast.LENGTH_SHORT).show()
				return@setOnPreferenceChangeListener false
			}
			sourcePrefs.edit().apply {
				if (value.isBlank()) remove(sourceVariableKey(sourceKey)) else putString(sourceVariableKey(sourceKey), value)
			}.apply()
			repo.invalidateCache()
			true
		}

		val defaultBookVarPref = findPreference<EditTextPreference>(KEY_LEGADO_BOOK_DEFAULT_CUSTOM)
			?: EditTextPreference(requireContext()).apply {
				key = KEY_LEGADO_BOOK_DEFAULT_CUSTOM
				title = "书籍变量（custom）默认值"
				dialogTitle = "书籍变量（custom）默认值"
				dialogMessage = "用于限制章节加载上限（聚合源常用）。-1 表示不限制；留空表示不设置。"
				order = 42
				isIconSpaceReserved = false
				isPersistent = false
				summaryProvider = EditTextDefaultSummaryProvider("未设置")
				setOnBindEditTextListener(
					EditTextBindListener(
						inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED,
						hint = "-1",
						validator = null,
					),
				)
				category.addPreference(this)
			}
		defaultBookVarPref.text = bookPrefs.getString(bookDefaultKey(sourceKey, "custom"), "").orEmpty()
		defaultBookVarPref.setOnPreferenceChangeListener { _, newValue ->
			val value = (newValue as? String)?.trim().orEmpty()
			if (!isSignedIntOrBlank(value)) {
				Toast.makeText(requireContext(), "请输入整数（可为空）", Toast.LENGTH_SHORT).show()
				return@setOnPreferenceChangeListener false
			}
			bookPrefs.edit().apply {
				if (value.isBlank()) remove(bookDefaultKey(sourceKey, "custom")) else putString(bookDefaultKey(sourceKey, "custom"), value)
			}.apply()
			repo.invalidateCache()
			true
		}
	}

	private fun tryAddLegadoAuthPreferences() {
		val (repo, config) = getLegadoRepoAndConfigOrNull() ?: return
		val sourceKey = config.bookSourceUrl.trim().ifBlank { return }

		val rawLoginUi = config.loginUi?.trim().orEmpty()
		val hasAnyLogin = rawLoginUi.isNotBlank() || config.loginUrl?.isNotBlank() == true || config.loginCheckJs?.isNotBlank() == true
		if (!hasAnyLogin) return

		val screen = preferenceScreen ?: return
		val sourcePrefs = requireContext().getSharedPreferences(LEGADO_SOURCE_PREFS, Context.MODE_PRIVATE)

		val category = findPreference<PreferenceCategory>(KEY_LEGADO_AUTH_CATEGORY)
			?: PreferenceCategory(requireContext()).apply {
				key = KEY_LEGADO_AUTH_CATEGORY
				title = "登录（Legado）"
				order = 35
				isIconSpaceReserved = false
				screen.addPreference(this)
			}

		val items = parseLegadoLoginUiItems(repo, rawLoginUi)
		items.forEachIndexed { idx, item ->
			when (item.type.lowercase()) {
				"text", "password" -> {
					val prefKey = "$KEY_LEGADO_LOGIN_FIELD_PREFIX$idx"
					val storeKey = sourceKvKey(sourceKey, item.name)
					val pref = findPreference<EditTextPreference>(prefKey) ?: EditTextPreference(requireContext()).apply {
						key = prefKey
						title = item.name
						order = 36 + idx
						isIconSpaceReserved = false
						isPersistent = false
						summaryProvider = if (item.type.equals("password", ignoreCase = true)) {
							PasswordSummaryProvider()
						} else {
							EditTextDefaultSummaryProvider("未设置")
						}
						setOnBindEditTextListener(
							EditTextBindListener(
								inputType = if (item.type.equals("password", ignoreCase = true)) {
									android.view.inputmethod.EditorInfo.TYPE_CLASS_TEXT or android.view.inputmethod.EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
								} else {
									android.view.inputmethod.EditorInfo.TYPE_CLASS_TEXT
								},
								hint = null,
								validator = null,
							),
						)
						category.addPreference(this)
					}
					pref.text = sourcePrefs.getString(storeKey, "").orEmpty()
					pref.setOnPreferenceChangeListener { _, newValue ->
						val value = (newValue as? String).orEmpty()
						sourcePrefs.edit().putString(storeKey, value).apply()
						repo.invalidateCache()
						true
					}
				}
				"button" -> {
					val action = item.action?.trim().orEmpty()
					if (action.isBlank()) return@forEachIndexed
					val prefKey = "$KEY_LEGADO_LOGIN_BUTTON_PREFIX$idx"
					if (findPreference<Preference>(prefKey) == null) {
						Preference(requireContext()).apply {
							key = prefKey
							title = item.name
							order = 36 + idx
							isIconSpaceReserved = false
							setOnPreferenceClickListener {
								viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
									val output = runCatching { repo.runUserScript(action) }.getOrNull()
									withContext(Dispatchers.Main) {
										val msg = output?.toString()?.take(200).orEmpty().ifBlank { "执行完成" }
										Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
									}
								}
								true
							}
							category.addPreference(this)
						}
					}
				}
			}
		}

		val checkJs = config.loginCheckJs?.trim().orEmpty()
		if (checkJs.isNotBlank() && findPreference<Preference>(KEY_LEGADO_LOGIN_CHECK) == null) {
			Preference(requireContext()).apply {
				key = KEY_LEGADO_LOGIN_CHECK
				title = "检测登录状态"
				order = 80
				isIconSpaceReserved = false
				setOnPreferenceClickListener {
					viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
						val output = runCatching { repo.evalUserExpression(checkJs) }.getOrNull()
						withContext(Dispatchers.Main) {
							Toast.makeText(requireContext(), output?.toString()?.take(200) ?: "执行失败", Toast.LENGTH_SHORT).show()
						}
					}
					true
				}
				category.addPreference(this)
			}
		}

		if (findPreference<Preference>(KEY_LEGADO_LOGIN_CLEAR) == null) {
			Preference(requireContext()).apply {
				key = KEY_LEGADO_LOGIN_CLEAR
				title = "清理登录信息"
				summary = "清空该源的 sourceVariable/loginInfo/登录表单缓存"
				order = 81
				isIconSpaceReserved = false
				setOnPreferenceClickListener {
					sourcePrefs.edit().apply {
						remove(sourceVariableKey(sourceKey))
						remove(loginInfoKey(sourceKey))
						val prefix = sourceKvPrefix(sourceKey)
						sourcePrefs.all.keys.filter { it.startsWith(prefix) }.forEach { remove(it) }
					}.apply()
					repo.invalidateCache()
					Toast.makeText(requireContext(), "已清理", Toast.LENGTH_SHORT).show()
					true
				}
				category.addPreference(this)
			}
		}
	}

	private data class LegadoLoginUiItem(
		val name: String,
		val type: String,
		val action: String?,
	)

	private fun parseLegadoLoginUiItems(
		repo: org.skepsun.kototoro.core.parser.legado.LegadoRepository,
		rawLoginUi: String,
	): List<LegadoLoginUiItem> {
		val resolved = resolveLegadoMaybeJs(repo, rawLoginUi).trim()
		if (resolved.isBlank()) return emptyList()

		val asJson = runCatching { legadoJson.parseToJsonElement(resolved) }.getOrNull()
			?: runCatching {
				// fallback：loginUi 可能是 JS object literal（不带双引号 key），用 Rhino 解析后再 JSON.stringify。
				val js = "var __ui = $resolved; JSON.stringify(__ui);"
				val str = repo.runUserScript(js)?.toString().orEmpty()
				legadoJson.parseToJsonElement(str)
			}.getOrNull()
			?: return emptyList()

		val array = asJson as? JsonArray ?: return emptyList()
		return array.mapNotNull { element ->
			val obj = element as? JsonObject ?: return@mapNotNull null
			val name = obj["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
			val type = obj["type"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
			val action = obj["action"]?.jsonPrimitive?.contentOrNull
			if (name.isBlank() || type.isBlank()) return@mapNotNull null
			LegadoLoginUiItem(name = name, type = type, action = action)
		}
	}

	private fun resolveLegadoMaybeJs(
		repo: org.skepsun.kototoro.core.parser.legado.LegadoRepository,
		text: String,
	): String {
		val trimmed = text.trim()
		if (trimmed.startsWith("@js:", ignoreCase = true)) {
			return repo.runUserScript(trimmed.removePrefix("@js:"))?.toString().orEmpty()
		}
		if (trimmed.startsWith("<js>", ignoreCase = true) && trimmed.contains("</js>", ignoreCase = true)) {
			val script = trimmed.substringAfter("<js>", "").substringBeforeLast("</js>", "")
			return repo.runUserScript(script)?.toString().orEmpty()
		}
		return trimmed
	}

	private fun getLegadoRepoAndConfigOrNull(): Pair<org.skepsun.kototoro.core.parser.legado.LegadoRepository, LegadoBookSource>? {
		val repo = viewModel.repository as? org.skepsun.kototoro.core.parser.legado.LegadoRepository ?: return null
		val jsonSource = repo.source as? JsonContentSource ?: return null
		val config = runCatching { legadoJson.decodeFromString<LegadoBookSource>(jsonSource.entity.config) }.getOrNull() ?: return null
		return repo to config
	}

	private fun getTvBoxRepoAndConfigOrNull(): Pair<TVBoxRepository, TVBoxStoredConfig>? {
		val repo = viewModel.repository as? TVBoxRepository ?: return null
		val jsonSource = repo.source as? JsonContentSource ?: return null
		val config = runCatching { TVBoxStoredConfig.parse(jsonSource.entity.config) }.getOrNull() ?: return null
		return repo to config
	}

	private fun getTvBoxTypeSummary(config: TVBoxStoredConfig): String {
		val typeLabel = when (config.site.type) {
			0 -> "XML"
			1 -> "JSON"
			3 -> "Spider"
			4 -> "JS"
			else -> getString(R.string.not_specified)
		}
		return "$typeLabel (${config.site.type})"
	}

	private fun sourceVariableKey(sourceKey: String): String = "sourceVariable_$sourceKey"

	private fun loginInfoKey(sourceKey: String): String = "userInfo_$sourceKey"

	private fun sourceKvPrefix(sourceKey: String): String = "v_${sourceKey}_"

	private fun sourceKvKey(sourceKey: String, key: String): String = "${sourceKvPrefix(sourceKey)}$key"

	private fun bookDefaultKey(sourceKey: String, key: String): String {
		val sourceHash = sourceKey.hashCode().toString(16)
		return "bookVar_default_${sourceHash}_$key"
	}

	private fun isSignedIntOrBlank(text: String): Boolean {
		if (text.isBlank()) return true
		return SIGNED_INT_PATTERN.matcher(text).matches()
	}

	private fun addJsLoginPreferences(meta: JsContentRepository.JsAccountMeta) {
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

	private fun focusPreference(key: String) {
		val preference = findPreference<Preference>(key)
		if (preference == null) {
			scrollToPreference(key)
			return
		}
		scrollToPreference(preference)
		val preferenceIndex = preferenceScreen.indexOf(key)
		val itemView = if (preferenceIndex >= 0) {
			listView.findViewHolderForAdapterPosition(preferenceIndex)?.itemView ?: return
		} else {
			return
		}
		itemView.context.getThemeDrawable(materialR.attr.colorTertiaryContainer)?.let {
			itemView.background = it
		}
	}

	private fun PreferenceScreen.indexOf(key: String): Int {
		for (index in 0 until preferenceCount) {
			if (get(index).key == key) {
				return index
			}
		}
		return -1
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

	private fun tryAddMihonPreferences() {
		val repo = viewModel.repository as? MihonMangaRepository ?: return
		val mihonSource = repo.mihonSource as? ConfigurableSource ?: return
		val screen = preferenceScreen ?: return
		try {
			mihonSource.setupPreferenceScreen(screen)
		} catch (e: Throwable) {
			android.util.Log.e("SourceSettingsFragment", "Failed to setup Mihon preferences", e)
		}
	}

	private fun tryAddAniyomiPreferences() {
		val repo = viewModel.repository as? org.skepsun.kototoro.aniyomi.AniyomiAnimeRepository ?: return
		val aniyomiSource = repo.aniyomiSource as? eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource ?: return
		val screen = preferenceScreen ?: return
		try {
			aniyomiSource.setupPreferenceScreen(screen)
		} catch (e: Throwable) {
			android.util.Log.e("SourceSettingsFragment", "Failed to setup Aniyomi preferences", e)
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
		private const val KEY_TVBOX_CATEGORY = "tvbox_info"
		private const val KEY_TVBOX_SITE_KEY = "tvbox_site_key"
		private const val KEY_TVBOX_SITE_API = "tvbox_site_api"
		private const val KEY_TVBOX_SITE_TYPE = "tvbox_site_type"
		private const val KEY_TVBOX_ROOT_SPIDER = "tvbox_root_spider"
		private const val KEY_TVBOX_SITE_JAR = "tvbox_site_jar"
		private const val KEY_TVBOX_SOURCE_LOCATOR = "tvbox_source_locator"
		private const val KEY_TVBOX_RUNTIME_STRATEGY = "tvbox_runtime_strategy"
		private const val KEY_TVBOX_RUNTIME_CANDIDATES = "tvbox_runtime_candidates"
		private const val KEY_TVBOX_STATUS = "tvbox_support_status"
		private const val KEY_LEGADO_VARIABLES_CATEGORY = "legado_variables"
		private const val KEY_LEGADO_SOURCE_VARIABLE = "legado_source_variable"
		private const val KEY_LEGADO_BOOK_DEFAULT_CUSTOM = "legado_book_default_custom"
		private const val KEY_LEGADO_AUTH_CATEGORY = "legado_auth"
		private const val KEY_LEGADO_LOGIN_FIELD_PREFIX = "legado_login_field_"
		private const val KEY_LEGADO_LOGIN_BUTTON_PREFIX = "legado_login_btn_"
		private const val KEY_LEGADO_LOGIN_CHECK = "legado_login_check"
		private const val KEY_LEGADO_LOGIN_CLEAR = "legado_login_clear"

		private const val LEGADO_SOURCE_PREFS = "legado_source_store"
		private const val LEGADO_BOOK_PREFS = "legado_book_store"
		private val SIGNED_INT_PATTERN = Pattern.compile("^-?\\d+$")

		fun newInstance(source: org.skepsun.kototoro.parsers.model.ContentSource) = SourceSettingsFragment().withArgs(1) {
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
		val repo = viewModel.repository as? JsContentRepository ?: return
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
