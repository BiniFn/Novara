package org.skepsun.kototoro.reader.novel

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.util.Base64
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.RadioGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.Gravity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.materialswitch.MaterialSwitch
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.parcelable.ParcelableManga
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.parser.MangaRepository
import org.skepsun.kototoro.core.ui.BaseFullscreenActivity
import org.skepsun.kototoro.core.util.ext.getParcelableExtraCompat
import org.skepsun.kototoro.core.util.ext.resolveDp
import org.skepsun.kototoro.core.util.ext.resolveSp
import org.skepsun.kototoro.databinding.ActivityNovelReaderBinding
import org.skepsun.kototoro.parsers.model.Manga
import org.skepsun.kototoro.parsers.model.MangaChapter
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class NovelReaderActivity : BaseFullscreenActivity<ActivityNovelReaderBinding>() {

    @Inject
    lateinit var mangaRepositoryFactory: MangaRepository.Factory

    private lateinit var manga: Manga
    private lateinit var repository: MangaRepository

    private var chapters: List<MangaChapter> = emptyList()
    private var currentIndex: Int = 0
    private var lastHtml: String? = null
    private var contentHeightPx: Int = 0
    private var isUiVisible: Boolean = false
    private var isUserSeeking: Boolean = false
    private lateinit var readerSettings: NovelReaderSettings

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ActivityNovelReaderBinding.inflate(layoutInflater))
        readerSettings = NovelReaderSettings.load(this)
        val parcelable = intent.getParcelableExtraCompat<ParcelableManga>(AppRouter.KEY_MANGA)
        val mangaSeed = parcelable?.manga
        if (mangaSeed == null) {
            finish()
            return
        }
        manga = mangaSeed
        repository = mangaRepositoryFactory.create(manga.source)
        setDisplayHomeAsUp(isEnabled = true, showUpAsClose = false)
        viewBinding.toolbar.title = manga.title
        viewBinding.toolbar.subtitle = getString(R.string.loading_)
        viewBinding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        initWebView()
        bindControls()
        loadChapters()
    }

    override fun onDestroy() {
        viewBinding.webView.destroy()
        super.onDestroy()
    }

    override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        viewBinding.webView.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
        ViewCompat.setPaddingRelative(viewBinding.toolbar, bars.left, bars.top, bars.right, 0)
        val dockedParams = viewBinding.toolbarDocked.layoutParams as CoordinatorLayout.LayoutParams
        if (shouldDockTop()) {
            dockedParams.gravity = Gravity.TOP
            ViewCompat.setPaddingRelative(viewBinding.toolbarDocked, bars.left, bars.top, bars.right, 0)
        } else {
            dockedParams.gravity = Gravity.BOTTOM
            ViewCompat.setPaddingRelative(viewBinding.toolbarDocked, bars.left, 0, bars.right, bars.bottom)
        }
        viewBinding.toolbarDocked.layoutParams = dockedParams
        return WindowInsetsCompat.Builder(insets)
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.NONE)
            .build()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initWebView() = with(viewBinding.webView.settings) {
        javaScriptEnabled = false
        builtInZoomControls = true
        displayZoomControls = false
        loadsImagesAutomatically = false
        cacheMode = WebSettings.LOAD_NO_CACHE
        viewBinding.webView.isHorizontalScrollBarEnabled = false
        viewBinding.webView.isScrollbarFadingEnabled = true

        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            val isDark =
                (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            WebSettingsCompat.setForceDark(
                this,
                if (isDark) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF,
            )
        }

        val gestureDetector = GestureDetector(this@NovelReaderActivity, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                toggleUiVisibility()
                return true
            }
        })

        viewBinding.webView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }

        viewBinding.webView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            updateProgressFromScroll(scrollY)
            saveScrollPosition()
        }

        viewBinding.webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectCss()
                view?.post {
                    updateContentHeightFromJs()
                    restoreScrollPosition()
                }
            }
        }
    }

    private fun injectCss() {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val css = readerSettings.buildCss(resources, isDark, shouldUseColumns = shouldUseColumns())
        val js = """
            (function() {
              var existing = document.getElementById('novel-style');
              if (existing) { existing.remove(); }
              var style = document.createElement('style');
              style.id = 'novel-style';
              style.innerHTML = `$css`;
              document.head.appendChild(style);
            })();
        """.trimIndent()
        viewBinding.webView.evaluateJavascript(js, null)
        updateContentHeightFromJs()
    }

    private fun bindControls() = with(viewBinding) {
        appbarTop.isVisible = false
        toolbarDocked.isVisible = false
        bottomControls.isVisible = true
        progressSlider.valueFrom = 0f
        progressSlider.valueTo = 100f
        progressSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(slider: Slider) {
                isUserSeeking = false
                scrollToProgress(slider.value)
            }
        })
        progressSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                scrollToProgress(value)
            }
        }
        buttonPrev.setOnClickListener { switchChapter(-1) }
        buttonNext.setOnClickListener { switchChapter(1) }
        buttonChapters.setOnClickListener { showChapterPicker() }
        buttonSettings.setOnClickListener { showSettingsSheet() }
    }

    private fun loadChapters() {
        lifecycleScope.launch {
            showLoading(true)
            runCatching {
                val details = repository.getDetails(manga)
                details.chapters.orEmpty()
            }.onSuccess { list ->
                chapters = list
                currentIndex = 0
                if (chapters.isEmpty()) {
                    showLoading(false)
                    viewBinding.webView.loadDataWithBaseURL(
                        null,
                        getString(R.string.no_chapters_in_manga),
                        "text/html",
                        "utf-8",
                        null,
                    )
                    updateProgressText(0f)
                } else {
                    setBarsVisible(true)
                    loadChapter(currentIndex)
                }
            }.onFailure {
                showLoading(false)
                Snackbar.make(viewBinding.root, R.string.chapters_load_failed, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun switchChapter(delta: Int) {
        val target = currentIndex + delta
        if (target in chapters.indices) {
            currentIndex = target
            loadChapter(currentIndex)
        }
    }

    private fun loadChapter(index: Int) {
        val chapter = chapters.getOrNull(index) ?: return
        viewBinding.toolbar.subtitle = getString(R.string.loading_)
        lifecycleScope.launch {
            showLoading(true)
            runCatching {
                val pages = repository.getPages(chapter)
                val html = pages.firstOrNull()?.url?.let(::decodeChapterHtml)
                    ?: getString(R.string.chapter_is_missing)
                Pair(chapter, html)
            }.onSuccess { (chap, html) ->
                showLoading(false)
                renderChapter(chap, html)
            }.onFailure {
                showLoading(false)
                Snackbar.make(viewBinding.root, R.string.chapter_is_missing, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun renderChapter(chapter: MangaChapter, html: String) {
        lastHtml = html
        viewBinding.toolbar.subtitle = chapter.title ?: getString(R.string.unnamed_chapter)
        viewBinding.buttonPrev.isEnabled = currentIndex > 0
        viewBinding.buttonNext.isEnabled = currentIndex < chapters.lastIndex
        viewBinding.progressSlider.isEnabled = true
        viewBinding.progressSlider.value = 0f
        lastScrollY = 0
        contentHeightPx = 0
        viewBinding.webView.loadDataWithBaseURL(chapter.url, html, "text/html", "utf-8", null)
        updateProgressText(0f)
    }

    private fun showLoading(loading: Boolean) {
        viewBinding.layoutLoading.isVisible = loading
        viewBinding.buttonPrev.isEnabled = !loading && currentIndex > 0
        viewBinding.buttonNext.isEnabled = !loading && currentIndex < chapters.lastIndex
        viewBinding.progressSlider.isEnabled = !loading && chapters.isNotEmpty()
        viewBinding.textProgress.isGone = loading
    }

    private fun decodeChapterHtml(url: String): String {
        if (url.startsWith("data:", ignoreCase = true)) {
            val commaIndex = url.indexOf(',')
            if (commaIndex != -1) {
                val meta = url.substring(5, commaIndex)
                val data = url.substring(commaIndex + 1)
                return if (meta.contains("base64", ignoreCase = true)) {
                    val decoded = Base64.decode(data, Base64.DEFAULT)
                    String(decoded, Charsets.UTF_8)
                } else {
                    data
                }
            }
        }
        return "<html><body>${getString(R.string.chapter_is_missing)}</body></html>"
    }

    private fun toggleUiVisibility() {
        setBarsVisible(!isUiVisible)
    }

    private fun setBarsVisible(visible: Boolean) {
        isUiVisible = visible
        viewBinding.appbarTop.isVisible = visible
        viewBinding.toolbarDocked.isVisible = visible
    }

    private var lastScrollY: Int = 0
    private fun saveScrollPosition() {
        lastScrollY = viewBinding.webView.scrollY
    }

    private fun restoreScrollPosition() {
        viewBinding.webView.post { viewBinding.webView.scrollTo(0, lastScrollY) }
    }

    private fun updateProgressFromScroll(scrollY: Int) {
        val maxScroll = (contentHeightPx - viewBinding.webView.height).coerceAtLeast(1)
        if (maxScroll <= 0) return
        val percent = (scrollY.toFloat() * 100f / maxScroll).coerceIn(0f, 100f)
        if (!isUserSeeking) {
            viewBinding.progressSlider.value = percent
        }
        updateProgressText(percent)
    }

    private fun scrollToProgress(value: Float) {
        val maxScroll = (contentHeightPx - viewBinding.webView.height).coerceAtLeast(1)
        val target = (maxScroll * (value / 100f)).roundToInt()
        viewBinding.webView.evaluateJavascript("window.scrollTo(0, $target);", null)
    }

    private fun updateProgressText(percent: Float) {
        val chapterName = chapters.getOrNull(currentIndex)?.title.orEmpty()
        val base = getString(
            R.string.reader_progress_template,
            currentIndex + 1,
            chapters.size.coerceAtLeast(1),
            percent.roundToInt(),
        )
        viewBinding.textProgress.text = if (chapterName.isNotEmpty()) {
            "$base · $chapterName"
        } else {
            base
        }
    }

    private fun refreshContentMetrics() {
        viewBinding.webView.post {
            updateContentHeightFromJs()
        }
    }

    private fun updateContentHeightFromJs() {
        val js = """
            (function() {
              const d = document.documentElement;
              const b = document.body;
              const h = Math.max(d.scrollHeight||0, b.scrollHeight||0);
              return h;
            })();
        """.trimIndent()
        viewBinding.webView.evaluateJavascript(js) { result ->
            val raw = result?.trim()?.trim('"') ?: return@evaluateJavascript
            val height = raw.toFloatOrNull() ?: return@evaluateJavascript
            val density = resources.displayMetrics.density
            contentHeightPx = (height * viewBinding.webView.scale * density).roundToInt().coerceAtLeast(viewBinding.webView.height)
            updateProgressFromScroll(viewBinding.webView.scrollY)
        }
    }

    private fun showChapterPicker() {
        if (chapters.isEmpty()) return
        val titles = chapters.mapIndexed { idx, ch ->
            "${idx + 1}. ${ch.title ?: getString(R.string.unnamed_chapter)}"
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.chapters)
            .setSingleChoiceItems(titles.toTypedArray(), currentIndex) { dialog, which ->
                currentIndex = which
                loadChapter(which)
                dialog.dismiss()
            }
            .show()
    }

    private fun showSettingsSheet() {
        val dialog = BottomSheetDialog(this)
        val content = LayoutInflater.from(this).inflate(R.layout.sheet_novel_settings, null)
        val fontSlider = content.findViewById<Slider>(R.id.sliderFontSize)
        val lineSlider = content.findViewById<Slider>(R.id.sliderLineHeight)
        val marginSlider = content.findViewById<Slider>(R.id.sliderMargin)
        val modeGroup = content.findViewById<RadioGroup>(R.id.radioModeGroup)
        val dualPageSwitch = content.findViewById<MaterialSwitch>(R.id.switchDualPage)

        fontSlider.value = readerSettings.fontSizeSp
        lineSlider.value = readerSettings.lineHeight
        marginSlider.value = readerSettings.marginDp
        dualPageSwitch.isChecked = readerSettings.enableDualPage
        when (readerSettings.readingMode) {
            NovelReadingMode.DEFAULT -> modeGroup.check(R.id.radioModeDefault)
            NovelReadingMode.RTL -> modeGroup.check(R.id.radioModeRtl)
            NovelReadingMode.VERTICAL_PAGED -> modeGroup.check(R.id.radioModeVertical)
            NovelReadingMode.VERTICAL_CONTINUOUS -> modeGroup.check(R.id.radioModeVerticalContinuous)
        }

        val onSettingsChanged = {
            val selectedMode = when (modeGroup.checkedRadioButtonId) {
                R.id.radioModeRtl -> NovelReadingMode.RTL
                R.id.radioModeVertical -> NovelReadingMode.VERTICAL_PAGED
                R.id.radioModeVerticalContinuous -> NovelReadingMode.VERTICAL_CONTINUOUS
                else -> NovelReadingMode.DEFAULT
            }
            readerSettings = readerSettings.copy(
                fontSizeSp = fontSlider.value,
                lineHeight = lineSlider.value,
                marginDp = marginSlider.value,
                readingMode = selectedMode,
                enableDualPage = dualPageSwitch.isChecked,
            )
            readerSettings.save(this)
            injectCss()
            refreshContentMetrics()
        }

        fontSlider.addOnChangeListener { _, _, fromUser -> if (fromUser) onSettingsChanged() }
        lineSlider.addOnChangeListener { _, _, fromUser -> if (fromUser) onSettingsChanged() }
        marginSlider.addOnChangeListener { _, _, fromUser -> if (fromUser) onSettingsChanged() }
        dualPageSwitch.setOnCheckedChangeListener { _, _ -> onSettingsChanged() }
        modeGroup.setOnCheckedChangeListener { _, _ -> onSettingsChanged() }

        dialog.setContentView(content)
        dialog.show()
    }

    private fun shouldDockTop(): Boolean {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val isWide = resources.configuration.smallestScreenWidthDp >= 600
        return readerSettings.enableDualPage && (isLandscape || isWide)
    }

    private fun shouldUseColumns(): Boolean = shouldDockTop()
}

private enum class NovelReadingMode {
    DEFAULT,
    RTL,
    VERTICAL_PAGED,
    VERTICAL_CONTINUOUS,
    ;

    fun isVertical() = this == VERTICAL_PAGED || this == VERTICAL_CONTINUOUS
    fun isRtl() = this == RTL
}

private data class NovelReaderSettings(
    val fontSizeSp: Float = 16f,
    val lineHeight: Float = 1.6f,
    val marginDp: Float = 16f,
    val readingMode: NovelReadingMode = NovelReadingMode.DEFAULT,
    val enableDualPage: Boolean = true,
) {

    fun save(context: NovelReaderActivity) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat(KEY_FONT_SIZE, fontSizeSp)
            .putFloat(KEY_LINE_HEIGHT, lineHeight)
            .putFloat(KEY_MARGIN, marginDp)
            .putString(KEY_MODE, readingMode.name)
            .putBoolean(KEY_DUAL_PAGE, enableDualPage)
            .apply()
    }

    fun buildCss(resources: android.content.res.Resources, isDark: Boolean, shouldUseColumns: Boolean): String {
        val bgColor = if (isDark) "#121212" else "#f8f5f1"
        val textColor = if (isDark) "#e0e0e0" else "#222222"
        val fontPx = resources.resolveSp(fontSizeSp).roundToInt()
        val marginPx = resources.resolveDp(marginDp).roundToInt()
        val direction = if (readingMode.isRtl()) "rtl" else "ltr"
        val writingMode = if (readingMode.isVertical()) "writing-mode: vertical-rl;" else ""
        val columns = if (shouldUseColumns) {
            val gap = resources.resolveDp(12f).roundToInt()
            val columnWidth = (resources.displayMetrics.widthPixels / 2f).roundToInt()
            "column-width: ${columnWidth}px; column-gap: ${gap}px;"
        } else {
            ""
        }
        return """
            body {
              background-color: $bgColor !important;
              color: $textColor !important;
              font-size: ${fontPx}px !important;
              line-height: ${lineHeight} !important;
              padding: ${marginPx}px !important;
              direction: $direction !important;
              $writingMode
              $columns
            }
            img { max-width: 100%; height: auto; }
        """.trimIndent()
    }

    companion object {
        private const val PREF_NAME = "novel_reader_settings"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_LINE_HEIGHT = "line_height"
        private const val KEY_MARGIN = "margin"
        private const val KEY_MODE = "mode"
        private const val KEY_DUAL_PAGE = "dual_page"

        fun load(context: NovelReaderActivity): NovelReaderSettings {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            return NovelReaderSettings(
                fontSizeSp = prefs.getFloat(KEY_FONT_SIZE, 16f),
                lineHeight = prefs.getFloat(KEY_LINE_HEIGHT, 1.6f),
                marginDp = prefs.getFloat(KEY_MARGIN, 16f),
                readingMode = runCatching {
                    NovelReadingMode.valueOf(prefs.getString(KEY_MODE, NovelReadingMode.DEFAULT.name)!!)
                }.getOrDefault(NovelReadingMode.DEFAULT),
                enableDualPage = prefs.getBoolean(KEY_DUAL_PAGE, true),
            )
        }
    }
}
