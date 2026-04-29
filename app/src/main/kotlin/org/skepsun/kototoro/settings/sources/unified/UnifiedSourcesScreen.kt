package org.skepsun.kototoro.settings.sources.unified

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.getSummary
import org.skepsun.kototoro.core.model.titleResId
import org.skepsun.kototoro.core.ui.compose.ContentSourceIcon
import org.skepsun.kototoro.core.ui.compose.rememberSafePainter
import org.skepsun.kototoro.core.util.ext.getDisplayName
import org.skepsun.kototoro.core.util.ext.toLocaleOrNull
import org.skepsun.kototoro.extensions.repo.ExternalExtensionRepo
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.settings.sources.extensions.formatExtensionFingerprint
import org.skepsun.kototoro.settings.sources.extensions.toInstalledIReaderPackageName
import java.util.Locale

private enum class UnifiedToolbarFilterPanel {
	LANGUAGE,
	MORE,
}

private sealed interface UnifiedSourcesDialogState {
	data object AddRepositoryKind : UnifiedSourcesDialogState
	data class AddRepositoryMode(
		val kind: UnifiedSourceKind,
		val prefillUrl: String? = null,
		val prefillTitle: String? = null,
	) : UnifiedSourcesDialogState

	data class UrlInput(
		val kind: UnifiedSourceKind,
		val prefillUrl: String? = null,
		val prefillTitle: String? = null,
	) : UnifiedSourcesDialogState

	data class InlineInput(
		val kind: UnifiedSourceKind,
		val prefillTitle: String? = null,
	) : UnifiedSourcesDialogState

	data class TrustRepository(val repo: ExternalExtensionRepo) : UnifiedSourcesDialogState
	data class DeleteRepository(val repository: UnifiedSourceRepositoryItem) : UnifiedSourcesDialogState
	data class PackageDetails(val item: UnifiedSourcePackageItem) : UnifiedSourcesDialogState
	data class ThirdPartyDisclaimer(val action: UnifiedThirdPartyAction) : UnifiedSourcesDialogState
}

private sealed interface UnifiedThirdPartyAction {
	data class AddRepositoryUrl(
		val kind: UnifiedSourceKind,
		val url: String,
		val title: String? = null,
	) : UnifiedThirdPartyAction

	data class AddInlineRepository(
		val kind: UnifiedSourceKind,
		val content: String,
		val title: String? = null,
	) : UnifiedThirdPartyAction

	data class OpenRepositoryFile(val kind: UnifiedSourceKind) : UnifiedThirdPartyAction
	data object OpenLocalJar : UnifiedThirdPartyAction
}

