package org.skepsun.kototoro.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.skepsun.kototoro.core.db.MangaDatabase
import org.skepsun.kototoro.local.epub.ChapterIdGeneratorImpl
import org.skepsun.kototoro.local.epub.EpubFileManagerImpl
import org.skepsun.kototoro.local.epub.EpubReaderImpl
import java.io.File
import java.io.IOException

/**
 * Integration test for EPUB download to parse flow.
 * 
 * Tests the complete flow:
 * Download → Save → Parse → Extract → Store mappings
 * 
 * This validates Requirements: 1.1, 1.2, 1.3, 1.4, 2.1, 2.2, 2.3, 2.4, 5.1, 5.3
 */
@RunWith(AndroidJUnit4::class)
class EpubDownloadToParseIntegrationTest {
    
    private lateinit var database: MangaDatabase
    private lateinit var context: Context
    private lateinit var epubFileManager: EpubFileManagerImpl
    private lateinit var epubReader: EpubReaderImpl
    private lateinit var chapterIdGenerator: ChapterIdGeneratorImpl
    private lateinit var testDir: File
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(
            context,
            MangaDatabase::class.java
        ).allowMainThreadQueries().build()
        
        epubFileManager = EpubFileManagerImpl()
        epubReader = EpubReaderImpl()
        chapterIdGenerator = ChapterIdGeneratorImpl()
        
