package org.skepsun.kototoro.settings

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import androidx.activity.viewModels
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePaddingRelative
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import kotlinx.coroutines.launch
import com.google.android.material.appbar.AppBarLayout
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.ContentSource
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.ui.BaseActivity
import org.skepsun.kototoro.core.util.ext.buildBundle
import org.skepsun.kototoro.core.util.ext.end
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.core.util.ext.observeEvent
import org.skepsun.kototoro.core.util.ext.start
import org.skepsun.kototoro.core.util.ext.textAndVisible
import org.skepsun.kototoro.databinding.ActivitySettingsBinding

import org.skepsun.kototoro.settings.about.AboutSettingsFragment
import org.skepsun.kototoro.settings.discord.DiscordSettingsFragment
import org.skepsun.kototoro.settings.search.SettingsItem
import org.skepsun.kototoro.settings.search.SettingsSearchFragment
import org.skepsun.kototoro.settings.search.SettingsSearchViewModel
import org.skepsun.kototoro.settings.sources.SourceSettingsHostFragment
import org.skepsun.kototoro.settings.sources.SourcesSettingsFragment
import org.skepsun.kototoro.settings.sources.unified.UnifiedSourcesFragment
import org.skepsun.kototoro.settings.tracker.TrackerSettingsFragment
import org.skepsun.kototoro.settings.userdata.BackupsSettingsFragment
import org.skepsun.kototoro.core.util.FoldableUtils

