package org.skepsun.kototoro.settings.sources.unified

import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.getSummary
import org.skepsun.kototoro.core.model.titleResId
import org.skepsun.kototoro.core.ui.compose.ContentSourceIcon
import org.skepsun.kototoro.core.util.ext.getDisplayName
import org.skepsun.kototoro.core.util.ext.toLocaleOrNull
import org.skepsun.kototoro.parsers.model.ContentType
import java.util.Locale

private enum class UnifiedToolbarFilterPanel {
	LANGUAGE,
	MORE,
}

@Composable
fun UnifiedSourcesRoute(
	onBack: () -> Unit,
	onBrowseSource: (UnifiedSourceItem) -> Unit,
	onOpenSourceSettings: (UnifiedSourceItem) -> Unit,
	onAddRepository: (UnifiedSourceRepositoryItem?) -> Unit,
	onRefreshRepository: (UnifiedSourceRepositoryItem) -> Unit,
	onDeleteRepository: (UnifiedSourceRepositoryItem) -> Unit,
	onImportLocalJar: () -> Unit,
	modifier: Modifier = Modifier,
	viewModel: UnifiedSourcesViewModel = hiltViewModel(),
) {
	val state by viewModel.uiState.collectAsStateWithLifecycle()
	val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
	val updateAllInProgress by viewModel.updateAllInProgress.collectAsStateWithLifecycle()
	UnifiedSourcesScreen(
		state = state,
		isLoading = isLoading,
		updateAllInProgress = updateAllInProgress,
		onBack = onBack,
		onSearchQueryChange = viewModel::setSearchQuery,
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
		onAddRepository = onAddRepository,
		onRefreshRepository = onRefreshRepository,
		onDeleteRepository = onDeleteRepository,
		onRefreshPackages = { viewModel.refreshPackages() },
		onUpdateAllPackages = viewModel::onUpdateAllPackagesAction,
		onPackagePrimaryAction = viewModel::onPackagePrimaryAction,
		onPackageUninstall = viewModel::uninstallPackage,
		onPackageCancelInstall = viewModel::cancelPackageInstall,
		onImportLocalJar = onImportLocalJar,
		modifier = modifier,
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
					contentDescription = "Close search",
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
			contentDescription = "Language filters",
			onClick = { activePanel = UnifiedToolbarFilterPanel.LANGUAGE },
		)
		ToolbarFilterIconButton(
			iconRes = R.drawable.ic_filter_menu,
			activeCount = state.filters.otherFilterCount(),
			contentDescription = "More filters",
			onClick = { activePanel = UnifiedToolbarFilterPanel.MORE },
		)
	}

	when (activePanel) {
		UnifiedToolbarFilterPanel.LANGUAGE -> UnifiedFilterGroupDialog(
			title = "Language",
			onDismiss = { activePanel = null },
			onClear = onClearFilters,
		) {
			FilterSection(title = "Language") {
				items(state.availableLanguages) { language ->
					CompactFilterChip(
						selected = language in state.filters.languages,
						onClick = { onLanguageClick(language) },
						text = language.displayLanguageLabel(),
					)
				}
			}
		}
		UnifiedToolbarFilterPanel.MORE -> UnifiedFilterGroupDialog(
			title = "More filters",
			onDismiss = { activePanel = null },
			onClear = onClearFilters,
		) {
			FilterSection(title = "Status") {
				items(UnifiedEnabledFilter.entries) { filter ->
					CompactFilterChip(
						selected = state.filters.enabledFilter == filter,
						onClick = { onEnabledFilterClick(filter) },
						text = filter.displayLabel(),
					)
				}
			}
			FilterSection(title = "Repository source") {
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
								text = "Search",
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
				contentDescription = "Search",
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
				Text("Done")
			}
		},
		dismissButton = {
			TextButton(onClick = onClear) {
				Text("Clear")
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
	onSearchQueryChange: (String) -> Unit,
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
					UnifiedSourcesFilterTabs(
						state = state,
						onContentTypeClick = onContentTypeClick,
						onKindClick = onKindClick,
					)
					TabRow(selectedTabIndex = selectedTab) {
						Tab(
							selected = selectedTab == 0,
							onClick = { selectedTab = 0 },
							text = { Text("Sources (${state.sources.size})") },
						)
						Tab(
							selected = selectedTab == 1,
							onClick = { selectedTab = 1 },
							text = { Text("Repositories (${state.repositories.size})") },
						)
						Tab(
							selected = selectedTab == 2,
							onClick = { selectedTab = 2 },
							text = { Text("Packages (${state.packages.size})") },
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
					text = "All content",
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
					text = "All sources",
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
private fun UnifiedSourcesCompactFilters(
	state: UnifiedSourcesUiState.Ready,
	onSearchQueryChange: (String) -> Unit,
	onKindClick: (UnifiedSourceKind) -> Unit,
	onContentTypeClick: (org.skepsun.kototoro.parsers.model.ContentType) -> Unit,
	onLocationTypeClick: (UnifiedRepositoryLocationType) -> Unit,
	onLanguageClick: (String) -> Unit,
	onEnabledFilterClick: (UnifiedEnabledFilter) -> Unit,
	onClearFilters: () -> Unit,
) {
	var filtersExpanded by rememberSaveable { mutableStateOf(false) }
	val activeFilterCount = state.filters.activeFilterCount()
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 12.dp, vertical = 6.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(8.dp),
	) {
		OutlinedTextField(
			value = state.filters.query,
			onValueChange = onSearchQueryChange,
			modifier = Modifier.weight(1f),
			singleLine = true,
			label = { Text("Search") },
		)
		AssistChip(
			onClick = { filtersExpanded = true },
			label = {
				Text(
					text = if (activeFilterCount == 0) "Filters" else "Filters ($activeFilterCount)",
					style = MaterialTheme.typography.labelSmall,
				)
			},
			leadingIcon = {
				Icon(
					painter = painterResource(R.drawable.ic_filter_menu),
					contentDescription = null,
					modifier = Modifier.size(18.dp),
				)
			},
		)
	}
	if (filtersExpanded) {
		UnifiedFiltersDialog(
			state = state,
			onDismiss = { filtersExpanded = false },
			onKindClick = onKindClick,
			onContentTypeClick = onContentTypeClick,
			onLocationTypeClick = onLocationTypeClick,
			onLanguageClick = onLanguageClick,
			onEnabledFilterClick = onEnabledFilterClick,
			onClearFilters = onClearFilters,
		)
	}
}

@Composable
private fun UnifiedFiltersDialog(
	state: UnifiedSourcesUiState.Ready,
	onDismiss: () -> Unit,
	onKindClick: (UnifiedSourceKind) -> Unit,
	onContentTypeClick: (org.skepsun.kototoro.parsers.model.ContentType) -> Unit,
	onLocationTypeClick: (UnifiedRepositoryLocationType) -> Unit,
	onLanguageClick: (String) -> Unit,
	onEnabledFilterClick: (UnifiedEnabledFilter) -> Unit,
	onClearFilters: () -> Unit,
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text("Filters") },
		text = {
			Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
				FilterSection(title = "Source type") {
					items(state.availableKinds) { kind ->
						CompactFilterChip(
							selected = kind in state.filters.kinds,
							onClick = { onKindClick(kind) },
							text = kind.displayLabel(),
						)
					}
				}
				FilterSection(title = "Content type") {
					items(state.availableContentTypes) { type ->
						CompactFilterChip(
							selected = type in state.filters.contentTypes,
							onClick = { onContentTypeClick(type) },
							text = stringResource(type.titleResId),
						)
					}
				}
				FilterSection(title = "Language") {
					items(state.availableLanguages) { language ->
						CompactFilterChip(
							selected = language in state.filters.languages,
							onClick = { onLanguageClick(language) },
							text = language.displayLanguageLabel(),
						)
					}
				}
				FilterSection(title = "Status") {
					items(UnifiedEnabledFilter.entries) { filter ->
						CompactFilterChip(
							selected = state.filters.enabledFilter == filter,
							onClick = { onEnabledFilterClick(filter) },
							text = filter.displayLabel(),
						)
					}
				}
				FilterSection(title = "Repository source") {
					items(state.availableLocationTypes) { type ->
						CompactFilterChip(
							selected = type in state.filters.locationTypes,
							onClick = { onLocationTypeClick(type) },
							text = type.displayLabel(),
						)
					}
				}
			}
		},
		confirmButton = {
			TextButton(onClick = onDismiss) {
				Text("Done")
			}
		},
		dismissButton = {
			TextButton(onClick = onClearFilters) {
				Text("Clear")
			}
		},
	)
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
private fun UnifiedSourcesFilters(
	state: UnifiedSourcesUiState.Ready,
	onSearchQueryChange: (String) -> Unit,
	onKindClick: (UnifiedSourceKind) -> Unit,
	onContentTypeClick: (org.skepsun.kototoro.parsers.model.ContentType) -> Unit,
	onLocationTypeClick: (UnifiedRepositoryLocationType) -> Unit,
	onLanguageClick: (String) -> Unit,
	onEnabledFilterClick: (UnifiedEnabledFilter) -> Unit,
	onClearFilters: () -> Unit,
) {
	Column(
		modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
		verticalArrangement = Arrangement.spacedBy(4.dp),
	) {
		OutlinedTextField(
			value = state.filters.query,
			onValueChange = onSearchQueryChange,
			modifier = Modifier.fillMaxWidth(),
			singleLine = true,
			label = { Text("Search sources, packages, repositories") },
		)
		LazyRow(
			horizontalArrangement = Arrangement.spacedBy(6.dp),
			contentPadding = PaddingValues(horizontal = 2.dp),
		) {
			items(state.availableKinds) { kind ->
				CompactFilterChip(
					selected = kind in state.filters.kinds,
					onClick = { onKindClick(kind) },
					text = "源 ${kind.displayLabel()}",
				)
			}
			items(state.availableContentTypes) { type ->
				CompactFilterChip(
					selected = type in state.filters.contentTypes,
					onClick = { onContentTypeClick(type) },
					text = "内容 ${stringResource(type.titleResId)}",
				)
			}
			items(state.availableLanguages) { language ->
				CompactFilterChip(
					selected = language in state.filters.languages,
					onClick = { onLanguageClick(language) },
					text = "语言 ${language.displayLanguageLabel()}",
				)
			}
			items(UnifiedEnabledFilter.entries) { filter ->
				CompactFilterChip(
					selected = state.filters.enabledFilter == filter,
					onClick = { onEnabledFilterClick(filter) },
					text = "状态 ${filter.displayLabel()}",
				)
			}
			items(state.availableLocationTypes) { type ->
				CompactFilterChip(
					selected = type in state.filters.locationTypes,
					onClick = { onLocationTypeClick(type) },
					text = "仓库 ${type.displayLabel()}",
				)
			}
			item {
				AssistChip(
					onClick = onClearFilters,
					modifier = Modifier.defaultMinSize(minHeight = 30.dp),
					label = {
						Text(
							text = "清除",
							style = MaterialTheme.typography.labelSmall,
						)
					},
				)
			}
		}
	}
}

@Composable
private fun CompactFilterChip(
	selected: Boolean,
	onClick: () -> Unit,
	text: String,
) {
	FilterChip(
		selected = selected,
		onClick = onClick,
		modifier = Modifier.defaultMinSize(minHeight = 30.dp),
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
					CompactTag(text = "Unavailable", isWarning = true)
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
					CompactTag(text = "NSFW", isWarning = true)
				}
			}
		}
		Box {
			IconButton(onClick = { menuExpanded = true }) {
				Icon(
					painter = painterResource(R.drawable.ic_more_vert),
					contentDescription = "More",
				)
			}
			DropdownMenu(
				expanded = menuExpanded,
				onDismissRequest = { menuExpanded = false },
			) {
				DropdownMenuItem(
					text = { Text("Browse") },
					onClick = {
						menuExpanded = false
						onBrowseSource(item)
					},
				)
				DropdownMenuItem(
					text = { Text(if (item.isPinned) "Unpin" else "Pin") },
					onClick = {
						menuExpanded = false
						onSourcePinnedChange(item.id, !item.isPinned)
					},
				)
				DropdownMenuItem(
					text = { Text("Settings") },
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
					label = { Text("Add URL, file, or pasted repository") },
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
								label = { Text("Refresh") },
							)
							AssistChip(
								onClick = { onDeleteRepository(item) },
								label = { Text("Delete") },
							)
						} else if (item.isPreset) {
							AssistChip(
								onClick = { onAddRepository(item) },
								label = { Text("Add") },
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
		contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
		verticalArrangement = Arrangement.spacedBy(6.dp),
	) {
		item(key = "package_actions") {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(bottom = 4.dp),
				horizontalArrangement = Arrangement.spacedBy(8.dp),
			) {
				AssistChip(
					onClick = onRefreshPackages,
					label = { Text("Refresh packages") },
				)
				AssistChip(
					onClick = onUpdateAllPackages,
					label = { Text(if (updateAllInProgress) "Cancel update all" else "Update all") },
				)
				AssistChip(
					onClick = onImportLocalJar,
					label = { Text("Import local JAR") },
				)
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
			modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
			verticalArrangement = Arrangement.spacedBy(5.dp),
		) {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(8.dp),
			) {
				Box(
					modifier = Modifier
						.size(34.dp)
						.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp)),
					contentAlignment = Alignment.Center,
				) {
					Icon(
						painter = painterResource(item.kind.packageIconRes()),
						contentDescription = null,
						modifier = Modifier.size(20.dp),
						tint = MaterialTheme.colorScheme.onSecondaryContainer,
					)
				}
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
					text = "Downloading ${item.installProgressPercent}%",
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
				item.repositoryName?.takeIf { it.isNotBlank() }?.let { CompactTag(it) }
				if (item.installedVersionName != null && item.state == UnifiedSourcePackageState.UPDATE_AVAILABLE) {
					CompactTag("Installed ${item.installedVersionName}")
				}
				if (item.isNsfw) {
					CompactTag("NSFW", isWarning = true)
				}
			}
			if (item.sourceNames.isNotEmpty()) {
				Text(
					text = item.sourceNames.take(8).joinToString(", "),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 2,
					overflow = TextOverflow.Ellipsis,
				)
			}
			Row(
				horizontalArrangement = Arrangement.spacedBy(8.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				when (item.state) {
					UnifiedSourcePackageState.AVAILABLE,
					UnifiedSourcePackageState.UPDATE_AVAILABLE,
					UnifiedSourcePackageState.UNTRUSTED,
					UnifiedSourcePackageState.INCOMPATIBLE -> {
						AssistChip(
							onClick = onPrimaryAction,
							label = { Text(item.state.primaryActionLabel()) },
						)
					}
					UnifiedSourcePackageState.INSTALLING -> {
						AssistChip(
							onClick = onCancelInstall,
							label = { Text("Cancel") },
						)
					}
					UnifiedSourcePackageState.INSTALLED -> Unit
				}
				if (item.isInstalled) {
					AssistChip(
						onClick = onUninstall,
						label = { Text("Uninstall") },
					)
				}
			}
		}
	}
}

