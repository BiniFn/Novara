package org.skepsun.kototoro.settings.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.skepsun.kototoro.R

@Composable
fun AISettingsScreen(
    onOpenOcrModels: () -> Unit,
    onOpenApiSettings: () -> Unit,
    onOpenE2eApiSettings: () -> Unit,
    onOpenTranslationSettings: () -> Unit,
    onOpenImageEnhancementSettings: () -> Unit,
    onOpenTtsSettings: () -> Unit,
    onOpenVideoEnhancementSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
        ) {
            SettingsPreferenceSection(title = stringResource(R.string.ai_section_core)) {
                SettingsActionPreference(
                    title = stringResource(R.string.reader_translation_manage_ocr_models),
                    summary = stringResource(R.string.reader_translation_manage_ocr_models_summary),
                    onClick = onOpenOcrModels
                )
                SettingsActionPreference(
                    title = stringResource(R.string.ai_api_settings),
                    summary = stringResource(R.string.ai_api_settings_summary),
                    onClick = onOpenApiSettings
                )
                SettingsActionPreference(
                    title = "End-to-End API Settings",
                    summary = "Configure API keys and Endpoint for End-to-End Multimodal OCR",
                    onClick = onOpenE2eApiSettings
                )
            }
            
            SettingsPreferenceSection(title = stringResource(R.string.ai_section_translation)) {
                SettingsActionPreference(
                    title = stringResource(R.string.translation_settings),
                    summary = stringResource(R.string.reader_translation_settings_entry_summary),
                    onClick = onOpenTranslationSettings
                )
            }
            
            SettingsPreferenceSection(title = stringResource(R.string.ai_section_image)) {
                SettingsActionPreference(
                    title = stringResource(R.string.ai_image_enhancement_settings),
                    summary = stringResource(R.string.ai_image_enhancement_summary),
                    onClick = onOpenImageEnhancementSettings
                )
            }
            
            SettingsPreferenceSection(title = "Voice & Subtitle (TTS)") {
                SettingsActionPreference(
                    title = "TTS Settings",
                    summary = "Configure TTS Engines, Voice, and Web Sources",
                    onClick = onOpenTtsSettings
                )
            }
            
            SettingsPreferenceSection(title = stringResource(R.string.ai_section_video)) {
                SettingsActionPreference(
                    title = stringResource(R.string.ai_video_enhancement_settings),
                    summary = stringResource(R.string.ai_video_enhancement_summary),
                    onClick = onOpenVideoEnhancementSettings
                )
            }
        }
    }
}
