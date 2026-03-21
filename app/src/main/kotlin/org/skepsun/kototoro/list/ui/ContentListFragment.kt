package org.skepsun.kototoro.list.ui

import android.content.res.ColorStateList
import android.os.Build
import androidx.annotation.CallSuper
import androidx.appcompat.view.ActionMode
import androidx.collection.ArraySet
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import coil3.ImageLoader
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.os.Bundle
import org.skepsun.kototoro.R
import org.skepsun.kototoro.alternatives.ui.AutoFixService
import org.skepsun.kototoro.core.model.FavouriteCategory
import org.skepsun.kototoro.core.exceptions.resolve.ExceptionResolver
import org.skepsun.kototoro.core.exceptions.resolve.SnackbarErrorObserver
import org.skepsun.kototoro.core.model.isLocal
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.ListMode
import org.skepsun.kototoro.core.ui.BaseFragment
import org.skepsun.kototoro.core.ui.dialog.buildAlertDialog
import org.skepsun.kototoro.core.ui.list.FitHeightGridLayoutManager
import org.skepsun.kototoro.core.ui.list.FitHeightLinearLayoutManager
import org.skepsun.kototoro.core.ui.list.ListSelectionController
import org.skepsun.kototoro.core.ui.list.PaginationScrollListener
import org.skepsun.kototoro.core.ui.list.fastscroll.FastScroller
import org.skepsun.kototoro.core.ui.util.RecyclerViewOwner
import org.skepsun.kototoro.core.ui.util.ReversibleActionObserver
import org.skepsun.kototoro.core.ui.widgets.TipView
import org.skepsun.kototoro.core.util.ShareHelper
import org.skepsun.kototoro.core.util.ext.addMenuProvider
import org.skepsun.kototoro.core.util.ext.consumeAll
import org.skepsun.kototoro.core.util.ext.findAppCompatDelegate
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.core.util.ext.observeEvent
import org.skepsun.kototoro.core.util.ext.viewLifecycleScope
import org.skepsun.kototoro.core.util.FoldableUtils
import org.skepsun.kototoro.databinding.FragmentListBinding
import org.skepsun.kototoro.explore.ui.model.BrowseGroupTab
import org.skepsun.kototoro.explore.ui.model.SourceTag
import org.skepsun.kototoro.list.domain.ListFilterOption
import org.skepsun.kototoro.main.ui.SearchBarFilterMenuProvider
import org.skepsun.kototoro.main.ui.owners.AppBarOwner
import org.skepsun.kototoro.list.domain.QuickFilterListener
import org.skepsun.kototoro.list.ui.adapter.ListItemType
import org.skepsun.kototoro.list.ui.adapter.ContentListAdapter
import org.skepsun.kototoro.list.ui.adapter.ContentListListener
import org.skepsun.kototoro.list.ui.adapter.TypedListSpacingDecoration
import org.skepsun.kototoro.list.ui.model.ListHeader
import org.skepsun.kototoro.list.ui.model.ListModel
import org.skepsun.kototoro.list.ui.model.ContentListModel
import org.skepsun.kototoro.list.ui.size.DynamicItemSizeResolver
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentTag
import org.skepsun.kototoro.search.ui.ContentListActivity
import javax.inject.Inject

