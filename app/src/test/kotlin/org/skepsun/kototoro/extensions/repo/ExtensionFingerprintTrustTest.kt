package org.skepsun.kototoro.extensions.repo

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ExtensionFingerprintTrustTest : FunSpec({

	test("blank expected fingerprint is trusted") {
		ExtensionFingerprintTrust.isTrusted("", setOf("abcdef")) shouldBe true
	}

	test("fingerprints are normalized before comparison") {
		ExtensionFingerprintTrust.isTrusted(
			expectedFingerprint = "AA:BB CC",
			actualFingerprints = setOf("aabbcc"),
		) shouldBe true
	}

	test("mismatched fingerprints are rejected") {
		ExtensionFingerprintTrust.isTrusted(
			expectedFingerprint = "AA:BB:CC",
			actualFingerprints = setOf("ddeeff"),
		) shouldBe false
	}
})
