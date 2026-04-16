package org.skepsun.kototoro.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.os.OpenDocumentTreeHelper
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.DownloadFormat
import org.skepsun.kototoro.core.prefs.TriStateOption
import org.skepsun.kototoro.core.prefs.observeAsState
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import org.skepsun.kototoro.core.util.ext.getQuantityStringSafe
import org.skepsun.kototoro.core.util.ext.powerManager
import org.skepsun.kototoro.core.util.ext.printStackTraceDebug
import org.skepsun.kototoro.core.util.ext.resolveFile
import org.skepsun.kototoro.core.util.ext.tryLaunch
import org.skepsun.kototoro.download.ui.worker.DownloadWorker
import org.skepsun.kototoro.local.data.LocalStorageManager
import org.skepsun.kototoro.settings.compose.DownloadsSettingsScreen
import org.skepsun.kototoro.settings.compose.DownloadsSettingsUiState
import org.skepsun.kototoro.settings.compose.SettingsChoiceOption
import org.skepsun.kototoro.settings.storage.ContentDirectorySelectDialog

@AndroidEntryPoint
class DownloadsSettingsFragment : Fragment() {

    @Inject
    lateinit var settings: AppSettings

    @Inject
    lateinit var storageManager: LocalStorageManager

    @Inject
    lateinit var downloadsScheduler: DownloadWorker.Scheduler

    private val storageTick = MutableStateFlow(0)
    private val dozeTick = MutableStateFlow(0)

    private val pickFileTreeLauncher = OpenDocumentTreeHelper(this) {
        if (it != null) {
            onDirectoryPicked(it)
        }
    }