@AndroidEntryPoint
abstract class ContentListFragment :
	BaseFragment<FragmentListBinding>(),
	PaginationScrollListener.Callback,
	ContentListListener,
	RecyclerViewOwner,
	SwipeRefreshLayout.OnRefreshListener,
	ListSelectionController.Callback,
	FastScroller.FastScrollListener,
	AppBarLayout.OnOffsetChangedListener,
	SearchBarFilterMenuProvider.Callback {

	@Inject
	lateinit var coil: ImageLoader

	@Inject
	lateinit var settings: AppSettings

	private var listAdapter: ContentListAdapter? = null
	private var paginationListener: PaginationScrollListener? = null
	private var selectionController: ListSelectionController? = null
	private var spanResolver: GridSpanResolver? = null
	private val spanSizeLookup = SpanSizeLookup()
	open val isSwipeRefreshEnabled = true

	private var isFoldUnfolded = false
	private var filterMenuProvider: SearchBarFilterMenuProvider? = null

	// Track chip IDs for filter groups
	private val categoryChipIds = mutableMapOf<Long, Int>()
	private val contentTypeChipIds = mutableMapOf<BrowseGroupTab, Int>()
	private val sourceTagChipIds = mutableMapOf<SourceTag, Int>()

	protected abstract val viewModel: ContentListViewModel

	protected val selectedItemsIds: Set<Long>
		get() = selectionController?.snapshot().orEmpty()

	protected val selectedItems: Set<Content>
		get() = collectSelectedItems()

	override val recyclerView: RecyclerView?
		get() = viewBinding?.recyclerView

	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	) = FragmentListBinding.inflate(inflater, container, false)

	protected open fun sourceTagChipEntries(): List<SourceTag> = SourceTag.entries.toList()

		override fun onViewBindingCreated(binding: FragmentListBinding, savedInstanceState: Bundle?) {
			super.onViewBindingCreated(binding, savedInstanceState)
			listAdapter = onCreateAdapter()
			spanResolver = GridSpanResolver(binding.root.resources)
			// 预先应用持久化的网格大小，确保重新进入页面后立即按用户选择的大小布局
			spanResolver?.setGridSize(settings.gridSize / 100f, binding.recyclerView)
			selectionController = ListSelectionController(
				appCompatDelegate = checkNotNull(findAppCompatDelegate()),
				decoration = ContentSelectionDecoration(binding.root.context),
				registryOwner = this,
				callback = this,
			)
		paginationListener = PaginationScrollListener(4, this)
		with(binding.recyclerView) {
			setHasFixedSize(true)
			adapter = listAdapter
			checkNotNull(selectionController).attachToRecyclerView(this)
			addItemDecoration(TypedListSpacingDecoration(context, false))
			addOnScrollListener(checkNotNull(paginationListener))
			fastScroller.setFastScrollListener(this@ContentListFragment)
		}
		with(binding.swipeRefreshLayout) {
			setOnRefreshListener(this@ContentListFragment)
			isEnabled = isSwipeRefreshEnabled
		}
		addMenuProvider(ContentListMenuProvider(this))

		viewModel.listMode.observe(viewLifecycleOwner, ::onListModeChanged)
		viewModel.gridScale.observe(viewLifecycleOwner, ::onGridScaleChanged)
		viewModel.isLoading.observe(viewLifecycleOwner, ::onLoadingStateChanged)
		viewModel.content.observe(viewLifecycleOwner, ::onListChanged)
		// Pass exceptionResolver and onResolved callback so CF errors can be resolved and retried
		viewModel.onError.observeEvent(
			viewLifecycleOwner, 
			SnackbarErrorObserver(
				host = binding.recyclerView, 
				fragment = this,
				resolver = exceptionResolver,
				onResolved = { resolved -> if (resolved) viewModel.onRetry() }
			)
		)
		viewModel.onActionDone.observeEvent(viewLifecycleOwner, ReversibleActionObserver(binding.recyclerView))

		// Determine if we have a SearchBar available (main activity) and we're not a child
		// of a container fragment that already adds its own SearchBarFilterMenuProvider
		val isInsideContainer = parentFragment != null
		val searchBar = if (!isInsideContainer) {
			(activity as? AppBarOwner)?.appBar?.let { appBar ->
				appBar.findViewById<View>(R.id.search_bar)
			} ?: activity?.findViewById(R.id.search_bar)
		} else {
			null
		}

		if (searchBar != null) {
			// Mode A: Use SearchBar filter icons
			filterMenuProvider = SearchBarFilterMenuProvider(this, searchBar)
			addMenuProvider(filterMenuProvider!!)
			binding.filterScrollView.visibility = View.GONE
		} else if (!isInsideContainer) {
			// Mode B: Inline chip groups (standalone activity without SearchBar)
			rebuildContentTypeChips(binding)
			rebuildSourceTagChips(binding)
			binding.filterScrollView.visibility = View.VISIBLE
		} else {
			// Mode C: Inside a container (e.g. FavouritesContainerFragment) — no filters here
			binding.filterScrollView.visibility = View.GONE
		}

		viewModel.currentGroupTab.observe(viewLifecycleOwner) { tab ->
			filterMenuProvider?.updateIcons()
			if (searchBar == null && !isInsideContainer) {
				updateContentTypeChipsSelection(binding, tab)
				updateSourceTagChipsEnabled(binding, tab)
			}
		}
		viewModel.currentSourceTags.observe(viewLifecycleOwner) { tags ->
			filterMenuProvider?.updateIcons()
			if (searchBar == null && !isInsideContainer) {
				updateSourceTagChipsSelection(binding, tags)
				updateContentTypeChipsEnabled(binding, tags)
			}
		}
		viewModel.availableCategories.observe(viewLifecycleOwner) { categories ->
			rebuildCategoryChips(binding, categories)
		}
		viewModel.currentCategoryIds.observe(viewLifecycleOwner) { ids ->
			updateCategoryChipsSelection(binding, ids)
		}

		observeFoldableState()

		// Register for appbar offset changes for filter bar positioning
		val appBar = (activity as? AppBarOwner)?.appBar
		appBar?.addOnOffsetChangedListener(this)
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val typeMask = WindowInsetsCompat.Type.systemBars()
		val barsInsets = insets.getInsets(typeMask)
		val basePadding = v.resources.getDimensionPixelOffset(R.dimen.list_spacing_normal)

		// Apply side padding to tags container so they aren't cut off by notches/rounded corners
		viewBinding?.filterChipsContainer?.updatePadding(
			left = barsInsets.left + basePadding,
			right = barsInsets.right + basePadding,
		)

		viewBinding?.recyclerView?.setPadding(
			left = barsInsets.left + basePadding,
			top = basePadding,
			right = barsInsets.right + basePadding,
			bottom = barsInsets.bottom + basePadding,
		)
		return insets.consumeAll(typeMask)
	}

	override fun onOffsetChanged(appBarLayout: AppBarLayout?, verticalOffset: Int) {
		val binding = viewBinding ?: return
		val appBar = appBarLayout ?: return

		val insets = ViewCompat.getRootWindowInsets(binding.root)?.getInsets(WindowInsetsCompat.Type.statusBars())
		val statusBarHeight = insets?.top ?: 0

		// Pin tags exactly to the status bar once the appbar scrolls past it.
		val topPadding = Math.max(0, statusBarHeight - appBar.bottom)
		if (binding.filterScrollView.paddingTop != topPadding) {
			binding.filterScrollView.updatePadding(top = topPadding)
		}
	}

	override fun onDestroyView() {
		val appBar = (activity as? AppBarOwner)?.appBar
		appBar?.removeOnOffsetChangedListener(this)

		filterMenuProvider = null
		listAdapter = null
		paginationListener = null
		selectionController = null
		spanResolver = null
		spanSizeLookup.invalidateCache()
		categoryChipIds.clear()
		contentTypeChipIds.clear()
		sourceTagChipIds.clear()
		super.onDestroyView()
	}

	override fun onItemClick(item: ContentListModel, view: View) {
		if (selectionController?.onItemClick(item.id) != true) {
			val manga = item.toContentWithOverride()
			if ((activity as? ContentListActivity)?.showPreview(manga) != true) {
				router.openDetails(manga)
			}
		}
	}

	override fun onItemLongClick(item: ContentListModel, view: View): Boolean {
		return selectionController?.onItemLongClick(view, item.id) == true
	}

	override fun onItemContextClick(item: ContentListModel, view: View): Boolean {
		return selectionController?.onItemContextClick(view, item.id) == true
	}

	override fun onReadClick(manga: Content, view: View) {
		if (selectionController?.onItemClick(manga.id) != true) {
			router.openReader(manga)
		}
	}

	override fun onTagClick(manga: Content, tag: ContentTag, view: View) {
		if (selectionController?.onItemClick(manga.id) != true) {
			router.showTagDialog(tag)
		}
	}

	override fun onFilterClick(view: View?) {
		// Toggle filter bar visibility or scroll to top
		val isVisible = viewModel.isFilterBarVisible.value
		// If we want to toggle via a button, we could do it here. 
		// Or if this is called when clicking a filter icon.
		// For now, let's just scroll to top to show filters if they are hidden by scroll
		if (isVisible) {
			viewBinding?.recyclerView?.smoothScrollToPosition(0)
		}
	}

	override fun onListHeaderClick(item: ListHeader, view: View) {
		// Default implementation: do nothing
	}

	@CallSuper
	override fun onRefresh() {
		requireViewBinding().swipeRefreshLayout.isRefreshing = true
		viewModel.onRefresh()
	}

	private suspend fun onListChanged(list: List<ListModel>) {
		listAdapter?.emit(list)
		spanSizeLookup.invalidateCache()
		viewBinding?.recyclerView?.let {
			paginationListener?.postInvalidate(it)
		}
	}

	private fun resolveException(e: Throwable) {
		if (ExceptionResolver.canResolve(e)) {
			viewLifecycleScope.launch {
				if (exceptionResolver.resolve(e)) {
					viewModel.onRetry()
				}
			}
		} else {
			viewModel.onRetry()
		}
	}

	@CallSuper
	protected open fun onLoadingStateChanged(isLoading: Boolean) {
		requireViewBinding().swipeRefreshLayout.isEnabled = requireViewBinding().swipeRefreshLayout.isRefreshing ||
			isSwipeRefreshEnabled && !isLoading
		if (!isLoading) {
			requireViewBinding().swipeRefreshLayout.isRefreshing = false
		}
	}

	protected open fun onCreateAdapter(): ContentListAdapter {
		return ContentListAdapter(
			listener = this,
			sizeResolver = DynamicItemSizeResolver(resources, viewLifecycleOwner, settings, adjustWidth = false),
		)
	}

	override fun onFilterOptionClick(option: ListFilterOption) {
		selectionController?.clear()
		(viewModel as? QuickFilterListener)?.toggleFilterOption(option)
	}

	override fun onEmptyActionClick() = Unit

	override fun onPrimaryButtonClick(tipView: TipView) = Unit

	override fun onSecondaryButtonClick(tipView: TipView) = Unit

	override fun onRetryClick(error: Throwable) {
		resolveException(error)
	}

	private fun onGridScaleChanged(scale: Float) {
		spanSizeLookup.invalidateCache()
		spanResolver?.setGridSize(scale, requireViewBinding().recyclerView)
	}

	private fun onListModeChanged(mode: ListMode) {
		spanSizeLookup.invalidateCache()
		with(requireViewBinding().recyclerView) {
			removeOnLayoutChangeListener(spanResolver)
			when (mode) {
				ListMode.LIST -> {
					layoutManager = FitHeightLinearLayoutManager(context)
				}

				ListMode.DETAILED_LIST -> {
					layoutManager = FitHeightLinearLayoutManager(context)
				}

				ListMode.GRID -> {
					layoutManager = FitHeightGridLayoutManager(context, checkNotNull(spanResolver).spanCount).also {
						it.spanSizeLookup = spanSizeLookup
					}
					addOnLayoutChangeListener(spanResolver)
				}
			}
		}
	}

	@CallSuper
	override fun onPrepareActionMode(controller: ListSelectionController, mode: ActionMode?, menu: Menu): Boolean {
		val hasNoLocal = selectedItems.none { it.isLocal }
		val isSingleSelection = controller.count == 1
		menu.findItem(R.id.action_save)?.isVisible = hasNoLocal
		menu.findItem(R.id.action_fix)?.isVisible = hasNoLocal
		menu.findItem(R.id.action_edit_override)?.isVisible = isSingleSelection
		return super.onPrepareActionMode(controller, mode, menu)
	}

	override fun onCreateActionMode(
		controller: ListSelectionController,
		menuInflater: MenuInflater,
		menu: Menu
	): Boolean {
		return menu.hasVisibleItems()
	}

	override fun onActionItemClicked(controller: ListSelectionController, mode: ActionMode?, item: MenuItem): Boolean {
		return when (item.itemId) {
			R.id.action_select_all -> {
				val ids = listAdapter?.items?.mapNotNull {
					(it as? ContentListModel)?.id
				} ?: return false
				selectionController?.addAll(ids)
				true
			}

			R.id.action_share -> {
				ShareHelper(requireContext()).shareContentLinks(selectedItems)
				mode?.finish()
				true
			}

			R.id.action_favourite -> {
				router.showFavoriteDialog(selectedItems)
				mode?.finish()
				true
			}

			R.id.action_save -> {
				router.showDownloadDialog(selectedItems, viewBinding?.recyclerView)
				mode?.finish()
				true
			}

			R.id.action_edit_override -> {
				router.openContentOverrideConfig(selectedItems.singleOrNull() ?: return false)
				mode?.finish()
				true
			}

			R.id.action_fix -> {
				val itemsSnapshot = selectedItemsIds
				buildAlertDialog(context ?: return false, isCentered = true) {
					setTitle(item.title)
					setIcon(item.icon)
					setMessage(R.string.manga_fix_prompt)
					setNegativeButton(android.R.string.cancel, null)
					setPositiveButton(R.string.fix) { _, _ ->
						AutoFixService.start(context, itemsSnapshot)
						mode?.finish()
					}
				}.show()
				true
			}

			else -> false
		}
	}

	override fun onSelectionChanged(controller: ListSelectionController, count: Int) {
		viewBinding?.recyclerView?.invalidateItemDecorations()
	}

	override fun onFastScrollStart(fastScroller: FastScroller) {
		(activity as? AppBarOwner)?.appBar?.setExpanded(false, true)
		requireViewBinding().swipeRefreshLayout.isEnabled = false
	}

	override fun onFastScrollStop(fastScroller: FastScroller) {
		requireViewBinding().swipeRefreshLayout.isEnabled = isSwipeRefreshEnabled
	}

	private fun collectSelectedItems(): Set<Content> {
		val checkedIds = selectionController?.peekCheckedIds() ?: return emptySet()
		val items = listAdapter?.items ?: return emptySet()
		val result = ArraySet<Content>(checkedIds.size)
		for (item in items) {
			if (item is ContentListModel && item.id in checkedIds) {
				result.add(item.manga)
			}
		}
		return result
	}

	private inner class SpanSizeLookup : GridLayoutManager.SpanSizeLookup() {

		init {
			isSpanIndexCacheEnabled = true
			isSpanGroupIndexCacheEnabled = true
		}

		override fun getSpanSize(position: Int): Int {
			val total = (viewBinding?.recyclerView?.layoutManager as? GridLayoutManager)?.spanCount ?: return 1
			return when (listAdapter?.getItemViewType(position)) {
				ListItemType.MANGA_GRID.ordinal -> 1
				else -> total
			}
		}

		fun invalidateCache() {
			invalidateSpanGroupIndexCache()
			invalidateSpanIndexCache()
		}
	}

	private fun observeFoldableState() {
		val activity = requireActivity()
		val foldableState = FoldableUtils.observeFoldableState(activity, viewLifecycleOwner)
		
		viewLifecycleScope.launch {
			foldableState.collect { isUnfolded ->
				isFoldUnfolded = isUnfolded
				adjustLayoutForFoldableState()
			}
		}
	}

	private fun adjustLayoutForFoldableState() {
		// 始终使用用户持久化的网格大小进行布局调整，避免被折叠状态覆盖
		val rv = viewBinding?.recyclerView ?: return
		val persistedScale = settings.gridSize / 100f
		spanResolver?.setGridSize(persistedScale, rv)
		viewBinding?.root?.requestLayout()
	}

	// === SearchBarFilterMenuProvider.Callback implementation ===

	override fun onContentTypeSelected(tab: BrowseGroupTab) {
		viewModel.setSelectedGroupTab(tab)
	}

	override fun onSourceTagSelected(tag: SourceTag?) {
		val selectedTags = if (tag != null) setOf(tag) else emptySet()
		viewModel.setSelectedSourceTags(selectedTags)
	}

	override fun getSelectedContentType(): BrowseGroupTab = viewModel.currentGroupTab.value

	override fun getSelectedSourceTags(): Set<SourceTag> = viewModel.currentSourceTags.value

	override fun getSourceTagEntries(): List<SourceTag> = sourceTagChipEntries()

	override fun isContentTypeEnabled(tab: BrowseGroupTab): Boolean {
		val selectedTags = viewModel.currentSourceTags.value
		return selectedTags.isEmpty() || selectedTags.any { it.supportsContentTab(tab) }
	}

	override fun isSourceTagEnabled(tag: SourceTag): Boolean {
		return viewModel.currentGroupTab.value.supportsSourceTag(tag)
	}

	private fun rebuildCategoryChips(binding: FragmentListBinding, categories: List<FavouriteCategory>) {
		val chipGroup = binding.chipGroupCategory
		chipGroup.removeAllViews()
		categoryChipIds.clear()

		if (categories.isEmpty()) {
			chipGroup.visibility = View.GONE
			return
		}

		chipGroup.visibility = View.VISIBLE

		val colors = createChipColors()
		val density = resources.displayMetrics.density
		val currentIds = viewModel.currentCategoryIds.value

		categories.forEach { category ->
			val chip = createCompactChip(
				text = category.title,
				iconRes = R.drawable.ic_filter_menu,
				colors = colors,
				density = density,
			)
			chip.text = category.title // Categories show text
			chip.chipStartPadding = 12 * density
			chip.chipEndPadding = 12 * density
			
			chip.tag = category.id
			chip.isChecked = category.id in currentIds
			chip.setOnCheckedChangeListener { _, isChecked ->
				val selectedIds = viewModel.currentCategoryIds.value.toMutableSet()
				if (isChecked) {
					selectedIds.add(category.id)
				} else {
					selectedIds.remove(category.id)
				}
				viewModel.setSelectedCategoryIds(selectedIds)
			}
			categoryChipIds[category.id] = chip.id
			chipGroup.addView(chip)
		}
	}

	private fun updateCategoryChipsSelection(binding: FragmentListBinding, ids: Set<Long>) {
		categoryChipIds.forEach { (categoryId, id) ->
			binding.chipGroupCategory.findViewById<Chip>(id)?.isChecked = (categoryId in ids)
		}
	}

	// === Inline chip groups for fallback mode (standalone activities) ===

	private fun rebuildContentTypeChips(binding: FragmentListBinding) {
		val group = binding.chipGroupContentType
		group.removeAllViews()
		contentTypeChipIds.clear()
		val colors = createChipColors()
		val density = resources.displayMetrics.density
		val tabs = listOf(BrowseGroupTab.Content, BrowseGroupTab.Novel, BrowseGroupTab.Video)
		for (tab in tabs) {
			val chip = createCompactChip(getString(tab.titleRes), tab.iconRes, colors, density)
			chip.isChecked = (viewModel.currentGroupTab.value == tab)
			chip.setOnClickListener {
				val current = viewModel.currentGroupTab.value
				viewModel.setSelectedGroupTab(if (current == tab) BrowseGroupTab.All else tab)
			}
			contentTypeChipIds[tab] = chip.id
			group.addView(chip)
		}
		group.visibility = View.VISIBLE
	}

	private fun rebuildSourceTagChips(binding: FragmentListBinding) {
		val group = binding.chipGroupSourceTag
		group.removeAllViews()
		sourceTagChipIds.clear()
		val colors = createChipColors()
		val density = resources.displayMetrics.density
		val tags = sourceTagChipEntries()
		for (tag in tags) {
			val chip = createCompactChip(getString(tag.titleRes), null, colors, density)
			chip.isChecked = viewModel.currentSourceTags.value.contains(tag)
			chip.setOnClickListener {
				val selected = viewModel.currentSourceTags.value
				val newTags = if (tag in selected) selected - tag else selected + tag
				viewModel.setSelectedSourceTags(newTags)
			}
			sourceTagChipIds[tag] = chip.id
			group.addView(chip)
		}
		group.visibility = if (tags.isNotEmpty()) View.VISIBLE else View.GONE
	}

	private fun updateContentTypeChipsSelection(binding: FragmentListBinding, selectedTab: BrowseGroupTab) {
		contentTypeChipIds.forEach { (tab, viewId) ->
			binding.chipGroupContentType.findViewById<Chip>(viewId)?.isChecked = (tab == selectedTab)
		}
	}

	private fun updateSourceTagChipsSelection(binding: FragmentListBinding, selectedTags: Set<SourceTag>) {
		sourceTagChipIds.forEach { (tag, viewId) ->
			binding.chipGroupSourceTag.findViewById<Chip>(viewId)?.isChecked = (tag in selectedTags)
		}
	}

	private fun updateContentTypeChipsEnabled(binding: FragmentListBinding, selectedTags: Set<SourceTag>) {
		contentTypeChipIds.forEach { (tab, viewId) ->
			val chip = binding.chipGroupContentType.findViewById<Chip>(viewId) ?: return@forEach
			chip.isEnabled = selectedTags.isEmpty() || selectedTags.any { it.supportsContentTab(tab) }
			chip.alpha = if (chip.isEnabled) 1.0f else 0.5f
		}
	}

	private fun updateSourceTagChipsEnabled(binding: FragmentListBinding, selectedTab: BrowseGroupTab) {
		sourceTagChipIds.forEach { (tag, viewId) ->
			val chip = binding.chipGroupSourceTag.findViewById<Chip>(viewId) ?: return@forEach
			chip.isEnabled = selectedTab.supportsSourceTag(tag)
			chip.alpha = if (chip.isEnabled) 1.0f else 0.5f
		}
	}

	private data class ChipColors(
		val bg: ColorStateList,
		val text: ColorStateList,
		val stroke: ColorStateList,
	)

	private fun createChipColors(): ChipColors {
		val bgUnchecked = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorSurfaceVariant, 0)
		val bgChecked = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorPrimaryContainer, 0)
		val bgDisabled = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorSurface, 0)
		
		val textUnchecked = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOnSurfaceVariant, 0)
		val textChecked = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOnPrimaryContainer, 0)
		val textDisabled = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOnSurface, 0).let {
			ColorStateList.valueOf(it).withAlpha(97).defaultColor // ~38% alpha
		}

		val stateDisabled = intArrayOf(-android.R.attr.state_enabled)
		val stateChecked = intArrayOf(android.R.attr.state_checked)
		val stateDefault = intArrayOf()

		return ChipColors(
			bg = ColorStateList(
				arrayOf(stateDisabled, stateChecked, stateDefault),
				intArrayOf(bgDisabled, bgChecked, bgUnchecked)
			),
			text = ColorStateList(
				arrayOf(stateDisabled, stateChecked, stateDefault),
				intArrayOf(textDisabled, textChecked, textUnchecked)
			),
			stroke = ColorStateList.valueOf(android.graphics.Color.TRANSPARENT),
		)
	}

	private fun createCompactChip(
		text: String,
		iconRes: Int?,
		colors: ChipColors,
		density: Float,
	): Chip {
		return Chip(requireContext()).apply {
			id = View.generateViewId()
			this.text = text
			contentDescription = text
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				tooltipText = text
			}

			isCheckable = true
			isChipIconVisible = iconRes != null
			if (iconRes != null) {
				setChipIconResource(iconRes)
				chipIconSize = 20f * density
				chipIconTint = colors.text
			}

			chipMinHeight = 32 * density
			minHeight = 0
			chipStartPadding = 12 * density
			chipEndPadding = 12 * density
			textStartPadding = if (iconRes != null) 6 * density else 0f
			textEndPadding = 0f
			setEnsureMinTouchTargetSize(false)

			chipStrokeWidth = 0f
			chipBackgroundColor = colors.bg
			setTextColor(colors.text)
		}
	}
}
