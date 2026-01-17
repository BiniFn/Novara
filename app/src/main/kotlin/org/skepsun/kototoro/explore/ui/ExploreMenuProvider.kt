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

	private var btnRandom: android.view.View? = null
	private var isRandomLoading = false

	override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
		menuInflater.inflate(R.menu.opt_explore, menu)
		
		val gridItem = menu.findItem(R.id.action_quick_grid)
		val actionView = gridItem.actionView
		if (actionView != null) {
			actionView.findViewById<android.view.View>(R.id.btn_action_local).setOnClickListener {
				router.openList(LocalMangaSource, null, null)
			}
			actionView.findViewById<android.view.View>(R.id.btn_action_bookmarks).setOnClickListener {
				router.openBookmarks()
			}
			val randomBtn = actionView.findViewById<android.view.View>(R.id.btn_action_random)
			randomBtn.setOnClickListener {
				if (!isRandomLoading) {
					onRandomClick()
				}
			}
			btnRandom = randomBtn
			actionView.findViewById<android.view.View>(R.id.btn_action_downloads).setOnClickListener {
				router.openDownloads()
			}
			updateRandomButtonState()
		}
	}

	override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
		return when (menuItem.itemId) {
			R.id.action_manage -> {
				router.openSourcesSettings()
				true
			}
			else -> false
		}
	}

	fun setRandomLoading(loading: Boolean) {
		isRandomLoading = loading
		updateRandomButtonState()
	}

	private fun updateRandomButtonState() {
		btnRandom?.let { btn ->
			btn.isEnabled = !isRandomLoading
			btn.alpha = if (isRandomLoading) 0.5f else 1.0f
		}
	}
}

