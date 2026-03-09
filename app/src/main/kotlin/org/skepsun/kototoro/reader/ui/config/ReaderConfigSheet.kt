package org.skepsun.kototoro.reader.ui.config

import android.os.Bundle
import android.app.Dialog
import android.content.res.Configuration
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.appcompat.view.ActionMode
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.activityViewModels
import androidx.transition.TransitionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.sidesheet.SideSheetDialog
import com.google.android.material.sidesheet.SideSheetBehavior
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.slider.Slider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.parser.MangaRepository
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.ReaderMode
import org.skepsun.kototoro.core.ui.sheet.BaseAdaptiveSheet
import org.skepsun.kototoro.core.util.ext.consume
import org.skepsun.kototoro.core.util.ext.copyToClipboard
import org.skepsun.kototoro.core.util.ext.findParentCallback
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.core.util.ext.setValueRounded
import org.skepsun.kototoro.core.util.ext.viewLifecycleScope
import org.skepsun.kototoro.core.util.progress.IntPercentLabelFormatter
import org.skepsun.kototoro.databinding.SheetReaderConfigBinding
import org.skepsun.kototoro.reader.domain.PageLoader
import org.skepsun.kototoro.reader.domain.PageLoader.TranslationLayerState
import org.skepsun.kototoro.reader.ui.ReaderViewModel
import org.skepsun.kototoro.reader.ui.ScreenOrientationHelper
import javax.inject.Inject

