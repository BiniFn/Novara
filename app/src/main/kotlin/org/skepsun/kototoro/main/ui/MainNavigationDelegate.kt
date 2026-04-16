package org.skepsun.kototoro.main.ui

import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.annotation.IdRes
import androidx.core.view.isEmpty
import androidx.core.view.isVisible
import androidx.core.view.iterator
import androidx.core.view.size
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.navigationrail.NavigationRailView
import com.google.android.material.transition.MaterialFadeThrough
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.skepsun.kototoro.R
import org.skepsun.kototoro.bookmarks.ui.AllBookmarksFragment
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.NavItem
import org.skepsun.kototoro.core.ui.util.RecyclerViewOwner
import org.skepsun.kototoro.core.ui.widgets.SlidingBottomNavigationView
import org.skepsun.kototoro.core.util.ext.buildBundle
import org.skepsun.kototoro.core.util.ext.setContentDescriptionAndTooltip
import org.skepsun.kototoro.core.util.ext.smoothScrollToTop
import org.skepsun.kototoro.databinding.NavigationRailFabBinding
import org.skepsun.kototoro.discover.ui.DiscoverFragment
import org.skepsun.kototoro.explore.ui.ExploreFragment
import org.skepsun.kototoro.favourites.ui.container.FavouritesContainerFragment
import org.skepsun.kototoro.home.ui.HomeFragment
import org.skepsun.kototoro.history.ui.HistoryListFragment
import org.skepsun.kototoro.local.ui.LocalListFragment
import org.skepsun.kototoro.suggestions.ui.SuggestionsFragment
import org.skepsun.kototoro.tracker.ui.feed.FeedFragment
import org.skepsun.kototoro.tracker.ui.updates.UpdatesFragment
import java.util.LinkedList
import com.google.android.material.R as materialR

private const val TAG_PRIMARY = "primary"

