package org.skepsun.kototoro.backups.domain

import android.content.ComponentName
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.skepsun.kototoro.backups.ui.webdav.DataSyncManager
import org.skepsun.kototoro.backups.ui.webdav.WebDavAutoRestoreService
import org.skepsun.kototoro.core.prefs.AppSettings

@OptIn(ExperimentalCoroutinesApi::class)
class BackupStartupCoordinatorTest {

	private val appContext = mockk<Context>(relaxed = true)
	private val settings = mockk<AppSettings>(relaxed = true)
	private val dataSyncManager = mockk<DataSyncManager>(relaxed = true)
	private val backupFlowPolicy = BackupFlowPolicy(settings)

	@AfterEach
	fun tearDown() {
		unmockkObject(WebDavAutoRestoreService.Companion)
	}

	@Test
	fun `startOnFirstLaunch starts backup services and skips auto restore when config is incomplete`() = runTest {
		every { appContext.startService(any()) } returns mockk<ComponentName>(relaxed = true)
		every { settings.isBackupWebDavAutoRestoreEnabled } returns true
		every { settings.backupWebDavServerUrl } returns ""
		every { settings.backupWebDavUsername } returns "user"
		every { settings.backupWebDavPassword } returns "pass"
		mockkObject(WebDavAutoRestoreService.Companion)
		every { WebDavAutoRestoreService.start(any()) } returns Unit

		val coordinator = BackupStartupCoordinator(appContext, backupFlowPolicy, dataSyncManager)
		coordinator.startOnFirstLaunch(this)
		advanceUntilIdle()

		verify(exactly = 1) { appContext.startService(any()) }
		verify(exactly = 1) { dataSyncManager.start() }
		verify(exactly = 0) { WebDavAutoRestoreService.start(any()) }
	}

	@Test
	fun `startOnFirstLaunch schedules auto restore when config is complete`() = runTest {
		every { appContext.startService(any()) } returns mockk<ComponentName>(relaxed = true)
		every { settings.isBackupWebDavAutoRestoreEnabled } returns true
		every { settings.backupWebDavServerUrl } returns "https://example.com"
		every { settings.backupWebDavUsername } returns "user"
		every { settings.backupWebDavPassword } returns "pass"
		mockkObject(WebDavAutoRestoreService.Companion)
		every { WebDavAutoRestoreService.start(any()) } returns Unit

		val coordinator = BackupStartupCoordinator(appContext, backupFlowPolicy, dataSyncManager)
		coordinator.startOnFirstLaunch(this)
		advanceTimeBy(3000)
		advanceUntilIdle()

		verify(exactly = 1) { dataSyncManager.start() }
		verify(exactly = 1) { WebDavAutoRestoreService.start(appContext) }
	}
}
