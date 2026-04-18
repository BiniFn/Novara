package org.skepsun.kototoro.search.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.core.model.parcelable.ParcelableContentListFilter
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import org.skepsun.kototoro.filter.ui.FilterCoordinator
import org.skepsun.kototoro.parsers.model.SortOrder
import org.skepsun.kototoro.remotelist.ui.RemoteListViewModel
import org.skepsun.kototoro.search.ui.compose.AppSearchContentListRoute
import org.skepsun.kototoro.core.util.ext.getParcelableExtraCompat
import org.skepsun.kototoro.core.util.ext.getSerializableExtraCompat
import androidx.core.view.WindowCompat

@AndroidEntryPoint
class ContentListActivity : FragmentActivity(), FilterCoordinator.Owner {

    private val viewModel: RemoteListViewModel by viewModels()

    override val filterCoordinator: FilterCoordinator
        get() = viewModel.filterCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val filter = intent.getParcelableExtraCompat<ParcelableContentListFilter>(AppRouter.KEY_FILTER)?.filter
        val sortOrder = intent.getSerializableExtraCompat<SortOrder>(AppRouter.KEY_SORT_ORDER)

        if (filter != null) filterCoordinator.setAdjusted(filter)
        if (sortOrder != null) filterCoordinator.setSortOrder(sortOrder)

        setContent {
            KototoroTheme {
                AppSearchContentListRoute(
                    appRouter = AppRouter(this)
                )
            }
        }
    }
}
