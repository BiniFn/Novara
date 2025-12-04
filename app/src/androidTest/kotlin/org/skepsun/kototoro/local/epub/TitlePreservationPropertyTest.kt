package org.skepsun.kototoro.local.epub

import androidx.test.platform.app.InstrumentationRegistry
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Property-based tests for EPUB chapter title preservation.
 * 
 * Feature: epub-reader-improvements
 */
class TitlePreservationPropertyTest : StringSpec({
    
    val reader = EpubReaderImpl()
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val tempDir = File(context.cacheDir, "epub-test-titles-${System.currentTimeMillis()}")
    
    beforeSpec {
        tempDir.mkdirs()
    }
    
    afterSpec {
        tempDir.deleteRecursively()
    }
    
    /**
     * Property 6: Title Preservation
     * Validates: Requirements 2.2
     * 
     * For any EPUB chapter with a title in metadata, 
     * the extracted chapter SHALL preserve that exact title
     */
    "extracted chapters preserve exact titles from EPUB metadata".config(invocations = 100) {
        checkAll(
            Arb.int(1..20),
            Arb.list(Arb.string(1..50), 1..20)
        ) { chapterCount, titlesList ->
            val testFile = File(tempDir, "test-titles-${System.currentTimeMillis()}.epub")
            
            // Use only the first chapterCount titles
            val titles = titlesList.take(chapterCount)
            
            // Create test EPUB with specific titles
            EpubTestHelper.createTestEpub(testFile, chapterCount, titles)
            
            // Parse the EPUB
            val content = runBlocking { reader.readEpub(testFile) }
            
            // Verify all titles are preserved exactly
            content?.chapters?.forEachIndexed { index, chapter ->
                chapter.title shouldBe titles[index]
            }
            
            testFile.delete()
        }
    }
    
    "titles with special characters are preserved correctly".config(invocations = 50) {
        val specialTitles = listOf(
            "Chapter 1: The Beginning",
            "第一章：开始",
            "Глава 1",
            "Chapter with <html> & \"quotes\"",
            "Chapter\nwith\nnewlines",
            "   Spaces   Around   ",
            "123456789",
            "!@#$%^&*()",
        )
        
        checkAll(Arb.int(1..specialTitles.size)) { count ->
            val testFile = File(tempDir, "test-special-${System.currentTimeMillis()}.epub")
            val titles = specialTitles.take(count)
            
            EpubTestHelper.createTestEpub(testFile, count, titles)
            
            val content = runBlocking { reader.readEpub(testFile) }
            
            content?.chapters?.forEachIndexed { index, chapter ->
                chapter.title shouldBe titles[index]
            }
            
            testFile.delete()
        }
    }
})
