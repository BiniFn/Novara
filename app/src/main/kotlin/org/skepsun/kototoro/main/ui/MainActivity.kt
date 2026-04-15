package org.skepsun.kototoro.main.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.activity.viewModels
import androidx.annotation.IdRes
import androidx.appcompat.view.ActionMode
import androidx.core.app.ActivityCompat
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
import org.skepsun.kototoro.main.ui.owners.AppBarOwner
import org.skepsun.kototoro.main.ui.owners.BottomNavOwner
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
class MainActivity : BaseActivity<ActivityMainBinding>(), AppBarOwner, BottomNavOwner,
	View.OnClickListener,
	SearchSuggestionItemCallback.SuggestionItemListener,
	MainNavigationDelegate.OnFragmentChangedListener,
	View.OnLayoutChangeListener,
	SearchView.TransitionListener {

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
	}
	private lateinit var navigationDelegate: MainNavigationDelegate
	private lateinit var fadingAppbarMediator: FadingAppbarMediator
	private var isFoldUnfolded = false

	override val appBar: AppBarLayout
		get() = viewBinding.appbar

	override val bottomNav: SlidingBottomNavigationView?
		get() = viewBinding.bottomNav

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityMainBinding.inflate(layoutInflater))
		setSupportActionBar(viewBinding.searchBar)

		viewBinding.fab?.setOnClickListener(this)
		viewBinding.navRail?.headerView?.findViewById<View>(R.id.railFab)?.setOnClickListener(this)
		fadingAppbarMediator =
			FadingAppbarMediator(viewBinding.appbar, viewBinding.layoutSearch ?: viewBinding.searchBar)

		navigationDelegate = MainNavigationDelegate(
			navBar = checkNotNull(bottomNav ?: viewBinding.navRail),
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

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !resources.getBoolean(R.bool.is_predictive_back_enabled)) {
			val legacySearchCallback = SearchViewLegacyBackCallback(viewBinding.searchView)
			viewBinding.searchView.addTransitionListener(legacySearchCallback)
			onBackPressedDispatcher.addCallback(legacySearchCallback)
		}

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
		viewModel.isNavFloating.observe(this, ::setNavFloating)
		viewModel.navHeight.observe(this, ::setNavHeight)
		searchSuggestionViewModel.isIncognitoModeEnabled.observe(this, this::onIncognitoModeChanged)
		viewBinding.bottomNav?.addOnLayoutChangeListener(this)
		viewBinding.searchView.addTransitionListener(this)
		viewBinding.searchView.addTransitionListener(exitCallback)
		initSearch()
		
		androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(viewBinding.container) { v, insets ->
			val bottomNav = bottomNav
			val isPinned = bottomNav != null && bottomNav.isPinned && bottomNav.isShownOrShowing
			val consumeBottom = isPinned && !isNavFloating
			val newInsets = insets.consume(v, WindowInsetsCompat.Type.systemBars(), bottom = consumeBottom)
			androidx.core.view.ViewCompat.onApplyWindowInsets(v, newInsets)
		}

		// 观察折叠屏状态变化
		observeFoldableState()
	}

	override fun onRestoreInstanceState(savedInstanceState: Bundle) {
		super.onRestoreInstanceState(savedInstanceState)
		adjustSearchUI(viewBinding.searchView.isShowing)
		navigationDelegate.syncSelectedItem()
	}

	override fun onResume() {
		super.onResume()
	}

	fun selectMainNavigationItem(@IdRes itemId: Int): Boolean {
		return navigationDelegate.selectItem(itemId)
	}

	override fun onFragmentChanged(fragment: Fragment, fromUser: Boolean) {
		adjustFabVisibility(topFragment = fragment)
		adjustAppbar(topFragment = fragment)
		if (fromUser) {
			actionModeDelegate.finishActionMode()
			viewBinding.appbar.setExpanded(true)
		}
	}

	override fun addMenuProvider(provider: MenuProvider, owner: LifecycleOwner, state: Lifecycle.State) {
		if (provider !is ContentSearchMenuProvider) { // do not duplicate search menu item
			super.addMenuProvider(provider, owner, state)
		}
	}

	override fun onClick(v: View) {
		when (v.id) {
			R.id.fab, R.id.railFab -> viewModel.openLastReader()
		}
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val typeMask = WindowInsetsCompat.Type.systemBars()
		val barsInsets = insets.getInsets(typeMask)
		val searchBarDefaultMargin = resources.getDimensionPixelOffset(materialR.dimen.m3_searchbar_margin_horizontal)
		viewBinding.searchBar.updateLayoutParams<MarginLayoutParams> {
			marginEnd = searchBarDefaultMargin + barsInsets.end(v)
			marginStart = if (viewBinding.navRail != null) {
				searchBarDefaultMargin
			} else {
				searchBarDefaultMargin + barsInsets.start(v)
			}
		}
		val navMargin = if (isNavFloating) (16 * resources.displayMetrics.density).toInt() else 0
		val bottomPadding = if (isNavFloating) 0 else barsInsets.bottom
		val bottomMargin = if (isNavFloating) barsInsets.bottom + navMargin else 0

		viewBinding.bottomNav?.updatePadding(
			left = if (isNavFloating) 0 else barsInsets.left,
			right = if (isNavFloating) 0 else barsInsets.right,
			bottom = bottomPadding,
		)
		
		viewBinding.bottomNav?.updateLayoutParams<MarginLayoutParams> {
			leftMargin = if (isNavFloating) barsInsets.left + navMargin else 0
			rightMargin = if (isNavFloating) barsInsets.right + navMargin else 0
			this.bottomMargin = bottomMargin
		}

		viewBinding.bottomNav?.let { bottomNav ->
			val bg = bottomNav.background
			if (bg is com.google.android.material.shape.MaterialShapeDrawable) {
				val radius = if (isNavFloating) 24 * resources.displayMetrics.density else 0f
				bg.shapeAppearanceModel = bg.shapeAppearanceModel.toBuilder()
					.setAllCornerSizes(radius)
					.build()
				bottomNav.clipToOutline = isNavFloating
			}

			val appSettings = dagger.hilt.android.EntryPointAccessors.fromApplication<org.skepsun.kototoro.core.ui.BaseActivityEntryPoint>(applicationContext).settings
			val blurMode = appSettings.blurMode
			if (bg is com.google.android.material.shape.MaterialShapeDrawable) {
				val baseColor = com.google.android.material.color.MaterialColors.getColor(bottomNav, com.google.android.material.R.attr.colorSurfaceContainer)
				if (isNavFloating && blurMode != org.skepsun.kototoro.core.prefs.AppSettings.BlurMode.STANDARD) {
					val alphaVal = if (blurMode == org.skepsun.kototoro.core.prefs.AppSettings.BlurMode.ENHANCED) 180 else 220
					bg.fillColor = android.content.res.ColorStateList.valueOf(androidx.core.graphics.ColorUtils.setAlphaComponent(baseColor, alphaVal))
				} else {
					bg.fillColor = android.content.res.ColorStateList.valueOf(baseColor)
				}
			}
		}

		viewBinding.navRail?.updateLayoutParams<MarginLayoutParams> {
			marginStart = barsInsets.start(v)
			topMargin = barsInsets.top
			this.bottomMargin = barsInsets.bottom
		}
		viewBinding.container.clipChildren = false
		viewBinding.container.clipToPadding = false
		updateContainerBottomMargin()
		val consumedInsets = insets.consume(v, typeMask, start = viewBinding.navRail != null)
		val finalInsets = if (isNavFloating && viewBinding.bottomNav?.isPinned == true) {
			val bNavHeight = viewBinding.bottomNav?.height ?: 0
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

	override fun onStateChanged(
		searchView: SearchView,
		previousState: SearchView.TransitionState,
		newState: SearchView.TransitionState,
	) {
		val wasOpened = previousState >= SearchView.TransitionState.SHOWING
		val isOpened = newState >= SearchView.TransitionState.SHOWING
		if (isOpened != wasOpened) {
			adjustSearchUI(isOpened)
		}
	}

	override fun onRemoveQuery(query: String) {
		searchSuggestionViewModel.deleteQuery(query)
	}

	override fun onSupportActionModeStarted(mode: ActionMode) {
		super.onSupportActionModeStarted(mode)
		adjustFabVisibility()
		bottomNav?.hide()
		(viewBinding.layoutSearch ?: viewBinding.searchBar).isInvisible = true
		updateContainerBottomMargin()
	}

	override fun onSupportActionModeFinished(mode: ActionMode) {
		super.onSupportActionModeFinished(mode)
		adjustFabVisibility()
		bottomNav?.show()
		(viewBinding.layoutSearch ?: viewBinding.searchBar).isInvisible = false
		updateContainerBottomMargin()
	}

	private fun onOpenReader(manga: Content) {
		val fab = viewBinding.fab ?: viewBinding.navRail?.headerView
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
		val fab = viewBinding.fab ?: viewBinding.navRail?.headerView ?: return
		fab.isEnabled = !isLoading
	}

	private fun onResumeEnabledChanged(isEnabled: Boolean) {
		adjustFabVisibility(isResumeEnabled = isEnabled)
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

	private fun adjustAppbar(topFragment: Fragment) {
		if (topFragment is HomeFragment || topFragment is FavouritesContainerFragment || topFragment is ExploreFragment || topFragment is FeedFragment) {
			viewBinding.appbar.fitsSystemWindows = true
		} else {
			viewBinding.appbar.fitsSystemWindows = false
		}

		if (topFragment is HomeFragment) {
			// HomeFragment uses a NestedScrollView whose content scrolls behind the
			// transparent AppBarLayout.  Give the AppBar a solid surface colour so the
			// pinned SearchBar area is opaque – matching all other pages.
			fadingAppbarMediator.unbind()
			val surfaceColor = com.google.android.material.color.MaterialColors.getColor(
				viewBinding.appbar,
				com.google.android.material.R.attr.colorSurface,
			)
			viewBinding.appbar.setBackgroundColor(surfaceColor)
		} else if (topFragment is FavouritesContainerFragment || topFragment is ExploreFragment || topFragment is FeedFragment) {
			viewBinding.appbar.setBackgroundColor(android.graphics.Color.TRANSPARENT)
			fadingAppbarMediator.bind()
		} else {
			viewBinding.appbar.setBackgroundColor(android.graphics.Color.TRANSPARENT)
			fadingAppbarMediator.unbind()
		}
	}

	private fun adjustFabVisibility(
		isResumeEnabled: Boolean = viewModel.isResumeEnabled.value,
		topFragment: Fragment? = navigationDelegate.primaryFragment,
		isSearchOpened: Boolean = viewBinding.searchView.isShowing,
	) {
		navigationDelegate.navRailHeader?.railFab?.isVisible = isResumeEnabled
		val fab = viewBinding.fab ?: return
		if (isResumeEnabled && !actionModeDelegate.isActionModeStarted && !isSearchOpened && topFragment is HistoryListFragment) {
			if (!fab.isVisible) {
				fab.show()
			}
		} else {
			if (fab.isVisible) {
				fab.hide()
			}
		}
	}

	private fun adjustSearchUI(isOpened: Boolean) {
		val appBarScrollFlags = if (isOpened) {
			SCROLL_FLAG_NO_SCROLL
		} else {
			SCROLL_FLAG_SCROLL or SCROLL_FLAG_ENTER_ALWAYS or SCROLL_FLAG_SNAP
		}
		viewBinding.insetsHolder.updateLayoutParams<AppBarLayout.LayoutParams> {
			scrollFlags = appBarScrollFlags
		}
		adjustFabVisibility(isSearchOpened = isOpened)
		bottomNav?.showOrHide(!isOpened)
		updateContainerBottomMargin()
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

	private fun handleSearchSuggestionsInsets(insets: WindowInsetsCompat) {
		val typeMask = WindowInsetsCompat.Type.ime() or WindowInsetsCompat.Type.systemBars()
		val barsInsets = insets.getInsets(typeMask)
		viewBinding.recyclerViewSearch.setPadding(barsInsets.left, 0, barsInsets.right, barsInsets.bottom)
	}

	private fun initSearch() {
		val listener = SearchSuggestionListenerImpl(router, viewBinding.searchView, searchSuggestionViewModel)
		val adapter = SearchSuggestionAdapter(listener)
		viewBinding.searchView.toolbar.addMenuProvider(
			SearchSuggestionMenuProvider(this, voiceInputLauncher, searchSuggestionViewModel),
		)
		viewBinding.searchView.editText.addTextChangedListener(listener)
		viewBinding.recyclerViewSearch.adapter = adapter
		viewBinding.searchView.editText.setOnEditorActionListener(listener)

		viewBinding.searchView.observeState()
			.map { it >= SearchView.TransitionState.SHOWING }
			.distinctUntilChanged()
			.flatMapLatest { isShowing ->
				if (isShowing) {
					searchSuggestionViewModel.suggestion
				} else {
					emptyFlow()
				}
			}.observe(this, adapter)
		searchSuggestionViewModel.onError.observeEvent(
			this,
			SnackbarErrorObserver(viewBinding.recyclerViewSearch, null),
		)
		ItemTouchHelper(SearchSuggestionItemCallback(this))
			.attachToRecyclerView(viewBinding.recyclerViewSearch)
	}

	private fun setNavbarPinned(isPinned: Boolean) {
		val bottomNavBar = viewBinding.bottomNav
		bottomNavBar?.isPinned = isPinned
		for (view in viewBinding.appbar.children) {
			val lp = view.layoutParams as? AppBarLayout.LayoutParams ?: continue
			val scrollFlags = if (isPinned) {
				lp.scrollFlags and SCROLL_FLAG_SCROLL.inv()
			} else {
				lp.scrollFlags or SCROLL_FLAG_SCROLL
			}
			if (scrollFlags != lp.scrollFlags) {
				lp.scrollFlags = scrollFlags
				view.layoutParams = lp
			}
		}
		updateContainerBottomMargin()
		androidx.core.view.ViewCompat.requestApplyInsets(viewBinding.root)
	}

	private var isNavFloating = false

	private fun setNavFloating(isFloating: Boolean) {
		isNavFloating = isFloating
		val bottomNavBar = viewBinding.bottomNav ?: return
		val radius = if (isFloating) 24 * resources.displayMetrics.density else 0f
		val background = bottomNavBar.background
		if (background is com.google.android.material.shape.MaterialShapeDrawable) {
			background.shapeAppearanceModel = background.shapeAppearanceModel.toBuilder()
				.setAllCornerSizes(radius)
				.build()
		}
		val floating = settings.isNavFloating
		val labeled = settings.isNavLabelsVisible
		val heightDp = if (floating) {
			settings.navFloatingHeight
		} else if (!labeled) {
			56
		} else {
			settings.navHeight
		}
		setNavHeight(heightDp)
		
		androidx.core.view.ViewCompat.requestApplyInsets(viewBinding.root)
	}

	private fun setNavHeight(heightDp: Int) {
		val bottomNavBar = viewBinding.bottomNav ?: return
		val px = (heightDp * resources.displayMetrics.density).toInt()
		bottomNavBar.minimumHeight = px
		
		val padding = (12 * resources.displayMetrics.density).toInt()
		bottomNavBar.itemActiveIndicatorHeight = if (isNavFloating) px - padding else (32 * resources.displayMetrics.density).toInt()
		
		// In SlidingBottomNavigationView, the minimumHeight forces the view to take that height,
		// and the icons will naturally remain centered.
		androidx.core.view.ViewCompat.requestApplyInsets(viewBinding.root)
	}

	private fun updateContainerBottomMargin() {
		val bottomNavBar = viewBinding.bottomNav ?: return
		val newMargin = if (bottomNavBar.isPinned && bottomNavBar.isShownOrShowing && !isNavFloating) bottomNavBar.height else 0
		with(viewBinding.container) {
			val params = layoutParams as MarginLayoutParams
			if (params.bottomMargin != newMargin) {
				params.bottomMargin = newMargin
				layoutParams = params
				androidx.core.view.ViewCompat.requestApplyInsets(this)
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

		// 仅根据布局需要切换导航栏，不改变屏幕方向
		if (shouldUseLandscapeLayout && hasNavRail) {
			// 展开或横屏且存在侧栏视图时，显示侧栏，隐藏底部栏
			viewBinding.navRail?.isVisible = true
			viewBinding.bottomNav?.isVisible = false
		} else {
			// 其他情况显示底部栏；如果存在侧栏视图则隐藏
			viewBinding.navRail?.isVisible = false
			viewBinding.bottomNav?.isVisible = true
		}

		// 通知当前Fragment布局状态变化
		navigationDelegate.primaryFragment?.let { fragment ->
			onFragmentChanged(fragment, false)
		}
	}

	private fun SearchView.observeState() = callbackFlow {
		val listener = SearchView.TransitionListener { _, _, state ->
			trySendBlocking(state)
		}
		addTransitionListener(listener)
		awaitClose { removeTransitionListener(listener) }
	}
}
