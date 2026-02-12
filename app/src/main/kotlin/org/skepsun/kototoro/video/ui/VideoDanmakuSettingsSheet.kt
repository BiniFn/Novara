package org.skepsun.kototoro.video.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.slider.Slider
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.ui.sheet.BaseAdaptiveSheet
import org.skepsun.kototoro.core.util.ext.consume
import org.skepsun.kototoro.databinding.SheetVideoDanmakuSettingsBinding

@AndroidEntryPoint
class VideoDanmakuSettingsSheet : BaseAdaptiveSheet<SheetVideoDanmakuSettingsBinding>() {

    @Inject
    lateinit var appSettings: AppSettings

    override fun onCreateViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): SheetVideoDanmakuSettingsBinding {
        return SheetVideoDanmakuSettingsBinding.inflate(inflater, container, false)
    }

    override fun onViewBindingCreated(
        binding: SheetVideoDanmakuSettingsBinding,
        savedInstanceState: Bundle?,
    ) {
        super.onViewBindingCreated(binding, savedInstanceState)
        binding.headerBar.setTitle(R.string.video_danmaku_settings)
        bindSlider(
            binding.sliderSize,
            appSettings.videoDanmakuSizePercent,
        ) { appSettings.videoDanmakuSizePercent = it }
        bindSlider(
            binding.sliderSpeed,
            appSettings.videoDanmakuSpeedPercent,
        ) { appSettings.videoDanmakuSpeedPercent = it }
        bindSlider(
            binding.sliderOpacity,
            appSettings.videoDanmakuOpacityPercent,
        ) { appSettings.videoDanmakuOpacityPercent = it }
        bindSlider(
            binding.sliderStroke,
            appSettings.videoDanmakuStrokePercent,
        ) { appSettings.videoDanmakuStrokePercent = it }
        bindSlider(
            binding.sliderMaxScreen,
            appSettings.videoDanmakuMaxScreenNum,
        ) { appSettings.videoDanmakuMaxScreenNum = it }
        bindSlider(
            binding.sliderMaxScrollLines,
            appSettings.videoDanmakuMaxScrollLines,
        ) { appSettings.videoDanmakuMaxScrollLines = it }
        bindSlider(
            binding.sliderMaxTopLines,
            appSettings.videoDanmakuMaxTopLines,
        ) { appSettings.videoDanmakuMaxTopLines = it }
        bindSlider(
            binding.sliderMaxBottomLines,
            appSettings.videoDanmakuMaxBottomLines,
        ) { appSettings.videoDanmakuMaxBottomLines = it }

        binding.switchScroll.isChecked = appSettings.videoDanmakuShowScroll
        binding.switchTop.isChecked = appSettings.videoDanmakuShowTop
        binding.switchBottom.isChecked = appSettings.videoDanmakuShowBottom
        binding.switchSourceDandan.isChecked = appSettings.videoDanmakuSourceDanDan
        binding.switchSourceBilibili.isChecked = appSettings.videoDanmakuSourceBilibili
        binding.switchSourceQq.isChecked = appSettings.videoDanmakuSourceQq

        binding.switchScroll.setOnCheckedChangeListener { _, checked ->
            appSettings.videoDanmakuShowScroll = checked
            notifySettingsChanged()
        }
        binding.switchTop.setOnCheckedChangeListener { _, checked ->
            appSettings.videoDanmakuShowTop = checked
            notifySettingsChanged()
        }
        binding.switchBottom.setOnCheckedChangeListener { _, checked ->
            appSettings.videoDanmakuShowBottom = checked
            notifySettingsChanged()
        }
        binding.switchSourceDandan.setOnCheckedChangeListener { _, checked ->
            appSettings.videoDanmakuSourceDanDan = checked
            notifySettingsChanged()
        }
        binding.switchSourceBilibili.setOnCheckedChangeListener { _, checked ->
            appSettings.videoDanmakuSourceBilibili = checked
            notifySettingsChanged()
        }
        binding.switchSourceQq.setOnCheckedChangeListener { _, checked ->
            appSettings.videoDanmakuSourceQq = checked
            notifySettingsChanged()
        }
    }

    private fun bindSlider(
        slider: Slider,
        value: Int,
        onChange: (Int) -> Unit,
    ) {
        slider.value = value.toFloat()
        slider.setLabelFormatter { "${it.toInt()}" }
        slider.addOnChangeListener { _, v, fromUser ->
            if (!fromUser) return@addOnChangeListener
            onChange(v.toInt())
            notifySettingsChanged()
        }
    }

    private fun notifySettingsChanged() {
        (activity as? VideoPlayerActivity)?.applyDanmakuSettings()
    }

    override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
        val typeMask = WindowInsetsCompat.Type.systemBars()
        viewBinding?.scrollView?.updatePadding(
            bottom = insets.getInsets(typeMask).bottom,
        )
        return insets.consume(v, typeMask, bottom = true)
    }
}
