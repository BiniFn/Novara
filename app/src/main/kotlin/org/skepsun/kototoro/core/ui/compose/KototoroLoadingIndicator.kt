package org.skepsun.kototoro.core.ui.compose

import android.view.ContextThemeWrapper
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
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
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlin.math.roundToInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.AppSettings

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
    state: PullToRefreshState = rememberPullToRefreshState(),
    indicatorTopInset: PaddingValues = PaddingValues(0.dp),
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    // Defer isRefreshing activation to avoid race condition where PullToRefreshModifierNode
    // tries to read CompositionLocal before it is fully attached.
    var deferredRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            kotlinx.coroutines.delay(50)
        }
        deferredRefreshing = isRefreshing
    }
    DisposableEffect(Unit) {
        onDispose {
            deferredRefreshing = false
        }
    }
    PullToRefreshBox(
        isRefreshing = deferredRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        contentAlignment = contentAlignment,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = deferredRefreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = indicatorTopInset.calculateTopPadding()),
            )
        },
        content = content,
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
