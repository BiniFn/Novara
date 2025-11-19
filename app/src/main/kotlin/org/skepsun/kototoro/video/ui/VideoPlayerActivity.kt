package org.skepsun.kototoro.video.ui

import android.os.Bundle
import android.view.View
import android.content.res.Configuration
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.appcompat.app.AlertDialog
import android.view.MenuItem
import android.view.GestureDetector
import android.view.MotionEvent
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.media3.ui.PlayerControlView
import android.content.DialogInterface
import android.view.ViewGroup
import org.skepsun.kototoro.core.util.ext.consumeAll
import org.skepsun.kototoro.core.ui.dialog.buildAlertDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.skepsun.kototoro.core.model.MangaSource
import org.skepsun.kototoro.core.parser.MangaRepository
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.network.CommonHeaders
import org.skepsun.kototoro.core.network.MangaHttpClient
import org.skepsun.kototoro.core.ui.BaseFullscreenActivity
import org.skepsun.kototoro.databinding.ActivityVideoPlayerBinding
import org.skepsun.kototoro.core.util.ext.getParcelableExtraCompat
import org.skepsun.kototoro.core.model.parcelable.ParcelableManga
import org.skepsun.kototoro.core.nav.ReaderIntent
import org.skepsun.kototoro.reader.ui.ReaderState
import org.skepsun.kototoro.parsers.model.MangaSource as ParsersMangaSource
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
import kotlin.math.abs
import org.skepsun.kototoro.core.util.ext.menuView
import org.skepsun.kototoro.history.data.HistoryRepository
import org.skepsun.kototoro.history.domain.HistoryUpdateUseCase

@AndroidEntryPoint
class VideoPlayerActivity : BaseFullscreenActivity<ActivityVideoPlayerBinding>() {

    @Inject
    @MangaHttpClient
    lateinit var okHttp: OkHttpClient

    private var player: ExoPlayer? = null
    private var isUiVisible: Boolean = false
    private var isFoldUnfolded: Boolean = false
    private var originalToolbarHeightPx: Int = 0

