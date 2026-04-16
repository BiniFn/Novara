package org.skepsun.kototoro.discover.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import androidx.fragment.app.activityViewModels
import androidx.compose.runtime.collectAsState
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.ui.BaseFragment
import org.skepsun.kototoro.core.util.ext.addSupportMenuProvider
import org.skepsun.kototoro.databinding.FragmentContentListBinding
import org.skepsun.kototoro.discover.ui.compose.DiscoverScreen
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService
import javax.inject.Inject

@AndroidEntryPoint
class DiscoverFragment :
	BaseFragment<FragmentContentListBinding>(),
	MenuItem.OnActionExpandListener,
	SearchView.OnQueryTextListener {
	private val mainViewModel: org.skepsun.kototoro.main.ui.MainViewModel by activityViewModels()


	private val viewModel by viewModels<DiscoverViewModel>()
	
	@Inject
	lateinit var settings: AppSettings

	override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentContentListBinding {
		return FragmentContentListBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: FragmentContentListBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)

		binding.composeView.apply {
			setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
			setContent {
				val items by viewModel.content.collectAsState(initial = emptyList())
				val isLoading by viewModel.isLoading.collectAsState(initial = false)
				val query by viewModel.query.collectAsState(initial = "")
				
				val isLoadingOnly = items.size == 1 && items.first() is org.skepsun.kototoro.list.ui.model.LoadingState
				val isCarousel = items.firstOrNull() is org.skepsun.kototoro.discover.ui.model.DiscoverCarouselRow || (items.firstOrNull() is org.skepsun.kototoro.list.ui.model.EmptyState && query.isBlank())
				
				DiscoverScreen(
					items = items,
					isRefreshing = isLoading && !isLoadingOnly,
					isCarousel = isCarousel,
					isLoadingOnly = isLoadingOnly,
					gridSpanCount = (settings.gridSize / 100f * 3).toInt().coerceAtLeast(1),
					onRefresh = { viewModel.refresh() },
					onLoadMore = { viewModel.loadNextPage() },
					onItemClick = { item ->
						val serviceName = item.manga.source.name.removePrefix("TRACKING_")
						val trackingService = viewModel.availableServices.value.find { it.name == serviceName } ?: return@DiscoverScreen
						if (viewModel.supportsDetails(trackingService)) {
							router.openTrackingSiteDetails(trackingService, item.manga.id, item.manga.publicUrl)
						} else {
							val url = item.manga.url ?: item.manga.publicUrl
							if (!url.isNullOrBlank()) {
								router.openExternalBrowser(url)
							}
						}
					},
					onCategoryMoreClick = { category ->
						val service = viewModel.activeService.value
						router.openTrackingDiscoveryCategory(service, category.id, category.nameResId)
					}
				)
			}
		}

		addSupportMenuProvider(org.skepsun.kototoro.list.ui.ContentListMenuProvider(this))
	}

	override fun onMenuItemActionExpand(item: MenuItem): Boolean = true

	override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
		val searchView = item.actionView as? SearchView ?: return true
		searchView.setQuery("", false)
		viewModel.clearQuery()
		return true
	}

	override fun onQueryTextSubmit(query: String?): Boolean {
		val value = query?.trim().orEmpty()
		if (value.isEmpty()) return false
		viewModel.submitQuery(value)
		return true
	}

	override fun onQueryTextChange(newText: String?): Boolean = false



	fun showServicePopup(anchor: android.view.View) {
		val popup = androidx.appcompat.widget.PopupMenu(anchor.context, anchor, android.view.Gravity.END)
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
			}
			true
		}
		popup.show()
	}

	private fun ScrobblerService.getPopupTitle(anchorView: android.view.View): CharSequence {
		val title = anchorView.context.getString(titleResId)
		return androidx.core.text.BidiFormatter.getInstance().unicodeWrap(title, androidx.core.text.TextDirectionHeuristicsCompat.LTR)
	}

	override fun onApplyWindowInsets(view: android.view.View, insets: androidx.core.view.WindowInsetsCompat): androidx.core.view.WindowInsetsCompat {
		requireViewBinding().root.clipToPadding = false
		return insets
	}
}