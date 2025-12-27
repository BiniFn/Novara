package org.skepsun.kototoro.favourites.ui.container

import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.view.MenuProvider
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.nav.AppRouter

class FavouritesContainerMenuProvider(
	private val router: AppRouter,
	private val onImportRequested: () -> Unit,
	private val onSyncRequested: () -> Unit,
) : MenuProvider {

	override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
		menuInflater.inflate(R.menu.opt_favourites_container, menu)
	}

	override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
		when (menuItem.itemId) {
			R.id.action_manage -> {
				router.openFavoriteCategories()
			}
			R.id.action_import_favourites -> onImportRequested()
			R.id.action_sync_favourites -> onSyncRequested()

			else -> return false
		}
		return true
	}
}
