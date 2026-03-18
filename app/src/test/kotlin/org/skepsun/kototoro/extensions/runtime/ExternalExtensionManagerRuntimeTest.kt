package org.skepsun.kototoro.extensions.runtime

import android.content.Context
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExternalExtensionManagerRuntimeTest {

	@Test
	fun `loadExtensions updates state and caches processed results`() = runBlocking {
		val source = FakeSource(1L)
		val wrapped = FakeWrappedSource(1L)
		val runtime = ExternalExtensionManagerRuntime<FakeResult, FakeSuccess, FakeError, FakeSource, FakeWrappedSource>(
			context = mockk<Context>(relaxed = true),
			scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
		)

		assertFalse(runtime.isLoading.value)
		assertTrue(runtime.installedExtensions.value.isEmpty())
		assertTrue(runtime.failedExtensions.value.isEmpty())

		runtime.loadExtensions(
			loadResults = { listOf(FakeResult.Ok) },
			processResults = {
				assertEquals(listOf(FakeResult.Ok), it)
				ProcessedExternalExtensions(
					successful = listOf(FakeSuccess("pkg.ok")),
					failed = listOf(FakeError("pkg.err")),
					sourceById = mapOf(source.id to source),
					wrappedSourceById = mapOf(wrapped.id to wrapped),
					untrustedPackages = listOf("pkg.untrusted"),
				)
			},
		)

		assertFalse(runtime.isLoading.value)
		assertEquals(listOf(FakeSuccess("pkg.ok")), runtime.installedExtensions.value)
		assertEquals(listOf(FakeError("pkg.err")), runtime.failedExtensions.value)
		assertSame(source, runtime.getSourceById(1L))
		assertSame(wrapped, runtime.getWrappedSourceById(1L))
		assertEquals(listOf(wrapped), runtime.getWrappedSources())
		assertEquals(1, runtime.getSourceCount())
		assertTrue(runtime.hasExtensions())
		assertNull(runtime.getSourceById(99L))
	}

	private sealed interface FakeResult {
		data object Ok : FakeResult
	}

	private data class FakeSuccess(
		val pkgName: String,
	)

	private data class FakeError(
		val pkgName: String,
	)

	private data class FakeSource(
		val id: Long,
	)

	private data class FakeWrappedSource(
		val id: Long,
	)
}
