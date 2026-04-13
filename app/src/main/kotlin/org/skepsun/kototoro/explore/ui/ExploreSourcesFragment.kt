package org.skepsun.kototoro.explore.ui

import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ActionMode
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import com.google.android.material.appbar.AppBarLayout
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.exceptions.resolve.SnackbarErrorObserver
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.parser.external.ExternalContentSource
import org.skepsun.kototoro.core.ui.BaseFragment
import org.skepsun.kototoro.core.ui.dialog.BigButtonsAlertDialog
import org.skepsun.kototoro.core.ui.list.ListSelectionController
import org.skepsun.kototoro.core.ui.list.OnListItemClickListener
import org.skepsun.kototoro.core.ui.util.RecyclerViewOwner
import org.skepsun.kototoro.core.ui.util.ReversibleActionObserver
import org.skepsun.kototoro.core.ui.util.SpanSizeResolver
import org.skepsun.kototoro.core.util.ext.addSupportMenuProvider
import org.skepsun.kototoro.core.util.ext.findAppCompatDelegate
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.core.util.ext.observeEvent
import org.skepsun.kototoro.databinding.FragmentExploreSourcesBinding
import org.skepsun.kototoro.explore.ui.adapter.ExploreAdapter
import org.skepsun.kototoro.explore.ui.adapter.ExploreListEventListener
import org.skepsun.kototoro.explore.ui.model.BrowseGroupTab
import org.skepsun.kototoro.explore.ui.model.ContentSourceItem
import org.skepsun.kototoro.explore.ui.model.SourceTag
import org.skepsun.kototoro.list.ui.adapter.TypedListSpacingDecoration
import org.skepsun.kototoro.list.ui.model.ListHeader
import org.skepsun.kototoro.main.ui.SearchBarFilterViewController
import org.skepsun.kototoro.main.ui.owners.AppBarOwner
import org.skepsun.kototoro.core.model.unwrap
import org.skepsun.kototoro.core.model.isLocal
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.core.model.unwrap

