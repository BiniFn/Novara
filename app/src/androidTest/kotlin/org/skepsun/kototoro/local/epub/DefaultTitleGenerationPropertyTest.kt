package org.skepsun.kototoro.local.epub

import androidx.test.platform.app.InstrumentationRegistry
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Property-based tests for default EPUB chapter title generation.
 * 
 * Feature: epub-reader-improvements
 */
class DefaultTitleGenerationPropertyTest : StringSpec({
    
    val reader = EpubReaderImpl()
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val tempDir = File(context.cacheDir, "epub-test-default-titles-${System.currentTimeMillis()}")
    
    beforeSpec {
        tempDir.mkdirs()
    }
    
    afterSpec {
        tempDir.deleteRecursively()
    }
    
    /**
     * Property 7: Default Title Generation
     * Validates: Requirements 2.3
     * 
     * For any EPUB chapter without a title, 
     * the system SHALL generate a title in the format "Chapter {index + 1}"
     */
    "chapters without titles get default titles in format 'Chapter N'".config(invocations = 100) {
        checkAll(Arb.int(1..50)) { chapterCount ->
            val testFile = File(tempDir, "test-default-${System.currentTimeMillis()}.epub")
            
            // Create test EPUB with no titles (null titles)
            val noTitles = List(chapterCount) { null }
            EpubTestHelper.createTestEpub(testFile, chapterCount, noTitles)
            
            // Parse the EPUB
            val content = runBlocking { reader.readEpub(testFile) }
            
            // Verify all chapters have default titles
            content?.chapters?.forEachIndexed { index, chapter ->
                val expectedTitle = "Chapter ${index + 1}"
                chapter.title shouldBe expectedTitle
            }
            
            testFile.delete()
        }
    }
    
    "empty string titles are treated as missing and get default titles".config(invocations = 50) {
        checkAll(Arb.int(1..30)) { chapterCount ->
            val testFile = File(tempDir, "test-empty-${System.currentTimeMillis()}.epub")
            
            // Create test EPUB with empty string titles
            val emptyTitles = List(chapterCount) { "" }
            EpubTestHelper.createTestEpub(testFile, chapterCount, emptyTitles)
            
            val content = runBlocking { reader.readEpub(testFile) }
            
            content?.chapters?.forEachIndexed { index, chapter ->
                val expectedTitle = "Chapter ${index + 1}"
                chapter.title shouldBe expectedTitle
            }
            
            testFile.delete()
        }
    }
    
    "whitespace-only titles are treated as missing and get default titles".config(invocations = 50) {
        checkAll(Arb.int(1..30)) { chapterCount ->
            val testFile = File(tempDir, "test-whitespace-${System.currentTimeMillis()}.epub")
            
            // Create test EPUB with whitespace-only titles
            val whitespaceTitles = List(chapterCount) { "   \t\n   " }
            EpubTestHelper.createTestEpub(testFile, chapterCount, whitespaceTitles)
            
            val content = runBlocking { reader.readEpub(testFile) }
            
            content?.chapters?.forEachIndexed { index, chapter ->
                val expectedTitle = "Chapter ${index + 1}"
                chapter.title shouldBe expectedTitle
            }
            
            testFile.delete()
        }
    }
    
    "mixed titles preserve provided titles and generate defaults for missing".config(invocations = 50) {
        checkAll(Arb.int(3..20)) { chapterCount ->
            val testFile = File(tempDir, "test-mixed-${System.currentTimeMillis()}.epub")
            
            // Create mixed titles: some with values, some null/empty
            val mixedTitles = List(chapterCount) { index ->
                when {
                    index % 3 == 0 -> "Custom Title $index"
                    index % 3 == 1 -> null
                    else -> ""
                }
            }
            
            EpubTestHelper.createTestEpub(testFile, chapterCount, mixedTitles)
            
            val content = runBlocking { reader.readEpub(testFile) }
            
            content?.chapters?.forEachIndexed { index, chapter ->
                val expectedTitle = when {
                    index % 3 == 0 -> "Custom Title $index"
                    else -> "Chapter ${index + 1}"
                }
                chapter.title shouldBe expectedTitle
            }
            
            testFile.delete()
        }
    }
})
