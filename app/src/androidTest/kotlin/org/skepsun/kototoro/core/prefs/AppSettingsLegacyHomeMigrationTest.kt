package org.skepsun.kototoro.core.prefs

import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AppSettingsLegacyHomeMigrationTest {

	@get:Rule
	var hiltRule = HiltAndroidRule(this)

	@Inject
	lateinit var settings: AppSettings

	@Before
	fun setUp() {
		hiltRule.inject()
		settings.prefs.edit { clear() }
	}

	@Test
	fun reconcileAfterAppUpgrade_sanitizesLegacyHomeState() {
		settings.prefs.edit {
			putString(AppSettings.KEY_SELECTED_GROUP_TAB, "json")
			putString(AppSettings.KEY_SELECTED_SOURCE_TAGS, "mihon,unknown,tvbox")
			putString(AppSettings.KEY_BACKUP_WEBDAV_LAST_UPLOAD_TIME, "123")
			putString(AppSettings.KEY_BACKUP_WEBDAV_LAST_RESTORE_TIME, "456")
			putString(AppSettings.KEY_BACKUP_WEBDAV_LAST_AUTO_RESTORE_CHECK_TIME, "789")
			putString(AppSettings.KEY_BACKUP_WEBDAV_LAST_MANUAL_RESTORE_TIME, "321")
		}

		settings.reconcileAfterAppUpgrade(currentVersion = 20260421)

		assertEquals("all", settings.getSelectedGroupTab())
		assertEquals(setOf("mihon", "tvbox"), settings.getSelectedSourceTags())
		assertEquals(123L, settings.backupWebDavLastUploadTime)
		assertEquals(456L, settings.backupWebDavLastRestoreTime)
		assertEquals(789L, settings.backupWebDavLastAutoRestoreCheckTime)
		assertEquals(321L, settings.backupWebDavLastManualRestoreTime)
		assertEquals(20260421, settings.prefs.getInt(AppSettings.KEY_APP_VERSION, 0))
	}

	@Test
	fun upsertAll_sanitizesLegacyHomeStateAndLongBackedPresetId() {
		settings.upsertAll(
			mapOf(
				AppSettings.KEY_SELECTED_GROUP_TAB to "aniyomi",
				AppSettings.KEY_SELECTED_SOURCE_TAGS to "json,pinned,bogus",
				AppSettings.KEY_ACTIVE_SOURCE_PRESET_ID to 7,
				AppSettings.KEY_BACKUP_WEBDAV_LAST_UPLOAD_TIME to 654,
			),
		)

		assertEquals("all", settings.getSelectedGroupTab())
		assertEquals(setOf("legado", "pinned"), settings.getSelectedSourceTags())
		assertEquals(7L, settings.activeSourcePresetId)
		assertEquals(654L, settings.backupWebDavLastUploadTime)
		assertEquals(654L, settings.prefs.getLong(AppSettings.KEY_BACKUP_WEBDAV_LAST_UPLOAD_TIME, -1L))
	}
}
