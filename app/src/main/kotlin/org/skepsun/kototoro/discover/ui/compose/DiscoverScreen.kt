package org.skepsun.kototoro.discover.ui.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.observeAsState
import org.skepsun.kototoro.core.ui.compose.KototoroLoadingIndicator
import org.skepsun.kototoro.core.ui.compose.KototoroPullToRefreshBox
import org.skepsun.kototoro.discover.ui.model.DiscoverCarouselRow
import org.skepsun.kototoro.list.ui.compose.KototoroContentCard
import org.skepsun.kototoro.list.ui.compose.rememberContentCardUiPrefs
import org.skepsun.kototoro.list.ui.model.ContentListModel
import org.skepsun.kototoro.list.ui.model.EmptyState
import org.skepsun.kototoro.list.ui.model.ListModel
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService
import org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
	contentPadding: PaddingValues = PaddingValues(0.dp),
	items: List<ListModel>,
	isRefreshing: Boolean,
	isCarousel: Boolean,
	isLoadingOnly: Boolean,
	activeService: ScrobblerService? = null,
	availableServices: List<ScrobblerService> = emptyList(),
	onRefresh: () -> Unit,
	onLoadMore: () -> Unit,
	onItemClick: (ContentListModel, Rect?) -> Unit,
	onSelectService: (ScrobblerService) -> Unit = {},
	onCategoryMoreClick: (TrackingSiteCategory) -> Unit,
	gridSpanCount: Int,
	modifier: Modifier = Modifier
) {
	if (isLoadingOnly) {
		Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
			KototoroLoadingIndicator()
		}
		return
	}
	val context = LocalContext.current
	val settings = remember(context.applicationContext) { AppSettings(context.applicationContext) }
	val gridScale = settings.observeAsState(AppSettings.KEY_GRID_SIZE) { gridSize / 100f }.value
	val cardUiPrefs = rememberContentCardUiPrefs(settings)

	KototoroPullToRefreshBox(
		isRefreshing = isRefreshing,
		onRefresh = onRefresh,
		modifier = modifier.fillMaxSize()
	) {
		val carouselRows = remember(items) { items.filterIsInstance<DiscoverCarouselRow>() }
		val emptyState = remember(items) { items.filterIsInstance<EmptyState>().firstOrNull() }

		if (isCarousel) {
			val heroRow = remember(carouselRows) {
				carouselRows.firstOrNull { row -> row.items.any { it is ContentListModel } }
			}
			val heroItems = remember(heroRow) {
				heroRow
					?.items
					?.filterIsInstance<ContentListModel>()
					?.take(6)
					.orEmpty()
			}

			if (carouselRows.isEmpty() && emptyState != null) {
				DiscoverEmptyState(
					state = emptyState,
					contentPadding = contentPadding,
					activeService = activeService,
					availableServices = availableServices,
					onSelectService = onSelectService,
				)
				return@KototoroPullToRefreshBox
			}

			val listState = rememberLazyListState()
			LazyColumn(
				state = listState,
				contentPadding = PaddingValues(
					top = contentPadding.calculateTopPadding() + 8.dp,
					bottom = contentPadding.calculateBottomPadding() + 8.dp
				),
				modifier = Modifier.fillMaxSize()
			) {
				if (heroItems.isNotEmpty() && heroRow != null) {
					item(key = "discover_hero") {
						DiscoverHeroCarousel(
							title = stringResource(heroRow.category.nameResId),
							items = heroItems,
							activeService = activeService,
							availableServices = availableServices,
							onItemClick = { item, coverBounds, sharedElementKey ->
								onItemClick(item, coverBounds)
							},
							onSelectService = onSelectService,
							modifier = Modifier.padding(bottom = 4.dp)
						)
					}
				}
				items(
					items = carouselRows,
					key = { it.category.id }
				) { row ->
					DiscoverCarousel(
						row = row,
						gridScale = gridScale,
						badgesBottomRight = cardUiPrefs.badgesBottomRight,
						onItemClick = onItemClick,
						onMoreClick = onCategoryMoreClick
					)
				}
			}
		} else {
			val gridState = rememberLazyGridState()
			val gridItems = remember(items) { items.filterIsInstance<ContentListModel>() }

			// Trigger pagination threshold for grid
			val shouldLoadMore by remember(gridState, gridSpanCount, gridItems.size) {
				derivedStateOf {
					if (gridItems.isEmpty()) {
						return@derivedStateOf false
					}
					val layoutInfo = gridState.layoutInfo
					val totalVisibleItems = layoutInfo.visibleItemsInfo.size
					val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
					lastVisibleItemIndex >= layoutInfo.totalItemsCount - 4 * gridSpanCount && totalVisibleItems > 0
				}
			}

			LaunchedEffect(shouldLoadMore) {
				if (shouldLoadMore && !isRefreshing) {
					onLoadMore()
				}
			}

			if (gridItems.isEmpty() && emptyState != null) {
				DiscoverEmptyState(
					state = emptyState,
					contentPadding = contentPadding,
					activeService = activeService,
					availableServices = availableServices,
					onSelectService = onSelectService,
				)
				return@KototoroPullToRefreshBox
			}

			LazyVerticalGrid(
				columns = GridCells.Fixed(gridSpanCount),
				state = gridState,
				contentPadding = PaddingValues(
					start = 16.dp,
					end = 16.dp,
					top = contentPadding.calculateTopPadding() + 16.dp,
					bottom = contentPadding.calculateBottomPadding() + 16.dp
				),
				modifier = Modifier.fillMaxSize()
			) {
				items(
					items = gridItems,
					key = { it.manga.id }
				) { item ->
					KototoroContentCard(
						model = item,
						isListLayout = false,
						onClick = { coverBounds -> onItemClick(item, coverBounds) },
						onLongClick = { },
						isSelected = false,
						selectionModeActive = false,
						uiPrefs = cardUiPrefs,
					)
				}
			}
		}
	}
}

