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
 * Property-based tests for EPUB spine extraction.
 * 
 * Feature: epub-reader-improvements
 */
class SpineExtractionPropertyTest : StringSpec({
    
    val reader = EpubReaderImpl()
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val tempDir = File(context.cacheDir, "epub-test-${System.currentTimeMillis()}")
    
    beforeSpec {
        tempDir.mkdirs()
    }
    
    afterSpec {
        tempDir.deleteRecursively()
    }
    
    /**
     * Property 5: Complete Spine Extraction
     * Validates: Requirements 2.1
     * 
     * For any EPUB file with N spine references, 
     * the parser SHALL extract exactly N chapters
     */
    "parser extracts exactly N chapters for EPUB with N spine references".config(invocations = 100) {
        checkAll(Arb.int(1..50)) { chapterCount ->
            val testFile = File(tempDir, "test-$chapterCount.epub")
            
            // Create test EPUB with specified number of chapters
            EpubTestHelper.createTestEpub(testFile, chapterCount)
            
            // Parse the EPUB
            val content = runBlocking { reader.readEpub(testFile) }
            
            // Verify exactly N chapters were extracted
            content?.chapters?.size shouldBe chapterCount
            
            // Clean up
            testFile.delete()
        }
    }
    
    "all extracted chapters have sequential indices starting from 0".config(invocations = 100) {
        checkAll(Arb.int(1..50)) { chapterCount ->
            val testFile = File(tempDir, "test-indices-$chapterCount.epub")
            
            EpubTestHelper.createTestEpub(testFile, chapterCount)
            
            val content = runBlocking { reader.readEpub(testFile) }
            
            // Verify indices are sequential
            content?.chapters?.forEachIndexed { expectedIndex, chapter ->
                chapter.index shouldBe expectedIndex
            }
            
            testFile.delete()
        }
    }
})
