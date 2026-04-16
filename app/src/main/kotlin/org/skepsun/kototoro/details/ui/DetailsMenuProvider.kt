package org.skepsun.kototoro.details.ui

import android.app.Activity
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.LocalMangaSource
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.os.AppShortcutManager
import org.skepsun.kototoro.core.ui.dialog.buildAlertDialog
import org.skepsun.kototoro.core.util.ext.isHttpUrl
import org.skepsun.kototoro.core.model.isNsfw

class DetailsMenuProvider(
	private val activity: FragmentActivity,
	private val viewModel: DetailsViewModel,
	private val snackbarHost: View,
	private val appShortcutManager: AppShortcutManager,
) : MenuProvider, ActivityResultCallback<ActivityResult> {

	private val activityForResultLauncher = activity.registerForActivityResult(
		ActivityResultContracts.StartActivityForResult(),
		this,
	)

	private val router: AppRouter
		get() = activity.router

	override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
		menuInflater.inflate(R.menu.opt_details, menu)
	}

	override fun onPrepareMenu(menu: Menu) {
		val manga = viewModel.manga.value
		menu.findItem(R.id.action_share).isVisible = manga != null && AppRouter.isShareSupported(manga)
		menu.findItem(R.id.action_save).isVisible = manga?.source != null && manga.source != LocalMangaSource
		menu.findItem(R.id.action_delete).isVisible = manga?.source == LocalMangaSource
		menu.findItem(R.id.action_browser).isVisible = manga?.publicUrl?.isHttpUrl() == true
		menu.findItem(R.id.action_alternatives).isVisible = manga?.source != LocalMangaSource
		menu.findItem(R.id.action_shortcut).isVisible = ShortcutManagerCompat.isRequestPinShortcutSupported(activity)
		menu.findItem(R.id.action_scrobbling).isVisible = viewModel.isScrobblingAvailable
		menu.findItem(R.id.action_online).isVisible = viewModel.remoteContent.value != null
		menu.findItem(R.id.action_stats).isVisible = viewModel.isStatsAvailable.value

		val actionMarkSafe = menu.findItem(R.id.action_mark_safe)
		val isNsfw = manga?.isNsfw() == true
		if (isNsfw) {
			actionMarkSafe.setTitle(R.string.mark_as_safe)
		} else {
			actionMarkSafe.setTitle(R.string.mark_as_nsfw)
		}

		val translateItem = menu.findItem(R.id.action_translate_title)
		if (viewModel.hasTranslationCache.value) {
			translateItem.setTitle(R.string.reader_translation_retranslate)
		} else {
			translateItem.setTitle(R.string.translate_title)
		}
		translateItem.isEnabled = !viewModel.isTranslating.value

		val toggleTranslationItem = menu.findItem(R.id.action_toggle_translation)
		toggleTranslationItem.isVisible = viewModel.hasTranslationCache.value
		toggleTranslationItem.isEnabled = viewModel.hasTranslationCache.value && !viewModel.isTranslating.value
		toggleTranslationItem.setTitle(
			if (viewModel.isShowingTranslation.value) {
				R.string.details_show_original
			} else {
				R.string.details_show_translation
			},
		)
	}

	override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
		val manga = viewModel.getContentOrNull() ?: return false
		when (menuItem.itemId) {
			R.id.action_share -> {
				router.showShareDialog(manga)
			}

			R.id.action_delete -> {
				buildAlertDialog(activity) {
					setTitle(R.string.delete_manga)
					setMessage(activity.getString(R.string.text_delete_local_manga, manga.title))
					setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteLocal() }
					setNegativeButton(android.R.string.cancel, null)
				}.show()
			}

			R.id.action_save -> {
				router.showDownloadDialog(manga, snackbarHost)
			}

			R.id.action_browser -> {
				router.openBrowser(url = manga.publicUrl, source = manga.source, title = manga.title)
			}

			R.id.action_online -> {
				router.openDetails(viewModel.remoteContent.value ?: return false)
			}

			R.id.action_related -> {
				router.openSearch(manga.title)
			}

			R.id.action_alternatives -> {
				router.openAlternatives(manga)
			}

			R.id.action_stats -> {
				router.showStatisticSheet(manga)
			}

			R.id.action_scrobbling -> {
				router.showScrobblingSelectorSheet(manga, null)
			}

			R.id.action_shortcut -> {
				activity.lifecycleScope.launch {
					if (!appShortcutManager.requestPinShortcut(manga)) {
						Snackbar.make(snackbarHost, R.string.operation_not_supported, Snackbar.LENGTH_SHORT)
							.show()
					}
				}
			}

			R.id.action_edit_override -> {
				val intent = AppRouter.overrideEditIntent(activity, manga)
				activityForResultLauncher.launch(intent)
			}

			R.id.action_mark_safe -> {
				viewModel.toggleMarkSafe()
			}

			R.id.action_translate_title -> {
				val hasCache = viewModel.hasTranslationCache.value
				viewModel.translateTitleAndDescription(forceRefresh = hasCache)
				Snackbar.make(
					snackbarHost,
					if (hasCache) R.string.reader_translation_retranslate_started else R.string.translating,
					Snackbar.LENGTH_SHORT,
				).show()
			}

			R.id.action_toggle_translation -> {
				viewModel.toggleTranslationDisplay()
			}

			else -> return false
		}
		return true
	}

	override fun onActivityResult(result: ActivityResult) {
		if (result.resultCode == Activity.RESULT_OK) {
			viewModel.reload()
		}
	}
}
