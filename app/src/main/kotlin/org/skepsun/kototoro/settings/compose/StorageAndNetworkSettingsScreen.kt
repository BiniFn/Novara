package org.skepsun.kototoro.settings.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.util.FileSize
import org.skepsun.kototoro.settings.userdata.storage.StorageUsage

private data class StorageUsageUiItem(
    val label: String,
    val bytes: Long,
    val progress: Float,
)

@Composable
fun StorageAndNetworkSettingsScreen(
    storageTitle: String,
    networkTitle: String,
    storageUsage: StorageUsage?,
    prefetchContent: @Composable () -> Unit,
    preloadPages: @Composable () -> Unit,
    proxy: @Composable () -> Unit,
    dns: @Composable () -> Unit,
    customDohUrl: @Composable () -> Unit,
    customDohIps: @Composable () -> Unit,
    imageProxy: @Composable () -> Unit,
    githubMirror: @Composable () -> Unit,
    huggingFaceMirror: @Composable () -> Unit,
    sslBypass: @Composable () -> Unit,
    offlineCheck: @Composable () -> Unit,
    adBlock: @Composable () -> Unit,
    dataRemoval: @Composable () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 20.dp,
                bottom = innerPadding.calculateBottomPadding() + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "storage") {
                SettingsPreferenceSection(title = storageTitle) {
                    StorageUsageBlock(storageUsage = storageUsage)
                    SettingsSectionDivider()
                    dataRemoval()
                }
            }
            item(key = "network") {
                SettingsPreferenceSection(title = networkTitle) {
                    prefetchContent()
                    SettingsSectionDivider()
                    preloadPages()
                    SettingsSectionDivider()
                    proxy()
                    SettingsSectionDivider()
                    dns()
                    customDohUrl()
                    customDohIps()
                    SettingsSectionDivider()
                    imageProxy()
                    SettingsSectionDivider()
                    githubMirror()
                    SettingsSectionDivider()
                    huggingFaceMirror()
                    SettingsSectionDivider()
                    sslBypass()
                    SettingsSectionDivider()
                    offlineCheck()
                    SettingsSectionDivider()
                    adBlock()
                }
            }
        }
    }
}

@Composable
private fun StorageUsageBlock(
    storageUsage: StorageUsage?,
) {
    val context = LocalContext.current
    val items = remember(storageUsage) {
        storageUsage?.let {
            listOf(
                StorageUsageUiItem(context.getString(R.string.saved_manga), it.savedContent.bytes, it.savedContent.percent),
                StorageUsageUiItem(context.getString(R.string.ai_local_models), it.aiModels.bytes, it.aiModels.percent),
                StorageUsageUiItem(context.getString(R.string.pages_cache), it.pagesCache.bytes, it.pagesCache.percent),
                StorageUsageUiItem(context.getString(R.string.other_cache), it.otherCache.bytes, it.otherCache.percent),
                StorageUsageUiItem(context.getString(R.string.available), it.available.bytes, it.available.percent),
            )
        }.orEmpty()
    }

    if (items.isEmpty()) {
        Text(
            text = context.getString(R.string.computing_),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        )
        return
    }

    items.forEachIndexed { index, item ->
        Text(
            text = context.getString(
                R.string.memory_usage_pattern,
                FileSize.BYTES.format(context, item.bytes),
                item.label,
            ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LinearProgressIndicator(
            progress = { item.progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        )
        if (index != items.lastIndex) {
            SettingsSectionDivider()
        }
    }
}
