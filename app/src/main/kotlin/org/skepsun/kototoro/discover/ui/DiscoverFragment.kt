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
import org.skepsun.kototoro.core.ui.BaseFragment
import org.skepsun.kototoro.core.ui.util.RecyclerViewOwner
import org.skepsun.kototoro.core.util.ext.addMenuProvider
import org.skepsun.kototoro.core.util.ext.consume
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
	private val serviceChipIds = LinkedHashMap<ScrobblerService, Int>()

	override val recyclerView: RecyclerView?
		get() = viewBinding?.recyclerView

	override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentListBinding {
		return FragmentListBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: FragmentListBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		binding.chipGroupSourceTag.visibility = View.GONE
		binding.chipGroupCategory.visibility = View.GONE
		binding.filterSeparator.visibility = View.GONE
		binding.filterSeparator2.visibility = View.GONE
		val adapter = DiscoverAdapter(this, ::onDiscoverItemClick)
		with(binding.recyclerView) {
			layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
			this.adapter = adapter
			setHasFixedSize(true)
			addItemDecoration(TypedListSpacingDecoration(context, true))
		}
		binding.swipeRefreshLayout.setOnRefreshListener(this)
		addMenuProvider(DiscoverSearchMenuProvider())

		viewModel.content.observe(viewLifecycleOwner, adapter)
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
		if (viewModel.supportsDetails(item.item.service)) {
			router.openTrackingSiteDetails(item.item.service, item.item.remoteId)
			return
		}
		val url = item.item.url ?: return
		router.openExternalBrowser(url)
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
