package org.skepsun.kototoro.details.ui

import android.app.assist.AssistContent
import android.content.Context
import android.os.Bundle
import android.text.SpannedString
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import androidx.core.text.method.LinkMovementMethodCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.view.updatePaddingRelative
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.transition.TransitionManager
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowRgb565
import coil3.request.crossfade
import coil3.request.lifecycle
import coil3.request.transformations
import coil3.size.Precision
import coil3.transform.RoundedCornersTransformation
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import org.skepsun.kototoro.R
import org.skepsun.kototoro.bookmarks.domain.Bookmark
import org.skepsun.kototoro.core.image.CoilMemoryCacheKey
import org.skepsun.kototoro.core.model.FavouriteCategory
import org.skepsun.kototoro.core.model.LocalMangaSource
import org.skepsun.kototoro.core.model.UnknownMangaSource
import org.skepsun.kototoro.core.model.getSummary
import org.skepsun.kototoro.core.model.getTitle
import org.skepsun.kototoro.core.jsonsource.JsonMangaSource
import org.skepsun.kototoro.core.jsonsource.JsonSourceManager
import org.skepsun.kototoro.core.model.titleResId
import org.skepsun.kototoro.core.nav.ReaderIntent
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.os.AppShortcutManager
import org.skepsun.kototoro.core.parser.favicon.faviconUri
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.ui.BaseActivity
import org.skepsun.kototoro.core.ui.BaseListAdapter
import org.skepsun.kototoro.core.ui.dialog.buildAlertDialog
import org.skepsun.kototoro.core.ui.image.FaviconDrawable
import org.skepsun.kototoro.core.ui.image.TextDrawable
import org.skepsun.kototoro.core.ui.image.TextViewTarget
import org.skepsun.kototoro.core.ui.list.OnListItemClickListener
import org.skepsun.kototoro.core.ui.sheet.BottomSheetCollapseCallback
import org.skepsun.kototoro.core.ui.util.MenuInvalidator
import org.skepsun.kototoro.core.ui.util.ReversibleActionObserver
import org.skepsun.kototoro.core.ui.widgets.ChipsView
import org.skepsun.kototoro.core.util.FileSize
import org.skepsun.kototoro.core.util.LocaleUtils
import org.skepsun.kototoro.core.util.ext.consume
import org.skepsun.kototoro.core.util.ext.copyToClipboard
import org.skepsun.kototoro.core.util.ext.drawableStart
import org.skepsun.kototoro.core.util.ext.end
import org.skepsun.kototoro.core.util.ext.enqueueWith
import org.skepsun.kototoro.core.util.ext.getQuantityStringSafe
import org.skepsun.kototoro.core.util.ext.isAnimationsEnabled
import org.skepsun.kototoro.core.util.ext.isTextTruncated
import org.skepsun.kototoro.core.util.ext.joinToStringWithLimit
import org.skepsun.kototoro.core.util.ext.mangaSourceExtra
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.core.util.ext.observeEvent
import org.skepsun.kototoro.core.util.ext.parentView
import org.skepsun.kototoro.core.util.ext.setTooltipCompat
import org.skepsun.kototoro.core.util.ext.start
import org.skepsun.kototoro.core.util.ext.textAndVisible
import org.skepsun.kototoro.core.util.ext.toUriOrNull
import org.skepsun.kototoro.core.util.FoldableUtils
import org.skepsun.kototoro.databinding.ActivityDetailsBinding
import org.skepsun.kototoro.databinding.LayoutDetailsTableBinding
import org.skepsun.kototoro.details.data.MangaDetails
import org.skepsun.kototoro.details.data.ReadingTime
import org.skepsun.kototoro.details.service.MangaPrefetchService
import org.skepsun.kototoro.details.ui.model.ChapterListItem
import org.skepsun.kototoro.details.ui.model.HistoryInfo
import org.skepsun.kototoro.details.ui.scrobbling.ScrobblingItemDecoration
import org.skepsun.kototoro.details.ui.scrobbling.ScrollingInfoAdapter
import org.skepsun.kototoro.download.ui.worker.DownloadStartedObserver
import org.skepsun.kototoro.list.domain.ReadingProgress
import org.skepsun.kototoro.list.ui.adapter.ListItemType
import org.skepsun.kototoro.list.ui.adapter.mangaGridItemAD
import org.skepsun.kototoro.list.ui.model.ListModel
import org.skepsun.kototoro.list.ui.model.MangaListModel
import org.skepsun.kototoro.list.ui.size.StaticItemSizeResolver
import org.skepsun.kototoro.main.ui.owners.BottomSheetOwner
import org.skepsun.kototoro.parsers.model.ContentRating
import org.skepsun.kototoro.parsers.model.Manga
import org.skepsun.kototoro.parsers.model.MangaTag
import org.skepsun.kototoro.parsers.util.ifNullOrEmpty
import org.skepsun.kototoro.parsers.util.nullIfEmpty
import org.skepsun.kototoro.parsers.util.toTitleCase
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblingInfo
import javax.inject.Inject
import kotlin.math.roundToInt
import com.google.android.material.R as materialR

