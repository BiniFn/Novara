package org.skepsun.kototoro.discover.ui.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.request.ImageRequest
import kotlinx.coroutines.launch
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.iconResId
import org.skepsun.kototoro.core.model.titleResId
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.ui.glass.GlassDefaults
import org.skepsun.kototoro.core.ui.glass.GlassSurface
import org.skepsun.kototoro.details.ui.compose.AnimatedPanoramaBackdrop
import org.skepsun.kototoro.details.ui.compose.DetailsChromeButton
import org.skepsun.kototoro.details.ui.compose.DetailsCoverFrame
import org.skepsun.kototoro.details.ui.compose.DetailsHeaderIconButton
import org.skepsun.kototoro.details.ui.compose.DetailsHeroBadge
import org.skepsun.kototoro.details.ui.compose.MetadataItem
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblingStatus

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TrackingSiteDetailsScreen(
    viewModel: TrackingSiteDetailsViewModel,
    settings: AppSettings,
    appRouter: AppRouter,
    onBackClick: () -> Unit,
) {
    val details by viewModel.details.collectAsState()
    val linkedContent by viewModel.linkedContent.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val scrobblingEntity by viewModel.scrobblingEntity.collectAsState()

    val cachedTranslatedTitle by viewModel.cachedTranslatedTitle.collectAsState()
    val cachedTranslatedDescription by viewModel.cachedTranslatedDescription.collectAsState()
    val isShowingTranslation by viewModel.isShowingTranslation.collectAsState()
    val isTranslating by viewModel.isTranslating.collectAsState()
    val hasTranslationCache by viewModel.hasTranslationCache.collectAsState()
    val translatedTitle by viewModel.translatedTitle.collectAsState()
    val translatedDescription by viewModel.translatedDescription.collectAsState()

    val availableLocalSources by viewModel.availableLocalSources.collectAsState()
    val selectedLocalSource by viewModel.selectedLocalSourceName.collectAsState()
    val isLocalSearchVisible by viewModel.isLocalSearchVisible.collectAsState()
    val localSearchResults by viewModel.localSearchResults.collectAsState()

    val isTrackerSearchVisible by viewModel.isTrackerSearchVisible.collectAsState()
    val alternativeTrackers by viewModel.alternativeTrackers.collectAsState()
    val isAltTrackersLoading by viewModel.isAlternativeTrackersLoading.collectAsState()

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    val context = LocalContext.current
    val rootView = LocalView.current
    val density = LocalDensity.current
    val toolbarGapPx = with(density) { 12.dp.toPx() }
    var toolbarBottomPx by remember { mutableFloatStateOf(Float.NaN) }
    var infoCardTopPx by remember { mutableFloatStateOf(Float.NaN) }
    var initialInfoCardTopPx by remember { mutableFloatStateOf(Float.NaN) }

    androidx.compose.runtime.LaunchedEffect(infoCardTopPx) {
        if (infoCardTopPx.isFinite() && (!initialInfoCardTopPx.isFinite() || infoCardTopPx > initialInfoCardTopPx)) {
            initialInfoCardTopPx = infoCardTopPx
        }
    }
    val collapseProgress by remember(
        scrollState, toolbarBottomPx, infoCardTopPx, initialInfoCardTopPx, toolbarGapPx,
    ) {
        derivedStateOf {
            val targetTop = toolbarBottomPx + toolbarGapPx
            if (toolbarBottomPx.isFinite() && infoCardTopPx.isFinite() && initialInfoCardTopPx.isFinite()) {
                val travelDistance = (initialInfoCardTopPx - targetTop).coerceAtLeast(1f)
                ((initialInfoCardTopPx - infoCardTopPx) / travelDistance).coerceIn(0f, 1f)
            } else {
                (scrollState.value / 360f).coerceIn(0f, 1f)
            }
        }
    }
    val coverCollapseProgress = (collapseProgress / 0.48f).coerceIn(0f, 1f)
    val coverAlpha = 1f - coverCollapseProgress
    val textCollapseProgress = ((collapseProgress - 0.08f) / 0.44f).coerceIn(0f, 1f)
    val actionsCollapseProgress = ((collapseProgress - 0.18f) / 0.36f).coerceIn(0f, 1f)

    Box(modifier = Modifier.fillMaxSize()) {
        if (settings.isPanoramaCoverEnabled) {
            val coverUrl = details?.coverUrl
            val request = remember(coverUrl) {
                ImageRequest.Builder(context).data(coverUrl).build()
            }
            AnimatedPanoramaBackdrop(
                settings = settings,
                model = request,
                contentAlpha = 0.6f * (1f - collapseProgress),
                backgroundColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                val displayTitle = translatedTitle ?: details?.title ?: stringResource(R.string.details)
                TopAppBar(
                    title = {
                        Text(
                            text = displayTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        DetailsChromeButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f * collapseProgress),
                    ),
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        toolbarBottomPx = coordinates.boundsInRoot().bottom
                    },
                )
            },
            bottomBar = {
                TrackingBottomBar(
                    hasLinkedContent = linkedContent != null,
                    hasExternalUrl = !details?.url.isNullOrBlank(),
                    canTranslate = details != null,
                    isShowingTranslation = isShowingTranslation,
                    isTranslating = isTranslating,
                    onOpenLinked = { linkedContent?.let { appRouter.openDetails(it, rootView) } },
                    onOpenLinkedTab = { tab ->
                        // Currently opens the local details regardless of tab, but provides the UI mirroring requested
                        linkedContent?.let { appRouter.openDetails(it, rootView) }
                    },
                    onManageBinding = {
                        val d = details ?: return@TrackingBottomBar
                        appRouter.openScrobblerBinding(d.service, d.remoteId, d.title, d.url)
                    },
                    onBrowseSources = { viewModel.setLocalSearchVisible(true) },
                    onOpenExternal = {
                        details?.url?.let { appRouter.openExternalBrowser(it) }
                    },
                    onToggleTranslation = {
                        if (hasTranslationCache) {
                            viewModel.toggleTranslationDisplay()
                        } else {
                            viewModel.translateTitleAndDescription()
                        }
                    },
                )
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(scrollState),
            ) {
                val currentDetails = details
                if (currentDetails != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        DetailsCoverFrame(
                            coverModel = currentDetails.coverUrl,
                            contentDescription = currentDetails.title,
                            onCoverBoundsSync = { _, _ -> },
                            syncAlpha = coverAlpha,
                            showNsfwBadge = false,
                            onClick = null,
                            modifier = Modifier.graphicsLayer {
                                alpha = coverAlpha
                                translationX = -48f * coverCollapseProgress
                            },
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer {
                                    alpha = 1f - textCollapseProgress
                                    translationX = -32f * textCollapseProgress
                                },
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Text(
                                text = translatedTitle ?: currentDetails.title,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!currentDetails.altTitle.isNullOrBlank()) {
                                Text(
                                    text = currentDetails.altTitle!!,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                DetailsHeroBadge(
                                    text = stringResource(currentDetails.service.titleResId),
                                    iconRes = currentDetails.service.iconResId,
                                )
                                currentDetails.year?.let {
                                    DetailsHeroBadge(text = it.toString())
                                }
                                currentDetails.score?.takeIf { it > 0f }?.let {
                                    DetailsHeroBadge(text = "★ ${"%.1f".format(it)}")
                                }
                            }

                            Row(
                                modifier = Modifier.graphicsLayer {
                                    alpha = 1f - actionsCollapseProgress
                                    translationX = -18f * actionsCollapseProgress
                                },
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                DetailsHeaderIconButton(
                                    iconRes = R.drawable.ic_open_external,
                                    onClick = { currentDetails.url?.let(appRouter::openExternalBrowser) },
                                    enabled = currentDetails.url != null,
                                )
                                DetailsHeaderIconButton(
                                    iconRes = R.drawable.ic_translate,
                                    onClick = {
                                        if (hasTranslationCache) {
                                            viewModel.toggleTranslationDisplay()
                                        } else {
                                            viewModel.translateTitleAndDescription()
                                        }
                                    },
                                    enabled = !isTranslating,
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .onGloballyPositioned { coordinates ->
                            infoCardTopPx = coordinates.boundsInRoot().top
                        },
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SourceAndTrackerChipsRow(
                        linkedTitle = linkedContent?.title,
                        currentServiceTitle = details?.service?.let { stringResource(it.titleResId) }
                            ?: "",
                        currentServiceIconRes = details?.service?.iconResId ?: R.drawable.ic_storage,
                        isLocalExpanded = isLocalSearchVisible,
                        isTrackerExpanded = isTrackerSearchVisible,
                        onToggleLocal = { viewModel.setLocalSearchVisible(!isLocalSearchVisible) },
                        onToggleTracker = { viewModel.setTrackerSearchVisible(!isTrackerSearchVisible) },
                    )

                    AnimatedVisibility(visible = isLocalSearchVisible) {
                        TrackingLocalSourcesPanel(
                            availableSources = availableLocalSources,
                            selectedSourceName = selectedLocalSource,
                            results = localSearchResults,
                            linkedContent = linkedContent,
                            onSourceSelected = { viewModel.selectLocalSource(it) },
                            onCandidateClick = { candidate ->
                                coroutineScope.launch {
                                    viewModel.bindLocalContent(candidate)
                                    appRouter.openDetails(candidate, rootView)
                                }
                            },
                            onRetry = { viewModel.retryLocalSearch(it) },
                            onOpenLinked = { linkedContent?.let { appRouter.openDetails(it, rootView) } },
                            onUnlinkClick = {
                                val d = details ?: return@TrackingLocalSourcesPanel
                                appRouter.openScrobblerBinding(d.service, d.remoteId, d.title, d.url)
                            },
                        )
                    }

                    AnimatedVisibility(visible = isTrackerSearchVisible) {
                        TrackingAlternativeTrackersPanel(
                            mappings = alternativeTrackers,
                            isLoading = isAltTrackersLoading,
                            onMappingClick = { mapping ->
                                appRouter.openTrackingSiteDetails(mapping.service, mapping.remoteId, mapping.url)
                            },
                        )
                    }

                    val currentDetails = details
                    if (currentDetails != null) {
                        // --- Info Card (metadata grid) ---
                        TrackingInfoCard(
                            details = currentDetails,
                            scrobblingStatus = scrobblingEntity?.let { viewModel.resolveScrobblingStatus(it) },
                            scrobblingProgress = scrobblingEntity?.let {
                                if (it.chapter > 0) "${it.chapter}" + (currentDetails.totalEpisodes?.let { total -> " / $total" } ?: "") else null
                            },
                        )

                        TrackingSiteDetailsBody(
                            title = currentDetails.title,
                            description = translatedDescription ?: currentDetails.description,
                        )
                    } else if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (error != null) {
                        GlassSurface(
                            modifier = Modifier.fillMaxWidth(),
                            style = GlassDefaults.regularStyle(),
                        ) {
                            Text(
                                text = error?.localizedMessage ?: stringResource(R.string.retry),
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun SourceAndTrackerChipsRow(
    linkedTitle: String?,
    currentServiceTitle: String,
    currentServiceIconRes: Int,
    isLocalExpanded: Boolean,
    isTrackerExpanded: Boolean,
    onToggleLocal: () -> Unit,
    onToggleTracker: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ChipToggle(
            label = linkedTitle ?: stringResource(R.string.local_storage),
            iconRes = R.drawable.ic_storage,
            selected = isLocalExpanded,
            onClick = onToggleLocal,
            badge = if (linkedTitle != null) stringResource(R.string.discover_local_linked) else null,
            modifier = Modifier.weight(1f),
        )
        ChipToggle(
            label = currentServiceTitle,
            iconRes = currentServiceIconRes,
            selected = isTrackerExpanded,
            onClick = onToggleTracker,
            badge = null,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ChipToggle(
    label: String,
    iconRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
    badge: String?,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    }
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = bg,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (badge != null) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = if (selected) "▴" else "▾",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrackingInfoCard(
    details: org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteItemDetails,
    scrobblingStatus: ScrobblingStatus?,
    scrobblingProgress: String?,
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        style = GlassDefaults.subtleStyle(),
        shape = RoundedCornerShape(24.dp),
    ) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            maxItemsInEachRow = 3,
        ) {
            MetadataItem(
                label = stringResource(R.string.tracking_source),
                value = stringResource(details.service.titleResId),
                iconRes = details.service.iconResId,
                modifier = Modifier.weight(1f),
            )
            if (details.authors.isNotEmpty()) {
                MetadataItem(
                    label = stringResource(R.string.author),
                    value = details.authors.joinToString(", "),
                    modifier = Modifier.weight(1f),
                )
            }
            details.score?.takeIf { it > 0f }?.let { score ->
                MetadataItem(
                    label = stringResource(R.string.score),
                    value = "★ ${"%.1f".format(score)}",
                    modifier = Modifier.weight(1f),
                )
            }
            scrobblingStatus?.let { status ->
                val statusText = when (status) {
                    ScrobblingStatus.PLANNED -> stringResource(R.string.status_planned)
                    ScrobblingStatus.READING -> stringResource(R.string.status_reading)
                    ScrobblingStatus.RE_READING -> stringResource(R.string.status_re_reading)
                    ScrobblingStatus.COMPLETED -> stringResource(R.string.status_completed)
                    ScrobblingStatus.ON_HOLD -> stringResource(R.string.status_on_hold)
                    ScrobblingStatus.DROPPED -> stringResource(R.string.status_dropped)
                }
                MetadataItem(
                    label = stringResource(R.string.status),
                    value = statusText,
                    modifier = Modifier.weight(1f),
                )
            }
            scrobblingProgress?.let { progress ->
                MetadataItem(
                    label = stringResource(R.string.chapters),
                    value = progress,
                    modifier = Modifier.weight(1f),
                )
            }
            details.year?.let { year ->
                MetadataItem(
                    label = stringResource(R.string.year),
                    value = year.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
            details.totalEpisodes?.let { total ->
                MetadataItem(
                    label = stringResource(R.string.total_chapters),
                    value = total.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TrackingSiteDetailsBody(
    title: String,
    description: String?,
) {
    if (description.isNullOrBlank()) return
    // No card container — render description as plain text
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.description),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        androidx.compose.foundation.text.selection.SelectionContainer {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