@Composable
fun UnifiedSourcesRoute(
	onBack: () -> Unit,
	onBrowseSource: (UnifiedSourceItem) -> Unit,
	onOpenSourceSettings: (UnifiedSourceItem) -> Unit,
	onOpenRepositoryFile: (UnifiedSourceKind) -> Unit,
	onOpenLocalJarPicker: () -> Unit,
	onStartInstall: (Intent) -> Unit,
	onStartUninstall: (Intent) -> Unit,
	modifier: Modifier = Modifier,
	viewModel: UnifiedSourcesViewModel = hiltViewModel(),
) {
	val context = LocalContext.current
	val state by viewModel.uiState.collectAsStateWithLifecycle()
	val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
	val updateAllInProgress by viewModel.updateAllInProgress.collectAsStateWithLifecycle()
	var activeDialog by remember { mutableStateOf<UnifiedSourcesDialogState?>(null) }
	var searchActive by rememberSaveable { mutableStateOf(false) }

	fun proceedThirdPartyAction(action: UnifiedThirdPartyAction) {
		when (action) {
			is UnifiedThirdPartyAction.AddRepositoryUrl -> {
				viewModel.addRepositoryFromUrl(action.kind, action.url, action.title)
			}
			is UnifiedThirdPartyAction.AddInlineRepository -> {
				viewModel.addRepositoryFromInline(action.kind, action.content, action.title)
			}
			is UnifiedThirdPartyAction.OpenRepositoryFile -> {
				onOpenRepositoryFile(action.kind)
			}
			UnifiedThirdPartyAction.OpenLocalJar -> {
				onOpenLocalJarPicker()
			}
		}
	}

	LaunchedEffect(viewModel, context) {
		viewModel.events.collect { event ->
			when (event) {
				is UnifiedSourcesEvent.Message -> {
					Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
				}
				is UnifiedSourcesEvent.TrustExternalRepository -> {
					activeDialog = UnifiedSourcesDialogState.TrustRepository(event.repo)
				}
				is UnifiedSourcesEvent.StartInstall -> onStartInstall(event.intent)
				is UnifiedSourcesEvent.StartUninstall -> onStartUninstall(event.intent)
				is UnifiedSourcesEvent.PackageStateDetails -> {
					activeDialog = UnifiedSourcesDialogState.PackageDetails(event.item)
				}
			}
		}
	}

	UnifiedSourcesScreen(
		state = state,
		isLoading = isLoading,
		updateAllInProgress = updateAllInProgress,
		onBack = onBack,
		searchActive = searchActive,
		onSearchClick = { searchActive = true },
		onSearchClose = {
			searchActive = false
			viewModel.setSearchQuery("")
		},
		onSearchQueryChange = viewModel::setSearchQuery,
		onApplyPreferredLanguages = viewModel::applyPreferredLanguages,
		onClearLanguages = viewModel::clearLanguages,
		onKindClick = viewModel::setKindFilter,
		onContentTypeClick = viewModel::setContentTypeFilter,
		onLocationTypeClick = viewModel::toggleLocationType,
		onLanguageClick = viewModel::toggleLanguage,
		onEnabledFilterClick = viewModel::setEnabledFilter,
		onClearFilters = viewModel::clearFilters,
		onSourceEnabledChange = viewModel::setSourceEnabled,
		onSourcePinnedChange = viewModel::setSourcePinned,
		onBrowseSource = onBrowseSource,
		onOpenSourceSettings = onOpenSourceSettings,
		onAddRepository = { preset ->
			activeDialog = if (preset != null) {
				UnifiedSourcesDialogState.UrlInput(
					kind = preset.kind,
					prefillUrl = preset.url,
					prefillTitle = preset.name,
				)
			} else {
				UnifiedSourcesDialogState.AddRepositoryKind
			}
		},
		onRefreshRepository = { item -> viewModel.refreshRepository(item.id) },
		onDeleteRepository = { item -> activeDialog = UnifiedSourcesDialogState.DeleteRepository(item) },
		onRefreshPackages = { viewModel.refreshPackages() },
		onUpdateAllPackages = viewModel::onUpdateAllPackagesAction,
		onPackagePrimaryAction = viewModel::onPackagePrimaryAction,
		onPackageUninstall = viewModel::uninstallPackage,
		onPackageCancelInstall = viewModel::cancelPackageInstall,
		onImportLocalJar = {
			activeDialog = UnifiedSourcesDialogState.ThirdPartyDisclaimer(UnifiedThirdPartyAction.OpenLocalJar)
		},
		modifier = modifier,
	)

	when (val dialog = activeDialog) {
			UnifiedSourcesDialogState.AddRepositoryKind -> UnifiedSelectionDialog(
				title = stringResource(R.string.add_repository),
			options = listOf(
				UnifiedSourceKind.LEGADO,
				UnifiedSourceKind.TVBOX,
				UnifiedSourceKind.LNREADER,
				UnifiedSourceKind.JAR,
				UnifiedSourceKind.MIHON,
				UnifiedSourceKind.ANIYOMI,
				UnifiedSourceKind.IREADER,
				UnifiedSourceKind.JS,
			),
				optionLabel = { kind -> context.getString(kind.dialogLabelResId()) },
			onDismiss = { activeDialog = null },
			onSelected = { kind ->
				activeDialog = if (kind.supportsJsonImport()) {
					UnifiedSourcesDialogState.AddRepositoryMode(kind)
				} else {
					UnifiedSourcesDialogState.UrlInput(kind = kind)
				}
			},
		)

			is UnifiedSourcesDialogState.AddRepositoryMode -> UnifiedSelectionDialog(
				title = stringResource(
					R.string.add_repository_with_kind,
					stringResource(dialog.kind.dialogLabelResId()),
				),
				options = listOf(R.string.remote_url, R.string.local_file, R.string.paste_content),
				optionLabel = { context.getString(it) },
				onDismiss = { activeDialog = null },
				onSelected = { mode ->
					activeDialog = when (mode) {
						R.string.local_file -> UnifiedSourcesDialogState.ThirdPartyDisclaimer(
							UnifiedThirdPartyAction.OpenRepositoryFile(dialog.kind),
						)
						R.string.paste_content -> UnifiedSourcesDialogState.InlineInput(
							kind = dialog.kind,
							prefillTitle = dialog.prefillTitle,
						)
					else -> UnifiedSourcesDialogState.UrlInput(
						kind = dialog.kind,
						prefillUrl = dialog.prefillUrl,
						prefillTitle = dialog.prefillTitle,
					)
				}
			},
		)

		is UnifiedSourcesDialogState.UrlInput -> UnifiedRepositoryUrlDialog(
			kind = dialog.kind,
			initialUrl = dialog.prefillUrl.orEmpty(),
			onDismiss = { activeDialog = null },
			onConfirm = { url ->
				activeDialog = UnifiedSourcesDialogState.ThirdPartyDisclaimer(
					UnifiedThirdPartyAction.AddRepositoryUrl(dialog.kind, url, dialog.prefillTitle),
				)
			},
		)

		is UnifiedSourcesDialogState.InlineInput -> UnifiedInlineRepositoryDialog(
			kind = dialog.kind,
			onDismiss = { activeDialog = null },
			onConfirm = { content ->
				activeDialog = UnifiedSourcesDialogState.ThirdPartyDisclaimer(
					UnifiedThirdPartyAction.AddInlineRepository(dialog.kind, content, dialog.prefillTitle),
				)
			},
		)

		is UnifiedSourcesDialogState.ThirdPartyDisclaimer -> UnifiedDisclaimerDialog(
			onDismiss = { activeDialog = null },
			onConfirm = {
				proceedThirdPartyAction(dialog.action)
				activeDialog = null
			},
		)

		is UnifiedSourcesDialogState.DeleteRepository -> UnifiedDeleteRepositoryDialog(
			repository = dialog.repository,
			onDismiss = { activeDialog = null },
			onConfirm = {
				viewModel.deleteRepository(dialog.repository.id)
				activeDialog = null
			},
		)

		is UnifiedSourcesDialogState.TrustRepository -> UnifiedTrustRepositoryDialog(
			repo = dialog.repo,
			onDismiss = { activeDialog = null },
			onConfirm = {
				viewModel.confirmExternalRepository(dialog.repo)
				activeDialog = null
			},
		)

		is UnifiedSourcesDialogState.PackageDetails -> UnifiedPackageStateDetailsDialog(
			item = dialog.item,
			onDismiss = { activeDialog = null },
			onManageRepositories = {
				activeDialog = UnifiedSourcesDialogState.AddRepositoryKind
			},
			onUninstall = {
				viewModel.uninstallPackage(dialog.item.id)
				activeDialog = null
			},
		)

		null -> Unit
	}
}

@Composable
private fun <T> UnifiedSelectionDialog(
	title: String,
	options: List<T>,
	optionLabel: (T) -> String,
	onDismiss: () -> Unit,
	onSelected: (T) -> Unit,
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = {
			TextButton(onClick = onDismiss) {
				Text(stringResource(android.R.string.cancel))
			}
		},
		title = { Text(title) },
		text = {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.heightIn(max = 360.dp)
					.verticalScroll(rememberScrollState()),
				verticalArrangement = Arrangement.spacedBy(4.dp),
			) {
				options.forEach { option ->
					TextButton(
						onClick = { onSelected(option) },
						modifier = Modifier.fillMaxWidth(),
					) {
						Text(
							text = optionLabel(option),
							modifier = Modifier.fillMaxWidth(),
						)
					}
				}
			}
		},
	)
}

@Composable
private fun UnifiedRepositoryUrlDialog(
	kind: UnifiedSourceKind,
	initialUrl: String,
	onDismiss: () -> Unit,
	onConfirm: (String) -> Unit,
) {
	var value by remember(kind, initialUrl) { mutableStateOf(initialUrl) }
	val hint = when (kind) {
		UnifiedSourceKind.LEGADO -> "https://example.com/legado.json"
		UnifiedSourceKind.TVBOX -> "http://z.qiqiv.cn/123.txt"
		else -> "https://example.com/index.min.json"
	}
	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = {
			TextButton(onClick = { onConfirm(value) }) {
				Text(stringResource(android.R.string.ok))
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text(stringResource(android.R.string.cancel))
			}
		},
			title = { Text(stringResource(R.string.repository_url)) },
		text = {
			OutlinedTextField(
				value = value,
				onValueChange = { value = it },
				modifier = Modifier.fillMaxWidth(),
				singleLine = true,
				label = { Text(hint) },
			)
		},
	)
}

