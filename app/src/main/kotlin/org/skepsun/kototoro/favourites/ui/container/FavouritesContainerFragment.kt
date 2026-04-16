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
import org.skepsun.kototoro.core.util.ext.addSupportMenuProvider
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
import com.google.android.material.appbar.AppBarLayout
import org.skepsun.kototoro.main.ui.SearchBarFilterViewController

@AndroidEntryPoint
class FavouritesContainerFragment : BaseFragment<FragmentFavouritesContainerBinding>(),
	ActionModeListener,
	RecyclerViewOwner,
	ViewStub.OnInflateListener,
	View.OnClickListener,
	AppBarLayout.OnOffsetChangedListener,
	SearchBarFilterViewController.Callback {

	private val viewModel: FavouritesContainerViewModel by viewModels()
	
	@javax.inject.Inject
	lateinit var settings: org.skepsun.kototoro.core.prefs.AppSettings

	private var filterMenuProvider: SearchBarFilterViewController? = null

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
		addSupportMenuProvider(FavouritesContainerMenuProvider(router, { showImportDialog() }, { showSyncDialog() }))
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

		// SearchBar filter icons are now Compose based
		val searchBar: android.view.View? = null

		if (searchBar != null) {
			filterMenuProvider = SearchBarFilterViewController(this)
			filterMenuProvider?.attachTo(this)
			
		}

		viewModel.currentGroupTab.observe(viewLifecycleOwner) { _ ->
			filterMenuProvider?.updateIcons()
		}
		viewModel.availableSourceTags.observe(viewLifecycleOwner) {
			filterMenuProvider?.updateVisibility()
			filterMenuProvider?.updateIcons()
		}
		viewModel.selectedSourceTags.observe(viewLifecycleOwner) { _ ->
			filterMenuProvider?.updateIcons()
		}
	}

	override fun onDestroyView() {
		filterMenuProvider = null
		actionModeDelegate.removeListener(this)
		super.onDestroyView()
	}

	override fun onOffsetChanged(appBarLayout: AppBarLayout?, verticalOffset: Int) {
		// No longer need to adjust filterScrollView padding
	}

	// === SearchBarFilterViewController.Callback implementation ===

	override fun onContentTypeSelected(tab: BrowseGroupTab) {
		viewModel.setSelectedGroupTab(tab)
	}

	override fun onSourceTagSelected(tag: SourceTag?) {
		if (tag != null) {
			viewModel.toggleSourceTag(tag)
		} else {
			// Clear all source tags
			viewModel.selectedSourceTags.value.forEach { viewModel.toggleSourceTag(it) }
		}
	}

	override fun getSelectedContentType(): BrowseGroupTab = viewModel.currentGroupTab.value

	override fun getSelectedSourceTags(): Set<SourceTag> = viewModel.selectedSourceTags.value

	override fun getSourceTagEntries(): List<SourceTag> = viewModel.availableSourceTags.value.toList()

	override fun isLanguagePresetFilterVisible(): Boolean = settings.isShowLanguagePresetFilter
	override fun isContentTypeFilterVisible(): Boolean = settings.isShowContentTypeFilter && true

	override fun isSourceTagFilterVisible(): Boolean = settings.isShowSourceTagFilter && true

	override fun isContentTypeEnabled(tab: BrowseGroupTab): Boolean {
		val selectedTags = viewModel.selectedSourceTags.value
		return selectedTags.isEmpty() || selectedTags.any { it.supportsContentTab(tab) }
	}

	override fun isSourceTagEnabled(tag: SourceTag): Boolean {
		return viewModel.currentGroupTab.value.supportsSourceTag(tag)
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
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

