package org.skepsun.kototoro.explore.ui

import android.content.DialogInterface
import android.content.Intent
import android.graphics.Rect
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
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import android.content.res.ColorStateList
import com.google.android.material.color.MaterialColors
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.exceptions.resolve.SnackbarErrorObserver
import org.skepsun.kototoro.core.model.LocalMangaSource
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.parser.external.ExternalMangaSource
import org.skepsun.kototoro.core.ui.BaseFragment
import org.skepsun.kototoro.core.ui.dialog.BigButtonsAlertDialog
import org.skepsun.kototoro.core.ui.list.ListSelectionController
import org.skepsun.kototoro.core.ui.list.OnListItemClickListener
import org.skepsun.kototoro.core.ui.util.RecyclerViewOwner
import org.skepsun.kototoro.core.ui.util.ReversibleActionObserver
import org.skepsun.kototoro.core.ui.util.SpanSizeResolver
import org.skepsun.kototoro.core.util.ext.addMenuProvider
import org.skepsun.kototoro.core.util.ext.consumeAll
import org.skepsun.kototoro.core.util.ext.findAppCompatDelegate
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.core.util.ext.observeEvent
import org.skepsun.kototoro.databinding.FragmentExploreBinding
import org.skepsun.kototoro.explore.ui.adapter.ExploreAdapter
import org.skepsun.kototoro.explore.ui.adapter.ExploreListEventListener
import org.skepsun.kototoro.explore.ui.model.MangaSourceItem
import org.skepsun.kototoro.explore.ui.model.SourceTag
import org.skepsun.kototoro.list.ui.adapter.TypedListSpacingDecoration
import org.skepsun.kototoro.list.ui.model.ListHeader
import org.skepsun.kototoro.parsers.model.Manga
import org.skepsun.kototoro.parsers.model.MangaParserSource