@Composable
private fun UnifiedInlineRepositoryDialog(
	kind: UnifiedSourceKind,
	onDismiss: () -> Unit,
	onConfirm: (String) -> Unit,
) {
	var value by remember(kind) { mutableStateOf("") }
	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = {
			TextButton(onClick = { onConfirm(value) }) {
				Text(stringResource(android.R.string.ok))
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text(stringResource(android.R.string.cancel))
			}
		},
			title = { Text(stringResource(R.string.paste_repository_with_kind, stringResource(kind.dialogLabelResId()))) },
		text = {
			OutlinedTextField(
				value = value,
				onValueChange = { value = it },
				modifier = Modifier
					.fillMaxWidth()
					.heightIn(min = 140.dp, max = 320.dp),
					label = { Text(stringResource(R.string.paste_content)) },
			)
		},
	)
}

@Composable
private fun UnifiedDisclaimerDialog(
	onDismiss: () -> Unit,
	onConfirm: () -> Unit,
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = {
			TextButton(onClick = onConfirm) {
				Text(stringResource(android.R.string.ok))
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text(stringResource(android.R.string.cancel))
			}
		},
		title = { Text(stringResource(R.string.add_repository)) },
		text = { Text(stringResource(R.string.welcome_plugins_disclaimer)) },
	)
}

@Composable
private fun UnifiedDeleteRepositoryDialog(
	repository: UnifiedSourceRepositoryItem,
	onDismiss: () -> Unit,
	onConfirm: () -> Unit,
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = {
			TextButton(onClick = onConfirm) {
				Text(stringResource(R.string.delete))
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text(stringResource(android.R.string.cancel))
			}
		},
			title = { Text(stringResource(R.string.delete_repository_title)) },
			text = { Text(stringResource(R.string.delete_repository_message, repository.name)) },
	)
}

@Composable
private fun UnifiedTrustRepositoryDialog(
	repo: ExternalExtensionRepo,
	onDismiss: () -> Unit,
	onConfirm: () -> Unit,
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = {
			TextButton(onClick = onConfirm) {
				Text(stringResource(R.string.trust_and_add))
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text(stringResource(android.R.string.cancel))
			}
		},
		title = { Text(stringResource(R.string.trust_extension_repository)) },
		text = {
			Text(
				text = stringResource(
					R.string.trust_extension_repository_message,
					repo.displayName,
					repo.website,
					repo.signingKeyFingerprint.formatExtensionFingerprint(),
				),
			)
		},
	)
}

@Composable
private fun UnifiedPackageStateDetailsDialog(
	item: UnifiedSourcePackageItem,
	onDismiss: () -> Unit,
	onManageRepositories: () -> Unit,
	onUninstall: () -> Unit,
) {
	val title: String
	val message: String
	when (item.state) {
		UnifiedSourcePackageState.UNTRUSTED -> {
			title = stringResource(R.string.untrusted_extension)
			message = stringResource(
				R.string.untrusted_extension_message,
				item.name,
				item.packageName.orEmpty(),
				item.installPayload?.signatureHash.orEmpty().formatExtensionFingerprint(),
			)
		}
		UnifiedSourcePackageState.INCOMPATIBLE -> {
			title = stringResource(R.string.incompatible_extension)
			message = stringResource(
				R.string.incompatible_extension_message,
				item.name,
				item.versionName.orEmpty(),
				item.installPayload?.libVersion?.toString().orEmpty(),
			)
		}
		else -> return
	}
	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = {
			TextButton(onClick = if (item.isInstalled) onUninstall else onDismiss) {
				Text(stringResource(if (item.isInstalled) R.string.remove else android.R.string.ok))
			}
		},
		dismissButton = {
			Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
				TextButton(onClick = onManageRepositories) {
					Text(stringResource(R.string.manage_extension_repositories))
				}
				if (item.isInstalled) {
					TextButton(onClick = onDismiss) {
						Text(stringResource(android.R.string.cancel))
					}
				}
			}
		},
		title = { Text(title) },
		text = { Text(message) },
	)
}