    private val ignoreDozeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        dozeTick.update { tick -> tick + 1 }
    }

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
            val context = requireContext()
            val storageRefreshKey = storageTick.collectAsStateWithLifecycle().value
            val dozeRefreshKey = dozeTick.collectAsStateWithLifecycle().value
            val preferredDownloadFormat =
                settings.observeAsState(AppSettings.KEY_DOWNLOADS_FORMAT) { preferredDownloadFormat }.value
            val isDownloadAlignedWithReader =
                settings.observeAsState(AppSettings.KEY_DOWNLOADS_ALIGN_READER) { isDownloadAlignedWithReader }.value
            val isDownloadAutoRetryOnNetworkError =
                settings.observeAsState(AppSettings.KEY_DOWNLOADS_AUTO_RETRY) { isDownloadAutoRetryOnNetworkError }.value
            val downloadThreads = settings.observeAsState(AppSettings.KEY_DOWNLOADS_THREADS) { downloadThreads }.value
            val downloadRequestDelayMs =
                settings.observeAsState(AppSettings.KEY_DOWNLOADS_REQUEST_DELAY) { downloadRequestDelayMs }.value
            val downloadRetryCount =
                settings.observeAsState(AppSettings.KEY_DOWNLOADS_RETRY_COUNT) { downloadRetryCount }.value
            val downloadRetryDelayMs =
                settings.observeAsState(AppSettings.KEY_DOWNLOADS_RETRY_DELAY) { downloadRetryDelayMs }.value
            val allowDownloadOnMeteredNetwork =
                settings.observeAsState(AppSettings.KEY_DOWNLOADS_METERED_NETWORK) { allowDownloadOnMeteredNetwork }.value
            val pagesSaveDirKey =
                settings.observeAsState(AppSettings.KEY_PAGES_SAVE_DIR) { getPagesSaveDir(context)?.uri?.toString() }.value
            val isPagesSavingAskEnabled =
                settings.observeAsState(AppSettings.KEY_PAGES_SAVE_ASK) { isPagesSavingAskEnabled }.value
            val mangaDirectoriesSummary = rememberMangaDirectoriesSummary(storageRefreshKey)
            val mangaStorageSummary = rememberStorageSummary(storageRefreshKey, ::loadMangaStorageSummary)
            val novelStorageSummary = rememberStorageSummary(storageRefreshKey, ::loadNovelStorageSummary)
            val videoStorageSummary = rememberStorageSummary(storageRefreshKey, ::loadVideoStorageSummary)
            val pagesDirectorySummary = rememberPagesDirectorySummary(storageRefreshKey, pagesSaveDirKey)
            val snackbarHostState = remember { SnackbarHostState() }
            val coroutineScope = rememberCoroutineScope()

            val downloadFormatOptions = listOf(
                SettingsChoiceOption(DownloadFormat.AUTOMATIC, getString(R.string.automatic)),
                SettingsChoiceOption(DownloadFormat.SINGLE_CBZ, getString(R.string.single_cbz_file)),
                SettingsChoiceOption(DownloadFormat.MULTIPLE_CBZ, getString(R.string.multiple_cbz_files)),
            )
            val meteredNetworkOptions = listOf(
                SettingsChoiceOption(TriStateOption.ENABLED, getString(R.string.allow_always)),
                SettingsChoiceOption(TriStateOption.ASK, getString(R.string.ask_every_time)),
                SettingsChoiceOption(TriStateOption.DISABLED, getString(R.string.dont_allow)),
            )

            val state = DownloadsSettingsUiState(
                mangaDirectoriesSummary = mangaDirectoriesSummary,
                mangaStorageSummary = mangaStorageSummary,
                novelStorageSummary = novelStorageSummary,
                videoStorageSummary = videoStorageSummary,
                preferredDownloadFormat = preferredDownloadFormat,
                isDownloadAlignedWithReader = isDownloadAlignedWithReader,
                isDownloadAutoRetryOnNetworkError = isDownloadAutoRetryOnNetworkError,
                downloadThreads = downloadThreads,
                downloadRequestDelayMs = downloadRequestDelayMs,
                downloadRetryCount = downloadRetryCount,
                downloadRetryDelayMs = downloadRetryDelayMs,
                allowDownloadOnMeteredNetwork = allowDownloadOnMeteredNetwork,
                isDozeIgnoreVisible = isDozeIgnoreAvailable(dozeRefreshKey),
                pagesDirectorySummary = pagesDirectorySummary,
                isPagesSavingAskEnabled = isPagesSavingAskEnabled,
            )

            KototoroTheme {
                DownloadsSettingsScreen(
                    downloadsTitle = getString(R.string.downloads),
                    pagesSavingTitle = getString(R.string.pages_saving),
                    state = state,
                    snackbarHostState = snackbarHostState,
                    downloadFormatOptions = downloadFormatOptions,
                    meteredNetworkOptions = meteredNetworkOptions,
                    onMangaDirectoriesClick = { router.openDirectoriesSettings() },
                    onMangaStorageClick = { router.showDirectorySelectDialog() },
                    onNovelStorageClick = {
                        router.showDirectorySelectDialog(ContentDirectorySelectDialog.CONTENT_TYPE_NOVEL)
                    },
                    onVideoStorageClick = {
                        router.showDirectorySelectDialog(ContentDirectorySelectDialog.CONTENT_TYPE_VIDEO)
                    },
                    onPreferredDownloadFormatChange = { settings.preferredDownloadFormat = it },
                    onDownloadAlignReaderChange = { settings.isDownloadAlignedWithReader = it },
                    onDownloadAutoRetryChange = { settings.isDownloadAutoRetryOnNetworkError = it },
                    onDownloadThreadsChange = { settings.downloadThreads = it },
                    onDownloadRequestDelayChange = { settings.downloadRequestDelayMs = it },
                    onDownloadRetryCountChange = { settings.downloadRetryCount = it },
                    onDownloadRetryDelayChange = { settings.downloadRetryDelayMs = it },
                    onAllowMeteredNetworkChange = { option ->
                        settings.allowDownloadOnMeteredNetwork = option
                        updateDownloadsConstraints()
                    },
                    onIgnoreDozeClick = {
                        if (!startIgnoreDozeActivity()) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(getString(R.string.operation_not_supported))
                            }
                        }
                    },
                    onPagesDirectoryClick = {
                        if (!pickFileTreeLauncher.tryLaunch(settings.getPagesSaveDir(context)?.uri)) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(getString(R.string.operation_not_supported))
                            }
                        }
                    },
                    onPagesSavingAskChange = { settings.isPagesSavingAskEnabled = it },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? SettingsActivity)?.setSectionTitle(getString(R.string.downloads))
        storageTick.update { it + 1 }
        dozeTick.update { it + 1 }
    }

    @Composable
    private fun rememberMangaDirectoriesSummary(
        refreshKey: Int,
    ): String {
        return produceState(
            initialValue = getString(R.string.loading_),
            key1 = refreshKey,
        ) {
            val dirs = storageManager.getReadableDirs().size
            value = resources.getQuantityStringSafe(R.plurals.items, dirs, dirs)
        }.value
    }

    @Composable
    private fun rememberStorageSummary(
        refreshKey: Int,
        loader: suspend () -> String,
    ): String {
        return produceState(
            initialValue = getString(R.string.loading_),
            key1 = refreshKey,
            producer = {
                value = loader()
            },
        ).value
    }

    @Composable
    private fun rememberPagesDirectorySummary(
        refreshKey: Int,
        pagesSaveDirKey: String?,
    ): String {
        return produceState(
            initialValue = getString(androidx.preference.R.string.not_set),
            key1 = refreshKey,
            key2 = pagesSaveDirKey,
        ) {
            value = withContext(Dispatchers.IO) {
                settings.getPagesSaveDir(requireContext())
            }?.getDisplayPath(requireContext()) ?: getString(androidx.preference.R.string.not_set)
        }.value
    }

    private suspend fun loadMangaStorageSummary(): String {
        val storage = storageManager.getDefaultWriteableDir()
        return if (storage != null) {
            storageManager.getDirectoryDisplayName(storage, isFullPath = true)
        } else {
            getString(R.string.not_available)
        }
    }

    private suspend fun loadNovelStorageSummary(): String {
        val storage = storageManager.getDefaultNovelWriteableDir()
        return if (storage != null) {
            storageManager.getDirectoryDisplayName(storage, isFullPath = true)
        } else {
            getString(R.string.not_available)
        }
    }

    private suspend fun loadVideoStorageSummary(): String {
        val storage = storageManager.getDefaultVideoWriteableDir()
        return if (storage != null) {
            storageManager.getDirectoryDisplayName(storage, isFullPath = true)
        } else {
            getString(R.string.not_available)
        }
    }

    private fun onDirectoryPicked(uri: Uri) {
        storageManager.takePermissions(uri)
        val doc = DocumentFile.fromTreeUri(requireContext(), uri)?.takeIf { it.canWrite() }
        settings.setPagesSaveDir(doc?.uri)
        storageTick.update { it + 1 }
    }

    private fun updateDownloadsConstraints() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.Default) {
                    val option = when (settings.allowDownloadOnMeteredNetwork) {
                        TriStateOption.ENABLED -> true
                        TriStateOption.ASK -> return@withContext
                        TriStateOption.DISABLED -> false
                    }
                    downloadsScheduler.updateConstraints(option)
                }
            } catch (e: Exception) {
                e.printStackTraceDebug()
            }
        }
    }

    private fun startIgnoreDozeActivity(): Boolean {
        val context = context ?: return false
        val packageName = context.packageName
        val powerManager = context.powerManager ?: return false
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
            return false
        }
        return try {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                "package:$packageName".toUri(),
            )
            ignoreDozeLauncher.launch(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private fun isDozeIgnoreAvailable(refreshKey: Int): Boolean {
        refreshKey
        val context = context ?: return false
        val powerManager = context.powerManager ?: return false
        return !powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun DocumentFile.getDisplayPath(context: Context): String {
        return uri.resolveFile(context)?.path ?: uri.toString()
    }
}
