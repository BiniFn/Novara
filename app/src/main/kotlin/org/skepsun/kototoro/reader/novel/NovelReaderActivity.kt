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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.isLocal
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
import org.skepsun.kototoro.parsers.model.MangaParserSource
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

    @Inject
    lateinit var novelContentLoader: NovelContentLoader
    
    @Inject
    lateinit var epubFileManager: org.skepsun.kototoro.local.epub.EpubFileManager
    
    @Inject
    lateinit var epubChapterMappingDao: org.skepsun.kototoro.core.db.dao.EpubChapterMappingDao
    
    @Inject
    lateinit var epubContentCache: org.skepsun.kototoro.local.epub.EpubContentCache

    @Inject
    lateinit var localMangaRepository: org.skepsun.kototoro.local.data.LocalMangaRepository

    private lateinit var manga: Manga
    private lateinit var repository: MangaRepository
    private var originalManga: Manga? = null  // Store original for online fallback
    private lateinit var readerSettings: NovelReaderSettings
    private lateinit var epubInternalChapterLoader: EpubInternalChapterLoader

    private var chapters: List<MangaChapter> = emptyList()
    private var currentChapterIndex: Int = 0
    private var isUiVisible: Boolean = false
    private var currentPageIndex: Int = 0
    private var desiredProgressRatio: Float? = null

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

        // Save original manga for online fallback（若当前是本地 URI，尝试从 index.json 恢复对应的远端信息以获得原始 URL）
        val isMangaSeedLocalUrl = mangaSeed.url.let { it.startsWith("file://") || it.startsWith("zip://") || it.startsWith("cbz://") || it.startsWith("local://") }
        val maybeRemote = runCatching {
            runBlocking {
                if (isMangaSeedLocalUrl) localMangaRepository.getRemoteManga(mangaSeed) else null
            }
        }.getOrNull()
        originalManga = maybeRemote ?: mangaSeed
        
        val local = runCatching {
            runBlocking {
                localMangaRepository.findSavedManga(mangaSeed, withDetails = true)
            }
        }.getOrNull()
        manga = local?.manga ?: mangaSeed
        
        // 如果是从历史记录进入（可能 URL 是 local 但 source 已修正）或者来源是 Unknown，
        // 尝试修正为原始来源以支持在线跳转，并确保有远程 URL 可用
        if ((manga.source.name.startsWith("LOCAL") || manga.source == org.skepsun.kototoro.core.model.UnknownMangaSource) 
            && originalManga != null) {
            manga = manga.copy(source = originalManga!!.source, url = originalManga!!.url)
            android.util.Log.d("NovelReaderActivity", "Fixed manga source to ${manga.source.name} and URL to ${manga.url}")
        }
        if (local != null && (manga.chapters.isNullOrEmpty())) {
            // 某些情况下索引未带章节，兜底从本地解析一遍
            runCatching {
                manga = runBlocking { localMangaRepository.getDetails(manga) }
                android.util.Log.d(
                    "NovelReaderActivity",
                    "Refetched local details, chapters=${manga.chapters?.size ?: 0}",
                )
            }.onFailure {
                android.util.Log.w("NovelReaderActivity", "Failed to refetch local details", it)
            }
            // 再次兜底：直接用 LocalMangaParser 解析目录/CBZ
            if (manga.chapters.isNullOrEmpty()) {
                runCatching {
                    val parser = org.skepsun.kototoro.local.data.input.LocalMangaParser.getOrNull(
                        java.io.File(java.net.URI(manga.url))
                    )
                    if (parser != null) {
                        manga = runBlocking { parser.getManga(withDetails = true).manga }
                        android.util.Log.d(
                            "NovelReaderActivity",
                            "Parsed chapters via LocalMangaParser fallback, count=${manga.chapters?.size ?: 0}",
                        )
                    }
                }.onFailure {
                    android.util.Log.w("NovelReaderActivity", "Fallback parse failed", it)
                }
            }
        }
        repository = mangaRepositoryFactory.create(manga.source)
        if (local != null) {
            android.util.Log.d("NovelReaderActivity", "Using local manga for reading: ${manga.title}")
        }
        epubInternalChapterLoader = EpubInternalChapterLoader(
            context = this,
            epubFileManager = epubFileManager,
            epubChapterMappingDao = epubChapterMappingDao,
            epubContentCache = epubContentCache
        )
        
        android.util.Log.d("NovelReaderActivity", "=== onCreate ===")
        android.util.Log.d("NovelReaderActivity", "Manga: id=${manga.id}, title=${manga.title}")
        android.util.Log.d("NovelReaderActivity", "Manga has chapters: ${manga.chapters != null}, count: ${manga.chapters?.size ?: 0}")
        android.util.Log.d("NovelReaderActivity", "Repository type: ${repository.javaClass.simpleName}")

        setDisplayHomeAsUp(isEnabled = true, showUpAsClose = false)
        
        // 设置标题为小说名称
        title = manga.title
        supportActionBar?.title = manga.title
        viewBinding.toolbar.title = manga.title
        viewBinding.toolbar.subtitle = getString(R.string.loading_)

        viewBinding.actionsView.listener = this
        setupImageHeaders()

        // 设置章节列表按钮点击事件
        viewBinding.actionsView.findViewById<View>(R.id.button_pages_thumbs)?.setOnClickListener {
            showChaptersSheet()
        }

        viewBinding.readerView.onPageChangeListener = { page, total ->
            // 显示用页码按双页 spread 计数，实际进度用字符比例
            val displayPage = viewBinding.readerView.getDisplayPageIndex()
            val displayTotal = viewBinding.readerView.getDisplayPageCount()
            updateProgress(displayPage, displayTotal)
            updateReadingStatus(displayPage, displayTotal)
        }
        
        // 使用手势区域处理
        viewBinding.readerView.onTapAreaListener = { area ->
            handleTapGesture(area)
        }
        
        // 章节切换请求处理
        viewBinding.readerView.onChapterChangeRequestListener = { delta ->
            switchChapterBy(delta)
        }

        // 修正：动态更新 readerView 的 headerHeight，考虑 paddingTop 已经避开了状态栏
        viewBinding.infoBar.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            if (viewBinding.infoBar.isVisible) {
                val totalH = bottom - top
                // infoBar 已经包含了针对状态栏的 padding，而 readerView 也已经设置了对应的 paddingTop
                // 所以这里只传递“额外”占用的高度
                viewBinding.readerView.setHeaderHeight(maxOf(0, totalH - viewBinding.readerView.paddingTop))
            } else {
                viewBinding.readerView.setHeaderHeight(0)
            }
        }

        viewBinding.readerView.updateSettings(readerSettings)
        updateDualPageMode()
        updateFullscreenMode()
        updateReadingStatusVisibility()

        // 初始状态：显示工具栏
        isUiVisible = true
        viewBinding.appbarTop.isVisible = true
        viewBinding.toolbarDocked.isVisible = true

        loadChapters()
    }

    private fun setupImageHeaders() {
        viewBinding.readerView.imageHeadersProvider = when (manga.source) {
            MangaParserSource.BILINOVEL -> { _ ->
                mapOf(
                    "Referer" to "https://www.bilinovel.com/",
                    "Origin" to "https://www.bilinovel.com",
                    "Accept-Encoding" to "identity",
                )
            }
            else -> null
        }
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

        viewBinding.toolbarDocked.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = systemBars.bottom
            rightMargin = systemBars.right
            leftMargin = systemBars.left
        }

        viewBinding.infoBar.updatePadding(top = systemBars.top)

        // 核心修复：保持 readerView 的 padding 稳定，避免在 UI 切换时发生“压缩”
        // 只避开系统栏（状态栏和导航栏），而不避开随 UI 浮动的工具栏
        viewBinding.readerView.updatePadding(
            top = systemBars.top,
            left = systemBars.left,
            right = systemBars.right,
            bottom = systemBars.bottom
        )

        // innerInsets 用于给 CoordinatorLayout 子视图的行为提供信息（如果使用了 appbar_scrolling_view_behavior）
        // 这里手动设置以告知顶栏和底栏避开的空间
        val innerInsets = Insets.of(
            systemBars.left,
            if (viewBinding.appbarTop.isVisible) viewBinding.appbarTop.height else systemBars.top,
            systemBars.right,
            if (viewBinding.toolbarDocked.isVisible) viewBinding.toolbarDocked.height else systemBars.bottom,
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
                val percent = getCurrentProgressRatio()
                
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

    /**
     * Restore reading progress from Intent or history
     * Requirements 7.5, 7.6: Restore last read chapter and page position, fallback to first chapter if not found
     * 
     * 修复：改进章节ID查找逻辑，支持数据库映射的章节ID
     */
    private suspend fun restoreReadingProgress(originalChapters: List<MangaChapter>) {
        android.util.Log.d("NovelReaderActivity", "=== restoreReadingProgress() ===")
        
        // Get ReaderState from Intent
        val state = intent.getParcelableExtraCompat<org.skepsun.kototoro.reader.ui.ReaderState>(
            org.skepsun.kototoro.core.nav.ReaderIntent.EXTRA_STATE
        )
        
        // Get history
        val history = historyRepository.getOne(manga)
        
        // Determine which chapter ID to restore
        val targetChapterId = when {
            state != null && state.chapterId != 0L -> {
                android.util.Log.d("NovelReaderActivity", "Using chapter ID from Intent: ${state.chapterId}")
                state.chapterId
            }
            history != null -> {
                android.util.Log.d("NovelReaderActivity", "Using chapter ID from history: ${history.chapterId}")
                history.chapterId
            }
            else -> {
                android.util.Log.d("NovelReaderActivity", "No saved state, starting from first chapter")
                null
            }
        }
        
        if (targetChapterId != null) {
            // 直接在展开后的章节列表中查找
            var targetIndex = chapters.indexOfFirst { it.id == targetChapterId }
            
            android.util.Log.d("NovelReaderActivity", "Looking for chapter ID $targetChapterId in ${chapters.size} expanded chapters")
            android.util.Log.d("NovelReaderActivity", "Found at index: $targetIndex")
            
            // 如果没找到，尝试通过数据库映射查找
            if (targetIndex < 0) {
                android.util.Log.d("NovelReaderActivity", "Chapter not found in expanded list, checking database mappings")
                
                // 检查是否是EPUB内部章节
                val mapping = try {
                    epubChapterMappingDao.getById(targetChapterId)
                } catch (e: Exception) {
                    android.util.Log.e("NovelReaderActivity", "Failed to query chapter mapping", e)
                    null
                }
                
                if (mapping != null) {
                    android.util.Log.d("NovelReaderActivity", "Found EPUB mapping: parentId=${mapping.parentChapterId}, index=${mapping.chapterIndex}")
                    
                    // 在展开后的章节列表中查找匹配的章节
                    // 匹配条件：URL包含相同的父章节ID和章节索引
                    val targetUrl = "#chapter/${mapping.chapterIndex}"
                    targetIndex = chapters.indexOfFirst { chapter ->
                        chapter.url.contains(targetUrl) && 
                        (chapter.id == targetChapterId || 
                         chapter.url.contains("chapter_${mapping.parentChapterId}.epub") ||
                         chapter.url.contains("/${mapping.parentChapterId}/"))
                    }
                    
                    android.util.Log.d("NovelReaderActivity", "Searching for URL pattern: $targetUrl, found at index: $targetIndex")
                }
            }
            
            // Requirement 7.6: If chapter ID is not found, fallback to first chapter
            if (targetIndex >= 0) {
                currentChapterIndex = targetIndex
                // Restore page position/ratio from history if available
                desiredProgressRatio = history?.percent?.takeIf { it > 0f }
                currentPageIndex = history?.page ?: state?.page ?: 0
                android.util.Log.d("NovelReaderActivity", "✅ Restored to chapter index $targetIndex (ID: ${chapters[targetIndex].id}), page $currentPageIndex")
                android.util.Log.d("NovelReaderActivity", "   Chapter title: ${chapters[targetIndex].title}")
                android.util.Log.d("NovelReaderActivity", "   Chapter URL: ${chapters[targetIndex].url.takeLast(50)}")
            } else {
                android.util.Log.w("NovelReaderActivity", "❌ Chapter ID $targetChapterId not found in local chapters")
                
                // Try to find the chapter in original manga (online source)
                var onlineChapter = originalManga?.chapters?.find { it.id == targetChapterId }
                if (onlineChapter == null && originalManga != null) {
                    // 若原始信息没有完整目录，尝试拉取远端详情
                    runCatching {
                        val onlineRepo = mangaRepositoryFactory.create(originalManga!!.source)
                        val details = runBlocking { onlineRepo.getDetails(originalManga!!) }
                        originalManga = details
                        onlineChapter = details.chapters?.find { it.id == targetChapterId }
                    }.onFailure {
                        android.util.Log.w("NovelReaderActivity", "Failed to fetch online details for missing chapter", it)
                    }
                }
                if (onlineChapter != null) {
                    // Found in online source - add it to chapters list and switch repository
                    android.util.Log.d("NovelReaderActivity", "✅ Found chapter in online source: ${onlineChapter.title}")
                    
                    // Create a new repository for online source
                    repository = mangaRepositoryFactory.create(originalManga!!.source)
                    
                    // Add the online chapter to our list temporarily
                    chapters = chapters + onlineChapter
                    currentChapterIndex = chapters.size - 1
                    currentPageIndex = 0
                    
                    android.widget.Toast.makeText(
                        this@NovelReaderActivity,
                        "正在从网络加载未下载的章节",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                } else {
                    android.util.Log.w("NovelReaderActivity", "Chapter not found in online source either, falling back to first chapter")
                    currentChapterIndex = 0
                    currentPageIndex = 0
                    
                    // Show toast to inform user that the chapter is not found
                    if (state != null && state.chapterId != 0L) {
                        android.widget.Toast.makeText(
                            this@NovelReaderActivity,
                            "该章节尚未下载，请先下载后再阅读",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        } else {
            // No saved state, start from first chapter
            currentChapterIndex = 0
            currentPageIndex = 0
        }
        
        // Clear Intent state to avoid reusing it
        intent.removeExtra(org.skepsun.kototoro.core.nav.ReaderIntent.EXTRA_STATE)
    }

    private fun loadChapters() {
        android.util.Log.d("NovelReaderActivity", "=== loadChapters() called ===")
        android.util.Log.d("NovelReaderActivity", "Current manga.chapters: ${manga.chapters?.size ?: 0} chapters")
        android.util.Log.d("NovelReaderActivity", "Manga is local: ${manga.isLocal}")
        
        lifecycleScope.launch {
            try {
                showLoading(true)
                
                android.util.Log.d("NovelReaderActivity", "Manga chapters null or empty: ${manga.chapters.isNullOrEmpty()}, isLocal: ${manga.isLocal}")

                // For local manga, ALWAYS reload from repository to get fresh chapter list from index
                val details = if (manga.isLocal || manga.chapters.isNullOrEmpty()) {
                    android.util.Log.d("NovelReaderActivity", "Loading chapters from repository (local=${manga.isLocal}, empty=${manga.chapters.isNullOrEmpty()})...")
                    val startTime = System.currentTimeMillis()
                    // 核心修复：如果是远程解析器，优先使用带有原始远程 URL 的 originalManga 获取详情，避免 SSL 错误
                    val result = if (repository !is org.skepsun.kototoro.local.novel.LocalNovelRepository && originalManga != null) {
                        android.util.Log.d("NovelReaderActivity", "Using originalManga for remote details fetch: ${originalManga!!.url}")
                        repository.getDetails(originalManga!!)
                    } else {
                        repository.getDetails(manga)
                    }
                    val elapsed = System.currentTimeMillis() - startTime
                    android.util.Log.d("NovelReaderActivity", "✅ Loaded from repository in ${elapsed}ms, got ${result.chapters?.size ?: 0} chapters")
                    result
                } else {
                    android.util.Log.d("NovelReaderActivity", "✅ Using chapters from manga object (${manga.chapters?.size} chapters) - SKIPPED NETWORK")
                    manga
                }

                // 如果本地启动且 originalManga 还没有目录，尝试拉取远端目录用于占位（历史入口常见）
                if (manga.isLocal && originalManga?.chapters.isNullOrEmpty() && originalManga != null) {
                    runCatching {
                        val onlineRepo = mangaRepositoryFactory.create(originalManga!!.source)
                        val remoteDetails = runBlocking { onlineRepo.getDetails(originalManga!!) }
                        originalManga = remoteDetails
                        android.util.Log.d(
                            "NovelReaderActivity",
                            "Fetched remote details for originalManga, chapters=${remoteDetails.chapters?.size ?: 0}",
                        )
                    }.onFailure {
                        android.util.Log.w("NovelReaderActivity", "Failed to fetch remote details for originalManga", it)
                    }
                }
                
                // 若当前是本地且有原始远端目录，合并远端目录与本地章节，保留未下载章节的占位
                var originalChapters = details.chapters.orEmpty()
                if (manga.isLocal && originalManga?.chapters != null) {
                    val remoteChapters = originalManga?.chapters.orEmpty()
                    val localById = originalChapters.associateBy { it.id }
                    val merged = remoteChapters.map { localById[it.id] ?: it }.toMutableList()
                    // 添加仅本地存在的章节（例如本地缓存的特殊章节）
                    val remoteIds = remoteChapters.map { it.id }.toSet()
                    originalChapters.filterNot { it.id in remoteIds }.forEach { merged.add(it) }
                    originalChapters = merged
                    android.util.Log.d(
                        "NovelReaderActivity",
                        "Merged remote chapters (${remoteChapters.size}) with local overrides (${localById.size}), result=${originalChapters.size}",
                    )
                }
                android.util.Log.d("NovelReaderActivity", "Original chapters count: ${originalChapters.size}")
                
                // 本地 CBZ/ZIP 或无 EPUB 迹象时直接使用原章节，避免错误展开
                val hasLikelyEpub = !manga.isLocal && originalChapters.any {
                    val url = it.url.lowercase()
                    url.contains(".epub") || url.contains("epub://")
                }
                if (hasLikelyEpub) {
                    android.util.Log.d("NovelReaderActivity", "Expanding EPUB chapters...")
                    chapters = expandEpubChapters(originalChapters)
                    android.util.Log.d("NovelReaderActivity", "After expansion: ${chapters.size} chapters")
                } else {
                    android.util.Log.d("NovelReaderActivity", "Skip EPUB expansion (local or no epub hints)")
                    chapters = originalChapters
                }
                
                // Restore reading progress (Requirements 7.5, 7.6)
                // Priority: Intent parameters > History > First chapter
                restoreReadingProgress(originalChapters)
                
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
        android.util.Log.d("NovelReaderActivity", "=== loadChapter($index) called ===")
        android.util.Log.d("NovelReaderActivity", "Total chapters available: ${chapters.size}")
        
        val chapter = chapters.getOrNull(index)
        if (chapter == null) {
            android.util.Log.e("NovelReaderActivity", "❌ Chapter at index $index not found")
            return
        }
        
        lifecycleScope.launch {
            try {
                // Determine if we need to show the loading spinner
                val needsLoading = !chapter.url.startsWith("epub://") && 
                                  !chapter.url.contains("#chapter/") &&
                                  !novelContentLoader.isCached(chapter)
                
                if (needsLoading) {
                    showLoading(true)
                }
                
                // Check if this is an EPUB internal chapter (Requirement 6.1, 6.2, 6.3)
                if (chapter.url.contains("#chapter/") || chapter.url.startsWith("epub://")) {
                    android.util.Log.d("NovelReaderActivity", "Detected EPUB internal chapter: ${chapter.url}")
                    
                    // Show progress indicator for large files (Requirement 11.4)
                    viewBinding.toolbar.subtitle = "正在加载EPUB章节..."
                    
                    // Load EPUB internal chapter using the dedicated loader
                    val result = epubInternalChapterLoader.loadEpubInternalChapter(chapter)
                    
                    viewBinding.toolbar.subtitle = chapter.title
                    
                    result.onSuccess { loadResult ->
                        android.util.Log.d("NovelReaderActivity", "Successfully loaded EPUB internal chapter")
                        showLoading(false)  // Dismiss loading indicator
                        renderChapterWithEpubInfo(chapter, loadResult.content, loadResult.epubFile, loadResult.chapterHref)
                    }.onFailure { error ->
                        android.util.Log.e("NovelReaderActivity", "Failed to load EPUB internal chapter", error)
                        showLoading(false)  // Dismiss loading indicator even on error
                        // Display user-friendly error message (Requirement 6.7)
                        val errorMessage = when {
                            error.message?.contains("not found") == true -> "EPUB文件未找到，可能已被删除"
                            error.message?.contains("out of bounds") == true -> "章节索引无效"
                            error.message?.contains("Invalid chapter URL") == true -> "章节URL格式错误"
                            error.message?.contains("Failed to parse") == true -> "EPUB文件解析失败"
                            else -> "无法加载EPUB章节: ${error.message}"
                        }
                        viewBinding.toastView.showTemporary(errorMessage, 3000L)
                    }
                    
                    return@launch
                }
                
                // Use chapter's source to get correct repository (local or online)
                // This allows seamless switching between downloaded and online chapters
                val chapterRepo = mangaRepositoryFactory.create(chapter.source)
                
                // 1. FAST PATH: Check if already cached
                if (novelContentLoader.isCached(chapter)) {
                    android.util.Log.d("NovelReaderActivity", "✅ Cache hit for chapter, loading directly")
                    val plainText = novelContentLoader.loadChapterContent(chapterRepo, chapter)
                    showLoading(false)
                    renderChapter(chapter, plainText)
                    preloadNextChapter(index + 1)
                    return@launch
                }

                // 2. SLOW PATH: Need to fetch from network
                val isLocalChapter = chapter.source is org.skepsun.kototoro.core.model.LocalNovelSource || 
                                    chapter.source is org.skepsun.kototoro.core.model.LocalMangaSource
                android.util.Log.d("NovelReaderActivity", "Cache miss, using repository for source: ${chapter.source}, isLocal: $isLocalChapter")
                
                val pages = chapterRepo.getPages(chapter)
                
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
                
                android.util.Log.d("NovelReaderActivity", "Processing as regular chapter with cache")
                // 使用已经解析的 pages 加载，避免二次抓取
                val plainText = novelContentLoader.loadChapterContent(chapterRepo, chapter, pages)
                
                android.util.Log.d("NovelReaderActivity", "Plain text length: ${plainText.length}")
                
                showLoading(false)
                renderChapter(chapter, plainText)
                
                // Preload next chapter
                preloadNextChapter(index + 1)
            } catch (e: Exception) {
                android.util.Log.e("NovelReaderActivity", "Failed to load chapter", e)
                showLoading(false)
                showError("加载章节失败: ${e.message}")
            }
        }
    }

    private fun preloadNextChapter(nextIndex: Int) {
        val nextChapter = chapters.getOrNull(nextIndex) ?: return
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (novelContentLoader.isCached(nextChapter)) return@launch
                
                android.util.Log.d("NovelReaderActivity", "Preloading next chapter: ${nextChapter.title}")
                val chapterRepo = mangaRepositoryFactory.create(nextChapter.source)
                novelContentLoader.loadChapterContent(chapterRepo, nextChapter)
                android.util.Log.d("NovelReaderActivity", "Successfully preloaded: ${nextChapter.title}")
            } catch (e: Exception) {
                android.util.Log.w("NovelReaderActivity", "Failed to preload chapter: ${nextChapter.title}", e)
            }
        }
    }

    /**
     * 加载EPUB内容
     * 
     * 支持两种URL格式：
     * 1. epub://{manga_id}/chapter/{index} - 新架构（NoveliaWenku, Z-Library等）
     * 2. file://path#chapter/N - 旧架构（向后兼容）
     */
    private suspend fun loadEpubContent(chapter: MangaChapter): String? {
        return try {
            android.util.Log.d("NovelReaderActivity", "Loading EPUB content for: ${chapter.title}, URL: ${chapter.url}")
            
            // 检查URL格式
            if (chapter.url.startsWith("epub://")) {
                // 新架构：使用EpubInternalChapterLoader
                android.util.Log.d("NovelReaderActivity", "Using EpubInternalChapterLoader for new architecture")
                
                val epubFileManager = org.skepsun.kototoro.local.epub.EpubFileManagerImpl()
                val database = org.skepsun.kototoro.core.db.MangaDatabase(this)
                val epubChapterMappingDao = database.getEpubChapterMappingDao()
                val epubInternalChapterLoader = org.skepsun.kototoro.reader.novel.EpubInternalChapterLoader(
                    context = this,
                    epubFileManager = epubFileManager,
                    epubChapterMappingDao = epubChapterMappingDao
                )
                
                    val result = epubInternalChapterLoader.loadEpubInternalChapter(chapter)
                    
                    if (result.isSuccess) {
                        val loadResult = result.getOrNull()
                        android.util.Log.d("NovelReaderActivity", "EPUB content loaded successfully, length: ${loadResult?.content?.length}")
                        // 直接用带 href 的渲染，保证图片相对路径解析正确
                        if (loadResult != null) {
                            renderChapterWithEpubInfo(
                                chapter = chapter,
                                text = loadResult.content,
                                epubFile = loadResult.epubFile,
                                chapterHref = loadResult.chapterHref,
                            )
                        }
                        return null
                    } else {
                        val error = result.exceptionOrNull()
                        android.util.Log.e("NovelReaderActivity", "Failed to load EPUB content: ${error?.message}", error)
                        return null
                    }
            } else {
                // 旧架构：使用EpubReader直接读取
                android.util.Log.d("NovelReaderActivity", "Using EpubReader for legacy architecture")
                
                val chapterUri = android.net.Uri.parse(chapter.url)
                val epubReader = org.skepsun.kototoro.local.epub.EpubReaderImpl()
                val epubContent = epubReader.readEpubFromUri(chapterUri)
                
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
                return fullContent
            }
        } catch (e: Exception) {
            android.util.Log.e("NovelReaderActivity", "Failed to load EPUB content", e)
            null
        }
    }

    /**
     * Render EPUB chapter with proper file info for image loading
     * 
     * @param chapter The chapter being rendered
     * @param text The chapter content
     * @param epubFile The EPUB file (optional, will be looked up if not provided)
     * @param chapterHref The chapter's path in EPUB (e.g., "OEBPS/Text/content_1.html")
     */
    private fun renderChapterWithEpubInfo(
        chapter: MangaChapter, 
        text: String,
        epubFile: java.io.File? = null,
        chapterHref: String? = null
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                var epubFileToSet: java.io.File? = epubFile
                var chapterPathToSet: String? = chapterHref
                
                // If EPUB file info not provided, try to extract from chapter URL
                if (epubFileToSet == null) {
                    if (chapter.url.startsWith("epub://")) {
                        // New architecture: epub://{manga_id}/chapter/{index}
                        val regex = Regex("epub://(-?\\d+)/chapter/(\\d+)")
                        val match = regex.matchEntire(chapter.url)
                        if (match != null) {
                            val mangaId = match.groupValues[1].toLong()
                            val chapterIndex = match.groupValues[2].toInt()
                            
                            val allMappings = epubChapterMappingDao.findByMangaId(mangaId)
                            val sortedMappings = allMappings.sortedWith(compareBy({ it.parentChapterId }, { it.chapterIndex }))
                            val mapping = sortedMappings.getOrNull(chapterIndex)
                            
                            if (mapping != null) {
                                val file = java.io.File(mapping.epubFilePath)
                                if (file.exists()) {
                                    epubFileToSet = file
                                    // Use actual chapter href if available
                                    if (chapterPathToSet == null) {
                                        // Try real href from cached EPUB
                                        val cached = epubContentCache.get(epubFileToSet!!)
                                        chapterPathToSet = cached?.chapters?.getOrNull(chapterIndex)?.href
                                            ?: "OEBPS/Text/chapter${mapping.chapterIndex}.xhtml"
                                    }
                                    android.util.Log.d("NovelReaderActivity", "Found EPUB file for epub:// URL: ${file.name}")
                                }
                            }
                        }
                    } else if (chapter.url.contains("#chapter/")) {
                        // Legacy architecture: file://path#chapter/N or local://path#chapter/N
                        val regex = Regex("#chapter/(\\d+)")
                        val match = regex.find(chapter.url)
                        if (match != null) {
                            val chapterIndex = match.groupValues[1].toInt()
                            
                            // Try to find EPUB file from database mapping
                            val allMappings = epubChapterMappingDao.findByMangaId(manga.id)
                            val sortedMappings = allMappings.sortedWith(compareBy({ it.parentChapterId }, { it.chapterIndex }))
                            val mapping = sortedMappings.getOrNull(chapterIndex)
                            
                            if (mapping != null) {
                                val file = java.io.File(mapping.epubFilePath)
                                if (file.exists()) {
                                    epubFileToSet = file
                                    // Use actual chapter href if available
                                    if (chapterPathToSet == null) {
                                        val cached = epubContentCache.get(epubFileToSet!!)
                                        chapterPathToSet = cached?.chapters?.getOrNull(chapterIndex)?.href
                                            ?: "OEBPS/Text/chapter${mapping.chapterIndex}.xhtml"
                                    }
                                    android.util.Log.d("NovelReaderActivity", "Found EPUB file for #chapter/ URL: ${file.name}")
                                }
                            }
                        }
                    }
                }
                
                // Render on main thread with EPUB info
                withContext(Dispatchers.Main) {
                    viewBinding.toolbar.subtitle = chapter.title ?: getString(R.string.unnamed_chapter)
                    
                    val contentToDisplay = if (text.isBlank()) {
                        android.util.Log.w("NovelReaderActivity", "Chapter content is blank")
                        "章节内容为空\n\n请检查网络连接或稍后重试"
                    } else {
                        text
                    }
                    
                    android.util.Log.d("NovelReaderActivity", "Content length: ${contentToDisplay.length}, first 100 chars: ${contentToDisplay.take(100)}")
                    
                    // Set EPUB info BEFORE setting content
                    viewBinding.readerView.setEpubInfo(epubFileToSet, chapterPathToSet)
                    android.util.Log.d("NovelReaderActivity", "Set EPUB info: file=${epubFileToSet?.name}, chapterPath=$chapterPathToSet")
                    
                    // Then set content
                    val savedPageIndex = currentPageIndex
                    val needsPageRestore = savedPageIndex != 0
                    val initialRatio = desiredProgressRatio

                    viewBinding.readerView.setContent(
                        content = contentToDisplay,
                        resetPage = true,
                        suppressNotification = needsPageRestore,
                        initialPageIndex = savedPageIndex,
                        initialProgressRatio = initialRatio
                    )
                    
                    android.util.Log.d("NovelReaderActivity", "Content set successfully with initial page: $savedPageIndex")
                    
                    if (needsPageRestore) {
                        viewBinding.readerView.postDelayed({
                            viewBinding.readerView.resumePageChangeNotification()
                        }, 100)
                    }
                    
                    currentPageIndex = 0
                    desiredProgressRatio = null
                    updateNavigationButtons()
                    
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
                }
            } catch (e: Exception) {
                android.util.Log.e("NovelReaderActivity", "Failed to render EPUB chapter", e)
                withContext(Dispatchers.Main) {
                    showError("渲染失败: ${e.message}")
                }
            }
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
            
            // 设置EPUB文件信息（用于提取图片）- 必须在setContent之前完成
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    var epubFileToSet: java.io.File? = null
                    var chapterPathToSet: String? = null
                    
                    if (chapter.url.startsWith("epub://")) {
                        // 新架构：从数据库查找EPUB文件
                        val regex = Regex("epub://(-?\\d+)/chapter/(\\d+)")
                        val match = regex.matchEntire(chapter.url)
                        if (match != null) {
                            val mangaId = match.groupValues[1].toLong()
                            val chapterIndex = match.groupValues[2].toInt()
                            
                            val allMappings = epubChapterMappingDao.findByMangaId(mangaId)
                            val sortedMappings = allMappings.sortedWith(compareBy({ it.parentChapterId }, { it.chapterIndex }))
                            val mapping = sortedMappings.getOrNull(chapterIndex)
                            
                            if (mapping != null) {
                                val epubFile = java.io.File(mapping.epubFilePath)
                                if (epubFile.exists()) {
                                    epubFileToSet = epubFile
                                    // 从EPUB中获取实际的章节路径
                                    // 需要读取EPUB的spine来获取章节的href
                                    try {
                                        val epubReader = org.skepsun.kototoro.local.epub.EpubReaderImpl(epubContentCache)
                                        val epubContent = epubReader.readEpub(epubFile)
                                        
                                        // 获取章节的实际路径（从EPUB的spine中）
                                        // 这里我们使用一个简化的方法：假设章节路径格式为 OEBPS/Text/chapterN.xhtml
                                        // 实际应该从epublib的Book对象中获取
                                        chapterPathToSet = "OEBPS/Text/chapter${mapping.chapterIndex}.xhtml"
                                        
                                        android.util.Log.d("NovelReaderActivity", "Prepared EPUB info: file=${epubFile.name}, chapterPath=$chapterPathToSet")
                                    } catch (e: Exception) {
                                        android.util.Log.e("NovelReaderActivity", "Failed to read EPUB for chapter path", e)
                                        // 使用fallback路径
                                        chapterPathToSet = null
                                        android.util.Log.d("NovelReaderActivity", "Will use null chapterPath (fallback)")
                                    }
                                } else {
                                    android.util.Log.w("NovelReaderActivity", "EPUB file not found: ${epubFile.absolutePath}")
                                }
                            } else {
                                android.util.Log.w("NovelReaderActivity", "No mapping found for chapter index: $chapterIndex")
                            }
                        }
                    }

                    // 本地小说 CBZ/ZIP 不再走 EPUB 通道，避免误判

                    // 在主线程上设置EPUB信息并渲染内容
                    withContext(Dispatchers.Main) {
                        try {
                            // 先设置EPUB信息
                            viewBinding.readerView.setEpubInfo(epubFileToSet, chapterPathToSet)
                            android.util.Log.d("NovelReaderActivity", "Set EPUB info: file=${epubFileToSet?.name}, chapterPath=$chapterPathToSet")
                            
                            // 然后设置内容
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
                    android.util.Log.e("NovelReaderActivity", "Failed to prepare EPUB info", e)
                    // 即使失败也要尝试显示内容
                    withContext(Dispatchers.Main) {
                        try {
                            viewBinding.readerView.setEpubInfo(null, null)
                            viewBinding.readerView.setContent(
                                content = contentToDisplay,
                                resetPage = true,
                                suppressNotification = false,
                                initialPageIndex = 0
                            )
                        } catch (e2: Exception) {
                            android.util.Log.e("NovelReaderActivity", "Failed to set content in fallback", e2)
                            showError("显示内容失败: ${e2.message}")
                        }
                    }
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
                    android.util.Log.d("NovelReaderActivity", "Found local EPUB file: ${localFile.absolutePath}")
                    
                    // 检查缓存 (cache is now managed by EpubReaderImpl)
                    val cachedContent = epubContentCache.get(localFile)
                    if (cachedContent != null) {
                        android.util.Log.d("NovelReaderActivity", "Using cached EPUB content for: ${localFile.absolutePath}")
                        return@withContext cachedContent
                    }
                    
                    // 读取并缓存 (cache is automatically managed by EpubReaderImpl)
                    val epubReader = org.skepsun.kototoro.local.epub.EpubReaderImpl(epubContentCache)
                    val content = epubReader.readEpub(localFile)
                    if (content != null) {
                        android.util.Log.d("NovelReaderActivity", "Loaded and cached EPUB content for: ${localFile.absolutePath}")
                    }
                    return@withContext content
                }
                
                // 如果是file://协议，尝试直接读取
                if (url.startsWith("file://")) {
                    val filePath = url.substring(7)
                    val file = java.io.File(filePath)
                    if (file.exists()) {
                        android.util.Log.d("NovelReaderActivity", "Loading from file path: $filePath")
                        
                        // 检查缓存 (cache is now managed by EpubReaderImpl)
                        val cachedContent = epubContentCache.get(file)
                        if (cachedContent != null) {
                            android.util.Log.d("NovelReaderActivity", "Using cached EPUB content for: $filePath")
                            return@withContext cachedContent
                        }
                        
                        // 读取并缓存 (cache is automatically managed by EpubReaderImpl)
                        val epubReader = org.skepsun.kototoro.local.epub.EpubReaderImpl(epubContentCache)
                        val content = epubReader.readEpub(file)
                        if (content != null) {
                            android.util.Log.d("NovelReaderActivity", "Loaded and cached EPUB content for: $filePath")
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
     * 
     * 修复：
     * 1. 使用卷名作为前缀，避免章节名重复
     * 2. 使用数据库映射的章节ID，确保与详情页一致
     */
    private suspend fun expandEpubChapters(originalChapters: List<MangaChapter>): List<MangaChapter> {
        // 本地 CBZ 小说直接返回，避免误判为 EPUB
        if (manga.isLocal) {
            android.util.Log.d("NovelReaderActivity", "expandEpubChapters: manga is local, skip expansion")
            return originalChapters
        }

        val expandedChapters = mutableListOf<MangaChapter>()
        
        android.util.Log.d("NovelReaderActivity", "expandEpubChapters: Processing ${originalChapters.size} chapters")
        
        for (chapter in originalChapters) {
            try {
                // 快速检查：如果URL不像EPUB文件，直接跳过
                // EPUB文件通常以.epub结尾，或者URL中包含epub关键字
                val isLikelyEpub = chapter.url.contains(".epub", ignoreCase = true) ||
                    chapter.url.contains("epub", ignoreCase = true)
                
                if (!isLikelyEpub) {
                    // 不像EPUB文件，直接添加，跳过网络请求
                    android.util.Log.d("NovelReaderActivity", "Chapter '${chapter.title}': Not EPUB-like URL, skipping check")
                    expandedChapters.add(chapter)
                    continue
                }
                
                // 可能是EPUB，需要检查
                android.util.Log.d("NovelReaderActivity", "Chapter '${chapter.title}': Checking if EPUB...")
                val pages = repository.getPages(chapter)
                android.util.Log.d("NovelReaderActivity", "Chapter '${chapter.title}': ${pages.size} pages, preview='${pages.firstOrNull()?.preview}'")
                
                if (pages.size == 1 && pages[0].preview == "EPUB") {
                    android.util.Log.d("NovelReaderActivity", "Found EPUB chapter: ${chapter.title}, ID=${chapter.id}, expanding...")
                    
                    // 首先尝试从数据库读取已保存的章节映射
                    val dbMappings = try {
                        epubChapterMappingDao.getByParentId(chapter.id)
                    } catch (e: Exception) {
                        android.util.Log.e("NovelReaderActivity", "Failed to query chapter mappings", e)
                        emptyList()
                    }
                    
                    if (dbMappings.isNotEmpty()) {
                        // 使用数据库中的映射
                        android.util.Log.d("NovelReaderActivity", "Using ${dbMappings.size} chapters from database")
                        
                        for (mapping in dbMappings.sortedBy { it.chapterIndex }) {
                            val internalChapter = MangaChapter(
                                id = mapping.internalChapterId,
                                title = mapping.chapterTitle,  // 不添加卷名前缀，详情页已经分组
                                number = chapter.number + mapping.chapterIndex,
                                volume = chapter.volume,
                                url = "${pages[0].url}#chapter/${mapping.chapterIndex}",
                                scanlator = chapter.scanlator,
                                uploadDate = mapping.createdAt,
                                branch = chapter.branch,
                                source = chapter.source,
                            )
                            expandedChapters.add(internalChapter)
                        }
                        android.util.Log.d("NovelReaderActivity", "Expanded EPUB chapter from database into ${dbMappings.size} internal chapters")
                    } else {
                        // 数据库中没有映射，尝试读取EPUB内容
                        android.util.Log.d("NovelReaderActivity", "No database mappings found, reading EPUB content")
                        val epubContent = loadEpubContentFromUrl(pages[0].url, chapter)
                        
                        if (epubContent != null && epubContent.chapters.isNotEmpty()) {
                            // 不过滤，显示所有章节
                            epubContent.chapters.forEachIndexed { chapterIndex, epubChapter ->
                                val generatedUrl = "${pages[0].url}#chapter/$chapterIndex"
                                // 使用与DownloadWorker相同的ID生成算法
                                val internalChapterId = chapter.id + (chapterIndex * 1000000L) + 1
                                
                                val internalChapter = MangaChapter(
                                    id = internalChapterId,
                                    title = epubChapter.title,  // 不添加卷名前缀，详情页已经分组
                                    number = chapter.number + chapterIndex,
                                    volume = chapter.volume,
                                    url = generatedUrl,
                                    scanlator = chapter.scanlator,
                                    uploadDate = chapter.uploadDate,
                                    branch = chapter.branch,
                                    source = chapter.source,
                                )
                                expandedChapters.add(internalChapter)
                                
                                // 打印章节映射信息（仅前5个和后5个）
                                if (chapterIndex < 5 || chapterIndex >= epubContent.chapters.size - 5) {
                                    android.util.Log.d("NovelReaderActivity", "  Chapter mapping: index=$chapterIndex, id=$internalChapterId, title='${internalChapter.title}', url='${internalChapter.url.takeLast(15)}'")
                                }
                            }
                            android.util.Log.d("NovelReaderActivity", "Expanded EPUB chapter into ${epubContent.chapters.size} internal chapters")
                        } else {
                            // 读取失败，保留原始章节
                            android.util.Log.w("NovelReaderActivity", "Failed to expand EPUB chapter, keeping original")
                            expandedChapters.add(chapter)
                        }
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
        val ratio = getCurrentProgressRatio()
        val sliderValue = if (total > 1) {
            (ratio * (total - 1)).toInt().coerceIn(0, total - 1)
        } else 0
        viewBinding.actionsView.setSliderValue(sliderValue, total - 1)
        viewBinding.actionsView.isSliderEnabled = total > 1
        // 页码显示使用当前分页（单页或双页 spread）
        viewBinding.actionsView.setPageLabel(page + 1, total)
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
            
            // 只有在工具栏隐藏、全屏模式且开启了阅读状态显示时，才显示 infoBar
            viewBinding.infoBar.isGone = visible || !readerSettings.showReadingStatus
            viewBinding.infoBar.isTimeVisible = readerSettings.enableFullscreen
            
            // 根据全屏设置和 UI 可见性控制系统 UI
            // 如果不是全屏模式，总是显示状态栏
            // 如果是全屏模式，只在 UI 可见时显示状态栏
            val shouldShowSystemUi = !readerSettings.enableFullscreen || visible
            systemUiController.setSystemUiVisible(shouldShowSystemUi)
            
            // 更新系统栏颜色
            updateSystemBarsColors()
            
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
        // 保存当前进度（按字符比例），用于横竖屏/单双页切换后的恢复
        val ratio = viewBinding.readerView.getProgressRatio()
        val currentStart = viewBinding.readerView.getCurrentCharOffset()
        val currentEnd = viewBinding.readerView.getCurrentPageEndOffset()
        val wasDual = viewBinding.readerView.isDualPage()

        updateDualPageMode()

        val nowDual = viewBinding.readerView.isDualPage()
        // 若从单页->双页，保证旧页首字符出现；双页->单页，保证旧页尾字符出现
        if (wasDual != nowDual) {
            if (!wasDual && nowDual) {
                viewBinding.readerView.setPendingOffset(currentStart, biasToEnd = false)
            } else if (wasDual && !nowDual) {
                viewBinding.readerView.setPendingOffset((currentEnd - 1).coerceAtLeast(0), biasToEnd = true)
            } else {
                viewBinding.readerView.setPendingProgressRatio(ratio)
            }
        } else {
            viewBinding.readerView.setPendingProgressRatio(ratio)
        }
        android.util.Log.d("NovelReaderActivity", "Configuration changed, saved ratio: $ratio, start=$currentStart, end=$currentEnd, wasDual=$wasDual, nowDual=$nowDual")
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
     * 更新阅读状态信息栏
     */
    private fun updateReadingStatus(page: Int, total: Int) {
        val chapter = chapters.getOrNull(currentChapterIndex) ?: return
        val uiState = org.skepsun.kototoro.reader.ui.pager.ReaderUiState(
            mangaName = manga.title,
            chapter = chapter,
            chapterIndex = currentChapterIndex,
            chaptersTotal = chapters.size,
            currentPage = page,
            totalPages = total,
            percent = getCurrentProgressRatio(),
            incognito = false // TODO: 获取无痕模式状态
        )
        viewBinding.infoBar.update(uiState)
        
        // 更新历史记录使用实际页数（单页计数）
        val actualPage = viewBinding.readerView.getCurrentPage()
        val actualTotal = viewBinding.readerView.getTotalPages()
        updateHistory(actualPage, actualTotal)
    }

    /**
     * 更新历史记录和阅读进度
     */
    private fun updateHistory(page: Int, total: Int) {
        val chapter = chapters.getOrNull(currentChapterIndex) ?: return
        
        lifecycleScope.launch {
            try {
                // 确保保存历史时包含完整目录：优先使用当前内存中的章节（已合并本地/远端），并修正来源
                val fixedSource = originalManga?.source ?: manga.source
                val fixedUrl = originalManga?.url ?: manga.url
                val baseManga = if (chapters.isNotEmpty()) {
                    manga.copy(chapters = chapters, source = fixedSource, url = fixedUrl)
                } else if (manga.chapters.isNullOrEmpty()) {
                    try {
                        repository.getDetails(manga).copy(source = fixedSource, url = fixedUrl)
                    } catch (e: Exception) {
                        android.util.Log.e("NovelReaderActivity", "Failed to get manga details for history", e)
                        manga.copy(source = fixedSource, url = fixedUrl)
                    }
                } else {
                    manga.copy(source = fixedSource, url = fixedUrl)
                }

                // 合并远端目录与当前章节（避免历史保存时只有已下载章节）
                val mergedForHistory = if (baseManga.isLocal && originalManga?.chapters != null) {
                    val remoteChapters = originalManga?.chapters.orEmpty()
                    val localById = baseManga.chapters.orEmpty().associateBy { it.id }
                    val merged = remoteChapters.map { localById[it.id] ?: it }.toMutableList()
                    val remoteIds = remoteChapters.map { it.id }.toSet()
                    baseManga.chapters.orEmpty().filterNot { it.id in remoteIds }.forEach { merged.add(it) }
                    baseManga.copy(chapters = merged, source = originalManga!!.source)
                } else {
                    baseManga
                }
                val mangaWithChapters = if (originalManga != null) {
                    originalManga!!.copy(
                        chapters = mergedForHistory.chapters,
                        source = originalManga!!.source,
                        url = originalManga!!.url,
                    )
                } else {
                    mergedForHistory
                }
                
                // 如果仍然没有章节信息，不保存历史
                if (mangaWithChapters.chapters.isNullOrEmpty()) {
                    android.util.Log.w("NovelReaderActivity", "Cannot save history: no chapters available")
                    return@launch
                }
                
                // 计算当前章节在所有章节中的进度（使用字符偏移更精确，兼容单/双页）
                val chapterProgress = getCurrentProgressRatio()
                
                // 计算总体阅读进度
                // 进度 = (已读完章节数 + 当前章节进度) / 总章节数
                val totalProgress = if (chapters.isNotEmpty()) {
                    (currentChapterIndex + chapterProgress) / chapters.size
                } else {
                    0f
                }.coerceIn(0f, 1f)
                
                android.util.Log.d("NovelReaderActivity", "Updating history: chapter=$currentChapterIndex/${chapters.size}, page=$page/$total, progress=$totalProgress")
                
                // 创建 ReaderState（仍沿用页码字段，历史恢复时会重新分页）
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
     * 基于字符偏移的精确进度（0f-1f）
     */
    private fun getCurrentProgressRatio(): Float {
        val totalChars = viewBinding.readerView.getChapterLength()
        if (totalChars <= 0) return 0f
        val offset = viewBinding.readerView.getCurrentCharOffset().coerceIn(0, totalChars)
        return (offset.toFloat() / totalChars).coerceIn(0f, 1f)
    }

    /**
     * 更新阅读状态可见性
     */
    private fun updateReadingStatusVisibility() {
        viewBinding.infoBar.isGone = isUiVisible || !readerSettings.showReadingStatus
        // 更新阅读状态背景可见性
        viewBinding.infoBar.drawBackground = !readerSettings.isReadingStatusTransparent
        
        // 当 infoBar 可见性变化时，其 layout 监听器会更新 readerView 的 headerHeight
        // 当 isUiVisible 变化时，requestApplyInsets 会更新 readerView 的 padding
        
        // 刷新一次阅读状态
        val displayPage = viewBinding.readerView.getDisplayPageIndex()
        val displayTotal = viewBinding.readerView.getDisplayPageCount()
        updateReadingStatus(displayPage, displayTotal)
    }

    /**
     * 更新状态栏和底部导航栏颜色
     */
    private fun updateSystemBarsColors() {
        val typedValue = android.util.TypedValue()
        
        // 状态栏
        if (!readerSettings.enableFullscreen) {
            // 在非全屏模式下，给状态栏添加背景色，避免内容透过状态栏显示
            theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)
            window.statusBarColor = typedValue.data
        } else {
            // 全屏模式下保持透明
            window.statusBarColor = android.graphics.Color.TRANSPARENT
        }

        // 导航栏
        if (isUiVisible) {
            // 当底栏工具栏可见时，导航栏颜色应与其一致
            theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)
            val navColor = typedValue.data
            window.navigationBarColor = navColor
            
            // 确保系统栏对比度正确（深色/浅色模式）
            val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightNavigationBars = !isDark
            }
            
            // 确保导航栏背景生效
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        } else {
            // 否则保持透明（沉浸式）
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }
    }

    /**
     * 更新全屏模式
     */
    private fun updateFullscreenMode() {
        // 根据全屏设置和当前 UI 状态控制系统 UI
        val shouldShowSystemUi = !readerSettings.enableFullscreen || isUiVisible
        systemUiController.setSystemUiVisible(shouldShowSystemUi)
        
        // 更新系统栏颜色
        updateSystemBarsColors()
        
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
                    updateReadingStatusVisibility()
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
