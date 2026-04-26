package org.skepsun.kototoro.details.ui.compose

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.ui.platform.LocalContext
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.observeAsState
import org.skepsun.kototoro.core.ui.image.panoramaBlur

@Immutable
data class PanoramaBackdropPrefs(
    val isEnabled: Boolean,
    val blurPercent: Int,
    val bottomGradientAlphaPercent: Int,
    val isAnimationEnabled: Boolean,
    val animationSpeedPercent: Int,
)

@Composable
fun rememberPanoramaBackdropPrefs(settings: AppSettings): PanoramaBackdropPrefs {
    val supportsRealtimeEffects = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val prefs by settings.observeAsState(
        AppSettings.KEY_PANORAMA_ENABLED,
        AppSettings.KEY_PANORAMA_BLUR,
        AppSettings.KEY_PANORAMA_BOTTOM_GRADIENT_ALPHA,
        AppSettings.KEY_PANORAMA_ANIMATION_ENABLED,
        AppSettings.KEY_PANORAMA_ANIMATION_SPEED,
    ) {
        PanoramaBackdropPrefs(
            isEnabled = isPanoramaCoverEnabled,
            blurPercent = panoramaCoverBlur,
            bottomGradientAlphaPercent = panoramaBottomGradientAlpha,
            isAnimationEnabled = supportsRealtimeEffects && isPanoramaCoverAnimationEnabled,
            animationSpeedPercent = panoramaAnimationSpeed,
        )
    }
    return prefs
}

@Composable
fun AnimatedPanoramaBackdrop(
    prefs: PanoramaBackdropPrefs,
    model: Any?,
    contentAlpha: Float,
    contentAlphaProvider: (() -> Float)? = null,
    backgroundColor: Color,
    crossfadeEnabled: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (!prefs.isEnabled) return

    val panoramaGradientAlphaFactor = (prefs.bottomGradientAlphaPercent / 100f).coerceIn(0f, 1f)
    val panoramaAnimationSpeedFactor = (prefs.animationSpeedPercent.coerceIn(50, 200)) / 100f
    val scaleAnimationDuration = (14000 / panoramaAnimationSpeedFactor).toInt().coerceAtLeast(4000)
    val horizontalPanAnimationDuration = (16000 / panoramaAnimationSpeedFactor).toInt().coerceAtLeast(4500)
    val verticalPanAnimationDuration = (12000 / panoramaAnimationSpeedFactor).toInt().coerceAtLeast(3500)

    val infiniteTransition = if (prefs.isAnimationEnabled) {
        rememberInfiniteTransition(label = "details_panorama_background")
    } else {
        null
    }
    val backgroundScaleState = if (infiniteTransition != null) {
        infiniteTransition.animateFloat(
            initialValue = 1.15f,
            targetValue = 1.22f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = scaleAnimationDuration, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "details_panorama_background_scale",
        )
    } else {
        null
    }
    val backgroundTranslationXState = if (infiniteTransition != null) {
        infiniteTransition.animateFloat(
            initialValue = -18f,
            targetValue = 18f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = horizontalPanAnimationDuration, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "details_panorama_background_translation_x",
        )
    } else {
        null
    }
    val backgroundTranslationYState = if (infiniteTransition != null) {
        infiniteTransition.animateFloat(
            initialValue = -12f,
            targetValue = 12f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = verticalPanAnimationDuration, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "details_panorama_background_translation_y",
        )
    } else {
        null
    }

    val context = LocalContext.current
    val backgroundRequest = androidx.compose.runtime.remember(
        model,
        context,
        crossfadeEnabled,
        prefs.blurPercent,
    ) {
        when (model) {
            is ImageRequest -> model.newBuilder()
                .size(400)
                .crossfade(crossfadeEnabled)
                .panoramaBlur(prefs.blurPercent)
                .build()
            else -> ImageRequest.Builder(context)
                .data(model)
                .size(400)
                .crossfade(crossfadeEnabled)
                .panoramaBlur(prefs.blurPercent)
                .build()
        }
    }

    AsyncImage(
        model = backgroundRequest,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val backgroundScale = backgroundScaleState?.value ?: 1f
                scaleX = backgroundScale
                scaleY = backgroundScale
                translationX = backgroundTranslationXState?.value ?: 0f
                translationY = backgroundTranslationYState?.value ?: 0f
                alpha = (contentAlphaProvider?.invoke() ?: contentAlpha).coerceIn(0f, 1f)
            }
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        backgroundColor.copy(alpha = panoramaGradientAlphaFactor),
                    ),
                ),
            ),
    )
}
