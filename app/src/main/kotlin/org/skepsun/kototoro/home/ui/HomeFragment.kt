package org.skepsun.kototoro.home.ui

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView

import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.ui.BaseFragment
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.core.util.ext.observeEvent
import org.skepsun.kototoro.databinding.FragmentHomeBinding
import org.skepsun.kototoro.explore.ui.model.BrowseGroupTab
import org.skepsun.kototoro.main.ui.owners.AppBarOwner
import org.skepsun.kototoro.main.ui.owners.BottomNavOwner
import org.skepsun.kototoro.main.ui.SearchBarFilterMenuProvider
import org.skepsun.kototoro.explore.ui.model.SourceTag
import kotlin.math.abs

@AndroidEntryPoint
class HomeFragment : BaseFragment<FragmentHomeBinding>(), SearchBarFilterMenuProvider.Callback {

	private val viewModel by viewModels<HomeViewModel>()
	private val recentCoverAdapter by lazy { HomeCoverAdapter { router.openDetails(it) } }
	private val updateCoverAdapter by lazy { HomeCoverAdapter { router.openDetails(it) } }
	private val recommendationCoverAdapter by lazy { HomeCoverAdapter { router.openDetails(it) } }
	private var homeScrollAnchorY = 0
	private var isHomeChromeHidden = false
	private var filterMenuProvider: SearchBarFilterMenuProvider? = null

	override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentHomeBinding {
		return FragmentHomeBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: FragmentHomeBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		with(binding) {
			buttonSettings.setOnClickListener { router.openSettings() }
			buttonReaderSettings.setOnClickListener { router.openReaderSettings() }
			buttonSyncSettings.setOnClickListener { router.openSyncSettings() }
			buttonSyncBackup.setOnClickListener { viewModel.uploadWebDavNow() }
			buttonSyncRestore.setOnClickListener { viewModel.restoreWebDavNow() }
			buttonViewAllRecent.setOnClickListener { router.openHistory(currentBrowseGroupTab()) }
			buttonViewAllUpdates.setOnClickListener { router.openMangaUpdates(currentBrowseGroupTab()) }
			buttonViewAllRecommendations.setOnClickListener { router.openSuggestions(currentBrowseGroupTab()) }
			buttonSourceSettings.setOnClickListener { router.openSourcesSettings() }
			buttonLibraryOpen.setOnClickListener { router.openFavorites() }
			buttonBookmarks.setOnClickListener { router.openBookmarks() }
			buttonLocal.setOnClickListener { router.openList(org.skepsun.kototoro.core.model.LocalMangaSource, null, null) }
			buttonDownloads.setOnClickListener { router.openDownloads() }
			buttonRandom.setOnClickListener { viewModel.openRandom() }
			buttonAutoTranslate.setOnClickListener { router.openTranslationSettings() }
			setupCoverStrip(recyclerViewRecentHistory, recentCoverAdapter)
			setupCoverStrip(recyclerViewRecentUpdates, updateCoverAdapter)
			setupCoverStrip(recyclerViewRecommendations, recommendationCoverAdapter)
		}
		
		val searchBar = (activity as? AppBarOwner)?.appBar?.let { appBar ->
			appBar.findViewById<View>(R.id.search_bar)
		} ?: activity?.findViewById(R.id.search_bar)

		if (searchBar != null) {
			filterMenuProvider = SearchBarFilterMenuProvider(this, searchBar)
			requireActivity().addMenuProvider(filterMenuProvider!!, viewLifecycleOwner)
		}
		setupHomeScrollChrome(binding.root)
		showHomeChrome()

		viewModel.isRandomLoading.observe(viewLifecycleOwner) { isLoading ->
			binding.buttonRandom.isEnabled = !isLoading
			binding.buttonRandom.alpha = if (isLoading) 0.5f else 1.0f
		}
		viewModel.onOpenContent.observeEvent(viewLifecycleOwner) { manga ->
			router.openDetails(manga)
		}

		viewModel.summaryState.observe(viewLifecycleOwner) { state ->
			syncSelectedTab(state.selectedTab)
			binding.textViewRecentCount.text = state.recentHistoryCount.toString()
			binding.textViewUpdatesCount.text = state.unreadUpdatesCount.toString()
			binding.textViewRecommendationsCount.text = state.recommendationsCount.toString()
			val isWebDavConfigured = state.syncState.isWebDavEnabled
			binding.buttonSyncBackup.visibility = if (isWebDavConfigured) View.VISIBLE else View.GONE
			binding.buttonSyncRestore.visibility = if (isWebDavConfigured) View.VISIBLE else View.GONE

			val syncStatusText = when {
				state.syncState.isWebDavEnabled && state.syncState.isAutoSyncEnabled -> getString(R.string.home_sync_status_auto)
				state.syncState.isWebDavEnabled -> getString(R.string.home_sync_status_ready)
				else -> getString(R.string.home_sync_status_not_configured)
			}
			binding.textViewSyncStatus.text = syncStatusText
			binding.textViewSyncSubtitle.text = when {
				state.syncState.lastUploadTime > 0L -> {
					getString(
						R.string.home_sync_last_upload,
						DateUtils.getRelativeTimeSpanString(
							state.syncState.lastUploadTime,
							System.currentTimeMillis(),
							DateUtils.MINUTE_IN_MILLIS,
						),
					)
				}
				state.syncState.isWebDavEnabled -> getString(R.string.home_sync_subtitle_ready)
				else -> getString(R.string.home_sync_subtitle_configure)
			}


			recentCoverAdapter.submitContents(state.recentHistoryItems.map { it.content })
			updateCoverAdapter.submitContents(state.recentUpdates.map { it.content })
			recommendationCoverAdapter.submitContents(state.recommendations.map { it.content })

			val sourceBreakdown = state.sourceBreakdown
			val sourceViews = listOf(
				binding.textViewSourceItem1,
				binding.textViewSourceItem2,
				binding.textViewSourceItem3,
			)
			sourceViews.forEachIndexed { index, textView ->
				val item = sourceBreakdown.getOrNull(index)
				textView.text = if (item != null) {
					getString(
						R.string.home_source_breakdown_item,
						getSourceOriginLabel(item.origin),
						item.count,
					)
				} else {
					getString(R.string.home_source_breakdown_empty)
				}
				textView.alpha = if (item != null) 1f else 0.6f
			}
		}
	}

