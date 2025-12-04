package org.skepsun.kototoro.reader.novel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.skepsun.kototoro.local.epub.EpubFileManager
import org.skepsun.kototoro.local.epub.EpubFileManagerImpl
import java.io.File
import kotlin.random.Random

/**
 * Property-based tests for parent ID file location.
 * 
 * Feature: epub-reader-improvements
 * Property 23: Parent ID File Location
 * Validates: Requirements 6.5
 * 
 * For any valid parent chapter ID, the system SHALL locate 
 * the corresponding EPUB file if it exists.
 */
@RunWith(AndroidJUnit4::class)
class ParentIdFileLocationPropertyTest {
    
    private lateinit var context: Context
    private lateinit var fileManager: EpubFileManager
    private val createdFiles = mutableListOf<File>()
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        fileManager = EpubFileManagerImpl()
    }
    
    @After
    fun teardown() {
        // Clean up created test files
        createdFiles.forEach { file ->
            if (file.exists()) {
                file.delete()
            }
        }
        createdFiles.clear()
    }
    
    /**
     * Property 23: Parent ID File Location
     * 
     * For any parent chapter ID with a corresponding EPUB file,
     * findEpubFile should locate the file.
     */
    @Test
    fun findEpubFileLocatesFileByParentId() {
        // Run 50 iterations with random parent IDs
        repeat(50) {
            val parentId = Random.nextLong(1L, 999_999L)
            val volumeNumber = Random.nextInt(1, 20)
            
            // Create a test EPUB file with the expected naming pattern
            val fileName = "chapter_${parentId}_vol${volumeNumber}.epub"
            val file = createTestEpubFile(fileName)
            
            // Try to find the file by parent ID
            val foundFile = fileManager.findEpubFile(context, parentId)
            
            // Verify the file was found
            assertNotNull("File should be found for parent ID $parentId", foundFile)
            assertEquals("Found file should match created file", file.absolutePath, foundFile?.absolutePath)
            assertTrue("Found file should exist", foundFile?.exists() == true)
        }
    }
    
    /**
     * Verify that the file manager can find files with different volume numbers.
     */
    @Test
    fun findEpubFileWorksWithDifferentVolumeNumbers() {
        repeat(30) {
            val parentId = Random.nextLong(1L, 999_999L)
            val volumeNumber = Random.nextInt(1, 100)
            
            val fileName = "chapter_${parentId}_vol${volumeNumber}.epub"
            val file = createTestEpubFile(fileName)
            
            val foundFile = fileManager.findEpubFile(context, parentId)
            
            assertNotNull(foundFile)
            assertEquals(file.absolutePath, foundFile?.absolutePath)
        }
    }
    
    /**
     * Verify that the file manager finds the first matching file when multiple exist.
     */
    @Test
    fun findEpubFileReturnsFirstMatchWhenMultipleExist() {
        repeat(20) {
            val parentId = Random.nextLong(1L, 999_999L)
            
            // Create multiple files with the same parent ID
            val file1 = createTestEpubFile("chapter_${parentId}_vol1.epub")
            val file2 = createTestEpubFile("chapter_${parentId}_vol2.epub")
            val file3 = createTestEpubFile("chapter_${parentId}_vol3.epub")
            
            val foundFile = fileManager.findEpubFile(context, parentId)
            
            assertNotNull(foundFile)
            // Should find one of the files (implementation may vary on which one)
            val matchesAny = foundFile?.absolutePath in listOf(
                file1.absolutePath,
                file2.absolutePath,
                file3.absolutePath
            )
            assertTrue("Found file should match one of the created files", matchesAny)
        }
    }
    
    /**
     * Verify that isEpubDownloaded correctly identifies downloaded files.
     */
    @Test
    fun isEpubDownloadedReturnsTrueForExistingFiles() {
        repeat(50) {
            val parentId = Random.nextLong(1L, 999_999L)
            val volumeNumber = Random.nextInt(1, 20)
            
            // Initially, file should not exist
            val initialCheck = fileManager.isEpubDownloaded(context, parentId)
            assertEquals("File should not exist initially", false, initialCheck)
            
            // Create the file
            val fileName = "chapter_${parentId}_vol${volumeNumber}.epub"
            createTestEpubFile(fileName)
            
            // Now file should be found
            val afterCreation = fileManager.isEpubDownloaded(context, parentId)
            assertEquals("File should exist after creation", true, afterCreation)
        }
    }
    
    /**
     * Verify that findEpubFile returns null for non-existent files.
     */
    @Test
    fun findEpubFileReturnsNullForNonExistentFiles() {
        repeat(50) {
            // Use a random parent ID that we haven't created files for
            val parentId = Random.nextLong(10_000_000L, 99_999_999L)
            
            val foundFile = fileManager.findEpubFile(context, parentId)
            
            // Should not find any file
            assertEquals("Should not find file for non-existent parent ID", null, foundFile)
        }
    }
    
    /**
     * Verify that file location works with the pattern matching.
     */
    @Test
    fun fileLocationUsesCorrectPatternMatching() {
        repeat(30) {
            val parentId = Random.nextLong(1L, 999_999L)
            
            // Create files with various suffixes
            val validFile = createTestEpubFile("chapter_${parentId}_volume1.epub")
            
            // Create files that should NOT match
            val wrongExtension = createTestEpubFile("chapter_${parentId}_vol1.txt")
            val wrongPrefix = createTestEpubFile("chap_${parentId}_vol1.epub")
            val wrongId = createTestEpubFile("chapter_${parentId + 1}_vol1.epub")
            
            val foundFile = fileManager.findEpubFile(context, parentId)
            
            assertNotNull("Should find the valid file", foundFile)
            assertEquals("Should find the correct file", validFile.absolutePath, foundFile?.absolutePath)
        }
    }
    
    /**
     * Verify that getEpubFile constructs correct file paths.
     */
    @Test
    fun getEpubFileConstructsCorrectPath() {
        repeat(50) {
            val parentId = Random.nextLong(1L, 999_999L)
            val volumeNumber = Random.nextInt(1, 20)
            val fileName = "chapter_${parentId}_vol${volumeNumber}.epub"
            
            val file = fileManager.getEpubFile(context, fileName)
            
            // Verify the file path contains the epub directory
            assertTrue("File path should contain 'epub'", file.absolutePath.contains("epub"))
            
            // Verify the file name is correct
            assertEquals("File name should match", fileName, file.name)
            
            // Verify the extension is .epub
            assertEquals("Extension should be .epub", "epub", file.extension)
        }
    }
    
    /**
     * Creates a test EPUB file in the epub directory.
     */
    private fun createTestEpubFile(fileName: String): File {
        val epubDir = fileManager.getEpubDirectory(context)
        if (!epubDir.exists()) {
            epubDir.mkdirs()
        }
        
        val file = File(epubDir, fileName)
        file.writeText("Test EPUB content")
        
        createdFiles.add(file)
        return file
    }
}
