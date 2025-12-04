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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.skepsun.kototoro.core.db.MangaDatabase
import org.skepsun.kototoro.core.db.entity.EpubChapterMappingEntity
import kotlin.random.Random

/**
 * Property-based tests for EPUB chapter mapping data completeness.
 * 
 * Feature: epub-reader-improvements
 * Property 29: Mapping Data Completeness
 * Validates: Requirements 8.2
 * 
 * For any chapter mapping insertion, all fields (internalChapterId, parentChapterId, 
 * epubFilePath, epubFileName, chapterIndex, chapterTitle) SHALL be stored.
 */
@RunWith(AndroidJUnit4::class)
class MappingDataCompletenessPropertyTest {
    
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
     * Generates a random EpubChapterMappingEntity with all fields populated.
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
            chapterTitle = "Chapter ${chapterIndex + 1}: Test Title ${Random.nextInt(1000)}",
            createdAt = System.currentTimeMillis()
        )
    }
    
    @Test
    fun allFieldsAreStoredAndRetrievable() = runBlocking {
        val dao = database.getEpubChapterMappingDao()
        
        // Run 100 iterations with random data
        repeat(100) {
            val mapping = generateRandomMapping()
            
            // Insert the mapping
            dao.insert(mapping)
            
            // Retrieve the mapping
            val retrieved = dao.getById(mapping.internalChapterId)
            
            // Verify all required fields are present and not null/empty
            assertNotNull(retrieved)
            assertEquals(mapping.internalChapterId, retrieved!!.internalChapterId)
            assertEquals(mapping.parentChapterId, retrieved.parentChapterId)
            assertTrue(retrieved.epubFilePath.isNotEmpty())
            assertEquals(mapping.epubFilePath, retrieved.epubFilePath)
            assertTrue(retrieved.epubFileName.isNotEmpty())
            assertEquals(mapping.epubFileName, retrieved.epubFileName)
            assertEquals(mapping.chapterIndex, retrieved.chapterIndex)
            assertTrue(retrieved.chapterTitle.isNotEmpty())
            assertEquals(mapping.chapterTitle, retrieved.chapterTitle)
        }
    }
    
    @Test
    fun noFieldsAreLostDuringInsertion() = runBlocking {
        val dao = database.getEpubChapterMappingDao()
        
        repeat(100) {
            val original = generateRandomMapping()
            
            // Insert
            dao.insert(original)
            
            // Retrieve
            val retrieved = dao.getById(original.internalChapterId)
            
            // Verify every single field is preserved
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
    
    @Test
    fun batchInsertPreservesAllFieldsForAllMappings() = runBlocking {
        val dao = database.getEpubChapterMappingDao()
        
        repeat(50) {
            val parentId = Random.nextLong(1L, 999_999L)
            val chapterCount = Random.nextInt(5, 15)
            val mappings = mutableListOf<EpubChapterMappingEntity>()
            
            // Generate multiple mappings
            for (i in 0 until chapterCount) {
                val internalId = parentId + (i * 1_000_000L) + 1
                val mapping = EpubChapterMappingEntity(
                    internalChapterId = internalId,
                    parentChapterId = parentId,
                    epubFilePath = "/storage/emulated/0/epub/chapter_${parentId}_vol1.epub",
                    epubFileName = "Volume 1",
                    chapterIndex = i,
                    chapterTitle = "Chapter ${i + 1}: Complete Title",
                    createdAt = System.currentTimeMillis()
                )
                mappings.add(mapping)
            }
            
            // Batch insert
            dao.insertAll(mappings)
            
            // Retrieve all by parent ID
            val retrieved = dao.getByParentId(parentId)
            
            // Verify all mappings have complete data
            assertEquals(chapterCount, retrieved.size)
            retrieved.forEachIndexed { index, retrievedMapping ->
                val original = mappings[index]
                assertEquals(original.internalChapterId, retrievedMapping.internalChapterId)
                assertEquals(original.parentChapterId, retrievedMapping.parentChapterId)
                assertEquals(original.epubFilePath, retrievedMapping.epubFilePath)
                assertEquals(original.epubFileName, retrievedMapping.epubFileName)
                assertEquals(original.chapterIndex, retrievedMapping.chapterIndex)
                assertEquals(original.chapterTitle, retrievedMapping.chapterTitle)
                assertEquals(original.createdAt, retrievedMapping.createdAt)
            }
        }
    }
    
    @Test
    fun stringFieldsAreNotTruncatedOrCorrupted() = runBlocking {
        val dao = database.getEpubChapterMappingDao()
        
        repeat(100) {
            val parentId = Random.nextLong(1L, 999_999L)
            val chapterIndex = Random.nextInt(0, 999)
            val internalId = parentId + (chapterIndex * 1_000_000L) + 1
            
            // Create mapping with long strings
            val longPath = "/storage/emulated/0/Android/data/org.skepsun.kototoro/files/epub/" +
                    "chapter_${parentId}_volume_${Random.nextInt(1, 100)}_part_${Random.nextInt(1, 10)}.epub"
            val longFileName = "Volume ${Random.nextInt(1, 100)} - Part ${Random.nextInt(1, 10)} - " +
                    "Special Edition with Extra Content"
            val longTitle = "Chapter ${chapterIndex + 1}: A Very Long Title That Contains Many Words " +
                    "And Describes The Chapter In Great Detail Including Plot Points And Character Names"
            
            val mapping = EpubChapterMappingEntity(
                internalChapterId = internalId,
                parentChapterId = parentId,
                epubFilePath = longPath,
                epubFileName = longFileName,
                chapterIndex = chapterIndex,
                chapterTitle = longTitle,
                createdAt = System.currentTimeMillis()
            )
            
            // Insert
            dao.insert(mapping)
            
            // Retrieve
            val retrieved = dao.getById(internalId)
            
            // Verify strings are complete and not truncated
            assertNotNull(retrieved)
            assertEquals(longPath, retrieved!!.epubFilePath)
            assertEquals(longFileName, retrieved.epubFileName)
            assertEquals(longTitle, retrieved.chapterTitle)
        }
    }
}
