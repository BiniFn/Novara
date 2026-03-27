package org.skepsun.kototoro.video.ui

import android.os.Bundle
import android.view.View
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.content.res.ColorStateList
import android.content.ContentValues
import android.os.Build
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import android.view.GestureDetector
import android.view.MotionEvent
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.TimeBar
import androidx.media3.ui.DefaultTimeBar
import com.google.android.material.progressindicator.LinearProgressIndicator
import android.view.ViewGroup
import android.view.SurfaceView
import android.app.PictureInPictureParams
import android.provider.MediaStore
import android.util.Rational
import android.view.PixelCopy
import android.util.Log
import org.skepsun.kototoro.core.util.ext.consumeAll
import org.skepsun.kototoro.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.skepsun.kototoro.core.model.ContentSource
import org.skepsun.kototoro.aniyomi.AniyomiAnimeRepository
import org.skepsun.kototoro.core.parser.ContentRepository
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.network.CommonHeaders
import org.skepsun.kototoro.core.network.webview.WebViewExecutor
import org.skepsun.kototoro.core.parser.tvbox.TVBoxPlayback
import org.skepsun.kototoro.core.ui.BaseFullscreenActivity
import org.skepsun.kototoro.databinding.ActivityVideoPlayerBinding
import org.skepsun.kototoro.core.util.ext.getParcelableExtraCompat
import org.skepsun.kototoro.core.model.parcelable.ParcelableContent
import org.skepsun.kototoro.core.nav.ReaderIntent
import androidx.core.net.toUri
import org.skepsun.kototoro.reader.ui.ReaderState
import org.skepsun.kototoro.parsers.model.ContentSource as ParsersContentSource
import javax.inject.Inject
import com.google.android.material.snackbar.Snackbar
import org.skepsun.kototoro.reader.ui.ScreenOrientationHelper
import org.skepsun.kototoro.core.util.FoldableUtils
import org.skepsun.kototoro.video.ui.VideoSettingsSheet
import androidx.core.view.updateLayoutParams
import com.google.android.material.color.MaterialColors
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.graphics.ColorUtils
import android.net.Uri
import java.net.URLDecoder
import android.media.AudioManager
import android.provider.Settings
import android.content.Context
import java.io.File
import kotlin.math.abs
import org.skepsun.kototoro.core.util.ext.menuView
import org.skepsun.kototoro.history.data.HistoryRepository
import org.skepsun.kototoro.history.domain.HistoryUpdateUseCase
import org.skepsun.kototoro.reader.ui.ReaderNavigationCallback
import org.skepsun.kototoro.parsers.model.ContentChapter
import org.skepsun.kototoro.reader.ui.pager.ReaderPage
import org.skepsun.kototoro.bookmarks.domain.Bookmark
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.VideoDecoderMode
import org.skepsun.kototoro.core.prefs.VideoRendererMode
import org.skepsun.kototoro.core.prefs.VideoSuperResolutionMode
import org.skepsun.kototoro.core.prefs.VideoSuperResolutionShader
import org.skepsun.kototoro.video.player.CustomMpvView
import org.skepsun.kototoro.video.player.MpvPlayer
import org.skepsun.kototoro.video.player.MpvShaderManager
import org.skepsun.kototoro.video.data.VideoLocalCacheProxy
import org.skepsun.kototoro.video.data.ExternalPlayerHelper
import org.skepsun.kototoro.video.danmaku.VideoDanmakuController
import org.skepsun.kototoro.video.danmaku.DanmakuSettings
import org.skepsun.kototoro.video.danmaku.DanmakuSourceManager
import com.bytedance.danmaku.render.engine.DanmakuView
import eu.kanade.tachiyomi.animesource.model.Video
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlin.math.roundToInt

@AndroidEntryPoint
class VideoPlayerActivity : BaseFullscreenActivity<ActivityVideoPlayerBinding>(), ReaderNavigationCallback {
    companion object {
        // 全局开关：m3u8 代理缓存默认开启
        private const val ENABLE_M3U8_PROXY_CACHE = true
    }


    @Inject
    lateinit var appSettings: AppSettings

    private var mpvPlayer: MpvPlayer? = null
    internal fun getMpvPlayer(): MpvPlayer? = mpvPlayer
    private var isUiVisible: Boolean = false
    private var autoNextTriggered: Boolean = false
    private var isFoldUnfolded: Boolean = false
    private var isHorizontalScrubbing: Boolean = false
    private var verticalAdjustMode: Int = 0 // 0: none, 1: brightness, 2: volume
    private var initialTouchX: Float = 0f
    private var initialScrubPositionStart: Long = 0L
    private var lastScrubPosition: Long = 0L
    private var originalToolbarHeightPx: Int = 0
    private var availableVideos: List<Video> = emptyList()
    private var currentVideoIndex: Int = 0
    private var currentVideoSource: ParsersContentSource? = null
    private var currentMediaHeaders: Map<String, String>? = null
    private var skipHistorySeekForCurrentMedia: Boolean = false
    private var pendingExternalSubtitles: List<eu.kanade.tachiyomi.animesource.model.Track> = emptyList()
    private var pendingExternalAudio: List<eu.kanade.tachiyomi.animesource.model.Track> = emptyList()
    private lateinit var mpvView: CustomMpvView
    private val danmakuController = VideoDanmakuController()
    private var danmakuLoadJob: Job? = null
    private var danmakuKey: String? = null

    @Inject
    lateinit var danmakuSourceManager: DanmakuSourceManager

    @Inject
    lateinit var videoDownloadIndex: org.skepsun.kototoro.video.data.VideoDownloadIndex

    @Inject
    lateinit var videoLocalCacheProxy: VideoLocalCacheProxy

    @Inject
    lateinit var webViewExecutor: WebViewExecutor

    // ReaderState（用于历史保存时提供章节与页信息）
    private var readerState: ReaderState? = null
    // 待应用的历史定位百分比（在播放器 STATE_READY 时按时长换算并 seek）
    private var pendingInitialSeekPercent: Float? = null
    // 标志：是否已经恢复过进度（避免重复恢复）
    private var hasRestoredProgress: Boolean = false
    // 标志：用户是否正在拖动底部进度条（避免定时刷新抢占用户交互）
    private var isUserScrubbing: Boolean = false
    // 防止重复绑定 TimeBar listener（wireControllerButtons 会被多次调用）
    private var isTimeBarListenerBound: Boolean = false
    private var currentMediaUrl: String? = null
    private var lastSubtitleTextFromPoll: String? = null
    private var subtitlePollCounter = 0
    private val mpvListener = object : MpvPlayer.Listener {
        override fun onDurationChanged(durationMs: Long) {
            if (!hasRestoredProgress && durationMs > 0) {
                runOnUiThread {
                    tryApplyInitialSeek()
                    hasRestoredProgress = true
                    viewBinding.root.removeCallbacks(progressSaveRunnable)
                    viewBinding.root.postDelayed(progressSaveRunnable, progressSaveIntervalMs)
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            runOnUiThread {
                updatePlaybackMenu()
                updatePlayPauseButton()
                danmakuController.onPlaybackStateChanged(isPlaying)
            }
        }

        override fun onPlaybackEnded() {
            savePlaybackProgress()
            saveHistoryProgressAsync()
            runOnUiThread {
                maybeAutoPlayNext()
            }
        }

        override fun onFileLoaded() {
            runOnUiThread {
                autoNextTriggered = false
                applySuperResolutionFromSettings()
                danmakuController.start()
                loadPendingExternalTracks()
            }
        }

        override fun onSubtitleTextChanged(text: String?) {
            // This callback may or may not fire depending on mpv-android-lib version
            Log.d("VideoPlayerActivity", "onSubtitleTextChanged callback: '$text'")
            updateSubtitleOverlay(text)
        }

        override fun onPositionChanged(positionMs: Long) {
            // Poll sub-text every ~5th position update (~every 500ms if updates come at ~100ms intervals)
            subtitlePollCounter++
            if (subtitlePollCounter % 5 == 0) {
                val text = mpvPlayer?.getPropertyString("sub-text")
                if (text != lastSubtitleTextFromPoll) {
                    lastSubtitleTextFromPoll = text
                    Log.d("VideoPlayerActivity", "sub-text poll: '$text'")
                    updateSubtitleOverlay(text)
                }
            }
        }

        override fun onSeek(positionMs: Long) {
            danmakuController.seekTo(positionMs)
        }
    }

    private val autoHideDelayMs = 3500
    private val hideUiRunnable = Runnable { setUiIsVisible(false) }
    private val progressUpdateIntervalMs = 500
    private val progressUpdateRunnable = object : Runnable {
        override fun run() {
            updateToolbarProgress()
            viewBinding.root.postDelayed(this, progressUpdateIntervalMs.toLong())
        }
    }
    // 底部控制条（PlayerControlView）进度与已播放时长的定时更新
    private val controllerProgressIntervalMs = 500
    private var lastSubtitleText: String? = null
    private val controllerProgressRunnable = object : Runnable {
        override fun run() {
            updateControllerProgress()
            pollSubtitleText()
            viewBinding.root.postDelayed(this, controllerProgressIntervalMs.toLong())
        }
    }
    // 定期保存播放进度（每5秒）
    private val progressSaveIntervalMs = 5000L
	private val progressSaveRunnable = object : Runnable {
        override fun run() {
            savePlaybackProgress()
            viewBinding.root.postDelayed(this, progressSaveIntervalMs)
        }
    }
    // 长按持续快进/快退配置与状态
    private val longSeekIntervalMs = 200
    private val longSeekStepMs = 2000
    private val quickTapJumpMs: Long
        get() = appSettings.videoSeekForwardMs.toLong()
    private val quickTapBackMs: Long
        get() = appSettings.videoSeekBackwardMs.toLong()
    private val longSeekHandler = Handler(Looper.getMainLooper())
    private var longSeekDirection: Int = 0 // -1: back, +1: forward, 0: none
    private var longSeekAccumulatedMs: Long = 0L
    private val longSeekRunnable = object : Runnable {
        override fun run() {
            val p = mpvPlayer ?: return
            val dur = p.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE
            val newPos = (p.positionMs + longSeekDirection * longSeekStepMs).coerceIn(0, dur)
            p.seekTo(newPos)
            if (longSeekDirection != 0) {
                longSeekAccumulatedMs += abs(longSeekStepMs.toLong())
                val sec = (longSeekAccumulatedMs / 1000).toInt()
                if (longSeekDirection < 0) {
                    overlaySeekLeft.text = getString(R.string.video_rewind_time, sec.toString())
                } else {
                    overlaySeekRight.text = getString(R.string.video_fast_forward_time, sec.toString())
                }
                longSeekHandler.postDelayed(this, longSeekIntervalMs.toLong())
            }
        }
    }
    private fun startLongSeek(direction: Int) {
        longSeekDirection = direction
        longSeekAccumulatedMs = 0L
        longSeekHandler.removeCallbacks(longSeekRunnable)
        if (direction != 0) {
            showLongSeekOverlay(direction)
            longSeekHandler.post(longSeekRunnable)
        }
    }
    private fun stopLongSeek() {
        longSeekDirection = 0
        longSeekHandler.removeCallbacks(longSeekRunnable)
        // do not hide immediately, let the handler do it for better UX
        overlayHandler.removeCallbacks(hideLeftRunnable)
        overlayHandler.removeCallbacks(hideRightRunnable)
        overlayHandler.postDelayed(hideLeftRunnable, 1500)
        overlayHandler.postDelayed(hideRightRunnable, 1500)
        longSeekAccumulatedMs = 0L
    }

    // 手势提示浮层：左/右
    private lateinit var overlaySeekLeft: TextView
    private lateinit var overlaySeekRight: TextView
    private lateinit var overlayPlayPause: TextView
    private val overlayHandler = Handler(Looper.getMainLooper())
    private val hideLeftRunnable = Runnable { overlaySeekLeft.visibility = View.GONE }
    private val hideRightRunnable = Runnable { overlaySeekRight.visibility = View.GONE }
    private val hideCenterRunnable = Runnable { overlayPlayPause.visibility = View.GONE }
    private fun showOverlayLeft(text: String, durationMs: Long? = 1200) {
        overlaySeekLeft.text = text
        overlaySeekLeft.visibility = View.VISIBLE
        overlayHandler.removeCallbacks(hideLeftRunnable)
        durationMs?.let { overlayHandler.postDelayed(hideLeftRunnable, it) }
    }
    private fun showOverlayRight(text: String, durationMs: Long? = 1200) {
        overlaySeekRight.text = text
        overlaySeekRight.visibility = View.VISIBLE
        overlayHandler.removeCallbacks(hideRightRunnable)
        durationMs?.let { overlayHandler.postDelayed(hideRightRunnable, it) }
    }
    private fun showPlayPauseOverlay(text: String, durationMs: Long = 800) {
        overlayPlayPause.text = text
        overlayPlayPause.visibility = View.VISIBLE
        overlayHandler.removeCallbacks(hideCenterRunnable)
        overlayHandler.postDelayed(hideCenterRunnable, durationMs)
    }
    private fun showLongSeekOverlay(direction: Int) {
        overlayHandler.removeCallbacks(hideLeftRunnable)
        overlayHandler.removeCallbacks(hideRightRunnable)
        if (direction < 0) {
            overlaySeekRight.visibility = View.GONE
            overlaySeekLeft.text = getString(R.string.video_rewind_time, "0")
            overlaySeekLeft.visibility = View.VISIBLE
        } else if (direction > 0) {
            overlaySeekLeft.visibility = View.GONE
            overlaySeekRight.text = getString(R.string.video_fast_forward_time, "0")
            overlaySeekRight.visibility = View.VISIBLE
        }
    }
    // 垂直手势：亮度/音量调整
    private lateinit var audioManager: AudioManager
    private var verticalAdjustAccum: Float = 0f
    private var currentBrightnessNormalized: Float = -1f
    private fun initCurrentBrightness() {
        val lp = window.attributes
        currentBrightnessNormalized = if (lp.screenBrightness in 0f..1f) {
            lp.screenBrightness
        } else {
            runCatching { Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) }
                .getOrNull()
                ?.let { it / 255f }
                ?: 0.5f
        }
    }
    private fun adjustBrightnessByStep(increase: Boolean) {
        val step = 0.03f
        currentBrightnessNormalized = (currentBrightnessNormalized + if (increase) step else -step).coerceIn(0f, 1f)
        val lp = window.attributes
        lp.screenBrightness = currentBrightnessNormalized
        window.attributes = lp
        val pct = (currentBrightnessNormalized * 100).toInt()
        showOverlayLeft(getString(R.string.video_brightness, pct.toString()), durationMs = null)
    }
    private fun adjustVolumeByStep(increase: Boolean) {
        val dir = if (increase) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, 0)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val curr = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val pct = if (max > 0) ((curr * 100f) / max).toInt() else 0
        showOverlayRight(getString(R.string.video_volume, pct.toString()), durationMs = null)
    }
    
