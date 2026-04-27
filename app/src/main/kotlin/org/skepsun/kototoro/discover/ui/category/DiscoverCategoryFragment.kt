package org.skepsun.kototoro.discover.ui.category
import org.skepsun.kototoro.core.util.ext.setSupportTitle

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.datepicker.MaterialDatePicker
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.nav.AppRouter.Companion.KEY_ID
import org.skepsun.kototoro.core.nav.AppRouter.Companion.KEY_KIND
import org.skepsun.kototoro.core.nav.AppRouter.Companion.KEY_TITLE
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.ui.BaseFragment
import org.skepsun.kototoro.databinding.FragmentDiscoverCategoryBinding
import org.skepsun.kototoro.list.ui.adapter.ContentListAdapter
import org.skepsun.kototoro.list.ui.adapter.ContentListListener
import org.skepsun.kototoro.list.ui.model.ContentListModel
import org.skepsun.kototoro.list.ui.size.DynamicItemSizeResolver
import org.skepsun.kototoro.list.ui.GridSpanResolver
import org.skepsun.kototoro.list.ui.adapter.TypedListSpacingDecoration
import org.skepsun.kototoro.core.prefs.ListMode
import org.skepsun.kototoro.core.ui.widgets.TipView
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.list.ui.model.ListHeader
import org.skepsun.kototoro.list.domain.ListFilterOption
import org.skepsun.kototoro.parsers.model.ContentTag
import org.skepsun.kototoro.core.ui.list.FitHeightGridLayoutManager
import org.skepsun.kototoro.core.ui.list.FitHeightLinearLayoutManager
import org.skepsun.kototoro.core.ui.list.PaginationScrollListener
import org.skepsun.kototoro.core.nav.router
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.tracking.discovery.domain.isTrackingDateDrivenCategory
import org.skepsun.kototoro.tracking.discovery.domain.resolveTrackingSeason
import org.skepsun.kototoro.tracking.discovery.domain.trackingCalendarDate
import org.skepsun.kototoro.core.util.ext.setSupportSubtitle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@AndroidEntryPoint
class DiscoverCategoryFragment : BaseFragment<FragmentDiscoverCategoryBinding>(), SwipeRefreshLayout.OnRefreshListener, org.skepsun.kototoro.filter.ui.FilterCoordinator.Owner {

	private val viewModel by activityViewModels<DiscoverCategoryViewModel>()
	private var paginationListener: PaginationScrollListener? = null
	private var spanResolver: GridSpanResolver? = null
	private val calendarChipIds = LinkedHashMap<Int, Int>()
	private var screenCategoryId: String? = null
	private var screenTitleResId: Int = 0
	
	override val filterCoordinator: org.skepsun.kototoro.filter.ui.FilterCoordinator
		get() = viewModel.filterCoordinator
	
	@javax.inject.Inject
	lateinit var settings: AppSettings

	val recyclerView: RecyclerView?
		get() = viewBinding?.recyclerView

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

		spanResolver = GridSpanResolver(binding.root.resources)
		spanResolver?.setGridSize(settings.gridSize / 100f, binding.recyclerView)

		val adapter = ContentListAdapter(
			listener = object : ContentListListener {
				override fun onItemClick(item: ContentListModel, view: View) {
					if (viewModel.supportsDetails(serviceName)) {
						router.openTrackingSiteDetails(
							org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService.entries.first { it.name == serviceName },
							item.manga.id,
							item.manga.publicUrl,
						)
					} else {
						val url = item.manga.url ?: item.manga.publicUrl
						if (!url.isNullOrBlank()) router.openExternalBrowser(url)
					}
				}
				override fun onItemLongClick(item: ContentListModel, view: View) = false
				override fun onItemContextClick(item: ContentListModel, view: View) = false
				override fun onEmptyActionClick() = this@DiscoverCategoryFragment.onEmptyActionClick()
				override fun onRetryClick(error: Throwable) = this@DiscoverCategoryFragment.onRetryClick(error)
				override fun onListHeaderClick(item: ListHeader, view: View) {}
				override fun onPrimaryButtonClick(tipView: TipView) {}
				override fun onSecondaryButtonClick(tipView: TipView) {}
				override fun onFilterOptionClick(option: ListFilterOption) {}
				override fun onFilterClick(view: View?) {}
				override fun onReadClick(manga: Content, view: View) {}
				override fun onTagClick(manga: Content, tag: ContentTag, view: View) {}
			},
			sizeResolver = DynamicItemSizeResolver(resources, viewLifecycleOwner, settings, adjustWidth = false),
		)

