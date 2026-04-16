package org.skepsun.kototoro.suggestions.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.view.MenuProvider
import androidx.fragment.app.viewModels
import com.google.android.material.snackbar.Snackbar
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.util.ext.addSupportMenuProvider
import org.skepsun.kototoro.databinding.FragmentContentListBinding
import org.skepsun.kototoro.explore.ui.model.BrowseGroupTab
import org.skepsun.kototoro.list.ui.ContentListFragment

@dagger.hilt.android.AndroidEntryPoint
class SuggestionsFragment : ContentListFragment() {

	override val viewModel by viewModels<SuggestionsViewModel>()
	override val isSwipeRefreshEnabled = false

	override fun onViewBindingCreated(binding: FragmentContentListBinding, savedInstanceState: Bundle?) {
		arguments?.getString(AppRouter.KEY_GROUP_TAB)
			?.let(BrowseGroupTab::fromId)
			?.let(viewModel::setSelectedGroupTab)
		super.onViewBindingCreated(binding, savedInstanceState)
		addSupportMenuProvider(SuggestionMenuProvider())
	}

	override fun onScrolledToEnd() = Unit



	private inner class SuggestionMenuProvider : MenuProvider {

		override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
			menuInflater.inflate(R.menu.opt_suggestions, menu)
		}

		override fun onPrepareMenu(menu: Menu) {
			super.onPrepareMenu(menu)
			menu.findItem(R.id.action_settings_suggestions)?.isVisible =
				menu.findItem(R.id.action_settings) == null
		}

		override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
			R.id.action_update -> {
				viewModel.updateSuggestions()
				Snackbar.make(
					requireViewBinding().root,
					R.string.suggestions_updating,
					Snackbar.LENGTH_LONG,
				).show()
				true
			}

			R.id.action_settings_suggestions -> {
				router.openSuggestionsSettings()
				true
			}

			else -> false
		}
	}

	companion object {

		@Deprecated(
			"",
			ReplaceWith(
				"SuggestionsFragment()",
				"org.skepsun.kototoro.suggestions.ui.SuggestionsFragment",
			),
		)
		fun newInstance() = SuggestionsFragment()
	}
}
