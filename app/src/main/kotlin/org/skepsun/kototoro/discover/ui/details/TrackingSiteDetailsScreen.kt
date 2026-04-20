package org.skepsun.kototoro.discover.ui.details

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.request.ImageRequest
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.ui.glass.GlassDefaults
import org.skepsun.kototoro.core.ui.glass.GlassSurface
import org.skepsun.kototoro.details.ui.compose.AnimatedPanoramaBackdrop
import org.skepsun.kototoro.details.ui.compose.DetailsBindingCard
import org.skepsun.kototoro.details.ui.compose.DetailsChromeButton
import org.skepsun.kototoro.details.ui.compose.DetailsHeader
import org.skepsun.kototoro.details.ui.model.HistoryInfo
import java.util.Locale

import org.skepsun.kototoro.details.ui.compose.DetailsCoverFrame
import org.skepsun.kototoro.details.ui.compose.DetailsHeaderIconButton
import org.skepsun.kototoro.details.ui.compose.DetailsHeroBadge
import org.skepsun.kototoro.details.ui.compose.MetadataItem
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.text.font.FontWeight
import org.skepsun.kototoro.core.model.iconResId
import org.skepsun.kototoro.core.model.titleResId

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
    val linkedTrackingItem by viewModel.linkedTrackingItem.collectAsState()
    val scrobblingEntity by viewModel.scrobblingEntity.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val scrollState = rememberScrollState()
    val title = details?.title ?: stringResource(R.string.details)
    val statuses = stringArrayResource(R.array.scrobbling_statuses)
    val defaultLocale = Locale.getDefault()

    val context = LocalContext.current
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
        scrollState,
        toolbarBottomPx,
        infoCardTopPx,
        initialInfoCardTopPx,
        toolbarGapPx,
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
            val request = remember(details) {
                ImageRequest.Builder(context)
                    .data(details?.coverUrl)
                    .build()
            }
            AnimatedPanoramaBackdrop(
                settings = settings,
                model = request,
                contentAlpha = 0.6f * (1f - collapseProgress),
                backgroundColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxSize()
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    DetailsChromeButton(onClick = onBackClick) {
                        androidx.compose.material3.Icon(
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
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .navigationBarsPadding(),
        ) {
            val currentDetails = details
            if (currentDetails != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    DetailsCoverFrame(
                        coverUrl = currentDetails.coverUrl,
                        contentDescription = currentDetails.title,
                        onCoverBoundsSync = { _, _ -> },
                        alpha = coverAlpha,
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
                            text = currentDetails.title,
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
                        }

                        Row(modifier = Modifier.graphicsLayer {
                            alpha = 1f - actionsCollapseProgress
                            translationX = -18f * actionsCollapseProgress
                        }, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DetailsHeaderIconButton(
                                iconRes = R.drawable.ic_open_external,
                                onClick = { currentDetails.url?.let(appRouter::openExternalBrowser) },
                                enabled = currentDetails.url != null,
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .onGloballyPositioned { coordinates ->
                        infoCardTopPx = coordinates.boundsInRoot().top
                    },
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // If there's bound local content, we show it via DetailsBindingCard
                if (linkedContent != null && linkedTrackingItem != null) {
                    val content = linkedContent!!
                    val linked = linkedTrackingItem!!
                    DetailsBindingCard(
                        badge = stringResource(R.string.local_storage),
                        badgeIconRes = R.drawable.ic_storage,
                        title = content.title,
                        subtitle = buildString {
                            linked.status?.let { status ->
                                append(statuses.getOrNull(status.ordinal).orEmpty())
                            }
                            linked.rating?.takeIf { it > 0f }?.let {
                                if (isNotEmpty()) append(" · ")
                                append(String.format(defaultLocale, "%.1f", it * 10f))
                            }
                        }.ifBlank { stringResource(R.string.tracking) },
                        supportingText = stringResource(R.string.discover_local_linked),
                        coverUrl = content.coverUrl,
                        primaryActionLabel = stringResource(R.string.details),
                        onPrimaryAction = { appRouter.openDetails(content) },
                        secondaryActionLabel = stringResource(R.string.discover_manage_binding),
                        onSecondaryAction = {
                            val current = details ?: return@DetailsBindingCard
                            appRouter.openScrobblerBinding(current.service, current.remoteId, current.title, current.url)
                        },
                    )
                } else if (linkedContent == null && details != null) {
                    // No local content bound, suggest to search
                    DetailsBindingCard(
                        badge = stringResource(R.string.local_storage),
                        badgeIconRes = R.drawable.ic_storage,
                        title = stringResource(R.string.unknown),
                        subtitle = stringResource(R.string.tracking),
                        supportingText = stringResource(R.string.discover_local_linked), // Or something suitable as hint
                        primaryActionLabel = stringResource(R.string.search),
                        onPrimaryAction = {
                            val current = details ?: return@DetailsBindingCard
                            // Usually there is a global search or manage binding route
                            appRouter.openScrobblerBinding(current.service, current.remoteId, current.title, current.url)
                        },
                    )
                }

                if (details != null) {
                    TrackingSiteDetailsBody(
                        details = details!!,
                    )
                } else if (isLoading) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center
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
            }
        }
    }
    }
}

@Composable
private fun TrackingSiteDetailsBody(
    details: org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteItemDetails,
) {
    if (details.description.isNullOrBlank()) return

    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        style = GlassDefaults.regularStyle(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.description),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(
                    text = details.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
