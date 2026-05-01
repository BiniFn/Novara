package org.skepsun.kototoro.discover.ui.category
import org.skepsun.kototoro.core.util.ext.setSupportTitle

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.datepicker.MaterialDatePicker
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.nav.AppRouter.Companion.KEY_ID
import org.skepsun.kototoro.core.nav.AppRouter.Companion.KEY_KIND
import org.skepsun.kototoro.core.nav.AppRouter.Companion.KEY_TITLE
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.ui.BaseFragment
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.core.util.ext.setSupportSubtitle
import org.skepsun.kototoro.databinding.FragmentDiscoverCategoryBinding
import org.skepsun.kototoro.list.ui.model.ContentListModel
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService
import org.skepsun.kototoro.tracking.discovery.domain.isTrackingDateDrivenCategory
import org.skepsun.kototoro.tracking.discovery.domain.resolveTrackingSeason
import org.skepsun.kototoro.tracking.discovery.domain.trackingCalendarDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@AndroidEntryPoint
class DiscoverCategoryFragment : BaseFragment<FragmentDiscoverCategoryBinding>(),
	org.skepsun.kototoro.filter.ui.FilterCoordinator.Owner {

	private val viewModel by activityViewModels<DiscoverCategoryViewModel>()
	private var screenCategoryId: String? = null
	private var screenTitleResId: Int = 0
	
	override val filterCoordinator: org.skepsun.kototoro.filter.ui.FilterCoordinator
		get() = viewModel.filterCoordinator

	override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentDiscoverCategoryBinding {
		return FragmentDiscoverCategoryBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: FragmentDiscoverCategoryBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		setHasOptionsMenu(true)
		
		val serviceName = arguments?.getString(KEY_ID) ?: return
		val categoryId = arguments?.getString(KEY_KIND) ?: return
		val titleResId = arguments?.getInt(KEY_TITLE) ?: return
		screenCategoryId = categoryId
		screenTitleResId = titleResId

		updateToolbarPresentation(categoryId, titleResId, null)

		binding.composeView.apply {
			setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
			setContent {
				KototoroTheme {
					val service = ScrobblerService.entries.firstOrNull { it.name == serviceName }
						?: return@KototoroTheme
					val items = viewModel.content.collectAsStateWithLifecycle(emptyList()).value
					val isLoading = viewModel.isLoading.collectAsStateWithLifecycle(initialValue = false).value
					val selectedDate = viewModel.selectedCalendarDateMillis.collectAsStateWithLifecycle().value

					DiscoverCategoryScreen(
						items = items,
						isRefreshing = isLoading,
						isDateDriven = isTrackingDateDrivenCategory(screenCategoryId ?: categoryId),
						selectedCalendarDateMillis = selectedDate,
						service = service,
						onRefresh = viewModel::refresh,
						onLoadMore = viewModel::loadNextPage,
						onItemClick = { item, _, _ -> openItem(service, item) },
						onDateClick = ::showCalendarDatePicker,
						onTodayClick = viewModel::selectToday,
						onDayClick = viewModel::applyDayFilter,
						modifier = Modifier,
					)
				}
			}
		}

		if (isTrackingDateDrivenCategory(categoryId)) {
			viewModel.selectedCalendarDateMillis.observe(viewLifecycleOwner) { selected ->
				updateToolbarPresentation(
					categoryId = screenCategoryId ?: categoryId,
					titleResId = screenTitleResId,
					selectedDateMillis = selected,
				)
			}
		}

		viewModel.initialize(serviceName, categoryId)
		requireActivity().invalidateOptionsMenu()
	}

	private fun showCalendarDatePicker() {
		val selection = viewModel.selectedCalendarDateMillis.value
		val picker = MaterialDatePicker.Builder.datePicker()
			.setTitleText(R.string.select_date)
			.setSelection(selection)
			.build()
		picker.addOnPositiveButtonClickListener { selected ->
			viewModel.applyDateFilter(selected)
		}
		picker.show(childFragmentManager, "tracking_calendar_date")
	}

	private fun openItem(service: ScrobblerService, item: ContentListModel) {
		if (viewModel.supportsDetails(service.name)) {
			router.openTrackingSiteDetails(
				service,
				item.manga.id,
				item.manga.publicUrl,
			)
		} else {
			val url = item.manga.url ?: item.manga.publicUrl
			if (!url.isNullOrBlank()) router.openExternalBrowser(url)
		}
	}

	override fun onCreateOptionsMenu(menu: android.view.Menu, inflater: android.view.MenuInflater) {
		super.onCreateOptionsMenu(menu, inflater)
		menu.add(0, R.id.action_sort, 0, R.string.sort_by).apply {
			setIcon(R.drawable.ic_sort)
			setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
			isVisible = viewModel.getCurrentSortOptions().size > 1
		}
		menu.add(0, R.id.action_filter, 0, R.string.filter).apply {
			setIcon(R.drawable.ic_filter_menu)
			setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
			isVisible = !isTrackingDateDrivenCategory(screenCategoryId)
		}
	}

	override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
		return when (item.itemId) {
			R.id.action_sort -> {
				showSortDialog()
				true
			}
			R.id.action_filter -> {
				org.skepsun.kototoro.filter.ui.sheet.FilterSheetFragment().show(childFragmentManager, "filter")
				true
			}
			else -> super.onOptionsItemSelected(item)
		}
	}

	fun onRetryClick(error: Throwable) {
		viewModel.refresh()
	}

	private fun showSortDialog() {
		val options = viewModel.getCurrentSortOptions()
		if (options.size <= 1) {
			return
		}
		val selectedId = viewModel.getSelectedSortOptionId()
		val checkedItem = options.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
		val labels = options.map { getString(it.nameResId) }.toTypedArray()
		MaterialAlertDialogBuilder(requireContext())
			.setTitle(R.string.sort_by)
			.setSingleChoiceItems(labels, checkedItem) { dialog, which ->
				val selected = options.getOrNull(which) ?: return@setSingleChoiceItems
				val applied = viewModel.applySortOption(selected.id) ?: return@setSingleChoiceItems
				if (applied.targetCategoryId != null) {
					screenCategoryId = applied.targetCategoryId
					screenTitleResId = applied.nameResId
					updateToolbarPresentation(
						categoryId = applied.targetCategoryId,
						titleResId = applied.nameResId,
						selectedDateMillis = viewModel.selectedCalendarDateMillis.value,
					)
				}
				requireActivity().invalidateOptionsMenu()
				dialog.dismiss()
			}
			.show()
	}

	override fun onDestroyView() {
		setSupportSubtitle(null)
		super.onDestroyView()
	}

	fun onEmptyActionClick() = Unit

	override fun onApplyWindowInsets(view: View, insets: androidx.core.view.WindowInsetsCompat): androidx.core.view.WindowInsetsCompat {
		val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
		requireViewBinding().composeView.setPadding(
			systemBars.left,
			requireViewBinding().composeView.paddingTop,
			systemBars.right,
			systemBars.bottom
		)
		return insets
	}

	private fun updateToolbarPresentation(
		categoryId: String,
		titleResId: Int,
		selectedDateMillis: Long?,
	) {
		setSupportTitle(titleResId)
		val subtitle = when (categoryId) {
			"al_anime_airing",
			"simkl_anime_airing",
			"simkl_tv_airing",
			-> selectedDateMillis?.let(::formatToolbarDate)
			"seasonal",
			"shiki_seasonal",
			-> selectedDateMillis?.let(::formatToolbarSeason)
			else -> null
		}
		setSupportSubtitle(subtitle)
	}

	private fun formatToolbarDate(selectedDateMillis: Long): String {
		return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(
			Instant.ofEpochMilli(selectedDateMillis)
				.atZone(ZoneId.systemDefault())
				.toLocalDate()
		)
	}

	private fun formatToolbarSeason(selectedDateMillis: Long): String {
		val date = trackingCalendarDate(selectedDateMillis) ?: return ""
		val season = resolveTrackingSeason(date)
		val seasonNameResId = when (season.malSeason) {
			"winter" -> R.string.tracking_season_winter
			"spring" -> R.string.tracking_season_spring
			"summer" -> R.string.tracking_season_summer
			else -> R.string.tracking_season_fall
		}
		return getString(
			R.string.tracking_season_label,
			getString(seasonNameResId),
			season.year,
		)
	}
}
