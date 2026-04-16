package org.skepsun.kototoro.local.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts

import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.LocalMangaSource
import org.skepsun.kototoro.core.nav.router

import org.skepsun.kototoro.core.ui.widgets.TipView
import org.skepsun.kototoro.core.util.ShareHelper
import org.skepsun.kototoro.core.util.ext.addSupportMenuProvider
import org.skepsun.kototoro.core.util.ext.observeEvent
import org.skepsun.kototoro.core.util.ext.tryLaunch
import org.skepsun.kototoro.databinding.FragmentContentListBinding
import org.skepsun.kototoro.filter.ui.FilterCoordinator
import org.skepsun.kototoro.list.ui.ContentListFragment
import org.skepsun.kototoro.remotelist.ui.ContentSearchMenuProvider
import org.skepsun.kototoro.remotelist.ui.RemoteListFragment
import org.skepsun.kototoro.settings.storage.RequestStorageManagerPermissionContract

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LocalListFragment : ContentListFragment(), FilterCoordinator.Owner {

	private val permissionRequestLauncher = registerForActivityResult(
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			RequestStorageManagerPermissionContract()
		} else {
			ActivityResultContracts.RequestPermission()
		},
	) {
		if (it) {
			viewModel.onRefresh()
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val args = arguments ?: Bundle(1)
		args.putString(
			RemoteListFragment.ARG_SOURCE,
			LocalMangaSource.name,
		) // required by FilterCoordinator
		arguments = args
	}

	override val viewModel by viewModels<LocalListViewModel>()

	override val filterCoordinator: FilterCoordinator
		get() = viewModel.filterCoordinator
		
	override val showSelectionRemoveOption = true

	override fun onViewBindingCreated(binding: FragmentContentListBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		addSupportMenuProvider(LocalListMenuProvider(this, this::onEmptyActionClick))
		addSupportMenuProvider(ContentSearchMenuProvider(filterCoordinator, viewModel))
		viewModel.onContentRemoved.observeEvent(viewLifecycleOwner) { onItemRemoved() }
	}

	override fun onEmptyActionClick() {
		router.showImportDialog()
	}

	override fun onFilterClick(view: View?) {
		router.showFilterSheet()
	}

	override fun onPrimaryButtonClick(tipView: TipView) {
		if (!permissionRequestLauncher.tryLaunch(Manifest.permission.READ_EXTERNAL_STORAGE)) {
			Snackbar.make(tipView, R.string.operation_not_supported, Snackbar.LENGTH_SHORT).show()
		}
	}

	override fun onSecondaryButtonClick(tipView: TipView) {
		router.openDirectoriesSettings()
	}

	override fun onScrolledToEnd() = viewModel.loadNextPage()

	override fun onSelectionAction(action: org.skepsun.kototoro.list.ui.compose.SelectionAction, ids: Set<Long>): Boolean {
		return when (action) {
			org.skepsun.kototoro.list.ui.compose.SelectionAction.REMOVE -> {
				showDeletionConfirm(ids)
				true
			}

			org.skepsun.kototoro.list.ui.compose.SelectionAction.SHARE -> {
				val files = selectedItems.map { it.url.toUri().toFile() }
				ShareHelper(requireContext()).shareCbz(files)
				true
			}

			else -> super.onSelectionAction(action, ids)
		}
	}

	private fun showDeletionConfirm(ids: Set<Long>) {
		MaterialAlertDialogBuilder(context ?: return)
			.setTitle(R.string.delete_manga)
			.setMessage(getString(R.string.text_delete_local_manga_batch))
			.setPositiveButton(R.string.delete) { _, _ ->
				viewModel.delete(ids)
			}
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	private fun onItemRemoved() {
		Snackbar.make(
			requireViewBinding().root,
			R.string.removal_completed,
			Snackbar.LENGTH_SHORT,
		).show()
	}

	override fun isContentTypeFilterVisible(): Boolean = false
	override fun isSourceTagFilterVisible(): Boolean = false
}