@AndroidEntryPoint
class DetailsActivity :
	BaseActivity<ActivityDetailsBinding>(),
	View.OnClickListener,
	View.OnLayoutChangeListener,
	ViewTreeObserver.OnDrawListener,
	ChipsView.OnChipClickListener,
	OnListItemClickListener<Bookmark>,
	SwipeRefreshLayout.OnRefreshListener,
	AuthorSpan.OnAuthorClickListener,
	BottomSheetOwner {

	@Inject
	lateinit var shortcutManager: AppShortcutManager

	@Inject
	lateinit var coil: ImageLoader

	@Inject
	lateinit var settings: AppSettings

	@Inject
	lateinit var jsonSourceManager: JsonSourceManager

	private val viewModel: DetailsViewModel by viewModels()
	private lateinit var menuProvider: DetailsMenuProvider
	private lateinit var infoBinding: LayoutDetailsTableBinding
	private var isFoldUnfolded = false

	override val bottomSheet: View?
		get() = viewBinding.containerBottomSheet

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityDetailsBinding.inflate(layoutInflater))
		infoBinding = LayoutDetailsTableBinding.bind(viewBinding.root)
		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = false)
		supportActionBar?.setDisplayShowTitleEnabled(false)
		viewBinding.chipFavorite.setOnClickListener(this)
		infoBinding.textViewLocal.setOnClickListener(this)
		infoBinding.textViewSource.setOnClickListener(this)
		viewBinding.imageViewCover.setOnClickListener(this)
		viewBinding.textViewTitle.setOnClickListener(this)
		viewBinding.buttonDescriptionMore.setOnClickListener(this)
		viewBinding.buttonScrobblingMore.setOnClickListener(this)
		viewBinding.buttonRelatedMore.setOnClickListener(this)
		viewBinding.textViewDescription.addOnLayoutChangeListener(this)
		viewBinding.swipeRefreshLayout.setOnRefreshListener(this)
		viewBinding.textViewDescription.viewTreeObserver.addOnDrawListener(this)
		infoBinding.textViewAuthor.movementMethod = LinkMovementMethodCompat.getInstance()
		viewBinding.textViewDescription.movementMethod = LinkMovementMethodCompat.getInstance()
		viewBinding.chipsTags.onChipClickListener = this
		TitleScrollCoordinator(viewBinding.textViewTitle).attach(viewBinding.scrollView)
		if (settings.isDescriptionExpanded) {
			viewBinding.textViewDescription.maxLines = Int.MAX_VALUE - 1
		}
		viewBinding.containerBottomSheet?.let { sheet ->
			sheet.setOnClickListener(this)
			sheet.addOnLayoutChangeListener(this)
			onBackPressedDispatcher.addCallback(BottomSheetCollapseCallback(sheet))
			BottomSheetBehavior.from(sheet).addBottomSheetCallback(
				DetailsBottomSheetCallback(viewBinding.swipeRefreshLayout, checkNotNull(viewBinding.navbarDim)),
			)
		}

		val appRouter = router
		viewModel.mangaDetails.filterNotNull().observe(this, ::onMangaUpdated)
		viewModel.coverUrl.observe(this, ::loadCover)
		viewModel.onMangaRemoved.observeEvent(this, ::onMangaRemoved)
		viewModel.onError
			.filterNot { appRouter.isChapterPagesSheetShown() }
			.observeEvent(this, DetailsErrorObserver(this, viewModel, exceptionResolver))
		viewModel.onActionDone
			.filterNot { appRouter.isChapterPagesSheetShown() }
			.observeEvent(this, ReversibleActionObserver(viewBinding.scrollView))
		combine(viewModel.historyInfo, viewModel.isLoading, ::Pair).observe(this) {
			onHistoryChanged(it.first, it.second)
		}
		viewModel.isLoading.observe(this, ::onLoadingStateChanged)
		viewModel.scrobblingInfo.observe(this, ::onScrobblingInfoChanged)
		viewModel.localSize.observe(this, ::onLocalSizeChanged)
		viewModel.relatedManga.observe(this, ::onRelatedMangaChanged)
		viewModel.favouriteCategories.observe(this, ::onFavoritesChanged)
		val menuInvalidator = MenuInvalidator(this)
		viewModel.isStatsAvailable.observe(this, menuInvalidator)
		viewModel.remoteManga.observe(this, menuInvalidator)
		viewModel.tags.observe(this, ::onTagsChanged)
		viewModel.chapters.observe(this, PrefetchObserver(this))
		viewModel.onDownloadStarted
			.filterNot { appRouter.isChapterPagesSheetShown() }
			.observeEvent(this, DownloadStartedObserver(viewBinding.scrollView))
		menuProvider = DetailsMenuProvider(
			activity = this,
			viewModel = viewModel,
			snackbarHost = viewBinding.scrollView,
			appShortcutManager = shortcutManager,
		)
		addMenuProvider(menuProvider)
		
		// 观察折叠屏状态变化
		observeFoldableState()
	}

	override fun onProvideAssistContent(outContent: AssistContent) {
		super.onProvideAssistContent(outContent)
		viewModel.getMangaOrNull()?.publicUrl?.toUriOrNull()?.let { outContent.webUri = it }
	}

	override fun isNsfwContent(): Flow<Boolean> = viewModel.manga.map { it?.contentRating == ContentRating.ADULT }

	override fun onClick(v: View) {
		when (v.id) {
			R.id.textView_source -> {
				val manga = viewModel.getMangaOrNull() ?: return
				router.openList(manga.source, null, null)
			}

			R.id.textView_local -> {
				val manga = viewModel.getMangaOrNull() ?: return
				router.showLocalInfoDialog(manga)
			}

			R.id.chip_favorite -> {
				val manga = viewModel.getMangaOrNull() ?: return
				router.showFavoriteDialog(manga)
			}

			R.id.imageView_cover -> {
				val manga = viewModel.getMangaOrNull() ?: return
				router.openImage(
					url = viewModel.coverUrl.value ?: return,
					source = manga.source,
					preview = CoilMemoryCacheKey.from(viewBinding.imageViewCover),
					anchor = v,
				)
			}

			R.id.button_description_more -> {
				val tv = viewBinding.textViewDescription
				if (tv.context.isAnimationsEnabled) {
					tv.parentView?.let {
						TransitionManager.beginDelayedTransition(it)
					}
				}
				if (tv.maxLines in 1 until Integer.MAX_VALUE) {
					tv.maxLines = Integer.MAX_VALUE
				} else {
					tv.maxLines = resources.getInteger(R.integer.details_description_lines)
				}
			}

			R.id.button_scrobbling_more -> {
				router.showScrobblingSelectorSheet(
					manga = viewModel.getMangaOrNull() ?: return,
					scrobblerService = viewModel.scrobblingInfo.value.firstOrNull()?.scrobbler,
				)
			}

			R.id.button_related_more -> {
				val manga = viewModel.getMangaOrNull() ?: return
				router.openRelated(manga)
			}

			R.id.textView_title -> {
				val title = viewModel.getMangaOrNull()?.title?.nullIfEmpty() ?: return
				buildAlertDialog(this) {
					setMessage(title)
					setNegativeButton(R.string.close, null)
					setPositiveButton(androidx.preference.R.string.copy) { _, _ ->
						copyToClipboard(getString(R.string.content_type_manga), title)
					}
				}.show()
			}
		}
	}

	override fun onAuthorClick(author: String) {
		router.showAuthorDialog(author, viewModel.getMangaOrNull()?.source ?: return)
	}

	override fun onChipClick(chip: Chip, data: Any?) {
		val tag = data as? MangaTag ?: return
		router.showTagDialog(tag)
	}

	override fun onItemClick(item: Bookmark, view: View) {
		router.openReader(ReaderIntent.Builder(view.context).bookmark(item).incognito().build())
		Toast.makeText(view.context, R.string.incognito_mode, Toast.LENGTH_SHORT).show()
	}

	override fun onRefresh() {
		viewModel.reload()
	}

	override fun onDraw() {
		viewBinding.run {
			buttonDescriptionMore.isVisible = textViewDescription.maxLines == Int.MAX_VALUE ||
				textViewDescription.isTextTruncated
		}
	}

	override fun onLayoutChange(
		v: View?,
		left: Int,
		top: Int,
		right: Int,
		bottom: Int,
		oldLeft: Int,
		oldTop: Int,
		oldRight: Int,
		oldBottom: Int
	) {
		with(viewBinding) {
			containerBottomSheet?.let { sheet ->
				val peekHeight = BottomSheetBehavior.from(sheet).peekHeight
				if (scrollView.paddingBottom != peekHeight) {
					scrollView.updatePadding(bottom = peekHeight)
				}
			}
		}
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val typeMask = WindowInsetsCompat.Type.systemBars()
		val barsInsets = insets.getInsets(typeMask)
		if (viewBinding.cardChapters != null) {
			// landscape
			viewBinding.cardChapters?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
				topMargin = barsInsets.top + resources.getDimensionPixelOffset(R.dimen.grid_spacing_outer)
				marginEnd = barsInsets.end(v) + resources.getDimensionPixelOffset(R.dimen.side_card_offset)
				bottomMargin = barsInsets.bottom + resources.getDimensionPixelOffset(R.dimen.side_card_offset)
			}
			viewBinding.scrollView.updatePaddingRelative(
				bottom = barsInsets.bottom,
				start = barsInsets.start(v),
			)
			viewBinding.appbar.updatePaddingRelative(
				start = barsInsets.start(v),
			)
			return insets.consume(v, typeMask, bottom = true, end = true)
		} else {
			viewBinding.navbarDim?.updateLayoutParams {
				height = barsInsets.bottom
			}
			return insets
		}
	}

	private fun onFavoritesChanged(categories: Set<FavouriteCategory>) {
		val chip = viewBinding.chipFavorite
		chip.setChipIconResource(if (categories.isEmpty()) R.drawable.ic_heart_outline else R.drawable.ic_heart)
		chip.text = if (categories.isEmpty()) {
			getString(R.string.add_to_favourites)
		} else {
			categories.joinToStringWithLimit(this, FAV_LABEL_LIMIT) { it.title }
		}
	}

	private fun onLocalSizeChanged(size: Long) {
		if (size == 0L) {
			infoBinding.textViewLocal.isVisible = false
			infoBinding.textViewLocalLabel.isVisible = false
		} else {
			infoBinding.textViewLocal.text = FileSize.BYTES.format(this, size)
			infoBinding.textViewLocal.isVisible = true
			infoBinding.textViewLocalLabel.isVisible = true
		}
	}

	private fun onRelatedMangaChanged(related: List<MangaListModel>) {
		if (related.isEmpty()) {
			viewBinding.groupRelated.isVisible = false
			return
		}
		val rv = viewBinding.recyclerViewRelated

		@Suppress("UNCHECKED_CAST")
		val adapter = (rv.adapter as? BaseListAdapter<ListModel>) ?: BaseListAdapter<ListModel>()
			.addDelegate(
				ListItemType.MANGA_GRID,
				mangaGridItemAD(
					sizeResolver = StaticItemSizeResolver(resources.getDimensionPixelSize(R.dimen.smaller_grid_width)),
				) { item, view ->
					router.openDetails(item.toMangaWithOverride())
				},
			).also { rv.adapter = it }
		adapter.items = related
		viewBinding.groupRelated.isVisible = true
	}

	private fun onLoadingStateChanged(isLoading: Boolean) {
		viewBinding.swipeRefreshLayout.isRefreshing = isLoading
	}

	private fun onScrobblingInfoChanged(scrobblings: List<ScrobblingInfo>) {
		var adapter = viewBinding.recyclerViewScrobbling.adapter as? ScrollingInfoAdapter
		viewBinding.groupScrobbling.isGone = scrobblings.isEmpty()
		if (adapter != null) {
			adapter.items = scrobblings
		} else {
			adapter = ScrollingInfoAdapter(router)
			adapter.items = scrobblings
			viewBinding.recyclerViewScrobbling.adapter = adapter
			viewBinding.recyclerViewScrobbling.addItemDecoration(ScrobblingItemDecoration())
		}
	}

	private fun onMangaUpdated(details: MangaDetails) {
		val manga = details.toManga()
		with(viewBinding) {
			textViewTitle.text = manga.title
			textViewSubtitle.textAndVisible = manga.altTitles.joinToString("\n")
			textViewNsfw16.isVisible = manga.contentRating == ContentRating.SUGGESTIVE
			textViewNsfw18.isVisible = manga.contentRating == ContentRating.ADULT
			textViewDescription.text = details.description.ifNullOrEmpty { getString(R.string.no_description) }
		}
		with(infoBinding) {
			val translation = details.getLocale()
			infoBinding.textViewTranslation.textAndVisible = translation?.getDisplayLanguage(translation)
				?.toTitleCase(translation)
			infoBinding.textViewTranslation.drawableStart = translation?.let {
				LocaleUtils.getEmojiFlag(it)
			}?.let {
				TextDrawable.compound(infoBinding.textViewTranslation, it)
			}
			infoBinding.textViewTranslationLabel.isVisible = infoBinding.textViewTranslation.isVisible
			textViewAuthor.textAndVisible = manga.getAuthorsString()
			textViewAuthorLabel.isVisible = textViewAuthor.isVisible
			if (manga.hasRating) {
				ratingBarRating.rating = manga.rating * ratingBarRating.numStars
				ratingBarRating.isVisible = true
				textViewRatingLabel.isVisible = true
			} else {
				ratingBarRating.isVisible = false
				textViewRatingLabel.isVisible = false
			}
			manga.state?.let { state ->
				textViewState.textAndVisible = resources.getString(state.titleResId)
				textViewStateLabel.isVisible = textViewState.isVisible
			} ?: run {
				textViewState.isVisible = false
				textViewStateLabel.isVisible = false
			}

			if (manga.source == LocalMangaSource || manga.source == UnknownMangaSource) {
				textViewSource.isVisible = false
				textViewSourceLabel.isVisible = false
			} else {
				val initialTitle = manga.source.getTitle(this@DetailsActivity)
				textViewSource.textAndVisible = initialTitle
				textViewSource.setTooltipCompat(manga.source.getSummary(this@DetailsActivity))
				textViewSourceLabel.isVisible = textViewSource.isVisible == true
				if ((initialTitle == getString(R.string.unknown) || manga.source.name.startsWith("JSON_")) &&
					manga.source !is JsonMangaSource
				) {
					// 某些场景 seed.source 仍是裸 ID，这里兜底同步显示数据库中的显示名
					lifecycleScope.launch {
						jsonSourceManager.getById(manga.source.name)?.name?.takeIf { it.isNotBlank() }?.let { displayName ->
							textViewSource.textAndVisible = displayName
							textViewSource.setTooltipCompat(displayName)
							textViewSourceLabel.isVisible = textViewSource.isVisible == true
						}
					}
				}
			}
			val faviconPlaceholderFactory = FaviconDrawable.Factory(R.style.FaviconDrawable_Chip)
			ImageRequest.Builder(this@DetailsActivity)
				.data(manga.source.faviconUri())
				.lifecycle(this@DetailsActivity)
				.crossfade(false)
				.precision(Precision.EXACT)
				.size(resources.getDimensionPixelSize(materialR.dimen.m3_chip_icon_size))
				.target(TextViewTarget(textViewSource, Gravity.START))
				.placeholder(faviconPlaceholderFactory)
				.error(faviconPlaceholderFactory)
				.fallback(faviconPlaceholderFactory)
				.mangaSourceExtra(manga.source)
				.transformations(RoundedCornersTransformation(resources.getDimension(R.dimen.chip_icon_corner)))
				.allowRgb565(true)
				.enqueueWith(coil)
		}
		title = manga.title
		invalidateOptionsMenu()
	}

	private fun onMangaRemoved(manga: Manga) {
		Toast.makeText(
			this,
			getString(R.string._s_deleted_from_local_storage, manga.title),
			Toast.LENGTH_SHORT,
		).show()
		finishAfterTransition()
	}

	private fun onHistoryChanged(info: HistoryInfo, isLoading: Boolean) = with(infoBinding) {
		textViewChapters.text = when {
			isLoading -> getString(R.string.loading_)
			info.currentChapter >= 0 -> getString(
				R.string.chapter_d_of_d,
				info.currentChapter + 1,
				info.totalChapters,
			).withEstimatedTime(info.estimatedTime)

			info.totalChapters == 0 -> getString(R.string.no_chapters)
			info.totalChapters == -1 -> getString(R.string.error_occurred)
			else -> resources.getQuantityStringSafe(R.plurals.chapters, info.totalChapters, info.totalChapters)
				.withEstimatedTime(info.estimatedTime)
		}
		textViewProgress.textAndVisible = if (info.percent <= 0f) {
			null
		} else {
			val displayPercent = if (ReadingProgress.isCompleted(info.percent)) 100 else (info.percent * 100f).toInt()
			getString(R.string.percent_string_pattern, displayPercent.toString())
		}

		progress.setProgressCompat(
			(progress.max * info.percent.coerceIn(0f, 1f)).roundToInt(),
			true,
		)
		textViewProgressLabel.isVisible = info.history != null
		textViewProgress.isVisible = info.history != null
		progress.isVisible = info.history != null
	}

	private fun onTagsChanged(tags: Collection<ChipsView.ChipModel>) {
		viewBinding.chipsTags.isVisible = tags.isNotEmpty()
		viewBinding.chipsTags.setChips(tags)
	}

	private fun loadCover(imageUrl: String?) {
		android.util.Log.d("DetailsActivity", "loadCover: $imageUrl")
		viewBinding.imageViewCover.setImageAsync(imageUrl, viewModel.getMangaOrNull())
	}

	/**
	 * 观察折叠屏状态变化并调整布局
	 */
	private fun observeFoldableState() {
		val foldableState = FoldableUtils.observeFoldableState(this, this)
		
		lifecycleScope.launch {
			foldableState.collect { unfolded ->
				if (unfolded != isFoldUnfolded) {
					isFoldUnfolded = unfolded
					adjustLayoutForFoldableState()
				}
			}
		}
	}

	/**
	 * 根据折叠屏状态调整布局
	 */
    private fun adjustLayoutForFoldableState() {
        // 仅折叠屏展开状态下，如果当前布局缺少侧栏容器则重建以切换到双栏资源
        if (isFoldUnfolded && viewBinding.cardChapters == null) {
            recreate()
            return
        }

        viewBinding.root.requestLayout()
    }

	private fun String.withEstimatedTime(time: ReadingTime?): String {
		if (time == null) {
			return this
		}
		val timeFormatted = time.formatShort(resources)
		return getString(R.string.chapters_time_pattern, this, timeFormatted)
	}

	private fun Manga.getAuthorsString(): SpannedString? {
		if (authors.isEmpty()) {
			return null
		}
		return buildSpannedString {
			authors.forEach { a ->
				if (a.isNotEmpty()) {
					if (isNotEmpty()) {
						append(", ")
					}
					inSpans(AuthorSpan(this@DetailsActivity)) {
						append(a)
					}
				}
			}
		}.nullIfEmpty()
	}

	private class PrefetchObserver(
		private val context: Context,
	) : FlowCollector<List<ChapterListItem>?> {

		private var isCalled = false

		override suspend fun emit(value: List<ChapterListItem>?) {
			if (value.isNullOrEmpty()) {
				return
			}
			if (!isCalled) {
				isCalled = true
				val item = value.find { it.isCurrent } ?: value.first()
				MangaPrefetchService.prefetchPages(context, item.chapter)
			}
		}
	}

	companion object {

		private const val FAV_LABEL_LIMIT = 16
	}
}
