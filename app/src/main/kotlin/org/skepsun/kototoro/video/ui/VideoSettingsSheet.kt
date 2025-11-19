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
import org.skepsun.kototoro.core.ui.sheet.BaseAdaptiveSheet
import org.skepsun.kototoro.core.util.ext.consume
import org.skepsun.kototoro.core.util.ext.viewLifecycleScope
import org.skepsun.kototoro.databinding.SheetVideoSettingsBinding
import org.skepsun.kototoro.reader.ui.ScreenOrientationHelper

/**
 * 视频“更多设置”底部 Sheet。
 * 提供屏幕旋转控制：自动旋转下显示“锁定”开关；否则显示“手动旋转”按钮。
 */
@AndroidEntryPoint
class VideoSettingsSheet : BaseAdaptiveSheet<SheetVideoSettingsBinding>() {

    @Inject
    lateinit var orientationHelper: ScreenOrientationHelper

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

        // 清晰度设置入口：直接调用宿主 Activity 的清晰度选择
        binding.buttonQuality.setOnClickListener {
            (activity as? VideoPlayerActivity)?.showQualityDialog()
        }

        binding.buttonScreenRotate.setOnClickListener {
            orientationHelper.isLandscape = !orientationHelper.isLandscape
        }
        binding.switchScreenLockRotation.setOnCheckedChangeListener { _, isChecked ->
            orientationHelper.isLocked = isChecked
        }

        observeScreenOrientation()
        updateOrientationLockSwitch()
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

    override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
        val typeMask = WindowInsetsCompat.Type.systemBars()
        viewBinding?.scrollView?.updatePadding(
            bottom = insets.getInsets(typeMask).bottom,
        )
        return insets.consume(v, typeMask, bottom = true)
    }
}