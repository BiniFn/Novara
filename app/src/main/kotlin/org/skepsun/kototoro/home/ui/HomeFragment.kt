package org.skepsun.kototoro.home.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.text.format.DateUtils
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.ui.BaseFragment
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.databinding.FragmentHomeBinding

@AndroidEntryPoint
class HomeFragment : BaseFragment<FragmentHomeBinding>() {

	private val viewModel by viewModels<HomeViewModel>()

	override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentHomeBinding {
		return FragmentHomeBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: FragmentHomeBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
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
		}

		viewModel.summaryState.observe(viewLifecycleOwner) { state ->
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
			binding.buttonResumeRead.isEnabled = state.resumeState.isAvailable
			binding.buttonResumeDetails.isEnabled = state.resumeState.isAvailable
			binding.buttonResumeRead.alpha = if (state.resumeState.isAvailable) 1f else 0.6f
			binding.buttonResumeDetails.alpha = if (state.resumeState.isAvailable) 1f else 0.6f

			val historyRows = listOf(
				binding.textViewRecentItem1 to binding.textViewRecentItem1Meta,
				binding.textViewRecentItem2 to binding.textViewRecentItem2Meta,
				binding.textViewRecentItem3 to binding.textViewRecentItem3Meta,
			)
			historyRows.forEachIndexed { index, (titleView, metaView) ->
				val item = state.recentHistoryItems.getOrNull(index)
				titleView.text = item?.title
					?: titleView.context.getString(R.string.history_is_empty)
				metaView.text = if (item != null) {
					getString(R.string.home_recent_open_details)
				} else {
					getString(R.string.home_recent_empty_subtitle)
				}
				val isEnabled = item != null
				titleView.isEnabled = isEnabled
				metaView.isEnabled = isEnabled
				titleView.alpha = if (isEnabled) 1f else 0.6f
				metaView.alpha = if (isEnabled) 1f else 0.6f
				val clickListener = android.view.View.OnClickListener {
					item?.let { recent -> router.openDetails(recent.content) }
				}
				titleView.setOnClickListener(clickListener)
				metaView.setOnClickListener(clickListener)
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
				val clickListener = android.view.View.OnClickListener {
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

	override fun onApplyWindowInsets(view: android.view.View, insets: WindowInsetsCompat): WindowInsetsCompat {
		requireViewBinding().root.updatePadding(
			left = insets.getInsets(WindowInsetsCompat.Type.systemBars()).left,
			right = insets.getInsets(WindowInsetsCompat.Type.systemBars()).right,
			bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom,
		)
		return insets
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
}