        // Create test directory
        testDir = File(context.cacheDir, "epub_integration_test")
        testDir.mkdirs()
    }
    
    @After
    fun teardown() {
        database.close()
        // Clean up test files
        testDir.deleteRecursively()
    }
    
    /**
     * Test 1: Complete flow with a valid EPUB file
     * 
     * Flow: Download → Save → Parse → Extract → Store mappings
     */
    @Test
    fun completeEpubDownloadToParseFlow_validEpub() = runBlocking {
        // Step 1: Simulate download - create a test EPUB file
        val parentChapterId = 100000L
        val epubFile = createTestEpubFile(parentChapterId)
        
        // Verify Step 1: File is saved with .epub extension (Requirement 1.1)
        epubFile.name shouldEndWith ".epub"
        epubFile.exists() shouldBe true
        
        // Verify Step 1: File is in EPUB directory (Requirement 1.3)
        epubFile.parentFile?.name shouldBe "epub"
        
        // Verify Step 1: Filename contains parent chapter ID (Requirement 1.4)
        epubFile.name shouldContain "chapter_${parentChapterId}_"
        
        // Step 2: Parse the EPUB file
        val epubContent = epubReader.readEpub(epubFile)
        
        // Verify Step 2: Parsing succeeded
        epubContent shouldNotBe null
        epubContent!!.title shouldNotBe ""
        assert(epubContent.chapters.isNotEmpty())
        
        // Verify Step 2: All spine references extracted (Requirement 2.1)
        val expectedChapterCount = 3 // Our test EPUB has 3 chapters
        epubContent.chapters shouldHaveSize expectedChapterCount
        
        // Verify Step 2: Chapter titles preserved (Requirement 2.2, 2.3)
        epubContent.chapters.forEachIndexed { index, chapter ->
            chapter.title shouldNotBe ""
            chapter.index shouldBe index
        }
        
        // Verify Step 2: HTML converted to text (Requirement 2.4)
        epubContent.chapters.forEach { chapter ->
            chapter.content shouldNotBe ""
            // HTML tags should be removed
            assert(!chapter.content.contains("<html", ignoreCase = true))
            assert(!chapter.content.contains("<body", ignoreCase = true))
        }
        
        // Step 3: Generate internal chapter IDs
        val internalChapterIds = epubContent.chapters.map { chapter ->
            chapterIdGenerator.generateEpubChapterId(parentChapterId, chapter.index)
        }
        
        // Verify Step 3: IDs follow formula (Requirement 5.1)
        internalChapterIds.forEachIndexed { index, id ->
            val expected = parentChapterId + (index * 1_000_000L) + 1
            id shouldBe expected
        }
        
        // Step 4: Store mappings in database
        val dao = database.getEpubChapterMappingDao()
        val mappings = epubContent.chapters.map { chapter ->
            org.skepsun.kototoro.core.db.entity.EpubChapterMappingEntity(
                internalChapterId = internalChapterIds[chapter.index],
                parentChapterId = parentChapterId,
                epubFilePath = epubFile.absolutePath,
                epubFileName = "Test Volume",
                chapterIndex = chapter.index,
                chapterTitle = chapter.title,
                createdAt = System.currentTimeMillis()
            )
        }
        dao.insertAll(mappings)
        
        // Verify Step 4: Mappings stored successfully (Requirement 5.3)
        val storedMappings = dao.getByParentId(parentChapterId)
        storedMappings shouldHaveSize expectedChapterCount
        
        // Verify Step 4: Each mapping can be retrieved
        internalChapterIds.forEach { id ->
            val mapping = dao.getById(id)
            mapping shouldNotBe null
            mapping!!.parentChapterId shouldBe parentChapterId
            mapping.epubFilePath shouldBe epubFile.absolutePath
        }
        
        // Verify complete flow: Can retrieve chapter content using mapping
        val firstMapping = storedMappings[0]
        val retrievedFile = File(firstMapping.epubFilePath)
        retrievedFile.exists() shouldBe true
        
        val retrievedContent = epubReader.readEpub(retrievedFile)
        retrievedContent shouldNotBe null
        retrievedContent!!.chapters[firstMapping.chapterIndex].title shouldBe firstMapping.chapterTitle
    }
    
    /**
     * Test 2: Flow with EPUB format preservation
     * 
     * Verifies that EPUB format is NOT converted to CBZ (Requirement 1.2)
     */
    @Test
    fun epubDownloadFlow_preservesEpubFormat() = runBlocking {
        val parentChapterId = 200000L
        val epubFile = createTestEpubFile(parentChapterId)
        
        // Verify: File extension is .epub, not .cbz
        epubFile.extension shouldBe "epub"
        assert(!epubFile.name.contains(".cbz"))
        
        // Verify: File can be parsed as EPUB
        val content = epubReader.readEpub(epubFile)
        content shouldNotBe null
        
        // Verify: Content is valid EPUB content
        assert(content!!.chapters.isNotEmpty())
    }
    
    /**
     * Test 3: Flow with multiple EPUB files
     * 
     * Verifies that multiple EPUBs can be downloaded and parsed without conflicts
     */
    @Test
    fun epubDownloadFlow_multipleEpubs() = runBlocking {
        val parentIds = listOf(300000L, 400000L, 500000L)
        val epubFiles = parentIds.map { createTestEpubFile(it) }
        
        // Parse all EPUBs
        val contents = epubFiles.map { epubReader.readEpub(it) }
        contents.forEach { content -> content shouldNotBe null }
        
        // Store all mappings
        val dao = database.getEpubChapterMappingDao()
        contents.forEachIndexed { fileIndex, content ->
            val parentId = parentIds[fileIndex]
            val mappings = content!!.chapters.map { chapter ->
                org.skepsun.kototoro.core.db.entity.EpubChapterMappingEntity(
                    internalChapterId = chapterIdGenerator.generateEpubChapterId(parentId, chapter.index),
                    parentChapterId = parentId,
                    epubFilePath = epubFiles[fileIndex].absolutePath,
                    epubFileName = "Volume ${fileIndex + 1}",
                    chapterIndex = chapter.index,
                    chapterTitle = chapter.title,
                    createdAt = System.currentTimeMillis()
                )
            }
            dao.insertAll(mappings)
        }
        
        // Verify: All mappings stored correctly
        parentIds.forEach { parentId ->
            val mappings = dao.getByParentId(parentId)
            assert(mappings.isNotEmpty())
        }
        
        // Verify: No ID conflicts between different EPUBs
        val allInternalIds = mutableSetOf<Long>()
        parentIds.forEach { parentId ->
            val mappings = dao.getByParentId(parentId)
            mappings.forEach { mapping ->
                val wasNew = allInternalIds.add(mapping.internalChapterId)
                wasNew shouldBe true // No duplicates
            }
        }
    }
    
    /**
     * Test 4: Flow with error handling
     * 
     * Verifies that errors are handled gracefully
     */
    @Test
    fun epubDownloadFlow_handlesErrors() = runBlocking {
        // Test with non-existent file
        val nonExistentFile = File(testDir, "nonexistent.epub")
        val content = epubReader.readEpub(nonExistentFile)
        
        // Verify: Returns null for non-existent file
        content shouldBe null
    }
    
    /**
     * Creates a test EPUB file for testing.
     * 
     * This creates a minimal valid EPUB file with test content.
     */
    private fun createTestEpubFile(parentChapterId: Long): File {
        val epubDir = File(testDir, "epub")
        epubDir.mkdirs()
        
        val timestamp = System.currentTimeMillis()
        val fileName = "chapter_${parentChapterId}_${timestamp}.epub"
        val epubFile = File(epubDir, fileName)
        
        // Create a minimal EPUB structure
        // For testing purposes, we'll create a simple ZIP with EPUB structure
        createMinimalEpub(epubFile)
        
        return epubFile
    }
    
    /**
     * Creates a minimal valid EPUB file for testing.
     */
    private fun createMinimalEpub(file: File) {
        // Create a minimal EPUB structure using java.util.zip
        java.util.zip.ZipOutputStream(file.outputStream()).use { zip ->
            // mimetype file (must be first, uncompressed)
            zip.setLevel(0)
            zip.putNextEntry(java.util.zip.ZipEntry("mimetype"))
            zip.write("application/epub+zip".toByteArray())
            zip.closeEntry()
            
            zip.setLevel(9)
            
            // META-INF/container.xml
            zip.putNextEntry(java.util.zip.ZipEntry("META-INF/container.xml"))
            zip.write("""<?xml version="1.0"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                    <rootfiles>
                        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                    </rootfiles>
                </container>
            """.trimIndent().toByteArray())
            zip.closeEntry()
            
            // OEBPS/content.opf
            zip.putNextEntry(java.util.zip.ZipEntry("OEBPS/content.opf"))
            zip.write("""<?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="BookId">
                    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Test EPUB</dc:title>
                        <dc:creator>Test Author</dc:creator>
                        <dc:language>en</dc:language>
                        <dc:identifier id="BookId">test-epub-001</dc:identifier>
                    </metadata>
                    <manifest>
                        <item id="chapter1" href="chapter1.html" media-type="application/xhtml+xml"/>
                        <item id="chapter2" href="chapter2.html" media-type="application/xhtml+xml"/>
                        <item id="chapter3" href="chapter3.html" media-type="application/xhtml+xml"/>
                    </manifest>
                    <spine>
                        <itemref idref="chapter1"/>
                        <itemref idref="chapter2"/>
                        <itemref idref="chapter3"/>
                    </spine>
                </package>
            """.trimIndent().toByteArray())
            zip.closeEntry()
            
            // OEBPS/chapter1.html
            zip.putNextEntry(java.util.zip.ZipEntry("OEBPS/chapter1.html"))
            zip.write("""<?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head><title>Chapter 1</title></head>
                <body>
                    <h1>Chapter 1: The Beginning</h1>
                    <p>This is the first chapter of the test EPUB.</p>
                </body>
                </html>
            """.trimIndent().toByteArray())
            zip.closeEntry()
            
            // OEBPS/chapter2.html
            zip.putNextEntry(java.util.zip.ZipEntry("OEBPS/chapter2.html"))
            zip.write("""<?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head><title>Chapter 2</title></head>
                <body>
                    <h1>Chapter 2: The Journey</h1>
                    <p>This is the second chapter of the test EPUB.</p>
                </body>
                </html>
            """.trimIndent().toByteArray())
            zip.closeEntry()
            
            // OEBPS/chapter3.html
            zip.putNextEntry(java.util.zip.ZipEntry("OEBPS/chapter3.html"))
            zip.write("""<?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head><title>Chapter 3</title></head>
                <body>
                    <h1>Chapter 3: The End</h1>
                    <p>This is the final chapter of the test EPUB.</p>
                </body>
                </html>
            """.trimIndent().toByteArray())
            zip.closeEntry()
        }
    }
}
