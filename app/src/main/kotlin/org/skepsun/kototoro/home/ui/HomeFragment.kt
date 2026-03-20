package org.skepsun.kototoro.home.ui

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.ui.BaseFragment
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.databinding.FragmentHomeBinding
import org.skepsun.kototoro.explore.ui.model.BrowseGroupTab
import org.skepsun.kototoro.main.ui.owners.AppBarOwner
import org.skepsun.kototoro.main.ui.owners.BottomNavOwner
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.util.ifNullOrEmpty
import kotlin.math.abs

@AndroidEntryPoint
class HomeFragment : BaseFragment<FragmentHomeBinding>() {

	private val viewModel by viewModels<HomeViewModel>()
	private var homeScrollAnchorY = 0
	private var isHomeChromeHidden = false

	override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentHomeBinding {
		return FragmentHomeBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: FragmentHomeBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		with(binding) {
			buttonSettings.setOnClickListener { router.openSettings() }
			buttonReaderSettings.setOnClickListener { router.openReaderSettings() }
			buttonSyncSettings.setOnClickListener { router.openSyncSettings() }
			buttonViewAllRecent.setOnClickListener { router.openHistory(currentBrowseGroupTab()) }
			buttonViewAllUpdates.setOnClickListener { router.openMangaUpdates(currentBrowseGroupTab()) }
			buttonViewAllRecommendations.setOnClickListener { router.openSuggestions(currentBrowseGroupTab()) }
			buttonSourceSettings.setOnClickListener { router.openSourcesSettings() }
			buttonLibraryOpen.setOnClickListener { router.openFavorites() }
			buttonRecentFilterManga.setOnClickListener { viewModel.setSelectedTab(HomeContentTab.MANGA) }
			buttonRecentFilterNovel.setOnClickListener { viewModel.setSelectedTab(HomeContentTab.NOVEL) }
			buttonRecentFilterVideo.setOnClickListener { viewModel.setSelectedTab(HomeContentTab.VIDEO) }
			buttonUpdatesFilterManga.setOnClickListener { viewModel.setSelectedTab(HomeContentTab.MANGA) }
			buttonUpdatesFilterNovel.setOnClickListener { viewModel.setSelectedTab(HomeContentTab.NOVEL) }
			buttonUpdatesFilterVideo.setOnClickListener { viewModel.setSelectedTab(HomeContentTab.VIDEO) }
			buttonRecommendationsFilterManga.setOnClickListener { viewModel.setSelectedTab(HomeContentTab.MANGA) }
			buttonRecommendationsFilterNovel.setOnClickListener { viewModel.setSelectedTab(HomeContentTab.NOVEL) }
			buttonRecommendationsFilterVideo.setOnClickListener { viewModel.setSelectedTab(HomeContentTab.VIDEO) }
		}
		setupHomeScrollChrome(binding.root)
		showHomeChrome()

		viewModel.summaryState.observe(viewLifecycleOwner) { state ->
			syncSelectedTab(state.selectedTab)
			binding.textViewRecentCount.text = state.recentHistoryCount.toString()
			binding.textViewLibraryFavoritesCount.text = state.favoritesCount.toString()
			binding.textViewLibraryCategoriesCount.text = state.favoriteCategoriesCount.toString()
			binding.textViewUpdatesCount.text = state.unreadUpdatesCount.toString()
			binding.textViewRecommendationsCount.text = state.recommendationsCount.toString()
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
			binding.textViewSourcesSummary.text = resources.getQuantityString(
				R.plurals.home_enabled_sources,
				state.enabledSourcesCount,
				state.enabledSourcesCount,
			)

			val historyRows = listOf(
				CoverSlotViews(
					container = binding.recentCover1Container,
					cover = binding.imageViewRecentItem1Cover,
				),
				CoverSlotViews(
					container = binding.recentCover2Container,
					cover = binding.imageViewRecentItem2Cover,
				),
				CoverSlotViews(
					container = binding.recentCover3Container,
					cover = binding.imageViewRecentItem3Cover,
				),
			)
			bindCoverSlots(historyRows, state.recentHistoryItems.map { it.content }) { recent ->
				router.openDetails(recent)
			}

			val updateRows = listOf(
				CoverSlotViews(
					container = binding.updateCover1Container,
					cover = binding.imageViewUpdateItem1Cover,
				),
				CoverSlotViews(
					container = binding.updateCover2Container,
					cover = binding.imageViewUpdateItem2Cover,
				),
				CoverSlotViews(
					container = binding.updateCover3Container,
					cover = binding.imageViewUpdateItem3Cover,
				),
			)
			bindCoverSlots(updateRows, state.recentUpdates.map { it.content }) { update ->
				router.openDetails(update)
			}

			val recommendationRows = listOf(
				CoverSlotViews(
					container = binding.recommendationCover1Container,
					cover = binding.imageViewRecommendationItem1Cover,
				),
				CoverSlotViews(
					container = binding.recommendationCover2Container,
					cover = binding.imageViewRecommendationItem2Cover,
				),
				CoverSlotViews(
					container = binding.recommendationCover3Container,
					cover = binding.imageViewRecommendationItem3Cover,
				),
			)
			bindCoverSlots(recommendationRows, state.recommendations.map { it.content }) { recommendation ->
				router.openDetails(recommendation)
			}

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

	private fun syncSelectedTab(tab: HomeContentTab) {
		val binding = requireViewBinding()
		val recentCheckedId = when (tab) {
			HomeContentTab.MANGA -> binding.buttonRecentFilterManga.id
			HomeContentTab.NOVEL -> binding.buttonRecentFilterNovel.id
			HomeContentTab.VIDEO -> binding.buttonRecentFilterVideo.id
		}
		val updatesCheckedId = when (tab) {
			HomeContentTab.MANGA -> binding.buttonUpdatesFilterManga.id
			HomeContentTab.NOVEL -> binding.buttonUpdatesFilterNovel.id
			HomeContentTab.VIDEO -> binding.buttonUpdatesFilterVideo.id
		}
		val recommendationsCheckedId = when (tab) {
			HomeContentTab.MANGA -> binding.buttonRecommendationsFilterManga.id
			HomeContentTab.NOVEL -> binding.buttonRecommendationsFilterNovel.id
			HomeContentTab.VIDEO -> binding.buttonRecommendationsFilterVideo.id
		}
		binding.toggleGroupRecentContentType.check(recentCheckedId)
		binding.toggleGroupUpdatesContentType.check(updatesCheckedId)
		binding.toggleGroupRecommendationsContentType.check(recommendationsCheckedId)
	}

	private fun currentBrowseGroupTab(): BrowseGroupTab {
		return when (viewModel.summaryState.value.selectedTab) {
			HomeContentTab.MANGA -> BrowseGroupTab.Content
			HomeContentTab.NOVEL -> BrowseGroupTab.Novel
			HomeContentTab.VIDEO -> BrowseGroupTab.Video
		}
	}

	override fun onDestroyView() {
		requireViewBinding().root.setOnScrollChangeListener(null as NestedScrollView.OnScrollChangeListener?)
		showHomeChrome()
		super.onDestroyView()
	}

	private fun bindCover(imageView: org.skepsun.kototoro.image.ui.CoverImageView, content: Content?) {
		val coverUrl = content?.largeCoverUrl.ifNullOrEmpty { content?.coverUrl }
		imageView.setImageAsync(coverUrl, content)
	}

	private fun bindCoverSlot(
		slot: CoverSlotViews,
		content: Content?,
		title: String?,
		onClick: (Content) -> Unit,
	) {
		bindCover(slot.cover, content)
		val isEnabled = content != null
		slot.container.isEnabled = isEnabled
		slot.container.isVisible = true
		slot.container.alpha = if (isEnabled) 1f else 0.35f
		slot.cover.alpha = if (isEnabled) 1f else 0.35f
		slot.container.contentDescription = title ?: getString(R.string.history_is_empty)
		slot.container.setOnClickListener {
			content?.let(onClick)
		}
	}

	private fun bindCoverSlots(
		slots: List<CoverSlotViews>,
		contents: List<Content>,
		onClick: (Content) -> Unit,
	) {
		slots.forEachIndexed { index, slot ->
			val content = contents.getOrNull(index)
			bindCoverSlot(slot, content, content?.title, onClick)
		}
	}

	private fun getSourceOriginLabel(origin: HomeSourceOrigin): String {
		return when (origin) {
			HomeSourceOrigin.BUILT_IN -> getString(R.string.source_type_native)
			HomeSourceOrigin.MIHON -> getString(R.string.source_type_mihon)
			HomeSourceOrigin.ANIYOMI -> getString(R.string.source_type_aniyomi)
			HomeSourceOrigin.LEGADO -> getString(R.string.source_type_legado)
			HomeSourceOrigin.TVBOX -> getString(R.string.source_type_tvbox)
			HomeSourceOrigin.JAVASCRIPT -> getString(R.string.source_type_js)
			HomeSourceOrigin.EXTERNAL -> getString(R.string.external_source)
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

	private data class CoverSlotViews(
		val container: View,
		val cover: org.skepsun.kototoro.image.ui.CoverImageView,
	)
}