		applyListMode(settings.listMode, binding.recyclerView, spanResolver)

		paginationListener = PaginationScrollListener(4, object : PaginationScrollListener.Callback {
			override fun onScrolledToEnd() {
				viewModel.loadNextPage()
			}
		})

		with(binding.recyclerView) {
			this.adapter = adapter
			setHasFixedSize(true)
			addItemDecoration(TypedListSpacingDecoration(context, false))
			addOnScrollListener(checkNotNull(paginationListener))
		}
		
		binding.swipeRefreshLayout.setOnRefreshListener(this)

		viewModel.content.observe(viewLifecycleOwner) { items ->
			viewLifecycleOwner.lifecycleScope.launch {
				val isLoadingOnly = items.size == 1 && items.first() is org.skepsun.kototoro.list.ui.model.LoadingState
				val progressBar = binding.root.findViewById<View>(R.id.progressBar)
				if (isLoadingOnly) {
					progressBar?.isVisible = true
					binding.recyclerView.isVisible = false
				} else {
					progressBar?.isVisible = false
					binding.recyclerView.isVisible = true
					adapter.emit(items)
				}
			}
		}
		viewModel.isLoading.observe(viewLifecycleOwner) {
			binding.swipeRefreshLayout.isRefreshing = it
		}

		if (isTrackingDateDrivenCategory(categoryId)) {
			binding.calendarActionRow.isVisible = true
			binding.calendarFilterScroll.isVisible = true
			setupCalendarFilters(binding)
			binding.calendarDateButton.setOnClickListener {
				showCalendarDatePicker()
			}
			binding.calendarTodayButton.setOnClickListener {
				viewModel.selectToday()
			}
			viewModel.selectedCalendarDateMillis.observe(viewLifecycleOwner) { selected ->
				updateCalendarDateUi(binding, selected)
				updateToolbarPresentation(
					categoryId = screenCategoryId ?: categoryId,
					titleResId = screenTitleResId,
					selectedDateMillis = selected,
				)
			}
		}

		viewModel.initialize(serviceName, categoryId)
	}

	private fun applyListMode(mode: ListMode, recyclerView: RecyclerView, spanResolver: GridSpanResolver?) {
		if (spanResolver != null) {
			recyclerView.removeOnLayoutChangeListener(spanResolver)
		}
		when (mode) {
			ListMode.LIST, ListMode.DETAILED_LIST -> {
				recyclerView.layoutManager = FitHeightLinearLayoutManager(recyclerView.context)
			}
			ListMode.GRID -> {
				val count = spanResolver?.spanCount ?: 2
				recyclerView.layoutManager = FitHeightGridLayoutManager(recyclerView.context, count)
				if (spanResolver != null) {
					recyclerView.addOnLayoutChangeListener(spanResolver)
				}
			}
		}
	}

	private fun setupCalendarFilters(binding: FragmentDiscoverCategoryBinding) {
		calendarChipIds.clear()
		val days: List<Pair<Int, Int>> = listOf(
			1 to R.string.day_monday,
			2 to R.string.day_tuesday,
			3 to R.string.day_wednesday,
			4 to R.string.day_thursday,
			5 to R.string.day_friday,
			6 to R.string.day_saturday,
			7 to R.string.day_sunday
		)

		var today = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
		today = if (today == java.util.Calendar.SUNDAY) 7 else today - 1

		days.forEach { (index, resId) ->
			val chip = Chip(requireContext()).apply {
				id = View.generateViewId()
				text = getString(resId)
				isCheckable = true
				setEnsureMinTouchTargetSize(false)
				isChecked = index == today
				setOnClickListener {
					viewModel.applyDayFilter(index)
				}
			}
			calendarChipIds[index] = chip.id
			binding.calendarChips.addView(chip)
		}
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

	private fun updateCalendarDateUi(
		binding: FragmentDiscoverCategoryBinding,
		selectedDateMillis: Long?,
	) {
		val millis = selectedDateMillis ?: return
		val selectedDate = Instant.ofEpochMilli(millis)
			.atZone(ZoneId.systemDefault())
			.toLocalDate()
		val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
		binding.calendarDateButton.text = formatter.format(selectedDate)
		val chipId = calendarChipIds[selectedDate.dayOfWeek.value] ?: return
		if (binding.calendarChips.checkedChipId != chipId) {
			binding.calendarChips.check(chipId)
		}
	}

	override fun onRefresh() {
		viewModel.refresh()
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
		requireViewBinding().recyclerView.setPadding(
			systemBars.left,
			requireViewBinding().recyclerView.paddingTop,
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
