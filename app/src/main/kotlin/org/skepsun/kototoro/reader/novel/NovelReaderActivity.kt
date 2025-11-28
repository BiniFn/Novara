package org.skepsun.kototoro.reader.novel

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.transition.Fade
import androidx.transition.Slide
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.parcelable.ParcelableManga
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.parser.MangaRepository
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.ReaderMode
import org.skepsun.kototoro.core.ui.BaseFullscreenActivity
import org.skepsun.kototoro.core.util.ext.getParcelableExtraCompat
import org.skepsun.kototoro.core.util.ext.isAnimationsEnabled
import org.skepsun.kototoro.databinding.ActivityNovelReaderV2Binding
import org.skepsun.kototoro.parsers.model.Manga
import org.skepsun.kototoro.parsers.model.MangaChapter
import org.skepsun.kototoro.reader.ui.ReaderControlDelegate
import javax.inject.Inject

/**
 * 小说阅读器 Activity - 基于 TextView 的实现
 * 复用漫画阅读器的工具栏设计
 */
@AndroidEntryPoint
class NovelReaderActivity : 
    BaseFullscreenActivity<ActivityNovelReaderV2Binding>(),
    ReaderControlDelegate.OnInteractionListener,
    NovelReaderConfigSheet.Callback,
    NovelChaptersSheet.Callback {

    @Inject
    lateinit var mangaRepositoryFactory: MangaRepository.Factory

    @Inject
    lateinit var settings: AppSettings

    @Inject
    lateinit var tapGridSettings: org.skepsun.kototoro.reader.data.TapGridSettings

    @Inject
    lateinit var bookmarksRepository: org.skepsun.kototoro.bookmarks.domain.BookmarksRepository

    @Inject
    lateinit var historyRepository: org.skepsun.kototoro.history.data.HistoryRepository

    @Inject
    lateinit var historyUpdateUseCase: org.skepsun.kototoro.history.domain.HistoryUpdateUseCase

    private lateinit var manga: Manga
    private lateinit var repository: MangaRepository
    private lateinit var readerSettings: NovelReaderSettings

    private var chapters: List<MangaChapter> = emptyList()
    private var currentChapterIndex: Int = 0
    private var isUiVisible: Boolean = false
    private var currentPageIndex: Int = 0
    
    // EPUB内容缓存：key是文件路径，value是EpubContent
    private val epubContentCache = mutableMapOf<String, org.skepsun.kototoro.local.epub.EpubContent>()

    override val readerMode: ReaderMode?
        get() = ReaderMode.STANDARD

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ActivityNovelReaderV2Binding.inflate(layoutInflater))

        readerSettings = NovelReaderSettings.load(this)
        
        // 只恢复UI状态，不恢复章节和页码（由loadChapters处理）
        savedInstanceState?.let {
            isUiVisible = it.getBoolean(KEY_UI_VISIBLE, true)
        }

        val parcelable = intent.getParcelableExtraCompat<ParcelableManga>(AppRouter.KEY_MANGA)
        val mangaSeed = parcelable?.manga
        if (mangaSeed == null) {
            finish()
            return
        }

        manga = mangaSeed
        repository = mangaRepositoryFactory.create(manga.source)

        setDisplayHomeAsUp(isEnabled = true, showUpAsClose = false)
        
        // 设置标题为小说名称
        title = manga.title
        supportActionBar?.title = manga.title
        viewBinding.toolbar.title = manga.title
        viewBinding.toolbar.subtitle = getString(R.string.loading_)

        viewBinding.actionsView.listener = this

        // 设置章节列表按钮点击事件
        viewBinding.actionsView.findViewById<View>(R.id.button_pages_thumbs)?.setOnClickListener {
            showChaptersSheet()
        }

        viewBinding.readerView.onPageChangeListener = { page, total ->
            updateProgress(page, total)
            updateFooter(page, total)
        }
        
        // 使用手势区域处理
        viewBinding.readerView.onTapAreaListener = { area ->
            handleTapGesture(area)
        }
        
        // 章节切换请求处理
        viewBinding.readerView.onChapterChangeRequestListener = { delta ->
            switchChapterBy(delta)
        }

        viewBinding.readerView.updateSettings(readerSettings)
        updateDualPageMode()
        updateFullscreenMode()
        updateFooterVisibility()

        // 初始状态：显示工具栏
        isUiVisible = true
        viewBinding.appbarTop.isVisible = true
        viewBinding.toolbarDocked.isVisible = true

        loadChapters()
    }

    override fun getParentActivityIntent(): Intent? {
        return AppRouter.detailsIntent(this, manga)
    }

    override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        
        viewBinding.toolbar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = systemBars.top
            rightMargin = systemBars.right
            leftMargin = systemBars.left
        }

        // 修复：使用 toolbarDocked 而不是 actionsView 来设置 margin
        // 因为 actionsView 是 toolbarDocked 的子视图
        viewBinding.toolbarDocked.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = systemBars.bottom
            rightMargin = systemBars.right
            leftMargin = systemBars.left
        }

        viewBinding.infoBar.updatePadding(top = systemBars.top)

        // 给 readerView 添加 padding，避免内容显示在状态栏下方
        // 始终添加顶部 padding，因为即使在全屏模式下，状态栏也会在 UI 显示时出现
        viewBinding.readerView.updatePadding(
            top = systemBars.top,
            left = systemBars.left,
            right = systemBars.right,
            bottom = 0  // 底部由页脚处理
        )

        val innerInsets = Insets.of(
            systemBars.left,
            if (viewBinding.appbarTop.isVisible) viewBinding.appbarTop.height else systemBars.top,
            systemBars.right,
            viewBinding.toolbarDocked.takeIf { it.isVisible }?.height ?: systemBars.bottom,
        )

        return WindowInsetsCompat.Builder(insets)
            .setInsets(WindowInsetsCompat.Type.systemBars(), innerInsets)
            .build()
    }

    override fun switchPageBy(delta: Int) {
        if (delta > 0) {
            if (!viewBinding.readerView.nextPage()) {
                switchChapterBy(1)
            }
        } else {
            if (!viewBinding.readerView.previousPage()) {
                switchChapterBy(-1)
            }
        }
    }

    override fun switchChapterBy(delta: Int) {
        val targetIndex = currentChapterIndex + delta
        if (targetIndex in chapters.indices) {
            currentChapterIndex = targetIndex
            // 如果是向下一章，从第一页开始；如果是向上一章，从最后一页开始
            currentPageIndex = if (delta > 0) 0 else -1  // -1 表示最后一页
            loadChapter(currentChapterIndex)
        } else {
            // 已经是第一章或最后一章
            val message = if (delta > 0) {
                "已经是最后一章"
            } else {
                "已经是第一章"
            }
            viewBinding.toastView.showTemporary(message, 1500L)
        }
    }

    override fun openMenu() {
        showConfigSheet()
    }

    override fun scrollBy(delta: Int, smooth: Boolean): Boolean = false

    override fun toggleUiVisibility() {
        setUiVisible(!isUiVisible)
    }

    override fun isReaderResumed(): Boolean = true

    override fun onBookmarkClick() {
        val chapter = chapters.getOrNull(currentChapterIndex)
        if (chapter == null) {
            viewBinding.toastView.showTemporary("无法添加书签", 1500L)
            return
        }
        
        lifecycleScope.launch {
            try {
                val currentPage = viewBinding.readerView.getCurrentPage()
                val totalPages = viewBinding.readerView.getTotalPages()
                val percent = if (totalPages > 0) {
                    currentPage.toFloat() / totalPages
                } else {
                    0f
                }
                
                // 检查是否已存在书签
                val existingBookmark = bookmarksRepository.observeBookmark(
                    manga, chapter.id, currentPage
                ).first()
                
                if (existingBookmark != null) {
                    // 删除书签
                    bookmarksRepository.removeBookmark(manga.id, chapter.id, currentPage)
                    viewBinding.toastView.showTemporary("已移除书签", 1500L)
                } else {
                    // 添加书签 - 保存当前页面的文本预览
                    val pageText = viewBinding.readerView.getCurrentPageText()
                    val previewText = pageText.take(200).trim() // 取前200字符作为预览
                    
                    val bookmark = org.skepsun.kototoro.bookmarks.domain.Bookmark(
                        manga = manga,
                        pageId = System.currentTimeMillis(), // 使用时间戳作为 ID
                        chapterId = chapter.id,
                        page = currentPage,
                        scroll = 0,
                        imageUrl = previewText, // 保存文本预览
                        createdAt = java.time.Instant.now(),
                        percent = percent,
                    )
                    bookmarksRepository.addBookmark(bookmark)
                    viewBinding.toastView.showTemporary("已添加书签", 1500L)
                }
            } catch (e: Exception) {
                android.util.Log.e("NovelReaderActivity", "Failed to toggle bookmark", e)
                viewBinding.toastView.showTemporary("书签操作失败: ${e.message}", 2000L)
            }
        }
    }

    override fun onSavePageClick() {}

    override fun onScrollTimerClick(isLongClick: Boolean) {}

    override fun toggleScreenOrientation() {}

    override fun switchPageTo(index: Int) {
        viewBinding.readerView.goToPage(index)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        // 用户交互时的处理
    }

    private fun loadChapters() {
        lifecycleScope.launch {
            try {
                showLoading(true)
                val details = repository.getDetails(manga)
                var originalChapters = details.chapters.orEmpty()
                
                // 展开EPUB章节
                chapters = expandEpubChapters(originalChapters)
                
                // 优先级：Intent参数 > 历史记录 > 第一章
                // 从Intent中获取ReaderState
                val state = intent.getParcelableExtraCompat<org.skepsun.kototoro.reader.ui.ReaderState>(
                    org.skepsun.kototoro.core.nav.ReaderIntent.EXTRA_STATE
                )
                
                if (state != null && state.chapterId != 0L) {
                    // 1. 优先使用Intent中的ReaderState
                    
                    // 首先尝试在展开后的章节列表中精确匹配
                    var targetIndex = chapters.indexOfFirst { it.id == state.chapterId }
                    
                    // 如果找到了，但是是第一个章节（可能是原始EPUB章节），检查历史记录
                    if (targetIndex == 0 && originalChapters.size == 1) {
                        // 这可能是EPUB章节，检查历史记录中是否有更精确的位置
                        val history = historyRepository.getOne(manga)
                        if (history != null && history.chapterId != state.chapterId) {
                            // 历史记录中保存的是不同的章节ID（内部章节）
                            val historyIndex = chapters.indexOfFirst { it.id == history.chapterId }
                            if (historyIndex >= 0) {
                                targetIndex = historyIndex
                                currentPageIndex = history.page
                            }
                        }
                    }
                    
                    // 如果找不到精确匹配，尝试查找原始EPUB章节
                    if (targetIndex < 0) {
                        val originalIndex = originalChapters.indexOfFirst { it.id == state.chapterId }
                        if (originalIndex >= 0) {
                            // 找到了原始章节，计算它在展开后列表中的起始位置
                            // 由于第一个内部章节保留了原始ID，直接使用原始索引作为fallback
                            targetIndex = originalIndex
                            
                            // 同时检查历史记录
                            val history = historyRepository.getOne(manga)
                            if (history != null) {
                                val historyIndex = chapters.indexOfFirst { it.id == history.chapterId }
                                if (historyIndex >= 0) {
                                    targetIndex = historyIndex
                                    currentPageIndex = history.page
                                }
                            }
                        } else {
                            // Intent中的章节ID不属于当前小说，尝试从历史记录恢复
                            val history = historyRepository.getOne(manga)
                            if (history != null) {
                                val historyIndex = chapters.indexOfFirst { it.id == history.chapterId }
                                if (historyIndex >= 0) {
                                    targetIndex = historyIndex
                                    currentPageIndex = history.page
                                }
                            }
                        }
                    }
                    
                    if (targetIndex >= 0) {
                        currentChapterIndex = targetIndex
                        if (currentPageIndex == 0) { // 只有在没有从历史记录恢复页码时才使用Intent中的页码
                            currentPageIndex = state.page
                        }
                    } else {
                        // 章节不存在（可能已被删除或来自其他小说），从第一章开始
                        currentChapterIndex = 0
                        currentPageIndex = 0
                    }
                    
                    // 清除Intent中的state，避免下次重复使用
                    intent.removeExtra(org.skepsun.kototoro.core.nav.ReaderIntent.EXTRA_STATE)
                } else {
                    // 2. 尝试从历史记录恢复
                    val history = historyRepository.getOne(manga)
                    if (history != null) {
                        val targetIndex = chapters.indexOfFirst { it.id == history.chapterId }
                        if (targetIndex >= 0) {
                            currentChapterIndex = targetIndex
                            currentPageIndex = history.page
                        } else {
                            // 历史章节不存在（可能已被删除），从第一章开始
                            currentChapterIndex = 0
                            currentPageIndex = 0
                        }
                    } else {
                        // 3. 默认从第一章开始
                        currentChapterIndex = 0
                        currentPageIndex = 0
                    }
                }
                
                if (chapters.isEmpty()) {
                    showLoading(false)
                    showError(getString(R.string.no_chapters_in_manga))
                } else {
                    loadChapter(currentChapterIndex)
                }
            } catch (e: Exception) {
                android.util.Log.e("NovelReaderActivity", "Failed to load chapters", e)
                showLoading(false)
                showError("加载章节列表失败: ${e.message}")
            }
        }
    }

    private fun loadChapter(index: Int) {
        val chapter = chapters.getOrNull(index)
        if (chapter == null) {
            android.util.Log.e("NovelReaderActivity", "Chapter at index $index not found")
            return
        }
        
        lifecycleScope.launch {
            try {
                showLoading(true)
                
                // 检查是否为EPUB内部章节（URL包含#chapter/）
                if (chapter.url.contains("#chapter/")) {
                    android.util.Log.d("NovelReaderActivity", "Detected EPUB internal chapter: ${chapter.url}")
                    
                    // 解析章节索引
                    val chapterIndexStr = chapter.url.substringAfter("#chapter/")
                    val chapterIndex = chapterIndexStr.toIntOrNull()
                    
                    if (chapterIndex != null) {
                        android.util.Log.d("NovelReaderActivity", "Parsed chapter index: $chapterIndex")
                        
                        // 从EPUB文件读取指定章节
                        // 使用当前chapter对象查找本地文件（因为所有内部章节共享同一个EPUB文件）
                        val epubContent = loadEpubContentFromUrl(chapter.url, chapter)
                        
                        if (epubContent != null) {
                            android.util.Log.d("NovelReaderActivity", "EPUB has ${epubContent.chapters.size} chapters, requesting index $chapterIndex")
                            
                            if (chapterIndex < epubContent.chapters.size) {
                                val epubChapter = epubContent.chapters[chapterIndex]
                                android.util.Log.d("NovelReaderActivity", "Loading EPUB chapter: title='${epubChapter.title}', content length=${epubChapter.content.length}")
                                
                                val content = buildString {
                                    append("【${epubChapter.title}】\n\n")
                                    append(epubChapter.content)
                                }
                                showLoading(false)
                                renderChapter(chapter, content)
                                return@launch
                            } else {
                                android.util.Log.e("NovelReaderActivity", "Chapter index $chapterIndex out of bounds (total: ${epubContent.chapters.size})")
                            }
                        } else {
                            android.util.Log.e("NovelReaderActivity", "Failed to load EPUB content")
                        }
                    } else {
                        android.util.Log.e("NovelReaderActivity", "Failed to parse chapter index from: $chapterIndexStr")
                    }
                    
                    // 解析失败，显示错误
                    showLoading(false)
                    showError("无法加载EPUB章节")
                    return@launch
                }
                
                val pages = repository.getPages(chapter)
                
                android.util.Log.d("NovelReaderActivity", "Got ${pages.size} pages, first page preview: ${pages.firstOrNull()?.preview}, url: ${pages.firstOrNull()?.url?.take(100)}")
                
                // 检查是否为EPUB章节（通过preview字段标记）
                if (pages.size == 1 && pages[0].preview == "EPUB") {
                    android.util.Log.d("NovelReaderActivity", "Detected EPUB chapter, loading EPUB content")
                    // 尝试读取EPUB内容
                    val epubContent = loadEpubContent(chapter)
                    showLoading(false)
                    
                    if (epubContent != null) {
                        // 成功读取EPUB，显示内容
                        renderChapter(chapter, epubContent)
                    } else {
                        // 读取失败，显示提示信息
                        val webUrl = pages[0].url
                        val epubMessage = """
                            此章节为EPUB格式文件
                            
                            Novelia文库的EPUB文件需要在网页端下载。
                            
                            下载步骤：
                            1. 在浏览器中打开小说页面
                            2. 找到对应的分卷
                            3. 点击下载按钮
                            4. 下载EPUB文件
                            5. 使用EPUB阅读器打开
                            
                            小说页面：
                            $webUrl
                            
                            提示：
                            - 可能需要登录Novelia账号
                            - 下载后可以使用Moon+ Reader等阅读器打开
                            - 未来版本将支持更便捷的下载方式
                        """.trimIndent()
                        renderChapter(chapter, epubMessage)
                    }
                    return@launch
                }
                
                android.util.Log.d("NovelReaderActivity", "Processing as regular chapter")
                val html = pages.firstOrNull()?.url?.let(::decodeChapterHtml)
                    ?: getString(R.string.chapter_is_missing)
                val plainText = htmlToPlainText(html)
                
                android.util.Log.d("NovelReaderActivity", "Plain text length: ${plainText.length}")
                
                showLoading(false)
                renderChapter(chapter, plainText)
            } catch (e: Exception) {
                android.util.Log.e("NovelReaderActivity", "Failed to load chapter", e)
                showLoading(false)
                showError("加载章节失败: ${e.message}")
            }
        }
    }

    /**
     * 加载EPUB内容
     * 从CBZ压缩包中提取并解析EPUB文件
     */
    private suspend fun loadEpubContent(chapter: MangaChapter): String? {
        return try {
            android.util.Log.d("NovelReaderActivity", "Loading EPUB content for: ${chapter.title}")
            
            // 获取章节URL
            val chapterUri = android.net.Uri.parse(chapter.url)
            
            // 使用EpubReader读取EPUB
            val epubReader = org.skepsun.kototoro.local.epub.EpubReader()
            val epubContent = epubReader.readEpub(chapterUri)
            
            if (epubContent == null) {
                android.util.Log.e("NovelReaderActivity", "Failed to read EPUB content")
                return null
            }
            
            // 合并所有章节内容
            val fullContent = buildString {
                append("《${epubContent.title}》\n")
                append("作者：${epubContent.author}\n")
                append("\n")
                append("=".repeat(40))
                append("\n\n")
                
                for (epubChapter in epubContent.chapters) {
                    append("【${epubChapter.title}】\n\n")
                    append(epubChapter.content)
                    append("\n\n")
                    append("-".repeat(40))
                    append("\n\n")
                }
            }
            
            android.util.Log.d("NovelReaderActivity", "EPUB content loaded successfully, length: ${fullContent.length}")
            fullContent
        } catch (e: Exception) {
            android.util.Log.e("NovelReaderActivity", "Failed to load EPUB content", e)
            null
        }
    }

    private fun renderChapter(chapter: MangaChapter, text: String) {
        try {
            android.util.Log.d("NovelReaderActivity", "renderChapter called for: ${chapter.title}")
            
            viewBinding.toolbar.subtitle = chapter.title ?: getString(R.string.unnamed_chapter)
            
            // 确保文本不为空
            val contentToDisplay = if (text.isBlank()) {
                android.util.Log.w("NovelReaderActivity", "Chapter content is blank")
                "章节内容为空\n\n请检查网络连接或稍后重试"
            } else {
                text
            }
            
            android.util.Log.d("NovelReaderActivity", "Content length: ${contentToDisplay.length}, first 100 chars: ${contentToDisplay.take(100)}")
            
            // 确保在主线程上执行
            runOnUiThread {
                try {
                    android.util.Log.d("NovelReaderActivity", "Setting content to readerView")
                    
                    val savedPageIndex = currentPageIndex
                    val needsPageRestore = savedPageIndex != 0
                    
                    // 直接在 setContent 时设置目标页码，避免中间状态
                    viewBinding.readerView.setContent(
                        content = contentToDisplay,
                        resetPage = true,
                        suppressNotification = needsPageRestore,
                        initialPageIndex = savedPageIndex  // 直接传入目标页码（包括 -1）
                    )
                    
                    android.util.Log.d("NovelReaderActivity", "Content set successfully with initial page: $savedPageIndex")
                    
                    // 如果需要恢复页码，等待分页完成后恢复通知
                    if (needsPageRestore) {
                        viewBinding.readerView.postDelayed({
                            // 恢复通知并立即通知一次
                            viewBinding.readerView.resumePageChangeNotification()
                        }, 100)
                    }
                    
                    currentPageIndex = 0 // 重置
                    updateNavigationButtons()
                    
                    // 立即保存一次历史记录，确保小说出现在历史列表中
                    viewBinding.readerView.post {
                        val currentPage = viewBinding.readerView.getCurrentPage()
                        val totalPages = viewBinding.readerView.getTotalPages()
                        if (totalPages > 0) {
                            updateHistory(currentPage, totalPages)
                        }
                    }
                    
                    if (settings.isReaderChapterToastEnabled) {
                        viewBinding.toastView.showTemporary(
                            chapter.title ?: getString(R.string.unnamed_chapter),
                            2000L,
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e("NovelReaderActivity", "Failed to set content", e)
                    showError("显示内容失败: ${e.message}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("NovelReaderActivity", "Failed to render chapter", e)
            showError("渲染失败: ${e.message}")
        }
    }

    /**
     * 从URL或本地文件加载EPUB内容（带缓存）
     */
    private suspend fun loadEpubContentFromUrl(url: String, chapter: MangaChapter): org.skepsun.kototoro.local.epub.EpubContent? {
        return withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("NovelReaderActivity", "Loading EPUB from URL: $url")
                
                // 首先尝试从本地下载的文件加载
                val localFile = findLocalEpubFile(chapter)
                if (localFile != null && localFile.exists()) {
                    val filePath = localFile.absolutePath
                    android.util.Log.d("NovelReaderActivity", "Found local EPUB file: $filePath")
                    
                    // 检查缓存
                    epubContentCache[filePath]?.let {
                        android.util.Log.d("NovelReaderActivity", "Using cached EPUB content for: $filePath")
                        return@withContext it
                    }
                    
                    // 读取并缓存
                    val epubReader = org.skepsun.kototoro.local.epub.EpubReader()
                    val uri = android.net.Uri.fromFile(localFile)
                    val content = epubReader.readEpub(uri)
                    if (content != null) {
                        epubContentCache[filePath] = content
                        android.util.Log.d("NovelReaderActivity", "Cached EPUB content for: $filePath")
                    }
                    return@withContext content
                }
                
                // 如果是file://协议，尝试直接读取
                if (url.startsWith("file://")) {
                    val filePath = url.substring(7)
                    val file = java.io.File(filePath)
                    if (file.exists()) {
                        android.util.Log.d("NovelReaderActivity", "Loading from file path: $filePath")
                        
                        // 检查缓存
                        epubContentCache[filePath]?.let {
                            android.util.Log.d("NovelReaderActivity", "Using cached EPUB content for: $filePath")
                            return@withContext it
                        }
                        
                        // 读取并缓存
                        val epubReader = org.skepsun.kototoro.local.epub.EpubReader()
                        val uri = android.net.Uri.fromFile(file)
                        val content = epubReader.readEpub(uri)
                        if (content != null) {
                            epubContentCache[filePath] = content
                            android.util.Log.d("NovelReaderActivity", "Cached EPUB content for: $filePath")
                        }
                        return@withContext content
                    }
                }
                
                android.util.Log.w("NovelReaderActivity", "EPUB file not found locally")
                null
            } catch (e: Exception) {
                android.util.Log.e("NovelReaderActivity", "Failed to load EPUB from URL", e)
                null
            }
        }
    }
    
    /**
     * 查找本地下载的EPUB文件（可能被重命名为.cbz）
     */
    private fun findLocalEpubFile(chapter: MangaChapter): java.io.File? {
        try {
            // 获取下载目录
            val downloadDir = getExternalFilesDir(null)?.resolve("manga") ?: return null
            
            android.util.Log.d("NovelReaderActivity", "Searching for EPUB file in: ${downloadDir.absolutePath}")
            android.util.Log.d("NovelReaderActivity", "Manga ID: ${manga.id}, Title: ${manga.title}")
            android.util.Log.d("NovelReaderActivity", "Chapter ID: ${chapter.id}, Title: ${chapter.title}")
            
            // 策略1: 按manga ID查找目录
            val mangaIdStr = manga.id.toString()
            val mangaDirs = listOf(
                downloadDir.resolve(mangaIdStr),
                downloadDir.resolve("_${mangaIdStr}"),
                downloadDir.resolve("__${mangaIdStr}"),
            )
            
            for (dir in mangaDirs) {
                if (!dir.exists() || !dir.isDirectory) continue
                
                val files = dir.listFiles { file ->
                    file.isFile && (file.name.endsWith(".cbz") || file.name.endsWith(".epub"))
                }
                
                if (files != null && files.isNotEmpty()) {
                    val file = files.firstOrNull()
                    if (file != null) {
                        android.util.Log.d("NovelReaderActivity", "Found EPUB file in manga dir: ${file.absolutePath}")
                        return file
                    }
                }
            }
            
            // 策略2: 在所有子目录中查找，并通过index.json验证是否属于当前manga
            val allDirs = downloadDir.listFiles { file -> file.isDirectory } ?: emptyArray()
            android.util.Log.d("NovelReaderActivity", "Searching in ${allDirs.size} directories")
            
            for (dir in allDirs) {
                // 检查index.json中的manga信息
                val indexFile = dir.resolve("index.json")
                if (indexFile.exists()) {
                    try {
                        val indexContent = indexFile.readText()
                        android.util.Log.d("NovelReaderActivity", "Checking ${dir.name}/index.json")
                        
                        // 严格匹配：必须完全匹配manga ID
                        val idPattern1 = "\"id\":${manga.id}"
                        val idPattern2 = "\"id\": ${manga.id}"
                        val idPattern3 = "\"id\" : ${manga.id}"
                        
                        if (indexContent.contains(idPattern1) || 
                            indexContent.contains(idPattern2) ||
                            indexContent.contains(idPattern3)) {
                            
                            android.util.Log.d("NovelReaderActivity", "Manga ID matched in ${dir.name}")
                            
                            val files = dir.listFiles { file ->
                                file.isFile && (file.name.endsWith(".cbz") || file.name.endsWith(".epub"))
                            }
                            
                            if (files != null && files.isNotEmpty()) {
                                val file = files.firstOrNull()
                                if (file != null) {
                                    android.util.Log.d("NovelReaderActivity", "Found EPUB file by index.json: ${file.absolutePath}")
                                    return file
                                }
                            }
                        } else {
                            android.util.Log.d("NovelReaderActivity", "Manga ID not matched in ${dir.name} (looking for ${manga.id})")
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("NovelReaderActivity", "Failed to read index.json in ${dir.name}", e)
                    }
                } else {
                    android.util.Log.d("NovelReaderActivity", "No index.json in ${dir.name}")
                }
            }
            
            android.util.Log.d("NovelReaderActivity", "No local EPUB file found for manga ${manga.id}")
        } catch (e: Exception) {
            android.util.Log.e("NovelReaderActivity", "Error finding local EPUB file", e)
        }
        return null
    }

    /**
     * 展开EPUB章节：将EPUB文件章节替换为其内部章节列表
     */
    private suspend fun expandEpubChapters(originalChapters: List<MangaChapter>): List<MangaChapter> {
        val expandedChapters = mutableListOf<MangaChapter>()
        
        android.util.Log.d("NovelReaderActivity", "expandEpubChapters: Processing ${originalChapters.size} chapters")
        
        for (chapter in originalChapters) {
            try {
                // 检查是否为EPUB章节
                val pages = repository.getPages(chapter)
                android.util.Log.d("NovelReaderActivity", "Chapter '${chapter.title}': ${pages.size} pages, preview='${pages.firstOrNull()?.preview}'")
                
                if (pages.size == 1 && pages[0].preview == "EPUB") {
                    android.util.Log.d("NovelReaderActivity", "Found EPUB chapter: ${chapter.title}, ID=${chapter.id}, expanding...")
                    
                    // 尝试读取EPUB内容
                    val epubContent = loadEpubContentFromUrl(pages[0].url, chapter)
                    if (epubContent != null && epubContent.chapters.isNotEmpty()) {
                        // 不过滤，显示所有章节
                        epubContent.chapters.forEachIndexed { chapterIndex, epubChapter ->
                            val generatedUrl = "${pages[0].url}#chapter/$chapterIndex"
                            val internalChapter = MangaChapter(
                                // 第一个内部章节保留原始章节ID，这样Intent能正确找到它
                                // 其他章节使用偏移ID
                                id = if (chapterIndex == 0) chapter.id else chapter.id + chapterIndex,
                                title = epubChapter.title,
                                number = chapter.number + chapterIndex,
                                volume = chapter.volume,
                                url = generatedUrl, // 使用章节索引
                                scanlator = chapter.scanlator,
                                uploadDate = chapter.uploadDate,
                                branch = chapter.branch,
                                source = chapter.source,
                            )
                            expandedChapters.add(internalChapter)
                            
                            // 打印章节映射信息（仅前5个和后5个）
                            if (chapterIndex < 5 || chapterIndex >= epubContent.chapters.size - 5) {
                                android.util.Log.d("NovelReaderActivity", "  Chapter mapping: index=$chapterIndex, title='${epubChapter.title}', url='${internalChapter.url.takeLast(15)}'")
                            }
                        }
                        android.util.Log.d("NovelReaderActivity", "Expanded EPUB chapter into ${epubContent.chapters.size} internal chapters")
                    } else {
                        // 读取失败，保留原始章节
                        android.util.Log.w("NovelReaderActivity", "Failed to expand EPUB chapter, keeping original")
                        expandedChapters.add(chapter)
                    }
                } else {
                    // 非EPUB章节，直接添加
                    expandedChapters.add(chapter)
                }
            } catch (e: Exception) {
                android.util.Log.e("NovelReaderActivity", "Failed to expand chapter: ${chapter.title}", e)
                // 出错时保留原始章节
                expandedChapters.add(chapter)
            }
        }
        
        return expandedChapters
    }

    private fun updateProgress(page: Int, total: Int) {
        viewBinding.actionsView.setSliderValue(page, total - 1)
        viewBinding.actionsView.isSliderEnabled = total > 1
    }

    private fun updateNavigationButtons() {
        viewBinding.actionsView.isPrevEnabled = currentChapterIndex > 0
        viewBinding.actionsView.isNextEnabled = currentChapterIndex < chapters.lastIndex
    }

    private fun setUiVisible(visible: Boolean) {
        if (viewBinding.appbarTop.isVisible != visible) {
            if (isAnimationsEnabled) {
                val transition = TransitionSet()
                    .setOrdering(TransitionSet.ORDERING_TOGETHER)
                    .addTransition(Slide(Gravity.TOP).addTarget(viewBinding.appbarTop))
                    .addTransition(Slide(Gravity.BOTTOM).addTarget(viewBinding.toolbarDocked))
                    .addTransition(Fade().addTarget(viewBinding.infoBar))
                TransitionManager.beginDelayedTransition(viewBinding.root, transition)
            }
            
            isUiVisible = visible
            viewBinding.appbarTop.isVisible = visible
            viewBinding.toolbarDocked.isVisible = visible
            viewBinding.infoBar.isGone = true // 小说阅读器暂时不显示 infoBar
            
            // 工具栏显示时隐藏页脚，避免重叠
            viewBinding.footerBar.isVisible = !visible && readerSettings.showFooter
            
            // 根据全屏设置和 UI 可见性控制系统 UI
            // 如果不是全屏模式，总是显示状态栏
            // 如果是全屏模式，只在 UI 可见时显示状态栏
            val shouldShowSystemUi = !readerSettings.enableFullscreen || visible
            systemUiController.setSystemUiVisible(shouldShowSystemUi)
            
            // 更新状态栏颜色
            updateStatusBarColor()
            
            viewBinding.root.requestApplyInsets()
        }
    }

    private fun showLoading(loading: Boolean) {
        viewBinding.layoutLoading.isVisible = loading
        if (loading) {
            viewBinding.toastView.show(R.string.loading_)
        } else {
            viewBinding.toastView.hide()
        }
    }

    private fun showError(message: String) {
        viewBinding.toastView.showTemporary(message, 3000L)
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

    private fun htmlToPlainText(html: String): String {
        return html
            .replace(Regex("<script[^>]*>.*?</script>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<style[^>]*>.*?</style>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .trim()
            .lines()
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
    }

    private fun updateDualPageMode() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val isTablet = resources.getBoolean(R.bool.is_tablet)
        val shouldEnableDualPage = readerSettings.enableDualPage && (isLandscape || isTablet)
        viewBinding.readerView.setDualPageMode(shouldEnableDualPage)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_CHAPTER_INDEX, currentChapterIndex)
        outState.putInt(KEY_PAGE_INDEX, viewBinding.readerView.getCurrentPage())
        outState.putBoolean(KEY_UI_VISIBLE, isUiVisible)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 保存当前页码
        val savedPageIndex = viewBinding.readerView.getCurrentPage()
        android.util.Log.d("NovelReaderActivity", "Configuration changed, saving page: $savedPageIndex")
        
        updateDualPageMode()
        
        // 等待重新分页完成后恢复页码
        // 使用 postDelayed 确保分页已经完成
        viewBinding.readerView.postDelayed({
            if (savedPageIndex > 0 && savedPageIndex < viewBinding.readerView.getTotalPages()) {
                android.util.Log.d("NovelReaderActivity", "Restoring page: $savedPageIndex")
                viewBinding.readerView.goToPage(savedPageIndex)
            }
        }, 100)
    }

    companion object {
        private const val KEY_CHAPTER_INDEX = "chapter_index"
        private const val KEY_PAGE_INDEX = "page_index"
        private const val KEY_UI_VISIBLE = "ui_visible"
    }

    /**
     * 显示章节选择器
     */
    private fun showChaptersSheet() {
        android.util.Log.d("NovelReaderActivity", "showChaptersSheet: chapters.size=${chapters.size}, currentChapterIndex=$currentChapterIndex")
        if (chapters.isEmpty()) {
            viewBinding.toastView.showTemporary("暂无章节", 1500L)
            return
        }

        // 打印前5个章节的URL用于调试
        chapters.take(5).forEachIndexed { index, chapter ->
            android.util.Log.d("NovelReaderActivity", "  Chapter[$index]: title='${chapter.title}', url='${chapter.url.takeLast(15)}'")
        }

        val sheet = NovelChaptersSheet.newInstance(chapters, currentChapterIndex)
        sheet.show(supportFragmentManager, "novel_chapters")
    }

    /**
     * 显示设置面板
     */
    private fun showConfigSheet() {
        val sheet = NovelReaderConfigSheet.newInstance()
        sheet.show(supportFragmentManager, "novel_config")
    }

    // NovelChaptersSheet.Callback 实现
    override fun onChapterSelected(index: Int) {
        android.util.Log.d("NovelReaderActivity", "onChapterSelected: index=$index, currentChapterIndex=$currentChapterIndex, chapters.size=${chapters.size}")
        if (index != currentChapterIndex && index in chapters.indices) {
            val selectedChapter = chapters[index]
            android.util.Log.d("NovelReaderActivity", "Loading selected chapter: index=$index, title='${selectedChapter.title}', url='${selectedChapter.url}'")
            currentChapterIndex = index
            loadChapter(currentChapterIndex)
        } else {
            android.util.Log.w("NovelReaderActivity", "Chapter selection ignored: index=$index, same as current or out of bounds")
        }
    }

    /**
     * 处理手势
     */
    private fun handleTapGesture(area: org.skepsun.kototoro.reader.domain.TapGridArea) {
        val action = tapGridSettings.getTapAction(area, false)
        android.util.Log.d("NovelReaderActivity", "Tap area: $area, action: $action")
        
        when (action) {
            org.skepsun.kototoro.reader.ui.tapgrid.TapAction.PAGE_NEXT -> switchPageBy(1)
            org.skepsun.kototoro.reader.ui.tapgrid.TapAction.PAGE_PREV -> switchPageBy(-1)
            org.skepsun.kototoro.reader.ui.tapgrid.TapAction.CHAPTER_NEXT -> switchChapterBy(1)
            org.skepsun.kototoro.reader.ui.tapgrid.TapAction.CHAPTER_PREV -> switchChapterBy(-1)
            org.skepsun.kototoro.reader.ui.tapgrid.TapAction.TOGGLE_UI -> toggleUiVisibility()
            org.skepsun.kototoro.reader.ui.tapgrid.TapAction.SHOW_MENU -> openMenu()
            null -> {
                // 没有配置动作，默认切换 UI
                toggleUiVisibility()
            }
        }
    }

    /**
     * 更新页脚
     */
    private fun updateFooter(page: Int, total: Int) {
        val chapter = chapters.getOrNull(currentChapterIndex)
        viewBinding.textFooterChapter.text = chapter?.title ?: getString(R.string.unnamed_chapter)
        viewBinding.textFooterProgress.text = "${page + 1}/$total"
        
        // 更新历史记录
        updateHistory(page, total)
    }

    /**
     * 更新历史记录和阅读进度
     */
    private fun updateHistory(page: Int, total: Int) {
        val chapter = chapters.getOrNull(currentChapterIndex) ?: return
        
        lifecycleScope.launch {
            try {
                // 确保 manga 有章节信息
                val mangaWithChapters = if (manga.chapters.isNullOrEmpty()) {
                    try {
                        repository.getDetails(manga)
                    } catch (e: Exception) {
                        android.util.Log.e("NovelReaderActivity", "Failed to get manga details for history", e)
                        manga
                    }
                } else {
                    manga
                }
                
                // 如果仍然没有章节信息，不保存历史
                if (mangaWithChapters.chapters.isNullOrEmpty()) {
                    android.util.Log.w("NovelReaderActivity", "Cannot save history: no chapters available")
                    return@launch
                }
                
                // 计算当前章节在所有章节中的进度
                val chapterProgress = if (total > 0) page.toFloat() / total else 0f
                
                // 计算总体阅读进度
                // 进度 = (已读完章节数 + 当前章节进度) / 总章节数
                val totalProgress = if (chapters.isNotEmpty()) {
                    (currentChapterIndex + chapterProgress) / chapters.size
                } else {
                    0f
                }.coerceIn(0f, 1f)
                
                android.util.Log.d("NovelReaderActivity", "Updating history: chapter=$currentChapterIndex/${chapters.size}, page=$page/$total, progress=$totalProgress")
                
                // 创建 ReaderState
                // 注意：对于EPUB，我们直接保存当前内部章节的ID和页码
                // 阅读器会通过fallback逻辑从历史记录中恢复
                val readerState = org.skepsun.kototoro.reader.ui.ReaderState(
                    chapterId = chapter.id,
                    page = page,
                    scroll = 0
                )
                
                // 异步更新历史记录
                historyUpdateUseCase.invokeAsync(mangaWithChapters, readerState, totalProgress)
                
                android.util.Log.d("NovelReaderActivity", "History update invoked successfully")
            } catch (e: Exception) {
                android.util.Log.e("NovelReaderActivity", "Failed to update history", e)
            }
        }
    }

    /**
     * 更新页脚可见性
     */
    private fun updateFooterVisibility() {
        viewBinding.footerBar.isVisible = readerSettings.showFooter
        
        // 测量页脚高度并通知 ReaderView
        viewBinding.footerBar.post {
            val footerHeight = if (readerSettings.showFooter) {
                viewBinding.footerBar.height
            } else {
                0
            }
            viewBinding.readerView.setFooterHeight(footerHeight)
        }
    }

    /**
     * 更新状态栏颜色
     */
    private fun updateStatusBarColor() {
        // 在非全屏模式下，给状态栏添加背景色，避免内容透过状态栏显示
        if (!readerSettings.enableFullscreen) {
            // 使用主题的 colorSurface 作为状态栏背景
            val typedValue = android.util.TypedValue()
            theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)
            window.statusBarColor = typedValue.data
        } else {
            // 全屏模式下保持透明
            window.statusBarColor = android.graphics.Color.TRANSPARENT
        }
    }

    /**
     * 更新全屏模式
     */
    private fun updateFullscreenMode() {
        // 根据全屏设置和当前 UI 状态控制系统 UI
        val shouldShowSystemUi = !readerSettings.enableFullscreen || isUiVisible
        systemUiController.setSystemUiVisible(shouldShowSystemUi)
        
        // 更新状态栏颜色
        updateStatusBarColor()
        
        // 重新应用 insets
        viewBinding.root.requestApplyInsets()
    }

    // NovelReaderConfigSheet.Callback 实现
    override fun onSettingsChanged(settings: NovelReaderSettings) {
        try {
            android.util.Log.d("NovelReaderActivity", "Settings changed: fontSize=${settings.fontSizeSp}")
            readerSettings = settings
            
            runOnUiThread {
                try {
                    viewBinding.readerView.updateSettings(settings)
                    updateDualPageMode()
                    updateFullscreenMode()
                    updateFooterVisibility()
                } catch (e: Exception) {
                    android.util.Log.e("NovelReaderActivity", "Failed to update settings", e)
                    showError("更新设置失败: ${e.message}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("NovelReaderActivity", "Failed to apply settings", e)
        }
    }
}
