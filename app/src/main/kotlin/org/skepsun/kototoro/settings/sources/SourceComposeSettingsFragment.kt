package org.skepsun.kototoro.settings.sources

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Headers
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.exceptions.resolve.ExceptionResolver
import org.skepsun.kototoro.core.exceptions.resolve.SnackbarErrorObserver
import org.skepsun.kototoro.core.model.getContentType
import org.skepsun.kototoro.core.model.getDomainTitleResId
import org.skepsun.kototoro.core.model.getEnableSourceTitleResId
import org.skepsun.kototoro.core.model.getRecommendationTermResId
import org.skepsun.kototoro.core.model.getTitle
import org.skepsun.kototoro.core.model.getUnsupportedSourceTitleResId
import org.skepsun.kototoro.core.parser.EmptyContentRepository
import org.skepsun.kototoro.core.parser.ParserContentRepository
import org.skepsun.kototoro.core.parser.kotatsu.KotatsuParserRepository
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.SourceSettings
import org.skepsun.kototoro.core.ui.BaseActivityEntryPoint
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import org.skepsun.kototoro.core.ui.util.ReversibleActionObserver
import org.skepsun.kototoro.core.util.ext.observeChanges
import org.skepsun.kototoro.core.util.ext.observeEvent
import org.skepsun.kototoro.core.util.ext.withArgs
import org.skepsun.kototoro.parsers.ContentParserCredentialsAuthProvider
import org.skepsun.kototoro.parsers.config.ConfigKey
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.settings.SettingsActivity
import org.skepsun.kototoro.settings.compose.SettingsChoiceOption
import org.skepsun.kototoro.settings.compose.SourceSettingsActionRowUiState
import org.skepsun.kototoro.settings.compose.SourceSettingsChoiceRowUiState
import org.skepsun.kototoro.settings.compose.SourceSettingsInfoRowUiState
import org.skepsun.kototoro.settings.compose.SourceSettingsRowUiState
import org.skepsun.kototoro.settings.compose.SourceSettingsScreen
import org.skepsun.kototoro.settings.compose.SourceSettingsSectionUiState
import org.skepsun.kototoro.settings.compose.SourceSettingsSwitchRowUiState
import org.skepsun.kototoro.settings.compose.SourceSettingsTextRowUiState
import org.skepsun.kototoro.settings.utils.validation.DomainValidator
import org.skepsun.kototoro.core.network.CommonHeaders
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.nav.router
import javax.inject.Inject

@AndroidEntryPoint
class SourceComposeSettingsFragment : Fragment() {

    @Inject
    lateinit var settings: AppSettings

    private lateinit var exceptionResolver: ExceptionResolver

    private val viewModel: SourceSettingsViewModel by viewModels()
    private val configKeysFlow = MutableStateFlow<List<ConfigKey<*>>>(emptyList())

    private val sourcePrefs: SharedPreferences by lazy {
        requireContext().getSharedPreferences(
            viewModel.source.name.replace(java.io.File.separatorChar, '$'),
            Context.MODE_PRIVATE,
        )
    }

