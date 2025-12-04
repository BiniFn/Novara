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
 * Property-based tests for EPUB directory path construction.
 * 
 * Feature: epub-reader-improvements
 * Property 31: EPUB Directory Path Construction
 * Validates: Requirements 9.1, 9.3
 * 
 * For any EPUB file storage, the path SHALL be {epubDirectory}/{filename}.epub
 */
@RunWith(AndroidJUnit4::class)
class DirectoryPathConstructionPropertyTest {
    
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
            if (file.name.startsWith("test_")) {
                file.delete()
            }
        }
    }
    
    @Test
    fun epubFilePathFollowsConstructionPattern() = runBlocking {
        // Run 100 iterations with random filenames
        repeat(100) {
            val chapterId = Random.nextLong(1L, 999_999L)
            val volumeNum = Random.nextInt(1, 20)
            val fileName = "test_chapter_${chapterId}_vol${volumeNum}.epub"
            
            // Get EPUB directory
            val epubDir = epubFileManager.getEpubDirectory(context)
            
            // Get EPUB file through manager
            val epubFile = epubFileManager.getEpubFile(context, fileName)
            
            // Verify path follows pattern: {epubDirectory}/{filename}.epub
            val expectedPath = File(epubDir, fileName).absolutePath
            assertEquals(
                "EPUB file path should follow pattern {epubDirectory}/{filename}.epub",
                expectedPath,
                epubFile.absolutePath
            )
            
            // Verify the file is directly in the EPUB directory (no subdirectories)
            assertEquals(
                "EPUB file should be directly in EPUB directory",
                epubDir.absolutePath,
                epubFile.parentFile?.absolutePath
            )
        }
    }
    
    @Test
    fun epubDirectoryUsesExternalStorageWhenAvailable() = runBlocking {
        val epubDir = epubFileManager.getEpubDirectory(context)
        
        // Check if external storage is available
        val externalDir = context.getExternalFilesDir("epub")
        
        if (externalDir != null && externalDir.exists()) {
            // If external storage is available, it should be used
            assertEquals(
                "EPUB directory should use external storage when available",
                externalDir.absolutePath,
                epubDir.absolutePath
            )
        } else {
            // Otherwise, internal storage should be used
            val internalDir = File(context.filesDir, "epub")
            assertEquals(
                "EPUB directory should use internal storage as fallback",
                internalDir.absolutePath,
                epubDir.absolutePath
            )
        }
    }
    
    @Test
    fun epubDirectoryPathContainsEpubSubdirectory() = runBlocking {
        repeat(100) {
            val epubDir = epubFileManager.getEpubDirectory(context)
            
            // Verify the path ends with "epub" directory
            assertTrue(
                "EPUB directory path should contain 'epub' subdirectory",
                epubDir.absolutePath.endsWith("/epub") || epubDir.absolutePath.endsWith("\\epub")
            )
            
            // Verify the directory name is "epub"
            assertEquals(
                "EPUB directory name should be 'epub'",
                "epub",
                epubDir.name
            )
        }
    }
    
    @Test
    fun constructedPathsAreConsistentAcrossMultipleCalls() = runBlocking {
        val fileName = "test_consistent_file.epub"
        val paths = mutableSetOf<String>()
        
        repeat(100) {
            val epubFile = epubFileManager.getEpubFile(context, fileName)
            paths.add(epubFile.absolutePath)
        }
        
        // All calls should return the same path
        assertEquals(
            "Constructed paths should be consistent across multiple calls",
            1,
            paths.size
        )
    }
    
    @Test
    fun pathConstructionWorksWithVariousFilenames() = runBlocking {
        val testFilenames = listOf(
            "chapter_123_vol1.epub",
            "chapter_999999_vol99.epub",
            "chapter_1_vol1.epub",
            "chapter_500000_vol10.epub",
            "test_special_chars_123.epub",
            "test_long_filename_with_many_characters_12345.epub"
        )
        
        val epubDir = epubFileManager.getEpubDirectory(context)
        
        testFilenames.forEach { fileName ->
            val epubFile = epubFileManager.getEpubFile(context, fileName)
            val expectedPath = File(epubDir, fileName).absolutePath
            
            assertEquals(
                "Path construction should work for filename: $fileName",
                expectedPath,
                epubFile.absolutePath
            )
        }
    }
    
    @Test
    fun epubDirectoryIsCreatedIfNotExists() = runBlocking {
        repeat(100) {
            val epubDir = epubFileManager.getEpubDirectory(context)
            
            // Verify directory exists or was created
            assertTrue(
                "EPUB directory should exist or be created",
                epubDir.exists()
            )
            
            assertTrue(
                "EPUB directory should be a directory",
                epubDir.isDirectory
            )
        }
    }
    
    @Test
    fun pathConstructionPreservesFilenameExactly() = runBlocking {
        repeat(100) {
            val chapterId = Random.nextLong(1L, 999_999L)
            val volumeNum = Random.nextInt(1, 20)
            val fileName = "test_chapter_${chapterId}_vol${volumeNum}.epub"
            
            val epubFile = epubFileManager.getEpubFile(context, fileName)
            
            // Verify the filename is preserved exactly
            assertEquals(
                "Filename should be preserved exactly in path construction",
                fileName,
                epubFile.name
            )
        }
    }
    
    @Test
    fun allConstructedPathsPointToSameDirectory() = runBlocking {
        val directories = mutableSetOf<String>()
        
        repeat(100) {
            val chapterId = Random.nextLong(1L, 999_999L)
            val fileName = "test_chapter_${chapterId}_vol1.epub"
            
            val epubFile = epubFileManager.getEpubFile(context, fileName)
            directories.add(epubFile.parentFile?.absolutePath ?: "")
        }
        
        // All files should be in the same directory
        assertEquals(
            "All constructed paths should point to the same EPUB directory",
            1,
            directories.size
        )
    }
}
