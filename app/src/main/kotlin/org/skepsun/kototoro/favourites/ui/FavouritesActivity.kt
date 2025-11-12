package org.skepsun.kototoro.favourites.ui

import android.os.Bundle
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.ui.FragmentContainerActivity
import org.skepsun.kototoro.favourites.ui.list.FavouritesListFragment

class FavouritesActivity : FragmentContainerActivity(FavouritesListFragment::class.java) {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val categoryTitle = intent.getStringExtra(AppRouter.KEY_TITLE)
		if (categoryTitle != null) {
			title = categoryTitle
		}
	}
}
