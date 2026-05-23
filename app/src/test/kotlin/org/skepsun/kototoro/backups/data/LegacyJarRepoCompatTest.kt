package org.skepsun.kototoro.backups.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skepsun.kototoro.backups.domain.BackupSection
import org.skepsun.kototoro.extensions.repo.ExternalExtensionType
import org.skepsun.kototoro.settings.sources.unified.UnifiedRecommendedRepository
import org.skepsun.kototoro.settings.sources.unified.UnifiedRepositoryCapability
import org.skepsun.kototoro.settings.sources.unified.UnifiedRepositoryLocationType
import org.skepsun.kototoro.settings.sources.unified.UnifiedSourceKind

class LegacyJarRepoCompatTest {

    @Test
    fun `should import legacy jar repos for old backup with restored sources`() {
        val result = LegacyJarRepoCompat.shouldImport(
            requestedSections = setOf(BackupSection.SOURCES, BackupSection.SETTINGS),
            archiveSections = setOf(BackupSection.INDEX, BackupSection.SOURCES),
            restoredSections = setOf(BackupSection.SOURCES),
            hasExistingJarRepos = false,
        )

        assertTrue(result)
    }

    @Test
    fun `should not import legacy jar repos when archive already contains extension repos`() {
        val result = LegacyJarRepoCompat.shouldImport(
            requestedSections = setOf(BackupSection.SOURCES),
            archiveSections = setOf(BackupSection.INDEX, BackupSection.SOURCES, BackupSection.EXTENSION_REPOS),
            restoredSections = setOf(BackupSection.SOURCES),
            hasExistingJarRepos = false,
        )

        assertFalse(result)
    }

    @Test
    fun `should not import legacy jar repos when current jar repos already exist`() {
        val result = LegacyJarRepoCompat.shouldImport(
            requestedSections = setOf(BackupSection.SOURCES),
            archiveSections = setOf(BackupSection.INDEX, BackupSection.SOURCES),
            restoredSections = setOf(BackupSection.SOURCES),
            hasExistingJarRepos = true,
        )

        assertFalse(result)
    }

    @Test
    fun `build entities normalizes index urls into base urls`() {
        val repos = LegacyJarRepoCompat.buildEntities(
            now = 1234L,
            recommendedRepos = listOf(
                UnifiedRecommendedRepository(
                    kind = UnifiedSourceKind.JAR,
                    name = "Repo A",
                    url = "https://example.com/a/index.min.json",
                    locationType = UnifiedRepositoryLocationType.REMOTE_URL,
                    capabilities = setOf(UnifiedRepositoryCapability.REFRESH),
                ),
                UnifiedRecommendedRepository(
                    kind = UnifiedSourceKind.JAR,
                    name = "Repo B",
                    url = "https://example.com/b",
                    locationType = UnifiedRepositoryLocationType.REMOTE_URL,
                    capabilities = setOf(UnifiedRepositoryCapability.REFRESH),
                ),
            ),
        )

        assertEquals(2, repos.size)
        assertEquals(ExternalExtensionType.JAR, repos[0].type)
        assertEquals("https://example.com/a", repos[0].baseUrl)
        assertEquals("Kototoro: Repo A", repos[0].name)
        assertEquals("https://example.com/b", repos[1].baseUrl)
        assertEquals("Repo B", repos[1].shortName)
        assertEquals(1234L, repos[0].createdAt)
        assertEquals(0L, repos[0].lastSuccessAt)
    }
}
