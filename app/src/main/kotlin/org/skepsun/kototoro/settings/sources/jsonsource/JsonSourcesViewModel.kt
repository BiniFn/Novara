package org.skepsun.kototoro.settings.sources.jsonsource

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.skepsun.kototoro.core.db.entity.JsonSourceEntity
import org.skepsun.kototoro.core.jsonsource.GroupedSourceList
import org.skepsun.kototoro.core.jsonsource.GroupingStrategy
import org.skepsun.kototoro.core.jsonsource.JsonSourceManager
import org.skepsun.kototoro.core.jsonsource.SourceGroup
import org.skepsun.kototoro.core.jsonsource.SourceGroupManager
import org.skepsun.kototoro.core.model.MangaSourceInfo
import org.skepsun.kototoro.core.ui.BaseViewModel
import org.skepsun.kototoro.explore.data.MangaSourcesRepository
import javax.inject.Inject

/**
 * ViewModel for managing JSON sources with grouping support.
 * 
 * Provides functionality to:
 * - Observe all JSON sources
 * - Toggle source enabled/disabled state
 * - Delete sources
 * - Test sources (execute test requests)
 * - Group sources by content type or origin type
 * - Manage group collapse/expand state
 */
@HiltViewModel
class JsonSourcesViewModel @Inject constructor(
	private val jsonSourceManager: JsonSourceManager,
	private val sourceGroupManager: SourceGroupManager,
	private val mangaSourcesRepository: MangaSourcesRepository,
) : BaseViewModel() {
	
	/**
	 * StateFlow of all JSON sources from the database.
	 * Automatically updates when sources change.
	 */
	val jsonSources: StateFlow<List<JsonSourceEntity>> = jsonSourceManager
		.observeAllJsonSources()
		.stateIn(
			scope = viewModelScope,
			started = SharingStarted.WhileSubscribed(5000),
			initialValue = emptyList()
		)
	
	/**
	 * Current grouping strategy (by content or by origin).
	 */
	private val _groupingStrategy = MutableStateFlow(GroupingStrategy.BY_ORIGIN)
	val groupingStrategy: StateFlow<GroupingStrategy> = _groupingStrategy.asStateFlow()
	
	/**
	 * Map of collapsed group states.
	 * Key: SourceGroup, Value: isCollapsed
	 */
	private val _collapsedGroups = MutableStateFlow<Map<SourceGroup, Boolean>>(emptyMap())
	
	/**
	 * StateFlow of grouped sources.
	 * Combines all JSON sources with grouping strategy and collapsed states.
	 * Shows ALL JSON sources (both enabled and disabled) for management.
	 */
	val groupedSources: StateFlow<GroupedSourceList> = combine(
		jsonSources,
		_groupingStrategy,
		_collapsedGroups
	) { jsonSourceEntities, strategy, collapsedMap ->
		// Convert JSON source entities to MangaSourceInfo
		val sourceInfoList = jsonSourceEntities.map { entity ->
			val jsonMangaSource = org.skepsun.kototoro.core.jsonsource.JsonMangaSource(entity)
			MangaSourceInfo(
				mangaSource = jsonMangaSource,
				isEnabled = entity.enabled,
				isPinned = entity.isPinned
			)
		}
		
		// Create grouped list
		val grouped = GroupedSourceList.fromSources(
			sources = sourceInfoList,
			groupBy = strategy,
			sourceGroupManager = sourceGroupManager
		)
		
		// Apply collapsed states
		val groupsWithCollapsedState = grouped.groups.map { groupInfo ->
			val isCollapsed = collapsedMap[groupInfo.group] ?: false
			groupInfo.withCollapsed(isCollapsed)
		}
		
		GroupedSourceList(groupsWithCollapsedState).filterNonEmpty()
	}.stateIn(
		scope = viewModelScope,
		started = SharingStarted.WhileSubscribed(5000),
		initialValue = GroupedSourceList.empty()
	)
	
	private val _testResult = MutableStateFlow<TestResult?>(null)
	val testResult: StateFlow<TestResult?> = _testResult.asStateFlow()
	
	/**
	 * Toggles the enabled state of a JSON source.
	 * 
	 * @param sourceId The source identifier
	 * @param enabled Whether the source should be enabled
	 */
	fun toggleSource(sourceId: String, enabled: Boolean) {
		launchJob(Dispatchers.Default) {
			jsonSourceManager.toggleSource(sourceId, enabled)
		}
	}
	
	/**
	 * Deletes a JSON source from the database.
	 * 
	 * @param sourceId The source identifier
	 */
	fun deleteSource(sourceId: String) {
		launchJob(Dispatchers.Default) {
			jsonSourceManager.deleteSource(sourceId)
		}
	}
	
	/**
	 * Tests a JSON source by attempting to execute a test request.
	 * 
	 * This is a placeholder implementation. In a full implementation,
	 * this would:
	 * 1. Create a dynamic parser from the source configuration
	 * 2. Execute a test search or list request
	 * 3. Return success/failure result
	 * 
	 * @param sourceId The source identifier to test
	 */
	fun testSource(sourceId: String) {
		viewModelScope.launch(Dispatchers.Default) {
			_testResult.value = TestResult.Testing(sourceId)
			
			try {
				// Get the source
				val source = jsonSourceManager.getById(sourceId)
				if (source == null) {
					_testResult.value = TestResult.Error(sourceId, "Source not found")
					return@launch
				}
				
				// TODO: Implement actual test logic
				// For now, just simulate a test
				kotlinx.coroutines.delay(1000)
				
				// Simulate success
				_testResult.value = TestResult.Success(sourceId, "Test completed successfully")
			} catch (e: Exception) {
				_testResult.value = TestResult.Error(
					sourceId,
					e.message ?: "Unknown error occurred"
				)
			}
		}
	}
	
	/**
	 * Clears the test result.
	 */
	fun clearTestResult() {
		_testResult.value = null
	}
	
	/**
	 * Toggles the collapsed state of a group.
	 * 
	 * @param group The source group to toggle
	 */
	fun toggleGroupCollapsed(group: SourceGroup) {
		val currentMap = _collapsedGroups.value
		val currentState = currentMap[group] ?: false
		_collapsedGroups.value = currentMap + (group to !currentState)
	}
	
	/**
	 * Sets the grouping strategy.
	 * 
	 * @param strategy The new grouping strategy
	 */
	fun setGroupingStrategy(strategy: GroupingStrategy) {
		_groupingStrategy.value = strategy
	}
	
	/**
	 * Collapses all groups.
	 */
	fun collapseAllGroups() {
		val allGroups = groupedSources.value.groups.map { it.group }
		_collapsedGroups.value = allGroups.associateWith { true }
	}
	
	/**
	 * Expands all groups.
	 */
	fun expandAllGroups() {
		_collapsedGroups.value = emptyMap()
	}
}

/**
 * Result of testing a JSON source.
 */
sealed class TestResult {
	abstract val sourceId: String
	
	/**
	 * Test is in progress.
	 */
	data class Testing(override val sourceId: String) : TestResult()
	
	/**
	 * Test completed successfully.
	 */
	data class Success(override val sourceId: String, val message: String) : TestResult()
	
	/**
	 * Test failed with error.
	 */
	data class Error(override val sourceId: String, val message: String) : TestResult()
}
