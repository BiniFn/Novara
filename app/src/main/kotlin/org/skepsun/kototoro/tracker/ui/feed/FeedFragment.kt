package org.skepsun.kototoro.tracker.ui.feed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import coil3.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
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
import org.skepsun.kototoro.main.ui.SearchBarFilterMenuProvider
import org.skepsun.kototoro.main.ui.owners.AppBarOwner
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentTag
import org.skepsun.kototoro.tracker.ui.feed.adapter.FeedAdapter
import kotlinx.coroutines.flow.drop
import javax.inject.Inject

@AndroidEntryPoint
class FeedFragment :
	BaseFragment<FragmentListBinding>(),
	PaginationScrollListener.Callback,
	RecyclerViewOwner,
	ContentListListener,
	SwipeRefreshLayout.OnRefreshListener,
	AppBarLayout.OnOffsetChangedListener,
	SearchBarFilterMenuProvider.Callback {

	@Inject
	lateinit var coil: ImageLoader

	@Inject
	lateinit var settings: org.skepsun.kototoro.core.prefs.AppSettings

	private val viewModel by viewModels<FeedViewModel>()
	private var filterMenuProvider: SearchBarFilterMenuProvider? = null

	override val recyclerView: RecyclerView?
		get() = viewBinding?.recyclerView

	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	) = FragmentListBinding.inflate(inflater, container, false)

	override fun onViewBindingCreated(binding: FragmentListBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)

		// Set up SearchBar filter icons
		val searchBar = (activity as? AppBarOwner)?.appBar?.let { appBar ->
			appBar.findViewById<View>(R.id.search_bar)
		} ?: activity?.findViewById(R.id.search_bar)

		if (searchBar != null) {
			filterMenuProvider = SearchBarFilterMenuProvider(this, searchBar)
			addMenuProvider(filterMenuProvider!!)
		}

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
			filterMenuProvider?.updateIcons()
		}
		viewModel.currentSourceTags.observe(viewLifecycleOwner) {
			filterMenuProvider?.updateIcons()
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
		filterMenuProvider = null
		super.onDestroyView()
	}

	override fun onOffsetChanged(appBarLayout: AppBarLayout?, verticalOffset: Int) {
		// No longer need to adjust filterScrollView padding
	}

	// === SearchBarFilterMenuProvider.Callback implementation ===

	override fun onContentTypeSelected(tab: BrowseGroupTab) {
		viewModel.setSelectedGroupTab(tab)
	}

	override fun onSourceTagSelected(tag: SourceTag?) {
		if (tag != null) {
			viewModel.toggleSourceTag(tag)
		} else {
			// Clear all source tags
			viewModel.currentSourceTags.value.forEach { viewModel.toggleSourceTag(it) }
		}
	}

	override fun getSelectedContentType(): BrowseGroupTab = viewModel.currentGroupTab.value

	override fun getSelectedSourceTags(): Set<SourceTag> = viewModel.currentSourceTags.value

	override fun getSourceTagEntries(): List<SourceTag> = SourceTag.quickFilterEntries

	override fun isContentTypeFilterVisible(): Boolean = !settings.isSearchBarFilterHidden && true

	override fun isSourceTagFilterVisible(): Boolean = !settings.isSearchBarFilterHidden && true

	override fun isContentTypeEnabled(tab: BrowseGroupTab): Boolean {
		val selectedTags = viewModel.currentSourceTags.value
		return selectedTags.isEmpty() || selectedTags.any { it.supportsContentTab(tab) }
	}

	override fun isSourceTagEnabled(tag: SourceTag): Boolean {
		return viewModel.currentGroupTab.value.supportsSourceTag(tag)
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val barsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
		val sidePadding = v.resources.getDimensionPixelOffset(R.dimen.list_spacing_normal)

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
