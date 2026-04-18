package org.skepsun.kototoro.list.ui

import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.nav.router

import org.skepsun.kototoro.list.ui.config.ListConfigSection

class ContentListMenuProvider(
	private val fragment: Fragment,
) : MenuProvider {

	override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
		menuInflater.inflate(R.menu.opt_list, menu)
	}

	override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
		R.id.action_list_mode -> {
			val section: ListConfigSection = ListConfigSection.General
			fragment.router.showListConfigSheet(section)
			true
		}

		else -> false
	}
}
