package org.skepsun.kototoro.reader.ui

import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.view.MenuProvider
import org.skepsun.kototoro.R

class ReaderMenuProvider(
	private val viewModel: ReaderViewModel,
	private val isTranslationAvailable: () -> Boolean,
	private val isTranslationSessionEnabled: () -> Boolean,
	private val onOpenTranslationLog: () -> Unit,
) : MenuProvider {

	override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
		menuInflater.inflate(R.menu.opt_reader, menu)
	}

	override fun onPrepareMenu(menu: Menu) {
		val isTranslationVisible = isTranslationAvailable()
		val isTranslationEnabled = isTranslationSessionEnabled()
		menu.findItem(R.id.action_retranslate)?.apply {
			isVisible = isTranslationVisible
			isEnabled = isTranslationEnabled
		}
		menu.findItem(R.id.action_translation_log)?.apply {
			isVisible = isTranslationVisible
			isEnabled = isTranslationEnabled
		}
	}

	override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
		return when (menuItem.itemId) {
			R.id.action_info -> {
				// TODO
				true
			}

			R.id.action_retranslate -> {
				if (isTranslationSessionEnabled()) {
					viewModel.retranslateCurrent()
				}
				true
			}

			R.id.action_translation_log -> {
				if (isTranslationSessionEnabled()) {
					onOpenTranslationLog()
				}
				true
			}

			else -> false
		}
	}
}
