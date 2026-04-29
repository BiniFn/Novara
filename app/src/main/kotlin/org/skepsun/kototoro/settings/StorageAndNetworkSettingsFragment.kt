package org.skepsun.kototoro.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import java.net.Proxy
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.network.DoHProvider
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.NetworkPolicy
import org.skepsun.kototoro.core.prefs.observeAsState
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import org.skepsun.kototoro.core.util.ext.getDisplayMessage
import org.skepsun.kototoro.settings.compose.SettingsActionPreference
import org.skepsun.kototoro.settings.compose.SettingsChoiceOption
import org.skepsun.kototoro.settings.compose.SettingsChoicePreference
import org.skepsun.kototoro.settings.compose.SettingsSectionDivider
import org.skepsun.kototoro.settings.compose.SettingsSwitchPreference
import org.skepsun.kototoro.settings.compose.SettingsTextInputPreference
import org.skepsun.kototoro.settings.compose.StorageAndNetworkSettingsScreen

@AndroidEntryPoint
class StorageAndNetworkSettingsFragment : Fragment() {

    @Inject
    lateinit var settings: AppSettings

    private val viewModel by viewModels<StorageAndNetworkSettingsViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (view as ComposeView).setContent {
            KototoroTheme {
                StorageAndNetworkSettingsRoute(
                    settings = settings,
                    viewModel = viewModel,
                    onOpenProxySettings = {
                        (activity as? SettingsActivity)?.openDestination(SettingsDestination.ProxySettings, null, false)
                    },
                    onOpenDataCleanupSettings = {
                        (activity as? SettingsActivity)?.openDestination(
                            SettingsDestination.DataCleanupSettings,
                            null,
                            false,
                        )
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? SettingsActivity)?.setSectionTitle(getString(R.string.storage_and_network))
    }
}

@Composable
fun StorageAndNetworkSettingsRoute(
    settings: AppSettings,
    viewModel: StorageAndNetworkSettingsViewModel,
    onOpenProxySettings: () -> Unit,
    onOpenDataCleanupSettings: () -> Unit,
) {
    val context = LocalContext.current
    val storageUsage = viewModel.storageUsage.collectAsStateWithLifecycle().value
    val prefetchPolicy = settings.observeAsState(AppSettings.KEY_PREFETCH_CONTENT) { contentPrefetchPolicy }.value
    val pagesPreloadPolicy = settings.observeAsState(AppSettings.KEY_PAGES_PRELOAD) { pagesPreloadPolicy }.value
    val dnsOverHttps = settings.observeAsState(AppSettings.KEY_DOH) { dnsOverHttps }.value
    val dohCustomUrl = settings.observeAsState(AppSettings.KEY_DOH_CUSTOM_URL) { dohCustomUrl.orEmpty() }.value
    val dohCustomIps = settings.observeAsState(AppSettings.KEY_DOH_CUSTOM_IPS) { dohCustomIps.orEmpty() }.value
    val imagesProxy = settings.observeAsState(AppSettings.KEY_IMAGES_PROXY) { imagesProxy }.value
    val gitHubMirror = settings.observeAsState(AppSettings.KEY_GITHUB_MIRROR) { gitHubMirror }.value
    val huggingFaceMirror = settings.observeAsState(AppSettings.KEY_HUGGINGFACE_MIRROR) { huggingFaceMirror }.value
    val sslBypass = settings.observeAsState(AppSettings.KEY_SSL_BYPASS) { isSSLBypassEnabled }.value
    val offlineDisabled = settings.observeAsState(AppSettings.KEY_OFFLINE_DISABLED) { isOfflineCheckDisabled }.value
    val adBlock = settings.observeAsState(AppSettings.KEY_ADBLOCK) { isAdBlockEnabled }.value
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val dohLabels = context.resources.getStringArray(R.array.doh_providers)
    val imageProxyLabels = context.resources.getStringArray(R.array.image_proxies)

    LaunchedEffect(viewModel.onError, context, snackbarHostState) {
        viewModel.onError.collect { event ->
            event?.consume { error ->
                snackbarHostState.showSnackbar(error.getDisplayMessage(context.resources))
            }
        }
    }

    val networkOptions = listOf(
        SettingsChoiceOption(NetworkPolicy.ALWAYS, context.getString(R.string.always)),
        SettingsChoiceOption(NetworkPolicy.NON_METERED, context.getString(R.string.only_using_wifi)),
        SettingsChoiceOption(NetworkPolicy.NEVER, context.getString(R.string.never)),
    )
    val dohOptions = listOf(
        SettingsChoiceOption(DoHProvider.NONE, dohLabels[0]),
        SettingsChoiceOption(DoHProvider.CUSTOM, dohLabels[1]),
        SettingsChoiceOption(DoHProvider.GOOGLE, dohLabels[2]),
        SettingsChoiceOption(DoHProvider.CLOUDFLARE, dohLabels[3]),
        SettingsChoiceOption(DoHProvider.ADGUARD, dohLabels[4]),
        SettingsChoiceOption(DoHProvider.ZERO_MS, dohLabels[5]),
    )
    val imageProxyOptions = listOf(
        SettingsChoiceOption(-1, imageProxyLabels[0]),
        SettingsChoiceOption(0, imageProxyLabels[1]),
        SettingsChoiceOption(1, imageProxyLabels[2]),
    )
    val gitHubMirrorOptions = listOf(
        SettingsChoiceOption(AppSettings.GitHubMirror.NATIVE, "Direct Native (Default)"),
        SettingsChoiceOption(AppSettings.GitHubMirror.KKGITHUB, "KKGithub Proxy"),
        SettingsChoiceOption(AppSettings.GitHubMirror.GHPROXY, "Ghproxy.com"),
        SettingsChoiceOption(AppSettings.GitHubMirror.GHPROXY_NET, "Ghproxy.net"),
    )
    val huggingFaceMirrorOptions = listOf(
        SettingsChoiceOption(AppSettings.HuggingFaceMirror.NATIVE, "Direct Native (Default)"),
        SettingsChoiceOption(AppSettings.HuggingFaceMirror.HF_MIRROR, "hf-mirror.com"),
    )

    StorageAndNetworkSettingsScreen(
        storageTitle = context.getString(R.string.storage_usage),
        networkTitle = context.getString(R.string.network),
        storageUsage = storageUsage,
        snackbarHostState = snackbarHostState,
        prefetchContent = {
            SettingsChoicePreference(
                title = context.getString(R.string.prefetch_content),
                value = prefetchPolicy,
                options = networkOptions,
                onValueChange = { settings.contentPrefetchPolicy = it },
            )
        },
        preloadPages = {
            SettingsChoicePreference(
                title = context.getString(R.string.preload_pages),
                value = pagesPreloadPolicy,
                options = networkOptions,
                onValueChange = { settings.pagesPreloadPolicy = it },
            )
        },
        proxy = {
            SettingsActionPreference(
                title = context.getString(R.string.proxy),
                summary = buildProxySummary(settings, context),
                onClick = onOpenProxySettings,
            )
        },
        dns = {
            SettingsChoicePreference(
                title = context.getString(R.string.dns_over_https),
                value = dnsOverHttps,
                options = dohOptions,
                onValueChange = { settings.dnsOverHttps = it },
            )
        },
        customDohUrl = {
            if (dnsOverHttps == DoHProvider.CUSTOM) {
                SettingsSectionDivider()
                SettingsTextInputPreference(
                    title = context.getString(R.string.pref_doh_custom_url),
                    value = dohCustomUrl,
                    onValueChange = { settings.dohCustomUrl = it },
                )
            }
        },
        customDohIps = {
            if (dnsOverHttps == DoHProvider.CUSTOM) {
                SettingsSectionDivider()
                SettingsTextInputPreference(
                    title = context.getString(R.string.pref_doh_custom_ips),
                    value = dohCustomIps,
                    onValueChange = { settings.dohCustomIps = it },
                )
            }
        },
        imageProxy = {
            SettingsChoicePreference(
                title = context.getString(R.string.images_proxy_title),
                value = imagesProxy,
                options = imageProxyOptions,
                onValueChange = { settings.imagesProxy = it },
            )
        },
        githubMirror = {
            SettingsChoicePreference(
                title = context.getString(R.string.pref_github_mirror),
                value = gitHubMirror,
                options = gitHubMirrorOptions,
                summary = context.getString(R.string.pref_github_mirror_summary),
                onValueChange = { settings.gitHubMirror = it },
            )
        },
        huggingFaceMirror = {
            SettingsChoicePreference(
                title = context.getString(R.string.pref_huggingface_mirror),
                value = huggingFaceMirror,
                options = huggingFaceMirrorOptions,
                summary = context.getString(R.string.pref_huggingface_mirror_summary),
                onValueChange = { settings.huggingFaceMirror = it },
            )
        },
        sslBypass = {
            SettingsSwitchPreference(
                title = context.getString(R.string.ignore_ssl_errors),
                checked = sslBypass,
                summary = context.getString(R.string.ignore_ssl_errors_summary),
                onCheckedChange = {
                    settings.isSSLBypassEnabled = it
                    if (it) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                message = context.getString(R.string.settings_apply_restart_required),
                                duration = SnackbarDuration.Long,
                            )
                        }
                    }
                },
            )
        },
        offlineCheck = {
            SettingsSwitchPreference(
                title = context.getString(R.string.disable_connectivity_check),
                checked = offlineDisabled,
                summary = context.getString(R.string.disable_connectivity_check_summary),
                onCheckedChange = { settings.isOfflineCheckDisabled = it },
            )
        },
        adBlock = {
            SettingsSwitchPreference(
                title = context.getString(R.string.adblock),
                checked = adBlock,
                summary = context.getString(R.string.adblock_summary),
                onCheckedChange = { settings.isAdBlockEnabled = it },
            )
        },
        dataRemoval = {
            SettingsActionPreference(
                title = context.getString(R.string.data_removal),
                summary = context.getString(R.string.clear_database_summary),
                onClick = onOpenDataCleanupSettings,
            )
        },
    )
}

private fun buildProxySummary(
    settings: AppSettings,
    context: android.content.Context,
): String {
    val type = settings.proxyType
    val address = settings.proxyAddress
    val port = settings.proxyPort
    return when {
        type == Proxy.Type.DIRECT -> context.getString(R.string.disabled)
        address.isNullOrEmpty() || port == 0 -> context.getString(R.string.invalid_proxy_configuration)
        else -> "$address:$port"
    }
}