@AndroidEntryPoint
class ExploreSourcesFragment :
	BaseFragment<FragmentExploreSourcesBinding>(),
	RecyclerViewOwner,
	ExploreListEventListener,
	OnListItemClickListener<ContentSourceItem>, ListSelectionController.Callback {

	private val viewModel by viewModels<ExploreViewModel>()
	private var exploreAdapter: ExploreAdapter? = null
	private var sourceSelectionController: ListSelectionController? = null

	override val recyclerView: RecyclerView?
		get() = viewBinding?.recyclerView

	override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentExploreSourcesBinding {
		return FragmentExploreSourcesBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: FragmentExploreSourcesBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		exploreAdapter = ExploreAdapter(this, this) { manga, view ->
			val coverView = view?.findViewById<View>(R.id.imageView_cover);
                        router.openDetails(manga, coverView)
		}
		sourceSelectionController = ListSelectionController(
			appCompatDelegate = checkNotNull(findAppCompatDelegate()),
			decoration = SourceSelectionDecoration(binding.root.context),
			registryOwner = this,
			callback = this,
		)
		with(binding.recyclerView) {
			adapter = exploreAdapter
			setHasFixedSize(true)
			SpanSizeResolver(this, resources.getDimensionPixelSize(R.dimen.explore_grid_width)).attach()
			addItemDecoration(TypedListSpacingDecoration(context, false))
			checkNotNull(sourceSelectionController).attachToRecyclerView(this)
		}
		// Setup menu with quick access actions
		val menuProvider = ExploreMenuProvider(router)
		addSupportMenuProvider(menuProvider)
		
		viewModel.content.observe(viewLifecycleOwner, checkNotNull(exploreAdapter))
		viewModel.onError.observeEvent(viewLifecycleOwner, SnackbarErrorObserver(binding.recyclerView, this))
		viewModel.onOpenContent.observeEvent(viewLifecycleOwner, ::onOpenContent)
		viewModel.onActionDone.observeEvent(viewLifecycleOwner, ReversibleActionObserver(binding.recyclerView))
		viewModel.isGrid.observe(viewLifecycleOwner, ::onGridModeChanged)
		viewModel.onShowSuggestionsTip.observeEvent(viewLifecycleOwner) {
			showSuggestionsTip()
		}
		
		// Observe filter changes
		viewModel.currentGroupTab.observe(viewLifecycleOwner) { _ ->
			updateTvBoxRepositoryLabel(binding)
		}
		viewModel.currentSourceTags.observe(viewLifecycleOwner) { _ ->
			updateTvBoxRepositoryLabel(binding)
		}
		viewModel.availableTabs.observe(viewLifecycleOwner) { _ ->
		}
		viewModel.activeTvBoxRepositoryTitle.observe(viewLifecycleOwner) {
			updateTvBoxRepositoryLabel(binding)
		}

	}

	override fun onDestroyView() {
		val appBar = (activity as? AppBarOwner)?.appBar

		super.onDestroyView()
		sourceSelectionController = null
		exploreAdapter = null
	}



	// === Other methods ===

	private fun updateTvBoxRepositoryLabel(binding: FragmentExploreSourcesBinding) {
		val title = viewModel.activeTvBoxRepositoryTitle.value
		val shouldShow = !title.isNullOrBlank() && (
			viewModel.currentSourceTags.value?.contains(SourceTag.TVBOX) == true ||
				viewModel.currentGroupTab.value == BrowseGroupTab.Video
			)
		binding.textViewTvboxRepository.visibility = if (shouldShow) View.VISIBLE else View.GONE
		if (shouldShow) {
			binding.textViewTvboxRepository.text = getString(R.string.tvbox_repository_current_label, title)
		}
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

	override fun onListHeaderClick(item: ListHeader, view: View) {
		if (item.payload == R.id.nav_suggestions) {
			router.openSuggestions()
		} else if (viewModel.isAllSourcesEnabled.value) {
			router.openManageSources()
		} else {
			router.openSourcesCatalog()
		}
	}

	override fun onClick(v: View) {
		when (v.id) {
			R.id.button_more -> router.openSuggestions()
		}
	}

	override fun onItemClick(item: ContentSourceItem, view: View) {
		if (sourceSelectionController?.onItemClick(item.id) == true) {
			return
		}
		router.openList(item.source, null, null)
	}

	override fun onItemLongClick(item: ContentSourceItem, view: View): Boolean {
		return sourceSelectionController?.onItemLongClick(view, item.id) == true
	}

	override fun onItemContextClick(item: ContentSourceItem, view: View): Boolean {
		return sourceSelectionController?.onItemContextClick(view, item.id) == true
	}

	override fun onRetryClick(error: Throwable) = Unit

	override fun onEmptyActionClick() = router.openSourcesCatalog()

	override fun onSelectionChanged(controller: ListSelectionController, count: Int) {
		viewBinding?.recyclerView?.invalidateItemDecorations()
	}

	override fun onCreateActionMode(
		controller: ListSelectionController,
		menuInflater: MenuInflater,
		menu: Menu
	): Boolean {
		menuInflater.inflate(R.menu.mode_source, menu)
		return true
	}

	override fun onPrepareActionMode(controller: ListSelectionController, mode: ActionMode?, menu: Menu): Boolean {
		val selectedSources = viewModel.sourcesSnapshot(controller.peekCheckedIds())
		val isSingleSelection = selectedSources.size == 1
		menu.findItem(R.id.action_settings).isVisible = isSingleSelection
		menu.findItem(R.id.action_shortcut).isVisible = isSingleSelection
		menu.findItem(R.id.action_pin).isVisible = selectedSources.all { !it.isPinned }
		menu.findItem(R.id.action_unpin).isVisible = selectedSources.all { it.isPinned }
		menu.findItem(R.id.action_disable)?.isVisible = !viewModel.isAllSourcesEnabled.value &&
			selectedSources.all { 
				val unwrapped = it.mangaSource.unwrap()
				!unwrapped.isLocal && unwrapped !is ExternalContentSource 
			}
		menu.findItem(R.id.action_delete)?.isVisible = selectedSources.all { it.mangaSource is ExternalContentSource }
		return super.onPrepareActionMode(controller, mode, menu)
	}

	override fun onActionItemClicked(controller: ListSelectionController, mode: ActionMode?, item: MenuItem): Boolean {
		val selectedSources = viewModel.sourcesSnapshot(controller.peekCheckedIds())
		if (selectedSources.isEmpty()) {
			return false
		}
		when (item.itemId) {
			R.id.action_settings -> {
				val source = selectedSources.singleOrNull() ?: return false
				router.openSourceSettings(source)
				mode?.finish()
			}

			R.id.action_disable -> {
				viewModel.disableSources(selectedSources)
				mode?.finish()
			}

			R.id.action_delete -> {
				selectedSources.forEach {
					(it.mangaSource as? ExternalContentSource)?.let { uninstallExternalSource(it) }
				}
				mode?.finish()
			}

			R.id.action_shortcut -> {
				val source = selectedSources.singleOrNull() ?: return false
				viewModel.requestPinShortcut(source)
				mode?.finish()
			}

			R.id.action_pin -> {
				viewModel.setSourcesPinned(selectedSources, isPinned = true)
				mode?.finish()
			}

			R.id.action_unpin -> {
				viewModel.setSourcesPinned(selectedSources, isPinned = false)
				mode?.finish()
			}

			else -> return false
		}
		return true
	}

	private fun onOpenContent(manga: Content) {
		val coverView = view?.findViewById<View>(R.id.imageView_cover);
                        router.openDetails(manga, coverView)
	}

	private fun onGridModeChanged(isGrid: Boolean) {
		viewBinding?.recyclerView?.layoutManager = if (isGrid) {
			GridLayoutManager(requireContext(), 4).also { lm ->
				lm.spanSizeLookup = ExploreGridSpanSizeLookup(checkNotNull(exploreAdapter), lm)
			}
		} else {
			LinearLayoutManager(requireContext())
		}
	}

	private fun showSuggestionsTip() {
		val listener = DialogInterface.OnClickListener { _, which ->
			viewModel.respondSuggestionTip(which == DialogInterface.BUTTON_POSITIVE)
		}
		BigButtonsAlertDialog.Builder(requireContext())
			.setIcon(R.drawable.ic_suggestion)
			.setTitle(R.string.suggestions_enable_prompt)
			.setPositiveButton(R.string.enable, listener)
			.setNegativeButton(R.string.no_thanks, listener)
			.create()
			.show()
	}

	private fun uninstallExternalSource(source: ExternalContentSource) {
		val uri = Uri.fromParts("package", source.packageName, null)
		val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			Intent.ACTION_DELETE
		} else {
			@Suppress("DEPRECATION")
			Intent.ACTION_UNINSTALL_PACKAGE
		}
		context?.startActivity(Intent(action, uri))
	}
}
