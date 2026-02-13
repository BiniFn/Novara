package org.skepsun.kototoro.video.danmaku

import android.graphics.Color
import android.view.View
import com.bytedance.danmaku.render.engine.DanmakuView
import com.bytedance.danmaku.render.engine.control.DanmakuController
import com.bytedance.danmaku.render.engine.render.draw.text.TextData
import com.bytedance.danmaku.render.engine.utils.LAYER_TYPE_SCROLL
import kotlin.math.max

class VideoDanmakuController {
    private var danmakuView: DanmakuView? = null
    private var danmakuController: DanmakuController? = null
    private var isReady = false
    private var pendingSeekMs: Long? = null
    private var isEnabled = false
    private var isVisible = false
    private var positionProvider: (() -> Long)? = null
    private var playingProvider: (() -> Boolean)? = null
    private var hasData = false

    fun attach(view: DanmakuView) {
        danmakuView = view
        danmakuController = view.controller
        isReady = true
        val target = pendingSeekMs ?: positionProvider?.invoke()
        if (target != null) {
            pendingSeekMs = null
            if (hasData) {
                danmakuController?.start(target)
            }
        }
        updateVisibility()
        if (playingProvider?.invoke() == true && hasData) {
            danmakuController?.start(positionProvider?.invoke() ?: 0L)
        }
    }

    fun setPlaybackPositionProvider(
        positionProvider: () -> Long,
        playingProvider: () -> Boolean,
    ) {
        this.positionProvider = positionProvider
        this.playingProvider = playingProvider
    }

    fun applySettings(settings: DanmakuSettings) {
        isEnabled = settings.enabled
        val size = max(18f, (settings.sizePercent / 100f) * 48f)
        val speedFactor = 2.0f - (settings.speedPercent / 100f) * 1.5f
        val moveTime = (8000L * speedFactor).toLong().coerceIn(2000L, 12000L)
        val alpha = (settings.opacityPercent / 100f * 255f).toInt().coerceIn(0, 255)
        val stroke = (settings.strokePercent / 100f) * 6f
        val controller = danmakuController ?: return
        val config = controller.config
        config.text.size = size
        config.text.strokeWidth = stroke
        config.common.alpha = alpha
        config.scroll.moveTime = moveTime
        config.common.topVisible = settings.showTop
        config.common.bottomVisible = settings.showBottom
        config.scroll.lineCount = when {
            !settings.showScroll -> 0
            settings.maxScrollLines > 0 -> settings.maxScrollLines
            else -> config.scroll.lineCount
        }
        config.top.lineCount = when {
            !settings.showTop -> 0
            settings.maxTopLines > 0 -> settings.maxTopLines
            else -> config.top.lineCount
        }
        config.bottom.lineCount = when {
            !settings.showBottom -> 0
            settings.maxBottomLines > 0 -> settings.maxBottomLines
            else -> config.bottom.lineCount
        }
        updateVisibility()
        if (isEnabled && isVisible && hasData && playingProvider?.invoke() == true) {
            controller.start(positionProvider?.invoke() ?: 0L)
        }
        controller.invalidateView()
    }

    fun loadDanmaku(
        items: List<DanmakuItem>,
        autoShow: Boolean = true,
        isPlaying: Boolean = false,
    ) {
        val controller = danmakuController ?: return
        val data = DanmakuListParser(items).toTextDataList()
        hasData = data.isNotEmpty()
        isVisible = autoShow
        android.util.Log.d("Danmaku", "Render load: items=${items.size} data=${data.size}")
        updateVisibility()
        val startTime = positionProvider?.invoke() ?: 0L
        controller.setData(data, startTime)
        if (isEnabled && isVisible && hasData && isPlaying) {
            controller.start(startTime)
        }
    }

    fun addLiveDanmaku(message: String, timeMs: Long) {
        if (!isEnabled) return
        val controller = danmakuController ?: return
        if (!hasData) {
            hasData = true
            updateVisibility()
        }
        android.util.Log.d("Danmaku", "Render append live at $timeMs: $message")
        val danmaku = TextData().apply {
            text = message
            showAtTime = timeMs
            layerType = LAYER_TYPE_SCROLL
            textColor = Color.WHITE
            textStrokeColor = Color.BLACK
        }
        controller.appendData(listOf(danmaku))
    }

    fun start() {
        if (!isEnabled) return
        if (!hasData) return
        danmakuController?.start(positionProvider?.invoke() ?: 0L)
    }

    fun setVisible(visible: Boolean) {
        isVisible = visible
        updateVisibility()
        if (isEnabled && isVisible && hasData && playingProvider?.invoke() == true) {
            danmakuController?.start(positionProvider?.invoke() ?: 0L)
        }
    }

    fun seekTo(positionMs: Long) {
        if (isReady && hasData) {
            val controller = danmakuController ?: return
            controller.pause()
            controller.clear()
            if (playingProvider?.invoke() == true) {
                controller.start(positionMs)
            }
        } else {
            pendingSeekMs = positionMs
        }
    }

    fun onPlaybackStateChanged(isPlaying: Boolean) {
        if (!isEnabled) return
        val controller = danmakuController ?: return
        if (!hasData) return
        if (isPlaying) controller.start(positionProvider?.invoke() ?: 0L) else controller.pause()
    }

    fun pause() {
        danmakuController?.pause()
    }

    fun resume() {
        if (!hasData) return
        danmakuController?.start(positionProvider?.invoke() ?: 0L)
    }

    fun clear() {
        hasData = false
        danmakuController?.stop()
        updateVisibility()
    }

    fun release() {
        danmakuController?.stop()
        danmakuController = null
        danmakuView = null
    }

    fun isPrepared(): Boolean = hasData

    private fun updateVisibility() {
        danmakuView?.visibility = if (isEnabled && isVisible && hasData) View.VISIBLE else View.GONE
    }
}
