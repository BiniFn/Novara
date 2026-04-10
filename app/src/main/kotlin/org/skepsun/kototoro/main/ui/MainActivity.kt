package org.skepsun.kototoro.main.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.appcompat.view.ActionMode
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import androidx.recyclerview.widget.ItemTouchHelper
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
import com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_NO_SCROLL
import com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL
import com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP
import com.google.android.material.search.SearchView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.skepsun.kototoro.R
import org.skepsun.kototoro.backups.domain.BackupStartupCoordinator
import org.skepsun.kototoro.browser.AdListUpdateService
import org.skepsun.kototoro.core.exceptions.resolve.SnackbarErrorObserver
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.os.VoiceInputContract
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.NavItem
import org.skepsun.kototoro.core.ui.BaseActivity
import org.skepsun.kototoro.core.ui.util.FadingAppbarMediator
import org.skepsun.kototoro.core.ui.util.MenuInvalidator
import org.skepsun.kototoro.core.ui.widgets.SlidingBottomNavigationView
import org.skepsun.kototoro.core.util.ext.consume
import org.skepsun.kototoro.core.util.ext.end
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.core.util.ext.observeEvent
import org.skepsun.kototoro.core.util.ext.printStackTraceDebug
import org.skepsun.kototoro.core.util.ext.start
import org.skepsun.kototoro.core.util.FoldableUtils
import org.skepsun.kototoro.databinding.ActivityMainBinding
import org.skepsun.kototoro.details.service.ContentPrefetchService
import org.skepsun.kototoro.explore.ui.ExploreFragment
import org.skepsun.kototoro.favourites.ui.container.FavouritesContainerFragment
import org.skepsun.kototoro.tracker.ui.feed.FeedFragment
import org.skepsun.kototoro.history.ui.HistoryListFragment
import org.skepsun.kototoro.home.ui.HomeFragment
import org.skepsun.kototoro.local.ui.LocalIndexUpdateService
import org.skepsun.kototoro.local.ui.LocalStorageCleanupWorker
import org.skepsun.kototoro.main.ui.compose.KototoroApp
import org.skepsun.kototoro.main.ui.compose.ComposeAppNavBarDelegator
import org.skepsun.kototoro.core.ui.widgets.BottomNavState
import kotlinx.coroutines.flow.MutableStateFlow
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.remotelist.ui.ContentSearchMenuProvider
import org.skepsun.kototoro.search.ui.suggestion.SearchSuggestionItemCallback
import org.skepsun.kototoro.search.ui.suggestion.SearchSuggestionListenerImpl
import org.skepsun.kototoro.search.ui.suggestion.SearchSuggestionMenuProvider
import org.skepsun.kototoro.search.ui.suggestion.SearchSuggestionViewModel
import org.skepsun.kototoro.search.ui.suggestion.adapter.SearchSuggestionAdapter
import javax.inject.Inject
import com.google.android.material.R as materialR

