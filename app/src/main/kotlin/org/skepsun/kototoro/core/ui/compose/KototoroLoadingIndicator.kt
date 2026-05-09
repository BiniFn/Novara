package org.skepsun.kototoro.core.ui.compose

import android.view.ContextThemeWrapper
import android.widget.ImageView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import kotlinx.coroutines.delay
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.isVisible
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.AppSettings
import kotlin.math.roundToInt

private const val IndicatorProgressMax = 10_000

@Composable
fun KototoroLoadingIndicator(
    modifier: Modifier = Modifier,
    progress: (() -> Float)? = null,
    style: AppSettings.LoadingCircleStyle? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = Color.Transparent,
) {
    val resolvedStyle = rememberLoadingIndicatorStyle(style)
    key(resolvedStyle) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                CircularProgressIndicator(
                    ContextThemeWrapper(context, resolvedStyle.themeOverlayResId),
                    null,
                    com.google.android.material.R.attr.circularProgressIndicatorStyle,
                ).apply {
                    max = IndicatorProgressMax
                }
            },
            update = { indicator ->
                indicator.setIndicatorColor(color.toArgb())
                indicator.trackColor = trackColor.toArgb()
                val determinateProgress = progress?.invoke()?.coerceIn(0f, 1f)
                if (determinateProgress == null) {
                    indicator.isIndeterminate = true
                } else {
                    indicator.isIndeterminate = false
                    indicator.setProgressCompat((determinateProgress * IndicatorProgressMax).roundToInt(), false)
                }
            },
        )
    }
}

@Composable
fun KototoroLinearProgressIndicator(
    modifier: Modifier = Modifier,
    progress: (() -> Float)? = null,
    style: AppSettings.LoadingCircleStyle? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
) {
    val resolvedStyle = rememberLoadingIndicatorStyle(style)
    key(resolvedStyle) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                LinearProgressIndicator(
                    ContextThemeWrapper(context, resolvedStyle.themeOverlayResId),
                    null,
                    com.google.android.material.R.attr.linearProgressIndicatorStyle,
                ).apply {
                    max = IndicatorProgressMax
                }
            },
            update = { indicator ->
                indicator.setIndicatorColor(color.toArgb())
                indicator.trackColor = trackColor.toArgb()
                val determinateProgress = progress?.invoke()?.coerceIn(0f, 1f)
                if (determinateProgress == null) {
                    indicator.isIndeterminate = true
                } else {
                    indicator.isIndeterminate = false
                    indicator.setProgressCompat((determinateProgress * IndicatorProgressMax).roundToInt(), false)
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KototoroPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    state: PullToRefreshState = rememberPullToRefreshState(),
    indicatorTopInset: PaddingValues = PaddingValues(0.dp),
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    if (!enabled) {
        Box(
            modifier = modifier,
            contentAlignment = contentAlignment,
            content = content,
        )
        return
    }

    // Defer isRefreshing activation to avoid race condition where PullToRefreshModifierNode
    // tries to read CompositionLocal before it is fully attached.
    var deferredRefreshing by remember { mutableStateOf(false) }
    var suppressDragIndicator by remember { mutableStateOf(false) }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            kotlinx.coroutines.delay(50)
            deferredRefreshing = true
            suppressDragIndicator = false
            return@LaunchedEffect
        }
        deferredRefreshing = isRefreshing
        suppressDragIndicator = true
        if (!isRefreshing && (state.distanceFraction > 0f || state.isAnimating)) {
            runCatching { state.animateToHidden() }
            if (state.distanceFraction > 0f) {
                runCatching { state.snapTo(0f) }
            }
        }
    }
    LaunchedEffect(state.distanceFraction, deferredRefreshing) {
        if (!deferredRefreshing && state.distanceFraction <= 0f) {
            suppressDragIndicator = false
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            deferredRefreshing = false
            suppressDragIndicator = false
        }
    }
    val dragProgress = state.distanceFraction.coerceIn(0f, 1f)
    val showDragIndicator = !deferredRefreshing && !suppressDragIndicator && dragProgress > 0f
    PullToRefreshBox(
        isRefreshing = deferredRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        contentAlignment = contentAlignment,
        indicator = {
            SwipeRefreshLikeIndicator(
                progress = dragProgress,
                isRefreshing = deferredRefreshing,
                visible = deferredRefreshing || showDragIndicator,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = indicatorTopInset.calculateTopPadding()),
            )
        },
        content = content,
    )
}

@Composable
private fun SwipeRefreshLikeIndicator(
    progress: Float,
    isRefreshing: Boolean,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!visible) {
        return
    }
    val color = MaterialTheme.colorScheme.primary.toArgb()
    AndroidView(
        modifier = modifier.size(40.dp),
        factory = { context ->
            ImageView(context).apply {
                val drawable = CircularProgressDrawable(context).apply {
                    setStyle(CircularProgressDrawable.DEFAULT)
                }
                setImageDrawable(drawable)
            }
        },
        update = { view ->
            view.isVisible = visible
            val drawable = view.drawable as? CircularProgressDrawable ?: return@AndroidView
            drawable.setColorSchemeColors(color)
            if (isRefreshing) {
                drawable.setArrowEnabled(false)
                drawable.start()
                return@AndroidView
            }

            val adjustedProgress = progress.coerceIn(0f, 1f)
            val swipeProgress = ((adjustedProgress - 0.4f).coerceAtLeast(0f) * 5f / 3f).coerceIn(0f, 1f)
            drawable.stop()
            drawable.setArrowEnabled(adjustedProgress > 0f)
            drawable.setStartEndTrim(0f, (0.8f * swipeProgress).coerceAtMost(0.8f))
            drawable.setArrowScale(swipeProgress)
            drawable.setProgressRotation((-0.25f + 0.4f * swipeProgress).coerceAtLeast(0f) * 0.5f)
            view.invalidate()
        },
    )
}

@Composable
private fun rememberLoadingIndicatorStyle(style: AppSettings.LoadingCircleStyle?): AppSettings.LoadingCircleStyle {
    if (style != null) {
        return style
    }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings = remember(context.applicationContext) { AppSettings(context.applicationContext) }
    var resolvedStyle by remember(settings) { mutableStateOf(settings.loadingCircleStyle) }
    DisposableEffect(settings, lifecycleOwner) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == AppSettings.KEY_LOADING_CIRCLE_STYLE) {
                resolvedStyle = settings.loadingCircleStyle
            }
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resolvedStyle = settings.loadingCircleStyle
            }
        }
        settings.subscribe(listener)
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            settings.unsubscribe(listener)
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        }
    }
    return resolvedStyle
}

private val AppSettings.LoadingCircleStyle.themeOverlayResId: Int
    get() = when (this) {
        AppSettings.LoadingCircleStyle.THICK_STRAIGHT -> R.style.ThemeOverlay_Kototoro_Loading_ThickStraight
        AppSettings.LoadingCircleStyle.THICK_WAVY -> R.style.ThemeOverlay_Kototoro_Loading_ThickWavy
        AppSettings.LoadingCircleStyle.THIN_STRAIGHT -> R.style.ThemeOverlay_Kototoro_Loading_ThinStraight
        AppSettings.LoadingCircleStyle.THIN_WAVY -> R.style.ThemeOverlay_Kototoro_Loading_ThinWavy
    }
