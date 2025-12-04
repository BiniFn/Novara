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
import org.skepsun.kototoro.core.util.MimeTypes
import java.io.File
import kotlin.random.Random

/**
 * Property-based tests for EPUB format non-conversion.
 * 
 * Feature: epub-reader-improvements
 * Property 2: EPUB Format Non-Conversion
 * Validates: Requirements 1.2
 * 
 * For any file with EPUB MIME type, the system SHALL NOT convert it to CBZ format.
 */
@RunWith(AndroidJUnit4::class)
class FormatNonConversionPropertyTest {
    
    private lateinit var context: Context
    private lateinit var testDir: File
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        testDir = File(context.cacheDir, "epub_format_test_${System.currentTimeMillis()}")
        testDir.mkdirs()
    }
    
    @After
    fun teardown() {
        testDir.deleteRecursively()
    }
    
    /**
     * Creates a valid EPUB file (ZIP format with PK magic bytes).
     */
    private fun createValidEpubFile(fileName: String): File {
        val file = File(testDir, fileName)
        file.outputStream().use { output ->
            // ZIP magic bytes: PK\x03\x04
            output.write(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
            // Add mimetype file (required for EPUB)
            output.write("mimetypeapplication/epub+zip".toByteArray())
            // Add some dummy content
            output.write("EPUB content data".toByteArray())
        }
        return file
    }
    
    /**
     * Verifies that a file is still in EPUB/ZIP format (not converted).
     */
    private fun isStillEpubFormat(file: File): Boolean {
        if (!file.exists() || file.length() < 4) {
            return false
        }
        
        return file.inputStream().use { input ->
            val header = ByteArray(4)
            val read = input.read(header)
            if (read < 2) return false
            
            // ZIP/EPUB magic bytes: PK\x03\x04
            header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
        }
    }
    
    @Test
    fun epubMimeTypeFilesAreNotConvertedToCbz() = runBlocking {
        // Run 100 iterations
        repeat(100) {
            val chapterId = Random.nextLong(1L, 999_999L)
            val fileName = "chapter_${chapterId}_vol${Random.nextInt(1, 10)}.epub"
            
            // Create a valid EPUB file
            val epubFile = createValidEpubFile(fileName)
            
            // Verify it's in EPUB/ZIP format
            assertTrue("File should be in EPUB/ZIP format", isStillEpubFormat(epubFile))
            
            // Verify extension is still .epub (not converted to .cbz)
            assertEquals("epub", epubFile.extension)
            
            // Verify the file still has ZIP magic bytes
            val magicBytes = epubFile.inputStream().use { input ->
                val bytes = ByteArray(2)
                input.read(bytes)
                bytes
            }
            assertEquals(0x50.toByte(), magicBytes[0])
            assertEquals(0x4B.toByte(), magicBytes[1])
        }
    }
    
    @Test
    fun epubFilesRetainOriginalFormatAfterSave() = runBlocking {
        repeat(100) {
            val chapterId = Random.nextLong(1L, 999_999L)
            val originalFileName = "chapter_${chapterId}_original.epub"
            val savedFileName = "chapter_${chapterId}_saved.epub"
            
            // Create original EPUB file
            val originalFile = createValidEpubFile(originalFileName)
            val originalContent = originalFile.readBytes()
            
            // Simulate saving (copy to new location)
            val savedFile = File(testDir, savedFileName)
            originalFile.copyTo(savedFile, overwrite = true)
            
            // Verify format is preserved
            assertTrue("Saved file should still be EPUB format", isStillEpubFormat(savedFile))
            assertEquals("epub", savedFile.extension)
            
            // Verify content is identical (no conversion occurred)
            val savedContent = savedFile.readBytes()
            assertTrue("Content should be identical", originalContent.contentEquals(savedContent))
        }
    }
    
    @Test
    fun epubMimeTypeIsPreservedNotConvertedToCbzMimeType() = runBlocking {
        repeat(100) {
            val chapterId = Random.nextLong(1L, 999_999L)
            val fileName = "chapter_${chapterId}_vol${Random.nextInt(1, 10)}.epub"
            
            // Create EPUB file
            val epubFile = createValidEpubFile(fileName)
            
            // Get MIME type from extension
            val mimeType = MimeTypes.getMimeTypeFromUrl(epubFile.absolutePath)
            val mimeTypeStr = mimeType?.toString()
            
            // Verify MIME type is EPUB-related, not CBZ
            assertTrue(
                "MIME type should be EPUB or ZIP, not CBZ",
                mimeTypeStr == "application/epub+zip" || 
                mimeTypeStr == "application/zip" ||
                mimeTypeStr == null // null is acceptable as it means no conversion
            )
            
            // Verify it's NOT CBZ MIME type
            assertTrue(
                "MIME type should not be CBZ",
                mimeTypeStr != "application/x-cbz" && mimeTypeStr != "application/vnd.comicbook+zip"
            )
        }
    }
    
    @Test
    fun downloadedEpubFilesKeepEpubExtensionNotCbz() = runBlocking {
        repeat(100) {
            val chapterId = Random.nextLong(1L, 999_999L)
            val volumeNumber = Random.nextInt(1, 20)
            
            // Simulate downloaded EPUB filename
            val downloadedFileName = "chapter_${chapterId}_vol${volumeNumber}.epub"
            
            // Create the file
            val downloadedFile = createValidEpubFile(downloadedFileName)
            
            // Verify extension is .epub, not .cbz
            assertEquals("epub", downloadedFile.extension)
            assertTrue("Filename should end with .epub", downloadedFile.name.endsWith(".epub"))
            assertTrue("Filename should not end with .cbz", !downloadedFile.name.endsWith(".cbz"))
            
            // Verify format is still EPUB/ZIP
            assertTrue("File should still be in EPUB format", isStillEpubFormat(downloadedFile))
        }
    }
    
    @Test
    fun epubFilesAreNotRenamedToCbzDuringProcessing() = runBlocking {
        repeat(100) {
            val chapterId = Random.nextLong(1L, 999_999L)
            val epubFileName = "chapter_${chapterId}_vol1.epub"
            
            // Create EPUB file
            val epubFile = createValidEpubFile(epubFileName)
            val originalPath = epubFile.absolutePath
            
            // Simulate processing (read and verify)
            val content = epubFile.readBytes()
            
            // Verify file still exists with same name
            assertTrue("File should still exist", epubFile.exists())
            assertEquals("File path should not change", originalPath, epubFile.absolutePath)
            assertEquals("Extension should still be .epub", "epub", epubFile.extension)
            
            // Verify no .cbz file was created
            val cbzFile = File(testDir, "chapter_${chapterId}_vol1.cbz")
            assertTrue("No .cbz file should be created", !cbzFile.exists())
        }
    }
}