@Composable
fun UnifiedSourcesToolbarControls(
	state: UnifiedSourcesUiState,
	searchActive: Boolean,
	onSearchClick: () -> Unit,
	onSearchClose: () -> Unit,
	onSearchQueryChange: (String) -> Unit,
	onLanguageClick: (String) -> Unit,
	onApplyPreferredLanguages: () -> Unit,
	onClearLanguages: () -> Unit,
	onEnabledFilterClick: (UnifiedEnabledFilter) -> Unit,
	onLocationTypeClick: (UnifiedRepositoryLocationType) -> Unit,
	onClearFilters: () -> Unit,
	modifier: Modifier = Modifier,
) {
	if (state !is UnifiedSourcesUiState.Ready) {
		return
	}
	var activePanel by rememberSaveable { mutableStateOf<UnifiedToolbarFilterPanel?>(null) }

	Row(
		modifier = modifier
			.fillMaxWidth()
			.height(48.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(4.dp),
	) {
		if (searchActive) {
			ToolbarSearchField(
				query = state.filters.query,
				onQueryChange = onSearchQueryChange,
				autofocus = true,
				modifier = Modifier
					.weight(1f)
					.widthIn(min = 96.dp),
			)
			IconButton(
				onClick = onSearchClose,
				modifier = Modifier.size(40.dp),
			) {
				Icon(
					Icons.Filled.Close,
					contentDescription = stringResource(android.R.string.cancel),
					modifier = Modifier.size(20.dp),
				)
			}
		} else {
			Spacer(modifier = Modifier.weight(1f))
			ToolbarSearchIconButton(
				active = state.filters.query.isNotBlank(),
				onClick = onSearchClick,
			)
		}
		ToolbarFilterIconButton(
			iconRes = R.drawable.ic_language,
			activeCount = state.filters.languages.size,
			contentDescription = stringResource(R.string.filter_extensions_by_language),
			onClick = { activePanel = UnifiedToolbarFilterPanel.LANGUAGE },
		)
		ToolbarFilterIconButton(
			iconRes = R.drawable.ic_filter_menu,
			activeCount = state.filters.otherFilterCount(),
			contentDescription = stringResource(R.string.more_filters),
			onClick = { activePanel = UnifiedToolbarFilterPanel.MORE },
		)
	}

	when (activePanel) {
		UnifiedToolbarFilterPanel.LANGUAGE -> UnifiedLanguageFilterDialog(
			languages = state.availableLanguages,
			selectedLanguages = state.filters.languages,
			onDismiss = { activePanel = null },
			onLanguageClick = onLanguageClick,
			onApplyPreferredLanguages = onApplyPreferredLanguages,
			onClear = onClearLanguages,
		)
		UnifiedToolbarFilterPanel.MORE -> UnifiedFilterGroupDialog(
			title = stringResource(R.string.more_filters),
			onDismiss = { activePanel = null },
			onClear = onClearFilters,
		) {
			FilterSection(title = stringResource(R.string.status)) {
				items(UnifiedEnabledFilter.entries) { filter ->
					CompactFilterChip(
						selected = state.filters.enabledFilter == filter,
						onClick = { onEnabledFilterClick(filter) },
						text = filter.displayLabel(),
					)
				}
			}
			FilterSection(title = stringResource(R.string.repository_source)) {
				items(state.availableLocationTypes) { type ->
					CompactFilterChip(
						selected = type in state.filters.locationTypes,
						onClick = { onLocationTypeClick(type) },
						text = type.displayLabel(),
					)
				}
			}
		}
		null -> Unit
	}
}

@Composable
private fun UnifiedLanguageFilterDialog(
	languages: List<String>,
	selectedLanguages: Set<String>,
	onDismiss: () -> Unit,
	onLanguageClick: (String) -> Unit,
	onApplyPreferredLanguages: () -> Unit,
	onClear: () -> Unit,
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = {
			TextButton(onClick = onDismiss) {
				Text(stringResource(R.string.done))
			}
		},
		dismissButton = {
			Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
				TextButton(onClick = onClear) {
					Text(stringResource(R.string.clear))
				}
				TextButton(onClick = onApplyPreferredLanguages) {
					Text(stringResource(R.string.use_setup_wizard_languages))
				}
			}
		},
		title = { Text(stringResource(R.string.filter_extensions_by_language)) },
		text = {
			LazyVerticalGrid(
				columns = GridCells.Adaptive(minSize = 120.dp),
				modifier = Modifier.heightIn(max = 360.dp),
				horizontalArrangement = Arrangement.spacedBy(8.dp),
				verticalArrangement = Arrangement.spacedBy(8.dp),
			) {
				gridItems(languages, key = { it }) { language ->
					CompactFilterChip(
						selected = language in selectedLanguages,
						onClick = { onLanguageClick(language) },
						text = language.displayLanguageLabel(),
						modifier = Modifier.fillMaxWidth(),
					)
				}
			}
		},
	)
}

@Composable
private fun ToolbarSearchField(
	query: String,
	onQueryChange: (String) -> Unit,
	modifier: Modifier = Modifier,
	autofocus: Boolean = false,
) {
	val focusRequester = remember { FocusRequester() }
	if (autofocus) {
		LaunchedEffect(Unit) {
			focusRequester.requestFocus()
		}
	}
	Surface(
		modifier = modifier.height(40.dp),
		shape = RoundedCornerShape(20.dp),
		color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
		contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
	) {
		BasicTextField(
			value = query,
			onValueChange = onQueryChange,
			modifier = Modifier
				.fillMaxSize()
				.focusRequester(focusRequester),
			singleLine = true,
			textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
			decorationBox = { innerTextField ->
				Row(
					modifier = Modifier
						.fillMaxSize()
						.padding(horizontal = 12.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					Icon(
						Icons.Filled.Search,
						contentDescription = null,
						modifier = Modifier.size(18.dp),
					)
					Spacer(modifier = Modifier.width(8.dp))
					Box(modifier = Modifier.weight(1f)) {
						if (query.isBlank()) {
								Text(
									text = stringResource(R.string.search),
								style = MaterialTheme.typography.bodyMedium,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
								maxLines = 1,
							)
						}
						innerTextField()
					}
				}
			},
		)
	}
}

@Composable
private fun ToolbarSearchIconButton(
	active: Boolean,
	onClick: () -> Unit,
) {
	Box(contentAlignment = Alignment.TopEnd) {
		IconButton(
			onClick = onClick,
			modifier = Modifier.size(40.dp),
		) {
			Icon(
				Icons.Filled.Search,
				contentDescription = stringResource(R.string.search),
				modifier = Modifier.size(20.dp),
				tint = if (active) {
					MaterialTheme.colorScheme.primary
				} else {
					MaterialTheme.colorScheme.onSurfaceVariant
				},
			)
		}
		if (active) {
			Surface(
				modifier = Modifier.size(8.dp),
				shape = RoundedCornerShape(4.dp),
				color = MaterialTheme.colorScheme.primary,
				contentColor = MaterialTheme.colorScheme.onPrimary,
			) {}
		}
	}
}

@Composable
private fun ToolbarFilterIconButton(
	iconRes: Int,
	activeCount: Int,
	contentDescription: String,
	onClick: () -> Unit,
) {
	Box(contentAlignment = Alignment.TopEnd) {
		IconButton(
			onClick = onClick,
			modifier = Modifier.size(40.dp),
		) {
			Icon(
				painter = painterResource(iconRes),
				contentDescription = contentDescription,
				modifier = Modifier.size(20.dp),
				tint = if (activeCount > 0) {
					MaterialTheme.colorScheme.primary
				} else {
					MaterialTheme.colorScheme.onSurfaceVariant
				},
			)
		}
		if (activeCount > 0) {
			Surface(
				shape = RoundedCornerShape(8.dp),
				color = MaterialTheme.colorScheme.primary,
				contentColor = MaterialTheme.colorScheme.onPrimary,
			) {
				Text(
					text = activeCount.coerceAtMost(9).toString(),
					modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
					style = MaterialTheme.typography.labelSmall,
				)
			}
		}
	}
}

@Composable
private fun UnifiedFilterGroupDialog(
	title: String,
	onDismiss: () -> Unit,
	onClear: () -> Unit,
	content: @Composable () -> Unit,
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(title) },
		text = {
			Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
				content()
			}
		},
		confirmButton = {
				TextButton(onClick = onDismiss) {
					Text(stringResource(R.string.done))
				}
			},
			dismissButton = {
				TextButton(onClick = onClear) {
					Text(stringResource(R.string.clear))
				}
			},
	)
}

