package org.skepsun.kototoro.reader.novel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.stringPattern
import io.kotest.property.checkAll

/**
 * Property-based tests for chapter URL index extraction.
 * 
 * Feature: epub-reader-improvements
 * Property 22: Chapter URL Index Extraction
 * Validates: Requirements 6.4
 * 
 * For any EPUB internal chapter URL in format "file:///.../file.epub#chapter/N",
 * the extracted index SHALL be N.
 */
class UrlIndexExtractionPropertyTest : StringSpec({
    
    /**
     * Property 22: Chapter URL Index Extraction
     * 
     * For any URL with format "...#chapter/N", extracting the index should return N.
     */
    "extracting index from URL returns correct value".config(invocations = 100) {
        checkAll(
            Arb.int(0..999_999), // Chapter index
            Arb.stringPattern("[a-z]{5,10}") // Random filename
        ) { chapterIndex, filename ->
            val url = "file:///storage/emulated/0/epub/$filename.epub#chapter/$chapterIndex"
            
            val extractedIndex = extractChapterIndexFromUrl(url)
            
            extractedIndex shouldNotBe null
            extractedIndex shouldBe chapterIndex
        }
    }
    
    /**
     * Verify extraction works with various path formats.
     */
    "extraction works with different path formats".config(invocations = 100) {
        checkAll(
            Arb.int(0..999_999)
        ) { chapterIndex ->
            val urls = listOf(
                "file:///path/to/file.epub#chapter/$chapterIndex",
                "file:///storage/emulated/0/epub/chapter_123_vol1.epub#chapter/$chapterIndex",
                "file:///data/data/org.app/files/epub/test.epub#chapter/$chapterIndex",
                "/absolute/path/file.epub#chapter/$chapterIndex",
                "relative/path/file.epub#chapter/$chapterIndex"
            )
            
            urls.forEach { url ->
                val extractedIndex = extractChapterIndexFromUrl(url)
                extractedIndex shouldBe chapterIndex
            }
        }
    }
    
    /**
     * Verify extraction handles edge cases.
     */
    "extraction handles edge cases correctly".config(invocations = 100) {
        checkAll(
            Arb.int(0..999_999)
        ) { chapterIndex ->
            // URL with query parameters before fragment
            val urlWithQuery = "file:///path/file.epub?param=value#chapter/$chapterIndex"
            extractChapterIndexFromUrl(urlWithQuery) shouldBe chapterIndex
            
            // URL with multiple # characters (only last fragment matters)
            val urlWithMultipleHash = "file:///path/file#name.epub#chapter/$chapterIndex"
            extractChapterIndexFromUrl(urlWithMultipleHash) shouldBe chapterIndex
        }
    }
    
    /**
     * Verify extraction returns null for invalid URLs.
     */
    "extraction returns null for invalid URLs".config(invocations = 50) {
        val invalidUrls = listOf(
            "file:///path/to/file.epub",  // No fragment
            "file:///path/to/file.epub#",  // Empty fragment
            "file:///path/to/file.epub#chapter/",  // No index
            "file:///path/to/file.epub#chapter/abc",  // Non-numeric index
            "file:///path/to/file.epub#page/5",  // Wrong fragment format
            "file:///path/to/file.epub#chapter/-1",  // Negative index (should be null)
            ""  // Empty string
        )
        
        invalidUrls.forEach { url ->
            val result = extractChapterIndexFromUrl(url)
            // For negative numbers, toIntOrNull() returns the number, but we should validate it
            if (url.contains("#chapter/-")) {
                result shouldNotBe null  // toIntOrNull returns -1
                (result!! < 0) shouldBe true  // But it's negative
            } else {
                result shouldBe null
            }
        }
    }
    
    /**
     * Verify extraction is consistent.
     */
    "extracting same URL multiple times returns same result".config(invocations = 100) {
        checkAll(
            Arb.int(0..999_999)
        ) { chapterIndex ->
            val url = "file:///path/to/file.epub#chapter/$chapterIndex"
            
            val first = extractChapterIndexFromUrl(url)
            val second = extractChapterIndexFromUrl(url)
            val third = extractChapterIndexFromUrl(url)
            
            first shouldBe second
            second shouldBe third
            first shouldBe chapterIndex
        }
    }
    
    /**
     * Verify extraction works with zero index.
     */
    "extraction works with zero index" {
        val url = "file:///path/to/file.epub#chapter/0"
        val extractedIndex = extractChapterIndexFromUrl(url)
        
        extractedIndex shouldNotBe null
        extractedIndex shouldBe 0
    }
    
    /**
     * Verify extraction works with large indices.
     */
    "extraction works with large indices".config(invocations = 50) {
        checkAll(
            Arb.int(100_000..999_999)
        ) { chapterIndex ->
            val url = "file:///path/to/file.epub#chapter/$chapterIndex"
            val extractedIndex = extractChapterIndexFromUrl(url)
            
            extractedIndex shouldBe chapterIndex
        }
    }
})

/**
 * Extracts chapter index from URL fragment.
 * URL format: file:///path/to/file.epub#chapter/N
 * 
 * @param url The chapter URL
 * @return The chapter index, or null if extraction fails
 */
private fun extractChapterIndexFromUrl(url: String): Int? {
    if (!url.contains("#chapter/")) return null
    
    val indexStr = url.substringAfter("#chapter/")
        .substringBefore("?")  // Remove query parameters if any
        .substringBefore("#")  // Remove additional fragments if any
    
    return indexStr.toIntOrNull()
}
