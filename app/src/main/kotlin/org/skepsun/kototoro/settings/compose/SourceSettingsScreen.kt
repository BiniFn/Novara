package org.skepsun.kototoro.settings.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

sealed interface SourceSettingsRowUiState {
    val id: String
}

data class SourceSettingsSectionUiState(
    val id: String,
    val title: String,
    val rows: List<SourceSettingsRowUiState>,
)

data class SourceSettingsActionRowUiState(
    override val id: String,
    val title: String,
    val summary: String? = null,
    val enabled: Boolean = true,
    val showChevron: Boolean = true,
    val onClick: () -> Unit,
) : SourceSettingsRowUiState

data class SourceSettingsSwitchRowUiState(
    override val id: String,
    val title: String,
    val checked: Boolean,
    val summary: String? = null,
    val enabled: Boolean = true,
    val onCheckedChange: (Boolean) -> Unit,
) : SourceSettingsRowUiState

data class SourceSettingsChoiceRowUiState(
    override val id: String,
    val title: String,
    val value: String,
    val options: List<SettingsChoiceOption<String>>,
    val summary: String? = null,
    val enabled: Boolean = true,
    val onValueChange: (String) -> Unit,
) : SourceSettingsRowUiState

data class SourceSettingsTextRowUiState(
    override val id: String,
    val title: String,
    val value: String,
    val summary: String? = null,
    val placeholder: String? = null,
    val isPassword: Boolean = false,
    val enabled: Boolean = true,
    val onValueChange: (String) -> Unit,
) : SourceSettingsRowUiState

data class SourceSettingsInfoRowUiState(
    override val id: String,
    val title: String,
    val summary: String,
) : SourceSettingsRowUiState

@Composable
fun SourceSettingsScreen(
    sections: List<SourceSettingsSectionUiState>,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 20.dp,
                bottom = innerPadding.calculateBottomPadding() +
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            sections.forEach { section ->
                item(key = section.id) {
                    SettingsPreferenceSection(title = section.title) {
                        section.rows.forEachIndexed { index, row ->
                            when (row) {
                                is SourceSettingsActionRowUiState -> {
                                    SettingsActionPreference(
                                        title = row.title,
                                        summary = row.summary,
                                        enabled = row.enabled,
                                        showChevron = row.showChevron,
                                        onClick = row.onClick,
                                    )
                                }

                                is SourceSettingsSwitchRowUiState -> {
                                    SettingsSwitchPreference(
                                        title = row.title,
                                        checked = row.checked,
                                        summary = row.summary,
                                        enabled = row.enabled,
                                        onCheckedChange = row.onCheckedChange,
                                    )
                                }

                                is SourceSettingsChoiceRowUiState -> {
                                    SettingsChoicePreference(
                                        title = row.title,
                                        value = row.value,
                                        options = row.options,
                                        summary = row.summary,
                                        enabled = row.enabled,
                                        onValueChange = row.onValueChange,
                                    )
                                }

                                is SourceSettingsTextRowUiState -> {
                                    SettingsDialogTextPreference(
                                        title = row.title,
                                        value = row.value,
                                        summary = row.summary,
                                        placeholder = row.placeholder,
                                        isPassword = row.isPassword,
                                        enabled = row.enabled,
                                        onValueChange = row.onValueChange,
                                    )
                                }

                                is SourceSettingsInfoRowUiState -> {
                                    SettingsInfoPreference(
                                        title = row.title,
                                        summary = row.summary,
                                    )
                                }
                            }
                            if (index < section.rows.lastIndex) {
                                SettingsSectionDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}
