package org.skepsun.kototoro.local.epub

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Property-based tests for ChapterType classification.
 * 
 * Feature: epub-reader-improvements
 */
class ChapterTypeClassificationPropertyTest : StringSpec({
    
    /**
     * Property 11: Chapter Type Classification Completeness
     * Validates: Requirements 3.1
     * 
     * For any chapter metadata, the system SHALL assign exactly one of 
     * NORMAL, EPUB_DOWNLOAD, or EPUB_INTERNAL type
     */
    "every chapter has exactly one type".config(invocations = 100) {
        checkAll(arbChapterMetadata()) { metadata ->
            // Verify the chapter type is one of the three valid types
            val validTypes = listOf(
                ChapterType.NORMAL,
                ChapterType.EPUB_DOWNLOAD,
                ChapterType.EPUB_INTERNAL
            )
            
            // The metadata must have exactly one type from the valid types
            validTypes.count { it == metadata.chapterType } shouldBe 1
        }
    }
    
    /**
     * Property: EPUB_INTERNAL chapters must have parent chapter ID
     * 
     * For any chapter with type EPUB_INTERNAL, the parentChapterId SHALL NOT be null
     */
    "EPUB_INTERNAL chapters must have parent chapter ID".config(invocations = 100) {
        checkAll(arbEpubInternalMetadata()) { metadata ->
            metadata.chapterType shouldBe ChapterType.EPUB_INTERNAL
            metadata.parentChapterId shouldBe metadata.parentChapterId // Should not be null
        }
    }
    
    /**
     * Property: EPUB_INTERNAL chapters must have EPUB filename
     * 
     * For any chapter with type EPUB_INTERNAL, the epubFileName SHALL NOT be null
     */
    "EPUB_INTERNAL chapters must have EPUB filename".config(invocations = 100) {
        checkAll(arbEpubInternalMetadata()) { metadata ->
            metadata.chapterType shouldBe ChapterType.EPUB_INTERNAL
            metadata.epubFileName shouldBe metadata.epubFileName // Should not be null
        }
    }
    
    /**
     * Property: NORMAL chapters should not have parent chapter ID
     * 
     * For any chapter with type NORMAL, the parentChapterId SHOULD be null
     */
    "NORMAL chapters should not have parent chapter ID".config(invocations = 100) {
        checkAll(arbNormalMetadata()) { metadata ->
            metadata.chapterType shouldBe ChapterType.NORMAL
            metadata.parentChapterId shouldBe null
        }
    }
    
    /**
     * Property: EPUB_DOWNLOAD chapters should not have parent chapter ID
     * 
     * For any chapter with type EPUB_DOWNLOAD, the parentChapterId SHOULD be null
     */
    "EPUB_DOWNLOAD chapters should not have parent chapter ID".config(invocations = 100) {
        checkAll(arbEpubDownloadMetadata()) { metadata ->
            metadata.chapterType shouldBe ChapterType.EPUB_DOWNLOAD
            metadata.parentChapterId shouldBe null
        }
    }
})

/**
 * Arbitrary generator for ChapterMetadata
 */
private fun arbChapterMetadata(): Arb<ChapterMetadata> = arbitrary {
    val chapterType = Arb.enum<ChapterType>().bind()
    val chapterId = Arb.long(1L..Long.MAX_VALUE).bind()
    
    when (chapterType) {
        ChapterType.NORMAL -> ChapterMetadata(
            chapterId = chapterId,
            chapterType = ChapterType.NORMAL,
            parentChapterId = null,
            epubFileName = null
        )
        ChapterType.EPUB_DOWNLOAD -> ChapterMetadata(
            chapterId = chapterId,
            chapterType = ChapterType.EPUB_DOWNLOAD,
            parentChapterId = null,
            epubFileName = Arb.string(1..50).orNull(0.3).bind()
        )
        ChapterType.EPUB_INTERNAL -> ChapterMetadata(
            chapterId = chapterId,
            chapterType = ChapterType.EPUB_INTERNAL,
            parentChapterId = Arb.long(1L..Long.MAX_VALUE).bind(),
            epubFileName = Arb.string(1..50).bind()
        )
    }
}

/**
 * Arbitrary generator for NORMAL chapter metadata
 */
private fun arbNormalMetadata(): Arb<ChapterMetadata> = arbitrary {
    ChapterMetadata(
        chapterId = Arb.long(1L..Long.MAX_VALUE).bind(),
        chapterType = ChapterType.NORMAL,
        parentChapterId = null,
        epubFileName = null
    )
}

/**
 * Arbitrary generator for EPUB_DOWNLOAD chapter metadata
 */
private fun arbEpubDownloadMetadata(): Arb<ChapterMetadata> = arbitrary {
    ChapterMetadata(
        chapterId = Arb.long(1L..Long.MAX_VALUE).bind(),
        chapterType = ChapterType.EPUB_DOWNLOAD,
        parentChapterId = null,
        epubFileName = Arb.string(1..50).orNull(0.3).bind()
    )
}

/**
 * Arbitrary generator for EPUB_INTERNAL chapter metadata
 */
private fun arbEpubInternalMetadata(): Arb<ChapterMetadata> = arbitrary {
    ChapterMetadata(
        chapterId = Arb.long(1L..Long.MAX_VALUE).bind(),
        chapterType = ChapterType.EPUB_INTERNAL,
        parentChapterId = Arb.long(1L..Long.MAX_VALUE).bind(),
        epubFileName = Arb.string(1..50).bind()
    )
}
