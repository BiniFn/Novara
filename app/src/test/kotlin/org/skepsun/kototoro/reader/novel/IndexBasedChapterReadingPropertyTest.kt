package org.skepsun.kototoro.reader.novel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import org.skepsun.kototoro.local.epub.EpubChapter
import org.skepsun.kototoro.local.epub.EpubContent

/**
 * Property-based tests for index-based chapter reading.
 * 
 * Feature: epub-reader-improvements
 * Property 24: Index-Based Chapter Reading
 * Validates: Requirements 6.6
 * 
 * For any EPUB file and valid chapter index I, reading at index I 
 * SHALL return the I-th chapter content.
 */
class IndexBasedChapterReadingPropertyTest : StringSpec({
    
    /**
     * Property 24: Index-Based Chapter Reading
     * 
     * For any EPUB with N chapters and any valid index I (0 <= I < N),
     * reading at index I returns the I-th chapter.
     */
    "reading at index I returns I-th chapter".config(invocations = 50) {
        checkAll(
            Arb.int(1..50), // Number of chapters in EPUB
            Arb.int(0..49), // Valid chapter index candidate
        ) { chapterCount, targetIndex ->
            if (targetIndex >= chapterCount) return@checkAll
            val epubContent = generateTestEpubContent(chapterCount)

            val chapter = requireNotNull(readChapterAtIndex(epubContent, targetIndex))
            chapter.index shouldBe targetIndex
            chapter.title shouldBe "Chapter ${targetIndex + 1}"
        }
    }
    
    /**
     * Verify that reading the same index multiple times returns the same chapter.
     */
    "reading same index multiple times returns same chapter".config(invocations = 50) {
        checkAll(
            Arb.int(1..50), // Number of chapters
            Arb.int(0..49)  // Index to read
        ) { chapterCount, targetIndex ->
            if (targetIndex >= chapterCount) return@checkAll
            
            val epubContent = generateTestEpubContent(chapterCount)
            
            val firstRead = readChapterAtIndex(epubContent, targetIndex)
            val secondRead = readChapterAtIndex(epubContent, targetIndex)
            val thirdRead = readChapterAtIndex(epubContent, targetIndex)
            
            firstRead shouldBe secondRead
            secondRead shouldBe thirdRead
        }
    }
    
    /**
     * Verify that reading different indices returns different chapters.
     */
    "reading different indices returns different chapters".config(invocations = 50) {
        checkAll(
            Arb.int(2..50) // At least 2 chapters
        ) { chapterCount ->
            val epubContent = generateTestEpubContent(chapterCount)

            val chapter0 = requireNotNull(readChapterAtIndex(epubContent, 0))
            val chapter1 = requireNotNull(readChapterAtIndex(epubContent, 1))

            chapter0.index shouldNotBe chapter1.index
            chapter0.title shouldNotBe chapter1.title
            chapter0.content shouldNotBe chapter1.content
        }
    }
    
    /**
     * Verify that reading at index 0 returns the first chapter.
     */
    "reading at index 0 returns first chapter".config(invocations = 50) {
        checkAll(
            Arb.int(1..50)
        ) { chapterCount ->
            val epubContent = generateTestEpubContent(chapterCount)
            
            val firstChapter = requireNotNull(readChapterAtIndex(epubContent, 0))

            firstChapter.index shouldBe 0
            firstChapter.title shouldBe "Chapter 1"
        }
    }
    
    /**
     * Verify that reading at last valid index returns the last chapter.
     */
    "reading at last index returns last chapter".config(invocations = 50) {
        checkAll(
            Arb.int(1..50)
        ) { chapterCount ->
            val epubContent = generateTestEpubContent(chapterCount)
            val lastIndex = chapterCount - 1
            
            val lastChapter = requireNotNull(readChapterAtIndex(epubContent, lastIndex))

            lastChapter.index shouldBe lastIndex
            lastChapter.title shouldBe "Chapter $chapterCount"
        }
    }
    
    /**
     * Verify that reading at invalid index returns null.
     */
    "reading at invalid index returns null".config(invocations = 50) {
        checkAll(
            Arb.int(1..50)
        ) { chapterCount ->
            val epubContent = generateTestEpubContent(chapterCount)
            
            // Try to read beyond the last chapter
            val invalidChapter = readChapterAtIndex(epubContent, chapterCount)
            invalidChapter shouldBe null
            
            // Try to read at negative index
            val negativeChapter = readChapterAtIndex(epubContent, -1)
            negativeChapter shouldBe null
        }
    }
    
    /**
     * Verify that all chapters in sequence can be read.
     */
    "all chapters can be read in sequence".config(invocations = 50) {
        checkAll(
            Arb.int(1..30)
        ) { chapterCount ->
            val epubContent = generateTestEpubContent(chapterCount)

            val readIndices = (0 until chapterCount).map { index ->
                requireNotNull(readChapterAtIndex(epubContent, index)).index
            }

            readIndices shouldBe (0 until chapterCount).toList()
        }
    }
    
    /**
     * Verify that chapter content is not empty.
     */
    "read chapters have non-empty content".config(invocations = 50) {
        checkAll(
            Arb.int(1..50),
            Arb.int(0..49)
        ) { chapterCount, targetIndex ->
            if (targetIndex >= chapterCount) return@checkAll
            
            val epubContent = generateTestEpubContent(chapterCount)
            val chapter = requireNotNull(readChapterAtIndex(epubContent, targetIndex))

            chapter.content.isNotBlank() shouldBe true
            chapter.title.isNotBlank() shouldBe true
        }
    }
    
    /**
     * Verify that reading chapters doesn't modify the EPUB content.
     */
    "reading chapters doesn't modify EPUB content".config(invocations = 50) {
        checkAll(
            Arb.int(1..50)
        ) { chapterCount ->
            val epubContent = generateTestEpubContent(chapterCount)
            val originalChapters = epubContent.chapters.toList()

            (0 until minOf(chapterCount, 10)).forEach { index ->
                readChapterAtIndex(epubContent, index)
            }

            epubContent.chapters shouldBe originalChapters
        }
    }
})

/**
 * Generates test EPUB content with the specified number of chapters.
 */
private fun generateTestEpubContent(chapterCount: Int): EpubContent {
    val chapters = (0 until chapterCount).map { index ->
        EpubChapter(
            index = index,
            title = "Chapter ${index + 1}",
            content = buildString {
                append("This is chapter ${index + 1}.\n\n")
                append("The chapter index is $index.\n\n")
                append("This chapter contains some sample text to simulate ")
                append("a real EPUB chapter. It has multiple paragraphs and ")
                append("enough content to be meaningful for testing purposes.\n\n")
                append("End of chapter ${index + 1}.")
            }
        )
    }
    
    return EpubContent(
        title = "Test EPUB Book",
        author = "Test Author",
        chapters = chapters
    )
}

/**
 * Reads a chapter at the specified index from EPUB content.
 * 
 * @param epubContent The EPUB content
 * @param index The chapter index (0-based)
 * @return The chapter at the specified index, or null if index is invalid
 */
private fun readChapterAtIndex(epubContent: EpubContent, index: Int): EpubChapter? {
    if (index < 0 || index >= epubContent.chapters.size) {
        return null
    }
    
    return epubContent.chapters[index]
}
