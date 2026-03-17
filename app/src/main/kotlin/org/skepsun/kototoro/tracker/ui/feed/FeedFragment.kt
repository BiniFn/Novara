package org.skepsun.kototoro.tracker.ui.feed

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.content.res.ColorStateList
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import coil3.ImageLoader
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import dagger.hilt.android.AndroidEntryPoint
import androidx.core.view.ViewCompat
import androidx.core.view.children
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import kotlinx.coroutines.flow.drop
import com.google.android.material.appbar.AppBarLayout
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.exceptions.resolve.SnackbarErrorObserver
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.ui.BaseFragment
import org.skepsun.kototoro.core.ui.list.PaginationScrollListener
import org.skepsun.kototoro.core.ui.list.RecyclerScrollKeeper
import org.skepsun.kototoro.core.ui.util.MenuInvalidator
import org.skepsun.kototoro.core.ui.util.RecyclerViewOwner
import org.skepsun.kototoro.core.ui.util.ReversibleActionObserver
import org.skepsun.kototoro.core.ui.widgets.TipView
import org.skepsun.kototoro.core.util.ext.addMenuProvider
import org.skepsun.kototoro.core.util.ext.consumeAll
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.core.util.ext.observeEvent
import org.skepsun.kototoro.databinding.FragmentListBinding
import org.skepsun.kototoro.explore.ui.model.BrowseGroupTab
import org.skepsun.kototoro.explore.ui.model.SourceTag
import org.skepsun.kototoro.list.domain.ListFilterOption
import org.skepsun.kototoro.list.ui.adapter.ContentListListener
import org.skepsun.kototoro.list.ui.adapter.TypedListSpacingDecoration
import org.skepsun.kototoro.list.ui.model.ListHeader
import org.skepsun.kototoro.list.ui.model.ContentListModel
import org.skepsun.kototoro.list.ui.size.StaticItemSizeResolver
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentTag
import org.skepsun.kototoro.tracker.ui.feed.adapter.FeedAdapter
import org.skepsun.kototoro.main.ui.owners.AppBarOwner
import javax.inject.Inject

