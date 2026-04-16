package org.skepsun.kototoro.main.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.IdRes
import androidx.appcompat.view.ActionMode
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.skepsun.kototoro.core.ui.util.MenuInvalidator
import org.skepsun.kototoro.core.ui.widgets.BottomNavState
import org.skepsun.kototoro.core.util.FoldableUtils
import org.skepsun.kototoro.core.util.ext.consume
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.core.util.ext.observeEvent
import org.skepsun.kototoro.databinding.ActivityMainBinding
import org.skepsun.kototoro.details.service.ContentPrefetchService
import org.skepsun.kototoro.explore.ui.model.BrowseGroupTab
import org.skepsun.kototoro.explore.ui.model.SourceTag
import org.skepsun.kototoro.local.ui.LocalIndexUpdateService
import org.skepsun.kototoro.local.ui.LocalStorageCleanupWorker
import org.skepsun.kototoro.main.ui.compose.ComposeAppNavBarDelegator
import org.skepsun.kototoro.main.ui.compose.KototoroApp
import org.skepsun.kototoro.main.ui.owners.BottomNavOwner
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.remotelist.ui.ContentSearchMenuProvider
import org.skepsun.kototoro.search.ui.suggestion.SearchSuggestionViewModel
import javax.inject.Inject
import androidx.fragment.app.FragmentContainerView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier

