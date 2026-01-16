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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import android.content.res.ColorStateList
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.chip.Chip
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
import org.skepsun.kototoro.core.util.ext.findAppCompatDelegate
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.core.util.ext.observeEvent
import org.skepsun.kototoro.databinding.FragmentExploreBinding
import org.skepsun.kototoro.explore.ui.adapter.ExploreAdapter
import org.skepsun.kototoro.explore.ui.adapter.ExploreListEventListener
import org.skepsun.kototoro.explore.ui.model.BrowseGroupTab
import org.skepsun.kototoro.explore.ui.model.MangaSourceItem
import org.skepsun.kototoro.explore.ui.model.SourceTag
import org.skepsun.kototoro.list.ui.adapter.TypedListSpacingDecoration
import org.skepsun.kototoro.list.ui.model.ListHeader
import org.skepsun.kototoro.main.ui.owners.AppBarOwner
import org.skepsun.kototoro.parsers.model.Manga
import org.skepsun.kototoro.parsers.model.MangaParserSource

@AndroidEntryPoint
class ExploreFragment :
	BaseFragment<FragmentExploreBinding>(),
	RecyclerViewOwner,
	ExploreListEventListener,
	OnListItemClickListener<MangaSourceItem>, ListSelectionController.Callback,
	AppBarLayout.OnOffsetChangedListener {

	private val viewModel by viewModels<ExploreViewModel>()
	private var exploreAdapter: ExploreAdapter? = null
	private var sourceSelectionController: ListSelectionController? = null
	
	// Track chip IDs for each filter group
	private val contentTypeChipIds = mutableMapOf<BrowseGroupTab, Int>()
	private val sourceTagChipIds = mutableMapOf<SourceTag, Int>()

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
		
		// Setup filter chips
		rebuildContentTypeChips(binding, viewModel.availableTabs.value ?: BrowseGroupTab.getAllTabs())
		rebuildSourceTagChips(binding)
		
		addMenuProvider(ExploreMenuProvider(router))
		viewModel.content.observe(viewLifecycleOwner, checkNotNull(exploreAdapter))
		viewModel.onError.observeEvent(viewLifecycleOwner, SnackbarErrorObserver(binding.recyclerView, this))
		viewModel.onOpenManga.observeEvent(viewLifecycleOwner, ::onOpenManga)
		viewModel.onActionDone.observeEvent(viewLifecycleOwner, ReversibleActionObserver(binding.recyclerView))
		viewModel.isGrid.observe(viewLifecycleOwner, ::onGridModeChanged)
		viewModel.onShowSuggestionsTip.observeEvent(viewLifecycleOwner) {
			showSuggestionsTip()
		}
		
		// Observe filter changes
		viewModel.currentGroupTab.observe(viewLifecycleOwner) { tab ->
			updateContentTypeChipsSelection(binding, tab)
		}
		viewModel.currentSourceTags.observe(viewLifecycleOwner) { tags ->
			updateSourceTagChipsSelection(binding, tags)
		}
		viewModel.availableTabs.observe(viewLifecycleOwner) { tabs ->
			rebuildContentTypeChips(binding, tabs)
		}

		// Register for appbar offset changes to handle sticky inset padding
		val appBar = (activity as? AppBarOwner)?.appBar
		appBar?.addOnOffsetChangedListener(this)

		// Remove snap flag from ALL children for this screen to make scroll feel multi-stage and linear
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

		// Restore snap flag when leaving
		appBar?.children?.forEach { child ->
			val lp = child.layoutParams as? AppBarLayout.LayoutParams
			if (lp != null && (child.id == R.id.search_bar || child.id == R.id.insetsHolder)) {
				lp.scrollFlags = lp.scrollFlags or AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP
				child.layoutParams = lp
			}
		}

		super.onDestroyView()
		sourceSelectionController = null
		exploreAdapter = null
		contentTypeChipIds.clear()
		sourceTagChipIds.clear()
	}

	override fun onOffsetChanged(appBarLayout: AppBarLayout?, verticalOffset: Int) {
		val binding = viewBinding ?: return
		val appBar = appBarLayout ?: return

		val insets = ViewCompat.getRootWindowInsets(binding.root)?.getInsets(WindowInsetsCompat.Type.statusBars())
		val statusBarHeight = insets?.top ?: 0

		// Improved Logic: Pin tags exactly to the status bar once the appbar scrolls past it.
		// Padding needed = max(0, statusBarHeight - appBar.bottom)
		// This doesn't care about scroll range or snap states, it's a direct geometric constraint.
		val topPadding = Math.max(0, statusBarHeight - appBar.bottom)

		binding.filterScrollView.updatePadding(top = topPadding)
	}

	private fun rebuildContentTypeChips(binding: FragmentExploreBinding, tabs: List<BrowseGroupTab>) {
		val chipGroup = binding.chipGroupContentType
		chipGroup.removeAllViews()
		contentTypeChipIds.clear()
		
		val colors = createChipColors()
		val density = resources.displayMetrics.density
		
		tabs.forEach { tab ->
			if (tab == BrowseGroupTab.All) return@forEach
			val chip = createCompactChip(
				text = getString(tab.titleRes),
				colors = colors,
				density = density,
			)
			chip.tag = tab
			chip.isChecked = tab == viewModel.getSelectedGroupTab()
			chip.setOnCheckedChangeListener { _, isChecked ->
				if (isChecked) {
					viewModel.setSelectedGroupTab(tab)
				} else if (viewModel.getSelectedGroupTab() == tab) {
					// If the currently selected chip is unchecked, revert to All
					viewModel.setSelectedGroupTab(BrowseGroupTab.All)
				}
			}
			contentTypeChipIds[tab] = chip.id
			chipGroup.addView(chip)
		}
	}
	
	private fun rebuildSourceTagChips(binding: FragmentExploreBinding) {
		val chipGroup = binding.chipGroupSourceTag
		chipGroup.removeAllViews()
		sourceTagChipIds.clear()
		
		val colors = createChipColors()
		val density = resources.displayMetrics.density
		val currentTags = viewModel.currentSourceTags.value ?: emptySet()
		
		SourceTag.entries.forEach { tag ->
			val chip = createCompactChip(
				text = getString(tag.titleRes),
				colors = colors,
				density = density,
			)
			chip.tag = tag
			chip.isChecked = tag in currentTags
			chip.setOnCheckedChangeListener { _, isChecked ->
				val selectedTags = if (isChecked) setOf(tag) else emptySet()
				viewModel.setSelectedSourceTags(selectedTags)
			}
			sourceTagChipIds[tag] = chip.id
			chipGroup.addView(chip)
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
		val textUnchecked = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOnSurface, 0)
		val textChecked = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOnPrimaryContainer, 0)
		val strokeUnchecked = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOutline, 0)
		val strokeChecked = MaterialColors.getColor(requireContext(), androidx.appcompat.R.attr.colorPrimary, 0)
		
		val stateChecked = intArrayOf(android.R.attr.state_checked)
		val stateDefault = intArrayOf(-android.R.attr.state_checked)
		
		return ChipColors(
			bg = ColorStateList(arrayOf(stateChecked, stateDefault), intArrayOf(bgChecked, bgUnchecked)),
			text = ColorStateList(arrayOf(stateChecked, stateDefault), intArrayOf(textChecked, textUnchecked)),
			stroke = ColorStateList(arrayOf(stateChecked, stateDefault), intArrayOf(strokeChecked, strokeUnchecked)),
		)
	}
	
	private fun createCompactChip(
		text: String,
		colors: ChipColors,
		density: Float,
	): Chip {
		return Chip(requireContext()).apply {
			id = View.generateViewId()
			this.text = text
			isCheckable = true
			
			// Compact visuals
			chipMinHeight = 26 * density
			minHeight = 0
			setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
			chipStartPadding = 6 * density
			chipEndPadding = 6 * density
			setEnsureMinTouchTargetSize(false)
			
			chipStrokeWidth = 1 * density
			chipStrokeColor = colors.stroke
			chipBackgroundColor = colors.bg
			setTextColor(colors.text)
		}
	}
	
	private fun updateContentTypeChipsSelection(binding: FragmentExploreBinding, selectedTab: BrowseGroupTab) {
		contentTypeChipIds.forEach { (tab, id) ->
			binding.chipGroupContentType.findViewById<Chip>(id)?.isChecked = (tab == selectedTab)
		}
	}
	
	private fun updateSourceTagChipsSelection(binding: FragmentExploreBinding, tags: Set<SourceTag>) {
		sourceTagChipIds.forEach { (tag, id) ->
			binding.chipGroupSourceTag.findViewById<Chip>(id)?.isChecked = (tag in tags)
		}
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val barsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
		val sidePadding = v.resources.getDimensionPixelOffset(R.dimen.list_spacing_normal)

		// Apply side padding to tags container so they aren't cut off by notches/rounded corners
		viewBinding?.filterChipsContainer?.updatePadding(
			left = barsInsets.left + sidePadding,
			right = barsInsets.right + sidePadding,
		)

		// RecyclerView handles side and bottom insets
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
