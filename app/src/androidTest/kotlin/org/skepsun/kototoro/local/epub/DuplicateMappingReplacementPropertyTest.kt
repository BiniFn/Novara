package org.skepsun.kototoro.local.epub

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.skepsun.kototoro.core.db.MangaDatabase
import org.skepsun.kototoro.core.db.entity.EpubChapterMappingEntity
import kotlin.random.Random

/**
 * Property-based tests for EPUB chapter mapping duplicate replacement.
 * 
 * Feature: epub-reader-improvements
 * Property 30: Duplicate Mapping Replacement
 * Validates: Requirements 8.4
 * 
 * For any duplicate chapter mapping insertion, 
 * the new mapping SHALL replace the old one.
 */
@RunWith(AndroidJUnit4::class)
class DuplicateMappingReplacementPropertyTest {
    
    private lateinit var database: MangaDatabase
    private lateinit var context: Context
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(
            context,
            MangaDatabase::class.java
        ).allowMainThreadQueries().build()
    }
    
    @After
    fun teardown() {
        database.close()
    }
    
    @Test
    fun duplicateInsertReplacesOldMapping() = runBlocking {
        val dao = database.getEpubChapterMappingDao()
        
        // Run 100 iterations
        repeat(100) {
            val parentId = Random.nextLong(1L, 999_999L)
            val chapterIndex = Random.nextInt(0, 999)
            val internalId = parentId + (chapterIndex * 1_000_000L) + 1
            
            // Insert first mapping
            val firstMapping = EpubChapterMappingEntity(
                internalChapterId = internalId,
                parentChapterId = parentId,
                epubFilePath = "/storage/old/path.epub",
                epubFileName = "Old Volume",
                chapterIndex = chapterIndex,
                chapterTitle = "Old Title",
                createdAt = 1000L
            )
            dao.insert(firstMapping)
            
            // Insert second mapping with same internal chapter ID
            val secondMapping = EpubChapterMappingEntity(
                internalChapterId = internalId,
                parentChapterId = parentId,
                epubFilePath = "/storage/new/path.epub",
                epubFileName = "New Volume",
                chapterIndex = chapterIndex,
                chapterTitle = "New Title",
                createdAt = 2000L
            )
            dao.insert(secondMapping)
            
            // Retrieve the mapping
            val retrieved = dao.getById(internalId)
            
            // Verify the new mapping replaced the old one
            assertNotNull(retrieved)
            assertEquals("/storage/new/path.epub", retrieved!!.epubFilePath)
            assertEquals("New Volume", retrieved.epubFileName)
            assertEquals("New Title", retrieved.chapterTitle)
            assertEquals(2000L, retrieved.createdAt)
            
            // Verify only one mapping exists
            val count = dao.countByParentId(parentId)
            assertEquals(1, count)
        }
    }
    
    @Test
    fun multipleReplacementsKeepLatestVersion() = runBlocking {
        val dao = database.getEpubChapterMappingDao()
        
        repeat(50) {
            val parentId = Random.nextLong(1L, 999_999L)
            val chapterIndex = Random.nextInt(0, 999)
            val internalId = parentId + (chapterIndex * 1_000_000L) + 1
            
            // Insert multiple versions of the same mapping
            val versions = Random.nextInt(3, 10)
            var latestMapping: EpubChapterMappingEntity? = null
            
            for (version in 1..versions) {
                val mapping = EpubChapterMappingEntity(
                    internalChapterId = internalId,
                    parentChapterId = parentId,
                    epubFilePath = "/storage/version_${version}/path.epub",
                    epubFileName = "Volume Version $version",
                    chapterIndex = chapterIndex,
                    chapterTitle = "Title Version $version",
                    createdAt = version.toLong() * 1000
                )
                dao.insert(mapping)
                latestMapping = mapping
            }
            
            // Retrieve the mapping
            val retrieved = dao.getById(internalId)
            
            // Verify it's the latest version
            assertEquals(latestMapping, retrieved)
            
            // Verify only one mapping exists
            val count = dao.countByParentId(parentId)
            assertEquals(1, count)
        }
    }
    
    @Test
    fun batchInsertWithDuplicatesReplacesCorrectly() = runBlocking {
        val dao = database.getEpubChapterMappingDao()
        
        repeat(50) {
            val parentId = Random.nextLong(1L, 999_999L)
            
            // Create initial mappings
            val initialMappings = mutableListOf<EpubChapterMappingEntity>()
            for (i in 0 until 5) {
                val internalId = parentId + (i * 1_000_000L) + 1
                val mapping = EpubChapterMappingEntity(
                    internalChapterId = internalId,
                    parentChapterId = parentId,
                    epubFilePath = "/storage/initial/path.epub",
                    epubFileName = "Initial Volume",
                    chapterIndex = i,
                    chapterTitle = "Initial Chapter $i",
                    createdAt = 1000L
                )
                initialMappings.add(mapping)
            }
            dao.insertAll(initialMappings)
            
            // Create updated mappings with same IDs
            val updatedMappings = mutableListOf<EpubChapterMappingEntity>()
            for (i in 0 until 5) {
                val internalId = parentId + (i * 1_000_000L) + 1
                val mapping = EpubChapterMappingEntity(
                    internalChapterId = internalId,
                    parentChapterId = parentId,
                    epubFilePath = "/storage/updated/path.epub",
                    epubFileName = "Updated Volume",
                    chapterIndex = i,
                    chapterTitle = "Updated Chapter $i",
                    createdAt = 2000L
                )
                updatedMappings.add(mapping)
            }
            dao.insertAll(updatedMappings)
            
            // Retrieve all mappings
            val retrieved = dao.getByParentId(parentId)
            
            // Verify we still have exactly 5 mappings (not 10)
            assertEquals(5, retrieved.size)
            
            // Verify all are the updated versions
            retrieved.forEach { mapping ->
                assertEquals("/storage/updated/path.epub", mapping.epubFilePath)
                assertEquals("Updated Volume", mapping.epubFileName)
                assert(mapping.chapterTitle.startsWith("Updated Chapter"))
                assertEquals(2000L, mapping.createdAt)
            }
        }
    }
    
    @Test
    fun replacementPreservesOnlyLatestData() = runBlocking {
        val dao = database.getEpubChapterMappingDao()
        
        repeat(100) {
            val parentId = Random.nextLong(1L, 999_999L)
            val chapterIndex = Random.nextInt(0, 999)
            val internalId = parentId + (chapterIndex * 1_000_000L) + 1
            
            // Insert original
            val original = EpubChapterMappingEntity(
                internalChapterId = internalId,
                parentChapterId = parentId,
                epubFilePath = "/original/path.epub",
                epubFileName = "Original",
                chapterIndex = chapterIndex,
                chapterTitle = "Original Title",
                createdAt = 1000L
            )
            dao.insert(original)
            
            // Verify original is stored
            val retrievedOriginal = dao.getById(internalId)
            assertEquals(original, retrievedOriginal)
            
            // Insert replacement
            val replacement = EpubChapterMappingEntity(
                internalChapterId = internalId,
                parentChapterId = parentId,
                epubFilePath = "/replacement/path.epub",
                epubFileName = "Replacement",
                chapterIndex = chapterIndex,
                chapterTitle = "Replacement Title",
                createdAt = 2000L
            )
            dao.insert(replacement)
            
            // Verify replacement is stored and original is gone
            val retrievedReplacement = dao.getById(internalId)
            assertEquals(replacement, retrievedReplacement)
            assertNotEquals(original, retrievedReplacement)
            
            // Verify no trace of original data remains
            assertNotEquals("/original/path.epub", retrievedReplacement!!.epubFilePath)
            assertNotEquals("Original", retrievedReplacement.epubFileName)
            assertNotEquals("Original Title", retrievedReplacement.chapterTitle)
            assertNotEquals(1000L, retrievedReplacement.createdAt)
        }
    }
}
