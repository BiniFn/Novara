package org.skepsun.kototoro.local.epub

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.skepsun.kototoro.core.db.MangaDatabase
import org.skepsun.kototoro.core.db.entity.EpubChapterMappingEntity
import kotlin.random.Random

/**
 * Property-based tests for bidirectional EPUB chapter mapping lookup.
 * 
 * Feature: epub-reader-improvements
 * Property 20: Bidirectional Mapping Lookup
 * Validates: Requirements 5.5
 * 
 * For any stored chapter mapping, it SHALL be retrievable by both 
 * internal chapter ID and parent chapter ID.
 */
@RunWith(AndroidJUnit4::class)
class BidirectionalMappingLookupPropertyTest {
    
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
     * Generates a random EpubChapterMappingEntity for testing.
     */
    private fun generateRandomMapping(parentId: Long? = null, chapterIndex: Int? = null): EpubChapterMappingEntity {
        val actualParentId = parentId ?: Random.nextLong(1L, 999_999L)
        val actualChapterIndex = chapterIndex ?: Random.nextInt(0, 999)
        val internalId = actualParentId + (actualChapterIndex * 1_000_000L) + 1
        
        return EpubChapterMappingEntity(
            internalChapterId = internalId,
            parentChapterId = actualParentId,
            epubFilePath = "/storage/emulated/0/epub/chapter_${actualParentId}_vol${Random.nextInt(1, 10)}.epub",
            epubFileName = "Volume ${Random.nextInt(1, 10)}",
            chapterIndex = actualChapterIndex,
            chapterTitle = "Chapter ${actualChapterIndex + 1}: ${generateRandomTitle()}",
            createdAt = System.currentTimeMillis()
        )
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
    fun mappingCanBeRetrievedByInternalChapterId() = runBlocking {
        val dao = database.getEpubChapterMappingDao()
        
        // Run 100 iterations with random data
        repeat(100) {
            val mapping = generateRandomMapping()
            
            // Insert the mapping
            dao.insert(mapping)
            
            // Retrieve by internal chapter ID
            val retrieved = dao.getById(mapping.internalChapterId)
            
            // Verify mapping was found
            assertNotNull("Mapping should be retrievable by internal chapter ID", retrieved)
            assertEquals("Retrieved mapping should match original", mapping, retrieved)
        }
    }
    
    @Test
    fun mappingCanBeRetrievedByParentChapterId() = runBlocking {
        val dao = database.getEpubChapterMappingDao()
        
        // Run 100 iterations with random data
        repeat(100) {
            val parentId = Random.nextLong(1L, 999_999L)
            val chapterCount = Random.nextInt(1, 20)
            val mappings = mutableListOf<EpubChapterMappingEntity>()
            
            // Generate multiple chapters for the same parent
            for (i in 0 until chapterCount) {
                val mapping = generateRandomMapping(parentId = parentId, chapterIndex = i)
                mappings.add(mapping)
            }
            
            // Insert all mappings
            dao.insertAll(mappings)
            
            // Retrieve by parent ID
            val retrieved = dao.getByParentId(parentId)
            
            // Verify all mappings were found
            assertEquals("All mappings should be retrievable by parent chapter ID", chapterCount, retrieved.size)
            
            // Verify each mapping is present
            mappings.forEach { original ->
                val found = retrieved.find { it.internalChapterId == original.internalChapterId }
                assertNotNull("Each mapping should be found in results", found)
                assertEquals("Retrieved mapping should match original", original, found)
            }
        }
    }
    
    @Test
    fun bidirectionalLookupReturnsConsistentData() = runBlocking {
        val dao = database.getEpubChapterMappingDao()
        
        // Run 100 iterations
        repeat(100) {
            val parentId = Random.nextLong(1L, 999_999L)
            val chapterCount = Random.nextInt(1, 20)
            val mappings = mutableListOf<EpubChapterMappingEntity>()
            
            // Generate multiple chapters for the same parent
            for (i in 0 until chapterCount) {
                val mapping = generateRandomMapping(parentId = parentId, chapterIndex = i)
                mappings.add(mapping)
            }
            
            // Insert all mappings
            dao.insertAll(mappings)
            
            // For each mapping, verify it can be retrieved both ways
            mappings.forEach { original ->
                // Retrieve by internal chapter ID
                val byInternalId = dao.getById(original.internalChapterId)
                assertNotNull("Mapping should be retrievable by internal ID", byInternalId)
                
                // Retrieve by parent chapter ID
                val byParentId = dao.getByParentId(original.parentChapterId)
                assertTrue("Mapping should be in parent ID results", 
                    byParentId.any { it.internalChapterId == original.internalChapterId })
                
                // Verify both lookups return the same data
                val fromParentLookup = byParentId.find { it.internalChapterId == original.internalChapterId }
                assertEquals("Both lookup methods should return identical data", byInternalId, fromParentLookup)
            }
        }
    }
    
    @Test
    fun parentIdLookupReturnsAllChildMappings() = runBlocking {
        val dao = database.getEpubChapterMappingDao()
        
        // Run 50 iterations
        repeat(50) {
            val parentId = Random.nextLong(1L, 999_999L)
            val chapterCount = Random.nextInt(5, 30)
            val expectedInternalIds = mutableSetOf<Long>()
            
            // Generate and insert multiple chapters
            for (i in 0 until chapterCount) {
                val mapping = generateRandomMapping(parentId = parentId, chapterIndex = i)
                expectedInternalIds.add(mapping.internalChapterId)
                dao.insert(mapping)
            }
            
            // Retrieve by parent ID
            val retrieved = dao.getByParentId(parentId)
            val retrievedInternalIds = retrieved.map { it.internalChapterId }.toSet()
            
            // Verify all expected IDs are present
            assertEquals("All internal chapter IDs should be retrievable via parent ID", 
                expectedInternalIds, retrievedInternalIds)
        }
    }
    
    @Test
    fun internalIdLookupReturnsCorrectParentId() = runBlocking {
        val dao = database.getEpubChapterMappingDao()
        
        // Run 100 iterations
        repeat(100) {
            val parentId = Random.nextLong(1L, 999_999L)
            val chapterIndex = Random.nextInt(0, 999)
            val mapping = generateRandomMapping(parentId = parentId, chapterIndex = chapterIndex)
            
            // Insert the mapping
            dao.insert(mapping)
            
            // Retrieve by internal chapter ID
            val retrieved = dao.getById(mapping.internalChapterId)
            
            // Verify parent ID is correct
            assertNotNull("Mapping should be found", retrieved)
            assertEquals("Parent ID should match", parentId, retrieved!!.parentChapterId)
            
            // Verify this mapping is also in the parent's children
            val siblings = dao.getByParentId(parentId)
            assertTrue("Mapping should be in parent's children", 
                siblings.any { it.internalChapterId == mapping.internalChapterId })
        }
    }
    
    @Test
    fun lookupPerformanceWithIndex() = runBlocking {
        val dao = database.getEpubChapterMappingDao()
        
        // Insert a large number of mappings across multiple parents
        val parentIds = List(10) { Random.nextLong(1L, 999_999L) }
        
        parentIds.forEach { parentId ->
            val mappings = List(50) { i ->
                generateRandomMapping(parentId = parentId, chapterIndex = i)
            }
            dao.insertAll(mappings)
        }
        
        // Verify lookups are fast (should complete quickly with index)
        val startTime = System.currentTimeMillis()
        
        parentIds.forEach { parentId ->
            val retrieved = dao.getByParentId(parentId)
            assertEquals("Should retrieve all 50 chapters", 50, retrieved.size)
        }
        
        val duration = System.currentTimeMillis() - startTime
        
        // With proper indexing, 500 lookups should be very fast (< 1 second)
        assertTrue("Lookups should be fast with index (took ${duration}ms)", duration < 1000)
    }
}
