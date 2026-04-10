package org.skepsun.kototoro.search.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.toDrawable
import androidx.core.os.bundleOf
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePaddingRelative
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.AppBarLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.LocalMangaSource
import org.skepsun.kototoro.core.model.ContentSource
import org.skepsun.kototoro.core.model.getSummary
import org.skepsun.kototoro.core.model.getTitle
import org.skepsun.kototoro.core.model.isNsfw
import org.skepsun.kototoro.core.model.parcelable.ParcelableContent
import org.skepsun.kototoro.core.model.parcelable.ParcelableContentListFilter
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.ui.BaseActivity
import org.skepsun.kototoro.core.ui.model.titleRes
import org.skepsun.kototoro.core.ui.util.FadingAppbarMediator
import org.skepsun.kototoro.core.util.ViewBadge
import org.skepsun.kototoro.core.util.ext.consumeSystemBarsInsets
import org.skepsun.kototoro.core.util.ext.end
import org.skepsun.kototoro.core.util.ext.getParcelableExtraCompat
import org.skepsun.kototoro.core.util.ext.getSerializableExtraCompat
import org.skepsun.kototoro.core.util.ext.getThemeColor
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.core.util.ext.setTextAndVisible
import org.skepsun.kototoro.core.util.ext.start
import org.skepsun.kototoro.databinding.ActivityContentListBinding
import org.skepsun.kototoro.filter.ui.FilterCoordinator
import org.skepsun.kototoro.filter.ui.FilterHeaderFragment
import org.skepsun.kototoro.filter.ui.sheet.FilterSheetFragment
import org.skepsun.kototoro.list.ui.preview.PreviewFragment
import org.skepsun.kototoro.local.ui.LocalListFragment
import org.skepsun.kototoro.core.util.FoldableUtils
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentListFilter
import org.skepsun.kototoro.parsers.model.ContentSource
import org.skepsun.kototoro.parsers.model.SortOrder
import org.skepsun.kototoro.remotelist.ui.RemoteListFragment
import kotlin.math.absoluteValue
import com.google.android.material.R as materialR
import javax.inject.Inject

