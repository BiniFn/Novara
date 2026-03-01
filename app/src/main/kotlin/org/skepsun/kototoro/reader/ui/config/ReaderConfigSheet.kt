package org.skepsun.kototoro.reader.ui.config

import android.os.Bundle
import android.app.Dialog
import android.content.res.Configuration
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
import org.skepsun.kototoro.core.util.ext.findParentCallback
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.core.util.ext.setValueRounded
import org.skepsun.kototoro.core.util.ext.viewLifecycleScope
import org.skepsun.kototoro.core.util.progress.IntPercentLabelFormatter
import org.skepsun.kototoro.databinding.SheetReaderConfigBinding
import org.skepsun.kototoro.reader.domain.PageLoader
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
