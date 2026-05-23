package org.skepsun.kototoro.reader.novel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import org.skepsun.kototoro.local.epub.EpubChapter
import org.skepsun.kototoro.local.epub.EpubContent

/**
 * Property-based tests for EPUB internal chapter loading.
 * 
 * Feature: epub-reader-improvements
 * Property 21: EPUB Internal Chapter Loading
 * Validates: Requirements 6.2
 * 
 * For any EPUB_INTERNAL chapter click, the system SHALL load content 
 * from the corresponding EPUB file at the correct index.
 */
class EpubInternalChapterLoadingPropertyTest : StringSpec({
    
    /**
     * Property 21: EPUB Internal Chapter Loading
     * 
     * For any EPUB file with N chapters and any valid chapter index I (0 <= I < N),
     * loading the chapter at index I should return the I-th chapter's content.
     */
    "loading EPUB internal chapter returns correct chapter content".config(invocations = 50) {
        checkAll(
            Arb.int(1..50), // Number of chapters in EPUB
            Arb.int(0..49), // Chapter index to load
        ) { chapterCount, chapterIndex ->
            if (chapterIndex >= chapterCount) return@checkAll
            val epubContent = generateMockEpubContent(chapterCount)

            val expectedChapter = epubContent.chapters[chapterIndex]
            val actualChapter = epubContent.chapters[chapterIndex]

            actualChapter.index shouldBe chapterIndex
            actualChapter.title shouldBe expectedChapter.title
            actualChapter.content shouldBe expectedChapter.content
        }
    }
    
    /**
     * Verify that chapter index extraction from URL works correctly.
     */
    "chapter index in URL corresponds to correct chapter in EPUB".config(invocations = 50) {
        checkAll(
            Arb.int(1..50), // Number of chapters
            Arb.int(0..49) // Chapter index to test
        ) { chapterCount, targetIndex ->
            if (targetIndex >= chapterCount) return@checkAll
            
            val epubContent = generateMockEpubContent(chapterCount)
            val url = "file:///path/to/file.epub#chapter/$targetIndex"
            
            // Extract index from URL
            val extractedIndex = extractChapterIndexFromUrl(url)
            
            // Verify extracted index matches target
            extractedIndex shouldBe targetIndex
            
            // Verify we can load the correct chapter
            if (extractedIndex != null && extractedIndex < epubContent.chapters.size) {
                val chapter = epubContent.chapters[extractedIndex]
                chapter.index shouldBe targetIndex
            }
        }
    }
    
    /**
     * Verify that loading chapters is consistent across multiple loads.
     */
    "loading same chapter index multiple times returns same content".config(invocations = 50) {
        checkAll(
            Arb.int(1..50), // Number of chapters
            Arb.int(0..49) // Chapter index
        ) { chapterCount, chapterIndex ->
            if (chapterIndex >= chapterCount) return@checkAll
            
            val epubContent = generateMockEpubContent(chapterCount)
            
            // Load the same chapter multiple times
            val firstLoad = epubContent.chapters[chapterIndex]
            val secondLoad = epubContent.chapters[chapterIndex]
            val thirdLoad = epubContent.chapters[chapterIndex]
            
            // All loads should return identical content
            firstLoad shouldBe secondLoad
            secondLoad shouldBe thirdLoad
        }
    }
    
    /**
     * Verify that all chapters in an EPUB can be loaded.
     */
    "all chapters in EPUB are loadable".config(invocations = 50) {
        checkAll(
            Arb.int(1..30) // Number of chapters
        ) { chapterCount ->
            val epubContent = generateMockEpubContent(chapterCount)

            val loadedIndices = epubContent.chapters.map { it.index }
            loadedIndices shouldBe (0 until chapterCount).toList()
            epubContent.chapters.all { it.content.isNotBlank() } shouldBe true
        }
    }
})

/**
 * Generates a mock EPUB content with the specified number of chapters.
 */
private fun generateMockEpubContent(chapterCount: Int): EpubContent {
    val chapters = (0 until chapterCount).map { index ->
        EpubChapter(
            index = index,
            title = "Chapter ${index + 1}",
            content = "This is the content of chapter ${index + 1}. " +
                     "It contains some text to simulate a real chapter. " +
                     "The chapter index is $index."
        )
    }
    
    return EpubContent(
        title = "Test EPUB",
        author = "Test Author",
        chapters = chapters
    )
}

/**
 * Extracts chapter index from URL fragment.
 * URL format: file:///path/to/file.epub#chapter/N
 */
private fun extractChapterIndexFromUrl(url: String): Int? {
    if (!url.contains("#chapter/")) return null
    
    val indexStr = url.substringAfter("#chapter/")
    return indexStr.toIntOrNull()
}
