package org.skepsun.kototoro.settings.sources.jsonsource

import android.app.Activity
import android.content.Intent
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
import org.skepsun.kototoro.core.ui.AlertDialogFragment
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.databinding.DialogImportJsonBinding

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
	private lateinit var sourceTypeOptions: List<SourceTypeEntry>
	private var selectedSourceType: JsonSourceType = JsonSourceType.LEGADO
	
	/**
	 * File picker launcher for selecting JSON files.
	 */
	private val filePickerLauncher = registerForActivityResult(
		ActivityResultContracts.StartActivityForResult()
	) { result ->
		if (result.resultCode == Activity.RESULT_OK) {
			result.data?.data?.let { uri ->
				viewModel.selectFile(uri)
			}
		}
	}
	
	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	) = DialogImportJsonBinding.inflate(inflater, container, false)
	
	override fun onViewBindingCreated(binding: DialogImportJsonBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		setupSourceTypeDropdown(binding)

		// Set up button listeners
		binding.buttonSelectFile.setOnClickListener(this)
		binding.buttonImportUrl.setOnClickListener(this)
		binding.buttonCancel.setOnClickListener(this)
		binding.buttonImport.setOnClickListener(this)
		
		// Observe fetched content from URL
		viewModel.fetchedContent.observe(viewLifecycleOwner) { content ->
			if (content != null) {
				viewModel.importJson(
					jsonContent = content,
					sourceType = determineSourceType(),
					sourceLocator = viewModel.lastFetchedUrl.value,
				)
				viewModel.clearFetchedContent()
			}
		}
		
		// Observe UI state changes
		viewModel.uiState.observe(viewLifecycleOwner, this::onUiStateChanged)
		
		// Observe selected file URI
		// Observe selected file URI
		viewModel.selectedFileUri.observe(viewLifecycleOwner) { uri ->
			binding.textViewSelectedFile.isVisible = uri != null
			binding.textViewSelectedFile.text = uri?.lastPathSegment ?: ""
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
			R.id.button_import_url -> showUrlInputDialog()
			R.id.button_cancel -> dismiss()
			R.id.button_import -> performImport()
		}
	}

	/**
	 * Sets up the source type dropdown with available options.
	 */
	private fun setupSourceTypeDropdown(binding: DialogImportJsonBinding) {
		sourceTypeOptions = listOf(
			SourceTypeEntry(JsonSourceType.LEGADO, getString(R.string.source_type_legado)),
			SourceTypeEntry(JsonSourceType.TVBOX, getString(R.string.source_type_tvbox)),
		)
		val adapter = ArrayAdapter(
			requireContext(),
			android.R.layout.simple_list_item_1,
			sourceTypeOptions.map { it.label },
		)
		binding.autoCompleteSourceType.setAdapter(adapter)
		binding.autoCompleteSourceType.setText(sourceTypeOptions.first().label, false)
		selectedSourceType = sourceTypeOptions.first().type
		updateUiForSourceType(binding, selectedSourceType)

		binding.autoCompleteSourceType.setOnItemClickListener { _, _, position, _ ->
			selectedSourceType = sourceTypeOptions[position].type
			updateUiForSourceType(binding, selectedSourceType)
		}
	}

	private fun updateUiForSourceType(
		binding: DialogImportJsonBinding,
		sourceType: JsonSourceType,
	) {
		val isLegado = sourceType == JsonSourceType.LEGADO
		binding.checkboxSkipUnreachable.isVisible = isLegado
		binding.checkboxSkipNoExplore.isVisible = isLegado
		binding.textInputLayoutJson.hint = when (sourceType) {
			JsonSourceType.LEGADO -> getString(R.string.paste_legado_content)
			JsonSourceType.TVBOX -> getString(R.string.paste_tvbox_content)
			JsonSourceType.JS -> getString(R.string.paste_js_content)
			JsonSourceType.LNREADER -> getString(R.string.paste_js_content)
		}
		binding.textInputLayoutSourceType.helperText = when (sourceType) {
			JsonSourceType.TVBOX -> getString(R.string.tvbox_source_zh_warning)
			else -> null
		}
	}
	
	/**
	 * Shows a dialog to input a URL for fetching JSON content.
	 */
	private fun showUrlInputDialog() {
		val input = com.google.android.material.textfield.TextInputEditText(requireContext()).apply {
			hint = "https://example.com/source.json"
			inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
		}
		
		val container = android.widget.FrameLayout(requireContext()).apply {
			val params = android.widget.FrameLayout.LayoutParams(
				android.view.ViewGroup.LayoutParams.MATCH_PARENT,
				android.view.ViewGroup.LayoutParams.WRAP_CONTENT
			)
			val margin = resources.getDimensionPixelSize(R.dimen.margin_normal)
			params.setMargins(margin, margin / 2, margin, 0)
			input.layoutParams = params
			addView(input)
		}
		
		MaterialAlertDialogBuilder(requireContext())
			.setTitle(R.string.import_from_url)
			.setMessage(R.string.enter_source_url)
			.setView(container)
			.setPositiveButton(R.string._import) { _, _ ->
				val url = input.text?.toString() ?: ""
				if (url.isNotBlank()) {
					viewModel.fetchFromUrl(url)
				}
			}
			.setNegativeButton(android.R.string.cancel, null)
			.show()
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
	 * Performs the JSON import operation.
	 */
	private fun performImport() {
		val binding = requireViewBinding()
		val uri = viewModel.selectedFileUri.value
		val jsonContent = binding.editTextJson.text?.toString() ?: ""
		
		val sourceType = determineSourceType()
		
		// Apply import options
		viewModel.skipUnreachableSources = binding.checkboxSkipUnreachable.isChecked
		viewModel.skipNoExploreSources = binding.checkboxSkipNoExplore.isChecked
		
		if (uri != null) {
			// Prefer file import
			viewModel.importJsonFromFile(uri, sourceType, requireContext().contentResolver)
		} else if (jsonContent.isNotBlank()) {
			viewModel.importJson(
				jsonContent = jsonContent,
				sourceType = sourceType,
				sourceLocator = null,
			)
		} else {
			Toast.makeText(requireContext(), R.string.select_file_or_enter_url, Toast.LENGTH_SHORT).show()
		}
	}

	private fun determineSourceType(): JsonSourceType {
		return selectedSourceType
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

	private data class SourceTypeEntry(
		val type: JsonSourceType,
		val label: String,
	)
}
