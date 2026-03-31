package org.skepsun.kototoro.discover.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.core.text.BidiFormatter
import androidx.core.text.TextDirectionHeuristicsCompat
import androidx.core.view.MenuProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.color.MaterialColors
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
import org.skepsun.kototoro.main.ui.owners.AppBarOwner
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService

@AndroidEntryPoint
class DiscoverFragment :
	BaseFragment<FragmentListBinding>(),
	RecyclerViewOwner,
	SwipeRefreshLayout.OnRefreshListener,
	ListStateHolderListener,
	MenuItem.OnActionExpandListener,
	androidx.appcompat.widget.SearchView.OnQueryTextListener,
	android.content.SharedPreferences.OnSharedPreferenceChangeListener {

	private val viewModel by viewModels<DiscoverViewModel>()
	private var paginationListener: PaginationScrollListener? = null
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
		binding.chipGroupCategory.visibility = View.GONE

		spanResolver = GridSpanResolver(binding.root.resources)
		spanResolver?.setGridSize(settings.gridSize / 100f, binding.recyclerView)

		val listListener = object : ContentListListener {
			override fun onItemClick(item: ContentListModel, view: View) {
				val serviceName = item.manga.source.name.removePrefix("TRACKING_")
				val trackingService = viewModel.availableServices.value.find { it.name == serviceName } ?: return
				if (viewModel.supportsDetails(trackingService)) {
					router.openTrackingSiteDetails(trackingService, item.manga.id, item.manga.publicUrl)
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

		binding.recyclerView.addItemDecoration(org.skepsun.kototoro.list.ui.adapter.TypedListSpacingDecoration(requireContext(), false))

		applyListMode(settings.listMode, binding.recyclerView)

		paginationListener = PaginationScrollListener(4, object : PaginationScrollListener.Callback {
			override fun onScrolledToEnd() {
				viewModel.loadNextPage()
			}
		})

		val carouselAdapter = DiscoverCarouselAdapter(
			contentListener = listListener,
			viewLifecycleOwner = viewLifecycleOwner,
			settings = settings,
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
			// Service selection now handled via SearchBar service menu provider
		}
		viewModel.activeService.observe(viewLifecycleOwner) {
			// Service selection now handled via SearchBar service menu provider
		}

		// Add the service menu provider to the SearchBar
		addMenuProvider(DiscoverServiceMenuProvider())
		addMenuProvider(org.skepsun.kototoro.list.ui.ContentListMenuProvider(this))

		settings.subscribe(this)
	}

	override fun onDestroyView() {
		settings.unsubscribe(this)
		super.onDestroyView()
	}

	override fun onSharedPreferenceChanged(sharedPreferences: android.content.SharedPreferences?, key: String?) {
		val recyclerView = requireViewBinding().recyclerView
		val isCarousel = recyclerView.adapter is DiscoverCarouselAdapter

		if (key == AppSettings.KEY_LIST_MODE) {
			if (!isCarousel) {
				applyListMode(settings.listMode, recyclerView)
			}
		} else if (key == AppSettings.KEY_GRID_SIZE) {
			if (!isCarousel) {
				spanResolver?.setGridSize(settings.gridSize / 100f, recyclerView)
			}
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

	/**
	 * MenuProvider that adds tracking service icons to the SearchBar.
	 * Each service gets a toggle icon (e.g. MAL, Bangumi, Kitsu).
	 */
	private inner class DiscoverServiceMenuProvider : MenuProvider {

		private var serviceMenuItem: MenuItem? = null

		override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
			val services = viewModel.availableServices.value
			if (services.size <= 1) return

			val activeService = viewModel.activeService.value
			val item = menu.add(Menu.NONE, View.generateViewId(), 0, activeService.titleResId)
			item.setIcon(createDropdownIcon(activeService.iconResId))
			item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
			updateServiceMenuItemTint(item, true)
			serviceMenuItem = item
		}

		override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
			if (menuItem != serviceMenuItem) return false
			showServicePopup(menuItem)
			return true
		}

		private fun showServicePopup(anchorItem: MenuItem) {
			val anchor = activity?.findViewById<View>(R.id.search_bar) ?: return
			val popup = PopupMenu(anchor.context, anchor, android.view.Gravity.END)
			val services = viewModel.availableServices.value
			val activeService = viewModel.activeService.value

			services.forEachIndexed { index, service ->
				popup.menu.add(0, index, index, service.getPopupTitle(anchor)).apply {
					setIcon(service.iconResId)
					isCheckable = true
					isChecked = service == activeService
					isEnabled = viewModel.isServiceSelectable(service)
				}
			}

			// Show icons in popup
			try {
				val field = popup.javaClass.getDeclaredField("mPopup")
				field.isAccessible = true
				val menuPopupHelper = field.get(popup)
				menuPopupHelper.javaClass.getDeclaredMethod("setForceShowIcon", Boolean::class.java)
					.invoke(menuPopupHelper, true)
			} catch (_: Exception) { }

			popup.setOnMenuItemClickListener { item ->
				services.getOrNull(item.itemId)?.let { service ->
					viewModel.selectService(service)
					// Update the toolbar icon to match the selected service
					serviceMenuItem?.icon = createDropdownIcon(service.iconResId)
					serviceMenuItem?.title = getString(service.titleResId)
					updateServiceMenuItemTint(serviceMenuItem ?: return@let, true)
				}
				true
			}
			popup.show()
		}

		private fun ScrobblerService.getPopupTitle(anchorView: View): CharSequence {
			val title = anchorView.context.getString(titleResId)
			return BidiFormatter.getInstance().unicodeWrap(title, TextDirectionHeuristicsCompat.LTR)
		}

		private fun updateServiceMenuItemTint(item: MenuItem, isActive: Boolean) {
			val context = context ?: return
			val tint = if (isActive) {
				MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, 0)
			} else {
				MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0)
			}
			val icon = item.icon
			if (icon is android.graphics.drawable.LayerDrawable) {
				// Only tint layer 0 (service icon); layer 1 (arrow) stays black
				icon.getDrawable(0)?.setTint(tint)
			} else {
				icon?.setTint(tint)
			}
		}

		private fun createDropdownIcon(@androidx.annotation.DrawableRes iconRes: Int): android.graphics.drawable.Drawable? {
			val context = context ?: return null
			val serviceIcon = androidx.core.content.ContextCompat.getDrawable(context, iconRes)?.mutate() ?: return null
			val arrowIcon = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_expand_more)?.mutate() ?: return serviceIcon
			arrowIcon.setTint(android.graphics.Color.BLACK)
			val dp = context.resources.displayMetrics.density
			val iconSize = (24 * dp).toInt()   // standard 24dp icon
			val arrowSize = (14 * dp).toInt()   // 14dp arrow for visibility
			val totalW = iconSize + arrowSize / 2
			val totalH = iconSize + arrowSize / 2
			// Service icon fills the top-left area
			val serviceInsetRight = totalW - iconSize
			val serviceInsetBottom = totalH - iconSize
			// Arrow in the absolute bottom-right corner
			val arrowInsetLeft = totalW - arrowSize
			val arrowInsetTop = totalH - arrowSize
			val layers = android.graphics.drawable.LayerDrawable(arrayOf(serviceIcon, arrowIcon))
			layers.setLayerInset(0, 0, 0, serviceInsetRight, serviceInsetBottom)
			layers.setLayerInset(1, arrowInsetLeft, arrowInsetTop, 0, 0)
			layers.setBounds(0, 0, totalW, totalH)
			return layers
		}
	}
}