@Composable
fun UnifiedSourcesScreen(
	state: UnifiedSourcesUiState,
	isLoading: Boolean,
	updateAllInProgress: Boolean,
	onBack: () -> Unit,
	searchActive: Boolean,
	onSearchClick: () -> Unit,
	onSearchClose: () -> Unit,
	onSearchQueryChange: (String) -> Unit,
	onApplyPreferredLanguages: () -> Unit,
	onClearLanguages: () -> Unit,
	onKindClick: (UnifiedSourceKind?) -> Unit,
	onContentTypeClick: (ContentType?) -> Unit,
	onLocationTypeClick: (UnifiedRepositoryLocationType) -> Unit,
	onLanguageClick: (String) -> Unit,
	onEnabledFilterClick: (UnifiedEnabledFilter) -> Unit,
	onClearFilters: () -> Unit,
	onSourceEnabledChange: (String, Boolean) -> Unit,
	onSourcePinnedChange: (String, Boolean) -> Unit,
	onBrowseSource: (UnifiedSourceItem) -> Unit,
	onOpenSourceSettings: (UnifiedSourceItem) -> Unit,
	onAddRepository: (UnifiedSourceRepositoryItem?) -> Unit,
	onRefreshRepository: (UnifiedSourceRepositoryItem) -> Unit,
	onDeleteRepository: (UnifiedSourceRepositoryItem) -> Unit,
	onRefreshPackages: () -> Unit,
	onUpdateAllPackages: () -> Unit,
	onPackagePrimaryAction: (String) -> Unit,
	onPackageUninstall: (String) -> Unit,
	onPackageCancelInstall: (String) -> Unit,
	onImportLocalJar: () -> Unit,
	modifier: Modifier = Modifier,
) {
	var selectedTab by rememberSaveable { mutableIntStateOf(0) }
	Scaffold(
		modifier = modifier,
		contentWindowInsets = WindowInsets(0, 0, 0, 0),
	) { innerPadding ->
		when (state) {
			UnifiedSourcesUiState.Loading -> {
				Column(
					modifier = Modifier
						.fillMaxSize()
						.padding(innerPadding),
				) {
					LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
				}
			}
			is UnifiedSourcesUiState.Ready -> {
				Column(
					modifier = Modifier
						.fillMaxSize()
						.padding(innerPadding),
				) {
					if (isLoading) {
						LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
					}
					UnifiedSourcesToolbarControls(
						state = state,
						searchActive = searchActive,
						onSearchClick = onSearchClick,
						onSearchClose = onSearchClose,
						onSearchQueryChange = onSearchQueryChange,
						onLanguageClick = onLanguageClick,
						onApplyPreferredLanguages = onApplyPreferredLanguages,
						onClearLanguages = onClearLanguages,
						onEnabledFilterClick = onEnabledFilterClick,
						onLocationTypeClick = onLocationTypeClick,
						onClearFilters = onClearFilters,
						modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
					)
					UnifiedSourcesFilterTabs(
						state = state,
						onContentTypeClick = onContentTypeClick,
						onKindClick = onKindClick,
					)
					TabRow(selectedTabIndex = selectedTab) {
						Tab(
								selected = selectedTab == 0,
								onClick = { selectedTab = 0 },
								text = { Text(stringResource(R.string.sources_tab_title, state.sources.size)) },
							)
						Tab(
								selected = selectedTab == 1,
								onClick = { selectedTab = 1 },
								text = { Text(stringResource(R.string.repositories_tab_title, state.repositories.size)) },
							)
						Tab(
								selected = selectedTab == 2,
								onClick = { selectedTab = 2 },
								text = { Text(stringResource(R.string.packages_tab_title, state.packages.size)) },
							)
					}
					when (selectedTab) {
						0 -> UnifiedSourceList(
							sources = state.sources,
							onBrowseSource = onBrowseSource,
							onOpenSourceSettings = onOpenSourceSettings,
							onSourceEnabledChange = onSourceEnabledChange,
							onSourcePinnedChange = onSourcePinnedChange,
						)
						1 -> UnifiedRepositoryList(
							repositories = state.repositories,
							onAddRepository = onAddRepository,
							onRefreshRepository = onRefreshRepository,
							onDeleteRepository = onDeleteRepository,
						)
						else -> UnifiedPackageList(
							packages = state.packages,
							updateAllInProgress = updateAllInProgress,
							onRefreshPackages = onRefreshPackages,
							onUpdateAllPackages = onUpdateAllPackages,
							onPackagePrimaryAction = onPackagePrimaryAction,
							onPackageUninstall = onPackageUninstall,
							onPackageCancelInstall = onPackageCancelInstall,
							onImportLocalJar = onImportLocalJar,
						)
					}
				}
			}
		}
	}
}

@Composable
private fun UnifiedSourcesFilterTabs(
	state: UnifiedSourcesUiState.Ready,
	onContentTypeClick: (ContentType?) -> Unit,
	onKindClick: (UnifiedSourceKind?) -> Unit,
) {
	Column(
		modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
		verticalArrangement = Arrangement.spacedBy(4.dp),
	) {
		LazyRow(
			horizontalArrangement = Arrangement.spacedBy(6.dp),
			contentPadding = PaddingValues(horizontal = 1.dp),
		) {
			item(key = "content_all") {
					CompactFilterChip(
						selected = state.filters.contentTypes.isEmpty(),
						onClick = { onContentTypeClick(null) },
						text = stringResource(R.string.all_content),
				)
			}
			items(state.availableContentTypes, key = { it.name }) { type ->
				CompactFilterChip(
					selected = type in state.filters.contentTypes,
					onClick = { onContentTypeClick(type) },
					text = stringResource(type.titleResId),
				)
			}
		}
		LazyRow(
			horizontalArrangement = Arrangement.spacedBy(6.dp),
			contentPadding = PaddingValues(horizontal = 1.dp),
		) {
			item(key = "kind_all") {
					CompactFilterChip(
						selected = state.filters.kinds.isEmpty(),
						onClick = { onKindClick(null) },
						text = stringResource(R.string.all_sources),
				)
			}
			items(state.availableKinds, key = { it.name }) { kind ->
				CompactFilterChip(
					selected = kind in state.filters.kinds,
					onClick = { onKindClick(kind) },
					text = kind.displayLabel(),
				)
			}
		}
	}
}

@Composable
private fun FilterSection(
	title: String,
	content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
	Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
		Text(
			text = title,
			style = MaterialTheme.typography.labelMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		LazyRow(
			horizontalArrangement = Arrangement.spacedBy(6.dp),
			contentPadding = PaddingValues(horizontal = 1.dp),
			content = content,
		)
	}
}

