package org.skepsun.kototoro.local.ui

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.os.OpenDocumentTreeHelper
import org.skepsun.kototoro.core.ui.AlertDialogFragment
import org.skepsun.kototoro.core.util.ext.tryLaunch
import org.skepsun.kototoro.databinding.DialogImportBinding
import org.skepsun.kototoro.local.data.LocalStorageManager
import org.skepsun.kototoro.local.data.importer.ImportMode
import org.skepsun.kototoro.local.data.importer.LocalImportKind
import javax.inject.Inject

@AndroidEntryPoint
class ImportDialogFragment : AlertDialogFragment<DialogImportBinding>(), View.OnClickListener {

	@Inject
	lateinit var storageManager: LocalStorageManager

	private val importFileCall = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
		startImportFiles(it)
	}
	private val importDirSingleCall = OpenDocumentTreeHelper(this) {
		startImportDirectory(it, ImportMode.SINGLE_MANGA)
	}
	private val importDirMultipleCall = OpenDocumentTreeHelper(this) {
		startImportDirectory(it, ImportMode.MULTIPLE_MANGA)
	}

	override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?): DialogImportBinding {
		return DialogImportBinding.inflate(inflater, container, false)
	}

	override fun onBuildDialog(builder: MaterialAlertDialogBuilder): MaterialAlertDialogBuilder {
		return super.onBuildDialog(builder)
			.setTitle(R.string._import)
			.setNegativeButton(android.R.string.cancel, null)
			.setCancelable(true)
	}

	override fun onViewBindingCreated(binding: DialogImportBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		binding.buttonFile.setOnClickListener(this)
		binding.buttonDirSingle.setOnClickListener(this)
		binding.buttonDirMultiple.setOnClickListener(this)

		// Link the two toggle rows for mutual exclusion
		binding.toggleContentType.addOnButtonCheckedListener { _, checkedId, isChecked ->
			if (isChecked) {
				binding.toggleContentTypeRow2.clearChecked()
				updateImportOptions(checkedId)
			}
		}
		binding.toggleContentTypeRow2.addOnButtonCheckedListener { _, checkedId, isChecked ->
			if (isChecked) {
				binding.toggleContentType.clearChecked()
				updateImportOptions(checkedId)
			}
		}

		// Initial state
		updateImportOptions(R.id.button_type_auto)
	}

	private fun getSelectedKind(): LocalImportKind? {
		val row1Checked = requireViewBinding().toggleContentType.checkedButtonId
		val row2Checked = requireViewBinding().toggleContentTypeRow2.checkedButtonId
		return when {
			row1Checked == R.id.button_type_manga -> LocalImportKind.MANGA
			row2Checked == R.id.button_type_novel -> LocalImportKind.NOVEL
			row2Checked == R.id.button_type_video -> LocalImportKind.VIDEO
			else -> null // Auto-detect
		}
	}

	private fun updateImportOptions(checkedId: Int) {
		val binding = requireViewBinding()
		when (checkedId) {
			R.id.button_type_manga -> {
				binding.buttonFile.title = getString(R.string.comics_archive)
				binding.buttonFile.subtitle = getString(R.string.import_file_description_manga)
				binding.buttonDirSingle.title = getString(R.string.folder_single_manga)
				binding.buttonDirSingle.subtitle = getString(R.string.import_folder_single_description_manga)
				binding.buttonDirMultiple.title = getString(R.string.folder_multiple_manga)
				binding.buttonDirMultiple.subtitle = getString(R.string.import_folder_multiple_description_manga)
				binding.buttonDirSingle.isVisible = true
				binding.buttonDirMultiple.isVisible = true
			}
			R.id.button_type_novel -> {
				binding.buttonFile.title = getString(R.string.import_file)
				binding.buttonFile.subtitle = getString(R.string.import_file_description_novel)
				binding.buttonDirSingle.title = getString(R.string.folder_single_novel)
				binding.buttonDirSingle.subtitle = getString(R.string.import_folder_single_description_novel)
				binding.buttonDirMultiple.title = getString(R.string.folder_multiple_novel)
				binding.buttonDirMultiple.subtitle = getString(R.string.import_folder_multiple_description_novel)
				binding.buttonDirSingle.isVisible = true
				binding.buttonDirMultiple.isVisible = true
			}
			R.id.button_type_video -> {
				binding.buttonFile.title = getString(R.string.import_file)
				binding.buttonFile.subtitle = getString(R.string.import_file_description_video)
				binding.buttonDirSingle.title = getString(R.string.folder_single_video)
				binding.buttonDirSingle.subtitle = getString(R.string.import_folder_single_description_video)
				binding.buttonDirMultiple.title = getString(R.string.folder_multiple_video)
				binding.buttonDirMultiple.subtitle = getString(R.string.import_folder_multiple_description_video)
				binding.buttonDirSingle.isVisible = true
				binding.buttonDirMultiple.isVisible = true
			}
			else -> { // Auto-detect
				binding.buttonFile.title = getString(R.string.import_file)
				binding.buttonFile.subtitle = getString(R.string.import_file_description)
				binding.buttonDirSingle.title = getString(R.string.import_folder_single)
				binding.buttonDirSingle.subtitle = getString(R.string.import_folder_single_description)
				binding.buttonDirMultiple.title = getString(R.string.import_folder_multiple)
				binding.buttonDirMultiple.subtitle = getString(R.string.import_folder_multiple_description)
				binding.buttonDirSingle.isVisible = true
				binding.buttonDirMultiple.isVisible = true
			}
		}
	}

	override fun onClick(v: View) {
		val res = when (v.id) {
			R.id.button_file -> importFileCall.tryLaunch(arrayOf("*/*"))
			R.id.button_dir_single -> importDirSingleCall.tryLaunch(null)
			R.id.button_dir_multiple -> importDirMultipleCall.tryLaunch(null)
			else -> true
		}
		if (!res) {
			Toast.makeText(v.context, R.string.operation_not_supported, Toast.LENGTH_SHORT).show()
		}
	}

	private fun startImportFiles(uris: Collection<Uri>) {
		val selectedKind = getSelectedKind()
		if (uris.isEmpty()) {
			return
		}
		uris.forEach {
			storageManager.takePermissions(it)
		}
		val ctx = requireContext()
		val msg = if (ImportService.start(ctx, uris, selectedKind)) {
			R.string.import_will_start_soon
		} else {
			R.string.error_occurred
		}
		Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
		dismiss()
	}

	private fun startImportDirectory(uri: Uri?, mode: ImportMode) {
		val selectedKind = getSelectedKind()
		if (uri == null) {
			return
		}
		storageManager.takePermissions(uri)
		val ctx = requireContext()
		val msg = if (ImportService.start(ctx, uri, mode, selectedKind)) {
			R.string.import_will_start_soon
		} else {
			R.string.error_occurred
		}
		Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
		dismiss()
	}
}
