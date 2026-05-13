package org.skepsun.kototoro.core.parser.tvbox

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TVBoxRuntimeDiagnosticsTest {

	@Test
	fun `jar missing class is classified separately from Guard native failures`() {
		val error = NoClassDefFoundError("com/github/catvod/net/OkHttp")

		val result = TVBoxRuntimeDiagnostics.classifyJar(error)

		assertEquals(TVBoxRuntimeFailureCategory.ORDINARY_JAR_MISSING_CLASS, result)
	}

	@Test
	fun `jar missing method is classified as host ABI mismatch`() {
		val error = NoSuchMethodError("com.github.catvod.crawler.Spider.homeContent(Z)Ljava/lang/String;")

		val result = TVBoxRuntimeDiagnostics.classifyJar(error)

		assertEquals(TVBoxRuntimeFailureCategory.ORDINARY_JAR_MISSING_METHOD, result)
	}

	@Test
	fun `Guard and native signals are classified as Guard native`() {
		val error = UnsatisfiedLinkError("DexNative Guard failed to load libguard.so")

		val result = TVBoxRuntimeDiagnostics.classifyJar(error)

		assertEquals(TVBoxRuntimeFailureCategory.GUARD_NATIVE, result)
	}

	@Test
	fun `QuickJS bytecode locator is classified as missing bridge feature`() {
		val result = TVBoxRuntimeDiagnostics.classifyQuickJs(
			error = null,
			detail = "unsupported_bytecode=https://example.test/cat.js //bb",
		)

		assertEquals(TVBoxRuntimeFailureCategory.QUICKJS_MISSING_FEATURE, result)
	}

	@Test
	fun `QuickJS ES module import is classified as missing bridge feature`() {
		val result = TVBoxRuntimeDiagnostics.classifyQuickJs(
			error = IllegalStateException("ES module import is not supported"),
		)

		assertEquals(TVBoxRuntimeFailureCategory.QUICKJS_MISSING_FEATURE, result)
	}

	@Test
	fun `repository failure without spider runtime is classified as CMS fallback by default`() {
		val result = TVBoxRuntimeDiagnostics.classifyRepository(
			error = IllegalStateException("HTTP 404"),
			requiresSpiderRuntime = false,
		)

		assertEquals(TVBoxRuntimeFailureCategory.CMS_FALLBACK, result)
	}

	@Test
	fun `repository failure with spider runtime delegates to jar classification`() {
		val result = TVBoxRuntimeDiagnostics.classifyRepository(
			error = NoSuchMethodError("proxyLocal"),
			requiresSpiderRuntime = true,
		)

		assertEquals(TVBoxRuntimeFailureCategory.ORDINARY_JAR_MISSING_METHOD, result)
	}
}
