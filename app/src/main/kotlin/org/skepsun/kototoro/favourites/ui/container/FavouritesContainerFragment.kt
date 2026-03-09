package org.skepsun.kototoro.favourites.ui.container

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.content.res.ColorStateList
import android.os.Build
import androidx.appcompat.view.ActionMode
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.updatePadding
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.color.MaterialColors
import dagger.hilt.android.AndroidEntryPoint
import android.widget.Toast
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.ui.BaseFragment
import org.skepsun.kototoro.core.ui.util.ActionModeListener
import org.skepsun.kototoro.core.ui.util.RecyclerViewOwner
import org.skepsun.kototoro.core.ui.util.ReversibleActionObserver
import org.skepsun.kototoro.core.util.ext.addMenuProvider
import org.skepsun.kototoro.core.util.ext.findCurrentPagerFragment
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.core.util.ext.observeEvent
import org.skepsun.kototoro.core.util.ext.recyclerView
import org.skepsun.kototoro.core.util.ext.setTabsEnabled
import org.skepsun.kototoro.core.util.ext.setTextAndVisible
import org.skepsun.kototoro.databinding.FragmentFavouritesContainerBinding
import org.skepsun.kototoro.databinding.ItemEmptyStateBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import org.skepsun.kototoro.core.prefs.ListMode
import org.skepsun.kototoro.explore.ui.model.BrowseGroupTab
import org.skepsun.kototoro.explore.ui.model.SourceTag
import org.skepsun.kototoro.list.ui.model.QuickFilter
import org.skepsun.kototoro.list.domain.ListFilterOption
import com.google.android.material.chip.Chip
import com.google.android.material.appbar.AppBarLayout
import org.skepsun.kototoro.main.ui.owners.AppBarOwner

