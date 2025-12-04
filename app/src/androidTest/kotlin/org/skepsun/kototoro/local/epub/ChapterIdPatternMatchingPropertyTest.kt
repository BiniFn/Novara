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
 * Property-based tests for chapter ID pattern matching.
 * 
 * Feature: epub-reader-improvements
 * Property 32: Chapter ID Pattern Matching
 * Validates: Requirements 9.2
 * 
 * For any chapter ID search, the system SHALL find files matching "chapter_{chapterId}_*.epub"
 */
@RunWith(AndroidJUnit4::class)
class ChapterIdPatternMatchingPropertyTest {
    
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
            if (file.name.startsWith("chapter_")) {
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
    
    @Test
    fun findEpubFileMatchesPatternWithChapterId() = runBlocking {
        repeat(100) {
            val chapterId = Random.nextLong(1L, 999_999L)
            val volumeNum = Random.nextInt(1, 20)
            val fileName = "chapter_${chapterId}_vol${volumeNum}.epub"
            
            // Create EPUB file
            createTestEpubFile(fileName)
            
            // Find file by chapter ID
            val foundFile = epubFileManager.findEpubFile(context, chapterId)
            
            // Verify file was found
            assertNotNull("File should be found for chapter ID $chapterId", foundFile)
            
            // Verify filename matches pattern
            assertTrue(
                "Found filename should match pattern chapter_{chapterId}_*.epub",
                foundFile!!.name.matches(Regex("chapter_${chapterId}_.*\\.epub"))
            )
            
            // Verify the chapter ID in the filename
            assertTrue(
                "Filename should contain chapter ID",
                foundFile.name.contains("chapter_${chapterId}_")
            )
        }
    }
    
    @Test
    fun patternMatchingWorksWithDifferentSuffixes() = runBlocking {
        val chapterId = Random.nextLong(1L, 999_999L)
        val suffixes = listOf(
            "vol1",
            "vol99",
            "volume_1",
            "part_2",
            "extra",
            "special",
            "bonus",
            "12345"
        )
        
        // Create files with different suffixes
        val createdFiles = suffixes.map { suffix ->
            createTestEpubFile("chapter_${chapterId}_${suffix}.epub")
        }
        
        // Find file by chapter ID (should find the first one)
        val foundFile = epubFileManager.findEpubFile(context, chapterId)
        
        // Verify a file was found
        assertNotNull("File should be found for chapter ID $chapterId", foundFile)
        
        // Verify it's one of the created files
        assertTrue(
            "Found file should be one of the created files",
            createdFiles.any { it.absolutePath == foundFile!!.absolutePath }
        )
        
        // Verify pattern matching
        assertTrue(
            "Found filename should match pattern",
            foundFile!!.name.matches(Regex("chapter_${chapterId}_.*\\.epub"))
        )
    }
    
    @Test
    fun patternMatchingReturnsNullForNonExistentChapterId() = runBlocking {
        repeat(100) {
            val nonExistentChapterId = Random.nextLong(1_000_000L, 9_999_999L)
            
            // Try to find file for non-existent chapter ID
            val foundFile = epubFileManager.findEpubFile(context, nonExistentChapterId)
            
            // Verify no file was found
            assertNull(
                "No file should be found for non-existent chapter ID $nonExistentChapterId",
                foundFile
            )
        }
    }
    
    @Test
    fun patternMatchingIgnoresFilesWithDifferentChapterId() = runBlocking {
        val chapterId1 = Random.nextLong(1L, 499_999L)
        val chapterId2 = Random.nextLong(500_000L, 999_999L)
        
        // Create files for both chapter IDs
        createTestEpubFile("chapter_${chapterId1}_vol1.epub")
        createTestEpubFile("chapter_${chapterId2}_vol1.epub")
        
        // Find file for first chapter ID
        val foundFile1 = epubFileManager.findEpubFile(context, chapterId1)
        assertNotNull("File should be found for chapter ID $chapterId1", foundFile1)
        assertTrue(
            "Found file should match chapter ID $chapterId1",
            foundFile1!!.name.contains("chapter_${chapterId1}_")
        )
        
        // Find file for second chapter ID
        val foundFile2 = epubFileManager.findEpubFile(context, chapterId2)
        assertNotNull("File should be found for chapter ID $chapterId2", foundFile2)
        assertTrue(
            "Found file should match chapter ID $chapterId2",
            foundFile2!!.name.contains("chapter_${chapterId2}_")
        )
        
        // Verify they are different files
        assertTrue(
            "Files for different chapter IDs should be different",
            foundFile1.absolutePath != foundFile2.absolutePath
        )
    }
    
    @Test
    fun patternMatchingRequiresExactChapterIdMatch() = runBlocking {
        val chapterId = 12345L
        
        // Create files with similar but different chapter IDs
        createTestEpubFile("chapter_12345_vol1.epub")
        createTestEpubFile("chapter_123456_vol1.epub")
        createTestEpubFile("chapter_1234_vol1.epub")
        
        // Find file for exact chapter ID
        val foundFile = epubFileManager.findEpubFile(context, chapterId)
        
        assertNotNull("File should be found for chapter ID $chapterId", foundFile)
        
        // Verify exact match (not partial)
        assertEquals(
            "Found file should have exact chapter ID match",
            "chapter_12345_vol1.epub",
            foundFile!!.name
        )
    }
    
    @Test
    fun patternMatchingIgnoresNonEpubFiles() = runBlocking {
        val chapterId = Random.nextLong(1L, 999_999L)
        val epubDir = epubFileManager.getEpubDirectory(context)
        
        // Create files with different extensions
        File(epubDir, "chapter_${chapterId}_vol1.txt").writeText("text file")
        File(epubDir, "chapter_${chapterId}_vol1.pdf").writeText("pdf file")
        File(epubDir, "chapter_${chapterId}_vol1.cbz").writeText("cbz file")
        
        // Try to find EPUB file
        val foundFile = epubFileManager.findEpubFile(context, chapterId)
        
        // Verify no file was found (only .epub files should match)
        assertNull(
            "No file should be found when only non-epub files exist",
            foundFile
        )
        
        // Now create an actual EPUB file
        createTestEpubFile("chapter_${chapterId}_vol1.epub")
        
        // Find file again
        val foundEpubFile = epubFileManager.findEpubFile(context, chapterId)
        
        // Verify EPUB file was found
        assertNotNull("EPUB file should be found", foundEpubFile)
        assertTrue(
            "Found file should have .epub extension",
            foundEpubFile!!.name.endsWith(".epub")
        )
    }
    
    @Test
    fun patternMatchingWorksWithIsEpubDownloaded() = runBlocking {
        repeat(100) {
            val chapterId = Random.nextLong(1L, 999_999L)
            
            // Initially, file should not be downloaded
            val isDownloadedBefore = epubFileManager.isEpubDownloaded(context, chapterId)
            assertTrue(
                "File should not be downloaded initially",
                !isDownloadedBefore
            )
            
            // Create EPUB file
            val fileName = "chapter_${chapterId}_vol${Random.nextInt(1, 10)}.epub"
            createTestEpubFile(fileName)
            
            // Now file should be downloaded
            val isDownloadedAfter = epubFileManager.isEpubDownloaded(context, chapterId)
            assertTrue(
                "File should be downloaded after creation",
                isDownloadedAfter
            )
        }
    }
    
    @Test
    fun patternMatchingFindsFirstMatchingFile() = runBlocking {
        val chapterId = Random.nextLong(1L, 999_999L)
        
        // Create multiple files with same chapter ID
        val file1 = createTestEpubFile("chapter_${chapterId}_vol1.epub")
        val file2 = createTestEpubFile("chapter_${chapterId}_vol2.epub")
        val file3 = createTestEpubFile("chapter_${chapterId}_vol3.epub")
        
        // Find file by chapter ID
        val foundFile = epubFileManager.findEpubFile(context, chapterId)
        
        // Verify a file was found
        assertNotNull("File should be found for chapter ID $chapterId", foundFile)
        
        // Verify it's one of the created files
        val createdFiles = listOf(file1, file2, file3)
        assertTrue(
            "Found file should be one of the created files",
            createdFiles.any { it.absolutePath == foundFile!!.absolutePath }
        )
    }
    
    @Test
    fun patternMatchingHandlesSpecialCharactersInSuffix() = runBlocking {
        val chapterId = Random.nextLong(1L, 999_999L)
        val specialSuffixes = listOf(
            "vol-1",
            "vol_1",
            "vol.1",
            "vol(1)",
            "vol[1]"
        )
        
        specialSuffixes.forEach { suffix ->
            val fileName = "chapter_${chapterId}_${suffix}.epub"
            createTestEpubFile(fileName)
            
            val foundFile = epubFileManager.findEpubFile(context, chapterId)
            
            assertNotNull(
                "File should be found for chapter ID $chapterId with suffix $suffix",
                foundFile
            )
            
            assertTrue(
                "Found filename should match pattern",
                foundFile!!.name.matches(Regex("chapter_${chapterId}_.*\\.epub"))
            )
            
            // Clean up for next iteration
            foundFile.delete()
        }
    }
}
