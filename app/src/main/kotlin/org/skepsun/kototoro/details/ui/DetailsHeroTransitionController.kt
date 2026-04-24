package org.skepsun.kototoro.details.ui

import android.animation.ValueAnimator
import android.graphics.Outline
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.core.animation.doOnCancel
import androidx.core.animation.doOnEnd
import androidx.core.view.doOnLayout
import androidx.core.view.updateLayoutParams
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.databinding.ActivityDetailsBinding
import org.skepsun.kototoro.parsers.model.Content

internal class DetailsHeroTransitionController(
    private val activity: DetailsActivity,
    private val binding: ActivityDetailsBinding,
    private val settings: AppSettings,
) {

    private companion object {
        private const val TAG = "HeroTransition"
        private const val KEY_SHOULD_RENDER_TRANSITION_COVER = "should_render_transition_cover"
    }

    private data class CoverSnapshot(
        val bounds: Rect,
        val alpha: Float,
    )

    private var shouldRenderTransitionCover = true
    private var pendingCoverStartBounds: Rect? = null
    private var entryCoverStartBounds: Rect? = null
    private var liveCoverSnapshot: CoverSnapshot? = null
    private var frozenEnterTarget: CoverSnapshot? = null
    private var frozenExitSource: CoverSnapshot? = null
    private var hasPlayedPendingCoverIntro = false
    private var hasPlayedContentEnterMotion = false
    private var isIntroHeroRunning = false
    private var isExitHeroRunning = false
    private var isFadeOutQueued = false
    private var activeCoverAnimator: ValueAnimator? = null

    var isHeroOverlayVisible by mutableStateOf(false)
        private set

    private val pendingIntroStarter = Runnable {
        val startBounds = pendingCoverStartBounds ?: return@Runnable
        val endSnapshot = liveCoverSnapshot ?: frozenEnterTarget ?: return@Runnable
        pendingCoverStartBounds = null
        hasPlayedPendingCoverIntro = true
        frozenEnterTarget = endSnapshot
        isIntroHeroRunning = true
        Log.d(TAG, "intro start: start=$startBounds end=${endSnapshot.bounds} alpha=${endSnapshot.alpha}")
        animateCoverIntro(
            startBounds = startBounds,
            endSnapshot = endSnapshot,
        ) {
            isIntroHeroRunning = false
            if (shouldRenderTransitionCover && !isExitHeroRunning) {
                liveCoverSnapshot?.let(::applyCoverSnapshot)
            }
            if (isFadeOutQueued) {
                isFadeOutQueued = false
                if (!isExitHeroRunning) {
                    scheduleTransitionCoverFadeOutInternal()
                }
            }
        }
    }

    private val transitionCoverFadeOutRunnable = Runnable {
        shouldRenderTransitionCover = false
        isHeroOverlayVisible = false
        binding.imageViewCover.alpha = 0f
        binding.imageViewCover.visibility = View.GONE
    }

    fun restoreState(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            shouldRenderTransitionCover = savedInstanceState.getBoolean(KEY_SHOULD_RENDER_TRANSITION_COVER, true)
        }
        pendingCoverStartBounds = null
        entryCoverStartBounds = null
        liveCoverSnapshot = null
        frozenEnterTarget = null
        frozenExitSource = null
        hasPlayedPendingCoverIntro = false
        hasPlayedContentEnterMotion = false
        isIntroHeroRunning = false
        isExitHeroRunning = false
        isFadeOutQueued = false
        isHeroOverlayVisible = false
    }

    fun saveState(outState: Bundle) {
        outState.putBoolean(KEY_SHOULD_RENDER_TRANSITION_COVER, shouldRenderTransitionCover)
    }

    fun prepareOnCreate(manga: Content?, isFreshLaunch: Boolean) {
        if (!settings.isSharedElementTransitionsEnabled || manga == null) {
            binding.imageViewTransitionBackground.setImageDrawable(null)
            binding.imageViewTransitionBackground.visibility = View.GONE
            return
        }
        val pendingTransition = DetailsCoverTransitionStore.consume(manga)
        pendingCoverStartBounds = pendingTransition?.bounds
        entryCoverStartBounds = pendingCoverStartBounds
        frozenEnterTarget = null
        frozenExitSource = null
        liveCoverSnapshot = null
        hasPlayedPendingCoverIntro = false
        hasPlayedContentEnterMotion = false
        isIntroHeroRunning = false
        isExitHeroRunning = false
        isFadeOutQueued = false
        activeCoverAnimator?.cancel()
        activeCoverAnimator = null
        binding.imageViewTransitionBackground.apply {
            if (pendingTransition?.backgroundSnapshot != null) {
                setImageBitmap(pendingTransition.backgroundSnapshot)
                visibility = View.VISIBLE
            } else {
                setImageDrawable(null)
                visibility = View.GONE
            }
        }
        Log.d(
            TAG,
            "onCreate consume: hasSnapshot=${pendingTransition?.backgroundSnapshot != null} bounds=${pendingTransition?.bounds}",
        )
        androidx.core.view.ViewCompat.setTransitionName(binding.imageViewCover, "cover_${manga.source.name}_${manga.url}")
        binding.imageViewCover.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, activity.resources.displayMetrics.density * 22f)
            }
        }
        binding.imageViewCover.clipToOutline = true
        pendingCoverStartBounds?.let { startBounds ->
            applyCoverBounds(startBounds, alpha = 1f)
        }
        isHeroOverlayVisible = pendingCoverStartBounds != null && shouldRenderTransitionCover
        if (isFreshLaunch) {
            binding.composeView.doOnLayout {
                playContentEnterMotionIfNeeded()
            }
        }
        activity.supportPostponeEnterTransition()
        activity.window.decorView.postDelayed({ activity.supportStartPostponedEnterTransition() }, 1200L)
    }

    fun onCoverImageLoadSettled() {
        activity.supportStartPostponedEnterTransition()
        scheduleTransitionCoverFadeOut()
    }

    fun syncCoverBounds(rect: Rect, alpha: Float) {
        val snapshot = rect
            .takeIf { it.width > 0f && it.height > 0f }
            ?.let { CoverSnapshot(bounds = it, alpha = alpha.coerceIn(0f, 1f)) }
        if (snapshot != null) {
            liveCoverSnapshot = snapshot
        }
        if (!shouldRenderTransitionCover) {
            binding.imageViewCover.visibility = View.GONE
            return
        }
        if (snapshot == null) {
            return
        }
        if (pendingCoverStartBounds != null && !hasPlayedPendingCoverIntro) {
            binding.imageViewCover.removeCallbacks(pendingIntroStarter)
            binding.imageViewCover.postDelayed(pendingIntroStarter, 32L)
            return
        }
        if (isIntroHeroRunning || isExitHeroRunning) {
            return
        }
        applyCoverSnapshot(snapshot)
    }

    fun playExitHeroIfNeeded(): Boolean {
        if (!settings.isSharedElementTransitionsEnabled || isExitHeroRunning) {
            return false
        }
        val startBounds = entryCoverStartBounds ?: return false
        val currentSnapshot = (liveCoverSnapshot ?: frozenEnterTarget) ?: return false
        if (currentSnapshot.alpha < 0.35f) {
            return false
        }
        frozenExitSource = currentSnapshot
        isExitHeroRunning = true
        isIntroHeroRunning = false
        shouldRenderTransitionCover = true
        isHeroOverlayVisible = true
        isFadeOutQueued = false
        binding.imageViewCover.removeCallbacks(pendingIntroStarter)
        binding.imageViewCover.removeCallbacks(transitionCoverFadeOutRunnable)
        binding.composeView.animate().cancel()
        activeCoverAnimator?.cancel()
        if (binding.imageViewTransitionBackground.drawable != null) {
            binding.imageViewTransitionBackground.apply {
                alpha = 1f
                visibility = View.VISIBLE
            }
        }
        binding.imageViewCover.bringToFront()
        applyCoverBounds(currentSnapshot.bounds, alpha = 1f)
        binding.imageViewCover.visibility = View.VISIBLE

        val travelDistance = resolveContentTravelDistancePx()
        val contentView = binding.composeView
        val contentStartTranslationX = contentView.translationX
        val contentStartAlpha = contentView.alpha
        Log.d(
            TAG,
            "before exit: startBounds=$startBounds currentBounds=${currentSnapshot.bounds} currentAlpha=${currentSnapshot.alpha} contentX=$contentStartTranslationX contentAlpha=$contentStartAlpha",
        )

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 320L
            interpolator = FastOutSlowInInterpolator()
            var isFinished = false
            addUpdateListener { animator ->
                val fraction = animator.animatedFraction
                val interpolatedBounds = Rect(
                    left = lerp(currentSnapshot.bounds.left, startBounds.left, fraction),
                    top = lerp(currentSnapshot.bounds.top, startBounds.top, fraction),
                    right = lerp(currentSnapshot.bounds.right, startBounds.right, fraction),
                    bottom = lerp(currentSnapshot.bounds.bottom, startBounds.bottom, fraction),
                )
                applyCoverBounds(interpolatedBounds, alpha = 1f)
                contentView.translationX = lerp(contentStartTranslationX, travelDistance, fraction)
                contentView.alpha = contentStartAlpha
            }
            doOnEnd {
                if (!isFinished) {
                    isFinished = true
                    activity.finishAfterTransition()
                    activity.overridePendingTransition(0, 0)
                }
            }
            doOnCancel {
                if (!isFinished) {
                    isFinished = true
                    activity.finishAfterTransition()
                    activity.overridePendingTransition(0, 0)
                }
            }
            startCoverAnimator(this)
        }
        return true
    }

    private fun applyCoverSnapshot(snapshot: CoverSnapshot) {
        applyCoverBounds(snapshot.bounds, snapshot.alpha)
    }

    private fun applyCoverBounds(rect: Rect, alpha: Float) {
        if (rect.width > 0 && rect.height > 0) {
            binding.imageViewCover.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                width = rect.width.toInt()
                height = rect.height.toInt()
                topMargin = rect.top.toInt()
                leftMargin = rect.left.toInt()
            }
        }
        binding.imageViewCover.alpha = alpha.coerceIn(0f, 1f)
        binding.imageViewCover.visibility = View.VISIBLE
    }

    private fun animateCoverIntro(
        startBounds: Rect,
        endSnapshot: CoverSnapshot,
        onEnd: (() -> Unit)? = null,
    ) {
        applyCoverBounds(startBounds, alpha = 1f)
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 320L
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                applyCoverBounds(
                    rect = Rect(
                        left = lerp(startBounds.left, endSnapshot.bounds.left, fraction),
                        top = lerp(startBounds.top, endSnapshot.bounds.top, fraction),
                        right = lerp(startBounds.right, endSnapshot.bounds.right, fraction),
                        bottom = lerp(startBounds.bottom, endSnapshot.bounds.bottom, fraction),
                    ),
                    alpha = lerp(1f, endSnapshot.alpha.coerceIn(0f, 1f), fraction),
                )
            }
            addOnAnimationEndCompat(onEnd)
            startCoverAnimator(this)
        }
    }

    private fun playContentEnterMotionIfNeeded() {
        if (!settings.isSharedElementTransitionsEnabled || hasPlayedContentEnterMotion) {
            return
        }
        if (entryCoverStartBounds == null) {
            return
        }
        val contentView = binding.composeView
        val travelDistance = resolveContentTravelDistancePx()
        if (travelDistance <= 0f) {
            return
        }
        hasPlayedContentEnterMotion = true
        contentView.animate().cancel()
        contentView.translationX = travelDistance
        contentView.alpha = 1f
        contentView.animate()
            .translationX(0f)
            .setDuration(320L)
            .setInterpolator(FastOutSlowInInterpolator())
            .start()
    }

    private fun resolveContentTravelDistancePx(): Float {
        val widthPx = binding.root.width.takeIf { it > 0 } ?: activity.resources.displayMetrics.widthPixels
        if (widthPx <= 0) {
            return 0f
        }
        return widthPx.toFloat()
    }

    private fun scheduleTransitionCoverFadeOut() {
        if (!shouldRenderTransitionCover) {
            return
        }
        if (pendingCoverStartBounds != null && !hasPlayedPendingCoverIntro) {
            isFadeOutQueued = true
            return
        }
        scheduleTransitionCoverFadeOutInternal()
    }

    private fun scheduleTransitionCoverFadeOutInternal() {
        binding.imageViewCover.removeCallbacks(transitionCoverFadeOutRunnable)
        val delayMs = if (settings.isSharedElementTransitionsEnabled) 380L else 0L
        binding.imageViewCover.postDelayed({
            transitionCoverFadeOutRunnable.run()
        }, delayMs)
    }

    private fun startCoverAnimator(animator: ValueAnimator) {
        activeCoverAnimator?.cancel()
        activeCoverAnimator = animator
        animator.doOnEnd {
            if (activeCoverAnimator === animator) {
                activeCoverAnimator = null
            }
        }
        animator.doOnCancel {
            if (activeCoverAnimator === animator) {
                activeCoverAnimator = null
            }
        }
        animator.start()
    }

    private fun ValueAnimator.addOnAnimationEndCompat(onEnd: (() -> Unit)?) {
        if (onEnd == null) {
            return
        }
        addListener(object : android.animation.Animator.AnimatorListener {
            override fun onAnimationStart(animation: android.animation.Animator) = Unit
            override fun onAnimationEnd(animation: android.animation.Animator) = onEnd()
            override fun onAnimationCancel(animation: android.animation.Animator) = onEnd()
            override fun onAnimationRepeat(animation: android.animation.Animator) = Unit
        })
    }
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction.coerceIn(0f, 1f)
}