@Composable
private fun DiscoverEmptyState(
	state: EmptyState,
	contentPadding: PaddingValues,
	activeService: ScrobblerService? = null,
	availableServices: List<ScrobblerService> = emptyList(),
	onSelectService: (ScrobblerService) -> Unit = {},
) {
	Box(
		modifier = Modifier
			.fillMaxSize()
			.padding(
				start = 24.dp,
				end = 24.dp,
				top = contentPadding.calculateTopPadding() + 24.dp,
				bottom = contentPadding.calculateBottomPadding() + 24.dp,
			),
		contentAlignment = Alignment.Center,
	) {
		Column(
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(12.dp),
		) {
			Image(
				painter = painterResource(state.icon),
				contentDescription = null,
				modifier = Modifier.align(Alignment.CenterHorizontally),
			)
			androidx.compose.material3.Text(
				text = stringResource(state.textPrimary),
				style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
				textAlign = TextAlign.Center,
			)
			androidx.compose.material3.Text(
				text = stringResource(state.textSecondary),
				style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
				color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
				textAlign = TextAlign.Center,
			)
			if (activeService != null && availableServices.size > 1) {
				DiscoverServiceSwitcherChip(
					activeService = activeService,
					availableServices = availableServices,
					onSelectService = onSelectService,
				)
			}
		}
	}
}

@Composable
private fun DiscoverServiceSwitcherChip(
	activeService: ScrobblerService,
	availableServices: List<ScrobblerService>,
	onSelectService: (ScrobblerService) -> Unit,
) {
	var expanded by remember { mutableStateOf(false) }
	Box {
		androidx.compose.material3.AssistChip(
			onClick = { expanded = true },
			leadingIcon = {
				androidx.compose.material3.Icon(
					painter = painterResource(activeService.iconResId),
					contentDescription = null,
					modifier = Modifier.padding(end = 2.dp),
				)
			},
			trailingIcon = {
				androidx.compose.material3.Icon(
					imageVector = Icons.Filled.ArrowDropDown,
					contentDescription = null,
				)
			},
			label = { androidx.compose.material3.Text(stringResource(activeService.titleResId)) },
		)
		androidx.compose.material3.DropdownMenu(
			expanded = expanded,
			onDismissRequest = { expanded = false },
		) {
			availableServices.forEach { candidate ->
				androidx.compose.material3.DropdownMenuItem(
					text = { androidx.compose.material3.Text(stringResource(candidate.titleResId)) },
					leadingIcon = {
						androidx.compose.material3.Icon(
							painter = painterResource(candidate.iconResId),
							contentDescription = null,
						)
					},
					onClick = {
						expanded = false
						onSelectService(candidate)
					},
				)
			}
		}
	}
}
