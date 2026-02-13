package org.skepsun.kototoro.video.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.ui.sheet.BaseAdaptiveSheet
import org.skepsun.kototoro.core.util.ext.consume
import org.skepsun.kototoro.core.util.ext.viewLifecycleScope
import org.skepsun.kototoro.databinding.SheetVideoSettingsBinding
import org.skepsun.kototoro.reader.ui.ScreenOrientationHelper

/**
 * 瑙嗛鈥滄洿澶氳缃€濆簳閮?Sheet銆?
 * 鎻愪緵灞忓箷鏃嬭浆鎺у埗锛氳嚜鍔ㄦ棆杞笅鏄剧ず鈥滈攣瀹氣€濆紑鍏筹紱鍚﹀垯鏄剧ず鈥滄墜鍔ㄦ棆杞€濇寜閽€?
 */
@AndroidEntryPoint
class VideoSettingsSheet : BaseAdaptiveSheet<SheetVideoSettingsBinding>() {

    @Inject
    lateinit var orientationHelper: ScreenOrientationHelper

    @Inject
    lateinit var appSettings: AppSettings

    override fun onCreateViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): SheetVideoSettingsBinding {
        return SheetVideoSettingsBinding.inflate(inflater, container, false)
    }

    override fun onViewBindingCreated(
        binding: SheetVideoSettingsBinding,
        savedInstanceState: Bundle?,
    ) {
        super.onViewBindingCreated(binding, savedInstanceState)
        binding.headerBar.setTitle(R.string.options)
        // 工具栏已有入口，隐藏重复操作项
        binding.buttonQuality.isGone = true
        binding.buttonDanmakuSettings.isGone = true
        binding.buttonScreenRotate.isGone = true

        binding.buttonReload.setOnClickListener {
            (activity as? VideoPlayerActivity)?.reloadPlayback()
            dismissAllowingStateLoss()
        }

        // 娓呮櫚搴﹁缃叆鍙ｏ細鐩存帴璋冪敤瀹夸富 Activity 鐨勬竻鏅板害閫夋嫨
        binding.buttonQuality.setOnClickListener {
            (activity as? VideoPlayerActivity)?.showQualityDialog()
        }
        binding.buttonSuperResolution.setOnClickListener {
            val fm = parentFragmentManager
            val tag = "VideoSuperResolutionSheet"
            if (fm.findFragmentByTag(tag) == null) {
                VideoSuperResolutionSheet().show(fm, tag)
            }
        }
        binding.buttonPlaybackSpeed.setOnClickListener {
            showSpeedDialog(
                titleRes = R.string.video_playback_speed,
                current = appSettings.videoPlaybackSpeed,
            ) { speed ->
                appSettings.videoPlaybackSpeed = speed
                (activity as? VideoPlayerActivity)?.applyPlaybackSpeed(speed)
                updateSpeedLabels()
            }
        }
        binding.buttonDefaultSpeed.setOnClickListener {
            showSpeedDialog(
                titleRes = R.string.video_default_speed,
                current = appSettings.videoDefaultSpeed,
            ) { speed ->
                appSettings.videoDefaultSpeed = speed
                updateSpeedLabels()
            }
        }
        binding.buttonSeekForwardTime.setOnClickListener {
            showSeekDialog(
                titleRes = R.string.video_seek_forward_time,
                currentMs = appSettings.videoSeekForwardMs,
            ) { value ->
                appSettings.videoSeekForwardMs = value
                updateSpeedLabels()
            }
        }
        binding.buttonSeekBackwardTime.setOnClickListener {
            showSeekDialog(
                titleRes = R.string.video_seek_backward_time,
                currentMs = appSettings.videoSeekBackwardMs,
            ) { value ->
                appSettings.videoSeekBackwardMs = value
                updateSpeedLabels()
            }
        }
        binding.buttonDanmakuSettings.setOnClickListener {
            val fm = parentFragmentManager
            val tag = "VideoDanmakuSettingsSheet"
            if (fm.findFragmentByTag(tag) == null) {
                VideoDanmakuSettingsSheet().show(fm, tag)
            }
        }
        binding.switchDanmaku.isChecked = appSettings.videoDanmakuEnabled
        binding.switchDanmaku.setOnCheckedChangeListener { _, isChecked ->
            appSettings.videoDanmakuEnabled = isChecked
            (activity as? VideoPlayerActivity)?.applyDanmakuSettings()
            binding.buttonDanmakuSettings.isVisible = isChecked
        }
        binding.buttonDanmakuSettings.isVisible = appSettings.videoDanmakuEnabled
        binding.switchVolumeBoost.isChecked = appSettings.videoVolumeBoostEnabled
        binding.switchVolumeBoost.setOnCheckedChangeListener { _, checked ->
            appSettings.videoVolumeBoostEnabled = checked
            (activity as? VideoPlayerActivity)?.applyPlaybackOptions()
        }
        binding.switchDeband.isChecked = appSettings.videoDebandEnabled
        binding.switchDeband.setOnCheckedChangeListener { _, checked ->
            appSettings.videoDebandEnabled = checked
            (activity as? VideoPlayerActivity)?.applyPlaybackOptions()
        }
        binding.switchAutoNext.isChecked = appSettings.videoAutoNextEnabled
        binding.switchAutoNext.setOnCheckedChangeListener { _, checked ->
            appSettings.videoAutoNextEnabled = checked
        }

        binding.buttonScreenRotate.setOnClickListener {
            orientationHelper.isLandscape = !orientationHelper.isLandscape
        }
        binding.switchScreenLockRotation.setOnCheckedChangeListener { _, isChecked ->
            orientationHelper.isLocked = isChecked
        }

        observeScreenOrientation()
        updateOrientationLockSwitch()
        updateSpeedLabels()
    }

    private fun observeScreenOrientation() {
        orientationHelper.observeAutoOrientation()
            .onEach { auto ->
                with(requireViewBinding()) {
                    buttonScreenRotate.isGone = auto
                    switchScreenLockRotation.isVisible = auto
                    updateOrientationLockSwitch()
                }
            }.launchIn(viewLifecycleScope)
    }

    private fun updateOrientationLockSwitch() {
        val switch = viewBinding?.switchScreenLockRotation ?: return
        switch.setOnCheckedChangeListener(null)
        switch.isChecked = orientationHelper.isLocked
        switch.setOnCheckedChangeListener { _, isChecked ->
            orientationHelper.isLocked = isChecked
        }
    }

    private fun updateSpeedLabels() {
        val binding = viewBinding ?: return
        val playback = appSettings.videoPlaybackSpeed
        val defaultSpeed = appSettings.videoDefaultSpeed
        val forward = appSettings.videoSeekForwardMs / 1000
        val backward = appSettings.videoSeekBackwardMs / 1000
        binding.buttonPlaybackSpeed.text = getString(
            R.string.video_playback_speed,
        ) + " ${"%.2fx".format(playback)}"
        binding.buttonDefaultSpeed.text = getString(
            R.string.video_default_speed,
        ) + " ${"%.2fx".format(defaultSpeed)}"
        binding.buttonSeekForwardTime.text = getString(
            R.string.video_seek_forward_time,
        ) + " ${forward}s"
        binding.buttonSeekBackwardTime.text = getString(
            R.string.video_seek_backward_time,
        ) + " ${backward}s"
    }

    private fun showSpeedDialog(
        titleRes: Int,
        current: Float,
        onSelect: (Float) -> Unit,
    ) {
        val options = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
        val labels = options.map { "${"%.2fx".format(it)}" }.toTypedArray()
        val checked = options.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }
            .takeIf { it >= 0 } ?: 2
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(titleRes)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                onSelect(options[which])
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showSeekDialog(
        titleRes: Int,
        currentMs: Int,
        onSelect: (Int) -> Unit,
    ) {
        val options = listOf(5, 10, 15, 30)
        val labels = options.map { "${it}s" }.toTypedArray()
        val checked = options.indexOfFirst { it * 1000 == currentMs }
            .takeIf { it >= 0 } ?: 1
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(titleRes)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                onSelect(options[which] * 1000)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
        val typeMask = WindowInsetsCompat.Type.systemBars()
        viewBinding?.scrollView?.updatePadding(
            bottom = insets.getInsets(typeMask).bottom,
        )
        return insets.consume(v, typeMask, bottom = true)
    }
}