private fun buildSourceSubtitle(item: UnifiedSourceItem): String {
	return listOfNotNull(
		item.kind.displayLabel(),
		item.contentType.name.lowercase().replaceFirstChar { it.titlecase() },
		item.language,
		item.repositoryName,
		item.packageName,
		"enabled=${item.isEnabled}",
	).joinToString(" · ")
}

private fun buildPackageSubtitle(item: UnifiedSourcePackageItem): String {
	return listOfNotNull(
		item.kind.displayLabel(),
		item.versionName?.let { "v$it" },
		item.language,
		item.repositoryName,
		"${item.sourceCount} source(s)",
	).joinToString(" · ")
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

private fun UnifiedSourcePackageState.displayLabel(): String {
	return when (this) {
		UnifiedSourcePackageState.AVAILABLE -> "Available"
		UnifiedSourcePackageState.UPDATE_AVAILABLE -> "Update"
		UnifiedSourcePackageState.INSTALLED -> "Installed"
		UnifiedSourcePackageState.INSTALLING -> "Installing"
		UnifiedSourcePackageState.UNTRUSTED -> "Untrusted"
		UnifiedSourcePackageState.INCOMPATIBLE -> "Incompatible"
	}
}

private val UnifiedSourcePackageState.isWarning: Boolean
	get() = this == UnifiedSourcePackageState.UNTRUSTED || this == UnifiedSourcePackageState.INCOMPATIBLE

private fun UnifiedSourcePackageState.primaryActionLabel(): String {
	return when (this) {
		UnifiedSourcePackageState.AVAILABLE -> "Install"
		UnifiedSourcePackageState.UPDATE_AVAILABLE -> "Update"
		UnifiedSourcePackageState.UNTRUSTED,
		UnifiedSourcePackageState.INCOMPATIBLE -> "Details"
		UnifiedSourcePackageState.INSTALLING,
		UnifiedSourcePackageState.INSTALLED -> ""
	}
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

private fun UnifiedSourceKind.displayLabel(): String {
	return when (this) {
		UnifiedSourceKind.NATIVE -> "Native"
		UnifiedSourceKind.JAR -> "JAR"
		UnifiedSourceKind.MIHON -> "Mihon"
		UnifiedSourceKind.ANIYOMI -> "Aniyomi"
		UnifiedSourceKind.IREADER -> "IReader"
		UnifiedSourceKind.LEGADO -> "Legado"
		UnifiedSourceKind.TVBOX -> "TVBox"
		UnifiedSourceKind.JS -> "JS"
		UnifiedSourceKind.LNREADER -> "LNReader"
	}
}

private fun UnifiedRepositoryLocationType.displayLabel(): String {
	return when (this) {
		UnifiedRepositoryLocationType.REMOTE_URL -> "URL"
		UnifiedRepositoryLocationType.LOCAL_FILE -> "File"
		UnifiedRepositoryLocationType.INLINE_IMPORT -> "Inline"
		UnifiedRepositoryLocationType.PRESET_ONLY -> "Preset"
	}
}

@Composable
private fun String.displayLanguageLabel(): String {
	if (isBlank()) return "All"
	val locale = toLocaleOrNull() ?: Locale.forLanguageTag(this)
	return locale.getDisplayName(LocalContext.current).ifBlank { uppercase() }
}

private fun UnifiedEnabledFilter.displayLabel(): String {
	return when (this) {
		UnifiedEnabledFilter.ALL -> "All"
		UnifiedEnabledFilter.ENABLED -> "Enabled"
		UnifiedEnabledFilter.DISABLED -> "Disabled"
	}
}

private fun UnifiedSourcesFilterState.activeFilterCount(): Int {
	return kinds.size +
		contentTypes.size +
		languages.size +
		locationTypes.size +
		if (enabledFilter == UnifiedEnabledFilter.ALL) 0 else 1
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
