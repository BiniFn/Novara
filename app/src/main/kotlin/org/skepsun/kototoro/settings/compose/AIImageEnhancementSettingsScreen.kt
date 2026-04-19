package org.skepsun.kototoro.settings.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.observeAsState

@Composable
fun AIImageEnhancementSettingsScreen(
    settings: AppSettings,
    ncnnModels: List<SettingsChoiceOption<String>>,
    onClearCacheClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val prefs = settings.prefs

    val engineNames = stringArrayResource(R.array.values_reader_super_resolution_engines).toList()
    val anime4kNames = stringArrayResource(R.array.values_reader_super_resolution_anime4k_modes).toList()
    val cacheLimits = stringArrayResource(R.array.values_reader_super_resolution_cache_limits).toList()

    val isEnabled = settings.observeAsState(AppSettings.KEY_READER_SUPER_RESOLUTION_ENABLED) { settings.isReaderSuperResolutionEnabled }.value
    val engine = settings.observeAsState(AppSettings.KEY_READER_SUPER_RESOLUTION_ENGINE) { settings.readerSuperResolutionEngine }.value

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
        ) {
            SettingsSwitchPreference(
                title = stringResource(R.string.reader_super_resolution),
                summary = stringResource(R.string.reader_super_resolution_summary),
                checked = isEnabled,
                onCheckedChange = { settings.prefs.edit { putBoolean(AppSettings.KEY_READER_SUPER_RESOLUTION_ENABLED, it) } }
            )

            if (isEnabled) {
                SettingsChoicePreference(
                    title = stringResource(R.string.reader_super_resolution_engine),
                    options = stringArrayResource(R.array.reader_super_resolution_engines).mapIndexed { index, label ->
                        SettingsChoiceOption(engineNames[index], label)
                    },
                    value = engine,
                    onValueChange = { settings.prefs.edit { putString(AppSettings.KEY_READER_SUPER_RESOLUTION_ENGINE, it) } }
                )

                if (engine == "ANIME4K" || engine == "VULKAN") {
                    SettingsChoicePreference(
                        title = stringResource(R.string.reader_super_resolution_anime4k_mode),
                        options = stringArrayResource(R.array.video_super_resolution_shaders).mapIndexed { index, label ->
                            SettingsChoiceOption(anime4kNames[index], label)
                        },
                        value = settings.observeAsState(AppSettings.KEY_READER_SUPER_RESOLUTION_ANIME4K_MODE) { prefs.getString(AppSettings.KEY_READER_SUPER_RESOLUTION_ANIME4K_MODE, "ANIME4K_A") ?: "ANIME4K_A" }.value,
                        onValueChange = { settings.prefs.edit { putString(AppSettings.KEY_READER_SUPER_RESOLUTION_ANIME4K_MODE, it) } }
                    )
                }

                if (engine == "NCNN") {
                    SettingsChoicePreference(
                        title = stringResource(R.string.reader_super_resolution_model),
                        options = ncnnModels,
                        value = settings.observeAsState(AppSettings.KEY_READER_SUPER_RESOLUTION_MODEL) { prefs.getString(AppSettings.KEY_READER_SUPER_RESOLUTION_MODEL, "SE") ?: "SE" }.value,
                        onValueChange = { settings.prefs.edit { putString(AppSettings.KEY_READER_SUPER_RESOLUTION_MODEL, it) } }
                    )
                }

                SettingsChoicePreference(
                    title = stringResource(R.string.reader_super_resolution_cache_limit),
                    options = stringArrayResource(R.array.reader_super_resolution_cache_limits).mapIndexed { index, label ->
                        SettingsChoiceOption(cacheLimits[index], label)
                    },
                    value = settings.observeAsState(AppSettings.KEY_READER_SUPER_RESOLUTION_CACHE_LIMIT) { prefs.getString(AppSettings.KEY_READER_SUPER_RESOLUTION_CACHE_LIMIT, "512") ?: "512" }.value,
                    onValueChange = { settings.prefs.edit { putString(AppSettings.KEY_READER_SUPER_RESOLUTION_CACHE_LIMIT, it) } }
                )

                SettingsActionPreference(
                    title = stringResource(R.string.reader_super_resolution_clear_cache),
                    summary = stringResource(R.string.reader_super_resolution_clear_cache_summary),
                    onClick = onClearCacheClick
                )
            }
        }
    }
}
