package org.skepsun.kototoro.video.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.ui.sheet.BaseAdaptiveSheet
import org.skepsun.kototoro.core.util.ext.consume
import org.skepsun.kototoro.databinding.SheetVideoSubtitleSettingsBinding
import javax.inject.Inject

@AndroidEntryPoint
class VideoSubtitleSettingsSheet : BaseAdaptiveSheet<SheetVideoSubtitleSettingsBinding>() {

    @Inject
    lateinit var appSettings: AppSettings

    private var currentColorTarget = 0 // 0=Text, 1=Border, 2=Bg

    override fun onCreateViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): SheetVideoSubtitleSettingsBinding {
        return SheetVideoSubtitleSettingsBinding.inflate(inflater, container, false)
    }

    override fun onViewBindingCreated(
        binding: SheetVideoSubtitleSettingsBinding,
        savedInstanceState: Bundle?,
    ) {
        super.onViewBindingCreated(binding, savedInstanceState)

        binding.tabs.addTab(binding.tabs.newTab().setText("Typography"))
        binding.tabs.addTab(binding.tabs.newTab().setText("Layout"))
        binding.tabs.addTab(binding.tabs.newTab().setText("Colors"))

        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                binding.containerTypography.isGone = tab?.position != 0
                binding.containerLayout.isGone = tab?.position != 1
                binding.containerColors.isGone = tab?.position != 2
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // Typography settings
        if (appSettings.videoSubtitleBold) binding.groupStyleFormat.check(R.id.btn_bold)
        if (appSettings.videoSubtitleItalic) binding.groupStyleFormat.check(R.id.btn_italic)

        when (appSettings.videoSubtitleAlignX) {
            0 -> binding.groupStyleAlign.check(R.id.btn_align_left)
            2 -> binding.groupStyleAlign.check(R.id.btn_align_right)
            else -> binding.groupStyleAlign.check(R.id.btn_align_center)
        }

        // Format settings
        binding.groupStyleFormat.addOnButtonCheckedListener { _, checkedId, isChecked ->
            when (checkedId) {
                R.id.btn_bold -> appSettings.videoSubtitleBold = isChecked
                R.id.btn_italic -> appSettings.videoSubtitleItalic = isChecked
            }
            applyToPlayer()
        }

        binding.groupStyleAlign.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btn_align_left -> appSettings.videoSubtitleAlignX = 0
                    R.id.btn_align_center -> appSettings.videoSubtitleAlignX = 1
                    R.id.btn_align_right -> appSettings.videoSubtitleAlignX = 2
                }
                applyToPlayer()
            }
        }

        binding.sliderFontSize.value = appSettings.videoSubtitleFontSize.coerceIn(10f, 100f)
        binding.sliderFontSize.addOnChangeListener { _, value, _ ->
            appSettings.videoSubtitleFontSize = value
            applyToPlayer()
            binding.tvFontSize.text = getString(R.string.video_subtitle_font_size) + " (${value.toInt()})"
        }
        binding.tvFontSize.text = getString(R.string.video_subtitle_font_size) + " (${appSettings.videoSubtitleFontSize.toInt()})"

        binding.sliderBorderSize.value = appSettings.videoSubtitleBorderSize.coerceIn(0f, 24f)
        binding.sliderBorderSize.addOnChangeListener { _, value, _ ->
            appSettings.videoSubtitleBorderSize = value
            applyToPlayer()
            binding.tvBorderSize.text = getString(R.string.video_subtitle_border_size) + " (${value.toInt()})"
        }
        binding.tvBorderSize.text = getString(R.string.video_subtitle_border_size) + " (${appSettings.videoSubtitleBorderSize.toInt()})"

        // Layout settings
        binding.sliderPosition.value = appSettings.videoSubtitlePosition.toFloat().coerceIn(0f, 300f)
        binding.sliderPosition.addOnChangeListener { _, value, _ ->
            appSettings.videoSubtitlePosition = value.toInt()
            applyToPlayer()
            binding.tvPosition.text = getString(R.string.video_subtitle_position) + " (${value.toInt()})"
        }
        binding.tvPosition.text = getString(R.string.video_subtitle_position) + " (${appSettings.videoSubtitlePosition})"

        // Colors settings
        binding.groupColorTarget.check(R.id.btn_color_text)
        binding.groupColorTarget.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                currentColorTarget = when (checkedId) {
                    R.id.btn_color_text -> 0
                    R.id.btn_color_border -> 1
                    R.id.btn_color_bg -> 2
                    else -> 0
                }
                loadCurrentColorToSliders()
            }
        }

        loadCurrentColorToSliders()

        val colorChangeListener = com.google.android.material.slider.Slider.OnChangeListener { _, _, _ ->
            val a = binding.sliderColorA.value.toInt()
            val r = binding.sliderColorR.value.toInt()
            val g = binding.sliderColorG.value.toInt()
            val b = binding.sliderColorB.value.toInt()
            val color = Color.argb(a, r, g, b)

            when (currentColorTarget) {
                0 -> appSettings.videoSubtitleTextColor = color
                1 -> appSettings.videoSubtitleBorderColor = color
                2 -> appSettings.videoSubtitleBgColor = color
            }
            applyToPlayer()
        }

        binding.sliderColorA.addOnChangeListener(colorChangeListener)
        binding.sliderColorR.addOnChangeListener(colorChangeListener)
        binding.sliderColorG.addOnChangeListener(colorChangeListener)
        binding.sliderColorB.addOnChangeListener(colorChangeListener)
    }

    private fun loadCurrentColorToSliders() {
        val binding = viewBinding ?: return
        val color = when (currentColorTarget) {
            0 -> appSettings.videoSubtitleTextColor
            1 -> appSettings.videoSubtitleBorderColor
            else -> appSettings.videoSubtitleBgColor
        }
        val a = Color.alpha(color).toFloat()
        val r = Color.red(color).toFloat()
        val g = Color.green(color).toFloat()
        val b = Color.blue(color).toFloat()

        binding.sliderColorA.value = a.coerceIn(0f, 255f)
        binding.sliderColorR.value = r.coerceIn(0f, 255f)
        binding.sliderColorG.value = g.coerceIn(0f, 255f)
        binding.sliderColorB.value = b.coerceIn(0f, 255f)
    }

    private fun applyToPlayer() {
        (activity as? VideoPlayerActivity)?.applySubtitleOverlayStyle()
    }

    override fun onApplyWindowInsets(v: android.view.View, insets: WindowInsetsCompat): WindowInsetsCompat {
        val typeMask = WindowInsetsCompat.Type.systemBars()
        viewBinding?.scrollView?.updatePadding(
            bottom = insets.getInsets(typeMask).bottom,
        )
        return insets.consume(v, typeMask, bottom = true)
    }
}
