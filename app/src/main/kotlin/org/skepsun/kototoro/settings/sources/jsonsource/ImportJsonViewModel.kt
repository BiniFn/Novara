package org.skepsun.kototoro.settings.sources.jsonsource

import android.net.Uri
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.skepsun.kototoro.core.db.entity.JsonSourceType
import org.skepsun.kototoro.core.jsonsource.JsonSourceManager
import org.skepsun.kototoro.core.jsonsource.SecurityValidator
import org.skepsun.kototoro.core.network.jsonsource.LegadoHttpClient
import org.skepsun.kototoro.core.ui.BaseViewModel
import javax.inject.Inject

/**
 * ViewModel for importing JSON source configurations.
 * 
 * Handles:
 * - File selection
 * - JSON content validation
 * - Import operations
 * - UI state management
 */
@HiltViewModel
class ImportJsonViewModel @Inject constructor(
	private val jsonSourceManager: JsonSourceManager,
	private val legadoHttpClient: LegadoHttpClient,
) : BaseViewModel() {
	
	// Import options (can be toggled from UI if needed)
	var skipUnreachableSources: Boolean = false
	var skipNoExploreSources: Boolean = false
	
	private val _uiState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
	val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()
	
	private val _selectedFileUri = MutableStateFlow<Uri?>(null)
	val selectedFileUri: StateFlow<Uri?> = _selectedFileUri.asStateFlow()

	private val _fetchedContent = MutableStateFlow<String?>(null)
	val fetchedContent = _fetchedContent.asStateFlow()
	
	/**
	 * Fetches JSON content from a URL.
	 * 
	 * @param url The URL to fetch from
	 */
	fun fetchFromUrl(url: String) {
		viewModelScope.launch {
			_uiState.value = ImportUiState.Loading
			try {
				val response = legadoHttpClient.get(url)
				if (response.isSuccessful) {
					val content = response.body?.string()
					if (content != null) {
						_fetchedContent.value = content
						_uiState.value = ImportUiState.Idle
					} else {
						_uiState.value = ImportUiState.Error("Empty response body")
					}
				} else {
					_uiState.value = ImportUiState.Error("HTTP Error ${response.code}")
				}
			} catch (e: Exception) {
				_uiState.value = ImportUiState.Error(e.message ?: "Unknown error occurred")
			}
		}
	}

	fun clearFetchedContent() {
		_fetchedContent.value = null
	}
	
	/**
	 * Selects a file for import.
	 * 
	 * @param uri The URI of the selected file
	 */
	fun selectFile(uri: Uri) {
		_selectedFileUri.value = uri
	}
	
	/**
	 * Imports JSON content.
	 * 
	 * Validates:
	 * - JSON file size (max 5MB)
	 * - JSON format
	 * - Required fields
	 * 
	 * @param jsonContent The JSON content to import
	 * @param sourceType The type of source (LEGADO or TVBOX)
	 */
	fun importJson(jsonContent: String, sourceType: JsonSourceType) {
		viewModelScope.launch(Dispatchers.Default) {
			_uiState.value = ImportUiState.Loading
			
			try {
				// Validate JSON file size
				val sizeInBytes = jsonContent.toByteArray().size.toLong()
				val sizeValidation = SecurityValidator.validateJsonFileSize(sizeInBytes)
				if (!sizeValidation.isValid) {
					_uiState.value = ImportUiState.Error(
						sizeValidation.errors.joinToString(", ")
					)
					return@launch
				}
				
				// Import based on source type
				val result = when (sourceType) {
					JsonSourceType.LEGADO -> jsonSourceManager.importLegadoJson(
						jsonContent,
						skipUnreachable = skipUnreachableSources,
						skipNoExplore = skipNoExploreSources
					)
					JsonSourceType.TVBOX -> {
						Result.failure(UnsupportedOperationException("TVBox import not yet implemented"))
					}
					JsonSourceType.JS -> jsonSourceManager.importJsSource(jsonContent)
				}
				
				handleImportResult(result)
			} catch (e: Exception) {
				_uiState.value = ImportUiState.Error(
					e.message ?: "Unknown error occurred"
				)
			}
		}
	}

	/**
	 * Imports JSON content from a file URI.
	 */
	fun importJsonFromFile(uri: Uri, sourceType: JsonSourceType, contentResolver: android.content.ContentResolver) {
		viewModelScope.launch(Dispatchers.IO) {
			_uiState.value = ImportUiState.Loading
			try {
				val inputStream = contentResolver.openInputStream(uri) ?: throw Exception("Failed to open file")
				val jsonContent = inputStream.bufferedReader().use { it.readText() }
				
				// Validate size (now uses higher limit)
				val sizeInBytes = jsonContent.toByteArray().size.toLong()
				val sizeValidation = SecurityValidator.validateJsonFileSize(sizeInBytes)
				if (!sizeValidation.isValid) {
					_uiState.value = ImportUiState.Error(sizeValidation.errors.joinToString(", "))
					return@launch
				}

				val result = when (sourceType) {
					JsonSourceType.LEGADO -> jsonSourceManager.importLegadoJson(
						jsonContent,
						skipUnreachable = skipUnreachableSources,
						skipNoExplore = skipNoExploreSources
					)
					JsonSourceType.JS -> jsonSourceManager.importJsSource(jsonContent)
					else -> Result.failure(UnsupportedOperationException("Unsupported type"))
				}
				
				handleImportResult(result)
			} catch (e: Exception) {
				_uiState.value = ImportUiState.Error(e.message ?: "Unknown error occurred")
			}
		}
	}

	private fun handleImportResult(result: Result<Int>) {
		result.fold(
			onSuccess = { count ->
				_uiState.value = ImportUiState.Success(count)
			},
			onFailure = { error ->
				_uiState.value = ImportUiState.Error(
					error.message ?: "Unknown error occurred"
				)
			}
		)
	}
	
	/**
	 * Resets the UI state to idle.
	 */
	fun resetState() {
		_uiState.value = ImportUiState.Idle
	}
}

/**
 * UI state for the import dialog.
 */
sealed class ImportUiState {
	/**
	 * Idle state - no operation in progress.
	 */
	object Idle : ImportUiState()
	
	/**
	 * Loading state - import in progress.
	 */
	object Loading : ImportUiState()
	
	/**
	 * Success state - import completed successfully.
	 * 
	 * @param count Number of sources imported
	 */
	data class Success(val count: Int) : ImportUiState()
	
	/**
	 * Error state - import failed.
	 * 
	 * @param message Error message
	 */
	data class Error(val message: String) : ImportUiState()
}
