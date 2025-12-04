package org.skepsun.kototoro.local.epub

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.skepsun.kototoro.core.db.MangaDatabase
import org.skepsun.kototoro.core.db.entity.EpubChapterMappingEntity
import kotlin.random.Random

/**
 * Property-based tests for EPUB chapter mapping cascade deletion.
 * 
 * Feature: epub-reader-improvements
 * Property 19: Cascade Deletion
 * Validates: Requirements 5.4
 * 
 * For any EPUB file deletion, all associated chapter mappings 
 * SHALL be removed from the database.
 */
@RunWith(AndroidJUnit4::class)
class CascadeDeletionPropertyTest {
    
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
    
    /**
     * Generates a list of random chapter mappings for a given parent ID.
     */
    private fun generateMappingsForParent(parentId: Long, count: Int): List<EpubChapterMappingEntity> {
        return (0 until count).map { index ->
            val internalId = parentId + (index * 1_000_000L) + 1
            EpubChapterMappingEntity(
                internalChapterId = internalId,
                parentChapterId = parentId,
                epubFilePath = "/storage/emulated/0/epub/chapter_${parentId}_vol1.epub",
                epubFileName = "Volume ${Random.nextInt(1, 10)}",
                chapterIndex = index,
                chapterTitle = "Chapter ${index + 1}: ${generateRandomTitle()}",
                createdAt = System.currentTimeMillis()
            )
        }
    }
    
    private fun generateRandomTitle(): String {
        val titles = listOf(
            "The Beginning",
            "A New Adventure",
            "The Journey Continues",
            "Unexpected Encounter",
            "The Final Battle",
            "Epilogue",
            "Prologue",
            "The Awakening",
            "Dark Secrets",
            "Hope Returns"
        )
        return titles.random()
    }
    
    @Test
    fun deletingParentRemovesAllAssociatedMappings() = runBlocking {
        val dao = database.getEpubChapterMappingDao()
        
        // Run 100 iterations with random data
        repeat(100) {
            val parentId = Random.nextLong(1L, 999_999L)
            val chapterCount = Random.nextInt(1, 50)
            
            // Generate and insert mappings
            val mappings = generateMappingsForParent(parentId, chapterCount)
            dao.insertAll(mappings)
            
            // Verify mappings exist
            val beforeDelete = dao.getByParentId(parentId)
            assertEquals(chapterCount, beforeDelete.size)
            
            // Delete by parent ID
            dao.deleteByParentId(parentId)
            
            // Verify all mappings are removed
            val afterDelete = dao.getByParentId(parentId)
            assertTrue("All mappings should be deleted", afterDelete.isEmpty())
            
            // Verify individual lookups also return null
            mappings.forEach { mapping ->
                val retrieved = dao.getById(mapping.internalChapterId)
                assertEquals("Mapping should not exist after cascade deletion", null, retrieved)
            }
        }
    }
    
    @Test
    fun deletingOneParentDoesNotAffectOtherParents() = runBlocking {
        val dao = database.getEpubChapterMappingDao()
        
        repeat(50) {
            // Create multiple parent chapters with their mappings
            val parent1Id = Random.nextLong(1L, 499_999L)
            val parent2Id = Random.nextLong(500_000L, 999_999L)
            
            val count1 = Random.nextInt(1, 20)
            val count2 = Random.nextInt(1, 20)
            
            val mappings1 = generateMappingsForParent(parent1Id, count1)
            val mappings2 = generateMappingsForParent(parent2Id, count2)
            
            // Insert both sets of mappings
            dao.insertAll(mappings1)
            dao.insertAll(mappings2)
            
            // Verify both exist
            assertEquals(count1, dao.getByParentId(parent1Id).size)
            assertEquals(count2, dao.getByParentId(parent2Id).size)
            
            // Delete only parent1
            dao.deleteByParentId(parent1Id)
            
            // Verify parent1 mappings are deleted
            assertTrue(dao.getByParentId(parent1Id).isEmpty())
            
            // Verify parent2 mappings are NOT affected
            val parent2Mappings = dao.getByParentId(parent2Id)
            assertEquals(count2, parent2Mappings.size)
            
            // Verify parent2 mappings are intact
            parent2Mappings.forEachIndexed { index, mapping ->
                assertEquals(mappings2[index], mapping)
            }
        }
    }
    
    @Test
    fun cascadeDeletionWorksWithEmptyMappingSet() = runBlocking {
        val dao = database.getEpubChapterMappingDao()
        
        repeat(50) {
            val parentId = Random.nextLong(1L, 999_999L)
            
            // Delete without inserting anything
            dao.deleteByParentId(parentId)
            
            // Verify no error and result is empty
            val result = dao.getByParentId(parentId)
            assertTrue(result.isEmpty())
        }
    }
    
    @Test
    fun cascadeDeletionWorksWithSingleMapping() = runBlocking {
        val dao = database.getEpubChapterMappingDao()
        
        repeat(100) {
            val parentId = Random.nextLong(1L, 999_999L)
            
            // Create single mapping
            val mapping = generateMappingsForParent(parentId, 1).first()
            dao.insert(mapping)
            
            // Verify it exists
            assertEquals(1, dao.getByParentId(parentId).size)
            
            // Delete
            dao.deleteByParentId(parentId)
            
            // Verify it's gone
            assertTrue(dao.getByParentId(parentId).isEmpty())
            assertEquals(null, dao.getById(mapping.internalChapterId))
        }
    }
    
    @Test
    fun cascadeDeletionWorksWithLargeNumberOfMappings() = runBlocking {
        val dao = database.getEpubChapterMappingDao()
        
        repeat(20) {
            val parentId = Random.nextLong(1L, 999_999L)
            val largeCount = Random.nextInt(100, 500)
            
            // Create many mappings
            val mappings = generateMappingsForParent(parentId, largeCount)
            dao.insertAll(mappings)
            
            // Verify count
            assertEquals(largeCount, dao.getByParentId(parentId).size)
            
            // Delete all
            dao.deleteByParentId(parentId)
            
            // Verify all deleted
            assertTrue(dao.getByParentId(parentId).isEmpty())
        }
    }
}