@AndroidEntryPoint
class ReaderConfigSheet :
    BaseAdaptiveSheet<SheetReaderConfigBinding>(),
    View.OnClickListener,
    MaterialButtonToggleGroup.OnButtonCheckedListener,
    CompoundButton.OnCheckedChangeListener,
    Slider.OnChangeListener {

    private val viewModel by activityViewModels<ReaderViewModel>()

    @Inject
    lateinit var orientationHelper: ScreenOrientationHelper

    @Inject
    lateinit var mangaRepositoryFactory: MangaRepository.Factory

    @Inject
    lateinit var pageLoader: PageLoader

    private lateinit var mode: ReaderMode
    private lateinit var imageServerDelegate: ImageServerDelegate
    private var taskFilter: TranslationTaskFilter = TranslationTaskFilter.ALL

    @Inject
    lateinit var settings: AppSettings

    /**
     * 在折叠屏展开竖屏或宽屏竖向场景下，将 Reader 设置弹窗改为右侧侧栏 SideSheet。
     * 其他场景仍保持默认的底部弹窗样式。
     */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val conf = context.resources.configuration
        val isPortrait = conf.orientation == Configuration.ORIENTATION_PORTRAIT
        // 以 600dp 作为宽屏阈值，覆盖折叠竖屏和大屏竖向场景
        val isWidePortrait = conf.screenWidthDp >= 600

        return if (isPortrait && isWidePortrait) {
            object : SideSheetDialog(context, theme) {
                override fun onSupportActionModeStarted(mode: ActionMode?) {
                    super.onSupportActionModeStarted(mode)
                    if (mode != null) dispatchSupportActionModeStarted(mode)
                }

                override fun onSupportActionModeFinished(mode: ActionMode?) {
                    super.onSupportActionModeFinished(mode)
                    if (mode != null) dispatchSupportActionModeFinished(mode)
                }
            }
        } else {
            // 其他场景维持 BaseAdaptiveSheet 默认逻辑（平板为侧栏，手机为底部弹窗）
            super.onCreateDialog(savedInstanceState)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = arguments?.getInt(AppRouter.KEY_READER_MODE)
            ?.let { ReaderMode.valueOf(it) }
            ?: ReaderMode.STANDARD
        imageServerDelegate = ImageServerDelegate(
            mangaRepositoryFactory = mangaRepositoryFactory,
            mangaSource = viewModel.getMangaOrNull()?.source,
        )
    }

    override fun onCreateViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): SheetReaderConfigBinding {
        return SheetReaderConfigBinding.inflate(inflater, container, false)
    }

    override fun onViewBindingCreated(
        binding: SheetReaderConfigBinding,
        savedInstanceState: Bundle?,
    ) {
        super.onViewBindingCreated(binding, savedInstanceState)
        observeScreenOrientation()
        binding.buttonStandard.isChecked = mode == ReaderMode.STANDARD
        binding.buttonReversed.isChecked = mode == ReaderMode.REVERSED
        binding.buttonWebtoon.isChecked = mode == ReaderMode.WEBTOON
        binding.buttonVertical.isChecked = mode == ReaderMode.VERTICAL
        binding.switchDoubleReader.isChecked = settings.isReaderDoubleOnLandscape
        binding.switchDoubleReader.isEnabled = mode == ReaderMode.STANDARD || mode == ReaderMode.REVERSED
        binding.switchDoubleFoldable.isChecked = settings.isReaderDoubleOnFoldable
        binding.switchDoubleFoldable.isEnabled = binding.switchDoubleReader.isEnabled
        binding.switchTranslationEnabled.isChecked = settings.isReaderTranslationEnabled
        binding.switchTranslationShowTranslated.isChecked = settings.isReaderTranslationShowTranslated
        binding.switchTranslationShowTranslated.isEnabled = settings.isReaderTranslationEnabled
        binding.buttonRetranslate.isEnabled = settings.isReaderTranslationEnabled
        binding.buttonTranslationLog.isEnabled = settings.isReaderTranslationEnabled
        updateTranslationBypassHint(binding)
        binding.sliderDoubleSensitivity.setValueRounded(settings.readerDoublePagesSensitivity * 100f)
        binding.sliderDoubleSensitivity.setLabelFormatter(IntPercentLabelFormatter(binding.root.context))
        binding.adjustSensitivitySlider(withAnimation = false)

        binding.checkableGroup.addOnButtonCheckedListener(this)
        binding.buttonSavePage.setOnClickListener(this)
        binding.buttonScreenRotate.setOnClickListener(this)
        binding.buttonSettings.setOnClickListener(this)
        binding.buttonImageServer.setOnClickListener(this)
        binding.buttonColorFilter.setOnClickListener(this)
        binding.buttonScrollTimer.setOnClickListener(this)
        binding.buttonBookmark.setOnClickListener(this)
        binding.switchDoubleReader.setOnCheckedChangeListener(this)
        binding.switchDoubleFoldable.setOnCheckedChangeListener(this)
        binding.switchTranslationEnabled.setOnCheckedChangeListener(this)
        binding.switchTranslationShowTranslated.setOnCheckedChangeListener(this)
        binding.buttonRetranslate.setOnClickListener(this)
        binding.buttonTranslationLog.setOnClickListener(this)
        binding.sliderDoubleSensitivity.addOnChangeListener(this)
        binding.buttonOpenInBrowser.setOnClickListener(this)

        viewModel.isBookmarkAdded.observe(viewLifecycleOwner) {
            binding.buttonBookmark.setText(if (it) R.string.bookmark_remove else R.string.bookmark_add)
            binding.buttonBookmark.setCompoundDrawablesRelativeWithIntrinsicBounds(
                if (it) R.drawable.ic_bookmark_checked else R.drawable.ic_bookmark, 0, 0, 0,
            )
        }

        viewLifecycleScope.launch {
            val isAvailable = imageServerDelegate.isAvailable()
            if (isAvailable) {
                bindImageServerTitle()
            }
            binding.buttonImageServer.isVisible = isAvailable
        }
    }

    override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
        val typeMask = WindowInsetsCompat.Type.systemBars()
        viewBinding?.scrollView?.updatePadding(
            bottom = insets.getInsets(typeMask).bottom,
        )
        return insets.consume(v, typeMask, bottom = true)
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.button_settings -> {
                router.openReaderSettings()
                dismissAllowingStateLoss()
            }

            R.id.button_scroll_timer -> {
                findParentCallback(Callback::class.java)?.onScrollTimerClick(false) ?: return
                dismissAllowingStateLoss()
            }

            R.id.button_save_page -> {
                findParentCallback(Callback::class.java)?.onSavePageClick() ?: return
                dismissAllowingStateLoss()
            }

            R.id.button_screen_rotate -> {
                orientationHelper.isLandscape = !orientationHelper.isLandscape
            }

            R.id.button_bookmark -> {
                viewModel.toggleBookmark()
            }

            R.id.button_color_filter -> {
                val page = viewModel.getCurrentPage() ?: return
                val manga = viewModel.getMangaOrNull() ?: return
                router.openColorFilterConfig(manga, page)
            }

            R.id.button_open_in_browser -> {
                val manga = viewModel.getMangaOrNull() ?: return
                val chapter = viewModel.uiState.value?.chapter
                if (chapter != null) {
                    val url = kotlin.runCatching {
                        if (chapter.url.startsWith("http", ignoreCase = true)) {
                            chapter.url
                        } else if (manga.publicUrl.startsWith("http", ignoreCase = true)) {
                            // Resolve relative chapter path against manga's absolute public URL
                            java.net.URL(java.net.URL(manga.publicUrl), chapter.url).toString()
                        } else {
                            // publicUrl is not a valid HTTP URL, fall back to manga.publicUrl itself
                            null
                        }
                    }.getOrNull()
                    val resolvedUrl = url?.takeIf { it.startsWith("http", ignoreCase = true) }
                        ?: manga.publicUrl.takeIf { it.startsWith("http", ignoreCase = true) }
                    if (resolvedUrl != null) {
                        router.openBrowser(resolvedUrl, manga.source, chapter.title)
                    } else {
                        router.openBrowser(manga)
                    }
                } else {
                    router.openBrowser(manga)
                }
                dismissAllowingStateLoss()
            }

            R.id.button_retranslate -> {
                if (settings.isReaderTranslationEnabled) {
                    showRetranslateActionDialog()
                }
            }

            R.id.button_translation_log -> {
                showTranslationTaskPanel()
            }

            R.id.button_image_server -> viewLifecycleScope.launch {
                if (imageServerDelegate.showDialog(v.context)) {
                    bindImageServerTitle()
                    pageLoader.invalidate(clearCache = true)
                    viewModel.switchChapterBy(0)
                }
            }
        }
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        when (buttonView.id) {
            R.id.switch_screen_lock_rotation -> {
                orientationHelper.isLocked = isChecked
            }

            R.id.switch_double_reader -> {
                settings.isReaderDoubleOnLandscape = isChecked
                viewBinding?.adjustSensitivitySlider(withAnimation = true)
                findParentCallback(Callback::class.java)?.onDoubleModeChanged(isChecked)
            }

            R.id.switch_double_foldable -> {
                settings.isReaderDoubleOnFoldable = isChecked
                // Re-evaluate double-page considering foldable state and current manual toggle
                findParentCallback(Callback::class.java)?.onDoubleModeChanged(settings.isReaderDoubleOnLandscape)
            }

            R.id.switch_translation_enabled -> {
                settings.isReaderTranslationEnabled = isChecked
                viewBinding?.switchTranslationShowTranslated?.isEnabled = isChecked
                viewBinding?.buttonRetranslate?.isEnabled = isChecked
                viewBinding?.buttonTranslationLog?.isEnabled = isChecked
                viewBinding?.let { updateTranslationBypassHint(it) }
            }

            R.id.switch_translation_show_translated -> {
                settings.isReaderTranslationShowTranslated = isChecked
            }
        }
    }

    override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
        settings.readerDoublePagesSensitivity = value / 100f
    }

    override fun onButtonChecked(
        group: MaterialButtonToggleGroup?,
        checkedId: Int,
        isChecked: Boolean,
    ) {
        if (!isChecked) {
            return
        }
        val newMode = when (checkedId) {
            R.id.button_standard -> ReaderMode.STANDARD
            R.id.button_webtoon -> ReaderMode.WEBTOON
            R.id.button_reversed -> ReaderMode.REVERSED
            R.id.button_vertical -> ReaderMode.VERTICAL
            else -> return
        }
        viewBinding?.run {
            switchDoubleReader.isEnabled = newMode == ReaderMode.STANDARD || newMode == ReaderMode.REVERSED
            switchDoubleFoldable.isEnabled = switchDoubleReader.isEnabled
            adjustSensitivitySlider(withAnimation = true)
        }
        if (newMode == mode) {
            return
        }
        findParentCallback(Callback::class.java)?.onReaderModeChanged(newMode) ?: return
        mode = newMode
    }

    private fun observeScreenOrientation() {
        orientationHelper.observeAutoOrientation()
            .onEach {
                with(requireViewBinding()) {
                    buttonScreenRotate.isGone = it
                    switchScreenLockRotation.isVisible = it
                    updateOrientationLockSwitch()
                }
            }.launchIn(viewLifecycleScope)
    }

    private fun updateOrientationLockSwitch() {
        val switch = viewBinding?.switchScreenLockRotation ?: return
        switch.setOnCheckedChangeListener(null)
        switch.isChecked = orientationHelper.isLocked
        switch.setOnCheckedChangeListener(this)
    }

    private fun updateTranslationBypassHint(binding: SheetReaderConfigBinding) {
        val hint = viewModel.getTranslationBypassHint(requireContext())
        val visible = settings.isReaderTranslationEnabled && !hint.isNullOrBlank()
        binding.textTranslationBypassHint.isVisible = visible
        if (visible) {
            binding.textTranslationBypassHint.text = hint
        }
    }

    private fun showRetranslateActionDialog() {
        val options = arrayOf(
            getString(R.string.reader_translation_retranslate_current_page),
            getString(R.string.reader_translation_retry_failed_pages),
            getString(R.string.reader_translation_retranslate_current_chapter),
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.reader_translation_retranslate)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> viewModel.retranslateCurrent()
                    1 -> viewModel.retranslateFailedInCurrentChapter()
                    2 -> viewModel.retranslateCurrentChapter()
                }
                dismissAllowingStateLoss()
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun showTranslationTaskPanel() {
        val snapshots = viewModel.getCurrentChapterTranslationTaskSnapshots()
        if (snapshots.isEmpty()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.reader_translation_task_panel_title)
                .setMessage(R.string.reader_translation_task_panel_empty)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        val filtered = snapshots.filter { taskFilter.matches(it) }
        if (filtered.isEmpty()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.reader_translation_task_panel_title)
                .setMessage(
                    getString(
                        R.string.reader_translation_task_panel_empty_for_filter,
                        taskFilter.label(requireContext()),
                    ),
                )
                .setPositiveButton(R.string.reader_translation_task_filter) { _, _ ->
                    showTaskFilterDialog()
                }
                .setNegativeButton(R.string.close, null)
                .show()
            return
        }
        val failed = filtered.count { it.state == TranslationLayerState.FAILED }
        val generating = filtered.count { it.state == TranslationLayerState.GENERATING }
        val ready = filtered.count { it.state == TranslationLayerState.READY }
        val summary = getString(
            R.string.reader_translation_task_panel_summary,
            filtered.size,
            ready,
            generating,
            failed,
        )
        val items = filtered.map { item ->
            val timeText = item.updatedAtMs?.let { updated ->
                DateUtils.getRelativeTimeSpanString(
                    updated,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE,
                ).toString()
            } ?: getString(R.string.reader_translation_task_time_unknown)
            val preview = item.log.lineSequence().lastOrNull().orEmpty().ifBlank {
                getString(R.string.reader_translation_page_log_empty)
            }
            getString(
                R.string.reader_translation_task_item,
                item.pageIndex + 1,
                translationStateLabel(item.state),
                timeText,
                preview,
            )
        }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.reader_translation_task_panel_title)
            .setMessage("$summary\n${getString(R.string.reader_translation_task_filter_current, taskFilter.label(requireContext()))}")
            .setItems(items) { _, which ->
                showTranslationPageDetail(filtered[which])
            }
            .setPositiveButton(R.string.reader_translation_retry_failed_pages) { _, _ ->
                viewModel.retranslateFailedInCurrentChapter()
            }
            .setNeutralButton(R.string.reader_translation_task_filter) { _, _ ->
                showTaskFilterDialog()
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun showTaskFilterDialog() {
        val filters = TranslationTaskFilter.entries
        val labels = filters.map { it.label(requireContext()) }.toTypedArray()
        val selected = filters.indexOf(taskFilter).coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.reader_translation_task_filter_title)
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                taskFilter = filters[which]
                dialog.dismiss()
                showTranslationTaskPanel()
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun showTranslationPageDetail(item: ReaderViewModel.TranslationPageTaskSnapshot) {
        val title = getString(
            R.string.reader_translation_task_detail_title,
            item.pageIndex + 1,
            translationStateLabel(item.state),
        )
        val rawLog = item.log.ifBlank {
            getString(R.string.reader_translation_page_log_empty)
        }
        val report = buildTranslationDetailReport(item.log)
        val message = if (report.isBlank()) rawLog else "$report\n\n----------------\n$rawLog"
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.reader_translation_retry_this_page) { _, _ ->
                viewModel.retryTranslationForPage(item.pageId)
            }
            .setNeutralButton(androidx.preference.R.string.copy) { _, _ ->
                requireContext().copyToClipboard(title, message)
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun buildTranslationDetailReport(log: String): String {
        if (log.isBlank()) return ""
        var sourceLang = "?"
        var targetLang = "?"
        var configuredOcr = "?"
        val ocrAttempts = linkedMapOf<String, Int>()
        var localRequested = -1
        var localDoneTranslated = -1
        var localDoneTotal = -1
        var renderedBubbles = -1
        var failedReason: String? = null
        var failCode: String? = null
        val timeline = ArrayList<String>(8)
        val pairs = ArrayList<Pair<String, String>>(10)

        log.lineSequence().forEach { line ->
            if (line.contains("process start ")) {
                Regex("""sourceLang=([^\s]+)""").find(line)?.groupValues?.getOrNull(1)?.let { sourceLang = it }
                Regex("""targetLang=([^\s]+)""").find(line)?.groupValues?.getOrNull(1)?.let { targetLang = it }
                Regex("""ocr=([^\s]+)""").find(line)?.groupValues?.getOrNull(1)?.let { configuredOcr = it }
                timeline.add("开始处理")
            }
            if (line.contains("process failed:")) {
                failedReason = line.substringAfter("process failed:", "").trim()
                timeline.add("处理失败")
            }
            Regex("""fail_code=([A-Z_]+)""").find(line)?.groupValues?.getOrNull(1)?.let {
                failCode = it
            }
            Regex("""ocr engine=([A-Z_]+) blocks=(\d+)""").find(line)?.let { m ->
                val engine = m.groupValues[1]
                val blocks = m.groupValues[2].toIntOrNull() ?: 0
                ocrAttempts[engine] = blocks
                timeline.add("OCR[$engine]=$blocks")
            }
            Regex("""translate local requested size=(\d+)""").find(line)?.let { m ->
                localRequested = m.groupValues[1].toIntOrNull() ?: -1
                timeline.add("本地翻译请求=$localRequested")
            }
            Regex("""translate local batch done translated=(\d+)/(\d+)""").find(line)?.let { m ->
                localDoneTranslated = m.groupValues[1].toIntOrNull() ?: -1
                localDoneTotal = m.groupValues[2].toIntOrNull() ?: -1
                timeline.add("本地翻译完成=$localDoneTranslated/$localDoneTotal")
            }
            Regex("""render done translatedBubbles=(\d+)""").find(line)?.let { m ->
                renderedBubbles = m.groupValues[1].toIntOrNull() ?: -1
                timeline.add("渲染完成=$renderedBubbles")
            }
            Regex("""bubble translate src=(.*?) out=(.*?) box=""").find(line)?.let { m ->
                if (pairs.size < 8) {
                    pairs.add(m.groupValues[1].trim() to m.groupValues[2].trim())
                }
            }
        }

        return buildString {
            appendLine("【翻译诊断】")
            appendLine("语言: $sourceLang -> $targetLang")
            appendLine("配置 OCR: $configuredOcr")
            if (ocrAttempts.isNotEmpty()) {
                appendLine("OCR 尝试: ${ocrAttempts.entries.joinToString { "${it.key}:${it.value}" }}")
            }
            if (localRequested >= 0) {
                appendLine("本地翻译: 请求 $localRequested, 完成 $localDoneTranslated/$localDoneTotal")
            }
            if (renderedBubbles >= 0) {
                appendLine("渲染气泡: $renderedBubbles")
            }
            failCode?.let {
                appendLine("失败代码: $it")
            }
            failedReason?.let {
                appendLine("失败原因: $it")
            }
            if (timeline.isNotEmpty()) {
                appendLine("阶段时间线: ${timeline.joinToString(" -> ")}")
            }
            if (pairs.isNotEmpty()) {
                appendLine()
                appendLine("示例识别/翻译:")
                pairs.forEachIndexed { idx, (src, out) ->
                    appendLine("${idx + 1}. 原: ${src.ifBlank { "<空>" }}")
                    appendLine("   译: ${out.ifBlank { "<空>" }}")
                }
            }
        }.trim()
    }

    private fun translationStateLabel(state: TranslationLayerState): String {
        return when (state) {
            TranslationLayerState.IDLE -> getString(R.string.reader_translation_task_state_idle)
            TranslationLayerState.GENERATING -> getString(R.string.reader_translation_task_state_generating)
            TranslationLayerState.READY -> getString(R.string.reader_translation_task_state_ready)
            TranslationLayerState.FAILED -> getString(R.string.reader_translation_task_state_failed)
        }
    }

    private enum class TranslationTaskFilter {
        ALL,
        FAILED,
        OCR_EMPTY,
        TRANSLATE_EMPTY,
        RENDER_FILTERED,
        PROCESS_EXCEPTION;

        fun matches(item: ReaderViewModel.TranslationPageTaskSnapshot): Boolean {
            return when (this) {
                ALL -> true
                FAILED -> item.state == TranslationLayerState.FAILED
                OCR_EMPTY -> item.failCode == "OCR_EMPTY"
                TRANSLATE_EMPTY -> item.failCode == "TRANSLATE_EMPTY"
                RENDER_FILTERED -> item.failCode == "RENDER_FILTERED"
                PROCESS_EXCEPTION -> item.failCode == "PROCESS_EXCEPTION"
            }
        }

        fun label(context: android.content.Context): String {
            return when (this) {
                ALL -> context.getString(R.string.reader_translation_task_filter_all)
                FAILED -> context.getString(R.string.reader_translation_task_filter_failed)
                OCR_EMPTY -> context.getString(R.string.reader_translation_task_filter_ocr_empty)
                TRANSLATE_EMPTY -> context.getString(R.string.reader_translation_task_filter_translate_empty)
                RENDER_FILTERED -> context.getString(R.string.reader_translation_task_filter_render_filtered)
                PROCESS_EXCEPTION -> context.getString(R.string.reader_translation_task_filter_exception)
            }
        }
    }

    private suspend fun bindImageServerTitle() {
        viewBinding?.buttonImageServer?.text = getString(
            R.string.inline_preference_pattern,
            getString(R.string.image_server),
            imageServerDelegate.getValue() ?: getString(R.string.automatic),
        )
    }

    private fun SheetReaderConfigBinding.adjustSensitivitySlider(withAnimation: Boolean) {
        val isSubOptionsVisible = switchDoubleReader.isEnabled && switchDoubleReader.isChecked
        val needTransition = withAnimation && (
            (isSubOptionsVisible != sliderDoubleSensitivity.isVisible) ||
                (isSubOptionsVisible != textDoubleSensitivity.isVisible) ||
                (isSubOptionsVisible != switchDoubleFoldable.isVisible)
            )
        if (needTransition) {
            TransitionManager.beginDelayedTransition(layoutMain)
        }
        sliderDoubleSensitivity.isVisible = isSubOptionsVisible
        textDoubleSensitivity.isVisible = isSubOptionsVisible
        switchDoubleFoldable.isVisible = isSubOptionsVisible
    }

    interface Callback {

        fun onReaderModeChanged(mode: ReaderMode)

        fun onDoubleModeChanged(isEnabled: Boolean)

        fun onSavePageClick()

        fun onScrollTimerClick(isLongClick: Boolean)

        fun onBookmarkClick()
    }
}