@AndroidEntryPoint
class MainActivity : BaseActivity<ActivityMainBinding>(),
	View.OnClickListener,
	MainNavigationDelegate.OnFragmentChangedListener,
	View.OnLayoutChangeListener {

	@Inject
	lateinit var settings: AppSettings

	@Inject
	lateinit var backupStartupCoordinator: BackupStartupCoordinator

	private val viewModel by viewModels<MainViewModel>()
	private val searchSuggestionViewModel by viewModels<SearchSuggestionViewModel>()
	private val voiceInputLauncher = registerForActivityResult(VoiceInputContract()) { result ->
		// TODO: Pass voice input result to Compose SearchBar state
	}

	private lateinit var navigationDelegate: MainNavigationDelegate
	private var isFoldUnfolded = false
	private val navStateFlow = MutableStateFlow(BottomNavState())
	private lateinit var composeNavBarDelegator: ComposeAppNavBarDelegator

	private var topBarHeightPx = 0
	private var bottomNavHeightPx = 0
	
	private var isResumeEnabledState by androidx.compose.runtime.mutableStateOf(false)

	private var currentFilterCallback: SearchBarFilterViewController.Callback? = null
	private var activeFilterContentType by mutableStateOf<ContentType?>(null)
	private var activeFilterSourceTags by mutableStateOf<Set<SourceTag>>(emptySet())
	
	private lateinit var container: FragmentContainerView

	fun setActiveFilterCallback(callback: SearchBarFilterViewController.Callback) {
		currentFilterCallback = callback
		refreshFilters()
	}

	fun clearActiveFilterCallback(callback: SearchBarFilterViewController.Callback) {
		if (currentFilterCallback == callback) {
			currentFilterCallback = null
		}
	}

	fun refreshFilters() {
		val callback = currentFilterCallback ?: return
		val selectedTab = callback.getSelectedContentType()
		activeFilterContentType = when (selectedTab) {
			BrowseGroupTab.Novel -> ContentType.NOVEL
			BrowseGroupTab.Video -> ContentType.VIDEO
			BrowseGroupTab.Content -> ContentType.MANGA
			else -> null
		}
		activeFilterSourceTags = callback.getSelectedSourceTags()
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		
		composeNavBarDelegator = ComposeAppNavBarDelegator(this, navStateFlow)
		
		viewModel.isResumeEnabled.observe(this) { isEnabled ->
			isResumeEnabledState = isEnabled
		}
		
		if (!setContentViewWebViewSafe { ActivityMainBinding.inflate(layoutInflater) }) {
			return
		}

		viewBinding.composeRoot.setContent {
			val suggestions by searchSuggestionViewModel.suggestion.collectAsState(initial = emptyList())

			KototoroApp(
					appSettings = settings,
					navStateFlow = navStateFlow,
					isResumeEnabled = isResumeEnabledState,
					onResumeClick = viewModel::openLastReader,
					onNavItemSelected = composeNavBarDelegator::handleItemSelected,
					onNavItemReselected = composeNavBarDelegator::handleItemSelected,
					onContainerReady = { fragmentContainer ->
						if (!::container.isInitialized) {
							container = fragmentContainer
							
							// Initialize NavigationDelegate when container is physically mounted
							navigationDelegate = MainNavigationDelegate(
								navBar = composeNavBarDelegator,
								fragmentManager = supportFragmentManager,
								settings = settings,
							)
							navigationDelegate.addOnFragmentChangedListener(this@MainActivity)
							navigationDelegate.onCreate(this@MainActivity, savedInstanceState)
							
							addMenuProvider(MainMenuProvider(router, viewModel))
							
							val exitCallback = ExitCallback(this@MainActivity, container)
							onBackPressedDispatcher.addCallback(exitCallback)
							onBackPressedDispatcher.addCallback(navigationDelegate)
							
							
							if (savedInstanceState == null) {
								onFirstStart()
							}
                            
                            viewModel.onOpenReader.observeEvent(this@MainActivity, this@MainActivity::onOpenReader)
                            viewModel.onError.observeEvent(this@MainActivity, org.skepsun.kototoro.core.exceptions.resolve.SnackbarErrorObserver(container, null))
                            viewModel.isLoading.observe(this@MainActivity, this@MainActivity::onLoadingStateChanged)
                            viewModel.isResumeEnabled.observe(this@MainActivity, this@MainActivity::onResumeEnabledChanged)
                            viewModel.feedCounter.observe(this@MainActivity, ::onFeedCounterChanged)
                            viewModel.appUpdate.observe(this@MainActivity, org.skepsun.kototoro.core.ui.util.MenuInvalidator(this@MainActivity))
                            
                            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
                                val sysInsets = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                                
                                // Return the insets so container natively dispatches them unmodified to children
                                // Children will rely on MainViewModel for their topBar and bottomNav physical heights
                                insets
                            }
						}
					},
					suggestions = suggestions,
				onQueryChanged = searchSuggestionViewModel::onQueryChanged,
				onSearch = { query ->
					searchSuggestionViewModel.saveQuery(query)
					router.openSearch(query)
				},
				onSuggestionClick = { item ->
					// Handle suggestion click by type
				},
				onDeleteQuery = searchSuggestionViewModel::deleteQuery,
				onVoiceInput = {
					try {
						voiceInputLauncher.launch(null)
					} catch (e: Exception) {
						android.widget.Toast.makeText(this@MainActivity, R.string.voice_search, android.widget.Toast.LENGTH_SHORT).show()
						e.printStackTrace()
					}
				},
				onMoreClick = { anchorView ->
					if (anchorView != null) {
						showOverflowMenu(anchorView)
					} else {
						showOverflowMenu(viewBinding.composeRoot)
					}
				},
				onTopBarHeightChanged = { height ->
					if (topBarHeightPx != height) {
						topBarHeightPx = height
						viewModel.setTopBarHeightPx(height)
					}
				},
				onBottomNavHeightChanged = { height ->
					if (bottomNavHeightPx != height) {
						bottomNavHeightPx = height
						viewModel.setBottomNavHeightPx(height)
					}
				},
				selectedContentType = activeFilterContentType,
				onContentTypeSelected = { type ->
					activeFilterContentType = type
					val tab = when (type) {
						ContentType.NOVEL -> BrowseGroupTab.Novel
						ContentType.VIDEO -> BrowseGroupTab.Video
						ContentType.MANGA -> BrowseGroupTab.Content
						else -> BrowseGroupTab.All
					}
					currentFilterCallback?.onContentTypeSelected(tab)
				},
				selectedSourceTags = activeFilterSourceTags,
				onSourceTagSelected = { tag ->
					activeFilterSourceTags = if (tag != null) setOf(tag) else emptySet()
					currentFilterCallback?.onSourceTagSelected(tag)
				}
			)
		}
		viewModel.onFirstStart.observeEvent(this) { router.showWelcomeSheet() }
		viewModel.isBottomNavPinned.observe(this, ::setNavbarPinned)
		
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

	fun selectMainNavigationItem(@IdRes itemId: Int): Boolean {
		return navigationDelegate.selectItem(itemId)
	}

	override fun onFragmentChanged(fragment: Fragment, fromUser: Boolean) {
		if (fromUser) {
			actionModeDelegate.finishActionMode()
		}
		// Active fragment changed, check if it has a filter menu
		if (fragment is SearchBarFilterViewController.Callback) {
			setActiveFilterCallback(fragment)
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
		val navMargin = if (isNavFloating) (16 * resources.displayMetrics.density).toInt() else 0
		val bottomPadding = if (isNavFloating) 0 else barsInsets.bottom

		container.clipChildren = false
		container.clipToPadding = false
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
			
			val h = if (bNavHeight > 0) bNavHeight else (56 * resources.displayMetrics.density).toInt()
			val totalBottom = barsInsets.bottom + navMargin + h
			androidx.core.view.WindowInsetsCompat.Builder(consumedInsets)
				.setInsets(typeMask, androidx.core.graphics.Insets.of(barsInsets.left, barsInsets.top, barsInsets.right, totalBottom))
				.build()
		} else {
			consumedInsets
		}
		return finalInsets
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
		router.openReader(manga, null)
	}

	private fun onFeedCounterChanged(counter: Int) {
		navigationDelegate.setCounter(NavItem.FEED, counter)
	}

	private fun onIncognitoModeChanged(isIncognito: Boolean) {
		invalidateOptionsMenu()
	}

	private fun onLoadingStateChanged(isLoading: Boolean) {
	}

	private fun onResumeEnabledChanged(isEnabled: Boolean) {
		isResumeEnabledState = isEnabled
	}

	private fun onFirstStart() = try {
		lifecycleScope.launch(Dispatchers.Main) { 
			withContext(Dispatchers.Default) {
				LocalStorageCleanupWorker.enqueue(applicationContext)
			}
			withResumed {
				ContentPrefetchService.prefetchLast(this@MainActivity)
				requestNotificationsPermission()
				startService(Intent(this@MainActivity, LocalIndexUpdateService::class.java))
					backupStartupCoordinator.startOnFirstLaunch(lifecycleScope)
				
				if (settings.isAdBlockEnabled) {
					startService(Intent(this@MainActivity, AdListUpdateService::class.java))
				}
			}
		}
	} catch (e: IllegalStateException) {
		e.printStackTrace()
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
		updateContainerBottomMargin()
	}

	private var isNavFloating = false

	private fun setNavFloating(isFloating: Boolean) {}

	private fun setNavHeight(heightDp: Int) {}

	private fun updateContainerPadding() {
		if (!::container.isInitialized) return
		container.requestApplyInsets()
	}

	private fun updateContainerBottomMargin() {
		updateContainerPadding()
	}

	private fun showOverflowMenu(anchorView: android.view.View?) {
		val anchor = anchorView ?: viewBinding.composeRoot
		val popup = androidx.appcompat.widget.PopupMenu(this, anchor, android.view.Gravity.END or android.view.Gravity.TOP)
		popup.menuInflater.inflate(R.menu.opt_main, popup.menu)
		
		popup.menu.findItem(R.id.action_incognito)?.isChecked = viewModel.isIncognitoModeEnabled.value
		popup.menu.findItem(R.id.action_app_update)?.isVisible = viewModel.appUpdate.value != null
		
		val displayItem = popup.menu.findItem(R.id.action_display_mode)
		displayItem?.title = getString(R.string.list_options)

		popup.setOnMenuItemClickListener { menuItem ->
			when (menuItem.itemId) {
				R.id.action_settings -> {
					router.openSettings()
					true
				}
				R.id.action_display_mode -> {
					router.showListConfigSheet(org.skepsun.kototoro.list.ui.config.ListConfigSection.General)
					true
				}
				R.id.action_incognito -> {
					viewModel.setIncognitoMode(!menuItem.isChecked)
					true
				}
				R.id.action_app_update -> {
					router.openAppUpdate()
					true
				}
				else -> false
			}
		}
		popup.show()
	}

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

	private fun adjustLayoutForFoldableState() {
		val shouldUseLandscapeLayout = FoldableUtils.shouldUseLandscapeLayout(this, isFoldUnfolded)

		navigationDelegate.primaryFragment?.let { fragment ->
			onFragmentChanged(fragment, false)
		}
	}
}
