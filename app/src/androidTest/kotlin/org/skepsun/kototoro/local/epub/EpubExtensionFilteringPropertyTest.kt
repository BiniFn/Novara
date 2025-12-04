package org.skepsun.kototoro.local.epub

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.random.Random

/**
 * Property-based tests for EPUB extension filtering.
 * 
 * Feature: epub-reader-improvements
 * Property 33: EPUB Extension Filtering
 * Validates: Requirements 9.5
 * 
 * For any file listing operation, only files with .epub extension SHALL be returned
 */
@RunWith(AndroidJUnit4::class)
class EpubExtensionFilteringPropertyTest {
    
    private lateinit var context: Context
    private lateinit var epubFileManager: EpubFileManager
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        epubFileManager = EpubFileManagerImpl()
    }
    
    @After
    fun teardown() {
        // Clean up all test files
        val epubDir = epubFileManager.getEpubDirectory(context)
        epubDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("test_") || file.name.startsWith("chapter_")) {
                file.delete()
            }
        }
    }
    
    /**
     * Creates a test EPUB file with the given name.
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
    
    /**
     * Creates a test file with the given name and extension.
     */
    private fun createTestFile(fileName: String, content: String = "test content"): File {
        val epubDir = epubFileManager.getEpubDirectory(context)
        val file = File(epubDir, fileName)
        file.writeText(content)
        return file
    }
    
    @Test
    fun findEpubFileOnlyReturnsEpubExtension() = runBlocking {
        repeat(100) {
            val chapterId = Random.nextLong(1L, 999_999L)
            val epubDir = epubFileManager.getEpubDirectory(context)
            
            // Create files with different extensions
            createTestFile("chapter_${chapterId}_vol1.txt", "text file")
            createTestFile("chapter_${chapterId}_vol1.pdf", "pdf file")
            createTestFile("chapter_${chapterId}_vol1.cbz", "cbz file")
            createTestFile("chapter_${chapterId}_vol1.zip", "zip file")
            
            // Try to find file (should not find non-epub files)
            val foundFile = epubFileManager.findEpubFile(context, chapterId)
            assertNull(
                "No file should be found when only non-epub files exist",
                foundFile
            )
            
            // Create EPUB file
            createTestEpubFile("chapter_${chapterId}_vol1.epub")
            
            // Now find file (should find epub file)
            val foundEpubFile = epubFileManager.findEpubFile(context, chapterId)
            assertNotNull("EPUB file should be found", foundEpubFile)
            assertTrue(
                "Found file should have .epub extension",
                foundEpubFile!!.extension == "epub"
            )
            
            // Clean up
            epubDir.listFiles()?.forEach { file ->
                if (file.name.contains("chapter_${chapterId}_")) {
                    file.delete()
                }
            }
        }
    }
    
    @Test
    fun extensionFilteringIgnoresCaseVariations() = runBlocking {
        val chapterId = Random.nextLong(1L, 999_999L)
        val epubDir = epubFileManager.getEpubDirectory(context)
        
        // Create files with different case variations (should not match)
        createTestFile("chapter_${chapterId}_vol1.EPUB", "uppercase")
        createTestFile("chapter_${chapterId}_vol2.Epub", "mixed case")
        createTestFile("chapter_${chapterId}_vol3.ePub", "mixed case 2")
        
        // Create proper lowercase .epub file
        createTestEpubFile("chapter_${chapterId}_vol4.epub")
        
        // Find file
        val foundFile = epubFileManager.findEpubFile(context, chapterId)
        
        // Verify only lowercase .epub is found
        assertNotNull("EPUB file should be found", foundFile)
        assertEquals(
            "Found file should have lowercase .epub extension",
            "epub",
            foundFile!!.extension
        )
    }
    
    @Test
    fun extensionFilteringRejectsFilesWithoutExtension() = runBlocking {
        val chapterId = Random.nextLong(1L, 999_999L)
        val epubDir = epubFileManager.getEpubDirectory(context)
        
        // Create file without extension
        createTestFile("chapter_${chapterId}_vol1", "no extension")
        
        // Try to find file
        val foundFile = epubFileManager.findEpubFile(context, chapterId)
        
        // Verify no file was found
        assertNull(
            "No file should be found for files without extension",
            foundFile
        )
    }
    
    @Test
    fun extensionFilteringRejectsPartialMatches() = runBlocking {
        val chapterId = Random.nextLong(1L, 999_999L)
        val epubDir = epubFileManager.getEpubDirectory(context)
        
        // Create files with partial matches
        createTestFile("chapter_${chapterId}_vol1.epub.txt", "epub.txt")
        createTestFile("chapter_${chapterId}_vol2.epub.bak", "epub.bak")
        createTestFile("chapter_${chapterId}_vol3.epub_old", "epub_old")
        createTestFile("chapter_${chapterId}_vol4.notepub", "notepub")
        
        // Try to find file
        val foundFile = epubFileManager.findEpubFile(context, chapterId)
        
        // Verify no file was found
        assertNull(
            "No file should be found for partial extension matches",
            foundFile
        )
    }
    
    @Test
    fun extensionFilteringWorksWithMultipleEpubFiles() = runBlocking {
        repeat(50) {
            val chapterId = Random.nextLong(1L, 999_999L)
            val epubDir = epubFileManager.getEpubDirectory(context)
            
            // Create multiple EPUB files
            val epubFiles = (1..5).map { volNum ->
                createTestEpubFile("chapter_${chapterId}_vol${volNum}.epub")
            }
            
            // Create non-EPUB files
            createTestFile("chapter_${chapterId}_vol6.txt", "text")
            createTestFile("chapter_${chapterId}_vol7.pdf", "pdf")
            
            // Find file
            val foundFile = epubFileManager.findEpubFile(context, chapterId)
            
            // Verify an EPUB file was found
            assertNotNull("EPUB file should be found", foundFile)
            assertTrue(
                "Found file should be one of the EPUB files",
                epubFiles.any { it.absolutePath == foundFile!!.absolutePath }
            )
            assertTrue(
                "Found file should have .epub extension",
                foundFile!!.extension == "epub"
            )
            
            // Clean up
            epubDir.listFiles()?.forEach { file ->
                if (file.name.contains("chapter_${chapterId}_")) {
                    file.delete()
                }
            }
        }
    }
    
    @Test
    fun extensionFilteringConsistentAcrossMultipleCalls() = runBlocking {
        val chapterId = Random.nextLong(1L, 999_999L)
        
        // Create EPUB file
        createTestEpubFile("chapter_${chapterId}_vol1.epub")
        
        // Create non-EPUB files
        createTestFile("chapter_${chapterId}_vol2.txt", "text")
        createTestFile("chapter_${chapterId}_vol3.pdf", "pdf")
        
        // Find file multiple times
        val foundFiles = (1..100).map {
            epubFileManager.findEpubFile(context, chapterId)
        }
        
        // Verify all calls found a file
        assertTrue(
            "All calls should find a file",
            foundFiles.all { it != null }
        )
        
        // Verify all calls found the same file
        val uniquePaths = foundFiles.map { it!!.absolutePath }.toSet()
        assertEquals(
            "All calls should find the same file",
            1,
            uniquePaths.size
        )
        
        // Verify all found files have .epub extension
        assertTrue(
            "All found files should have .epub extension",
            foundFiles.all { it!!.extension == "epub" }
        )
    }
    
    @Test
    fun extensionFilteringWorksWithIsEpubDownloaded() = runBlocking {
        repeat(100) {
            val chapterId = Random.nextLong(1L, 999_999L)
            val epubDir = epubFileManager.getEpubDirectory(context)
            
            // Create non-EPUB files
            createTestFile("chapter_${chapterId}_vol1.txt", "text")
            createTestFile("chapter_${chapterId}_vol1.pdf", "pdf")
            
            // Check if downloaded (should be false)
            val isDownloadedBefore = epubFileManager.isEpubDownloaded(context, chapterId)
            assertTrue(
                "File should not be considered downloaded with only non-epub files",
                !isDownloadedBefore
            )
            
            // Create EPUB file
            createTestEpubFile("chapter_${chapterId}_vol1.epub")
            
            // Check if downloaded (should be true)
            val isDownloadedAfter = epubFileManager.isEpubDownloaded(context, chapterId)
            assertTrue(
                "File should be considered downloaded with epub file",
                isDownloadedAfter
            )
            
            // Clean up
            epubDir.listFiles()?.forEach { file ->
                if (file.name.contains("chapter_${chapterId}_")) {
                    file.delete()
                }
            }
        }
    }
    
    @Test
    fun extensionFilteringHandlesEmptyDirectory() = runBlocking {
        repeat(100) {
            val chapterId = Random.nextLong(1_000_000L, 9_999_999L)
            
            // Try to find file in empty directory
            val foundFile = epubFileManager.findEpubFile(context, chapterId)
            
            // Verify no file was found
            assertNull(
                "No file should be found in empty directory",
                foundFile
            )
        }
    }
    
    @Test
    fun extensionFilteringOnlyMatchesExactEpubExtension() = runBlocking {
        val chapterId = Random.nextLong(1L, 999_999L)
        val epubDir = epubFileManager.getEpubDirectory(context)
        
        // Create files with similar but different extensions
        val nonMatchingExtensions = listOf(
            "epubx",
            "epub2",
            "epub3",
            "epubb",
            "aepub",
            "epub_",
            "_epub"
        )
        
        nonMatchingExtensions.forEach { ext ->
            createTestFile("chapter_${chapterId}_vol1.${ext}", "content")
        }
        
        // Try to find file
        val foundFile = epubFileManager.findEpubFile(context, chapterId)
        
        // Verify no file was found
        assertNull(
            "No file should be found for non-exact extension matches",
            foundFile
        )
        
        // Create proper EPUB file
        createTestEpubFile("chapter_${chapterId}_vol1.epub")
        
        // Find file again
        val foundEpubFile = epubFileManager.findEpubFile(context, chapterId)
        
        // Verify EPUB file was found
        assertNotNull("EPUB file should be found", foundEpubFile)
        assertEquals(
            "Found file should have exact 'epub' extension",
            "epub",
            foundEpubFile!!.extension
        )
    }
    
    @Test
    fun extensionFilteringWorksWithVariousChapterIds() = runBlocking {
        val chapterIds = listOf(
            1L,
            999L,
            1000L,
            99999L,
            100000L,
            999999L
        )
        
        chapterIds.forEach { chapterId ->
            // Create EPUB file
            createTestEpubFile("chapter_${chapterId}_vol1.epub")
            
            // Create non-EPUB files
            createTestFile("chapter_${chapterId}_vol1.txt", "text")
            
            // Find file
            val foundFile = epubFileManager.findEpubFile(context, chapterId)
            
            // Verify EPUB file was found
            assertNotNull("EPUB file should be found for chapter ID $chapterId", foundFile)
            assertEquals(
                "Found file should have .epub extension",
                "epub",
                foundFile!!.extension
            )
            
            // Clean up
            val epubDir = epubFileManager.getEpubDirectory(context)
            epubDir.listFiles()?.forEach { file ->
                if (file.name.contains("chapter_${chapterId}_")) {
                    file.delete()
                }
            }
        }
    }
}
