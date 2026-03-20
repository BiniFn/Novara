package org.skepsun.kototoro.discover.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.view.MenuProvider
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.ListMode
import org.skepsun.kototoro.core.ui.BaseFragment
import org.skepsun.kototoro.core.ui.list.FitHeightGridLayoutManager
import org.skepsun.kototoro.core.ui.list.FitHeightLinearLayoutManager
import org.skepsun.kototoro.list.domain.ListFilterOption
import org.skepsun.kototoro.list.ui.GridSpanResolver
import org.skepsun.kototoro.list.ui.adapter.ContentListAdapter
import org.skepsun.kototoro.list.ui.adapter.ContentListListener
import org.skepsun.kototoro.list.ui.model.ContentListModel
import org.skepsun.kototoro.list.ui.model.ListHeader
import org.skepsun.kototoro.list.ui.size.DynamicItemSizeResolver
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentTag
import org.skepsun.kototoro.core.ui.widgets.TipView
import org.skepsun.kototoro.core.ui.util.RecyclerViewOwner
import org.skepsun.kototoro.core.util.ext.addMenuProvider
import org.skepsun.kototoro.core.util.ext.consume
import org.skepsun.kototoro.core.ui.list.PaginationScrollListener
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.databinding.FragmentListBinding
import org.skepsun.kototoro.discover.ui.model.DiscoverItem
import org.skepsun.kototoro.list.ui.adapter.ListStateHolderListener
import org.skepsun.kototoro.list.ui.adapter.TypedListSpacingDecoration
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService

@AndroidEntryPoint
class DiscoverFragment :
	BaseFragment<FragmentListBinding>(),
	RecyclerViewOwner,
	SwipeRefreshLayout.OnRefreshListener,
	ListStateHolderListener,
	MenuItem.OnActionExpandListener,
	SearchView.OnQueryTextListener {

	private val viewModel by viewModels<DiscoverViewModel>()
	private var paginationListener: PaginationScrollListener? = null
	private val serviceChipIds = LinkedHashMap<ScrobblerService, Int>()
	private var spanResolver: GridSpanResolver? = null
	
	@javax.inject.Inject
	lateinit var settings: AppSettings

	override val recyclerView: RecyclerView?
		get() = viewBinding?.recyclerView

	override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentListBinding {
		return FragmentListBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: FragmentListBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		binding.chipGroupSourceTag.visibility = View.GONE
		binding.chipGroupCategory.visibility = View.GONE

		spanResolver = GridSpanResolver(binding.root.resources)
		spanResolver?.setGridSize(settings.gridSize / 100f, binding.recyclerView)

		val listListener = object : ContentListListener {
			override fun onItemClick(item: ContentListModel, view: View) {
				val serviceName = item.manga.source.name.removePrefix("TRACKING_")
				val trackingService = viewModel.availableServices.value.find { it.name == serviceName } ?: return
				if (viewModel.supportsDetails(trackingService)) {
					router.openTrackingSiteDetails(trackingService, item.manga.id)
				} else {
					val url = item.manga.url ?: item.manga.publicUrl
					if (!url.isNullOrBlank()) {
						router.openExternalBrowser(url)
					}
				}
			}
			override fun onItemLongClick(item: ContentListModel, view: View) = false
			override fun onItemContextClick(item: ContentListModel, view: View) = false
			override fun onEmptyActionClick() = this@DiscoverFragment.onEmptyActionClick()
			override fun onRetryClick(error: Throwable) = this@DiscoverFragment.onRetryClick(error)
			override fun onListHeaderClick(item: ListHeader, view: View) {}
			override fun onPrimaryButtonClick(tipView: TipView) {}
			override fun onSecondaryButtonClick(tipView: TipView) {}
			override fun onFilterOptionClick(option: ListFilterOption) {}
			override fun onFilterClick(view: View?) {}
			override fun onReadClick(manga: Content, view: View) {}
			override fun onTagClick(manga: Content, tag: ContentTag, view: View) {}
		}

		val adapter = ContentListAdapter(
			listener = listListener,
			sizeResolver = DynamicItemSizeResolver(resources, viewLifecycleOwner, settings, adjustWidth = false),
		)

		applyListMode(settings.listMode, binding.recyclerView)

		paginationListener = PaginationScrollListener(4, object : PaginationScrollListener.Callback {
			override fun onScrolledToEnd() {
				viewModel.loadNextPage()
			}
		})

		val carouselAdapter = DiscoverCarouselAdapter(
			settings = settings,
			contentListener = listListener,
			onMoreClick = { category ->
				val service = viewModel.activeService.value
				router.openTrackingDiscoveryCategory(service, category.id, category.nameResId)
			}
		)

		viewModel.content.observe(viewLifecycleOwner) { items -> 
			val isLoadingOnly = items.size == 1 && items.first() is org.skepsun.kototoro.list.ui.model.LoadingState
			val isCarousel = items.firstOrNull() is org.skepsun.kototoro.discover.ui.model.DiscoverCarouselRow || (items.firstOrNull() is org.skepsun.kototoro.list.ui.model.EmptyState && viewModel.query.value.isBlank())
			
			val progressBar = binding.root.findViewById<View>(R.id.progressBar)
			
			if (isLoadingOnly) {
				// Show centered standalone progress bar
				progressBar?.isVisible = true
				binding.recyclerView.isVisible = false
			} else {
				progressBar?.isVisible = false
				binding.recyclerView.isVisible = true
				
				val currentAdapter = binding.recyclerView.adapter
				if (isCarousel) {
					if (currentAdapter != carouselAdapter) {
						binding.recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(binding.recyclerView.context)
						binding.recyclerView.adapter = carouselAdapter
						paginationListener?.let { binding.recyclerView.removeOnScrollListener(it) }
					}
					carouselAdapter.submitList(items.filterIsInstance<org.skepsun.kototoro.discover.ui.model.DiscoverCarouselRow>())
				} else {
					if (currentAdapter != adapter) {
						applyListMode(settings.listMode, binding.recyclerView)
						binding.recyclerView.adapter = adapter
						paginationListener?.let { binding.recyclerView.addOnScrollListener(it) }
					}
					adapter.emit(items)
				}
			}
		}

		viewModel.isLoading.observe(viewLifecycleOwner) {
			binding.swipeRefreshLayout.isRefreshing = it
		}
		viewModel.availableServices.observe(viewLifecycleOwner) {
			rebuildServiceChips(binding, it)
		}
		viewModel.activeService.observe(viewLifecycleOwner) {
			updateServiceChipSelection(binding, it)
		}
		viewModel.query.observe(viewLifecycleOwner) {
			rebuildServiceChips(binding, viewModel.availableServices.value)
		}
	}

	private fun applyListMode(mode: ListMode, recyclerView: RecyclerView) {
		recyclerView.removeOnLayoutChangeListener(spanResolver)
		when (mode) {
			ListMode.LIST, ListMode.DETAILED_LIST -> {
				recyclerView.layoutManager = FitHeightLinearLayoutManager(recyclerView.context)
			}
			ListMode.GRID -> {
				recyclerView.layoutManager = FitHeightGridLayoutManager(recyclerView.context, checkNotNull(spanResolver).spanCount)
				recyclerView.addOnLayoutChangeListener(spanResolver)
			}
		}
	}

	override fun onApplyWindowInsets(view: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
		requireViewBinding().recyclerView.updatePadding(
			left = systemBars.left,
			right = systemBars.right,
			bottom = systemBars.bottom,
		)
		return insets.consume(view, WindowInsetsCompat.Type.systemBars(), start = true, end = true, bottom = true)
	}

	override fun onRefresh() {
		viewModel.refresh()
	}

	override fun onRetryClick(error: Throwable) {
		viewModel.refresh()
	}

	override fun onEmptyActionClick() = Unit

	override fun onMenuItemActionExpand(item: MenuItem): Boolean = true

	override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
		val searchView = item.actionView as? SearchView ?: return true
		searchView.setQuery("", false)
		viewModel.clearQuery()
		return true
	}

	override fun onQueryTextSubmit(query: String?): Boolean {
		val value = query?.trim().orEmpty()
		if (value.isEmpty()) {
			return false
		}
		viewModel.submitQuery(value)
		return true
	}

	override fun onQueryTextChange(newText: String?): Boolean = false

	private fun onDiscoverItemClick(item: DiscoverItem) {
		// Method preserved as a stub just in case
	}

	private fun rebuildServiceChips(
		binding: FragmentListBinding,
		services: List<ScrobblerService>,
	) {
		serviceChipIds.clear()
		with(binding.chipGroupContentType) {
			removeAllViews()
			services.forEach { service ->
				val chip = createServiceChip(service)
				serviceChipIds[service] = chip.id
				addView(chip)
			}
		}
		binding.chipGroupContentType.visibility = if (services.size > 1) View.VISIBLE else View.GONE
		binding.filterScrollView.visibility = if (services.size > 1) View.VISIBLE else View.GONE
		updateServiceChipSelection(binding, viewModel.activeService.value)
	}



	private fun updateServiceChipSelection(
		binding: FragmentListBinding,
		selected: ScrobblerService,
	) {
		serviceChipIds.forEach { (service, chipId) ->
			binding.chipGroupContentType.findViewById<Chip>(chipId)?.isChecked = service == selected
		}
	}

	private fun createServiceChip(service: ScrobblerService): Chip {
		return Chip(requireContext()).apply {
			id = View.generateViewId()
			text = context.getString(service.titleResId)
			isCheckable = true
			isClickable = true
			isChipIconVisible = true
			setChipIconResource(service.iconResId)
			setEnsureMinTouchTargetSize(false)
			isEnabled = viewModel.isServiceSelectable(service)
			alpha = if (isEnabled) 1f else 0.56f
			if (!isEnabled) {
				text = context.getString(R.string.discover_service_search_only, text)
			}
			setOnClickListener {
				viewModel.selectService(service)
			}
		}
	}

	private inner class DiscoverSearchMenuProvider : MenuProvider {

		override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
			menuInflater.inflate(R.menu.opt_search, menu)
			val menuItem = menu.findItem(R.id.action_search)
			menuItem.setOnActionExpandListener(this@DiscoverFragment)
			val searchView = menuItem.actionView as SearchView
			searchView.setOnQueryTextListener(this@DiscoverFragment)
			searchView.queryHint = menuItem.title
			searchView.setQuery(viewModel.query.value, false)
		}

		override fun onMenuItemSelected(menuItem: MenuItem): Boolean = false
	}
}
