package org.skepsun.kototoro.home.ui

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import com.google.android.material.appbar.AppBarLayout
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.main.ui.owners.AppBarOwner
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.ui.BaseFragment
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.databinding.FragmentHomeBinding
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.util.ifNullOrEmpty

@AndroidEntryPoint
class HomeFragment : BaseFragment<FragmentHomeBinding>(), AppBarLayout.OnOffsetChangedListener {

	private val viewModel by viewModels<HomeViewModel>()

	override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentHomeBinding {
		return FragmentHomeBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: FragmentHomeBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		val appBar = (activity as? AppBarOwner)?.appBar
		appBar?.addOnOffsetChangedListener(this)
		binding.root.doOnPreDraw {
			appBar?.setExpanded(false, false)
		}
		with(binding) {
			buttonSettings.setOnClickListener { router.openSettings() }
			buttonReaderSettings.setOnClickListener { router.openReaderSettings() }
			buttonSyncSettings.setOnClickListener { router.openSettings() }
			buttonViewAllRecent.setOnClickListener { router.openHistory() }
			buttonViewAllUpdates.setOnClickListener { router.openMangaUpdates() }
			buttonSourceSettings.setOnClickListener { router.openManageSources() }
			buttonLibraryOpen.setOnClickListener { router.openFavorites() }
			buttonResumeRead.setOnClickListener {
				viewModel.summaryState.value.resumeState.content?.let { content ->
					router.openReader(content)
				}
			}
			buttonResumeDetails.setOnClickListener {
				viewModel.summaryState.value.resumeState.content?.let { content ->
					router.openDetails(content)
				}
			}
			chipHomeManga.setOnClickListener { viewModel.setSelectedTab(HomeContentTab.MANGA) }
			chipHomeNovel.setOnClickListener { viewModel.setSelectedTab(HomeContentTab.NOVEL) }
			chipHomeVideo.setOnClickListener { viewModel.setSelectedTab(HomeContentTab.VIDEO) }
		}

		viewModel.summaryState.observe(viewLifecycleOwner) { state ->
			binding.chipGroupHomeContentType.check(
				when (state.selectedTab) {
					HomeContentTab.MANGA -> binding.chipHomeManga.id
					HomeContentTab.NOVEL -> binding.chipHomeNovel.id
					HomeContentTab.VIDEO -> binding.chipHomeVideo.id
				},
			)
			updateUpdatesButtonLabel(state.selectedTab)
			binding.textViewRecentCount.text = state.recentHistoryCount.toString()
			binding.textViewLibraryFavoritesCount.text = state.favoritesCount.toString()
			binding.textViewLibraryCategoriesCount.text = state.favoriteCategoriesCount.toString()
			binding.textViewUpdatesCount.text = state.unreadUpdatesCount.toString()
			binding.textViewTrackingSite.text = getString(state.preferredTrackingSite.titleResId)
			val syncStatusText = when {
				state.syncState.isWebDavEnabled && state.syncState.isAutoSyncEnabled -> getString(R.string.home_sync_status_auto)
				state.syncState.isWebDavEnabled -> getString(R.string.home_sync_status_ready)
				else -> getString(R.string.home_sync_status_not_configured)
			}
			binding.textViewOverviewSyncStatus.text = syncStatusText
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

			val resumeContent = state.resumeState.content
			binding.textViewResumeTitle.text = resumeContent?.title
				?: getString(R.string.home_resume_empty_title)
			binding.textViewResumeSubtitle.text = when {
				state.resumeState.progressPercent != null -> {
					getString(
						R.string.home_resume_progress,
						state.resumeState.progressPercent,
					)
				}
				resumeContent != null -> getString(R.string.home_recent_open_details)
				else -> getString(R.string.home_resume_empty_subtitle)
			}
			bindCover(binding.imageViewResumeCover, resumeContent)
			binding.buttonResumeRead.isEnabled = state.resumeState.isAvailable
			binding.buttonResumeDetails.isEnabled = state.resumeState.isAvailable
			binding.buttonResumeRead.alpha = if (state.resumeState.isAvailable) 1f else 0.6f
			binding.buttonResumeDetails.alpha = if (state.resumeState.isAvailable) 1f else 0.6f
			binding.imageViewResumeCover.alpha = if (state.resumeState.isAvailable) 1f else 0.6f

			val historyRows = listOf(
				RecentRowViews(
					container = binding.recentItem1Container,
					title = binding.textViewRecentItem1,
					meta = binding.textViewRecentItem1Meta,
					cover = binding.imageViewRecentItem1Cover,
				),
				RecentRowViews(
					container = binding.recentItem2Container,
					title = binding.textViewRecentItem2,
					meta = binding.textViewRecentItem2Meta,
					cover = binding.imageViewRecentItem2Cover,
				),
				RecentRowViews(
					container = binding.recentItem3Container,
					title = binding.textViewRecentItem3,
					meta = binding.textViewRecentItem3Meta,
					cover = binding.imageViewRecentItem3Cover,
				),
			)
			historyRows.forEachIndexed { index, row ->
				val item = state.recentHistoryItems.getOrNull(index)
				val typeLabelResId = item?.typeLabelResId
				row.title.text = item?.title
					?: row.title.context.getString(R.string.history_is_empty)
				row.meta.text = when {
					typeLabelResId != null -> getString(typeLabelResId)
					item != null -> getString(R.string.home_recent_open_details)
					else -> getString(R.string.home_recent_empty_subtitle)
				}
				bindCover(row.cover, item?.content)
				val isEnabled = item != null
				row.container.isEnabled = isEnabled
				row.title.isEnabled = isEnabled
				row.meta.isEnabled = isEnabled
				val alpha = if (isEnabled) 1f else 0.6f
				row.container.alpha = alpha
				row.title.alpha = alpha
				row.meta.alpha = alpha
				row.cover.alpha = alpha
				row.container.setOnClickListener {
					item?.let { recent -> router.openDetails(recent.content) }
				}
			}

			val updateRows = listOf(
				binding.textViewUpdateItem1 to binding.textViewUpdateItem1Meta,
				binding.textViewUpdateItem2 to binding.textViewUpdateItem2Meta,
				binding.textViewUpdateItem3 to binding.textViewUpdateItem3Meta,
			)
			updateRows.forEachIndexed { index, (titleView, metaView) ->
				val item = state.recentUpdates.getOrNull(index)
				titleView.text = item?.title
					?: titleView.context.getString(R.string.home_updates_empty_title)
				metaView.text = if (item != null) {
					resources.getQuantityString(
						R.plurals.home_updates_new_chapters,
						item.newChapters,
						item.newChapters,
					)
				} else {
					getString(R.string.home_updates_empty_subtitle)
				}
				val isEnabled = item != null
				titleView.isEnabled = isEnabled
				metaView.isEnabled = isEnabled
				titleView.alpha = if (isEnabled) 1f else 0.6f
				metaView.alpha = if (isEnabled) 1f else 0.6f
				val clickListener = View.OnClickListener {
					item?.let { update -> router.openDetails(update.content) }
				}
				titleView.setOnClickListener(clickListener)
				metaView.setOnClickListener(clickListener)
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

	override fun onOffsetChanged(appBarLayout: AppBarLayout?, verticalOffset: Int) {
		val appBar = appBarLayout ?: return
		if (appBar.bottom > 0) {
			appBar.setExpanded(false, false)
		}
	}

	override fun onDestroyView() {
		(activity as? AppBarOwner)?.appBar?.removeOnOffsetChangedListener(this)
		super.onDestroyView()
	}

	private fun updateUpdatesButtonLabel(tab: HomeContentTab) {
		requireViewBinding().buttonViewAllUpdates.text = when (tab) {
			HomeContentTab.MANGA -> getString(R.string.view_all)
			HomeContentTab.NOVEL -> getString(R.string.view_all)
			HomeContentTab.VIDEO -> getString(R.string.view_all)
		}
	}

	private fun bindCover(imageView: org.skepsun.kototoro.image.ui.CoverImageView, content: Content?) {
		val coverUrl = content?.largeCoverUrl.ifNullOrEmpty { content?.coverUrl }
		imageView.setImageAsync(coverUrl, content)
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

	private data class RecentRowViews(
		val container: View,
		val title: android.widget.TextView,
		val meta: android.widget.TextView,
		val cover: org.skepsun.kototoro.image.ui.CoverImageView,
	)
}
