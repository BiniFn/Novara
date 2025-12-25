package org.skepsun.kototoro.reader.novel

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.slider.Slider
import org.skepsun.kototoro.databinding.SheetNovelReaderConfigBinding

/**
 * 小说阅读器设置面板
 */
class NovelReaderConfigSheet : BottomSheetDialogFragment(),
    Slider.OnChangeListener,
    CompoundButton.OnCheckedChangeListener,
    View.OnClickListener {

    private var _binding: SheetNovelReaderConfigBinding? = null
    private val binding get() = _binding!!

    private lateinit var settings: NovelReaderSettings
    private var callback: Callback? = null
    
    // 防抖动处理
    private val handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null
    private val updateDelay = 150L // 150ms 延迟

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetNovelReaderConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 获取回调
        callback = parentFragment as? Callback ?: activity as? Callback

        // 加载设置
        settings = NovelReaderSettings.load(requireContext())

        // 初始化控件
        binding.sliderFontSize.value = settings.fontSizeSp
        binding.sliderLineSpacing.value = settings.lineSpacing
        binding.sliderParagraphSpacing.value = settings.paragraphSpacing
        binding.sliderMargin.value = settings.marginHorizontal.toFloat()
        binding.switchDualPage.isChecked = settings.enableDualPage
        binding.switchFullscreen.isChecked = settings.enableFullscreen
        binding.switchShowReadingStatus.isChecked = settings.showReadingStatus
        binding.switchReadingStatusTransparent.isChecked = settings.isReadingStatusTransparent

        // 初始化值显示
        updateValueDisplays()

        // 设置监听器
        binding.sliderFontSize.addOnChangeListener(this)
        binding.sliderLineSpacing.addOnChangeListener(this)
        binding.sliderParagraphSpacing.addOnChangeListener(this)
        binding.sliderMargin.addOnChangeListener(this)
        binding.switchDualPage.setOnCheckedChangeListener(this)
        binding.switchFullscreen.setOnCheckedChangeListener(this)
        binding.switchShowReadingStatus.setOnCheckedChangeListener(this)
        binding.switchReadingStatusTransparent.setOnCheckedChangeListener(this)
        binding.buttonBookmark.setOnClickListener(this)
        binding.buttonReset.setOnClickListener(this)
        binding.buttonClose.setOnClickListener(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 清理防抖动任务
        updateRunnable?.let { handler.removeCallbacks(it) }
        updateRunnable = null
        _binding = null
    }

    override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
        if (!fromUser) return

        settings = when (slider.id) {
            org.skepsun.kototoro.R.id.sliderFontSize -> settings.copy(fontSizeSp = value)
            org.skepsun.kototoro.R.id.sliderLineSpacing -> settings.copy(lineSpacing = value)
            org.skepsun.kototoro.R.id.sliderParagraphSpacing -> settings.copy(paragraphSpacing = value)
            org.skepsun.kototoro.R.id.sliderMargin -> settings.copy(
                marginHorizontal = value.toInt(),
                marginVertical = value.toInt()
            )
            else -> return
        }

        updateValueDisplays()
        applySettingsDebounced()
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        when (buttonView.id) {
            org.skepsun.kototoro.R.id.switchDualPage -> {
                settings = settings.copy(enableDualPage = isChecked)
                applySettings()
            }
            org.skepsun.kototoro.R.id.switchFullscreen -> {
                settings = settings.copy(enableFullscreen = isChecked)
                applySettings()
            }
            org.skepsun.kototoro.R.id.switchShowReadingStatus -> {
                settings = settings.copy(showReadingStatus = isChecked)
                applySettings()
            }
            org.skepsun.kototoro.R.id.switchReadingStatusTransparent -> {
                settings = settings.copy(isReadingStatusTransparent = isChecked)
                applySettings()
            }
        }
    }

    override fun onClick(v: View) {
        when (v.id) {
            org.skepsun.kototoro.R.id.buttonBookmark -> {
                callback?.onBookmarkClick()
                dismiss()
            }
            org.skepsun.kototoro.R.id.buttonReset -> resetSettings()
            org.skepsun.kototoro.R.id.buttonClose -> dismiss()
        }
    }

    private fun applySettings() {
        settings.save(requireContext())
        callback?.onSettingsChanged(settings)
    }

    /**
     * 防抖动应用设置 - 避免频繁更新
     */
    private fun applySettingsDebounced() {
        // 取消之前的更新
        updateRunnable?.let { handler.removeCallbacks(it) }
        
        // 创建新的更新任务
        updateRunnable = Runnable {
            applySettings()
        }
        
        // 延迟执行
        handler.postDelayed(updateRunnable!!, updateDelay)
    }

    private fun resetSettings() {
        // 重置为默认值
        settings = NovelReaderSettings()

        // 更新 UI
        binding.sliderFontSize.value = settings.fontSizeSp
        binding.sliderLineSpacing.value = settings.lineSpacing
        binding.sliderParagraphSpacing.value = settings.paragraphSpacing
        binding.sliderMargin.value = settings.marginHorizontal.toFloat()
        binding.switchDualPage.isChecked = settings.enableDualPage
        binding.switchFullscreen.isChecked = settings.enableFullscreen
        binding.switchShowReadingStatus.isChecked = settings.showReadingStatus
        binding.switchReadingStatusTransparent.isChecked = settings.isReadingStatusTransparent

        updateValueDisplays()
        applySettings()
    }

    private fun updateValueDisplays() {
        binding.textFontSizeValue.text = "${settings.fontSizeSp.toInt()}sp"
        binding.textLineSpacingValue.text = String.format("%.1f", settings.lineSpacing)
        binding.textParagraphSpacingValue.text = "${settings.paragraphSpacing.toInt()}dp"
        binding.textMarginValue.text = "${settings.marginHorizontal}dp"
    }

    interface Callback {
        fun onSettingsChanged(settings: NovelReaderSettings)
        fun onBookmarkClick()
    }

    companion object {
        fun newInstance(): NovelReaderConfigSheet {
            return NovelReaderConfigSheet()
        }
    }
}