@AndroidEntryPoint
class SettingsActivity :
	BaseActivity<ActivitySettingsBinding>(),
	PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

	private val isMasterDetails
		get() = viewBinding.containerMaster != null && if (kototoroAppSettings.tabletUiMode == org.skepsun.kototoro.core.prefs.TabletUiMode.STRICT) {
			resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
		} else {
			true
		}

	private val viewModel: SettingsSearchViewModel by viewModels()

	private var isFoldUnfolded = false

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivitySettingsBinding.inflate(layoutInflater))
		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = false)
		val fm = supportFragmentManager
		val currentFragment = fm.findFragmentById(R.id.container)
		if (currentFragment == null || (isMasterDetails && currentFragment is RootSettingsFragment)) {
			openDefaultFragment()
		}
		if (isMasterDetails && fm.findFragmentById(R.id.container_master) == null) {
			supportFragmentManager.commit {
				setReorderingAllowed(true)
				replace(R.id.container_master, RootSettingsFragment())
			}
		} else if (!isMasterDetails) {
			val zombieMaster = fm.findFragmentById(R.id.container_master)
			if (zombieMaster != null) {
				fm.commit {
					remove(zombieMaster)
				}
			}
		}
		viewModel.isSearchActive.observe(this, ::toggleSearchMode)
		viewModel.onNavigateToPreference.observeEvent(this, ::navigateToPreference)
		
		observeFoldableState()
	}

	override fun onResume() {
		super.onResume()
		// 从后台恢复或状态变化后，立即按当前折叠状态调整布局
		adjustLayoutForFoldableState()
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
		val isTablet = viewBinding.containerMaster != null
		viewBinding.appbar.updatePaddingRelative(
			start = bars.start(v),
			top = bars.top,
			end = if (isTablet) 0 else bars.end(v),
		)
		viewBinding.appbarDetail?.updatePaddingRelative(
			end = bars.end(v),
			top = bars.top,
		)
		return insets
	}

	override fun onPreferenceStartFragment(
		caller: PreferenceFragmentCompat,
		pref: Preference,
	): Boolean {
		val fragmentName = pref.fragment ?: return false
		openFragment(
			fragmentClass = FragmentFactory.loadFragmentClass(classLoader, fragmentName),
			args = pref.peekExtras(),
			isFromRoot = false,
		)
		return true
	}

	fun setSectionTitle(title: CharSequence?) {
		viewBinding.toolbarDetail?.let { toolbar ->
			toolbar.title = title
			toolbar.isVisible = title != null
		} ?: setTitle(title ?: getString(R.string.settings))
	}

	fun setSectionToolbarActions(view: View?, fillAvailableWidth: Boolean = false) {
		val toolbar = viewBinding.toolbarDetail ?: viewBinding.toolbar
		val tag = "section_toolbar_actions"
		(0 until toolbar.childCount)
			.map { toolbar.getChildAt(it) }
			.firstOrNull { it.tag == tag }
			?.let(toolbar::removeView)
		if (view == null) return

		view.tag = tag
		val maxActionWidth = (420 * resources.displayMetrics.density).toInt()
		val actionWidth = (resources.displayMetrics.widthPixels * 0.62f).toInt()
			.coerceAtMost(maxActionWidth)
		toolbar.addView(
			view,
			androidx.appcompat.widget.Toolbar.LayoutParams(
				if (fillAvailableWidth) ViewGroup.LayoutParams.MATCH_PARENT else actionWidth,
				ViewGroup.LayoutParams.MATCH_PARENT,
				Gravity.END or Gravity.CENTER_VERTICAL,
			),
		)
	}

	fun openFragment(fragmentClass: Class<out Fragment>, args: Bundle?, isFromRoot: Boolean) {
		viewModel.discardSearch()
		val hasFragment = supportFragmentManager.findFragmentById(R.id.container) != null
		supportFragmentManager.commit {
			setReorderingAllowed(true)
			replace(R.id.container, fragmentClass, args)
			setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
			if (!isMasterDetails || (hasFragment && !isFromRoot)) {
				addToBackStack(null)
			}
		}
	}

	private fun toggleSearchMode(isEnabled: Boolean) {
		viewBinding.containerSearch.isVisible = isEnabled
		val searchFragment = supportFragmentManager.findFragmentById(R.id.container_search)
		if (searchFragment != null) {
			if (!isEnabled) {
				invalidateOptionsMenu()
				supportFragmentManager.commit {
					setReorderingAllowed(true)
					remove(searchFragment)
					setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)
				}
			}
		} else if (isEnabled) {
			supportFragmentManager.commit {
				setReorderingAllowed(true)
				add(R.id.container_search, SettingsSearchFragment::class.java, null)
				setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
			}
		}
	}

	private fun openDefaultFragment() {
		val fragment = when (intent?.action) {
			AppRouter.ACTION_READER -> ReaderSettingsFragment()
			AppRouter.ACTION_SUGGESTIONS -> SuggestionsSettingsFragment()
			AppRouter.ACTION_HISTORY -> BackupsSettingsFragment()
			AppRouter.ACTION_SYNC_SETTINGS -> BackupsSettingsFragment()
			AppRouter.ACTION_TRACKER -> TrackerSettingsFragment()
			AppRouter.ACTION_TRANSLATION -> org.skepsun.kototoro.settings.TranslationSettingsFragment()
			AppRouter.ACTION_PERIODIC_BACKUP -> BackupsSettingsFragment()
			AppRouter.ACTION_SOURCES -> SourcesSettingsFragment()
			AppRouter.ACTION_MANAGE_DISCORD -> DiscordSettingsFragment()
			AppRouter.ACTION_PROXY -> ProxySettingsFragment()
			AppRouter.ACTION_MANAGE_DOWNLOADS -> DownloadsSettingsFragment()
			AppRouter.ACTION_SOURCE -> SourceSettingsHostFragment.newInstance(
				ContentSource(intent.getStringExtra(AppRouter.KEY_SOURCE)),
			)

			AppRouter.ACTION_MANAGE_SOURCES -> UnifiedSourcesFragment()
			Intent.ACTION_VIEW -> {
				when (intent.data?.host) {
					HOST_ABOUT -> AboutSettingsFragment()
					HOST_SYNC_SETTINGS -> SyncSettingsFragment()
					"add-repo" -> {
						val url = intent.data?.getQueryParameter("url")
						val type = when (intent.data?.scheme) {
							"aniyomi", "anikku" -> org.skepsun.kototoro.extensions.repo.ExternalExtensionType.ANIYOMI
							else -> org.skepsun.kototoro.extensions.repo.ExternalExtensionType.MIHON
						}
						org.skepsun.kototoro.settings.sources.extensions.ExtensionRepositoriesFragment().apply {
							arguments = Bundle().apply {
								putString(org.skepsun.kototoro.settings.sources.extensions.ARG_EXTENSION_TYPE, type.name)
								putString("add_repo_url", url)
							}
						}
					}
					else -> null
				}
			}

			else -> null
		} ?: if (isMasterDetails) AppearanceSettingsFragment() else RootSettingsFragment()
		supportFragmentManager.commit {
			setReorderingAllowed(true)
			replace(R.id.container, fragment)
		}
	}

	private fun navigateToPreference(item: SettingsItem) {
		val args = buildBundle(1) {
			putString(ARG_PREF_KEY, item.key)
		}
		openFragment(item.fragmentClass, args, true)
	}

	companion object {

		private const val HOST_ABOUT = "about"
		private const val HOST_SYNC_SETTINGS = "sync-settings"
		const val ARG_PREF_KEY = "pref_key"
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
        // 设置页不改变屏幕方向，仅保持默认方向
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        // 仅在折叠屏展开且窗口满足双栏宽度时重建，避免分屏窄窗口反复重建
        if (isFoldUnfolded && viewBinding.containerMaster == null && FoldableUtils.shouldUseTwoPaneLayout(this)) {
            recreate()
            return
        }

        viewBinding.root.requestLayout()
    }
}
