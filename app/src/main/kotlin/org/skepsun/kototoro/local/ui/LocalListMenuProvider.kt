package org.skepsun.kototoro.local.ui

import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.nav.router

class LocalListMenuProvider(
	private val appRouter: org.skepsun.kototoro.core.nav.AppRouter,
	private val onImportClick: Function0<Unit>,
) : MenuProvider {

	override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
		menuInflater.inflate(R.menu.opt_local, menu)
		menuInflater.inflate(R.menu.opt_list, menu)
	}

	override fun onPrepareMenu(menu: Menu) {
		super.onPrepareMenu(menu)
		menu.findItem(R.id.action_filter)?.isVisible = appRouter.isFilterSupported()
	}

	override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
		return when (menuItem.itemId) {
			R.id.action_import -> {
				onImportClick()
				true
			}

			R.id.action_directories -> {
				appRouter.openDirectoriesSettings()
				true
			}

			R.id.action_filter -> {
				appRouter.showFilterSheet()
				true
			}

			R.id.action_list_mode -> {
				appRouter.showListConfigSheet(org.skepsun.kototoro.list.ui.config.ListConfigSection.General)
				true
			}

			else -> false
		}
	}
}