@AndroidEntryPoint
class FeedFragment :
	BaseFragment<FragmentListBinding>(),
	PaginationScrollListener.Callback,
	RecyclerViewOwner,
	ContentListListener,
	SwipeRefreshLayout.OnRefreshListener,
	AppBarLayout.OnOffsetChangedListener {

	@Inject
	lateinit var coil: ImageLoader

	private val viewModel by viewModels<FeedViewModel>()

	private val contentTypeChipIds = mutableMapOf<String, Int>()
	private val sourceTagChipIds = mutableMapOf<String, Int>()

	override val recyclerView: RecyclerView?
		get() = viewBinding?.recyclerView

	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	) = FragmentListBinding.inflate(inflater, container, false)

	override fun onViewBindingCreated(binding: FragmentListBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		
		// Initialize Filter Bar
		binding.filterScrollView.isVisible = true
		rebuildContentTypeChips(binding)
		rebuildSourceTagChips(binding)
		
		val sizeResolver = StaticItemSizeResolver(resources.getDimensionPixelSize(R.dimen.smaller_grid_width))
		val feedAdapter = FeedAdapter(this, sizeResolver) { item, v ->
			viewModel.onItemClick(item)
			router.openDetails(item.toContentWithOverride())
		}
		with(binding.recyclerView) {
			val paddingVertical = resources.getDimensionPixelSize(R.dimen.list_spacing_normal)
			setPadding(0, paddingVertical, 0, paddingVertical)
			layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
			adapter = feedAdapter
			setHasFixedSize(true)
			addOnScrollListener(PaginationScrollListener(4, this@FeedFragment))
			addItemDecoration(TypedListSpacingDecoration(context, true))
			RecyclerScrollKeeper(this).attach()
		}
		binding.swipeRefreshLayout.setOnRefreshListener(this)
		addMenuProvider(FeedMenuProvider(binding.recyclerView, viewModel))

		viewModel.isHeaderEnabled.drop(1).observe(viewLifecycleOwner, MenuInvalidator(requireActivity()))
		viewModel.content.observe(viewLifecycleOwner, feedAdapter)
		viewModel.onError.observeEvent(viewLifecycleOwner, SnackbarErrorObserver(binding.recyclerView, this))
		viewModel.onActionDone.observeEvent(viewLifecycleOwner, ReversibleActionObserver(binding.recyclerView))
		viewModel.isRunning.observe(viewLifecycleOwner, this::onIsTrackerRunningChanged)

		viewModel.currentGroupTab.observe(viewLifecycleOwner) { 
			updateContentTypeChipsSelection(binding, it)
			updateSourceTagChipsEnabled(binding, it)
		}
		viewModel.currentSourceTags.observe(viewLifecycleOwner) {
			updateSourceTagChipsSelection(binding, it)
			updateContentTypeChipsEnabled(binding, it)
		}

		// Register for appbar offset changes
		val appBar = (activity as? AppBarOwner)?.appBar
		appBar?.addOnOffsetChangedListener(this)

		// Remove snap flag for linear scroll
		appBar?.children?.forEach { child ->
			val lp = child.layoutParams as? AppBarLayout.LayoutParams
			if (lp != null && (lp.scrollFlags and AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP != 0)) {
				lp.scrollFlags = lp.scrollFlags and AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP.inv()
				child.layoutParams = lp
			}
		}
	}

	override fun onDestroyView() {
		val appBar = (activity as? AppBarOwner)?.appBar
		appBar?.removeOnOffsetChangedListener(this)

		// Restore snap flag
		appBar?.children?.forEach { child ->
			val lp = child.layoutParams as? AppBarLayout.LayoutParams
			if (lp != null && (child.id == R.id.search_bar || child.id == R.id.insetsHolder)) {
				lp.scrollFlags = lp.scrollFlags or AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP
				child.layoutParams = lp
			}
		}
		super.onDestroyView()
	}

	override fun onOffsetChanged(appBarLayout: AppBarLayout?, verticalOffset: Int) {
		val binding = viewBinding ?: return
		val appBar = appBarLayout ?: return
		val insets = ViewCompat.getRootWindowInsets(binding.root)?.getInsets(WindowInsetsCompat.Type.statusBars())
		val statusBarHeight = insets?.top ?: 0
		val topPadding = Math.max(0, statusBarHeight - appBar.bottom)
		if (binding.filterScrollView.paddingTop != topPadding) {
			binding.filterScrollView.updatePadding(top = topPadding)
		}
	}


	private fun rebuildContentTypeChips(binding: FragmentListBinding) {
		val group = binding.chipGroupContentType
		group.removeAllViews()
		contentTypeChipIds.clear()

		val colors = createChipColors()
		val density = resources.displayMetrics.density

		val tabs = listOf(BrowseGroupTab.Content, BrowseGroupTab.Novel, BrowseGroupTab.Video)
		for (tab in tabs) {
			val chip = createCompactChip(getString(tab.titleRes), tab.iconRes, colors, density)
			val id = View.generateViewId()
			chip.id = id
			contentTypeChipIds[tab.id] = id
			
			chip.isChecked = (viewModel.currentGroupTab.value == tab)
			chip.setOnClickListener {
				viewModel.setSelectedGroupTab(tab)
			}
			group.addView(chip)
		}

		updateContentTypeChipsSelection(binding, viewModel.currentGroupTab.value)
		updateContentTypeChipsEnabled(binding, viewModel.currentSourceTags.value)
	}

	private fun rebuildSourceTagChips(binding: FragmentListBinding) {
		val group = binding.chipGroupSourceTag
		group.removeAllViews()
		sourceTagChipIds.clear()

		val colors = createChipColors()
		val density = resources.displayMetrics.density

		val tags = SourceTag.entries
		for (tag in tags) {
			val chip = createCompactChip(getString(tag.titleRes), tag.iconRes, colors, density)
			val id = View.generateViewId()
			chip.id = id
			sourceTagChipIds[tag.id] = id
			
			chip.isChecked = viewModel.currentSourceTags.value.contains(tag)
			chip.setOnClickListener {
				viewModel.toggleSourceTag(tag)
			}
			group.addView(chip)
		}

		updateSourceTagChipsSelection(binding, viewModel.currentSourceTags.value)
		updateSourceTagChipsEnabled(binding, viewModel.currentGroupTab.value)
	}

	private fun updateContentTypeChipsSelection(binding: FragmentListBinding, selectedTab: BrowseGroupTab) {
		val group = binding.chipGroupContentType
		for (tabId in contentTypeChipIds.keys) {
			val viewId = contentTypeChipIds[tabId] ?: continue
			val chip = group.findViewById<Chip>(viewId) ?: continue
			val shouldBeChecked = (tabId == selectedTab.id)
			if (chip.isChecked != shouldBeChecked) {
				chip.isChecked = shouldBeChecked
			}
		}
	}

	private fun updateSourceTagChipsSelection(binding: FragmentListBinding, selectedTags: Set<SourceTag>) {
		val group = binding.chipGroupSourceTag
		for (tagId in sourceTagChipIds.keys) {
			val viewId = sourceTagChipIds[tagId] ?: continue
			val chip = group.findViewById<Chip>(viewId) ?: continue
			val shouldBeChecked = selectedTags.any { it.id == tagId }
			if (chip.isChecked != shouldBeChecked) {
				chip.isChecked = shouldBeChecked
			}
		}
	}

	private fun updateContentTypeChipsEnabled(binding: FragmentListBinding, selectedTags: Set<SourceTag>) {
		val group = binding.chipGroupContentType
		for (tabId in contentTypeChipIds.keys) {
			val viewId = contentTypeChipIds[tabId] ?: continue
			val chip = group.findViewById<Chip>(viewId) ?: continue
			val tab = BrowseGroupTab.fromId(tabId)
			chip.isEnabled = selectedTags.isEmpty() || selectedTags.any { it.supportsContentTab(tab) }
			chip.alpha = if (chip.isEnabled) 1.0f else 0.5f
		}
	}

	private fun updateSourceTagChipsEnabled(binding: FragmentListBinding, selectedTab: BrowseGroupTab) {
		val group = binding.chipGroupSourceTag
		for (tagId in sourceTagChipIds.keys) {
			val viewId = sourceTagChipIds[tagId] ?: continue
			val chip = group.findViewById<Chip>(viewId) ?: continue
			val tag = SourceTag.entries.find { it.id == tagId } ?: continue
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

	private fun createCompactChip(text: String, iconRes: Int, colors: ChipColors, density: Float): Chip {
		return Chip(requireContext()).apply {
			id = View.generateViewId()
			this.text = ""
			contentDescription = text
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				tooltipText = text
			}
			
			isCheckable = true
			isChipIconVisible = true
			setChipIconResource(iconRes)
			chipIconSize = 20f * density
			chipIconTint = colors.text
			
			chipMinHeight = 32 * density
			minHeight = 0
			chipStartPadding = 8 * density
			chipEndPadding = 8 * density
			textStartPadding = 0f
			textEndPadding = 0f
			setEnsureMinTouchTargetSize(false)
			
			chipStrokeWidth = 0f
			chipBackgroundColor = colors.bg
			setTextColor(colors.text)
		}
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val barsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
		val sidePadding = v.resources.getDimensionPixelOffset(R.dimen.list_spacing_normal)

		viewBinding?.filterChipsContainer?.updatePadding(
			left = barsInsets.left + sidePadding,
			right = barsInsets.right + sidePadding,
		)

		viewBinding?.recyclerView?.updatePadding(
			left = barsInsets.left + sidePadding,
			right = barsInsets.right + sidePadding,
			bottom = barsInsets.bottom + sidePadding,
		)
		
		return insets
	}

	override fun onRefresh() {
		viewModel.update()
	}

	override fun onFilterOptionClick(option: ListFilterOption) = viewModel.toggleFilterOption(option)

	override fun onRetryClick(error: Throwable) = Unit

	override fun onFilterClick(view: View?) = Unit

	override fun onEmptyActionClick() = Unit

	override fun onPrimaryButtonClick(tipView: TipView) = Unit

	override fun onSecondaryButtonClick(tipView: TipView) = Unit

	override fun onListHeaderClick(item: ListHeader, view: View) {
		router.openMangaUpdates()
	}

	private fun onIsTrackerRunningChanged(isRunning: Boolean) {
		requireViewBinding().swipeRefreshLayout.isRefreshing = isRunning
	}

	override fun onScrolledToEnd() {
		viewModel.requestMoreItems()
	}

	override fun onItemClick(item: ContentListModel, view: View) {
		router.openDetails(item.toContentWithOverride())
	}

	override fun onReadClick(manga: Content, view: View) = Unit

	override fun onTagClick(manga: Content, tag: ContentTag, view: View) = Unit
}