class MainNavigationDelegate(
	private val navBar: AppNavBarDelegator,
	private val fragmentManager: FragmentManager,
	private val settings: AppSettings,
) : OnBackPressedCallback(false),
	NavigationBarView.OnItemSelectedListener,
	NavigationBarView.OnItemReselectedListener, View.OnClickListener {

	private val listeners = LinkedList<OnFragmentChangedListener>()
	val navRailHeader = (navBar.asView() as? NavigationRailView)?.headerView?.let {
		NavigationRailFabBinding.bind(it)
	}

	val primaryFragment: Fragment?
		get() = fragmentManager.findFragmentByTag(TAG_PRIMARY)
			?: fragmentManager.fragments.lastOrNull { !it.isHidden && it.id == R.id.container }

	init {
		navBar.setOnItemSelectedListener(this)
		navBar.setOnItemReselectedListener(this)
		navRailHeader?.run {
			root.updateLayoutParams<FrameLayout.LayoutParams> {
				gravity = Gravity.TOP or Gravity.CENTER
			}
			val horizontalPadding = (navBar.asView() as NavigationRailView).itemActiveIndicatorMarginHorizontal
			root.setPadding(horizontalPadding, 0, horizontalPadding, 0)
			buttonExpand.setOnClickListener(this@MainNavigationDelegate)
			buttonExpand.setContentDescriptionAndTooltip(R.string.expand)
			railFab.isExtended = false
			railFab.isAnimationEnabled = false
		}
	}

	override fun onNavigationItemSelected(item: MenuItem): Boolean {
		return if (onNavigationItemSelected(item.itemId)) {
			item.isChecked = true
			true
		} else {
			false
		}
	}

	override fun onNavigationItemReselected(item: MenuItem) {
		onNavigationItemReselected()
	}

	override fun onClick(v: View) {
		when (v.id) {
			R.id.button_expand -> {
				val navView = navBar.asView()
				if (navView is NavigationRailView) {
					setNavbarIsExpanded(!navView.isExpanded)
				}
			}
		}
	}

	override fun handleOnBackPressed() {
		navBar.selectedItemId = navBar.getFirstVisibleItemId() ?: return
	}

	fun onCreate(lifecycleOwner: LifecycleOwner, savedInstanceState: Bundle?) {
		if (navBar.isMenuEmpty()) {
			navBar.setupMenu(settings.mainNavItems)
		}
		observeSettings(lifecycleOwner)

		if (savedInstanceState != null) {
			// FragmentManager restores fragments with their hidden state preserved.
			// The bottom nav might lose its selectedItemId if the menu was created dynamically.
			val restoredActiveFrag = fragmentManager.fragments.lastOrNull { 
				it.id == R.id.container && !it.isHidden 
			}
			val selectedItemId = restoredActiveFrag?.let { getItemId(it) } ?: navBar.selectedItemId
			if (navBar.selectedItemId != selectedItemId) {
				navBar.setOnItemSelectedListener(null)
				navBar.selectedItemId = selectedItemId
				navBar.setOnItemSelectedListener(this)
			}
			
			val selectedFragClass = itemIdToFragmentClass(selectedItemId)
			val selectedTag = selectedFragClass?.name
			val transaction = fragmentManager.beginTransaction()
			var found: Fragment? = null
			for (f in fragmentManager.fragments) {
				if (f.id == R.id.container) {
					if (f.tag == selectedTag) {
						transaction.show(f)
						transaction.setMaxLifecycle(f, Lifecycle.State.RESUMED)
						found = f
					} else {
						transaction.hide(f)
						transaction.setMaxLifecycle(f, Lifecycle.State.STARTED)
					}
				}
			}
			transaction.commitNowAllowingStateLoss()
			if (found != null) {
				onFragmentChanged(found, fromUser = false)
			} else {
				onNavigationItemSelected(selectedItemId)
			}
		} else {
			val fragment = primaryFragment
			if (fragment != null) {
				onFragmentChanged(fragment, fromUser = false)
				val itemId = getItemId(fragment)
				if (navBar.selectedItemId != itemId) {
					navBar.selectedItemId = itemId
				}
			} else {
				val itemId = navBar.getFirstVisibleItemId() ?: navBar.selectedItemId
				onNavigationItemSelected(itemId)
			}
		}
	}

	fun observeTitle() = callbackFlow {
		val listener = OnFragmentChangedListener { f, _ ->
			trySendBlocking(getItemId(f))
		}
		addOnFragmentChangedListener(listener)
		awaitClose { removeOnFragmentChangedListener(listener) }
	}.map {
		navBar.getItemTitle(it)?.toString()
	}

	fun setCounter(item: NavItem, counter: Int) {
		setCounter(item.id, counter)
	}

	fun syncSelectedItem() {
		val fragment = primaryFragment ?: return
		onFragmentChanged(fragment, fromUser = false)
		val itemId = getItemId(fragment)
		if (navBar.selectedItemId != itemId) {
			navBar.selectedItemId = itemId
		}
	}

	fun selectItem(@IdRes itemId: Int): Boolean {
		if (navBar.menu.findItem(itemId) == null) {
			return false
		}
		if (navBar.selectedItemId == itemId) {
			onNavigationItemReselected()
			return true
		}
		navBar.selectedItemId = itemId
		return true
	}

	private fun setCounter(@IdRes id: Int, counter: Int) {
		if (counter == 0) {
			navBar.setBadgeVisible(id, false)
		} else {
			if (counter < 0) {
				navBar.clearBadge(id)
			} else {
				navBar.setBadgeNumber(id, counter)
			}
			navBar.setBadgeVisible(id, true)
		}
	}

	fun setItemVisibility(@IdRes itemId: Int, isVisible: Boolean) {
		navBar.setItemVisibility(itemId, isVisible)
		if (navBar.isItemChecked(itemId) && !isVisible) {
			navBar.selectedItemId = navBar.getFirstVisibleItemId() ?: return
		}
	}

	fun addOnFragmentChangedListener(listener: OnFragmentChangedListener) {
		listeners.add(listener)
	}

	fun removeOnFragmentChangedListener(listener: OnFragmentChangedListener) {
		listeners.remove(listener)
	}

	private fun onNavigationItemSelected(@IdRes itemId: Int): Boolean {
		val newFragment = when (itemId) {
			R.id.nav_home -> HomeFragment::class.java
			R.id.nav_history -> HistoryListFragment::class.java
			R.id.nav_favorites -> FavouritesContainerFragment::class.java
			R.id.nav_explore -> ExploreFragment::class.java
			R.id.nav_discover -> DiscoverFragment::class.java
			R.id.nav_feed -> FeedFragment::class.java
			R.id.nav_local -> LocalListFragment::class.java
			R.id.nav_suggestions -> SuggestionsFragment::class.java
			R.id.nav_bookmarks -> AllBookmarksFragment::class.java
			R.id.nav_updated -> UpdatesFragment::class.java
			else -> return false
		}
		if (!setPrimaryFragment(newFragment)) {
			// probably already selected
			onNavigationItemReselected()
		}
		return true
	}

	private fun getItemId(fragment: Fragment) = when (fragment) {
		is HomeFragment -> R.id.nav_home
		is HistoryListFragment -> R.id.nav_history
		is FavouritesContainerFragment -> R.id.nav_favorites
		is ExploreFragment -> R.id.nav_explore
		is DiscoverFragment -> R.id.nav_discover
		is FeedFragment -> R.id.nav_feed
		is LocalListFragment -> R.id.nav_local
		is SuggestionsFragment -> R.id.nav_suggestions
		is AllBookmarksFragment -> R.id.nav_bookmarks
		is UpdatesFragment -> R.id.nav_updated
		else -> 0
	}

	private fun itemIdToFragmentClass(@IdRes itemId: Int): Class<out Fragment>? = when (itemId) {
		R.id.nav_home -> HomeFragment::class.java
		R.id.nav_history -> HistoryListFragment::class.java
		R.id.nav_favorites -> FavouritesContainerFragment::class.java
		R.id.nav_explore -> ExploreFragment::class.java
		R.id.nav_discover -> DiscoverFragment::class.java
		R.id.nav_feed -> FeedFragment::class.java
		R.id.nav_local -> LocalListFragment::class.java
		R.id.nav_suggestions -> SuggestionsFragment::class.java
		R.id.nav_bookmarks -> AllBookmarksFragment::class.java
		R.id.nav_updated -> UpdatesFragment::class.java
		else -> null
	}

	private fun setPrimaryFragment(fragmentClass: Class<out Fragment>): Boolean {
		if (fragmentManager.isStateSaved || fragmentClass.isInstance(primaryFragment)) {
			return false
		}
		val tag = fragmentClass.name
		val currentFrag = primaryFragment
		val existingFrag = fragmentManager.findFragmentByTag(tag)

		val transaction = fragmentManager.beginTransaction()
			.setReorderingAllowed(true)

		// Hide current fragment and demote to STARTED so its MenuProviders
		// (registered with RESUMED lifecycle) are deactivated immediately
		if (currentFrag != null) {
			transaction.hide(currentFrag)
			transaction.setMaxLifecycle(currentFrag, Lifecycle.State.STARTED)
		}

		val targetFrag: Fragment
		if (existingFrag != null) {
			// Fragment already cached — just show it
			transaction.show(existingFrag)
			transaction.setMaxLifecycle(existingFrag, Lifecycle.State.RESUMED)
			targetFrag = existingFrag
		} else {
			// First visit — create, add and tag it
			val fragment = instantiateFragment(fragmentClass)
			fragment.arguments = buildBundle(1) {
				putBoolean(AppRouter.KEY_IS_BOTTOMTAB, true)
			}
			transaction.add(R.id.container, fragment, tag)
			targetFrag = fragment
		}

		transaction
			.runOnCommit { onFragmentChanged(targetFrag, fromUser = true) }
			.commit()
		return true
	}



	private fun onNavigationItemReselected() {
		val recyclerView = (primaryFragment as? RecyclerViewOwner)?.recyclerView ?: return
		recyclerView.smoothScrollToTop()
	}

	private fun onFragmentChanged(fragment: Fragment, fromUser: Boolean) {
		isEnabled = getItemId(fragment) != navBar.getFirstVisibleItemId()
		listeners.forEach { it.onFragmentChanged(fragment, fromUser) }
	}

	private fun instantiateFragment(fragmentClass: Class<out Fragment>): Fragment {
		val classLoader = navBar.asView()?.context?.classLoader ?: fragmentManager.fragments.firstOrNull()?.requireContext()?.classLoader ?: javaClass.classLoader!!
		return fragmentManager.fragmentFactory.instantiate(classLoader, fragmentClass.name)
	}

	private fun observeSettings(lifecycleOwner: LifecycleOwner) {
		settings.observe(AppSettings.KEY_TRACKER_ENABLED, AppSettings.KEY_SUGGESTIONS, AppSettings.KEY_NAV_LABELS)
			.onEach {
				setItemVisibility(R.id.nav_suggestions, settings.isSuggestionsEnabled)
				setItemVisibility(R.id.nav_feed, settings.isTrackerEnabled)
				setNavbarIsLabeled(settings.isNavLabelsVisible)
			}.launchIn(lifecycleOwner.lifecycleScope)
	}

	private fun setNavbarIsLabeled(value: Boolean) {
		navRailHeader?.buttonExpand?.isVisible = value
		if (!value) {
			setNavbarIsExpanded(false)
		}
		navBar.labelVisibilityMode = if (value) {
			NavigationBarView.LABEL_VISIBILITY_LABELED
		} else {
			NavigationBarView.LABEL_VISIBILITY_UNLABELED
		}
	}

	private fun setNavbarIsExpanded(value: Boolean) {
		val navView = navBar.asView()
		if (navView !is NavigationRailView) {
			return
		}
		if (value) {
			navView.expand()
			navRailHeader?.run {
				root.updateLayoutParams<FrameLayout.LayoutParams> {
					gravity = Gravity.TOP or Gravity.START
				}
				railFab.extend()
				buttonExpand.setImageResource(R.drawable.ic_drawer_menu_open)
				buttonExpand.setContentDescriptionAndTooltip(R.string.collapse)
				val horizontalPadding = navView.itemActiveIndicatorExpandedMarginHorizontal
				root.setPadding(horizontalPadding, 0, horizontalPadding, 0)
			}
		} else {
			navView.collapse()
			navRailHeader?.run {
				root.updateLayoutParams<FrameLayout.LayoutParams> {
					gravity = Gravity.TOP or Gravity.CENTER
				}
				railFab.shrink()
				buttonExpand.setImageResource(R.drawable.ic_drawer_menu)
				buttonExpand.setContentDescriptionAndTooltip(R.string.expand)
				val horizontalPadding = navView.itemActiveIndicatorMarginHorizontal
				root.setPadding(horizontalPadding, 0, horizontalPadding, 0)
			}
		}
	}

	fun interface OnFragmentChangedListener {

		fun onFragmentChanged(fragment: Fragment, fromUser: Boolean)
	}

	companion object {

		const val MAX_ITEM_COUNT = 6
	}
}