    // ReaderState（用于历史保存时提供章节与页信息）
    private var readerState: ReaderState? = null
    // 待应用的历史定位百分比（在播放器 STATE_READY 时按时长换算并 seek）
    private var pendingInitialSeekPercent: Float? = null

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
    private val controllerProgressRunnable = object : Runnable {
        override fun run() {
            updateControllerProgress()
            viewBinding.root.postDelayed(this, controllerProgressIntervalMs.toLong())
        }
    }
    // 长按持续快进/快退配置与状态
    private val longSeekIntervalMs = 200
    private val longSeekStepMs = 2000
    private val quickTapJumpMs = 10_000
    private val longSeekHandler = Handler(Looper.getMainLooper())
    private var longSeekDirection: Int = 0 // -1: back, +1: forward, 0: none
    private var longSeekAccumulatedMs: Long = 0L
    private val longSeekRunnable = object : Runnable {
        override fun run() {
            val p = player ?: return
            val dur = p.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
            val newPos = (p.currentPosition + longSeekDirection * longSeekStepMs).coerceIn(0, dur)
            if (longSeekDirection < 0) {
                runCatching { p.seekBack() }.getOrElse { p.seekTo(newPos) }
            } else if (longSeekDirection > 0) {
                runCatching { p.seekForward() }.getOrElse { p.seekTo(newPos) }
            }
            if (longSeekDirection != 0) {
                longSeekAccumulatedMs += abs(longSeekStepMs.toLong())
                val sec = (longSeekAccumulatedMs / 1000).toInt()
                if (longSeekDirection < 0) {
                    overlaySeekLeft.text = "快退 ${sec} 秒"
                } else {
                    overlaySeekRight.text = "快进 ${sec} 秒"
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
        hideLongSeekOverlay()
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
    private fun showOverlayLeft(text: String, durationMs: Long = 800) {
        overlaySeekLeft.text = text
        overlaySeekLeft.visibility = View.VISIBLE
        overlayHandler.removeCallbacks(hideLeftRunnable)
        overlayHandler.postDelayed(hideLeftRunnable, durationMs)
    }
    private fun showOverlayRight(text: String, durationMs: Long = 800) {
        overlaySeekRight.text = text
        overlaySeekRight.visibility = View.VISIBLE
        overlayHandler.removeCallbacks(hideRightRunnable)
        overlayHandler.postDelayed(hideRightRunnable, durationMs)
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
            overlaySeekLeft.text = "快退 0 秒"
            overlaySeekLeft.visibility = View.VISIBLE
        } else if (direction > 0) {
            overlaySeekLeft.visibility = View.GONE
            overlaySeekRight.text = "快进 0 秒"
            overlaySeekRight.visibility = View.VISIBLE
        }
    }
    private fun hideLongSeekOverlay() {
        overlaySeekLeft.visibility = View.GONE
        overlaySeekRight.visibility = View.GONE
    }

    // 垂直手势：亮度/音量调整
    private lateinit var audioManager: AudioManager
    private var verticalAdjustMode: Int = 0 // -1: 亮度（左侧），+1: 音量（右侧），0: 无
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
        showOverlayLeft("亮度 ${pct}%")
    }
    private fun adjustVolumeByStep(increase: Boolean) {
        val dir = if (increase) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, 0)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val curr = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val pct = if (max > 0) ((curr * 100f) / max).toInt() else 0
        showOverlayRight("音量 ${pct}%")
    }
    
    @Inject
    lateinit var orientationHelper: ScreenOrientationHelper

    @Inject
    lateinit var historyRepository: HistoryRepository

    @Inject
    lateinit var historyUpdateUseCase: HistoryUpdateUseCase

    @Inject
    lateinit var mangaRepositoryFactory: MangaRepository.Factory

    private fun isLandscapeOrientation(): Boolean =
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

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

        // 记录初始工具栏高度，用于按方向动态调整高度
        originalToolbarHeightPx = viewBinding.toolbar.layoutParams.height

        // 读取传入的 ReaderState（可能来自阅读器路由，用于历史保存与初始定位）
        readerState = intent.getParcelableExtraCompat<ReaderState>(ReaderIntent.EXTRA_STATE)

        // 标题：优先使用传入的 KEY_TITLE；其次使用 ParcelableManga.title；最后从 URL 推断
        val titleFromIntent = intent.getStringExtra(AppRouter.KEY_TITLE).takeUnless { it.isNullOrBlank() }
            ?: intent.getParcelableExtraCompat<ParcelableManga>(AppRouter.KEY_MANGA)?.manga?.title
            ?: intent.getStringExtra(AppRouter.KEY_URL)?.let { deriveEpisodeTitle(it) }
            ?: ""
        supportActionBar?.title = titleFromIntent
        // 同步到 Toolbar 本身，确保 SupportActionBar 不可用时也能显示
        viewBinding.toolbar.title = titleFromIntent
        // Subtitle: episode/chapter title derived from URL
        intent.getStringExtra(AppRouter.KEY_URL)?.let { url ->
            val episode = deriveEpisodeTitle(url)
            supportActionBar?.subtitle = episode
            viewBinding.toolbar.subtitle = episode
        }

        // Apply default orientation: portrait when foldable unfolded in portrait; else landscape
        observeFoldableStateForOrientation()

        // 仅设置菜单点击监听；实际菜单由 rebuildToolbarMenuForOrientation() 按方向重建
        viewBinding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                org.skepsun.kototoro.R.id.action_play_pause -> {
                    player?.let { p ->
                        if (p.isPlaying) p.pause() else p.play()
                        updatePlaybackMenu()
                    }
                    true
                }
                org.skepsun.kototoro.R.id.action_rewind -> {
                    player?.let { p ->
                        runCatching { p.seekBack() }.getOrElse {
                            val pos = p.currentPosition
                            p.seekTo((pos - 10_000).coerceAtLeast(0))
                        }
                        updatePlaybackMenu()
                    }
                    true
                }
                org.skepsun.kototoro.R.id.action_fast_forward -> {
                    player?.let { p ->
                        runCatching { p.seekForward() }.getOrElse {
                            val pos = p.currentPosition
                            val dur = p.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                            p.seekTo((pos + 10_000).coerceAtMost(dur))
                        }
                        updatePlaybackMenu()
                    }
                    true
                }
                org.skepsun.kototoro.R.id.action_prev -> {
                    player?.seekToPrevious()
                    updatePlaybackMenu()
                    true
                }
                org.skepsun.kototoro.R.id.action_next -> {
                    player?.seekToNext()
                    updatePlaybackMenu()
                    true
                }
                org.skepsun.kototoro.R.id.action_quality -> {
                    showQualityDialog()
                    true
                }
                org.skepsun.kototoro.R.id.action_pages -> {
                    val parcelable = intent.getParcelableExtraCompat<ParcelableManga>(AppRouter.KEY_MANGA)
                    if (parcelable != null) {
                        AppRouter(this).showChapterPagesSheet()
                    } else {
                        Snackbar.make(viewBinding.root, org.skepsun.kototoro.R.string.operation_not_supported, Snackbar.LENGTH_SHORT).show()
                    }
                    true
                }
                org.skepsun.kototoro.R.id.action_settings -> {
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
            val source = MangaSource(sourceName)

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

        // 绑定手势提示浮层视图
        overlaySeekLeft = findViewById(org.skepsun.kototoro.R.id.overlay_seek_left)
        overlaySeekRight = findViewById(org.skepsun.kototoro.R.id.overlay_seek_right)
        overlayPlayPause = findViewById(org.skepsun.kototoro.R.id.overlay_play_pause)

        // 初始化音量与亮度上下文
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        initCurrentBrightness()

        // Hook player view gestures: 双击播放/暂停；单击显隐UI；长按左右持续快进/快退
        findViewById<PlayerView>(org.skepsun.kototoro.R.id.player_view)?.let { pv ->
            pv.setControllerShowTimeoutMs(3500)
            pv.setControllerHideOnTouch(false)
            pv.setControllerAutoShow(false)
            pv.setUseController(false)
            pv.isClickable = true

            val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    val w = pv.width.takeIf { it > 0 } ?: -1
                    val x = e.x
                    val p = player
                    if (w > 0 && p != null) {
                        val left = w * 0.33f
                        val right = w * 0.67f
                        when {
                            x < left -> {
                                val newPos = (p.currentPosition - quickTapJumpMs).coerceAtLeast(0)
                                runCatching { p.seekTo(newPos) }.onFailure { runCatching { p.seekBack() } }
                                showOverlayLeft("快退 10 秒")
                            }
                            x > right -> {
                                val dur = p.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                                val newPos = (p.currentPosition + quickTapJumpMs).coerceAtMost(dur)
                                runCatching { p.seekTo(newPos) }.onFailure { runCatching { p.seekForward() } }
                                showOverlayRight("快进 10 秒")
                            }
                            else -> {
                                val wasPlaying = p.isPlaying
                                if (wasPlaying) p.pause() else p.play()
                                showPlayPauseOverlay(if (wasPlaying) "暂停" else "播放")
                            }
                        }
                        updatePlaybackMenu()
                        return true
                    }
                    // 兜底：视图宽度不可用时，保持原有切换播放/暂停行为
                    player?.let { p ->
                        val wasPlaying = p.isPlaying
                        if (wasPlaying) p.pause() else p.play()
                        showPlayPauseOverlay(if (wasPlaying) "暂停" else "播放")
                        updatePlaybackMenu()
                    }
                    return true
                }

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    toggleUiVisibility()
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    val w = pv.width.takeIf { it > 0 } ?: return
                    val dir = if (e.x >= w / 2f) +1 else -1
                    startLongSeek(dir)
                }

                override fun onScroll(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    distanceX: Float,
                    distanceY: Float
                ): Boolean {
                    val w = pv.width.takeIf { it > 0 } ?: return false
                    val h = pv.height.takeIf { it > 0 } ?: return false
                    // 首次判定：竖向位移显著大于横向位移时进入垂直调整模式
                    if (verticalAdjustMode == 0) {
                        if (kotlin.math.abs(distanceY) > kotlin.math.abs(distanceX)) {
                            val startX = e1?.x ?: e2.x
                            verticalAdjustMode = if (startX < w / 2f) -1 else +1
                            verticalAdjustAccum = 0f
                            // 初始提示
                            if (verticalAdjustMode < 0) {
                                val pct = (currentBrightnessNormalized.coerceIn(0f, 1f) * 100).toInt()
                                showOverlayLeft("亮度 ${pct}%")
                            } else {
                                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                val curr = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                val pct = if (max > 0) ((curr * 100f) / max).toInt() else 0
                                showOverlayRight("音量 ${pct}%")
                            }
                        }
                    }
                    if (verticalAdjustMode != 0) {
                        // 避免与长按快进/快退提示冲突
                        hideLongSeekOverlay()
                        // 上滑增加，下滑减少；按屏幕高度归一化
                        val ratioChange = (distanceY) / h.toFloat()
                        verticalAdjustAccum += ratioChange
                        val unit = 0.02f // 每累计 2% 高度触发一次调整，更灵敏
                        while (kotlin.math.abs(verticalAdjustAccum) >= unit) {
                            val increase = verticalAdjustAccum > 0
                            if (verticalAdjustMode < 0) adjustBrightnessByStep(increase) else adjustVolumeByStep(increase)
                            verticalAdjustAccum += if (increase) -unit else unit
                        }
                        return true
                    }
                    return false
                }
                override fun onDown(e: MotionEvent): Boolean {
                    // 返回 true 以确保后续的 onScroll 能被触发
                    return true
                }
            })

            pv.setOnTouchListener { _, ev ->
                detector.onTouchEvent(ev)
                when (ev.actionMasked) {
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        stopLongSeek()
                        verticalAdjustMode = 0
                        overlayHandler.post(hideLeftRunnable)
                        overlayHandler.post(hideRightRunnable)
                    }
                }
                true
            }
        }

        // 兜底点击区域：当控制器隐藏时，任何空白处点击也可唤回 UI
        viewBinding.root.isClickable = true
        viewBinding.root.setOnClickListener { setUiIsVisible(true) }

        // 同步系统导航栏颜色为底栏背景色，实现与小白条区域的视觉合并
        runCatching {
            val navColor = MaterialColors.getColor(viewBinding.root, com.google.android.material.R.attr.colorSurfaceContainer)
            window.navigationBarColor = navColor
        }

        // Wire controller buttons: pages and settings
        findViewById<View>(org.skepsun.kototoro.R.id.button_pages_thumbs)?.let { btn ->
            val parcelable = intent.getParcelableExtraCompat<ParcelableManga>(AppRouter.KEY_MANGA)
            btn.isVisible = parcelable != null
            btn.setOnClickListener {
                AppRouter(this).showChapterPagesSheet()
            }
        }
        findViewById<View>(org.skepsun.kototoro.R.id.button_options)?.setOnClickListener {
            val tag = "VideoSettingsSheet"
            val fm = supportFragmentManager
            if (fm.findFragmentByTag(tag) == null) {
                VideoSettingsSheet().show(fm, tag)
            }
        }

        // 外部控制器初始由 Activity 管理显隐；不直接改动 DockedToolbar 的可见性
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

    private fun prepareAndPlay(url: String, source: ParsersMangaSource?) {
        // OkHttp DataSource with common headers support via X-Manga-Source
        val dsFactory = OkHttpDataSource.Factory(okHttp)
            .setDefaultRequestProperties(
                mapOf(CommonHeaders.MANGA_SOURCE to (source?.name ?: ""))
            )

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dsFactory))
            .build()
            .also { exo ->
                // Avoid direct reference to StyledPlayerView to sidestep IDE/Kotlin type resolution issues
                val pv = findViewById<View>(org.skepsun.kototoro.R.id.player_view)
                try {
                    val setPlayer = pv.javaClass.getMethod("setPlayer", Player::class.java)
                    setPlayer.invoke(pv, exo)
                } catch (_: Exception) {
                    // ignore, player view not available
                }
                // 绑定外部 PlayerControlView
                findViewById<PlayerControlView>(org.skepsun.kototoro.R.id.controller)?.let { ctl ->
                    ctl.player = exo
                    // 控制器常显，避免初始不可见以及进度不更新问题
                    ctl.setShowTimeoutMs(0)
                    ctl.show()
                }
                val mediaItem = MediaItem.Builder()
                    .setUri(url)
                    .setMediaId(url)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(deriveEpisodeTitle(url))
                            .build()
                    )
                    .build()
                exo.setMediaItem(mediaItem)
                updateTitleFromMediaItem(mediaItem)
                updateSubtitleFromMediaItem(mediaItem)
                exo.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            tryApplyInitialSeek(exo)
                        }
                    }
                })
                exo.prepare()
                // 恢复上次播放进度（如有）
                restorePlaybackProgress(exo)
                lifecycleScope.launch {
                    restoreInitialSeekPercentFromHistory()
                    // 若已就绪则立即应用，否则等待监听回调
                    tryApplyInitialSeek(exo)
                }
                exo.play()
            }
            // 监听播放状态/时间线变化，更新顶部菜单
            player?.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updatePlaybackMenu()
                }

                override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                    updatePlaybackMenu()
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    updatePlaybackMenu()
                    updateTitleFromMediaItem(mediaItem)
                    updateSubtitleFromMediaItem(mediaItem)
                }

                override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
                    updatePlaybackMenu()
                }
            })
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        rebuildToolbarMenuForOrientation()
        adjustToolbarForOrientation()
        updateStatusBarByToolbar()
    }

    private fun toggleUiVisibility() {
        setUiIsVisible(!isUiVisible)
    }

    private fun setUiIsVisible(visible: Boolean) {
        isUiVisible = visible
        val isLandscape = isLandscapeOrientation()
        viewBinding.toolbar.isVisible = visible
        // 顶部状态栏遮罩与工具栏同步显隐
        viewBinding.root.findViewById<View>(org.skepsun.kototoro.R.id.status_bar_scrim)?.isVisible = visible
        if (visible) {
            // 当显示 UI 时，将遮罩与工具栏一并置顶，避免被底部控制层或视频内容覆盖
            viewBinding.root.findViewById<View>(org.skepsun.kototoro.R.id.status_bar_scrim)?.bringToFront()
            viewBinding.toolbar.bringToFront()
        }
        systemUiController.setSystemUiVisible(visible)
        updateStatusBarByToolbar()
        if (visible) {
            rebuildToolbarMenuForOrientation()
            adjustToolbarForOrientation()
        }
        // 同步外部 PlayerControlView 与顶部工具栏显隐
        findViewById<PlayerControlView>(org.skepsun.kototoro.R.id.controller)?.let { ctl ->
            if (visible) {
                ctl.visibility = View.VISIBLE
                ctl.show()
            } else {
                ctl.hide()
                ctl.visibility = View.GONE
            }
        }
        // 由外部 PlayerControlView 控制底部显隐
        viewBinding.root.requestApplyInsets()
        if (visible) {
            viewBinding.root.removeCallbacks(hideUiRunnable)
            viewBinding.root.postDelayed(hideUiRunnable, autoHideDelayMs.toLong())
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
        // 仅保留返回与标题：隐藏并不渲染任何菜单项
        viewBinding.toolbar.menuView?.isVisible = false
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
        // 始终隐藏 ActionMenuView，避免占位与溢出
        viewBinding.toolbar.menuView?.isVisible = false
    }

    private fun updateTitleFromMediaItem(mediaItem: MediaItem?) {
        val titleText = mediaItem?.mediaMetadata?.title?.toString()
            .takeUnless { it.isNullOrBlank() }
            ?: intent.getStringExtra(AppRouter.KEY_TITLE).takeUnless { it.isNullOrBlank() }
            ?: intent.getParcelableExtraCompat<ParcelableManga>(AppRouter.KEY_MANGA)?.manga?.title
            ?: intent.getStringExtra(AppRouter.KEY_URL)?.let { deriveEpisodeTitle(it) }
            ?: ""
        supportActionBar?.title = titleText
        viewBinding.toolbar.title = titleText
    }

    private fun updatePlaybackMenu() {
        // 仅在横屏下顶部工具栏包含播放控件
        if (!isLandscapeOrientation()) return
        val menu = viewBinding.toolbar.menu
        val p = player

        // 播放/暂停图标
        menu.findItem(org.skepsun.kototoro.R.id.action_play_pause)?.let { item ->
            val isPlaying = p?.isPlaying == true
            item.setIcon(
                if (isPlaying) org.skepsun.kototoro.R.drawable.ic_pause
                else org.skepsun.kototoro.R.drawable.ic_play
            )
        }

        // 上一/下一是否可用
        menu.findItem(org.skepsun.kototoro.R.id.action_prev)?.isEnabled = (p?.hasPreviousMediaItem() == true)
        menu.findItem(org.skepsun.kototoro.R.id.action_next)?.isEnabled = (p?.hasNextMediaItem() == true)
    }

    private fun updateToolbarProgress() {
        val indicator = viewBinding.toolbarProgress
        // 顶部进度条不再显示
        indicator.isVisible = false
    }

    // 简单时间格式化（mm:ss 或 hh:mm:ss）
    private fun formatTimeMs(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val hours = (totalSec / 3600)
        val minutes = ((totalSec % 3600) / 60)
        val seconds = (totalSec % 60)
        return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
        else String.format("%d:%02d", minutes, seconds)
    }

    // 手动驱动底部控制条（DefaultTimeBar 与已播放时长文本）定时刷新
    private fun updateControllerProgress() {
        if (!isUiVisible) return
        val ctl = findViewById<androidx.media3.ui.PlayerControlView>(org.skepsun.kototoro.R.id.controller) ?: return
        if (ctl.visibility != View.VISIBLE) return

        val p = player ?: return
        val duration = p.duration
        val position = p.currentPosition
        val buffered = p.bufferedPosition

        // 更新文本：当前播放位置 + 总时长
        runCatching {
            val posTv = ctl.findViewById<TextView>(androidx.media3.ui.R.id.exo_position)
            val durTv = ctl.findViewById<TextView>(androidx.media3.ui.R.id.exo_duration)
            posTv?.text = formatTimeMs(position)
            if (duration > 0) {
                durTv?.text = formatTimeMs(duration)
            }
        }

        // 更新时间条进度
        runCatching {
            val timeBar = ctl.findViewById<androidx.media3.ui.DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)
            if (duration > 0) {
                timeBar?.setDuration(duration)
                timeBar?.setBufferedPosition(buffered)
                timeBar?.setPosition(position)
            }
        }
    }

    private fun updateStatusBarByToolbar() {
        val color = MaterialColors.getColor(viewBinding.toolbar, com.google.android.material.R.attr.colorSurfaceContainerHigh)
        // 更新状态栏图标明暗以匹配遮罩背景颜色
        val isLight = ColorUtils.calculateLuminance(color) > 0.5
        WindowInsetsControllerCompat(window, viewBinding.root).setAppearanceLightStatusBars(isLight)
        // 遮罩视图背景颜色与工具栏保持一致
        viewBinding.root.findViewById<View>(org.skepsun.kototoro.R.id.status_bar_scrim)?.setBackgroundColor(color)
    }

    private fun deriveEpisodeTitle(url: String): String {
        return runCatching {
            val uri = Uri.parse(url)
            val raw = uri.lastPathSegment ?: url
            URLDecoder.decode(raw, "UTF-8")
        }.getOrElse { url }
    }

    private fun updateSubtitleFromMediaItem(mediaItem: MediaItem?) {
        val title = mediaItem?.mediaMetadata?.title
        if (!title.isNullOrEmpty()) {
            val sub = title.toString()
            supportActionBar?.subtitle = sub
            viewBinding.toolbar.subtitle = sub
        } else {
            intent.getStringExtra(AppRouter.KEY_URL)?.let { url ->
                val sub = deriveEpisodeTitle(url)
                supportActionBar?.subtitle = sub
                viewBinding.toolbar.subtitle = sub
            }
        }
    }

    fun showQualityDialog() {
        val exo = player ?: return
        val labels = mutableListOf(getString(org.skepsun.kototoro.R.string.quality_auto))
        val overrides = mutableListOf<TrackSelectionOverride?>(null)
        val tracks: Tracks = exo.currentTracks
        for (i in 0 until tracks.groups.size) {
            val group = tracks.groups[i]
            if (group.type != C.TRACK_TYPE_VIDEO) continue
            val trackCount = group.length
            for (t in 0 until trackCount) {
                if (!group.isTrackSupported(t)) continue
                val format: Format = group.getTrackFormat(t)
                val height = format.height
                val bitrateKbps = if (format.bitrate != Format.NO_VALUE) format.bitrate / 1000 else 0
                val label = buildString {
                    if (height > 0) append("${height}p") else append("Video")
                    if (bitrateKbps > 0) append(" (${bitrateKbps} kbps)")
                }
                labels.add(label)
                overrides.add(TrackSelectionOverride(group.mediaTrackGroup, listOf(t)))
            }
        }
        val dialog: AlertDialog = buildAlertDialog(this) {
            setTitle(org.skepsun.kototoro.R.string.quality_select)
            setSingleChoiceItems(labels.toTypedArray(), 0) { d: DialogInterface, which: Int ->
                val builder: TrackSelectionParameters.Builder = exo.trackSelectionParameters.buildUpon()
                builder.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                val selectedOverride = overrides.getOrNull(which)
                if (selectedOverride != null) {
                    builder.setOverrideForType(selectedOverride)
                }
                exo.trackSelectionParameters = builder.build()
                d.dismiss()
            }
        }
        dialog.show()
    }

    override fun onStop() {
        viewBinding.root.removeCallbacks(hideUiRunnable)
        viewBinding.root.removeCallbacks(progressUpdateRunnable)
        viewBinding.root.removeCallbacks(controllerProgressRunnable)
        stopLongSeek()
        super.onStop()
        // 保存当前播放进度（本地与历史）
        savePlaybackProgress()
        saveHistoryProgressAsync()
        player?.pause()
    }

    override fun onDestroy() {
        viewBinding.root.removeCallbacks(hideUiRunnable)
        viewBinding.root.removeCallbacks(progressUpdateRunnable)
        viewBinding.root.removeCallbacks(controllerProgressRunnable)
        stopLongSeek()
        // 兜底保存进度（本地与历史）
        savePlaybackProgress()
        saveHistoryProgressAsync()
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun savePlaybackProgress() {
        val exo = player ?: return
        val id = exo.currentMediaItem?.mediaId ?: return
        val pos = exo.currentPosition
        runCatching {
            getSharedPreferences("video_progress", MODE_PRIVATE)
                .edit()
                .putLong(id, pos)
                .apply()
        }
    }

    private fun restorePlaybackProgress(exo: ExoPlayer) {
        val id = exo.currentMediaItem?.mediaId ?: return
        val pos = getSharedPreferences("video_progress", MODE_PRIVATE).getLong(id, 0L)
        if (pos > 0L) {
            exo.seekTo(pos)
        }
    }

    private suspend fun restoreInitialSeekPercentFromHistory() {
        val manga = intent.getParcelableExtraCompat<ParcelableManga>(AppRouter.KEY_MANGA)?.manga ?: return
        val history = runCatching { historyRepository.getOne(manga) }.getOrNull() ?: return
        val overall = history.percent
        if (overall !in 0f..1f) return
        val chapters = manga.chapters ?: run {
            // 无章节信息时无法拆分整体百分比，直接使用整体值（退化为单集）
            pendingInitialSeekPercent = overall
            return
        }
        val chapter = chapters.find { it.id == history.chapterId } ?: run {
            pendingInitialSeekPercent = overall
            return
        }
        val branchChapters = chapters.filter { it.branch == chapter.branch }
        val count = branchChapters.size
        if (count <= 0) {
            pendingInitialSeekPercent = overall
            return
        }
        val idx = branchChapters.indexOfFirst { it.id == chapter.id }.coerceAtLeast(0)
        // 单集百分比 = 整体百分比 * 总集数 - 当前集索引
        val episodePercent = (overall * count - idx).coerceIn(0f, 1f)
        pendingInitialSeekPercent = episodePercent
    }

    private fun tryApplyInitialSeek(exo: ExoPlayer) {
        // 若已通过本地毫秒值恢复，保持当前位置不再用百分比覆盖
        if (exo.currentPosition > 0L) {
            pendingInitialSeekPercent = null
            return
        }
        val p = pendingInitialSeekPercent ?: return
        val dur = exo.duration
        if (dur > 0 && dur != C.TIME_UNSET) {
            exo.seekTo((p * dur).toLong())
            pendingInitialSeekPercent = null
        }
    }

    private fun saveHistoryProgressAsync() {
        val exo = player ?: return
        val mangaSeed = intent.getParcelableExtraCompat<ParcelableManga>(AppRouter.KEY_MANGA)?.manga ?: return
        val dur = exo.duration
        val pos = exo.currentPosition
        // 当时长未知（直播或刚开始播放）时，也保存一个有效百分比以建立历史记录
        val episodePercent = if (dur > 0 && dur != C.TIME_UNSET) {
            (pos.toFloat() / dur).coerceIn(0f, 1f)
        } else 0f

        val state = readerState

        fun computeSeriesPercent(m: org.skepsun.kototoro.parsers.model.Manga, s: ReaderState, ep: Float): Float {
            val chapters = m.chapters ?: return ep
            val curr = chapters.find { it.id == s.chapterId } ?: return ep
            val branchChapters = chapters.filter { it.branch == curr.branch }
            val count = branchChapters.size
            if (count <= 0) return ep
            val idx = branchChapters.indexOfFirst { it.id == curr.id }.coerceAtLeast(0)
            val ppc = 1f / count
            return (ppc * idx + ppc * ep).coerceIn(0f, 1f)
        }

        // 其余部分需要加载详情以确保 chapters 非空
        lifecycleScope.launch {
            // 先确保漫画详情含章节
            val repo = mangaRepositoryFactory.create(mangaSeed.source)
            val manga = if (mangaSeed.chapters.isNullOrEmpty()) runCatching { repo.getDetails(mangaSeed) }.getOrDefault(mangaSeed) else mangaSeed
            // 若仍无章节信息（网络/源不可用），避免保存触发断言失败
            if (manga.chapters.isNullOrEmpty()) return@launch

            if (state != null) {
                // ReaderState 已提供：直接计算整体百分比并保存
                val overall = computeSeriesPercent(manga, state, episodePercent)
                historyUpdateUseCase.invokeAsync(manga, state, overall)
            } else {
                // 无 ReaderState：优先使用已有历史，否则用首章构造
                val history = runCatching { historyRepository.getOne(manga) }.getOrNull()
                val fallbackState = history?.let { ReaderState(it) } ?: runCatching { ReaderState(manga, null) }.getOrNull()
                if (fallbackState != null) {
                    val overall = computeSeriesPercent(manga, fallbackState, episodePercent)
                    historyUpdateUseCase.invokeAsync(manga, fallbackState, overall)
                }
            }
        }
    }

    override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
        val type = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
        val bars = insets.getInsets(type)
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
        // 将 DockedToolbar 与系统导航栏视觉合并：为其设置左右/底部边距
        findViewById<View>(org.skepsun.kototoro.R.id.toolbar_docked)?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            leftMargin = bars.left
            rightMargin = bars.right
            bottomMargin = 0
        }
        // 为 DockedToolbar 追加底部内边距，使内容不被导航白条遮挡，同时背景延伸至底部实现视觉合并
        findViewById<View>(org.skepsun.kototoro.R.id.toolbar_docked)?.updatePadding(
            left = bars.left,
            right = bars.right,
            bottom = bars.bottom,
        )
        // PlayerView 内容保持与左右系统栏对齐，底部不再额外内边距，避免与 DockedToolbar 重叠留白
        findViewById<View>(org.skepsun.kototoro.R.id.player_view).updatePadding(
            left = bars.left,
            right = bars.right,
            bottom = 0,
        )
        return insets.consumeAll(type)
    }
}