package org.skepsun.kototoro.local.epub

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.skepsun.kototoro.core.db.MangaDatabase
import org.skepsun.kototoro.core.db.entity.EpubChapterMappingEntity
import kotlin.random.Random

/**
 * Property-based tests for EPUB chapter mapping persistence.
 * 
 * Feature: epub-reader-improvements
 * Property 18: Mapping Persistence Round-Trip
 * Validates: Requirements 5.3
 * 
 * For any chapter mapping stored in the database, 
 * retrieving it SHALL return the same data.
 */
@RunWith(AndroidJUnit4::class)
class MappingPersistencePropertyTest {
    
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
    private fun generateRandomMapping(): EpubChapterMappingEntity {
        val parentId = Random.nextLong(1L, 999_999L)
        val chapterIndex = Random.nextInt(0, 999)
        val internalId = parentId + (chapterIndex * 1_000_000L) + 1
        
        return EpubChapterMappingEntity(
            internalChapterId = internalId,
            parentChapterId = parentId,
            epubFilePath = "/storage/emulated/0/epub/chapter_${parentId}_vol${Random.nextInt(1, 10)}.epub",
            epubFileName = "Volume ${Random.nextInt(1, 10)}",
            chapterIndex = chapterIndex,
            chapterTitle = "Chapter ${chapterIndex + 1}: ${generateRandomTitle()}",
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
    fun storedMappingsCanBeRetrievedWithSameData() = runBlocking {
        val dao = database.getEpubChapterMappingDao()
        
        // Run 100 iterations with random data
        repeat(100) {
            val mapping = generateRandomMapping()
            
            // Insert the mapping
            dao.insert(mapping)
            
            // Retrieve by internal chapter ID
            val retrieved = dao.getById(mapping.internalChapterId)
            
            // Verify all fields match
            assertEquals(mapping, retrieved)
        }
    }
    
    @Test
    fun multipleStoredMappingsCanBeRetrievedByParentId() = runBlocking {
        val dao = database.getEpubChapterMappingDao()
        
        // Run 50 iterations
        repeat(50) {
            val parentId = Random.nextLong(1L, 999_999L)
            val chapterCount = Random.nextInt(1, 20)
            val mappings = mutableListOf<EpubChapterMappingEntity>()
            
            // Generate multiple chapters for the same parent
            for (i in 0 until chapterCount) {
                val internalId = parentId + (i * 1_000_000L) + 1
                val mapping = EpubChapterMappingEntity(
                    internalChapterId = internalId,
                    parentChapterId = parentId,
                    epubFilePath = "/storage/emulated/0/epub/chapter_${parentId}_vol1.epub",
                    epubFileName = "Volume 1",
                    chapterIndex = i,
                    chapterTitle = "Chapter ${i + 1}",
                    createdAt = System.currentTimeMillis()
                )
                mappings.add(mapping)
            }
            
            // Insert all mappings
            dao.insertAll(mappings)
            
            // Retrieve by parent ID
            val retrieved = dao.getByParentId(parentId)
            
            // Verify count matches
            assertEquals(chapterCount, retrieved.size)
            
            // Verify all mappings are present and in correct order
            retrieved.forEachIndexed { index, retrievedMapping ->
                assertEquals(mappings[index], retrievedMapping)
            }
        }
    }
    
    @Test
    fun roundTripInsertAndRetrievePreservesAllFields() = runBlocking {
        val dao = database.getEpubChapterMappingDao()
        
        repeat(100) {
            val original = generateRandomMapping()
            
            // Insert
            dao.insert(original)
            
            // Retrieve
            val retrieved = dao.getById(original.internalChapterId)
            
            // Verify each field individually
            assertNotNull(retrieved)
            assertEquals(original.internalChapterId, retrieved!!.internalChapterId)
            assertEquals(original.parentChapterId, retrieved.parentChapterId)
            assertEquals(original.epubFilePath, retrieved.epubFilePath)
            assertEquals(original.epubFileName, retrieved.epubFileName)
            assertEquals(original.chapterIndex, retrieved.chapterIndex)
            assertEquals(original.chapterTitle, retrieved.chapterTitle)
            assertEquals(original.createdAt, retrieved.createdAt)
        }
    }
}