    @Inject
    lateinit var orientationHelper: ScreenOrientationHelper

    @Inject
    lateinit var historyRepository: HistoryRepository

    @Inject
    lateinit var historyUpdateUseCase: HistoryUpdateUseCase

    @Inject
    lateinit var mangaRepositoryFactory: ContentRepository.Factory

    private fun isLandscapeOrientation(): Boolean =
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun bindDanmakuOverlay() {
        val danmakuView = findViewById<DanmakuView>(
            org.skepsun.kototoro.R.id.danmaku_view
        ) ?: return
        danmakuController.attach(danmakuView)
    }

    private fun initializeMpvRuntime(): Boolean {
        return runCatching {
            mpvView.initialize(filesDir.path, cacheDir.path)
            mpvPlayer = MpvPlayer().also { player ->
                player.initialize()
                player.addListener(mpvListener)
            }
        }.onFailure { error ->
            Log.e("VideoPlayerActivity", "Failed to initialize mpv runtime", error)
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.error_occurred)
                .setMessage(R.string.video_player_native_init_failed)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    finish()
                }
                .setOnDismissListener {
                    if (!isFinishing) finish()
                }
                .show()
        }.isSuccess
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ActivityVideoPlayerBinding.inflate(layoutInflater))
        // 将布局中的 MaterialToolbar 设为 SupportActionBar，以便正确显示标题/副标题与导航按钮
        setSupportActionBar(viewBinding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        setDisplayHomeAsUp(isEnabled = true, showUpAsClose = false)
        // 确保标题靠左显示，避免被右侧动作按钮挤占
        viewBinding.toolbar.setTitleCentered(false)
        // 进度条置顶以免被菜单视图遮挡
        viewBinding.toolbarProgress.bringToFront()
        // 确保工具栏整体位于其他层级之上，避免被 PlayerView 或控制层遮挡
        viewBinding.toolbar.bringToFront()
        applyPlaybackBackground()
        mpvView = findViewById(org.skepsun.kototoro.R.id.player_view)
        if (!initializeMpvRuntime()) {
            return
        }
        bindDanmakuOverlay()
        danmakuController.setPlaybackPositionProvider(
            positionProvider = { mpvPlayer?.positionMs ?: 0L },
            playingProvider = { mpvPlayer?.isPlaying == true },
        )
        applyDanmakuSettings()

        // 记录初始工具栏高度，用于按方向动态调整高度
        originalToolbarHeightPx = viewBinding.toolbar.layoutParams.height

        // 读取传入的 ReaderState（可能来自阅读器路由，用于历史保存与初始定位）
        readerState = intent.getParcelableExtraCompat<ReaderState>(ReaderIntent.EXTRA_STATE)

        // 使用新的统一方法设置标题和副标题
        updateTitleAndSubtitle()

        // Apply default orientation: portrait when foldable unfolded in portrait; else landscape
        observeFoldableStateForOrientation()

        // 仅设置菜单点击监听；实际菜单由 rebuildToolbarMenuForOrientation() 按方向重建
        viewBinding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                org.skepsun.kototoro.R.id.action_subtitle_track -> {
                    showSubtitleTrackDialog()
                    true
                }
                org.skepsun.kototoro.R.id.action_external_player -> {
                    openInExternalPlayer()
                    true
                }
                org.skepsun.kototoro.R.id.action_cast -> {
                    showDlnaDeviceSheet()
                    true
                }
                org.skepsun.kototoro.R.id.action_quality -> {
                    showQualityDialog()
                    true
                }
                org.skepsun.kototoro.R.id.action_screenshot -> {
                    takeScreenshot()
                    true
                }
                org.skepsun.kototoro.R.id.action_pip -> {
                    enterPictureInPicture()
                    true
                }
                org.skepsun.kototoro.R.id.action_info -> {
                    openVideoDetails()
                    true
                }
                org.skepsun.kototoro.R.id.action_more -> {
                    val tag = "VideoSettingsSheet"
                    val fm = supportFragmentManager
                    if (fm.findFragmentByTag(tag) == null) {
                        VideoSettingsSheet().show(fm, tag)
                    }
                    true
                }
                else -> false
            }
        }

        rebuildToolbarMenuForOrientation()
        adjustToolbarForOrientation()

        lifecycleScope.launch {
            val url = intent.getStringExtra(AppRouter.KEY_URL)
            val sourceName = intent.getStringExtra(AppRouter.KEY_SOURCE)
            val source = ContentSource(sourceName)

            if (url.isNullOrEmpty()) {
                // No URL provided – nothing to play
                finishAfterTransition()
                return@launch
            }

            prepareAndPlay(url, source)
        }

        // 首次进入默认显示 UI（标题与底栏控件），之后按超时自动隐藏
        setUiIsVisible(true)
        updateStatusBarByToolbar()
		applyControlsAlpha()

        // 绑定手势提示浮层视图
        overlaySeekLeft = findViewById(org.skepsun.kototoro.R.id.overlay_seek_left)
        overlaySeekRight = findViewById(org.skepsun.kototoro.R.id.overlay_seek_right)
        overlayPlayPause = findViewById(org.skepsun.kototoro.R.id.overlay_play_pause)

        // 初始化音量与亮度上下文
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        initCurrentBrightness()

        // Hook player view gestures: 双击播放/暂停；单击显隐UI；长按左右持续快进/快退
        findViewById<View>(org.skepsun.kototoro.R.id.player_view)?.let { pv ->
            pv.isClickable = true

            // State variables for gestures
            var isHorizontalScrubbing = false
            var isLongPressSpeeding = false
            var initialScrubPositionStart = 0L
            var initialTouchX = 0f
            var lastScrubPosition = 0L

            val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean {
                    isHorizontalScrubbing = false
                    isLongPressSpeeding = false
                    return true
                }
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    val w = pv.width.takeIf { it > 0 } ?: -1
                    val x = e.x
                    val p = mpvPlayer
                    val allowDoubleTapSeek = appSettings.videoDoubleTapSeekEnabled
                    if (w > 0 && p != null) {
                        val left = w * 0.33f
                        val right = w * 0.67f
                        when {
                            allowDoubleTapSeek && x < left -> {
                                val newPos = (p.positionMs - quickTapBackMs).coerceAtLeast(0)
                                p.seekTo(newPos)
                                val sec = (appSettings.videoSeekBackwardMs / 1000).coerceAtLeast(1)
                                showOverlayLeft(getString(R.string.video_rewind_time, sec.toString()))
                            }
                            allowDoubleTapSeek && x > right -> {
                                val dur = p.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE
                                val newPos = (p.positionMs + quickTapJumpMs).coerceAtMost(dur)
                                p.seekTo(newPos)
                                val sec = (appSettings.videoSeekForwardMs / 1000).coerceAtLeast(1)
                                showOverlayRight(getString(R.string.video_fast_forward_time, sec.toString()))
                            }
                            else -> {
                                val wasPlaying = p.isPlaying
                                if (wasPlaying) p.pause() else p.play()
                                showPlayPauseOverlay(getString(if (wasPlaying) R.string.video_pause else R.string.video_play))
                            }
                        }
                        updatePlaybackMenu()
                        return true
                    }
                    mpvPlayer?.let { p ->
                        val wasPlaying = p.isPlaying
                        if (wasPlaying) p.pause() else p.play()
                        showPlayPauseOverlay(getString(if (wasPlaying) R.string.video_pause else R.string.video_play))
                        updatePlaybackMenu()
                    }
                    return true
                }

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    toggleUiVisibility()
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    val p = mpvPlayer ?: return
                    isLongPressSpeeding = true
                    p.setRate(2.0)
                    showPlayPauseOverlay("2.0x", 2000)
                }

                override fun onScroll(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    distanceX: Float,
                    distanceY: Float
                ): Boolean {
                    val w = pv.width.takeIf { it > 0 } ?: return false
                    val h = pv.height.takeIf { it > 0 } ?: return false
                    
                    if (isLongPressSpeeding) return false

                    // 首次判定：竖向位移显著大于横向位移时进入垂直调整模式，反之进入水平进度调整模式
                    if (verticalAdjustMode == 0 && !isHorizontalScrubbing) {
                        if (kotlin.math.abs(distanceX) > kotlin.math.abs(distanceY)) {
                            isHorizontalScrubbing = true
                            isUserScrubbing = true
                            // Capture actual start position and touch X when horizontal drag is confirmed
                            initialScrubPositionStart = mpvPlayer?.positionMs ?: 0L
                            initialTouchX = e2.x
                            lastScrubPosition = initialScrubPositionStart
                            // Auto-show controller when scrubbing starts
                            setUiIsVisible(true)
                        } else if (kotlin.math.abs(distanceY) > kotlin.math.abs(distanceX)) {
                            val startX = e1?.x ?: e2.x
                            verticalAdjustMode = if (startX < w / 2f) -1 else +1
                            verticalAdjustAccum = 0f
                            // 初始提示
                            if (verticalAdjustMode < 0) {
                                val pct = (currentBrightnessNormalized.coerceIn(0f, 1f) * 100).toInt()
                                showOverlayLeft(getString(R.string.video_brightness, pct.toString()), durationMs = null)
                            } else {
                                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                val curr = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                val pct = if (max > 0) ((curr * 100f) / max).toInt() else 0
                                showOverlayRight(getString(R.string.video_volume, pct.toString()), durationMs = null)
                            }
                        }
                    }

                    if (isHorizontalScrubbing) {
                        val duration = mpvPlayer?.durationMs ?: return true
                        if (duration <= 0) return true
                        
                        // Proportional Seek: One screen width equals the entire video duration
                        // This makes the dot on the seek bar track the finger 1:1
                        val deltaX = e2.x - initialTouchX
                        val seekOffset = (deltaX / w * duration).toLong()
                        lastScrubPosition = (initialScrubPositionStart + seekOffset).coerceIn(0L, duration)
                        
                        showSeekFeedback(lastScrubPosition, duration, seekOffset)
                        
                        return true
                    }

                    if (verticalAdjustMode != 0) {
                        val ratioChange = (distanceY) / h.toFloat()
                        verticalAdjustAccum += ratioChange
                        val unit = 0.02f
                        while (kotlin.math.abs(verticalAdjustAccum) >= unit) {
                            val increase = verticalAdjustAccum > 0
                            if (verticalAdjustMode < 0) adjustBrightnessByStep(increase) else adjustVolumeByStep(increase)
                            verticalAdjustAccum += if (increase) -unit else unit
                        }
                        return true
                    }
                    return false
                }
            })

            pv.setOnTouchListener { v, event ->
                val handled = detector.onTouchEvent(event)
                when (event.actionMasked) {
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val wasVerticalAdjusting = verticalAdjustMode != 0
                        // Restore from long press speed
                        if (isLongPressSpeeding) {
                            val originalSpeed = appSettings.videoPlaybackSpeed.toDouble()
                            mpvPlayer?.setRate(originalSpeed)
                            isLongPressSpeeding = false
                        }
                        
                        // Action final horizontal scrub seek
                        if (isHorizontalScrubbing) {
                            mpvPlayer?.seekTo(lastScrubPosition)
                            isHorizontalScrubbing = false
                            isUserScrubbing = false
                            hideSeekFeedback()
                            // Auto-hide controller after scrubbing ends
                            setUiIsVisible(false)
                        }
                        
                        if (longSeekDirection != 0) {
                            stopLongSeek()
                        }
                        verticalAdjustMode = 0
                        verticalAdjustAccum = 0f
                        v.performClick()

                        if (wasVerticalAdjusting) {
                            // Keep the last brightness/volume feedback visible briefly after finger release.
                            overlayHandler.removeCallbacks(hideLeftRunnable)
                            overlayHandler.removeCallbacks(hideRightRunnable)
                            overlayHandler.postDelayed(hideLeftRunnable, 1500)
                            overlayHandler.postDelayed(hideRightRunnable, 1500)
                        }
                    }
                }
                handled || true
            }
        }

        // 兜底点击区域：当控制器隐藏时，任何空白处点击也可唤回 UI
        viewBinding.root.isClickable = true
        viewBinding.root.setOnClickListener { setUiIsVisible(true) }

        // 同步系统导航栏颜色为底栏背景色，实现与小白条区域的视觉合并
        runCatching {
            val navColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = navColor
        }

        // Wire controller buttons: pages and settings
        wireControllerButtons()

        // 外部控制器初始由 Activity 管理显隐；不直接改动 DockedToolbar 的可见性
    }
    
    private fun wireControllerButtons() {
        val ctl = findViewById<PlayerControlView>(org.skepsun.kototoro.R.id.controller)
        ctl?.bringToFront()

        // 进度条可拖拽/点击快进快退：显式监听用户 scrub，避免定时刷新覆盖拖动状态
        if (!isTimeBarListenerBound) {
            ctl?.findViewById<androidx.media3.ui.DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)?.let { timeBar ->
                timeBar.isClickable = true
                timeBar.isFocusable = true
                timeBar.isFocusableInTouchMode = true
                // 避免父级的点击/手势拦截 TimeBar 的拖动事件
                timeBar.setOnTouchListener { v, ev ->
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    false
                }
                val controlView = ctl
                timeBar.addListener(object : TimeBar.OnScrubListener {
                    override fun onScrubStart(timeBar: TimeBar, position: Long) {
                        isUserScrubbing = true
                    }

                    override fun onScrubMove(timeBar: TimeBar, position: Long) {
                        // 拖动时同步显示当前拖动位置，提升反馈一致性
                        val showHours = (mpvPlayer?.durationMs ?: 0L) >= 3600_000L
                        controlView?.findViewById<TextView>(androidx.media3.ui.R.id.exo_position)?.text = formatTimeMs(position, forceHours = showHours)
                    }

                    override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
                        isUserScrubbing = false
                        if (!canceled) {
                            val p = mpvPlayer
                            if (p != null && p.durationMs > 0) {
                                p.seekTo(position)
                            }
                        }
                    }
                })
                isTimeBarListenerBound = true
            }
        }

        findViewById<View>(org.skepsun.kototoro.R.id.button_pages_thumbs)?.let { btn ->
            val parcelable = intent.getParcelableExtraCompat<ParcelableContent>(AppRouter.KEY_MANGA)
            btn.isVisible = parcelable != null
            btn.setOnClickListener {
                AppRouter(this).showChapterPagesSheet()
            }
        }
        findViewById<View>(org.skepsun.kototoro.R.id.button_quality)?.setOnClickListener {
            showQualityDialog()
        }
        ctl?.findViewById<View>(androidx.media3.ui.R.id.exo_rew)?.apply {
            isVisible = appSettings.videoDoubleTapSeekEnabled
            setOnClickListener {
                mpvPlayer?.let { p ->
                    val pos = (p.positionMs - appSettings.videoSeekBackwardMs).coerceAtLeast(0)
                    p.seekTo(pos)
                }
            }
        }
        ctl?.findViewById<View>(androidx.media3.ui.R.id.exo_play_pause)?.setOnClickListener {
            mpvPlayer?.let { p ->
                if (p.isPlaying) p.pause() else p.play()
                updatePlaybackMenu()
            }
        }
        ctl?.findViewById<View>(androidx.media3.ui.R.id.exo_play_pause)?.apply {
            isEnabled = true
            isClickable = true
            alpha = 1f
        }
        ctl?.findViewById<View>(androidx.media3.ui.R.id.exo_ffwd)?.apply {
            isVisible = appSettings.videoDoubleTapSeekEnabled
            setOnClickListener {
                mpvPlayer?.let { p ->
                    val pos = (p.positionMs + appSettings.videoSeekForwardMs).coerceAtMost(p.durationMs.coerceAtLeast(0))
                    p.seekTo(pos)
                }
            }
        }
        ctl?.findViewById<View>(org.skepsun.kototoro.R.id.button_prev_chapter)?.setOnClickListener {
            navigateChapter(-1)
        }
        ctl?.findViewById<View>(org.skepsun.kototoro.R.id.button_next_chapter)?.setOnClickListener {
            navigateChapter(1)
        }
        ctl?.findViewById<View>(org.skepsun.kototoro.R.id.button_danmaku_settings)?.setOnClickListener {
            val fm = supportFragmentManager
            val tag = "VideoDanmakuSettingsSheet"
            if (fm.findFragmentByTag(tag) == null) {
                VideoDanmakuSettingsSheet().show(fm, tag)
            }
        }
        ctl?.findViewById<View>(org.skepsun.kototoro.R.id.button_danmaku_toggle)?.setOnClickListener {
            appSettings.videoDanmakuEnabled = !appSettings.videoDanmakuEnabled
            applyDanmakuSettings()
            updateDanmakuToggleState()
        }
        val danmakuInput = ctl?.findViewById<android.widget.EditText>(
            org.skepsun.kototoro.R.id.edit_danmaku_input
        )
        danmakuInput?.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                setUiIsVisible(true)
                viewBinding.root.removeCallbacks(hideUiRunnable)
            } else if (isUiVisible) {
                viewBinding.root.removeCallbacks(hideUiRunnable)
                viewBinding.root.postDelayed(hideUiRunnable, autoHideDelayMs.toLong())
            }
        }
        ctl?.findViewById<View>(org.skepsun.kototoro.R.id.button_danmaku_send)?.setOnClickListener {
            val text = danmakuInput?.text?.toString()?.trim().orEmpty()
            if (text.isBlank()) return@setOnClickListener
            sendLocalDanmaku(text)
            danmakuInput?.setText("")
        }
        ctl?.findViewById<View>(org.skepsun.kototoro.R.id.button_rotate_screen)?.setOnClickListener {
            orientationHelper.isLandscape = !orientationHelper.isLandscape
        }
        danmakuInput?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                val text = danmakuInput.text?.toString()?.trim().orEmpty()
                if (text.isNotBlank()) {
                    sendLocalDanmaku(text)
                    danmakuInput.setText("")
                }
                true
            } else {
                false
            }
        }

        updateChapterNavButtons()
    }

    private fun updateQualityButtonVisibility() {
        findViewById<View>(org.skepsun.kototoro.R.id.button_quality)?.isVisible = availableVideos.isNotEmpty()
    }

    private fun observeFoldableStateForOrientation() {
        val flow = FoldableUtils.observeFoldableState(this, this)
        lifecycleScope.launch {
            flow.collect { unfolded ->
                isFoldUnfolded = unfolded
                // 动态应用：折叠屏状态变化时自动调整，若已锁定则尊重用户设置
                if (!orientationHelper.isLocked) {
                    val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
                    val shouldPortrait = unfolded && isPortrait
                    orientationHelper.isLandscape = !shouldPortrait
                }
            }
        }
    }

    private fun prepareAndPlay(url: String, source: ParsersContentSource?, headers: Map<String, String>? = null) {
        val normalizedUrl = TVBoxPlayback.normalizeLocator(url.trim())
        val lastSegment = runCatching { Uri.parse(normalizedUrl).lastPathSegment }.getOrNull() ?: normalizedUrl
        val lowerUrl = normalizedUrl.lowercase()
        val isHttpLike = lowerUrl.startsWith("http://") || lowerUrl.startsWith("https://")
        val isHtmlPlaybackPage = isHttpLike && TVBoxPlayback.looksLikeHtmlPlaybackPage(normalizedUrl)
        val isDirectPlaybackUrl = TVBoxPlayback.looksLikeDirectPlaybackUrl(normalizedUrl)
        val isDirectStream = lastSegment.endsWith(".m3u8", ignoreCase = true) ||
            lastSegment.endsWith(".mp4", ignoreCase = true) ||
            isDirectPlaybackUrl
        val isDirectLocator = lowerUrl.startsWith("magnet:") ||
            lowerUrl.startsWith("thunder:") ||
            lowerUrl.startsWith("ed2k:") ||
            lowerUrl.startsWith("ftp://") ||
            lowerUrl.startsWith("rtsp://") ||
            lowerUrl.startsWith("rtmp://") ||
            lowerUrl.startsWith("mms://")
        val isResolvedPlaybackUrl = isDirectStream || isDirectLocator || (isHttpLike && headers != null && !isHtmlPlaybackPage)
        val manga = intent.getParcelableExtraCompat<ParcelableContent>(AppRouter.KEY_MANGA)?.manga
        val currentState = readerState ?: intent.getParcelableExtraCompat<ReaderState>(ReaderIntent.EXTRA_STATE)
        val localUrl = resolveLocalVideoUrl(manga, currentState, url)
        if (localUrl != null) {
            currentVideoSource = manga?.source
            availableVideos = emptyList()
            currentVideoIndex = 0
            updateQualityButtonVisibility()
            startMpvPlayback(localUrl, manga?.source, headers = null)
            return
        }

        android.util.Log.d("VideoPlayer", "prepareAndPlay: url=$normalizedUrl, manga=${manga?.title}, chapters=${manga?.chapters?.size}, state=$currentState, isDirectStream=$isDirectStream")

        if (isHtmlPlaybackPage) {
            resolvePlaybackPageAndPlay(
                url = normalizedUrl,
                source = source,
                headers = headers,
            )
            return
        }

        if (isResolvedPlaybackUrl) {
            currentVideoSource = source
            availableVideos = emptyList()
            currentVideoIndex = 0
            updateQualityButtonVisibility()
            val mergedHeaders = if (headers.isNullOrEmpty() && source != null) {
                runCatching { mangaRepositoryFactory.create(source).getRequestHeaders() }.getOrDefault(emptyMap())
            } else {
                headers
            }
            if (lowerUrl.startsWith("magnet:") || lowerUrl.startsWith("thunder:") || lowerUrl.startsWith("ed2k:")) {
                android.util.Log.w("VideoPlayer", "Unsupported direct playback scheme: $url")
                Snackbar.make(
                    viewBinding.root,
                    org.skepsun.kototoro.R.string.error_occurred,
                    Snackbar.LENGTH_LONG,
                ).show()
                return
            }
            startMpvPlayback(normalizedUrl, source, mergedHeaders)
            return
        }

        if (manga != null && !manga.chapters.isNullOrEmpty()) {
            lifecycleScope.launch {
                try {
                    val repo = mangaRepositoryFactory.create(manga.source)
                    val chapters = manga.chapters ?: emptyList()
                    val currentChapter = if (currentState != null) {
                        chapters.find { it.id == currentState.chapterId }
                    } else {
                        chapters.find { it.url == url }
                    } ?: chapters.firstOrNull()

                    if (currentChapter != null) {
                        android.util.Log.d("VideoPlayer", "Loading current chapter: ${currentChapter.title} (id=${currentChapter.id})")
                        val resolved = runCatching {
                            if (currentChapter.url.startsWith("file://") || currentChapter.url.startsWith("/") || currentChapter.url.startsWith("content://")) {
                                throw IllegalStateException("Local downloaded video format is unsupported or corrupted (possibly downloaded as .cbz). Please delete the download and re-download it.")
                            }
                            if (repo is AniyomiAnimeRepository) {
                                val videos = repo.getVideoListForChapter(currentChapter)
                                    .filter { it.videoUrl.isNotBlank() }
                                if (videos.isNotEmpty()) {
                                    availableVideos = videos
                                    updateQualityButtonVisibility()
                                    currentVideoSource = manga.source
                                    currentVideoIndex = videos.indexOfFirst { it.preferred }
                                        .takeIf { it >= 0 } ?: 0
                                    val selected = videos[currentVideoIndex]
                                    val mergedHeaders = mergeHeaders(repo.getRequestHeaders(), headersToMap(selected.headers))
                                    pendingExternalSubtitles = selected.subtitleTracks
                                    pendingExternalAudio = selected.audioTracks
                                    startMpvPlayback(
                                        selected.videoUrl,
                                        manga.source,
                                        mergedHeaders,
                                    )
                                    return@runCatching true
                                }
                            }
                            val pages = repo.getPages(currentChapter)
                            val page = pages.firstOrNull()
                            if (page != null) {
                                val streamUrl = repo.getPageUrl(page)
                                val streamHeaders = mergeHeaders(repo.getRequestHeaders(), page.headers)
                                availableVideos = emptyList()
                                currentVideoIndex = 0
                                updateQualityButtonVisibility()
                                currentVideoSource = manga.source
                                prepareAndPlay(streamUrl, manga.source, streamHeaders)
                                return@runCatching true
                            }
                            false
                        }.onFailure { e ->
                            android.util.Log.e("VideoPlayer", "Failed to get stream URL", e)
                        }.getOrNull() ?: false

                        if (resolved) {
                            readerState = ReaderState(currentChapter.id, 0, 0)
                            updateChapterNavButtons()
                            android.util.Log.d("VideoPlayer", "Playing chapter: ${currentChapter.title}")
                        } else {
                            android.util.Log.e("VideoPlayer", "Failed to resolve stream URL for current chapter")
                            Snackbar.make(
                                viewBinding.root,
                                org.skepsun.kototoro.R.string.error_occurred,
                                Snackbar.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        android.util.Log.e("VideoPlayer", "Current chapter not found")
                        Snackbar.make(
                            viewBinding.root,
                            org.skepsun.kototoro.R.string.error_occurred,
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("VideoPlayer", "Failed to load video", e)
                    Snackbar.make(
                        viewBinding.root,
                        org.skepsun.kototoro.R.string.error_occurred,
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        } else {
            android.util.Log.e("VideoPlayer", "Cannot resolve non-direct URL without manga info")
            Snackbar.make(
                viewBinding.root,
                org.skepsun.kototoro.R.string.error_occurred,
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun resolvePlaybackPageAndPlay(
        url: String,
        source: ParsersContentSource?,
        headers: Map<String, String>?,
    ) {
        lifecycleScope.launch {
            val sniffed = runCatching {
                webViewExecutor.sniffMediaUrl(
                    url = url,
                    headers = headers,
                )
            }.onFailure {
                Log.w("VideoPlayer", "Failed to sniff playback page: $url", it)
            }.getOrNull()

            if (sniffed != null) {
                Log.d("VideoPlayer", "Sniffed playable media from web page: ${sniffed.url}")
                currentVideoSource = source
                availableVideos = emptyList()
                currentVideoIndex = 0
                updateQualityButtonVisibility()
                startMpvPlayback(
                    url = sniffed.url,
                    source = source,
                    headers = mergeHeaders(headers, sniffed.headers),
                )
                return@launch
            }

            Log.w("VideoPlayer", "No playable media sniffed from web page, fallback to browser: $url")
            AppRouter(this@VideoPlayerActivity).openBrowser(
                url = url,
                source = source,
                title = intent.getParcelableExtraCompat<ParcelableContent>(AppRouter.KEY_MANGA)?.manga?.title,
            )
            finish()
        }
    }

    private fun resolveLocalVideoUrl(
        manga: org.skepsun.kototoro.parsers.model.Content?,
        state: ReaderState?,
        url: String,
    ): String? {
        val chapters = manga?.chapters ?: return null
        val currentChapter = if (state != null) {
            chapters.find { it.id == state.chapterId }
        } else {
            chapters.find { it.url == url }
        } ?: return null
        val file = videoDownloadIndex.getFile(manga.id, currentChapter.id) ?: return null
        return file.toUri().toString()
    }

    private fun startMpvPlayback(
        url: String,
        source: ParsersContentSource?,
        headers: Map<String, String>? = null,
        startMs: Long? = null,
    ) {
        hasRestoredProgress = false
        currentMediaUrl = url
        currentVideoSource = source
        currentMediaHeaders = headers
        maybeLoadDanmaku()
        val mergedHeaders = headers.orEmpty()
        videoLocalCacheProxy.resetSessionStats("startMpvPlayback")
        val initialStartMs = startMs ?: resolveSavedPlaybackProgress(url)
        skipHistorySeekForCurrentMedia = initialStartMs != null
        mpvPlayer?.setVideoOutput(resolveVideoRenderer())
        if (appSettings.videoDecoderMode == VideoDecoderMode.SOFTWARE) {
            mpvPlayer?.setHardwareDecodingMode("no")
        } else {
            mpvPlayer?.setHardwareDecodingMode("auto")
        }
        
        // Apply optimized streaming options for network stability
        mpvPlayer?.setStreamingOptions(appSettings.videoCacheSizeMb)
        
        applyPlaybackOptions()
        applyAspectRatio()
        applyGradientAlpha()
        val defaultSpeed = appSettings.videoDefaultSpeed
        appSettings.videoPlaybackSpeed = defaultSpeed
        mpvPlayer?.setRate(defaultSpeed.toDouble())

        Log.d("VideoPlayerActivity", "Loading media. URL: $url, Headers: ${mergedHeaders.keys}")
        val isHttpSource = url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
        val useProxy = shouldUseLocalProxy(url, isHttpSource)
        val (playUrl, playHeaders) = if (useProxy) {
            runCatching {
                val proxyUrl = videoLocalCacheProxy.getProxyUrl(url, mergedHeaders)
                proxyUrl to mergedHeaders
            }.getOrElse {
                Log.w("VideoPlayerActivity", "Proxy cache unavailable, fallback to origin URL", it)
                url to mergedHeaders
            }
        } else {
            Log.d("VideoPlayerActivity", "Bypass local proxy for URL: $url")
            url to mergedHeaders
        }
        Log.d("VideoPlayerActivity", "Resolved playback URL: $playUrl")
        
        val doLoad = {
            mpvPlayer?.load(playUrl, playHeaders, initialStartMs)
            mpvPlayer?.play()
        }
        
        val holder = mpvView.holder
        val surface = holder.surface
        if (surface != null && surface.isValid) {
            doLoad()
        } else {
            Log.d("VideoPlayerActivity", "Surface not ready, waiting for surfaceCreated to load MPV")
            holder.addCallback(object : android.view.SurfaceHolder.Callback {
                override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                    holder.removeCallback(this)
                    Log.d("VideoPlayerActivity", "Surface ready, loading MPV now")
                    mpvView.post { doLoad() }
                }
                override fun surfaceChanged(holder: android.view.SurfaceHolder, format: Int, width: Int, height: Int) {}
                override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {}
            })
        }
        updateTitleAndSubtitle()
        updatePlaybackMenu()
        if (!skipHistorySeekForCurrentMedia) {
            lifecycleScope.launch {
                restoreInitialSeekPercentFromHistory()
            }
        }
    }

    private fun shouldUseLocalProxy(url: String, isHttpSource: Boolean): Boolean {
        if (!isHttpSource) return false
        val host = runCatching { Uri.parse(url).host.orEmpty().lowercase() }.getOrDefault("")
        if (host == "127.0.0.1" || host == "localhost") {
            Log.d("VideoPlayerActivity", "Bypass local proxy for loopback URL: $url")
            return false
        }
        val lower = url.lowercase()
        val isMpd = lower.contains(".mpd")
        if (isMpd) return false
        val isM3u8 = lower.contains(".m3u8")
        if (isM3u8 && !ENABLE_M3U8_PROXY_CACHE) {
            Log.d("VideoPlayerActivity", "m3u8 proxy cache disabled by feature flag")
            return false
        }
        return true
    }

    private fun resolveVideoRenderer(): String {
        return when (appSettings.videoRendererMode) {
            VideoRendererMode.AUTO -> {
                if (Build.VERSION.SDK_INT >= 34) "gpu-next" else "gpu"
            }
            VideoRendererMode.GPU -> "gpu"
            VideoRendererMode.GPU_NEXT -> "gpu-next"
            VideoRendererMode.MEDIACODEC_EMBED -> "mediacodec_embed"
        }
    }

    private fun headersToMap(headers: okhttp3.Headers?): Map<String, String> {
        if (headers == null) return emptyMap()
        val map = mutableMapOf<String, String>()
        for (i in 0 until headers.size) {
            map[headers.name(i)] = headers.value(i)
        }
        return map
    }

    private fun mergeHeaders(
        base: Map<String, String>?,
        extra: Map<String, String>?,
    ): Map<String, String> {
        if (base.isNullOrEmpty()) return extra.orEmpty()
        if (extra.isNullOrEmpty()) return base
        return base.toMutableMap().apply { putAll(extra) }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        rebuildToolbarMenuForOrientation()
        adjustToolbarForOrientation()
        updateStatusBarByToolbar()
		applyControlsAlpha()
        
        // Update title/subtitle after configuration change to ensure they persist
        updateTitleAndSubtitle()
        
        // 屏幕旋转后重新绑定按钮事件（布局已通过 layout-port 自动切换）
        findViewById<PlayerControlView>(org.skepsun.kototoro.R.id.controller)?.post {
            wireControllerButtons()
        }
    }

    private fun toggleUiVisibility() {
        setUiIsVisible(!isUiVisible)
    }

    private fun applyControlsAlpha() {
        val alpha = appSettings.videoControlsAlpha.coerceIn(0f, 1f)
        val colored = android.graphics.Color.argb((alpha * 255f).toInt(), 0, 0, 0)
        val useGradient = appSettings.videoGradientAlpha.coerceIn(0f, 1f) > 0f

        // 仅调整背景透明度，避免工具栏文本被整体 alpha 变淡
        viewBinding.toolbar.alpha = 1f
        viewBinding.toolbar.setBackgroundColor(if (useGradient) android.graphics.Color.TRANSPARENT else colored)
        val titleColor = MaterialColors.getColor(viewBinding.toolbar, com.google.android.material.R.attr.colorOnSurface)
        val subtitleColor = MaterialColors.getColor(viewBinding.toolbar, com.google.android.material.R.attr.colorOnSurfaceVariant)
        viewBinding.toolbar.setTitleTextColor(titleColor)
        viewBinding.toolbar.setSubtitleTextColor(subtitleColor)

        findViewById<PlayerControlView>(org.skepsun.kototoro.R.id.controller)?.let { ctl ->
            ctl.alpha = 1f
            ctl.findViewById<View>(org.skepsun.kototoro.R.id.toolbar_docked)
                ?.setBackgroundColor(if (useGradient) android.graphics.Color.TRANSPARENT else colored)
        }
        viewBinding.root.findViewById<View>(org.skepsun.kototoro.R.id.status_bar_scrim)?.apply {
            this.alpha = 1f
            setBackgroundColor(if (useGradient) android.graphics.Color.TRANSPARENT else colored)
        }
        @Suppress("DEPRECATION")
        window.statusBarColor = if (useGradient) android.graphics.Color.TRANSPARENT else colored
        @Suppress("DEPRECATION")
        window.navigationBarColor = if (useGradient) android.graphics.Color.TRANSPARENT else colored
        val isLight = ColorUtils.calculateLuminance(colored) > 0.5
        WindowInsetsControllerCompat(window, viewBinding.root).setAppearanceLightStatusBars(isLight)
        applyGradientAlpha()
    }

    private fun applyGradientAlpha() {
        val alpha = appSettings.videoGradientAlpha.coerceIn(0f, 1f)
        val topGradient = viewBinding.root.findViewById<View>(org.skepsun.kototoro.R.id.top_gradient)
        val bottomGradient = viewBinding.root.findViewById<View>(org.skepsun.kototoro.R.id.bottom_gradient)
        topGradient?.alpha = alpha
        bottomGradient?.alpha = alpha
        val visible = alpha > 0f && isUiVisible
        topGradient?.isVisible = visible
        bottomGradient?.isVisible = visible
    }

    private fun setUiIsVisible(visible: Boolean) {
        isUiVisible = visible
        val isLandscape = isLandscapeOrientation()
        val showTopBar = visible
        viewBinding.toolbar.isVisible = showTopBar
        // 顶部状态栏遮罩与工具栏同步显隐（横屏隐藏，避免拥挤）
        viewBinding.root.findViewById<View>(org.skepsun.kototoro.R.id.status_bar_scrim)?.isVisible = showTopBar
        viewBinding.root.findViewById<View>(org.skepsun.kototoro.R.id.top_gradient)?.isVisible = showTopBar
        viewBinding.root.findViewById<View>(org.skepsun.kototoro.R.id.bottom_gradient)?.isVisible = showTopBar
        if (visible) {
            // 确保渐变在视频之上，但在控件之下，避免遮住底部控件导致变暗
            viewBinding.root.findViewById<View>(org.skepsun.kototoro.R.id.top_gradient)?.bringToFront()
            viewBinding.root.findViewById<View>(org.skepsun.kototoro.R.id.bottom_gradient)?.bringToFront()
            viewBinding.root.findViewById<View>(org.skepsun.kototoro.R.id.status_bar_scrim)?.bringToFront()
            viewBinding.toolbar.bringToFront()
            findViewById<PlayerControlView>(org.skepsun.kototoro.R.id.controller)?.bringToFront()
            // Make sure feedback overlays are on top of everything including the controller
            findViewById<View>(org.skepsun.kototoro.R.id.seek_feedback_layout)?.bringToFront()
            findViewById<View>(org.skepsun.kototoro.R.id.overlay_seek_left)?.bringToFront()
            findViewById<View>(org.skepsun.kototoro.R.id.overlay_seek_right)?.bringToFront()
            findViewById<View>(org.skepsun.kototoro.R.id.overlay_play_pause)?.bringToFront()
        }
        // 播放器控件显隐与系统栏解耦，避免折叠屏任务栏随下工具栏一起唤出并重叠
        systemUiController.setSystemUiVisible(false)
        updateStatusBarByToolbar()
        if (visible) {
            rebuildToolbarMenuForOrientation()
            adjustToolbarForOrientation()
            updatePlayPauseButton()
        }
        // 同步外部 PlayerControlView 与顶部工具栏显隐
        findViewById<PlayerControlView>(org.skepsun.kototoro.R.id.controller)?.let { ctl ->
            if (visible) {
                ctl.visibility = View.VISIBLE
                ctl.alpha = 1f
                applyControllerTint(ctl)
            } else {
                ctl.visibility = View.GONE
            }
        }
        // 由外部 PlayerControlView 控制底部显隐
        viewBinding.root.requestApplyInsets()
        if (visible) {
            viewBinding.root.removeCallbacks(hideUiRunnable)
            // If user is scrubbing (either horizontal or vertical), DON'T start auto-hide timer
            if (!isHorizontalScrubbing && !isUserScrubbing && verticalAdjustMode == 0) {
                viewBinding.root.postDelayed(hideUiRunnable, autoHideDelayMs.toLong())
            }
            viewBinding.root.removeCallbacks(progressUpdateRunnable)
            updateToolbarProgress()
            viewBinding.root.postDelayed(progressUpdateRunnable, progressUpdateIntervalMs.toLong())
            // 同步启动底部控制条定时更新
            viewBinding.root.removeCallbacks(controllerProgressRunnable)
            updateControllerProgress()
            viewBinding.root.postDelayed(controllerProgressRunnable, controllerProgressIntervalMs.toLong())
        } else {
            viewBinding.root.removeCallbacks(hideUiRunnable)
            viewBinding.root.removeCallbacks(progressUpdateRunnable)
            viewBinding.root.removeCallbacks(controllerProgressRunnable)
            viewBinding.toolbarProgress.isVisible = false
            viewBinding.toolbar.menu.clear()
        }
    }

    private fun rebuildToolbarMenuForOrientation() {
        val menu = viewBinding.toolbar.menu
        menu.clear()
        viewBinding.toolbar.inflateMenu(org.skepsun.kototoro.R.menu.menu_video_player)
        // Force subtitle button to always show as icon (not in overflow)
        menu.findItem(org.skepsun.kototoro.R.id.action_subtitle_track)?.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
        viewBinding.toolbar.menuView?.isVisible = true
    }

    // 按方向调整工具栏高度：横屏恢复初始高度以容纳标题与菜单；竖屏压缩为 wrap_content 仅显示进度条
    private fun adjustToolbarForOrientation() {
        val lp = viewBinding.toolbar.layoutParams
        if (isLandscapeOrientation()) {
            if (originalToolbarHeightPx > 0) {
                lp.height = originalToolbarHeightPx
            }
        } else {
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        viewBinding.toolbar.minimumHeight = 0
        viewBinding.toolbar.requestLayout()
        viewBinding.toolbar.menuView?.isVisible = true
    }

    private fun updatePlaybackMenu() {
        updatePlayPauseButton()
    }

    private fun updatePlayPauseButton() {
        val ctl = findViewById<PlayerControlView>(org.skepsun.kototoro.R.id.controller) ?: return
        val btn = ctl.findViewById<android.widget.ImageButton>(androidx.media3.ui.R.id.exo_play_pause) ?: return
        val isPlaying = mpvPlayer?.isPlaying == true
        btn.isEnabled = true
        btn.isClickable = true
        btn.alpha = 1f
        btn.setImageResource(
            if (isPlaying) org.skepsun.kototoro.R.drawable.ic_pause
            else org.skepsun.kototoro.R.drawable.ic_play
        )
        btn.contentDescription = getString(
            if (isPlaying) org.skepsun.kototoro.R.string.pause
            else org.skepsun.kototoro.R.string.play
        )
    }

    private fun applyControllerTint(ctl: PlayerControlView) {
        val white = Color.WHITE
        val whiteList = ColorStateList.valueOf(white)
        ctl.findViewById<TextView>(androidx.media3.ui.R.id.exo_position)?.setTextColor(white)
        ctl.findViewById<TextView>(androidx.media3.ui.R.id.exo_duration)?.setTextColor(white)
        ctl.findViewById<androidx.media3.ui.DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)?.let { timeBar ->
            timeBar.setPlayedColor(white)
            timeBar.setBufferedColor(0x99FFFFFF.toInt())
            timeBar.setUnplayedColor(0x55FFFFFF.toInt())
            timeBar.setScrubberColor(white)
        }
        ctl.findViewById<android.widget.ImageButton>(androidx.media3.ui.R.id.exo_play_pause)
            ?.setColorFilter(white)
        val iconButtons = listOf(
            org.skepsun.kototoro.R.id.button_prev_chapter,
            org.skepsun.kototoro.R.id.button_next_chapter,
            org.skepsun.kototoro.R.id.button_pages_thumbs,
            org.skepsun.kototoro.R.id.button_danmaku_send,
            org.skepsun.kototoro.R.id.button_rotate_screen,
            org.skepsun.kototoro.R.id.button_quality,
        )
        iconButtons.forEach { id ->
            val view = ctl.findViewById<View>(id)
            when (view) {
                is android.widget.ImageButton -> view.setColorFilter(white)
                is com.google.android.material.button.MaterialButton -> view.iconTint = whiteList
            }
        }
        ctl.findViewById<TextView>(
            org.skepsun.kototoro.R.id.button_danmaku_toggle,
        )?.setTextColor(white)
        ctl.findViewById<android.widget.ImageView>(
            org.skepsun.kototoro.R.id.button_danmaku_settings,
        )?.setColorFilter(white)
        updateDanmakuToggleState()
    }

    private fun updateDanmakuToggleState() {
        val ctl = findViewById<PlayerControlView>(org.skepsun.kototoro.R.id.controller) ?: return
        val btn = ctl.findViewById<View>(org.skepsun.kototoro.R.id.button_danmaku_toggle) ?: return
        val enabled = appSettings.videoDanmakuEnabled
        btn.alpha = if (enabled) 1f else 0.45f
        btn.setBackgroundResource(org.skepsun.kototoro.R.drawable.bg_danmaku_tv)
    }

    private fun updateToolbarProgress() {
        val indicator = viewBinding.toolbarProgress
        // 顶部进度条不再显示
        indicator.isVisible = false
    }

    // 简单时间格式化（mm:ss 或 hh:mm:ss）
    // forceHours: 当总时长包含小时时，强制显示小时位保持格式一致
    private fun formatTimeMs(ms: Long, forceHours: Boolean = false): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val hours = (totalSec / 3600)
        val minutes = ((totalSec % 3600) / 60)
        val seconds = (totalSec % 60)
        return if (hours > 0 || forceHours) String.format("%02d:%02d:%02d", hours, minutes, seconds)
        else String.format("%02d:%02d", minutes, seconds)
    }

    // 手动驱动底部控制条（DefaultTimeBar 与已播放时长文本）定时刷新
    private fun updateControllerProgress() {
        if (!isUiVisible) return
        // 用户拖动时不要覆盖 timebar 的临时位置，否则会导致“拖不动/点不准”的体验
        if (isUserScrubbing) return
        val ctl = findViewById<androidx.media3.ui.PlayerControlView>(org.skepsun.kototoro.R.id.controller) ?: return
        if (ctl.visibility != View.VISIBLE) return

        val p = mpvPlayer ?: return
        val duration = p.durationMs
        val position = p.positionMs
        val buffered = position

        // 判断是否需要显示小时位（总时长超过 1 小时）
        val showHours = duration >= 3600_000L

        // 更新文本：当前播放位置 + 总时长
        runCatching {
            val posTv = ctl.findViewById<TextView>(androidx.media3.ui.R.id.exo_position)
            val durTv = ctl.findViewById<TextView>(androidx.media3.ui.R.id.exo_duration)
            posTv?.text = formatTimeMs(position, forceHours = showHours)
            if (duration > 0) {
                durTv?.text = formatTimeMs(duration)
            }
        }

        // 更新时间条进度
        runCatching {
            val timeBar = ctl.findViewById<androidx.media3.ui.DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)
            val seekable = duration > 0
            timeBar?.isEnabled = seekable
            timeBar?.isClickable = seekable
            if (duration > 0) {
                timeBar?.setDuration(duration)
                timeBar?.setBufferedPosition(buffered)
                timeBar?.setPosition(position)
            }
        }
        updatePlayPauseButton()
    }

    private fun updateStatusBarByToolbar() {
        val color = android.graphics.Color.TRANSPARENT
        val isLight = ColorUtils.calculateLuminance(color) > 0.5
        WindowInsetsControllerCompat(window, viewBinding.root).setAppearanceLightStatusBars(isLight)
        viewBinding.root.findViewById<View>(org.skepsun.kototoro.R.id.status_bar_scrim)?.setBackgroundColor(color)
        @Suppress("DEPRECATION")
        window.statusBarColor = color
        @Suppress("DEPRECATION")
        window.navigationBarColor = color
    }

    private fun applyPlaybackBackground() {
        val drawable = appSettings.videoBackground.resolve(this)
        viewBinding.root.background = drawable
        // SurfaceView 使用独立 Surface 渲染视频，保持背景透明避免遮挡画面
        viewBinding.playerView.background = null
    }

    private fun deriveEpisodeTitle(url: String): String {
        return runCatching {
            val uri = Uri.parse(url)
            val raw = uri.lastPathSegment ?: url
            URLDecoder.decode(raw, "UTF-8")
        }.getOrElse { url }
    }

    private fun extractChapterInfo(): Pair<String, String> {
        // Extract manga and state from intent
        val manga = intent.getParcelableExtraCompat<ParcelableContent>(AppRouter.KEY_MANGA)?.manga
        val state = readerState ?: intent.getParcelableExtraCompat<ReaderState>(ReaderIntent.EXTRA_STATE)
        val fallbackUrl = currentMediaUrl ?: intent.getStringExtra(AppRouter.KEY_URL)
        
        // Extract title: prioritize manga.title, then KEY_TITLE, then URL-derived
        val title = manga?.title
            ?: intent.getStringExtra(AppRouter.KEY_TITLE).takeUnless { it.isNullOrBlank() }
            ?: fallbackUrl?.let { deriveEpisodeTitle(it) }
            ?: ""
        
        // Extract chapter name: prioritize chapter.name from manga.chapters, then URL-derived
        val chapterName = if (manga != null && state != null) {
            manga.chapters?.find { it.id == state.chapterId }?.title
                ?: fallbackUrl?.let { deriveEpisodeTitle(it) }
                ?: ""
        } else {
            fallbackUrl?.let { deriveEpisodeTitle(it) }
                ?: ""
        }
        
        return Pair(title, chapterName)
    }

    private fun updateTitleAndSubtitle() {
        val (title, subtitle) = extractChapterInfo()
        supportActionBar?.title = title
        supportActionBar?.subtitle = subtitle
        viewBinding.toolbar.title = title
        viewBinding.toolbar.subtitle = subtitle
    }

    /**
     * Load any external subtitle and audio tracks from the Aniyomi Video model.
     * Called after file is loaded so MPV can accept sub-add/audio-add commands.
     */
    private fun loadPendingExternalTracks() {
        val player = mpvPlayer ?: return
        val subs = pendingExternalSubtitles.toList()
        val audios = pendingExternalAudio.toList()
        pendingExternalSubtitles = emptyList()
        pendingExternalAudio = emptyList()

        if (subs.isEmpty() && audios.isEmpty()) {
            autoSelectTracksByLanguage()
            return
        }

        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            if (subs.isNotEmpty()) {
                android.util.Log.d("VideoPlayerActivity", "Loading ${subs.size} external subtitle tracks")
                for (track in subs) {
                    player.addSubtitleTrack(track.url, track.lang, track.lang)
                }
            }
            if (audios.isNotEmpty()) {
                android.util.Log.d("VideoPlayerActivity", "Loading ${audios.size} external audio tracks")
                for (track in audios) {
                    player.addAudioTrack(track.url, track.lang, track.lang)
                }
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (!isDestroyed && !isFinishing) {
                    autoSelectTracksByLanguage()
                }
            }
        }
    }

    /**
     * Poll MPV's sub-text property and update the subtitle overlay.
     * Called every 500ms from the controller progress runnable.
     * This is a reliable fallback since property observation may not work
     * for string properties in some mpv-android-lib versions.
     */
    private fun pollSubtitleText() {
        val player = mpvPlayer ?: return
        val text = player.getPropertyString("sub-text")
        if (text != lastSubtitleText) {
            lastSubtitleText = text
            val overlay = findViewById<android.widget.TextView>(org.skepsun.kototoro.R.id.subtitle_overlay)
            if (text.isNullOrBlank()) {
                overlay?.visibility = android.view.View.GONE
                overlay?.text = ""
            } else {
                overlay?.text = text
                overlay?.visibility = android.view.View.VISIBLE
            }
        }
    }

    /**
     * Update the subtitle overlay TextView with the given text.
     * Can be called from any thread — dispatches to UI thread.
     */
    private var subtitleOverlayView: android.widget.TextView? = null

    private fun getOrCreateSubtitleOverlay(): android.widget.TextView {
        subtitleOverlayView?.let { return it }
        val ctx = this
        val overlay = android.widget.TextView(ctx).apply {
            id = android.view.View.generateViewId()
            setTextColor(android.graphics.Color.WHITE)
            textSize = 18f
            setLineSpacing(4f * resources.displayMetrics.density, 1f)
            setShadowLayer(8f, 0f, 0f, android.graphics.Color.BLACK)
            setBackgroundColor(0x66000000)
            gravity = android.view.Gravity.CENTER
            val pad = (12 * resources.displayMetrics.density).toInt()
            val padV = (6 * resources.displayMetrics.density).toInt()
            setPadding(pad, padV, pad, padV)
            visibility = android.view.View.GONE
            isClickable = false
            isFocusable = false
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val lp = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            val margin = (32 * resources.displayMetrics.density).toInt()
            val bottomMargin = (80 * resources.displayMetrics.density).toInt()
            setMargins(margin, 0, margin, bottomMargin)
        }
        viewBinding.root.addView(overlay, lp)
        subtitleOverlayView = overlay
        Log.d("VideoPlayerActivity", "subtitle overlay CREATED programmatically")
        return overlay
    }

    private fun updateSubtitleOverlay(text: String?) {
        runOnUiThread {
            val overlay = getOrCreateSubtitleOverlay()
            if (text.isNullOrBlank()) {
                overlay.visibility = android.view.View.GONE
                overlay.text = ""
            } else {
                overlay.text = text
                overlay.visibility = android.view.View.VISIBLE
                overlay.bringToFront()
            }
        }
    }

    /**
     * Auto-select subtitle and audio tracks matching the system language.
     * Called after file is loaded and tracks are available.
     */
    private fun autoSelectTracksByLanguage() {
        val player = mpvPlayer ?: return
        val systemLang = java.util.Locale.getDefault().language // e.g. "zh", "en", "ja"
        Log.d("VideoPlayerActivity", "autoSelectTracksByLanguage: systemLang=$systemLang")

        // Auto-select subtitle track matching system language
        val subTracks = player.getSubtitleTracks()
        if (subTracks.isNotEmpty()) {
            val match = subTracks.find { it.language?.startsWith(systemLang, ignoreCase = true) == true }
            if (match != null && !match.isSelected) {
                player.setSubtitleTrack(match.id)
                Log.d("VideoPlayerActivity", "Auto-selected subtitle: ${match.displayName()}")
            }
        }

        // Auto-select audio track matching system language (if multiple audio tracks exist)
        val audioTracks = player.getAudioTracks()
        if (audioTracks.size > 1) {
            val match = audioTracks.find { it.language?.startsWith(systemLang, ignoreCase = true) == true }
            if (match != null && !match.isSelected) {
                player.setAudioTrack(match.id)
                Log.d("VideoPlayerActivity", "Auto-selected audio: ${match.displayName()}")
            }
        }
    }

    fun applySuperResolutionFromSettings() {
        val vo = mpvPlayer?.getPropertyString("vo")
        val voParams = mpvPlayer?.getPropertyString("video-out-params/vo")
        val hwdec = mpvPlayer?.getPropertyString("hwdec-current")
        val voCombined = listOfNotNull(vo, voParams).joinToString("|")
        val isMediacodecEmbed = voCombined.contains("mediacodec_embed", ignoreCase = true)
        android.util.Log.d("MpvPlayer", "SuperResolution check: vo=$vo voParams=$voParams hwdec=$hwdec")
        if (isMediacodecEmbed) {
            android.util.Log.d("MpvPlayer", "SuperResolution disabled: vo=$voCombined hwdec=$hwdec")
            mpvPlayer?.applyShaderList(null)
            if (appSettings.videoDecoderMode == VideoDecoderMode.SOFTWARE) {
                mpvPlayer?.setHardwareDecodingMode("no")
            } else {
                mpvPlayer?.setHardwareDecodingMode("auto")
            }
            return
        }
        if (appSettings.videoSuperResolutionMode == VideoSuperResolutionMode.OFF) {
            android.util.Log.d("MpvPlayer", "SuperResolution disabled: mode=OFF")
            mpvPlayer?.applyShaderList(null)
            if (appSettings.videoDecoderMode == VideoDecoderMode.SOFTWARE) {
                mpvPlayer?.setHardwareDecodingMode("no")
            } else {
                mpvPlayer?.setHardwareDecodingMode("auto")
            }
            return
        }
        if (appSettings.videoDecoderMode == VideoDecoderMode.SOFTWARE) {
            mpvPlayer?.setHardwareDecodingMode("no")
        } else {
            mpvPlayer?.setHardwareDecodingMode("mediacodec-copy")
        }
        val dir = MpvShaderManager.ensureShadersCopied(this)
        val shaderList = when (appSettings.videoSuperResolutionMode) {
            VideoSuperResolutionMode.OFF -> emptyList()
            VideoSuperResolutionMode.QUALITY -> mapSubModeToPreset(
                resolveSubMode(VideoSuperResolutionMode.QUALITY, appSettings.videoSuperResolutionQualityShader)
            )
            VideoSuperResolutionMode.BALANCED -> mapSubModeToPreset(
                resolveSubMode(VideoSuperResolutionMode.BALANCED, appSettings.videoSuperResolutionBalancedShader)
            )
            VideoSuperResolutionMode.PERFORMANCE -> mapSubModeToPreset(
                resolveSubMode(VideoSuperResolutionMode.PERFORMANCE, appSettings.videoSuperResolutionPerformanceShader)
            )
            VideoSuperResolutionMode.ADVANCED -> {
                mapSubModeToPreset(appSettings.videoSuperResolutionShader)
            }
        }
        val shaderPaths = if (shaderList.isEmpty()) null else {
            MpvShaderManager.buildShaderPathList(dir, shaderList)
        }
        mpvPlayer?.applyShaderList(shaderPaths)
    }

    private fun resolveSubMode(
        mode: VideoSuperResolutionMode,
        shader: VideoSuperResolutionShader,
    ): VideoSuperResolutionShader {
        return when (mode) {
            VideoSuperResolutionMode.OFF -> shader
            VideoSuperResolutionMode.QUALITY -> shader
            VideoSuperResolutionMode.BALANCED -> when (shader) {
                VideoSuperResolutionShader.MODE_AA -> VideoSuperResolutionShader.MODE_A
                VideoSuperResolutionShader.MODE_BB -> VideoSuperResolutionShader.MODE_B
                VideoSuperResolutionShader.MODE_CA -> VideoSuperResolutionShader.MODE_C
                else -> shader
            }
            VideoSuperResolutionMode.PERFORMANCE -> when (shader) {
                VideoSuperResolutionShader.MODE_A,
                VideoSuperResolutionShader.MODE_AA -> VideoSuperResolutionShader.MODE_B
                VideoSuperResolutionShader.MODE_B,
                VideoSuperResolutionShader.MODE_BB -> VideoSuperResolutionShader.MODE_C
                VideoSuperResolutionShader.MODE_C,
                VideoSuperResolutionShader.MODE_CA -> VideoSuperResolutionShader.MODE_C
                else -> shader
            }
            VideoSuperResolutionMode.ADVANCED -> shader
        }
    }

    private fun mapSubModeToPreset(shader: VideoSuperResolutionShader): List<String> {
        return when (shader) {
            VideoSuperResolutionShader.MODE_A -> MpvShaderManager.modeAPreset
            VideoSuperResolutionShader.MODE_B -> MpvShaderManager.modeBPreset
            VideoSuperResolutionShader.MODE_C -> MpvShaderManager.modeCPreset
            VideoSuperResolutionShader.MODE_AA -> MpvShaderManager.modeAPlusPreset
            VideoSuperResolutionShader.MODE_BB -> MpvShaderManager.modeBPlusPreset
            VideoSuperResolutionShader.MODE_CA -> MpvShaderManager.modeCAPlusPreset
            VideoSuperResolutionShader.CUSTOM -> appSettings.videoSuperResolutionCustomShaders.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
    }

    private fun showSubtitleTrackDialog() {
        val player = mpvPlayer ?: return
        val tracks = player.getSubtitleTracks()
        if (tracks.isEmpty()) {
            Snackbar.make(
                viewBinding.root,
                org.skepsun.kototoro.R.string.video_no_subtitle_tracks,
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }
        val labels = arrayOf(getString(org.skepsun.kototoro.R.string.video_subtitle_off)) +
            tracks.map { it.displayName() }.toTypedArray()
        val selectedTrack = tracks.indexOfFirst { it.isSelected }
        val checked = if (selectedTrack >= 0) selectedTrack + 1 else 0

        MaterialAlertDialogBuilder(this)
            .setTitle(org.skepsun.kototoro.R.string.video_subtitle_track)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                if (which == 0) {
                    player.setSubtitleTrack(null)
                } else {
                    player.setSubtitleTrack(tracks[which - 1].id)
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }


    fun showQualityDialog() {
        if (availableVideos.isEmpty()) {
            Snackbar.make(
                viewBinding.root,
                org.skepsun.kototoro.R.string.operation_not_supported,
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }
        val titles = availableVideos.mapIndexed { index, video ->
            val title = video.videoTitle.trim()
            if (title.isNotEmpty()) title else "线路${index + 1}"
        }.toTypedArray()
        val selected = currentVideoIndex.coerceIn(0, titles.lastIndex)
        MaterialAlertDialogBuilder(this)
            .setTitle(org.skepsun.kototoro.R.string.video_quality)
            .setSingleChoiceItems(titles, selected) { dialog, which ->
                if (which == currentVideoIndex) {
                    dialog.dismiss()
                    return@setSingleChoiceItems
                }
                val video = availableVideos[which]
                val resumeMs = mpvPlayer?.positionMs ?: 0L
                currentVideoIndex = which
                pendingExternalSubtitles = video.subtitleTracks
                pendingExternalAudio = video.audioTracks
                startMpvPlayback(
                    video.videoUrl,
                    currentVideoSource,
                    headersToMap(video.headers),
                    resumeMs,
                )
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onStop() {
        viewBinding.root.removeCallbacks(hideUiRunnable)
        viewBinding.root.removeCallbacks(progressUpdateRunnable)
        viewBinding.root.removeCallbacks(controllerProgressRunnable)
        viewBinding.root.removeCallbacks(progressSaveRunnable)
        stopLongSeek()
        super.onStop()
        // 保存当前播放进度（本地与历史）
        savePlaybackProgress()
        saveHistoryProgressAsync()
        videoLocalCacheProxy.logSessionStats("onStop")
        mpvPlayer?.pause()
        danmakuController.pause()
    }

    override fun onDestroy() {
        viewBinding.root.removeCallbacks(hideUiRunnable)
        viewBinding.root.removeCallbacks(progressUpdateRunnable)
        viewBinding.root.removeCallbacks(controllerProgressRunnable)
        viewBinding.root.removeCallbacks(progressSaveRunnable)
        stopLongSeek()
        // 兜底保存进度（本地与历史）
        savePlaybackProgress()
        saveHistoryProgressAsync()
        mpvPlayer?.release()
        mpvPlayer = null
        runCatching { mpvView.destroy() }
        danmakuController.release()
        super.onDestroy()
    }

    fun applyDanmakuSettings() {
        val settings = DanmakuSettings(
            enabled = appSettings.videoDanmakuEnabled,
            sizePercent = appSettings.videoDanmakuSizePercent,
            speedPercent = appSettings.videoDanmakuSpeedPercent,
            opacityPercent = appSettings.videoDanmakuOpacityPercent,
            strokePercent = appSettings.videoDanmakuStrokePercent,
            showScroll = appSettings.videoDanmakuShowScroll,
            showTop = appSettings.videoDanmakuShowTop,
            showBottom = appSettings.videoDanmakuShowBottom,
            maxScrollLines = appSettings.videoDanmakuMaxScrollLines,
            maxTopLines = appSettings.videoDanmakuMaxTopLines,
            maxBottomLines = appSettings.videoDanmakuMaxBottomLines,
            maxScreenNum = appSettings.videoDanmakuMaxScreenNum,
        )
        danmakuController.applySettings(settings)
        updateDanmakuToggleState()
        if (!settings.enabled) {
            danmakuController.setVisible(false)
        } else {
            if (danmakuController.isPrepared()) {
                danmakuController.setVisible(true)
            } else {
                danmakuKey = null
                maybeLoadDanmaku()
            }
        }
    }

    fun applyPlaybackSpeed(speed: Float) {
        mpvPlayer?.setRate(speed.toDouble())
    }

    fun applyPlaybackOptions() {
        val volume = if (appSettings.videoVolumeBoostEnabled) 130.0 else 100.0
        mpvPlayer?.setVolume(volume)
        val mpvCacheDir = getExternalFilesDir("mpv_cache") ?: File(filesDir, "mpv_cache")
        mpvPlayer?.applyCacheSettings(appSettings.videoCacheSizeMb, mpvCacheDir)
    }

    fun applyAspectRatio() {
        mpvPlayer?.setAspectRatio(appSettings.videoAspectRatio)
    }

    fun reloadPlayback() {
        val url = currentMediaUrl
        if (url.isNullOrBlank()) {
            Snackbar.make(viewBinding.root, org.skepsun.kototoro.R.string.error_occurred, Snackbar.LENGTH_SHORT).show()
            return
        }
        val resumeMs = mpvPlayer?.positionMs ?: 0L
        startMpvPlayback(url, currentVideoSource, currentMediaHeaders, resumeMs)
    }

    private fun openVideoDetails() {
        val dialogView = layoutInflater.inflate(org.skepsun.kototoro.R.layout.dialog_video_player_info, null)
        dialogView.findViewById<android.widget.TextView>(org.skepsun.kototoro.R.id.text_video_info).text = buildVideoDetailsText()
        val dialog = MaterialAlertDialogBuilder(this, org.skepsun.kototoro.R.style.ThemeOverlay_Kototoro_VideoInfoDialog)
            .setView(dialogView)
            .create()
        dialogView.findViewById<android.widget.ImageButton>(org.skepsun.kototoro.R.id.button_close).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
    }

    private fun buildVideoDetailsText(): String {
        fun String?.orDash(): String = this?.takeIf { it.isNotBlank() } ?: "-"
        fun formatBytes(bytes: Long): String {
            if (bytes < 1024) return "${bytes} B"
            val kb = bytes / 1024.0
            if (kb < 1024) return String.format("%.1f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024) return String.format("%.1f MB", mb)
            val gb = mb / 1024.0
            return String.format("%.2f GB", gb)
        }

        val (title, chapter) = extractChapterInfo()
        val decoderSetting = when (appSettings.videoDecoderMode) {
            VideoDecoderMode.HARDWARE -> getString(org.skepsun.kototoro.R.string.video_info_hw_decoding)
            VideoDecoderMode.SOFTWARE -> getString(org.skepsun.kototoro.R.string.video_info_sw_decoding)
        }
        val rendererSetting = when (appSettings.videoRendererMode) {
            VideoRendererMode.AUTO -> getString(org.skepsun.kototoro.R.string.video_info_auto)
            VideoRendererMode.GPU -> "GPU"
            VideoRendererMode.GPU_NEXT -> "GPU Next"
            VideoRendererMode.MEDIACODEC_EMBED -> "MediaCodec Embed"
        }
        val hwdecCurrent = mpvPlayer?.getPropertyString("hwdec-current").orDash()
        val voCurrent = mpvPlayer?.getPropertyString("vo").orDash()
        val videoCodec = mpvPlayer?.getPropertyString("video-codec").orDash()
        val audioCodec = mpvPlayer?.getPropertyString("audio-codec-name").orDash()
        val videoWidth = mpvPlayer?.getPropertyString("video-params/w").orDash()
        val videoHeight = mpvPlayer?.getPropertyString("video-params/h").orDash()
        val fps = (
            mpvPlayer?.getPropertyString("estimated-vf-fps")
                ?: mpvPlayer?.getPropertyString("video-params/fps")
                ?: mpvPlayer?.getPropertyString("container-fps")
            ).orDash()
        val sourceName = currentVideoSource?.name.orDash()
        val proxyStats = videoLocalCacheProxy.getSessionStatsSnapshot()

        val resolution = if (videoWidth != "-" && videoHeight != "-") {
            "${videoWidth}x${videoHeight}"
        } else {
            "-"
        }

        return buildString {
            appendLine(getString(org.skepsun.kototoro.R.string.video_info_title, title.ifBlank { "-" }))
            appendLine(getString(org.skepsun.kototoro.R.string.video_info_chapter, chapter.ifBlank { "-" }))
            appendLine(getString(org.skepsun.kototoro.R.string.video_info_source, sourceName))
            appendLine(getString(org.skepsun.kototoro.R.string.video_info_url, currentMediaUrl.orDash()))
            appendLine()
            appendLine(getString(org.skepsun.kototoro.R.string.video_info_decoding_setting, decoderSetting))
            appendLine(getString(org.skepsun.kototoro.R.string.video_info_current_decoder, hwdecCurrent))
            appendLine(getString(org.skepsun.kototoro.R.string.video_info_renderer_setting, rendererSetting))
            appendLine(getString(org.skepsun.kototoro.R.string.video_info_current_renderer, voCurrent))
            appendLine()
            appendLine(getString(org.skepsun.kototoro.R.string.video_info_video_codec, videoCodec))
            appendLine(getString(org.skepsun.kototoro.R.string.video_info_audio_codec, audioCodec))
            appendLine(getString(org.skepsun.kototoro.R.string.video_info_resolution, resolution))
            appendLine(getString(org.skepsun.kototoro.R.string.video_info_fps, fps))
            appendLine()
            appendLine(getString(org.skepsun.kototoro.R.string.video_info_proxy_stats))
            appendLine(getString(org.skepsun.kototoro.R.string.video_info_hits, proxyStats.hit))
            appendLine(getString(org.skepsun.kototoro.R.string.video_info_misses, proxyStats.miss))
            appendLine(getString(org.skepsun.kototoro.R.string.video_info_writes, proxyStats.writeCount))
            append(getString(org.skepsun.kototoro.R.string.video_info_write_bytes, formatBytes(proxyStats.writeBytes)))
        }
    }

    private fun enterPictureInPicture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val pm = packageManager
        if (!pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            Snackbar.make(viewBinding.root, org.skepsun.kototoro.R.string.operation_not_supported, Snackbar.LENGTH_SHORT).show()
            return
        }
        setUiIsVisible(false)
        val paramsBuilder = PictureInPictureParams.Builder()
        val pipWidth = mpvPlayer?.getPropertyString("video-params/w")?.toIntOrNull()
        val pipHeight = mpvPlayer?.getPropertyString("video-params/h")?.toIntOrNull()
        if (pipWidth != null && pipHeight != null && pipWidth > 0 && pipHeight > 0) {
            paramsBuilder.setAspectRatio(Rational(pipWidth, pipHeight))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            paramsBuilder.setSeamlessResizeEnabled(false)
        }
        enterPictureInPictureMode(paramsBuilder.build())
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            setUiIsVisible(false)
            viewBinding.root.findViewById<View>(org.skepsun.kototoro.R.id.top_gradient)?.isVisible = false
            viewBinding.root.findViewById<View>(org.skepsun.kototoro.R.id.bottom_gradient)?.isVisible = false
        } else {
            applyGradientAlpha()
        }
    }

    private fun takeScreenshot() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val surfaceView = findViewById<SurfaceView>(org.skepsun.kototoro.R.id.player_view) ?: return
        if (surfaceView.width <= 0 || surfaceView.height <= 0) {
            Snackbar.make(viewBinding.root, org.skepsun.kototoro.R.string.error_occurred, Snackbar.LENGTH_SHORT).show()
            return
        }
        val bitmap = Bitmap.createBitmap(surfaceView.width, surfaceView.height, Bitmap.Config.ARGB_8888)
        PixelCopy.request(
            surfaceView,
            bitmap,
            { result ->
                if (result == PixelCopy.SUCCESS) {
                    saveBitmapToGallery(bitmap)
                } else {
                    Snackbar.make(viewBinding.root, org.skepsun.kototoro.R.string.error_occurred, Snackbar.LENGTH_SHORT).show()
                }
            },
            Handler(Looper.getMainLooper()),
        )
    }

    private fun saveBitmapToGallery(bitmap: Bitmap) {
        val filename = "kototoro_${System.currentTimeMillis()}.png"
        val resolver = contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Kototoro")
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            Snackbar.make(viewBinding.root, org.skepsun.kototoro.R.string.error_occurred, Snackbar.LENGTH_SHORT).show()
            return
        }
        resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        Snackbar.make(viewBinding.root, org.skepsun.kototoro.R.string.saved, Snackbar.LENGTH_SHORT).show()
    }

    private fun maybeLoadDanmaku() {
        if (!appSettings.videoDanmakuEnabled) {
            android.util.Log.d("Danmaku", "Danmaku disabled by settings; keep loading in background")
        }
        val manga = intent.getParcelableExtraCompat<ParcelableContent>(AppRouter.KEY_MANGA)?.manga
        val title = manga?.title?.takeIf { it.isNotBlank() }
            ?: intent.getStringExtra(AppRouter.KEY_TITLE)
            ?: run {
                android.util.Log.d("Danmaku", "Danmaku skipped: missing title")
                return
            }
        val cacheKey = buildDanmakuCacheKey(manga?.id, title)
        val keywords = buildDanmakuKeywords(manga, title)
        val episode = resolveEpisodeNumber(manga?.chapters.orEmpty())
        if (episode <= 0) {
            android.util.Log.d("Danmaku", "Danmaku skipped: episode=$episode title=$title")
            return
        }
        val url = currentMediaUrl ?: ""
        val key = "$title#$episode#$url"
        if (key == danmakuKey) {
            android.util.Log.d("Danmaku", "Danmaku cache hit: key=$key")
            return
        }
        danmakuKey = key
        danmakuController.clear()
        danmakuLoadJob?.cancel()
        danmakuLoadJob = lifecycleScope.launch {
            android.util.Log.d(
                "Danmaku",
                "Load start: title=$title episode=$episode url=$url filters=dandan:${appSettings.videoDanmakuSourceDanDan} bili:${appSettings.videoDanmakuSourceBilibili} qq:${appSettings.videoDanmakuSourceQq}",
            )
            val items = loadDanmakuFromSources(title, episode, url, cacheKey, keywords)
            if (items.isEmpty()) {
                android.util.Log.d("Danmaku", "Load result: empty")
                danmakuController.setVisible(false)
                return@launch
            }
            android.util.Log.d("Danmaku", "Load result: ${items.size} items")
            val autoShow = appSettings.videoDanmakuEnabled
            danmakuController.loadDanmaku(
                items = items,
                autoShow = autoShow,
                isPlaying = mpvPlayer?.isPlaying == true,
            )
            danmakuController.setVisible(autoShow)
        }
    }

    private suspend fun loadDanmakuFromSources(
        title: String,
        episode: Int,
        url: String,
        cacheKey: String,
        keywords: List<String>,
    ): List<org.skepsun.kototoro.video.danmaku.DanmakuItem> {
        return danmakuSourceManager.loadFromSources(
            title = title,
            episode = episode,
            url = url,
            cacheKey = cacheKey,
            keywords = keywords,
            enableDanDan = appSettings.videoDanmakuSourceDanDan,
            enableBilibili = appSettings.videoDanmakuSourceBilibili,
            enableQq = appSettings.videoDanmakuSourceQq,
        )
    }

    private fun buildDanmakuCacheKey(mangaId: Long?, title: String): String {
        val idPart = mangaId?.takeIf { it > 0 }?.toString()
        return idPart ?: title.trim()
    }

    private fun buildDanmakuKeywords(
        manga: org.skepsun.kototoro.parsers.model.Content?,
        title: String,
    ): List<String> {
        val candidates = LinkedHashSet<String>()
        candidates.add(title)
        manga?.altTitles?.forEach { alt: String ->
            if (alt.isNotBlank()) candidates.add(alt)
        }
        val sanitized = candidates.flatMap { keywordVariants(it) }
        return sanitized.distinct().filter { it.isNotBlank() }
    }

    private fun keywordVariants(title: String): List<String> {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return emptyList()
        val removeBrackets = trimmed.replace(Regex("[\\[\\(（【].*?[\\]）】]"), "")
        val noPunct = removeBrackets.replace(Regex("[\\s\\p{Punct}！？。、《》“”‘’·]"), "")
        return listOf(trimmed, removeBrackets, noPunct).distinct()
    }

    private fun resolveEpisodeNumber(chapters: List<ContentChapter>): Int {
        val chapter = if (chapters.isNotEmpty()) {
            val currentId = readerState?.chapterId ?: chapters.first().id
            chapters.firstOrNull { it.id == currentId } ?: chapters.first()
        } else {
            null
        }
        val number = chapter?.number ?: 0f
        if (number > 0f) {
            return number.roundToInt()
        }
        val title = chapter?.title
            ?: extractChapterInfo().second.takeIf { it.isNotBlank() }
            ?: return 0
        val match = Regex("(\\d+)").find(title) ?: return 0
        return match.groupValues[1].toIntOrNull() ?: 0
    }

    private fun sendLocalDanmaku(message: String) {
        if (!appSettings.videoDanmakuEnabled) {
            Snackbar.make(viewBinding.root, org.skepsun.kototoro.R.string.video_danmaku_enabled, Snackbar.LENGTH_SHORT).show()
            return
        }
        val timeMs = mpvPlayer?.positionMs ?: return
        danmakuController.addLiveDanmaku(message, timeMs)
    }

    private fun savePlaybackProgress() {
        val currentUrl = currentMediaUrl ?: return
        val pos = mpvPlayer?.positionMs ?: return
        val dur = mpvPlayer?.durationMs ?: return
        runCatching {
            getSharedPreferences("video_progress", MODE_PRIVATE)
                .edit()
                .putLong(currentUrl, pos)
                .putLong("${currentUrl}_duration", dur)
                .putLong("${currentUrl}_timestamp", System.currentTimeMillis())
                .commit() // 使用commit()同步保存，确保数据不丢失
        }.onFailure { e ->
            android.util.Log.e("VideoPlayer", "Failed to save progress", e)
        }
    }

    private fun restorePlaybackProgress() {
        val currentUrl = currentMediaUrl ?: return
        val prefs = getSharedPreferences("video_progress", MODE_PRIVATE)
        val pos = prefs.getLong(currentUrl, 0L)
        val dur = prefs.getLong("${currentUrl}_duration", 0L)
        if (pos <= 0L) return
        if (dur > 0L && pos >= (dur - 2_000L)) {
            android.util.Log.d("VideoPlayer", "Skip restore: near end pos=$pos dur=$dur")
            return
        }
        mpvPlayer?.seekTo(pos)
    }

    private fun resolveSavedPlaybackProgress(url: String): Long? {
        val prefs = getSharedPreferences("video_progress", MODE_PRIVATE)
        val pos = prefs.getLong(url, 0L)
        val dur = prefs.getLong("${url}_duration", 0L)
        if (pos <= 0L) return null
        if (dur > 0L && pos >= (dur - 2_000L)) return null
        return pos
    }

    private suspend fun restoreInitialSeekPercentFromHistory() {
        val manga = intent.getParcelableExtraCompat<ParcelableContent>(AppRouter.KEY_MANGA)?.manga ?: return
        val history = runCatching { historyRepository.getOne(manga) }.getOrNull() ?: return
        android.util.Log.d("VideoPlayer", "Restore history: chapterId=${history.chapterId}, percent=${history.percent}")
        
        // Get current chapter ID from ReaderState or intent
        val currentState = readerState ?: intent.getParcelableExtraCompat<ReaderState>(ReaderIntent.EXTRA_STATE)
        val currentChapterId = currentState?.chapterId
        
        android.util.Log.d("VideoPlayer", "Current chapter ID from intent/state: $currentChapterId")
        
        // Verify chapter ID matches current playing chapter
        if (currentChapterId != null && currentChapterId != history.chapterId) {
            android.util.Log.w("VideoPlayer", "Chapter mismatch: history has ${history.chapterId}, but playing ${currentChapterId}. Not restoring position.")
            // Don't restore position when chapter doesn't match
            return
        }
        
        val overall = history.percent
        if (overall !in 0f..1f) {
            android.util.Log.w("VideoPlayer", "Invalid history percent: $overall")
            return
        }
        if (overall >= 0.98f) {
            android.util.Log.d("VideoPlayer", "Skip history seek: overall=$overall")
            return
        }
        
        val chapters = manga.chapters ?: run {
            // 无章节信息时无法拆分整体百分比，直接使用整体值（退化为单集）
            android.util.Log.d("VideoPlayer", "No chapters, using overall percent: $overall")
            pendingInitialSeekPercent = overall
            return
        }
        
        val chapter = chapters.find { it.id == history.chapterId } ?: run {
            android.util.Log.w("VideoPlayer", "Chapter not found for id=${history.chapterId}, using overall percent")
            pendingInitialSeekPercent = overall
            return
        }
        
        android.util.Log.d("VideoPlayer", "Found chapter: ${chapter.title} (id=${chapter.id})")
        
        val branchChapters = chapters.filter { it.branch == chapter.branch }
        val count = branchChapters.size
        if (count <= 0) {
            android.util.Log.w("VideoPlayer", "No chapters in branch '${chapter.branch}'")
            pendingInitialSeekPercent = overall
            return
        }
        val idx = branchChapters.indexOfFirst { it.id == chapter.id }.coerceAtLeast(0)
        // 单集百分比 = 整体百分比 * 总集数 - 当前集索引
        val episodePercent = (overall * count - idx).coerceIn(0f, 1f)
        android.util.Log.d("VideoPlayer", "Calculated episode percent: $episodePercent (idx=$idx, count=$count, overall=$overall)")
        pendingInitialSeekPercent = episodePercent
    }

    private fun tryApplyInitialSeek() {
        val p = pendingInitialSeekPercent ?: return
        if (p >= 0.98f) {
            android.util.Log.d("VideoPlayer", "Skip initial seek: percent=$p")
            pendingInitialSeekPercent = null
            return
        }
        val pos = mpvPlayer?.positionMs ?: 0L
        if (pos > 0L) {
            pendingInitialSeekPercent = null
            return
        }
        val dur = mpvPlayer?.durationMs ?: 0L
        if (dur > 0) {
            mpvPlayer?.seekTo((p * dur).toLong())
            pendingInitialSeekPercent = null
        }
    }

    private fun saveHistoryProgressAsync() {
        val exo = mpvPlayer ?: return
        val mangaSeed = intent.getParcelableExtraCompat<ParcelableContent>(AppRouter.KEY_MANGA)?.manga ?: return
        val dur = exo.durationMs
        val pos = exo.positionMs
        // 当时长未知（直播或刚开始播放）时，也保存一个有效百分比以建立历史记录
        val episodePercent = if (dur > 0) {
            (pos.toFloat() / dur).coerceIn(0f, 1f)
        } else 0f

        android.util.Log.d("VideoPlayer", "Save progress: pos=$pos, dur=$dur, episodePercent=$episodePercent")

        // Ensure ReaderState reflects current chapter before saving
        val state = readerState
        android.util.Log.d("VideoPlayer", "ReaderState before save: chapterId=${state?.chapterId}, page=${state?.page}")
        
        if (state == null) {
            android.util.Log.w("VideoPlayer", "ReaderState is null, cannot save accurate chapter progress")
        }

        fun computeSeriesPercent(m: org.skepsun.kototoro.parsers.model.Content, s: ReaderState, ep: Float): Float {
            val chapters = m.chapters ?: run {
                android.util.Log.w("VideoPlayer", "No chapters available for series percent calculation")
                return ep
            }
            val curr = chapters.find { it.id == s.chapterId } ?: run {
                android.util.Log.w("VideoPlayer", "Current chapter (id=${s.chapterId}) not found in chapters list")
                return ep
            }
            val branchChapters = chapters.filter { it.branch == curr.branch }
            val count = branchChapters.size
            if (count <= 0) {
                android.util.Log.w("VideoPlayer", "No chapters in branch '${curr.branch}'")
                return ep
            }
            val idx = branchChapters.indexOfFirst { it.id == curr.id }.coerceAtLeast(0)
            val ppc = 1f / count
            val seriesPercent = (ppc * idx + ppc * ep).coerceIn(0f, 1f)
            android.util.Log.d("VideoPlayer", "Series percent calculation: chapter=${curr.title}, idx=$idx, count=$count, episodePercent=$ep, seriesPercent=$seriesPercent")
            return seriesPercent
        }

        // 其余部分需要加载详情以确保 chapters 非空
        lifecycleScope.launch {
            // 先确保漫画详情含章节
            val repo = mangaRepositoryFactory.create(mangaSeed.source)
            val manga = if (mangaSeed.chapters.isNullOrEmpty()) runCatching { repo.getDetails(mangaSeed) }.getOrDefault(mangaSeed) else mangaSeed
            // 若仍无章节信息（网络/源不可用），避免保存触发断言失败
            if (manga.chapters.isNullOrEmpty()) {
                android.util.Log.w("VideoPlayer", "Cannot save history: manga has no chapters")
                return@launch
            }

            if (state != null) {
                // Verify ReaderState chapter ID exists in manga chapters
                val chapterExists = manga.chapters?.any { it.id == state.chapterId } == true
                if (!chapterExists) {
                    android.util.Log.e("VideoPlayer", "ReaderState chapter ID ${state.chapterId} does not exist in manga chapters!")
                }
                
                // ReaderState 已提供：直接计算整体百分比并保存
                val overall = computeSeriesPercent(manga, state, episodePercent)
                android.util.Log.d("VideoPlayer", "Saving history with ReaderState: chapterId=${state.chapterId}, overall=$overall")
                historyUpdateUseCase.invokeAsync(manga, state, overall)
            } else {
                // 无 ReaderState：优先使用已有历史，否则用首章构造
                val history = runCatching { historyRepository.getOne(manga) }.getOrNull()
                val fallbackState = history?.let { ReaderState(it) } ?: runCatching { ReaderState(manga, null) }.getOrNull()
                if (fallbackState != null) {
                    android.util.Log.d("VideoPlayer", "Using fallback ReaderState: chapterId=${fallbackState.chapterId}")
                    val overall = computeSeriesPercent(manga, fallbackState, episodePercent)
                    historyUpdateUseCase.invokeAsync(manga, fallbackState, overall)
                } else {
                    android.util.Log.w("VideoPlayer", "Cannot create fallback ReaderState")
                }
            }
        }
    }

    override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
        val type = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
        val bars = insets.getInsets(type)
        val taskbarInsets = insets.getInsets(WindowInsetsCompat.Type.tappableElement())
        val mandatoryGestureInsets = insets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures())
        val cutoutInsets = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
        val bottomSafeInset = maxOf(
            bars.bottom,
            taskbarInsets.bottom,
            mandatoryGestureInsets.bottom,
            cutoutInsets.bottom,
        )
        // 顶部工具栏：使用外边距对齐系统栏；避免设置顶部内边距导致导航按钮被裁切
        viewBinding.toolbar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            leftMargin = bars.left
            rightMargin = bars.right
            topMargin = bars.top
        }
        viewBinding.toolbar.updatePadding(
            left = 0,
            right = 0,
            top = 0,
        )
        // 更新状态栏遮罩高度与左右边距以匹配系统栏
        viewBinding.root.findViewById<View>(org.skepsun.kototoro.R.id.status_bar_scrim)?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            height = bars.top
            leftMargin = bars.left
            rightMargin = bars.right
        }
        // 将 DockedToolbar 与系统导航栏视觉合并：使用内边距吸收导航栏高度，避免底部留白
        val dockedToolbar = findViewById<View>(org.skepsun.kototoro.R.id.toolbar_docked)
        dockedToolbar?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            leftMargin = bars.left
            rightMargin = bars.right
            bottomMargin = 0
        }
        dockedToolbar?.updatePadding(bottom = bottomSafeInset)
        // PlayerView 内容保持与左右系统栏对齐，底部不再额外内边距，避免与 DockedToolbar 重叠留白
        findViewById<View>(org.skepsun.kototoro.R.id.player_view).updatePadding(
            left = bars.left,
            right = bars.right,
            bottom = 0,
        )
        return insets.consumeAll(type)
    }

    // ReaderNavigationCallback implementation
    override fun onPageSelected(page: ReaderPage): Boolean {
        // Video player doesn't support page-level navigation
        return false
    }

    override fun onChapterSelected(chapter: ContentChapter): Boolean {
        // Handle chapter selection from ChaptersPagesSheet
        val manga = intent.getParcelableExtraCompat<ParcelableContent>(AppRouter.KEY_MANGA)?.manga 
            ?: return false
        
        android.util.Log.d("VideoPlayer", "Chapter selected: ${chapter.title} (id=${chapter.id})")
        
        // Find the new chapter's video URL asynchronously
        lifecycleScope.launch {
            try {
                val repo = mangaRepositoryFactory.create(manga.source)
                val pages = repo.getPages(chapter)
                val page = pages.firstOrNull()
                val streamUrl = page?.let { repo.getPageUrl(it) }
                val streamHeaders = page?.headers
                
                if (streamUrl != null) {
                    android.util.Log.d("VideoPlayer", "Stream URL resolved: $streamUrl")
                    
                    // Update ReaderState with new chapter
                    readerState = ReaderState(chapter.id, 0, 0)
                    updateChapterNavButtons()
                    
                    // Update player with new URL
                    prepareAndPlay(streamUrl, manga.source, streamHeaders)
                    
                    // Update title/subtitle to reflect new chapter
                    updateTitleAndSubtitle()
                    
                    // Save progress for new chapter
                    saveHistoryProgressAsync()
                } else {
                    android.util.Log.w("VideoPlayer", "Failed to resolve stream URL for chapter ${chapter.id}")
                    Snackbar.make(
                        viewBinding.root,
                        org.skepsun.kototoro.R.string.error_occurred,
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("VideoPlayer", "Error loading chapter", e)
                Snackbar.make(
                    viewBinding.root,
                    org.skepsun.kototoro.R.string.error_occurred,
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
        
        return true // Indicate we handled the selection
    }

    private fun updateChapterNavButtons() {
        val ctl = findViewById<PlayerControlView>(org.skepsun.kototoro.R.id.controller) ?: return
        val prev = ctl.findViewById<View>(org.skepsun.kototoro.R.id.button_prev_chapter)
        val next = ctl.findViewById<View>(org.skepsun.kototoro.R.id.button_next_chapter)

        val manga = intent.getParcelableExtraCompat<ParcelableContent>(AppRouter.KEY_MANGA)?.manga
        val chapters = manga?.chapters.orEmpty()
        if (chapters.isEmpty()) {
            prev?.isEnabled = false
            prev?.alpha = 0.4f
            next?.isEnabled = false
            next?.alpha = 0.4f
            return
        }

        val currentId = readerState?.chapterId ?: chapters.first().id
        val currentIndex = chapters.indexOfFirst { it.id == currentId }.takeIf { it >= 0 } ?: 0
        val hasPrev = currentIndex > 0
        val hasNext = currentIndex < chapters.lastIndex

        prev?.isEnabled = hasPrev
        prev?.alpha = if (hasPrev) 1f else 0.4f
        next?.isEnabled = hasNext
        next?.alpha = if (hasNext) 1f else 0.4f
    }

    private fun navigateChapter(offset: Int) {
        val manga = intent.getParcelableExtraCompat<ParcelableContent>(AppRouter.KEY_MANGA)?.manga ?: return
        val chapters = manga.chapters ?: return
        if (chapters.isEmpty()) return
        val currentId = readerState?.chapterId ?: chapters.first().id
        val currentIndex = chapters.indexOfFirst { it.id == currentId }
        if (currentIndex == -1) return
        val targetIndex = (currentIndex + offset).coerceIn(0, chapters.size - 1)
        if (targetIndex == currentIndex) return
        val targetChapter = chapters[targetIndex]
        onChapterSelected(targetChapter)
    }

	private fun maybeAutoPlayNext() {
		if (!appSettings.videoAutoNextEnabled || autoNextTriggered) return
		val duration = mpvPlayer?.durationMs ?: 0L
		val position = mpvPlayer?.positionMs ?: 0L
		if (duration <= 0L) {
			android.util.Log.d("VideoPlayer", "AutoNext skipped: duration=0")
			return
		}
		val ratio = position.toDouble() / duration.toDouble()
		if (ratio < 0.98) {
			android.util.Log.d("VideoPlayer", "AutoNext skipped: ratio=$ratio pos=$position dur=$duration")
			return
		}
		val manga = intent.getParcelableExtraCompat<ParcelableContent>(AppRouter.KEY_MANGA)?.manga ?: return
		val chapters = manga.chapters ?: return
		if (chapters.isEmpty()) return
		val currentId = readerState?.chapterId ?: chapters.first().id
		val currentIndex = chapters.indexOfFirst { it.id == currentId }.takeIf { it >= 0 } ?: return
		if (currentIndex < chapters.lastIndex) {
			android.util.Log.i("VideoPlayerActivity", "AutoNext successfully triggered. Navigating to index ${currentIndex + 1}.")
			autoNextTriggered = true
			navigateChapter(+1)
		}
	}

    override fun onBookmarkSelected(bookmark: Bookmark): Boolean {
        // Video player doesn't support bookmarks
        return false
    }

    private fun showSeekFeedback(posMs: Long, durationMs: Long, seekOffsetMs: Long) {
        val layout = viewBinding.seekFeedbackLayout ?: return
        val textTv = viewBinding.seekFeedbackText ?: return
        val progressInd = viewBinding.seekFeedbackProgress ?: return

        val showHours = durationMs >= 3600_000L
        val timeStr = formatTimeMs(posMs, showHours) + " / " + formatTimeMs(durationMs, showHours)
        
        val offsetSec = (kotlin.math.abs(seekOffsetMs) / 1000).toInt()
        val deltaStr = if (seekOffsetMs > 0) {
            getString(org.skepsun.kototoro.R.string.video_fast_forward_time, offsetSec.toString())
        } else if (seekOffsetMs < 0) {
            getString(org.skepsun.kototoro.R.string.video_rewind_time, offsetSec.toString())
        } else {
            ""
        }
        
        textTv.text = if (deltaStr.isNotEmpty()) "$deltaStr\n$timeStr" else timeStr
        textTv.gravity = android.view.Gravity.CENTER

        progressInd.max = 1000
        progressInd.progress = if (durationMs > 0) ((posMs * 1000) / durationMs).toInt() else 0

        layout.alpha = 1f
        layout.visibility = android.view.View.VISIBLE

        // Explicitly update the bottom TimeBar during gesture.
        // We use viewBinding since PlayerControlView is not strictly tied to an ExoPlayer here.
        val progressView = viewBinding.controller.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_progress) as? androidx.media3.ui.TimeBar
        if (progressView != null && durationMs > 0) {
            progressView.setDuration(durationMs)
            progressView.setPosition(posMs)
        }
        val posView = viewBinding.controller.findViewById<android.widget.TextView>(androidx.media3.ui.R.id.exo_position)
        if (posView != null) {
            posView.text = formatTimeMs(posMs, showHours)
        }
    }

    private fun hideSeekFeedback() {
        viewBinding.seekFeedbackLayout?.isVisible = false
    }

    private fun openInExternalPlayer() {
        val url = currentMediaUrl
        if (url.isNullOrBlank()) {
            Snackbar.make(viewBinding.root, R.string.no_video_loaded, Snackbar.LENGTH_SHORT).show()
            return
        }
        val headers = currentMediaHeaders.orEmpty()
        val proxyUrl = videoLocalCacheProxy.getProxyUrl(url, headers)
        val title = viewBinding.toolbar.title?.toString()
        ExternalPlayerHelper.openInExternalPlayer(this, proxyUrl, title)
    }

    private fun showDlnaDeviceSheet() {
        val url = currentMediaUrl
        if (url.isNullOrBlank()) {
            Snackbar.make(viewBinding.root, R.string.no_video_loaded, Snackbar.LENGTH_SHORT).show()
            return
        }
        val headers = currentMediaHeaders.orEmpty()
        val positionMs = mpvPlayer?.positionMs ?: 0L
        val tag = "DlnaDeviceSheet"
        val fm = supportFragmentManager
        if (fm.findFragmentByTag(tag) == null) {
            val sheet = DlnaDeviceSheet.newInstance(url, headers, positionMs)
            sheet.onCastStarted = {
                // Pause local playback when casting starts
                runOnUiThread { mpvPlayer?.pause() }
            }
            sheet.show(fm, tag)
        }
    }
}
