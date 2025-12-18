package org.skepsun.kototoro.settings.sources.jsonsource

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.db.entity.JsonSourceType
import org.skepsun.kototoro.core.js.JSSourceParser
import org.skepsun.kototoro.core.ui.AlertDialogFragment
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.databinding.DialogImportJsonBinding
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Dialog fragment for importing JSON source configurations.
 * 
 * Provides two import methods:
 * 1. File selection - User can select a JSON file from device storage
 * 2. Text paste - User can paste JSON content directly into a text field
 * 
 * Supports importing:
 * - Legado book sources
 * - TVBox site configurations (future)
 */
@AndroidEntryPoint
class ImportJsonDialogFragment : AlertDialogFragment<DialogImportJsonBinding>(), View.OnClickListener {
	
	private val viewModel: ImportJsonViewModel by viewModels()
	
	/**
	 * File picker launcher for selecting JSON files.
	 */
	private val filePickerLauncher = registerForActivityResult(
		ActivityResultContracts.StartActivityForResult()
	) { result ->
		if (result.resultCode == Activity.RESULT_OK) {
			result.data?.data?.let { uri ->
				viewModel.selectFile(uri)
				loadFileContent(uri)
			}
		}
	}
	
	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	) = DialogImportJsonBinding.inflate(inflater, container, false)
	
	override fun onViewBindingCreated(binding: DialogImportJsonBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		
		// Set up source type dropdown
		setupSourceTypeDropdown(binding)
		
		// Set up button listeners
		binding.buttonSelectFile.setOnClickListener(this)
		binding.buttonCancel.setOnClickListener(this)
		binding.buttonImport.setOnClickListener(this)
		
		// Observe UI state changes
		viewModel.uiState.observe(viewLifecycleOwner, this::onUiStateChanged)
		
		// Observe selected file URI
		viewModel.selectedFileUri.observe(viewLifecycleOwner) { uri ->
			binding.textViewSelectedFile.isVisible = uri != null
			binding.textViewSelectedFile.text = uri?.lastPathSegment ?: ""
			if (uri?.lastPathSegment?.endsWith(".js", ignoreCase = true) == true) {
				binding.autoCompleteSourceType.setText(getString(R.string.source_type_js), false)
			}
		}
		
		// Clear status when user types
		binding.editTextJson.doAfterTextChanged {
			if (viewModel.uiState.value is ImportUiState.Error || 
				viewModel.uiState.value is ImportUiState.Success) {
				viewModel.resetState()
			}
		}
	}
	
	override fun onBuildDialog(builder: MaterialAlertDialogBuilder): MaterialAlertDialogBuilder {
		return super.onBuildDialog(builder)
			.setTitle(R.string.import_json_sources)
			.setCancelable(true)
	}
	
	override fun onClick(v: View) {
		when (v.id) {
			R.id.button_select_file -> openFilePicker()
			R.id.button_cancel -> dismiss()
			R.id.button_import -> performImport()
		}
	}
	
	/**
	 * Sets up the source type dropdown with available options.
	 */
	private fun setupSourceTypeDropdown(binding: DialogImportJsonBinding) {
		val sourceTypes = arrayOf(
			getString(R.string.source_type_legado),
			getString(R.string.source_type_tvbox),
			getString(R.string.source_type_js),
		)
		
		val adapter = ArrayAdapter(
			requireContext(),
			android.R.layout.simple_dropdown_item_1line,
			sourceTypes
		)
		
		binding.autoCompleteSourceType.setAdapter(adapter)
		
		// Set default selection to Legado
		binding.autoCompleteSourceType.setText(sourceTypes[0], false)
	}
	
	/**
	 * Opens the file picker to select a JSON file.
	 */
    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            // JSON or JS for dynamic sources
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/plain", "application/javascript", "text/javascript"))
        }

        filePickerLauncher.launch(intent)
    }
	
	/**
	 * Loads content from the selected file URI.
	 */
	private fun loadFileContent(uri: Uri) {
		try {
			val contentResolver = requireContext().contentResolver
			val inputStream = contentResolver.openInputStream(uri)
			
			if (inputStream != null) {
				val reader = BufferedReader(InputStreamReader(inputStream))
				val content = reader.use { it.readText() }
				
				requireViewBinding().editTextJson.setText(content)
				
				Toast.makeText(
					requireContext(),
					R.string.file_loaded_successfully,
					Toast.LENGTH_SHORT
				).show()
			}
		} catch (e: Exception) {
			Toast.makeText(
				requireContext(),
				getString(R.string.error_loading_file, e.message),
				Toast.LENGTH_LONG
			).show()
		}
	}
	
	/**
	 * Performs the JSON import operation.
	 */
	private fun performImport() {
		val binding = requireViewBinding()
		val jsonContent = binding.editTextJson.text?.toString() ?: ""
		
		if (jsonContent.isBlank()) {
			binding.textInputLayoutJson.error = getString(R.string.json_content_required)
			return
		}
		
		binding.textInputLayoutJson.error = null
		
		// Determine source type from dropdown selection
		val sourceType = when {
			binding.autoCompleteSourceType.text.toString() == getString(R.string.source_type_tvbox) -> JsonSourceType.TVBOX
			binding.autoCompleteSourceType.text.toString() == getString(R.string.source_type_js) -> JsonSourceType.JS
			binding.textViewSelectedFile.text?.toString()?.endsWith(".js", ignoreCase = true) == true -> JsonSourceType.JS
			else -> JsonSourceType.LEGADO
		}
		
		// Apply import options
		viewModel.skipUnreachableSources = binding.checkboxSkipUnreachable.isChecked
		viewModel.skipNoExploreSources = binding.checkboxSkipNoExplore.isChecked
		
		// Trigger import
		viewModel.importJson(jsonContent, sourceType)
	}
	
	/**
	 * Handles UI state changes from the ViewModel.
	 */
	private fun onUiStateChanged(state: ImportUiState) {
		val binding = requireViewBinding()
		
		when (state) {
			is ImportUiState.Idle -> {
				binding.progressBar.isVisible = false
				binding.textViewStatus.isVisible = false
				binding.buttonImport.isEnabled = true
				binding.buttonSelectFile.isEnabled = true
				binding.editTextJson.isEnabled = true
				binding.autoCompleteSourceType.isEnabled = true
			}
			
			is ImportUiState.Loading -> {
				binding.progressBar.isVisible = true
				binding.textViewStatus.isVisible = true
				binding.textViewStatus.text = getString(R.string.importing_)
				binding.textViewStatus.setTextColor(
					requireContext().getColor(android.R.color.secondary_text_dark)
				)
				binding.buttonImport.isEnabled = false
				binding.buttonSelectFile.isEnabled = false
				binding.editTextJson.isEnabled = false
				binding.autoCompleteSourceType.isEnabled = false
			}
			
			is ImportUiState.Success -> {
				binding.progressBar.isVisible = false
				binding.textViewStatus.isVisible = true
				binding.textViewStatus.text = resources.getQuantityString(
					R.plurals.sources_imported_successfully,
					state.count,
					state.count
				)
				binding.textViewStatus.setTextColor(
					requireContext().getColor(com.google.android.material.R.color.design_default_color_primary)
				)
				binding.buttonImport.isEnabled = true
				binding.buttonSelectFile.isEnabled = true
				binding.editTextJson.isEnabled = true
				binding.autoCompleteSourceType.isEnabled = true
				
				// Show toast and dismiss after a short delay
				Toast.makeText(
					requireContext(),
					resources.getQuantityString(
						R.plurals.sources_imported_successfully,
						state.count,
						state.count
					),
					Toast.LENGTH_LONG
				).show()
				
				// Dismiss dialog after successful import
				binding.root.postDelayed({ dismiss() }, 1500)
			}
			
			is ImportUiState.Error -> {
				binding.progressBar.isVisible = false
				binding.textViewStatus.isVisible = true
				binding.textViewStatus.text = getString(R.string.import_error, state.message)
				binding.textViewStatus.setTextColor(
					requireContext().getColor(com.google.android.material.R.color.design_default_color_error)
				)
				binding.buttonImport.isEnabled = true
				binding.buttonSelectFile.isEnabled = true
				binding.editTextJson.isEnabled = true
				binding.autoCompleteSourceType.isEnabled = true
			}
		}
	}
	
	companion object {
		const val TAG = "ImportJsonDialogFragment"
		
		/**
		 * Creates a new instance of the dialog.
		 */
		fun newInstance(): ImportJsonDialogFragment {
			return ImportJsonDialogFragment()
		}
	}
}