    private val sourceSettings: SourceSettings by lazy {
        SourceSettings(requireContext(), viewModel.source)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        val entryPoint = EntryPointAccessors.fromApplication<BaseActivityEntryPoint>(context)
        exceptionResolver = entryPoint.exceptionResolverFactory.create(this)
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.onError.observeEvent(
            viewLifecycleOwner,
            SnackbarErrorObserver(view, this, exceptionResolver) { viewModel.onResume() },
        )
        viewModel.onActionDone.observeEvent(viewLifecycleOwner, ReversibleActionObserver(view))

        viewLifecycleOwner.lifecycleScope.launch {
            configKeysFlow.value = when (val repository = viewModel.repository) {
                is ParserContentRepository -> repository.getConfigKeys()
                is KotatsuParserRepository -> repository.getConfigKeys()
                else -> emptyList()
            }
        }

        (view as ComposeView).setContent {
            sourcePrefs.observeChanges()
                .map { Any() }
                .collectAsStateWithLifecycle(initialValue = Any())
                .value
            val configKeys = configKeysFlow.asStateFlow().collectAsStateWithLifecycle().value
            val isEnabled = viewModel.isEnabled.collectAsStateWithLifecycle(initialValue = false).value
            val browserUrl = viewModel.browserUrl.collectAsStateWithLifecycle(initialValue = null).value
            val username = viewModel.username.collectAsStateWithLifecycle(initialValue = null).value
            val isAuthorized = viewModel.isAuthorized.collectAsStateWithLifecycle(initialValue = null).value
            val isLoading = viewModel.isLoading.collectAsStateWithLifecycle(initialValue = false).value

            KototoroTheme {
                SourceSettingsScreen(
                    sections = buildSections(
                        configKeys = configKeys,
                        isEnabled = isEnabled,
                        browserUrl = browserUrl,
                        username = username,
                        isAuthorized = isAuthorized,
                        isLoading = isLoading,
                    ),
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? SettingsActivity)?.setSectionTitle(viewModel.source.getTitle(requireContext()))
        viewModel.onResume()
    }

    private fun buildSections(
        configKeys: List<ConfigKey<*>>,
        isEnabled: Boolean,
        browserUrl: String?,
        username: String?,
        isAuthorized: Boolean?,
        isLoading: Boolean,
    ): List<SourceSettingsSectionUiState> {
        val repository = viewModel.repository
        val sections = mutableListOf<SourceSettingsSectionUiState>()
        val contentType = viewModel.source.getContentType()
        val isValidSource = repository !is EmptyContentRepository

        buildGeneralRows(contentType, isValidSource, isEnabled).takeIf { it.isNotEmpty() }?.let { rows ->
            sections += SourceSettingsSectionUiState(
                id = "general",
                title = getString(R.string.reader_translation_section_general),
                rows = rows,
            )
        }

        buildConfigRows(contentType, configKeys).takeIf { it.isNotEmpty() }?.let { rows ->
            sections += SourceSettingsSectionUiState(
                id = "source_config",
                title = getString(R.string.settings),
                rows = rows,
            )
        }

        buildAuthRows(
            repository = repository,
            browserUrl = browserUrl,
            username = username,
            isAuthorized = isAuthorized,
            isLoading = isLoading,
        ).takeIf { it.isNotEmpty() }?.let { rows ->
            sections += SourceSettingsSectionUiState(
                id = "auth",
                title = getString(R.string.auth_title),
                rows = rows,
            )
        }

        if (repository is EmptyContentRepository) {
            sections += SourceSettingsSectionUiState(
                id = "unsupported",
                title = getString(R.string.settings),
                rows = listOf(
                    SourceSettingsInfoRowUiState(
                        id = "unsupported_info",
                        title = viewModel.source.getTitle(requireContext()),
                        summary = getString(contentType.getUnsupportedSourceTitleResId()),
                    ),
                ),
            )
        }

        return sections
    }

    private fun buildGeneralRows(
        contentType: ContentType,
        isValidSource: Boolean,
        isEnabled: Boolean,
    ): List<SourceSettingsRowUiState> {
        val rows = mutableListOf<SourceSettingsRowUiState>()
        if (isValidSource && !settings.isAllSourcesEnabled) {
            rows += SourceSettingsSwitchRowUiState(
                id = "enable",
                title = getString(contentType.getEnableSourceTitleResId()),
                checked = isEnabled,
                onCheckedChange = viewModel::setEnabled,
            )
        }
        if (isValidSource) {
            rows += SourceSettingsSwitchRowUiState(
                id = SourceSettings.KEY_NO_CAPTCHA,
                title = getString(R.string.disable_captcha_notifications),
                checked = sourcePrefs.getBoolean(SourceSettings.KEY_NO_CAPTCHA, false),
                summary = getString(
                    R.string.disable_captcha_notifications_summary,
                    getString(contentType.getRecommendationTermResId()),
                ),
                onCheckedChange = { checked ->
                    sourcePrefs.edit { putBoolean(SourceSettings.KEY_NO_CAPTCHA, checked) }
                },
            )
            rows += SourceSettingsSwitchRowUiState(
                id = SourceSettings.KEY_NO_AUTO_CAPTCHA,
                title = getString(R.string.disable_captcha_auto_solve),
                checked = sourcePrefs.getBoolean(SourceSettings.KEY_NO_AUTO_CAPTCHA, false),
                summary = getString(R.string.disable_captcha_auto_solve_summary),
                onCheckedChange = { checked ->
                    sourcePrefs.edit { putBoolean(SourceSettings.KEY_NO_AUTO_CAPTCHA, checked) }
                },
            )
            rows += SourceSettingsSwitchRowUiState(
                id = SourceSettings.KEY_SLOWDOWN,
                title = getString(R.string.download_slowdown),
                checked = sourcePrefs.getBoolean(SourceSettings.KEY_SLOWDOWN, false),
                summary = getString(R.string.download_slowdown_summary),
                onCheckedChange = { checked ->
                    sourcePrefs.edit { putBoolean(SourceSettings.KEY_SLOWDOWN, checked) }
                },
            )
        }
        return rows
    }

    private fun buildConfigRows(
        contentType: ContentType,
        configKeys: List<ConfigKey<*>>,
    ): List<SourceSettingsRowUiState> {
        return buildList {
            configKeys.forEach { key ->
                when (key) {
                    is ConfigKey.Domain -> add(
                        SourceSettingsTextRowUiState(
                            id = key.key,
                            title = getString(contentType.getDomainTitleResId()),
                            value = sourceSettings[key],
                            placeholder = key.defaultValue,
                            onValueChange = onDomainValueChange@{ value ->
                                val trimmed = value.trim()
                                if (trimmed.isNotEmpty() && !DomainValidator.isValidDomain(trimmed)) {
                                    showToast(R.string.invalid_domain_message)
                                    return@onDomainValueChange
                                }
                                sourceSettings[key] = trimmed
                            },
                        ),
                    )

                    is ConfigKey.Text -> add(
                        SourceSettingsTextRowUiState(
                            id = key.key,
                            title = key.title,
                            value = sourceSettings[key],
                            placeholder = key.defaultValue,
                            onValueChange = { value ->
                                sourceSettings[key] = value
                            },
                        ),
                    )

                    is ConfigKey.UserAgent -> {
                        val currentValue = sourceSettings[key]
                        val presetOptions = buildUserAgentPresetOptions(currentValue)
                        add(
                            SourceSettingsChoiceRowUiState(
                                id = "${key.key}_preset",
                                title = getString(R.string.user_agent),
                                value = currentValue.takeIf { value ->
                                    presetOptions.any { it.value == value }
                                } ?: USER_AGENT_CUSTOM_VALUE,
                                options = presetOptions,
                                summary = getString(R.string.custom),
                                onValueChange = { value ->
                                    if (value != USER_AGENT_CUSTOM_VALUE) {
                                        sourceSettings[key] = value
                                    }
                                },
                            ),
                        )
                        add(
                            SourceSettingsTextRowUiState(
                                id = key.key,
                                title = getString(R.string.custom),
                                value = currentValue,
                                placeholder = key.defaultValue,
                                onValueChange = onUserAgentValueChange@{ value ->
                                    if (value.isNotBlank() && !isValidHeaderValue(value.trim())) {
                                        showToast(R.string.invalid_value_message)
                                        return@onUserAgentValueChange
                                    }
                                    sourceSettings[key] = value
                                },
                            ),
                        )
                    }

                    is ConfigKey.ShowSuspiciousContent -> add(
                        SourceSettingsSwitchRowUiState(
                            id = key.key,
                            title = getString(R.string.show_suspicious_content),
                            checked = sourceSettings[key],
                            onCheckedChange = { checked ->
                                sourceSettings[key] = checked
                            },
                        ),
                    )

                    is ConfigKey.InterceptCloudflare -> Unit

                    is ConfigKey.Toggle -> add(
                        SourceSettingsSwitchRowUiState(
                            id = key.key,
                            title = key.title,
                            checked = sourceSettings[key],
                            onCheckedChange = { checked ->
                                sourceSettings[key] = checked
                            },
                        ),
                    )

                    is ConfigKey.SplitByTranslations -> add(
                        SourceSettingsSwitchRowUiState(
                            id = key.key,
                            title = getString(R.string.split_by_translations),
                            checked = sourceSettings[key],
                            summary = getString(R.string.split_by_translations_summary),
                            onCheckedChange = { checked ->
                                sourceSettings[key] = checked
                            },
                        ),
                    )

                    is ConfigKey.PreferredImageServer -> add(
                        SourceSettingsChoiceRowUiState(
                            id = key.key,
                            title = getString(R.string.image_server),
                            value = sourceSettings[key].orEmpty(),
                            options = key.presetValues.map { entry ->
                                SettingsChoiceOption(
                                    entry.key.orEmpty(),
                                    entry.value ?: getString(R.string.automatic),
                                )
                            },
                            onValueChange = { value ->
                                sourceSettings[key] = value.ifEmpty { null }
                            },
                        ),
                    )

                    is ConfigKey.PreferredLanguage -> add(
                        SourceSettingsChoiceRowUiState(
                            id = key.key,
                            title = key.title,
                            value = sourceSettings[key],
                            options = key.presetValues.map { entry ->
                                SettingsChoiceOption(entry.key, entry.value)
                            },
                            onValueChange = { value ->
                                sourceSettings[key] = value
                            },
                        ),
                    )
                }
            }
        }
    }

    private fun buildUserAgentPresetOptions(currentValue: String): List<SettingsChoiceOption<String>> {
        val options = mutableListOf(
            SettingsChoiceOption(USER_AGENT_CUSTOM_VALUE, getString(R.string.custom)),
        )
        options += userAgentPresets().map { preset ->
            SettingsChoiceOption(
                value = preset.value,
                label = preset.label,
            )
        }
        if (currentValue.isNotBlank() && options.none { it.value == currentValue }) {
            options.add(1, SettingsChoiceOption(currentValue, currentValue))
        }
        return options
    }

    private fun buildAuthRows(
        repository: org.skepsun.kototoro.core.parser.ContentRepository,
        browserUrl: String?,
        username: String?,
        isAuthorized: Boolean?,
        isLoading: Boolean,
    ): List<SourceSettingsRowUiState> {
        val parserRepository = repository as? ParserContentRepository ?: return buildBrowserOnlyRows(browserUrl)
        val rows = mutableListOf<SourceSettingsRowUiState>()
        val authProvider = parserRepository.getAuthProvider()
        val credentialsProvider = authProvider as? ContentParserCredentialsAuthProvider

        if (credentialsProvider != null) {
            rows += SourceSettingsInfoRowUiState(
                id = "auth_status",
                title = getString(R.string.sign_in),
                summary = username?.let { getString(R.string.logged_in_as, it) }
                    ?: getString(R.string.auth_required),
            )
            rows += SourceSettingsTextRowUiState(
                id = "auth_username",
                title = getString(R.string.enter_name),
                value = sourcePrefs.getString(KEY_AUTH_USERNAME, "").orEmpty(),
                enabled = !isLoading,
                onValueChange = { value ->
                    sourcePrefs.edit { putString(KEY_AUTH_USERNAME, value) }
                },
            )
            rows += SourceSettingsTextRowUiState(
                id = "auth_password",
                title = getString(R.string.enter_password),
                value = sourcePrefs.getString(KEY_AUTH_PASSWORD, "").orEmpty(),
                isPassword = true,
                enabled = !isLoading,
                onValueChange = { value ->
                    sourcePrefs.edit { putString(KEY_AUTH_PASSWORD, value) }
                },
            )
            rows += SourceSettingsActionRowUiState(
                id = "auth_login",
                title = getString(R.string.sign_in),
                enabled = !isLoading,
                showChevron = false,
                onClick = {
                    viewModel.loginByCredentials(
                        sourcePrefs.getString(KEY_AUTH_USERNAME, "").orEmpty().trim(),
                        sourcePrefs.getString(KEY_AUTH_PASSWORD, "").orEmpty().trim(),
                    )
                },
            )
        } else if (authProvider != null) {
            rows += SourceSettingsActionRowUiState(
                id = "auth_browser",
                title = getString(R.string.sign_in),
                summary = username?.let { getString(R.string.logged_in_as, it) }
                    ?: getString(R.string.auth_required),
                enabled = !isLoading && isAuthorized != true,
                showChevron = false,
                onClick = {
                    router.openSourceAuth(viewModel.source)
                },
            )
        }

        browserUrl?.let { url ->
            rows += SourceSettingsActionRowUiState(
                id = AppSettings.KEY_OPEN_BROWSER,
                title = getString(R.string.open_in_browser),
                summary = url,
                onClick = {
                    router.openBrowser(
                        url = url,
                        source = viewModel.source,
                        title = viewModel.source.getTitle(requireContext()),
                    )
                },
            )
        }

        rows += SourceSettingsActionRowUiState(
            id = AppSettings.KEY_COOKIES_CLEAR,
            title = getString(R.string.clear_cookies),
            summary = getString(R.string.clear_source_cookies_summary),
            showChevron = false,
            onClick = viewModel::clearCookies,
        )

        return rows
    }

    private fun buildBrowserOnlyRows(browserUrl: String?): List<SourceSettingsRowUiState> {
        if (browserUrl == null) {
            return emptyList()
        }
        return listOf(
            SourceSettingsActionRowUiState(
                id = AppSettings.KEY_OPEN_BROWSER,
                title = getString(R.string.open_in_browser),
                summary = browserUrl,
                onClick = {
                    router.openBrowser(
                        url = browserUrl,
                        source = viewModel.source,
                        title = viewModel.source.getTitle(requireContext()),
                    )
                },
            ),
        )
    }

    private fun isValidHeaderValue(value: String): Boolean {
        return runCatching {
            Headers.Builder()[CommonHeaders.USER_AGENT] = value
        }.isSuccess
    }

    private fun showToast(stringRes: Int) {
        Toast.makeText(requireContext(), stringRes, Toast.LENGTH_SHORT).show()
    }

    companion object {

        private const val KEY_AUTH_USERNAME = "auth_username"
        private const val KEY_AUTH_PASSWORD = "auth_password"
        private const val USER_AGENT_CUSTOM_VALUE = "__custom__"

        fun newInstance(source: org.skepsun.kototoro.parsers.model.ContentSource) =
            SourceComposeSettingsFragment().withArgs(1) {
                putString(AppRouter.KEY_SOURCE, source.name)
            }
    }
}