	override fun onApplyWindowInsets(view: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
		requireViewBinding().root.updatePadding(
			left = systemBars.left,
			right = systemBars.right,
			bottom = systemBars.bottom,
		)
		requireViewBinding().homeContentContainer.updatePadding(
			bottom = systemBars.bottom + resources.getDimensionPixelOffset(R.dimen.list_spacing_normal),
		)
		return insets
	}

	private fun syncSelectedTab(tab: HomeContentTab?) {
		filterMenuProvider?.updateIcons()
	}

	private fun currentBrowseGroupTab(): BrowseGroupTab {
		return when (viewModel.summaryState.value.selectedTab) {
			HomeContentTab.MANGA -> BrowseGroupTab.Content
			HomeContentTab.NOVEL -> BrowseGroupTab.Novel
			HomeContentTab.VIDEO -> BrowseGroupTab.Video
			null -> BrowseGroupTab.All
		}
	}

	override fun onDestroyView() {
		requireViewBinding().root.setOnScrollChangeListener(null as NestedScrollView.OnScrollChangeListener?)
		showHomeChrome()
		super.onDestroyView()
	}

	private fun setupCoverStrip(recyclerView: RecyclerView, adapter: HomeCoverAdapter) {
		recyclerView.layoutManager = LinearLayoutManager(recyclerView.context, RecyclerView.HORIZONTAL, false)
		recyclerView.adapter = adapter
		recyclerView.isNestedScrollingEnabled = false
	}

	private fun getSourceOriginLabel(origin: HomeSourceOrigin): String {
		return when (origin) {
			HomeSourceOrigin.BUILT_IN -> getString(R.string.source_type_native)
			HomeSourceOrigin.MIHON -> getString(R.string.source_type_mihon)
			HomeSourceOrigin.ANIYOMI -> getString(R.string.source_type_aniyomi)
			HomeSourceOrigin.LEGADO -> getString(R.string.source_type_legado)
			HomeSourceOrigin.TVBOX -> getString(R.string.source_type_tvbox)
			HomeSourceOrigin.EXTERNAL -> getString(R.string.external_source)
			HomeSourceOrigin.IREADER -> getString(R.string.source_type_ireader)
		}
	}

	private fun setupHomeScrollChrome(scrollView: NestedScrollView) {
		val scrollThreshold = resources.getDimensionPixelOffset(R.dimen.list_spacing_large) * 2
		homeScrollAnchorY = scrollView.scrollY
		scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
			val delta = scrollY - homeScrollAnchorY
			when {
				scrollY <= 0 -> {
					homeScrollAnchorY = 0
					showHomeChrome()
				}

				abs(delta) < scrollThreshold -> Unit

				delta > 0 -> {
					homeScrollAnchorY = scrollY
					hideHomeChrome()
				}

				else -> {
					homeScrollAnchorY = scrollY
					showHomeChrome()
				}
			}
		}
	}

	private fun hideHomeChrome() {
		if (isHomeChromeHidden) return
		(activity as? AppBarOwner)?.appBar?.setExpanded(false, true)
		(activity as? BottomNavOwner)?.bottomNav?.hide()
		isHomeChromeHidden = true
	}

	private fun showHomeChrome() {
		(activity as? AppBarOwner)?.appBar?.setExpanded(true, true)
		(activity as? BottomNavOwner)?.bottomNav?.show()
		isHomeChromeHidden = false
	}
	
	// === SearchBarFilterMenuProvider.Callback implementation ===

	override fun onContentTypeSelected(tab: BrowseGroupTab) {
		val homeTab = when (tab) {
			BrowseGroupTab.Content -> HomeContentTab.MANGA
			BrowseGroupTab.Novel -> HomeContentTab.NOVEL
			BrowseGroupTab.Video -> HomeContentTab.VIDEO
			else -> null
		}
		viewModel.setSelectedTab(homeTab)
	}

	override fun onSourceTagSelected(tag: SourceTag?) {
		val selectedTags = if (tag != null) setOf(tag) else emptySet()
		viewModel.setSelectedSourceTags(selectedTags)
	}

	override fun getSelectedContentType(): BrowseGroupTab {
		return currentBrowseGroupTab()
	}

	override fun getSelectedSourceTags(): Set<SourceTag> {
		return viewModel.summaryState.value.selectedSourceTags
	}

	override fun isContentTypeFilterVisible(): Boolean = true

	override fun isSourceTagFilterVisible(): Boolean = true

	override fun isContentTypeEnabled(tab: BrowseGroupTab): Boolean {
		val selectedTags = getSelectedSourceTags()
		return selectedTags.isEmpty() || selectedTags.any { it.supportsContentTab(tab) }
	}

	override fun isSourceTagEnabled(tag: SourceTag): Boolean {
		return getSelectedContentType().supportsSourceTag(tag)
	}
}