@AndroidEntryPoint
class MainActivity : BaseActivity<ActivityMainBinding>(),
	View.OnClickListener,
	SearchSuggestionItemCallback.SuggestionItemListener,
	MainNavigationDelegate.OnFragmentChangedListener,
	View.OnLayoutChangeListener {

	@Inject
	lateinit var settings: AppSettings

	@Inject
	lateinit var backupStartupCoordinator: BackupStartupCoordinator

	private val viewModel by viewModels<MainViewModel>()
	private val searchSuggestionViewModel by viewModels<SearchSuggestionViewModel>()
	private val voiceInputLauncher = registerForActivityResult(VoiceInputContract()) { result ->
		if (result != null) {
			viewBinding.searchView.setText(result)
		}
	private lateinit var navigationDelegate: MainNavigationDelegate
	private var isFoldUnfolded = false
    private val navStateFlow = MutableStateFlow(BottomNavState())
    private lateinit var composeNavBarDelegator: ComposeAppNavBarDelegator
		if (!setContentViewWebViewSafe { org.skepsun.kototoro.databinding.ActivityMainBinding.inflate(layoutInflater) }) {
			return
		}
		
        composeNavBarDelegator = ComposeAppNavBarDelegator(this, navStateFlow)
		super.onCreate(savedInstanceState)
		
		viewBinding.composeBottomNav?.setContent {
			KototoroApp(
				navStateFlow = navStateFlow,
				onNavItemSelected = composeNavBarDelegator::handleItemSelected,
				onNavItemReselected = composeNavBarDelegator::handleItemSelected
			)
		}
        
		setSupportActionBar(viewBinding.searchBar)

		navigationDelegate = MainNavigationDelegate(
			navBar = composeNavBarDelegator,
			fragmentManager = supportFragmentManager,
			settings = settings,
		)
		navigationDelegate.addOnFragmentChangedListener(this)

		navigationDelegate.onCreate(this, savedInstanceState)
		viewBinding.textViewTitle?.let { tv ->
			navigationDelegate.observeTitle().observe(this) { tv.text = it }
		}

		addMenuProvider(MainMenuProvider(router, viewModel))

		val exitCallback = ExitCallback(this, viewBinding.container)
		onBackPressedDispatcher.addCallback(exitCallback)
		onBackPressedDispatcher.addCallback(navigationDelegate)

		onBackPressedDispatcher.addCallback(navigationDelegate)

		if (savedInstanceState == null) {
			onFirstStart()
			// 首次创建 Activity 时启动 WebDAV 自动同步监听（避免重复添加观察者）
		}

		viewModel.onOpenReader.observeEvent(this, this::onOpenReader)
		viewModel.onError.observeEvent(this, SnackbarErrorObserver(viewBinding.container, null))
		viewModel.isLoading.observe(this, this::onLoadingStateChanged)
		viewModel.isResumeEnabled.observe(this, this::onResumeEnabledChanged)
		viewModel.feedCounter.observe(this, ::onFeedCounterChanged)
		viewModel.appUpdate.observe(this, MenuInvalidator(this))
		viewModel.onFirstStart.observeEvent(this) { router.showWelcomeSheet() }
		viewModel.isBottomNavPinned.observe(this, ::setNavbarPinned)
		androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(viewBinding.container) { v, insets ->
			val isPinned = settings.isNavBarPinned
			val consumeBottom = isPinned && !isNavFloating
			val newInsets = insets.consume(v, WindowInsetsCompat.Type.systemBars(), bottom = consumeBottom)
			androidx.core.view.ViewCompat.onApplyWindowInsets(v, newInsets)
		}

		// 观察折叠屏状态变化
		observeFoldableState()
	}

	override fun onRestoreInstanceState(savedInstanceState: Bundle) {
		super.onRestoreInstanceState(savedInstanceState)
		navigationDelegate.syncSelectedItem()
	}

	override fun onResume() {
		super.onResume()
	}

	override fun onFragmentChanged(fragment: Fragment, fromUser: Boolean) {
		if (fromUser) {
			actionModeDelegate.finishActionMode()
		}
	}

	override fun addMenuProvider(provider: MenuProvider, owner: LifecycleOwner, state: Lifecycle.State) {
		if (provider !is ContentSearchMenuProvider) { // do not duplicate search menu item
			super.addMenuProvider(provider, owner, state)
		}
	}

	override fun onClick(v: View) {
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val typeMask = WindowInsetsCompat.Type.systemBars()
		val barsInsets = insets.getInsets(typeMask)
		val isNavFloating = settings.isNavFloating
		val searchBarDefaultMargin = resources.getDimensionPixelOffset(materialR.dimen.m3_searchbar_margin_horizontal)
		viewBinding.searchBar.updateLayoutParams<MarginLayoutParams> {
			marginEnd = searchBarDefaultMargin + barsInsets.end(v)
			marginStart = searchBarDefaultMargin + barsInsets.start(v)
		}
		val navMargin = if (isNavFloating) (16 * resources.displayMetrics.density).toInt() else 0
		val bottomPadding = if (isNavFloating) 0 else barsInsets.bottom
		val bottomMargin = if (isNavFloating) barsInsets.bottom + navMargin else 0

		viewBinding.container.clipChildren = false
		viewBinding.container.clipToPadding = false
		updateContainerBottomMargin()
		val consumedInsets = insets.consume(v, typeMask, start = false)
		val finalInsets = if (isNavFloating && settings.isNavBarPinned) {
			val floating = settings.isNavFloating
			val labeled = settings.isNavLabelsVisible
			val heightDp = if (floating) {
				settings.navFloatingHeight
			} else if (!labeled) {
				56
			} else {
				settings.navHeight
			}
			val bNavHeight = if (heightDp > 0) (heightDp * resources.displayMetrics.density).toInt() else (56 * resources.displayMetrics.density).toInt()
			// Use measured height or default 56dp roughly if not laid out yet
			val h = if (bNavHeight > 0) bNavHeight else (56 * resources.displayMetrics.density).toInt()
			val totalBottom = barsInsets.bottom + navMargin + h
			androidx.core.view.WindowInsetsCompat.Builder(consumedInsets)
				.setInsets(typeMask, androidx.core.graphics.Insets.of(barsInsets.left, barsInsets.top, barsInsets.right, totalBottom))
				.build()
		} else {
			consumedInsets
		}
		return finalInsets.also {
			handleSearchSuggestionsInsets(it)
		}
	}

	override fun onLayoutChange(
		v: View?,
		left: Int,
		top: Int,
		right: Int,
		bottom: Int,
		oldLeft: Int,
		oldTop: Int,
		oldRight: Int,
		oldBottom: Int
	) {
		if (top != oldTop || bottom != oldBottom) {
			updateContainerBottomMargin()
		}
	}

	override fun onSupportActionModeStarted(mode: ActionMode) {
		super.onSupportActionModeStarted(mode)
		updateContainerBottomMargin()
	}

	override fun onSupportActionModeFinished(mode: ActionMode) {
		super.onSupportActionModeFinished(mode)
		updateContainerBottomMargin()
	}

	private fun onOpenReader(manga: Content) {
		val fab = viewBinding.fab
		router.openReader(manga, fab)
	}

	private fun onFeedCounterChanged(counter: Int) {
		navigationDelegate.setCounter(NavItem.FEED, counter)
	}

	private fun onIncognitoModeChanged(isIncognito: Boolean) {
		var options = viewBinding.searchView.getEditText().imeOptions
		options = if (isIncognito) {
			options or EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING
		} else {
			options and EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING.inv()
		}
		viewBinding.searchView.getEditText().imeOptions = options
		invalidateOptionsMenu()
	}

	private fun onLoadingStateChanged(isLoading: Boolean) {
	}

	private fun onResumeEnabledChanged(isEnabled: Boolean) {
	}

	private fun onFirstStart() = try {
		lifecycleScope.launch(Dispatchers.Main) { // not a default `Main.immediate` dispatcher
			withContext(Dispatchers.Default) {
				LocalStorageCleanupWorker.enqueue(applicationContext)
			}
			withResumed {
				ContentPrefetchService.prefetchLast(this@MainActivity)
				requestNotificationsPermission()
				startService(Intent(this@MainActivity, LocalIndexUpdateService::class.java))
					backupStartupCoordinator.startOnFirstLaunch(lifecycleScope)
				// 延迟启动 WebDavAutoRestoreService 以避免系统级 DiskWriteViolation
				// 并且仅在 WebDAV 自动恢复开关开启且配置完整时启动
				if (settings.isAdBlockEnabled) {
					startService(Intent(this@MainActivity, AdListUpdateService::class.java))
				}
			}
		}
	} catch (e: IllegalStateException) {
		e.printStackTraceDebug()
	}

	private fun requestNotificationsPermission() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
				this,
				Manifest.permission.POST_NOTIFICATIONS,
			) != PERMISSION_GRANTED
		) {
			ActivityCompat.requestPermissions(
				this,
				arrayOf(Manifest.permission.POST_NOTIFICATIONS),
				1,
			)
		}
	}

	private fun setNavbarPinned(isPinned: Boolean) {
		navStateFlow.value = navStateFlow.value.copy(isPinned = isPinned)
		updateContainerBottomMargin()
	}

	private var isNavFloating = false

	private fun setNavFloating(isFloating: Boolean) {}

	private fun setNavHeight(heightDp: Int) {}

	private fun updateContainerBottomMargin() {
		val isPinned = settings.isNavBarPinned
		val floating = settings.isNavFloating
		val labeled = settings.isNavLabelsVisible
		val heightDp = if (floating) {
			settings.navFloatingHeight
		} else if (!labeled) {
			56
		} else {
			settings.navHeight
		}
		val px = if (heightDp > 0) (heightDp * resources.displayMetrics.density).toInt() else (56 * resources.displayMetrics.density).toInt()
		val newMargin = if (isPinned && !floating) px else 0
		with(viewBinding.container) {
			val params = layoutParams as MarginLayoutParams
			if (params.bottomMargin != newMargin) {
				params.bottomMargin = newMargin
				layoutParams = params
			}
		}
	}

	/**
	 * 观察折叠屏状态变化并调整布局
	 */
	private fun observeFoldableState() {
		val foldableState = FoldableUtils.observeFoldableState(this, this)
		
		lifecycleScope.launch {
			foldableState.collect { unfolded ->
				if (unfolded != isFoldUnfolded) {
					isFoldUnfolded = unfolded
					adjustLayoutForFoldableState()
				}
			}
		}
	}

	/**
	 * 根据折叠屏状态调整布局
	 */
	private fun adjustLayoutForFoldableState() {
		val shouldUseLandscapeLayout = FoldableUtils.shouldUseLandscapeLayout(this, isFoldUnfolded)
		val hasNavRail = viewBinding.navRail != null

		// 通知当前Fragment布局状态变化
		navigationDelegate.primaryFragment?.let { fragment ->
			onFragmentChanged(fragment, false)
		}
	}

}
