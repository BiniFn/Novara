package org.skepsun.kototoro.settings.sources.extensions

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExternalInstalledExtensionsScreenRuntimeTest {

	@Test
	fun `initialize triggers refresh when installed list is empty`() = runTest {
		val runtime = createRuntime(
			scope = this,
			hasExtensions = { false },
		)
		try {
			runtime.initialize()
			advanceUntilIdle()

			assertEquals(1, reloadCalls)
			assertFalse(runtime.isLoading.value)
		} finally {
			runtime.scope.cancel()
		}
	}

	@Test
	fun `initialize skips refresh when extensions already exist`() = runTest {
		val runtime = createRuntime(
			scope = this,
			hasExtensions = { true },
		)
		try {
			runtime.initialize()
			advanceUntilIdle()

			assertEquals(0, reloadCalls)
		} finally {
			runtime.scope.cancel()
		}
	}

	@Test
	fun `extensions counts and source totals track installed entries`() = runTest {
		val entries = MutableStateFlow(
			listOf(
				InstalledExtensionEntry(
					pkgName = "pkg.one",
					name = "One",
					versionName = "1.0",
					versionCode = 1L,
					libVersion = 1.0,
					lang = "en",
					isNsfw = false,
					sourceNames = listOf("A", "B"),
				),
				InstalledExtensionEntry(
					pkgName = "pkg.two",
					name = "Two",
					versionName = "2.0",
					versionCode = 2L,
					libVersion = 1.0,
					lang = "ja",
					isNsfw = true,
					sourceNames = listOf("C"),
				),
			),
		)
		val runtime = createRuntime(
			scope = this,
			installedEntries = entries,
			hasExtensions = { true },
		)
		val extensionsJob = runtime.scope.launch { runtime.instance.extensions.collect {} }
		val extensionCountJob = runtime.scope.launch { runtime.instance.extensionCount.collect {} }
		val sourceCountJob = runtime.scope.launch { runtime.instance.sourceCount.collect {} }
		try {
			advanceUntilIdle()

			assertEquals(2, runtime.instance.extensionCount.value)
			assertEquals(3, runtime.instance.sourceCount.value)
			assertEquals(listOf("pkg.one", "pkg.two"), runtime.instance.extensions.value.map { it.pkgName })
			assertTrue(runtime.instance.extensions.value[1].isNsfw)
		} finally {
			extensionsJob.cancel()
			extensionCountJob.cancel()
			sourceCountJob.cancel()
			runtime.scope.cancel()
		}
	}

	private var reloadCalls = 0

	private fun createRuntime(
		scope: TestScope,
		installedEntries: MutableStateFlow<List<InstalledExtensionEntry>> = MutableStateFlow(emptyList()),
		hasExtensions: () -> Boolean,
	): RuntimeFixture {
		reloadCalls = 0
		val dispatcher = StandardTestDispatcher(scope.testScheduler)
		val runtimeScope = CoroutineScope(SupervisorJob() + dispatcher)
		return RuntimeFixture(
			instance = ExternalInstalledExtensionsScreenRuntime(
				scope = runtimeScope,
				installedEntries = installedEntries,
				hasExtensions = hasExtensions,
				reloadAction = { reloadCalls += 1 },
				dispatcher = dispatcher,
			),
			scope = runtimeScope,
		)
	}

	private data class RuntimeFixture(
		val instance: ExternalInstalledExtensionsScreenRuntime,
		val scope: CoroutineScope,
	) {
		fun initialize() = instance.initialize()
		val isLoading get() = instance.isLoading
	}
}