@Composable
private fun CompactFilterChip(
	selected: Boolean,
	onClick: () -> Unit,
	text: String,
	modifier: Modifier = Modifier,
) {
	FilterChip(
		selected = selected,
		onClick = onClick,
		modifier = modifier.defaultMinSize(minHeight = 30.dp),
		label = {
			Text(
				text = text,
				style = MaterialTheme.typography.labelSmall,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		},
	)
}

@Composable
private fun UnifiedSourceList(
	sources: List<UnifiedSourceItem>,
	onBrowseSource: (UnifiedSourceItem) -> Unit,
	onOpenSourceSettings: (UnifiedSourceItem) -> Unit,
	onSourceEnabledChange: (String, Boolean) -> Unit,
	onSourcePinnedChange: (String, Boolean) -> Unit,
) {
	LazyColumn(
		contentPadding = PaddingValues(vertical = 4.dp),
	) {
		items(sources, key = { it.id }) { item ->
			UnifiedSourceRow(
				item = item,
				onBrowseSource = onBrowseSource,
				onOpenSourceSettings = onOpenSourceSettings,
				onSourceEnabledChange = onSourceEnabledChange,
				onSourcePinnedChange = onSourcePinnedChange,
			)
			HorizontalDivider(modifier = Modifier.padding(start = 64.dp))
		}
	}
}

@Composable
private fun UnifiedSourceRow(
	item: UnifiedSourceItem,
	onBrowseSource: (UnifiedSourceItem) -> Unit,
	onOpenSourceSettings: (UnifiedSourceItem) -> Unit,
	onSourceEnabledChange: (String, Boolean) -> Unit,
	onSourcePinnedChange: (String, Boolean) -> Unit,
) {
	val context = LocalContext.current
	var menuExpanded by rememberSaveable(item.id) { mutableStateOf(false) }
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.background(MaterialTheme.colorScheme.background)
			.clickable { onBrowseSource(item) }
			.padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		ContentSourceIcon(
			source = item.source,
			modifier = Modifier.size(32.dp),
			contentDescription = item.title,
		)
		Spacer(modifier = Modifier.width(16.dp))
		Column(
			modifier = Modifier.weight(1f),
			verticalArrangement = Arrangement.spacedBy(3.dp),
		) {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(6.dp),
			) {
				Text(
					text = item.title,
					modifier = Modifier.weight(1f, fill = false),
					style = MaterialTheme.typography.titleSmall,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				if (item.isPinned) {
					Icon(
						painter = painterResource(R.drawable.ic_pin_small),
						contentDescription = null,
						modifier = Modifier.size(14.dp),
						tint = MaterialTheme.colorScheme.primary,
					)
				}
					CompactTag(text = item.kind.displayLabel())
					if (!item.isAvailable || item.isBroken) {
						CompactTag(text = stringResource(R.string.unavailable), isWarning = true)
					}
			}
			Text(
				text = item.source.getSummary(context, item.contentType) ?: buildSourceSubtitle(item),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			Row(
				horizontalArrangement = Arrangement.spacedBy(6.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				item.language?.takeIf { it.isNotBlank() }?.let { CompactTag(it.uppercase()) }
				CompactTag(item.contentType.name.lowercase().replaceFirstChar { it.titlecase() })
				item.repositoryName?.takeIf { it.isNotBlank() }?.let { CompactTag(it) }
					item.packageName?.takeIf { it.isNotBlank() }?.let { CompactTag(it) }
					if (item.isNsfw) {
						CompactTag(text = stringResource(R.string.nsfw), isWarning = true)
					}
			}
		}
		Box {
			IconButton(onClick = { menuExpanded = true }) {
					Icon(
						painter = painterResource(R.drawable.ic_more_vert),
						contentDescription = stringResource(R.string.more_filters),
					)
				}
			DropdownMenu(
				expanded = menuExpanded,
				onDismissRequest = { menuExpanded = false },
			) {
					DropdownMenuItem(
						text = { Text(stringResource(R.string.browse_available_extensions)) },
					onClick = {
						menuExpanded = false
						onBrowseSource(item)
					},
				)
					DropdownMenuItem(
						text = { Text(stringResource(if (item.isPinned) R.string.unpin else R.string.pin)) },
					onClick = {
						menuExpanded = false
						onSourcePinnedChange(item.id, !item.isPinned)
					},
				)
					DropdownMenuItem(
						text = { Text(stringResource(R.string.settings)) },
					onClick = {
						menuExpanded = false
						onOpenSourceSettings(item)
					},
				)
			}
		}
		Switch(
			checked = item.isEnabled,
			onCheckedChange = { onSourceEnabledChange(item.id, it) },
		)
	}
}

@Composable
private fun UnifiedRepositoryList(
	repositories: List<UnifiedSourceRepositoryItem>,
	onAddRepository: (UnifiedSourceRepositoryItem?) -> Unit,
	onRefreshRepository: (UnifiedSourceRepositoryItem) -> Unit,
	onDeleteRepository: (UnifiedSourceRepositoryItem) -> Unit,
) {
	LazyColumn(
		contentPadding = PaddingValues(vertical = 4.dp),
	) {
		item(key = "add_repository") {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 16.dp, vertical = 8.dp),
			) {
					AssistChip(
						onClick = { onAddRepository(null) },
						label = { Text(stringResource(R.string.add_repository_prompt)) },
				)
			}
		}
		items(repositories, key = { it.id }) { item ->
			ElevatedCard(modifier = Modifier.fillMaxWidth()) {
				Column(
					modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
					verticalArrangement = Arrangement.spacedBy(4.dp),
				) {
					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(8.dp),
					) {
						Text(
							text = item.name,
							modifier = Modifier.weight(1f),
							style = MaterialTheme.typography.titleSmall,
							fontWeight = FontWeight.SemiBold,
						)
						if (item.isConfigured) {
								AssistChip(
									onClick = { onRefreshRepository(item) },
									label = { Text(stringResource(R.string.refresh_action)) },
								)
								AssistChip(
									onClick = { onDeleteRepository(item) },
									label = { Text(stringResource(R.string.delete)) },
								)
							} else if (item.isPreset) {
								AssistChip(
									onClick = { onAddRepository(item) },
									label = { Text(stringResource(R.string.add)) },
								)
							}
					}
					Text(
						text = "${item.kind.displayLabel()} · ${item.locationType.displayLabel()}",
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
					Text(
						text = item.url,
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
				}
			}
		}
	}
}

@Composable
private fun UnifiedPackageList(
	packages: List<UnifiedSourcePackageItem>,
	updateAllInProgress: Boolean,
	onRefreshPackages: () -> Unit,
	onUpdateAllPackages: () -> Unit,
	onPackagePrimaryAction: (String) -> Unit,
	onPackageUninstall: (String) -> Unit,
	onPackageCancelInstall: (String) -> Unit,
	onImportLocalJar: () -> Unit,
) {
	LazyColumn(
		contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
		verticalArrangement = Arrangement.spacedBy(4.dp),
	) {
		item(key = "package_actions") {
			LazyRow(
				modifier = Modifier
					.fillMaxWidth()
					.padding(bottom = 4.dp),
				horizontalArrangement = Arrangement.spacedBy(8.dp),
			) {
				item(key = "refresh_packages") {
						CompactActionChip(
						onClick = onRefreshPackages,
						label = { Text(stringResource(R.string.refresh_packages)) },
						)
				}
				item(key = "update_all_packages") {
						CompactActionChip(
						onClick = onUpdateAllPackages,
						label = {
							Text(
								stringResource(
									if (updateAllInProgress) R.string.cancel_update_all_packages else R.string.update_all_packages,
								),
							)
						},
						)
				}
				item(key = "import_local_jar") {
						CompactActionChip(
						onClick = onImportLocalJar,
						label = { Text(stringResource(R.string.import_local_jar)) },
						)
				}
			}
		}
		items(packages, key = { it.id }) { item ->
			UnifiedPackageRow(
				item = item,
				onPrimaryAction = { onPackagePrimaryAction(item.id) },
				onUninstall = { onPackageUninstall(item.id) },
				onCancelInstall = { onPackageCancelInstall(item.id) },
			)
		}
	}
}

@Composable
private fun UnifiedPackageRow(
	item: UnifiedSourcePackageItem,
	onPrimaryAction: () -> Unit,
	onUninstall: () -> Unit,
	onCancelInstall: () -> Unit,
) {
	ElevatedCard(modifier = Modifier.fillMaxWidth()) {
		Column(
			modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
			verticalArrangement = Arrangement.spacedBy(4.dp),
		) {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(10.dp),
			) {
				UnifiedPackageIcon(item = item)
				Column(modifier = Modifier.weight(1f)) {
					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(6.dp),
					) {
						Text(
							text = item.name,
							modifier = Modifier.weight(1f, fill = false),
							style = MaterialTheme.typography.titleSmall,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
						)
						CompactTag(item.kind.displayLabel())
						CompactTag(item.state.displayLabel(), isWarning = item.state.isWarning)
					}
					Text(
						text = buildPackageSubtitle(item),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
				}
			}
				if (item.installProgressPercent != null) {
					Text(
						text = stringResource(R.string.package_download_progress, item.installProgressPercent),
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
				LinearProgressIndicator(
					progress = { item.installProgressPercent / 100f },
					modifier = Modifier.fillMaxWidth(),
				)
			}
			Row(
				horizontalArrangement = Arrangement.spacedBy(6.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				item.language?.takeIf { it.isNotBlank() }?.let { CompactTag(it.uppercase()) }
				if (item.installedVersionName != null && item.state == UnifiedSourcePackageState.UPDATE_AVAILABLE) {
					CompactTag(stringResource(R.string.installed_version_pattern, item.installedVersionName))
				}
				if (item.isNsfw) {
					CompactTag(stringResource(R.string.nsfw), isWarning = true)
				}
			}
			if (item.sourceNames.isNotEmpty()) {
				Text(
					text = item.sourceNames.take(8).joinToString(", "),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}
			Row(
				horizontalArrangement = Arrangement.spacedBy(6.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				when (item.state) {
					UnifiedSourcePackageState.AVAILABLE,
					UnifiedSourcePackageState.UPDATE_AVAILABLE,
					UnifiedSourcePackageState.UNTRUSTED,
					UnifiedSourcePackageState.INCOMPATIBLE -> {
						CompactActionChip(
							onClick = onPrimaryAction,
							label = { Text(item.state.primaryActionLabel()) },
						)
					}
						UnifiedSourcePackageState.INSTALLING -> {
							CompactActionChip(
								onClick = onCancelInstall,
								label = { Text(stringResource(android.R.string.cancel)) },
							)
						}
					UnifiedSourcePackageState.INSTALLED -> Unit
				}
					if (item.isInstalled) {
						CompactActionChip(
							onClick = onUninstall,
							label = { Text(stringResource(R.string.remove)) },
						)
					}
			}
		}
	}
}

@Composable
private fun UnifiedPackageIcon(
	item: UnifiedSourcePackageItem,
	modifier: Modifier = Modifier,
) {
	val context = LocalContext.current
	val fallbackPainter = rememberSafePainter(item.kind.packageIconRes())
	val installedIcon = remember(item.kind, item.packageName, item.isInstalled, context) {
		val installedPackageName = item.installedIconPackageName() ?: return@remember null
		runCatching { context.packageManager.getApplicationIcon(installedPackageName) }.getOrNull()
	}
	val iconModel = installedIcon ?: item.iconUrl

	Box(
		modifier = modifier
			.size(32.dp)
			.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp)),
		contentAlignment = Alignment.Center,
	) {
		if (iconModel != null) {
			AsyncImage(
				model = iconModel,
				contentDescription = null,
				modifier = Modifier.size(20.dp),
				placeholder = fallbackPainter,
				error = fallbackPainter,
				fallback = fallbackPainter,
			)
		} else {
			Icon(
				painter = fallbackPainter,
				contentDescription = null,
				modifier = Modifier.size(18.dp),
				tint = MaterialTheme.colorScheme.onSecondaryContainer,
			)
		}
	}
}

@Composable
private fun buildSourceSubtitle(item: UnifiedSourceItem): String {
	return listOfNotNull(
		item.kind.displayLabel(),
		stringResource(item.contentType.titleResId),
		item.language,
		item.repositoryName,
		item.packageName,
	).joinToString(" · ")
}

@Composable
private fun buildPackageSubtitle(item: UnifiedSourcePackageItem): String {
	return listOfNotNull(
		item.versionName?.let { "v$it" },
		item.repositoryName,
		stringResource(R.string.extension_source_count, item.sourceCount),
	).joinToString(" · ")
}

private fun UnifiedSourcePackageItem.installedIconPackageName(): String? {
	if (!isInstalled) {
		return null
	}
	return when (kind) {
		UnifiedSourceKind.MIHON,
		UnifiedSourceKind.ANIYOMI -> packageName
		UnifiedSourceKind.IREADER -> packageName?.toInstalledIReaderPackageName()
		else -> null
	}
}

private fun UnifiedSourceKind.packageIconRes(): Int {
	return when (this) {
		UnifiedSourceKind.JAR -> R.drawable.ic_file_zip
		UnifiedSourceKind.MIHON -> R.drawable.ic_source_mihon
		UnifiedSourceKind.ANIYOMI -> R.drawable.ic_source_aniyomi
		UnifiedSourceKind.IREADER -> R.drawable.ic_source_ireader
		UnifiedSourceKind.LEGADO -> R.drawable.ic_source_legado
		UnifiedSourceKind.TVBOX -> R.drawable.ic_source_tvbox
		UnifiedSourceKind.JS -> R.drawable.ic_source_js
		UnifiedSourceKind.LNREADER -> R.drawable.ic_source_lnreader
		UnifiedSourceKind.NATIVE -> R.drawable.ic_source_builtin
	}
}

@Composable
private fun UnifiedSourcePackageState.displayLabel(): String {
	return when (this) {
		UnifiedSourcePackageState.AVAILABLE -> stringResource(R.string.available)
		UnifiedSourcePackageState.UPDATE_AVAILABLE -> stringResource(R.string.update)
		UnifiedSourcePackageState.INSTALLED -> stringResource(R.string.installed)
		UnifiedSourcePackageState.INSTALLING -> stringResource(R.string.installing_extension)
		UnifiedSourcePackageState.UNTRUSTED -> stringResource(R.string.untrusted_extension)
		UnifiedSourcePackageState.INCOMPATIBLE -> stringResource(R.string.incompatible_extension)
	}
}

private val UnifiedSourcePackageState.isWarning: Boolean
	get() = this == UnifiedSourcePackageState.UNTRUSTED || this == UnifiedSourcePackageState.INCOMPATIBLE

@Composable
private fun UnifiedSourcePackageState.primaryActionLabel(): String {
	return when (this) {
		UnifiedSourcePackageState.AVAILABLE -> stringResource(R.string.install_extension)
		UnifiedSourcePackageState.UPDATE_AVAILABLE -> stringResource(R.string.update_extension)
		UnifiedSourcePackageState.UNTRUSTED,
		UnifiedSourcePackageState.INCOMPATIBLE -> stringResource(R.string.details)
		UnifiedSourcePackageState.INSTALLING,
		UnifiedSourcePackageState.INSTALLED -> ""
	}
}

@Composable
private fun CompactActionChip(
	onClick: () -> Unit,
	label: @Composable () -> Unit,
	modifier: Modifier = Modifier,
) {
	AssistChip(
		onClick = onClick,
		modifier = modifier.defaultMinSize(minHeight = 30.dp),
		label = {
			Box(modifier = Modifier.padding(horizontal = 2.dp)) {
				label()
			}
		},
	)
}

@Composable
private fun CompactTag(
	text: String,
	isWarning: Boolean = false,
) {
	Surface(
		shape = RoundedCornerShape(4.dp),
		color = if (isWarning) {
			MaterialTheme.colorScheme.errorContainer
		} else {
			MaterialTheme.colorScheme.secondaryContainer
		},
		contentColor = if (isWarning) {
			MaterialTheme.colorScheme.onErrorContainer
		} else {
			MaterialTheme.colorScheme.onSecondaryContainer
		},
	) {
		Text(
			text = text,
			modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
			style = MaterialTheme.typography.labelSmall,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
		)
	}
}

@Composable
private fun UnifiedSourceKind.displayLabel(): String {
	return stringResource(labelResId())
}

private fun UnifiedSourceKind.labelResId(): Int {
	return when (this) {
		UnifiedSourceKind.NATIVE -> R.string.source_type_native
		UnifiedSourceKind.JAR -> R.string.source_type_jar
		UnifiedSourceKind.MIHON -> R.string.source_type_mihon
		UnifiedSourceKind.ANIYOMI -> R.string.source_type_aniyomi
		UnifiedSourceKind.IREADER -> R.string.source_type_ireader
		UnifiedSourceKind.LEGADO -> R.string.source_type_legado
		UnifiedSourceKind.TVBOX -> R.string.source_type_tvbox
		UnifiedSourceKind.JS -> R.string.source_type_js
		UnifiedSourceKind.LNREADER -> R.string.source_type_lnreader
	}
}

private fun UnifiedSourceKind.dialogLabelResId(): Int {
	return when (this) {
		UnifiedSourceKind.NATIVE -> R.string.source_type_builtin_short
		UnifiedSourceKind.JAR -> R.string.source_type_jar
		UnifiedSourceKind.MIHON -> R.string.source_type_mihon_apk
		UnifiedSourceKind.ANIYOMI -> R.string.source_type_aniyomi_apk
		UnifiedSourceKind.IREADER -> R.string.source_type_ireader_apk
		UnifiedSourceKind.LEGADO -> R.string.source_type_legado_json
		UnifiedSourceKind.TVBOX -> R.string.source_type_tvbox_json
		UnifiedSourceKind.JS -> R.string.source_type_js_source
		UnifiedSourceKind.LNREADER -> R.string.source_type_lnreader
	}
}

@Composable
private fun UnifiedRepositoryLocationType.displayLabel(): String {
	return when (this) {
		UnifiedRepositoryLocationType.REMOTE_URL -> stringResource(R.string.remote_url)
		UnifiedRepositoryLocationType.LOCAL_FILE -> stringResource(R.string.local_file)
		UnifiedRepositoryLocationType.INLINE_IMPORT -> stringResource(R.string.repository_location_inline)
		UnifiedRepositoryLocationType.PRESET_ONLY -> stringResource(R.string.repository_location_preset)
	}
}

@Composable
private fun String.displayLanguageLabel(): String {
	if (isBlank()) return stringResource(R.string.all)
	val locale = toLocaleOrNull() ?: Locale.forLanguageTag(this)
	return locale.getDisplayName(LocalContext.current).ifBlank { uppercase() }
}

@Composable
private fun UnifiedEnabledFilter.displayLabel(): String {
	return when (this) {
		UnifiedEnabledFilter.ALL -> stringResource(R.string.all)
		UnifiedEnabledFilter.ENABLED -> stringResource(R.string.enabled)
		UnifiedEnabledFilter.DISABLED -> stringResource(R.string.disabled)
	}
}

private fun UnifiedSourcesFilterState.otherFilterCount(): Int {
	return locationTypes.size + if (enabledFilter == UnifiedEnabledFilter.ALL) 0 else 1
}

private val topBarContentTypes = linkedSetOf(
	ContentType.MANGA,
	ContentType.NOVEL,
	ContentType.VIDEO,
)

private fun Set<ContentType>.primaryContentType(): ContentType? {
	return singleOrNull { it in topBarContentTypes }
}
