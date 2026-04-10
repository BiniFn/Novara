package org.skepsun.kototoro.home.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.ui.BaseFragment
import org.skepsun.kototoro.core.util.ext.observeEvent
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.databinding.FragmentHomeBinding
import org.skepsun.kototoro.explore.ui.model.BrowseGroupTab
import org.skepsun.kototoro.main.ui.owners.AppBarOwner
import org.skepsun.kototoro.main.ui.owners.BottomNavOwner
import org.skepsun.kototoro.main.ui.SearchBarFilterViewController
import org.skepsun.kototoro.explore.ui.model.SourceTag
import org.skepsun.kototoro.home.ui.compose.HomeScreen
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import androidx.compose.runtime.getValue

@AndroidEntryPoint
class HomeFragment : BaseFragment<FragmentHomeBinding>(), SearchBarFilterViewController.Callback {

	@javax.inject.Inject
	lateinit var settings: AppSettings

	private val viewModel by viewModels<HomeViewModel>()
	private var isHomeChromeHidden = false
	private var filterMenuProvider: SearchBarFilterViewController? = null

	override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentHomeBinding {
		return FragmentHomeBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: FragmentHomeBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		
		filterMenuProvider = SearchBarFilterViewController(this)
		filterMenuProvider?.attachTo(this)
		showHomeChrome()

		viewModel.onOpenContent.observeEvent(viewLifecycleOwner) { manga ->
			router.openDetails(manga)
		}

		binding.composeView.setContent {
			val state by viewModel.summaryState.collectAsStateWithLifecycle()
			val isRandomLoading by viewModel.isRandomLoading.collectAsStateWithLifecycle()
			
			syncSelectedTab(state.selectedTab)
			
			KototoroTheme {
				HomeScreen(
					state = state,
					appSettings = settings,
					onContentClick = { content -> router.openDetails(content, null) },
					onSettingsClick = { router.openSettings() },
					onReaderSettingsClick = { router.openReaderSettings() },
					onSyncSettingsClick = { router.openSyncSettings() },
					onSyncBackupClick = { viewModel.uploadWebDavNow() },
					onSyncRestoreClick = { viewModel.restoreWebDavNow() },
					onViewAllRecentClick = { router.openHistory(currentBrowseGroupTab()) },
					onViewAllUpdatesClick = { router.openMangaUpdates(currentBrowseGroupTab()) },
					onViewAllRecommendationsClick = { router.openSuggestions(currentBrowseGroupTab()) },
					onSourceSettingsClick = { router.openSourcesSettings() },
					onLibraryOpenClick = { router.openFavorites() },
					onBookmarksClick = { router.openBookmarks() },
					onLocalClick = { router.openList(org.skepsun.kototoro.core.model.LocalMangaSource, null, null) },
					onDownloadsClick = { router.openDownloads() },
					onRandomClick = { viewModel.openRandom() },
					onAutoTranslateClick = { router.openTranslationSettings() },
					isRandomLoading = isRandomLoading
				)
			}
		}
	}

	override fun onApplyWindowInsets(view: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
		requireViewBinding().root.clipToPadding = false
		requireViewBinding().root.updatePadding(
			left = systemBars.left,
			right = systemBars.right,
			bottom = 0,
		)
		requireViewBinding().composeView.updatePadding(
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
		showHomeChrome()
		super.onDestroyView()
	}

	private fun hideHomeChrome() {
		if (isHomeChromeHidden) return
		val bottomNav = (activity as? BottomNavOwner)?.bottomNav
		if (bottomNav?.isPinned == true) return
		(activity as? AppBarOwner)?.appBar?.setExpanded(false, true)
		bottomNav?.hide()
		isHomeChromeHidden = true
	}

	private fun showHomeChrome() {
		if (!isHomeChromeHidden) return
		(activity as? AppBarOwner)?.appBar?.setExpanded(true, true)
		(activity as? BottomNavOwner)?.bottomNav?.show()
		isHomeChromeHidden = false
	}
	
	// === SearchBarFilterViewController.Callback implementation ===

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

	override fun isContentTypeFilterVisible(): Boolean = !settings.isSearchBarFilterHidden && true

	override fun isSourceTagFilterVisible(): Boolean = !settings.isSearchBarFilterHidden && true

	override fun isContentTypeEnabled(tab: BrowseGroupTab): Boolean {
		val selectedTags = getSelectedSourceTags()
		return selectedTags.isEmpty() || selectedTags.any { it.supportsContentTab(tab) }
	}

	override fun isSourceTagEnabled(tag: SourceTag): Boolean {
		return getSelectedContentType().supportsSourceTag(tag)
	}
}