@AndroidEntryPoint
class FavouritesContainerFragment : BaseFragment<FragmentFavouritesContainerBinding>(),
	ActionModeListener,
	RecyclerViewOwner,
	ViewStub.OnInflateListener,
	View.OnClickListener,
	AppBarLayout.OnOffsetChangedListener {

	private val viewModel: FavouritesContainerViewModel by viewModels()
	private val contentTypeChipIds = mutableMapOf<BrowseGroupTab, Int>()
	private val sourceTagChipIds = mutableMapOf<SourceTag, Int>()

	override val recyclerView: RecyclerView?
		get() = (findCurrentFragment() as? RecyclerViewOwner)?.recyclerView

	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	) = FragmentFavouritesContainerBinding.inflate(inflater, container, false)

	override fun onViewBindingCreated(binding: FragmentFavouritesContainerBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		val pagerAdapter = FavouritesContainerAdapter(this)
		binding.pager.adapter = pagerAdapter
		binding.pager.offscreenPageLimit = 1
		binding.pager.recyclerView?.isNestedScrollingEnabled = false
		TabLayoutMediator(
			binding.tabs,
			binding.pager,
			FavouritesTabConfigurationStrategy(pagerAdapter, viewModel, router),
		).attach()
		binding.stubEmpty.setOnInflateListener(this)
		actionModeDelegate.addListener(this)
		viewModel.categories.observe(viewLifecycleOwner, pagerAdapter)
		viewModel.isEmpty.observe(viewLifecycleOwner, ::onEmptyStateChanged)
		addMenuProvider(FavouritesContainerMenuProvider(router, { showImportDialog() }, { showSyncDialog() }))
		viewModel.onActionDone.observeEvent(viewLifecycleOwner, ReversibleActionObserver(binding.pager))
		viewLifecycleOwner.lifecycleScope.launch {
			viewModel.importMessages.collect { event ->
				event?.consume { msg ->
					if (msg.isNotBlank()) {
						Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
					}
				}
			}
		}
		viewLifecycleOwner.lifecycleScope.launch {
			viewModel.syncMessages.collect { event ->
				event?.consume { msg ->
					if (msg.isNotBlank()) {
						Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
					}
				}
			}
		}

		rebuildContentTypeChips(binding)
		viewModel.currentGroupTab.observe(viewLifecycleOwner) { tab ->
			updateContentTypeChipsSelection(binding, tab)
			updateSourceTagChipsEnabled(binding, tab)
		}
		viewModel.availableSourceTags.observe(viewLifecycleOwner) { 
			rebuildSourceTagChips(binding)
			updateSourceTagChipsSelection(binding, viewModel.selectedSourceTags.value)
			updateContentTypeChipsEnabled(binding, viewModel.selectedSourceTags.value)
		}
		viewModel.selectedSourceTags.observe(viewLifecycleOwner) { tags ->
			updateSourceTagChipsSelection(binding, tags)
			updateContentTypeChipsEnabled(binding, tags)
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

		actionModeDelegate.removeListener(this)
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

	private fun rebuildContentTypeChips(binding: FragmentFavouritesContainerBinding) {
		binding.chipGroupContentType.removeAllViews()
		contentTypeChipIds.clear()
		val tabs = listOf(
			BrowseGroupTab.Manga,
			BrowseGroupTab.Novel,
			BrowseGroupTab.Video,
		)
		
		val colors = createChipColors()
		val density = resources.displayMetrics.density

		tabs.forEach { tab ->
			val chip = createCompactChip(
				text = getString(tab.titleRes),
				iconRes = tab.iconRes,
				colors = colors,
				density = density,
			)
			chip.isChecked = tab == viewModel.currentGroupTab.value
			chip.setOnClickListener { 
				val isChecked = (it as Chip).isChecked
				if (isChecked) {
					viewModel.setSelectedGroupTab(tab)
				} else {
					viewModel.setSelectedGroupTab(BrowseGroupTab.All)
				}
			}
			contentTypeChipIds[tab] = chip.id
			binding.chipGroupContentType.addView(chip)
		}

		updateContentTypeChipsSelection(binding, viewModel.currentGroupTab.value)
		updateContentTypeChipsEnabled(binding, viewModel.selectedSourceTags.value)
	}

	private fun updateContentTypeChipsSelection(binding: FragmentFavouritesContainerBinding, selectedTab: BrowseGroupTab) {
		contentTypeChipIds.forEach { (tab, id) ->
			binding.chipGroupContentType.findViewById<Chip>(id)?.isChecked = (tab == selectedTab)
		}
	}

	private fun rebuildSourceTagChips(binding: FragmentFavouritesContainerBinding) {
		val available = viewModel.availableSourceTags.value
		binding.chipGroupSourceTag.removeAllViews()
		sourceTagChipIds.clear()
		
		if (available.isEmpty()) {
			binding.chipGroupSourceTag.isVisible = false
			binding.filterSeparator.isVisible = false
			return
		}
		binding.chipGroupSourceTag.isVisible = true
		binding.filterSeparator.isVisible = true

		val colors = createChipColors()
		val density = resources.displayMetrics.density

		available.forEach { tag ->
			val chip = createCompactChip(
				text = getString(tag.titleRes),
				iconRes = tag.iconRes,
				colors = colors,
				density = density,
			)
			chip.setOnClickListener { 
				viewModel.toggleSourceTag(tag)
			}
			sourceTagChipIds[tag] = chip.id
			binding.chipGroupSourceTag.addView(chip)
		}

		updateSourceTagChipsSelection(binding, viewModel.selectedSourceTags.value)
		updateSourceTagChipsEnabled(binding, viewModel.currentGroupTab.value)
	}

	private fun updateSourceTagChipsSelection(binding: FragmentFavouritesContainerBinding, tags: Set<SourceTag>) {
		sourceTagChipIds.forEach { (tag, id) ->
			binding.chipGroupSourceTag.findViewById<Chip>(id)?.isChecked = (tag in tags)
		}
	}

	private fun updateContentTypeChipsEnabled(binding: FragmentFavouritesContainerBinding, selectedTags: Set<SourceTag>) {
		contentTypeChipIds.forEach { (tab, id) ->
			val chip = binding.chipGroupContentType.findViewById<Chip>(id) ?: return@forEach
			chip.isEnabled = selectedTags.isEmpty() || selectedTags.any { it.supportsContentTab(tab) }
			chip.alpha = if (chip.isEnabled) 1.0f else 0.5f
		}
	}

	private fun updateSourceTagChipsEnabled(binding: FragmentFavouritesContainerBinding, selectedTab: BrowseGroupTab) {
		sourceTagChipIds.forEach { (tag, id) ->
			val chip = binding.chipGroupSourceTag.findViewById<Chip>(id) ?: return@forEach
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
		val context = requireContext()
		val bgUnchecked = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceVariant, 0)
		val bgChecked = MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimaryContainer, 0)
		val bgDisabled = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, 0)
		
		val textUnchecked = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0)
		val textChecked = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnPrimaryContainer, 0)
		val textDisabled = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, 0).let {
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
		@androidx.annotation.DrawableRes iconRes: Int,
		colors: ChipColors,
		density: Float,
	): Chip {
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
		
		return insets
	}

	override fun onActionModeStarted(mode: ActionMode) {
		viewBinding?.run {
			pager.isUserInputEnabled = false
			tabs.setTabsEnabled(false)
		}
	}

	override fun onActionModeFinished(mode: ActionMode) {
		viewBinding?.run {
			pager.isUserInputEnabled = true
			tabs.setTabsEnabled(true)
		}
	}

	override fun onInflate(stub: ViewStub?, inflated: View) {
		val stubBinding = ItemEmptyStateBinding.bind(inflated)
		stubBinding.icon.setImageAsync(R.drawable.ic_empty_favourites)
		stubBinding.textPrimary.setText(R.string.text_empty_holder_primary)
		stubBinding.textSecondary.setTextAndVisible(R.string.empty_favourite_categories)
		stubBinding.buttonRetry.setTextAndVisible(R.string.manage)
		stubBinding.buttonRetry.setOnClickListener(this)
	}

	override fun onClick(v: View) {
		when (v.id) {
			R.id.button_retry -> router.openFavoriteCategories()
		}
	}

	private fun onEmptyStateChanged(isEmpty: Boolean) {
		viewBinding?.run {
			pager.isGone = isEmpty
			tabs.isGone = isEmpty
			stubEmpty.isVisible = isEmpty
		}
	}

	private fun findCurrentFragment(): Fragment? {
		return childFragmentManager.findCurrentPagerFragment(
			viewBinding?.pager ?: return null,
		)
	}

	private fun showImportDialog() {
		viewLifecycleOwner.lifecycleScope.launch {
			val candidates = viewModel.loadImportCandidates()
			if (candidates.isEmpty()) {
				Toast.makeText(requireContext(), R.string.import_favourites_no_available, Toast.LENGTH_SHORT).show()
				return@launch
			}
			val titles = candidates.map { it.title }.toTypedArray()
			val checked = BooleanArray(titles.size) { true }
			MaterialAlertDialogBuilder(requireContext())
				.setTitle(R.string.import_favourites_title)
				.setMultiChoiceItems(titles, checked) { _, which, isChecked ->
					checked[which] = isChecked
				}
				.setNegativeButton(android.R.string.cancel, null)
				.setPositiveButton(R.string.import_favourites) { _, _ ->
					val selectedIndices = candidates.indices.filter { checked[it] }
					if (selectedIndices.isEmpty()) return@setPositiveButton
					
					viewLifecycleOwner.lifecycleScope.launch {
						val finalSelected = mutableListOf<FavouritesContainerViewModel.ImportSource>()
						for (i in selectedIndices) {
							val candidate = candidates[i]
							val folders = viewModel.loadFavoriteFolders(candidate.source)
							if (folders.size > 1) {
								val folderTitles = folders.map { it.title }.toTypedArray()
								val folderChecked = BooleanArray(folderTitles.size) { true }
								val chosen = showFolderDialog(candidate.title, folderTitles, folderChecked)
								if (chosen != null) {
									val selectedFolders = folders.filterIndexed { index, _ -> chosen[index] }
									if (selectedFolders.isNotEmpty()) {
										finalSelected.add(candidate.copy(folders = selectedFolders))
									}
								}
							} else {
								finalSelected.add(candidate)
							}
						}
						if (finalSelected.isNotEmpty()) {
							viewModel.importFavorites(finalSelected)
						}
					}
				}
				.show()
		}
	}

	private suspend fun showFolderDialog(
		sourceTitle: String,
		titles: Array<String>,
		checked: BooleanArray,
	): BooleanArray? = suspendCancellableCoroutine { continuation ->
		MaterialAlertDialogBuilder(requireContext())
			.setTitle(sourceTitle)
			.setMultiChoiceItems(titles, checked) { _, which, isChecked ->
				checked[which] = isChecked
			}
			.setPositiveButton(android.R.string.ok) { _, _ ->
				continuation.resume(checked)
			}
			.setNegativeButton(android.R.string.cancel) { _, _ ->
				continuation.resume(null)
			}
			.setOnCancelListener {
				continuation.resume(null)
			}
			.show()
	}

	private fun showSyncDialog() {
		viewLifecycleOwner.lifecycleScope.launch {
			val candidates = viewModel.loadSyncCandidates()
			if (candidates.isEmpty()) {
				Toast.makeText(requireContext(), R.string.import_favourites_no_available, Toast.LENGTH_SHORT).show()
				return@launch
			}
			val titles = candidates.map { it.title }.toTypedArray()
			val checked = BooleanArray(titles.size) { true }
			MaterialAlertDialogBuilder(requireContext())
				.setTitle(R.string.sync_favourites_title)
				.setMultiChoiceItems(titles, checked) { _, which, isChecked ->
					checked[which] = isChecked
				}
				.setNegativeButton(android.R.string.cancel, null)
				.setPositiveButton(R.string.sync_favourites) { _, _ ->
					val selected = candidates.indices
						.filter { checked[it] }
						.map { candidates[it] }
					if (selected.isEmpty()) return@setPositiveButton
					MaterialAlertDialogBuilder(requireContext())
						.setTitle(R.string.sync_favourites_title)
						.setMessage(R.string.sync_favourites_warning)
						.setNegativeButton(android.R.string.cancel, null)
						.setPositiveButton(R.string.sync_favourites) { _, _ ->
							viewModel.syncFavorites(selected)
						}
						.show()
				}
				.show()
		}
	}
}
