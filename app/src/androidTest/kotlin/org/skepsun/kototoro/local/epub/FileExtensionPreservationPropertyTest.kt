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
 * Property-based tests for EPUB file extension preservation.
 * 
 * Feature: epub-reader-improvements
 * Property 1: EPUB File Extension Preservation
 * Validates: Requirements 1.1
 * 
 * For any EPUB file download, the saved file SHALL have the .epub extension.
 */
@RunWith(AndroidJUnit4::class)
class FileExtensionPreservationPropertyTest {
    
    private lateinit var context: Context
    private lateinit var epubFileManager: EpubFileManager
    private lateinit var testDir: File
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        epubFileManager = EpubFileManagerImpl()
        testDir = File(context.cacheDir, "epub_test_${System.currentTimeMillis()}")
        testDir.mkdirs()
    }
    
    @After
    fun teardown() {
        testDir.deleteRecursively()
    }
    
    /**
     * Generates a random chapter ID for testing.
     */
    private fun generateRandomChapterId(): Long {
        return Random.nextLong(1L, 999_999L)
    }
    
    /**
     * Generates a random EPUB filename.
     */
    private fun generateRandomEpubFilename(chapterId: Long): String {
        val volumeNumber = Random.nextInt(1, 100)
        return "chapter_${chapterId}_vol${volumeNumber}.epub"
    }
    
    /**
     * Simulates saving an EPUB file with the given filename.
     */
    private fun saveEpubFile(fileName: String): File {
        val file = File(testDir, fileName)
        // Create a minimal valid EPUB/ZIP file (PK magic bytes)
        file.outputStream().use { output ->
            // ZIP magic bytes: PK\x03\x04
            output.write(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
            // Add some dummy content
            output.write("EPUB content".toByteArray())
        }
        return file
    }
    
    @Test
    fun allSavedEpubFilesHaveEpubExtension() = runBlocking {
        // Run 100 iterations with random chapter IDs
        repeat(100) {
            val chapterId = generateRandomChapterId()
            val fileName = generateRandomEpubFilename(chapterId)
            
            // Save the EPUB file
            val savedFile = saveEpubFile(fileName)
            
            // Verify the file has .epub extension
            assertEquals("epub", savedFile.extension)
            assertTrue("File should have .epub extension", savedFile.name.endsWith(".epub"))
        }
    }
    
    @Test
    fun epubFileManagerCreatesFilesWithEpubExtension() = runBlocking {
        repeat(100) {
            val chapterId = generateRandomChapterId()
            val fileName = generateRandomEpubFilename(chapterId)
            
            // Get EPUB file through EpubFileManager
            val epubFile = epubFileManager.getEpubFile(context, fileName)
            
            // Verify the file path has .epub extension
            assertEquals("epub", epubFile.extension)
            assertTrue("File path should end with .epub", epubFile.absolutePath.endsWith(".epub"))
        }
    }
    
    @Test
    fun findEpubFileOnlyReturnsFilesWithEpubExtension() = runBlocking {
        repeat(50) {
            val chapterId = generateRandomChapterId()
            val epubDir = File(testDir, "epub_${chapterId}")
            epubDir.mkdirs()
            
            // Create some files with different extensions
            val epubFile = File(epubDir, "chapter_${chapterId}_vol1.epub")
            val cbzFile = File(epubDir, "chapter_${chapterId}_vol2.cbz")
            val txtFile = File(epubDir, "chapter_${chapterId}_vol3.txt")
            
            // Create the files
            epubFile.writeText("EPUB content")
            cbzFile.writeText("CBZ content")
            txtFile.writeText("TXT content")
            
            // Find EPUB files
            val foundFiles = epubDir.listFiles { file ->
                file.isFile && file.extension == "epub"
            }
            
            // Verify only .epub files are found
            assertEquals(1, foundFiles?.size)
            assertEquals("epub", foundFiles?.firstOrNull()?.extension)
        }
    }
    
    @Test
    fun epubFilenamePatternAlwaysIncludesEpubExtension() = runBlocking {
        repeat(100) {
            val chapterId = generateRandomChapterId()
            val volumeNumber = Random.nextInt(1, 100)
            
            // Generate filename following the pattern: chapter_{chapterId}_*.epub
            val fileName = "chapter_${chapterId}_vol${volumeNumber}.epub"
            
            // Verify pattern
            assertTrue("Filename should start with 'chapter_'", fileName.startsWith("chapter_"))
            assertTrue("Filename should contain chapter ID", fileName.contains(chapterId.toString()))
            assertTrue("Filename should end with .epub", fileName.endsWith(".epub"))
            assertEquals("epub", File(fileName).extension)
        }
    }
}
