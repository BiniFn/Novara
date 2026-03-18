package org.skepsun.kototoro.backups.domain

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skepsun.kototoro.core.prefs.AppSettings

class BackupFlowPolicyTest {

	private val settings = mockk<AppSettings>(relaxed = true)
	private val policy = BackupFlowPolicy(settings)

	@Test
	fun `auto restore decision distinguishes disabled incomplete and ready`() {
		every { settings.isBackupWebDavAutoRestoreEnabled } returns false
		assertEquals(
			BackupFlowPolicy.FlowDecision(false, "feature_disabled"),
			policy.autoRestoreStartupDecision(),
		)

		every { settings.isBackupWebDavAutoRestoreEnabled } returns true
		every { settings.backupWebDavServerUrl } returns null
		every { settings.backupWebDavUsername } returns "user"
		every { settings.backupWebDavPassword } returns "pass"
		assertEquals(
			BackupFlowPolicy.FlowDecision(false, "incomplete_config"),
			policy.autoRestoreStartupDecision(),
		)

		every { settings.backupWebDavServerUrl } returns "https://example.com"
		assertEquals(
			BackupFlowPolicy.FlowDecision(true, null),
			policy.autoRestoreStartupDecision(),
		)
	}

	@Test
	fun `auto sync decision requires feature and complete webdav config`() {
		every { settings.isBackupWebDavAutoSyncEnabled } returns true
		every { settings.isBackupWebDavUploadEnabled } returns true
		every { settings.backupWebDavServerUrl } returns "https://example.com"
		every { settings.backupWebDavUsername } returns "user"
		every { settings.backupWebDavPassword } returns "pass"

		assertTrue(policy.autoSyncUploadDecision().allowed)

		every { settings.isBackupWebDavUploadEnabled } returns false
		assertEquals("feature_disabled", policy.autoSyncUploadDecision().reason)

		every { settings.isBackupWebDavUploadEnabled } returns true
		every { settings.backupWebDavPassword } returns ""
		assertEquals("incomplete_config", policy.autoSyncUploadDecision().reason)
	}

	@Test
	fun `periodical backup plan reports destinations and missing destination reason`() {
		every { settings.isPeriodicalBackupEnabled } returns true
		every { settings.isBackupWebDavKeepLocalCopyEnabled } returns false
		every { settings.periodicalBackupDirectory } returns null
		every { settings.isBackupTelegramUploadEnabled } returns false
		every { settings.isBackupWebDavUploadEnabled } returns false

		val emptyPlan = policy.periodicalBackupPlan(telegramAvailable = false)
		assertFalse(emptyPlan.decision.allowed)
		assertEquals("no_destination", emptyPlan.decision.reason)
		assertFalse(emptyPlan.destinations.hasAnyDestination)

		every { settings.isBackupWebDavUploadEnabled } returns true
		val webDavPlan = policy.periodicalBackupPlan(telegramAvailable = false)
		assertTrue(webDavPlan.decision.allowed)
		assertTrue(webDavPlan.destinations.hasWebDavDestination)
		assertTrue(webDavPlan.destinations.hasAnyDestination)
	}

	@Test
	fun `periodical backup frequency decision only considers enabled destinations`() {
		every { settings.periodicalBackupFrequencyMillis } returns 1_000L
		val destinations = BackupFlowPolicy.PeriodicalBackupDestinations(
			hasLocalCopyDestination = false,
			hasTelegramDestination = false,
			hasWebDavDestination = true,
		)

		assertEquals(
			BackupFlowPolicy.FlowDecision(false, "frequency_gate"),
			policy.periodicalBackupFrequencyDecision(
				now = 2_000L,
				localLastBackupTime = 10_000L,
				webDavLastUploadTime = 1_500L,
				destinations = destinations,
			),
		)
		assertEquals(
			BackupFlowPolicy.FlowDecision(true, null),
			policy.periodicalBackupFrequencyDecision(
				now = 3_000L,
				localLastBackupTime = 10_000L,
				webDavLastUploadTime = 1_500L,
				destinations = destinations,
			),
		)
	}
}