@AndroidEntryPoint
class ContentListActivity :
    BaseActivity<ActivityContentListBinding>(), View.OnClickListener,
    FilterCoordinator.Owner,
    AppBarLayout.OnOffsetChangedListener {

    private var isFoldUnfolded = false
    
    @Inject
    lateinit var mangaRepositoryFactory: org.skepsun.kototoro.core.parser.ContentRepository.Factory

	
	override val filterCoordinator: FilterCoordinator
		get() = checkNotNull(findFilterOwner()) {
			"Cannot find FilterCoordinator.Owner fragment in ${supportFragmentManager.fragments}"
		}.filterCoordinator

	private lateinit var source: ContentSource

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityContentListBinding.inflate(layoutInflater))
		viewBinding.collapsingToolbarLayout?.let { collapsingToolbarLayout ->
			FadingAppbarMediator(viewBinding.appbar, collapsingToolbarLayout).bind()
		}
		val filter = intent.getParcelableExtraCompat<ParcelableContentListFilter>(AppRouter.KEY_FILTER)?.filter
		val sortOrder = intent.getSerializableExtraCompat<SortOrder>(AppRouter.KEY_SORT_ORDER)
		source = ContentSource(intent.getStringExtra(AppRouter.KEY_SOURCE))
		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = false)
		if (viewBinding.containerFilterHeader != null) {
			viewBinding.appbar.addOnOffsetChangedListener(this)
		}
		viewBinding.buttonOrder?.setOnClickListener(this)
        // 注意：对于 JSON_* 源，这里拿到的是占位 ContentSource，需要通过 repository 解析真实名称
        title = source.getTitle(this)
        lifecycleScope.launch(Dispatchers.Default) {
            val resolvedTitle = runCatching {
                mangaRepositoryFactory.create(source).source.getTitle(this@ContentListActivity)
            }.getOrDefault(title.toString())
            withContext(Dispatchers.Main) {
                title = resolvedTitle
            }
        }
        initList(source, filter, sortOrder)

        observeFoldableState()
    }

	override fun isNsfwContent(): Flow<Boolean> = flowOf(source.isNsfw())

	override fun onOffsetChanged(appBarLayout: AppBarLayout, verticalOffset: Int) {
		val container = viewBinding.containerFilterHeader ?: return
		container.background = if (verticalOffset.absoluteValue < appBarLayout.totalScrollRange) {
			container.context.getThemeColor(materialR.attr.backgroundColor).toDrawable()
		} else {
			viewBinding.collapsingToolbarLayout?.contentScrim
		}
	}

	/**
	 * Only for landscape
	 */
	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val barsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
		viewBinding.cardSide?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
			marginEnd = barsInsets.end(v) + resources.getDimensionPixelOffset(R.dimen.side_card_offset)
			topMargin = barsInsets.top + resources.getDimensionPixelOffset(R.dimen.grid_spacing_outer_double)
			bottomMargin = barsInsets.bottom + resources.getDimensionPixelOffset(R.dimen.side_card_offset)
		}
		viewBinding.appbar.updatePaddingRelative(
			top = barsInsets.top,
			end = if (viewBinding.cardSide == null) barsInsets.end(v) else 0,
			start = barsInsets.start(v),
		)
		return insets.consumeSystemBarsInsets(v, top = true, end = true)
	}

	override fun onClick(v: View) {
		when (v.id) {
			R.id.button_order -> router.showFilterSheet()
		}
	}

	fun showPreview(manga: Content): Boolean = setSideFragment(
		PreviewFragment::class.java,
		bundleOf(AppRouter.KEY_MANGA to ParcelableContent(manga)),
	)

	fun hidePreview() = setSideFragment(FilterSheetFragment::class.java, null)

	private fun initList(source: ContentSource, filter: ContentListFilter?, sortOrder: SortOrder?) {
		val fm = supportFragmentManager
		val existingFragment = fm.findFragmentById(R.id.container)
		if (existingFragment is FilterCoordinator.Owner) {
			initFilter(existingFragment)
		} else {
			fm.commit {
				setReorderingAllowed(true)
				val fragment = if (source == LocalMangaSource) {
					LocalListFragment()
				} else {
					RemoteListFragment.newInstance(source)
				}
				replace(R.id.container, fragment)
				runOnCommit { initFilter(fragment) }
				if (filter != null || sortOrder != null) {
					runOnCommit(ApplyFilterRunnable(fragment, filter, sortOrder))
				}
			}
		}
	}

	private fun initFilter(filterOwner: FilterCoordinator.Owner) {
		if (viewBinding.containerSide != null) {
			if (supportFragmentManager.findFragmentById(R.id.container_side) == null) {
				setSideFragment(FilterSheetFragment::class.java, null)
			}
		} else if (viewBinding.containerFilterHeader != null) {
			if (supportFragmentManager.findFragmentById(R.id.container_filter_header) == null) {
				supportFragmentManager.commit {
					setReorderingAllowed(true)
					replace(R.id.container_filter_header, FilterHeaderFragment::class.java, null)
				}
			}
		}
		val filter = filterOwner.filterCoordinator
		val chipSort = viewBinding.buttonOrder
		if (chipSort != null) {
			val filterBadge = ViewBadge(chipSort, this)
			filterBadge.setMaxCharacterCount(0)
			filter.observe().observe(this) { snapshot ->
				chipSort.setTextAndVisible(snapshot.sortOrder.titleRes)
				filterBadge.counter = if (snapshot.listFilter.hasNonSearchOptions()) 1 else 0
			}
		} else {
			filter.observe().map {
				it.listFilter.getSummary()
			}.flowOn(Dispatchers.Default)
				.observe(this) {
					supportActionBar?.subtitle = it
				}
		}
	}

	private fun findFilterOwner(): FilterCoordinator.Owner? {
		return supportFragmentManager.findFragmentById(R.id.container) as? FilterCoordinator.Owner
	}

    private fun setSideFragment(cls: Class<out Fragment>, args: Bundle?) = if (viewBinding.containerSide != null) {
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.container_side, cls, args)
        }
        true
    } else {
        false
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
        // 仅在折叠屏展开且窗口满足双栏宽度时重建，避免分屏窄窗口反复重建
        if (isFoldUnfolded && viewBinding.containerSide == null && FoldableUtils.shouldUseTwoPaneLayout(this)) {
            recreate()
        }
    }

	private class ApplyFilterRunnable(
		private val filterOwner: FilterCoordinator.Owner,
		private val filter: ContentListFilter?,
		private val sortOrder: SortOrder?,
	) : Runnable {

		override fun run() {
			if (sortOrder != null) {
				filterOwner.filterCoordinator.setSortOrder(sortOrder)
			}
			if (filter != null) {
				filterOwner.filterCoordinator.setAdjusted(filter)
			}
		}
	}
}
