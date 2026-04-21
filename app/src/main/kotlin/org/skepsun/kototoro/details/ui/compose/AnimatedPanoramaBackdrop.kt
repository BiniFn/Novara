package org.skepsun.kototoro.details.ui.compose

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.ui.platform.LocalContext
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.observeAsState

@Composable
fun AnimatedPanoramaBackdrop(
    settings: AppSettings,
    model: Any?,
    contentAlpha: Float,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
) {
    val isPanoramaCoverEnabled by settings.observeAsState(AppSettings.KEY_PANORAMA_ENABLED) { isPanoramaCoverEnabled }
    val panoramaBlur by settings.observeAsState(AppSettings.KEY_PANORAMA_BLUR) { panoramaCoverBlur }
    val panoramaBottomAlpha by settings.observeAsState(AppSettings.KEY_PANORAMA_BOTTOM_GRADIENT_ALPHA) {
        panoramaBottomGradientAlpha
    }
    val isPanoramaCoverAnimationEnabled by settings.observeAsState(AppSettings.KEY_PANORAMA_ANIMATION_ENABLED) {
        isPanoramaCoverAnimationEnabled
    }
    val panoramaAnimationSpeed by settings.observeAsState(AppSettings.KEY_PANORAMA_ANIMATION_SPEED) {
        panoramaAnimationSpeed
    }

    if (!isPanoramaCoverEnabled) return

    val blurRadius = (((panoramaBlur / 100f).coerceIn(0f, 1f)) * 20f).dp
    val panoramaGradientAlphaFactor = (panoramaBottomAlpha / 100f).coerceIn(0f, 1f)
    val panoramaAnimationSpeedFactor = (panoramaAnimationSpeed.coerceIn(50, 200)) / 100f
    val scaleAnimationDuration = (14000 / panoramaAnimationSpeedFactor).toInt().coerceAtLeast(4000)
    val horizontalPanAnimationDuration = (16000 / panoramaAnimationSpeedFactor).toInt().coerceAtLeast(4500)
    val verticalPanAnimationDuration = (12000 / panoramaAnimationSpeedFactor).toInt().coerceAtLeast(3500)

    val infiniteTransition = rememberInfiniteTransition(label = "details_panorama_background")
    val backgroundScale by infiniteTransition.animateFloat(
        initialValue = if (isPanoramaCoverAnimationEnabled) 1.15f else 1f,
        targetValue = if (isPanoramaCoverAnimationEnabled) 1.22f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = scaleAnimationDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "details_panorama_background_scale",
    )
    val backgroundTranslationX by infiniteTransition.animateFloat(
        initialValue = if (isPanoramaCoverAnimationEnabled) -18f else 0f,
        targetValue = if (isPanoramaCoverAnimationEnabled) 18f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = horizontalPanAnimationDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "details_panorama_background_translation_x",
    )
    val backgroundTranslationY by infiniteTransition.animateFloat(
        initialValue = if (isPanoramaCoverAnimationEnabled) -12f else 0f,
        targetValue = if (isPanoramaCoverAnimationEnabled) 12f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = verticalPanAnimationDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "details_panorama_background_translation_y",
    )

    val context = LocalContext.current
    val backgroundRequest = androidx.compose.runtime.remember(model) {
        when (model) {
            is ImageRequest -> model.newBuilder()
                .size(400)
                .crossfade(true)
                .build()
            else -> ImageRequest.Builder(context)
                .data(model)
                .size(400)
                .crossfade(true)
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
                scaleX = backgroundScale
                scaleY = backgroundScale
                translationX = backgroundTranslationX
                translationY = backgroundTranslationY
            }
            .blur(blurRadius)
            .alpha(contentAlpha),
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
