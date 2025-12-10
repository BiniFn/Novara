package org.skepsun.kototoro.settings.sources.jsonsource

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.skepsun.kototoro.core.db.entity.JsonSourceEntity
import org.skepsun.kototoro.core.db.entity.JsonSourceType
import org.skepsun.kototoro.core.jsonsource.JsonSourceManager

/**
 * Unit tests for JsonSourcesViewModel.
 * 
 * NOTE: These tests are currently disabled as they require Mockito which is not
 * available in the project. They can be re-enabled if Mockito is added as a dependency.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JsonSourcesViewModelTest {
	
	// Tests commented out - require Mockito
	/*
	
	private lateinit var jsonSourceManager: JsonSourceManager
	private lateinit var viewModel: JsonSourcesViewModel
	
	@Before
	fun setup() {
		jsonSourceManager = mock(JsonSourceManager::class.java)
	}
	
	@Test
	fun `toggleSource should call jsonSourceManager toggleSource`() = runTest {
		// Given
		val testSources = listOf(
			JsonSourceEntity(
				id = "JSON_LEGADO_TEST",
				name = "Test Source",
				type = JsonSourceType.LEGADO,
				config = "{}",
				enabled = true,
				createdAt = System.currentTimeMillis(),
				updatedAt = System.currentTimeMillis()
			)
		)
		`when`(jsonSourceManager.observeAllJsonSources()).thenReturn(flowOf(testSources))
		
		viewModel = JsonSourcesViewModel(jsonSourceManager)
		
		// When
		viewModel.toggleSource("JSON_LEGADO_TEST", false)
		
		// Then
		verify(jsonSourceManager).toggleSource("JSON_LEGADO_TEST", false)
	}
	
	@Test
	fun `deleteSource should call jsonSourceManager deleteSource`() = runTest {
		// Given
		val testSources = listOf<JsonSourceEntity>()
		`when`(jsonSourceManager.observeAllJsonSources()).thenReturn(flowOf(testSources))
		
		viewModel = JsonSourcesViewModel(jsonSourceManager)
		
		// When
		viewModel.deleteSource("JSON_LEGADO_TEST")
		
		// Then
		verify(jsonSourceManager).deleteSource("JSON_LEGADO_TEST")
	}
	*/
}
