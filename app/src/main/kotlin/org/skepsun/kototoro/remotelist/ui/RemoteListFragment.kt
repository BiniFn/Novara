package org.skepsun.kototoro.remotelist.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.view.MenuProvider
import androidx.fragment.app.viewModels
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.getTitle
import org.skepsun.kototoro.core.nav.router

import org.skepsun.kototoro.core.ui.util.MenuInvalidator
import org.skepsun.kototoro.core.util.ext.addMenuProvider
import org.skepsun.kototoro.core.util.ext.getCauseUrl
import org.skepsun.kototoro.core.util.ext.isHttpUrl
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.core.util.ext.observeEvent
import org.skepsun.kototoro.core.util.ext.withArgs
import org.skepsun.kototoro.databinding.FragmentContentListBinding
import org.skepsun.kototoro.filter.ui.FilterCoordinator
import org.skepsun.kototoro.list.ui.ContentListFragment
import org.skepsun.kototoro.parsers.model.ContentSource
import org.skepsun.kototoro.search.domain.SearchKind

@AndroidEntryPoint
class RemoteListFragment : ContentListFragment(), FilterCoordinator.Owner, View.OnClickListener {

    override val viewModel by viewModels<RemoteListViewModel>()

    override val filterCoordinator: FilterCoordinator
        get() = viewModel.filterCoordinator

    override fun onViewBindingCreated(binding: FragmentContentListBinding, savedInstanceState: Bundle?) {
        super.onViewBindingCreated(binding, savedInstanceState)
        addMenuProvider(RemoteListMenuProvider())
        addMenuProvider(ContentSearchMenuProvider(filterCoordinator, viewModel))
        viewModel.isRandomLoading.observe(viewLifecycleOwner, MenuInvalidator(requireActivity()))
        viewModel.onOpenContent.observeEvent(viewLifecycleOwner) { router.openDetails(it) }
        viewModel.onSourceBroken.observeEvent(viewLifecycleOwner) { showSourceBrokenWarning() }
        // 确保进入 JS 源时标题使用解析后的 displayName，而不�?JSON_JS_* 占位
        activity?.title = viewModel.source.getTitle(requireContext())
        filterCoordinator.observe().distinctUntilChangedBy { it.listFilter.isEmpty() }
            .drop(1)
            .observe(viewLifecycleOwner) {
                activity?.invalidateMenu()
            }
    }

    override fun onScrolledToEnd() {
        viewModel.loadNextPage()
    }

    override fun isContentTypeFilterVisible(): Boolean = false
    override fun isSourceTagFilterVisible(): Boolean = false



    override fun onFilterClick(view: View?) {
        router.showFilterSheet()
    }

    override fun onEmptyActionClick() {
        if (filterCoordinator.isFilterApplied) {
            filterCoordinator.reset()
        } else {
            openInBrowser(null) // should never be called
        }
    }

    override fun onFooterButtonClick() {
        val filter = filterCoordinator.snapshot().listFilter
        when {
            !filter.query.isNullOrEmpty() -> router.openSearch(filter.query.orEmpty(), SearchKind.SIMPLE)
            !filter.author.isNullOrEmpty() -> router.openSearch(filter.author.orEmpty(), SearchKind.AUTHOR)
            filter.tags.size == 1 -> router.openSearch(filter.tags.singleOrNull()?.title.orEmpty(), SearchKind.TAG)
        }
    }

    override fun onSecondaryErrorActionClick(error: Throwable) {
        openInBrowser(error.getCauseUrl())
    }

    override fun onClick(v: View?) = Unit // from Snackbar, do nothing

    private fun openInBrowser(url: String?) {
        if (url?.isHttpUrl() == true) {
            router.openBrowser(
                url = url,
                source = viewModel.source,
                title = viewModel.source.getTitle(requireContext()),
            )
        } else {
            Snackbar.make(requireViewBinding().root, R.string.operation_not_supported, Snackbar.LENGTH_SHORT)
                .show()
        }
    }

    private fun showSourceBrokenWarning() {
        val snackbar = Snackbar.make(
            viewBinding?.root ?: return,
            R.string.source_broken_warning,
            Snackbar.LENGTH_INDEFINITE,
        )
        snackbar.setAction(R.string.got_it, this)
        snackbar.show()
    }

    private inner class RemoteListMenuProvider : MenuProvider {

        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.opt_list_remote, menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
            R.id.action_source_settings -> {
                router.openSourceSettings(viewModel.source)
                true
            }

            R.id.action_random -> {
                viewModel.openRandom()
                true
            }

            R.id.action_filter -> {
                onFilterClick(null as View?)
                true
            }

            R.id.action_filter_reset -> {
                filterCoordinator.reset()
                true
            }

            else -> false
        }

        override fun onPrepareMenu(menu: Menu) {
            super.onPrepareMenu(menu)
            menu.findItem(R.id.action_random)?.isEnabled = !viewModel.isRandomLoading.value
            menu.findItem(R.id.action_filter_reset)?.isVisible = filterCoordinator.isFilterApplied
        }
    }

    companion object {

        const val ARG_SOURCE = "provider"

        fun newInstance(source: ContentSource) = RemoteListFragment().withArgs(1) {
            putString(ARG_SOURCE, source.name)
        }
    }
}