@AndroidEntryPoint
class ExploreFragment :
	BaseFragment<FragmentExploreBinding>(),
	RecyclerViewOwner,
	ExploreListEventListener,
	OnListItemClickListener<MangaSourceItem>, ListSelectionController.Callback {

	private val viewModel by viewModels<ExploreViewModel>()
	private var exploreAdapter: ExploreAdapter? = null
	private var sourceSelectionController: ListSelectionController? = null
	private var groupTabsInitialPadding: Rect? = null

	override val recyclerView: RecyclerView?
		get() = viewBinding?.recyclerView

	override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentExploreBinding {
		return FragmentExploreBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: FragmentExploreBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		exploreAdapter = ExploreAdapter(this, this) { manga, view ->
			router.openDetails(manga)
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
		
		// Setup group tabs
		binding.groupTabs.setOnTabSelectedListener { tab ->
			viewModel.setSelectedGroupTab(tab)
		}
		
		// Restore selected tab
		binding.groupTabs.setSelectedTab(viewModel.getSelectedGroupTab())
		
		addMenuProvider(ExploreMenuProvider(router))
		viewModel.content.observe(viewLifecycleOwner, checkNotNull(exploreAdapter))
		viewModel.onError.observeEvent(viewLifecycleOwner, SnackbarErrorObserver(binding.recyclerView, this))
		viewModel.onOpenManga.observeEvent(viewLifecycleOwner, ::onOpenManga)
		viewModel.onActionDone.observeEvent(viewLifecycleOwner, ReversibleActionObserver(binding.recyclerView))
		viewModel.isGrid.observe(viewLifecycleOwner, ::onGridModeChanged)
		viewModel.onShowSuggestionsTip.observeEvent(viewLifecycleOwner) {
			showSuggestionsTip()
		}
		viewModel.currentGroupTab.observe(viewLifecycleOwner) { tab ->
			// Update tab selection if changed programmatically
			if (binding.groupTabs.getSelectedTab() != tab) {
				binding.groupTabs.setSelectedTab(tab)
			}
		}
		viewModel.availableTabs.observe(viewLifecycleOwner) { tabs ->
			binding.groupTabs.setTabs(tabs)
		}
		
		// Setup source tag chips (multi-select)
		setupSourceTagChips(binding)
		
		// Observe tag filter visibility
		viewModel.isSourceFilterVisible.observe(viewLifecycleOwner) { isVisible ->
			binding.adultFilterScrollView.visibility = if (isVisible) View.VISIBLE else View.GONE
		}
		
		// Observe selected tags
		viewModel.currentSourceTags.observe(viewLifecycleOwner) { tags ->
			updateSourceTagChipsSelection(binding, tags)
		}
	}

	private fun setupSourceTagChips(binding: FragmentExploreBinding) {
		val chipGroup = binding.chipGroupAdultFilter
		chipGroup.removeAllViews()
		chipGroup.isSingleSelection = true
		chipGroup.isSelectionRequired = false
		
		// Colors tuned for better contrast and feedback
		val bgUnchecked = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorSurfaceVariant, 0)
		val bgChecked = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorPrimaryContainer, 0)
		val textUnchecked = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOnSurface, 0)
		val textChecked = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOnPrimaryContainer, 0)
		val strokeUnchecked = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOutline, 0)
			val strokeChecked = MaterialColors.getColor(requireContext(), androidx.appcompat.R.attr.colorPrimary, 0)

		val stateChecked = intArrayOf(android.R.attr.state_checked)
		val stateDefault = intArrayOf(-android.R.attr.state_checked)

		val bgColors = ColorStateList(
			arrayOf(stateChecked, stateDefault),
			intArrayOf(bgChecked, bgUnchecked),
		)
		val textColors = ColorStateList(
			arrayOf(stateChecked, stateDefault),
			intArrayOf(textChecked, textUnchecked),
		)
		val strokeColors = ColorStateList(
			arrayOf(stateChecked, stateDefault),
			intArrayOf(strokeChecked, strokeUnchecked),
		)

		SourceTag.entries.forEach { tag ->
			val chip = com.google.android.material.chip.Chip(requireContext()).apply {
				id = View.generateViewId()
				text = getString(tag.titleRes)
				this.tag = tag
				isCheckable = true
				isChecked = tag in viewModel.currentSourceTags.value
				
				// Compact visuals
				val density = resources.displayMetrics.density
				chipMinHeight = 28 * density
				minHeight = 0
				setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
				chipStartPadding = 4 * density
				chipEndPadding = 4 * density
				setEnsureMinTouchTargetSize(false) // allow height < 48dp
				
				chipStrokeWidth = 1 * density
				chipStrokeColor = strokeColors
				chipBackgroundColor = bgColors
				setTextColor(textColors)
			}
			chipGroup.addView(chip)
		}
		
		chipGroup.chipSpacingVertical = 0
		
		chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
			val selectedTags = checkedIds.mapNotNull { id ->
				group.findViewById<com.google.android.material.chip.Chip>(id)?.tag as? SourceTag
			}.toSet()
			viewModel.setSelectedSourceTags(selectedTags)
		}
	}
	
	private fun updateSourceTagChipsSelection(binding: FragmentExploreBinding, tags: Set<SourceTag>) {
		val chipGroup = binding.chipGroupAdultFilter
		for (i in 0 until chipGroup.childCount) {
			val chip = chipGroup.getChildAt(i) as? com.google.android.material.chip.Chip
			chip?.isChecked = chip?.tag in tags
		}
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val barsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
		val basePadding = v.resources.getDimensionPixelOffset(R.dimen.list_spacing_normal)
		val groupTabs = viewBinding?.groupTabs
		if (groupTabsInitialPadding == null && groupTabs != null) {
			groupTabsInitialPadding = Rect(
				groupTabs.paddingLeft,
				groupTabs.paddingTop,
				groupTabs.paddingRight,
				groupTabs.paddingBottom,
			)
		}
		
		// Keep original vertical spacing; only extend horizontal padding for insets
		groupTabsInitialPadding?.let { padding ->
			groupTabs?.setPadding(
				/* left = */ padding.left + barsInsets.left,
				/* top = */ padding.top,
				/* right = */ padding.right + barsInsets.right,
				/* bottom = */ padding.bottom,
			)
		}
		
		// Apply side and bottom insets to recycler view
		viewBinding?.recyclerView?.setPadding(
			/* left = */ barsInsets.left + basePadding,
			/* top = */ basePadding,
			/* right = */ barsInsets.right + basePadding,
			/* bottom = */ barsInsets.bottom + basePadding,
		)
		return insets.consumeAll(WindowInsetsCompat.Type.systemBars())
	}

	override fun onDestroyView() {
		super.onDestroyView()
		sourceSelectionController = null
		exploreAdapter = null
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
			R.id.button_local -> router.openList(LocalMangaSource, null, null)
			R.id.button_bookmarks -> router.openBookmarks()
			R.id.button_more -> router.openSuggestions()
			R.id.button_downloads -> router.openDownloads()
			R.id.button_random -> viewModel.openRandom()
		}
	}

	override fun onItemClick(item: MangaSourceItem, view: View) {
		if (sourceSelectionController?.onItemClick(item.id) == true) {
			return
		}
		router.openList(item.source, null, null)
	}

	override fun onItemLongClick(item: MangaSourceItem, view: View): Boolean {
		return sourceSelectionController?.onItemLongClick(view, item.id) == true
	}

	override fun onItemContextClick(item: MangaSourceItem, view: View): Boolean {
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
			selectedSources.all { it.mangaSource is MangaParserSource }
		menu.findItem(R.id.action_delete)?.isVisible = selectedSources.all { it.mangaSource is ExternalMangaSource }
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
					(it.mangaSource as? ExternalMangaSource)?.let { uninstallExternalSource(it) }
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

	private fun onOpenManga(manga: Manga) {
		router.openDetails(manga)
	}

	private fun onGridModeChanged(isGrid: Boolean) {
		requireViewBinding().recyclerView.layoutManager = if (isGrid) {
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

	private fun uninstallExternalSource(source: ExternalMangaSource) {
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
