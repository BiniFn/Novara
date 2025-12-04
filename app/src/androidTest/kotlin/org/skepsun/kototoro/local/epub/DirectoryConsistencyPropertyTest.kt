package org.skepsun.kototoro.local.epub

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.random.Random

/**
 * Property-based tests for EPUB directory consistency.
 * 
 * Feature: epub-reader-improvements
 * Property 3: EPUB Directory Consistency
 * Validates: Requirements 1.3
 * 
 * For any EPUB file storage operation, the file SHALL be stored in the dedicated EPUB directory.
 */
@RunWith(AndroidJUnit4::class)
class DirectoryConsistencyPropertyTest {
    
    private lateinit var context: Context
    private lateinit var epubFileManager: EpubFileManager
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        epubFileManager = EpubFileManagerImpl()
    }
    
    @After
    fun teardown() {
        // Clean up test files
        val epubDir = epubFileManager.getEpubDirectory(context)
        epubDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("test_chapter_")) {
                file.delete()
            }
        }
    }
    
    /**
     * Creates a test EPUB file in the EPUB directory.
     */
    private fun createTestEpubFile(fileName: String): File {
        val epubDir = epubFileManager.getEpubDirectory(context)
        val file = File(epubDir, fileName)
        file.outputStream().use { output ->
            // ZIP magic bytes: PK\x03\x04
            output.write(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
            output.write("Test EPUB content".toByteArray())
        }
        return file
    }
    
    @Test
    fun allEpubFilesAreStoredInDedicatedEpubDirectory() = runBlocking {
        val epubDir = epubFileManager.getEpubDirectory(context)
        
        // Run 100 iterations
        repeat(100) {
            val chapterId = Random.nextLong(1L, 999_999L)
            val fileName = "test_chapter_${chapterId}_vol${Random.nextInt(1, 10)}.epub"
            
            // Get file through EpubFileManager
            val epubFile = epubFileManager.getEpubFile(context, fileName)
            
            // Verify the file is in the EPUB directory
            assertEquals(
                "File should be in EPUB directory",
                epubDir.absolutePath,
                epubFile.parentFile?.absolutePath
            )
            
            // Verify the path contains "epub" directory
            assertTrue(
                "File path should contain 'epub' directory",
                epubFile.absolutePath.contains("/epub/")
            )
        }
    }
    
    @Test
    fun epubDirectoryIsConsistentAcrossMultipleCalls() = runBlocking {
        // Get EPUB directory multiple times
        val directories = mutableSetOf<String>()
        
        repeat(100) {
            val epubDir = epubFileManager.getEpubDirectory(context)
            directories.add(epubDir.absolutePath)
        }
        
        // Verify all calls return the same directory
        assertEquals(
            "EPUB directory should be consistent across calls",
            1,
            directories.size
        )
    }
    
    @Test
    fun createdEpubFilesExistInEpubDirectory() = runBlocking {
        val epubDir = epubFileManager.getEpubDirectory(context)
        
        repeat(50) {
            val chapterId = Random.nextLong(1L, 999_999L)
            val fileName = "test_chapter_${chapterId}_vol1.epub"
            
            // Create EPUB file
            val createdFile = createTestEpubFile(fileName)
            
            // Verify file exists in EPUB directory
            assertTrue("File should exist", createdFile.exists())
            assertEquals(
                "File should be in EPUB directory",
                epubDir.absolutePath,
                createdFile.parentFile?.absolutePath
            )
            
            // Verify we can find it through the directory
            val foundFile = File(epubDir, fileName)
            assertTrue("File should be findable in EPUB directory", foundFile.exists())
            assertEquals(
                "Found file should be the same as created file",
                createdFile.absolutePath,
                foundFile.absolutePath
            )
        }
    }
    
    @Test
    fun findEpubFileSearchesInEpubDirectory() = runBlocking {
        val epubDir = epubFileManager.getEpubDirectory(context)
        
        repeat(50) {
            val chapterId = Random.nextLong(1L, 999_999L)
            val fileName = "test_chapter_${chapterId}_vol1.epub"
            
            // Create EPUB file in EPUB directory
            createTestEpubFile(fileName)
            
            // Find the file by chapter ID
            val foundFile = epubFileManager.findEpubFile(context, chapterId)
            
            // Verify file was found and is in EPUB directory
            assertTrue("File should be found", foundFile != null)
            assertEquals(
                "Found file should be in EPUB directory",
                epubDir.absolutePath,
                foundFile?.parentFile?.absolutePath
            )
        }
    }
    
    @Test
    fun epubDirectoryPathConstructionIsConsistent() = runBlocking {
        repeat(100) {
            val chapterId = Random.nextLong(1L, 999_999L)
            val fileName = "test_chapter_${chapterId}_vol1.epub"
            
            // Get file through EpubFileManager
            val epubFile = epubFileManager.getEpubFile(context, fileName)
            
            // Get EPUB directory
            val epubDir = epubFileManager.getEpubDirectory(context)
            
            // Verify path construction: {epubDirectory}/{filename}.epub
            val expectedPath = File(epubDir, fileName).absolutePath
            assertEquals(
                "File path should follow pattern {epubDirectory}/{filename}.epub",
                expectedPath,
                epubFile.absolutePath
            )
        }
    }
    
    @Test
    fun epubDirectoryExistsOrIsCreated() = runBlocking {
        repeat(100) {
            val epubDir = epubFileManager.getEpubDirectory(context)
            
            // Verify directory exists
            assertTrue("EPUB directory should exist", epubDir.exists())
            assertTrue("EPUB directory should be a directory", epubDir.isDirectory)
            
            // Verify we can write to it
            assertTrue("EPUB directory should be writable", epubDir.canWrite())
        }
    }
    
    @Test
    fun allEpubOperationsUseTheSameDirectory() = runBlocking {
        val directories = mutableSetOf<String>()
        
        repeat(50) {
            val chapterId = Random.nextLong(1L, 999_999L)
            val fileName = "test_chapter_${chapterId}_vol1.epub"
            
            // Get directory through different operations
            val dir1 = epubFileManager.getEpubDirectory(context)
            val file = epubFileManager.getEpubFile(context, fileName)
            val dir2 = file.parentFile
            
            // Create a file and find it
            createTestEpubFile(fileName)
            val foundFile = epubFileManager.findEpubFile(context, chapterId)
            val dir3 = foundFile?.parentFile
            
            // Collect all directories
            directories.add(dir1.absolutePath)
            if (dir2 != null) directories.add(dir2.absolutePath)
            if (dir3 != null) directories.add(dir3.absolutePath)
        }
        
        // Verify all operations use the same directory
        assertEquals(
            "All EPUB operations should use the same directory",
            1,
            directories.size
        )
    }
}
