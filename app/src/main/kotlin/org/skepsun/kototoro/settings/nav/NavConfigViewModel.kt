package org.skepsun.kototoro.settings.nav

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.NavItem
import org.skepsun.kototoro.core.ui.BaseViewModel
import org.skepsun.kototoro.core.ui.util.ActivityRecreationHandle
import org.skepsun.kototoro.list.ui.model.ListModel
import org.skepsun.kototoro.main.ui.MainActivity
import org.skepsun.kototoro.parsers.util.move
import org.skepsun.kototoro.settings.nav.model.NavItemAddModel
import org.skepsun.kototoro.settings.nav.model.NavItemConfigModel
import javax.inject.Inject

const val MAX_ITEM_COUNT = 5

@HiltViewModel
class NavConfigViewModel @Inject constructor(
	private val settings: AppSettings,
	private val activityRecreationHandle: ActivityRecreationHandle,
) : BaseViewModel() {

	private val items = MutableStateFlow(settings.mainNavItems)

	val content: StateFlow<List<ListModel>> = items.map { snapshot ->
		buildList(snapshot.size + 1) {
			snapshot.mapTo(this) {
				NavItemConfigModel(it, getUnavailabilityHint(it))
			}
			if (size < NavItem.entries.size) {
				add(NavItemAddModel(size < MAX_ITEM_COUNT))
			}
		}
	}.stateIn(
		viewModelScope + Dispatchers.Default,
		SharingStarted.WhileSubscribed(5000),
		emptyList(),
	)

	private var commitJob: Job? = null

	val availableItems
		get() = items.value.let { snapshot ->
			NavItem.entries.filterNot { x -> x in snapshot || x == NavItem.DISCOVER }
		}

	fun reorder(fromPos: Int, toPos: Int) {
		items.value = items.value.toMutableList().apply {
			move(fromPos, toPos)
			commit(this)
		}
	}

	fun addItem(item: NavItem) {
		items.value = items.value.plus(item).also {
			commit(it)
		}
	}

	fun removeItem(item: NavItem) {
		val newList = items.value.toMutableList()
		newList.remove(item)
		if (newList.isEmpty()) {
			newList.add(NavItem.HOME)
		}
		items.value = newList
		commit(newList)
	}

	private fun commit(value: List<NavItem>) {
		val prevJob = commitJob
		commitJob = launchJob {
			prevJob?.cancelAndJoin()
			delay(500)
			settings.mainNavItems = value
			activityRecreationHandle.recreate(MainActivity::class.java)
		}
	}

	private fun getUnavailabilityHint(item: NavItem) = if (item.isAvailable(settings)) {
		0
	} else when (item) {
		NavItem.FEED -> R.string.check_for_new_chapters_disabled
		NavItem.SUGGESTIONS -> R.string.suggestions_unavailable_text
		else -> 0
	}
}
