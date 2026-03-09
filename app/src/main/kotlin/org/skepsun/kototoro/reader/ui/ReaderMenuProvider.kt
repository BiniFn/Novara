package org.skepsun.kototoro.reader.ui

import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.view.MenuProvider
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.AppSettings

class ReaderMenuProvider(
	private val viewModel: ReaderViewModel,
	private val settings: AppSettings,
) : MenuProvider {

	override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
		menuInflater.inflate(R.menu.opt_reader, menu)
	}

	override fun onPrepareMenu(menu: Menu) {
		menu.findItem(R.id.action_retranslate)?.isEnabled = settings.isReaderTranslationEnabled
	}

	override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
		return when (menuItem.itemId) {
			R.id.action_info -> {
				// TODO
				true
			}

			R.id.action_retranslate -> {
				if (settings.isReaderTranslationEnabled) {
					viewModel.retranslateCurrent()
				}
				true
			}

			else -> false
		}
	}
}
