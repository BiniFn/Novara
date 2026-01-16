package org.skepsun.kototoro.explore.ui

import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.view.MenuProvider
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.LocalMangaSource
import org.skepsun.kototoro.core.nav.AppRouter

class ExploreMenuProvider(
	private val router: AppRouter,
	private val onRandomClick: () -> Unit,
) : MenuProvider {

	private var randomMenuItem: MenuItem? = null
	private var isRandomLoading = false

	override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
		menuInflater.inflate(R.menu.opt_explore, menu)
		randomMenuItem = menu.findItem(R.id.action_random)
	}

	override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
		return when (menuItem.itemId) {
			R.id.action_local -> {
				router.openList(LocalMangaSource, null, null)
				true
			}

			R.id.action_bookmarks -> {
				router.openBookmarks()
				true
			}

			R.id.action_random -> {
				if (!isRandomLoading) {
					onRandomClick()
				}
				true
			}

			R.id.action_downloads -> {
				router.openDownloads()
				true
			}

			R.id.action_manage -> {
				router.openSourcesSettings()
				true
			}

			else -> false
		}
	}

	fun setRandomLoading(loading: Boolean) {
		isRandomLoading = loading
		randomMenuItem?.isEnabled = !loading
	}
}

